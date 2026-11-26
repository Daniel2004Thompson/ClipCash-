package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unity3d.ads.AdExpiredListener
import com.unity3d.ads.InitializationConfiguration
import com.unity3d.ads.InitializationListener
import com.unity3d.ads.InterstitialAd
import com.unity3d.ads.InterstitialShowListener
import com.unity3d.ads.LoadConfiguration
import com.unity3d.ads.LoadListener
import com.unity3d.ads.LogLevel
import com.unity3d.ads.RewardedAd
import com.unity3d.ads.RewardedShowListener
import com.unity3d.ads.ShowConfiguration
import com.unity3d.ads.ShowFinishState
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsError
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdManager(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isRewardedLoaded = MutableStateFlow(false)
    val isRewardedLoaded: StateFlow<Boolean> = _isRewardedLoaded.asStateFlow()

    private val _isInterstitialLoaded = MutableStateFlow(false)
    val isInterstitialLoaded: StateFlow<Boolean> = _isInterstitialLoaded.asStateFlow()

    @Volatile
    private var initStarted = false

    @Volatile
    private var isAppInForeground = false

    @Volatile
    private var lastUserTouchAtMs = 0L

    @Volatile
    private var isShowingFullscreenAd = false

    private var loadedInterstitial: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var shortsWatchedSinceLastInterstitial = 0
    private var lastInterstitialShownAtMs = 0L

    private var loadedRewarded: RewardedAd? = null
    private var isLoadingRewarded = false

    fun initialize(context: Context, hasUserConsent: Boolean = true) {
        if (initStarted) return
        synchronized(this) {
            if (initStarted) return
            initStarted = true
        }

        val appContext = context.applicationContext
        runCatching {
            // General audience (not child-directed): configure user privacy consent before initialization.
            @Suppress("DEPRECATION")
            UnityAds.userConsent = hasUserConsent
            @Suppress("DEPRECATION")
            UnityAds.userOptOut = !hasUserConsent
            @Suppress("DEPRECATION")
            UnityAds.nonBehavioral = false

            val config = InitializationConfiguration.Builder(UNITY_GAME_ID)
                .withContext(appContext)
                .withTestMode(UNITY_TEST_MODE)
                .withLogLevel(LogLevel.INFO)
                .build()

            UnityAds.initialize(config, InitializationListener { error ->
                runOnMain {
                    if (error == null) {
                        _isInitialized.value = true
                        preloadInterstitial()
                        preloadRewarded()
                    } else {
                        _isInitialized.value = false
                        initStarted = false
                        Log.w(TAG, "Unity Ads init failed (${error.code}): ${error.message}")
                    }
                }
            })
        }.onFailure { throwable ->
            initStarted = false
            _isInitialized.value = false
            Log.w(TAG, "Unity Ads init threw exception", throwable)
        }
    }

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (inForeground && _isInitialized.value) {
            runOnMain {
                if (loadedInterstitial == null && !isLoadingInterstitial) {
                    preloadInterstitial()
                }
                if (loadedRewarded == null && !isLoadingRewarded) {
                    preloadRewarded()
                }
            }
        }
    }

    fun recordUserTouch(timestampMs: Long = clock()) {
        lastUserTouchAtMs = timestampMs
    }

    fun preloadInterstitial() {
        runOnMain {
            if (!_isInitialized.value && !UnityAds.isInitialized) return@runOnMain
            if (loadedInterstitial != null || isLoadingInterstitial) return@runOnMain

            isLoadingInterstitial = true
            runCatching {
                val request = LoadConfiguration.Builder(INTERSTITIAL_PLACEMENT_ID).build()
                InterstitialAd.load(request, object : LoadListener<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd?, error: UnityAdsError?) {
                        runOnMain {
                            isLoadingInterstitial = false
                            if (error == null && ad != null) {
                                ad.onAdExpired = AdExpiredListener {
                                    runOnMain {
                                        if (loadedInterstitial === ad) {
                                            loadedInterstitial = null
                                            _isInterstitialLoaded.value = false
                                            preloadInterstitial()
                                        }
                                    }
                                }
                                loadedInterstitial = ad
                                _isInterstitialLoaded.value = true
                            } else {
                                loadedInterstitial = null
                                _isInterstitialLoaded.value = false
                                Log.w(
                                    TAG,
                                    "Interstitial load failed (${error?.code}): ${error?.message ?: "no fill"}"
                                )
                            }
                        }
                    }
                })
            }.onFailure { throwable ->
                isLoadingInterstitial = false
                loadedInterstitial = null
                _isInterstitialLoaded.value = false
                Log.w(TAG, "Interstitial load threw exception", throwable)
            }
        }
    }

    fun preloadRewarded() {
        runOnMain {
            if (!_isInitialized.value && !UnityAds.isInitialized) return@runOnMain
            if (loadedRewarded != null || isLoadingRewarded) return@runOnMain

            isLoadingRewarded = true
            runCatching {
                val request = LoadConfiguration.Builder(REWARDED_PLACEMENT_ID).build()
                RewardedAd.load(request, object : LoadListener<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd?, error: UnityAdsError?) {
                        runOnMain {
                            isLoadingRewarded = false
                            if (error == null && ad != null) {
                                ad.onAdExpired = AdExpiredListener {
                                    runOnMain {
                                        if (loadedRewarded === ad) {
                                            loadedRewarded = null
                                            _isRewardedLoaded.value = false
                                            preloadRewarded()
                                        }
                                    }
                                }
                                loadedRewarded = ad
                                _isRewardedLoaded.value = true
                            } else {
                                loadedRewarded = null
                                _isRewardedLoaded.value = false
                                Log.w(
                                    TAG,
                                    "Rewarded load failed (${error?.code}): ${error?.message ?: "no fill"}"
                                )
                            }
                        }
                    }
                })
            }.onFailure { throwable ->
                isLoadingRewarded = false
                loadedRewarded = null
                _isRewardedLoaded.value = false
                Log.w(TAG, "Rewarded load threw exception", throwable)
            }
        }
    }

    /**
     * Call only when a YouTube Short finishes playing on its own (video ENDED state).
     * Never call on a user swipe or tap, mid-playback, on app launch, or in background.
     */
    fun onShortWatchedBreak(activity: Activity?, onDone: () -> Unit = {}) {
        runOnMain {
            shortsWatchedSinceLastInterstitial += 1
            val nowMs = clock()

            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                onDone()
                return@runOnMain
            }
            if (!isAppInForeground || isShowingFullscreenAd) {
                onDone()
                return@runOnMain
            }
            if (shortsWatchedSinceLastInterstitial < INTERSTITIAL_SHORTS_INTERVAL) {
                if (loadedInterstitial == null) preloadInterstitial()
                onDone()
                return@runOnMain
            }
            if (nowMs - lastInterstitialShownAtMs < INTERSTITIAL_MIN_GAP_MS) {
                if (loadedInterstitial == null) preloadInterstitial()
                onDone()
                return@runOnMain
            }
            if (lastUserTouchAtMs > 0L && nowMs - lastUserTouchAtMs < INTERSTITIAL_TOUCH_GUARD_MS) {
                if (loadedInterstitial == null) preloadInterstitial()
                onDone()
                return@runOnMain
            }

            val ad = loadedInterstitial
            if (ad == null) {
                preloadInterstitial()
                onDone()
                return@runOnMain
            }

            loadedInterstitial = null
            _isInterstitialLoaded.value = false
            isShowingFullscreenAd = true
            shortsWatchedSinceLastInterstitial = 0
            lastInterstitialShownAtMs = nowMs

            var finished = false
            fun finishOnce() {
                if (!finished) {
                    finished = true
                    isShowingFullscreenAd = false
                    preloadInterstitial()
                    onDone()
                }
            }

            runCatching {
                val showConfig = ShowConfiguration.Builder().build()
                ad.show(activity, showConfig, object : InterstitialShowListener {
                    override fun onStarted(ad: InterstitialAd) = Unit

                    override fun onClicked(ad: InterstitialAd) = Unit

                    override fun onCompleted(ad: InterstitialAd, state: ShowFinishState) {
                        runOnMain { finishOnce() }
                    }

                    override fun onFailed(ad: InterstitialAd, error: UnityAdsError) {
                        Log.w(TAG, "Interstitial show failed (${error.code}): ${error.message}")
                        runOnMain { finishOnce() }
                    }
                })
            }.onFailure { throwable ->
                Log.w(TAG, "Interstitial show threw exception", throwable)
                finishOnce()
            }
        }
    }

    /**
     * Shows the preloaded Unity rewarded ad and invokes [onCompleted] ONLY when the finish
     * state is [ShowFinishState.COMPLETED]. Skipped, failed, or tapped ads do not grant coins.
     */
    fun showRewardedAd(
        activity: Activity?,
        onCompleted: () -> Unit,
        onNotCompleted: (String) -> Unit
    ) {
        runOnMain {
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                onNotCompleted("Unable to open ad right now.")
                return@runOnMain
            }
            if (!isAppInForeground || isShowingFullscreenAd) {
                onNotCompleted("Another ad is already active.")
                return@runOnMain
            }
            val ad = loadedRewarded
            if (ad == null) {
                preloadRewarded()
                onNotCompleted("Rewarded ad is still loading. Please try again.")
                return@runOnMain
            }

            loadedRewarded = null
            _isRewardedLoaded.value = false
            isShowingFullscreenAd = true

            var finished = false
            fun completeSuccess() {
                if (!finished) {
                    finished = true
                    isShowingFullscreenAd = false
                    preloadRewarded()
                    onCompleted()
                }
            }

            fun completeFailure(reason: String) {
                if (!finished) {
                    finished = true
                    isShowingFullscreenAd = false
                    preloadRewarded()
                    onNotCompleted(reason)
                }
            }

            runCatching {
                val showConfig = ShowConfiguration.Builder().build()
                ad.show(activity, showConfig, object : RewardedShowListener {
                    override fun onStarted(ad: RewardedAd) = Unit

                    override fun onClicked(ad: RewardedAd) = Unit

                    override fun onRewarded(ad: RewardedAd) {
                        // Coin grant is strictly gated on ShowFinishState.COMPLETED in onCompleted.
                    }

                    override fun onCompleted(ad: RewardedAd, state: ShowFinishState) {
                        runOnMain {
                            if (state == ShowFinishState.COMPLETED) {
                                completeSuccess()
                            } else {
                                completeFailure("Ad was skipped. Finish the full ad to earn 10 coins.")
                            }
                        }
                    }

                    override fun onFailed(ad: RewardedAd, error: UnityAdsError) {
                        Log.w(TAG, "Rewarded show failed (${error.code}): ${error.message}")
                        runOnMain {
                            completeFailure("Rewarded ad could not finish playing. Please try again.")
                        }
                    }
                })
            }.onFailure { throwable ->
                Log.w(TAG, "Rewarded show threw exception", throwable)
                completeFailure("Rewarded ad is unavailable right now.")
            }
        }
    }

    @Composable
    fun UnityBannerAd(
        activity: Activity,
        modifier: Modifier = Modifier
    ) {
        val sdkReady by isInitialized.collectAsStateWithLifecycle()
        if (!sdkReady) return

        val bannerView = remember(activity) {
            BannerView(
                activity,
                BANNER_PLACEMENT_ID,
                UnityBannerSize(BANNER_WIDTH_DP, BANNER_HEIGHT_DP)
            ).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                listener = object : BannerView.IListener {
                    override fun onBannerLoaded(bannerAdView: BannerView?) = Unit

                    override fun onBannerShown(bannerAdView: BannerView?) = Unit

                    override fun onBannerClick(bannerAdView: BannerView?) = Unit

                    override fun onBannerFailedToLoad(
                        bannerAdView: BannerView?,
                        errorInfo: BannerErrorInfo?
                    ) {
                        Log.w(
                            TAG,
                            "Banner failed to load (${errorInfo?.errorCode}): ${errorInfo?.errorMessage}"
                        )
                    }

                    override fun onBannerLeftApplication(bannerAdView: BannerView?) = Unit
                }
                runCatching { load() }
            }
        }

        DisposableEffect(bannerView) {
            onDispose {
                runCatching { bannerView.destroy() }
            }
        }

        Box(
            modifier = modifier
                .size(width = BANNER_WIDTH_DP.dp, height = BANNER_HEIGHT_DP.dp)
                .testTag("unity_banner_container")
        ) {
            AndroidView(
                factory = { bannerView },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("unity_banner_view")
            )

            if (BANNER_TOUCH_WALL) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .testTag("unity_banner_touch_wall")
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        private const val TAG = "ClipCashUnityAds"

        const val UNITY_GAME_ID = "800455563"
        const val BANNER_PLACEMENT_ID = "BP_Banner_Android"
        const val INTERSTITIAL_PLACEMENT_ID = "BP_Interstitial_Android"
        const val REWARDED_PLACEMENT_ID = "BP_Rewarded_Android"

        const val UNITY_TEST_MODE = false
        const val BANNER_TOUCH_WALL = true

        const val BANNER_WIDTH_DP = 320
        const val BANNER_HEIGHT_DP = 50
        const val INTERSTITIAL_SHORTS_INTERVAL = 3
        const val INTERSTITIAL_MIN_GAP_MS = 60_000L
        const val INTERSTITIAL_TOUCH_GUARD_MS = 3_000L
    }
}
