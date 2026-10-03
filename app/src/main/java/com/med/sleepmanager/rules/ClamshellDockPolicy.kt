package com.med.sleepmanager.rules

enum class DockDisconnectDecision {
    ALREADY_ASLEEP,
    REQUEST_SLEEP,
    KEEP_AWAKE
}

object ClamshellDockPolicy {
    fun shouldDebounceDisconnect(
        wasConnected: Boolean,
        lidClosed: Boolean
    ): Boolean =
        wasConnected && lidClosed

    fun disconnectDecision(
        interactive: Boolean,
        sleepWhenDisconnected: Boolean
    ): DockDisconnectDecision =
        when {
            !interactive -> DockDisconnectDecision.ALREADY_ASLEEP
            sleepWhenDisconnected -> DockDisconnectDecision.REQUEST_SLEEP
            else -> DockDisconnectDecision.KEEP_AWAKE
        }
}
