package com.med.sleepmanager.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object ValidatedNetworkAwaiter {
    fun hasValidatedNetwork(context: Context): Boolean {
        val connectivity =
            context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager
                ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities =
            connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) &&
            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
    }

    fun await(
        context: Context,
        timeoutMs: Long
    ): Boolean {
        val appContext = context.applicationContext
        if (hasValidatedNetwork(appContext)) return true

        val connectivity =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager
                ?: return false
        val latch = CountDownLatch(1)
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    if (
                        capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET
                        ) &&
                        capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED
                        )
                    ) {
                        latch.countDown()
                    }
                }

                override fun onAvailable(network: Network) {
                    if (hasValidatedNetwork(appContext)) {
                        latch.countDown()
                    }
                }
            }

        return try {
            val request =
                NetworkRequest.Builder()
                    .addCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                    .build()
            connectivity.registerNetworkCallback(request, callback)
            if (hasValidatedNetwork(appContext)) {
                true
            } else {
                latch.await(
                    timeoutMs.coerceAtLeast(1L),
                    TimeUnit.MILLISECONDS
                ) && hasValidatedNetwork(appContext)
            }
        } catch (_: Throwable) {
            hasValidatedNetwork(appContext)
        } finally {
            runCatching {
                connectivity.unregisterNetworkCallback(callback)
            }
        }
    }
}
