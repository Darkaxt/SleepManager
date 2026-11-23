package com.med.sleepmanager.data

import android.content.Context
import com.med.sleepmanager.device.DeviceControlStore
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.KeyMapperController
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.connector.TailscaleConnector
import com.med.sleepmanager.service.DisableRestoreRetryScheduler
import com.med.sleepmanager.sync.SyncStopOwnershipStore

/** Lifecycle that owns a participant's restore state. */
enum class RestoreScope {
    /** Tied to the active SleepCycleStore transaction; abandoned by Forget. */
    SLEEP_CYCLE,

    /** Has its own ownership store and retry lifecycle; survives Forget. */
    OWNED_STATE
}

/** Where wake and disable perform this participant's restore. */
enum class RestoreRoute {
    /** Shared immediate step of wake and disable: [com.med.sleepmanager.service.RestoreCoordinator]. */
    IMMEDIATE,

    /** Waits for a validated network (Syncthing FOLLOW, Tailscale reconnect). */
    AFTER_NETWORK,

    /** Helper wake/restore protocol and its acknowledgement retries. */
    HELPER_RESULT,

    /** Device controls and BasicSync stop ownership, with their own retry runnables. */
    OWNED_STATE_RETRY,

    /** Bookkeeping only; nothing is restored (pre-sleep gate, the cycle record). */
    NONE
}

/**
 * Service lifecycle points at which process-death recovery runs, in the order
 * SleepManagerService reaches them.
 */
enum class RecoveryEvent {
    /** onCreate, before the enabled check: also runs in restore-only (disabled) mode. */
    CREATE,

    /** onCreate while enabled, after screen and lid monitoring are registered. */
    CREATE_ENABLED,

    /** Every onStartCommand while enabled. */
    START_ENABLED,

    /** The first enabled onStartCommand: the current screen state is replayed once. */
    SCREEN_STATE_REPLAY,

    /** onDestroy while enabled; the active sleep transaction is preserved. */
    DESTROY_ENABLED
}

/** What a participant's recovery does at one [RecoveryEvent]. */
enum class RecoveryBehaviour {
    /** Restores the owned state, but only on a real wake (screen on, no suppressed false wake). */
    RESTORE_ON_REAL_WAKE,

    /**
     * Restores owned state the app or its integration no longer manages,
     * whatever the screen state. The action itself checks that condition and
     * always runs, because it also resets its retry state when nothing is owned.
     */
    RESTORE_WHEN_UNMANAGED,

    /**
     * The participant's own recovery policy chooses between restoring the
     * previous state (real wake) and re-applying the sleep state (still asleep).
     */
    RESTORE_OR_REAPPLY,

    /**
     * Re-asserts the sleep state while effectively sleeping, if pending. Only
     * the first pending participant in [RestoreParticipants.recoveryOrder]
     * runs: they are alternative ways of re-asserting the same radio state.
     */
    REAPPLY_WHILE_SLEEPING,

    /**
     * Recovered by replaying the current screen state once: the screen-on flow
     * restores it on a real wake, the screen-off flow resumes it while asleep.
     */
    REPLAY_SCREEN_STATE
}

data class RecoveryStep(val event: RecoveryEvent, val behaviour: RecoveryBehaviour) {
    init {
        require(
            (event == RecoveryEvent.SCREEN_STATE_REPLAY) ==
                (behaviour == RecoveryBehaviour.REPLAY_SCREEN_STATE)
        ) { "$behaviour cannot run at $event" }
    }
}

/**
 * One piece of state SleepManager changes during sleep and must restore or
 * explicitly abandon. Every participant is registered once in
 * [RestoreParticipants]; pending checks, Forget and cycle completion are all
 * derived from that list, so a new participant cannot be left out of one of
 * them.
 */
class RestoreParticipant(
    val id: String,
    val scope: RestoreScope,
    val route: RestoreRoute,
    /** True if a pending state keeps the sleep cycle from completing. */
    val blocksCycleCompletion: Boolean,
    /** A connector whose ownership is recorded in SleepCycleStore. */
    val cycleConnector: Boolean = false,
    /**
     * How service process-death recovery handles this participant. Required:
     * an empty list is an explicit "no recovery" declaration.
     */
    val recovery: List<RecoveryStep>,
    private val pending: (Context, SleepCycleStore.Snapshot) -> Boolean,
    /**
     * Abandons the state on Forget. Null only for cycle connectors, whose
     * ownership is erased together with the sleep-cycle record, and for
     * owned-state participants, which Forget deliberately keeps.
     */
    val forget: ((Context) -> Unit)?
) {
    init {
        require(recovery.map { it.event }.toSet().size == recovery.size) {
            "$id declares more than one recovery step for one event"
        }
    }

    fun isPending(context: Context, cycle: SleepCycleStore.Snapshot): Boolean =
        pending(context, cycle)

    fun recoveryAt(event: RecoveryEvent): RecoveryStep? =
        recovery.singleOrNull { it.event == event }
}

object RestoreParticipants {
    const val HELPER = "helper"
    const val SLEEP_CYCLE_RECORD = "sleep_cycle"
    const val DIRECT_RADIO = "direct_radio"
    const val RA_OFFLINE_PROXY_SLEEP_GATE = "raofflineproxy_sleep_gate"
    const val BATTERY_SAVER = "battery_saver"
    const val CHARGING_SEPARATION = "charging_separation"
    const val BASIC_SYNC_STOP_OWNERSHIP = "basicsync_stop_ownership"

    private val replayScreenState =
        RecoveryStep(RecoveryEvent.SCREEN_STATE_REPLAY, RecoveryBehaviour.REPLAY_SCREEN_STATE)

    private fun connector(
        id: String,
        route: RestoreRoute,
        vararg recovery: RecoveryStep
    ) =
        RestoreParticipant(
            id = id,
            scope = RestoreScope.SLEEP_CYCLE,
            route = route,
            blocksCycleCompletion = true,
            cycleConnector = true,
            recovery = listOf(*recovery) + replayScreenState,
            pending = { context, _ -> SleepCycleStore.hasConnectorChange(context, id) },
            forget = null
        )

    /**
     * Registration order is execution order: Forget runs in this order, and
     * IMMEDIATE participants are restored in this order on wake and disable.
     */
    val all: List<RestoreParticipant> = listOf(
        RestoreParticipant(
            id = HELPER,
            scope = RestoreScope.SLEEP_CYCLE,
            route = RestoreRoute.HELPER_RESULT,
            blocksCycleCompletion = true,
            recovery = listOf(
                replayScreenState,
                // Create-time re-apply also requires a Wi-Fi-managing cycle and
                // an installed Helper; the service action checks both.
                RecoveryStep(RecoveryEvent.CREATE_ENABLED, RecoveryBehaviour.REAPPLY_WHILE_SLEEPING),
                RecoveryStep(RecoveryEvent.DESTROY_ENABLED, RecoveryBehaviour.REAPPLY_WHILE_SLEEPING)
            ),
            pending = { _, cycle ->
                cycle.active && cycle.helperExpected && !cycle.helperRestored
            },
            forget = { context -> HelperController.forgetPendingState(context) }
        ),
        connector(JamesDspConnector.id, RestoreRoute.IMMEDIATE),
        connector(
            KeyMapperController.CONNECTOR_ID,
            RestoreRoute.IMMEDIATE,
            RecoveryStep(RecoveryEvent.START_ENABLED, RecoveryBehaviour.RESTORE_ON_REAL_WAKE)
        ),
        connector(
            RaOfflineProxyConnector.id,
            RestoreRoute.IMMEDIATE,
            // Also in restore-only mode, while the integration is unmanaged
            // (the service action checks that); a managed integration is
            // restored by the regular wake flow.
            RecoveryStep(RecoveryEvent.CREATE, RecoveryBehaviour.RESTORE_ON_REAL_WAKE)
        ),
        connector(BasicSyncConnector.id, RestoreRoute.IMMEDIATE),
        connector(SyncthingConnector.id, RestoreRoute.AFTER_NETWORK),
        connector(TailscaleConnector.id, RestoreRoute.AFTER_NETWORK),
        RestoreParticipant(
            id = SLEEP_CYCLE_RECORD,
            scope = RestoreScope.SLEEP_CYCLE,
            route = RestoreRoute.NONE,
            blocksCycleCompletion = false,
            recovery = listOf(replayScreenState),
            pending = { _, cycle -> cycle.active },
            forget = { context -> SleepCycleStore.clear(context) }
        ),
        RestoreParticipant(
            id = DIRECT_RADIO,
            scope = RestoreScope.SLEEP_CYCLE,
            route = RestoreRoute.IMMEDIATE,
            blocksCycleCompletion = true,
            recovery = listOf(
                replayScreenState,
                RecoveryStep(RecoveryEvent.CREATE_ENABLED, RecoveryBehaviour.REAPPLY_WHILE_SLEEPING),
                RecoveryStep(RecoveryEvent.DESTROY_ENABLED, RecoveryBehaviour.REAPPLY_WHILE_SLEEPING)
            ),
            pending = { context, cycle ->
                cycle.active && DirectRadioStore.isPendingForCycle(context, cycle.cycleId)
            },
            forget = { context -> DirectRadioStore.clear(context) }
        ),
        RestoreParticipant(
            id = RA_OFFLINE_PROXY_SLEEP_GATE,
            scope = RestoreScope.SLEEP_CYCLE,
            route = RestoreRoute.NONE,
            blocksCycleCompletion = false,
            recovery = listOf(
                replayScreenState,
                // Resumed on create only; onDestroy re-asserts radios alone.
                RecoveryStep(RecoveryEvent.CREATE_ENABLED, RecoveryBehaviour.REAPPLY_WHILE_SLEEPING)
            ),
            pending = { context, cycle ->
                cycle.active &&
                    RaOfflineProxySleepStore.isPendingForCycle(context, cycle.cycleId)
            },
            forget = { context -> RaOfflineProxySleepStore.clear(context) }
        ),
        RestoreParticipant(
            id = BATTERY_SAVER,
            scope = RestoreScope.OWNED_STATE,
            route = RestoreRoute.OWNED_STATE_RETRY,
            blocksCycleCompletion = false,
            recovery = listOf(
                RecoveryStep(RecoveryEvent.CREATE_ENABLED, RecoveryBehaviour.RESTORE_OR_REAPPLY),
                replayScreenState
            ),
            pending = { context, _ -> DeviceControlStore.batterySaver(context).owned },
            forget = null
        ),
        RestoreParticipant(
            id = CHARGING_SEPARATION,
            scope = RestoreScope.OWNED_STATE,
            route = RestoreRoute.OWNED_STATE_RETRY,
            blocksCycleCompletion = false,
            // Lid-driven, not screen-driven: the lid monitor handles it afterwards.
            recovery = listOf(
                RecoveryStep(RecoveryEvent.CREATE_ENABLED, RecoveryBehaviour.RESTORE_OR_REAPPLY)
            ),
            pending = { context, _ -> DeviceControlStore.chargingSeparation(context).owned },
            forget = null
        ),
        RestoreParticipant(
            id = BASIC_SYNC_STOP_OWNERSHIP,
            scope = RestoreScope.OWNED_STATE,
            route = RestoreRoute.OWNED_STATE_RETRY,
            blocksCycleCompletion = false,
            recovery = listOf(
                RecoveryStep(RecoveryEvent.CREATE, RecoveryBehaviour.RESTORE_WHEN_UNMANAGED),
                RecoveryStep(RecoveryEvent.START_ENABLED, RecoveryBehaviour.RESTORE_WHEN_UNMANAGED)
            ),
            pending = { context, _ -> SyncStopOwnershipStore.hasBasicSyncOwnership(context) },
            forget = null
        )
    )

    fun withRoute(route: RestoreRoute): List<RestoreParticipant> =
        all.filter { it.route == route }

    /**
     * Execution order of each recovery event, as data. Order matters: device
     * controls are reconciled before radios are re-asserted, and for
     * REAPPLY_WHILE_SLEEPING only the first pending participant runs (the
     * RAOfflineProxy gate, then Direct radios, then the Helper). The screen
     * replay is one shared action and has no per-participant order. Every
     * participant declaring a step for an event must appear exactly once.
     */
    val recoveryOrder: Map<RecoveryEvent, List<String>> = mapOf(
        RecoveryEvent.CREATE to listOf(
            BASIC_SYNC_STOP_OWNERSHIP,
            RaOfflineProxyConnector.id
        ),
        RecoveryEvent.CREATE_ENABLED to listOf(
            BATTERY_SAVER,
            CHARGING_SEPARATION,
            RA_OFFLINE_PROXY_SLEEP_GATE,
            DIRECT_RADIO,
            HELPER
        ),
        RecoveryEvent.START_ENABLED to listOf(
            BASIC_SYNC_STOP_OWNERSHIP,
            KeyMapperController.CONNECTOR_ID
        ),
        RecoveryEvent.DESTROY_ENABLED to listOf(
            DIRECT_RADIO,
            HELPER
        )
    )

    fun isRegisteredConnector(connectorId: String): Boolean =
        all.any { it.cycleConnector && it.id == connectorId }
}

/** Pure decisions over participant states; see [RestoreTransaction]. */
internal object PendingRestorePolicy {
    /**
     * Restore work SleepManager can still perform. A restore problem message on
     * its own is not counted: without owned state there is nothing left to
     * retry, and the message stays visible until the user dismisses it.
     */
    fun isPending(cycleActive: Boolean, ownedStatePending: Boolean): Boolean =
        cycleActive || ownedStatePending

    /**
     * The cycle completes when no blocking participant is pending. Any recorded
     * connector change also blocks, even one whose id is not registered, so an
     * unregistered connector can never be silently dropped.
     */
    fun cycleRestored(
        blockingParticipantPending: Boolean,
        anyConnectorChangeRecorded: Boolean
    ): Boolean = !blockingParticipantPending && !anyConnectorChangeRecorded
}

/**
 * The single place that answers what still needs restoring, abandons a sleep
 * cycle and decides whether a cycle has been fully restored.
 */
object RestoreTransaction {
    fun pendingParticipants(context: Context): List<String> {
        val cycle = SleepCycleStore.current(context)
        return RestoreParticipants.all
            .filter { it.isPending(context, cycle) }
            .map { it.id }
    }

    /**
     * True while any owned state still needs restoring. Cycle-scoped work is
     * represented by the active sleep cycle; owned-state participants have their
     * own stores.
     */
    fun hasPendingRestore(context: Context): Boolean {
        val cycle = SleepCycleStore.current(context)
        return PendingRestorePolicy.isPending(
            cycleActive = cycle.active,
            ownedStatePending =
                RestoreParticipants.all.any {
                    it.scope == RestoreScope.OWNED_STATE && it.isPending(context, cycle)
                }
        )
    }

    /**
     * Abandons the pending sleep-cycle restore after the user chooses Forget.
     * Every cycle-scoped participant is forgotten so none is left orphaned
     * (their restore requires the matching active cycle). Owned-state
     * participants keep their own restore lifecycles, so disabled-state retries
     * continue only while one of them is still owned.
     */
    fun forgetSleepCycle(context: Context) {
        RestoreParticipants.all
            .filter { it.scope == RestoreScope.SLEEP_CYCLE }
            .forEach { it.forget?.invoke(context) }
        DisableRestoreRetryScheduler.cancel(context)
        DisableRestoreRetryScheduler.scheduleIfPending(context)
    }

    /** Whether every blocking participant of the active [cycle] is restored. */
    fun cycleRestored(context: Context, cycle: SleepCycleStore.Snapshot): Boolean =
        PendingRestorePolicy.cycleRestored(
            blockingParticipantPending =
                RestoreParticipants.all.any {
                    it.scope == RestoreScope.SLEEP_CYCLE &&
                        it.blocksCycleCompletion &&
                        it.isPending(context, cycle)
                },
            anyConnectorChangeRecorded = SleepCycleStore.hasPendingConnectorChanges(context)
        )
}
