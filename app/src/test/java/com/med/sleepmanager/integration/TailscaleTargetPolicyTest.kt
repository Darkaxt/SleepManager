package com.med.sleepmanager.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TailscaleTargetPolicyTest {
    @Test
    fun activeOfficialClientWinsWhenBothClientsAreInstalled() {
        assertEquals(
            TailscaleTargetPolicy.OFFICIAL_PACKAGE,
            TailscaleTargetPolicy.selectActivePackage(
                installedPackages =
                    setOf(
                        TailscaleTargetPolicy.OFFICIAL_PACKAGE,
                        TailscaleTargetPolicy.TAILDNS_PACKAGE
                    ),
                activeOwnerPackages = setOf(TailscaleTargetPolicy.OFFICIAL_PACKAGE),
                tailscaleVpnPresent = true
            )
        )
    }

    @Test
    fun activeTailDnsClientWinsWhenBothClientsAreInstalled() {
        assertEquals(
            TailscaleTargetPolicy.TAILDNS_PACKAGE,
            TailscaleTargetPolicy.selectActivePackage(
                installedPackages =
                    setOf(
                        TailscaleTargetPolicy.OFFICIAL_PACKAGE,
                        TailscaleTargetPolicy.TAILDNS_PACKAGE
                    ),
                activeOwnerPackages = setOf(TailscaleTargetPolicy.TAILDNS_PACKAGE),
                tailscaleVpnPresent = true
            )
        )
    }

    @Test
    fun soleInstalledClientIsSafeFallbackWhenVpnOwnerIsUnavailable() {
        assertEquals(
            TailscaleTargetPolicy.TAILDNS_PACKAGE,
            TailscaleTargetPolicy.selectActivePackage(
                installedPackages = setOf(TailscaleTargetPolicy.TAILDNS_PACKAGE),
                activeOwnerPackages = emptySet(),
                tailscaleVpnPresent = true
            )
        )
    }

    @Test
    fun ambiguousInstalledClientsWithoutOwnerAreNotControlled() {
        assertNull(
            TailscaleTargetPolicy.selectActivePackage(
                installedPackages =
                    setOf(
                        TailscaleTargetPolicy.OFFICIAL_PACKAGE,
                        TailscaleTargetPolicy.TAILDNS_PACKAGE
                    ),
                activeOwnerPackages = emptySet(),
                tailscaleVpnPresent = true
            )
        )
    }

    @Test
    fun noTailscaleVpnMeansNoActiveTarget() {
        assertNull(
            TailscaleTargetPolicy.selectActivePackage(
                installedPackages = setOf(TailscaleTargetPolicy.TAILDNS_PACKAGE),
                activeOwnerPackages = setOf(TailscaleTargetPolicy.TAILDNS_PACKAGE),
                tailscaleVpnPresent = false
            )
        )
    }

    @Test
    fun tailDnsIsPreferredForNonTransactionalUiWhenBothAreInstalled() {
        assertEquals(
            TailscaleTargetPolicy.TAILDNS_PACKAGE,
            TailscaleTargetPolicy.selectPreferredInstalledPackage(
                setOf(
                    TailscaleTargetPolicy.OFFICIAL_PACKAGE,
                    TailscaleTargetPolicy.TAILDNS_PACKAGE
                )
            )
        )
    }

    @Test
    fun tailDnsControlActionsUseTailDnsApplicationId() {
        assertEquals(
            "io.github.darkaxt.taildns.DISCONNECT_VPN",
            TailscaleTargetPolicy.disconnectAction(TailscaleTargetPolicy.TAILDNS_PACKAGE)
        )
        assertEquals(
            "io.github.darkaxt.taildns.CONNECT_VPN",
            TailscaleTargetPolicy.connectAction(TailscaleTargetPolicy.TAILDNS_PACKAGE)
        )
    }

    @Test
    fun supportedPackagesHaveTruthfulDisplayNames() {
        assertEquals(
            "Tailscale",
            TailscaleTargetPolicy.displayName(TailscaleTargetPolicy.OFFICIAL_PACKAGE)
        )
        assertEquals(
            "TailDNS",
            TailscaleTargetPolicy.displayName(TailscaleTargetPolicy.TAILDNS_PACKAGE)
        )
    }
}
