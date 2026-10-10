package com.med.sleepmanager.data

import android.content.Context

/**
 * Crash-safe ownership for the direct PServer radio backend.
 *
 * The intent to change a radio is persisted before the privileged command so a
 * process death between command execution and acknowledgement cannot strand the
 * user's original radio state.
 */
object DirectRadioStore {
    private const val PREFS = "direct_radio_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_CYCLE_ID = "cycle_id"
    private const val KEY_WIFI_MANAGED = "wifi_managed"
    private const val KEY_WIFI_PREVIOUS = "wifi_previous"
    private const val KEY_WIFI_CHANGED = "wifi_changed"
    private const val KEY_BLUETOOTH_MANAGED = "bluetooth_managed"
    private const val KEY_BLUETOOTH_PREVIOUS = "bluetooth_previous"
    private const val KEY_BLUETOOTH_CHANGED = "bluetooth_changed"

    data class Snapshot(
        val active: Boolean,
        val cycleId: Long,
        val wifiManaged: Boolean,
        val wifiPrevious: Boolean,
        val wifiChanged: Boolean,
        val bluetoothManaged: Boolean,
        val bluetoothPrevious: Boolean,
        val bluetoothChanged: Boolean
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun begin(
        context: Context,
        cycleId: Long,
        wifiManaged: Boolean,
        wifiPrevious: Boolean,
        wifiChangeExpected: Boolean,
        bluetoothManaged: Boolean,
        bluetoothPrevious: Boolean,
        bluetoothChangeExpected: Boolean
    ) {
        prefs(context).edit()
            .clear()
            .putBoolean(KEY_ACTIVE, wifiChangeExpected || bluetoothChangeExpected)
            .putLong(KEY_CYCLE_ID, cycleId)
            .putBoolean(KEY_WIFI_MANAGED, wifiManaged)
            .putBoolean(KEY_WIFI_PREVIOUS, wifiPrevious)
            .putBoolean(KEY_WIFI_CHANGED, wifiChangeExpected)
            .putBoolean(KEY_BLUETOOTH_MANAGED, bluetoothManaged)
            .putBoolean(KEY_BLUETOOTH_PREVIOUS, bluetoothPrevious)
            .putBoolean(KEY_BLUETOOTH_CHANGED, bluetoothChangeExpected)
            .commit()
    }

    fun recordResults(
        context: Context,
        wifiChanged: Boolean,
        bluetoothChanged: Boolean
    ) {
        val current = current(context)
        if (current.cycleId == 0L) return
        val active = wifiChanged || bluetoothChanged
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putBoolean(KEY_WIFI_CHANGED, wifiChanged)
            .putBoolean(KEY_BLUETOOTH_CHANGED, bluetoothChanged)
            .commit()
        if (!active) clear(context)
    }

    fun current(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            active = p.getBoolean(KEY_ACTIVE, false),
            cycleId = p.getLong(KEY_CYCLE_ID, 0L),
            wifiManaged = p.getBoolean(KEY_WIFI_MANAGED, false),
            wifiPrevious = p.getBoolean(KEY_WIFI_PREVIOUS, false),
            wifiChanged = p.getBoolean(KEY_WIFI_CHANGED, false),
            bluetoothManaged = p.getBoolean(KEY_BLUETOOTH_MANAGED, false),
            bluetoothPrevious = p.getBoolean(KEY_BLUETOOTH_PREVIOUS, false),
            bluetoothChanged = p.getBoolean(KEY_BLUETOOTH_CHANGED, false)
        )
    }

    fun isPendingForCycle(context: Context, cycleId: Long): Boolean {
        val state = current(context)
        return state.active && state.cycleId == cycleId
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }
}
