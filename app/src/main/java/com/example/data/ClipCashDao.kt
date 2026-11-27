package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipCashDao {
    @Query("SELECT * FROM wallet_state WHERE id = 1")
    fun observeWalletState(): Flow<WalletStateEntity?>

    @Query("SELECT * FROM wallet_state WHERE id = 1")
    suspend fun getWalletState(): WalletStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWalletState(wallet: WalletStateEntity)

    @Query("SELECT * FROM clip_progress")
    fun observeAllClipProgress(): Flow<List<ClipProgressEntity>>

    @Query("SELECT * FROM clip_progress")
    suspend fun getAllClipProgressSync(): List<ClipProgressEntity>

    @Query("SELECT * FROM clip_progress WHERE clipId = :clipId")
    suspend fun getClipProgress(clipId: String): ClipProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClipProgress(progress: ClipProgressEntity)

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT 200")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentTransactions(limit: Int = 40): List<TransactionEntity>

    @Insert
    suspend fun insertTransaction(tx: TransactionEntity)

    @Insert
    suspend fun insertTransactions(txs: List<TransactionEntity>)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("SELECT * FROM claimed_quests")
    fun observeClaimedQuests(): Flow<List<ClaimedQuestEntity>>

    @Query("SELECT questId FROM claimed_quests")
    suspend fun getClaimedQuestIdsSync(): List<String>

    @Query("SELECT * FROM claimed_quests WHERE questId = :questId")
    suspend fun getClaimedQuest(questId: String): ClaimedQuestEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClaimedQuest(quest: ClaimedQuestEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClaimedQuests(quests: List<ClaimedQuestEntity>)

    @Query("DELETE FROM claimed_quests")
    suspend fun clearClaimedQuests()

    @Query("DELETE FROM wallet_state")
    suspend fun clearWalletState()

    @Query("DELETE FROM clip_progress")
    suspend fun clearClipProgress()
}
