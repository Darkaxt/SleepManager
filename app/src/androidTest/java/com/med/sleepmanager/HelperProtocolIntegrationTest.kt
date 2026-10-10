package com.med.sleepmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.DiagnosticsCycleStore
import com.med.sleepmanager.data.EventHistoryStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.service.SleepManagerService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HelperProtocolIntegrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetState() {
        stopMainService()
        HelperController.forgetPendingState(context)
        SystemClock.sleep(250L)
        SleepCycleStore.clear(context)
        DiagnosticsCycleStore.clear(context)
        EventHistoryStore.clear(context)
    }

    @After
    fun cleanup() {
        stopMainService()
        HelperController.forgetPendingState(context)
        SystemClock.sleep(250L)
        SleepCycleStore.clear(context)
        DiagnosticsCycleStore.clear(context)
        EventHistoryStore.clear(context)
    }

    @Test
    fun helperReceiver_rejectsOlderMismatchedCycles_andKeepsDuplicatesIdempotent() {
        assertTrue(
            "Helper APK must be installed for protocol integration",
            HelperController.isInstalled(context)
        )

        val results = LinkedBlockingQueue<Intent>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    if (intent?.action == HelperController.resultAction(context)) {
                        results.offer(Intent(intent))
                    }
                }
            }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(HelperController.resultAction(context)),
            HelperController.responsePermission(context),
            null,
            ContextCompat.RECEIVER_EXPORTED
        )

        try {
            val cycleId = 7_001L
            val otherCycleId = 7_000L

            assertTrue(
                HelperController.sendSleep(
                    context = context,
                    wifi = false,
                    bluetooth = false,
                    cycleId = cycleId
                )
            )
            assertResult(
                result = awaitResult(results),
                phase = HelperController.PHASE_SLEEP,
                cycleId = cycleId,
                status = HelperController.STATUS_OK
            )

            assertTrue(
                HelperController.sendSleep(
                    context = context,
                    wifi = false,
                    bluetooth = false,
                    cycleId = cycleId
                )
            )
            assertResult(
                result = awaitResult(results),
                phase = HelperController.PHASE_SLEEP,
                cycleId = cycleId,
                status = HelperController.STATUS_ALREADY_SLEEPING
            )

            assertTrue(
                HelperController.sendSleep(
                    context = context,
                    wifi = false,
                    bluetooth = false,
                    cycleId = otherCycleId
                )
            )
            assertResult(
                result = awaitResult(results),
                phase = HelperController.PHASE_SLEEP,
                cycleId = otherCycleId,
                status = HelperController.STATUS_CYCLE_MISMATCH
            )

            assertTrue(HelperController.restoreNow(context, otherCycleId))
            assertResult(
                result = awaitResult(results),
                phase = HelperController.PHASE_WAKE,
                cycleId = otherCycleId,
                status = HelperController.STATUS_CYCLE_MISMATCH,
                restoreSuccess = false
            )

            assertTrue(HelperController.restoreNow(context, cycleId))
            assertResult(
                result = awaitResult(results),
                phase = HelperController.PHASE_WAKE,
                cycleId = cycleId,
                status = HelperController.STATUS_OK,
                restoreSuccess = true
            )

            assertTrue(HelperController.restoreNow(context, cycleId))
            assertResult(
                result = awaitResult(results),
                phase = HelperController.PHASE_WAKE,
                cycleId = cycleId,
                status = HelperController.STATUS_ALREADY_RESTORED,
                restoreSuccess = true
            )
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun mainReceiver_ignoresDelayedOldCycle_andDuplicateCompletedResult() {
        AppPreferences.setSetupComplete(context, true)
        AppPreferences.setEnabled(context, true)

        startMainServiceAndWaitUntilSettled()

        DiagnosticsCycleStore.begin(context)
        val cycle =
            SleepCycleStore.begin(
                context = context,
                helperExpected = true,
                wifiManaged = false,
                bluetoothManaged = false
            )
        SleepCycleStore.markHelperSleepRequested(context)

        val oldCycleId = cycle.cycleId - 1L
        sendHelperResult(
            phase = HelperController.PHASE_WAKE,
            cycleId = oldCycleId,
            restoreSuccess = true,
            status = HelperController.STATUS_OK
        )

        assertTrue(
            "Delayed old-cycle result mutated the active transaction",
            waitUntil(timeoutMs = 1_500L) {
                val current = SleepCycleStore.current(context)
                current.active &&
                    current.cycleId == cycle.cycleId &&
                    !current.helperRestored
            }
        )
        assertNull(SleepCycleStore.restoreProblem(context))
        assertTrue(
            "Stale old-cycle result was not observed by the Main receiver",
            waitUntil(timeoutMs = 1_500L) {
                EventHistoryStore.diagnosticHistory(context)
                    .any { it.message.contains("stale cycle $oldCycleId") }
            }
        )

        sendHelperResult(
            phase = HelperController.PHASE_WAKE,
            cycleId = cycle.cycleId,
            restoreSuccess = true,
            status = HelperController.STATUS_OK
        )
        assertTrue(
            "Matching Helper result did not complete the transaction",
            waitUntil(timeoutMs = 1_500L) {
                !SleepCycleStore.isActive(context)
            }
        )

        sendHelperResult(
            phase = HelperController.PHASE_WAKE,
            cycleId = cycle.cycleId,
            restoreSuccess = true,
            status = HelperController.STATUS_ALREADY_RESTORED
        )
        SystemClock.sleep(150L)

        assertFalse(SleepCycleStore.isActive(context))
        assertEquals(0L, SleepCycleStore.current(context).cycleId)
        assertTrue(
            "Duplicate completed-cycle result was not rejected as stale",
            waitUntil(timeoutMs = 1_500L) {
                EventHistoryStore.diagnosticHistory(context)
                    .any { it.message.contains("stale cycle " + cycle.cycleId) }
            }
        )
    }

    @Test
    fun mainReceiver_acceptsLegacyWakeResultWithoutCycleId() {
        AppPreferences.setSetupComplete(context, true)
        AppPreferences.setEnabled(context, true)

        startMainServiceAndWaitUntilSettled()

        DiagnosticsCycleStore.begin(context)
        SleepCycleStore.begin(
            context = context,
            helperExpected = true,
            wifiManaged = false,
            bluetoothManaged = false
        )
        SleepCycleStore.markHelperSleepRequested(context)

        sendLegacyHelperResult(
            phase = HelperController.PHASE_WAKE,
            restoreSuccess = true,
            status = HelperController.STATUS_OK
        )

        assertTrue(
            "Legacy uncorrelated Helper result did not complete the active transaction",
            waitUntil(timeoutMs = 1_500L) {
                !SleepCycleStore.isActive(context)
            }
        )
        assertNull(SleepCycleStore.restoreProblem(context))
    }

    private fun sendLegacyHelperResult(
        phase: String,
        restoreSuccess: Boolean,
        status: String
    ) {
        context.sendBroadcast(
            Intent(HelperController.ACTION_RESULT)
                .setPackage(context.packageName)
                .putExtra(HelperController.EXTRA_PHASE, phase)
                .putExtra(HelperController.EXTRA_WIFI_MANAGED, false)
                .putExtra(HelperController.EXTRA_WIFI_CHANGED, false)
                .putExtra(HelperController.EXTRA_WIFI_ATTEMPTED, false)
                .putExtra(HelperController.EXTRA_WIFI_ACTION, "NONE")
                .putExtra(HelperController.EXTRA_WIFI_TOGGLE_SUCCESS, true)
                .putExtra(HelperController.EXTRA_AIRPLANE_MODE, false)
                .putExtra(HelperController.EXTRA_BLUETOOTH_MANAGED, false)
                .putExtra(HelperController.EXTRA_BLUETOOTH_CHANGED, false)
                .putExtra(HelperController.EXTRA_RESTORE_SUCCESS, restoreSuccess)
                .putExtra(HelperController.EXTRA_STATUS, status),
            HelperController.PERMISSION
        )
    }

    private fun sendHelperResult(
        phase: String,
        cycleId: Long,
        restoreSuccess: Boolean,
        status: String
    ) {
        context.sendBroadcast(
            Intent(HelperController.ACTION_RESULT)
                .setPackage(context.packageName)
                .putExtra(HelperController.EXTRA_PHASE, phase)
                .putExtra(HelperController.EXTRA_CYCLE_ID, cycleId)
                .putExtra(HelperController.EXTRA_WIFI_MANAGED, false)
                .putExtra(HelperController.EXTRA_WIFI_CHANGED, false)
                .putExtra(HelperController.EXTRA_WIFI_ATTEMPTED, false)
                .putExtra(HelperController.EXTRA_WIFI_ACTION, "NONE")
                .putExtra(HelperController.EXTRA_WIFI_TOGGLE_SUCCESS, true)
                .putExtra(HelperController.EXTRA_AIRPLANE_MODE, false)
                .putExtra(HelperController.EXTRA_BLUETOOTH_MANAGED, false)
                .putExtra(HelperController.EXTRA_BLUETOOTH_CHANGED, false)
                .putExtra(HelperController.EXTRA_RESTORE_SUCCESS, restoreSuccess)
                .putExtra(HelperController.EXTRA_STATUS, status),
            HelperController.PERMISSION
        )
    }

    private fun awaitResult(queue: LinkedBlockingQueue<Intent>): Intent =
        queue.poll(2, TimeUnit.SECONDS)
            ?: error("Timed out waiting for Helper ACTION_RESULT")

    private fun assertResult(
        result: Intent,
        phase: String,
        cycleId: Long,
        status: String,
        restoreSuccess: Boolean? = null
    ) {
        assertEquals(phase, result.getStringExtra(HelperController.EXTRA_PHASE))
        assertEquals(cycleId, result.getLongExtra(HelperController.EXTRA_CYCLE_ID, -1L))
        assertEquals(status, result.getStringExtra(HelperController.EXTRA_STATUS))
        restoreSuccess?.let {
            assertEquals(
                it,
                result.getBooleanExtra(
                    HelperController.EXTRA_RESTORE_SUCCESS,
                    !it
                )
            )
        }
    }

    private fun waitUntil(
        timeoutMs: Long,
        condition: () -> Boolean
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(25L)
        }
        return condition()
    }

    private fun startMainServiceAndWaitUntilSettled() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, SleepManagerService::class.java)
        )
        assertTrue(
            "SleepManager service did not start",
            waitUntil(timeoutMs = 3_000L) { SleepManagerService.running }
        )

        // running=true is set in onCreate(), before onStartCommand() finishes
        // applying the current screen state. Let that initial transition settle
        // before creating a synthetic sleep transaction for these protocol tests.
        SystemClock.sleep(350L)
    }

    private fun stopMainService() {
        AppPreferences.setEnabled(context, false)
        context.stopService(Intent(context, SleepManagerService::class.java))
        val deadline = SystemClock.elapsedRealtime() + 1_000L
        while (SleepManagerService.running && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(25L)
        }
    }
}
