package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TransactionEntity
import com.example.model.RewardOption
import com.example.model.TransactionType
import com.example.ui.components.CoinBadge
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.BrightCyanBlue
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DeepEmerald
import com.example.ui.theme.DeepRoyalBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.InkNavy
import com.example.viewmodel.ClipCashUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LedgerFilter(val label: String) {
    ALL("All"),
    EARNED("Earned"),
    REDEEMED("Perks")
}

@Composable
fun WalletScreen(
    uiState: ClipCashUiState,
    onRedeemReward: (RewardOption, String, (String) -> Unit) -> Unit,
    onSyncWallet: () -> Unit,
    onChangeEmail: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedReward by remember { mutableStateOf<RewardOption?>(null) }
    var ledgerFilter by remember { mutableStateOf(LedgerFilter.ALL) }

    val filteredTransactions = remember(uiState.transactions, ledgerFilter) {
        when (ledgerFilter) {
            LedgerFilter.ALL -> uiState.transactions
            LedgerFilter.EARNED -> uiState.transactions.filter { it.amountCoins >= 0 }
            LedgerFilter.REDEEMED -> uiState.transactions.filter { it.amountCoins < 0 }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Signed-In ClipCash Account Row
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, DarkBorder),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val session = uiState.auth.session
                                val displayTitle = when {
                                    session != null && session.fullName.isNotBlank() && session.username.isNotBlank() ->
                                        "${session.fullName} (@${session.username})"
                                    session != null && session.fullName.isNotBlank() -> session.fullName
                                    session != null && session.username.isNotBlank() -> "@${session.username}"
                                    session != null && session.email.isNotBlank() -> session.email
                                    else -> "ClipCash Member"
                                }
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val accountDetails = buildList {
                                    if (session != null) {
                                        if (session.nationality.isNotBlank()) add(session.nationality)
                                        if (session.phoneNumber.isNotBlank()) add(session.phoneNumber)
                                        if (session.email.isNotBlank() && session.email != displayTitle) add(session.email)
                                    }
                                }.joinToString(" · ")
                                val syncLabel = when {
                                    !uiState.isOnline -> "Network connection is bad — check internet to sync"
                                    uiState.dbSyncNotice != null -> uiState.dbSyncNotice
                                    !session?.email.isNullOrBlank() -> "Wallet synced for ${session?.email}"
                                    else -> "Synced with ClipCash Cloud"
                                }
                                val syncColor = if (!uiState.isOnline || uiState.dbSyncNotice != null) {
                                    CoralRed
                                } else {
                                    CashEmerald
                                }
                                if (accountDetails.isNotBlank()) {
                                    Text(
                                        text = accountDetails,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = syncLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = syncColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onSyncWallet,
                                modifier = Modifier.testTag("sync_wallet_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Sync ClipCash wallet",
                                    tint = BrightCyanBlue
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onChangeEmail,
                                modifier = Modifier.testTag("change_email_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Email,
                                    contentDescription = null,
                                    tint = BrightCyanBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Change Email", fontWeight = FontWeight.SemiBold, color = BrightCyanBlue)
                            }
                            TextButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Text("Sign Out", fontWeight = FontWeight.Bold, color = CoralRed)
                            }
                        }
                    }
            }
        }

        // Wallet Hero Card
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(DeepRoyalBlue, ElectricBlue, DeepEmerald)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        text = "CLIPCASH WALLET BALANCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFA6FFCF)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CoinBadge(sizeDp = 34)
                        Text(
                            text = "${uiState.wallet.coinBalance} Coins",
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Virtual in-app coins for arcade & profile perks · No real-money value",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WalletStat("Lifetime", "${uiState.wallet.totalEarnedCoins}")
                        WalletStat("Clips", "${uiState.wallet.clipsWatchedCount}")
                        WalletStat("Games", "${uiState.wallet.gamesPlayedCount}")
                        WalletStat("Streak", "${uiState.wallet.streakDays}d")
                    }
                }
            }
        }

        // Virtual Coin Shop Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Virtual Coin Shop",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Spend earned coins on in-app badges, arcade passes & collector perks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(uiState.rewards, key = { it.id }) { reward ->
            RewardCard(
                reward = reward,
                coinBalance = uiState.wallet.coinBalance,
                onRedeemClick = { selectedReward = reward }
            )
        }

        // Activity History Header + Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Activity Ledger",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = ledgerFilter == filter,
                            onClick = { ledgerFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
        }

        if (filteredTransactions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No activity in this filter yet. Watch clips or complete quests to earn!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(filteredTransactions, key = { it.id }) { tx ->
                TransactionRow(tx = tx)
            }
        }
    }

    selectedReward?.let { reward ->
        RedeemDialog(
            reward = reward,
            onDismiss = { selectedReward = null },
            onConfirm = { destination ->
                onRedeemReward(reward, destination) {
                    selectedReward = null
                }
            }
        )
    }
}

@Composable
private fun WalletStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun RewardCard(
    reward: RewardOption,
    coinBalance: Int,
    onRedeemClick: () -> Unit
) {
    val canRedeem = coinBalance >= reward.coinCost
    val progress = (coinBalance.toFloat() / reward.coinCost.toFloat()).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DarkBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElectricBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CardGiftcard,
                            contentDescription = null,
                            tint = BrightCyanBlue
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = reward.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = reward.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${reward.brand} · ${reward.coinCost} Coins",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onRedeemClick,
                    enabled = canRedeem,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CashEmerald,
                        contentColor = InkNavy
                    ),
                    modifier = Modifier.testTag("redeem_${reward.id}")
                ) {
                    Text(if (canRedeem) "Unlock" else "${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
                }
            }

            if (!canRedeem) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = ElectricBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionEntity) {
    val isPositive = tx.amountCoins >= 0
    val icon = when (tx.type) {
        TransactionType.VIDEO.name -> Icons.Filled.PlayCircle
        TransactionType.GAME.name -> Icons.Filled.SportsEsports
        TransactionType.BONUS.name -> Icons.Filled.LocalFireDepartment
        TransactionType.QUEST.name -> Icons.Filled.Stars
        else -> Icons.Filled.Redeem
    }
    val timeText = remember(tx.createdAt) {
        SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(tx.createdAt))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, DarkBorder),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPositive) CashEmerald.copy(alpha = 0.14f) else CoralRed.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPositive) CashEmerald else CoralRed,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tx.subtitle} · $timeText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isPositive) "+${tx.amountCoins}" else "${tx.amountCoins}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isPositive) CashEmerald else CoralRed
            )
        }
    }
}

@Composable
private fun RedeemDialog(
    reward: RewardOption,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock ${reward.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = reward.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Virtual coins are for in-app entertainment and have no real-money value. Unlocking deducts ${reward.coinCost} coins from your balance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Optional ledger note") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("redeem_destination_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(note) },
                modifier = Modifier.testTag("confirm_redeem_button")
            ) {
                Text("Unlock (${reward.coinCost})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
