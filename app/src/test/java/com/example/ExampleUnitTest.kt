package com.example

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
        assertEquals(0.06, ClipCatalog.coinsToUsd(100), 0.0001)
        assertTrue(ClipCatalog.coinsToUsd(ClipCatalog.MIN_WITHDRAW_COINS) >= 5.0)
        assertTrue(ClipCatalog.rewards.all { it.coinCost >= ClipCatalog.MIN_WITHDRAW_COINS })
    }

    @Test
    fun gatewayUrl_pointsToCentralPoint() {
        assertTrue(BuildConfig.SUPABASE_CENTRAL_POINT_URL.startsWith("https://"))
        assertTrue(BuildConfig.SUPABASE_CENTRAL_POINT_URL.endsWith("/functions/v1/central-point"))
    }
}
