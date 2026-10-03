package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.connector.SyncthingConnector
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SyncthingController {
    const val PACKAGE_CURRENT = "com.github.catfriend1.syncthingfork"
    const val PACKAGE_CURRENT_DEBUG = "com.github.catfriend1.syncthingfork.debug"
    const val PACKAGE_LEGACY = "com.github.catfriend1.syncthingandroid"
    const val PACKAGE_LEGACY_DEBUG = "com.github.catfriend1.syncthingandroid.debug"

    private const val DEFAULT_GUI_HOST = "127.0.0.1"
    private const val DEFAULT_GUI_PORT = 8384
    private const val HEALTH_TIMEOUT_MS = 500

    enum class RuntimeState {
        RUNNING,
        STOPPED,
        UNKNOWN
    }

    enum class HealthProbeState {
        RUNNING,
        UNAVAILABLE
    }

    private val supported = listOf(
        PACKAGE_CURRENT,
        PACKAGE_CURRENT_DEBUG,
        PACKAGE_LEGACY,
        PACKAGE_LEGACY_DEBUG
    )

    data class Target(val packageName: String, val displayName: String)

    fun installedTargets(context: Context): List<Target> =
        supported
            .filter { isInstalled(context, it) }
            .map { Target(it, displayName(context, it)) }

    fun selectedTarget(context: Context): Target? {
        val installed = installedTargets(context)
        if (installed.isEmpty()) return null

        val preferred = AppPreferences.getSelectedSyncthing(context)
        val target =
            installed.firstOrNull { it.packageName == preferred }
                ?: installed.first()

        if (target.packageName != preferred) {
            AppPreferences.setSelectedSyncthing(
                context,
                target.packageName
            )
        }

        return target
    }

    fun select(context: Context, packageName: String) {
        if (
            supported.contains(packageName) &&
            isInstalled(context, packageName)
        ) {
            AppPreferences.setSelectedSyncthing(context, packageName)
        }
    }

    fun runtimeState(context: Context): RuntimeState {
        if (selectedTarget(context) == null) {
            return RuntimeState.UNKNOWN
        }

        if (
            SleepCycleStore.hasConnectorChange(
                context,
                SyncthingConnector.id
            )
        ) {
            return RuntimeState.STOPPED
        }

        return when (healthProbeState()) {
            HealthProbeState.RUNNING -> RuntimeState.RUNNING
            HealthProbeState.UNAVAILABLE -> RuntimeState.UNKNOWN
        }
    }

    fun healthProbeState(): HealthProbeState =
        if (healthCheckDefaultGui()) {
            HealthProbeState.RUNNING
        } else {
            HealthProbeState.UNAVAILABLE
        }

    fun sendStart(context: Context) =
        send(
            context,
            selectedTarget(context)?.packageName,
            ".action.START",
            "START"
        )

    fun sendStartTo(context: Context, packageName: String) =
        send(context, packageName, ".action.START", "START")

    fun sendStop(context: Context) =
        send(
            context,
            selectedTarget(context)?.packageName,
            ".action.STOP",
            "STOP"
        )

    fun sendStopTo(context: Context, packageName: String) =
        send(context, packageName, ".action.STOP", "STOP")

    fun sendFollow(context: Context) =
        send(
            context,
            selectedTarget(context)?.packageName,
            ".action.FOLLOW",
            "FOLLOW"
        )

    fun sendFollowTo(context: Context, packageName: String) =
        send(context, packageName, ".action.FOLLOW", "FOLLOW")

    fun open(context: Context): Boolean {
        val target = selectedTarget(context) ?: return false
        val launch =
            context.packageManager
                .getLaunchIntentForPackage(target.packageName)
                ?: return false

        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }

    private fun healthCheckDefaultGui(): Boolean =
        healthCheck(
            "http://$DEFAULT_GUI_HOST:$DEFAULT_GUI_PORT/rest/noauth/health"
        ) ||
            healthCheck(
                "https://$DEFAULT_GUI_HOST:$DEFAULT_GUI_PORT/rest/noauth/health"
            )

    private fun healthCheck(urlString: String): Boolean =
        runCatching {
            val connection =
                URL(urlString).openConnection() as HttpURLConnection

            connection.connectTimeout = HEALTH_TIMEOUT_MS
            connection.readTimeout = HEALTH_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"

            if (connection is HttpsURLConnection) {
                connection.sslSocketFactory =
                    localSyncthingSslContext().socketFactory
                connection.hostnameVerifier =
                    javax.net.ssl.HostnameVerifier { hostname, _ ->
                        hostname == DEFAULT_GUI_HOST ||
                            hostname == "localhost"
                    }
            }

            try {
                connection.responseCode == HttpURLConnection.HTTP_OK
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)

    private fun localSyncthingSslContext(): SSLContext {
        val trustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    emptyArray()
            }

        return SSLContext.getInstance("TLS").apply {
            init(
                null,
                arrayOf<TrustManager>(trustManager),
                SecureRandom()
            )
        }
    }

    private fun send(
        context: Context,
        packageName: String?,
        suffix: String,
        label: String
    ): Boolean {
        if (
            packageName == null ||
            !isInstalled(context, packageName)
        ) {
            return false
        }

        return try {
            context.sendBroadcast(
                Intent(packageName + suffix)
                    .setPackage(packageName)
            )
            Log.i(
                "SleepManager",
                "Sent Syncthing $label to $packageName"
            )
            true
        } catch (t: Throwable) {
            Log.e(
                "SleepManager",
                "Unable to send Syncthing $label to $packageName",
                t
            )
            false
        }
    }

    private fun isInstalled(
        context: Context,
        packageName: String
    ): Boolean =
        try {
            context.packageManager.getPackageInfo(
                packageName,
                0
            )
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun displayName(
        context: Context,
        packageName: String
    ): String {
        val base = when (packageName) {
            PACKAGE_CURRENT -> "Syncthing-Fork"
            PACKAGE_CURRENT_DEBUG ->
                "Syncthing-Fork Debug / Root"
            PACKAGE_LEGACY ->
                "Syncthing-Fork Legacy"
            PACKAGE_LEGACY_DEBUG ->
                "Syncthing-Fork Legacy Debug"
            else -> "Syncthing-Fork"
        }

        return try {
            val version =
                context.packageManager
                    .getPackageInfo(packageName, 0)
                    .versionName

            if (version.isNullOrBlank()) {
                base
            } else {
                "$base • $version"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            base
        }
    }
}
