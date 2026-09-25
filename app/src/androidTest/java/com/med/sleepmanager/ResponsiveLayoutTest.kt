package com.med.sleepmanager

import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import java.io.FileInputStream
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResponsiveLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @After
    fun resetDisplay() {
        shell("wm size reset")
        shell("wm density reset")
    }

    @Test
    fun compact360dp_portrait_keepsCoreUiReadable() {
        verifyCompactProfile(
            widthPx = 945,
            heightPx = 2100,
            densityDpi = 420
        )
    }

    @Test
    fun compact411dp_portrait_keepsCoreUiReadable() {
        verifyCompactProfile(
            widthPx = 1080,
            heightPx = 2400,
            densityDpi = 420
        )
    }

    @Test
    fun medium600dp_portrait_keepsCoreUiReadable() {
        applyDisplay(
            widthPx = 1200,
            heightPx = 1920,
            densityDpi = 320
        )

        composeRule.onNodeWithTag("top_app_title").assertIsDisplayed()
        composeRule.onNodeWithTag("compact_side_rail").assertIsDisplayed()
        assertBatteryShape()
        assertIntegrationsReadable()
    }

    @Test
    fun landscape720dp_keepsCoreUiReadable() {
        applyDisplay(
            widthPx = 1440,
            heightPx = 900,
            densityDpi = 320
        )

        composeRule.onNodeWithTag("top_app_title").assertIsDisplayed()
        composeRule.onNodeWithTag("compact_side_rail").assertIsDisplayed()
        assertBatteryShape()
        assertIntegrationsReadable()
    }

    @Test
    fun shortLandscape_testSleepWakeDialogCanScrollToLastInstruction() {
        applyDisplay(
            widthPx = 1280,
            heightPx = 960,
            densityDpi = 360
        )

        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("How to test sleep / wake"))
        composeRule.onNodeWithText("How to test sleep / wake")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            "Copy log includes the full transaction details if needed."
        )
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Got it")
            .assertIsDisplayed()
    }

    @Test
    fun advancedPage_separatesSyncAndSleepConditions() {
        applyDisplay(
            widthPx = 1440,
            heightPx = 900,
            densityDpi = 320
        )

        composeRule.onNodeWithContentDescription("Advanced settings")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Advanced sync conditions")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Periodic sync while sleeping")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Sync then stop on sleep & wake")
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription(
            "Periodic sync while sleeping toggle"
        ).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(
            "Sync then stop on sleep & wake toggle"
        ).assertIsNotEnabled()

        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("Advanced sleep conditions"))
        composeRule.onNodeWithText("Advanced sleep conditions")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Use custom delay")
            .assertIsDisplayed()
    }

    @Test
    fun switchingToStats_doesNotReuseHomeScrollPosition() {
        applyDisplay(
            widthPx = 1440,
            heightPx = 900,
            densityDpi = 320
        )

        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasText("App integrations"))
        composeRule.onNodeWithText("Stats")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Battery")
            .assertIsDisplayed()
    }

    private fun verifyCompactProfile(
        widthPx: Int,
        heightPx: Int,
        densityDpi: Int
    ) {
        applyDisplay(widthPx, heightPx, densityDpi)

        composeRule.onNodeWithTag("top_app_title").assertIsDisplayed()

        // Phones should not surrender 64dp to a permanent navigation rail.
        // The drawer entry point must remain visible instead.
        composeRule.onNodeWithTag("compact_side_rail").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Open navigation")
            .assertIsDisplayed()

        assertBatteryShape()
        assertIntegrationsReadable()
    }

    private fun assertBatteryShape() {
        composeRule.onNodeWithTag("main_list")
            .performScrollToNode(hasTestTag("battery_gauge"))

        val bounds = composeRule
            .onNodeWithTag("battery_gauge_body", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Battery gauge should stay recognizably horizontal; " +
                "was ${bounds.width}x${bounds.height}",
            bounds.width >= bounds.height * 2.4f
        )
    }

    private fun assertIntegrationsReadable() {
        val list = composeRule.onNodeWithTag("main_list")
        list.performScrollToNode(hasText("App integrations"))
        composeRule.onNodeWithText("App integrations").assertIsDisplayed()

        assertTextIsNotCrushed("integration_title_Syncthing‑Fork")
        assertTextIsNotCrushed("integration_title_Tailscale / TailDNS")
        assertTextIsNotCrushed("integration_title_JamesDSP")
    }

    private fun assertTextIsNotCrushed(tag: String) {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        node.performScrollTo()

        val bounds: Rect = node
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "$tag is wrapping into an unusably narrow column: " +
                "${bounds.width}x${bounds.height}",
            bounds.width >= bounds.height * 1.25f
        )
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

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor =
            instrumentation.uiAutomation.executeShellCommand(command)

        return FileInputStream(descriptor.fileDescriptor)
            .bufferedReader()
            .use { it.readText() }
            .also { descriptor.close() }
    }
}
