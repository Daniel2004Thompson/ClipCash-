package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.ui.theme.DarkNavBar
import com.example.ui.theme.DarkTextMuted
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AdMobManager {
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        initScope.launch {
            runCatching {
                MobileAds.initialize(appContext) {}
            }
        }
    }
}

class InterstitialAdController {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var naturalBreakCount = 0
    private var lastShownAtMs = 0L

    fun preload(context: Context) {
        if (interstitialAd != null || isLoading) return
        isLoading = true
        val appContext = context.applicationContext
        runCatching {
            InterstitialAd.load(
                appContext,
                BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isLoading = false
                    }
                }
            )
        }.onFailure {
            isLoading = false
        }
    }

    /**
     * Non-incentivized natural-break interstitial: never grants coins or rewards,
     * only shows after every 3rd finished clip/game AND >= 90s cooldown.
     */
    fun maybeShowAtNaturalBreak(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        naturalBreakCount++

        val now = System.currentTimeMillis()
        val cooldownPassed = (now - lastShownAtMs) >= INTERSTITIAL_COOLDOWN_MS
        val frequencyMatched = (naturalBreakCount % NATURAL_BREAK_INTERVAL) == 0

        if (!frequencyMatched || !cooldownPassed) {
            preload(activity)
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            preload(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                lastShownAtMs = System.currentTimeMillis()
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                preload(activity)
            }
        }
        lastShownAtMs = now
        interstitialAd = null
        runCatching {
            ad.show(activity)
        }.onFailure {
            preload(activity)
        }
    }

    private companion object {
        const val NATURAL_BREAK_INTERVAL = 3
        const val INTERSTITIAL_COOLDOWN_MS = 90_000L
    }
}

@Composable
fun ClipCashBannerAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAdLoaded by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkNavBar)
            .testTag("admob_banner_container")
    ) {
        val adWidthDp = maxWidth.value.toInt().coerceIn(320, 728)

        val adView = remember(adWidthDp) {
            AdView(context).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        context,
                        adWidthDp
                    )
                )
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        isAdLoaded = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isAdLoaded = false
                    }
                }
                runCatching {
                    loadAd(AdRequest.Builder().build())
                }
            }
        }

        DisposableEffect(lifecycleOwner, adView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> runCatching { adView.resume() }
                    Lifecycle.Event.ON_PAUSE -> runCatching { adView.pause() }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                runCatching { adView.destroy() }
            }
        }

        AnimatedVisibility(visible = isAdLoaded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 1.dp, color = Color(0xFF1A2A50))
                Text(
                    text = "Sponsored",
                    fontSize = 9.sp,
                    color = DarkTextMuted,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { adView },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
