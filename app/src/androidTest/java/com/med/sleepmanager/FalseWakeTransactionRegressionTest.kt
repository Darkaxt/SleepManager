package com.med.sleepmanager

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.DiagnosticsCycleStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.device.DeviceControlStore
import com.med.sleepmanager.integration.TailscaleTargetPolicy
import com.med.sleepmanager.integration.TailscaleTransactionToken
import com.med.sleepmanager.integration.connector.BasicSyncConnector
import com.med.sleepmanager.integration.connector.JamesDspConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.connector.TailscaleConnector
import com.med.sleepmanager.rules.SleepWakePolicy
import com.med.sleepmanager.service.SleepCycleRuntimeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration-level regression for the Thor closed-lid false-wake sequence.
 *
 * It deliberately exercises the same policy + transient runtime state +
 * persistent ownership stores used by SleepManagerService:
 *
 * sleep -> ownership recorded -> suppressed false wake -> one-shot re-sleep ->
 * real wake -> Helper/connectors restored -> transaction completes.
 *
 * The key invariant is that the false wake and the forced SCREEN_OFF are not
 * allowed to start or clear a second transaction.
 */
@RunWith(AndroidJUnit4::class)
class FalseWakeTransactionRegressionTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetState() {
        SleepCycleStore.clear(context)
        DiagnosticsCycleStore.clear(context)
        AppPreferences.setAdvancedDiagnosticsEnabled(context, true)
        clearDeviceControlState()
    }

    @After
    fun cleanup() {
        SleepCycleStore.clear(context)
        DiagnosticsCycleStore.clear(context)
        AppPreferences.setAdvancedDiagnosticsEnabled(context, false)
        clearDeviceControlState()
    }

    @Test
    fun falseWakeResleep_preservesAllOwnershipUntilRealWake() {
        DiagnosticsCycleStore.begin(context)

        val runtime = SleepCycleRuntimeState().apply {
            markActionsApplied()
        }
        val initialCycle =
            SleepCycleStore.begin(
                context = context,
                helperExpected = true,
                wifiManaged = true,
                bluetoothManaged = true
            )

        SleepCycleStore.markHelperSleepRequested(context)
        SleepCycleStore.recordConnectorChange(
            context,
            SyncthingConnector.id,
            "confirmed:com.github.catfriend1.syncthingfork"
        )
        SleepCycleStore.recordConnectorChange(
            context,
            BasicSyncConnector.id,
            BasicSyncConnector.TOKEN_AUTO_MODE
        )
        SleepCycleStore.recordConnectorChange(
            context,
            TailscaleConnector.id,
            TailscaleTransactionToken.restore(TailscaleTargetPolicy.TAILDNS_PACKAGE)
        )
        SleepCycleStore.recordConnectorChange(
            context,
            JamesDspConnector.id,
            "james.dsp"
        )
        DeviceControlStore.takeBatterySaverOwnership(
            context,
            previous = false
        )
        DeviceControlStore.takeChargingSeparationOwnership(
            context,
            previous = true
        )

        val falseWake =
            SleepWakePolicy.onScreenOn(
                closedLidProtectionEnabled = true,
                lidClosed = true,
                sleepDelayPending = false
            )
        assertTrue(falseWake.suppressWake)
        assertFalse(falseWake.restoreNormalWake)

        val preserve =
            SleepWakePolicy.shouldPreserveSleepTransactionOnFalseWake(
                suppressWake = falseWake.suppressWake,
                sleepDelayPending = false,
                cycleActive = SleepCycleStore.isActive(context),
                actionsApplied = runtime.actionsApplied,
                stopWaitPending = false
            )
        assertTrue(preserve)
        runtime.markFalseWakeResleepPending()

        DiagnosticsCycleStore.recordFalseWake(context)
        DiagnosticsCycleStore.recordEvent(
            context,
            "Test false wake suppressed"
        )

        // This models the forced SCREEN_OFF early-return in onScreenOff().
        // If the one-shot flag is consumed, no new SleepCycleStore.begin() is
        // executed and the original ownership must remain untouched.
        assertTrue(runtime.consumeFalseWakeResleepPending())
        assertFalse(runtime.falseWakeResleepPending)

        val afterResleep = SleepCycleStore.current(context)
        assertEquals(initialCycle.cycleId, afterResleep.cycleId)
        assertTrue(afterResleep.active)
        assertTrue(afterResleep.helperExpected)
        assertTrue(afterResleep.helperSleepRequested)
        assertFalse(afterResleep.helperRestored)
        assertTrue(afterResleep.wifiManaged)
        assertTrue(afterResleep.bluetoothManaged)

        assertConnectorPending(
            SyncthingConnector.id,
            "confirmed:com.github.catfriend1.syncthingfork"
        )
        assertConnectorPending(
            BasicSyncConnector.id,
            BasicSyncConnector.TOKEN_AUTO_MODE
        )
        assertConnectorPending(
            TailscaleConnector.id,
            TailscaleTransactionToken.restore(TailscaleTargetPolicy.TAILDNS_PACKAGE)
        )
        assertConnectorPending(
            JamesDspConnector.id,
            "james.dsp"
        )
        assertTrue(DeviceControlStore.batterySaver(context).owned)
        assertFalse(DeviceControlStore.batterySaver(context).previous)
        assertTrue(DeviceControlStore.chargingSeparation(context).owned)
        assertTrue(DeviceControlStore.chargingSeparation(context).previous)

        val diagnosticCycle =
            DiagnosticsCycleStore.diagnosticHistory(context, 1).single()
        assertEquals(initialCycle.cycleId, diagnosticCycle.transactionCycleId)
        assertEquals(1, diagnosticCycle.falseWakeCount)
        assertEquals(4, diagnosticCycle.connectors.count { it.pending })

        // Real wake: restoration is now allowed.
        val realWake =
            SleepWakePolicy.onScreenOn(
                closedLidProtectionEnabled = true,
                lidClosed = false,
                sleepDelayPending = false
            )
        assertFalse(realWake.suppressWake)
        assertTrue(realWake.restoreNormalWake)

        DiagnosticsCycleStore.markWake(
            context = context,
            sleepSession = null,
            restorationPending = true
        )
        assertEquals(
            DiagnosticsCycleStore.STATUS_AWAKE,
            DiagnosticsCycleStore.diagnosticHistory(context, 1).single().status
        )

        // Completing only the Helper is not enough while connector ownership
        // remains. The persistent transaction must stay active.
        SleepCycleStore.markHelperRestored(context)
        assertFalse(SleepCycleStore.completeIfRestored(context))
        assertTrue(SleepCycleStore.isActive(context))

        SleepCycleStore.clearConnectorChange(context, SyncthingConnector.id)
        SleepCycleStore.clearConnectorChange(context, BasicSyncConnector.id)
        SleepCycleStore.clearConnectorChange(context, TailscaleConnector.id)
        assertFalse(SleepCycleStore.completeIfRestored(context))
        assertTrue(SleepCycleStore.isActive(context))

        SleepCycleStore.clearConnectorChange(context, JamesDspConnector.id)
        DeviceControlStore.clearBatterySaverOwnership(context)
        DeviceControlStore.clearChargingSeparationOwnership(context)

        assertTrue(SleepCycleStore.completeIfRestored(context))
        assertFalse(SleepCycleStore.isActive(context))

        assertFalse(DeviceControlStore.batterySaver(context).owned)
        assertFalse(DeviceControlStore.chargingSeparation(context).owned)

        val completed =
            DiagnosticsCycleStore.diagnosticHistory(context, 1).single()
        assertEquals(
            DiagnosticsCycleStore.STATUS_COMPLETE,
            completed.status
        )
        assertEquals(initialCycle.cycleId, completed.transactionCycleId)
        assertTrue(completed.helperRestored)
        assertEquals(4, completed.connectors.size)
        assertTrue(completed.connectors.all { !it.pending })
        assertEquals(1, completed.falseWakeCount)
    }

    private fun assertConnectorPending(
        connectorId: String,
        expectedToken: String
    ) {
        val change =
            SleepCycleStore.connectorChange(context, connectorId)
        assertNotNull(change)
        assertEquals(expectedToken, change?.restoreToken)
        assertTrue(SleepCycleStore.hasConnectorChange(context, connectorId))
    }

    private fun clearDeviceControlState() {
        context.getSharedPreferences(
            "device_control_state",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }
}
