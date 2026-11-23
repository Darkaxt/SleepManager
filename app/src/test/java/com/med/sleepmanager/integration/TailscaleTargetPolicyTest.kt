package com.med.sleepmanager.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscaleTargetPolicyTest {
    private val bothInstalled =
        setOf(
            TailscaleTargetPolicy.OFFICIAL_PACKAGE,
            TailscaleTargetPolicy.TAILDNS_PACKAGE
        )

    @Test
    fun soleInstalledClientIsTheOnlyDisconnectCandidate() {
        assertEquals(
            listOf(TailscaleTargetPolicy.OFFICIAL_PACKAGE),
            TailscaleTargetPolicy.disconnectCandidates(
                installedPackages = setOf(TailscaleTargetPolicy.OFFICIAL_PACKAGE),
                tailscaleVpnPresent = true
            )
        )
        assertEquals(
            listOf(TailscaleTargetPolicy.TAILDNS_PACKAGE),
            TailscaleTargetPolicy.disconnectCandidates(
                installedPackages = setOf(TailscaleTargetPolicy.TAILDNS_PACKAGE),
                tailscaleVpnPresent = true
            )
        )
    }

    @Test
    fun bothInstalledClientsAreTriedInPreferenceOrder() {
        assertEquals(
            listOf(
                TailscaleTargetPolicy.TAILDNS_PACKAGE,
                TailscaleTargetPolicy.OFFICIAL_PACKAGE
            ),
            TailscaleTargetPolicy.disconnectCandidates(
                installedPackages = bothInstalled,
                tailscaleVpnPresent = true
            )
        )
    }

    @Test
    fun noTailscaleVpnMeansNoDisconnectCandidate() {
        assertTrue(
            TailscaleTargetPolicy.disconnectCandidates(
                installedPackages = bothInstalled,
                tailscaleVpnPresent = false
            ).isEmpty()
        )
    }

    @Test
    fun nextCandidateFollowsAnUnverifiedDisconnect() {
        assertEquals(
            TailscaleTargetPolicy.OFFICIAL_PACKAGE,
            TailscaleTargetPolicy.nextDisconnectCandidate(
                installedPackages = bothInstalled,
                attemptedPackage = TailscaleTargetPolicy.TAILDNS_PACKAGE
            )
        )
    }

    @Test
    fun noNextCandidateAfterTheLastOrOnlyClient() {
        assertNull(
            TailscaleTargetPolicy.nextDisconnectCandidate(
                installedPackages = bothInstalled,
                attemptedPackage = TailscaleTargetPolicy.OFFICIAL_PACKAGE
            )
        )
        assertNull(
            TailscaleTargetPolicy.nextDisconnectCandidate(
                installedPackages = setOf(TailscaleTargetPolicy.TAILDNS_PACKAGE),
                attemptedPackage = TailscaleTargetPolicy.TAILDNS_PACKAGE
            )
        )
    }

    @Test
    fun uninstalledAttemptedPackageHasNoNextCandidate() {
        assertNull(
            TailscaleTargetPolicy.nextDisconnectCandidate(
                installedPackages = setOf(TailscaleTargetPolicy.OFFICIAL_PACKAGE),
                attemptedPackage = TailscaleTargetPolicy.TAILDNS_PACKAGE
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
    fun installedLabelIsShownForSharedTailDnsPackage() {
        assertEquals(
            "TailD",
            TailscaleTargetPolicy.displayName(TailscaleTargetPolicy.TAILDNS_PACKAGE, "TailD")
        )
    }

    @Test
    fun staticNamesAreFallbackWhenNoLabelIsAvailable() {
        assertEquals(
            "Tailscale",
            TailscaleTargetPolicy.displayName(TailscaleTargetPolicy.OFFICIAL_PACKAGE, null)
        )
        assertEquals(
            "TailDNS",
            TailscaleTargetPolicy.displayName(TailscaleTargetPolicy.TAILDNS_PACKAGE, "  ")
        )
    }
}
