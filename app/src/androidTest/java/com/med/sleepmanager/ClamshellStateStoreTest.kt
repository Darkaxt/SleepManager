package com.med.sleepmanager

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.ClamshellStateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClamshellStateStoreTest {
    private lateinit var context: Context

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
    fun lastKnownLidStateKeepsLegacyKeysAndUnknownSemantics() {
        assertNull(ClamshellStateStore.lastKnownLidClosed(context))

        assertTrue(
            ClamshellStateStore.setLastKnownLidClosed(
                context = context,
                closed = true
            )
        )

        val prefs = context.getSharedPreferences("sleep_manager", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("thor_lid_state_known", false))
        assertTrue(prefs.getBoolean("thor_lid_closed_last_known", false))
        assertEquals(true, ClamshellStateStore.lastKnownLidClosed(context))

        assertTrue(
            ClamshellStateStore.setLastKnownLidClosed(
                context = context,
                closed = false
            )
        )
        assertEquals(false, ClamshellStateStore.lastKnownLidClosed(context))
    }

    private fun clearState() {
        context.getSharedPreferences("sleep_manager", Context.MODE_PRIVATE)
            .edit()
            .remove("thor_lid_closed_last_known")
            .remove("thor_lid_state_known")
            .commit()
    }
}
