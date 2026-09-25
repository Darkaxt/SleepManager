package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object TailscaleController {
    fun isInstalled(context: Context): Boolean =
        installedPackages(context).isNotEmpty()

    fun selectedPackage(context: Context): String? =
        activePackage(context)
            ?: TailscaleTargetPolicy.selectPreferredInstalledPackage(installedPackages(context))

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

    fun isConnected(context: Context, packageName: String): Boolean =
        activePackage(context) == packageName

    fun activePackage(context: Context): String? {
        val installedPackages = installedPackages(context)
        if (installedPackages.isEmpty()) return null

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null

        return runCatching {
            var tailscaleVpnPresent = false
            val activeOwnerPackages = linkedSetOf<String>()

            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.forEach { network ->
                val capabilities =
                    connectivityManager.getNetworkCapabilities(network)
                        ?: return@forEach

                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return@forEach
                }

                val linkProperties =
                    connectivityManager.getLinkProperties(network)
                        ?: return@forEach

                val hasTailscaleAddress = linkProperties.linkAddresses.any { linkAddress ->
                    isTailscaleAddress(linkAddress.address)
                }
                if (!hasTailscaleAddress) return@forEach

                tailscaleVpnPresent = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager
                        .getPackagesForUid(capabilities.ownerUid)
                        ?.let(activeOwnerPackages::addAll)
                }
            }

            TailscaleTargetPolicy.selectActivePackage(
                installedPackages = installedPackages,
                activeOwnerPackages = activeOwnerPackages,
                tailscaleVpnPresent = tailscaleVpnPresent
            )
        }.getOrNull()
    }

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
