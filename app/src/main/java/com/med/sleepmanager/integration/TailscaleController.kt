package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object TailscaleController {
    fun isInstalled(context: Context): Boolean =
        installedPackages(context).isNotEmpty()

    /**
     * Package used for non-transactional UI (version, Open). Sleep control
     * never uses this: it uses [disconnectCandidates] and verified ownership.
     */
    fun selectedPackage(context: Context): String? =
        TailscaleTargetPolicy.selectPreferredInstalledPackage(installedPackages(context))

    fun displayName(context: Context, packageName: String): String {
        val label =
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()
        return TailscaleTargetPolicy.displayName(packageName, label)
    }

    fun versionName(context: Context): String? =
        selectedPackage(context)?.let { packageName ->
            runCatching {
                context.packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull()
        }

    fun open(context: Context): Boolean {
        val packageName = selectedPackage(context) ?: return false
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun isConnected(context: Context): Boolean =
        isInstalled(context) && hasTailscaleVpn(context)

    /**
     * Installed clients that may own the currently active Tailscale VPN, in
     * the order SleepManager tries them. Empty when no Tailscale VPN is up.
     */
    fun disconnectCandidates(context: Context): List<String> =
        TailscaleTargetPolicy.disconnectCandidates(
            installedPackages = installedPackages(context),
            tailscaleVpnPresent = hasTailscaleVpn(context)
        )

    fun nextDisconnectCandidate(context: Context, attemptedPackage: String): String? =
        TailscaleTargetPolicy.nextDisconnectCandidate(
            installedPackages = installedPackages(context),
            attemptedPackage = attemptedPackage
        )

    private fun hasTailscaleVpn(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        return runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.any { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                    ?: return@any false
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return@any false
                }

                connectivityManager.getLinkProperties(network)
                    ?.linkAddresses
                    ?.any { linkAddress -> isTailscaleAddress(linkAddress.address) }
                    ?: false
            }
        }.getOrDefault(false)
    }

    fun hasAnyVpnTransport(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        return runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.any { network ->
                connectivityManager
                    .getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrDefault(false)
    }

    fun sendDisconnect(context: Context, packageName: String): Boolean =
        sendControlBroadcast(
            context,
            packageName,
            TailscaleTargetPolicy.disconnectAction(packageName),
            "DISCONNECT"
        )

    fun sendConnect(context: Context, packageName: String): Boolean =
        sendControlBroadcast(
            context,
            packageName,
            TailscaleTargetPolicy.connectAction(packageName),
            "CONNECT"
        )

    private fun isTailscaleAddress(address: InetAddress): Boolean {
        val bytes = address.address

        if (address is Inet4Address && bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            return first == 100 && second in 64..127
        }

        if (address is Inet6Address && bytes.size == 16) {
            return (bytes[0].toInt() and 0xff) == 0xfd &&
                (bytes[1].toInt() and 0xff) == 0x7a &&
                (bytes[2].toInt() and 0xff) == 0x11 &&
                (bytes[3].toInt() and 0xff) == 0x5c &&
                (bytes[4].toInt() and 0xff) == 0xa1 &&
                (bytes[5].toInt() and 0xff) == 0xe0
        }

        return false
    }

    private fun sendControlBroadcast(
        context: Context,
        packageName: String,
        action: String,
        label: String
    ): Boolean {
        if (packageName !in installedPackages(context)) return false

        return runCatching {
            context.sendBroadcast(
                Intent(action)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            )
            Log.i("SleepManager", "Sent Tailscale $label to $packageName")
            true
        }.getOrElse {
            Log.e("SleepManager", "Unable to send Tailscale $label to $packageName", it)
            false
        }
    }

    private fun installedPackages(context: Context): Set<String> =
        TailscaleTargetPolicy.supportedPackages.filterTo(linkedSetOf()) { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
}
