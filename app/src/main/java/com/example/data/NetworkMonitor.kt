package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkIsOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        registerCallbackSafely()
    }

    fun isCurrentlyConnected(): Boolean {
        val connected = checkIsOnline()
        if (_isOnline.value != connected) {
            _isOnline.value = connected
        }
        return connected
    }

    private fun checkIsOnline(): Boolean {
        val cm = connectivityManager ?: return true
        return runCatching {
            val activeNetwork = cm.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidatedOrVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            hasInternet && isValidatedOrVpn
        }.getOrDefault(true)
    }

    private fun registerCallbackSafely() {
        val cm = connectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = checkIsOnline()
            }

            override fun onLost(network: Network) {
                _isOnline.value = checkIsOnline()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                _isOnline.value = checkIsOnline()
            }

            override fun onUnavailable() {
                _isOnline.value = false
            }
        }
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
        }.onFailure {
            runCatching {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, callback)
            }
        }
    }

    companion object {
        const val OFFLINE_ERROR_MESSAGE =
            "Network connection is bad. Please check your internet connection and try again."
    }
}
