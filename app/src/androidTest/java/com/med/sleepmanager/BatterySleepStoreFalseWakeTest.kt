package com.med.sleepmanager

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.BatterySleepStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatterySleepStoreFalseWakeTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetStore() {
        clearStore()
    }

    @After
    fun cleanup() {
        clearStore()
    }

    @Test
    fun falseWakesAreRecordedInsideCurrentSleepSession() {
        BatterySleepStore.beginSession(context)

        BatterySleepStore.noteFalseWake(context)
        BatterySleepStore.noteFalseWake(context)

        val session = BatterySleepStore.finishSession(context)
        assertNotNull(session)
        assertEquals(2, session?.falseWakeCount)
    }

    @Test
    fun falseWakeOutsideSleepSessionDoesNotLeakIntoNextSession() {
        BatterySleepStore.noteFalseWake(context)

        BatterySleepStore.beginSession(context)
        val session = BatterySleepStore.finishSession(context)

        assertNotNull(session)
        assertEquals(0, session?.falseWakeCount)
    }

    private fun clearStore() {
        context.getSharedPreferences("battery_sleep_stats", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
