package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet_state")
data class WalletStateEntity(
    @PrimaryKey val id: Int = 1,
    val userId: String = "",
    val userEmail: String = "",
    val coinBalance: Int = 150,
    val totalEarnedCoins: Int = 150,
    val streakDays: Int = 0,
    val lastCheckInDate: String = "",
    val lastFreeSpinDate: String = "",
    val clipsWatchedCount: Int = 0,
    val gamesPlayedCount: Int = 0
)

@Entity(tableName = "clip_progress")
data class ClipProgressEntity(
    @PrimaryKey val clipId: String,
    val watchCount: Int = 0,
    val lastWatchedAt: Long = 0L,
    val earnedCoinsTotal: Int = 0
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val subtitle: String,
    val amountCoins: Int,
    val type: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "claimed_quests")
data class ClaimedQuestEntity(
    @PrimaryKey val questId: String,
    val claimedAt: Long = System.currentTimeMillis()
)
