package com.med.sleepmanager.rules

enum class ChargingSeparationOwnedApplyDecision {
    ALREADY_DISABLED,
    REAPPLY_DISABLED,
    CANNOT_CONFIRM_DISABLED
}

enum class ChargingSeparationRestoreDecision {
    NOTHING_TO_RESTORE,
    CLEAR_OWNERSHIP,
    RESTORE_PREVIOUS
}

enum class ChargingSeparationRecoveryDecision {
    NOTHING,
    RESTORE_PREVIOUS,
    ALREADY_DISABLED,
    REAPPLY_DISABLED
}

object ChargingSeparationPolicy {
    fun ownedApplyDecision(
        previous: Boolean,
        current: Boolean?
    ): ChargingSeparationOwnedApplyDecision =
        when {
            current == false ->
                ChargingSeparationOwnedApplyDecision.ALREADY_DISABLED
            previous ->
                ChargingSeparationOwnedApplyDecision.REAPPLY_DISABLED
            else ->
                ChargingSeparationOwnedApplyDecision.CANNOT_CONFIRM_DISABLED
        }

    fun shouldTakeOwnershipAndDisable(
        current: Boolean?
    ): Boolean =
        current == true

    fun restoreDecision(
        owned: Boolean,
        previous: Boolean,
        current: Boolean?
    ): ChargingSeparationRestoreDecision =
        when {
            !owned ->
                ChargingSeparationRestoreDecision.NOTHING_TO_RESTORE
            current == previous ->
                ChargingSeparationRestoreDecision.CLEAR_OWNERSHIP
            else ->
                ChargingSeparationRestoreDecision.RESTORE_PREVIOUS
        }

    fun recoveryDecision(
        owned: Boolean,
        shouldRemainDisabled: Boolean,
        current: Boolean?
    ): ChargingSeparationRecoveryDecision =
        when {
            !owned ->
                ChargingSeparationRecoveryDecision.NOTHING
            !shouldRemainDisabled ->
                ChargingSeparationRecoveryDecision.RESTORE_PREVIOUS
            current == false ->
                ChargingSeparationRecoveryDecision.ALREADY_DISABLED
            else ->
                ChargingSeparationRecoveryDecision.REAPPLY_DISABLED
        }

    fun shouldRemainDisabledDuringRecovery(
        manageWithLid: Boolean,
        lidSupported: Boolean,
        lidClosed: Boolean,
        externalDisplayConnected: Boolean
    ): Boolean =
        manageWithLid &&
            lidSupported &&
            lidClosed &&
            !externalDisplayConnected
}
