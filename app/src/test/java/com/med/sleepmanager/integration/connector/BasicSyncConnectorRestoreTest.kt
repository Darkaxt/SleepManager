package com.med.sleepmanager.integration.connector

import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.BasicSyncController.Mode
import com.med.sleepmanager.integration.BasicSyncController.RunState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicSyncConnectorRestoreTest {
    @Test
    fun autoModeIsConfirmedEvenWhileNetworkIsTemporarilyDisconnected() {
        val state =
            BasicSyncController.RemoteState(
                mode = Mode.AUTO_MODE,
                runState = RunState.PAUSED,
                blockedReasons = setOf("DISCONNECTED")
            )

        assertTrue(
            BasicSyncConnector.restoreConfirmed(
                BasicSyncConnector.TOKEN_AUTO_MODE,
                state
            )
        )
    }

    @Test
    fun staleManualStoppedStateDoesNotConfirmAutoRestore() {
        val state =
            BasicSyncController.RemoteState(
                mode = Mode.MANUAL_MODE_STOPPED,
                runState = RunState.PAUSED
            )

        assertFalse(
            BasicSyncConnector.restoreConfirmed(
                BasicSyncConnector.TOKEN_AUTO_MODE,
                state
            )
        )
    }

    @Test
    fun manualStopRequiresAnActuallyStoppedRunState() {
        val stopping =
            BasicSyncController.RemoteState(
                mode = Mode.MANUAL_MODE_STOPPED,
                runState = RunState.STOPPING
            )
        val paused =
            stopping.copy(runState = RunState.PAUSED)

        assertFalse(
            BasicSyncConnector.restoreConfirmed(
                BasicSyncConnector.TOKEN_MANUAL_MODE_STOPPED,
                stopping
            )
        )
        assertTrue(
            BasicSyncConnector.restoreConfirmed(
                BasicSyncConnector.TOKEN_MANUAL_MODE_STOPPED,
                paused
            )
        )
    }
}
