package com.med.sleepmanager.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RaOfflineProxySleepStoreTest {
    private val context =
        InstrumentationRegistry.getInstrumentation()
            .targetContext

    @After
    fun cleanUp() {
        RaOfflineProxySleepStore.clear(context)
    }

    @Test
    fun stopRequestedIsPersistedAsPending() {
        RaOfflineProxySleepStore.set(
            context,
            RaOfflineProxySleepStore.Phase.STOP_REQUESTED,
            cycleId = 77L,
            wifi = true,
            bluetooth = true
        )

        val restored = RaOfflineProxySleepStore.current(context)
        assertTrue(restored.pending)
        assertTrue(
            RaOfflineProxySleepStore.isPendingForCycle(
                context,
                77L
            )
        )
    }

    @Test
    fun pendingGateSurvivesStoreReload() {
        RaOfflineProxySleepStore.set(
            context,
            RaOfflineProxySleepStore.Phase.WAITING_FOR_QUEUE,
            cycleId = 123L,
            wifi = true,
            bluetooth = false
        )

        assertTrue(
            RaOfflineProxySleepStore.isPendingForCycle(
                context,
                123L
            )
        )
        assertFalse(
            RaOfflineProxySleepStore.isPendingForCycle(
                context,
                124L
            )
        )
    }
}
