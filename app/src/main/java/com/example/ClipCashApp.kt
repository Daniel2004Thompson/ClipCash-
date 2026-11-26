package com.example

import android.app.Application
import com.google.android.gms.ads.MobileAds

class ClipCashApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            MobileAds.initialize(this) {}
        }
    }
}
