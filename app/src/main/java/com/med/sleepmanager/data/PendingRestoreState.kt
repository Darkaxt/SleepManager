package com.med.sleepmanager.data

import android.content.Context
import com.med.sleepmanager.device.DeviceControlStore
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.service.DisableRestoreRetryScheduler
import com.med.sleepmanager.sync.SyncStopOwnershipStore

/**
 * Restore work SleepManager can still perform. A restore problem message on its
 * own is not counted: without owned state there is nothing left to retry, and
 * the message stays visible until the user dismisses it.
 */
internal object PendingRestorePolicy {
    fun isPending(
        cycleActive: Boolean,
        batterySaverOwned: Boolean,
        chargingSeparationOwned: Boolean,
        basicSyncOwned: Boolean
    ): Boolean =
        cycleActive ||
            batterySaverOwned ||
            chargingSeparationOwned ||
            basicSyncOwned
}

object PendingRestoreState {
    /**
     * True while any owned state still needs restoring. Direct radio, Helper,
     * connector and RAOfflineProxy work is tied to the active sleep cycle;
     * device controls and BasicSync ownership have their own stores.
     */
    fun hasPendingRestore(context: Context): Boolean =
        PendingRestorePolicy.isPending(
            cycleActive = SleepCycleStore.isActive(context),
            batterySaverOwned = DeviceControlStore.batterySaver(context).owned,
            chargingSeparationOwned =
                DeviceControlStore.chargingSeparation(context).owned,
            basicSyncOwned = SyncStopOwnershipStore.hasBasicSyncOwnership(context)
        )

    /**
     * Abandons the pending sleep-cycle restore after the user chooses Forget.
     * Clears every store keyed to that cycle so none is left orphaned (their
     * restore requires the matching active cycle). Device-control and BasicSync
     * ownership are not cycle-keyed and keep their own restore lifecycles, so
     * disabled-state retries continue only while one of them is still owned.
     */
    fun forgetSleepCycle(context: Context) {
        HelperController.forgetPendingState(context)
        SleepCycleStore.clear(context)
        DirectRadioStore.clear(context)
        RaOfflineProxySleepStore.clear(context)
        DisableRestoreRetryScheduler.cancel(context)
        DisableRestoreRetryScheduler.scheduleIfPending(context)
    }
}
