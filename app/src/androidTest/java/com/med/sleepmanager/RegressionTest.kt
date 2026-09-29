package com.med.sleepmanager

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.protection.ThorLidMonitor
import com.med.sleepmanager.service.SleepManagerService
import java.io.FileInputStream
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val targetContext
        get() = instrumentation.targetContext

    @Before
    fun resetState() {
        resetDisplay()
        resetPreferences()
        targetContext.stopService(
            Intent(targetContext, SleepManagerService::class.java)
        )
        composeRule.runOnUiThread {
            composeRule.activity.activityRefreshToken++
        }
        composeRule.waitForIdle()
    }

    @After
    fun cleanUp() {
        resetPreferences()
        targetContext.stopService(
            Intent(targetContext, SleepManagerService::class.java)
        )
        resetDisplay()
    }

    @Test
    fun home_separatesSleepBehaviorAndHidesClamshellOnNormalEmulator() {
        applyDisplay(
            widthPx = 1200,
            heightPx = 1920,
            densityDpi = 320
        )

        val list = composeRule.onNodeWithTag("main_list")

        list.performScrollToNode(hasText("Sleep behavior"))
        composeRule.onNodeWithText("Sleep behavior")
            .assertIsDisplayed()

        list.performScrollToNode(hasText("Grace period"))
        composeRule.onNodeWithText("Grace period")
            .assertIsDisplayed()

        assertFalse(
            "Pixel emulator unexpectedly exposes a compatible lid sensor",
            ThorLidMonitor.isSupported()
        )

        assertFalse(
            "Clamshell options must stay hidden when no compatible lid is detected",
            listContainsText("Clamshell options")
        )
        assertFalse(
            "Home UI should not expose legacy Thor branding",
            listContainsText("Thor", substring = true)
        )
        assertFalse(
            "Home UI should not expose AYN branding",
            listContainsText("AYN", substring = true)
        )

        list.performScrollToNode(hasText("App integrations"))
        composeRule.onNodeWithText("App integrations")
            .assertIsDisplayed()
    }

    @Test
    fun gracePeriod_selectionPersistsAcrossActivityRecreation() {
        applyDisplay(
            widthPx = 1200,
            heightPx = 1920,
            densityDpi = 320
        )

        val list = composeRule.onNodeWithTag("main_list")
        list.performScrollToNode(hasText("Grace period"))

        composeRule.onNodeWithText("5 s")
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.sleepGraceMs(targetContext) == 5_000L
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("Grace period"))

        composeRule.onNodeWithText("5 s")
            .assertIsSelected()
    }

    @Test
    fun homeOptions_writeExpectedPreferencesAndCapabilityGatingIsCorrect() {
        AppPreferences.setSetupComplete(targetContext, true)

        applyDisplay(
            widthPx = 1440,
            heightPx = 2600,
            densityDpi = 320
        )

        assertTrue(
            "Regression runner must install the Helper before Home tests",
            HelperController.isInstalled(targetContext)
        )

        openSection("Home", "SleepManager")

        val list = composeRule.onNodeWithTag("main_list")
        list.performScrollToNode(hasTestTag("system_controls_card"))
        composeRule.onNodeWithTag("system_controls_card")
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Wi‑Fi toggle")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.manageWifi(targetContext)
        }

        composeRule.onNodeWithContentDescription("Bluetooth toggle")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.manageBluetooth(targetContext)
        }

        list.performScrollToNode(hasText("Grace period"))
        composeRule.onNodeWithText("10 s")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.sleepGraceMs(targetContext) == 10_000L
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.currentDeviceControlCapabilities != null
        }
        assertFalse(
            "Normal emulator must not report privileged Battery Saver control",
            composeRule.activity.currentDeviceControlCapabilities
                ?.batterySaverControl == true
        )

        list.performScrollToNode(hasTestTag("app_integrations_card"))
        composeRule.onNodeWithTag("app_integrations_card")
            .assertIsDisplayed()
        assertIntegrationToggleMatchesAvailability(
            contentDescription = "Syncthing‑Fork toggle",
            available = SyncthingController.installedTargets(targetContext).isNotEmpty()
        )
        assertIntegrationToggleMatchesAvailability(
            contentDescription = "Tailscale toggle",
            available = TailscaleController.isInstalled(targetContext)
        )
        assertIntegrationToggleMatchesAvailability(
            contentDescription = "JamesDSP toggle",
            available = JamesDspController.isInstalled(targetContext)
        )
        assertIntegrationToggleMatchesAvailability(
            contentDescription = "BasicSync toggle",
            available = BasicSyncController.isInstalled(targetContext)
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        assertTrue(AppPreferences.manageWifi(targetContext))
        assertTrue(AppPreferences.manageBluetooth(targetContext))
        assertTrue(AppPreferences.sleepGraceMs(targetContext) == 10_000L)
    }

    @Test
    fun advancedOptions_writeExpectedPreferencesAndPersist() {
        applyDisplay(
            widthPx = 1440,
            heightPx = 3200,
            densityDpi = 320
        )

        assertTrue(
            "Regression setup should grant exact-alarm access on the emulator",
            composeRule.activity.canScheduleExactAlarms()
        )

        openSection("Advanced settings", "Advanced")
        val list = composeRule.onNodeWithTag("main_list")

        composeRule.onNodeWithContentDescription(
            "Periodic sync while sleeping toggle"
        ).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(
            "Sync then stop on sleep & wake toggle"
        ).assertIsNotEnabled()

        list.performScrollToNode(hasText("Use custom delay"))
        composeRule.onNodeWithContentDescription("Use custom delay toggle")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.customDelayEnabled(targetContext)
        }
        assertTrue(AppPreferences.sleepGraceMs(targetContext) == 0L)

        list.performScrollToNode(hasText("Delay before sleep actions"))
        composeRule.onNodeWithText("5 min")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.customDelayMs(targetContext) == 300_000L
        }

        list.performScrollToNode(hasText("Battery level"))
        composeRule.onNodeWithContentDescription("Battery level toggle")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.batteryConditionEnabled(targetContext)
        }

        composeRule.onNodeWithText("< 50%")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.batteryBelowPercent(targetContext) == 50
        }

        list.performScrollToNode(hasText("Not charging"))
        composeRule.onNodeWithContentDescription("Not charging toggle")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.notChargingOnly(targetContext)
        }

        // Battery Saver condition is read-only and must remain available
        // on the normal emulator even though privileged Home control is absent.
        list.performScrollToNode(hasText("Battery Saver"))
        composeRule.onNodeWithText("ON")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.batterySaverMode(targetContext) ==
                AppPreferences.BATTERY_SAVER_ON
        }

        list.performScrollToNode(hasText("Schedule"))
        composeRule.onNodeWithContentDescription("Schedule toggle")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.scheduleEnabled(targetContext)
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        assertTrue(AppPreferences.customDelayEnabled(targetContext))
        assertTrue(AppPreferences.customDelayMs(targetContext) == 300_000L)
        assertTrue(AppPreferences.batteryConditionEnabled(targetContext))
        assertTrue(AppPreferences.batteryBelowPercent(targetContext) == 50)
        assertTrue(AppPreferences.notChargingOnly(targetContext))
        assertTrue(
            AppPreferences.batterySaverMode(targetContext) ==
                AppPreferences.BATTERY_SAVER_ON
        )
        assertTrue(AppPreferences.scheduleEnabled(targetContext))

        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("Battery level"))
        composeRule.onNodeWithText("Only when battery is below 50%")
            .assertIsDisplayed()
    }

    @Test
    fun batterySaverActionAndConditionRemainMutuallyExclusive() {
        applyDisplay(
            widthPx = 1440,
            heightPx = 3200,
            densityDpi = 320
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.currentDeviceControlCapabilities != null
        }

        composeRule.runOnUiThread {
            composeRule.activity.currentDeviceControlCapabilities =
                com.med.sleepmanager.device.DeviceControlController
                    .ControlCapabilities(
                        pServerAvailable = true,
                        batterySaverControl = true,
                        chargingSeparationControl = false
                    )
        }
        composeRule.waitForIdle()

        val list = composeRule.onNodeWithTag("main_list")
        list.performScrollToNode(hasText("Battery Saver"))
        composeRule.onNodeWithContentDescription("Battery Saver toggle")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.manageBatterySaver(targetContext)
        }
        assertTrue(
            AppPreferences.batterySaverMode(targetContext) ==
                AppPreferences.BATTERY_SAVER_IGNORE
        )

        openSection("Advanced settings", "Advanced")
        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("Battery Saver"))

        composeRule.onNodeWithText("ON")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.batterySaverMode(targetContext) ==
                AppPreferences.BATTERY_SAVER_ON
        }

        assertFalse(
            "Choosing a Battery Saver condition must disable the sleep action",
            AppPreferences.manageBatterySaver(targetContext)
        )

        openSection("Home", "SleepManager")
        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("Battery Saver"))
        composeRule.onNodeWithContentDescription("Battery Saver toggle")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.manageBatterySaver(targetContext)
        }
        assertTrue(
            "Re-enabling the Home Battery Saver action must reset the Advanced condition",
            AppPreferences.batterySaverMode(targetContext) ==
                AppPreferences.BATTERY_SAVER_IGNORE
        )
    }

    @Test
    fun helper_stateBroadcastStillReachesMainActivity() {
        assertTrue(
            "Regression runner must install the Helper before instrumentation",
            HelperController.isInstalled(targetContext)
        )

        composeRule.runOnUiThread {
            HelperController.requestState(composeRule.activity)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.currentWifiState != null &&
                composeRule.activity.currentBluetoothState != null
        }

        assertNotNull(composeRule.activity.currentWifiState)
        assertNotNull(composeRule.activity.currentBluetoothState)
    }

    @Test
    fun manager_enableDisableStillControlsForegroundService() {
        composeRule.onNodeWithText("Enable SleepManager")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            AppPreferences.isEnabled(targetContext)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            SleepManagerService.running
        }

        assertTrue(AppPreferences.isEnabled(targetContext))
        assertTrue(SleepManagerService.running)

        composeRule.onNodeWithText("Disable SleepManager")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !AppPreferences.isEnabled(targetContext)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !SleepManagerService.running
        }

        assertFalse(AppPreferences.isEnabled(targetContext))
        assertFalse(SleepManagerService.running)
    }

    @Test
    fun primarySections_openAndCurrentSectionSurvivesRecreation() {
        applyDisplay(
            widthPx = 1440,
            heightPx = 1920,
            densityDpi = 320
        )

        composeRule.onNodeWithTag("top_app_title")
            .assertTextEquals("SleepManager")

        openSection("Advanced settings", "Advanced")
        composeRule.onNodeWithText("Advanced sync behavior")
            .assertIsDisplayed()

        openSection("Stats", "Stats")
        openSection("Activity log", "Activity log")

        openSection("About", "About")
        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("App details"))
        composeRule.onNodeWithText("App details")
            .assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("top_app_title")
            .assertTextEquals("About")
    }

    @Test
    fun backgroundReliabilityState_survivesPeriodicActivityRefresh() {
        applyDisplay(
            widthPx = 1440,
            heightPx = 1920,
            densityDpi = 320
        )

        openSection("About", "About")

        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("Background reliability"))
        composeRule.onNodeWithText("Background reliability")
            .assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.currentBackgroundReliability != null
        }

        val beforeRefresh =
            composeRule.activity.currentBackgroundReliability
        assertNotNull(beforeRefresh)

        // MainActivity refreshes visible runtime state every 3 seconds.
        // The previous beta briefly reset async values to null on each pass.
        Thread.sleep(3_500)
        composeRule.waitForIdle()

        assertNotNull(
            "Background reliability must keep its last known value during refresh",
            composeRule.activity.currentBackgroundReliability
        )

        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("Battery optimization"))
        composeRule.onNodeWithText("Battery optimization")
            .assertIsDisplayed()

        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("Unused app restrictions"))
        composeRule.onNodeWithText("Unused app restrictions")
            .assertIsDisplayed()
    }

    private fun openSection(
        contentDescription: String,
        expectedTitle: String
    ) {
        composeRule.onNodeWithContentDescription(contentDescription)
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top_app_title")
            .assertTextEquals(expectedTitle)
    }

    private fun listContainsText(
        text: String,
        substring: Boolean = false
    ): Boolean =
        runCatching {
            composeRule.onNodeWithTag("main_list")
                .performScrollToNode(
                    hasText(text, substring = substring)
                )
        }.isSuccess

    private fun resetPreferences() {
        AppPreferences.setEnabled(targetContext, false)
        AppPreferences.setSetupComplete(targetContext, false)
        AppPreferences.setManageWifi(targetContext, false)
        AppPreferences.setManageBluetooth(targetContext, false)
        AppPreferences.setManageBatterySaver(targetContext, false)
        AppPreferences.setManageSyncthing(targetContext, false)
        AppPreferences.setManageTailscale(targetContext, false)
        AppPreferences.setManageJamesDsp(targetContext, false)
        AppPreferences.setManageBasicSync(targetContext, false)
        AppPreferences.setPeriodicSyncWhileSleeping(targetContext, false)
        AppPreferences.setSyncThenStopOnSleepWake(targetContext, false)
        AppPreferences.setManageThorProtection(targetContext, false)
        AppPreferences.setThorDockDisconnectSleeps(targetContext, false)
        AppPreferences.setThorClosedPowerSleeps(targetContext, false)
        AppPreferences.setManageChargingSeparationWithLid(
            targetContext,
            false
        )
        AppPreferences.setCustomDelayEnabled(targetContext, false)
        AppPreferences.setCustomDelayMs(targetContext, 60_000L)
        AppPreferences.setSleepGraceMs(targetContext, 0L)
        AppPreferences.setBatteryConditionEnabled(targetContext, false)
        AppPreferences.setBatteryBelowPercent(targetContext, 30)
        AppPreferences.setNotChargingOnly(targetContext, false)
        AppPreferences.setBatterySaverMode(
            targetContext,
            AppPreferences.BATTERY_SAVER_IGNORE
        )
        AppPreferences.setScheduleEnabled(targetContext, false)
        AppPreferences.setScheduleStartMinutes(targetContext, 23 * 60)
        AppPreferences.setScheduleEndMinutes(targetContext, 7 * 60)
    }

    private fun assertIntegrationToggleMatchesAvailability(
        contentDescription: String,
        available: Boolean
    ) {
        val node = composeRule.onNodeWithContentDescription(contentDescription)
        if (available) {
            node.assertIsEnabled()
        } else {
            node.assertIsNotEnabled()
        }
    }

    private fun applyDisplay(
        widthPx: Int,
        heightPx: Int,
        densityDpi: Int
    ) {
        shell("wm size ${widthPx}x${heightPx}")
        shell("wm density $densityDpi")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    private fun resetDisplay() {
        shell("wm size reset")
        shell("wm density reset")
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor =
            instrumentation.uiAutomation.executeShellCommand(command)

        return FileInputStream(descriptor.fileDescriptor)
            .bufferedReader()
            .use { it.readText() }
            .also { descriptor.close() }
    }
}
