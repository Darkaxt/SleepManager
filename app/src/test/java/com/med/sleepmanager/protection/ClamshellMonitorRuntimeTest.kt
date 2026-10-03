package com.med.sleepmanager.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClamshellMonitorRuntimeTest {
    @Test
    fun attachAndStopLidMonitor_updatesOwnership() {
        val runtime = ClamshellMonitorRuntime()
        val monitor = LidMonitor(
            onClosed = {},
            onOpened = {}
        )

        runtime.attachLidMonitor(monitor)
        assertTrue(runtime.hasLidMonitor)

        runtime.stopLidMonitor()

        assertFalse(runtime.hasLidMonitor)
    }

    @Test
    fun attachAndStopPowerMonitor_updatesOwnership() {
        val runtime = ClamshellMonitorRuntime()
        val monitor = PmicPowerButtonMonitor(
            onPressed = {}
        )

        runtime.attachPowerButtonMonitor(monitor)
        assertTrue(runtime.hasPowerButtonMonitor)

        runtime.stopPowerButtonMonitor()

        assertFalse(runtime.hasPowerButtonMonitor)
    }

    @Test
    fun stopAll_clearsBothMonitorReferences() {
        val runtime = ClamshellMonitorRuntime()
        runtime.attachLidMonitor(
            LidMonitor(
                onClosed = {},
                onOpened = {}
            )
        )
        runtime.attachPowerButtonMonitor(
            PmicPowerButtonMonitor(
                onPressed = {}
            )
        )

        runtime.stopAll()

        assertFalse(runtime.hasLidMonitor)
        assertFalse(runtime.hasPowerButtonMonitor)
    }
}
