package com.med.sleepmanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.med.sleepmanager.ui.components.ClamshellOptionsCard
import com.med.sleepmanager.ui.theme.SleepManagerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ClamshellComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clamshellOptions_areGenericOrderedAndCapabilityAware() {
        var protectionEnabled by mutableStateOf(false)

        composeRule.setContent {
            SleepManagerTheme {
                ClamshellOptionsCard(
                    closedLidProtectionEnabled = protectionEnabled,
                    chargingSeparationSupported = true,
                    chargingSeparationEnabled = false,
                    sleepOnExternalDisplayDisconnect = false,
                    powerButtonSleepSupported = true,
                    powerButtonSleepsWithLidClosed = false,
                    onClosedLidProtectionChange = {
                        protectionEnabled = it
                    },
                    onChargingSeparationChange = {},
                    onSleepOnExternalDisplayDisconnectChange = {},
                    onPowerButtonSleepsWithLidClosedChange = {}
                )
            }
        }

        val closedLid = "Closed-lid protection"
        val disconnect = "Sleep when external display disconnects"
        val power = "Power button sleeps with lid closed"
        val charging = "Disable Charging Separation with lid closed"

        val closedLidTop =
            composeRule.onNodeWithText(closedLid)
                .fetchSemanticsNode().boundsInRoot.top
        val disconnectTop =
            composeRule.onNodeWithText(disconnect)
                .fetchSemanticsNode().boundsInRoot.top
        val powerTop =
            composeRule.onNodeWithText(power)
                .fetchSemanticsNode().boundsInRoot.top
        val chargingTop =
            composeRule.onNodeWithText(charging)
                .fetchSemanticsNode().boundsInRoot.top

        assertTrue(closedLidTop < disconnectTop)
        assertTrue(disconnectTop < powerTop)
        assertTrue(powerTop < chargingTop)

        composeRule.onNodeWithText(
            "Return the device to sleep after accidental wake-ups while the lid is still closed. External displays remain usable."
        ).fetchSemanticsNode()

        composeRule.onNodeWithText(
            "With the lid closed and the device awake, press Power to put it back to sleep—docked or after disconnecting the external display. Off keeps the device's default behavior."
        ).fetchSemanticsNode()

        composeRule.onNodeWithText("Thor", substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("AYN", substring = true)
            .assertDoesNotExist()

        composeRule.onNodeWithContentDescription(
            "$disconnect toggle"
        ).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(
            "$power toggle"
        ).assertIsNotEnabled()

        composeRule.onNodeWithContentDescription(
            "$closedLid toggle"
        ).assertIsEnabled().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(
            "$disconnect toggle"
        ).assertIsEnabled()
        composeRule.onNodeWithContentDescription(
            "$power toggle"
        ).assertIsEnabled()

        // Charging Separation is capability-gated, but intentionally does not
        // depend on Closed-lid protection being enabled.
        composeRule.onNodeWithContentDescription(
            "$charging toggle"
        ).assertIsEnabled()
    }

    @Test
    fun optionalClamshellRows_hideWhenCapabilitiesAreMissing() {
        composeRule.setContent {
            SleepManagerTheme {
                ClamshellOptionsCard(
                    closedLidProtectionEnabled = false,
                    chargingSeparationSupported = false,
                    chargingSeparationEnabled = false,
                    sleepOnExternalDisplayDisconnect = false,
                    powerButtonSleepSupported = false,
                    powerButtonSleepsWithLidClosed = false,
                    onClosedLidProtectionChange = {},
                    onChargingSeparationChange = {},
                    onSleepOnExternalDisplayDisconnectChange = {},
                    onPowerButtonSleepsWithLidClosedChange = {}
                )
            }
        }

        composeRule.onNodeWithText(
            "Disable Charging Separation with lid closed"
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            "Power button sleeps with lid closed"
        ).assertDoesNotExist()

        composeRule.onNodeWithText("Closed-lid protection")
            .fetchSemanticsNode()
        composeRule.onNodeWithText(
            "Sleep when external display disconnects"
        ).fetchSemanticsNode()
    }
}
