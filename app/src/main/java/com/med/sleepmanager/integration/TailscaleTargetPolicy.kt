package com.med.sleepmanager.integration

object TailscaleTargetPolicy {
    const val OFFICIAL_PACKAGE = "com.tailscale.ipn"
    const val TAILDNS_PACKAGE = "io.github.darkaxt.taildns"

    val supportedPackages: Set<String> =
        linkedSetOf(TAILDNS_PACKAGE, OFFICIAL_PACKAGE)

    /**
     * Ordered clients that may own the active Tailscale VPN.
     *
     * Android redacts the VPN owner UID from NetworkCapabilities for every app
     * except the VPN owner, so SleepManager cannot read which client owns the
     * tunnel. Instead it tries the installed clients in preference order and
     * attributes ownership to the client whose DISCONNECT actually removes the
     * Tailscale VPN. With one installed client the list has one entry.
     */
    fun disconnectCandidates(
        installedPackages: Set<String>,
        tailscaleVpnPresent: Boolean
    ): List<String> {
        if (!tailscaleVpnPresent) return emptyList()
        return supportedPackages.filter { it in installedPackages }
    }

    /**
     * The installed candidate tried after [attemptedPackage], or null when every
     * candidate has been tried. Order matches [disconnectCandidates].
     */
    fun nextDisconnectCandidate(
        installedPackages: Set<String>,
        attemptedPackage: String
    ): String? {
        val candidates = supportedPackages.filter { it in installedPackages }
        val index = candidates.indexOf(attemptedPackage)
        if (index < 0) return null
        return candidates.getOrNull(index + 1)
    }

    fun selectPreferredInstalledPackage(installedPackages: Set<String>): String? =
        supportedPackages.firstOrNull { it in installedPackages }

    fun isSupported(packageName: String): Boolean =
        packageName in supportedPackages

    fun disconnectAction(packageName: String): String =
        controlAction(packageName, "DISCONNECT_VPN")

    fun connectAction(packageName: String): String =
        controlAction(packageName, "CONNECT_VPN")

    /**
     * The installed app's own label, falling back to a static name. The
     * io.github.darkaxt.taildns package is shipped by both TailDNS and TailD,
     * so the static name is only used when PackageManager has no label.
     */
    fun displayName(packageName: String, installedLabel: String?): String =
        installedLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackDisplayName(packageName)

    fun fallbackDisplayName(packageName: String): String =
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
