package com.med.sleepmanager.helper

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log

class SleepManagerHelperReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SleepManagerHelper"
        private const val ACTION_SLEEP = "com.med.sleepmanager.helper.action.SLEEP"
        private const val ACTION_WAKE = "com.med.sleepmanager.helper.action.WAKE"
        private const val ACTION_RESTORE = "com.med.sleepmanager.helper.action.RESTORE"
        private const val ACTION_QUERY = "com.med.sleepmanager.helper.action.QUERY_STATE"
        private const val ACTION_FORGET_STATE = "com.med.sleepmanager.helper.action.FORGET_STATE"
        private const val ACTION_SET_TEMP_WIFI = "com.med.sleepmanager.helper.action.SET_TEMP_WIFI"
        private const val ACTION_STATE = "com.med.sleepmanager.helper.action.STATE"
        private const val ACTION_RESULT = "com.med.sleepmanager.helper.action.RESULT"
        private const val MAIN_PACKAGE = "com.med.sleepmanager"
        private const val PERMISSION = "com.med.sleepmanager.permission.CONTROL_HELPER"

        private const val EXTRA_WIFI = "wifi"
        private const val EXTRA_BLUETOOTH = "bluetooth"
        private const val EXTRA_CYCLE_ID = "cycle_id"
        private const val EXTRA_WIFI_STATE = "wifi_state"
        private const val EXTRA_BLUETOOTH_STATE = "bluetooth_state"
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_WIFI_MANAGED = "wifi_managed"
        private const val EXTRA_WIFI_PREVIOUS = "wifi_previous"
        private const val EXTRA_WIFI_CHANGED = "wifi_changed"
        private const val EXTRA_WIFI_ATTEMPTED = "wifi_attempted"
        private const val EXTRA_WIFI_ACTION = "wifi_action"
        private const val EXTRA_WIFI_TOGGLE_SUCCESS = "wifi_toggle_success"
        private const val EXTRA_AIRPLANE_MODE = "airplane_mode"
        private const val EXTRA_BLUETOOTH_MANAGED = "bluetooth_managed"
        private const val EXTRA_BLUETOOTH_PREVIOUS = "bluetooth_previous"
        private const val EXTRA_BLUETOOTH_CHANGED = "bluetooth_changed"
        private const val EXTRA_RESTORE_SUCCESS = "restore_success"
        private const val EXTRA_STATUS = "status"
        private const val STATUS_OK = "OK"
        private const val STATUS_ALREADY_SLEEPING = "ALREADY_SLEEPING"
        private const val STATUS_ALREADY_RESTORED = "ALREADY_RESTORED"
        private const val STATUS_NO_ACTIVE_CYCLE = "NO_ACTIVE_CYCLE"
        private const val STATUS_RESTORE_FAILED = "RESTORE_FAILED"
        private const val STATUS_WIFI_TOGGLE_FAILED = "WIFI_TOGGLE_FAILED"
        private const val PHASE_SLEEP = "sleep"
        private const val PHASE_WAKE = "wake"
        private const val PHASE_MAINTENANCE_WIFI = "maintenance_wifi"

        private const val PREFS = "helper_state"
        private const val KEY_CYCLE_ACTIVE = "cycle_active"
        private const val KEY_CYCLE_ID = "cycle_id"
        private const val KEY_LAST_RESTORED_CYCLE_ID = "last_restored_cycle_id"
        private const val KEY_WIFI_PREVIOUS = "wifi_previous"
        private const val KEY_WIFI_CHANGED = "wifi_changed"
        private const val KEY_BT_PREVIOUS = "bt_previous"
        private const val KEY_BT_CHANGED = "bt_changed"
        private const val KEY_WIFI_MANAGED = "wifi_managed"
        private const val KEY_BT_MANAGED = "bt_managed"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_SLEEP -> enterSleep(
                context,
                manageWifi = intent.getBooleanExtra(EXTRA_WIFI, false),
                manageBluetooth = intent.getBooleanExtra(EXTRA_BLUETOOTH, false),
                cycleId = intent.getLongExtra(EXTRA_CYCLE_ID, 0L)
            )
            ACTION_WAKE, ACTION_RESTORE -> restore(
                context,
                requestedCycleId = intent.getLongExtra(EXTRA_CYCLE_ID, 0L)
            )
            ACTION_QUERY -> reportCurrentState(context)
            ACTION_SET_TEMP_WIFI -> setTemporaryWifi(
                context,
                enabled = intent.getBooleanExtra(EXTRA_WIFI, false)
            )
            ACTION_FORGET_STATE -> {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
                Log.i(TAG, "Pending Helper state forgotten")
            }
        }
    }

    private fun reportCurrentState(context: Context) {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val bluetooth = bluetoothAdapter(context)

        val wifiOn = safeWifiState(wifiManager)
        val bluetoothOn = safeBluetoothState(bluetooth)

        val response = Intent(ACTION_STATE)
            .setPackage(MAIN_PACKAGE)
            .putExtra(EXTRA_WIFI_STATE, wifiOn)
            .putExtra(EXTRA_BLUETOOTH_STATE, bluetoothOn)

        context.sendBroadcast(response, PERMISSION)
        Log.i(TAG, "Current state reported: wifi=$wifiOn bluetooth=$bluetoothOn")
    }

    private fun enterSleep(
        context: Context,
        manageWifi: Boolean,
        manageBluetooth: Boolean,
        cycleId: Long
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CYCLE_ACTIVE, false)) {
            Log.i(TAG, "Sleep cycle already active; reporting existing state")
            sendResult(
                context = context,
                phase = PHASE_SLEEP,
                wifiManaged = prefs.getBoolean(KEY_WIFI_MANAGED, false),
                wifiPrevious = prefs.getBoolean(KEY_WIFI_PREVIOUS, false),
                wifiChanged = prefs.getBoolean(KEY_WIFI_CHANGED, false),
                bluetoothManaged = prefs.getBoolean(KEY_BT_MANAGED, false),
                bluetoothPrevious = prefs.getBoolean(KEY_BT_PREVIOUS, false),
                bluetoothChanged = prefs.getBoolean(KEY_BT_CHANGED, false),
                status = STATUS_ALREADY_SLEEPING
            )
            return
        }

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val bluetooth = bluetoothAdapter(context)

        val wifiWasOn = safeWifiState(wifiManager)
        val bluetoothWasOn = safeBluetoothState(bluetooth)
        val airplaneModeOn = isAirplaneModeOn(context)

        val wifiChangeExpected = manageWifi && wifiWasOn
        val bluetoothChangeExpected = manageBluetooth && bluetoothWasOn

        // Persist the recovery intent before touching system radios. If the
        // process dies after a toggle but before the final result is written,
        // restore() still knows which previous state must be recovered.
        prefs.edit()
            .putBoolean(KEY_CYCLE_ACTIVE, true)
            .putLong(KEY_CYCLE_ID, cycleId)
            .putBoolean(KEY_WIFI_PREVIOUS, wifiWasOn)
            .putBoolean(KEY_WIFI_CHANGED, wifiChangeExpected)
            .putBoolean(KEY_BT_PREVIOUS, bluetoothWasOn)
            .putBoolean(KEY_BT_CHANGED, bluetoothChangeExpected)
            .putBoolean(KEY_WIFI_MANAGED, manageWifi)
            .putBoolean(KEY_BT_MANAGED, manageBluetooth)
            .commit()

        val wifiChanged =
            if (wifiChangeExpected) {
                setWifi(wifiManager, false)
            } else {
                false
            }

        val bluetoothChanged =
            if (bluetoothChangeExpected) {
                setBluetooth(bluetooth, false)
            } else {
                false
            }

        // Replace the recovery intent with the actual system-call result.
        prefs.edit()
            .putBoolean(KEY_WIFI_CHANGED, wifiChanged)
            .putBoolean(KEY_BT_CHANGED, bluetoothChanged)
            .commit()

        val wifiToggleFailed = wifiChangeExpected && !wifiChanged

        Log.i(
            TAG,
            "Sleep applied: wifiWasOn=$wifiWasOn wifiAttempted=$wifiChangeExpected " +
                "wifiChanged=$wifiChanged airplaneMode=$airplaneModeOn " +
                "btWasOn=$bluetoothWasOn btChanged=$bluetoothChanged"
        )

        if (wifiToggleFailed) {
            Log.w(
                TAG,
                "Wi-Fi OFF toggle failed; airplaneMode=$airplaneModeOn"
            )
        }

        sendResult(
            context = context,
            phase = PHASE_SLEEP,
            wifiManaged = manageWifi,
            wifiPrevious = wifiWasOn,
            wifiChanged = wifiChanged,
            wifiAttempted = wifiChangeExpected,
            wifiAction = "OFF",
            wifiToggleSuccess = !wifiChangeExpected || wifiChanged,
            airplaneMode = airplaneModeOn,
            bluetoothManaged = manageBluetooth,
            bluetoothPrevious = bluetoothWasOn,
            bluetoothChanged = bluetoothChanged,
            status = if (wifiToggleFailed) STATUS_WIFI_TOGGLE_FAILED else STATUS_OK
        )
    }

    private fun setTemporaryWifi(
        context: Context,
        enabled: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cycleActive = prefs.getBoolean(KEY_CYCLE_ACTIVE, false)
        val wifiManaged = prefs.getBoolean(KEY_WIFI_MANAGED, false)
        val wifiOwnedBySleepCycle =
            cycleActive &&
                wifiManaged &&
                prefs.getBoolean(KEY_WIFI_CHANGED, false) &&
                prefs.getBoolean(KEY_WIFI_PREVIOUS, false)

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiWasOn = safeWifiState(wifiManager)
        val airplaneModeOn = isAirplaneModeOn(context)

        if (!wifiOwnedBySleepCycle) {
            Log.i(
                TAG,
                "Temporary Wi-Fi ignored: enabled=$enabled cycleActive=$cycleActive " +
                    "wifiManaged=$wifiManaged ownedBySleepCycle=false"
            )
            sendResult(
                context = context,
                phase = PHASE_MAINTENANCE_WIFI,
                wifiManaged = wifiManaged,
                wifiPrevious = wifiWasOn,
                wifiChanged = false,
                wifiAttempted = false,
                wifiAction = if (enabled) "ON" else "OFF",
                wifiToggleSuccess = true,
                airplaneMode = airplaneModeOn,
                bluetoothManaged = false,
                bluetoothPrevious = false,
                bluetoothChanged = false,
                status = if (cycleActive) STATUS_OK else STATUS_NO_ACTIVE_CYCLE
            )
            return
        }

        val changeRequired = wifiWasOn != enabled
        val changed =
            if (changeRequired) {
                setWifi(wifiManager, enabled)
            } else {
                false
            }
        val success = !changeRequired || changed

        Log.i(
            TAG,
            "Temporary sleep Wi-Fi: requested=$enabled previous=$wifiWasOn " +
                "attempted=$changeRequired changed=$changed airplaneMode=$airplaneModeOn"
        )

        sendResult(
            context = context,
            phase = PHASE_MAINTENANCE_WIFI,
            wifiManaged = true,
            wifiPrevious = wifiWasOn,
            wifiChanged = changed,
            wifiAttempted = changeRequired,
            wifiAction = if (enabled) "ON" else "OFF",
            wifiToggleSuccess = success,
            airplaneMode = airplaneModeOn,
            bluetoothManaged = false,
            bluetoothPrevious = false,
            bluetoothChanged = false,
            status = if (success) STATUS_OK else STATUS_WIFI_TOGGLE_FAILED
        )
    }

    private fun restore(
        context: Context,
        requestedCycleId: Long
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_CYCLE_ACTIVE, false)) {
            val alreadyRestored =
                requestedCycleId != 0L &&
                    prefs.getLong(KEY_LAST_RESTORED_CYCLE_ID, 0L) == requestedCycleId

            Log.i(
                TAG,
                if (alreadyRestored) {
                    "Restore replay for already restored cycle=$requestedCycleId"
                } else {
                    "No active sleep cycle to restore; reporting mismatch"
                }
            )
            sendResult(
                context = context,
                phase = PHASE_WAKE,
                wifiManaged = false,
                wifiPrevious = false,
                wifiChanged = false,
                bluetoothManaged = false,
                bluetoothPrevious = false,
                bluetoothChanged = false,
                restoreSuccess = alreadyRestored,
                status = if (alreadyRestored) STATUS_ALREADY_RESTORED else STATUS_NO_ACTIVE_CYCLE
            )
            return
        }

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val bluetooth = bluetoothAdapter(context)

        val activeCycleId = prefs.getLong(KEY_CYCLE_ID, 0L)
        val effectiveCycleId =
            activeCycleId.takeIf { it != 0L } ?: requestedCycleId
        val wifiPrevious = prefs.getBoolean(KEY_WIFI_PREVIOUS, false)
        val wifiChanged = prefs.getBoolean(KEY_WIFI_CHANGED, false)
        val airplaneModeOn = isAirplaneModeOn(context)
        val bluetoothPrevious = prefs.getBoolean(KEY_BT_PREVIOUS, false)
        val bluetoothChanged = prefs.getBoolean(KEY_BT_CHANGED, false)
        val wifiManaged = prefs.getBoolean(KEY_WIFI_MANAGED, false)
        val bluetoothManaged = prefs.getBoolean(KEY_BT_MANAGED, false)

        val wifiRestoreRequired = wifiChanged && wifiPrevious
        val bluetoothRestoreRequired = bluetoothChanged && bluetoothPrevious

        val wifiRestored = if (wifiRestoreRequired) {
            setWifi(wifiManager, true)
        } else {
            false
        }
        val bluetoothRestored = if (bluetoothRestoreRequired) {
            setBluetooth(bluetooth, true)
        } else {
            false
        }

        val wifiRestoreSuccess = !wifiRestoreRequired || wifiRestored
        val bluetoothRestoreSuccess = !bluetoothRestoreRequired || bluetoothRestored
        val restoreSuccess = wifiRestoreSuccess && bluetoothRestoreSuccess

        if (restoreSuccess) {
            prefs.edit()
                .clear()
                .putLong(KEY_LAST_RESTORED_CYCLE_ID, effectiveCycleId)
                .commit()
        } else {
            prefs.edit()
                .putBoolean(KEY_WIFI_CHANGED, wifiChanged && !wifiRestoreSuccess)
                .putBoolean(KEY_BT_CHANGED, bluetoothChanged && !bluetoothRestoreSuccess)
                .commit()
        }

        Log.i(
            TAG,
            "Wake restore result: success=$restoreSuccess " +
                "wifi=$wifiPrevious (attempted=$wifiRestoreRequired restored=$wifiRestored) " +
                "airplaneMode=$airplaneModeOn " +
                "bluetooth=$bluetoothPrevious (restored=$bluetoothRestored)"
        )

        if (wifiRestoreRequired && !wifiRestored) {
            Log.w(
                TAG,
                "Wi-Fi ON restore failed; airplaneMode=$airplaneModeOn"
            )
        }

        sendResult(
            context = context,
            phase = PHASE_WAKE,
            wifiManaged = wifiManaged,
            wifiPrevious = wifiPrevious,
            wifiChanged = wifiRestored,
            wifiAttempted = wifiRestoreRequired,
            wifiAction = "ON",
            wifiToggleSuccess = !wifiRestoreRequired || wifiRestored,
            airplaneMode = airplaneModeOn,
            bluetoothManaged = bluetoothManaged,
            bluetoothPrevious = bluetoothPrevious,
            bluetoothChanged = bluetoothRestored,
            restoreSuccess = restoreSuccess,
            status = if (restoreSuccess) STATUS_OK else STATUS_RESTORE_FAILED
        )
    }

    private fun sendResult(
        context: Context,
        phase: String,
        wifiManaged: Boolean,
        wifiPrevious: Boolean,
        wifiChanged: Boolean,
        wifiAttempted: Boolean = false,
        wifiAction: String = "NONE",
        wifiToggleSuccess: Boolean = true,
        airplaneMode: Boolean = false,
        bluetoothManaged: Boolean,
        bluetoothPrevious: Boolean,
        bluetoothChanged: Boolean,
        restoreSuccess: Boolean = true,
        status: String = STATUS_OK
    ) {
        val response = Intent(ACTION_RESULT)
            .setPackage(MAIN_PACKAGE)
            .putExtra(EXTRA_PHASE, phase)
            .putExtra(EXTRA_WIFI_MANAGED, wifiManaged)
            .putExtra(EXTRA_WIFI_PREVIOUS, wifiPrevious)
            .putExtra(EXTRA_WIFI_CHANGED, wifiChanged)
            .putExtra(EXTRA_WIFI_ATTEMPTED, wifiAttempted)
            .putExtra(EXTRA_WIFI_ACTION, wifiAction)
            .putExtra(EXTRA_WIFI_TOGGLE_SUCCESS, wifiToggleSuccess)
            .putExtra(EXTRA_AIRPLANE_MODE, airplaneMode)
            .putExtra(EXTRA_BLUETOOTH_MANAGED, bluetoothManaged)
            .putExtra(EXTRA_BLUETOOTH_PREVIOUS, bluetoothPrevious)
            .putExtra(EXTRA_BLUETOOTH_CHANGED, bluetoothChanged)
            .putExtra(EXTRA_RESTORE_SUCCESS, restoreSuccess)
            .putExtra(EXTRA_STATUS, status)

        context.sendBroadcast(response, PERMISSION)
    }

    private fun isAirplaneModeOn(context: Context): Boolean =
        try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read Airplane mode state", t)
            false
        }

    private fun safeWifiState(wifiManager: WifiManager?): Boolean =
        try {
            wifiManager?.isWifiEnabled == true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read Wi-Fi state", t)
            false
        }

    @Suppress("DEPRECATION")
    private fun setWifi(wifiManager: WifiManager?, enabled: Boolean): Boolean =
        try {
            val result = wifiManager?.setWifiEnabled(enabled) == true
            Log.i(TAG, "Wi-Fi -> $enabled result=$result")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "Wi-Fi toggle failed", t)
            false
        }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? =
        (
            context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? BluetoothManager
        )?.adapter

    // The Helper intentionally targets API 28 and declares the legacy
    // BLUETOOTH/BLUETOOTH_ADMIN permissions so these compatibility calls
    // remain available on handheld firmware.
    @SuppressLint("MissingPermission")
    private fun safeBluetoothState(adapter: BluetoothAdapter?): Boolean =
        try {
            adapter?.isEnabled == true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read Bluetooth state", t)
            false
        }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun setBluetooth(adapter: BluetoothAdapter?, enabled: Boolean): Boolean =
        try {
            val result = if (enabled) {
                adapter?.enable() == true
            } else {
                adapter?.disable() == true
            }
            Log.i(TAG, "Bluetooth -> $enabled result=$result")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "Bluetooth toggle failed", t)
            false
        }
}
