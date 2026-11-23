package com.med.sleepmanager.integration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object JamesDspController {
    const val O2P_PACKAGE = "james.dsp"
    const val ROOTLESS_PACKAGE = "me.timschneeberger.rootlessjamesdsp"

    private const val ACTION_SET_POWER =
        "me.timschneeberger.rootlessjamesdsp.SET_POWER_STATE"
    private const val EXTRA_ENABLED = "rootlessjamesdsp.enabled"
    private const val RECEIVER_CLASS =
        "me.timschneeberger.rootlessjamesdsp.receiver.PowerStateReceiver"

    data class Target(
        val packageName: String,
        val displayName: String,
        val versionName: String?
    )

    fun installedTargets(context: Context): List<Target> =
        listOf(
            O2P_PACKAGE to "JamesDSP Manager",
            ROOTLESS_PACKAGE to "RootlessJamesDSP"
        ).mapNotNull { (packageName, displayName) ->
            packageInfo(context, packageName)?.let { info ->
                Target(
                    packageName = packageName,
                    displayName = displayName,
                    versionName = info.versionName
                )
            }
        }

    fun selectedTarget(context: Context): Target? =
        installedTargets(context).firstOrNull()

    fun isInstalled(context: Context): Boolean =
        selectedTarget(context) != null

    fun open(context: Context): Boolean {
        val target = selectedTarget(context) ?: return false
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(target.packageName)
                ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    /**
     * Sends a power command. Fire-and-forget by necessity: true means the
     * explicit broadcast was handed to Android, not that JamesDSP applied it.
     * JamesDSP exposes no state readback; see JamesDspConnector.currentState.
     */
    fun setPowered(
        context: Context,
        packageName: String,
        enabled: Boolean
    ): Boolean {
        if (packageInfo(context, packageName) == null) return false

        return runCatching {
            val intent =
                Intent(ACTION_SET_POWER)
                    .setComponent(ComponentName(packageName, RECEIVER_CLASS))
                    .putExtra(EXTRA_ENABLED, enabled)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

            context.sendBroadcast(intent)
            Log.i(
                "SleepManager",
                "Sent JamesDSP power=${if (enabled) "ON" else "OFF"} to $packageName"
            )
            true
        }.getOrElse {
            Log.e(
                "SleepManager",
                "Unable to send JamesDSP power=${if (enabled) "ON" else "OFF"} to $packageName",
                it
            )
            false
        }
    }

    private fun packageInfo(
        context: Context,
        packageName: String
    ) = try {
        context.packageManager.getPackageInfo(packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
