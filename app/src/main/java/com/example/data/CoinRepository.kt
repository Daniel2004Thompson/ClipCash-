package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.coinPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "clipcash_coins"
)

data class CoinRewardStatus(
    val balance: Int = 0,
    val adsWatchedToday: Int = 0,
    val dailyCap: Int = CoinRepository.DAILY_REWARDED_CAP,
    val lastRewardedAtMs: Long = 0L,
    val cooldownDurationMs: Long = CoinRepository.REWARDED_COOLDOWN_MS
) {
    fun cooldownSecondsRemaining(nowMs: Long = System.currentTimeMillis()): Int {
        if (lastRewardedAtMs <= 0L) return 0
        val remainingMs = cooldownDurationMs - (nowMs - lastRewardedAtMs)
        return if (remainingMs > 0L) ((remainingMs + 999L) / 1000L).toInt() else 0
    }

    fun isDailyCapReached(): Boolean = adsWatchedToday >= dailyCap

    fun canWatchRewardedAd(nowMs: Long = System.currentTimeMillis()): Boolean {
        return !isDailyCapReached() && cooldownSecondsRemaining(nowMs) == 0
    }
}

data class CoinSyncSnapshot(
    val ownerUserId: String = "",
    val ownerEmail: String = "",
    val lastSyncedBalance: Int? = null,
    val pendingCloudPush: Boolean = false,
    val adsWatchedDate: String = ""
)

interface CoinRepository {
    val status: Flow<CoinRewardStatus>

    suspend fun getStatus(): CoinRewardStatus

    suspend fun grantRewardedAdCoins(amount: Int = REWARD_COINS_PER_AD): Result<CoinRewardStatus>

    suspend fun grantInterstitialBreakCoins(amount: Int = POST_SHORTS_AD_COINS): Result<CoinRewardStatus>

    suspend fun consumeLegacyUnmigratedAdCoins(userId: String, email: String): Int

    suspend fun applySyncedCloudState(
        userId: String,
        email: String,
        balance: Int,
        adsWatchedToday: Int,
        adsWatchedDate: String,
        lastRewardedAtMs: Long,
        lastSyncedBalance: Int,
        pendingCloudPush: Boolean
    )

    suspend fun getSyncSnapshot(): CoinSyncSnapshot

    suspend fun markCloudPushPending(userId: String, email: String)

    suspend fun markCloudPushSucceeded(userId: String, email: String, syncedBalance: Int)

    suspend fun clearForSignOut()

    companion object {
        const val REWARD_COINS_PER_AD = 10
        const val POST_SHORTS_AD_COINS = 30
        const val POST_GAMES_AD_COINS = 30
        const val REWARDED_COOLDOWN_MS = 30_000L
        const val DAILY_REWARDED_CAP = 20
    }
}

class DataStoreCoinRepository(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val dateKeyProvider: () -> String = {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
) : CoinRepository {

    private val dataStore = context.applicationContext.coinPreferencesDataStore
    private val mutex = Mutex()

    override val status: Flow<CoinRewardStatus> = dataStore.data.map { prefs ->
        mapPreferencesToStatus(prefs, dateKeyProvider())
    }

    override suspend fun getStatus(): CoinRewardStatus {
        val prefs = dataStore.data.first()
        return mapPreferencesToStatus(prefs, dateKeyProvider())
    }

    override suspend fun grantRewardedAdCoins(amount: Int): Result<CoinRewardStatus> = mutex.withLock {
        if (amount <= 0) {
            return Result.failure(IllegalArgumentException("Reward amount must be positive."))
        }

        val nowMs = clock()
        val today = dateKeyProvider()
        var rejectionReason: String? = null
        var updatedStatus = CoinRewardStatus()

        dataStore.edit { prefs ->
            val current = mapPreferencesToStatus(prefs, today)
            if (current.isDailyCapReached()) {
                rejectionReason = "Daily rewarded ad limit (${CoinRepository.DAILY_REWARDED_CAP}/day) reached."
                updatedStatus = current
                return@edit
            }
            val cooldownSec = current.cooldownSecondsRemaining(nowMs)
            if (cooldownSec > 0) {
                rejectionReason = "Please wait ${cooldownSec}s before watching another rewarded ad."
                updatedStatus = current
                return@edit
            }

            val nextBalance = current.balance + amount
            val nextDailyCount = current.adsWatchedToday + 1

            prefs[KEY_COIN_BALANCE] = nextBalance
            prefs[KEY_DAILY_COUNT] = nextDailyCount
            prefs[KEY_DAILY_DATE] = today
            prefs[KEY_LAST_REWARDED_MS] = nowMs
            prefs[KEY_WALLET_MIGRATED] = true

            updatedStatus = CoinRewardStatus(
                balance = nextBalance,
                adsWatchedToday = nextDailyCount,
                dailyCap = CoinRepository.DAILY_REWARDED_CAP,
                lastRewardedAtMs = nowMs,
                cooldownDurationMs = CoinRepository.REWARDED_COOLDOWN_MS
            )
        }

        val error = rejectionReason
        if (error != null) {
            Result.failure(IllegalStateException(error))
        } else {
            Result.success(updatedStatus)
        }
    }

    override suspend fun grantInterstitialBreakCoins(amount: Int): Result<CoinRewardStatus> = mutex.withLock {
        if (amount <= 0) {
            return Result.failure(IllegalArgumentException("Reward amount must be positive."))
        }

        val today = dateKeyProvider()
        var updatedStatus = CoinRewardStatus()

        dataStore.edit { prefs ->
            val current = mapPreferencesToStatus(prefs, today)
            val nextBalance = current.balance + amount
            prefs[KEY_COIN_BALANCE] = nextBalance
            prefs[KEY_WALLET_MIGRATED] = true
            updatedStatus = current.copy(balance = nextBalance)
        }

        Result.success(updatedStatus)
    }

    override suspend fun consumeLegacyUnmigratedAdCoins(userId: String, email: String): Int = mutex.withLock {
        var migratedAmount = 0
        dataStore.edit { prefs ->
            val ownerId = prefs[KEY_OWNER_USER_ID].orEmpty()
            val alreadyMigrated = prefs[KEY_WALLET_MIGRATED] == true
            val storedBalance = prefs[KEY_COIN_BALANCE] ?: 0

            if (ownerId.isNotBlank() && ownerId != userId) {
                // Previous user's ad state must never leak into a different signed-in account.
                prefs.clear()
                prefs[KEY_OWNER_USER_ID] = userId
                prefs[KEY_OWNER_EMAIL] = email
                prefs[KEY_WALLET_MIGRATED] = true
                return@edit
            }

            if (!alreadyMigrated && storedBalance > 0) {
                migratedAmount = storedBalance
            }
            prefs[KEY_OWNER_USER_ID] = userId
            if (email.isNotBlank()) {
                prefs[KEY_OWNER_EMAIL] = email
            }
            prefs[KEY_WALLET_MIGRATED] = true
        }
        migratedAmount
    }

    override suspend fun applySyncedCloudState(
        userId: String,
        email: String,
        balance: Int,
        adsWatchedToday: Int,
        adsWatchedDate: String,
        lastRewardedAtMs: Long,
        lastSyncedBalance: Int,
        pendingCloudPush: Boolean
    ) {
        mutex.withLock {
            val today = dateKeyProvider()
            dataStore.edit { prefs ->
                val current = mapPreferencesToStatus(prefs, today)
                val sameOwner = prefs[KEY_OWNER_USER_ID].isNullOrBlank() || prefs[KEY_OWNER_USER_ID] == userId
                val effectiveDailyCount = when {
                    adsWatchedDate == today && sameOwner -> maxOf(adsWatchedToday, current.adsWatchedToday)
                    adsWatchedDate == today -> adsWatchedToday
                    sameOwner -> current.adsWatchedToday
                    else -> 0
                }
                val currentStoredDate = prefs[KEY_DAILY_DATE].orEmpty()
                val effectiveDate = when {
                    adsWatchedDate == today || (sameOwner && current.adsWatchedToday > 0) -> today
                    else -> adsWatchedDate.ifBlank { currentStoredDate }
                }
                val effectiveLastRewarded = if (sameOwner) {
                    maxOf(lastRewardedAtMs, current.lastRewardedAtMs)
                } else {
                    lastRewardedAtMs
                }

                prefs[KEY_OWNER_USER_ID] = userId
                if (email.isNotBlank()) {
                    prefs[KEY_OWNER_EMAIL] = email
                }
                prefs[KEY_COIN_BALANCE] = balance.coerceAtLeast(0)
                prefs[KEY_DAILY_COUNT] = effectiveDailyCount.coerceAtLeast(0)
                prefs[KEY_DAILY_DATE] = effectiveDate
                prefs[KEY_LAST_REWARDED_MS] = effectiveLastRewarded.coerceAtLeast(0L)
                prefs[KEY_LAST_SYNCED_BALANCE] = lastSyncedBalance
                prefs[KEY_PENDING_CLOUD_PUSH] = pendingCloudPush
                prefs[KEY_WALLET_MIGRATED] = true
            }
        }
    }

    override suspend fun getSyncSnapshot(): CoinSyncSnapshot {
        val prefs = dataStore.data.first()
        val today = dateKeyProvider()
        val storedDate = prefs[KEY_DAILY_DATE].orEmpty()
        return CoinSyncSnapshot(
            ownerUserId = prefs[KEY_OWNER_USER_ID].orEmpty(),
            ownerEmail = prefs[KEY_OWNER_EMAIL].orEmpty(),
            lastSyncedBalance = if (prefs.contains(KEY_LAST_SYNCED_BALANCE)) prefs[KEY_LAST_SYNCED_BALANCE] else null,
            pendingCloudPush = prefs[KEY_PENDING_CLOUD_PUSH] == true,
            adsWatchedDate = if (storedDate == today) storedDate else ""
        )
    }

    override suspend fun markCloudPushPending(userId: String, email: String) {
        mutex.withLock {
            dataStore.edit { prefs ->
                prefs[KEY_OWNER_USER_ID] = userId
                if (email.isNotBlank()) {
                    prefs[KEY_OWNER_EMAIL] = email
                }
                prefs[KEY_PENDING_CLOUD_PUSH] = true
            }
        }
    }

    override suspend fun markCloudPushSucceeded(userId: String, email: String, syncedBalance: Int) {
        mutex.withLock {
            dataStore.edit { prefs ->
                prefs[KEY_OWNER_USER_ID] = userId
                if (email.isNotBlank()) {
                    prefs[KEY_OWNER_EMAIL] = email
                }
                prefs[KEY_COIN_BALANCE] = syncedBalance.coerceAtLeast(0)
                prefs[KEY_LAST_SYNCED_BALANCE] = syncedBalance
                prefs[KEY_PENDING_CLOUD_PUSH] = false
                prefs[KEY_WALLET_MIGRATED] = true
            }
        }
    }

    override suspend fun clearForSignOut() {
        mutex.withLock {
            dataStore.edit { it.clear() }
        }
    }

    private fun mapPreferencesToStatus(prefs: Preferences, today: String): CoinRewardStatus {
        val storedDate = prefs[KEY_DAILY_DATE].orEmpty()
        val dailyCount = if (storedDate == today) prefs[KEY_DAILY_COUNT] ?: 0 else 0
        return CoinRewardStatus(
            balance = prefs[KEY_COIN_BALANCE] ?: 0,
            adsWatchedToday = dailyCount,
            dailyCap = CoinRepository.DAILY_REWARDED_CAP,
            lastRewardedAtMs = prefs[KEY_LAST_REWARDED_MS] ?: 0L,
            cooldownDurationMs = CoinRepository.REWARDED_COOLDOWN_MS
        )
    }

    private companion object {
        val KEY_COIN_BALANCE = intPreferencesKey("coin_balance")
        val KEY_DAILY_COUNT = intPreferencesKey("rewarded_daily_count")
        val KEY_DAILY_DATE = stringPreferencesKey("rewarded_daily_date")
        val KEY_LAST_REWARDED_MS = longPreferencesKey("last_rewarded_ad_ms")
        val KEY_OWNER_USER_ID = stringPreferencesKey("owner_user_id")
        val KEY_OWNER_EMAIL = stringPreferencesKey("owner_email")
        val KEY_LAST_SYNCED_BALANCE = intPreferencesKey("last_synced_balance")
        val KEY_PENDING_CLOUD_PUSH = androidx.datastore.preferences.core.booleanPreferencesKey("pending_cloud_push")
        val KEY_WALLET_MIGRATED = androidx.datastore.preferences.core.booleanPreferencesKey("wallet_migrated_v1")
    }
}
