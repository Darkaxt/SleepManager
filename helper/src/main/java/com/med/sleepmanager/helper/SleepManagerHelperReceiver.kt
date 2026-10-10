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
import com.med.sleepmanager.protocol.HelperCyclePolicy
import com.med.sleepmanager.protocol.HelperProtocol

open class SleepManagerHelperReceiver : BroadcastReceiver() {
    protected open val responsePermission: String
        get() = HelperProtocol.PERMISSION
    protected open val stateAction: String
        get() = HelperProtocol.ACTION_STATE
    protected open val resultAction: String
        get() = HelperProtocol.ACTION_RESULT

    companion object {
        private const val TAG = "SleepManagerHelper"
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
            HelperProtocol.ACTION_SLEEP -> enterSleep(
                context,
                manageWifi = intent.getBooleanExtra(HelperProtocol.EXTRA_WIFI, false),
                manageBluetooth = intent.getBooleanExtra(HelperProtocol.EXTRA_BLUETOOTH, false),
                cycleId = intent.getLongExtra(HelperProtocol.EXTRA_CYCLE_ID, 0L)
            )
            HelperProtocol.ACTION_WAKE, HelperProtocol.ACTION_RESTORE -> restore(
                context,
                requestedCycleId = intent.getLongExtra(HelperProtocol.EXTRA_CYCLE_ID, 0L)
            )
            HelperProtocol.ACTION_QUERY -> reportCurrentState(context)
            HelperProtocol.ACTION_SET_TEMP_WIFI -> setTemporaryWifi(
                context,
                enabled = intent.getBooleanExtra(HelperProtocol.EXTRA_WIFI, false)
            )
            HelperProtocol.ACTION_FORGET_STATE -> {
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

        val response = Intent(stateAction)
            .setPackage(HelperProtocol.MAIN_PACKAGE)
            .putExtra(HelperProtocol.EXTRA_WIFI_STATE, wifiOn)
            .putExtra(HelperProtocol.EXTRA_BLUETOOTH_STATE, bluetoothOn)

        context.sendBroadcast(response, responsePermission)
        Log.i(TAG, "Current state reported: wifi=$wifiOn bluetooth=$bluetoothOn")
    }

    private fun enterSleep(
        context: Context,
        manageWifi: Boolean,
        manageBluetooth: Boolean,
        cycleId: Long
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cycleActive = prefs.getBoolean(KEY_CYCLE_ACTIVE, false)
        val activeCycleId = prefs.getLong(KEY_CYCLE_ID, 0L)

        when (
            HelperCyclePolicy.sleepDecision(
                cycleActive,
                activeCycleId,
                cycleId
            )
        ) {
            HelperCyclePolicy.SleepDecision.RECONCILE_STALE_ACTIVE -> {
                Log.w(
                    TAG,
                    "Newer sleep cycle=$cycleId arrived while stale Helper cycle=" +
                        "$activeCycleId is active; reconciling stale ownership first"
                )

                // A previous Main/Helper acknowledgement can be lost even after
                // the radios have physically returned to their pre-sleep state.
                // Reconcile only when the incoming correlated cycle is newer.
                // The old wake result is cycle-correlated, so the newer Main
                // transaction safely ignores it as stale.
                restore(
                    context = context,
                    requestedCycleId = activeCycleId
                )

                if (
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getBoolean(KEY_CYCLE_ACTIVE, false)
                ) {
                    Log.w(
                        TAG,
                        "Stale Helper cycle could not be reconciled; " +
                            "rejecting new sleep cycle=$cycleId"
                    )
                    sendResult(
                        context = context,
                        phase = HelperProtocol.PHASE_SLEEP,
                        cycleId = cycleId,
                        wifiManaged = false,
                        wifiPrevious = false,
                        wifiChanged = false,
                        bluetoothManaged = false,
                        bluetoothPrevious = false,
                        bluetoothChanged = false,
                        status = HelperProtocol.STATUS_CYCLE_MISMATCH
                    )
                    return
                }

                Log.i(
                    TAG,
                    "Stale Helper cycle=$activeCycleId reconciled; " +
                        "starting new cycle=$cycleId"
                )
                enterSleep(
                    context = context,
                    manageWifi = manageWifi,
                    manageBluetooth = manageBluetooth,
                    cycleId = cycleId
                )
                return
            }

            HelperCyclePolicy.SleepDecision.CYCLE_MISMATCH -> {
                Log.w(
                    TAG,
                    "Rejecting stale sleep cycle=$cycleId because newer Helper " +
                        "cycle=$activeCycleId is active"
                )
                sendResult(
                    context = context,
                    phase = HelperProtocol.PHASE_SLEEP,
                    cycleId = cycleId,
                    wifiManaged = false,
                    wifiPrevious = false,
                    wifiChanged = false,
                    bluetoothManaged = false,
                    bluetoothPrevious = false,
                    bluetoothChanged = false,
                    status = HelperProtocol.STATUS_CYCLE_MISMATCH
                )
                return
            }

            HelperCyclePolicy.SleepDecision.ALREADY_SLEEPING -> {
                Log.i(TAG, "Sleep cycle already active; reporting existing state")
                sendResult(
                    context = context,
                    phase = HelperProtocol.PHASE_SLEEP,
                    cycleId = activeCycleId,
                    wifiManaged = prefs.getBoolean(KEY_WIFI_MANAGED, false),
                    wifiPrevious = prefs.getBoolean(KEY_WIFI_PREVIOUS, false),
                    wifiChanged = prefs.getBoolean(KEY_WIFI_CHANGED, false),
                    bluetoothManaged = prefs.getBoolean(KEY_BT_MANAGED, false),
                    bluetoothPrevious = prefs.getBoolean(KEY_BT_PREVIOUS, false),
                    bluetoothChanged = prefs.getBoolean(KEY_BT_CHANGED, false),
                    status = HelperProtocol.STATUS_ALREADY_SLEEPING
                )
                return
            }

            HelperCyclePolicy.SleepDecision.START_NEW -> Unit
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
            phase = HelperProtocol.PHASE_SLEEP,
            cycleId = cycleId,
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
            status = if (wifiToggleFailed) HelperProtocol.STATUS_WIFI_TOGGLE_FAILED else HelperProtocol.STATUS_OK
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
                phase = HelperProtocol.PHASE_MAINTENANCE_WIFI,
                cycleId = prefs.getLong(KEY_CYCLE_ID, 0L),
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
                status = if (cycleActive) HelperProtocol.STATUS_OK else HelperProtocol.STATUS_NO_ACTIVE_CYCLE
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
            phase = HelperProtocol.PHASE_MAINTENANCE_WIFI,
            cycleId = prefs.getLong(KEY_CYCLE_ID, 0L),
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
            status = if (success) HelperProtocol.STATUS_OK else HelperProtocol.STATUS_WIFI_TOGGLE_FAILED
        )
    }

    private fun restore(
        context: Context,
        requestedCycleId: Long
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cycleActive = prefs.getBoolean(KEY_CYCLE_ACTIVE, false)
        val activeCycleId = prefs.getLong(KEY_CYCLE_ID, 0L)
        val lastRestoredCycleId =
            prefs.getLong(KEY_LAST_RESTORED_CYCLE_ID, 0L)

        when (
            HelperCyclePolicy.restoreDecision(
                cycleActive,
                activeCycleId,
                lastRestoredCycleId,
                requestedCycleId
            )
        ) {
            HelperCyclePolicy.RestoreDecision.ALREADY_RESTORED -> {
                Log.i(
                    TAG,
                    "Restore replay for already restored cycle=$requestedCycleId"
                )
                sendResult(
                    context = context,
                    phase = HelperProtocol.PHASE_WAKE,
                    cycleId = requestedCycleId,
                    wifiManaged = false,
                    wifiPrevious = false,
                    wifiChanged = false,
                    bluetoothManaged = false,
                    bluetoothPrevious = false,
                    bluetoothChanged = false,
                    restoreSuccess = true,
                    status = HelperProtocol.STATUS_ALREADY_RESTORED
                )
                return
            }

            HelperCyclePolicy.RestoreDecision.NO_ACTIVE_CYCLE -> {
                Log.i(
                    TAG,
                    "No active sleep cycle to restore; reporting mismatch"
                )
                sendResult(
                    context = context,
                    phase = HelperProtocol.PHASE_WAKE,
                    cycleId = requestedCycleId,
                    wifiManaged = false,
                    wifiPrevious = false,
                    wifiChanged = false,
                    bluetoothManaged = false,
                    bluetoothPrevious = false,
                    bluetoothChanged = false,
                    restoreSuccess = false,
                    status = HelperProtocol.STATUS_NO_ACTIVE_CYCLE
                )
                return
            }

            HelperCyclePolicy.RestoreDecision.RECONCILE_STALE_ACTIVE -> {
                Log.w(
                    TAG,
                    "Newer restore cycle=$requestedCycleId arrived while stale Helper " +
                        "cycle=$activeCycleId is active; reconciling stale ownership first"
                )

                restore(
                    context = context,
                    requestedCycleId = activeCycleId
                )

                val staleStillActive =
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getBoolean(KEY_CYCLE_ACTIVE, false)

                if (staleStillActive) {
                    Log.w(
                        TAG,
                        "Stale Helper cycle=$activeCycleId could not be restored; " +
                            "preserving ownership"
                    )
                    sendResult(
                        context = context,
                        phase = HelperProtocol.PHASE_WAKE,
                        cycleId = requestedCycleId,
                        wifiManaged = false,
                        wifiPrevious = false,
                        wifiChanged = false,
                        bluetoothManaged = false,
                        bluetoothPrevious = false,
                        bluetoothChanged = false,
                        restoreSuccess = false,
                        status = HelperProtocol.STATUS_CYCLE_MISMATCH
                    )
                    return
                }

                Log.i(
                    TAG,
                    "Stale Helper cycle=$activeCycleId reconciled; newer Main cycle=" +
                        "$requestedCycleId has no remaining Helper-owned radio state"
                )
                sendResult(
                    context = context,
                    phase = HelperProtocol.PHASE_WAKE,
                    cycleId = requestedCycleId,
                    wifiManaged = false,
                    wifiPrevious = false,
                    wifiChanged = false,
                    bluetoothManaged = false,
                    bluetoothPrevious = false,
                    bluetoothChanged = false,
                    restoreSuccess = true,
                    status = HelperProtocol.STATUS_ALREADY_RESTORED
                )
                return
            }

            HelperCyclePolicy.RestoreDecision.CYCLE_MISMATCH -> {
                Log.w(
                    TAG,
                    "Rejecting stale restore cycle=$requestedCycleId because newer " +
                        "Helper cycle=$activeCycleId is active"
                )
                sendResult(
                    context = context,
                    phase = HelperProtocol.PHASE_WAKE,
                    cycleId = requestedCycleId,
                    wifiManaged = false,
                    wifiPrevious = false,
                    wifiChanged = false,
                    bluetoothManaged = false,
                    bluetoothPrevious = false,
                    bluetoothChanged = false,
                    restoreSuccess = false,
                    status = HelperProtocol.STATUS_CYCLE_MISMATCH
                )
                return
            }

            HelperCyclePolicy.RestoreDecision.RESTORE_ACTIVE -> Unit
        }

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val bluetooth = bluetoothAdapter(context)

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

        val wifiAlreadyRestored =
            wifiRestoreRequired && safeWifiState(wifiManager) == wifiPrevious
        val bluetoothAlreadyRestored =
            bluetoothRestoreRequired &&
                safeBluetoothState(bluetooth) == bluetoothPrevious

        val wifiRestoreAttempted =
            wifiRestoreRequired && !wifiAlreadyRestored
        val bluetoothRestoreAttempted =
            bluetoothRestoreRequired && !bluetoothAlreadyRestored

        val wifiRestored =
            when {
                !wifiRestoreRequired -> false
                wifiAlreadyRestored -> true
                else -> setWifi(wifiManager, wifiPrevious)
            }
        val bluetoothRestored =
            when {
                !bluetoothRestoreRequired -> false
                bluetoothAlreadyRestored -> true
                else -> setBluetooth(bluetooth, bluetoothPrevious)
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
                "wifi=$wifiPrevious (attempted=$wifiRestoreAttempted restored=$wifiRestored) " +
                "airplaneMode=$airplaneModeOn " +
                "bluetooth=$bluetoothPrevious " +
                "(attempted=$bluetoothRestoreAttempted restored=$bluetoothRestored)"
        )

        if (wifiRestoreRequired && !wifiRestored) {
            Log.w(
                TAG,
                "Wi-Fi ON restore failed; airplaneMode=$airplaneModeOn"
            )
        }

        sendResult(
            context = context,
            phase = HelperProtocol.PHASE_WAKE,
            cycleId = effectiveCycleId,
            wifiManaged = wifiManaged,
            wifiPrevious = wifiPrevious,
            wifiChanged = wifiRestored,
            wifiAttempted = wifiRestoreAttempted,
            wifiAction = "ON",
            wifiToggleSuccess = !wifiRestoreRequired || wifiRestored,
            airplaneMode = airplaneModeOn,
            bluetoothManaged = bluetoothManaged,
            bluetoothPrevious = bluetoothPrevious,
            bluetoothChanged = bluetoothRestored,
            restoreSuccess = restoreSuccess,
            status = if (restoreSuccess) HelperProtocol.STATUS_OK else HelperProtocol.STATUS_RESTORE_FAILED
        )
    }

    private fun sendResult(
        context: Context,
        phase: String,
        cycleId: Long,
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
        status: String = HelperProtocol.STATUS_OK
    ) {
        val response = Intent(resultAction)
            .setPackage(HelperProtocol.MAIN_PACKAGE)
            .putExtra(HelperProtocol.EXTRA_PHASE, phase)
            .putExtra(HelperProtocol.EXTRA_CYCLE_ID, cycleId)
            .putExtra(HelperProtocol.EXTRA_WIFI_MANAGED, wifiManaged)
            .putExtra(HelperProtocol.EXTRA_WIFI_PREVIOUS, wifiPrevious)
            .putExtra(HelperProtocol.EXTRA_WIFI_CHANGED, wifiChanged)
            .putExtra(HelperProtocol.EXTRA_WIFI_ATTEMPTED, wifiAttempted)
            .putExtra(HelperProtocol.EXTRA_WIFI_ACTION, wifiAction)
            .putExtra(HelperProtocol.EXTRA_WIFI_TOGGLE_SUCCESS, wifiToggleSuccess)
            .putExtra(HelperProtocol.EXTRA_AIRPLANE_MODE, airplaneMode)
            .putExtra(HelperProtocol.EXTRA_BLUETOOTH_MANAGED, bluetoothManaged)
            .putExtra(HelperProtocol.EXTRA_BLUETOOTH_PREVIOUS, bluetoothPrevious)
            .putExtra(HelperProtocol.EXTRA_BLUETOOTH_CHANGED, bluetoothChanged)
            .putExtra(HelperProtocol.EXTRA_RESTORE_SUCCESS, restoreSuccess)
            .putExtra(HelperProtocol.EXTRA_STATUS, status)

        context.sendBroadcast(response, responsePermission)
        Log.i(
            TAG,
            "Result reported: phase=$phase cycle=$cycleId status=$status " +
                "restoreSuccess=$restoreSuccess"
        )
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
