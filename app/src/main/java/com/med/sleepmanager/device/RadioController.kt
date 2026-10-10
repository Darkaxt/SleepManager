package com.med.sleepmanager.device

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.med.sleepmanager.data.DirectRadioStore
import com.med.sleepmanager.integration.HelperController

enum class RadioBackend {
    PSERVER,
    HELPER,
    NONE
}

data class DirectRadioSleepResult(
    val wifiManaged: Boolean,
    val wifiPrevious: Boolean,
    val wifiChanged: Boolean,
    val bluetoothManaged: Boolean,
    val bluetoothPrevious: Boolean,
    val bluetoothChanged: Boolean,
    val success: Boolean
)

/**
 * Single radio-control abstraction for SleepManager.
 *
 * PServer is selected by a harmless capability probe, never by brand/model.
 * The Compatibility Helper is only the fallback when direct control is not
 * available.
 */
object RadioController {
    private const val TAG = "SleepManagerRadio"

    @Volatile
    private var directCapabilityConfirmed = false

    fun backend(context: Context): RadioBackend =
        when {
            directAvailable() ->
                RadioBackend.PSERVER
            HelperController.isInstalled(context) ->
                RadioBackend.HELPER
            else ->
                RadioBackend.NONE
        }

    fun directAvailable(): Boolean {
        if (directCapabilityConfirmed) return true
        val ready = DeviceControlController.supportsDirectRadioControl()
        if (ready) {
            directCapabilityConfirmed = true
        }
        return ready
    }

    fun helperRequired(context: Context): Boolean =
        !directAvailable() && !HelperController.isInstalled(context)

    fun canManageWifi(context: Context): Boolean =
        when {
            directAvailable() -> true
            !HelperController.isInstalled(context) -> false
            !HelperController.isBatteryUnrestricted(context) -> false
            HelperController.wifiControlAccess(context).effectivelyAllowed == false -> false
            else -> true
        }

    fun canManageBluetooth(context: Context): Boolean =
        directAvailable() ||
            (
                HelperController.isInstalled(context) &&
                    HelperController.isBatteryUnrestricted(context)
            )

    fun currentWifiEnabled(context: Context): Boolean? =
        if (directAvailable()) {
            DeviceControlController.wifiEnabledPrivileged()
        } else {
            runCatching {
                val manager =
                    context.applicationContext
                        .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        ?: return@runCatching null
                manager.isWifiEnabled
            }.getOrNull()
        }

    fun currentBluetoothEnabled(context: Context): Boolean? =
        if (directAvailable()) {
            DeviceControlController.bluetoothEnabledPrivileged()
        } else {
            runCatching {
                val manager =
                    context.getSystemService(Context.BLUETOOTH_SERVICE)
                        as? BluetoothManager
                        ?: return@runCatching null
                manager.adapter?.isEnabled
            }.getOrNull()
        }

    fun applyDirectSleep(
        context: Context,
        wifi: Boolean,
        bluetooth: Boolean,
        cycleId: Long
    ): DirectRadioSleepResult {
        require(backend(context) == RadioBackend.PSERVER) {
            "Direct radio backend is unavailable"
        }

        val wifiPrevious = currentWifiEnabled(context) == true
        val bluetoothPrevious = currentBluetoothEnabled(context) == true
        val wifiExpected = wifi && wifiPrevious
        val bluetoothExpected = bluetooth && bluetoothPrevious

        DirectRadioStore.begin(
            context = context,
            cycleId = cycleId,
            wifiManaged = wifi,
            wifiPrevious = wifiPrevious,
            wifiChangeExpected = wifiExpected,
            bluetoothManaged = bluetooth,
            bluetoothPrevious = bluetoothPrevious,
            bluetoothChangeExpected = bluetoothExpected
        )

        val wifiCommandOk =
            !wifiExpected ||
                DeviceControlController.setWifiEnabledPrivileged(false)
        val bluetoothCommandOk =
            !bluetoothExpected ||
                DeviceControlController.setBluetoothEnabledPrivileged(false)

        val actualWifiChanged = wifiExpected && wifiCommandOk
        val actualBluetoothChanged = bluetoothExpected && bluetoothCommandOk

        DirectRadioStore.recordResults(
            context = context,
            wifiChanged = actualWifiChanged,
            bluetoothChanged = actualBluetoothChanged
        )

        val success =
            (!wifiExpected || actualWifiChanged) &&
                (!bluetoothExpected || actualBluetoothChanged)

        Log.i(
            TAG,
            "PServer sleep radios cycle=$cycleId wifi=$wifi/$wifiPrevious/$actualWifiChanged " +
                "bluetooth=$bluetooth/$bluetoothPrevious/$actualBluetoothChanged success=$success"
        )

        return DirectRadioSleepResult(
            wifiManaged = wifi,
            wifiPrevious = wifiPrevious,
            wifiChanged = actualWifiChanged,
            bluetoothManaged = bluetooth,
            bluetoothPrevious = bluetoothPrevious,
            bluetoothChanged = actualBluetoothChanged,
            success = success
        )
    }

    fun restoreDirect(context: Context, cycleId: Long): Boolean {
        val state = DirectRadioStore.current(context)
        if (!state.active) return true
        if (state.cycleId != cycleId) {
            Log.w(
                TAG,
                "PServer restore cycle mismatch requested=$cycleId stored=${state.cycleId}"
            )
            return false
        }
        if (!directAvailable()) {
            Log.w(TAG, "PServer disappeared while direct radio restore is pending")
            return false
        }

        val wifiRestored =
            !state.wifiChanged ||
                !state.wifiPrevious ||
                DeviceControlController.setWifiEnabledPrivileged(true)
        val bluetoothRestored =
            !state.bluetoothChanged ||
                !state.bluetoothPrevious ||
                DeviceControlController.setBluetoothEnabledPrivileged(true)

        val success = wifiRestored && bluetoothRestored
        if (success) {
            DirectRadioStore.clear(context)
        }

        Log.i(
            TAG,
            "PServer restore cycle=$cycleId wifi=$wifiRestored " +
                "bluetooth=$bluetoothRestored success=$success"
        )
        return success
    }

    fun reapplyDirectSleepState(context: Context, cycleId: Long): Boolean {
        val state = DirectRadioStore.current(context)
        if (!state.active || state.cycleId != cycleId || !directAvailable()) {
            return false
        }

        val wifiOk =
            !state.wifiChanged ||
                DeviceControlController.setWifiEnabledPrivileged(false)
        val bluetoothOk =
            !state.bluetoothChanged ||
                DeviceControlController.setBluetoothEnabledPrivileged(false)
        return wifiOk && bluetoothOk
    }

    fun setTemporaryWifi(context: Context, enabled: Boolean): Boolean =
        when (backend(context)) {
            RadioBackend.PSERVER ->
                DeviceControlController.setWifiEnabledPrivileged(enabled)
            RadioBackend.HELPER ->
                HelperController.setTemporaryWifi(context, enabled)
            RadioBackend.NONE ->
                false
        }

    fun directSleepWifiOwned(context: Context, cycleId: Long): Boolean {
        val state = DirectRadioStore.current(context)
        return state.active &&
            state.cycleId == cycleId &&
            state.wifiChanged
    }
}
