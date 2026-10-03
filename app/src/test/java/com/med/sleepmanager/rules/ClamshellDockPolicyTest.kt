package com.med.sleepmanager.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClamshellDockPolicyTest {
    @Test
    fun debounce_requiresPreviousConnectionAndClosedLid() {
        assertTrue(
            ClamshellDockPolicy.shouldDebounceDisconnect(
                wasConnected = true,
                lidClosed = true
            )
        )
        assertFalse(
            ClamshellDockPolicy.shouldDebounceDisconnect(
                wasConnected = false,
                lidClosed = true
            )
        )
        assertFalse(
            ClamshellDockPolicy.shouldDebounceDisconnect(
                wasConnected = true,
                lidClosed = false
            )
        )
    }

    @Test
    fun sleepingDevice_doesNotRequestAnotherSleep() {
        assertEquals(
            DockDisconnectDecision.ALREADY_ASLEEP,
            ClamshellDockPolicy.disconnectDecision(
                interactive = false,
                sleepWhenDisconnected = true
            )
        )
    }

    @Test
    fun interactiveDevice_requestsSleepWhenEnabled() {
        assertEquals(
            DockDisconnectDecision.REQUEST_SLEEP,
            ClamshellDockPolicy.disconnectDecision(
                interactive = true,
                sleepWhenDisconnected = true
            )
        )
    }

    @Test
    fun interactiveDevice_keepsAwakeWhenSleepOnDisconnectIsDisabled() {
        assertEquals(
            DockDisconnectDecision.KEEP_AWAKE,
            ClamshellDockPolicy.disconnectDecision(
                interactive = true,
                sleepWhenDisconnected = false
            )
        )
    }
}
