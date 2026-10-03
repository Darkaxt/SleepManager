package com.med.sleepmanager.service

/**
 * In-memory runtime state for the current sleep/wake cycle.
 *
 * Persistent ownership and restore information remains in SleepCycleStore.
 * This class only groups transient flags used while the service process is
 * alive, keeping their reset semantics independent.
 */
internal class SleepCycleRuntimeState {
    var actionsApplied: Boolean = false
        private set

    var skippedByConditions: Boolean = false
        private set

    var falseWakeResleepPending: Boolean = false
        private set

    fun markActionsApplied() {
        actionsApplied = true
    }

    fun clearActionsApplied() {
        actionsApplied = false
    }

    fun markFalseWakeResleepPending() {
        falseWakeResleepPending = true
    }

    fun consumeFalseWakeResleepPending(): Boolean {
        val pending = falseWakeResleepPending
        falseWakeResleepPending = false
        return pending
    }

    fun clearFalseWakeResleepPending() {
        falseWakeResleepPending = false
    }

    fun markSkippedByConditions() {
        skippedByConditions = true
    }

    fun clearSkippedByConditions() {
        skippedByConditions = false
    }
}
