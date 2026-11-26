package com.example.ui.screens.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.theme.DarkElevated
import com.example.ui.theme.DeepEmerald
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.InkNavy
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// ---------------------------------------------------------------------------
// 1. Memory Match Flip
// ---------------------------------------------------------------------------

private data class MemoryCardModel(
    val id: Int,
    val pairId: Int,
    val icon: ImageVector,
    val color: Color,
    val isFaceUp: Boolean = false,
    val isMatched: Boolean = false
)

@Composable
fun MemoryMatchDialog(
    onDismiss: () -> Unit,
    onCollect: (Int) -> Unit
) {
    val pairDefs = remember {
        listOf(
            Pair(Icons.Filled.Star, CoinGold),
            Pair(Icons.Filled.Diamond, BrightCyanBlue),
            Pair(Icons.Filled.Bolt, CoinAmber),
            Pair(Icons.Filled.Favorite, CoralRed),
            Pair(Icons.Filled.RocketLaunch, CashEmerald),
            Pair(Icons.Filled.EmojiEvents, Color(0xFF9C6BFF))
        )
    }

    val cards = remember {
        mutableStateListOf<MemoryCardModel>().apply {
            val deck = pairDefs.flatMapIndexed { pairIdx, (icon, color) ->
                listOf(
                    MemoryCardModel(id = pairIdx * 2, pairId = pairIdx, icon = icon, color = color),
                    MemoryCardModel(id = pairIdx * 2 + 1, pairId = pairIdx, icon = icon, color = color)
                )
            }.shuffled()
            addAll(deck)
        }
    }

    var firstIndex by remember { mutableStateOf<Int?>(null) }
    var secondIndex by remember { mutableStateOf<Int?>(null) }
    var moves by remember { mutableIntStateOf(0) }
    var matchedPairs by remember { mutableIntStateOf(0) }
    var isResolving by remember { mutableStateOf(false) }

    LaunchedEffect(isResolving, firstIndex, secondIndex) {
        if (!isResolving) return@LaunchedEffect
        val idx1 = firstIndex ?: return@LaunchedEffect
        val idx2 = secondIndex ?: return@LaunchedEffect
        delay(620L)
        cards[idx1] = cards[idx1].copy(isFaceUp = false)
        cards[idx2] = cards[idx2].copy(isFaceUp = false)
        firstIndex = null
        secondIndex = null
        isResolving = false
    }

    val isFinished = matchedPairs == pairDefs.size
    val earnedCoins = if (isFinished) {
        (100 - ((moves - pairDefs.size) * 5).coerceAtLeast(0)).coerceIn(30, 100)
    } else {
        0
    }

    GameDialogScaffold(
        title = "Memory Match Flip",
        scoreText = "$matchedPairs / ${pairDefs.size} Pairs",
        statusText = "$moves Moves",
        statusColor = ElectricBlue,
        isFinished = isFinished,
        finishTitle = "All Pairs Matched!",
        finishSubtitle = "Cleared the board in $moves moves.",
        earnedCoins = earnedCoins,
        collectButtonTag = "collect_memory_match_button",
        onDismiss = onDismiss,
        onCollect = onCollect
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Match all 6 pairs in fewer moves for up to +100 Coins!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0 until 3) {
                        val idx = row * 3 + col
                        val card = cards[idx]
                        val faceShown = card.isFaceUp || card.isMatched
                        val bgColor by animateColorAsState(
                            targetValue = when {
                                card.isMatched -> CashEmerald.copy(alpha = 0.22f)
                                faceShown -> DarkElevated
                                else -> DeepRoyalBlue.copy(alpha = 0.72f)
                            },
                            label = "card_bg"
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = bgColor,
                            border = BorderStroke(
                                width = if (card.isMatched) 2.dp else 1.dp,
                                color = if (card.isMatched) CashEmerald else DarkBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(enabled = !faceShown && !isResolving && !isFinished) {
                                    cards[idx] = card.copy(isFaceUp = true)
                                    val openIdx = firstIndex
                                    if (openIdx == null) {
                                        firstIndex = idx
                                    } else {
                                        moves++
                                        val firstCard = cards[openIdx]
                                        if (firstCard.pairId == card.pairId) {
                                            cards[openIdx] = firstCard.copy(isMatched = true)
                                            cards[idx] = cards[idx].copy(isMatched = true)
                                            firstIndex = null
                                            matchedPairs++
                                        } else {
                                            secondIndex = idx
                                            isResolving = true
                                        }
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (faceShown) card.icon else Icons.Filled.QuestionMark,
                                    contentDescription = if (faceShown) "Card icon" else "Hidden card",
                                    tint = if (faceShown) card.color else Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.size(if (faceShown) 36.dp else 28.dp)
                                )
                            }
                        }
                    }
                }
                if (row < 3) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Math Dash Sprint
// ---------------------------------------------------------------------------

private data class MathQuestion(
    val prompt: String,
    val correctAnswer: Int,
    val options: List<Int>
)

private fun generateMathQuestion(): MathQuestion {
    val op = Random.nextInt(3)
    val a: Int
    val b: Int
    val ans: Int
    val symbol: String
    when (op) {
        0 -> {
            a = Random.nextInt(5, 38)
            b = Random.nextInt(4, 34)
            ans = a + b
            symbol = "+"
        }
        1 -> {
            val high = Random.nextInt(14, 55)
            val low = Random.nextInt(3, high)
            a = high
            b = low
            ans = a - b
            symbol = "-"
        }
        else -> {
            a = Random.nextInt(2, 13)
            b = Random.nextInt(2, 12)
            ans = a * b
            symbol = "×"
        }
    }
    val distractors = mutableSetOf(ans)
    while (distractors.size < 4) {
        val offset = listOf(-10, -4, -3, -2, -1, 1, 2, 3, 4, 10).random()
        val candidate = (ans + offset).coerceAtLeast(0)
        distractors.add(candidate)
    }
    return MathQuestion(
        prompt = "$a $symbol $b = ?",
        correctAnswer = ans,
        options = distractors.shuffled()
    )
}

@Composable
fun MathDashDialog(
    onDismiss: () -> Unit,
    onCollect: (Int) -> Unit
) {
    var timeLeftSec by remember { mutableIntStateOf(20) }
    var score by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(generateMathQuestion()) }
    var feedbackText by remember { mutableStateOf("Tap the right answer fast!") }
    var feedbackColor by remember { mutableStateOf(BrightCyanBlue) }

    val isFinished = timeLeftSec <= 0

    LaunchedEffect(Unit) {
        while (timeLeftSec > 0) {
            delay(1000L)
            timeLeftSec--
        }
    }

    val finalCoins = score.coerceAtMost(160)

    GameDialogScaffold(
        title = "Math Dash Sprint",
        scoreText = "$score Coins",
        statusText = "${timeLeftSec}s",
        statusColor = if (timeLeftSec <= 5) CoralRed else ElectricBlue,
        isFinished = isFinished,
        finishTitle = "Time's Up!",
        finishSubtitle = "Solved $correctCount equations correctly.",
        earnedCoins = finalCoins,
        collectButtonTag = "collect_math_dash_button",
        onDismiss = onDismiss,
        onCollect = onCollect
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (streak >= 2) CashEmerald.copy(alpha = 0.22f) else DarkElevated
                ) {
                    Text(
                        text = if (streak >= 2) "Streak x${streak.coerceAtMost(3)}!" else "Streak: $streak",
                        color = if (streak >= 2) CashEmerald else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Text(
                    text = feedbackText,
                    style = MaterialTheme.typography.labelLarge,
                    color = feedbackColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = question.prompt,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 36.dp, horizontal = 16.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val optionColors = listOf(ElectricBlue, DeepEmerald, CoinAmber, Color(0xFF5C6BC0))
                for (row in 0 until 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (col in 0 until 2) {
                            val idx = row * 2 + col
                            val value = question.options[idx]
                            Button(
                                onClick = {
                                    if (isFinished) return@Button
                                    if (value == question.correctAnswer) {
                                        streak++
                                        correctCount++
                                        val multiplier = streak.coerceAtMost(3)
                                        val gain = 8 + multiplier * 4
                                        score = (score + gain).coerceAtMost(160)
                                        feedbackText = "+$gain Coins!"
                                        feedbackColor = CashEmerald
                                    } else {
                                        streak = 0
                                        feedbackText = "Wrong! Try next"
                                        feedbackColor = CoralRed
                                    }
                                    question = generateMathQuestion()
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = optionColors[idx],
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                            ) {
                                Text(
                                    text = value.toString(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Slide & Merge 256
// ---------------------------------------------------------------------------

private enum class SlideDirection { LEFT, RIGHT, UP, DOWN }

private data class SlideMoveResult(
    val board: List<Int>,
    val scoreGained: Int,
    val moved: Boolean
)

private fun slideAndMergeRow(row: List<Int>): Pair<List<Int>, Int> {
    val nonZero = row.filter { it != 0 }
    val merged = mutableListOf<Int>()
    var gained = 0
    var i = 0
    while (i < nonZero.size) {
        if (i + 1 < nonZero.size && nonZero[i] == nonZero[i + 1]) {
            val combined = nonZero[i] * 2
            merged.add(combined)
            gained += combined
            i += 2
        } else {
            merged.add(nonZero[i])
            i += 1
        }
    }
    while (merged.size < 4) merged.add(0)
    return merged to gained
}

private fun applySlideMove(board: List<Int>, dir: SlideDirection): SlideMoveResult {
    val next = board.toMutableList()
    var gainedTotal = 0

    for (line in 0 until 4) {
        val indices = when (dir) {
            SlideDirection.LEFT -> List(4) { col -> line * 4 + col }
            SlideDirection.RIGHT -> List(4) { col -> line * 4 + (3 - col) }
            SlideDirection.UP -> List(4) { row -> row * 4 + line }
            SlideDirection.DOWN -> List(4) { row -> (3 - row) * 4 + line }
        }
        val lineVals = indices.map { board[it] }
        val (slid, gained) = slideAndMergeRow(lineVals)
        gainedTotal += gained
        indices.forEachIndexed { pos, boardIdx ->
            next[boardIdx] = slid[pos]
        }
    }

    val moved = next != board
    if (moved) {
        val emptyIndices = next.indices.filter { next[it] == 0 }
        if (emptyIndices.isNotEmpty()) {
            val pick = emptyIndices.random()
            next[pick] = if (Random.nextFloat() < 0.85f) 2 else 4
        }
    }
    return SlideMoveResult(next, gainedTotal, moved)
}

private fun hasValidMoves(board: List<Int>): Boolean {
    if (board.any { it == 0 }) return true
    for (r in 0 until 4) {
        for (c in 0 until 4) {
            val v = board[r * 4 + c]
            if (c + 1 < 4 && board[r * 4 + (c + 1)] == v) return true
            if (r + 1 < 4 && board[(r + 1) * 4 + c] == v) return true
        }
    }
    return false
}

private fun initialMergeBoard(): List<Int> {
    val list = MutableList(16) { 0 }
    val first = Random.nextInt(16)
    var second = Random.nextInt(16)
    while (second == first) second = Random.nextInt(16)
    list[first] = 2
    list[second] = 2
    return list
}

private fun tileBgColor(value: Int): Color = when (value) {
    0 -> DarkCard
    2 -> Color(0xFF1B2B52)
    4 -> Color(0xFF21407A)
    8 -> ElectricBlue
    16 -> BrightCyanBlue
    32 -> DeepEmerald
    64 -> CashEmerald
    128 -> CoinAmber
    else -> CoinGold
}

@Composable
fun SlideMergeDialog(
    onDismiss: () -> Unit,
    onCollect: (Int) -> Unit
) {
    var board by remember { mutableStateOf(initialMergeBoard()) }
    var gameScore by remember { mutableIntStateOf(0) }
    var moves by remember { mutableIntStateOf(0) }
    var cashedOut by remember { mutableStateOf(false) }

    val maxTile = board.maxOrNull() ?: 2
    val noMovesLeft = !hasValidMoves(board)
    val reachedGoal = maxTile >= 256
    val isFinished = cashedOut || noMovesLeft || reachedGoal

    val earnedCoins = ((maxTile / 2) + (gameScore / 25)).coerceIn(if (moves > 0) 20 else 0, 140)

    fun performMove(dir: SlideDirection) {
        if (isFinished) return
        val res = applySlideMove(board, dir)
        if (res.moved) {
            board = res.board
            gameScore += res.scoreGained
            moves++
        }
    }

    GameDialogScaffold(
        title = "Slide & Merge 256",
        scoreText = "Score $gameScore · Max $maxTile",
        statusText = "$moves Moves",
        statusColor = DeepEmerald,
        isFinished = isFinished,
        finishTitle = if (reachedGoal) "256 Tile Reached!" else "Game Complete!",
        finishSubtitle = "Highest tile $maxTile · Score $gameScore",
        earnedCoins = earnedCoins,
        collectButtonTag = "collect_slide_merge_button",
        onDismiss = onDismiss,
        onCollect = onCollect
    ) {
        var dragX by remember { mutableFloatStateOf(0f) }
        var dragY by remember { mutableFloatStateOf(0f) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .pointerInput(isFinished) {
                    detectDragGestures(
                        onDragStart = {
                            dragX = 0f
                            dragY = 0f
                        },
                        onDrag = { _, dragAmount ->
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                        },
                        onDragEnd = {
                            if (abs(dragX) > abs(dragY) && abs(dragX) > 36f) {
                                performMove(if (dragX > 0f) SlideDirection.RIGHT else SlideDirection.LEFT)
                            } else if (abs(dragY) > abs(dragX) && abs(dragY) > 36f) {
                                performMove(if (dragY > 0f) SlideDirection.DOWN else SlideDirection.UP)
                            }
                        }
                    )
                },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Swipe or tap arrows to merge tiles up to 256!",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )

            // 4x4 Tile Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF071024))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in 0 until 4) {
                            val value = board[r * 4 + c]
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = tileBgColor(value),
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (value > 0) {
                                        Text(
                                            text = value.toString(),
                                            fontSize = if (value >= 128) 18.sp else 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (value >= 64) InkNavy else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Accessible Direction Pad + Cash Out
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { performMove(SlideDirection.UP) },
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Slide Up")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { performMove(SlideDirection.LEFT) },
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Slide Left")
                    }
                    Button(
                        onClick = { performMove(SlideDirection.DOWN) },
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Slide Down")
                    }
                    Button(
                        onClick = { performMove(SlideDirection.RIGHT) },
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Slide Right")
                    }
                }
                if (moves >= 4) {
                    OutlinedButton(
                        onClick = { cashedOut = true },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, CashEmerald),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = "Claim +$earnedCoins Coins",
                            color = CashEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Color Beat Simon
// ---------------------------------------------------------------------------

@Composable
fun ColorBeatDialog(
    onDismiss: () -> Unit,
    onCollect: (Int) -> Unit
) {
    val padColors = remember { listOf(CashEmerald, ElectricBlue, CoinGold, CoralRed) }
    val padNames = remember { listOf("Emerald", "Blue", "Gold", "Coral") }

    val sequence = remember { mutableStateListOf(Random.nextInt(4)) }
    var inputIndex by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(1) }
    var isShowingSequence by remember { mutableStateOf(true) }
    var activePad by remember { mutableStateOf<Int?>(null) }
    var bankedCoins by remember { mutableIntStateOf(0) }
    var isFailed by remember { mutableStateOf(false) }
    var cashedOut by remember { mutableStateOf(false) }

    val isFinished = isFailed || cashedOut || round > 8

    LaunchedEffect(round, isShowingSequence) {
        if (!isShowingSequence || isFinished) return@LaunchedEffect
        delay(350L)
        for (step in sequence) {
            activePad = step
            delay(380L)
            activePad = null
            delay(150L)
        }
        inputIndex = 0
        isShowingSequence = false
    }

    val finalCoins = bankedCoins.coerceAtMost(120)

    GameDialogScaffold(
        title = "Color Beat Simon",
        scoreText = "$finalCoins Coins Banked",
        statusText = "Round ${round.coerceAtMost(8)}",
        statusColor = Color(0xFF9C6BFF),
        isFinished = isFinished,
        finishTitle = if (round > 8) "Master of Beats!" else if (isFailed) "Pattern Broken!" else "Coins Banked!",
        finishSubtitle = "Cleared ${(round - 1).coerceAtLeast(0)} pattern rounds.",
        earnedCoins = finalCoins,
        collectButtonTag = "collect_color_beat_button",
        onDismiss = onDismiss,
        onCollect = onCollect
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    isShowingSequence -> "Watch the sequence carefully..."
                    else -> "Your turn! Tap ${sequence.size - inputIndex} more (${padNames.joinToString("/")})"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (isShowingSequence) CoinGold else CashEmerald,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (r in 0 until 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        for (c in 0 until 2) {
                            val padIdx = r * 2 + c
                            val baseColor = padColors[padIdx]
                            val lit = activePad == padIdx
                            val displayColor by animateColorAsState(
                                targetValue = if (lit) baseColor else baseColor.copy(alpha = 0.34f),
                                label = "simon_pad"
                            )
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = displayColor,
                                border = BorderStroke(
                                    width = if (lit) 3.dp else 1.5.dp,
                                    color = if (lit) Color.White else baseColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable(enabled = !isShowingSequence && !isFinished) {
                                        activePad = padIdx
                                        val expected = sequence[inputIndex]
                                        if (padIdx == expected) {
                                            inputIndex++
                                            if (inputIndex >= sequence.size) {
                                                bankedCoins = (bankedCoins + 15).coerceAtMost(120)
                                                round++
                                                if (round <= 8) {
                                                    sequence.add(Random.nextInt(4))
                                                    isShowingSequence = true
                                                }
                                            }
                                        } else {
                                            if (bankedCoins == 0) bankedCoins = 10
                                            isFailed = true
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = padNames[padIdx],
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (bankedCoins > 0 && !isFinished) {
                OutlinedButton(
                    onClick = { cashedOut = true },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, CashEmerald),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Bank +$bankedCoins Coins Now",
                        color = CashEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }

        LaunchedEffect(activePad) {
            if (!isShowingSequence && activePad != null) {
                delay(180L)
                activePad = null
            }
        }
    }
}
