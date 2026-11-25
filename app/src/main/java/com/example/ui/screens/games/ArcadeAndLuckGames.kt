package com.example.ui.screens.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrightCyanBlue
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.CoinAmber
import com.example.ui.theme.CoinGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DeepEmerald
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.InkNavy
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.random.Random

// ---------------------------------------------------------------------------
// 5. Balloon Pop Rush
// ---------------------------------------------------------------------------

private enum class BalloonKind { REGULAR, GOLDEN, SPIKED }

private data class BalloonEntity(
    val id: Int,
    val xFraction: Float,
    val yPx: Float,
    val speedPx: Float,
    val color: Color,
    val kind: BalloonKind
)

@Composable
fun BalloonPopDialog(
    onDismiss: () -> Unit,
    onCollect: (Int) -> Unit
) {
    val density = LocalDensity.current
    var timeLeftSec by remember { mutableIntStateOf(15) }
    var score by remember { mutableIntStateOf(0) }
    var poppedCount by remember { mutableIntStateOf(0) }
    val balloons = remember { mutableStateListOf<BalloonEntity>() }
    var nextId by remember { mutableIntStateOf(1) }

    val isFinished = timeLeftSec <= 0
    val balloonPalette = remember { listOf(ElectricBlue, CashEmerald, CoralRed, BrightCyanBlue) }

    LaunchedEffect(Unit) {
        while (timeLeftSec > 0) {
            delay(1000L)
            timeLeftSec--
        }
    }

    GameDialogScaffold(
        title = "Balloon Pop Rush",
        scoreText = "$score Coins",
        statusText = "${timeLeftSec}s",
        statusColor = if (timeLeftSec <= 5) CoralRed else CoralRed.copy(alpha = 0.85f),
        isFinished = isFinished,
        finishTitle = "Sky Rush Over!",
        finishSubtitle = "Popped $poppedCount balloons!",
        earnedCoins = score.coerceAtMost(180),
        collectButtonTag = "collect_balloon_pop_button",
        onDismiss = onDismiss,
        onCollect = onCollect
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DeepRoyalBlue, Color(0xFF071128))))
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val hitRadiusPx = with(density) { 48.dp.toPx() }

            LaunchedEffect(isFinished, heightPx) {
                var ticks = 0
                while (!isFinished) {
                    delay(32L)
                    ticks++
                    if (ticks % 11 == 0) {
                        val roll = Random.nextFloat()
                        val kind = when {
                            roll < 0.65f -> BalloonKind.REGULAR
                            roll < 0.86f -> BalloonKind.GOLDEN
                            else -> BalloonKind.SPIKED
                        }
                        val color = when (kind) {
                            BalloonKind.REGULAR -> balloonPalette.random()
                            BalloonKind.GOLDEN -> CoinGold
                            BalloonKind.SPIKED -> Color(0xFF23283B)
                        }
                        balloons.add(
                            BalloonEntity(
                                id = nextId++,
                                xFraction = 0.14f + Random.nextFloat() * 0.72f,
                                yPx = heightPx + 30f,
                                speedPx = with(density) { (4.5f + Random.nextFloat() * 3.2f).dp.toPx() },
                                color = color,
                                kind = kind
                            )
                        )
                    }
                    for (i in balloons.indices.reversed()) {
                        val b = balloons[i]
                        val nextY = b.yPx - b.speedPx
                        if (nextY < -60f) {
                            balloons.removeAt(i)
                        } else {
                            balloons[i] = b.copy(yPx = nextY)
                        }
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isFinished, widthPx) {
                        detectTapGestures { tap ->
                            if (isFinished) return@detectTapGestures
                            for (i in balloons.indices.reversed()) {
                                val b = balloons[i]
                                val cx = b.xFraction * widthPx
                                val cy = b.yPx
                                if (hypot(tap.x - cx, tap.y - cy) <= hitRadiusPx) {
                                    when (b.kind) {
                                        BalloonKind.REGULAR -> {
                                            score = (score + 10).coerceAtMost(180)
                                            poppedCount++
                                        }
                                        BalloonKind.GOLDEN -> {
                                            score = (score + 25).coerceAtMost(180)
                                            poppedCount++
                                        }
                                        BalloonKind.SPIKED -> {
                                            score = (score - 10).coerceAtLeast(0)
                                        }
                                    }
                                    balloons.removeAt(i)
                                    break
                                }
                            }
                        }
                    }
            ) {
                balloons.forEach { b ->
                    val cx = b.xFraction * size.width
                    val cy = b.yPx
                    val w = 44.dp.toPx()
                    val h = 54.dp.toPx()

                    // String
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(cx, cy + h / 2f),
                        end = Offset(cx + 4.dp.toPx(), cy + h / 2f + 22.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    // Balloon body
                    drawOval(
                        color = b.color,
                        topLeft = Offset(cx - w / 2f, cy - h / 2f),
                        size = Size(w, h)
                    )
                    // Highlight or hazard center
                    when (b.kind) {
                        BalloonKind.SPIKED -> {
                            drawCircle(
                                color = CoralRed,
                                radius = 7.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                        BalloonKind.GOLDEN -> {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.55f),
                                radius = 6.dp.toPx(),
                                center = Offset(cx - 8.dp.toPx(), cy - 10.dp.toPx())
                            )
                        }
                        BalloonKind.REGULAR -> {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.4f),
                                radius = 5.dp.toPx(),
                                center = Offset(cx - 7.dp.toPx(), cy - 9.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. Reaction Flash
// ---------------------------------------------------------------------------

private enum class ReflexPhase { WAITING, READY, TOO_EARLY, ROUND_RESULT }

@Composable
fun ReactionFlashDialog(
    onDismiss: () -> Unit,
    onCollect: (Int) -> Unit
) {
    val totalRounds = 5
    var round by remember { mutableIntStateOf(1) }
    var phase by remember { mutableStateOf(ReflexPhase.WAITING) }
    var readyTimestampMs by remember { mutableLongStateOf(0L) }
    var lastReactionMs by remember { mutableIntStateOf(0) }
    val recordedTimes = remember { mutableStateListOf<Int>() }
    var triggerToken by remember { mutableIntStateOf(0) }

    val isFinished = recordedTimes.size >= totalRounds
    val avgMs = if (recordedTimes.isEmpty()) 0 else recordedTimes.average().toInt()
    val rewardCoins = if (isFinished) {
        when {
            avgMs <= 270 -> 100
            avgMs <= 350 -> 80
            avgMs <= 450 -> 60
            else -> 40
        }
    } else {
        0
    }

    LaunchedEffect(round, triggerToken, isFinished) {
        if (isFinished) return@LaunchedEffect
        phase = ReflexPhase.WAITING
        delay(Random.nextLong(1100L, 2800L))
        if (!isFinished) {
            readyTimestampMs = System.currentTimeMillis()
            phase = ReflexPhase.READY
        }
    }

    val padColor = when (phase) {
        ReflexPhase.WAITING -> Color(0xFF8E2438)
        ReflexPhase.READY -> CashEmerald
        ReflexPhase.TOO_EARLY -> CoinAmber
        ReflexPhase.ROUND_RESULT -> ElectricBlue
    }

    GameDialogScaffold(
        title = "Reaction Flash",
        scoreText = if (recordedTimes.isEmpty()) "5 Rounds" else "Avg ${avgMs}ms",
        statusText = "Round ${round.coerceAtMost(totalRounds)}/$totalRounds",
        statusColor = DeepEmerald,
        isFinished = isFinished,
        finishTitle = "Lightning Reflexes!",
        finishSubtitle = "5-round average: ${avgMs}ms",
        earnedCoins = rewardCoins,
        collectButtonTag = "collect_reaction_flash_button",
        onDismiss = onDismiss,
        onCollect = onCollect
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (recordedTimes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recordedTimes.forEachIndexed { idx, ms ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkCard,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "#${idx + 1}\n${ms}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = CashEmerald,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Tap the pad the instant it flashes bright green!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = padColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 18.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(enabled = !isFinished) {
                        when (phase) {
                            ReflexPhase.WAITING -> {
                                phase = ReflexPhase.TOO_EARLY
                            }
                            ReflexPhase.READY -> {
                                val delta = (System.currentTimeMillis() - readyTimestampMs)
                                    .toInt()
                                    .coerceIn(90, 2500)
                                lastReactionMs = delta
                                recordedTimes.add(delta)
                                if (recordedTimes.size < totalRounds) {
                                    round++
                                    triggerToken++
                                }
                            }
                            ReflexPhase.TOO_EARLY -> {
                                triggerToken++
                            }
                            ReflexPhase.ROUND_RESULT -> Unit
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (phase) {
                            ReflexPhase.WAITING -> "WAIT FOR GREEN..."
                            ReflexPhase.READY -> "TAP NOW!"
                            ReflexPhase.TOO_EARLY -> "TOO EARLY!"
                            ReflexPhase.ROUND_RESULT -> "${lastReactionMs} ms"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (phase == ReflexPhase.READY) InkNavy else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (phase) {
                            ReflexPhase.WAITING -> "Hold steady — tapping red restarts this round."
                            ReflexPhase.READY -> "Hit anywhere on this green pad!"
                            ReflexPhase.TOO_EARLY -> "Tap to retry Round $round."
                            ReflexPhase.ROUND_RESULT -> "Next round starting..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (phase == ReflexPhase.READY) InkNavy.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 7. Scratch & Win
// ---------------------------------------------------------------------------

private data class ScratchPrizeTier(val symbol: String, val coins: Int)

@Composable
fun ScratchCardDialog(
    onDismiss: () -> Unit,
    onCollect: (Int) -> Unit
) {
    val tiers = remember {
        listOf(
            ScratchPrizeTier("💎", 150),
            ScratchPrizeTier("🪙", 100),
            ScratchPrizeTier("💵", 70),
            ScratchPrizeTier("⭐", 45)
        )
    }

    val chosenWin = remember {
        val roll = Random.nextFloat()
        when {
            roll < 0.14f -> tiers[0]
            roll < 0.36f -> tiers[1]
            roll < 0.68f -> tiers[2]
            else -> tiers[3]
        }
    }

    val gridSymbols = remember {
        val decoys = tiers.filter { it.symbol != chosenWin.symbol }.flatMap { listOf(it.symbol, it.symbol) }
        (List(3) { chosenWin.symbol } + decoys.shuffled().take(6)).shuffled()
    }

    val cols = 15
    val rows = 10
    val totalFoilCells = cols * rows
    val clearedFoil = remember { mutableStateListOf<Boolean>().apply { repeat(totalFoilCells) { add(false) } } }
    var forceRevealed by remember { mutableStateOf(false) }

    val clearedCount = clearedFoil.count { it }
    val progressPct = if (forceRevealed) 100 else (clearedCount * 100) / totalFoilCells
    val isFinished = forceRevealed || progressPct >= 70

    GameDialogScaffold(
        title = "Scratch & Win",
        scoreText = if (isFinished) "+${chosenWin.coins} Coins!" else "Match 3 Symbols",
        statusText = "$progressPct% Scratched",
        statusColor = CoinAmber,
        isFinished = isFinished,
        finishTitle = "${chosenWin.symbol} Jackpot Match!",
        finishSubtitle = "Matched 3x ${chosenWin.symbol} on your scratch card.",
        earnedCoins = chosenWin.coins,
        collectButtonTag = "collect_scratch_card_button",
        onDismiss = onDismiss,
        onCollect = onCollect
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Rub your finger over the silver foil to reveal your match-3 prize!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(DeepRoyalBlue, DarkCard)))
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }

                fun scratchAt(pos: Offset) {
                    if (widthPx <= 0f || heightPx <= 0f) return
                    val cellW = widthPx / cols
                    val cellH = heightPx / rows
                    val cx = (pos.x / cellW).toInt()
                    val cy = (pos.y / cellH).toInt()
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = cx + dx
                            val ny = cy + dy
                            if (nx in 0 until cols && ny in 0 until rows) {
                                clearedFoil[ny * cols + nx] = true
                            }
                        }
                    }
                }

                // Underlying 3x3 symbol prize grid
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (r in 0 until 3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (c in 0 until 3) {
                                val sym = gridSymbols[r * 3 + c]
                                val isWinner = sym == chosenWin.symbol
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isWinner) CashEmerald.copy(alpha = 0.22f) else DarkCard,
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (isWinner) CoinGold else DarkBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = sym, fontSize = 36.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive Foil Mask Canvas
                if (!forceRevealed) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(widthPx, heightPx) {
                                detectTapGestures { pos -> scratchAt(pos) }
                            }
                            .pointerInput(widthPx, heightPx) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    scratchAt(change.position)
                                }
                            }
                    ) {
                        val cellW = size.width / cols
                        val cellH = size.height / rows
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                if (!clearedFoil[r * cols + c]) {
                                    drawRoundRect(
                                        color = if ((r + c) % 2 == 0) Color(0xFF7B8CA8) else Color(0xFF8C9DB8),
                                        topLeft = Offset(c * cellW, r * cellH),
                                        size = Size(cellW + 1f, cellH + 1f),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (progressPct >= 35 && !isFinished) {
                OutlinedButton(
                    onClick = { forceRevealed = true },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, CoinGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Quick Reveal Card",
                        color = CoinGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "💎 150  ·  🪙 100  ·  💵 70  ·  ⭐ 45",
                    style = MaterialTheme.typography.labelLarge,
                    color = CoinGold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 8. Whack-a-Coin
// ---------------------------------------------------------------------------

private enum class BurrowItem { EMPTY, COIN, GEM, BOMB }

@Composable
fun WhackCoinDialog(
    onDismiss: () -> Unit,
    onCollect: (Int) -> Unit
) {
    var timeLeftSec by remember { mutableIntStateOf(15) }
    var score by remember { mutableIntStateOf(0) }
    var whackedCount by remember { mutableIntStateOf(0) }
    val holes = remember { mutableStateListOf<BurrowItem>().apply { repeat(9) { add(BurrowItem.EMPTY) } } }

    val isFinished = timeLeftSec <= 0

    LaunchedEffect(Unit) {
        while (timeLeftSec > 0) {
            delay(1000L)
            timeLeftSec--
        }
    }

    LaunchedEffect(isFinished) {
        while (!isFinished) {
            // Clear 1-2 random occupied holes and spawn 1-2 fresh items
            val occupied = holes.indices.filter { holes[it] != BurrowItem.EMPTY }
            if (occupied.size >= 3) {
                holes[occupied.random()] = BurrowItem.EMPTY
            }
            val emptyHoles = holes.indices.filter { holes[it] == BurrowItem.EMPTY }
            if (emptyHoles.isNotEmpty()) {
                val target = emptyHoles.random()
                val roll = Random.nextFloat()
                holes[target] = when {
                    roll < 0.58f -> BurrowItem.COIN
                    roll < 0.82f -> BurrowItem.GEM
                    else -> BurrowItem.BOMB
                }
            }
            delay(440L)
        }
    }

    GameDialogScaffold(
        title = "Whack-a-Coin",
        scoreText = "$score Coins",
        statusText = "${timeLeftSec}s",
        statusColor = if (timeLeftSec <= 5) CoralRed else CoinAmber,
        isFinished = isFinished,
        finishTitle = "Blitz Complete!",
        finishSubtitle = "Whacked $whackedCount treasure targets!",
        earnedCoins = score.coerceAtMost(180),
        collectButtonTag = "collect_whack_coin_button",
        onDismiss = onDismiss,
        onCollect = onCollect
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tap Coins (+10) & Gems (+25) — dodge Bombs (-10)!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            for (r in 0 until 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (c in 0 until 3) {
                        val idx = r * 3 + c
                        val item = holes[idx]
                        val bgColor = when (item) {
                            BurrowItem.EMPTY -> DarkCard
                            BurrowItem.COIN -> CoinGold.copy(alpha = 0.24f)
                            BurrowItem.GEM -> CashEmerald.copy(alpha = 0.24f)
                            BurrowItem.BOMB -> CoralRed.copy(alpha = 0.26f)
                        }
                        val borderColor = when (item) {
                            BurrowItem.EMPTY -> DarkBorder
                            BurrowItem.COIN -> CoinGold
                            BurrowItem.GEM -> CashEmerald
                            BurrowItem.BOMB -> CoralRed
                        }
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = bgColor,
                            border = BorderStroke(2.dp, borderColor),
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .clickable(enabled = !isFinished && item != BurrowItem.EMPTY) {
                                    when (item) {
                                        BurrowItem.COIN -> {
                                            score = (score + 10).coerceAtMost(180)
                                            whackedCount++
                                        }
                                        BurrowItem.GEM -> {
                                            score = (score + 25).coerceAtMost(180)
                                            whackedCount++
                                        }
                                        BurrowItem.BOMB -> {
                                            score = (score - 10).coerceAtLeast(0)
                                        }
                                        BurrowItem.EMPTY -> Unit
                                    }
                                    holes[idx] = BurrowItem.EMPTY
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when (item) {
                                    BurrowItem.EMPTY -> {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF060C1B))
                                        )
                                    }
                                    BurrowItem.COIN -> Text(text = "🪙", fontSize = 36.sp)
                                    BurrowItem.GEM -> Text(text = "💎", fontSize = 36.sp)
                                    BurrowItem.BOMB -> Text(text = "💣", fontSize = 36.sp)
                                }
                            }
                        }
                    }
                }
                if (r < 2) Spacer(Modifier.height(12.dp))
            }
        }
    }
}
