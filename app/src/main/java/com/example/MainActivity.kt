package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ads.findActivity
import com.example.model.ClipCatalog
import com.example.ui.components.ClipCashTopBar
import com.example.ui.components.RewardToastBanner
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChangeEmailScreen
import com.example.ui.screens.GamesScreen
import com.example.ui.screens.QuestsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.screens.WatchScreen
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.ClipCashTheme
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkMidnight
import com.example.ui.theme.DarkNavBar
import com.example.ui.theme.DarkTextMuted
import com.example.ui.theme.InkNavy
import com.example.viewmodel.ClipCashViewModel
import kotlinx.coroutines.delay

const val ROUTE_SPLASH = "splash"
const val ROUTE_MAIN = "main"
const val ROUTE_PLAYER = "player/{clipId}"
const val ROUTE_CHANGE_EMAIL = "change_email"

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    WATCH("Watch", Icons.Filled.PlayArrow, Icons.Outlined.PlayCircle, "nav_watch"),
    GAMES("Games", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports, "nav_games"),
    QUESTS("Quests", Icons.Filled.TrackChanges, Icons.Outlined.TrackChanges, "nav_quests"),
    WALLET("Wallet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_wallet")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            ClipCashTheme {
                ClipCashRoot()
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            (application as? ClipCashApp)?.adManager?.recordUserTouch(System.currentTimeMillis())
        }
        return super.dispatchTouchEvent(ev)
    }
}

@Composable
fun ClipCashRoot(
    viewModel: ClipCashViewModel = viewModel(factory = ClipCashViewModel.Factory)
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val adManager = remember(context) { (context.applicationContext as ClipCashApp).adManager }
    val isRewardedAdLoaded by adManager.isRewardedLoaded.collectAsStateWithLifecycle()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val rewardedCooldownSeconds = uiState.coinStatus.cooldownSecondsRemaining(nowMs)

    LaunchedEffect(uiState.coinStatus.lastRewardedAtMs) {
        nowMs = System.currentTimeMillis()
        while (uiState.coinStatus.cooldownSecondsRemaining(nowMs) > 0) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    DisposableEffect(lifecycleOwner, adManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    adManager.setAppInForeground(true)
                    viewModel.refreshWalletSyncIfStale()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    adManager.setAppInForeground(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            adManager.setAppInForeground(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.feedbackBanner, uiState.errorMessage) {
        if (uiState.feedbackBanner != null || uiState.errorMessage != null) {
            delay(3200L)
            viewModel.dismissBanner()
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_SPLASH
    ) {
        composable(ROUTE_SPLASH) {
            SplashScreen(
                onGetStarted = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(ROUTE_MAIN) {
            val session = uiState.auth.session
            if (session == null) {
                AuthScreen(
                    authState = uiState.auth,
                    onStageChange = viewModel::setAuthStage,
                    onSignIn = viewModel::signIn,
                    onVerifySignInCode = viewModel::verifySignInCode,
                    onResendSignInCode = viewModel::resendSignInCode,
                    onSignUp = viewModel::signUp,
                    onVerifySignUpCode = viewModel::verifySignUpCode,
                    onResendSignUpCode = viewModel::resendSignUpCode,
                    onRequestPasswordReset = viewModel::requestPasswordReset,
                    onVerifyAndResetPassword = viewModel::verifyAndResetPassword,
                    onResendPasswordResetCode = viewModel::resendPasswordResetCode,
                    onClearMessages = viewModel::clearAuthMessages
                )
            } else {
                var currentTab by rememberSaveable { mutableStateOf(MainTab.WATCH) }
                val readyQuestsCount = uiState.quests.count { it.isReadyToClaim }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWide = maxWidth >= 600.dp

                    Scaffold(
                        containerColor = DarkMidnight,
                        contentWindowInsets = WindowInsets.systemBars,
                        topBar = {
                            ClipCashTopBar(
                                coinBalance = uiState.wallet.coinBalance,
                                streakDays = uiState.wallet.streakDays,
                                onWalletClick = { currentTab = MainTab.WALLET }
                            )
                        },
                        bottomBar = {
                            if (!isWide) {
                                Column {
                                    HorizontalDivider(thickness = 1.dp, color = Color(0xFF1A2A50))
                                    NavigationBar(
                                        containerColor = DarkNavBar,
                                        windowInsets = WindowInsets.navigationBars,
                                        tonalElevation = 0.dp
                                    ) {
                                        MainTab.entries.forEach { tab ->
                                            val selected = currentTab == tab
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { currentTab = tab },
                                                colors = NavigationBarItemDefaults.colors(
                                                    indicatorColor = Color.Transparent,
                                                    selectedIconColor = CashEmerald,
                                                    selectedTextColor = CashEmerald,
                                                    unselectedIconColor = DarkTextMuted,
                                                    unselectedTextColor = DarkTextMuted
                                                ),
                                                icon = {
                                                    TabIconWithBadge(
                                                        tab = tab,
                                                        selected = selected,
                                                        badgeCount = if (tab == MainTab.QUESTS) readyQuestsCount else 0
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = tab.label,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                },
                                                modifier = Modifier.testTag(tab.testTag)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        ) {
                            if (isWide) {
                                NavigationRail(
                                    modifier = Modifier.fillMaxHeight(),
                                    containerColor = DarkNavBar
                                ) {
                                    MainTab.entries.forEach { tab ->
                                        val selected = currentTab == tab
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { currentTab = tab },
                                            colors = NavigationRailItemDefaults.colors(
                                                indicatorColor = Color.Transparent,
                                                selectedIconColor = CashEmerald,
                                                selectedTextColor = CashEmerald,
                                                unselectedIconColor = DarkTextMuted,
                                                unselectedTextColor = DarkTextMuted
                                            ),
                                            icon = {
                                                TabIconWithBadge(
                                                    tab = tab,
                                                    selected = selected,
                                                    badgeCount = if (tab == MainTab.QUESTS) readyQuestsCount else 0
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = tab.label,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            modifier = Modifier.testTag(tab.testTag)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .widthIn(max = 960.dp)
                                    ) {
                                        when (currentTab) {
                                            MainTab.WATCH -> WatchScreen(
                                                uiState = uiState,
                                                isWideLayout = isWide,
                                                isRewardedAdLoaded = isRewardedAdLoaded,
                                                rewardedCooldownSeconds = rewardedCooldownSeconds,
                                                onWatchRewardedAd = {
                                                    val currentNow = System.currentTimeMillis()
                                                    nowMs = currentNow
                                                    when {
                                                        uiState.coinStatus.isDailyCapReached() -> {
                                                            viewModel.reportAdMessage(
                                                                "Daily limit reached (${uiState.coinStatus.dailyCap}/day)."
                                                            )
                                                        }
                                                        uiState.coinStatus.cooldownSecondsRemaining(currentNow) > 0 -> {
                                                            val sec = uiState.coinStatus.cooldownSecondsRemaining(currentNow)
                                                            viewModel.reportAdMessage("Wait ${sec}s before watching another ad.")
                                                        }
                                                        else -> {
                                                            adManager.showRewardedAd(
                                                                activity = activity,
                                                                onCompleted = { viewModel.grantRewardedAdCoins() },
                                                                onNotCompleted = { reason -> viewModel.reportAdMessage(reason) }
                                                            )
                                                        }
                                                    }
                                                },
                                                onSearchQueryChange = viewModel::updateSearchQuery,
                                                onSubmitSearch = viewModel::submitSearch,
                                                onSelectCategory = viewModel::selectCategory,
                                                onLoadMoreClips = viewModel::loadMoreClips,
                                                onWatchClip = { clip ->
                                                    viewModel.resetWatchSession()
                                                    navController.navigate("player/${clip.id}")
                                                },
                                                bannerContent = activity?.let { act ->
                                                    { adManager.UnityBannerAd(activity = act) }
                                                }
                                            )
                                            MainTab.GAMES -> GamesScreen(
                                                uiState = uiState,
                                                onCoinCatcherFinished = { score ->
                                                    viewModel.recordCoinCatcherScore(score)
                                                    adManager.onGameAttemptCompleted(
                                                        activity = activity,
                                                        onAdCompleted = { viewModel.grantPostGamesAdCoins() }
                                                    )
                                                },
                                                onSpinWheelResult = { prizeCoins, usedFreeSpin ->
                                                    val spinAllowed = usedFreeSpin || uiState.wallet.coinBalance >= 25
                                                    viewModel.spinLuckyWheel(prizeCoins, usedFreeSpin)
                                                    if (spinAllowed) {
                                                        adManager.onGameAttemptCompleted(
                                                            activity = activity,
                                                            onAdCompleted = { viewModel.grantPostGamesAdCoins() }
                                                        )
                                                    }
                                                },
                                                onArcadeGameFinished = { gameTitle, coins ->
                                                    viewModel.recordArcadeGameResult(gameTitle, coins)
                                                    adManager.onGameAttemptCompleted(
                                                        activity = activity,
                                                        onAdCompleted = { viewModel.grantPostGamesAdCoins() }
                                                    )
                                                }
                                            )
                                            MainTab.QUESTS -> QuestsScreen(
                                                uiState = uiState,
                                                onClaimQuest = viewModel::claimQuest
                                            )
                                            MainTab.WALLET -> WalletScreen(
                                                uiState = uiState,
                                                onRedeemReward = viewModel::redeemReward,
                                                onSyncWallet = viewModel::refreshWalletSync,
                                                onChangeEmail = {
                                                    viewModel.resetChangeEmailFlow()
                                                    navController.navigate(ROUTE_CHANGE_EMAIL)
                                                },
                                                onSignOut = viewModel::signOut
                                            )
                                        }

                                        RewardToastBanner(
                                            feedback = uiState.feedbackBanner,
                                            errorMessage = uiState.errorMessage,
                                            onDismiss = viewModel::dismissBanner,
                                            modifier = Modifier.align(Alignment.TopCenter)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        composable(
            route = ROUTE_PLAYER,
            arguments = listOf(navArgument("clipId") { type = NavType.StringType })
        ) { backStackEntry ->
            val clipId = backStackEntry.arguments?.getString("clipId")
            val clip = uiState.clips.firstOrNull { it.id == clipId }
                ?: ClipCatalog.fallbackClips.firstOrNull { it.id == clipId }
                ?: uiState.clips.firstOrNull()
                ?: ClipCatalog.fallbackClips.first()

            VideoPlayerScreen(
                clip = clip,
                allClips = uiState.clips,
                isLoadingMore = uiState.isLoadingMoreClips,
                onLoadMoreClips = viewModel::loadMoreClips,
                onSelectNextClip = { nextClip ->
                    viewModel.resetWatchSession()
                    navController.navigate("player/${nextClip.id}") {
                        popUpTo(ROUTE_PLAYER) { inclusive = true }
                    }
                },
                onBack = {
                    viewModel.resetWatchSession()
                    navController.popBackStack()
                },
                feedbackBanner = uiState.feedbackBanner,
                errorMessage = uiState.errorMessage,
                onDismissBanner = viewModel::dismissBanner,
                onShortNaturalBreak = {
                    adManager.onShortWatchedBreak(
                        activity = activity,
                        onAdCompleted = { viewModel.grantPostShortsAdCoins() }
                    )
                }
            )
        }

        composable(ROUTE_CHANGE_EMAIL) {
            val currentEmail = uiState.auth.session?.email.orEmpty()
            ChangeEmailScreen(
                currentEmail = currentEmail,
                state = uiState.changeEmail,
                onSendCode = { newEmail ->
                    viewModel.requestEmailChange(newEmail, currentEmail)
                },
                onVerifyCode = { code ->
                    viewModel.verifyEmailChange(code) {
                        navController.popBackStack()
                    }
                },
                onResendCode = viewModel::resendEmailChangeCode,
                onBackToEnterEmail = viewModel::backToEnterNewEmail,
                onClearMessages = viewModel::clearChangeEmailMessages,
                onNavigateBack = {
                    viewModel.resetChangeEmailFlow()
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: MainTab,
    selected: Boolean,
    badgeCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    val iconContent: @Composable () -> Unit = {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(CashEmerald),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = InkNavy,
                    modifier = Modifier.size(19.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = tab.label,
                tint = DarkTextMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = CoralRed, contentColor = Color.White) {
                    Text("$badgeCount")
                }
            }
        ) {
            iconContent()
        }
    } else {
        iconContent()
    }
}
