package com.med.sleepmanager

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.UpdateStateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UpdateStateStoreTest {
    private lateinit var context: Context

    private val keys = listOf(
        "last_update_check_attempt",
        "last_update_check_success",
        "latest_release_version",
        "latest_release_version_code",
        "latest_release_url",
        "latest_release_apk_url",
        "latest_release_sha256",
        "latest_helper_version",
        "latest_helper_version_code",
        "latest_helper_apk_url",
        "latest_helper_sha256",
        "last_notified_update_version",
        "last_notified_helper_update_version"
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearUpdateState()
    }

    @After
    fun tearDown() {
        clearUpdateState()
    }

    @Test
    fun updateStateKeepsLegacySharedPreferenceKeysAndNullSemantics() {
        UpdateStateStore.setLastUpdateCheckAttempt(context, 111L)
        UpdateStateStore.setLastUpdateCheckSuccess(context, 222L)
        UpdateStateStore.setLatestRelease(
            context = context,
            version = "9.8.7",
            versionCode = 987L,
            url = "https://example.invalid/release",
            apkUrl = "https://example.invalid/main.apk",
            sha256 = "a".repeat(64),
            helperVersion = "6.5.4",
            helperVersionCode = 654L,
            helperApkUrl = "https://example.invalid/helper.apk",
            helperSha256 = "b".repeat(64)
        )
        UpdateStateStore.setLastNotifiedUpdateVersion(context, "9.8.7")
        UpdateStateStore.setLastNotifiedHelperUpdateVersion(context, "6.5.4")

        val prefs = context.getSharedPreferences("sleep_manager", Context.MODE_PRIVATE)
        assertEquals(111L, prefs.getLong("last_update_check_attempt", 0L))
        assertEquals(222L, prefs.getLong("last_update_check_success", 0L))
        assertEquals("9.8.7", prefs.getString("latest_release_version", null))
        assertEquals(987L, prefs.getLong("latest_release_version_code", -1L))
        assertEquals("6.5.4", prefs.getString("latest_helper_version", null))
        assertEquals(654L, prefs.getLong("latest_helper_version_code", -1L))
        assertEquals("9.8.7", UpdateStateStore.latestReleaseVersion(context))
        assertEquals("6.5.4", UpdateStateStore.latestHelperVersion(context))

        UpdateStateStore.setLatestRelease(
            context = context,
            version = "9.8.8",
            versionCode = null,
            url = "https://example.invalid/release-2",
            apkUrl = null,
            sha256 = null,
            helperVersion = null,
            helperVersionCode = null,
            helperApkUrl = null,
            helperSha256 = null
        )

        assertNull(UpdateStateStore.latestReleaseVersionCode(context))
        assertNull(UpdateStateStore.latestReleaseApkUrl(context))
        assertNull(UpdateStateStore.latestReleaseSha256(context))
        assertNull(UpdateStateStore.latestHelperVersion(context))
        assertNull(UpdateStateStore.latestHelperVersionCode(context))
        assertNull(UpdateStateStore.latestHelperApkUrl(context))
        assertNull(UpdateStateStore.latestHelperSha256(context))
    }

    private fun clearUpdateState() {
        val editor =
            context.getSharedPreferences("sleep_manager", Context.MODE_PRIVATE)
                .edit()
        keys.forEach(editor::remove)
        editor.commit()
    }
}
