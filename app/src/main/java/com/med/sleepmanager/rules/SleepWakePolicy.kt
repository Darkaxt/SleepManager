package com.med.sleepmanager.rules

object SleepWakePolicy {
    data class WakeDecision(
        val suppressWake: Boolean,
        val cancelSleepDelay: Boolean,
        val restoreNormalWake: Boolean,
        val requestClosedLidLock: Boolean
    )

    fun onScreenOn(
        closedLidProtectionEnabled: Boolean,
        lidClosed: Boolean,
        sleepDelayPending: Boolean
    ): WakeDecision {
        val suppress = closedLidProtectionEnabled && lidClosed
        return WakeDecision(
            suppressWake = suppress,
            cancelSleepDelay = !suppress && sleepDelayPending,
            restoreNormalWake = !suppress,
            requestClosedLidLock = suppress
        )
    }

    fun isSuppressedClosedLidFalseWake(
        interactive: Boolean,
        closedLidProtectionEnabled: Boolean,
        lidClosed: Boolean,
        bypassClosedLidProtection: Boolean
    ): Boolean =
        interactive &&
            closedLidProtectionEnabled &&
            lidClosed &&
            !bypassClosedLidProtection

    fun isEffectivelySleeping(
        interactive: Boolean,
        closedLidProtectionEnabled: Boolean,
        lidClosed: Boolean,
        bypassClosedLidProtection: Boolean
    ): Boolean =
        !interactive ||
            isSuppressedClosedLidFalseWake(
                interactive = interactive,
                closedLidProtectionEnabled = closedLidProtectionEnabled,
                lidClosed = lidClosed,
                bypassClosedLidProtection = bypassClosedLidProtection
            )

    fun isRealWake(
        interactive: Boolean,
        closedLidProtectionEnabled: Boolean,
        lidClosed: Boolean,
        bypassClosedLidProtection: Boolean
    ): Boolean =
        interactive &&
            !isSuppressedClosedLidFalseWake(
                interactive = interactive,
                closedLidProtectionEnabled = closedLidProtectionEnabled,
                lidClosed = lidClosed,
                bypassClosedLidProtection = bypassClosedLidProtection
            )

    /**
     * A suppressed closed-lid false wake must not restart or recover an
     * already-running sleep transaction when the forced re-sleep produces a
     * second SCREEN_OFF. Preserve the original ownership until a real wake.
     */
    fun shouldPreserveSleepTransactionOnFalseWake(
        suppressWake: Boolean,
        sleepDelayPending: Boolean,
        cycleActive: Boolean,
        actionsApplied: Boolean,
        stopWaitPending: Boolean
    ): Boolean =
        suppressWake &&
            (
                sleepDelayPending ||
                    cycleActive ||
                    actionsApplied ||
                    stopWaitPending
            )

    /**
     * STOP-capable integrations must settle before SleepManager applies an
     * action that can make their runtime state harder to verify.
     *
     * Wi-Fi only counts when the Helper is available to actually turn it off.
     * Battery Saver counts independently because it can change app run
     * conditions even when SleepManager leaves Wi-Fi untouched.
     */
    fun shouldWaitForManagedStopsBeforeDisruptiveSleepAction(
        wifiManaged: Boolean,
        helperAvailable: Boolean,
        batterySaverWillEnable: Boolean,
        syncthingStopRequested: Boolean,
        basicSyncStopRequested: Boolean
    ): Boolean {
        val disruptiveActionPending =
            (wifiManaged && helperAvailable) || batterySaverWillEnable
        val managedStopPending =
            syncthingStopRequested || basicSyncStopRequested

        return disruptiveActionPending && managedStopPending
    }

    /**
     * Preserve the existing Helper ordering for Tailscale, while also making
     * Battery Saver wait for a pending VPN disconnect verification.
     */
    fun shouldWaitForTailscaleBeforeDisruptiveSleepAction(
        helperAvailable: Boolean,
        batterySaverWillEnable: Boolean,
        tailscaleVerificationPending: Boolean
    ): Boolean =
        tailscaleVerificationPending &&
            (helperAvailable || batterySaverWillEnable)
}
