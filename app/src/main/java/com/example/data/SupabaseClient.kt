package com.example.data

import com.example.BuildConfig
import com.example.R
import com.example.model.ClipCatalog
import com.example.model.ClipCategory
import com.example.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface SupabaseSignUpResult {
    data class VerificationRequired(
        val email: String,
        val message: String,
        val usesEmailOtpFallback: Boolean = false
    ) : SupabaseSignUpResult
}

sealed interface SupabaseSignInResult {
    data class OtpSent(
        val email: String,
        val message: String
    ) : SupabaseSignInResult

    data class SignUpConfirmationNeeded(
        val email: String,
        val message: String
    ) : SupabaseSignInResult
}

sealed interface EmailChangeVerifyResult {
    data class Completed(
        val newEmail: String,
        val updatedSession: SupabaseSession
    ) : EmailChangeVerifyResult

    data class SecondCodeRequired(
        val nextEmailToVerify: String,
        val message: String
    ) : EmailChangeVerifyResult
}

sealed interface SupabaseDbSyncResult<out T> {
    data class Success<T>(val data: T) : SupabaseDbSyncResult<T>
    data object TableNotProvisioned : SupabaseDbSyncResult<Nothing>
    data class Error(val message: String) : SupabaseDbSyncResult<Nothing>
}

data class RemoteProfileData(
    val wallet: WalletStateEntity,
    val email: String = "",
    val fullName: String = "",
    val username: String = "",
    val nationality: String = "",
    val phoneNumber: String = "",
    val claimedQuestIds: Set<String> = emptySet(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val clipProgress: List<ClipProgressEntity> = emptyList(),
    val hasWalletsRow: Boolean = false,
    val hasPublicUsersRow: Boolean = false,
    val serverWalletBalance: Int? = null,
    val adsWatchedToday: Int = 0,
    val adsWatchedDate: String = "",
    val lastRewardedAtMs: Long = 0L
)

data class ShortsPageResult(
    val clips: List<VideoClip>,
    val nextPageToken: String? = null
)

class SupabaseClient(
    private val sessionStore: SupabaseSessionStore
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val centralPointUrl = BuildConfig.SUPABASE_CENTRAL_POINT_URL
    private val directSearchFunctionUrl = BuildConfig.SUPABASE_SEARCH_FUNCTION_URL

    private fun buildGatewayUrl(queryBuilder: (HttpUrl.Builder.() -> Unit)? = null): HttpUrl {
        val base = centralPointUrl.toHttpUrlOrNull()
            ?: throw IllegalStateException("Invalid ClipCash gateway URL")
        if (queryBuilder == null) return base
        return base.newBuilder().apply(queryBuilder).build()
    }

    private fun newGatewayRequestBuilder(
        targetPath: String,
        accessToken: String? = null,
        queryBuilder: (HttpUrl.Builder.() -> Unit)? = null
    ): Request.Builder {
        val builder = Request.Builder()
            .url(buildGatewayUrl(queryBuilder))
            .addHeader("x-target-path", targetPath)
        if (!accessToken.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $accessToken")
        }
        return builder
    }

    // --- Supabase Auth (routed through central-point gateway) ---

    suspend fun signInWithVerification(email: String, password: String): Result<SupabaseSignInResult> =
        withContext(Dispatchers.IO) {
            val cleanEmail = email.trim()
            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", password)
            }
            val request = newGatewayRequestBuilder(
                targetPath = "/auth/v1/token",
                queryBuilder = { addQueryParameter("grant_type", "password") }
            )
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val parsedErr = parseSupabaseError(body, "Sign in failed (${response.code})")
                        if (parsedErr.contains("email not confirmed", ignoreCase = true) ||
                            body.contains("email_not_confirmed", ignoreCase = true)
                        ) {
                            sendResendOtp(email = cleanEmail, type = "signup")
                            return@use SupabaseSignInResult.SignUpConfirmationNeeded(
                                email = cleanEmail,
                                message = "Your email is not verified yet. We sent a verification code to $cleanEmail."
                            )
                        }
                        throw IllegalStateException(parsedErr)
                    }

                    // Password validated — now send a login verification code to this email
                    sendEmailOtpBlocking(email = cleanEmail)
                    SupabaseSignInResult.OtpSent(
                        email = cleanEmail,
                        message = "Verification code sent to $cleanEmail."
                    )
                }
            }
        }

    suspend fun verifySignInCode(email: String, code: String): Result<SupabaseSession> =
        withContext(Dispatchers.IO) {
            verifyOtpSessionBlocking(email = email.trim(), token = code.trim(), type = "email")
        }

    suspend fun resendSignInCode(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { sendEmailOtpBlocking(email = email.trim()) }
    }

    suspend fun signUp(
        fullName: String,
        username: String,
        nationality: String,
        countryIso: String,
        phoneNumber: String,
        email: String,
        password: String
    ): Result<SupabaseSignUpResult> = withContext(Dispatchers.IO) {
        val cleanFullName = fullName.trim()
        val cleanUsername = username.trim().removePrefix("@")
        val cleanNationality = nationality.trim()
        val cleanPhone = phoneNumber.trim()
        val cleanEmail = email.trim()
        val metadata = JSONObject().apply {
            put("full_name", cleanFullName)
            put("username", cleanUsername)
            put("nationality", cleanNationality)
            put("country_code", countryIso.trim())
            put("phone_number", cleanPhone)
        }
        val payload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
            put("data", metadata)
        }
        val request = newGatewayRequestBuilder(targetPath = "/auth/v1/signup")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseSupabaseError(body, "Sign up failed (${response.code})"))
                }
                val json = JSONObject(body)
                val parsed = parseSessionJson(json)
                if (parsed != null) {
                    // Auto-confirm was active on the project; send an OTP code explicitly so the user always verifies by email
                    sendEmailOtpBlocking(email = cleanEmail)
                    SupabaseSignUpResult.VerificationRequired(
                        email = cleanEmail,
                        message = "We sent a verification code to $cleanEmail to verify your new account.",
                        usesEmailOtpFallback = true
                    )
                } else {
                    SupabaseSignUpResult.VerificationRequired(
                        email = cleanEmail,
                        message = "Verification code sent to $cleanEmail. Enter the code to activate your account.",
                        usesEmailOtpFallback = false
                    )
                }
            }
        }
    }

    suspend fun verifySignUpCode(
        email: String,
        code: String,
        usesEmailOtpFallback: Boolean
    ): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanCode = code.trim()
        val primaryType = if (usesEmailOtpFallback) "email" else "signup"
        val primaryResult = verifyOtpSessionBlocking(email = cleanEmail, token = cleanCode, type = primaryType)
        if (primaryResult.isSuccess || usesEmailOtpFallback) {
            primaryResult
        } else {
            // Some configurations issue an email-type OTP; check fallback only if signup returned invalid/not found
            val errMsg = primaryResult.exceptionOrNull()?.message.orEmpty()
            if (errMsg.contains("invalid", ignoreCase = true) || errMsg.contains("expired", ignoreCase = true)) {
                val alt = verifyOtpSessionBlocking(email = cleanEmail, token = cleanCode, type = "email")
                if (alt.isSuccess) return@withContext alt
            }
            primaryResult
        }
    }

    suspend fun resendSignUpCode(email: String, usesEmailOtpFallback: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanEmail = email.trim()
                if (usesEmailOtpFallback) {
                    sendEmailOtpBlocking(email = cleanEmail)
                } else {
                    sendResendOtp(email = cleanEmail, type = "signup")
                }
            }
        }

    suspend fun requestPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val payload = JSONObject().apply {
            put("email", cleanEmail)
        }
        val request = newGatewayRequestBuilder(targetPath = "/auth/v1/recover")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        parseSupabaseError(body, "Unable to send reset code (${response.code})")
                    )
                }
            }
        }
    }

    suspend fun verifyAndResetPassword(
        email: String,
        code: String,
        newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanCode = code.trim()
        runCatching {
            val recoverySession = verifyOtpSessionBlocking(
                email = cleanEmail,
                token = cleanCode,
                type = "recovery"
            ).getOrThrow()

            val updatePayload = JSONObject().apply {
                put("password", newPassword)
            }
            val updateRequest = newGatewayRequestBuilder(
                targetPath = "/auth/v1/user",
                accessToken = recoverySession.accessToken
            )
                .addHeader("Content-Type", "application/json")
                .put(updatePayload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(updateRequest).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException(
                        parseSupabaseError(body, "Unable to update password (${resp.code})")
                    )
                }
            }

            // Revoke temporary recovery token cleanly so sign-in starts fresh with verification
            runCatching {
                val logoutReq = newGatewayRequestBuilder(
                    targetPath = "/auth/v1/logout",
                    accessToken = recoverySession.accessToken
                )
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(logoutReq).execute().close()
            }
            Unit
        }
    }

    suspend fun requestEmailChange(newEmail: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanNewEmail = newEmail.trim()
        val initialSession = sessionStore.getSession()
            ?: return@withContext Result.failure(IllegalStateException("Please sign in again to change your email."))

        fun executeUpdate(accessToken: String): Pair<Int, String> {
            val payload = JSONObject().apply {
                put("email", cleanNewEmail)
            }
            val request = newGatewayRequestBuilder(
                targetPath = "/auth/v1/user",
                accessToken = accessToken
            )
                .addHeader("Content-Type", "application/json")
                .put(payload.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                return resp.code to resp.body?.string().orEmpty()
            }
        }

        runCatching {
            var (code, body) = executeUpdate(initialSession.accessToken)
            if (code == 401) {
                val refreshed = refreshSessionIfNeeded(initialSession)
                    ?: throw IllegalStateException("Session expired. Please sign in again.")
                val retry = executeUpdate(refreshed.accessToken)
                code = retry.first
                body = retry.second
            }
            if (code !in 200..299) {
                throw IllegalStateException(
                    parseSupabaseError(body, "Unable to send email verification code ($code)")
                )
            }
        }
    }

    suspend fun verifyEmailChange(
        emailToVerify: String,
        desiredNewEmail: String,
        code: String
    ): Result<EmailChangeVerifyResult> = withContext(Dispatchers.IO) {
        val currentSession = sessionStore.getSession()
            ?: return@withContext Result.failure(IllegalStateException("Session expired. Please sign in again."))
        val cleanTargetEmail = emailToVerify.trim()
        val cleanDesiredEmail = desiredNewEmail.trim()
        val cleanCode = code.trim()

        fun executeVerify(accessToken: String): Pair<Int, String> {
            val payload = JSONObject().apply {
                put("email", cleanTargetEmail)
                put("token", cleanCode)
                put("type", "email_change")
            }
            val req = newGatewayRequestBuilder(
                targetPath = "/auth/v1/verify",
                accessToken = accessToken
            )
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                return resp.code to resp.body?.string().orEmpty()
            }
        }

        runCatching {
            var activeSession = currentSession
            var (httpCode, body) = executeVerify(activeSession.accessToken)
            if (httpCode == 401) {
                val refreshed = refreshSessionIfNeeded(activeSession)
                    ?: throw IllegalStateException("Session expired. Please sign in again.")
                activeSession = refreshed
                val retry = executeVerify(refreshed.accessToken)
                httpCode = retry.first
                body = retry.second
            }
            if (httpCode !in 200..299) {
                throw IllegalStateException(
                    parseSupabaseError(body, "Invalid or expired verification code ($httpCode)")
                )
            }

            val json = if (body.isNotBlank()) JSONObject(body) else JSONObject()
            val parsedSession = parseSessionJson(json)
            val userObj = json.optJSONObject("user") ?: json
            val returnedEmail = userObj.optCleanString("email")
            val pendingNewEmail = userObj.optCleanString("new_email")

            val baseUpdated = if (parsedSession != null) {
                parsedSession.copy(
                    fullName = parsedSession.fullName.ifBlank { activeSession.fullName },
                    username = parsedSession.username.ifBlank { activeSession.username },
                    nationality = parsedSession.nationality.ifBlank { activeSession.nationality },
                    phoneNumber = parsedSession.phoneNumber.ifBlank { activeSession.phoneNumber }
                )
            } else {
                activeSession
            }

            if (pendingNewEmail.isNotBlank() &&
                !returnedEmail.equals(cleanDesiredEmail, ignoreCase = true)
            ) {
                // Secure email change is enabled and expects the second confirmation from the previous email
                val nextEmail = if (cleanTargetEmail.equals(cleanDesiredEmail, ignoreCase = true) &&
                    returnedEmail.isNotBlank()
                ) {
                    returnedEmail
                } else {
                    cleanDesiredEmail
                }
                EmailChangeVerifyResult.SecondCodeRequired(
                    nextEmailToVerify = nextEmail,
                    message = "First code verified! Enter the confirmation code sent to $nextEmail to complete the email change."
                )
            } else {
                val finalEmail = returnedEmail.ifBlank { cleanDesiredEmail }
                EmailChangeVerifyResult.Completed(
                    newEmail = finalEmail,
                    updatedSession = baseUpdated.copy(email = finalEmail)
                )
            }
        }
    }

    private fun sendEmailOtpBlocking(email: String) {
        val payload = JSONObject().apply {
            put("email", email)
            put("create_user", false)
        }
        val request = newGatewayRequestBuilder(targetPath = "/auth/v1/otp")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    parseSupabaseError(body, "Failed to send verification code (${resp.code})")
                )
            }
        }
    }

    private fun sendResendOtp(email: String, type: String) {
        val payload = JSONObject().apply {
            put("type", type)
            put("email", email)
        }
        val request = newGatewayRequestBuilder(targetPath = "/auth/v1/resend")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    parseSupabaseError(body, "Unable to resend verification code (${resp.code})")
                )
            }
        }
    }

    private fun verifyOtpSessionBlocking(
        email: String,
        token: String,
        type: String
    ): Result<SupabaseSession> {
        val payload = JSONObject().apply {
            put("email", email)
            put("token", token)
            put("type", type)
        }
        val request = newGatewayRequestBuilder(targetPath = "/auth/v1/verify")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException(
                        parseSupabaseError(body, "Invalid or expired verification code (${resp.code})")
                    )
                }
                parseSessionJson(JSONObject(body))
                    ?: throw IllegalStateException("Verification succeeded, but session details were missing.")
            }
        }
    }

    suspend fun refreshSessionIfNeeded(fallbackSession: SupabaseSession? = null): SupabaseSession? = withContext(Dispatchers.IO) {
        val current = sessionStore.getSession() ?: fallbackSession ?: return@withContext null
        if (current.refreshToken.isBlank()) return@withContext null
        val payload = JSONObject().apply {
            put("refresh_token", current.refreshToken)
        }
        val request = newGatewayRequestBuilder(
            targetPath = "/auth/v1/token",
            queryBuilder = { addQueryParameter("grant_type", "refresh_token") }
        )
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val parsed = parseSessionJson(JSONObject(response.body?.string().orEmpty()))
                if (parsed != null) {
                    val merged = parsed.copy(
                        email = parsed.email.ifBlank { current.email },
                        fullName = parsed.fullName.ifBlank { current.fullName },
                        username = parsed.username.ifBlank { current.username },
                        nationality = parsed.nationality.ifBlank { current.nationality },
                        phoneNumber = parsed.phoneNumber.ifBlank { current.phoneNumber }
                    )
                    if (sessionStore.getSession() != null) {
                        sessionStore.saveSession(merged)
                    }
                    merged
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = sessionStore.getSession()
        if (session != null) {
            runCatching {
                val request = newGatewayRequestBuilder(
                    targetPath = "/auth/v1/logout",
                    accessToken = session.accessToken
                )
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(request).execute().close()
            }
        }
        sessionStore.clearSession()
    }

    // --- Video Search Edge Function (presented as ClipCash video source) ---

    suspend fun searchVideos(
        query: String,
        category: ClipCategory
    ): Result<List<VideoClip>> = searchVideosPage(
        query = query,
        category = category,
        pageIndex = 0,
        pageToken = null,
        seenVideoIds = emptySet()
    ).map { it.clips }

    suspend fun searchVideosPage(
        query: String,
        category: ClipCategory,
        pageIndex: Int,
        pageToken: String?,
        seenVideoIds: Set<String>
    ): Result<ShortsPageResult> = withContext(Dispatchers.IO) {
        val rawQuery = query.trim()
        val initialSession = sessionStore.getSession()

        suspend fun runQuery(effectiveQuery: String, token: String?): ShortsPageResult {
            return try {
                searchWithGatewayOrDirect(effectiveQuery, category, token, initialSession)
            } catch (err: Throwable) {
                val msg = err.message.orEmpty()
                if (msg.contains("401") || msg.contains("jwt", ignoreCase = true) || msg.contains("unauthorized", ignoreCase = true)) {
                    val refreshed = refreshSessionIfNeeded()
                    if (refreshed != null) {
                        return searchWithGatewayOrDirect(effectiveQuery, category, token, refreshed)
                    }
                }
                throw err
            }
        }

        runCatching {
            val baseQuery = buildCategorySubQuery(rawQuery, category, pageIndex)
            // First try native pageToken if provided
            if (pageIndex > 0 && !pageToken.isNullOrBlank()) {
                val paged = runQuery(buildCategorySubQuery(rawQuery, category, 0), pageToken)
                val fresh = paged.clips.filter { it.videoId !in seenVideoIds }
                if (fresh.isNotEmpty()) {
                    return@runCatching ShortsPageResult(
                        clips = fresh,
                        nextPageToken = paged.nextPageToken
                    )
                }
            }

            // Try rotated sub-query waves so categories & feeds expand without stopping at 10
            val collected = mutableListOf<VideoClip>()
            val localSeen = seenVideoIds.toMutableSet()
            var latestToken: String? = null
            val maxAttempts = if (pageIndex == 0) 2 else 3

            for (attempt in 0 until maxAttempts) {
                val queryVariant = if (attempt == 0) {
                    baseQuery
                } else {
                    buildCategorySubQuery(rawQuery, category, pageIndex + attempt * 7)
                }
                val batch = runQuery(queryVariant, null)
                latestToken = batch.nextPageToken
                for (item in batch.clips) {
                    if (localSeen.add(item.videoId)) {
                        collected.add(item)
                    }
                }
                if (collected.size >= 8) break
            }

            ShortsPageResult(
                clips = collected,
                nextPageToken = latestToken
            )
        }
    }

    private fun buildCategorySubQuery(
        rawQuery: String,
        category: ClipCategory,
        waveIndex: Int
    ): String {
        if (rawQuery.isNotBlank()) {
            val base = if (rawQuery.contains("short", ignoreCase = true)) rawQuery else "$rawQuery shorts"
            if (waveIndex == 0) return base
            val modifiers = listOf(
                "viral", "best", "funny", "top", "new", "highlights",
                "trending", "epic", "amazing", "clips", "daily", "latest"
            )
            val mod = modifiers[(waveIndex - 1) % modifiers.size]
            val cycle = (waveIndex - 1) / modifiers.size
            return if (cycle == 0) "$rawQuery $mod shorts" else "$rawQuery $mod shorts ${2025 - (cycle % 3)}"
        }

        val categoryPool = when (category) {
            ClipCategory.ALL -> listOf(
                "trending shorts", "viral shorts", "funny shorts", "amazing shorts",
                "satisfying shorts", "comedy shorts", "life hacks shorts", "cool gadgets shorts",
                "sports highlights shorts", "travel shorts", "animals shorts", "science shorts",
                "dance shorts", "art shorts", "food shorts", "magic tricks shorts"
            )
            ClipCategory.TRENDING -> listOf(
                "viral shorts", "trending shorts today", "popular shorts", "crazy moments shorts",
                "top viral shorts", "most watched shorts", "viral challenges shorts", "funny viral shorts",
                "unbelievable shorts", "trending reels shorts", "best viral clips shorts", "epic fails shorts"
            )
            ClipCategory.MUSIC -> listOf(
                "music shorts", "singing shorts", "guitar solo shorts", "piano cover shorts",
                "hip hop beat shorts", "dance music shorts", "remix shorts", "live concert shorts",
                "vocal cover shorts", "drumming shorts", "violin shorts", "acoustic cover shorts"
            )
            ClipCategory.GAMING -> listOf(
                "gaming shorts", "epic gaming moments shorts", "minecraft shorts", "fortnite shorts",
                "valorant clutch shorts", "speedrun shorts", "gaming funny moments shorts", "gta shorts",
                "fps highlights shorts", "esports shorts", "mobile gaming shorts", "indie games shorts"
            )
            ClipCategory.COMEDY -> listOf(
                "comedy shorts", "funny prank shorts", "standup comedy shorts", "hilarious fails shorts",
                "skit comedy shorts", "try not to laugh shorts", "funny reactions shorts", "memes shorts",
                "comedy sketches shorts", "funny moments shorts", "parody shorts", "relatable comedy shorts"
            )
            ClipCategory.TECH -> listOf(
                "tech gadgets shorts", "smartphone tips shorts", "pc setup shorts", "ai tools shorts",
                "unboxing shorts", "cool tech shorts", "desk setup shorts", "coding shorts",
                "robotics shorts", "camera gear shorts", "laptop tips shorts", "future tech shorts"
            )
        }
        val selected = categoryPool[waveIndex % categoryPool.size]
        val cycle = waveIndex / categoryPool.size
        return if (cycle == 0) selected else "$selected part ${cycle + 1}"
    }

    private fun searchWithGatewayOrDirect(
        effectiveQuery: String,
        category: ClipCategory,
        pageToken: String?,
        session: SupabaseSession?
    ): ShortsPageResult {
        val gatewayResult = runCatching {
            executeSearchRequest(
                useGateway = true,
                effectiveQuery = effectiveQuery,
                category = category,
                pageToken = pageToken,
                session = session
            )
        }
        if (gatewayResult.isSuccess) {
            return gatewayResult.getOrThrow()
        }

        return executeSearchRequest(
            useGateway = false,
            effectiveQuery = effectiveQuery,
            category = category,
            pageToken = pageToken,
            session = session
        )
    }

    private fun executeSearchRequest(
        useGateway: Boolean,
        effectiveQuery: String,
        category: ClipCategory,
        pageToken: String?,
        session: SupabaseSession?
    ): ShortsPageResult {
        val postPayload = JSONObject().apply {
            put("query", effectiveQuery)
            put("q", effectiveQuery)
            put("maxResults", 25)
            put("videoDuration", "short")
            if (!pageToken.isNullOrBlank()) {
                put("pageToken", pageToken)
            }
        }
        val postBuilder = if (useGateway) {
            newGatewayRequestBuilder(
                targetPath = "/functions/v1/youtube-search",
                accessToken = session?.accessToken
            )
        } else {
            Request.Builder().url(directSearchFunctionUrl).apply {
                session?.accessToken?.takeIf { it.isNotBlank() }?.let {
                    addHeader("Authorization", "Bearer $it")
                }
            }
        }
            .addHeader("Content-Type", "application/json")
            .post(postPayload.toString().toRequestBody(jsonMediaType))

        httpClient.newCall(postBuilder.build()).execute().use { postResp ->
            val postBody = postResp.body?.string().orEmpty()
            if (postResp.isSuccessful) {
                val parsed = parseEdgeFunctionPage(postBody, category)
                if (parsed.clips.isNotEmpty()) return parsed
            }
            if (postResp.code !in listOf(400, 404, 405)) {
                if (!postResp.isSuccessful) {
                    throw IllegalStateException(
                        parseSupabaseError(postBody, "ClipCash feed unavailable (${postResp.code})")
                    )
                }
            }
        }

        // Fallback to GET with parameterized HttpUrl query params
        val getBuilder = if (useGateway) {
            newGatewayRequestBuilder(
                targetPath = "/functions/v1/youtube-search",
                accessToken = session?.accessToken,
                queryBuilder = {
                    addQueryParameter("q", effectiveQuery)
                    addQueryParameter("query", effectiveQuery)
                    addQueryParameter("maxResults", "25")
                    addQueryParameter("videoDuration", "short")
                    if (!pageToken.isNullOrBlank()) {
                        addQueryParameter("pageToken", pageToken)
                    }
                }
            ).get()
        } else {
            val getUrl = directSearchFunctionUrl.toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("q", effectiveQuery)
                ?.addQueryParameter("query", effectiveQuery)
                ?.addQueryParameter("maxResults", "25")
                ?.addQueryParameter("videoDuration", "short")
                ?.apply {
                    if (!pageToken.isNullOrBlank()) {
                        addQueryParameter("pageToken", pageToken)
                    }
                }
                ?.build()
                ?: throw IllegalStateException("Invalid Edge Function URL")
            Request.Builder().url(getUrl).get().apply {
                session?.accessToken?.takeIf { it.isNotBlank() }?.let {
                    addHeader("Authorization", "Bearer $it")
                }
            }
        }

        httpClient.newCall(getBuilder.build()).execute().use { getResp ->
            val getBody = getResp.body?.string().orEmpty()
            if (!getResp.isSuccessful) {
                throw IllegalStateException(
                    parseSupabaseError(getBody, "ClipCash feed unavailable (${getResp.code})")
                )
            }
            return parseEdgeFunctionPage(getBody, category)
        }
    }

    private fun parseEdgeFunctionPage(rawBody: String, category: ClipCategory): ShortsPageResult {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return ShortsPageResult(emptyList(), null)

        var nextToken: String? = null
        val itemsArray: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val root = JSONObject(trimmed)
                nextToken = root.optCleanString("nextPageToken")
                    .ifBlank { root.optCleanString("next_page_token") }
                    .ifBlank { null }
                root.optJSONArray("items")
                    ?: root.optJSONArray("videos")
                    ?: root.optJSONArray("results")
                    ?: root.optJSONArray("data")
                    ?: JSONArray()
            }
        }

        val fallbackThumbs = listOf(
            R.drawable.thumb_clip_neon_pulse,
            R.drawable.thumb_clip_pixel_arcade,
            R.drawable.thumb_clip_aurora_flow,
            R.drawable.thumb_clip_cosmic_zoom,
            R.drawable.thumb_clip_prism_waves
        )

        val clips = mutableListOf<VideoClip>()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.optJSONObject(i) ?: continue
            val snippet = obj.optJSONObject("snippet")
            val idObj = obj.optJSONObject("id")
            val resourceIdObj = snippet?.optJSONObject("resourceId")

            val rawUrl = obj.optString("url").takeIf { it.isNotBlank() }
                ?: obj.optString("link").takeIf { it.isNotBlank() }
                ?: obj.optString("watchUrl").takeIf { it.isNotBlank() }

            val videoId = obj.optString("videoId").takeIf { it.isNotBlank() }
                ?: obj.optString("video_id").takeIf { it.isNotBlank() }
                ?: idObj?.optString("videoId")?.takeIf { it.isNotBlank() }
                ?: resourceIdObj?.optString("videoId")?.takeIf { it.isNotBlank() }
                ?: obj.optString("id").takeIf { it.isNotBlank() && !it.startsWith("{") }
                ?: extractVideoIdFromUrl(rawUrl)
                ?: continue

            val rawTitle = obj.optString("title").takeIf { it.isNotBlank() }
                ?: snippet?.optString("title")?.takeIf { it.isNotBlank() }
                ?: "ClipCash Featured Video #${i + 1}"

            val cleanTitle = sanitizeClipCashText(unescapeHtml(rawTitle))

            val rawDesc = obj.optString("description").takeIf { it.isNotBlank() }
                ?: snippet?.optString("description")?.takeIf { it.isNotBlank() }
                ?: "Watch on ClipCash to earn instant coin rewards."

            val cleanDesc = sanitizeClipCashText(unescapeHtml(rawDesc))

            val thumbnails = obj.optJSONObject("thumbnails") ?: snippet?.optJSONObject("thumbnails")
            val thumbUrl = thumbnails?.optJSONObject("maxres")?.optString("url")?.takeIf { it.isNotBlank() }
                ?: thumbnails?.optJSONObject("standard")?.optString("url")?.takeIf { it.isNotBlank() }
                ?: thumbnails?.optJSONObject("high")?.optString("url")?.takeIf { it.isNotBlank() }
                ?: obj.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                ?: obj.optString("thumbnail_url").takeIf { it.isNotBlank() }
                ?: thumbnails?.optJSONObject("medium")?.optString("url")?.takeIf { it.isNotBlank() }
                ?: thumbnails?.optJSONObject("default")?.optString("url")?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/sddefault.jpg"

            clips.add(
                VideoClip(
                    id = "cc_$videoId",
                    videoId = videoId,
                    title = cleanTitle,
                    sourceLabel = "ClipCash",
                    category = category,
                    durationSeconds = 15,
                    watchTargetSeconds = 10,
                    rewardCoins = ClipCatalog.VIDEO_REWARD_COINS,
                    thumbnailUrl = thumbUrl,
                    fallbackThumbResId = fallbackThumbs[i % fallbackThumbs.size],
                    description = cleanDesc
                )
            )
        }
        return ShortsPageResult(clips = clips, nextPageToken = nextToken)
    }

    // --- Supabase public.videos + watch RPCs (start_watch / complete_watch) ---

    suspend fun fetchActiveVideos(
        category: ClipCategory = ClipCategory.ALL,
        query: String = ""
    ): Result<List<VideoClip>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = sessionStore.getSession()
            val req = newGatewayRequestBuilder(
                targetPath = "/rest/v1/videos",
                accessToken = session?.accessToken,
                queryBuilder = {
                    addQueryParameter("is_active", "eq.true")
                    addQueryParameter("select", "*")
                }
            )
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    if (isMissingTableResponse(resp.code, body)) return@runCatching emptyList()
                    throw IllegalStateException(parseSupabaseError(body, "Unable to load videos (${resp.code})"))
                }
                val arr = runCatching { JSONArray(body) }.getOrNull() ?: return@runCatching emptyList()
                val fallbackThumbs = listOf(
                    R.drawable.thumb_clip_neon_pulse,
                    R.drawable.thumb_clip_pixel_arcade,
                    R.drawable.thumb_clip_aurora_flow,
                    R.drawable.thumb_clip_cosmic_zoom,
                    R.drawable.thumb_clip_prism_waves
                )
                val q = query.trim()
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val uuid = obj.optCleanString("id")
                        val platform = obj.optCleanString("platform").ifBlank { "youtube" }
                        val rawVideoUrl = obj.optCleanString("video_url").ifBlank { null }
                        val videoId = obj.optCleanString("video_id")
                            .ifBlank { extractVideoIdFromUrl(rawVideoUrl).orEmpty() }
                        if (videoId.isBlank()) continue
                        if (!platform.equals("youtube", ignoreCase = true) && !platform.equals("clipcash", ignoreCase = true)) {
                            continue
                        }

                        val rawTitle = obj.optCleanString("title").ifBlank { "ClipCash Featured Video #${i + 1}" }
                        val cleanTitle = sanitizeClipCashText(unescapeHtml(rawTitle))
                        if (q.isNotBlank() && !cleanTitle.contains(q, ignoreCase = true) && !rawTitle.contains(q, ignoreCase = true)) {
                            continue
                        }

                        val durationSec = obj.optInt("duration_seconds", 15).coerceAtLeast(5)
                        val rewardEligible = obj.optBoolean("reward_eligible", false)
                        val rewardCoins = obj.optInt("reward_coins", ClipCatalog.VIDEO_REWARD_COINS).coerceAtLeast(0)
                        val thumbUrl = obj.optCleanString("thumbnail_url")
                            .ifBlank { "https://i.ytimg.com/vi/$videoId/sddefault.jpg" }

                        add(
                            VideoClip(
                                id = uuid.ifBlank { "cc_$videoId" },
                                videoId = videoId,
                                title = cleanTitle,
                                sourceLabel = "ClipCash",
                                category = category,
                                durationSeconds = durationSec,
                                watchTargetSeconds = durationSec.coerceIn(5, 15),
                                rewardCoins = rewardCoins,
                                thumbnailUrl = thumbUrl,
                                fallbackThumbResId = fallbackThumbs[i % fallbackThumbs.size],
                                description = if (rewardEligible && rewardCoins > 0) {
                                    "Watch to earn +$rewardCoins coins on ClipCash."
                                } else {
                                    "Featured ClipCash Short."
                                },
                                remoteUuid = uuid.ifBlank { null },
                                platform = platform,
                                rewardEligible = rewardEligible,
                                videoUrl = rawVideoUrl
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun startWatchRpc(
        session: SupabaseSession,
        videoUuid: String
    ): Result<String> {
        val result = executeAuthorizedRest(session) { activeSession ->
            val payload = JSONObject().apply {
                put("p_video_id", videoUuid)
            }
            val req = newGatewayRequestBuilder(
                targetPath = "/rest/v1/rpc/start_watch",
                accessToken = activeSession.accessToken
            )
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty().trim()
                if (isJwtExpiredOrUnauthorized(resp.code, body)) {
                    return@executeAuthorizedRest null to true
                }
                if (!resp.isSuccessful) {
                    return@executeAuthorizedRest SupabaseDbSyncResult.Error(
                        parseSupabaseError(body, "Unable to start watch session (${resp.code})")
                    ) to false
                }
                val parsedSessionId = when {
                    body.startsWith("{") -> {
                        val obj = JSONObject(body)
                        obj.optCleanString("session_id").ifBlank { obj.optCleanString("id") }
                    }
                    body.startsWith("\"") && body.endsWith("\"") -> body.removeSurrounding("\"")
                    else -> body
                }.trim()
                if (parsedSessionId.isBlank() || parsedSessionId == "null") {
                    return@executeAuthorizedRest SupabaseDbSyncResult.Error("Invalid watch session response") to false
                }
                SupabaseDbSyncResult.Success(parsedSessionId) to false
            }
        }
        return when (result) {
            is SupabaseDbSyncResult.Success -> Result.success(result.data)
            is SupabaseDbSyncResult.Error -> Result.failure(IllegalStateException(result.message))
            SupabaseDbSyncResult.TableNotProvisioned -> Result.failure(IllegalStateException("Watch session service unavailable"))
        }
    }

    suspend fun completeWatchRpc(
        session: SupabaseSession,
        sessionId: String
    ): Result<Long> {
        val result = executeAuthorizedRest(session) { activeSession ->
            val payload = JSONObject().apply {
                put("p_session_id", sessionId)
            }
            val req = newGatewayRequestBuilder(
                targetPath = "/rest/v1/rpc/complete_watch",
                accessToken = activeSession.accessToken
            )
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty().trim()
                if (isJwtExpiredOrUnauthorized(resp.code, body)) {
                    return@executeAuthorizedRest null to true
                }
                if (!resp.isSuccessful) {
                    return@executeAuthorizedRest SupabaseDbSyncResult.Error(
                        parseSupabaseError(body, "Unable to complete watch (${resp.code})")
                    ) to false
                }
                val coinsAwarded = when {
                    body.startsWith("{") -> {
                        val obj = JSONObject(body)
                        obj.optLong("coins", obj.optLong("reward_coins", 0L))
                    }
                    else -> body.removeSurrounding("\"").toLongOrNull() ?: 0L
                }
                SupabaseDbSyncResult.Success(coinsAwarded) to false
            }
        }
        return when (result) {
            is SupabaseDbSyncResult.Success -> Result.success(result.data)
            is SupabaseDbSyncResult.Error -> Result.failure(IllegalStateException(result.message))
            SupabaseDbSyncResult.TableNotProvisioned -> Result.failure(IllegalStateException("Reward verification unavailable"))
        }
    }

    suspend fun fetchWalletBalance(session: SupabaseSession): Result<Long> {
        val result = executeAuthorizedRest(session) { activeSession ->
            val (walletRow, needsRefresh, err) = querySingleRow(
                activeSession = activeSession,
                targetPath = "/rest/v1/wallets",
                filterColumn = "user_id",
                filterValue = activeSession.userId
            )
            if (needsRefresh) return@executeAuthorizedRest null to true
            if (err != null) return@executeAuthorizedRest err to false
            val bal = walletRow?.optLong("balance", 0L) ?: 0L
            SupabaseDbSyncResult.Success(bal) to false
        }
        return when (result) {
            is SupabaseDbSyncResult.Success -> Result.success(result.data)
            is SupabaseDbSyncResult.Error -> Result.failure(IllegalStateException(result.message))
            SupabaseDbSyncResult.TableNotProvisioned -> Result.failure(IllegalStateException("Wallet unavailable"))
        }
    }

    // --- Supabase Database & Auth Sync (public.users + read-only public.wallets + auth user_metadata) ---

    suspend fun fetchProfile(session: SupabaseSession): SupabaseDbSyncResult<RemoteProfileData?> =
        executeAuthorizedRest(session) { activeSession ->
            // 1. Read canonical Auth user & user_metadata from Supabase Auth (auth.users)
            val (authUser, authNeedsRefresh, authErr) = fetchAuthUserBlocking(activeSession)
            if (authNeedsRefresh) return@executeAuthorizedRest null to true
            if (authErr != null || authUser == null) {
                return@executeAuthorizedRest (authErr ?: SupabaseDbSyncResult.Error("Unable to verify ClipCash account")) to false
            }

            val canonicalUserId = authUser.optCleanString("id")
            if (canonicalUserId.isNotBlank() && canonicalUserId != activeSession.userId) {
                return@executeAuthorizedRest SupabaseDbSyncResult.Error("Account mismatch detected. Please sign in again.") to false
            }

            val authEmail = authUser.optCleanString("email").ifBlank { activeSession.email }
            val userMeta = authUser.optJSONObject("user_metadata")
            val metaFullName = userMeta?.optCleanString("full_name").orEmpty()
            val metaUsername = userMeta?.optCleanString("username").orEmpty()
            val metaNationality = userMeta?.optCleanString("nationality").orEmpty()
            val metaPhone = userMeta?.optCleanString("phone_number").orEmpty()
            val cloudState = userMeta?.optJSONObject("clipcash_state")

            // 2. Read public.users (if provisioned / accessible)
            val (usersRow, usersNeedsRefresh, usersErr) = querySingleRow(
                activeSession = activeSession,
                targetPath = "/rest/v1/users",
                filterColumn = "id",
                filterValue = activeSession.userId
            )
            if (usersNeedsRefresh) return@executeAuthorizedRest null to true
            val resolvedUsersRow = if (usersRow == null && authEmail.isNotBlank()) {
                val (byEmail, emailRefresh, _) = querySingleRow(
                    activeSession = activeSession,
                    targetPath = "/rest/v1/users",
                    filterColumn = "email",
                    filterValue = authEmail
                )
                if (emailRefresh) return@executeAuthorizedRest null to true
                byEmail
            } else {
                usersRow
            }

            // 3. Read public.wallets (primary table for each user's coin balance)
            val (walletRow, walletNeedsRefresh, walletErr) = querySingleRow(
                activeSession = activeSession,
                targetPath = "/rest/v1/wallets",
                filterColumn = "user_id",
                filterValue = activeSession.userId
            )
            if (walletNeedsRefresh) return@executeAuthorizedRest null to true
            if (walletErr != null) {
                return@executeAuthorizedRest walletErr to false
            }

            if (walletRow == null && cloudState == null) {
                return@executeAuthorizedRest SupabaseDbSyncResult.Success(null) to false
            }

            val rowEmail = resolvedUsersRow?.optCleanString("email").orEmpty().ifBlank { authEmail }
            val resolvedUsername = activeSession.username
                .ifBlank { metaUsername }
                .ifBlank { resolvedUsersRow?.optCleanString("username").orEmpty() }
            val resolvedFullName = activeSession.fullName
                .ifBlank { metaFullName }
                .ifBlank { resolvedUsersRow?.optCleanString("display_name").orEmpty() }
            val resolvedNationality = activeSession.nationality.ifBlank { metaNationality }
            val resolvedPhone = activeSession.phoneNumber.ifBlank { metaPhone }

            val hasWalletRow = walletRow != null
            val serverWalletBalance = if (walletRow != null && walletRow.has("balance")) {
                walletRow.optDouble("balance", 0.0).toInt()
            } else {
                null
            }
            val metaBalance = cloudState?.optInt("coin_balance", -1)?.takeIf { it >= 0 }
            val cloudBalance = when {
                serverWalletBalance != null && metaBalance != null -> {
                    if (cloudState.has("last_server_wallet_balance")) {
                        val prevServerBal = cloudState.optInt("last_server_wallet_balance", serverWalletBalance)
                        val serverDelta = (serverWalletBalance - prevServerBal).coerceAtLeast(0)
                        (metaBalance + serverDelta).coerceAtLeast(0)
                    } else {
                        maxOf(serverWalletBalance, metaBalance)
                    }
                }
                serverWalletBalance != null -> serverWalletBalance
                metaBalance != null -> metaBalance
                else -> 150
            }
            val metaTotalEarned = cloudState?.optInt("total_earned_coins", cloudBalance) ?: cloudBalance
            val effectiveTotalEarned = maxOf(metaTotalEarned, cloudBalance)

            val claimedQuestIds = buildSet {
                val arr = cloudState?.optJSONArray("claimed_quests")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val qId = arr.optString(i)
                        if (qId.isNotBlank()) add(qId)
                    }
                }
            }

            val recentTxs = buildList {
                val arr = cloudState?.optJSONArray("recent_transactions")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val txObj = arr.optJSONObject(i) ?: continue
                        add(
                            TransactionEntity(
                                title = txObj.optString("title", "ClipCash Activity"),
                                subtitle = txObj.optString("subtitle", ""),
                                amountCoins = txObj.optInt("amount_coins", 0),
                                type = txObj.optString("type", "BONUS"),
                                createdAt = txObj.optLong("created_at_ms", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            val cloudClipProgress = buildList {
                val arr = cloudState?.optJSONArray("clip_progress")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val cid = obj.optCleanString("clip_id")
                        if (cid.isBlank()) continue
                        add(
                            ClipProgressEntity(
                                clipId = cid,
                                watchCount = obj.optInt("watch_count", 0).coerceAtLeast(0),
                                lastWatchedAt = obj.optLong("last_watched_at", 0L).coerceAtLeast(0L),
                                earnedCoinsTotal = obj.optInt("earned_coins_total", 0).coerceAtLeast(0)
                            )
                        )
                    }
                }
            }

            val entity = WalletStateEntity(
                id = 1,
                userId = activeSession.userId,
                userEmail = rowEmail,
                coinBalance = cloudBalance,
                totalEarnedCoins = effectiveTotalEarned,
                streakDays = cloudState?.optInt("streak_days", 0) ?: 0,
                lastCheckInDate = cloudState?.optCleanString("last_check_in_date").orEmpty(),
                lastFreeSpinDate = cloudState?.optCleanString("last_free_spin_date").orEmpty(),
                clipsWatchedCount = cloudState?.optInt("clips_watched_count", 0) ?: 0,
                gamesPlayedCount = cloudState?.optInt("games_played_count", 0) ?: 0
            )

            SupabaseDbSyncResult.Success(
                RemoteProfileData(
                    wallet = entity,
                    email = rowEmail,
                    fullName = resolvedFullName,
                    username = resolvedUsername,
                    nationality = resolvedNationality,
                    phoneNumber = resolvedPhone,
                    claimedQuestIds = claimedQuestIds,
                    recentTransactions = recentTxs,
                    clipProgress = cloudClipProgress,
                    hasWalletsRow = hasWalletRow,
                    hasPublicUsersRow = resolvedUsersRow != null,
                    serverWalletBalance = serverWalletBalance,
                    adsWatchedToday = cloudState?.optInt("ads_watched_today", 0)?.coerceAtLeast(0) ?: 0,
                    adsWatchedDate = cloudState?.optCleanString("ads_watched_date").orEmpty(),
                    lastRewardedAtMs = cloudState?.optLong("last_rewarded_ad_ms", 0L)?.coerceAtLeast(0L) ?: 0L
                )
            ) to false
        }

    suspend fun upsertProfile(
        session: SupabaseSession,
        wallet: WalletStateEntity,
        claimedQuestIds: Set<String> = emptySet(),
        recentTransactions: List<TransactionEntity> = emptyList(),
        clipProgress: List<ClipProgressEntity> = emptyList(),
        coinStatus: CoinRewardStatus = CoinRewardStatus(),
        adsWatchedDate: String = "",
        lastServerWalletBalance: Int? = null,
        ensurePublicUserRow: Boolean = false
    ): SupabaseDbSyncResult<Unit> =
        executeAuthorizedRest(session) { activeSession ->
            // Clients must only READ public.wallets (server RPCs start_watch/complete_watch mutate balances).
            // Mirror ClipCash state (coins, ad limits, streaks, quests, games, progress, ledger) to authenticated
            // user_metadata bound directly to this user's Supabase Auth UUID & email.
            val (metaOk, metaNeedsRefresh) = updateAuthMetadataBlocking(
                activeSession = activeSession,
                wallet = wallet,
                claimedQuestIds = claimedQuestIds,
                recentTransactions = recentTransactions,
                clipProgress = clipProgress,
                coinStatus = coinStatus,
                adsWatchedDate = adsWatchedDate,
                lastServerWalletBalance = lastServerWalletBalance
            )
            if (metaNeedsRefresh) return@executeAuthorizedRest null to true

            if (metaOk && ensurePublicUserRow) {
                runCatching {
                    val (_, userNeedsRefresh) = upsertPublicUserBlocking(activeSession)
                    if (userNeedsRefresh) return@executeAuthorizedRest null to true
                }
            }

            val result = if (metaOk) {
                SupabaseDbSyncResult.Success(Unit)
            } else {
                SupabaseDbSyncResult.Error("Unable to sync ClipCash account state")
            }
            result to false
        }

    private fun fetchAuthUserBlocking(
        activeSession: SupabaseSession
    ): Triple<JSONObject?, Boolean, SupabaseDbSyncResult.Error?> {
        val req = newGatewayRequestBuilder(
            targetPath = "/auth/v1/user",
            accessToken = activeSession.accessToken
        )
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (isJwtExpiredOrUnauthorized(resp.code, body)) return Triple(null, true, null)
            if (!resp.isSuccessful || body.isBlank()) {
                return Triple(
                    null,
                    false,
                    SupabaseDbSyncResult.Error(parseSupabaseError(body, "Account sync error (${resp.code})"))
                )
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return Triple(null, false, SupabaseDbSyncResult.Error("Invalid account response"))
            return Triple(json, false, null)
        }
    }

    private fun querySingleRow(
        activeSession: SupabaseSession,
        targetPath: String,
        filterColumn: String,
        filterValue: String
    ): Triple<JSONObject?, Boolean, SupabaseDbSyncResult.Error?> {
        val req = newGatewayRequestBuilder(
            targetPath = targetPath,
            accessToken = activeSession.accessToken,
            queryBuilder = {
                addQueryParameter(filterColumn, "eq.$filterValue")
                addQueryParameter("select", "*")
                addQueryParameter("limit", "1")
            }
        )
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (isJwtExpiredOrUnauthorized(resp.code, body)) {
                return Triple(null, true, null)
            }
            if (isMissingTableResponse(resp.code, body)) {
                return Triple(null, false, null)
            }
            if (!resp.isSuccessful) {
                return Triple(
                    null,
                    false,
                    SupabaseDbSyncResult.Error(parseSupabaseError(body, "Sync error (${resp.code})"))
                )
            }
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: return Triple(null, false, null)
            val row = if (arr.length() > 0) arr.optJSONObject(0) else null
            return Triple(row, false, null)
        }
    }

    private fun upsertPublicUserBlocking(activeSession: SupabaseSession): Pair<Boolean, Boolean> {
        val baseUsername = activeSession.username
            .ifBlank { activeSession.email.substringBefore("@") }
            .replace(Regex("[^a-zA-Z0-9._]"), "_")
            .take(24)
            .ifBlank { "user_${activeSession.userId.take(6)}" }
        val displayName = activeSession.fullName.ifBlank { baseUsername }

        fun buildUserPayload(usernameVal: String): JSONObject = JSONObject().apply {
            put("id", activeSession.userId)
            if (activeSession.email.isNotBlank()) put("email", activeSession.email)
            put("username", usernameVal)
            put("display_name", displayName)
        }

        // 1) Try merge-duplicates upsert on id
        val upsertReq = newGatewayRequestBuilder(
            targetPath = "/rest/v1/users",
            accessToken = activeSession.accessToken,
            queryBuilder = { addQueryParameter("on_conflict", "id") }
        )
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .addHeader("Content-Type", "application/json")
            .post(buildUserPayload(baseUsername).toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(upsertReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (isJwtExpiredOrUnauthorized(resp.code, body)) return false to true
            if (resp.isSuccessful) return true to false

            // 2) Try PATCH by id (if RLS only permits UPDATE or row already exists)
            val patchPayload = JSONObject().apply {
                if (activeSession.email.isNotBlank()) put("email", activeSession.email)
                if (activeSession.username.isNotBlank()) put("username", activeSession.username)
                if (activeSession.fullName.isNotBlank()) put("display_name", activeSession.fullName)
            }
            if (patchPayload.length() > 0) {
                val patchReq = newGatewayRequestBuilder(
                    targetPath = "/rest/v1/users",
                    accessToken = activeSession.accessToken,
                    queryBuilder = { addQueryParameter("id", "eq.${activeSession.userId}") }
                )
                    .addHeader("Prefer", "return=representation")
                    .addHeader("Content-Type", "application/json")
                    .patch(patchPayload.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(patchReq).execute().use { patchResp ->
                    val patchBody = patchResp.body?.string().orEmpty()
                    if (isJwtExpiredOrUnauthorized(patchResp.code, patchBody)) return false to true
                    val updatedArr = runCatching { JSONArray(patchBody) }.getOrNull()
                    if (patchResp.isSuccessful && updatedArr != null && updatedArr.length() > 0) {
                        return true to false
                    }
                }
            }

            // 3) If unique username conflict (409 / 23505), retry insert/upsert with suffixed username
            if (resp.code == 409 || body.contains("23505")) {
                val uniqueUsername = "${baseUsername.take(16)}_${activeSession.userId.take(6)}"
                val retryReq = newGatewayRequestBuilder(
                    targetPath = "/rest/v1/users",
                    accessToken = activeSession.accessToken,
                    queryBuilder = { addQueryParameter("on_conflict", "id") }
                )
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .addHeader("Content-Type", "application/json")
                    .post(buildUserPayload(uniqueUsername).toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(retryReq).execute().use { retryResp ->
                    val retryBody = retryResp.body?.string().orEmpty()
                    if (isJwtExpiredOrUnauthorized(retryResp.code, retryBody)) return false to true
                    if (retryResp.isSuccessful) return true to false
                }
            }
        }
        return false to false
    }

    private fun upsertWalletBlocking(
        activeSession: SupabaseSession,
        coinBalance: Int
    ): Pair<SupabaseDbSyncResult<Unit>, Boolean> {
        val fullPayload = JSONObject().apply {
            put("user_id", activeSession.userId)
            put("balance", coinBalance)
        }

        // 1) Try merge-duplicates upsert on user_id
        val upsertReq = newGatewayRequestBuilder(
            targetPath = "/rest/v1/wallets",
            accessToken = activeSession.accessToken,
            queryBuilder = { addQueryParameter("on_conflict", "user_id") }
        )
            .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
            .addHeader("Content-Type", "application/json")
            .post(fullPayload.toString().toRequestBody(jsonMediaType))
            .build()

        var lastCode = 0
        var lastBody = ""
        httpClient.newCall(upsertReq).execute().use { resp ->
            lastCode = resp.code
            lastBody = resp.body?.string().orEmpty()
            if (isJwtExpiredOrUnauthorized(lastCode, lastBody)) {
                return SupabaseDbSyncResult.Error("Refreshing session") to true
            }
            if (resp.isSuccessful) {
                return SupabaseDbSyncResult.Success(Unit) to false
            }
            if (isMissingTableResponse(lastCode, lastBody)) {
                return SupabaseDbSyncResult.TableNotProvisioned to false
            }
        }

        // 2) Try PATCH by user_id (if row already exists and on_conflict or INSERT RLS failed)
        val patchPayload = JSONObject().apply { put("balance", coinBalance) }
        val patchReq = newGatewayRequestBuilder(
            targetPath = "/rest/v1/wallets",
            accessToken = activeSession.accessToken,
            queryBuilder = { addQueryParameter("user_id", "eq.${activeSession.userId}") }
        )
            .addHeader("Prefer", "return=representation")
            .addHeader("Content-Type", "application/json")
            .patch(patchPayload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(patchReq).execute().use { patchResp ->
            val patchBody = patchResp.body?.string().orEmpty()
            if (isJwtExpiredOrUnauthorized(patchResp.code, patchBody)) {
                return SupabaseDbSyncResult.Error("Refreshing session") to true
            }
            val arr = runCatching { JSONArray(patchBody) }.getOrNull()
            if (patchResp.isSuccessful && arr != null && arr.length() > 0) {
                return SupabaseDbSyncResult.Success(Unit) to false
            }
        }

        // 3) Try plain POST insert without on_conflict (in case user_id has non-standard constraint)
        val insertReq = newGatewayRequestBuilder(
            targetPath = "/rest/v1/wallets",
            accessToken = activeSession.accessToken
        )
            .addHeader("Prefer", "return=representation")
            .addHeader("Content-Type", "application/json")
            .post(fullPayload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(insertReq).execute().use { insertResp ->
            val insertBody = insertResp.body?.string().orEmpty()
            if (isJwtExpiredOrUnauthorized(insertResp.code, insertBody)) {
                return SupabaseDbSyncResult.Error("Refreshing session") to true
            }
            if (insertResp.isSuccessful) {
                return SupabaseDbSyncResult.Success(Unit) to false
            }
            if (isMissingTableResponse(insertResp.code, insertBody)) {
                return SupabaseDbSyncResult.TableNotProvisioned to false
            }
            val errMsg = parseSupabaseError(
                insertBody.ifBlank { lastBody },
                "Wallet sync failed (${insertResp.code})"
            )
            return SupabaseDbSyncResult.Error(errMsg) to false
        }
    }

    private fun updateAuthMetadataBlocking(
        activeSession: SupabaseSession,
        wallet: WalletStateEntity,
        claimedQuestIds: Set<String>,
        recentTransactions: List<TransactionEntity>,
        clipProgress: List<ClipProgressEntity>,
        coinStatus: CoinRewardStatus,
        adsWatchedDate: String,
        lastServerWalletBalance: Int?
    ): Pair<Boolean, Boolean> {
        val txArray = JSONArray().apply {
            recentTransactions.take(25).forEach { tx ->
                put(
                    JSONObject().apply {
                        put("title", tx.title.take(64))
                        put("subtitle", tx.subtitle.take(64))
                        put("amount_coins", tx.amountCoins)
                        put("type", tx.type)
                        put("created_at_ms", tx.createdAt)
                    }
                )
            }
        }
        val progressArray = JSONArray().apply {
            clipProgress
                .sortedByDescending { it.lastWatchedAt }
                .take(20)
                .forEach { prog ->
                    put(
                        JSONObject().apply {
                            put("clip_id", prog.clipId.take(48))
                            put("watch_count", prog.watchCount)
                            put("last_watched_at", prog.lastWatchedAt)
                            put("earned_coins_total", prog.earnedCoinsTotal)
                        }
                    )
                }
        }
        val questsArray = JSONArray().apply {
            claimedQuestIds.forEach { put(it) }
        }
        val clipCashState = JSONObject().apply {
            put("user_id", activeSession.userId)
            put("email", activeSession.email)
            put("coin_balance", wallet.coinBalance)
            put("total_earned_coins", wallet.totalEarnedCoins)
            put("streak_days", wallet.streakDays)
            put("last_check_in_date", wallet.lastCheckInDate)
            put("last_free_spin_date", wallet.lastFreeSpinDate)
            put("clips_watched_count", wallet.clipsWatchedCount)
            put("games_played_count", wallet.gamesPlayedCount)
            put("ads_watched_today", coinStatus.adsWatchedToday)
            put("ads_watched_date", adsWatchedDate)
            put("last_rewarded_ad_ms", coinStatus.lastRewardedAtMs)
            if (lastServerWalletBalance != null) {
                put("last_server_wallet_balance", lastServerWalletBalance)
            }
            put("updated_at_ms", System.currentTimeMillis())
            put("claimed_quests", questsArray)
            put("clip_progress", progressArray)
            put("recent_transactions", txArray)
        }
        val userData = JSONObject().apply {
            if (activeSession.fullName.isNotBlank()) put("full_name", activeSession.fullName)
            if (activeSession.username.isNotBlank()) put("username", activeSession.username)
            if (activeSession.nationality.isNotBlank()) put("nationality", activeSession.nationality)
            if (activeSession.phoneNumber.isNotBlank()) put("phone_number", activeSession.phoneNumber)
            put("clipcash_state", clipCashState)
        }
        val payload = JSONObject().apply {
            put("data", userData)
        }
        val req = newGatewayRequestBuilder(
            targetPath = "/auth/v1/user",
            accessToken = activeSession.accessToken
        )
            .addHeader("Content-Type", "application/json")
            .put(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (isJwtExpiredOrUnauthorized(resp.code, body)) return false to true
            return resp.isSuccessful to false
        }
    }

    private fun isJwtExpiredOrUnauthorized(code: Int, body: String): Boolean {
        return code == 401 ||
            (code == 403 && body.contains("jwt", ignoreCase = true)) ||
            body.contains("PGRST301") ||
            body.contains("JWT expired", ignoreCase = true)
    }

    private fun isMissingTableResponse(code: Int, body: String): Boolean {
        return code == 404 ||
            body.contains("PGRST205") ||
            (body.contains("relation", ignoreCase = true) && body.contains("does not exist", ignoreCase = true))
    }

    private suspend fun <T> executeAuthorizedRest(
        session: SupabaseSession,
        block: (SupabaseSession) -> Pair<SupabaseDbSyncResult<T>?, Boolean>
    ): SupabaseDbSyncResult<T> = withContext(Dispatchers.IO) {
        runCatching {
            val (firstResult, shouldRefresh) = block(session)
            if (!shouldRefresh && firstResult != null) return@withContext firstResult
            if (shouldRefresh) {
                val refreshed = refreshSessionIfNeeded(session)
                    ?: return@withContext SupabaseDbSyncResult.Error("Session expired. Please sign in again.")
                val (retryResult, _) = block(refreshed)
                return@withContext retryResult ?: SupabaseDbSyncResult.Error("Unauthorized")
            }
            SupabaseDbSyncResult.Error("Unknown sync error")
        }.getOrElse { err ->
            SupabaseDbSyncResult.Error(err.localizedMessage ?: "Network unavailable")
        }
    }

    private fun JSONObject.optCleanString(key: String): String {
        return optString(key, "").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
    }

    private fun parseSessionJson(json: JSONObject): SupabaseSession? {
        val accessToken = json.optString("access_token")
        val refreshToken = json.optString("refresh_token")
        val userObj = json.optJSONObject("user")
        val userId = userObj?.optString("id").orEmpty()
        val email = userObj?.optString("email").orEmpty()
        val metadata = userObj?.optJSONObject("user_metadata")
        val fullName = metadata?.optString("full_name").orEmpty().takeIf { it != "null" }.orEmpty()
        val username = metadata?.optString("username").orEmpty().takeIf { it != "null" }.orEmpty()
        val nationality = metadata?.optString("nationality").orEmpty().takeIf { it != "null" }.orEmpty()
        val phoneNumber = metadata?.optString("phone_number").orEmpty().takeIf { it != "null" }.orEmpty()
        if (accessToken.isBlank() || userId.isBlank()) return null
        return SupabaseSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            email = email,
            fullName = fullName,
            username = username,
            nationality = nationality,
            phoneNumber = phoneNumber
        )
    }

    private fun parseSupabaseError(rawBody: String, fallback: String): String {
        val raw = runCatching {
            val obj = JSONObject(rawBody)
            obj.optString("error_description").takeIf { it.isNotBlank() }
                ?: obj.optString("msg").takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("error").takeIf { it.isNotBlank() }
                ?: fallback
        }.getOrDefault(fallback)
        return raw
            .replace(Regex("(?i)supabase"), "ClipCash")
            .replace(Regex("(?i)postgrest?"), "ClipCash")
            .trim()
    }

    private fun unescapeHtml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun extractVideoIdFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        parsed.queryParameter("v")?.takeIf { it.isNotBlank() }?.let { return it }
        val segs = parsed.pathSegments.filter { it.isNotBlank() }
        return segs.lastOrNull()?.takeIf { it.isNotBlank() && it != "watch" }
    }

    private fun sanitizeClipCashText(input: String): String {
        return input
            .replace(Regex("(?i)youtube"), "ClipCash")
            .replace(Regex("(?i)youtu\\.be"), "clipcash.app")
            .trim()
    }
}
