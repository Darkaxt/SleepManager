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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HelperVersionCompatibilityTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetState() {
        assumeTrue(
            "Helper compatibility test is opt-in",
            compatibilityEnabled()
        )
        stopMainService()
        HelperController.forgetPendingState(context)
        SystemClock.sleep(250L)
        SleepCycleStore.clear(context)
        DiagnosticsCycleStore.clear(context)
        EventHistoryStore.clear(context)
    }

    @After
    fun cleanup() {
        if (!compatibilityEnabled()) return
        stopMainService()
        HelperController.forgetPendingState(context)
        SystemClock.sleep(250L)
        SleepCycleStore.clear(context)
        DiagnosticsCycleStore.clear(context)
        EventHistoryStore.clear(context)
    }

    @Test
    fun legacyHelper111_realRoundTripWithoutCycleId_isAcceptedByMain() {
        assertEquals(
            InstrumentationRegistry.getArguments().getString("expectedStableHelperVersion", "1.1.1"),
            helperVersionName()
        )
        runRealRoundTrip(expectCycleId = false)
    }

    @Test
    fun helper112_realRoundTripEchoesCycleId_andCompletesMain() {
        assertEquals(
            InstrumentationRegistry.getArguments().getString("expectedCurrentHelperVersion", "1.1.2"),
            helperVersionName()
        )
        runRealRoundTrip(expectCycleId = true)
    }

    private fun runRealRoundTrip(expectCycleId: Boolean) {
        AppPreferences.setSetupComplete(context, true)
        AppPreferences.setEnabled(context, true)
        startMainServiceAndWaitUntilSettled()

        val results = LinkedBlockingQueue<Intent>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    if (intent?.action == HelperController.ACTION_RESULT) {
                        results.offer(Intent(intent))
                    }
                }
            }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(HelperController.ACTION_RESULT),
            HelperController.PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )

        try {
            DiagnosticsCycleStore.begin(context)
            val cycle =
                SleepCycleStore.begin(
                    context = context,
                    helperExpected = true,
                    wifiManaged = false,
                    bluetoothManaged = false
                )
            SleepCycleStore.markHelperSleepRequested(context)

            assertTrue(
                HelperController.sendSleep(
                    context = context,
                    wifi = false,
                    bluetooth = false,
                    cycleId = cycle.cycleId
                )
            )

            val sleepResult = awaitPhase(results, HelperController.PHASE_SLEEP)
            assertEquals(
                HelperController.STATUS_OK,
                sleepResult.getStringExtra(HelperController.EXTRA_STATUS)
            )
            assertCycleCorrelation(sleepResult, cycle.cycleId, expectCycleId)

            assertTrue(
                "Main transaction did not remain active after Helper sleep result",
                waitUntil(timeoutMs = 1_500L) {
                    val current = SleepCycleStore.current(context)
                    current.active &&
                        current.cycleId == cycle.cycleId &&
                        !current.helperRestored
                }
            )

            assertTrue(HelperController.restoreNow(context, cycle.cycleId))
            val wakeResult = awaitPhase(results, HelperController.PHASE_WAKE)
            assertEquals(
                HelperController.STATUS_OK,
                wakeResult.getStringExtra(HelperController.EXTRA_STATUS)
            )
            assertTrue(
                wakeResult.getBooleanExtra(
                    HelperController.EXTRA_RESTORE_SUCCESS,
                    false
                )
            )
            assertCycleCorrelation(wakeResult, cycle.cycleId, expectCycleId)

            assertTrue(
                "Main did not accept the real Helper wake result",
                waitUntil(timeoutMs = 1_500L) {
                    !SleepCycleStore.isActive(context)
                }
            )
            assertNull(SleepCycleStore.restoreProblem(context))
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun assertCycleCorrelation(
        result: Intent,
        cycleId: Long,
        expectCycleId: Boolean
    ) {
        if (expectCycleId) {
            assertTrue("Helper 1.1.2 result did not include cycleId", result.hasExtra(HelperController.EXTRA_CYCLE_ID))
            assertEquals(
                cycleId,
                result.getLongExtra(HelperController.EXTRA_CYCLE_ID, -1L)
            )
        } else {
            assertFalse("Helper 1.1.1 unexpectedly returned cycleId", result.hasExtra(HelperController.EXTRA_CYCLE_ID))
        }
    }

    private fun awaitPhase(
        queue: LinkedBlockingQueue<Intent>,
        phase: String
    ): Intent {
        val deadline = SystemClock.elapsedRealtime() + 2_500L
        while (SystemClock.elapsedRealtime() < deadline) {
            val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
            val result = queue.poll(remaining, TimeUnit.MILLISECONDS) ?: break
            if (result.getStringExtra(HelperController.EXTRA_PHASE) == phase) {
                return result
            }
        }
        error("Timed out waiting for Helper phase=$phase")
    }

    private fun compatibilityEnabled(): Boolean =
        InstrumentationRegistry.getArguments()
            .getString("helperCompatibility") == "true"

    @Suppress("DEPRECATION")
    private fun helperVersionName(): String? =
        context.packageManager
            .getPackageInfo(HelperController.PACKAGE, 0)
            .versionName

    private fun startMainServiceAndWaitUntilSettled() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, SleepManagerService::class.java)
        )
        assertTrue(
            "SleepManager service did not start",
            waitUntil(timeoutMs = 3_000L) { SleepManagerService.running }
        )
        SystemClock.sleep(350L)
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

    private fun stopMainService() {
        AppPreferences.setEnabled(context, false)
        context.stopService(Intent(context, SleepManagerService::class.java))
        val deadline = SystemClock.elapsedRealtime() + 1_000L
        while (SleepManagerService.running && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(25L)
        }
    }
}
