package com.med.sleepmanager.service

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.util.Log
import com.med.sleepmanager.data.DiagnosticsStateStore
import com.med.sleepmanager.data.RaOfflineProxySleepStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.RaOfflineProxyController
import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyCommandResult
import com.med.sleepmanager.rules.RaOfflineProxyPolicy
import com.med.sleepmanager.rules.RaOfflineProxyPreSleepDecision
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Event-driven RAOfflineProxy sleep/wake orchestration.
 *
 * Long queue waits never hold a wakelock and never poll. ContentObserver wakes
 * the coordinator when upstream state changes. Only the short stop/start
 * confirmations use bounded fallback checks.
 */
internal class RaOfflineProxyCoordinator(
    context: Context,
    private val handler: Handler,
    private val canContinueSleepGate: (Long) -> Boolean,
    private val onSleepGateReady: (
        cycleId: Long,
        wifi: Boolean,
        bluetooth: Boolean
    ) -> Unit
) {
    companion object {
        private const val TAG = "RAOfflineProxyFlow"
        private const val STOP_CONFIRM_TIMEOUT_MS = 15_000L
        private const val STOP_CONFIRM_RECHECK_MS = 1_000L
        private const val RESTORE_CONFIRM_TIMEOUT_MS = 15_000L
        private const val RESTORE_CONFIRM_RECHECK_MS = 750L
    }

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong(0L)

    @Volatile
    private var observer: ContentObserver? = null

    @Volatile
    private var stopConfirmStartedAt = 0L

    @Volatile
    private var restorePending = false

    @Volatile
    private var restoreStartedAt = 0L

    @Volatile
    private var restoreCallback: ((Boolean, String) -> Unit)? = null

    fun beginSleepGate(
        cycleId: Long,
        wifi: Boolean,
        bluetooth: Boolean
    ) {
        val token = generation.incrementAndGet()
        stopConfirmStartedAt = 0L
        // Persist immediately so a process death before the worker executes
        // cannot let service recovery cut Wi-Fi ahead of the proxy gate.
        RaOfflineProxySleepStore.set(
            appContext,
            RaOfflineProxySleepStore.Phase.WAITING_FOR_SAFE_STATUS,
            cycleId,
            wifi,
            bluetooth
        )
        ensureObserver()
        evaluateSleepGateAsync(
            token = token,
            cycleId = cycleId,
            wifi = wifi,
            bluetooth = bluetooth
        )
    }

    fun resumeSleepGate(cycleId: Long) {
        val state = RaOfflineProxySleepStore.current(appContext)
        if (!state.pending || state.cycleId != cycleId) {
            return
        }

        val token = generation.incrementAndGet()
        ensureObserver()
        evaluateSleepGateAsync(
            token = token,
            cycleId = cycleId,
            wifi = state.wifi,
            bluetooth = state.bluetooth
        )
    }

    fun cancelSleepGate(clearPersistedState: Boolean) {
        generation.incrementAndGet()
        stopConfirmStartedAt = 0L
        if (clearPersistedState) {
            RaOfflineProxySleepStore.clear(appContext)
        }
        if (!restorePending) {
            unregisterObserver()
        }
    }

    fun restoreOwned(
        onFinished: (success: Boolean, detail: String) -> Unit
    ) {
        val change =
            SleepCycleStore.connectorChange(
                appContext,
                RaOfflineProxyConnector.id
            )
        if (
            change?.restoreToken !=
            RaOfflineProxyConnector.TOKEN_RESTART
        ) {
            handler.post {
                onFinished(true, "not owned")
            }
            return
        }

        restoreCallback = onFinished
        restorePending = true
        restoreStartedAt = System.currentTimeMillis()
        ensureObserver()

        executor.execute {
            val result = RaOfflineProxyController.start(appContext)
            when (result.code) {
                RaOfflineProxyCommandResult.RESULT_OK -> {
                    val status =
                        result.status
                            ?: RaOfflineProxyController.status(appContext)
                    if (RaOfflineProxyPolicy.restoreConfirmed(status)) {
                        finishRestore(
                            success = true,
                            detail = "START confirmed"
                        )
                    } else {
                        scheduleRestoreConfirmation()
                    }
                }

                RaOfflineProxyCommandResult
                    .RESULT_FOREGROUND_SERVICE_NOT_ALLOWED -> {
                    finishRestore(
                        success = false,
                        detail =
                            "Android blocked background start; set RAOfflineProxy battery usage to Unrestricted"
                    )
                }

                else -> {
                    finishRestore(
                        success = false,
                        detail =
                            "START failed: " +
                                (result.code ?: result.detail ?: "unknown")
                    )
                }
            }
        }
    }

    fun shutdown(preservePersistedState: Boolean) {
        generation.incrementAndGet()
        if (!preservePersistedState) {
            RaOfflineProxySleepStore.clear(appContext)
        }
        unregisterObserver()
        restorePending = false
        restoreCallback = null
        executor.shutdownNow()
    }

    private fun evaluateSleepGateAsync(
        token: Long,
        cycleId: Long,
        wifi: Boolean,
        bluetooth: Boolean
    ) {
        if (token != generation.get()) return

        executor.execute {
            if (
                token != generation.get() ||
                !canContinueSleepGate(cycleId)
            ) {
                return@execute
            }

            val currentState =
                RaOfflineProxySleepStore.current(appContext)
            val status = RaOfflineProxyController.status(appContext)

            if (
                token != generation.get() ||
                !canContinueSleepGate(cycleId)
            ) {
                return@execute
            }

            if (
                currentState.cycleId == cycleId &&
                (
                    currentState.phase ==
                        RaOfflineProxySleepStore.Phase.STOP_REQUESTED ||
                    currentState.phase ==
                        RaOfflineProxySleepStore.Phase
                            .WAITING_FOR_STOP_CONFIRMATION ||
                    currentState.phase ==
                        RaOfflineProxySleepStore.Phase
                            .STOP_CONFIRMATION_TIMEOUT
                ) &&
                status != null &&
                !status.shouldBeRunning
            ) {
                if (!status.running) {
                    completeSleepGate(
                        token,
                        cycleId,
                        wifi,
                        bluetooth,
                        "STOP confirmed"
                    )
                } else if (
                    currentState.phase !=
                    RaOfflineProxySleepStore.Phase
                        .STOP_CONFIRMATION_TIMEOUT
                ) {
                    if (
                        stopConfirmStartedAt == 0L &&
                        currentState.updatedAt > 0L
                    ) {
                        stopConfirmStartedAt =
                            currentState.updatedAt
                    }
                    waitForStopConfirmation(
                        token,
                        cycleId,
                        wifi,
                        bluetooth
                    )
                }
                // After a confirmed timeout, remain observer-only. A later
                // notifyChange can still complete the gate without restarting
                // a periodic retry loop.
                return@execute
            }

            when (RaOfflineProxyPolicy.preSleepDecision(status)) {
                RaOfflineProxyPreSleepDecision.LEAVE_UNTOUCHED -> {
                    completeSleepGate(
                        token,
                        cycleId,
                        wifi,
                        bluetooth,
                        "already stopped"
                    )
                }

                RaOfflineProxyPreSleepDecision.STOP_NOW -> {
                    if (
                        token != generation.get() ||
                        !canContinueSleepGate(cycleId)
                    ) {
                        return@execute
                    }

                    val initial = status

                    // Transaction log first: if our process dies after the
                    // provider changes RAOfflineProxy but before call() returns,
                    // the next real wake still knows that START is owed.
                    SleepCycleStore.recordConnectorChange(
                        appContext,
                        RaOfflineProxyConnector.id,
                        RaOfflineProxyConnector.TOKEN_RESTART
                    )
                    RaOfflineProxySleepStore.set(
                        appContext,
                        RaOfflineProxySleepStore.Phase.STOP_REQUESTED,
                        cycleId,
                        wifi,
                        bluetooth
                    )

                    val result =
                        RaOfflineProxyController.stop(appContext)

                    // A real wake/disable can happen while the blocking provider
                    // call is in flight. Keep provisional ownership in that
                    // case: the wake path will restore it after STOP releases
                    // the upstream control lock.
                    if (
                        token != generation.get() ||
                        !canContinueSleepGate(cycleId)
                    ) {
                        return@execute
                    }

                    val owned =
                        RaOfflineProxyPolicy.shouldTakeStopOwnership(
                            initialStatus = initial,
                            stopResult = result
                        )

                    if (!owned) {
                        SleepCycleStore.clearConnectorChange(
                            appContext,
                            RaOfflineProxyConnector.id
                        )
                        persistAndHold(
                            phase =
                                RaOfflineProxySleepStore.Phase
                                    .WAITING_FOR_SAFE_STATUS,
                            cycleId = cycleId,
                            wifi = wifi,
                            bluetooth = bluetooth,
                            message =
                                "Sleep → RAOfflineProxy STOP not safely owned · " +
                                    (result.code ?: "unknown")
                        )
                        return@execute
                    }

                    DiagnosticsStateStore.recordEvent(
                        appContext,
                        "Sleep → RAOfflineProxy STOP accepted · restore owned"
                    )

                    val afterStop =
                        result.status
                            ?: RaOfflineProxyController.status(appContext)

                    if (
                        token != generation.get() ||
                        !canContinueSleepGate(cycleId)
                    ) {
                        return@execute
                    }

                    if (
                        RaOfflineProxyPolicy.stopConfirmed(afterStop)
                    ) {
                        completeSleepGate(
                            token,
                            cycleId,
                            wifi,
                            bluetooth,
                            "STOP confirmed"
                        )
                    } else {
                        stopConfirmStartedAt =
                            System.currentTimeMillis()
                        RaOfflineProxySleepStore.set(
                            appContext,
                            RaOfflineProxySleepStore.Phase
                                .WAITING_FOR_STOP_CONFIRMATION,
                            cycleId,
                            wifi,
                            bluetooth
                        )
                        scheduleStopConfirmation(
                            token,
                            cycleId,
                            wifi,
                            bluetooth
                        )
                    }
                }

                RaOfflineProxyPreSleepDecision.WAIT_FOR_QUEUE -> {
                    RaOfflineProxySleepStore.set(
                        appContext,
                        RaOfflineProxySleepStore.Phase
                            .WAITING_FOR_QUEUE,
                        cycleId,
                        wifi,
                        bluetooth
                    )
                    DiagnosticsStateStore.recordEvent(
                        appContext,
                        "Sleep → RAOfflineProxy queue busy · Wi-Fi kept available"
                    )
                }

                RaOfflineProxyPreSleepDecision.WAIT_FOR_SAFE_STATUS -> {
                    persistAndHold(
                        phase =
                            RaOfflineProxySleepStore.Phase
                                .WAITING_FOR_SAFE_STATUS,
                        cycleId = cycleId,
                        wifi = wifi,
                        bluetooth = bluetooth,
                        message =
                            "Sleep → RAOfflineProxy status unavailable · Wi-Fi kept available"
                    )
                }

                RaOfflineProxyPreSleepDecision.UNSUPPORTED_API -> {
                    persistAndHold(
                        phase =
                            RaOfflineProxySleepStore.Phase
                                .WAITING_FOR_SAFE_STATUS,
                        cycleId = cycleId,
                        wifi = wifi,
                        bluetooth = bluetooth,
                        message =
                            "Sleep → unsupported RAOfflineProxy automation API · Wi-Fi kept available"
                    )
                }
            }
        }
    }

    private fun waitForStopConfirmation(
        token: Long,
        cycleId: Long,
        wifi: Boolean,
        bluetooth: Boolean
    ) {
        if (stopConfirmStartedAt == 0L) {
            val persisted =
                RaOfflineProxySleepStore.current(appContext)
                    .updatedAt
                    .takeIf { it > 0L }
            stopConfirmStartedAt =
                persisted ?: System.currentTimeMillis()
        }

        val elapsed =
            System.currentTimeMillis() - stopConfirmStartedAt
        if (elapsed >= STOP_CONFIRM_TIMEOUT_MS) {
            RaOfflineProxySleepStore.set(
                appContext,
                RaOfflineProxySleepStore.Phase
                    .STOP_CONFIRMATION_TIMEOUT,
                cycleId,
                wifi,
                bluetooth
            )
            DiagnosticsStateStore.recordEvent(
                appContext,
                "Sleep → RAOfflineProxy STOP confirmation timed out · Wi-Fi kept available"
            )
            return
        }

        scheduleStopConfirmation(
            token,
            cycleId,
            wifi,
            bluetooth
        )
    }

    private fun scheduleStopConfirmation(
        token: Long,
        cycleId: Long,
        wifi: Boolean,
        bluetooth: Boolean
    ) {
        handler.postDelayed(
            {
                if (
                    token == generation.get() &&
                    canContinueSleepGate(cycleId)
                ) {
                    evaluateSleepGateAsync(
                        token,
                        cycleId,
                        wifi,
                        bluetooth
                    )
                }
            },
            STOP_CONFIRM_RECHECK_MS
        )
    }

    private fun scheduleRestoreConfirmation() {
        handler.postDelayed(
            {
                if (!restorePending) return@postDelayed
                executor.execute {
                    if (!restorePending) return@execute

                    val status =
                        RaOfflineProxyController.status(appContext)
                    if (
                        RaOfflineProxyPolicy
                            .restoreConfirmed(status)
                    ) {
                        finishRestore(
                            success = true,
                            detail = "START confirmed"
                        )
                        return@execute
                    }

                    val elapsed =
                        System.currentTimeMillis() -
                            restoreStartedAt
                    if (elapsed >= RESTORE_CONFIRM_TIMEOUT_MS) {
                        finishRestore(
                            success = false,
                            detail =
                                "START was accepted but running=true was not confirmed"
                        )
                    } else {
                        scheduleRestoreConfirmation()
                    }
                }
            },
            RESTORE_CONFIRM_RECHECK_MS
        )
    }

    private fun finishRestore(
        success: Boolean,
        detail: String
    ) {
        if (success) {
            SleepCycleStore.clearConnectorChange(
                appContext,
                RaOfflineProxyConnector.id
            )
        }

        val callback = restoreCallback
        restoreCallback = null
        restorePending = false
        restoreStartedAt = 0L

        if (!RaOfflineProxySleepStore.current(appContext).pending) {
            unregisterObserver()
        }

        handler.post {
            callback?.invoke(success, detail)
        }
    }

    private fun completeSleepGate(
        token: Long,
        cycleId: Long,
        wifi: Boolean,
        bluetooth: Boolean,
        reason: String
    ) {
        if (token != generation.get()) return

        // Invalidate any already-queued observer/fallback evaluation before
        // exposing completion to the service. This keeps Helper/device actions
        // exactly-once for this gate.
        val completedGeneration = generation.incrementAndGet()

        RaOfflineProxySleepStore.clear(appContext)
        stopConfirmStartedAt = 0L
        if (!restorePending) {
            unregisterObserver()
        }

        DiagnosticsStateStore.recordEvent(
            appContext,
            "Sleep → RAOfflineProxy gate complete · " + reason
        )

        handler.post {
            if (
                completedGeneration == generation.get() &&
                canContinueSleepGate(cycleId)
            ) {
                onSleepGateReady(
                    cycleId,
                    wifi,
                    bluetooth
                )
            }
        }
    }

    private fun persistAndHold(
        phase: RaOfflineProxySleepStore.Phase,
        cycleId: Long,
        wifi: Boolean,
        bluetooth: Boolean,
        message: String
    ) {
        RaOfflineProxySleepStore.set(
            appContext,
            phase,
            cycleId,
            wifi,
            bluetooth
        )
        DiagnosticsStateStore.recordEvent(
            appContext,
            message
        )
    }

    private fun ensureObserver() {
        if (observer != null) return
        handler.post {
            if (observer != null) return@post
            if (
                !restorePending &&
                !RaOfflineProxySleepStore.current(appContext).pending
            ) {
                return@post
            }
            observer =
                RaOfflineProxyController.registerStatusObserver(
                    appContext,
                    handler
                ) {
                    if (restorePending) {
                        executor.execute {
                            if (!restorePending) return@execute
                            val status =
                                RaOfflineProxyController
                                    .status(appContext)
                            if (
                                RaOfflineProxyPolicy
                                    .restoreConfirmed(status)
                            ) {
                                finishRestore(
                                    success = true,
                                    detail =
                                        "START confirmed by status change"
                                )
                            }
                        }
                    } else {
                        val state =
                            RaOfflineProxySleepStore
                                .current(appContext)
                        if (
                            state.pending &&
                            canContinueSleepGate(state.cycleId)
                        ) {
                            evaluateSleepGateAsync(
                                token = generation.get(),
                                cycleId = state.cycleId,
                                wifi = state.wifi,
                                bluetooth = state.bluetooth
                            )
                        }
                    }
                }
        }
    }

    private fun unregisterObserver() {
        val current = observer ?: return
        observer = null
        handler.post {
            RaOfflineProxyController.unregisterStatusObserver(
                appContext,
                current
            )
        }
    }
}
