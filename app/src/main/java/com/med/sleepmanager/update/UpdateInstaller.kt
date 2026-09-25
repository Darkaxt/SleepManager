package com.med.sleepmanager.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class UpdateDownloadResult {
    data class Success(
        val apk: File,
        val versionName: String,
        val versionCode: Long
    ) : UpdateDownloadResult()

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : UpdateDownloadResult()
}

object UpdateInstaller {
    private const val EXPECTED_PACKAGE = "com.med.sleepmanager"
    private const val EXPECTED_HELPER_PACKAGE = "com.med.sleepmanager.helper"
    private const val EXPECTED_SIGNER_SHA256 =
        "87c2f2f5d5dac021d3ee26bb6b361f722410122e2dedc465a37e5b9e0276ecec"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L

    fun downloadAndVerify(
        context: Context,
        update: UpdateInfo
    ): UpdateDownloadResult =
        downloadAndVerifyTarget(
            context = context,
            versionName = update.versionName,
            versionCode = update.versionCode,
            apkUrl = update.apkUrl,
            expectedSha = update.sha256,
            expectedPackage = EXPECTED_PACKAGE,
            installedPackage = context.packageName,
            allowFreshInstall = false,
            fileName = "SleepManager-${update.versionName}.apk"
        )

    fun downloadAndVerifyHelper(
        context: Context,
        update: HelperUpdateInfo
    ): UpdateDownloadResult =
        downloadAndVerifyTarget(
            context = context,
            versionName = update.versionName,
            versionCode = update.versionCode,
            apkUrl = update.apkUrl,
            expectedSha = update.sha256,
            expectedPackage = EXPECTED_HELPER_PACKAGE,
            installedPackage = EXPECTED_HELPER_PACKAGE,
            allowFreshInstall = true,
            fileName = "SleepManager-Helper-${update.versionName}.apk"
        )

    private fun downloadAndVerifyTarget(
        context: Context,
        versionName: String,
        versionCode: Long?,
        apkUrl: String?,
        expectedSha: String?,
        expectedPackage: String,
        installedPackage: String,
        allowFreshInstall: Boolean,
        fileName: String
    ): UpdateDownloadResult {
        apkUrl
            ?: return UpdateDownloadResult.Failure(
                "Direct download is unavailable for this release."
            )
        expectedSha
            ?: return UpdateDownloadResult.Failure(
                "This release does not include a trusted SHA-256 digest."
            )

        if (
            !SleepManagerReleaseOrigin.isExpectedDownloadUrl(apkUrl)
        ) {
            return UpdateDownloadResult.Failure("Unexpected update download URL.")
        }

        val updateDir = File(context.cacheDir, "updates")
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            return UpdateDownloadResult.Failure(
                "Unable to create the update download folder."
            )
        }

        val finalFile = File(updateDir, fileName)
        val partialFile = File(updateDir, "${finalFile.name}.part")
        partialFile.delete()

        return try {
            val actualSha = downloadApk(apkUrl, partialFile)
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                partialFile.delete()
                return UpdateDownloadResult.Failure(
                    "Downloaded APK failed SHA-256 verification."
                )
            }

            val verification = verifyArchive(
                context = context,
                apk = partialFile,
                versionName = versionName,
                versionCode = versionCode,
                expectedPackage = expectedPackage,
                installedPackage = installedPackage,
                allowFreshInstall = allowFreshInstall
            )
            if (verification is UpdateDownloadResult.Failure) {
                partialFile.delete()
                return verification
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partialFile.renameTo(finalFile)) {
                partialFile.copyTo(finalFile, overwrite = true)
                partialFile.delete()
            }

            val success = verification as UpdateDownloadResult.Success
            UpdateDownloadResult.Success(
                apk = finalFile,
                versionName = success.versionName,
                versionCode = success.versionCode
            )
        } catch (t: Throwable) {
            partialFile.delete()
            UpdateDownloadResult.Failure(
                message = t.message ?: "Unable to download the update.",
                cause = t
            )
        }
    }

    private fun downloadApk(url: String, destination: File): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SleepManager updater")
            setRequestProperty("Accept", "application/vnd.android.package-archive")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("APK download failed with HTTP $status")
            }

            val declaredSize = connection.contentLengthLong
            if (declaredSize > MAX_APK_BYTES) {
                throw IllegalStateException("Update APK is unexpectedly large")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_APK_BYTES) {
                            throw IllegalStateException(
                                "Update APK exceeded the maximum allowed size"
                            )
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }

            if (totalBytes <= 0L) {
                throw IllegalStateException("Downloaded APK is empty")
            }

            return digest.digest().toHex()
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyArchive(
        context: Context,
        apk: File,
        versionName: String,
        versionCode: Long?,
        expectedPackage: String,
        installedPackage: String,
        allowFreshInstall: Boolean
    ): UpdateDownloadResult {
        val packageManager = context.packageManager
        val archiveInfo =
            packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
                ?: return UpdateDownloadResult.Failure(
                    "Android could not read the downloaded APK."
                )

        if (archiveInfo.packageName != expectedPackage) {
            return UpdateDownloadResult.Failure(
                "Downloaded APK has the wrong package name."
            )
        }

        val downloadedVersionName = archiveInfo.versionName.orEmpty()
        if (downloadedVersionName != versionName) {
            return UpdateDownloadResult.Failure(
                "Downloaded APK version does not match the GitHub release."
            )
        }

        val installedInfo = runCatching {
            packageManager.getPackageInfo(installedPackage, 0)
        }.getOrNull()
        if (installedInfo == null && !allowFreshInstall) {
            return UpdateDownloadResult.Failure(
                "The app to update is not installed."
            )
        }

        val downloadedVersionCode = archiveInfo.longVersionCode
        if (
            installedInfo != null &&
            downloadedVersionCode <= installedInfo.longVersionCode
        ) {
            return UpdateDownloadResult.Failure(
                "Downloaded APK is not newer than the installed build."
            )
        }

        versionCode?.let { expectedVersionCode ->
            if (downloadedVersionCode != expectedVersionCode) {
                return UpdateDownloadResult.Failure(
                    "Downloaded APK version code does not match update metadata."
                )
            }
        }

        val signers = archiveInfo.signingInfo?.apkContentsSigners.orEmpty()
        if (signers.isEmpty()) {
            return UpdateDownloadResult.Failure(
                "Downloaded APK has no readable signing certificate."
            )
        }

        val signerMatches = signers.any { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .toHex()
                .equals(EXPECTED_SIGNER_SHA256, ignoreCase = true)
        }

        if (!signerMatches) {
            return UpdateDownloadResult.Failure(
                "Downloaded APK is not signed by the SleepManager release key."
            )
        }

        return UpdateDownloadResult.Success(
            apk = apk,
            versionName = downloadedVersionName,
            versionCode = downloadedVersionCode
        )
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }
}
