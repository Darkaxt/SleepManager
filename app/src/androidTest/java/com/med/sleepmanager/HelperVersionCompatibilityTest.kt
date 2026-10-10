package com.med.sleepmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
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
        SleepCycleStore.clear(context)
        DiagnosticsCycleStore.clear(context)
        EventHistoryStore.clear(context)
    }

    @After
    fun cleanup() {
        if (!compatibilityEnabled()) return
        stopMainService()
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
    fun currentHelper_realRoundTripEchoesCycleId_andCompletesMain() {
        val expectedVersion =
            InstrumentationRegistry.getArguments()
                .getString("expectedHelperVersion")
        assertTrue(
            "expectedHelperVersion instrumentation argument is required",
            !expectedVersion.isNullOrBlank()
        )
        assertEquals(expectedVersion, helperVersionName())
        runRealRoundTrip(expectCycleId = true)
    }

    @Test
    fun currentHelper_reconcilesStaleCycleBeforeDifferentSleep() {
        val expectedVersion =
            InstrumentationRegistry.getArguments()
                .getString("expectedHelperVersion")
        assumeTrue(
            "Requires current Helper version",
            !expectedVersion.isNullOrBlank() &&
                helperVersionName() == expectedVersion
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
            val staleCycleId = 41L
            val replacementCycleId = 42L

            assertTrue(
                HelperController.sendSleep(
                    context = context,
                    wifi = false,
                    bluetooth = false,
                    cycleId = staleCycleId
                )
            )
            val firstSleep = awaitPhase(results, HelperController.PHASE_SLEEP)
            assertEquals(
                HelperController.STATUS_OK,
                firstSleep.getStringExtra(HelperController.EXTRA_STATUS)
            )
            assertEquals(
                staleCycleId,
                firstSleep.getLongExtra(HelperController.EXTRA_CYCLE_ID, -1L)
            )

            assertTrue(
                HelperController.sendSleep(
                    context = context,
                    wifi = false,
                    bluetooth = false,
                    cycleId = replacementCycleId
                )
            )
            val replacementSleep =
                awaitPhase(results, HelperController.PHASE_SLEEP)
            assertEquals(
                HelperController.STATUS_OK,
                replacementSleep.getStringExtra(HelperController.EXTRA_STATUS)
            )
            assertEquals(
                replacementCycleId,
                replacementSleep.getLongExtra(
                    HelperController.EXTRA_CYCLE_ID,
                    -1L
                )
            )

            assertTrue(
                HelperController.restoreNow(
                    context,
                    replacementCycleId
                )
            )
            val wakeResult = awaitPhase(results, HelperController.PHASE_WAKE)
            assertEquals(
                replacementCycleId,
                wakeResult.getLongExtra(HelperController.EXTRA_CYCLE_ID, -1L)
            )
            assertTrue(
                wakeResult.getBooleanExtra(
                    HelperController.EXTRA_RESTORE_SUCCESS,
                    false
                )
            )
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun currentHelper_reconcilesStaleActiveCycleOnNewerRestore() {
        val expectedVersion =
            InstrumentationRegistry.getArguments()
                .getString("expectedHelperVersion")
        assumeTrue(
            "Requires current Helper version",
            !expectedVersion.isNullOrBlank() &&
                helperVersionName() == expectedVersion
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
            val staleCycleId = 51L
            val newerMainCycleId = 52L

            assertTrue(
                HelperController.sendSleep(
                    context = context,
                    wifi = false,
                    bluetooth = false,
                    cycleId = staleCycleId
                )
            )
            val sleepResult =
                awaitPhaseForCycle(
                    results,
                    HelperController.PHASE_SLEEP,
                    staleCycleId
                )
            assertEquals(
                HelperController.STATUS_OK,
                sleepResult.getStringExtra(HelperController.EXTRA_STATUS)
            )

            assertTrue(
                HelperController.restoreNow(
                    context,
                    newerMainCycleId
                )
            )
            val wakeResult =
                awaitPhaseForCycle(
                    results,
                    HelperController.PHASE_WAKE,
                    newerMainCycleId
                )
            assertTrue(
                wakeResult.getBooleanExtra(
                    HelperController.EXTRA_RESTORE_SUCCESS,
                    false
                )
            )
            assertEquals(
                HelperController.STATUS_ALREADY_RESTORED,
                wakeResult.getStringExtra(HelperController.EXTRA_STATUS)
            )
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun runRealRoundTrip(expectCycleId: Boolean) {
        AppPreferences.setSetupComplete(context, true)
        AppPreferences.setEnabled(context, true)
        AppPreferences.setManageWifi(context, true)
        AppPreferences.setManageBluetooth(context, false)
        AppPreferences.setSleepGraceMs(context, 0L)
        AppPreferences.setCustomDelayEnabled(context, false)

        startMainServiceAndWaitUntilSettled()

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
            setScreenInteractive(false)

            val sleepResult = awaitPhase(results, HelperController.PHASE_SLEEP)
            assertEquals(
                HelperController.STATUS_OK,
                sleepResult.getStringExtra(HelperController.EXTRA_STATUS)
            )

            val cycle =
                waitForActiveHelperCycle(
                    timeoutMs = 5_000L
                )
            assertCycleCorrelation(sleepResult, cycle.cycleId, expectCycleId)

            setScreenInteractive(true)

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
                waitUntil(timeoutMs = 5_000L) {
                    !SleepCycleStore.isActive(context)
                }
            )
            assertNull(SleepCycleStore.restoreProblem(context))
        } finally {
            context.unregisterReceiver(receiver)
            setScreenInteractive(true)
        }
    }

    private fun assertCycleCorrelation(
        result: Intent,
        cycleId: Long,
        expectCycleId: Boolean
    ) {
        if (expectCycleId) {
            assertTrue("Current Helper result did not include cycleId", result.hasExtra(HelperController.EXTRA_CYCLE_ID))
            assertEquals(
                cycleId,
                result.getLongExtra(HelperController.EXTRA_CYCLE_ID, -1L)
            )
        } else {
            assertFalse("Helper 1.1.1 unexpectedly returned cycleId", result.hasExtra(HelperController.EXTRA_CYCLE_ID))
        }
    }

    private fun awaitPhaseForCycle(
        queue: LinkedBlockingQueue<Intent>,
        phase: String,
        cycleId: Long
    ): Intent {
        val deadline = SystemClock.elapsedRealtime() + 8_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val remaining =
                (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
            val result = queue.poll(remaining, TimeUnit.MILLISECONDS) ?: break
            if (
                result.getStringExtra(HelperController.EXTRA_PHASE) == phase &&
                result.getLongExtra(HelperController.EXTRA_CYCLE_ID, -1L) ==
                    cycleId
            ) {
                return result
            }
        }
        error("Timed out waiting for Helper phase=$phase cycle=$cycleId")
    }

    private fun awaitPhase(
        queue: LinkedBlockingQueue<Intent>,
        phase: String
    ): Intent {
        val deadline = SystemClock.elapsedRealtime() + 8_000L
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

    private fun setScreenInteractive(interactive: Boolean) {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val uiAutomation =
            InstrumentationRegistry.getInstrumentation().uiAutomation

        val keyCode = if (interactive) 224 else 223
        uiAutomation.executeShellCommand("input keyevent $keyCode").close()

        assertTrue(
            if (interactive) {
                "Emulator did not return to interactive state"
            } else {
                "Emulator did not reach non-interactive state"
            },
            waitUntil(timeoutMs = 8_000L) {
                powerManager.isInteractive == interactive
            }
        )
    }

    private fun waitForActiveHelperCycle(
        timeoutMs: Long
    ): SleepCycleStore.Snapshot {
        var snapshot = SleepCycleStore.current(context)
        assertTrue(
            "Main transaction did not remain active after Helper sleep result",
            waitUntil(timeoutMs) {
                snapshot = SleepCycleStore.current(context)
                snapshot.active &&
                    snapshot.helperExpected &&
                    snapshot.helperSleepRequested &&
                    !snapshot.helperRestored
            }
        )
        return snapshot
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
        SystemClock.sleep(750L)
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
