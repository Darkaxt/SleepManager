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
class StableMainHelperCompatibilityTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetState() {
        assumeTrue(
            "Stable Main compatibility test is opt-in",
            InstrumentationRegistry.getArguments()
                .getString("stableMainHelperCompatibility") == "true"
        )

        AppPreferences.setEnabled(context, false)
        context.stopService(Intent(context, SleepManagerService::class.java))
        waitUntil(timeoutMs = 1_500L) { !SleepManagerService.running }
        HelperController.forgetPendingState(context)
        SystemClock.sleep(250L)
        SleepCycleStore.clear(context)
    }

    @After
    fun cleanup() {
        if (
            InstrumentationRegistry.getArguments()
                .getString("stableMainHelperCompatibility") != "true"
        ) {
            return
        }

        AppPreferences.setEnabled(context, false)
        context.stopService(Intent(context, SleepManagerService::class.java))
        waitUntil(timeoutMs = 1_500L) { !SleepManagerService.running }
        HelperController.forgetPendingState(context)
        SystemClock.sleep(250L)
        SleepCycleStore.clear(context)
    }

    @Test
    fun stableMain061_acceptsHelper112CorrelatedResults() {
        assertEquals(
            InstrumentationRegistry.getArguments().getString("expectedStableMainVersion", "0.6.1"),
            packageVersionName(context.packageName)
        )
        assertEquals(
            InstrumentationRegistry.getArguments().getString("expectedCurrentHelperVersion", "1.1.2"),
            packageVersionName(HelperController.PACKAGE)
        )
        assertTrue("Helper 1.1.2 must be installed", HelperController.isInstalled(context))

        AppPreferences.setSetupComplete(context, true)
        AppPreferences.setEnabled(context, true)

        ContextCompat.startForegroundService(
            context,
            Intent(context, SleepManagerService::class.java)
        )
        assertTrue(
            "Stable SleepManager service did not start",
            waitUntil(timeoutMs = 3_000L) { SleepManagerService.running }
        )
        SystemClock.sleep(350L)

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
            assertTrue("Helper 1.1.2 sleep result is missing cycleId", sleepResult.hasExtra("cycle_id"))
            assertEquals(cycle.cycleId, sleepResult.getLongExtra("cycle_id", -1L))

            assertTrue(
                "Stable Main did not preserve the active transaction after Helper sleep",
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
            assertTrue("Helper 1.1.2 wake result is missing cycleId", wakeResult.hasExtra("cycle_id"))
            assertEquals(cycle.cycleId, wakeResult.getLongExtra("cycle_id", -1L))

            assertTrue(
                "Stable Main did not accept Helper 1.1.2 wake result",
                waitUntil(timeoutMs = 1_500L) {
                    !SleepCycleStore.isActive(context)
                }
            )
            assertNull(SleepCycleStore.restoreProblem(context))
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Suppress("DEPRECATION")
    private fun packageVersionName(packageName: String): String? =
        context.packageManager.getPackageInfo(packageName, 0).versionName

    private fun awaitPhase(
        queue: LinkedBlockingQueue<Intent>,
        phase: String
    ): Intent {
        val deadline = SystemClock.elapsedRealtime() + 2_500L
        while (SystemClock.elapsedRealtime() < deadline) {
            val remaining =
                (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
            val result = queue.poll(remaining, TimeUnit.MILLISECONDS) ?: break
            if (result.getStringExtra(HelperController.EXTRA_PHASE) == phase) {
                return result
            }
        }
        error("Timed out waiting for Helper phase=$phase")
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
}
