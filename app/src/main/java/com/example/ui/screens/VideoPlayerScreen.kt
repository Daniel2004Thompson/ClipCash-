package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.example.model.VideoClip
import com.example.ui.components.CoinBadge
import com.example.ui.components.RewardToastBanner
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.CoinGold
import com.example.ui.theme.DarkMidnight
import com.example.ui.theme.ElectricBlue
import com.example.viewmodel.RewardFeedback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class ShortPlaybackState {
    LOADING,
    PLAYING,
    PAUSED,
    ERROR
}

private enum class PlayerEngineMode {
    NONE,
    MOBILE_WEB,
    DIRECT_MP4
}

@Composable
fun VideoPlayerScreen(
    clip: VideoClip,
    allClips: List<VideoClip>,
    isLoadingMore: Boolean,
    onLoadMoreClips: () -> Unit,
    onRewardEarned: (VideoClip) -> Unit,
    onSelectNextClip: (VideoClip) -> Unit,
    onBack: () -> Unit,
    feedbackBanner: RewardFeedback? = null,
    errorMessage: String? = null,
    onDismissBanner: () -> Unit = {},
    onVideoPlaying: (VideoClip) -> Unit = {},
    onVideoEnded: (VideoClip) -> Unit = {},
    onResetWatchSession: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val clips = remember(allClips, clip) {
        allClips.ifEmpty { listOf(clip) }
    }
    val startIndex = remember(clip.id) {
        clips.indexOfFirst { it.id == clip.id }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { clips.size }
    )
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val elapsedByClipId = remember { mutableStateMapOf<String, Int>() }
    val rewardedByClipId = remember { mutableStateMapOf<String, Boolean>() }

    val activeClip = clips.getOrNull(pagerState.settledPage) ?: clip
    val currentActiveClip by rememberUpdatedState(activeClip)
    val isRpcRewardClip = !activeClip.remoteUuid.isNullOrBlank() && activeClip.rewardEligible
    val activeRewardClaimed = rewardedByClipId[activeClip.id] == true
    val directMediaUrl = remember(activeClip.videoUrl) {
        activeClip.videoUrl?.takeIf { url ->
            val lower = url.lowercase()
            lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8") ||
                (!lower.contains("youtube.com") && !lower.contains("youtu.be") && lower.startsWith("http"))
        }
    }

    var playerReloadNonce by remember { mutableIntStateOf(0) }
    var useWebFallback by remember { mutableStateOf(false) }
    var isDeviceOnline by remember { mutableStateOf(isInternetAvailable(context)) }
    var activeNativePlayer by remember { mutableStateOf<YouTubePlayer?>(null) }
    var webController by remember { mutableStateOf<ClipShortsWebController?>(null) }

    var currentPlaybackState by remember { mutableStateOf(ShortPlaybackState.LOADING) }
    var hasStartedCurrentVideo by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }
    var showSlowLoadActions by remember { mutableStateOf(false) }
    var showTapPulse by remember { mutableStateOf(false) }

    val onPlayingUpdated by rememberUpdatedState(onVideoPlaying)
    val onEndedUpdated by rememberUpdatedState(onVideoEnded)
    val onResetSessionUpdated by rememberUpdatedState(onResetWatchSession)

    val isActivePlaying = currentPlaybackState == ShortPlaybackState.PLAYING &&
        !pagerState.isScrollInProgress

    // Reset session & check device connectivity whenever user settles on a new Short
    LaunchedEffect(activeClip.id, playerReloadNonce) {
        onResetSessionUpdated()
        isDeviceOnline = isInternetAvailable(context)
        useWebFallback = directMediaUrl != null
        activeNativePlayer = null
        hasStartedCurrentVideo = false
        showSlowLoadActions = false
        if (!isDeviceOnline) {
            currentPlaybackState = ShortPlaybackState.ERROR
            playerErrorMessage = "No internet connection detected on this device or emulator. Turn on Wi-Fi/mobile data and tap Retry."
        } else {
            currentPlaybackState = ShortPlaybackState.LOADING
            playerErrorMessage = null
        }
    }

    // Pause fallback WebView when app moves to background
    DisposableEffect(lifecycleOwner, webController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    webController?.onLifecyclePause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    webController?.onLifecycleResume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-prefetch next Shorts batches while user swipes vertically
    LaunchedEffect(pagerState.currentPage, clips.size, isLoadingMore) {
        if (pagerState.currentPage >= clips.size - 3 && !isLoadingMore) {
            onLoadMoreClips()
        }
    }

    // Load Mobile Web or Direct MP4 fallback whenever active
    LaunchedEffect(activeClip.videoId, directMediaUrl, useWebFallback, webController, isDeviceOnline) {
        if (useWebFallback && isDeviceOnline) {
            val controller = webController ?: return@LaunchedEffect
            hasStartedCurrentVideo = false
            currentPlaybackState = ShortPlaybackState.LOADING
            playerErrorMessage = null
            showSlowLoadActions = false
            if (directMediaUrl != null) {
                controller.loadDirectMedia(activeClip.videoId, directMediaUrl)
            } else {
                controller.loadVideo(activeClip.videoId)
            }
        }
    }

    // Multi-stage autoplay nudge & fallback watchdog:
    // 1) In native player: at 1.8s try muted autoplay nudge if browser blocked unmuted start; at 5.0s switch to Mobile Web
    // 2) In Mobile Web fallback: at 1.2s & 2.8s nudge autoplay with muted fallback; at 9.5s surface Retry / Next Short
    LaunchedEffect(
        activeClip.id,
        useWebFallback,
        hasStartedCurrentVideo,
        playerErrorMessage,
        playerReloadNonce,
        isDeviceOnline
    ) {
        if (!isDeviceOnline || hasStartedCurrentVideo || playerErrorMessage != null) {
            return@LaunchedEffect
        }
        if (!useWebFallback) {
            delay(1800L)
            if (!hasStartedCurrentVideo && playerErrorMessage == null) {
                activeNativePlayer?.let { player ->
                    isMuted = true
                    player.mute()
                    player.play()
                }
            }
            delay(3200L)
            if (!hasStartedCurrentVideo && playerErrorMessage == null) {
                useWebFallback = true
            }
        } else {
            delay(1200L)
            if (!hasStartedCurrentVideo && playerErrorMessage == null) {
                webController?.nudgeAutoplayWithMuteFallback()
            }
            delay(1600L)
            if (!hasStartedCurrentVideo && playerErrorMessage == null) {
                webController?.nudgeAutoplayWithMuteFallback()
            }
            delay(6500L)
            if (!hasStartedCurrentVideo && playerErrorMessage == null) {
                isDeviceOnline = isInternetAvailable(context)
                if (!isDeviceOnline) {
                    currentPlaybackState = ShortPlaybackState.ERROR
                    playerErrorMessage = "No internet connection detected on this device or emulator."
                } else {
                    showSlowLoadActions = true
                }
            }
        }
    }

    // Mark clip rewarded when server RPC toast confirms coins earned
    LaunchedEffect(feedbackBanner, activeClip.id) {
        if (feedbackBanner != null && feedbackBanner.coinsDelta > 0 && isRpcRewardClip) {
            rewardedByClipId[activeClip.id] = true
        }
    }

    // Active watch progress timer:
    // - For non-RPC unlimited Shorts, ticks toward watchTargetSeconds and awards +10 coins
    // - For public.videos RPC clips, updates visual ring progress until ENDED triggers complete_watch()
    LaunchedEffect(activeClip.id, isActivePlaying, activeRewardClaimed, isRpcRewardClip) {
        while (isActivePlaying && !activeRewardClaimed) {
            delay(100L)
            val updated = (elapsedByClipId[activeClip.id] ?: 0) + 100
            elapsedByClipId[activeClip.id] = updated
            if (!isRpcRewardClip && updated >= activeClip.watchTargetSeconds * 1000) {
                rewardedByClipId[activeClip.id] = true
                onRewardEarned(activeClip)
            }
        }
    }

    LaunchedEffect(showTapPulse) {
        if (showTapPulse) {
            delay(650L)
            showTapPulse = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkMidnight)
    ) {
        // 1. Primary Player: androidyoutubeplayer core 13.0.0 with explicit origin + lifecycle observer + fallback
        if (isDeviceOnline && !useWebFallback) {
            key(activeClip.id, playerReloadNonce) {
                val clipSnapshot = activeClip
                val playingCb by rememberUpdatedState {
                    if (currentActiveClip.id == clipSnapshot.id) {
                        currentPlaybackState = ShortPlaybackState.PLAYING
                        hasStartedCurrentVideo = true
                        playerErrorMessage = null
                        showSlowLoadActions = false
                        onPlayingUpdated(clipSnapshot)
                    }
                }
                val endedCb by rememberUpdatedState {
                    if (currentActiveClip.id == clipSnapshot.id) {
                        onEndedUpdated(clipSnapshot)
                    }
                }
                val pausedCb by rememberUpdatedState {
                    if (currentActiveClip.id == clipSnapshot.id && hasStartedCurrentVideo) {
                        currentPlaybackState = ShortPlaybackState.PAUSED
                    }
                }
                val bufferingCb by rememberUpdatedState {
                    if (currentActiveClip.id == clipSnapshot.id && !hasStartedCurrentVideo) {
                        currentPlaybackState = ShortPlaybackState.LOADING
                    }
                }
                val errorCb by rememberUpdatedState { _: PlayerConstants.PlayerError ->
                    if (currentActiveClip.id == clipSnapshot.id) {
                        useWebFallback = true
                        currentPlaybackState = ShortPlaybackState.LOADING
                        playerErrorMessage = null
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val options = IFramePlayerOptions.Builder(ctx)
                            .controls(0)
                            .rel(0)
                            .ivLoadPolicy(3)
                            .origin("https://www.youtube.com")
                            .build()
                        YouTubePlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            enableAutomaticInitialization = false
                            lifecycleOwner.lifecycle.addObserver(this)
                            initialize(object : AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    activeNativePlayer = youTubePlayer
                                    if (isMuted) youTubePlayer.mute() else youTubePlayer.unMute()
                                    youTubePlayer.loadVideo(clipSnapshot.videoId, 0f)
                                }

                                override fun onStateChange(
                                    youTubePlayer: YouTubePlayer,
                                    state: PlayerConstants.PlayerState
                                ) {
                                    when (state) {
                                        PlayerConstants.PlayerState.PLAYING -> playingCb()
                                        PlayerConstants.PlayerState.PAUSED -> pausedCb()
                                        PlayerConstants.PlayerState.BUFFERING -> bufferingCb()
                                        PlayerConstants.PlayerState.VIDEO_CUED -> {
                                            youTubePlayer.play()
                                        }
                                        PlayerConstants.PlayerState.ENDED -> {
                                            endedCb()
                                            youTubePlayer.seekTo(0f)
                                            youTubePlayer.play()
                                        }
                                        else -> Unit
                                    }
                                }

                                override fun onError(
                                    youTubePlayer: YouTubePlayer,
                                    error: PlayerConstants.PlayerError
                                ) {
                                    errorCb(error)
                                }
                            }, options)
                        }
                    },
                    onRelease = { playerView ->
                        lifecycleOwner.lifecycle.removeObserver(playerView)
                        runCatching { playerView.release() }
                        activeNativePlayer = null
                    }
                )
            }
        } else if (isDeviceOnline) {
            key(playerReloadNonce) {
                AndroidView(
                    factory = { ctx ->
                        lateinit var controller: ClipShortsWebController
                        controller = ClipShortsWebController(
                            context = ctx,
                            onPlaying = { videoId ->
                                if (videoId == currentActiveClip.videoId) {
                                    currentPlaybackState = ShortPlaybackState.PLAYING
                                    hasStartedCurrentVideo = true
                                    playerErrorMessage = null
                                    showSlowLoadActions = false
                                    onPlayingUpdated(currentActiveClip)
                                }
                            },
                            onPaused = { videoId ->
                                if (videoId == currentActiveClip.videoId && hasStartedCurrentVideo) {
                                    currentPlaybackState = ShortPlaybackState.PAUSED
                                }
                            },
                            onBuffering = { videoId ->
                                if (videoId == currentActiveClip.videoId && !hasStartedCurrentVideo) {
                                    currentPlaybackState = ShortPlaybackState.LOADING
                                }
                            },
                            onEnded = { videoId ->
                                if (videoId == currentActiveClip.videoId) {
                                    onEndedUpdated(currentActiveClip)
                                }
                            },
                            onMuteChanged = { muted ->
                                isMuted = muted
                            },
                            onTerminalError = { videoId, detail ->
                                if (videoId == currentActiveClip.videoId) {
                                    currentPlaybackState = ShortPlaybackState.ERROR
                                    isDeviceOnline = isInternetAvailable(context)
                                    playerErrorMessage = if (!isDeviceOnline) {
                                        "No internet connection detected on this device or emulator."
                                    } else {
                                        detail ?: "Stream blocked or unavailable on this network/emulator. Try Retry or Next Short."
                                    }
                                }
                            }
                        )
                        controller.webView.tag = controller
                        webController = controller
                        controller.webView
                    },
                    onRelease = { releasedView ->
                        val taggedController = releasedView.tag as? ClipShortsWebController
                        taggedController?.destroy()
                        if (webController === taggedController) {
                            webController = null
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. VerticalPager sits above the player with transparent pages once playing
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { index -> clips[index].id },
            modifier = Modifier
                .fillMaxSize()
                .testTag("shorts_vertical_pager")
        ) { pageIndex ->
            val pageClip = clips[pageIndex]
            val isSettledActive = pagerState.settledPage == pageIndex
            val elapsedMs = elapsedByClipId[pageClip.id] ?: 0
            val rewardClaimed = rewardedByClipId[pageClip.id] == true
            val isPagePlaying = isSettledActive && isActivePlaying
            val showThumbnailCover = !isSettledActive ||
                pagerState.isScrollInProgress ||
                (!hasStartedCurrentVideo && currentPlaybackState != ShortPlaybackState.PAUSED)

            ShortOverlayPage(
                clip = pageClip,
                pageIndex = pageIndex,
                isSettledActive = isSettledActive,
                isPlaying = isPagePlaying,
                isMuted = isSettledActive && isMuted,
                isDeviceOnline = isDeviceOnline,
                hasStartedVideo = isSettledActive && hasStartedCurrentVideo,
                showThumbnailCover = showThumbnailCover,
                showSlowLoadActions = isSettledActive && showSlowLoadActions,
                showTapPulse = isSettledActive && showTapPulse,
                playerErrorMessage = if (isSettledActive) playerErrorMessage else null,
                elapsedWatchMs = elapsedMs,
                rewardClaimed = rewardClaimed,
                isLoadingMore = isLoadingMore,
                onTapScreen = {
                    val nativePlayer = activeNativePlayer
                    val fallbackWeb = webController
                    if (!useWebFallback && nativePlayer != null) {
                        if (currentPlaybackState == ShortPlaybackState.PLAYING) {
                            if (isMuted) {
                                nativePlayer.unMute()
                                isMuted = false
                            } else {
                                nativePlayer.pause()
                                currentPlaybackState = ShortPlaybackState.PAUSED
                            }
                        } else {
                            nativePlayer.play()
                            if (isMuted) {
                                nativePlayer.unMute()
                                isMuted = false
                            }
                        }
                    } else if (useWebFallback && fallbackWeb != null) {
                        if (currentPlaybackState == ShortPlaybackState.PLAYING) {
                            if (isMuted) {
                                fallbackWeb.unmute()
                            } else {
                                fallbackWeb.pause()
                            }
                        } else if (!hasStartedCurrentVideo) {
                            fallbackWeb.nudgeAutoplayWithMuteFallback()
                        } else {
                            fallbackWeb.play()
                            if (isMuted) fallbackWeb.unmute()
                        }
                    } else {
                        playerReloadNonce += 1
                    }
                    showTapPulse = true
                },
                onToggleMute = {
                    val nativePlayer = activeNativePlayer
                    val fallbackWeb = webController
                    if (!useWebFallback && nativePlayer != null) {
                        val nextMuted = !isMuted
                        if (nextMuted) nativePlayer.mute() else nativePlayer.unMute()
                        isMuted = nextMuted
                    } else if (fallbackWeb != null) {
                        if (isMuted) fallbackWeb.unmute() else fallbackWeb.mute()
                    }
                },
                onRetry = {
                    playerReloadNonce += 1
                },
                onBack = onBack,
                onSwipePrevious = {
                    if (pageIndex > 0) {
                        scope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
                    }
                },
                onSwipeNext = {
                    if (pageIndex + 1 < clips.size) {
                        scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                    } else {
                        onLoadMoreClips()
                        if (clips.size == 1) {
                            onSelectNextClip(pageClip)
                        }
                    }
                }
            )
        }

        // 3. Feedback & Error Toast Overlay (e.g., "+N coins earned!", "No reward for this video", "watched too briefly")
        RewardToastBanner(
            feedback = feedbackBanner,
            errorMessage = errorMessage,
            onDismiss = onDismissBanner,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        )
    }
}

private fun isInternetAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
}

private class ClipShortsWebController(
    context: Context,
    private val onPlaying: (String) -> Unit,
    private val onPaused: (String) -> Unit,
    private val onBuffering: (String) -> Unit,
    private val onEnded: (String) -> Unit,
    private val onMuteChanged: (Boolean) -> Unit,
    private val onTerminalError: (String, String?) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeVideoId: String = ""
    private var engineMode: PlayerEngineMode = PlayerEngineMode.NONE
    private var isDestroyed: Boolean = false

    val webView: WebView = createConfiguredWebView(context)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(context: Context): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(AndroidColor.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            seedVideoCookies(this)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                userAgentString = userAgentString
                    .replace("; wv)", ")")
                    .replace("; wv;", ";")
            }

            addJavascriptInterface(ShortsJsBridge(), "ClipCashBridge")

            val emptyPoster = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): Bitmap = emptyPoster
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return true
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    if (scheme != "http" && scheme != "https") {
                        return true
                    }
                    if (engineMode == PlayerEngineMode.DIRECT_MP4) return false
                    val host = uri.host?.lowercase().orEmpty()
                    val allowedHost = host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("youtu.be") ||
                        host.endsWith("googlevideo.com") ||
                        host.endsWith("ytimg.com") ||
                        host.endsWith("google.com")
                    return !allowedHost
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (isDestroyed) return
                    val currentUrl = url.orEmpty()
                    if (currentUrl.contains("consent.youtube.com") || currentUrl.contains("consent.google.com")) {
                        injectConsentBypassScript()
                        return
                    }
                    if (engineMode == PlayerEngineMode.MOBILE_WEB && currentUrl.contains("youtube.com")) {
                        injectDomVideoController()
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (isDestroyed) return
                    if (request?.isForMainFrame == true) {
                        val msg = error?.description?.toString()?.takeIf { it.isNotBlank() }
                        onTerminalError(activeVideoId, msg)
                    }
                }
            }
        }
    }

    private fun seedVideoCookies(webView: WebView) {
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
            val socs = "SOCS=CAESHAgBEhJnd3NfMjAyMzA4MTAtMF9SQzIaAmVuIAEaBgiA_LyaBg; Domain=.youtube.com; Path=/; Secure; SameSite=None"
            val consent = "CONSENT=YES+cb.20210328-17-p0.en+FX+999; Domain=.youtube.com; Path=/; Secure; SameSite=None"
            cm.setCookie("https://www.youtube.com", socs)
            cm.setCookie("https://www.youtube.com", consent)
            cm.setCookie("https://m.youtube.com", socs)
            cm.setCookie("https://m.youtube.com", consent)
            cm.flush()
        }
    }

    fun loadVideo(videoId: String) {
        if (isDestroyed) return
        activeVideoId = videoId
        seedVideoCookies(webView)
        engineMode = PlayerEngineMode.MOBILE_WEB
        webView.loadUrl(
            "https://m.youtube.com/shorts/$videoId",
            mapOf("Referer" to "https://m.youtube.com/")
        )
    }

    fun loadDirectMedia(videoId: String, mediaUrl: String) {
        if (isDestroyed) return
        activeVideoId = videoId
        engineMode = PlayerEngineMode.DIRECT_MP4
        val escapedUrl = JSONObject.quote(mediaUrl)
        val escapedId = JSONObject.quote(videoId)
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
              <style>
                html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
                video { position:fixed; inset:0; width:100%; height:100%; object-fit:cover; background:#000; }
              </style>
            </head>
            <body>
              <video id="v" src=$escapedUrl autoplay playsinline webkit-playsinline></video>
              <script>
                (function() {
                  var v = document.getElementById('v');
                  window.__ccUserPaused = false;
                  function report() {
                    if (!window.ClipCashBridge) return;
                    var isPlaying = !v.paused && !v.ended && v.readyState >= 2 && v.currentTime > 0;
                    var isPaused = !!v.paused && window.__ccUserPaused;
                    window.ClipCashBridge.onPlayerVideoState($escapedId, isPlaying, isPaused, !!v.muted);
                  }
                  window.__ccTryAutoplay = function(allowMute) {
                    if (window.__ccUserPaused) return;
                    v.play().catch(function() {
                      if (allowMute) {
                        v.muted = true;
                        v.play().catch(function() {});
                        report();
                      }
                    });
                  };
                  window.__ccPlay = function() { window.__ccUserPaused = false; window.__ccTryAutoplay(true); report(); };
                  window.__ccPause = function() { window.__ccUserPaused = true; v.pause(); report(); };
                  window.__ccSetMuted = function(m) { v.muted = !!m; report(); };
                  v.addEventListener('playing', report);
                  v.addEventListener('pause', report);
                  v.addEventListener('timeupdate', function() { if (v.currentTime > 0 && !v.paused) report(); });
                  v.addEventListener('ended', function() {
                    window.ClipCashBridge && window.ClipCashBridge.onVideoEnded($escapedId);
                    v.currentTime = 0;
                    if (!window.__ccUserPaused) v.play().catch(function() {});
                  });
                  v.addEventListener('error', function() {
                    window.ClipCashBridge && window.ClipCashBridge.onPlayerBlocked($escapedId, 'Unable to stream video file.');
                  });
                  window.__ccTryAutoplay(true);
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://clipcash.app/", html, "text/html", "utf-8", null)
    }

    fun play() {
        if (isDestroyed) return
        evalJs("if (window.__ccPlay) window.__ccPlay();")
    }

    fun pause() {
        if (isDestroyed) return
        evalJs("if (window.__ccPause) window.__ccPause();")
    }

    fun mute() {
        if (isDestroyed) return
        evalJs("if (window.__ccSetMuted) window.__ccSetMuted(true);")
    }

    fun unmute() {
        if (isDestroyed) return
        evalJs("if (window.__ccSetMuted) window.__ccSetMuted(false);")
    }

    fun nudgeAutoplayWithMuteFallback() {
        if (isDestroyed) return
        evalJs("if (window.__ccTryAutoplay) window.__ccTryAutoplay(true);")
    }

    fun onLifecyclePause() {
        if (isDestroyed) return
        pause()
        webView.onPause()
    }

    fun onLifecycleResume() {
        if (isDestroyed) return
        webView.onResume()
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeJavascriptInterface("ClipCashBridge")
            webView.clearHistory()
            webView.destroy()
        }
    }

    private fun evalJs(script: String) {
        if (isDestroyed) return
        mainHandler.post {
            if (!isDestroyed) {
                webView.evaluateJavascript(script, null)
            }
        }
    }

    private fun injectConsentBypassScript() {
        evalJs(
            """
            (function() {
                var btns = document.querySelectorAll('form button, button');
                for (var i = 0; i < btns.length; i++) {
                    var txt = (btns[i].innerText || btns[i].getAttribute('aria-label') || '').toLowerCase();
                    if (txt.indexOf('accept') !== -1 || txt.indexOf('agree') !== -1 || txt.indexOf('reject') !== -1) {
                        btns[i].click();
                        return;
                    }
                }
            })();
            """.trimIndent()
        )
    }

    private fun injectDomVideoController() {
        val targetId = activeVideoId
        val escapedId = JSONObject.quote(targetId)
        val js = """
            (function() {
                var modeKey = $escapedId + '_mobile';
                if (window.__ccInjectedKey === modeKey) return;
                window.__ccInjectedKey = modeKey;

                var style = document.getElementById('cc-shorts-clean-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'cc-shorts-clean-style';
                    style.textContent = `
                        html, body {
                            background: #000000 !important;
                            overflow: hidden !important;
                            margin: 0 !important;
                            padding: 0 !important;
                            height: 100vh !important;
                            width: 100vw !important;
                        }
                        ytm-mobile-topbar-renderer,
                        ytm-pivot-bar-renderer,
                        ytm-upsell-dialog-renderer,
                        .mobile-topbar-header,
                        .upsell-dialog,
                        ytm-bottom-sheet-renderer,
                        ytm-reel-player-overlay-renderer,
                        reel-player-header-renderer,
                        .reel-player-overlay-actions,
                        .reel-player-overlay-metadata,
                        .ytm-autonav-bar,
                        ytm-watch-below-the-player,
                        ytm-engagement-panel-section-list-renderer {
                            display: none !important;
                            opacity: 0 !important;
                            pointer-events: none !important;
                        }
                        video {
                            position: fixed !important;
                            top: 0 !important;
                            left: 0 !important;
                            width: 100vw !important;
                            height: 100vh !important;
                            object-fit: cover !important;
                            background: #000000 !important;
                        }
                    `;
                    document.head && document.head.appendChild(style);
                }

                window.__ccUserPaused = false;
                var reportedError = false;

                function findVideo() {
                    var vids = document.querySelectorAll('video');
                    for (var i = 0; i < vids.length; i++) {
                        if (vids[i].src || vids[i].currentSrc || vids[i].readyState > 0) return vids[i];
                    }
                    return vids[0] || null;
                }

                function detectBlockReason() {
                    var bodyText = (document.body && document.body.innerText || '').toLowerCase();
                    if (bodyText.indexOf('confirm you\u2019re not a bot') !== -1 ||
                        bodyText.indexOf("confirm you're not a bot") !== -1) {
                        return 'Video streaming is blocked by network bot-verification on this emulator IP. Try Retry or Next Short, or test on a real device.';
                    }
                    if (bodyText.indexOf('video unavailable') !== -1 ||
                        bodyText.indexOf("this video isn't available") !== -1) {
                        return 'This Short is unavailable in this region. Tap Next Short.';
                    }
                    return null;
                }

                function reportState(v) {
                    if (!window.ClipCashBridge) return;
                    var isPlaying = v ? (!v.paused && !v.ended && v.readyState >= 2 && v.currentTime > 0) : false;
                    var isPaused = v ? (!!v.paused && window.__ccUserPaused) : window.__ccUserPaused;
                    var isMuted = v ? !!v.muted : false;
                    window.ClipCashBridge.onPlayerVideoState($escapedId, isPlaying, isPaused, isMuted);
                }

                window.__ccTryAutoplay = function(allowMuteFallback) {
                    if (window.__ccUserPaused) return;
                    var playBtn = document.querySelector('.ytp-large-play-button, button[aria-label*="Play"]');
                    if (playBtn && playBtn.offsetParent !== null) {
                        try { playBtn.click(); } catch (e) {}
                    }
                    var v = findVideo();
                    if (v) {
                        v.playsInline = true;
                        v.loop = false;
                        var p = v.play();
                        if (p && p.catch) {
                            p.catch(function() {
                                if (allowMuteFallback) {
                                    v.muted = true;
                                    v.play().catch(function() {});
                                    reportState(v);
                                }
                            });
                        }
                    }
                };

                window.__ccPlay = function() {
                    window.__ccUserPaused = false;
                    window.__ccTryAutoplay(true);
                    reportState(findVideo());
                };

                window.__ccPause = function() {
                    window.__ccUserPaused = true;
                    var v = findVideo();
                    if (v) v.pause();
                    reportState(v);
                };

                window.__ccSetMuted = function(shouldMute) {
                    var v = findVideo();
                    if (v) {
                        v.muted = !!shouldMute;
                        if (!shouldMute && v.volume === 0) v.volume = 1.0;
                    }
                    reportState(v);
                };

                if (window.__ccPollTimer) {
                    clearInterval(window.__ccPollTimer);
                }
                window.__ccPollTimer = setInterval(function() {
                    if (window.__ccInjectedKey !== modeKey) {
                        clearInterval(window.__ccPollTimer);
                        return;
                    }
                    var v = findVideo();
                    if (v) {
                        if (!v.__ccBound) {
                            v.__ccBound = true;
                            v.loop = false;
                            v.addEventListener('playing', function() { reportState(v); });
                            v.addEventListener('pause', function() { reportState(v); });
                            v.addEventListener('ended', function() {
                                window.ClipCashBridge && window.ClipCashBridge.onVideoEnded($escapedId);
                                v.currentTime = 0;
                                if (!window.__ccUserPaused) {
                                    v.play().catch(function() {});
                                }
                            });
                            v.addEventListener('timeupdate', function() {
                                if (v.currentTime > 0 && !v.paused) {
                                    reportState(v);
                                }
                            });
                        }
                        if (v.paused && !window.__ccUserPaused) {
                            window.__ccTryAutoplay(true);
                        }
                        reportState(v);
                    } else if (!reportedError) {
                        var reason = detectBlockReason();
                        if (reason) {
                            reportedError = true;
                            clearInterval(window.__ccPollTimer);
                            window.ClipCashBridge && window.ClipCashBridge.onPlayerBlocked($escapedId, reason);
                        }
                    }
                }, 260);

                window.__ccTryAutoplay(true);
            })();
        """.trimIndent()
        evalJs(js)
    }

    private inner class ShortsJsBridge {
        @JavascriptInterface
        fun onVideoEnded(videoId: String) {
            mainHandler.post {
                if (isDestroyed) return@post
                onEnded.invoke(videoId)
            }
        }

        @JavascriptInterface
        fun onPlayerBlocked(videoId: String, reason: String) {
            mainHandler.post {
                if (isDestroyed) return@post
                onTerminalError(videoId, reason)
            }
        }

        @JavascriptInterface
        fun onPlayerVideoState(
            videoId: String,
            isPlaying: Boolean,
            isPaused: Boolean,
            muted: Boolean
        ) {
            mainHandler.post {
                if (isDestroyed) return@post
                onMuteChanged(muted)
                when {
                    isPlaying -> onPlaying(videoId)
                    isPaused -> onPaused(videoId)
                    else -> onBuffering(videoId)
                }
            }
        }
    }
}

@Composable
private fun ShortOverlayPage(
    clip: VideoClip,
    pageIndex: Int,
    isSettledActive: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    isDeviceOnline: Boolean,
    hasStartedVideo: Boolean,
    showThumbnailCover: Boolean,
    showSlowLoadActions: Boolean,
    showTapPulse: Boolean,
    playerErrorMessage: String?,
    elapsedWatchMs: Int,
    rewardClaimed: Boolean,
    isLoadingMore: Boolean,
    onTapScreen: () -> Unit,
    onToggleMute: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit
) {
    val isRpcReward = !clip.remoteUuid.isNullOrBlank() && clip.rewardEligible
    val targetSeconds = if (isRpcReward) clip.durationSeconds.coerceAtLeast(clip.watchTargetSeconds) else clip.watchTargetSeconds
    val targetMs = targetSeconds * 1000
    val progressRatio = (elapsedWatchMs.toFloat() / targetMs.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progressRatio, label = "short_watch_ring")

    Box(modifier = Modifier.fillMaxSize()) {
        // Cover thumbnail only during swipes / initial buffering so live video shines through once started
        if (showThumbnailCover) {
            val coverContext = LocalContext.current
            val coverRequest = remember(clip.thumbnailUrl) {
                ImageRequest.Builder(coverContext)
                    .data(clip.thumbnailUrl)
                    .crossfade(true)
                    .size(CoilSize.ORIGINAL)
                    .build()
            }
            AsyncImage(
                model = coverRequest,
                contentDescription = clip.title,
                placeholder = painterResource(id = clip.fallbackThumbResId),
                error = painterResource(id = clip.fallbackThumbResId),
                fallback = painterResource(id = clip.fallbackThumbResId),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
            if (isSettledActive && playerErrorMessage == null && !showSlowLoadActions) {
                CircularProgressIndicator(
                    color = CashEmerald,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center)
                )
            }
        }

        // Top & Bottom cinematic readability scrims
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // Transparent tap layer: consumes single taps for Play/Pause while VerticalPager owns vertical swipes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTapScreen
                )
        )

        // Tap / Paused center feedback
        AnimatedVisibility(
            visible = showTapPulse || (isSettledActive && hasStartedVideo && !isPlaying && playerErrorMessage == null),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Paused" else "Play Short",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Slow load or Playback Error actionable overlay
        if (playerErrorMessage != null || showSlowLoadActions) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.86f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isDeviceOnline) {
                        Icon(
                            imageVector = Icons.Filled.WifiOff,
                            contentDescription = "No Internet Connection",
                            tint = CoinGold,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = playerErrorMessage
                            ?: "Connected to internet, but this Short is taking longer than usual to stream. Tap Retry or Next Short.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                        Button(
                            onClick = onSwipeNext,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Next Short")
                        }
                    }
                }
            }
        }

        // Top Bar: Back + Live Watch-to-Earn Ring + Unlimited Counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .testTag("player_back_button")
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to ClipCash Feed",
                    tint = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.25f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = if (rewardClaimed) CashEmerald else CoinGold,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    if (rewardClaimed) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Reward Claimed",
                            tint = CashEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        val secLeft = (((targetMs - elapsedWatchMs).coerceAtLeast(0)) / 1000) + 1
                        Text(
                            text = "${secLeft}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Column {
                    Text(
                        text = when {
                            !clip.rewardEligible -> "Featured Short"
                            rewardClaimed -> "Earned +${clip.rewardCoins} Coins!"
                            else -> "Earning +${clip.rewardCoins} Coins"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (rewardClaimed) CashEmerald else Color.White
                    )
                    Text(
                        text = when {
                            !isDeviceOnline -> "No internet connection"
                            rewardClaimed -> "Swipe up for next Short"
                            isPlaying && isRpcReward -> "Finish Short #${pageIndex + 1} to claim"
                            isPlaying -> "Watching Short #${pageIndex + 1}"
                            isLoadingMore -> "Loading more Shorts..."
                            else -> "Loading Short..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }

        // Right-Side Shorts Vertical Action Rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (rewardClaimed) CashEmerald.copy(alpha = 0.28f)
                            else Color.Black.copy(alpha = 0.5f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CoinBadge(sizeDp = 28)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        !clip.rewardEligible -> "Watch"
                        rewardClaimed -> "Paid"
                        else -> "+${clip.rewardCoins}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rewardClaimed) CashEmerald else Color.White
                )
            }

            ShortsRailButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (isPlaying) "Pause" else "Play",
                containerColor = ElectricBlue.copy(alpha = 0.85f),
                testTag = "player_play_pause",
                onClick = onTapScreen
            )

            ShortsRailButton(
                icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                label = if (isMuted) "Muted" else "Sound",
                containerColor = if (isMuted) CoinGold.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.5f),
                testTag = "shorts_mute_button",
                onClick = onToggleMute
            )

            ShortsRailButton(
                icon = Icons.Filled.KeyboardArrowUp,
                label = "Prev",
                containerColor = Color.Black.copy(alpha = 0.5f),
                enabled = pageIndex > 0,
                testTag = "shorts_prev_button",
                onClick = onSwipePrevious
            )

            ShortsRailButton(
                icon = Icons.Filled.KeyboardArrowDown,
                label = "Next",
                containerColor = Color.Black.copy(alpha = 0.5f),
                testTag = "watch_next_button",
                onClick = onSwipeNext
            )

            ShortsRailButton(
                icon = Icons.Filled.GridView,
                label = "Feed",
                containerColor = Color.Black.copy(alpha = 0.5f),
                testTag = "shorts_feed_button",
                onClick = onBack
            )
        }

        // Bottom-Left Shorts Info + Celebration
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 86.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = rewardClaimed,
                enter = fadeIn() + scaleIn()
            ) {
                Surface(
                    shape = CircleShape,
                    color = CashEmerald.copy(alpha = 0.24f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CoinBadge(sizeDp = 18)
                        Text(
                            text = "+${clip.rewardCoins} Coins Credited!",
                            style = MaterialTheme.typography.labelLarge,
                            color = CashEmerald
                        )
                    }
                }
            }

            if (isMuted && isPlaying) {
                Surface(
                    shape = CircleShape,
                    color = CoinGold.copy(alpha = 0.24f),
                    modifier = Modifier.clickable(onClick = onToggleMute)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Tap to Unmute",
                            tint = CoinGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Tap for Sound",
                            style = MaterialTheme.typography.labelMedium,
                            color = CoinGold
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.58f)
            ) {
                Text(
                    text = "Source: ClipCash · Short #${pageIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CashEmerald,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Text(
                text = clip.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${clip.sourceLabel} · ${clip.description}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Bottom edge Shorts progress bar
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            color = if (rewardClaimed) CashEmerald else ElectricBlue,
            trackColor = Color.White.copy(alpha = 0.22f)
        )
    }
}

@Composable
private fun ShortsRailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    enabled: Boolean = true,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .testTag(testTag)
                .size(48.dp)
                .clip(CircleShape)
                .background(if (enabled) containerColor else Color.Black.copy(alpha = 0.25f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
        )
    }
}
