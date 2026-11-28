package com.example

import android.app.Application
import com.example.ads.AdManager
import com.example.data.CoinRepository
import com.example.data.DataStoreCoinRepository
import com.example.data.NetworkMonitor

class ClipCashApp : Application() {
    val adManager: AdManager by lazy { AdManager() }
    val coinRepository: CoinRepository by lazy { DataStoreCoinRepository(this) }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(this) }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            adManager.initialize(this, hasUserConsent = true)
        }
    }
}
