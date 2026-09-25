package com.med.sleepmanager.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperUpdatePolicyTest {
    private fun helper(
        versionName: String = "1.0.0",
        versionCode: Long? = 1000L
    ) = HelperUpdateInfo(
        versionName = versionName,
        versionCode = versionCode,
        releaseUrl = "https://github.com/Darkaxt/SleepManager/releases/tag/v0.5.4"
    )

    @Test
    fun olderInstalledHelperNeedsUpdate() {
        assertTrue(
            UpdateChecker.helperNeedsUpdate(
                installedVersionCode = 528L,
                installedVersionName = "0.5.3",
                helper = helper()
            )
        )
    }

    @Test
    fun samePublishedHelperDoesNotNeedUpdate() {
        assertFalse(
            UpdateChecker.helperNeedsUpdate(
                installedVersionCode = 1000L,
                installedVersionName = "1.0.0",
                helper = helper()
            )
        )
    }

    @Test
    fun newerInstalledHelperDoesNotDowngrade() {
        assertFalse(
            UpdateChecker.helperNeedsUpdate(
                installedVersionCode = 1100L,
                installedVersionName = "1.1.0",
                helper = helper()
            )
        )
    }

    @Test
    fun metadataWithoutVersionCodeFallsBackToVersionName() {
        assertTrue(
            UpdateChecker.helperNeedsUpdate(
                installedVersionCode = 528L,
                installedVersionName = "0.5.3",
                helper = helper(versionCode = null)
            )
        )
    }
}
