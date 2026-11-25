package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ClipCashDatabase
import com.example.data.ClipCashRepository
import com.example.data.ClipProgressEntity
import com.example.data.EmailChangeVerifyResult
import com.example.data.SupabaseClient
import com.example.data.SupabaseSession
import com.example.data.SupabaseSessionStore
import com.example.data.SupabaseSignInResult
import com.example.data.SupabaseSignUpResult
import com.example.data.TransactionEntity
import com.example.data.WalletStateEntity
import com.example.model.ClipCatalog
import com.example.model.ClipCategory
import com.example.model.NationalityOption
import com.example.model.QuestItem
import com.example.model.QuestMetric
import com.example.model.RewardOption
import com.example.model.TransactionType
import com.example.model.VideoClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuestProgressUi(
    val quest: QuestItem,
    val currentValue: Int,
    val isClaimed: Boolean
) {
    val isReadyToClaim: Boolean get() = !isClaimed && currentValue >= quest.targetValue
    val progressFraction: Float get() = (currentValue.toFloat() / quest.targetValue.toFloat()).coerceIn(0f, 1f)
}

data class RewardFeedback(
    val title: String,
    val subtitle: String,
    val coinsDelta: Int
)

enum class AuthStage {
    SIGN_IN,
    SIGN_UP,
    VERIFY_SIGN_IN,
    VERIFY_SIGN_UP,
    FORGOT_PASSWORD,
    VERIFY_RESET_PASSWORD
}

enum class ChangeEmailStage {
    ENTER_NEW_EMAIL,
    VERIFY_CODE
}

data class AuthUiState(
    val session: SupabaseSession? = null,
    val stage: AuthStage = AuthStage.SIGN_IN,
    val pendingEmail: String = "",
    val signUpUsesOtpFallback: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

data class ChangeEmailUiState(
    val stage: ChangeEmailStage = ChangeEmailStage.ENTER_NEW_EMAIL,
    val desiredNewEmail: String = "",
    val emailBeingVerified: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

data class ClipCashUiState(
    val auth: AuthUiState = AuthUiState(),
    val changeEmail: ChangeEmailUiState = ChangeEmailUiState(),
    val wallet: WalletStateEntity = WalletStateEntity(),
    val selectedCategory: ClipCategory = ClipCategory.ALL,
    val searchQuery: String = "",
    val clips: List<VideoClip> = ClipCatalog.fallbackClips,
    val isSearchingClips: Boolean = false,
    val isLoadingMoreClips: Boolean = false,
    val searchError: String? = null,
    val dbSyncNotice: String? = null,
    val clipProgress: Map<String, ClipProgressEntity> = emptyMap(),
    val quests: List<QuestProgressUi> = emptyList(),
    val rewards: List<RewardOption> = ClipCatalog.rewards,
    val transactions: List<TransactionEntity> = emptyList(),
    val isCheckInAvailable: Boolean = true,
    val isFreeSpinAvailable: Boolean = true,
    val feedbackBanner: RewardFeedback? = null,
    val errorMessage: String? = null
) {
    val estimatedUsdValue: Double
        get() = ClipCatalog.coinsToUsd(wallet.coinBalance)
}

private data class FeedSearchState(
    val category: ClipCategory = ClipCategory.ALL,
    val query: String = "",
    val clips: List<VideoClip> = ClipCatalog.fallbackClips,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val pageIndex: Int = 0,
    val nextPageToken: String? = null,
    val error: String? = null
)

class ClipCashViewModel(private val repository: ClipCashRepository) : ViewModel() {

    private val authStage = MutableStateFlow(AuthStage.SIGN_IN)
    private val authPendingEmail = MutableStateFlow("")
    private val authSignUpOtpFallback = MutableStateFlow(false)
    private val authLoading = MutableStateFlow(false)
    private val authError = MutableStateFlow<String?>(null)
    private val authInfo = MutableStateFlow<String?>(null)
    private val changeEmailState = MutableStateFlow(ChangeEmailUiState())

    private val feedState = MutableStateFlow(FeedSearchState())
    private val feedbackBanner = MutableStateFlow<RewardFeedback?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private var lastSyncedUserId: String? = null
    private var lastSyncAtMs: Long = 0L
    private var feedGeneration: Int = 0
    private var activeWatchSessionId: String? = null
    private var startingSessionForClipId: String? = null

    init {
        viewModelScope.launch {
            repository.authSessionFlow.collect { session ->
                if (session != null) {
                    if (lastSyncedUserId != session.userId) {
                        lastSyncedUserId = session.userId
                        if (!repository.isUserSynced(session.userId)) {
                            repository.syncFromSupabase(session)
                        }
                        lastSyncAtMs = System.currentTimeMillis()
                    }
                    if (feedState.value.clips == ClipCatalog.fallbackClips) {
                        fetchClips(query = feedState.value.query, category = feedState.value.category)
                    }
                } else {
                    lastSyncedUserId = null
                    lastSyncAtMs = 0L
                }
            }
        }
    }

    fun refreshWalletSync() {
        viewModelScope.launch {
            val notice = repository.syncFromSupabase()
            lastSyncAtMs = System.currentTimeMillis()
            if (notice == null) {
                val email = uiState.value.auth.session?.email
                feedbackBanner.value = RewardFeedback(
                    title = "Wallet Synced!",
                    subtitle = if (!email.isNullOrBlank()) "Balance synced to $email" else "Synced with ClipCash Cloud",
                    coinsDelta = 0
                )
            }
        }
    }

    fun refreshWalletSyncIfStale() {
        val now = System.currentTimeMillis()
        if (lastSyncedUserId != null && now - lastSyncAtMs >= 15_000L) {
            lastSyncAtMs = now
            viewModelScope.launch {
                repository.syncFromSupabase()
            }
        }
    }

    private val authUiStateFlow = combine(
        repository.authSessionFlow,
        combine(authStage, authPendingEmail, authSignUpOtpFallback) { stage, pending, fallback ->
            Triple(stage, pending, fallback)
        },
        combine(authLoading, authError, authInfo) { loading, err, info ->
            Triple(loading, err, info)
        }
    ) { session, flowMeta, status ->
        val (stage, pendingEmail, fallback) = flowMeta
        val (loading, err, info) = status
        AuthUiState(
            session = session,
            stage = stage,
            pendingEmail = pendingEmail,
            signUpUsesOtpFallback = fallback,
            isLoading = loading,
            errorMessage = err,
            infoMessage = info
        )
    }

    private val walletBundleFlow = combine(
        repository.walletState,
        repository.clipProgressMap,
        repository.transactions,
        repository.claimedQuestIds
    ) { wallet, progressMap, txs, claimedIds ->
        val today = repository.todayDateKey()
        val questsUi = ClipCatalog.quests.map { quest ->
            val currentVal = when (quest.metric) {
                QuestMetric.CLIPS_WATCHED -> wallet.clipsWatchedCount
                QuestMetric.GAMES_PLAYED -> wallet.gamesPlayedCount
                QuestMetric.TOTAL_COINS_EARNED -> wallet.totalEarnedCoins
                QuestMetric.DAILY_CHECKIN -> if (wallet.lastCheckInDate == today) 1 else 0
            }
            QuestProgressUi(
                quest = quest,
                currentValue = currentVal.coerceAtMost(quest.targetValue),
                isClaimed = quest.id in claimedIds
            )
        }
        Triple(wallet, progressMap, Pair(txs, questsUi))
    }

    val uiState: StateFlow<ClipCashUiState> = combine(
        authUiStateFlow,
        changeEmailState,
        walletBundleFlow,
        feedState,
        combine(repository.syncNoticeFlow, feedbackBanner, errorMessage) { sync, banner, err ->
            Triple(sync, banner, err)
        }
    ) { auth, emailChange, bundle, feed, notices ->
        val (wallet, progressMap, txsAndQuests) = bundle
        val (txs, questsUi) = txsAndQuests
        val (syncNotice, banner, err) = notices
        val today = repository.todayDateKey()
        val videoTxsByTitle = txs
            .filter { it.type == TransactionType.VIDEO.name }
            .groupBy { it.title }
        val mergedProgress = buildMap {
            putAll(progressMap)
            for (clip in feed.clips) {
                val matchingTxs = videoTxsByTitle[clip.title].orEmpty()
                if (matchingTxs.isNotEmpty()) {
                    val existing = progressMap[clip.id]
                    val effectiveCount = maxOf(existing?.watchCount ?: 0, matchingTxs.size)
                    put(
                        clip.id,
                        ClipProgressEntity(
                            clipId = clip.id,
                            watchCount = effectiveCount,
                            lastWatchedAt = maxOf(
                                existing?.lastWatchedAt ?: 0L,
                                matchingTxs.maxOf { it.createdAt }
                            ),
                            earnedCoinsTotal = maxOf(
                                existing?.earnedCoinsTotal ?: 0,
                                matchingTxs.sumOf { it.amountCoins }
                            )
                        )
                    )
                }
            }
        }
        ClipCashUiState(
            auth = auth,
            changeEmail = emailChange,
            wallet = wallet,
            selectedCategory = feed.category,
            searchQuery = feed.query,
            clips = feed.clips,
            isSearchingClips = feed.isLoading,
            isLoadingMoreClips = feed.isLoadingMore,
            searchError = feed.error,
            dbSyncNotice = syncNotice,
            clipProgress = mergedProgress,
            quests = questsUi,
            rewards = ClipCatalog.rewards,
            transactions = txs,
            isCheckInAvailable = wallet.lastCheckInDate != today,
            isFreeSpinAvailable = wallet.lastFreeSpinDate != today,
            feedbackBanner = banner,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ClipCashUiState()
    )

    fun setAuthStage(stage: AuthStage) {
        authStage.value = stage
        authError.value = null
        authInfo.value = null
    }

    fun signIn(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || password.length < 6) {
            authError.value = "Enter a valid email and at least 6 characters."
            return
        }
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.signInWithVerification(cleanEmail, password)
                .onSuccess { res ->
                    when (res) {
                        is SupabaseSignInResult.OtpSent -> {
                            authPendingEmail.value = res.email
                            authStage.value = AuthStage.VERIFY_SIGN_IN
                            authInfo.value = res.message
                        }
                        is SupabaseSignInResult.SignUpConfirmationNeeded -> {
                            authPendingEmail.value = res.email
                            authSignUpOtpFallback.value = false
                            authStage.value = AuthStage.VERIFY_SIGN_UP
                            authInfo.value = res.message
                        }
                    }
                }
                .onFailure { authError.value = it.message ?: "Sign in failed" }
            authLoading.value = false
        }
    }

    fun verifySignInCode(code: String) {
        val cleanCode = code.trim()
        val targetEmail = authPendingEmail.value
        if (cleanCode.length < 6) {
            authError.value = "Enter the verification code sent to your email."
            return
        }
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.verifySignInCode(targetEmail, cleanCode)
                .onSuccess {
                    authStage.value = AuthStage.SIGN_IN
                    authPendingEmail.value = ""
                }
                .onFailure { authError.value = it.message ?: "Invalid verification code" }
            authLoading.value = false
        }
    }

    fun resendSignInCode() {
        val targetEmail = authPendingEmail.value
        if (targetEmail.isBlank()) return
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.resendSignInCode(targetEmail)
                .onSuccess { authInfo.value = "A fresh verification code was sent to $targetEmail." }
                .onFailure { authError.value = it.message ?: "Unable to resend code" }
            authLoading.value = false
        }
    }

    fun signUp(
        fullName: String,
        username: String,
        nationalityOption: NationalityOption,
        phoneNumber: String,
        email: String,
        password: String,
        confirmPassword: String
    ) {
        val cleanFullName = fullName.trim()
        val cleanUsername = username.trim().removePrefix("@")
        val rawPhone = phoneNumber.trim()
        val phoneDigits = rawPhone.filter { it.isDigit() }
        val formattedPhone = if (rawPhone.startsWith("+")) {
            "+$phoneDigits"
        } else {
            "${nationalityOption.dialCode.replace("-", "")}${phoneDigits}"
        }
        val cleanEmail = email.trim()

        when {
            cleanFullName.length < 2 -> {
                authError.value = "Please enter your full name."
                return
            }
            !cleanUsername.matches(Regex("^[a-zA-Z0-9._]{3,24}$")) -> {
                authError.value = "Username must be 3-24 characters (letters, numbers, . or _)."
                return
            }
            phoneDigits.length !in 6..15 -> {
                authError.value = "Enter a valid phone number (6-15 digits)."
                return
            }
            cleanEmail.isBlank() || !cleanEmail.contains("@") -> {
                authError.value = "Enter a valid email address."
                return
            }
            password.length < 6 -> {
                authError.value = "Password must be at least 6 characters."
                return
            }
            password != confirmPassword -> {
                authError.value = "Passwords do not match."
                return
            }
        }

        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.signUp(
                fullName = cleanFullName,
                username = cleanUsername,
                nationality = nationalityOption.nationality,
                countryIso = nationalityOption.isoCode,
                phoneNumber = formattedPhone,
                email = cleanEmail,
                password = password
            )
                .onSuccess { res ->
                    if (res is SupabaseSignUpResult.VerificationRequired) {
                        authPendingEmail.value = res.email
                        authSignUpOtpFallback.value = res.usesEmailOtpFallback
                        authStage.value = AuthStage.VERIFY_SIGN_UP
                        authInfo.value = res.message
                    }
                }
                .onFailure { authError.value = it.message ?: "Sign up failed" }
            authLoading.value = false
        }
    }

    fun verifySignUpCode(code: String) {
        val cleanCode = code.trim()
        val targetEmail = authPendingEmail.value
        if (cleanCode.length < 6) {
            authError.value = "Enter the verification code sent to your email."
            return
        }
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.verifySignUpCode(targetEmail, cleanCode, authSignUpOtpFallback.value)
                .onSuccess {
                    authStage.value = AuthStage.SIGN_IN
                    authPendingEmail.value = ""
                    authSignUpOtpFallback.value = false
                }
                .onFailure { authError.value = it.message ?: "Invalid verification code" }
            authLoading.value = false
        }
    }

    fun resendSignUpCode() {
        val targetEmail = authPendingEmail.value
        if (targetEmail.isBlank()) return
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.resendSignUpCode(targetEmail, authSignUpOtpFallback.value)
                .onSuccess { authInfo.value = "Verification code resent to $targetEmail." }
                .onFailure { authError.value = it.message ?: "Unable to resend code" }
            authLoading.value = false
        }
    }

    fun requestPasswordReset(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            authError.value = "Enter a valid email address."
            return
        }
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.requestPasswordReset(cleanEmail)
                .onSuccess {
                    authPendingEmail.value = cleanEmail
                    authStage.value = AuthStage.VERIFY_RESET_PASSWORD
                    authInfo.value = "Password reset verification code sent to $cleanEmail."
                }
                .onFailure { authError.value = it.message ?: "Unable to send reset code" }
            authLoading.value = false
        }
    }

    fun verifyAndResetPassword(code: String, newPassword: String, confirmPassword: String) {
        val cleanCode = code.trim()
        val targetEmail = authPendingEmail.value
        when {
            cleanCode.length < 6 -> {
                authError.value = "Enter the verification code sent to $targetEmail."
                return
            }
            newPassword.length < 6 -> {
                authError.value = "New password must be at least 6 characters."
                return
            }
            newPassword != confirmPassword -> {
                authError.value = "New passwords do not match."
                return
            }
        }
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.verifyAndResetPassword(targetEmail, cleanCode, newPassword)
                .onSuccess {
                    authStage.value = AuthStage.SIGN_IN
                    authPendingEmail.value = ""
                    authInfo.value = "Password updated! Sign in with your new password."
                }
                .onFailure { authError.value = it.message ?: "Password reset failed" }
            authLoading.value = false
        }
    }

    fun resendPasswordResetCode() {
        val targetEmail = authPendingEmail.value
        if (targetEmail.isBlank()) return
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.requestPasswordReset(targetEmail)
                .onSuccess { authInfo.value = "Password reset code resent to $targetEmail." }
                .onFailure { authError.value = it.message ?: "Unable to resend reset code" }
            authLoading.value = false
        }
    }

    fun resetChangeEmailFlow() {
        changeEmailState.value = ChangeEmailUiState()
    }

    fun clearChangeEmailMessages() {
        changeEmailState.value = changeEmailState.value.copy(
            errorMessage = null,
            infoMessage = null
        )
    }

    fun backToEnterNewEmail() {
        changeEmailState.value = changeEmailState.value.copy(
            stage = ChangeEmailStage.ENTER_NEW_EMAIL,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun requestEmailChange(newEmail: String, currentEmail: String) {
        val cleanNew = newEmail.trim()
        when {
            cleanNew.isBlank() || !cleanNew.contains("@") -> {
                changeEmailState.value = changeEmailState.value.copy(
                    errorMessage = "Enter a valid new email address.",
                    infoMessage = null
                )
                return
            }
            cleanNew.equals(currentEmail.trim(), ignoreCase = true) -> {
                changeEmailState.value = changeEmailState.value.copy(
                    errorMessage = "New email must be different from your current email.",
                    infoMessage = null
                )
                return
            }
        }
        viewModelScope.launch {
            changeEmailState.value = changeEmailState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )
            repository.requestEmailChange(cleanNew)
                .onSuccess {
                    changeEmailState.value = ChangeEmailUiState(
                        stage = ChangeEmailStage.VERIFY_CODE,
                        desiredNewEmail = cleanNew,
                        emailBeingVerified = cleanNew,
                        isLoading = false,
                        infoMessage = "Verification code sent to $cleanNew."
                    )
                }
                .onFailure { err ->
                    changeEmailState.value = changeEmailState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Unable to send email verification code"
                    )
                }
        }
    }

    fun verifyEmailChange(code: String, onCompleted: (String) -> Unit) {
        val cleanCode = code.trim()
        val current = changeEmailState.value
        if (cleanCode.length < 6) {
            changeEmailState.value = current.copy(
                errorMessage = "Enter the verification code sent to ${current.emailBeingVerified}.",
                infoMessage = null
            )
            return
        }
        viewModelScope.launch {
            changeEmailState.value = current.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )
            repository.verifyEmailChange(
                emailToVerify = current.emailBeingVerified,
                desiredNewEmail = current.desiredNewEmail,
                code = cleanCode
            )
                .onSuccess { outcome ->
                    when (outcome) {
                        is EmailChangeVerifyResult.Completed -> {
                            changeEmailState.value = ChangeEmailUiState()
                            feedbackBanner.value = RewardFeedback(
                                title = "Email Address Updated!",
                                subtitle = "Your ClipCash wallet is now linked to ${outcome.newEmail}",
                                coinsDelta = 0
                            )
                            onCompleted(outcome.newEmail)
                        }
                        is EmailChangeVerifyResult.SecondCodeRequired -> {
                            changeEmailState.value = current.copy(
                                emailBeingVerified = outcome.nextEmailToVerify,
                                isLoading = false,
                                errorMessage = null,
                                infoMessage = outcome.message
                            )
                        }
                    }
                }
                .onFailure { err ->
                    changeEmailState.value = changeEmailState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Invalid or expired verification code"
                    )
                }
        }
    }

    fun resendEmailChangeCode() {
        val desired = changeEmailState.value.desiredNewEmail
        if (desired.isBlank()) return
        viewModelScope.launch {
            changeEmailState.value = changeEmailState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )
            repository.requestEmailChange(desired)
                .onSuccess {
                    changeEmailState.value = changeEmailState.value.copy(
                        isLoading = false,
                        infoMessage = "Verification code resent to $desired."
                    )
                }
                .onFailure { err ->
                    changeEmailState.value = changeEmailState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Unable to resend verification code"
                    )
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            lastSyncedUserId = null
            lastSyncAtMs = 0L
            authStage.value = AuthStage.SIGN_IN
            authPendingEmail.value = ""
            authSignUpOtpFallback.value = false
            changeEmailState.value = ChangeEmailUiState()
            feedState.value = FeedSearchState()
            feedbackBanner.value = null
            errorMessage.value = null
            authError.value = null
            authInfo.value = null
        }
    }

    fun clearAuthMessages() {
        authError.value = null
        authInfo.value = null
    }

    fun updateSearchQuery(query: String) {
        feedState.value = feedState.value.copy(query = query)
    }

    fun selectCategory(category: ClipCategory) {
        feedState.value = feedState.value.copy(category = category, query = "")
        fetchClips(query = "", category = category)
    }

    fun submitSearch() {
        val current = feedState.value
        fetchClips(query = current.query, category = current.category)
    }

    fun fetchClips(query: String = feedState.value.query, category: ClipCategory = feedState.value.category) {
        val generation = ++feedGeneration
        viewModelScope.launch {
            feedState.value = feedState.value.copy(
                query = query,
                category = category,
                isLoading = true,
                isLoadingMore = false,
                pageIndex = 0,
                nextPageToken = null,
                error = null
            )
            repository.searchClipsPage(
                query = query,
                category = category,
                pageIndex = 0,
                pageToken = null,
                seenVideoIds = emptySet()
            )
                .onSuccess { page ->
                    if (generation != feedGeneration) return@onSuccess
                    val results = page.clips
                    feedState.value = feedState.value.copy(
                        clips = if (results.isNotEmpty()) results else ClipCatalog.fallbackClips,
                        isLoading = false,
                        pageIndex = 1,
                        nextPageToken = page.nextPageToken,
                        error = if (results.isEmpty()) "No videos matched. Showing featured ClipCash picks." else null
                    )
                    // Proactively fetch a second wave when initial batch has <= 12 Shorts so every category feels rich immediately
                    if (results.isNotEmpty() && results.size <= 12) {
                        loadMoreClips()
                    }
                }
                .onFailure { err ->
                    if (generation != feedGeneration) return@onFailure
                    feedState.value = feedState.value.copy(
                        isLoading = false,
                        error = err.message ?: "Unable to refresh ClipCash feed"
                    )
                }
        }
    }

    fun loadMoreClips() {
        val current = feedState.value
        if (current.isLoading || current.isLoadingMore) return
        val generation = feedGeneration
        val currentSeen = current.clips.map { it.videoId }.toSet()

        viewModelScope.launch {
            feedState.value = feedState.value.copy(isLoadingMore = true)
            repository.searchClipsPage(
                query = current.query,
                category = current.category,
                pageIndex = current.pageIndex,
                pageToken = current.nextPageToken,
                seenVideoIds = currentSeen
            )
                .onSuccess { page ->
                    if (generation != feedGeneration) return@onSuccess
                    val existing = feedState.value.clips
                    val existingIds = existing.mapTo(HashSet()) { it.videoId }
                    val newClips = page.clips.filter { existingIds.add(it.videoId) }
                    feedState.value = feedState.value.copy(
                        clips = existing + newClips,
                        isLoadingMore = false,
                        pageIndex = feedState.value.pageIndex + 1,
                        nextPageToken = page.nextPageToken
                    )
                }
                .onFailure {
                    if (generation != feedGeneration) return@onFailure
                    feedState.value = feedState.value.copy(isLoadingMore = false)
                }
        }
    }

    fun claimDailyCheckIn() {
        viewModelScope.launch {
            val coins = repository.claimDailyCheckIn()
            if (coins > 0) {
                feedbackBanner.value = RewardFeedback(
                    title = "Daily Check-In Claimed!",
                    subtitle = "Keep your streak alive tomorrow for bigger bonuses.",
                    coinsDelta = coins
                )
            }
        }
    }

    fun resetWatchSession() {
        activeWatchSessionId = null
        startingSessionForClipId = null
    }

    fun onVideoPlaying(clip: VideoClip) {
        if (clip.remoteUuid.isNullOrBlank() || !clip.rewardEligible) return
        if (activeWatchSessionId != null || startingSessionForClipId == clip.id) return

        startingSessionForClipId = clip.id
        viewModelScope.launch {
            repository.startWatchSession(clip)
                .onSuccess { sid ->
                    if (!sid.isNullOrBlank()) {
                        activeWatchSessionId = sid
                    }
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Unable to start watch session"
                }
            if (startingSessionForClipId == clip.id) {
                startingSessionForClipId = null
            }
        }
    }

    fun onVideoEnded(clip: VideoClip, onAwarded: (Int) -> Unit = {}) {
        val sid = activeWatchSessionId ?: return
        viewModelScope.launch {
            repository.completeWatchSession(clip, sid)
                .onSuccess { coins ->
                    activeWatchSessionId = null
                    if (coins > 0) {
                        feedbackBanner.value = RewardFeedback(
                            title = "+$coins coins earned!",
                            subtitle = "Earned from \"${clip.title}\"",
                            coinsDelta = coins
                        )
                        onAwarded(coins)
                    } else {
                        feedbackBanner.value = RewardFeedback(
                            title = "Watch Complete",
                            subtitle = "No reward for this video",
                            coinsDelta = 0
                        )
                    }
                }
                .onFailure { err ->
                    // Keep activeWatchSessionId open on "watched too briefly" so user can retry
                    errorMessage.value = err.message ?: "Unable to claim watch reward"
                }
        }
    }

    fun recordClipWatched(clip: VideoClip) {
        if (!clip.remoteUuid.isNullOrBlank() && clip.rewardEligible) {
            onVideoEnded(clip)
            return
        }
        viewModelScope.launch {
            val earned = repository.recordClipWatched(clip)
            feedbackBanner.value = RewardFeedback(
                title = "ClipCash Reward Unlocked!",
                subtitle = "Earned from \"${clip.title}\"",
                coinsDelta = earned
            )
        }
    }

    fun recordCoinCatcherScore(coinsWon: Int) {
        viewModelScope.launch {
            val earned = repository.recordCoinCatcherGame(coinsWon)
            if (earned > 0) {
                feedbackBanner.value = RewardFeedback(
                    title = "Coin Catcher Complete!",
                    subtitle = "Awesome reflexes — coins added to your wallet.",
                    coinsDelta = earned
                )
            }
        }
    }

    fun recordArcadeGameResult(gameTitle: String, coinsWon: Int) {
        viewModelScope.launch {
            val earned = repository.recordArcadeGameReward(gameTitle, coinsWon)
            if (earned > 0) {
                feedbackBanner.value = RewardFeedback(
                    title = "$gameTitle Complete!",
                    subtitle = "Coins added to your ClipCash wallet.",
                    coinsDelta = earned
                )
            }
        }
    }

    fun spinLuckyWheel(prizeCoins: Int, usedFreeSpin: Boolean) {
        viewModelScope.launch {
            repository.spinLuckyWheel(prizeCoins, usedFreeSpin)
                .onSuccess { won ->
                    feedbackBanner.value = RewardFeedback(
                        title = "Lucky Wheel Winner!",
                        subtitle = if (usedFreeSpin) "Free daily spin prize" else "Bonus spin prize",
                        coinsDelta = won
                    )
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Unable to spin right now"
                }
        }
    }

    fun claimQuest(quest: QuestItem) {
        viewModelScope.launch {
            val earned = repository.claimQuest(quest)
            if (earned > 0) {
                feedbackBanner.value = RewardFeedback(
                    title = "Quest Complete!",
                    subtitle = quest.title,
                    coinsDelta = earned
                )
            }
        }
    }

    fun redeemReward(reward: RewardOption, destination: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            repository.redeemReward(reward, destination)
                .onSuccess { refCode ->
                    feedbackBanner.value = RewardFeedback(
                        title = "Payout Requested!",
                        subtitle = "${reward.brand} ${reward.cashLabel} · Ref $refCode",
                        coinsDelta = -reward.coinCost
                    )
                    onSuccess(refCode)
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Redemption failed"
                }
        }
    }

    fun dismissBanner() {
        feedbackBanner.value = null
        errorMessage.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = ClipCashDatabase.getInstance(app)
                val sessionStore = SupabaseSessionStore(app)
                val supabaseClient = SupabaseClient(sessionStore)
                ClipCashViewModel(ClipCashRepository(db, supabaseClient, sessionStore))
            }
        }
    }
}
