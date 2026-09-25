package com.med.sleepmanager.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepManagerReleaseOriginTest {
    @Test
    fun forkReleaseAndDownloadUrlsAreAccepted() {
        assertTrue(
            SleepManagerReleaseOrigin.isExpectedReleaseUrl(
                "https://github.com/Darkaxt/SleepManager/releases/tag/v0.6.0.1"
            )
        )
        assertTrue(
            SleepManagerReleaseOrigin.isExpectedDownloadUrl(
                "https://github.com/Darkaxt/SleepManager/releases/download/" +
                    "v0.6.0.1/SleepManager-0.6.0.1.apk"
            )
        )
    }

    @Test
    fun upstreamAndLookalikeRepositoriesAreRejected() {
        assertFalse(
            SleepManagerReleaseOrigin.isExpectedReleaseUrl(
                "https://github.com/Baggio94/SleepManager/releases/tag/v0.6.1"
            )
        )
        assertFalse(
            SleepManagerReleaseOrigin.isExpectedDownloadUrl(
                "https://github.com/Darkaxt/SleepManager-malicious/releases/download/" +
                    "v1/SleepManager-1.apk"
            )
        )
    }
}
