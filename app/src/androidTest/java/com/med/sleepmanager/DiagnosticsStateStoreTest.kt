package com.med.sleepmanager

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.DiagnosticsStateStore
import com.med.sleepmanager.data.EventHistoryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiagnosticsStateStoreTest {
    private lateinit var context: Context

    private val keys = listOf(
        "last_event",
        "last_event_time",
        "last_wifi_diagnostic_known",
        "last_wifi_diagnostic_phase",
        "last_wifi_diagnostic_action",
        "last_wifi_diagnostic_attempted",
        "last_wifi_diagnostic_success",
        "last_wifi_diagnostic_airplane",
        "last_wifi_diagnostic_time",
        "false_wake_count",
        "last_false_wake_time"
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun diagnosticsKeepLegacySharedPreferenceKeysAndEventHistoryBehavior() {
        DiagnosticsStateStore.recordEvent(context, "Diagnostic event")

        val prefs = context.getSharedPreferences("sleep_manager", Context.MODE_PRIVATE)
        assertEquals("Diagnostic event", prefs.getString("last_event", null))
        assertTrue(prefs.getLong("last_event_time", 0L) > 0L)
        assertEquals("Diagnostic event", DiagnosticsStateStore.lastEvent(context))
        assertTrue(DiagnosticsStateStore.lastEventTime(context) > 0L)
        assertEquals("Diagnostic event", EventHistoryStore.recent(context).firstOrNull()?.message)

        DiagnosticsStateStore.recordWifiToggleDiagnostic(
            context = context,
            phase = "wake",
            action = "ON",
            attempted = true,
            success = false,
            airplaneMode = true
        )

        assertTrue(prefs.getBoolean("last_wifi_diagnostic_known", false))
        assertEquals("wake", prefs.getString("last_wifi_diagnostic_phase", null))
        assertEquals("ON", prefs.getString("last_wifi_diagnostic_action", null))
        assertTrue(prefs.getBoolean("last_wifi_diagnostic_attempted", false))
        assertFalse(prefs.getBoolean("last_wifi_diagnostic_success", true))
        assertTrue(prefs.getBoolean("last_wifi_diagnostic_airplane", false))
        assertTrue(prefs.getLong("last_wifi_diagnostic_time", 0L) > 0L)

        DiagnosticsStateStore.recordFalseWake(context)
        DiagnosticsStateStore.recordFalseWake(context)
        assertEquals(2, DiagnosticsStateStore.falseWakeCount(context))
        assertTrue(DiagnosticsStateStore.lastFalseWakeTime(context) > 0L)
        assertEquals(2, prefs.getInt("false_wake_count", 0))
        assertTrue(prefs.getLong("last_false_wake_time", 0L) > 0L)

        val diagnostic = DiagnosticsStateStore.lastWifiToggleDiagnostic(context)
        assertNotNull(diagnostic)
        assertEquals("wake", diagnostic?.phase)
        assertEquals("ON", diagnostic?.action)
        assertEquals(true, diagnostic?.attempted)
        assertEquals(false, diagnostic?.success)
        assertEquals(true, diagnostic?.airplaneMode)
    }

    @Test
    fun eventHistoryConcurrentWritesDoNotLoseEvents() {
        AppPreferences.setAdvancedDiagnosticsEnabled(context, true)
        val eventCount = 24
        val ready = CountDownLatch(eventCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(eventCount)

        repeat(eventCount) { index ->
            Thread {
                ready.countDown()
                try {
                    start.await()
                    EventHistoryStore.record(context, "Concurrent event $index")
                } finally {
                    done.countDown()
                }
            }.start()
        }

        assertTrue("Concurrent writers did not become ready", ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        assertTrue("Concurrent event writes did not finish", done.await(5, TimeUnit.SECONDS))

        val messages = EventHistoryStore.diagnosticHistory(context).map { it.message }.toSet()
        repeat(eventCount) { index ->
            assertTrue(
                "Concurrent event $index was lost from diagnostic history",
                "Concurrent event $index" in messages
            )
        }
    }

    @Test
    fun standardDiagnosticsStayLightweightUntilAdvancedModeIsEnabled() {
        assertFalse(AppPreferences.advancedDiagnosticsEnabled(context))

        repeat(24) { index ->
            EventHistoryStore.record(context, "Standard event $index")
        }
        assertEquals(20, EventHistoryStore.diagnosticHistory(context).size)

        AppPreferences.setAdvancedDiagnosticsEnabled(context, true)
        repeat(24) { index ->
            EventHistoryStore.record(context, "Advanced event $index")
        }
        assertTrue(EventHistoryStore.diagnosticHistory(context).size > 20)
    }

    @Test
    fun diagnosticsDefaultsRemainUnchangedWhenNoStateExists() {
        assertEquals("No activity yet", DiagnosticsStateStore.lastEvent(context))
        assertEquals(0L, DiagnosticsStateStore.lastEventTime(context))
        assertNull(DiagnosticsStateStore.lastWifiToggleDiagnostic(context))
        assertEquals(0, DiagnosticsStateStore.falseWakeCount(context))
        assertEquals(0L, DiagnosticsStateStore.lastFalseWakeTime(context))
    }

    private fun clearState() {
        val editor =
            context.getSharedPreferences("sleep_manager", Context.MODE_PRIVATE)
                .edit()
        keys.forEach(editor::remove)
        editor.remove("advanced_diagnostics")
        editor.commit()
        EventHistoryStore.clear(context)
    }
}
