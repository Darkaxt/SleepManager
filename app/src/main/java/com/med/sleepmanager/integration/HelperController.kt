package com.med.sleepmanager.integration

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.os.Build
import android.os.PowerManager
import com.med.sleepmanager.protocol.HelperProtocol

data class HelperWifiControlAccess(
    val permissionGranted: Boolean,
    val appOpMode: Int?,
    val appOpModeName: String,
    val effectivelyAllowed: Boolean?
)

object HelperController {
    @JvmField val PACKAGE = HelperProtocol.HELPER_PACKAGE
    @JvmField val PERMISSION = HelperProtocol.PERMISSION
    @JvmField val PERMISSION_V2 = HelperProtocol.PERMISSION_V2

    private val ACTION_SLEEP = HelperProtocol.ACTION_SLEEP
    private val ACTION_WAKE = HelperProtocol.ACTION_WAKE
    private val ACTION_RESTORE = HelperProtocol.ACTION_RESTORE
    private val ACTION_QUERY = HelperProtocol.ACTION_QUERY
    private val ACTION_FORGET_STATE = HelperProtocol.ACTION_FORGET_STATE
    private val ACTION_SET_TEMP_WIFI = HelperProtocol.ACTION_SET_TEMP_WIFI
    private val HELPER_RECEIVER =
        ComponentName(PACKAGE, HelperProtocol.HELPER_RECEIVER_CLASS)
    private val HELPER_ACTIVATION =
        ComponentName(PACKAGE, HelperProtocol.HELPER_ACTIVATION_ACTIVITY_CLASS)
    private val HELPER_RECEIVER_V2 =
        ComponentName(PACKAGE, HelperProtocol.HELPER_RECEIVER_V2_CLASS)
    private val HELPER_ACTIVATION_V2 =
        ComponentName(PACKAGE, HelperProtocol.HELPER_ACTIVATION_ACTIVITY_V2_CLASS)
    private val HELPER_COMMAND_SERVICE_V2 =
        ComponentName(PACKAGE, HelperProtocol.HELPER_COMMAND_SERVICE_V2_CLASS)
    @JvmField val ACTION_STATE = HelperProtocol.ACTION_STATE
    @JvmField val ACTION_RESULT = HelperProtocol.ACTION_RESULT
    @JvmField val ACTION_STATE_V2 = HelperProtocol.ACTION_STATE_V2
    @JvmField val ACTION_RESULT_V2 = HelperProtocol.ACTION_RESULT_V2

    private val EXTRA_WIFI = HelperProtocol.EXTRA_WIFI
    private val EXTRA_BLUETOOTH = HelperProtocol.EXTRA_BLUETOOTH
    @JvmField val EXTRA_CYCLE_ID = HelperProtocol.EXTRA_CYCLE_ID
    @JvmField val EXTRA_WIFI_STATE = HelperProtocol.EXTRA_WIFI_STATE
    @JvmField val EXTRA_BLUETOOTH_STATE = HelperProtocol.EXTRA_BLUETOOTH_STATE
    @JvmField val EXTRA_PHASE = HelperProtocol.EXTRA_PHASE
    @JvmField val EXTRA_WIFI_MANAGED = HelperProtocol.EXTRA_WIFI_MANAGED
    @JvmField val EXTRA_WIFI_PREVIOUS = HelperProtocol.EXTRA_WIFI_PREVIOUS
    @JvmField val EXTRA_WIFI_CHANGED = HelperProtocol.EXTRA_WIFI_CHANGED
    @JvmField val EXTRA_WIFI_ATTEMPTED = HelperProtocol.EXTRA_WIFI_ATTEMPTED
    @JvmField val EXTRA_WIFI_ACTION = HelperProtocol.EXTRA_WIFI_ACTION
    @JvmField val EXTRA_WIFI_TOGGLE_SUCCESS = HelperProtocol.EXTRA_WIFI_TOGGLE_SUCCESS
    @JvmField val EXTRA_AIRPLANE_MODE = HelperProtocol.EXTRA_AIRPLANE_MODE
    @JvmField val EXTRA_BLUETOOTH_MANAGED = HelperProtocol.EXTRA_BLUETOOTH_MANAGED
    @JvmField val EXTRA_BLUETOOTH_PREVIOUS = HelperProtocol.EXTRA_BLUETOOTH_PREVIOUS
    @JvmField val EXTRA_BLUETOOTH_CHANGED = HelperProtocol.EXTRA_BLUETOOTH_CHANGED
    @JvmField val EXTRA_RESTORE_SUCCESS = HelperProtocol.EXTRA_RESTORE_SUCCESS
    @JvmField val EXTRA_STATUS = HelperProtocol.EXTRA_STATUS
    @JvmField val STATUS_OK = HelperProtocol.STATUS_OK
    @JvmField val STATUS_ALREADY_SLEEPING = HelperProtocol.STATUS_ALREADY_SLEEPING
    @JvmField val STATUS_ALREADY_RESTORED = HelperProtocol.STATUS_ALREADY_RESTORED
    @JvmField val STATUS_NO_ACTIVE_CYCLE = HelperProtocol.STATUS_NO_ACTIVE_CYCLE
    @JvmField val STATUS_RESTORE_FAILED = HelperProtocol.STATUS_RESTORE_FAILED
    @JvmField val STATUS_WIFI_TOGGLE_FAILED = HelperProtocol.STATUS_WIFI_TOGGLE_FAILED
    @JvmField val STATUS_CYCLE_MISMATCH = HelperProtocol.STATUS_CYCLE_MISMATCH
    @JvmField val PHASE_SLEEP = HelperProtocol.PHASE_SLEEP
    @JvmField val PHASE_WAKE = HelperProtocol.PHASE_WAKE
    @JvmField val PHASE_MAINTENANCE_WIFI = HelperProtocol.PHASE_MAINTENANCE_WIFI

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun helperVersionCode(context: Context): Long =
        runCatching {
            context.packageManager.getPackageInfo(PACKAGE, 0).longVersionCode
        }.getOrDefault(0L)

    private fun ownerStablePermissionGranted(context: Context): Boolean =
        context.packageManager.checkPermission(
            PERMISSION_V2,
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED

    fun usesOwnerStableProtocol(context: Context): Boolean =
        helperVersionCode(context) >=
            HelperProtocol.HELPER_PROTOCOL_V2_MIN_VERSION_CODE &&
            ownerStablePermissionGranted(context)

    private fun usesOwnerStableService(context: Context): Boolean =
        helperVersionCode(context) >=
            HelperProtocol.HELPER_SERVICE_V2_MIN_VERSION_CODE &&
            ownerStablePermissionGranted(context)

    fun responsePermission(context: Context): String =
        if (usesOwnerStableProtocol(context)) PERMISSION_V2 else PERMISSION

    fun stateAction(context: Context): String =
        if (usesOwnerStableProtocol(context)) ACTION_STATE_V2 else ACTION_STATE

    fun resultAction(context: Context): String =
        if (usesOwnerStableProtocol(context)) ACTION_RESULT_V2 else ACTION_RESULT

    private fun commandReceiver(context: Context): ComponentName =
        if (usesOwnerStableProtocol(context)) {
            HELPER_RECEIVER_V2
        } else {
            HELPER_RECEIVER
        }

    private fun activationActivity(context: Context): ComponentName =
        if (usesOwnerStableProtocol(context)) {
            HELPER_ACTIVATION_V2
        } else {
            HELPER_ACTIVATION
        }

    fun isBatteryUnrestricted(context: Context): Boolean {
        if (!isInstalled(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return false

        return powerManager.isIgnoringBatteryOptimizations(PACKAGE)
    }

    /**
     * Android 9+ exposes Wi-Fi control as the OP_CHANGE_WIFI_STATE app-op in
     * addition to the normal CHANGE_WIFI_STATE manifest permission. OEMs may
     * still impose an extra legacy confirmation even when this reports allowed,
     * so diagnostics keep the raw app-op mode instead of treating it as proof
     * that no OEM prompt can appear.
     */
    fun wifiControlAccess(context: Context): HelperWifiControlAccess {
        val packageManager = context.packageManager
        val permissionGranted =
            packageManager.checkPermission(
                Manifest.permission.CHANGE_WIFI_STATE,
                PACKAGE
            ) == PackageManager.PERMISSION_GRANTED

        if (!isInstalled(context)) {
            return HelperWifiControlAccess(
                permissionGranted = false,
                appOpMode = null,
                appOpModeName = "helper_not_installed",
                effectivelyAllowed = false
            )
        }

        val uid =
            runCatching {
                packageManager.getApplicationInfo(PACKAGE, 0).uid
            }.getOrNull()
        val appOps =
            context.getSystemService(Context.APP_OPS_SERVICE)
                as? AppOpsManager
        val mode =
            if (uid != null && appOps != null) {
                runCatching {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        "android:change_wifi_state",
                        uid,
                        PACKAGE
                    )
                }.getOrNull()
            } else {
                null
            }

        val allowed =
            when (mode) {
                AppOpsManager.MODE_ALLOWED,
                AppOpsManager.MODE_DEFAULT ->
                    permissionGranted
                AppOpsManager.MODE_IGNORED,
                AppOpsManager.MODE_ERRORED ->
                    false
                else ->
                    null
            }

        return HelperWifiControlAccess(
            permissionGranted = permissionGranted,
            appOpMode = mode,
            appOpModeName =
                when (mode) {
                    AppOpsManager.MODE_ALLOWED -> "allowed"
                    AppOpsManager.MODE_DEFAULT -> "default"
                    AppOpsManager.MODE_IGNORED -> "ignored"
                    AppOpsManager.MODE_ERRORED -> "errored"
                    null -> "unknown"
                    else -> "mode_$mode"
                },
            effectivelyAllowed = allowed
        )
    }

    private fun activateIfFreshlyStopped(context: Context) {
        val applicationInfo =
            runCatching {
                context.packageManager.getApplicationInfo(PACKAGE, 0)
            }.getOrNull() ?: return

        if (applicationInfo.flags and ApplicationInfo.FLAG_STOPPED == 0) {
            return
        }

        runCatching {
            context.startActivity(
                Intent()
                    .setComponent(activationActivity(context))
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
            )
            Log.i("SleepManager", "Compatibility Helper activation requested")
        }.onFailure { error ->
            Log.w(
                "SleepManager",
                "Compatibility Helper activation failed",
                error
            )
        }
    }

    private fun commandIntent(action: String): Intent =
        Intent(action)

    private fun dispatchCommand(
        context: Context,
        intent: Intent
    ): Boolean {
        if (usesOwnerStableService(context)) {
            return runCatching {
                context.startService(
                    Intent(intent).setComponent(HELPER_COMMAND_SERVICE_V2)
                )
                Log.i(
                    "SleepManager",
                    "Helper command service start: action=${intent.action}"
                )
                true
            }.getOrElse { error ->
                Log.w(
                    "SleepManager",
                    "Helper command service start failed: action=${intent.action}",
                    error
                )
                false
            }
        }

        context.sendBroadcast(
            Intent(intent)
                .setComponent(commandReceiver(context))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        )
        return true
    }

    fun sendSleep(
        context: Context,
        wifi: Boolean,
        bluetooth: Boolean,
        cycleId: Long
    ): Boolean {
        if (!isInstalled(context)) return false
        val intent = commandIntent(ACTION_SLEEP)
            .putExtra(EXTRA_WIFI, wifi)
            .putExtra(EXTRA_BLUETOOTH, bluetooth)
            .putExtra(EXTRA_CYCLE_ID, cycleId)
        val sent = dispatchCommand(context, intent)
        Log.i(
            "SleepManager",
            "Helper sleep request: wifi=$wifi bluetooth=$bluetooth sent=$sent"
        )
        return sent
    }

    fun sendWake(context: Context, cycleId: Long): Boolean {
        if (!isInstalled(context)) return false
        val sent =
            dispatchCommand(
                context,
                commandIntent(ACTION_WAKE)
                    .putExtra(EXTRA_CYCLE_ID, cycleId)
            )
        Log.i("SleepManager", "Helper wake request: sent=$sent")
        return sent
    }


    fun setTemporaryWifi(context: Context, enabled: Boolean): Boolean {
        if (!isInstalled(context)) return false
        val sent =
            dispatchCommand(
                context,
                commandIntent(ACTION_SET_TEMP_WIFI)
                    .putExtra(EXTRA_WIFI, enabled)
            )
        Log.i(
            "SleepManager",
            "Helper temporary Wi-Fi request: enabled=$enabled sent=$sent"
        )
        return sent
    }

    fun requestState(context: Context): Boolean {
        if (!isInstalled(context)) return false
        activateIfFreshlyStopped(context)
        val sent =
            dispatchCommand(
                context,
                commandIntent(ACTION_QUERY)
            )
        Log.i("SleepManager", "Helper state query: sent=$sent")
        return sent
    }

    fun forgetPendingState(context: Context): Boolean {
        if (!isInstalled(context)) return false
        val sent =
            dispatchCommand(
                context,
                commandIntent(ACTION_FORGET_STATE)
            )
        Log.i("SleepManager", "Helper pending state forget request: sent=$sent")
        return sent
    }

    fun restoreNow(context: Context, cycleId: Long): Boolean {
        if (!isInstalled(context)) return false
        val sent =
            dispatchCommand(
                context,
                commandIntent(ACTION_RESTORE)
                    .putExtra(EXTRA_CYCLE_ID, cycleId)
            )
        Log.i("SleepManager", "Helper restore request: sent=$sent")
        return sent
    }
}
