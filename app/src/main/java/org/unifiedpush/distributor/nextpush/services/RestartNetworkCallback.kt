package org.unifiedpush.distributor.nextpush.services

import android.app.Service
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import org.unifiedpush.distributor.nextpush.utils.TAG
import java.lang.Exception

class RestartNetworkCallback(val context: Context) : ConnectivityManager.NetworkCallback() {
    private var connectivityManager: ConnectivityManager? = null

    override fun onAvailable(network: Network) {
        Log.d(TAG, "Network is CONNECTED")
        if (FailureHandler.hasFailed(twice = true, orNeverStart = false)) {
            Log.d(TAG, "networkCallback: restarting worker")
            RestartWorker.run(context, delay = 0)
        }
    }

    override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
    ) {
        Log.d(TAG, "Network Capabilities changed")
        if (FailureHandler.hasFailed(twice = true, orNeverStart = false)) {
            Log.d(TAG, "networkCallback: restarting worker")
            RestartWorker.run(context, delay = 0)
        } // else, it retries in max 2sec
    }

    fun register() {
        connectivityManager?.let {
            Log.d(TAG, "ConnectivityManager already registered")
        } ?: run {
            Log.d(TAG, "Registering new ConnectivityManager")
            try {
                connectivityManager = (
                    context.getSystemService(Service.CONNECTIVITY_SERVICE) as ConnectivityManager
                    ).apply {
                    registerDefaultNetworkCallback(this@RestartNetworkCallback)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun unregister() {
        Log.d(TAG, "Unregistering ConnectivityManager")
        connectivityManager?.unregisterNetworkCallback(this)
        connectivityManager = null
    }
}