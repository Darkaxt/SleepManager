package com.med.sleepmanager.rules

object ClamshellMonitoringPolicy {
    fun shouldMonitorLid(
        lidSupported: Boolean,
        closedLidProtectionEnabled: Boolean,
        chargingSeparationWithLidEnabled: Boolean,
        chargingSeparationControlSupported: Boolean
    ): Boolean =
        lidSupported &&
            (
                closedLidProtectionEnabled ||
                    (
                        chargingSeparationWithLidEnabled &&
                            chargingSeparationControlSupported
                    )
            )
}
