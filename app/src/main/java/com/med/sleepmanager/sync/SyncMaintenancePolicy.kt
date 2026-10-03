package com.med.sleepmanager.sync

enum class PeriodicAlarmDeviceDecision {
    RUN,
    RETRY_AFTER_CLOSED_LID_FALSE_WAKE,
    CANCEL_AWAKE
}

enum class PeriodicMaintenanceFollowUp {
    SCHEDULE_NEXT,
    CANCEL
}

object SyncMaintenancePolicy {
    fun completionReady(
        selectedProviderCount: Int,
        allSelectedProvidersCompletionAware: Boolean
    ): Boolean =
        selectedProviderCount > 0 &&
            allSelectedProvidersCompletionAware

    fun periodicCanRun(
        managerEnabled: Boolean,
        periodicEnabled: Boolean,
        selectedProviderCount: Int,
        allSelectedProvidersCompletionAware: Boolean
    ): Boolean =
        managerEnabled &&
            periodicEnabled &&
            completionReady(
                selectedProviderCount,
                allSelectedProvidersCompletionAware
            )

    fun transitionCanRun(
        managerEnabled: Boolean,
        transitionEnabled: Boolean,
        selectedProviderCount: Int,
        allSelectedProvidersCompletionAware: Boolean
    ): Boolean =
        managerEnabled &&
            transitionEnabled &&
            completionReady(
                selectedProviderCount,
                allSelectedProvidersCompletionAware
            )

    fun shouldCancelMaintenanceOnScreenOff(
        activeTrigger: SyncMaintenanceTrigger?
    ): Boolean =
        activeTrigger == SyncMaintenanceTrigger.AFTER_WAKE

    fun periodicAlarmDeviceDecision(
        interactive: Boolean,
        closedLidWakeSuppressed: Boolean
    ): PeriodicAlarmDeviceDecision =
        when {
            !interactive -> PeriodicAlarmDeviceDecision.RUN
            closedLidWakeSuppressed ->
                PeriodicAlarmDeviceDecision.RETRY_AFTER_CLOSED_LID_FALSE_WAKE
            else -> PeriodicAlarmDeviceDecision.CANCEL_AWAKE
        }

    fun periodicCompletionFollowUp(
        stillSleeping: Boolean,
        periodicEnabled: Boolean
    ): PeriodicMaintenanceFollowUp =
        if (stillSleeping && periodicEnabled) {
            PeriodicMaintenanceFollowUp.SCHEDULE_NEXT
        } else {
            PeriodicMaintenanceFollowUp.CANCEL
        }
}
