package com.med.sleepmanager.api

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.service.SleepManagerService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepManagerStatusProviderTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun readStatus(): JSONObject {
        val raw = context.contentResolver.call(
            SleepManagerStatusProvider.URI,
            SleepManagerStatusProvider.METHOD_STATUS,
            null,
            null
        )?.getString(SleepManagerStatusProvider.RESULT_STATUS)
        assertNotNull("Public status response is required", raw)
        return JSONObject(raw!!)
    }

    @Test
    fun returnsVersionedReadOnlyEnabledAndRealServiceState() {
        val wasEnabled = AppPreferences.isEnabled(context)
        try {
            AppPreferences.setEnabled(context, false)
            val disabled = readStatus()
            assertEquals(1, disabled.getInt("apiVersion"))
            assertFalse(disabled.getBoolean("enabled"))
            assertEquals(SleepManagerService.running, disabled.getBoolean("serviceRunning"))
            assertEquals(setOf("apiVersion", "enabled", "serviceRunning"),
                disabled.keys().asSequence().toSet())

            AppPreferences.setEnabled(context, true)
            assertTrue(readStatus().getBoolean("enabled"))
        } finally {
            AppPreferences.setEnabled(context, wasEnabled)
        }
    }

    @Test
    fun enabledStateChangeNotifiesContentObserver() {
        val old = AppPreferences.isEnabled(context)
        val latch = CountDownLatch(1)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                latch.countDown()
            }
        }
        context.contentResolver.registerContentObserver(
            SleepManagerStatusProvider.URI, false, observer
        )
        try {
            AppPreferences.setEnabled(context, !old)
            assertTrue("Status observer never received enabled change",
                latch.await(5, TimeUnit.SECONDS))
            assertEquals(!old, readStatus().getBoolean("enabled"))
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
            AppPreferences.setEnabled(context, old)
        }
    }
}
