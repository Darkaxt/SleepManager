package com.med.sleepmanager.rules

import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyCommandResult
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyPendingAwardState
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyQueueState
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyStatus

enum class RaOfflineProxyPreSleepDecision {
    LEAVE_UNTOUCHED,
    STOP_NOW,
    WAIT_FOR_QUEUE,
    WAIT_FOR_SAFE_STATUS,
    UNSUPPORTED_API
}

enum class RaOfflineProxyRestoreDecision {
    NOTHING,
    START,
    NEEDS_UNRESTRICTED_BATTERY,
    RETRY_OR_REPORT_FAILURE
}

object RaOfflineProxyPolicy {
    const val MIN_SUPPORTED_API_VERSION = 1
    const val MAX_SUPPORTED_API_VERSION = 2

    fun preSleepDecision(
        status: RaOfflineProxyStatus?
    ): RaOfflineProxyPreSleepDecision {
        if (status == null) {
            return RaOfflineProxyPreSleepDecision.WAIT_FOR_SAFE_STATUS
        }

        if (status.version !in MIN_SUPPORTED_API_VERSION..MAX_SUPPORTED_API_VERSION) {
            return RaOfflineProxyPreSleepDecision.UNSUPPORTED_API
        }

        if (!status.shouldBeRunning) {
            return RaOfflineProxyPreSleepDecision.LEAVE_UNTOUCHED
        }

        when (status.queue.state) {
            RaOfflineProxyQueueState.CACHING,
            RaOfflineProxyQueueState.WAITING ->
                return RaOfflineProxyPreSleepDecision.WAIT_FOR_QUEUE

            RaOfflineProxyQueueState.UNKNOWN ->
                return RaOfflineProxyPreSleepDecision.WAIT_FOR_SAFE_STATUS

            RaOfflineProxyQueueState.IDLE,
            RaOfflineProxyQueueState.BLOCKED -> Unit
        }

        // API v1 has no pendingAwards object. Preserve the shipped 0.7.1
        // cache-queue behavior as the compatibility fallback.
        val pending = status.pendingAwards
            ?: return RaOfflineProxyPreSleepDecision.STOP_NOW

        // A Wi-Fi radio that is ON does not imply usable Internet. If
        // RAOfflineProxy says it is offline, waiting/syncing awards cannot make
        // progress, so do not strand the sleep transition. Smart Wi-Fi is the
        // separate feature that may acquire connectivity later.
        if (!status.online) {
            return when (pending.state) {
                RaOfflineProxyPendingAwardState.UNKNOWN ->
                    RaOfflineProxyPreSleepDecision.WAIT_FOR_SAFE_STATUS
                else ->
                    RaOfflineProxyPreSleepDecision.STOP_NOW
            }
        }

        return when (pending.state) {
            RaOfflineProxyPendingAwardState.IDLE,
            RaOfflineProxyPendingAwardState.BLOCKED ->
                RaOfflineProxyPreSleepDecision.STOP_NOW

            RaOfflineProxyPendingAwardState.WAITING,
            RaOfflineProxyPendingAwardState.SYNCING ->
                RaOfflineProxyPreSleepDecision.WAIT_FOR_QUEUE

            RaOfflineProxyPendingAwardState.UNKNOWN ->
                RaOfflineProxyPreSleepDecision.WAIT_FOR_SAFE_STATUS
        }
    }

    /**
     * STOP ownership is recorded as soon as RAOfflineProxy accepted the stop
     * and reports that it no longer intends to run. running may remain true for
     * a short time; Wi-Fi must stay available until it becomes false.
     */
    fun shouldTakeStopOwnership(
        initialStatus: RaOfflineProxyStatus?,
        stopResult: RaOfflineProxyCommandResult
    ): Boolean =
        initialStatus?.shouldBeRunning == true &&
            stopResult.code == RaOfflineProxyCommandResult.RESULT_OK &&
            stopResult.status?.shouldBeRunning == false

    fun stopConfirmed(
        status: RaOfflineProxyStatus?
    ): Boolean =
        status != null &&
            !status.shouldBeRunning &&
            !status.running

    fun canReleaseManagedWifi(
        status: RaOfflineProxyStatus?
    ): Boolean = stopConfirmed(status)

    fun restoreDecision(
        owned: Boolean,
        lastResultCode: String? = null
    ): RaOfflineProxyRestoreDecision {
        if (!owned) {
            return RaOfflineProxyRestoreDecision.NOTHING
        }

        return when (lastResultCode) {
            null,
            RaOfflineProxyCommandResult.RESULT_OK ->
                RaOfflineProxyRestoreDecision.START

            RaOfflineProxyCommandResult
                .RESULT_FOREGROUND_SERVICE_NOT_ALLOWED ->
                RaOfflineProxyRestoreDecision.NEEDS_UNRESTRICTED_BATTERY

            else ->
                RaOfflineProxyRestoreDecision.RETRY_OR_REPORT_FAILURE
        }
    }

    fun restoreConfirmed(
        status: RaOfflineProxyStatus?
    ): Boolean =
        status != null &&
            status.shouldBeRunning &&
            status.running
}
