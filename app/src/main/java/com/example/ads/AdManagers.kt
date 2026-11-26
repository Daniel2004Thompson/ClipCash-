package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class InterstitialManager(context: Context) {
    private val appContext = context.applicationContext
    private var ad: InterstitialAd? = null
    private var isLoading = false
    private var lastShownAt = 0L
    private val minIntervalMs = 3 * 60 * 1000L

    fun load() {
        if (ad != null || isLoading) return
        isLoading = true
        runCatching {
            InterstitialAd.load(
                appContext,
                AdIds.INTERSTITIAL,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(loaded: InterstitialAd) {
                        ad = loaded
                        isLoading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        ad = null
                        isLoading = false
                    }
                }
            )
        }.onFailure {
            ad = null
            isLoading = false
        }
    }

    fun show(activity: Activity, onDone: () -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) {
            onDone()
            return
        }
        val current = ad
        val tooSoon = System.currentTimeMillis() - lastShownAt < minIntervalMs
        if (current == null || tooSoon) {
            if (current == null) load()
            onDone()
            return
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                load()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                load()
                onDone()
            }
        }
        ad = null
        lastShownAt = System.currentTimeMillis()
        runCatching {
            current.show(activity)
        }.onFailure {
            load()
            onDone()
        }
    }
}

class RewardedManager(context: Context) {
    private val appContext = context.applicationContext
    private var ad: RewardedAd? = null
    private var isLoading = false

    fun load() {
        if (ad != null || isLoading) return
        isLoading = true
        runCatching {
            RewardedAd.load(
                appContext,
                AdIds.REWARDED,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(loaded: RewardedAd) {
                        ad = loaded
                        isLoading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        ad = null
                        isLoading = false
                    }
                }
            )
        }.onFailure {
            ad = null
            isLoading = false
        }
    }

    /** Returns false if no ad is ready yet. */
    fun show(activity: Activity, onReward: () -> Unit): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val current = ad ?: run {
            load()
            return false
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                load()
            }
        }
        ad = null
        return runCatching {
            current.show(activity) { onReward() }
            true
        }.getOrElse {
            load()
            false
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
