package com.med.sleepmanager.data

import android.content.Context

/**
 * Persisted updater runtime/cache state.
 *
 * This intentionally keeps the existing "sleep_manager" SharedPreferences file
 * and legacy key names so 0.6.x update cache and notification state survive
 * this responsibility split without migration.
 *
 * The user-facing automatic-update preference remains in AppPreferences.
 */
object UpdateStateStore {
    private const val PREFS = "sleep_manager"
    private const val KEY_LAST_UPDATE_CHECK_ATTEMPT = "last_update_check_attempt"
    private const val KEY_LAST_UPDATE_CHECK_SUCCESS = "last_update_check_success"
    private const val KEY_LATEST_RELEASE_VERSION = "latest_release_version"
    private const val KEY_LATEST_RELEASE_VERSION_CODE = "latest_release_version_code"
    private const val KEY_LATEST_RELEASE_URL = "latest_release_url"
    private const val KEY_LATEST_RELEASE_APK_URL = "latest_release_apk_url"
    private const val KEY_LATEST_RELEASE_SHA256 = "latest_release_sha256"
    private const val KEY_LATEST_HELPER_VERSION = "latest_helper_version"
    private const val KEY_LATEST_HELPER_VERSION_CODE = "latest_helper_version_code"
    private const val KEY_LATEST_HELPER_APK_URL = "latest_helper_apk_url"
    private const val KEY_LATEST_HELPER_SHA256 = "latest_helper_sha256"
    private const val KEY_LAST_NOTIFIED_UPDATE_VERSION = "last_notified_update_version"
    private const val KEY_LAST_NOTIFIED_HELPER_UPDATE_VERSION =
        "last_notified_helper_update_version"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastUpdateCheckAttempt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_UPDATE_CHECK_ATTEMPT, 0L)

    fun setLastUpdateCheckAttempt(context: Context, value: Long) =
        prefs(context).edit().putLong(KEY_LAST_UPDATE_CHECK_ATTEMPT, value).apply()

    fun lastUpdateCheckSuccess(context: Context): Long =
        prefs(context).getLong(KEY_LAST_UPDATE_CHECK_SUCCESS, 0L)

    fun setLastUpdateCheckSuccess(context: Context, value: Long) =
        prefs(context).edit().putLong(KEY_LAST_UPDATE_CHECK_SUCCESS, value).apply()

    fun latestReleaseVersion(context: Context): String? =
        prefs(context).getString(KEY_LATEST_RELEASE_VERSION, null)

    fun latestReleaseVersionCode(context: Context): Long? =
        prefs(context)
            .getLong(KEY_LATEST_RELEASE_VERSION_CODE, -1L)
            .takeIf { it >= 0L }

    fun latestReleaseUrl(context: Context): String? =
        prefs(context).getString(KEY_LATEST_RELEASE_URL, null)

    fun latestReleaseApkUrl(context: Context): String? =
        prefs(context).getString(KEY_LATEST_RELEASE_APK_URL, null)

    fun latestReleaseSha256(context: Context): String? =
        prefs(context).getString(KEY_LATEST_RELEASE_SHA256, null)

    fun latestHelperVersion(context: Context): String? =
        prefs(context).getString(KEY_LATEST_HELPER_VERSION, null)

    fun latestHelperVersionCode(context: Context): Long? =
        prefs(context)
            .getLong(KEY_LATEST_HELPER_VERSION_CODE, -1L)
            .takeIf { it >= 0L }

    fun latestHelperApkUrl(context: Context): String? =
        prefs(context).getString(KEY_LATEST_HELPER_APK_URL, null)

    fun latestHelperSha256(context: Context): String? =
        prefs(context).getString(KEY_LATEST_HELPER_SHA256, null)

    fun setLatestRelease(
        context: Context,
        version: String,
        versionCode: Long?,
        url: String,
        apkUrl: String?,
        sha256: String?,
        helperVersion: String?,
        helperVersionCode: Long?,
        helperApkUrl: String?,
        helperSha256: String?
    ) =
        prefs(context).edit()
            .putString(KEY_LATEST_RELEASE_VERSION, version)
            .apply {
                if (versionCode != null) {
                    putLong(KEY_LATEST_RELEASE_VERSION_CODE, versionCode)
                } else {
                    remove(KEY_LATEST_RELEASE_VERSION_CODE)
                }
                if (apkUrl != null) {
                    putString(KEY_LATEST_RELEASE_APK_URL, apkUrl)
                } else {
                    remove(KEY_LATEST_RELEASE_APK_URL)
                }
                if (sha256 != null) {
                    putString(KEY_LATEST_RELEASE_SHA256, sha256)
                } else {
                    remove(KEY_LATEST_RELEASE_SHA256)
                }
                if (helperVersion != null) {
                    putString(KEY_LATEST_HELPER_VERSION, helperVersion)
                } else {
                    remove(KEY_LATEST_HELPER_VERSION)
                }
                if (helperVersionCode != null) {
                    putLong(KEY_LATEST_HELPER_VERSION_CODE, helperVersionCode)
                } else {
                    remove(KEY_LATEST_HELPER_VERSION_CODE)
                }
                if (helperApkUrl != null) {
                    putString(KEY_LATEST_HELPER_APK_URL, helperApkUrl)
                } else {
                    remove(KEY_LATEST_HELPER_APK_URL)
                }
                if (helperSha256 != null) {
                    putString(KEY_LATEST_HELPER_SHA256, helperSha256)
                } else {
                    remove(KEY_LATEST_HELPER_SHA256)
                }
            }
            .putString(KEY_LATEST_RELEASE_URL, url)
            .apply()

    fun lastNotifiedUpdateVersion(context: Context): String? =
        prefs(context).getString(KEY_LAST_NOTIFIED_UPDATE_VERSION, null)

    fun setLastNotifiedUpdateVersion(context: Context, version: String) =
        prefs(context).edit().putString(KEY_LAST_NOTIFIED_UPDATE_VERSION, version).apply()

    fun lastNotifiedHelperUpdateVersion(context: Context): String? =
        prefs(context).getString(KEY_LAST_NOTIFIED_HELPER_UPDATE_VERSION, null)

    fun setLastNotifiedHelperUpdateVersion(context: Context, version: String) =
        prefs(context).edit()
            .putString(KEY_LAST_NOTIFIED_HELPER_UPDATE_VERSION, version)
            .apply()
}
