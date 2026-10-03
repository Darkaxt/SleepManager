package com.med.sleepmanager.service

/**
 * One-shot in-memory handoff from a successful Helper wake/restore result to
 * pending network connector restoration.
 *
 * Helper IPC, network readiness, connector calls, logging and persistent
 * restore ownership remain in SleepManagerService.
 */
internal class HelperNetworkRestoreHandoffState {
    var isPending: Boolean = false
        private set

    fun prepare(
        helperRestoreNeeded: Boolean,
        networkRestoreNeeded: Boolean
    ) {
        isPending = helperRestoreNeeded && networkRestoreNeeded
    }

    fun consume(): Boolean {
        val pending = isPending
        isPending = false
        return pending
    }

    fun clear() {
        isPending = false
    }
}
