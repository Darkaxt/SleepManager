package com.med.sleepmanager

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.BatterySleepStore
import com.med.sleepmanager.data.DiagnosticsCycleStore
import com.med.sleepmanager.data.SleepCycleStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsCycleStoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetStore() {
        DiagnosticsCycleStore.clear(context)
        AppPreferences.setAdvancedDiagnosticsEnabled(context, true)
    }

    @After
    fun cleanup() {
        DiagnosticsCycleStore.clear(context)
        AppPreferences.setAdvancedDiagnosticsEnabled(context, false)
    }

    @Test
    fun cycleKeepsStructuredSleepWakeAndRestoreHistory() {
        val record = DiagnosticsCycleStore.begin(context)
        val transactionId = record.sessionId + 100L

        DiagnosticsCycleStore.attachTransaction(
            context,
            SleepCycleStore.Snapshot(
                active = true,
                cycleId = transactionId,
                startedAt = record.startedAt,
                helperExpected = true,
                helperSleepRequested = false,
                helperRestored = false,
                wifiManaged = true,
                bluetoothManaged = true
            )
        )
        DiagnosticsCycleStore.markHelperSleepRequested(context)
        DiagnosticsCycleStore.recordConnectorOwnership(
            context,
            connectorId = "syncthing",
            restoreTokenPresent = true
        )
        DiagnosticsCycleStore.recordFalseWake(context)
        DiagnosticsCycleStore.captureSystemSnapshot(
            context = context,
            phase = DiagnosticsCycleStore.PHASE_FALSE_WAKE,
            includeDetailedProcessMemory = false
        )
        DiagnosticsCycleStore.recordEvent(context, "False wake suppressed")
        DiagnosticsCycleStore.clearConnectorOwnership(context, "syncthing")
        DiagnosticsCycleStore.markHelperRestored(context)

        val battery =
            BatterySleepStore.SleepSession(
                startedAt = record.startedAt,
                endedAt = record.startedAt + 60_000L,
                startPercent = 80,
                endPercent = 79,
                drainPercent = 1,
                durationMs = 60_000L,
                chargedDuringSleep = false,
                drainMah = 25.0,
                deepSleepMs = 50_000L,
                preciseDrainPercent = 0.4,
                falseWakeCount = 1
            )

        DiagnosticsCycleStore.markWake(
            context = context,
            sleepSession = battery,
            restorationPending = true
        )
        DiagnosticsCycleStore.markRestorationComplete(
            context,
            transactionCycleId = transactionId
        )

        val saved = DiagnosticsCycleStore.diagnosticHistory(context, 1).single()
        assertEquals(DiagnosticsCycleStore.STATUS_COMPLETE, saved.status)
        assertEquals(transactionId, saved.transactionCycleId)
        assertTrue(saved.helperExpected)
        assertTrue(saved.helperSleepRequested)
        assertTrue(saved.helperRestored)
        assertEquals(1, saved.falseWakeCount)
        assertNotNull(saved.battery)
        assertEquals(1, saved.battery?.falseWakeCount)
        assertEquals(1, saved.connectors.size)
        assertFalse(saved.connectors.single().pending)
        assertTrue(saved.connectors.single().restoreTokenPresent)
        assertTrue(saved.events.any { it.message == "False wake suppressed" })
        assertEquals(1, saved.systemSnapshots.size)
        assertEquals(
            DiagnosticsCycleStore.PHASE_FALSE_WAKE,
            saved.systemSnapshots.single().phase
        )
        assertTrue(saved.systemSnapshots.single().totalRamBytes != null)
        assertTrue(saved.systemSnapshots.single().captureDurationMs >= 0L)
        assertTrue(saved.systemSnapshots.single().processCpuTimeMs >= 0L)
    }

    @Test
    fun keepsOnlyThirtyMostRecentCycles() {
        repeat(DiagnosticsCycleStore.MAX_STORED_CYCLES + 5) {
            DiagnosticsCycleStore.begin(context)
            DiagnosticsCycleStore.markWake(
                context = context,
                sleepSession = null,
                restorationPending = false
            )
            Thread.sleep(2L)
        }

        assertEquals(
            DiagnosticsCycleStore.MAX_STORED_CYCLES,
            DiagnosticsCycleStore.storedCount(context)
        )
    }

    @Test
    fun screenOffDuringSameSleepingCycleResumesExistingRecord() {
        val first = DiagnosticsCycleStore.begin(context)
        val second = DiagnosticsCycleStore.begin(context)

        assertEquals(first.sessionId, second.sessionId)
        assertEquals(1, DiagnosticsCycleStore.storedCount(context))
    }
}
