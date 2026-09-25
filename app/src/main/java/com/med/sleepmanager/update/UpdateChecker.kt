package com.med.sleepmanager.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.med.sleepmanager.BuildConfig
import com.med.sleepmanager.data.AppPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HelperUpdateInfo(
    val versionName: String,
    val versionCode: Long? = null,
    val releaseUrl: String,
    val apkUrl: String? = null,
    val sha256: String? = null
) {
    val directInstallAvailable: Boolean
        get() = apkUrl != null && sha256 != null
}

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long? = null,
    val releaseUrl: String,
    val apkUrl: String? = null,
    val sha256: String? = null,
    val helper: HelperUpdateInfo? = null
) {
    val directInstallAvailable: Boolean
        get() = apkUrl != null && sha256 != null
}

sealed class UpdateCheckResult {
    data class Available(
        val info: UpdateInfo,
        val helperInfo: HelperUpdateInfo? = null
    ) : UpdateCheckResult()

    data class HelperAvailable(val info: HelperUpdateInfo) : UpdateCheckResult()
    data class UpToDate(val latestVersion: String) : UpdateCheckResult()
    data class Error(val cause: Throwable) : UpdateCheckResult()
    object Disabled : UpdateCheckResult()
    object NotDue : UpdateCheckResult()
}

object UpdateChecker {
    private const val HELPER_PACKAGE = "com.med.sleepmanager.helper"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    private val checkGate = UpdateCheckGate()

    fun cachedUpdate(context: Context): UpdateInfo? {
        val version = AppPreferences.latestReleaseVersion(context) ?: return null
        val url = AppPreferences.latestReleaseUrl(context) ?: return null
        if (!SleepManagerReleaseOrigin.isExpectedReleaseUrl(url)) return null

        if (!BuildConfig.VERSION_NAME.contains("-dev")) {
            val developerTestCache =
                version.contains("updater-test", ignoreCase = true)
            if (developerTestCache) return null
        }

        return if (VersionComparator.isNewer(version, BuildConfig.VERSION_NAME)) {
            UpdateInfo(
                versionName = version,
                versionCode = AppPreferences.latestReleaseVersionCode(context),
                releaseUrl = url,
                apkUrl = AppPreferences.latestReleaseApkUrl(context),
                sha256 = AppPreferences.latestReleaseSha256(context),
                helper = cachedHelperRelease(context)
            )
        } else {
            null
        }
    }

    fun cachedHelperUpdate(context: Context): HelperUpdateInfo? =
        helperUpdateForRelease(context, cachedHelperRelease(context))

    fun cachedHelperReleaseInfo(context: Context): HelperUpdateInfo? =
        cachedHelperRelease(context)

    fun fetchLatestHelperForInstall(context: Context): HelperUpdateInfo? {
        val appContext = context.applicationContext
        val release = fetchLatestStableRelease()
        cacheRelease(appContext, release)
        return release.helper
    }

    fun simulateAvailableUpdate(
        context: Context,
        versionName: String = "0.5.2"
    ): UpdateInfo {
        val appContext = context.applicationContext
        val update = UpdateInfo(
            versionName = versionName,
            releaseUrl = SleepManagerReleaseOrigin.RELEASES_URL
        )
        cacheRelease(appContext, update)
        UpdateNotifier.notifyIfNeeded(appContext, update)
        return update
    }

    fun checkIfDueAsync(context: Context, notify: Boolean): Thread? =
        checkAsync(context, notify, UpdateCheckTrigger.BACKGROUND)

    fun checkOnForegroundAsync(context: Context, notify: Boolean): Thread? =
        checkAsync(context, notify, UpdateCheckTrigger.FOREGROUND)

    private fun checkAsync(
        context: Context,
        notify: Boolean,
        trigger: UpdateCheckTrigger
    ): Thread? {
        val appContext = context.applicationContext
        // Claim before creating a worker, so duplicate lifecycle/job triggers do
        // not even create another thread. Both APKs use the same release fetch.
        if (beginCheck(appContext, trigger) != null) return null
        return try {
            Thread(
                { performCheck(appContext, notify) },
                "SleepManagerUpdateCheck"
            ).also { it.start() }
        } catch (t: Throwable) {
            checkGate.finish()
            null
        }
    }

    fun check(
        context: Context,
        force: Boolean,
        notify: Boolean
    ): UpdateCheckResult {
        val appContext = context.applicationContext
        val trigger = if (force) UpdateCheckTrigger.MANUAL else UpdateCheckTrigger.BACKGROUND
        beginCheck(appContext, trigger)?.let { return it }
        return performCheck(appContext, notify)
    }

    private fun beginCheck(
        context: Context,
        trigger: UpdateCheckTrigger
    ): UpdateCheckResult? {
        val now = System.currentTimeMillis()
        val automatic = AppPreferences.automaticUpdateChecks(context)
        return when (
            checkGate.tryBegin(
                trigger = trigger,
                automaticChecks = automatic,
                networkReady = trigger == UpdateCheckTrigger.MANUAL ||
                    (automatic && hasValidatedNetwork(context)),
                now = now,
                lastAttempt = AppPreferences.lastUpdateCheckAttempt(context),
                lastSuccess = AppPreferences.lastUpdateCheckSuccess(context)
            )
        ) {
            UpdateCheckStart.DISABLED -> UpdateCheckResult.Disabled
            UpdateCheckStart.NOT_DUE -> UpdateCheckResult.NotDue
            UpdateCheckStart.READY -> {
                AppPreferences.setLastUpdateCheckAttempt(context, now)
                null
            }
        }
    }

    private fun performCheck(appContext: Context, notify: Boolean): UpdateCheckResult {
        return try {
            if (Thread.currentThread().isInterrupted) {
                return UpdateCheckResult.NotDue
            }

            val release = fetchLatestStableRelease()

            // A foreground/job check may have been cancelled while the HTTP
            // request was in flight. Do not cache or notify after cancellation.
            if (Thread.currentThread().isInterrupted) {
                return UpdateCheckResult.NotDue
            }

            cacheRelease(appContext, release)
            AppPreferences.setLastUpdateCheckSuccess(appContext, System.currentTimeMillis())

            val helperUpdate = helperUpdateForRelease(appContext, release.helper)
            if (VersionComparator.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
                if (notify) {
                    if (helperUpdate != null) {
                        UpdateNotifier.notifyCombinedIfNeeded(
                            appContext,
                            release,
                            helperUpdate
                        )
                    } else {
                        UpdateNotifier.notifyIfNeeded(appContext, release)
                    }
                }
                UpdateCheckResult.Available(
                    info = release,
                    helperInfo = helperUpdate
                )
            } else if (helperUpdate != null) {
                if (notify) {
                    UpdateNotifier.notifyHelperIfNeeded(appContext, helperUpdate)
                }
                UpdateCheckResult.HelperAvailable(helperUpdate)
            } else {
                UpdateCheckResult.UpToDate(release.versionName)
            }
        } catch (t: Throwable) {
            UpdateCheckResult.Error(t)
        } finally {
            checkGate.finish()
        }
    }

    private fun hasValidatedNetwork(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    private fun cachedHelperRelease(context: Context): HelperUpdateInfo? {
        val version = AppPreferences.latestHelperVersion(context) ?: return null
        val releaseUrl = AppPreferences.latestReleaseUrl(context) ?: return null
        return HelperUpdateInfo(
            versionName = version,
            versionCode = AppPreferences.latestHelperVersionCode(context),
            releaseUrl = releaseUrl,
            apkUrl = AppPreferences.latestHelperApkUrl(context),
            sha256 = AppPreferences.latestHelperSha256(context)
        )
    }

    private fun helperUpdateForRelease(
        context: Context,
        helper: HelperUpdateInfo?
    ): HelperUpdateInfo? {
        helper ?: return null
        val installed = runCatching {
            context.packageManager.getPackageInfo(HELPER_PACKAGE, 0)
        }.getOrNull() ?: return null

        return helper.takeIf {
            helperNeedsUpdate(
                installedVersionCode = installed.longVersionCode,
                installedVersionName = installed.versionName.orEmpty(),
                helper = helper
            )
        }
    }

    internal fun helperNeedsUpdate(
        installedVersionCode: Long,
        installedVersionName: String,
        helper: HelperUpdateInfo
    ): Boolean =
        helper.versionCode?.let { latestCode ->
            latestCode > installedVersionCode
        } ?: VersionComparator.isNewer(
            helper.versionName,
            installedVersionName
        )

    private fun cacheRelease(context: Context, release: UpdateInfo) {
        AppPreferences.setLatestRelease(
            context = context,
            version = release.versionName,
            versionCode = release.versionCode,
            url = release.releaseUrl,
            apkUrl = release.apkUrl,
            sha256 = release.sha256,
            helperVersion = release.helper?.versionName,
            helperVersionCode = release.helper?.versionCode,
            helperApkUrl = release.helper?.apkUrl,
            helperSha256 = release.helper?.sha256
        )
    }

    private fun fetchLatestStableRelease(): UpdateInfo =
        runCatching { fetchReleaseManifest(SleepManagerReleaseOrigin.RELEASE_MANIFEST) }
            .map { manifest ->
                if (manifest.helper != null) {
                    manifest
                } else {
                    runCatching { fetchLatestStableReleaseFromApi() }
                        .getOrNull()
                        ?.takeIf { it.versionName == manifest.versionName }
                        ?.let { apiRelease ->
                            manifest.copy(helper = apiRelease.helper)
                        }
                        ?: manifest
                }
            }
            .getOrElse { fetchLatestStableReleaseFromApi() }

    private fun fetchReleaseManifest(url: String): UpdateInfo {
        val connection = openJsonConnection(url)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException(
                    "Release manifest check failed with HTTP $status"
                )
            }

            val jsonText =
                connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)

            val versionName = json.getString("versionName")
            val versionCode = json.getLong("versionCode")
            val releaseUrl = json.getString("releaseUrl")
            val apkUrl = json.getString("apkUrl")
            val sha256 = normalizeSha256(json.getString("sha256"))

            validateReleaseUrl(releaseUrl)
            validateApkUrl(apkUrl)
            require(versionCode > 0L) { "Invalid release version code" }
            require(sha256.length == 64) { "Invalid release SHA-256" }

            val helper = if (json.has("helperApkUrl")) {
                val helperVersionName =
                    json.optString("helperVersionName", versionName)
                        .ifBlank { versionName }
                val helperVersionCode =
                    if (json.has("helperVersionCode")) {
                        json.optLong("helperVersionCode")
                            .takeIf { it > 0L }
                    } else {
                        null
                    }
                val helperApkUrl =
                    json.optString("helperApkUrl")
                        .takeIf { it.isNotBlank() }
                val helperSha256 =
                    json.optString("helperSha256")
                        .takeIf { it.isNotBlank() }
                        ?.let(::normalizeSha256)

                helperApkUrl?.let(::validateApkUrl)
                helperSha256?.let {
                    require(it.length == 64) { "Invalid Helper SHA-256" }
                }
                HelperUpdateInfo(
                    versionName = helperVersionName,
                    versionCode = helperVersionCode,
                    releaseUrl = releaseUrl,
                    apkUrl = helperApkUrl,
                    sha256 = helperSha256
                )
            } else {
                null
            }

            return UpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                releaseUrl = releaseUrl,
                apkUrl = apkUrl,
                sha256 = sha256,
                helper = helper
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchLatestStableReleaseFromApi(): UpdateInfo {
        val connection = openJsonConnection(SleepManagerReleaseOrigin.RELEASE_API)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException(
                    "GitHub release check failed with HTTP $status"
                )
            }

            val jsonText =
                connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)

            if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
                throw IllegalStateException("Latest release is not stable")
            }

            val tag = json.getString("tag_name")
            val version = tag.removePrefix("v")
            val releaseUrl = json.getString("html_url")
            validateReleaseUrl(releaseUrl)

            var apkUrl: String? = null
            var sha256: String? = null
            var helperApkUrl: String? = null
            var helperSha256: String? = null
            var helperVersionName: String? = null
            val expectedApkName = "SleepManager-$version.apk"
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val assetName = asset.optString("name")
                    val candidateUrl = asset.optString("browser_download_url")
                    val digest = asset.optString("digest")
                    val candidateSha =
                        if (digest.startsWith("sha256:", ignoreCase = true)) {
                            normalizeSha256(digest.substringAfter(':'))
                                .takeIf { it.length == 64 }
                        } else {
                            null
                        }

                    when {
                        assetName == expectedApkName -> {
                            runCatching { validateApkUrl(candidateUrl) }
                                .onSuccess { apkUrl = candidateUrl }
                            sha256 = candidateSha
                        }

                        assetName.startsWith("SleepManager-Helper-") &&
                            assetName.endsWith(".apk") -> {
                            val candidateHelperVersion =
                                assetName
                                    .removePrefix("SleepManager-Helper-")
                                    .removeSuffix(".apk")
                                    .takeIf { it.isNotBlank() }

                            if (candidateHelperVersion != null) {
                                runCatching { validateApkUrl(candidateUrl) }
                                    .onSuccess {
                                        helperApkUrl = candidateUrl
                                        helperVersionName = candidateHelperVersion
                                    }
                                helperSha256 = candidateSha
                            }
                        }
                    }
                }
            }

            val helper = helperApkUrl?.let { url ->
                HelperUpdateInfo(
                    versionName = helperVersionName ?: version,
                    releaseUrl = releaseUrl,
                    apkUrl = url,
                    sha256 = helperSha256
                )
            }

            return UpdateInfo(
                versionName = version,
                releaseUrl = releaseUrl,
                apkUrl = apkUrl,
                sha256 = sha256,
                helper = helper
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openJsonConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SleepManager/${BuildConfig.VERSION_NAME}")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    private fun validateReleaseUrl(url: String) {
        require(SleepManagerReleaseOrigin.isExpectedReleaseUrl(url)) {
            "Unexpected release URL"
        }
    }

    private fun validateApkUrl(url: String) {
        require(SleepManagerReleaseOrigin.isExpectedDownloadUrl(url)) {
            "Unexpected APK URL"
        }
    }

    private fun normalizeSha256(value: String): String =
        value
            .lowercase()
            .replace(":", "")
            .filter { it in '0'..'9' || it in 'a'..'f' }
}
