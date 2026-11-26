package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.example.data.ClipProgressEntity
import com.example.model.ClipCategory
import com.example.model.VideoClip
import com.example.ui.components.CoinBadge
import com.example.ui.theme.BrightCyanBlue
import com.example.ui.theme.BrightGold
import com.example.ui.theme.BrightMint
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.CoinAmber
import com.example.ui.theme.CoinGold
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkElevated
import com.example.ui.theme.DarkNavBar
import com.example.ui.theme.DarkTextMuted
import com.example.ui.theme.DeepEmerald
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.InkNavy
import com.example.viewmodel.ClipCashUiState

@Composable
fun WatchScreen(
    uiState: ClipCashUiState,
    isWideLayout: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onSelectCategory: (ClipCategory) -> Unit,
    onLoadMoreClips: () -> Unit,
    onClaimCheckIn: () -> Unit,
    onWatchClip: (VideoClip) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = if (isWideLayout) 3 else 2
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember(uiState.clips.size) {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.isSearchingClips, uiState.isLoadingMoreClips) {
        if (shouldLoadMore && !uiState.isSearchingClips && !uiState.isLoadingMoreClips && uiState.clips.isNotEmpty()) {
            onLoadMoreClips()
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Daily Check-In Card
        item(span = { GridItemSpan(columns) }) {
            DailyCheckInCard(
                streakDays = uiState.wallet.streakDays,
                isAvailable = uiState.isCheckInAvailable,
                clipsWatched = uiState.wallet.clipsWatchedCount,
                onClaim = onClaimCheckIn
            )
        }

        // ClipCash Shorts Header + Search Bar & Category Chips
        item(span = { GridItemSpan(columns) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniClipCashPlayMark(sizeDp = 26)
                        Text(
                            text = "ClipCash Shorts",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val firstClip = uiState.clips.firstOrNull()
                        if (firstClip != null) {
                            Box(
                                modifier = Modifier
                                    .testTag("play_shorts_feed_button")
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(listOf(BrightCyanBlue, ElectricBlue))
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                                    .clickable { onWatchClip(firstClip) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Swipe Shorts",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (uiState.isSearchingClips) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DarkCard)
                                    .border(1.dp, DarkBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = CashEmerald,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .testTag("refresh_feed_button")
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DarkCard)
                                    .border(1.dp, DarkBorder, CircleShape)
                                    .clickable(onClick = onSubmitSearch),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh ClipCash Shorts",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                // HD Dark Search Bar matching mockup
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search ClipCash Shorts...",
                            color = DarkTextMuted,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = DarkTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    onSearchQueryChange("")
                                    onSubmitSearch()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear search",
                                        tint = DarkTextMuted
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .testTag("search_clips_button")
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DarkElevated)
                                    .border(1.dp, DarkBorder, CircleShape)
                                    .clickable(onClick = onSubmitSearch),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (uiState.searchQuery.isNotBlank()) {
                                        Icons.AutoMirrored.Filled.ArrowForward
                                    } else {
                                        Icons.Filled.Tune
                                    },
                                    contentDescription = "Search Shorts",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkNavBar,
                        unfocusedContainerColor = DarkNavBar,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = CashEmerald
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_clips_input")
                )

                // Category Chips with icons matching mockup
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ClipCategory.entries) { category ->
                        val selected = uiState.selectedCategory == category
                        CategoryPillChip(
                            category = category,
                            selected = selected,
                            onClick = { onSelectCategory(category) }
                        )
                    }
                }

                uiState.searchError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkTextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onSubmitSearch) {
                                Text("Retry", color = CashEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Video Clips
        items(uiState.clips, key = { it.id }) { clip ->
            val progress = uiState.clipProgress[clip.id]
            VideoClipCard(
                clip = clip,
                progress = progress,
                onWatchClick = { onWatchClip(clip) }
            )
        }

        // Infinite Shorts Footer
        if (uiState.clips.isNotEmpty()) {
            item(span = { GridItemSpan(columns) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoadingMoreClips) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = CashEmerald,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Loading more ClipCash Shorts...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkTextMuted
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onLoadMoreClips,
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkCard,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("load_more_shorts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = CashEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Load More Shorts", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPillChip(
    category: ClipCategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (category) {
        ClipCategory.ALL -> null
        ClipCategory.TRENDING -> Icons.Filled.LocalFireDepartment
        ClipCategory.MUSIC -> Icons.Filled.MusicNote
        ClipCategory.GAMING -> Icons.Filled.SportsEsports
        ClipCategory.COMEDY -> Icons.Filled.SentimentSatisfiedAlt
        ClipCategory.TECH -> Icons.Filled.Memory
    }
    val bgModifier = if (selected) {
        Modifier
            .background(Brush.horizontalGradient(listOf(BrightCyanBlue, ElectricBlue)))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
    } else {
        Modifier
            .background(DarkCard)
            .border(1.dp, DarkBorder, CircleShape)
    }

    Box(
        modifier = Modifier
            .testTag("filter_${category.name.lowercase()}")
            .clip(CircleShape)
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else Color(0xFF9FB8F5),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (selected) Color.White else Color(0xFFD6E2FF)
            )
        }
    }
}

@Composable
private fun MiniClipCashPlayMark(sizeDp: Int = 24) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.16f, h * 0.12f)
            lineTo(w * 0.90f, h * 0.50f)
            lineTo(w * 0.16f, h * 0.88f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(listOf(Color(0xFF19F5FF), CashEmerald)),
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun DailyCheckInCard(
    streakDays: Int,
    isAvailable: Boolean,
    clipsWatched: Int,
    onClaim: () -> Unit
) {
    val nextBonus = (50 + streakDays * 10).coerceAtMost(120)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(DeepRoyalBlue, ElectricBlue, Color(0xFF0099D6), Color(0xFF0ECA7F)),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 520f)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top translucent day badge
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "DAILY CHECK-IN · DAY ${streakDays + if (isAvailable) 1 else 0}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                    }

                    // Bold Headline with bright green +50
                    if (isAvailable) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White)) {
                                    append("Claim ")
                                }
                                withStyle(SpanStyle(color = BrightMint)) {
                                    append("+$nextBonus")
                                }
                                withStyle(SpanStyle(color = Color.White)) {
                                    append(" Bonus\nCoins")
                                }
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 23.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    } else {
                        Text(
                            text = "Streak Active!\n($clipsWatched clips watched)",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 21.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color.White
                        )
                    }

                    Text(
                        text = "Watch any ClipCash video for 10s to earn instant coins.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        ),
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    // Emerald CTA Pill matching mockup
                    Box(
                        modifier = Modifier
                            .testTag("daily_checkin_button")
                            .clip(CircleShape)
                            .background(
                                if (isAvailable) {
                                    Brush.horizontalGradient(listOf(Color(0xFF1EEB87), Color(0xFF0DC168)))
                                } else {
                                    Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.16f)))
                                }
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape)
                            .clickable(enabled = isAvailable, onClick = onClaim)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isAvailable) {
                                CoinBadge(sizeDp = 22)
                                Text(
                                    text = "+$nextBonus",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = InkNavy
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Claim Bonus",
                                    tint = InkNavy,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Claimed",
                                    tint = BrightMint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Claimed Today",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Right-side 3D Gold Coin + Glowing Cyan Pedestal + +50 floating badge
                HeroCoinPedestalGraphic(bonusAmount = nextBonus)
            }
        }
    }
}

@Composable
private fun HeroCoinPedestalGraphic(bonusAmount: Int) {
    val textMeasurer = rememberTextMeasurer()
    Box(
        modifier = Modifier
            .width(126.dp)
            .height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.48f

            // Ambient radial glow behind coin
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF44FFE0).copy(alpha = 0.36f),
                        Color(0xFF0077FF).copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(cx, h * 0.48f),
                    radius = w * 0.58f
                ),
                radius = w * 0.58f,
                center = Offset(cx, h * 0.48f)
            )

            // Holographic blue/cyan pedestal base discs
            val baseY = h * 0.72f
            drawOval(
                color = Color(0xFF0055E6).copy(alpha = 0.75f),
                topLeft = Offset(cx - w * 0.42f, baseY + 6.dp.toPx()),
                size = Size(w * 0.84f, 26.dp.toPx())
            )
            drawOval(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF007BFF), Color(0xFF2CE8FF), Color(0xFF007BFF))
                ),
                topLeft = Offset(cx - w * 0.39f, baseY),
                size = Size(w * 0.78f, 22.dp.toPx())
            )
            drawOval(
                color = Color(0xFF073A9E),
                topLeft = Offset(cx - w * 0.28f, baseY + 3.dp.toPx()),
                size = Size(w * 0.56f, 14.dp.toPx())
            )
            drawOval(
                color = Color(0xFF70F5FF).copy(alpha = 0.7f),
                topLeft = Offset(cx - w * 0.39f, baseY),
                size = Size(w * 0.78f, 22.dp.toPx()),
                style = Stroke(width = 1.6.dp.toPx())
            )

            // 3D Gold Coin body
            val coinCenter = Offset(cx, h * 0.47f)
            val coinR = w * 0.34f

            // Coin depth edge
            drawCircle(
                color = Color(0xFFB36800),
                radius = coinR,
                center = Offset(coinCenter.x + 4.dp.toPx(), coinCenter.y + 3.dp.toPx())
            )
            // Outer bevel rim
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF3A1), CoinGold, CoinAmber),
                    start = Offset(coinCenter.x - coinR, coinCenter.y - coinR),
                    end = Offset(coinCenter.x + coinR, coinCenter.y + coinR)
                ),
                radius = coinR,
                center = coinCenter
            )
            // Inner recessed face
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(BrightGold, CoinGold, Color(0xFFE08800)),
                    center = Offset(coinCenter.x - coinR * 0.2f, coinCenter.y - coinR * 0.25f),
                    radius = coinR * 1.1f
                ),
                radius = coinR * 0.78f,
                center = coinCenter
            )
            drawCircle(
                color = Color(0xFFFFF5B8).copy(alpha = 0.75f),
                radius = coinR * 0.78f,
                center = coinCenter,
                style = Stroke(width = 1.8.dp.toPx())
            )

            // Crisp '★' virtual coin emblem
            val dollarLayout = textMeasurer.measure(
                text = "★",
                style = TextStyle(
                    color = Color(0xFFFFF7CC),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            drawText(
                textLayoutResult = dollarLayout,
                topLeft = Offset(
                    coinCenter.x - dollarLayout.size.width / 2f,
                    coinCenter.y - dollarLayout.size.height / 2f - 1.dp.toPx()
                )
            )

            // 4-point sparkles around coin
            drawFourPointSparkle(Offset(w * 0.14f, h * 0.20f), 7.dp.toPx(), Color.White)
            drawFourPointSparkle(Offset(w * 0.88f, h * 0.68f), 6.dp.toPx(), Color(0xFF9BFFDF))
            drawFourPointSparkle(Offset(w * 0.16f, h * 0.62f), 4.5.dp.toPx(), Color(0xFFB3EEFF))
        }

        // Floating emerald +50 badge top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = 4.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF19EC84), DeepEmerald)))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                text = "+$bonusAmount",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFourPointSparkle(
    center: Offset,
    radius: Float,
    color: Color
) {
    val inner = radius * 0.26f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    drawPath(path, color = color)
}

@Composable
private fun VideoClipCard(
    clip: VideoClip,
    progress: ClipProgressEntity?,
    onWatchClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(clip.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(clip.thumbnailUrl)
            .crossfade(true)
            .size(CoilSize.ORIGINAL)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("play_clip_${clip.id}")
            .border(1.dp, Color(0xFF263B6B), RoundedCornerShape(20.dp))
            .clickable(onClick = onWatchClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 14.5f)
                .clip(RoundedCornerShape(20.dp))
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = clip.title,
                placeholder = painterResource(id = clip.fallbackThumbResId),
                error = painterResource(id = clip.fallbackThumbResId),
                fallback = painterResource(id = clip.fallbackThumbResId),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom gradient scrim keeping center photo crisp & unobstructed
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.22f),
                            0.35f to Color.Transparent,
                            0.62f to Color.Black.copy(alpha = 0.28f),
                            1.0f to Color.Black.copy(alpha = 0.92f)
                        )
                    )
            )

            // Top-Left Source Pill with mini play mark
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
                    .clip(CircleShape)
                    .background(Color(0xB8081024))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MiniClipCashPlayMark(sizeDp = 12)
                Text(
                    text = clip.sourceLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Top-Right Emerald Coin Reward Pill
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .clip(CircleShape)
                    .background(CashEmerald)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CoinBadge(sizeDp = 15)
                Text(
                    text = "+${clip.rewardCoins}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = InkNavy
                )
            }

            // Bottom Portrait Title + Metadata Row matching mockup
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = clip.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = if (progress != null && progress.watchCount > 0) CashEmerald else Color(0xFFB3C5EB),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (progress != null && progress.watchCount > 0) {
                                "Watched ${progress.watchCount}x"
                            } else {
                                "Instant +${clip.rewardCoins}"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (progress != null && progress.watchCount > 0) CashEmerald else Color.White.copy(alpha = 0.88f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xB8081024))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        val secText = clip.watchTargetSeconds.toString().padStart(2, '0')
                        Text(
                            text = "0:$secText",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
