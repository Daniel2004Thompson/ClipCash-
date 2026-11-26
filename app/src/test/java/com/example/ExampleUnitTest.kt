package com.example

import com.example.ads.AdManager
import com.example.data.CoinRepository
import com.example.data.CoinRewardStatus
import com.example.model.ClipCatalog
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
        assertEquals(60_000L, AdManager.INTERSTITIAL_MIN_GAP_MS)
        assertEquals(3_000L, AdManager.INTERSTITIAL_TOUCH_GUARD_MS)
    }

    @Test
    fun coinRewardStatus_enforcesCooldownAndDailyCap() {
        assertEquals(10, CoinRepository.REWARD_COINS_PER_AD)
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
}
