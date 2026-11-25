package com.example.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.CoinBadge
import com.example.ui.theme.BrightCyanBlue
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.CoinAmber
import com.example.ui.theme.CoinGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkMidnight
import com.example.ui.theme.DeepEmerald
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.InkNavy

enum class ArcadeCategory(val label: String) {
    ALL("All Games"),
    ARCADE("Arcade"),
    PUZZLE("Puzzle"),
    BRAIN("Brain"),
    LUCK("Luck")
}

enum class ArcadeGameType(
    val title: String,
    val genreTag: String,
    val category: ArcadeCategory,
    val subtitle: String,
    val description: String,
    val ctaLabel: String,
    val accentColor: Color,
    val icon: ImageVector,
    val testTag: String
) {
    MEMORY_MATCH(
        title = "Memory Match Flip",
        genreTag = "Memory Puzzle",
        category = ArcadeCategory.PUZZLE,
        subtitle = "12 Cards · Up to +100 Coins",
        description = "Flip tiles and match all 6 icon pairs in as few moves as possible to maximize your coin bonus.",
        ctaLabel = "Play Memory Match",
        accentColor = BrightCyanBlue,
        icon = Icons.Filled.Style,
        testTag = "play_memory_match_button"
    ),
    BALLOON_POP(
        title = "Balloon Pop Rush",
        genreTag = "Sky Arcade",
        category = ArcadeCategory.ARCADE,
        subtitle = "15s Sky Pop · Up to +180 Coins",
        description = "Pop rising bright balloons (+10) and golden stars (+25) before they float away. Watch out for spiked dark balloons!",
        ctaLabel = "Play Balloon Pop",
        accentColor = CoralRed,
        icon = Icons.Filled.AutoAwesome,
        testTag = "play_balloon_pop_button"
    ),
    MATH_DASH(
        title = "Math Dash Sprint",
        genreTag = "Speed Quiz",
        category = ArcadeCategory.BRAIN,
        subtitle = "20s Brain Sprint · Up to +160 Coins",
        description = "Solve rapid-fire math equations under pressure. Chain correct answers to unlock 2x and 3x streak multipliers!",
        ctaLabel = "Play Math Dash",
        accentColor = ElectricBlue,
        icon = Icons.Filled.Calculate,
        testTag = "play_math_dash_button"
    ),
    REACTION_FLASH(
        title = "Reaction Flash",
        genreTag = "Reflex Test",
        category = ArcadeCategory.BRAIN,
        subtitle = "5-Round Reflex · Up to +100 Coins",
        description = "Wait for the pad to turn bright emerald green, then tap as fast as you can across 5 lightning rounds.",
        ctaLabel = "Play Reaction Flash",
        accentColor = CashEmerald,
        icon = Icons.Filled.Bolt,
        testTag = "play_reaction_flash_button"
    ),
    SCRATCH_WIN(
        title = "Scratch & Win",
        genreTag = "Instant Card",
        category = ArcadeCategory.LUCK,
        subtitle = "Foil Scratch Card · Up to +150 Coins",
        description = "Rub across the metallic foil card with your finger to uncover matching ClipCash symbols and claim your instant prize.",
        ctaLabel = "Open Scratch Card",
        accentColor = CoinGold,
        icon = Icons.Filled.AdsClick,
        testTag = "play_scratch_win_button"
    ),
    SLIDE_MERGE(
        title = "Slide & Merge 256",
        genreTag = "Strategy Merge",
        category = ArcadeCategory.PUZZLE,
        subtitle = "4x4 Number Grid · Up to +140 Coins",
        description = "Swipe or use the direction arrows to slide and combine matching numbers toward 256. Cash out your earned coins anytime!",
        ctaLabel = "Play Slide & Merge",
        accentColor = DeepEmerald,
        icon = Icons.Filled.GridView,
        testTag = "play_slide_merge_button"
    ),
    COLOR_BEAT(
        title = "Color Beat Simon",
        genreTag = "Pattern Memory",
        category = ArcadeCategory.BRAIN,
        subtitle = "Endless Rounds · Up to +120 Coins",
        description = "Watch the 4 neon arcade pads flash in sequence, then repeat the growing pattern beat for beat.",
        ctaLabel = "Play Color Beat",
        accentColor = Color(0xFF9C6BFF),
        icon = Icons.Filled.GraphicEq,
        testTag = "play_color_beat_button"
    ),
    WHACK_A_COIN(
        title = "Whack-a-Coin",
        genreTag = "Grid Whack",
        category = ArcadeCategory.ARCADE,
        subtitle = "15s Burrow Blitz · Up to +180 Coins",
        description = "Tap popping gold coins (+10) and emerald gems (+25) across 9 burrows while avoiding hazard bombs!",
        ctaLabel = "Play Whack-a-Coin",
        accentColor = CoinAmber,
        icon = Icons.Filled.Psychology,
        testTag = "play_whack_coin_button"
    )
}

@Composable
fun GameDialogScaffold(
    title: String,
    scoreText: String,
    statusText: String,
    statusColor: Color,
    isFinished: Boolean,
    finishTitle: String,
    finishSubtitle: String,
    earnedCoins: Int,
    collectButtonTag: String,
    onDismiss: () -> Unit,
    onCollect: (Int) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize(),
                shape = RoundedCornerShape(28.dp),
                color = DarkMidnight
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CoinBadge(sizeDp = 18)
                                Text(
                                    text = scoreText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CoinGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = statusColor
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close $title",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF0B1530))
                    ) {
                        content()

                        if (isFinished) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.78f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = finishTitle,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = finishSubtitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "+$earnedCoins Coins",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = CashEmerald,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(Modifier.height(22.dp))
                                    Button(
                                        onClick = { onCollect(earnedCoins) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CashEmerald,
                                            contentColor = InkNavy
                                        ),
                                        modifier = Modifier
                                            .testTag(collectButtonTag)
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Text(
                                            text = if (earnedCoins > 0) "Collect +$earnedCoins Coins" else "Done",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
