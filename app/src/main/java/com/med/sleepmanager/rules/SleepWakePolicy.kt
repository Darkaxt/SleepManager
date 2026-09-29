package com.med.sleepmanager.rules

object SleepWakePolicy {
    data class WakeDecision(
        val suppressWake: Boolean,
        val cancelSleepDelay: Boolean,
        val restoreNormalWake: Boolean,
        val requestThorLock: Boolean
    )

    fun onScreenOn(
        thorProtectionEnabled: Boolean,
        lidClosed: Boolean,
        sleepDelayPending: Boolean
    ): WakeDecision {
        val suppress = thorProtectionEnabled && lidClosed
        return WakeDecision(
            suppressWake = suppress,
            cancelSleepDelay = !suppress && sleepDelayPending,
            restoreNormalWake = !suppress,
            requestThorLock = suppress
        )
    }

    fun isSuppressedThorFalseWake(
        interactive: Boolean,
        thorProtectionEnabled: Boolean,
        lidClosed: Boolean,
        bypassClosedLidProtection: Boolean
    ): Boolean =
        interactive &&
            thorProtectionEnabled &&
            lidClosed &&
            !bypassClosedLidProtection

    fun isEffectivelySleeping(
        interactive: Boolean,
        thorProtectionEnabled: Boolean,
        lidClosed: Boolean,
        bypassClosedLidProtection: Boolean
    ): Boolean =
        !interactive ||
            isSuppressedThorFalseWake(
                interactive = interactive,
                thorProtectionEnabled = thorProtectionEnabled,
                lidClosed = lidClosed,
                bypassClosedLidProtection = bypassClosedLidProtection
            )

    fun isRealWake(
        interactive: Boolean,
        thorProtectionEnabled: Boolean,
        lidClosed: Boolean,
        bypassClosedLidProtection: Boolean
    ): Boolean =
        interactive &&
            !isSuppressedThorFalseWake(
                interactive = interactive,
                thorProtectionEnabled = thorProtectionEnabled,
                lidClosed = lidClosed,
                bypassClosedLidProtection = bypassClosedLidProtection
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
