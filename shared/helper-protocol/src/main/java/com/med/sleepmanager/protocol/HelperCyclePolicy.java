package com.med.sleepmanager.protocol;

/**
 * Pure cycle-correlation rules used by the compatibility Helper.
 *
 * Keeping these decisions independent from Android state makes duplicate,
 * legacy and stale-cycle behavior explicit and regression-testable.
 */
public final class HelperCyclePolicy {
    private HelperCyclePolicy() {}

    public enum SleepDecision {
        START_NEW,
        ALREADY_SLEEPING,
        CYCLE_MISMATCH
    }

    public enum RestoreDecision {
        RESTORE_ACTIVE,
        ALREADY_RESTORED,
        NO_ACTIVE_CYCLE,
        CYCLE_MISMATCH
    }

    public static SleepDecision sleepDecision(
            boolean cycleActive,
            long activeCycleId,
            long requestedCycleId
    ) {
        if (!cycleActive) {
            return SleepDecision.START_NEW;
        }

        if (
                requestedCycleId != 0L &&
                activeCycleId != 0L &&
                requestedCycleId != activeCycleId
        ) {
            return SleepDecision.CYCLE_MISMATCH;
        }

        return SleepDecision.ALREADY_SLEEPING;
    }

    public static RestoreDecision restoreDecision(
            boolean cycleActive,
            long activeCycleId,
            long lastRestoredCycleId,
            long requestedCycleId
    ) {
        if (!cycleActive) {
            if (
                    requestedCycleId != 0L &&
                    lastRestoredCycleId == requestedCycleId
            ) {
                return RestoreDecision.ALREADY_RESTORED;
            }
            return RestoreDecision.NO_ACTIVE_CYCLE;
        }

        if (
                requestedCycleId != 0L &&
                activeCycleId != 0L &&
                requestedCycleId != activeCycleId
        ) {
            return RestoreDecision.CYCLE_MISMATCH;
        }

        return RestoreDecision.RESTORE_ACTIVE;
    }
}
