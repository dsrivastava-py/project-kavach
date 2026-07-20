package com.kavach.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes real-time network connectivity using the modern [ConnectivityManager] API.
 * Emits [ConnectivityStatus] whenever the device's internet access changes.
 */
@Singleton
class NetworkConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class ConnectivityStatus { Available, Unavailable, Losing, Lost }

    /** Cold [Flow] that emits on every connectivity change. */
    val status: Flow<ConnectivityStatus> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network)     = trySend(ConnectivityStatus.Available).let {}
            override fun onLosing(network: Network, maxMsToLive: Int) = trySend(ConnectivityStatus.Losing).let {}
            override fun onLost(network: Network)          = trySend(ConnectivityStatus.Lost).let {}
            override fun onUnavailable()                   = trySend(ConnectivityStatus.Unavailable).let {}
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        manager.registerNetworkCallback(request, callback)

        // Emit initial state
        val current = manager.activeNetwork
            ?.let { manager.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ?: false
        trySend(if (current) ConnectivityStatus.Available else ConnectivityStatus.Unavailable)

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** Synchronous check — use sparingly (prefer collecting [status]). */
    fun isCurrentlyConnected(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.activeNetwork
            ?.let { manager.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ?: false
    }
}
