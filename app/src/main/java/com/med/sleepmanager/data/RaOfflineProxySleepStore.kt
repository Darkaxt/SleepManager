package com.med.sleepmanager.data

import android.content.Context

object RaOfflineProxySleepStore {
    private const val PREFS = "raofflineproxy_sleep_gate"
    private const val KEY_PHASE = "phase"
    private const val KEY_CYCLE_ID = "cycle_id"
    private const val KEY_WIFI = "wifi"
    private const val KEY_BLUETOOTH = "bluetooth"
    private const val KEY_UPDATED_AT = "updated_at"

    enum class Phase {
        NONE,
        WAITING_FOR_QUEUE,
        WAITING_FOR_SAFE_STATUS,
        STOP_REQUESTED,
        WAITING_FOR_STOP_CONFIRMATION,
        STOP_CONFIRMATION_TIMEOUT
    }

    data class Snapshot(
        val phase: Phase,
        val cycleId: Long,
        val wifi: Boolean,
        val bluetooth: Boolean,
        val updatedAt: Long
    ) {
        val pending: Boolean
            get() = phase != Phase.NONE && cycleId != 0L
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(context: Context): Snapshot {
        val p = prefs(context)
        val phase =
            runCatching {
                Phase.valueOf(
                    p.getString(KEY_PHASE, Phase.NONE.name)
                        ?: Phase.NONE.name
                )
            }.getOrDefault(Phase.NONE)

        return Snapshot(
            phase = phase,
            cycleId = p.getLong(KEY_CYCLE_ID, 0L),
            wifi = p.getBoolean(KEY_WIFI, false),
            bluetooth = p.getBoolean(KEY_BLUETOOTH, false),
            updatedAt = p.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun set(
        context: Context,
        phase: Phase,
        cycleId: Long,
        wifi: Boolean,
        bluetooth: Boolean
    ) {
        prefs(context).edit()
            .putString(KEY_PHASE, phase.name)
            .putLong(KEY_CYCLE_ID, cycleId)
            .putBoolean(KEY_WIFI, wifi)
            .putBoolean(KEY_BLUETOOTH, bluetooth)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .commit()
    }

    fun isPendingForCycle(
        context: Context,
        cycleId: Long
    ): Boolean {
        val state = current(context)
        return state.pending && state.cycleId == cycleId
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }
}
