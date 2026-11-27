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
    private val sessionStore: SupabaseSessionStore,
    private val coinRepository: CoinRepository
) {
    private val dao = database.clipCashDao()
    private val syncMutex = Mutex()
    private var verifiedRemoteUserId: String? = null
    private var syncedPublicUserId: String? = null
    private var lastKnownServerWalletBalance: Int? = null
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
                syncMutex.withLock {
                    sessionStore.saveSession(outcome.updatedSession)
                    val currentWallet = dao.getWalletState()
                    if (currentWallet != null) {
                        val updatedWallet = currentWallet.copy(
                            userId = outcome.updatedSession.userId,
                            userEmail = outcome.newEmail
                        )
                        dao.upsertWalletState(updatedWallet)
                        val claimed = dao.getClaimedQuestIdsSync().toSet()
                        val txs = dao.getRecentTransactions(25)
                        val progress = dao.getAllClipProgressSync()
                        val coinStatus = coinRepository.getStatus()
                        val adsDate = coinRepository.getSyncSnapshot().adsWatchedDate.ifBlank {
                            if (coinStatus.adsWatchedToday > 0) todayDateKey() else ""
                        }
                        val pushRes = supabaseClient.upsertProfile(
                            session = outcome.updatedSession,
                            wallet = updatedWallet,
                            claimedQuestIds = claimed,
                            recentTransactions = txs,
                            clipProgress = progress,
                            coinStatus = coinStatus,
                            adsWatchedDate = adsDate,
                            lastServerWalletBalance = lastKnownServerWalletBalance,
                            ensurePublicUserRow = true
                        )
                        if (pushRes is SupabaseDbSyncResult.Success) {
                            verifiedRemoteUserId = outcome.updatedSession.userId
                            syncedPublicUserId = outcome.updatedSession.userId
                            coinRepository.markCloudPushSucceeded(
                                userId = outcome.updatedSession.userId,
                                email = outcome.newEmail,
                                syncedBalance = updatedWallet.coinBalance
                            )
                            _syncNotice.value = null
                        } else {
                            coinRepository.markCloudPushPending(outcome.updatedSession.userId, outcome.newEmail)
                        }
                    }
                }
            }
        }
        return result
    }

    suspend fun signOut() {
        verifiedRemoteUserId = null
        syncedPublicUserId = null
        lastKnownServerWalletBalance = null
        supabaseClient.signOut()
        coinRepository.clearForSignOut()
        clearLocalCache()
        _syncNotice.value = null
    }

    suspend fun hasPendingCloudSync(): Boolean = coinRepository.getSyncSnapshot().pendingCloudPush

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
            syncedPublicUserId = null
            lastKnownServerWalletBalance = null
        }

        val preSyncSnapshot = coinRepository.getSyncSnapshot()
        val legacyAdCoins = coinRepository.consumeLegacyUnmigratedAdCoins(
            userId = activeSession.userId,
            email = activeSession.email
        )

        when (val profileRes = supabaseClient.fetchProfile(activeSession)) {
            is SupabaseDbSyncResult.Success -> {
                val remoteProfile = profileRes.data
                if (remoteProfile != null) {
                    lastKnownServerWalletBalance = remoteProfile.serverWalletBalance
                    if (remoteProfile.hasPublicUsersRow) {
                        syncedPublicUserId = activeSession.userId
                    }
                    hydratedSession = activeSession.copy(
                        email = remoteProfile.email.ifBlank { activeSession.email },
                        fullName = remoteProfile.fullName.ifBlank { activeSession.fullName },
                        username = remoteProfile.username.ifBlank { activeSession.username },
                        nationality = remoteProfile.nationality.ifBlank { activeSession.nationality },
                        phoneNumber = remoteProfile.phoneNumber.ifBlank { activeSession.phoneNumber }
                    )

                    val postConsumeWallet = dao.getWalletState()?.takeIf { it.userId == activeSession.userId }
                    val localTxsForUser = if (postConsumeWallet != null) {
                        dao.getRecentTransactions(40)
                    } else {
                        emptyList()
                    }
                    val localQuestsForUser = if (postConsumeWallet != null) {
                        dao.getClaimedQuestIdsSync().toSet()
                    } else {
                        emptySet()
                    }
                    val hasPendingOfflineChanges = postConsumeWallet != null &&
                        preSyncSnapshot.pendingCloudPush &&
                        (preSyncSnapshot.ownerUserId.isBlank() || preSyncSnapshot.ownerUserId == activeSession.userId)
                    val shouldAdoptLocalUnsynced = postConsumeWallet != null &&
                        (!remoteProfile.hasWalletsRow &&
                            remoteProfile.wallet.coinBalance == 0 &&
                            remoteProfile.wallet.clipsWatchedCount == 0 &&
                            postConsumeWallet.clipsWatchedCount > 0)

                    val mergedBalance = when {
                        postConsumeWallet != null && hasPendingOfflineChanges -> {
                            val baseBalance = preSyncSnapshot.lastSyncedBalance ?: 150
                            val offlineDelta = postConsumeWallet.coinBalance - baseBalance
                            (remoteProfile.wallet.coinBalance + offlineDelta + legacyAdCoins).coerceAtLeast(0)
                        }
                        postConsumeWallet != null && shouldAdoptLocalUnsynced -> {
                            (maxOf(remoteProfile.wallet.coinBalance, postConsumeWallet.coinBalance) + legacyAdCoins).coerceAtLeast(0)
                        }
                        else -> (remoteProfile.wallet.coinBalance + legacyAdCoins).coerceAtLeast(0)
                    }
                    val localTotalEarned = postConsumeWallet?.totalEarnedCoins ?: 0
                    val localEarnedDelta = if (hasPendingOfflineChanges) {
                        (localTotalEarned - (preSyncSnapshot.lastSyncedBalance ?: 150)).coerceAtLeast(0)
                    } else {
                        0
                    }
                    val syncedWallet = remoteProfile.wallet.copy(
                        userId = activeSession.userId,
                        userEmail = hydratedSession.email.ifBlank { remoteProfile.wallet.userEmail },
                        coinBalance = mergedBalance,
                        totalEarnedCoins = maxOf(
                            remoteProfile.wallet.totalEarnedCoins + localEarnedDelta + legacyAdCoins,
                            if (shouldAdoptLocalUnsynced) localTotalEarned + legacyAdCoins else 0,
                            mergedBalance
                        ),
                        streakDays = maxOf(
                            remoteProfile.wallet.streakDays,
                            postConsumeWallet?.streakDays ?: 0
                        ),
                        lastCheckInDate = remoteProfile.wallet.lastCheckInDate.ifBlank {
                            postConsumeWallet?.lastCheckInDate.orEmpty()
                        },
                        lastFreeSpinDate = remoteProfile.wallet.lastFreeSpinDate.ifBlank {
                            postConsumeWallet?.lastFreeSpinDate.orEmpty()
                        },
                        clipsWatchedCount = maxOf(
                            remoteProfile.wallet.clipsWatchedCount,
                            postConsumeWallet?.clipsWatchedCount ?: 0
                        ),
                        gamesPlayedCount = maxOf(
                            remoteProfile.wallet.gamesPlayedCount,
                            postConsumeWallet?.gamesPlayedCount ?: 0
                        )
                    )
                    val mergedQuests = remoteProfile.claimedQuestIds + localQuestsForUser
                    val legacyTx = if (legacyAdCoins > 0) {
                        listOf(
                            TransactionEntity(
                                title = "Synced Ad Coin Balance",
                                subtitle = "Migrated prior ad rewards to cloud",
                                amountCoins = legacyAdCoins,
                                type = TransactionType.BONUS.name
                            )
                        )
                    } else {
                        emptyList()
                    }
                    val mergedTxs = (legacyTx + remoteProfile.recentTransactions + localTxsForUser)
                        .distinctBy { "${it.createdAt}_${it.title}_${it.amountCoins}" }
                        .sortedByDescending { it.createdAt }
                        .take(40)

                    val localProgressForUser = if (postConsumeWallet != null) {
                        dao.getAllClipProgressSync()
                    } else {
                        emptyList()
                    }
                    val mergedProgress = buildList {
                        val byId = LinkedHashMap<String, ClipProgressEntity>()
                        for (prog in remoteProfile.clipProgress + localProgressForUser) {
                            val prev = byId[prog.clipId]
                            byId[prog.clipId] = if (prev == null) {
                                prog
                            } else {
                                ClipProgressEntity(
                                    clipId = prog.clipId,
                                    watchCount = maxOf(prev.watchCount, prog.watchCount),
                                    lastWatchedAt = maxOf(prev.lastWatchedAt, prog.lastWatchedAt),
                                    earnedCoinsTotal = maxOf(prev.earnedCoinsTotal, prog.earnedCoinsTotal)
                                )
                            }
                        }
                        addAll(byId.values)
                    }

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
                        dao.clearClipProgress()
                        for (prog in mergedProgress) {
                            dao.upsertClipProgress(prog)
                        }
                        backfillClipProgressFromTransactions(displayTxs)
                        dao.clearClaimedQuests()
                        if (mergedQuests.isNotEmpty()) {
                            dao.insertClaimedQuests(mergedQuests.map { ClaimedQuestEntity(it) })
                        }
                    }

                    coinRepository.applySyncedCloudState(
                        userId = hydratedSession.userId,
                        email = hydratedSession.email,
                        balance = syncedWallet.coinBalance,
                        adsWatchedToday = remoteProfile.adsWatchedToday,
                        adsWatchedDate = remoteProfile.adsWatchedDate,
                        lastRewardedAtMs = remoteProfile.lastRewardedAtMs,
                        lastSyncedBalance = syncedWallet.coinBalance,
                        pendingCloudPush = hasPendingOfflineChanges || legacyAdCoins > 0
                    )

                    val needsPush = hasPendingOfflineChanges ||
                        shouldAdoptLocalUnsynced ||
                        legacyAdCoins > 0 ||
                        !remoteProfile.hasPublicUsersRow
                    if (needsPush) {
                        val currentCoinStatus = coinRepository.getStatus()
                        val currentProgress = dao.getAllClipProgressSync()
                        val pushAdsDate = coinRepository.getSyncSnapshot().adsWatchedDate.ifBlank {
                            if (currentCoinStatus.adsWatchedToday > 0) todayDateKey() else remoteProfile.adsWatchedDate
                        }
                        val pushRes = supabaseClient.upsertProfile(
                            session = hydratedSession,
                            wallet = syncedWallet,
                            claimedQuestIds = mergedQuests,
                            recentTransactions = mergedTxs,
                            clipProgress = currentProgress,
                            coinStatus = currentCoinStatus,
                            adsWatchedDate = pushAdsDate,
                            lastServerWalletBalance = lastKnownServerWalletBalance,
                            ensurePublicUserRow = !remoteProfile.hasPublicUsersRow
                        )
                        when (pushRes) {
                            is SupabaseDbSyncResult.Success -> {
                                verifiedRemoteUserId = activeSession.userId
                                syncedPublicUserId = activeSession.userId
                                coinRepository.markCloudPushSucceeded(
                                    userId = hydratedSession.userId,
                                    email = hydratedSession.email,
                                    syncedBalance = syncedWallet.coinBalance
                                )
                            }
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
                    syncNotice = ensureInitializedAndPush(activeSession, legacyAdCoins)
                    if (syncNotice == null) {
                        verifiedRemoteUserId = activeSession.userId
                    }
                }
            }
            SupabaseDbSyncResult.TableNotProvisioned -> {
                ensureInitializedLocalOnly(activeSession, legacyAdCoins)
                syncNotice = "Cloud sync unavailable — saving locally."
            }
            is SupabaseDbSyncResult.Error -> {
                ensureInitializedLocalOnly(activeSession, legacyAdCoins)
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

    private suspend fun ensureInitializedLocalOnly(session: SupabaseSession, legacyAdCoins: Int = 0) {
        var resolvedWallet: WalletStateEntity? = null
        database.withTransaction {
            val existing = dao.getWalletState()
            if (existing == null) {
                val initial = WalletStateEntity(
                    userId = session.userId,
                    userEmail = session.email,
                    coinBalance = 150 + legacyAdCoins,
                    totalEarnedCoins = 150 + legacyAdCoins
                )
                dao.upsertWalletState(initial)
                dao.insertTransaction(
                    TransactionEntity(
                        title = "Welcome Bonus",
                        subtitle = "Starter reward for joining ClipCash",
                        amountCoins = 150,
                        type = TransactionType.BONUS.name
                    )
                )
                if (legacyAdCoins > 0) {
                    dao.insertTransaction(
                        TransactionEntity(
                            title = "Synced Ad Coin Balance",
                            subtitle = "Migrated prior ad rewards",
                            amountCoins = legacyAdCoins,
                            type = TransactionType.BONUS.name
                        )
                    )
                }
                resolvedWallet = initial
            } else if (legacyAdCoins > 0) {
                val updated = existing.copy(
                    userId = session.userId,
                    userEmail = session.email,
                    coinBalance = existing.coinBalance + legacyAdCoins,
                    totalEarnedCoins = existing.totalEarnedCoins + legacyAdCoins
                )
                dao.upsertWalletState(updated)
                dao.insertTransaction(
                    TransactionEntity(
                        title = "Synced Ad Coin Balance",
                        subtitle = "Migrated prior ad rewards",
                        amountCoins = legacyAdCoins,
                        type = TransactionType.BONUS.name
                    )
                )
                resolvedWallet = updated
            } else {
                resolvedWallet = existing
            }
        }
        val wallet = resolvedWallet ?: return
        val status = coinRepository.getStatus()
        val adsDate = coinRepository.getSyncSnapshot().adsWatchedDate.ifBlank {
            if (status.adsWatchedToday > 0) todayDateKey() else ""
        }
        coinRepository.applySyncedCloudState(
            userId = session.userId,
            email = session.email,
            balance = wallet.coinBalance,
            adsWatchedToday = status.adsWatchedToday,
            adsWatchedDate = adsDate,
            lastRewardedAtMs = status.lastRewardedAtMs,
            lastSyncedBalance = 150,
            pendingCloudPush = true
        )
    }

    private suspend fun ensureInitializedAndPush(session: SupabaseSession, legacyAdCoins: Int = 0): String? {
        val welcomeTx = TransactionEntity(
            title = "Welcome Bonus",
            subtitle = "Starter reward for joining ClipCash",
            amountCoins = 150,
            type = TransactionType.BONUS.name
        )
        val initialWallet = WalletStateEntity(
            userId = session.userId,
            userEmail = session.email,
            coinBalance = 150 + legacyAdCoins,
            totalEarnedCoins = 150 + legacyAdCoins
        )
        database.withTransaction {
            val existing = dao.getWalletState()
            if (existing == null) {
                dao.upsertWalletState(initialWallet)
                dao.insertTransaction(welcomeTx)
                if (legacyAdCoins > 0) {
                    dao.insertTransaction(
                        TransactionEntity(
                            title = "Synced Ad Coin Balance",
                            subtitle = "Migrated prior ad rewards to cloud",
                            amountCoins = legacyAdCoins,
                            type = TransactionType.BONUS.name
                        )
                    )
                }
            } else {
                val updated = existing.copy(
                    userId = session.userId,
                    userEmail = session.email,
                    coinBalance = existing.coinBalance + legacyAdCoins,
                    totalEarnedCoins = existing.totalEarnedCoins + legacyAdCoins
                )
                dao.upsertWalletState(updated)
                if (legacyAdCoins > 0) {
                    dao.insertTransaction(
                        TransactionEntity(
                            title = "Synced Ad Coin Balance",
                            subtitle = "Migrated prior ad rewards to cloud",
                            amountCoins = legacyAdCoins,
                            type = TransactionType.BONUS.name
                        )
                    )
                }
            }
        }
        val currentWallet = dao.getWalletState() ?: initialWallet
        val claimed = dao.getClaimedQuestIdsSync().toSet()
        val recentTxs = dao.getRecentTransactions(25)
        val currentProgress = dao.getAllClipProgressSync()
        val status = coinRepository.getStatus()
        val adsDate = coinRepository.getSyncSnapshot().adsWatchedDate.ifBlank {
            if (status.adsWatchedToday > 0) todayDateKey() else ""
        }
        return when (
            val profilePush = supabaseClient.upsertProfile(
                session = session,
                wallet = currentWallet,
                claimedQuestIds = claimed,
                recentTransactions = recentTxs,
                clipProgress = currentProgress,
                coinStatus = status,
                adsWatchedDate = adsDate,
                lastServerWalletBalance = lastKnownServerWalletBalance,
                ensurePublicUserRow = true
            )
        ) {
            is SupabaseDbSyncResult.Success -> {
                syncedPublicUserId = session.userId
                coinRepository.applySyncedCloudState(
                    userId = session.userId,
                    email = session.email,
                    balance = currentWallet.coinBalance,
                    adsWatchedToday = status.adsWatchedToday,
                    adsWatchedDate = adsDate,
                    lastRewardedAtMs = status.lastRewardedAtMs,
                    lastSyncedBalance = currentWallet.coinBalance,
                    pendingCloudPush = false
                )
                null
            }
            SupabaseDbSyncResult.TableNotProvisioned -> {
                coinRepository.markCloudPushPending(session.userId, session.email)
                "Cloud sync unavailable — saving locally."
            }
            is SupabaseDbSyncResult.Error -> {
                coinRepository.markCloudPushPending(session.userId, session.email)
                profilePush.message
            }
        }
    }

    private suspend fun getOrCreateWalletForSession(session: SupabaseSession?): WalletStateEntity {
        val existing = dao.getWalletState()
        if (existing != null) {
            val uid = existing.userId.ifBlank { session?.userId.orEmpty() }
            val email = session?.email?.takeIf { it.isNotBlank() } ?: existing.userEmail
            return if (uid != existing.userId || email != existing.userEmail) {
                existing.copy(userId = uid, userEmail = email)
            } else {
                existing
            }
        }
        return WalletStateEntity(
            userId = session?.userId.orEmpty(),
            userEmail = session?.email.orEmpty()
        )
    }

    suspend fun claimDailyCheckIn(): Int {
        val session = sessionStore.getSession()
        val today = todayDateKey()
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val bonus = database.withTransaction {
            val current = getOrCreateWalletForSession(session)
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
        val session = sessionStore.getSession()
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val reward = database.withTransaction {
            val current = getOrCreateWalletForSession(session)
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
        val session = sessionStore.getSession()
        val safeCoins = coinsWon.coerceAtLeast(0)
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        database.withTransaction {
            val current = getOrCreateWalletForSession(session)
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
        val session = sessionStore.getSession()
        val today = todayDateKey()
        val spinCost = if (usedFreeSpin) 0 else 25
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val result = database.withTransaction {
            val current = getOrCreateWalletForSession(session)
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
        val session = sessionStore.getSession()
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val earned = database.withTransaction {
            if (dao.getClaimedQuest(quest.id) != null) return@withTransaction 0
            val current = getOrCreateWalletForSession(session)
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
        val session = sessionStore.getSession()
        val cleanNote = note.trim()
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        val result = database.withTransaction {
            val current = getOrCreateWalletForSession(session)
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

    suspend fun recordAdReward(
        title: String,
        subtitle: String,
        coins: Int,
        coinStatus: CoinRewardStatus
    ): Int {
        if (coins <= 0) return 0
        val session = sessionStore.getSession()
        var newWallet: WalletStateEntity? = null
        var newTx: TransactionEntity? = null

        database.withTransaction {
            val current = getOrCreateWalletForSession(session)
            val updated = current.copy(
                coinBalance = current.coinBalance + coins,
                totalEarnedCoins = current.totalEarnedCoins + coins
            )
            val tx = TransactionEntity(
                title = title,
                subtitle = subtitle,
                amountCoins = coins,
                type = TransactionType.BONUS.name
            )
            dao.upsertWalletState(updated)
            dao.insertTransaction(tx)
            newWallet = updated
            newTx = tx
        }

        pushWalletAndTx(newWallet, newTx, coinStatusOverride = coinStatus)
        return coins
    }

    private suspend fun pushWalletAndTx(
        wallet: WalletStateEntity?,
        tx: TransactionEntity?,
        coinStatusOverride: CoinRewardStatus? = null
    ) = syncMutex.withLock {
        val session = sessionStore.getSession() ?: return@withLock
        coinRepository.markCloudPushPending(session.userId, session.email)

        var walletToPush = wallet?.copy(
            userId = session.userId,
            userEmail = session.email
        )
        var claimedQuests = dao.getClaimedQuestIdsSync().toSet()
        var recentTxs = dao.getRecentTransactions(25)
        var allProgress = dao.getAllClipProgressSync()
        var effectiveCoinStatus = coinStatusOverride ?: coinRepository.getStatus()
        val syncSnapshot = coinRepository.getSyncSnapshot()
        var effectiveAdsDate = syncSnapshot.adsWatchedDate.ifBlank {
            if (effectiveCoinStatus.adsWatchedToday > 0) todayDateKey() else ""
        }

        if (walletToPush != null && verifiedRemoteUserId != session.userId) {
            when (val verifyRes = supabaseClient.fetchProfile(session)) {
                is SupabaseDbSyncResult.Success -> {
                    val remoteProfile = verifyRes.data
                    val remote = remoteProfile?.wallet
                    verifiedRemoteUserId = session.userId
                    if (remote != null) {
                        if (remoteProfile.hasPublicUsersRow) {
                            syncedPublicUserId = session.userId
                        }
                        lastKnownServerWalletBalance = remoteProfile.serverWalletBalance
                        val baseBalance = if (syncSnapshot.ownerUserId == session.userId && syncSnapshot.lastSyncedBalance != null) {
                            syncSnapshot.lastSyncedBalance
                        } else {
                            150
                        }
                        val deltaBalance = walletToPush.coinBalance - baseBalance
                        val deltaTotal = (walletToPush.totalEarnedCoins - baseBalance).coerceAtLeast(0)
                        walletToPush = remote.copy(
                            userId = session.userId,
                            userEmail = remoteProfile.email.ifBlank { session.email.ifBlank { remote.userEmail } },
                            coinBalance = (remote.coinBalance + deltaBalance).coerceAtLeast(0),
                            totalEarnedCoins = maxOf(
                                remote.totalEarnedCoins + deltaTotal,
                                (remote.coinBalance + deltaBalance).coerceAtLeast(0)
                            ),
                            clipsWatchedCount = maxOf(remote.clipsWatchedCount, walletToPush.clipsWatchedCount),
                            gamesPlayedCount = maxOf(remote.gamesPlayedCount, walletToPush.gamesPlayedCount),
                            streakDays = maxOf(remote.streakDays, walletToPush.streakDays),
                            lastCheckInDate = walletToPush.lastCheckInDate.ifBlank { remote.lastCheckInDate },
                            lastFreeSpinDate = walletToPush.lastFreeSpinDate.ifBlank { remote.lastFreeSpinDate }
                        )
                        claimedQuests = remoteProfile.claimedQuestIds + claimedQuests
                        recentTxs = (recentTxs + remoteProfile.recentTransactions)
                            .distinctBy { "${it.createdAt}_${it.title}_${it.amountCoins}" }
                            .sortedByDescending { it.createdAt }
                            .take(40)
                        val progressById = LinkedHashMap<String, ClipProgressEntity>()
                        for (prog in remoteProfile.clipProgress + allProgress) {
                            val prev = progressById[prog.clipId]
                            progressById[prog.clipId] = if (prev == null) {
                                prog
                            } else {
                                ClipProgressEntity(
                                    clipId = prog.clipId,
                                    watchCount = maxOf(prev.watchCount, prog.watchCount),
                                    lastWatchedAt = maxOf(prev.lastWatchedAt, prog.lastWatchedAt),
                                    earnedCoinsTotal = maxOf(prev.earnedCoinsTotal, prog.earnedCoinsTotal)
                                )
                            }
                        }
                        allProgress = progressById.values.toList()
                        val today = todayDateKey()
                        val mergedAdsToday = if (remoteProfile.adsWatchedDate == today) {
                            maxOf(remoteProfile.adsWatchedToday, effectiveCoinStatus.adsWatchedToday)
                        } else {
                            effectiveCoinStatus.adsWatchedToday
                        }
                        val mergedLastRewarded = maxOf(remoteProfile.lastRewardedAtMs, effectiveCoinStatus.lastRewardedAtMs)
                        effectiveAdsDate = if (mergedAdsToday > 0) today else remoteProfile.adsWatchedDate
                        effectiveCoinStatus = effectiveCoinStatus.copy(
                            balance = walletToPush.coinBalance,
                            adsWatchedToday = mergedAdsToday,
                            lastRewardedAtMs = mergedLastRewarded
                        )
                        database.withTransaction {
                            dao.upsertWalletState(walletToPush)
                            for (prog in allProgress) {
                                dao.upsertClipProgress(prog)
                            }
                            if (claimedQuests.isNotEmpty()) {
                                dao.insertClaimedQuests(claimedQuests.map { ClaimedQuestEntity(it) })
                            }
                        }
                    }
                }
                SupabaseDbSyncResult.TableNotProvisioned -> {
                    _syncNotice.value = "Cloud sync unavailable — saving locally."
                    return@withLock
                }
                is SupabaseDbSyncResult.Error -> {
                    _syncNotice.value = verifyRes.message
                    return@withLock
                }
            }
        }

        if (walletToPush != null) {
            val needPublicRow = syncedPublicUserId != session.userId
            when (
                val res = supabaseClient.upsertProfile(
                    session = session,
                    wallet = walletToPush,
                    claimedQuestIds = claimedQuests,
                    recentTransactions = recentTxs,
                    clipProgress = allProgress,
                    coinStatus = effectiveCoinStatus,
                    adsWatchedDate = effectiveAdsDate,
                    lastServerWalletBalance = lastKnownServerWalletBalance,
                    ensurePublicUserRow = needPublicRow
                )
            ) {
                is SupabaseDbSyncResult.Success -> {
                    verifiedRemoteUserId = session.userId
                    if (needPublicRow) {
                        syncedPublicUserId = session.userId
                    }
                    coinRepository.applySyncedCloudState(
                        userId = session.userId,
                        email = walletToPush.userEmail.ifBlank { session.email },
                        balance = walletToPush.coinBalance,
                        adsWatchedToday = effectiveCoinStatus.adsWatchedToday,
                        adsWatchedDate = effectiveAdsDate,
                        lastRewardedAtMs = effectiveCoinStatus.lastRewardedAtMs,
                        lastSyncedBalance = walletToPush.coinBalance,
                        pendingCloudPush = false
                    )
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
