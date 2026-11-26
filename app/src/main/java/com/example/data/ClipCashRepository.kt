package com.example.data

import androidx.room.withTransaction
import com.example.model.ClipCatalog
import com.example.model.ClipCategory
import com.example.model.QuestItem
import com.example.model.RewardOption
import com.example.model.TransactionType
import com.example.model.VideoClip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipCashRepository(
    private val database: ClipCashDatabase,
    private val supabaseClient: SupabaseClient,
    private val sessionStore: SupabaseSessionStore
) {
    private val dao = database.clipCashDao()
    private val syncMutex = Mutex()
    private var verifiedRemoteUserId: String? = null
    private val _syncNotice = MutableStateFlow<String?>(null)
    val syncNoticeFlow: StateFlow<String?> = _syncNotice.asStateFlow()

    val authSessionFlow: Flow<SupabaseSession?> = sessionStore.sessionFlow

    val walletState: Flow<WalletStateEntity> = dao.observeWalletState()
        .map { it ?: WalletStateEntity() }

    val clipProgressMap: Flow<Map<String, ClipProgressEntity>> = dao.observeAllClipProgress()
        .map { list -> list.associateBy { it.clipId } }

    val transactions: Flow<List<TransactionEntity>> = dao.observeTransactions()

    val claimedQuestIds: Flow<Set<String>> = dao.observeClaimedQuests()
        .map { list -> list.map { it.questId }.toSet() }

    fun todayDateKey(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date())
    }

    suspend fun signInWithVerification(email: String, password: String): Result<SupabaseSignInResult> {
        return supabaseClient.signInWithVerification(email, password)
    }

    suspend fun verifySignInCode(email: String, code: String): Result<SupabaseSession> {
        val result = supabaseClient.verifySignInCode(email, code)
        return result.map { rawSession ->
            val (hydratedSession, _) = syncInternal(rawSession, persistHydratedSession = false)
            val finalSession = hydratedSession ?: rawSession
            sessionStore.saveSession(finalSession)
            finalSession
        }
    }

    suspend fun resendSignInCode(email: String): Result<Unit> {
        return supabaseClient.resendSignInCode(email)
    }

    suspend fun signUp(
        fullName: String,
        username: String,
        nationality: String,
        countryIso: String,
        phoneNumber: String,
        email: String,
        password: String
    ): Result<SupabaseSignUpResult> {
        return supabaseClient.signUp(
            fullName = fullName,
            username = username,
            nationality = nationality,
            countryIso = countryIso,
            phoneNumber = phoneNumber,
            email = email,
            password = password
        )
    }

    suspend fun verifySignUpCode(
        email: String,
        code: String,
        usesEmailOtpFallback: Boolean
    ): Result<SupabaseSession> {
        val result = supabaseClient.verifySignUpCode(email, code, usesEmailOtpFallback)
        return result.map { rawSession ->
            val (hydratedSession, _) = syncInternal(rawSession, persistHydratedSession = false)
            val finalSession = hydratedSession ?: rawSession
            sessionStore.saveSession(finalSession)
            finalSession
        }
    }

    suspend fun resendSignUpCode(email: String, usesEmailOtpFallback: Boolean): Result<Unit> {
        return supabaseClient.resendSignUpCode(email, usesEmailOtpFallback)
    }

    suspend fun requestPasswordReset(email: String): Result<Unit> {
        return supabaseClient.requestPasswordReset(email)
    }

    suspend fun verifyAndResetPassword(
        email: String,
        code: String,
        newPassword: String
    ): Result<Unit> {
        return supabaseClient.verifyAndResetPassword(email, code, newPassword)
    }

    suspend fun requestEmailChange(newEmail: String): Result<Unit> {
        return supabaseClient.requestEmailChange(newEmail)
    }

    suspend fun verifyEmailChange(
        emailToVerify: String,
        desiredNewEmail: String,
        code: String
    ): Result<EmailChangeVerifyResult> {
        val result = supabaseClient.verifyEmailChange(emailToVerify, desiredNewEmail, code)
        if (result.isSuccess) {
            val outcome = result.getOrNull()
            if (outcome is EmailChangeVerifyResult.Completed) {
                sessionStore.saveSession(outcome.updatedSession)
                val currentWallet = dao.getWalletState()
                if (currentWallet != null) {
                    val updatedWallet = currentWallet.copy(
                        userId = outcome.updatedSession.userId,
                        userEmail = outcome.newEmail
                    )
                    dao.upsertWalletState(updatedWallet)
                    val claimed = dao.getClaimedQuestIdsSync().toSet()
                    val txs = dao.getRecentTransactions(40)
                    supabaseClient.upsertProfile(outcome.updatedSession, updatedWallet, claimed, txs)
                }
            }
        }
        return result
    }

    suspend fun signOut() {
        verifiedRemoteUserId = null
        supabaseClient.signOut()
        clearLocalCache()
        _syncNotice.value = null
    }

    private suspend fun clearLocalCache() {
        database.withTransaction {
            dao.clearWalletState()
            dao.clearClipProgress()
            dao.clearTransactions()
            dao.clearClaimedQuests()
        }
    }

    suspend fun searchClips(query: String, category: ClipCategory): Result<List<VideoClip>> {
        return supabaseClient.searchVideos(query, category)
    }

    suspend fun searchClipsPage(
        query: String,
        category: ClipCategory,
        pageIndex: Int,
        pageToken: String?,
        seenVideoIds: Set<String>
    ): Result<ShortsPageResult> {
        val dbVideos = if (pageIndex == 0 && (category == ClipCategory.ALL || category == ClipCategory.TRENDING || query.isNotBlank())) {
            supabaseClient.fetchActiveVideos(category = category, query = query).getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val searchResult = supabaseClient.searchVideosPage(
            query = query,
            category = category,
            pageIndex = pageIndex,
            pageToken = pageToken,
            seenVideoIds = seenVideoIds
        )
        if (dbVideos.isEmpty()) return searchResult

        val seen = HashSet(seenVideoIds)
        val merged = mutableListOf<VideoClip>()
        for (v in dbVideos) {
            if (seen.add(v.videoId)) merged.add(v)
        }
        val searchPage = searchResult.getOrNull()
        if (searchPage != null) {
            for (v in searchPage.clips) {
                if (seen.add(v.videoId)) merged.add(v)
            }
        }
        return Result.success(
            ShortsPageResult(
                clips = merged,
                nextPageToken = searchPage?.nextPageToken
            )
        )
    }

    fun isUserSynced(userId: String): Boolean = verifiedRemoteUserId == userId

    suspend fun syncFromSupabase(session: SupabaseSession? = null): String? =
        syncInternal(session = session, persistHydratedSession = true).second

    private suspend fun syncInternal(
        session: SupabaseSession?,
        persistHydratedSession: Boolean
    ): Pair<SupabaseSession?, String?> = syncMutex.withLock {
        val activeSession = session ?: sessionStore.getSession() ?: return@withLock null to null
        var hydratedSession = activeSession
        var syncNotice: String? = null

        val localWallet = dao.getWalletState()
        if (localWallet != null && localWallet.userId.isNotBlank() && localWallet.userId != activeSession.userId) {
            clearLocalCache()
            verifiedRemoteUserId = null
        }

        when (val profileRes = supabaseClient.fetchProfile(activeSession)) {
            is SupabaseDbSyncResult.Success -> {
                val remoteProfile = profileRes.data
                if (remoteProfile != null) {
                    hydratedSession = activeSession.copy(
                        email = activeSession.email.ifBlank { remoteProfile.email },
                        fullName = activeSession.fullName.ifBlank { remoteProfile.fullName },
                        username = activeSession.username.ifBlank { remoteProfile.username },
                        nationality = activeSession.nationality.ifBlank { remoteProfile.nationality },
                        phoneNumber = activeSession.phoneNumber.ifBlank { remoteProfile.phoneNumber }
                    )

                    // If local cache belonged to this same user and never finished cloud sync yet while DB had 0/uninitialized state,
                    // preserve locally earned coins/progress rather than overwriting them with 0.
                    val localTxsForUser = if (localWallet != null && localWallet.userId == activeSession.userId) {
                        dao.getRecentTransactions(40)
                    } else {
                        emptyList()
                    }
                    val localQuestsForUser = if (localWallet != null && localWallet.userId == activeSession.userId) {
                        dao.getClaimedQuestIdsSync().toSet()
                    } else {
                        emptySet()
                    }
                    val shouldAdoptLocalUnsynced = localWallet != null &&
                        localWallet.userId == activeSession.userId &&
                        (!remoteProfile.hasWalletsRow ||
                            (remoteProfile.wallet.coinBalance == 0 && remoteProfile.wallet.clipsWatchedCount == 0 && localWallet.clipsWatchedCount > 0))

                    val mergedBalance = if (shouldAdoptLocalUnsynced) {
                        maxOf(remoteProfile.wallet.coinBalance, localWallet.coinBalance)
                    } else {
                        remoteProfile.wallet.coinBalance
                    }
                    val syncedWallet = remoteProfile.wallet.copy(
                        userId = activeSession.userId,
                        userEmail = hydratedSession.email.ifBlank { remoteProfile.wallet.userEmail },
                        coinBalance = mergedBalance,
                        totalEarnedCoins = maxOf(
                            remoteProfile.wallet.totalEarnedCoins,
                            if (shouldAdoptLocalUnsynced) localWallet.totalEarnedCoins else 0,
                            mergedBalance
                        ),
                        streakDays = maxOf(
                            remoteProfile.wallet.streakDays,
                            if (shouldAdoptLocalUnsynced) localWallet.streakDays else 0
                        ),
                        lastCheckInDate = remoteProfile.wallet.lastCheckInDate.ifBlank {
                            if (shouldAdoptLocalUnsynced) localWallet.lastCheckInDate else ""
                        },
                        lastFreeSpinDate = remoteProfile.wallet.lastFreeSpinDate.ifBlank {
                            if (shouldAdoptLocalUnsynced) localWallet.lastFreeSpinDate else ""
                        },
                        clipsWatchedCount = maxOf(
                            remoteProfile.wallet.clipsWatchedCount,
                            if (shouldAdoptLocalUnsynced) localWallet.clipsWatchedCount else 0
                        ),
                        gamesPlayedCount = maxOf(
                            remoteProfile.wallet.gamesPlayedCount,
                            if (shouldAdoptLocalUnsynced) localWallet.gamesPlayedCount else 0
                        )
                    )
                    val mergedQuests = remoteProfile.claimedQuestIds + localQuestsForUser
                    val mergedTxs = (remoteProfile.recentTransactions + localTxsForUser)
                        .distinctBy { "${it.createdAt}_${it.title}_${it.amountCoins}" }
                        .sortedByDescending { it.createdAt }
                        .take(40)

                    database.withTransaction {
                        dao.upsertWalletState(syncedWallet)
                        dao.clearTransactions()
                        val displayTxs = mergedTxs.ifEmpty {
                            listOf(
                                TransactionEntity(
                                    title = "ClipCash Wallet Synced",
                                    subtitle = "Balance restored for ${hydratedSession.email}",
                                    amountCoins = syncedWallet.coinBalance,
                                    type = TransactionType.BONUS.name
                                )
                            )
                        }
                        dao.insertTransactions(displayTxs)
                        backfillClipProgressFromTransactions(displayTxs)
                        dao.clearClaimedQuests()
                        if (mergedQuests.isNotEmpty()) {
                            dao.insertClaimedQuests(mergedQuests.map { ClaimedQuestEntity(it) })
                        }
                    }

                    if (!remoteProfile.hasWalletsRow || shouldAdoptLocalUnsynced) {
                        val pushRes = supabaseClient.upsertProfile(
                            session = hydratedSession,
                            wallet = syncedWallet,
                            claimedQuestIds = mergedQuests,
                            recentTransactions = mergedTxs
                        )
                        when (pushRes) {
                            is SupabaseDbSyncResult.Success -> verifiedRemoteUserId = activeSession.userId
                            SupabaseDbSyncResult.TableNotProvisioned -> syncNotice = "Cloud sync unavailable — saving locally."
                            is SupabaseDbSyncResult.Error -> syncNotice = pushRes.message
                        }
                    } else {
                        verifiedRemoteUserId = activeSession.userId
                    }

                    if (persistHydratedSession && hydratedSession != activeSession) {
                        sessionStore.saveSession(hydratedSession)
                    }
                } else {
                    syncNotice = ensureInitializedAndPush(activeSession)
                    if (syncNotice == null) {
                        verifiedRemoteUserId = activeSession.userId
                    }
                }
            }
            SupabaseDbSyncResult.TableNotProvisioned -> {
                ensureInitializedLocalOnly(activeSession)
                syncNotice = "Cloud sync unavailable — saving locally."
            }
            is SupabaseDbSyncResult.Error -> {
                ensureInitializedLocalOnly(activeSession)
                syncNotice = profileRes.message
            }
        }

        _syncNotice.value = syncNotice
        hydratedSession to syncNotice
    }

    private suspend fun backfillClipProgressFromTransactions(transactions: List<TransactionEntity>) {
        val videoTxsByTitle = transactions
            .filter { it.type == TransactionType.VIDEO.name }
            .groupBy { it.title }
        if (videoTxsByTitle.isEmpty()) return

        for (clip in ClipCatalog.fallbackClips) {
            val matching = videoTxsByTitle[clip.title].orEmpty()
            if (matching.isNotEmpty()) {
                val existing = dao.getClipProgress(clip.id)
                if (existing == null || existing.watchCount < matching.size) {
                    dao.upsertClipProgress(
                        ClipProgressEntity(
                            clipId = clip.id,
                            watchCount = matching.size,
                            lastWatchedAt = matching.maxOf { it.createdAt },
                            earnedCoinsTotal = matching.sumOf { it.amountCoins }
                        )
                    )
                }
            }
        }
    }

    private suspend fun ensureInitializedLocalOnly(session: SupabaseSession) {
        database.withTransaction {
            if (dao.getWalletState() == null) {
                dao.upsertWalletState(
                    WalletStateEntity(
                        userId = session.userId,
                        userEmail = session.email
                    )
                )
                dao.insertTransaction(
                    TransactionEntity(
                        title = "Welcome Bonus",
                        subtitle = "Starter reward for joining ClipCash",
                        amountCoins = 150,
                        type = TransactionType.BONUS.name
                    )
                )
            }
        }
    }

    private suspend fun ensureInitializedAndPush(session: SupabaseSession): String? {
        val welcomeTx = TransactionEntity(
            title = "Welcome Bonus",
            subtitle = "Starter reward for joining ClipCash",
            amountCoins = 150,
            type = TransactionType.BONUS.name
        )
        val initialWallet = WalletStateEntity(
            userId = session.userId,
            userEmail = session.email
        )
        database.withTransaction {
            val existing = dao.getWalletState()
            if (existing == null) {
                dao.upsertWalletState(initialWallet)
                dao.insertTransaction(welcomeTx)
            } else if (existing.userId.isBlank() || existing.userEmail.isBlank()) {
                dao.upsertWalletState(existing.copy(userId = session.userId, userEmail = session.email))
            }
        }
        val currentWallet = dao.getWalletState() ?: initialWallet
        val claimed = dao.getClaimedQuestIdsSync().toSet()
        val recentTxs = dao.getRecentTransactions(40)
        return when (val profilePush = supabaseClient.upsertProfile(session, currentWallet, claimed, recentTxs)) {
            is SupabaseDbSyncResult.Success -> null
            SupabaseDbSyncResult.TableNotProvisioned -> "Cloud sync unavailable — saving locally."
            is SupabaseDbSyncResult.Error -> profilePush.message
        }
    }

    suspend fun claimDailyCheckIn(): Int {
        val today = todayDateKey()
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val bonus = database.withTransaction {
            val current = dao.getWalletState() ?: WalletStateEntity()
            if (current.lastCheckInDate == today) return@withTransaction 0

            val newStreak = current.streakDays + 1
            val bonusCoins = (50 + (newStreak - 1) * 10).coerceAtMost(120)
            val updated = current.copy(
                coinBalance = current.coinBalance + bonusCoins,
                totalEarnedCoins = current.totalEarnedCoins + bonusCoins,
                streakDays = newStreak,
                lastCheckInDate = today
            )
            val tx = TransactionEntity(
                title = "Daily Check-In (Day $newStreak)",
                subtitle = "Streak bonus claimed",
                amountCoins = bonusCoins,
                type = TransactionType.BONUS.name
            )
            dao.upsertWalletState(updated)
            dao.insertTransaction(tx)
            newWallet = updated
            newTx = tx
            bonusCoins
        }

        if (bonus > 0) {
            pushWalletAndTx(newWallet, newTx)
        }
        return bonus
    }

    suspend fun startWatchSession(clip: VideoClip): Result<String?> {
        val uuid = clip.remoteUuid?.takeIf { it.isNotBlank() }
        if (uuid == null || !clip.rewardEligible) {
            return Result.success(null)
        }
        val activeSession = sessionStore.getSession()
            ?: return Result.failure(IllegalStateException("Please sign in to earn coins."))
        return supabaseClient.startWatchRpc(activeSession, uuid).map { it }
    }

    suspend fun completeWatchSession(clip: VideoClip, sessionId: String?): Result<Int> {
        val activeSession = sessionStore.getSession()
        if (sessionId.isNullOrBlank() || activeSession == null) {
            val localEarned = recordClipWatched(clip)
            return Result.success(localEarned)
        }

        val rpcResult = supabaseClient.completeWatchRpc(activeSession, sessionId)
        val coinsAwarded = rpcResult.getOrElse { err ->
            return Result.failure(err)
        }.toInt().coerceAtLeast(0)

        val authoritativeServerBalance = supabaseClient.fetchWalletBalance(activeSession)
            .getOrNull()
            ?.toInt()

        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        database.withTransaction {
            val current = dao.getWalletState() ?: WalletStateEntity(
                userId = activeSession.userId,
                userEmail = activeSession.email
            )
            val nextBalance = if (authoritativeServerBalance != null) {
                maxOf(authoritativeServerBalance, current.coinBalance + coinsAwarded)
            } else {
                current.coinBalance + coinsAwarded
            }
            val updated = current.copy(
                coinBalance = nextBalance,
                totalEarnedCoins = maxOf(current.totalEarnedCoins + coinsAwarded, nextBalance),
                clipsWatchedCount = current.clipsWatchedCount + if (coinsAwarded > 0) 1 else 0
            )
            dao.upsertWalletState(updated)
            if (coinsAwarded > 0) {
                val prevProgress = dao.getClipProgress(clip.id)
                dao.upsertClipProgress(
                    ClipProgressEntity(
                        clipId = clip.id,
                        watchCount = (prevProgress?.watchCount ?: 0) + 1,
                        lastWatchedAt = System.currentTimeMillis(),
                        earnedCoinsTotal = (prevProgress?.earnedCoinsTotal ?: 0) + coinsAwarded
                    )
                )
                val tx = TransactionEntity(
                    title = clip.title,
                    subtitle = "Watched on ClipCash",
                    amountCoins = coinsAwarded,
                    type = TransactionType.VIDEO.name
                )
                dao.insertTransaction(tx)
                newTx = tx
            }
            newWallet = updated
        }

        pushWalletAndTx(newWallet, newTx)
        return Result.success(coinsAwarded)
    }

    suspend fun recordClipWatched(clip: VideoClip): Int {
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val reward = database.withTransaction {
            val current = dao.getWalletState() ?: WalletStateEntity()
            val earned = clip.rewardCoins
            val updated = current.copy(
                coinBalance = current.coinBalance + earned,
                totalEarnedCoins = current.totalEarnedCoins + earned,
                clipsWatchedCount = current.clipsWatchedCount + 1
            )
            dao.upsertWalletState(updated)
            val prevProgress = dao.getClipProgress(clip.id)
            dao.upsertClipProgress(
                ClipProgressEntity(
                    clipId = clip.id,
                    watchCount = (prevProgress?.watchCount ?: 0) + 1,
                    lastWatchedAt = System.currentTimeMillis(),
                    earnedCoinsTotal = (prevProgress?.earnedCoinsTotal ?: 0) + earned
                )
            )
            val tx = TransactionEntity(
                title = clip.title,
                subtitle = "Watched on ClipCash (${clip.watchTargetSeconds}s)",
                amountCoins = earned,
                type = TransactionType.VIDEO.name
            )
            dao.insertTransaction(tx)
            newWallet = updated
            newTx = tx
            earned
        }

        pushWalletAndTx(newWallet, newTx)
        return reward
    }

    suspend fun recordCoinCatcherGame(coinsWon: Int): Int =
        recordArcadeGameReward(gameTitle = "Coin Catcher Run", coinsWon = coinsWon)

    suspend fun recordArcadeGameReward(gameTitle: String, coinsWon: Int): Int {
        val safeCoins = coinsWon.coerceAtLeast(0)
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        database.withTransaction {
            val current = dao.getWalletState() ?: WalletStateEntity()
            val updated = current.copy(
                coinBalance = current.coinBalance + safeCoins,
                totalEarnedCoins = current.totalEarnedCoins + safeCoins,
                gamesPlayedCount = current.gamesPlayedCount + 1
            )
            dao.upsertWalletState(updated)
            newWallet = updated
            if (safeCoins > 0) {
                val tx = TransactionEntity(
                    title = gameTitle,
                    subtitle = "Arcade mini-game reward",
                    amountCoins = safeCoins,
                    type = TransactionType.GAME.name
                )
                dao.insertTransaction(tx)
                newTx = tx
            }
        }

        pushWalletAndTx(newWallet, newTx)
        return safeCoins
    }

    suspend fun spinLuckyWheel(prizeCoins: Int, usedFreeSpin: Boolean): Result<Int> {
        val today = todayDateKey()
        val spinCost = if (usedFreeSpin) 0 else 25
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val result = database.withTransaction {
            val current = dao.getWalletState() ?: WalletStateEntity()
            if (usedFreeSpin && current.lastFreeSpinDate == today) {
                return@withTransaction Result.failure(IllegalStateException("Daily free spin already used"))
            }
            if (current.coinBalance < spinCost) {
                return@withTransaction Result.failure(IllegalStateException("Need $spinCost coins to spin"))
            }
            val newBalance = current.coinBalance - spinCost + prizeCoins
            val updated = current.copy(
                coinBalance = newBalance,
                totalEarnedCoins = current.totalEarnedCoins + prizeCoins,
                lastFreeSpinDate = if (usedFreeSpin) today else current.lastFreeSpinDate,
                gamesPlayedCount = current.gamesPlayedCount + 1
            )
            val tx = TransactionEntity(
                title = "Lucky Wheel Prize",
                subtitle = if (usedFreeSpin) "Free daily spin" else "Bonus spin (-25 coins entry)",
                amountCoins = prizeCoins - spinCost,
                type = TransactionType.GAME.name
            )
            dao.upsertWalletState(updated)
            dao.insertTransaction(tx)
            newWallet = updated
            newTx = tx
            Result.success(prizeCoins)
        }

        if (result.isSuccess) {
            pushWalletAndTx(newWallet, newTx)
        }
        return result
    }

    suspend fun claimQuest(quest: QuestItem): Int {
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val earned = database.withTransaction {
            if (dao.getClaimedQuest(quest.id) != null) return@withTransaction 0
            val current = dao.getWalletState() ?: WalletStateEntity()
            val updated = current.copy(
                coinBalance = current.coinBalance + quest.rewardCoins,
                totalEarnedCoins = current.totalEarnedCoins + quest.rewardCoins
            )
            val tx = TransactionEntity(
                title = quest.title,
                subtitle = "Quest milestone reward",
                amountCoins = quest.rewardCoins,
                type = TransactionType.QUEST.name
            )
            dao.insertClaimedQuest(ClaimedQuestEntity(questId = quest.id))
            dao.upsertWalletState(updated)
            dao.insertTransaction(tx)
            newWallet = updated
            newTx = tx
            quest.rewardCoins
        }

        if (earned > 0) {
            pushWalletAndTx(newWallet, newTx)
        }
        return earned
    }

    suspend fun redeemReward(reward: RewardOption, note: String = ""): Result<String> {
        val cleanNote = note.trim()
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val result = database.withTransaction {
            val current = dao.getWalletState() ?: WalletStateEntity()
            if (current.coinBalance < reward.coinCost) {
                return@withTransaction Result.failure(
                    IllegalStateException("You need ${reward.coinCost - current.coinBalance} more coins.")
                )
            }
            val code = "PK-${(100000..999999).random()}"
            val updated = current.copy(coinBalance = current.coinBalance - reward.coinCost)
            val subtitleText = if (cleanNote.isNotEmpty()) {
                "${reward.brand} · $cleanNote · Ref $code"
            } else {
                "${reward.brand} · ${reward.perkLabel} · Ref $code"
            }
            val tx = TransactionEntity(
                title = reward.title,
                subtitle = subtitleText,
                amountCoins = -reward.coinCost,
                type = TransactionType.REDEEM.name
            )
            dao.upsertWalletState(updated)
            dao.insertTransaction(tx)
            newWallet = updated
            newTx = tx
            Result.success(code)
        }

        if (result.isSuccess) {
            pushWalletAndTx(newWallet, newTx)
        }
        return result
    }

    private suspend fun pushWalletAndTx(wallet: WalletStateEntity?, tx: TransactionEntity?) {
        val session = sessionStore.getSession() ?: return
        var walletToPush = wallet?.copy(
            userId = session.userId,
            userEmail = session.email
        )
        var claimedQuests = dao.getClaimedQuestIdsSync().toSet()
        var recentTxs = dao.getRecentTransactions(40)

        if (walletToPush != null && verifiedRemoteUserId != session.userId) {
            when (val verifyRes = supabaseClient.fetchProfile(session)) {
                is SupabaseDbSyncResult.Success -> {
                    val remoteProfile = verifyRes.data
                    val remote = remoteProfile?.wallet
                    verifiedRemoteUserId = session.userId
                    if (remote != null && remoteProfile.hasWalletsRow) {
                        val deltaBalance = walletToPush.coinBalance - 150
                        val deltaTotal = (walletToPush.totalEarnedCoins - 150).coerceAtLeast(0)
                        walletToPush = remote.copy(
                            userId = session.userId,
                            userEmail = session.email.ifBlank { remote.userEmail },
                            coinBalance = (remote.coinBalance + deltaBalance).coerceAtLeast(0),
                            totalEarnedCoins = maxOf(
                                remote.totalEarnedCoins + deltaTotal,
                                (remote.coinBalance + deltaBalance).coerceAtLeast(0)
                            ),
                            clipsWatchedCount = remote.clipsWatchedCount + walletToPush.clipsWatchedCount,
                            gamesPlayedCount = remote.gamesPlayedCount + walletToPush.gamesPlayedCount,
                            streakDays = maxOf(remote.streakDays, walletToPush.streakDays),
                            lastCheckInDate = walletToPush.lastCheckInDate.ifBlank { remote.lastCheckInDate },
                            lastFreeSpinDate = walletToPush.lastFreeSpinDate.ifBlank { remote.lastFreeSpinDate }
                        )
                        claimedQuests = remoteProfile.claimedQuestIds + claimedQuests
                        recentTxs = (recentTxs + remoteProfile.recentTransactions)
                            .distinctBy { "${it.createdAt}_${it.title}_${it.amountCoins}" }
                            .sortedByDescending { it.createdAt }
                            .take(40)
                        database.withTransaction {
                            dao.upsertWalletState(walletToPush)
                            if (claimedQuests.isNotEmpty()) {
                                dao.insertClaimedQuests(claimedQuests.map { ClaimedQuestEntity(it) })
                            }
                        }
                    }
                }
                SupabaseDbSyncResult.TableNotProvisioned -> {
                    _syncNotice.value = "Cloud sync unavailable — saving locally."
                    return
                }
                is SupabaseDbSyncResult.Error -> {
                    _syncNotice.value = verifyRes.message
                    return
                }
            }
        }

        if (walletToPush != null) {
            when (val res = supabaseClient.upsertProfile(session, walletToPush, claimedQuests, recentTxs)) {
                is SupabaseDbSyncResult.Success -> {
                    verifiedRemoteUserId = session.userId
                    _syncNotice.value = null
                }
                SupabaseDbSyncResult.TableNotProvisioned -> {
                    _syncNotice.value = "Cloud sync unavailable — saving locally."
                }
                is SupabaseDbSyncResult.Error -> {
                    _syncNotice.value = res.message
                }
            }
        }
    }
}
