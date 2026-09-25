package com.med.sleepmanager.integration

object TailscaleTargetPolicy {
    const val OFFICIAL_PACKAGE = "com.tailscale.ipn"
    const val TAILDNS_PACKAGE = "io.github.darkaxt.taildns"

    val supportedPackages: Set<String> =
        linkedSetOf(TAILDNS_PACKAGE, OFFICIAL_PACKAGE)

    fun selectActivePackage(
        installedPackages: Set<String>,
        activeOwnerPackages: Set<String>,
        tailscaleVpnPresent: Boolean
    ): String? {
        if (!tailscaleVpnPresent) return null

        val installedSupported = supportedPackages.filterTo(linkedSetOf()) {
            it in installedPackages
        }
        val activeSupported = installedSupported.filter { it in activeOwnerPackages }

        return when {
            activeSupported.size == 1 -> activeSupported.single()
            installedSupported.size == 1 -> installedSupported.single()
            else -> null
        }
    }

    fun selectPreferredInstalledPackage(installedPackages: Set<String>): String? =
        supportedPackages.firstOrNull { it in installedPackages }

    fun isSupported(packageName: String): Boolean =
        packageName in supportedPackages

    fun disconnectAction(packageName: String): String =
        controlAction(packageName, "DISCONNECT_VPN")

    fun connectAction(packageName: String): String =
        controlAction(packageName, "CONNECT_VPN")

    fun displayName(packageName: String): String =
        when (packageName) {
            OFFICIAL_PACKAGE -> "Tailscale"
            TAILDNS_PACKAGE -> "TailDNS"
            else -> requireSupported(packageName)
        }

    private fun controlAction(packageName: String, suffix: String): String {
        if (!isSupported(packageName)) requireSupported(packageName)
        return "$packageName.$suffix"
    }

    private fun requireSupported(packageName: String): Nothing {
        throw IllegalArgumentException("Unsupported Tailscale client package: $packageName")
    }
}

object TailscaleTransactionToken {
    private const val LEGACY_VERIFY_DISCONNECT = "verify_disconnect"
    private const val LEGACY_RESTORE = "restore"
    private const val VERIFY_DISCONNECT_PREFIX = "verify_disconnect:"
    private const val RESTORE_PREFIX = "restore:"

    fun disconnectVerification(packageName: String): String {
        require(TailscaleTargetPolicy.isSupported(packageName)) {
            "Unsupported Tailscale client package: $packageName"
        }
        return "$VERIFY_DISCONNECT_PREFIX$packageName"
    }

    fun restore(packageName: String): String {
        require(TailscaleTargetPolicy.isSupported(packageName)) {
            "Unsupported Tailscale client package: $packageName"
        }
        return "$RESTORE_PREFIX$packageName"
    }

    fun disconnectTarget(token: String?): String? =
        when {
            token == LEGACY_VERIFY_DISCONNECT -> TailscaleTargetPolicy.OFFICIAL_PACKAGE
            token?.startsWith(VERIFY_DISCONNECT_PREFIX) == true ->
                supportedSuffix(token, VERIFY_DISCONNECT_PREFIX)
            else -> null
        }

    fun restoreTarget(token: String?): String? =
        when {
            token == LEGACY_RESTORE -> TailscaleTargetPolicy.OFFICIAL_PACKAGE
            token?.startsWith(RESTORE_PREFIX) == true ->
                supportedSuffix(token, RESTORE_PREFIX)
            else -> null
        }

    private fun supportedSuffix(token: String, prefix: String): String? =
        token.removePrefix(prefix).takeIf(TailscaleTargetPolicy::isSupported)
}
