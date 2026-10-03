package com.med.sleepmanager.rules

enum class HelperResultCorrelation {
    CURRENT,
    LEGACY_UNCORRELATED,
    STALE
}

object HelperResultPolicy {
    /**
     * Helper 1.1.1 and older do not echo cycleId in ACTION_RESULT.
     * Keep those results temporarily compatible while allowing newer Helpers
     * to be strictly correlated with the active SleepCycleStore transaction.
     */
    fun correlate(
        resultCycleId: Long,
        cycleActive: Boolean,
        currentCycleId: Long
    ): HelperResultCorrelation {
        if (!cycleActive || currentCycleId == 0L) {
            return HelperResultCorrelation.STALE
        }

        if (resultCycleId == 0L) {
            return HelperResultCorrelation.LEGACY_UNCORRELATED
        }

        return if (resultCycleId == currentCycleId) {
            HelperResultCorrelation.CURRENT
        } else {
            HelperResultCorrelation.STALE
        }
    }
}
