package com.med.sleepmanager.data

import android.content.Context

object SleepCycleStore {
    private const val PREFS = "sleep_cycle_state"

    private const val KEY_ACTIVE = "active"
    private const val KEY_CYCLE_ID = "cycle_id"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_HELPER_EXPECTED = "helper_expected"
    private const val KEY_HELPER_SLEEP_REQUESTED = "helper_sleep_requested"
    private const val KEY_HELPER_RESTORED = "helper_restored"
    private const val KEY_WIFI_MANAGED = "wifi_managed"
    private const val KEY_BLUETOOTH_MANAGED = "bluetooth_managed"
    private const val KEY_RESTORE_PROBLEM = "restore_problem"
    private const val CONNECTOR_PREFIX = "connector."
    private const val CONNECTOR_CHANGED_SUFFIX = ".changed"

    data class ConnectorChange(
        val connectorId: String,
        val restoreToken: String?
    )

    data class Snapshot(
        val active: Boolean,
        val cycleId: Long,
        val startedAt: Long,
        val helperExpected: Boolean,
        val helperSleepRequested: Boolean,
        val helperRestored: Boolean,
        val wifiManaged: Boolean,
        val bluetoothManaged: Boolean
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun begin(
        context: Context,
        helperExpected: Boolean,
        wifiManaged: Boolean,
        bluetoothManaged: Boolean
    ): Snapshot {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .clear()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_CYCLE_ID, now)
            .putLong(KEY_STARTED_AT, now)
            .putBoolean(KEY_HELPER_EXPECTED, helperExpected)
            .putBoolean(KEY_HELPER_SLEEP_REQUESTED, false)
            .putBoolean(KEY_HELPER_RESTORED, !helperExpected)
            .putBoolean(KEY_WIFI_MANAGED, wifiManaged)
            .putBoolean(KEY_BLUETOOTH_MANAGED, bluetoothManaged)
            .commit()

        val snapshot = current(context)
        DiagnosticsCycleStore.attachTransaction(context, snapshot)
        return snapshot
    }

    fun current(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            active = p.getBoolean(KEY_ACTIVE, false),
            cycleId = p.getLong(KEY_CYCLE_ID, 0L),
            startedAt = p.getLong(KEY_STARTED_AT, 0L),
            helperExpected = p.getBoolean(KEY_HELPER_EXPECTED, false),
            helperSleepRequested = p.getBoolean(KEY_HELPER_SLEEP_REQUESTED, false),
            helperRestored = p.getBoolean(KEY_HELPER_RESTORED, true),
            wifiManaged = p.getBoolean(KEY_WIFI_MANAGED, false),
            bluetoothManaged = p.getBoolean(KEY_BLUETOOTH_MANAGED, false)
        )
    }

    fun isActive(context: Context): Boolean = current(context).active

    fun markHelperSleepRequested(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_HELPER_SLEEP_REQUESTED, true)
            .commit()
        DiagnosticsCycleStore.markHelperSleepRequested(context)
    }

    fun markHelperRestored(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_HELPER_RESTORED, true)
            .commit()
        DiagnosticsCycleStore.markHelperRestored(context)
    }

    fun recordConnectorChange(
        context: Context,
        connectorId: String,
        restoreToken: String?
    ) {
        prefs(context).edit()
            .putBoolean(connectorChangedKey(connectorId), true)
            .apply {
                if (restoreToken == null) remove(connectorTokenKey(connectorId))
                else putString(connectorTokenKey(connectorId), restoreToken)
            }
            .commit()
        DiagnosticsCycleStore.recordConnectorOwnership(
            context = context,
            connectorId = connectorId,
            restoreTokenPresent = restoreToken != null
        )
    }

    fun connectorChange(context: Context, connectorId: String): ConnectorChange? {
        val p = prefs(context)
        if (!p.getBoolean(connectorChangedKey(connectorId), false)) return null
        return ConnectorChange(
            connectorId = connectorId,
            restoreToken = p.getString(connectorTokenKey(connectorId), null)
        )
    }

    fun clearConnectorChange(context: Context, connectorId: String) {
        prefs(context).edit()
            .remove(connectorChangedKey(connectorId))
            .remove(connectorTokenKey(connectorId))
            .commit()
        DiagnosticsCycleStore.clearConnectorOwnership(context, connectorId)
    }

    fun hasConnectorChange(context: Context, connectorId: String): Boolean =
        prefs(context).getBoolean(connectorChangedKey(connectorId), false)

    fun hasPendingConnectorChanges(context: Context): Boolean =
        prefs(context).all.any { (key, value) ->
            key.startsWith(CONNECTOR_PREFIX) &&
                key.endsWith(CONNECTOR_CHANGED_SUFFIX) &&
                value == true
        }

    fun markRestoreProblem(context: Context, message: String) {
        prefs(context).edit()
            .putString(KEY_RESTORE_PROBLEM, message)
            .commit()
        DiagnosticsCycleStore.markRestoreProblem(context, message)
    }

    fun restoreProblem(context: Context): String? =
        prefs(context).getString(KEY_RESTORE_PROBLEM, null)

    fun clearRestoreProblem(context: Context) {
        prefs(context).edit().remove(KEY_RESTORE_PROBLEM).commit()
        DiagnosticsCycleStore.clearRestoreProblem(context)
    }

    fun completeIfRestored(context: Context): Boolean {
        val snapshot = current(context)
        if (!snapshot.active) return true

        val helperDone = !snapshot.helperExpected || snapshot.helperRestored
        val connectorsDone = !hasPendingConnectorChanges(context)
        val directRadiosDone =
            !DirectRadioStore.isPendingForCycle(context, snapshot.cycleId)

        if (helperDone && connectorsDone && directRadiosDone) {
            DiagnosticsCycleStore.markRestorationComplete(
                context = context,
                transactionCycleId = snapshot.cycleId
            )
            clear(context)
            return true
        }
        return false
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    private fun connectorChangedKey(id: String) =
        "$CONNECTOR_PREFIX$id$CONNECTOR_CHANGED_SUFFIX"

    private fun connectorTokenKey(id: String) = "$CONNECTOR_PREFIX$id.token"
}
