package com.med.sleepmanager.device

import android.content.Context

object DeviceControlStore {
    private const val PREFS = "device_control_state"

    private const val KEY_BATTERY_SAVER_OWNED = "battery_saver_owned"
    private const val KEY_BATTERY_SAVER_PREVIOUS = "battery_saver_previous"
    private const val KEY_BATTERY_SAVER_DEFERRED_FOR_POWER =
        "battery_saver_deferred_for_external_power"
    private const val KEY_CHARGING_SEPARATION_OWNED = "charging_separation_owned"
    private const val KEY_CHARGING_SEPARATION_PREVIOUS = "charging_separation_previous"
    private const val KEY_LAST_SERVICE_RECOVERY = "last_service_recovery"

    data class OwnedState(
        val owned: Boolean,
        val previous: Boolean
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun batterySaver(context: Context): OwnedState =
        OwnedState(
            owned = prefs(context).getBoolean(KEY_BATTERY_SAVER_OWNED, false),
            previous = prefs(context).getBoolean(KEY_BATTERY_SAVER_PREVIOUS, false)
        )

    fun takeBatterySaverOwnership(context: Context, previous: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_BATTERY_SAVER_OWNED, true)
            .putBoolean(KEY_BATTERY_SAVER_PREVIOUS, previous)
            .commit()
    }

    fun clearBatterySaverOwnership(context: Context) {
        prefs(context).edit()
            .remove(KEY_BATTERY_SAVER_OWNED)
            .remove(KEY_BATTERY_SAVER_PREVIOUS)
            .commit()
    }

    fun batterySaverDeferredForExternalPower(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BATTERY_SAVER_DEFERRED_FOR_POWER, false)

    fun setBatterySaverDeferredForExternalPower(
        context: Context,
        deferred: Boolean
    ) {
        prefs(context).edit()
            .apply {
                if (deferred) {
                    putBoolean(KEY_BATTERY_SAVER_DEFERRED_FOR_POWER, true)
                } else {
                    remove(KEY_BATTERY_SAVER_DEFERRED_FOR_POWER)
                }
            }
            .commit()
    }

    fun chargingSeparation(context: Context): OwnedState =
        OwnedState(
            owned = prefs(context).getBoolean(KEY_CHARGING_SEPARATION_OWNED, false),
            previous = prefs(context).getBoolean(KEY_CHARGING_SEPARATION_PREVIOUS, false)
        )

    fun takeChargingSeparationOwnership(context: Context, previous: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_CHARGING_SEPARATION_OWNED, true)
            .putBoolean(KEY_CHARGING_SEPARATION_PREVIOUS, previous)
            .commit()
    }

    fun clearChargingSeparationOwnership(context: Context) {
        prefs(context).edit()
            .remove(KEY_CHARGING_SEPARATION_OWNED)
            .remove(KEY_CHARGING_SEPARATION_PREVIOUS)
            .commit()
    }

    fun recordServiceRecovery(context: Context, message: String) {
        prefs(context).edit()
            .putString(KEY_LAST_SERVICE_RECOVERY, message)
            .commit()
    }

    fun lastServiceRecovery(context: Context): String? =
        prefs(context).getString(KEY_LAST_SERVICE_RECOVERY, null)
}
