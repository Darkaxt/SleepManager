package com.med.sleepmanager.rules

enum class BatterySaverRestoreDecision {
    NOTHING_TO_RESTORE,
    CLEAR_OWNERSHIP,
    RESTORE_PREVIOUS
}

enum class BatterySaverRecoveryDecision {
    NOTHING,
    RESTORE_PREVIOUS,
    REAPPLY_SLEEP_STATE
}

enum class BatterySaverPowerEventDecision {
    NOTHING,
    ENABLE_SLEEP_STATE,
    RESTORE_PREVIOUS
}

object BatterySaverPolicy {
    fun shouldEnableForSleep(
        manageEnabled: Boolean,
        controlSupported: Boolean,
        owned: Boolean,
        currentlyEnabled: Boolean,
        externalPowerConnected: Boolean = false
    ): Boolean =
        manageEnabled &&
            controlSupported &&
            !owned &&
            !currentlyEnabled &&
            !externalPowerConnected

    fun restoreDecision(
        owned: Boolean,
        previous: Boolean,
        current: Boolean
    ): BatterySaverRestoreDecision =
        when {
            !owned -> BatterySaverRestoreDecision.NOTHING_TO_RESTORE
            current == previous -> BatterySaverRestoreDecision.CLEAR_OWNERSHIP
            else -> BatterySaverRestoreDecision.RESTORE_PREVIOUS
        }

    fun recoveryDecision(
        owned: Boolean,
        realWake: Boolean,
        effectivelySleeping: Boolean?,
        previous: Boolean,
        manageEnabled: Boolean?,
        currentlyEnabled: Boolean?,
        externalPowerConnected: Boolean? = null
    ): BatterySaverRecoveryDecision =
        when {
            !owned ->
                BatterySaverRecoveryDecision.NOTHING
            realWake ->
                BatterySaverRecoveryDecision.RESTORE_PREVIOUS
            effectivelySleeping != true ->
                BatterySaverRecoveryDecision.NOTHING
            externalPowerConnected == true ->
                BatterySaverRecoveryDecision.RESTORE_PREVIOUS
            previous ->
                BatterySaverRecoveryDecision.NOTHING
            manageEnabled != true ->
                BatterySaverRecoveryDecision.NOTHING
            currentlyEnabled == false ->
                BatterySaverRecoveryDecision.REAPPLY_SLEEP_STATE
            else ->
                BatterySaverRecoveryDecision.NOTHING
        }

    fun powerEventDecision(
        manageEnabled: Boolean,
        controlSupported: Boolean,
        effectivelySleeping: Boolean,
        externalPowerConnected: Boolean,
        deferredForExternalPower: Boolean,
        owned: Boolean,
        previous: Boolean,
        currentlyEnabled: Boolean
    ): BatterySaverPowerEventDecision =
        when {
            !controlSupported || !effectivelySleeping ->
                BatterySaverPowerEventDecision.NOTHING
            externalPowerConnected && owned ->
                BatterySaverPowerEventDecision.RESTORE_PREVIOUS
            externalPowerConnected ->
                BatterySaverPowerEventDecision.NOTHING
            !deferredForExternalPower ->
                BatterySaverPowerEventDecision.NOTHING
            shouldEnableForSleep(
                manageEnabled = manageEnabled,
                controlSupported = true,
                owned = owned,
                currentlyEnabled = currentlyEnabled,
                externalPowerConnected = false
            ) ->
                BatterySaverPowerEventDecision.ENABLE_SLEEP_STATE
            else ->
                BatterySaverPowerEventDecision.NOTHING
        }

    fun shouldTemporarilyRestoreForMaintenance(
        owned: Boolean,
        previous: Boolean,
        current: Boolean
    ): Boolean =
        owned &&
            !previous &&
            current

    fun shouldReapplyAfterMaintenance(
        owned: Boolean,
        previous: Boolean,
        isRealWake: Boolean,
        current: Boolean
    ): Boolean =
        owned &&
            !previous &&
            !isRealWake &&
            !current
}
