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
    private val pending: (Context, SleepCycleStore.Snapshot) -> Boolean,
    /**
     * Abandons the state on Forget. Null only for cycle connectors, whose
     * ownership is erased together with the sleep-cycle record, and for
     * owned-state participants, which Forget deliberately keeps.
     */
    val forget: ((Context) -> Unit)?
) {
    fun isPending(context: Context, cycle: SleepCycleStore.Snapshot): Boolean =
        pending(context, cycle)
}

object RestoreParticipants {
    const val HELPER = "helper"
    const val SLEEP_CYCLE_RECORD = "sleep_cycle"
    const val DIRECT_RADIO = "direct_radio"
    const val RA_OFFLINE_PROXY_SLEEP_GATE = "raofflineproxy_sleep_gate"
    const val BATTERY_SAVER = "battery_saver"
    const val CHARGING_SEPARATION = "charging_separation"
    const val BASIC_SYNC_STOP_OWNERSHIP = "basicsync_stop_ownership"

    private fun connector(id: String, route: RestoreRoute) =
        RestoreParticipant(
            id = id,
            scope = RestoreScope.SLEEP_CYCLE,
            route = route,
            blocksCycleCompletion = true,
            cycleConnector = true,
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
            pending = { _, cycle ->
                cycle.active && cycle.helperExpected && !cycle.helperRestored
            },
            forget = { context -> HelperController.forgetPendingState(context) }
        ),
        connector(JamesDspConnector.id, RestoreRoute.IMMEDIATE),
        connector(KeyMapperController.CONNECTOR_ID, RestoreRoute.IMMEDIATE),
        connector(RaOfflineProxyConnector.id, RestoreRoute.IMMEDIATE),
        connector(BasicSyncConnector.id, RestoreRoute.IMMEDIATE),
        connector(SyncthingConnector.id, RestoreRoute.AFTER_NETWORK),
        connector(TailscaleConnector.id, RestoreRoute.AFTER_NETWORK),
        RestoreParticipant(
            id = SLEEP_CYCLE_RECORD,
            scope = RestoreScope.SLEEP_CYCLE,
            route = RestoreRoute.NONE,
            blocksCycleCompletion = false,
            pending = { _, cycle -> cycle.active },
            forget = { context -> SleepCycleStore.clear(context) }
        ),
        RestoreParticipant(
            id = DIRECT_RADIO,
            scope = RestoreScope.SLEEP_CYCLE,
            route = RestoreRoute.IMMEDIATE,
            blocksCycleCompletion = true,
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
            pending = { context, _ -> DeviceControlStore.batterySaver(context).owned },
            forget = null
        ),
        RestoreParticipant(
            id = CHARGING_SEPARATION,
            scope = RestoreScope.OWNED_STATE,
            route = RestoreRoute.OWNED_STATE_RETRY,
            blocksCycleCompletion = false,
            pending = { context, _ -> DeviceControlStore.chargingSeparation(context).owned },
            forget = null
        ),
        RestoreParticipant(
            id = BASIC_SYNC_STOP_OWNERSHIP,
            scope = RestoreScope.OWNED_STATE,
            route = RestoreRoute.OWNED_STATE_RETRY,
            blocksCycleCompletion = false,
            pending = { context, _ -> SyncStopOwnershipStore.hasBasicSyncOwnership(context) },
            forget = null
        )
    )

    fun withRoute(route: RestoreRoute): List<RestoreParticipant> =
        all.filter { it.route == route }

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
