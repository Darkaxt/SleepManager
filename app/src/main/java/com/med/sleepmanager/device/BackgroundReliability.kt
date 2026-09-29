package com.med.sleepmanager.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import com.med.sleepmanager.protection.ThorDeviceAdminReceiver
import java.util.concurrent.TimeUnit

object BackgroundReliability {
    enum class Status {
        OK,
        NEEDS_ATTENTION,
        UNAVAILABLE,
        UNKNOWN
    }

    data class Snapshot(
        val batteryOptimization: Status,
        val unusedAppRestrictions: Status,
        val unusedAppRestrictionsExemptByDeviceAdmin: Boolean
    )

    fun snapshot(context: Context): Snapshot {
        val deviceAdminActive = sleepManagerDeviceAdminActive(context)

        return Snapshot(
            batteryOptimization = batteryOptimizationStatus(context),
            unusedAppRestrictions =
                unusedAppRestrictionsStatus(
                    context = context,
                    deviceAdminActive = deviceAdminActive
                ),
            unusedAppRestrictionsExemptByDeviceAdmin = deviceAdminActive
        )
    }

    private fun batteryOptimizationStatus(context: Context): Status {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return Status.UNAVAILABLE
        }

        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return Status.UNKNOWN

        return if (
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        ) {
            Status.OK
        } else {
            Status.NEEDS_ATTENTION
        }
    }

    private fun unusedAppRestrictionsStatus(
        context: Context,
        deviceAdminActive: Boolean
    ): Status {
        // Android exempts active device-admin apps from unused-app
        // restrictions. Some vendor builds can still report the generic
        // restriction API as enabled, so prefer the stronger exemption.
        if (deviceAdminActive) {
            return Status.OK
        }

        return runCatching {
            when (
                PackageManagerCompat
                    .getUnusedAppRestrictionsStatus(context)
                    .get(2, TimeUnit.SECONDS)
            ) {
                UnusedAppRestrictionsConstants.DISABLED ->
                    Status.OK

                UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE ->
                    Status.UNAVAILABLE

                UnusedAppRestrictionsConstants.API_30_BACKPORT,
                UnusedAppRestrictionsConstants.API_30,
                UnusedAppRestrictionsConstants.API_31 ->
                    Status.NEEDS_ATTENTION

                else ->
                    Status.UNKNOWN
            }
        }.getOrDefault(Status.UNKNOWN)
    }

    private fun sleepManagerDeviceAdminActive(context: Context): Boolean =
        runCatching {
            val dpm =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as? DevicePolicyManager
                    ?: return@runCatching false
            dpm.isAdminActive(
                ComponentName(
                    context,
                    ThorDeviceAdminReceiver::class.java
                )
            )
        }.getOrDefault(false)
}
