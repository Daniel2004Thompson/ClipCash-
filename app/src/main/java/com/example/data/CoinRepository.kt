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

interface CoinRepository {
    val status: Flow<CoinRewardStatus>

    suspend fun getStatus(): CoinRewardStatus

    suspend fun grantRewardedAdCoins(amount: Int = REWARD_COINS_PER_AD): Result<CoinRewardStatus>

    companion object {
        const val REWARD_COINS_PER_AD = 10
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
    }
}
