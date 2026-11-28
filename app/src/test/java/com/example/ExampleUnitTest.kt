package com.example

import com.example.ads.AdManager
import com.example.data.CoinRepository
import com.example.data.CoinRewardStatus
import com.example.model.ClipCatalog
import com.example.model.ClipCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun catalog_hasValidClipsQuestsAndRewards() {
        assertEquals(3, ClipCatalog.fallbackClips.size)
        assertTrue(ClipCatalog.fallbackClips.all {
            it.rewardCoins == 10 && it.videoId.isNotBlank() && it.watchTargetSeconds > 0
        })
        assertTrue(ClipCatalog.fallbackClips.none {
            it.sourceLabel.contains("youtube", ignoreCase = true)
        })
        assertFalse(ClipCatalog.quests.isEmpty())
        assertFalse(ClipCatalog.rewards.isEmpty())
        assertTrue(ClipCatalog.rewards.all {
            it.coinCost > 0 && it.title.isNotBlank() && it.perkLabel.isNotBlank() && it.badge.isNotBlank()
        })
        assertEquals(ClipCatalog.rewards.size, ClipCatalog.rewards.map { it.id }.distinct().size)
    }

    @Test
    fun gatewayUrl_pointsToCentralPoint() {
        assertTrue(BuildConfig.SUPABASE_CENTRAL_POINT_URL.startsWith("https://"))
        assertTrue(BuildConfig.SUPABASE_CENTRAL_POINT_URL.endsWith("/functions/v1/central-point"))
    }

    @Test
    fun unityAdManager_constantsMatchConfiguration() {
        assertEquals("800455563", AdManager.UNITY_GAME_ID)
        assertEquals("BP_Banner_Android", AdManager.BANNER_PLACEMENT_ID)
        assertEquals("BP_Interstitial_Android", AdManager.INTERSTITIAL_PLACEMENT_ID)
        assertEquals("BP_Rewarded_Android", AdManager.REWARDED_PLACEMENT_ID)
        assertFalse(AdManager.UNITY_TEST_MODE)
        assertTrue(AdManager.BANNER_TOUCH_WALL)
        assertEquals(3, AdManager.INTERSTITIAL_SHORTS_INTERVAL)
        assertEquals(2, AdManager.INTERSTITIAL_GAMES_INTERVAL)
        assertEquals(60_000L, AdManager.INTERSTITIAL_MIN_GAP_MS)
        assertEquals(3_000L, AdManager.INTERSTITIAL_TOUCH_GUARD_MS)
    }

    @Test
    fun coinRewardStatus_enforcesCooldownAndDailyCap() {
        assertEquals(10, CoinRepository.REWARD_COINS_PER_AD)
        assertEquals(30, CoinRepository.POST_SHORTS_AD_COINS)
        assertEquals(30, CoinRepository.POST_GAMES_AD_COINS)
        assertEquals(30_000L, CoinRepository.REWARDED_COOLDOWN_MS)
        assertEquals(20, CoinRepository.DAILY_REWARDED_CAP)

        val initial = CoinRewardStatus(balance = 0, adsWatchedToday = 0, lastRewardedAtMs = 0L)
        assertTrue(initial.canWatchRewardedAd(nowMs = 100_000L))
        assertEquals(0, initial.cooldownSecondsRemaining(nowMs = 100_000L))

        val onCooldown = CoinRewardStatus(balance = 10, adsWatchedToday = 1, lastRewardedAtMs = 100_000L)
        assertFalse(onCooldown.canWatchRewardedAd(nowMs = 110_000L))
        assertEquals(20, onCooldown.cooldownSecondsRemaining(nowMs = 110_000L))
        assertTrue(onCooldown.canWatchRewardedAd(nowMs = 130_000L))

        val capped = CoinRewardStatus(balance = 200, adsWatchedToday = 20, lastRewardedAtMs = 100_000L)
        assertTrue(capped.isDailyCapReached())
        assertFalse(capped.canWatchRewardedAd(nowMs = 500_000L))
    }

    @Test
    fun coinSyncSnapshot_tracksUserUuidAndEmailBinding() {
        val emptySnapshot = com.example.data.CoinSyncSnapshot()
        assertEquals("", emptySnapshot.ownerUserId)
        assertEquals("", emptySnapshot.ownerEmail)
        assertEquals(null, emptySnapshot.lastSyncedBalance)
        assertFalse(emptySnapshot.pendingCloudPush)

        val syncedSnapshot = com.example.data.CoinSyncSnapshot(
            ownerUserId = "550e8400-e29b-41d4-a716-446655440000",
            ownerEmail = "player@example.com",
            lastSyncedBalance = 190,
            pendingCloudPush = true,
            adsWatchedDate = "2026-11-27"
        )
        assertEquals("550e8400-e29b-41d4-a716-446655440000", syncedSnapshot.ownerUserId)
        assertEquals("player@example.com", syncedSnapshot.ownerEmail)
        assertEquals(190, syncedSnapshot.lastSyncedBalance)
        assertTrue(syncedSnapshot.pendingCloudPush)
        assertEquals("2026-11-27", syncedSnapshot.adsWatchedDate)

        val syncedStatus = CoinRewardStatus(
            balance = 190,
            adsWatchedToday = 2,
            lastRewardedAtMs = 500_000L
        )
        assertEquals(2, syncedStatus.adsWatchedToday)
        assertEquals(190, syncedStatus.balance)
    }

    @Test
    fun errorSanitizer_hidesBackendHostAndServiceNamesWhenOfflineOrFailing() {
        val dnsError = "Unable to resolve host \"vidvozabnlngqhnxrhmf.supabase.co\": No address associated with hostname"
        val sanitizedDns = com.example.data.SupabaseClient.sanitizeUserErrorMessage(dnsError)
        assertEquals(com.example.data.NetworkMonitor.OFFLINE_ERROR_MESSAGE, sanitizedDns)
        assertFalse(sanitizedDns.contains("supabase", ignoreCase = true))
        assertFalse(sanitizedDns.contains("vidvozabnlngqhnxrhmf", ignoreCase = true))

        val genericBackendError =
            "Postgrest error at https://vidvozabnlngqhnxrhmf.supabase.co/rest/v1/users via gotrue"
        val sanitizedGeneric = com.example.data.SupabaseClient.sanitizeUserErrorMessage(genericBackendError)
        assertFalse(sanitizedGeneric.contains("supabase", ignoreCase = true))
        assertFalse(sanitizedGeneric.contains("postgrest", ignoreCase = true))
        assertFalse(sanitizedGeneric.contains("gotrue", ignoreCase = true))
        assertFalse(sanitizedGeneric.contains("vidvozabnlngqhnxrhmf", ignoreCase = true))
        assertFalse(sanitizedGeneric.contains("https://", ignoreCase = true))

        assertTrue(com.example.viewmodel.AuthUiState().isOnline)
        assertTrue(com.example.viewmodel.ChangeEmailUiState().isOnline)
        assertTrue(com.example.viewmodel.ClipCashUiState().isOnline)
    }

    @Test
    fun clipCatalog_includesRequestedCategoriesAnd1080pHdQuality() {
        val labels = ClipCategory.entries.map { it.label }
        assertTrue(labels.contains("Football"))
        assertTrue(labels.contains("Music"))
        assertTrue(labels.contains("Movies"))
        assertTrue(labels.contains("Food"))
        assertTrue(labels.contains("Sports"))
        assertTrue(labels.contains("Lifestyle"))
        assertTrue(ClipCatalog.fallbackClips.all { it.qualityLabel == "1080p HD" })
    }

    @Test
    fun authAndChangeEmailStages_showVerificationCodeBeforeNewCredentials() {
        val authStages = com.example.viewmodel.AuthStage.entries
        assertTrue(
            authStages.indexOf(com.example.viewmodel.AuthStage.VERIFY_RESET_PASSWORD) <
                authStages.indexOf(com.example.viewmodel.AuthStage.RESET_NEW_PASSWORD)
        )

        val changeEmailStages = com.example.viewmodel.ChangeEmailStage.entries
        assertEquals(com.example.viewmodel.ChangeEmailStage.VERIFY_CURRENT_EMAIL, changeEmailStages.first())
        assertTrue(
            changeEmailStages.indexOf(com.example.viewmodel.ChangeEmailStage.VERIFY_CURRENT_EMAIL) <
                changeEmailStages.indexOf(com.example.viewmodel.ChangeEmailStage.ENTER_NEW_EMAIL)
        )
        assertEquals(
            com.example.viewmodel.ChangeEmailStage.VERIFY_CURRENT_EMAIL,
            com.example.viewmodel.ChangeEmailUiState().stage
        )
    }
}
