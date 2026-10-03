package com.med.sleepmanager.protection

/**
 * Owns the currently attached clamshell input monitors.
 *
 * SleepManagerService still creates the monitors and owns all callbacks and
 * policy decisions. This class only centralizes nullable-reference ownership
 * and stop/detach semantics so lifecycle cleanup stays consistent.
 */
internal class ClamshellMonitorRuntime {
    private var lidMonitor: LidMonitor? = null
    private var powerButtonMonitor: PmicPowerButtonMonitor? = null

    val hasLidMonitor: Boolean
        get() = lidMonitor != null

    val hasPowerButtonMonitor: Boolean
        get() = powerButtonMonitor != null

    fun attachLidMonitor(monitor: LidMonitor) {
        lidMonitor = monitor
    }

    fun attachPowerButtonMonitor(monitor: PmicPowerButtonMonitor) {
        powerButtonMonitor = monitor
    }

    fun stopLidMonitor() {
        lidMonitor?.stop()
        lidMonitor = null
    }

    fun stopPowerButtonMonitor() {
        powerButtonMonitor?.stop()
        powerButtonMonitor = null
    }

    fun stopAll() {
        stopLidMonitor()
        stopPowerButtonMonitor()
    }
}
