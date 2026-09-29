package com.med.sleepmanager.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object HelperController {
    const val PACKAGE = "com.med.sleepmanager.helper"
    const val PERMISSION = "com.med.sleepmanager.permission.CONTROL_HELPER"

    private const val ACTION_SLEEP = "com.med.sleepmanager.helper.action.SLEEP"
    private const val ACTION_WAKE = "com.med.sleepmanager.helper.action.WAKE"
    private const val ACTION_RESTORE = "com.med.sleepmanager.helper.action.RESTORE"
    private const val ACTION_QUERY = "com.med.sleepmanager.helper.action.QUERY_STATE"
    private const val ACTION_FORGET_STATE = "com.med.sleepmanager.helper.action.FORGET_STATE"
    private const val ACTION_SET_TEMP_WIFI = "com.med.sleepmanager.helper.action.SET_TEMP_WIFI"
    const val ACTION_STATE = "com.med.sleepmanager.helper.action.STATE"
    const val ACTION_RESULT = "com.med.sleepmanager.helper.action.RESULT"

    private const val EXTRA_WIFI = "wifi"
    private const val EXTRA_BLUETOOTH = "bluetooth"
    private const val EXTRA_CYCLE_ID = "cycle_id"
    const val EXTRA_WIFI_STATE = "wifi_state"
    const val EXTRA_BLUETOOTH_STATE = "bluetooth_state"
    const val EXTRA_PHASE = "phase"
    const val EXTRA_WIFI_MANAGED = "wifi_managed"
    const val EXTRA_WIFI_PREVIOUS = "wifi_previous"
    const val EXTRA_WIFI_CHANGED = "wifi_changed"
    const val EXTRA_WIFI_ATTEMPTED = "wifi_attempted"
    const val EXTRA_WIFI_ACTION = "wifi_action"
    const val EXTRA_WIFI_TOGGLE_SUCCESS = "wifi_toggle_success"
    const val EXTRA_AIRPLANE_MODE = "airplane_mode"
    const val EXTRA_BLUETOOTH_MANAGED = "bluetooth_managed"
    const val EXTRA_BLUETOOTH_PREVIOUS = "bluetooth_previous"
    const val EXTRA_BLUETOOTH_CHANGED = "bluetooth_changed"
    const val EXTRA_RESTORE_SUCCESS = "restore_success"
    const val EXTRA_STATUS = "status"
    const val STATUS_OK = "OK"
    const val STATUS_ALREADY_SLEEPING = "ALREADY_SLEEPING"
    const val STATUS_ALREADY_RESTORED = "ALREADY_RESTORED"
    const val STATUS_NO_ACTIVE_CYCLE = "NO_ACTIVE_CYCLE"
    const val STATUS_RESTORE_FAILED = "RESTORE_FAILED"
    const val STATUS_WIFI_TOGGLE_FAILED = "WIFI_TOGGLE_FAILED"
    const val PHASE_SLEEP = "sleep"
    const val PHASE_WAKE = "wake"
    const val PHASE_MAINTENANCE_WIFI = "maintenance_wifi"

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun sendSleep(
        context: Context,
        wifi: Boolean,
        bluetooth: Boolean,
        cycleId: Long
    ): Boolean {
        if (!isInstalled(context)) return false
        val intent = Intent(ACTION_SLEEP)
            .setPackage(PACKAGE)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(EXTRA_WIFI, wifi)
            .putExtra(EXTRA_BLUETOOTH, bluetooth)
            .putExtra(EXTRA_CYCLE_ID, cycleId)
        context.sendBroadcast(intent, PERMISSION)
        Log.i("SleepManager", "Helper sleep request: wifi=$wifi bluetooth=$bluetooth")
        return true
    }

    fun sendWake(context: Context, cycleId: Long): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            Intent(ACTION_WAKE)
                .setPackage(PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(EXTRA_CYCLE_ID, cycleId),
            PERMISSION
        )
        Log.i("SleepManager", "Helper wake request")
        return true
    }


    fun setTemporaryWifi(context: Context, enabled: Boolean): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            Intent(ACTION_SET_TEMP_WIFI)
                .setPackage(PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(EXTRA_WIFI, enabled),
            PERMISSION
        )
        Log.i("SleepManager", "Helper temporary Wi-Fi request: enabled=$enabled")
        return true
    }

    fun requestState(context: Context): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            Intent(ACTION_QUERY)
                .setPackage(PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            PERMISSION
        )
        Log.i("SleepManager", "Helper state query")
        return true
    }

    fun forgetPendingState(context: Context): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            Intent(ACTION_FORGET_STATE)
                .setPackage(PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            PERMISSION
        )
        Log.i("SleepManager", "Helper pending state forget request")
        return true
    }

    fun restoreNow(context: Context, cycleId: Long): Boolean {
        if (!isInstalled(context)) return false
        context.sendBroadcast(
            Intent(ACTION_RESTORE)
                .setPackage(PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(EXTRA_CYCLE_ID, cycleId),
            PERMISSION
        )
        Log.i("SleepManager", "Helper restore request")
        return true
    }
}
