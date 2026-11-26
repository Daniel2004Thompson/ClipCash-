package com.example.ads

import com.example.BuildConfig

object AdIds {
    val BANNER = if (BuildConfig.DEBUG)
        "ca-app-pub-3940256099942544/9214589741"
    else
        "ca-app-pub-6630110637017284/4316658027"

    val INTERSTITIAL = if (BuildConfig.DEBUG)
        "ca-app-pub-3940256099942544/1033173712"
    else
        "ca-app-pub-6630110637017284/6500415348"

    val REWARDED = if (BuildConfig.DEBUG)
        "ca-app-pub-3940256099942544/5224354917"
    else
        "ca-app-pub-6630110637017284/5326487367"
}
