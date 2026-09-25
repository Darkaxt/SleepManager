package com.med.sleepmanager.update

object SleepManagerReleaseOrigin {
    const val REPOSITORY_URL = "https://github.com/Darkaxt/SleepManager"
    const val RELEASES_URL = "$REPOSITORY_URL/releases"
    const val RELEASE_API =
        "https://api.github.com/repos/Darkaxt/SleepManager/releases/latest"
    const val RELEASE_MANIFEST = "$RELEASES_URL/latest/download/update.json"

    private const val RELEASE_PREFIX = "$RELEASES_URL/"
    private const val DOWNLOAD_PREFIX = "$RELEASES_URL/download/"

    fun isExpectedReleaseUrl(url: String): Boolean =
        url.startsWith(RELEASE_PREFIX)

    fun isExpectedDownloadUrl(url: String): Boolean =
        url.startsWith(DOWNLOAD_PREFIX)
}
