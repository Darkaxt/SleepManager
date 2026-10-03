package com.med.sleepmanager.data

import android.content.Context

/**
 * Persisted diagnostics and latest-activity state.
 *
 * This intentionally keeps the existing "sleep_manager" SharedPreferences file
 * and legacy key names so 0.6.x diagnostic/activity state survives this
 * responsibility split without migration.
 */
object DiagnosticsStateStore {
    private const val PREFS = "sleep_manager"
    private const val KEY_LAST_EVENT = "last_event"
    private const val KEY_LAST_EVENT_TIME = "last_event_time"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_KNOWN = "last_wifi_diagnostic_known"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_PHASE = "last_wifi_diagnostic_phase"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_ACTION = "last_wifi_diagnostic_action"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_ATTEMPTED = "last_wifi_diagnostic_attempted"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_SUCCESS = "last_wifi_diagnostic_success"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_AIRPLANE = "last_wifi_diagnostic_airplane"
    private const val KEY_LAST_WIFI_DIAGNOSTIC_TIME = "last_wifi_diagnostic_time"
    private const val KEY_FALSE_WAKE_COUNT = "false_wake_count"
    private const val KEY_LAST_FALSE_WAKE_TIME = "last_false_wake_time"

    data class WifiToggleDiagnostic(
        val phase: String,
        val action: String,
        val attempted: Boolean,
        val success: Boolean,
        val airplaneMode: Boolean,
        val timestamp: Long
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordEvent(context: Context, event: String) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putString(KEY_LAST_EVENT, event)
            .putLong(KEY_LAST_EVENT_TIME, now)
            .apply()
        DiagnosticsCycleStore.recordEvent(context, event, now)
        EventHistoryStore.record(context, event, now)
        DiagnosticsTransitionStore.recordEvent(context, event)
    }

    fun lastEvent(context: Context): String =
        prefs(context).getString(KEY_LAST_EVENT, "No activity yet") ?: "No activity yet"

    fun lastEventTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_EVENT_TIME, 0L)

    fun recordWifiToggleDiagnostic(
        context: Context,
        phase: String,
        action: String,
        attempted: Boolean,
        success: Boolean,
        airplaneMode: Boolean
    ) {
        prefs(context).edit()
            .putBoolean(KEY_LAST_WIFI_DIAGNOSTIC_KNOWN, true)
            .putString(KEY_LAST_WIFI_DIAGNOSTIC_PHASE, phase)
            .putString(KEY_LAST_WIFI_DIAGNOSTIC_ACTION, action)
            .putBoolean(KEY_LAST_WIFI_DIAGNOSTIC_ATTEMPTED, attempted)
            .putBoolean(KEY_LAST_WIFI_DIAGNOSTIC_SUCCESS, success)
            .putBoolean(KEY_LAST_WIFI_DIAGNOSTIC_AIRPLANE, airplaneMode)
            .putLong(KEY_LAST_WIFI_DIAGNOSTIC_TIME, System.currentTimeMillis())
            .apply()
        DiagnosticsTransitionStore.recordWifiToggle(
            context = context,
            phase = phase,
            action = action,
            attempted = attempted,
            success = success,
            airplaneMode = airplaneMode
        )
    }

    fun recordFalseWake(context: Context) {
        val p = prefs(context)
        val nextCount = p.getInt(KEY_FALSE_WAKE_COUNT, 0) + 1
        p.edit()
            .putInt(KEY_FALSE_WAKE_COUNT, nextCount)
            .putLong(KEY_LAST_FALSE_WAKE_TIME, System.currentTimeMillis())
            .apply()
        DiagnosticsCycleStore.recordFalseWake(context)
    }

    fun falseWakeCount(context: Context): Int =
        prefs(context).getInt(KEY_FALSE_WAKE_COUNT, 0)

    fun lastFalseWakeTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_FALSE_WAKE_TIME, 0L)

    fun lastWifiToggleDiagnostic(context: Context): WifiToggleDiagnostic? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_LAST_WIFI_DIAGNOSTIC_KNOWN, false)) return null

        return WifiToggleDiagnostic(
            phase = p.getString(KEY_LAST_WIFI_DIAGNOSTIC_PHASE, "unknown") ?: "unknown",
            action = p.getString(KEY_LAST_WIFI_DIAGNOSTIC_ACTION, "unknown") ?: "unknown",
            attempted = p.getBoolean(KEY_LAST_WIFI_DIAGNOSTIC_ATTEMPTED, false),
            success = p.getBoolean(KEY_LAST_WIFI_DIAGNOSTIC_SUCCESS, false),
            airplaneMode = p.getBoolean(KEY_LAST_WIFI_DIAGNOSTIC_AIRPLANE, false),
            timestamp = p.getLong(KEY_LAST_WIFI_DIAGNOSTIC_TIME, 0L)
        )
    }
}
