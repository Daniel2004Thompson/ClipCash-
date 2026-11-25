package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.components.CoinBadge
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.CoinAmber
import com.example.ui.theme.CoinGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkMidnight
import com.example.ui.theme.DeepEmerald
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.InkNavy
import com.example.viewmodel.ClipCashUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.random.Random

private val WHEEL_PRIZES = listOf(25, 100, 15, 250, 40, 75, 10, 50)
private val WHEEL_COLORS = listOf(
    ElectricBlue,
    CashEmerald,
    CoinGold,
    DeepRoyalBlue,
    Color(0xFF00B4D8),
    DeepEmerald,
    CoinAmber,
    Color(0xFF3F51B5)
)

@Composable
fun GamesScreen(
    uiState: ClipCashUiState,
    onCoinCatcherFinished: (Int) -> Unit,
    onSpinWheelResult: (prizeCoins: Int, usedFreeSpin: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCatcherDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Arcade Header Banner
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(ElectricBlue, DeepRoyalBlue))
                        )
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.clipcash_logo),
                        contentDescription = "ClipCash Arcade",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Play & Earn Arcade",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Play Coin Catcher or spin the Lucky Wheel to stack instant coins.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
            }
        }

        // Mini-game 1: Coin Catcher Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, DarkBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CashEmerald.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SportsEsports,
                                    contentDescription = null,
                                    tint = CashEmerald
                                )
                            }
                            Column {
                                Text(
                                    text = "Coin Catcher Blitz",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "15s Reflex Game · Up to +250 Coins",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Tap falling gold coins (+10) and green cash bills (+25) before they drop! Avoid red hazard bombs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = { showCatcherDialog = true },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CashEmerald,
                            contentColor = InkNavy
                        ),
                        modifier = Modifier
                            .testTag("start_catcher_button")
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play Coin Catcher (Free)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Mini-game 2: Lucky Spin Wheel Card
        item {
            LuckySpinCard(
                coinBalance = uiState.wallet.coinBalance,
                isFreeSpinAvailable = uiState.isFreeSpinAvailable,
                onSpinComplete = onSpinWheelResult
            )
        }
    }

    if (showCatcherDialog) {
        CoinCatcherDialog(
            onDismiss = { showCatcherDialog = false },
            onSaveScore = { score ->
                onCoinCatcherFinished(score)
                showCatcherDialog = false
            }
        )
    }
}

@Composable
private fun LuckySpinCard(
    coinBalance: Int,
    isFreeSpinAvailable: Boolean,
    onSpinComplete: (prizeCoins: Int, usedFreeSpin: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    var targetRotation by remember { mutableFloatStateOf(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var lastPrize by remember { mutableIntStateOf(0) }

    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
        label = "wheel_spin"
    )

    val canAffordSpin = isFreeSpinAvailable || coinBalance >= 25

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DarkBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CoinGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Casino,
                            contentDescription = null,
                            tint = CoinAmber
                        )
                    }
                    Column {
                        Text("Lucky Cash Wheel", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (isFreeSpinAvailable) "1 Free Daily Spin Ready!" else "Bonus Spin · 25 Coins",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isFreeSpinAvailable) CashEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Pointer + Wheel
            Box(
                modifier = Modifier.size(236.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val diameter = size.minDimension - 16.dp.toPx()
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f + 6.dp.toPx())
                    val wheelSize = Size(diameter, diameter)
                    val radius = diameter / 2f
                    val center = Offset(topLeft.x + radius, topLeft.y + radius)

                    // Outer rim
                    drawCircle(
                        color = InkNavy,
                        radius = radius + 6.dp.toPx(),
                        center = center
                    )

                    rotate(degrees = animatedRotation, pivot = center) {
                        val sweep = 360f / WHEEL_PRIZES.size
                        WHEEL_PRIZES.forEachIndexed { index, prize ->
                            val startAngle = -90f - (sweep / 2f) + index * sweep
                            drawArc(
                                color = WHEEL_COLORS[index % WHEEL_COLORS.size],
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = true,
                                topLeft = topLeft,
                                size = wheelSize
                            )
                            drawWheelLabel(
                                textMeasurer = textMeasurer,
                                label = "+$prize",
                                center = center,
                                radius = radius * 0.65f,
                                angleDeg = -90f + index * sweep
                            )
                        }
                    }

                    // Center hub
                    drawCircle(color = Color.White, radius = 22.dp.toPx(), center = center)
                    drawCircle(color = CoinGold, radius = 15.dp.toPx(), center = center)

                    // Top pointer triangle
                    val pointerPath = Path().apply {
                        moveTo(center.x, topLeft.y + 8.dp.toPx())
                        lineTo(center.x - 12.dp.toPx(), topLeft.y - 12.dp.toPx())
                        lineTo(center.x + 12.dp.toPx(), topLeft.y - 12.dp.toPx())
                        close()
                    }
                    drawPath(pointerPath, color = CoralRed)
                }
            }

            if (lastPrize > 0 && !isSpinning) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Last Spin Won +$lastPrize Coins!",
                    style = MaterialTheme.typography.labelLarge,
                    color = CashEmerald
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    if (isSpinning || !canAffordSpin) return@Button
                    val useFree = isFreeSpinAvailable
                    isSpinning = true
                    val targetIndex = Random.nextInt(WHEEL_PRIZES.size)
                    val prize = WHEEL_PRIZES[targetIndex]
                    val currentMod = ((targetRotation % 360f) + 360f) % 360f
                    val desiredMod = (360f - targetIndex * (360f / WHEEL_PRIZES.size)) % 360f
                    val delta = ((desiredMod - currentMod) + 360f) % 360f + (5 * 360f)
                    targetRotation += delta

                    scope.launch {
                        delay(2600L)
                        lastPrize = prize
                        isSpinning = false
                        onSpinComplete(prize, useFree)
                    }
                },
                enabled = !isSpinning && canAffordSpin,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .testTag("spin_wheel_button")
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = when {
                        isSpinning -> "Spinning..."
                        isFreeSpinAvailable -> "Spin Free Now!"
                        coinBalance >= 25 -> "Spin Again (25 Coins)"
                        else -> "Need 25 Coins to Spin"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWheelLabel(
    textMeasurer: TextMeasurer,
    label: String,
    center: Offset,
    radius: Float,
    angleDeg: Float
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val x = center.x + (radius * kotlin.math.cos(rad)).toFloat()
    val y = center.y + (radius * kotlin.math.sin(rad)).toFloat()
    val measured = textMeasurer.measure(
        text = label,
        style = TextStyle(
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(x - measured.size.width / 2f, y - measured.size.height / 2f)
    )
}

private enum class FallingKind { COIN, CASH_BILL, BOMB }

private data class FallingItem(
    val id: Int,
    val xFraction: Float,
    val yPx: Float,
    val speedPxPerTick: Float,
    val kind: FallingKind
)

@Composable
private fun CoinCatcherDialog(
    onDismiss: () -> Unit,
    onSaveScore: (Int) -> Unit
) {
    val density = LocalDensity.current
    var timeLeftSec by remember { mutableIntStateOf(15) }
    var score by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(true) }
    val items = remember { mutableStateListOf<FallingItem>() }
    var nextId by remember { mutableIntStateOf(1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = DarkMidnight
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoinBadge(sizeDp = 24)
                        Text(
                            text = "Score: $score",
                            style = MaterialTheme.typography.titleLarge,
                            color = CoinGold
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (timeLeftSec <= 5) CoralRed else ElectricBlue
                    ) {
                        Text(
                            text = "${timeLeftSec}s",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close game", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(10.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF0E1D42), Color(0xFF071026)))
                        )
                ) {
                    val widthPx = with(density) { maxWidth.toPx() }
                    val heightPx = with(density) { maxHeight.toPx() }
                    val hitRadiusPx = with(density) { 42.dp.toPx() }

                    // Countdown timer
                    LaunchedEffect(isRunning) {
                        while (isRunning && timeLeftSec > 0) {
                            delay(1000L)
                            timeLeftSec -= 1
                            if (timeLeftSec <= 0) {
                                isRunning = false
                            }
                        }
                    }

                    // Physics & spawner loop
                    LaunchedEffect(isRunning, heightPx) {
                        var ticks = 0
                        while (isRunning) {
                            delay(32L)
                            ticks++
                            if (ticks % 12 == 0) {
                                val roll = Random.nextFloat()
                                val kind = when {
                                    roll < 0.62f -> FallingKind.COIN
                                    roll < 0.85f -> FallingKind.CASH_BILL
                                    else -> FallingKind.BOMB
                                }
                                items.add(
                                    FallingItem(
                                        id = nextId++,
                                        xFraction = 0.12f + Random.nextFloat() * 0.76f,
                                        yPx = -20f,
                                        speedPxPerTick = with(density) { (4.5f + Random.nextFloat() * 3.5f).dp.toPx() },
                                        kind = kind
                                    )
                                )
                            }
                            for (i in items.indices.reversed()) {
                                val item = items[i]
                                val nextY = item.yPx + item.speedPxPerTick
                                if (nextY > heightPx + 40f) {
                                    items.removeAt(i)
                                } else {
                                    items[i] = item.copy(yPx = nextY)
                                }
                            }
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isRunning, widthPx) {
                                detectTapGestures { tapOffset ->
                                    if (!isRunning) return@detectTapGestures
                                    for (i in items.indices.reversed()) {
                                        val item = items[i]
                                        val cx = item.xFraction * widthPx
                                        val cy = item.yPx
                                        if (hypot(tapOffset.x - cx, tapOffset.y - cy) <= hitRadiusPx) {
                                            when (item.kind) {
                                                FallingKind.COIN -> score += 10
                                                FallingKind.CASH_BILL -> score += 25
                                                FallingKind.BOMB -> score = (score - 15).coerceAtLeast(0)
                                            }
                                            items.removeAt(i)
                                            break
                                        }
                                    }
                                }
                            }
                    ) {
                        items.forEach { item ->
                            val cx = item.xFraction * size.width
                            val cy = item.yPx
                            when (item.kind) {
                                FallingKind.COIN -> {
                                    drawCircle(color = CoinGold, radius = 22.dp.toPx(), center = Offset(cx, cy))
                                    drawCircle(color = CoinAmber, radius = 15.dp.toPx(), center = Offset(cx, cy))
                                }
                                FallingKind.CASH_BILL -> {
                                    val w = 44.dp.toPx()
                                    val h = 26.dp.toPx()
                                    drawRect(
                                        color = CashEmerald,
                                        topLeft = Offset(cx - w / 2f, cy - h / 2f),
                                        size = Size(w, h)
                                    )
                                    drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(cx, cy))
                                }
                                FallingKind.BOMB -> {
                                    drawCircle(color = CoralRed, radius = 20.dp.toPx(), center = Offset(cx, cy))
                                    drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(cx, cy))
                                }
                            }
                        }
                    }

                    if (!isRunning) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Time's Up!",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "You caught $score Coins!",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = CashEmerald
                                )
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = { onSaveScore(score) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CashEmerald,
                                        contentColor = InkNavy
                                    ),
                                    modifier = Modifier
                                        .testTag("claim_catcher_reward_button")
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Collect +$score Coins", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
