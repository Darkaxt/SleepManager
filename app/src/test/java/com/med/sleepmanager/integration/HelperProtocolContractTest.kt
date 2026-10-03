package com.med.sleepmanager.integration

import com.med.sleepmanager.protocol.HelperProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class HelperProtocolContractTest {
    @Test
    fun helperControllerCompatibilityAliasesUseSharedProtocol() {
        assertEquals(HelperProtocol.HELPER_PACKAGE, HelperController.PACKAGE)
        assertEquals(HelperProtocol.PERMISSION, HelperController.PERMISSION)
        assertEquals(HelperProtocol.ACTION_STATE, HelperController.ACTION_STATE)
        assertEquals(HelperProtocol.ACTION_RESULT, HelperController.ACTION_RESULT)
        assertEquals(HelperProtocol.EXTRA_PHASE, HelperController.EXTRA_PHASE)
        assertEquals(HelperProtocol.EXTRA_CYCLE_ID, HelperController.EXTRA_CYCLE_ID)
        assertEquals(HelperProtocol.EXTRA_WIFI_MANAGED, HelperController.EXTRA_WIFI_MANAGED)
        assertEquals(HelperProtocol.EXTRA_WIFI_PREVIOUS, HelperController.EXTRA_WIFI_PREVIOUS)
        assertEquals(HelperProtocol.EXTRA_WIFI_CHANGED, HelperController.EXTRA_WIFI_CHANGED)
        assertEquals(HelperProtocol.EXTRA_WIFI_ATTEMPTED, HelperController.EXTRA_WIFI_ATTEMPTED)
        assertEquals(HelperProtocol.EXTRA_WIFI_ACTION, HelperController.EXTRA_WIFI_ACTION)
        assertEquals(
            HelperProtocol.EXTRA_WIFI_TOGGLE_SUCCESS,
            HelperController.EXTRA_WIFI_TOGGLE_SUCCESS
        )
        assertEquals(HelperProtocol.EXTRA_AIRPLANE_MODE, HelperController.EXTRA_AIRPLANE_MODE)
        assertEquals(
            HelperProtocol.EXTRA_BLUETOOTH_MANAGED,
            HelperController.EXTRA_BLUETOOTH_MANAGED
        )
        assertEquals(
            HelperProtocol.EXTRA_BLUETOOTH_PREVIOUS,
            HelperController.EXTRA_BLUETOOTH_PREVIOUS
        )
        assertEquals(
            HelperProtocol.EXTRA_BLUETOOTH_CHANGED,
            HelperController.EXTRA_BLUETOOTH_CHANGED
        )
        assertEquals(HelperProtocol.EXTRA_RESTORE_SUCCESS, HelperController.EXTRA_RESTORE_SUCCESS)
        assertEquals(HelperProtocol.EXTRA_STATUS, HelperController.EXTRA_STATUS)
        assertEquals(
            HelperProtocol.STATUS_CYCLE_MISMATCH,
            HelperController.STATUS_CYCLE_MISMATCH
        )
        assertEquals(HelperProtocol.PHASE_SLEEP, HelperController.PHASE_SLEEP)
        assertEquals(HelperProtocol.PHASE_WAKE, HelperController.PHASE_WAKE)
        assertEquals(
            HelperProtocol.PHASE_MAINTENANCE_WIFI,
            HelperController.PHASE_MAINTENANCE_WIFI
        )
    }

    @Test
    fun sharedProtocolKeepsPublishedPackagePermissionAndActions() {
        assertEquals("com.med.sleepmanager", HelperProtocol.MAIN_PACKAGE)
        assertEquals("com.med.sleepmanager.helper", HelperProtocol.HELPER_PACKAGE)
        assertEquals(
            "com.med.sleepmanager.permission.CONTROL_HELPER",
            HelperProtocol.PERMISSION
        )
        assertEquals(
            "com.med.sleepmanager.helper.action.SLEEP",
            HelperProtocol.ACTION_SLEEP
        )
        assertEquals(
            "com.med.sleepmanager.helper.action.WAKE",
            HelperProtocol.ACTION_WAKE
        )
        assertEquals(
            "com.med.sleepmanager.helper.action.RESTORE",
            HelperProtocol.ACTION_RESTORE
        )
        assertEquals(
            "com.med.sleepmanager.helper.action.QUERY_STATE",
            HelperProtocol.ACTION_QUERY
        )
        assertEquals(
            "com.med.sleepmanager.helper.action.SET_TEMP_WIFI",
            HelperProtocol.ACTION_SET_TEMP_WIFI
        )
        assertEquals(
            "com.med.sleepmanager.helper.action.FORGET_STATE",
            HelperProtocol.ACTION_FORGET_STATE
        )
        assertEquals("cycle_id", HelperProtocol.EXTRA_CYCLE_ID)
        assertEquals("CYCLE_MISMATCH", HelperProtocol.STATUS_CYCLE_MISMATCH)
    }
}
