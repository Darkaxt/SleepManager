package com.med.sleepmanager.data

import android.content.Context

/** Compatibility entry points; the participant list lives in [RestoreTransaction]. */
object PendingRestoreState {
    /** True while any owned state still needs restoring. */
    fun hasPendingRestore(context: Context): Boolean =
        RestoreTransaction.hasPendingRestore(context)

    /** Abandons the pending sleep-cycle restore after the user chooses Forget. */
    fun forgetSleepCycle(context: Context) =
        RestoreTransaction.forgetSleepCycle(context)
}
