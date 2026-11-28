package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ClipCashApp
import com.example.data.ClipCashDatabase
import com.example.data.ClipCashRepository
import com.example.data.ClipProgressEntity
import com.example.data.CoinRepository
import com.example.data.CoinRewardStatus
import com.example.data.DataStoreCoinRepository
import com.example.data.EmailChangeRequestResult
import com.example.data.EmailChangeVerifyResult
import com.example.data.NetworkMonitor
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
    VERIFY_RESET_PASSWORD,
    RESET_NEW_PASSWORD
}

enum class ChangeEmailStage {
    VERIFY_CURRENT_EMAIL,
    ENTER_NEW_EMAIL,
    VERIFY_NEW_EMAIL
}

data class AuthUiState(
    val session: SupabaseSession? = null,
    val stage: AuthStage = AuthStage.SIGN_IN,
    val pendingEmail: String = "",
    val signUpUsesOtpFallback: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isOnline: Boolean = true
)

data class ChangeEmailUiState(
    val stage: ChangeEmailStage = ChangeEmailStage.VERIFY_CURRENT_EMAIL,
    val desiredNewEmail: String = "",
    val emailBeingVerified: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isOnline: Boolean = true
)

data class ClipCashUiState(
    val auth: AuthUiState = AuthUiState(),
    val changeEmail: ChangeEmailUiState = ChangeEmailUiState(),
    val wallet: WalletStateEntity = WalletStateEntity(),
    val coinStatus: CoinRewardStatus = CoinRewardStatus(),
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
    val errorMessage: String? = null,
    val isOnline: Boolean = true
)

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

class ClipCashViewModel(
    private val repository: ClipCashRepository,
    private val coinRepository: CoinRepository,
    private val networkMonitor: NetworkMonitor? = null
) : ViewModel() {

    private val authStage = MutableStateFlow(AuthStage.SIGN_IN)
    private val authPendingEmail = MutableStateFlow("")
    private val authSignUpOtpFallback = MutableStateFlow(false)
    private val authLoading = MutableStateFlow(false)
    private val authError = MutableStateFlow<String?>(null)
    private val authInfo = MutableStateFlow<String?>(null)
    private val changeEmailState = MutableStateFlow(ChangeEmailUiState())
    private val isOnlineFlow: StateFlow<Boolean> = networkMonitor?.isOnline ?: MutableStateFlow(true)

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
            var wasOnline = isOnlineFlow.value
            isOnlineFlow.collect { online ->
                changeEmailState.value = changeEmailState.value.copy(
                    isOnline = online,
                    errorMessage = if (online && changeEmailState.value.errorMessage == NetworkMonitor.OFFLINE_ERROR_MESSAGE) {
                        null
                    } else {
                        changeEmailState.value.errorMessage
                    }
                )
                if (online && !wasOnline) {
                    if (authError.value == NetworkMonitor.OFFLINE_ERROR_MESSAGE) {
                        authError.value = null
                    }
                    if (lastSyncedUserId != null) {
                        refreshWalletSyncIfStale()
                        if (feedState.value.clips == ClipCatalog.fallbackClips) {
                            fetchClips(query = feedState.value.query, category = feedState.value.category)
                        }
                    }
                }
                wasOnline = online
            }
        }
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
        if (lastSyncedUserId == null) return
        viewModelScope.launch {
            val hasPending = repository.hasPendingCloudSync()
            if (hasPending || now - lastSyncAtMs >= 15_000L) {
                lastSyncAtMs = now
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
        },
        isOnlineFlow
    ) { session, flowMeta, status, online ->
        val (stage, pendingEmail, fallback) = flowMeta
        val (loading, err, info) = status
        AuthUiState(
            session = session,
            stage = stage,
            pendingEmail = pendingEmail,
            signUpUsesOtpFallback = fallback,
            isLoading = loading,
            errorMessage = err?.let { SupabaseClient.sanitizeUserErrorMessage(it) },
            infoMessage = info,
            isOnline = online
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

    private val noticesAndCoinsFlow = combine(
        repository.syncNoticeFlow,
        feedbackBanner,
        errorMessage,
        coinRepository.status,
        isOnlineFlow
    ) { sync, banner, err, coinStatus, online ->
        listOf<Any?>(sync, banner, err, coinStatus, online)
    }

    val uiState: StateFlow<ClipCashUiState> = combine(
        authUiStateFlow,
        changeEmailState,
        walletBundleFlow,
        feedState,
        noticesAndCoinsFlow
    ) { auth, emailChange, bundle, feed, meta ->
        val (wallet, progressMap, txsAndQuests) = bundle
        val (txs, questsUi) = txsAndQuests
        val syncNotice = (meta[0] as String?)?.let { SupabaseClient.sanitizeUserErrorMessage(it) }
        val banner = meta[1] as RewardFeedback?
        val err = (meta[2] as String?)?.let { SupabaseClient.sanitizeUserErrorMessage(it) }
        val coinStatus = meta[3] as CoinRewardStatus
        val online = meta[4] as Boolean
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
            changeEmail = emailChange.copy(isOnline = online),
            wallet = wallet,
            coinStatus = coinStatus,
            selectedCategory = feed.category,
            searchQuery = feed.query,
            clips = feed.clips,
            isSearchingClips = feed.isLoading,
            isLoadingMoreClips = feed.isLoadingMore,
            searchError = feed.error?.let { SupabaseClient.sanitizeUserErrorMessage(it) },
            dbSyncNotice = syncNotice,
            clipProgress = mergedProgress,
            quests = questsUi,
            rewards = ClipCatalog.rewards,
            transactions = txs,
            isCheckInAvailable = wallet.lastCheckInDate != today,
            isFreeSpinAvailable = wallet.lastFreeSpinDate != today,
            feedbackBanner = banner,
            errorMessage = err,
            isOnline = online
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ClipCashUiState()
    )

    private var pendingResetRecoveryToken: String? = null

    fun setAuthStage(stage: AuthStage) {
        if (stage != AuthStage.RESET_NEW_PASSWORD) {
            pendingResetRecoveryToken = null
        }
        authStage.value = stage
        authError.value = null
        authInfo.value = null
    }

    private fun requireOnlineForAuth(): Boolean {
        val online = networkMonitor?.isCurrentlyConnected() ?: true
        if (!online) {
            authInfo.value = null
            authError.value = NetworkMonitor.OFFLINE_ERROR_MESSAGE
        }
        return online
    }

    private fun requireOnlineForChangeEmail(): Boolean {
        val online = networkMonitor?.isCurrentlyConnected() ?: true
        if (!online) {
            changeEmailState.value = changeEmailState.value.copy(
                errorMessage = NetworkMonitor.OFFLINE_ERROR_MESSAGE,
                infoMessage = null
            )
        }
        return online
    }

    fun signIn(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || password.length < 6) {
            authError.value = "Enter a valid email and at least 6 characters."
            return
        }
        if (!requireOnlineForAuth()) return
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
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Sign in failed. Please try again." }
                }
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
        if (!requireOnlineForAuth()) return
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.verifySignInCode(targetEmail, cleanCode)
                .onSuccess {
                    authStage.value = AuthStage.SIGN_IN
                    authPendingEmail.value = ""
                }
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Invalid verification code." }
                }
            authLoading.value = false
        }
    }

    fun resendSignInCode() {
        val targetEmail = authPendingEmail.value
        if (targetEmail.isBlank()) return
        if (!requireOnlineForAuth()) return
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.resendSignInCode(targetEmail)
                .onSuccess { authInfo.value = "A fresh verification code was sent to $targetEmail." }
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Unable to resend code." }
                }
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

        if (!requireOnlineForAuth()) return

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
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Sign up failed. Please try again." }
                }
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
        if (!requireOnlineForAuth()) return
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
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Invalid verification code." }
                }
            authLoading.value = false
        }
    }

    fun resendSignUpCode() {
        val targetEmail = authPendingEmail.value
        if (targetEmail.isBlank()) return
        if (!requireOnlineForAuth()) return
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.resendSignUpCode(targetEmail, authSignUpOtpFallback.value)
                .onSuccess { authInfo.value = "Verification code resent to $targetEmail." }
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Unable to resend code." }
                }
            authLoading.value = false
        }
    }

    fun requestPasswordReset(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            authError.value = "Enter a valid email address."
            return
        }
        if (!requireOnlineForAuth()) return
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
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Unable to send reset code." }
                }
            authLoading.value = false
        }
    }

    fun verifyPasswordResetCode(code: String) {
        val cleanCode = code.trim()
        val targetEmail = authPendingEmail.value
        if (cleanCode.length < 6) {
            authError.value = "Enter the verification code sent to $targetEmail."
            return
        }
        if (!requireOnlineForAuth()) return
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.verifyPasswordResetCode(targetEmail, cleanCode)
                .onSuccess { recoveryToken ->
                    pendingResetRecoveryToken = recoveryToken
                    authStage.value = AuthStage.RESET_NEW_PASSWORD
                    authInfo.value = "Verification code confirmed! Enter your new password below."
                }
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Invalid or expired verification code." }
                }
            authLoading.value = false
        }
    }

    fun completePasswordReset(newPassword: String, confirmPassword: String) {
        val recoveryToken = pendingResetRecoveryToken
        if (recoveryToken.isNullOrBlank()) {
            authStage.value = AuthStage.VERIFY_RESET_PASSWORD
            authError.value = "Please verify your reset code first."
            return
        }
        when {
            newPassword.length < 6 -> {
                authError.value = "New password must be at least 6 characters."
                return
            }
            newPassword != confirmPassword -> {
                authError.value = "New passwords do not match."
                return
            }
        }
        if (!requireOnlineForAuth()) return
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.updatePasswordWithRecoveryToken(recoveryToken, newPassword)
                .onSuccess {
                    pendingResetRecoveryToken = null
                    authStage.value = AuthStage.SIGN_IN
                    authPendingEmail.value = ""
                    authInfo.value = "Password updated! Sign in with your new password."
                }
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Password reset failed." }
                }
            authLoading.value = false
        }
    }

    fun resendPasswordResetCode() {
        val targetEmail = authPendingEmail.value
        if (targetEmail.isBlank()) return
        if (!requireOnlineForAuth()) return
        viewModelScope.launch {
            authLoading.value = true
            authError.value = null
            authInfo.value = null
            repository.requestPasswordReset(targetEmail)
                .onSuccess { authInfo.value = "Password reset code resent to $targetEmail." }
                .onFailure {
                    authError.value = SupabaseClient.sanitizeUserErrorMessage(it.message)
                        .ifBlank { "Unable to resend reset code." }
                }
            authLoading.value = false
        }
    }

    fun resetChangeEmailFlow() {
        changeEmailState.value = ChangeEmailUiState(isOnline = isOnlineFlow.value)
    }

    fun startChangeEmailFlow(currentEmail: String) {
        val cleanCurrent = currentEmail.trim()
        changeEmailState.value = ChangeEmailUiState(
            stage = ChangeEmailStage.VERIFY_CURRENT_EMAIL,
            emailBeingVerified = cleanCurrent,
            isLoading = false,
            isOnline = isOnlineFlow.value
        )
        if (cleanCurrent.isBlank()) {
            changeEmailState.value = changeEmailState.value.copy(
                errorMessage = "Current email address not found. Please sign in again."
            )
            return
        }
        sendCurrentEmailVerificationOtp(cleanCurrent, isResend = false)
    }

    fun resendCurrentEmailChangeCode(currentEmail: String) {
        val target = changeEmailState.value.emailBeingVerified.ifBlank { currentEmail.trim() }
        if (target.isBlank()) return
        sendCurrentEmailVerificationOtp(target, isResend = true)
    }

    private fun sendCurrentEmailVerificationOtp(targetEmail: String, isResend: Boolean) {
        if (!requireOnlineForChangeEmail()) return
        viewModelScope.launch {
            changeEmailState.value = changeEmailState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )
            repository.requestCurrentEmailVerificationOtp(targetEmail)
                .onSuccess {
                    changeEmailState.value = changeEmailState.value.copy(
                        stage = ChangeEmailStage.VERIFY_CURRENT_EMAIL,
                        emailBeingVerified = targetEmail,
                        isLoading = false,
                        infoMessage = if (isResend) {
                            "Verification code resent to $targetEmail."
                        } else {
                            "Verification code sent to $targetEmail."
                        }
                    )
                }
                .onFailure { err ->
                    changeEmailState.value = changeEmailState.value.copy(
                        isLoading = false,
                        errorMessage = SupabaseClient.sanitizeUserErrorMessage(err.message)
                            .ifBlank { "Unable to send verification code." }
                    )
                }
        }
    }

    fun verifyCurrentEmailForChange(code: String) {
        val cleanCode = code.trim()
        val current = changeEmailState.value
        val targetEmail = current.emailBeingVerified
        if (cleanCode.length < 6) {
            changeEmailState.value = current.copy(
                errorMessage = "Enter the verification code sent to $targetEmail.",
                infoMessage = null
            )
            return
        }
        if (!requireOnlineForChangeEmail()) return
        viewModelScope.launch {
            changeEmailState.value = current.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )
            repository.verifyCurrentEmailForChange(targetEmail, cleanCode)
                .onSuccess {
                    changeEmailState.value = ChangeEmailUiState(
                        stage = ChangeEmailStage.ENTER_NEW_EMAIL,
                        emailBeingVerified = targetEmail,
                        isLoading = false,
                        infoMessage = "Current email verified! Enter your new email address below.",
                        isOnline = isOnlineFlow.value
                    )
                }
                .onFailure { err ->
                    changeEmailState.value = changeEmailState.value.copy(
                        isLoading = false,
                        errorMessage = SupabaseClient.sanitizeUserErrorMessage(err.message)
                            .ifBlank { "Invalid or expired verification code." }
                    )
                }
        }
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

    fun requestEmailChange(newEmail: String, currentEmail: String, onCompleted: (String) -> Unit = {}) {
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
        if (!requireOnlineForChangeEmail()) return
        viewModelScope.launch {
            changeEmailState.value = changeEmailState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )
            repository.requestEmailChange(cleanNew)
                .onSuccess { outcome ->
                    when (outcome) {
                        is EmailChangeRequestResult.CompletedImmediately -> {
                            changeEmailState.value = ChangeEmailUiState(isOnline = isOnlineFlow.value)
                            feedbackBanner.value = RewardFeedback(
                                title = "Email Address Updated!",
                                subtitle = "Your ClipCash wallet is now linked to ${outcome.newEmail}",
                                coinsDelta = 0
                            )
                            onCompleted(outcome.newEmail)
                        }
                        is EmailChangeRequestResult.VerificationRequired -> {
                            changeEmailState.value = ChangeEmailUiState(
                                stage = ChangeEmailStage.VERIFY_NEW_EMAIL,
                                desiredNewEmail = cleanNew,
                                emailBeingVerified = outcome.emailToVerify,
                                isLoading = false,
                                infoMessage = outcome.message,
                                isOnline = isOnlineFlow.value
                            )
                        }
                    }
                }
                .onFailure { err ->
                    changeEmailState.value = changeEmailState.value.copy(
                        isLoading = false,
                        errorMessage = SupabaseClient.sanitizeUserErrorMessage(err.message)
                            .ifBlank { "Unable to update email address." }
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
        if (!requireOnlineForChangeEmail()) return
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
                            changeEmailState.value = ChangeEmailUiState(isOnline = isOnlineFlow.value)
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
                        errorMessage = SupabaseClient.sanitizeUserErrorMessage(err.message)
                            .ifBlank { "Invalid or expired verification code." }
                    )
                }
        }
    }

    fun resendEmailChangeCode() {
        val desired = changeEmailState.value.desiredNewEmail
        if (desired.isBlank()) return
        if (!requireOnlineForChangeEmail()) return
        viewModelScope.launch {
            changeEmailState.value = changeEmailState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null
            )
            repository.requestEmailChange(desired)
                .onSuccess { outcome ->
                    val target = if (outcome is EmailChangeRequestResult.VerificationRequired) {
                        outcome.emailToVerify
                    } else {
                        desired
                    }
                    changeEmailState.value = changeEmailState.value.copy(
                        isLoading = false,
                        infoMessage = "Verification code resent to $target."
                    )
                }
                .onFailure { err ->
                    changeEmailState.value = changeEmailState.value.copy(
                        isLoading = false,
                        errorMessage = SupabaseClient.sanitizeUserErrorMessage(err.message)
                            .ifBlank { "Unable to resend verification code." }
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
            pendingResetRecoveryToken = null
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

    fun grantRewardedAdCoins() {
        viewModelScope.launch {
            coinRepository.grantRewardedAdCoins(CoinRepository.REWARD_COINS_PER_AD)
                .onSuccess { status ->
                    repository.recordAdReward(
                        title = "Rewarded Ad Bonus",
                        subtitle = "Rewarded video completed",
                        coins = CoinRepository.REWARD_COINS_PER_AD,
                        coinStatus = status
                    )
                    feedbackBanner.value = RewardFeedback(
                        title = "+${CoinRepository.REWARD_COINS_PER_AD} Coins Earned!",
                        subtitle = "Rewarded ad completed.",
                        coinsDelta = CoinRepository.REWARD_COINS_PER_AD
                    )
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Unable to credit coins right now."
                }
        }
    }

    fun grantPostShortsAdCoins() {
        viewModelScope.launch {
            coinRepository.grantInterstitialBreakCoins(CoinRepository.POST_SHORTS_AD_COINS)
                .onSuccess { status ->
                    repository.recordAdReward(
                        title = "Shorts Ad Break Bonus",
                        subtitle = "Completed ad after 3 Shorts",
                        coins = CoinRepository.POST_SHORTS_AD_COINS,
                        coinStatus = status
                    )
                    feedbackBanner.value = RewardFeedback(
                        title = "+${CoinRepository.POST_SHORTS_AD_COINS} Coins Earned!",
                        subtitle = "Completed ad after 3 Shorts.",
                        coinsDelta = CoinRepository.POST_SHORTS_AD_COINS
                    )
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Unable to credit coins right now."
                }
        }
    }

    fun grantPostGamesAdCoins() {
        viewModelScope.launch {
            coinRepository.grantInterstitialBreakCoins(CoinRepository.POST_GAMES_AD_COINS)
                .onSuccess { status ->
                    repository.recordAdReward(
                        title = "Game Ad Break Bonus",
                        subtitle = "Completed ad after 2 game attempts",
                        coins = CoinRepository.POST_GAMES_AD_COINS,
                        coinStatus = status
                    )
                    feedbackBanner.value = RewardFeedback(
                        title = "+${CoinRepository.POST_GAMES_AD_COINS} Coins Earned!",
                        subtitle = "Completed ad after 2 game attempts.",
                        coinsDelta = CoinRepository.POST_GAMES_AD_COINS
                    )
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Unable to credit coins right now."
                }
        }
    }

    fun reportAdMessage(message: String) {
        errorMessage.value = message
    }

    fun onVideoPlaying(clip: VideoClip) {
        // Watching YouTube videos must never award coins, points, or any reward.
    }

    fun onVideoEnded(clip: VideoClip, onAwarded: (Int) -> Unit = {}) {
        // Watching YouTube videos must never award coins, points, or any reward.
    }

    fun recordClipWatched(clip: VideoClip) {
        // Watching YouTube videos must never award coins, points, or any reward.
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

    fun redeemReward(reward: RewardOption, note: String = "", onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            repository.redeemReward(reward, note)
                .onSuccess { refCode ->
                    feedbackBanner.value = RewardFeedback(
                        title = "Perk Unlocked!",
                        subtitle = "${reward.title} · Ref $refCode",
                        coinsDelta = -reward.coinCost
                    )
                    onSuccess(refCode)
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Unable to unlock perk"
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
                val clipCashApp = app as? ClipCashApp
                val db = ClipCashDatabase.getInstance(app)
                val sessionStore = SupabaseSessionStore(app)
                val networkMonitor = clipCashApp?.networkMonitor ?: NetworkMonitor(app)
                val supabaseClient = SupabaseClient(sessionStore, networkMonitor)
                val coinRepository = clipCashApp?.coinRepository ?: DataStoreCoinRepository(app)
                ClipCashViewModel(
                    repository = ClipCashRepository(db, supabaseClient, sessionStore, coinRepository),
                    coinRepository = coinRepository,
                    networkMonitor = networkMonitor
                )
            }
        }
    }
}
