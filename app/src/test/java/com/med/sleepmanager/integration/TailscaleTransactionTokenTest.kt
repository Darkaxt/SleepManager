package com.med.sleepmanager.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TailscaleTransactionTokenTest {
    @Test
    fun tailDnsDisconnectTokenRoundTripsToSamePackage() {
        val token =
            TailscaleTransactionToken.disconnectVerification(
                TailscaleTargetPolicy.TAILDNS_PACKAGE
            )

        assertEquals(
            TailscaleTargetPolicy.TAILDNS_PACKAGE,
            TailscaleTransactionToken.disconnectTarget(token)
        )
    }

    @Test
    fun tailDnsRestoreTokenRoundTripsToSamePackage() {
        val token = TailscaleTransactionToken.restore(TailscaleTargetPolicy.TAILDNS_PACKAGE)

        assertEquals(
            TailscaleTargetPolicy.TAILDNS_PACKAGE,
            TailscaleTransactionToken.restoreTarget(token)
        )
    }

    @Test
    fun legacyTokensRemainOfficialTailscaleTransactions() {
        assertEquals(
            TailscaleTargetPolicy.OFFICIAL_PACKAGE,
            TailscaleTransactionToken.disconnectTarget("verify_disconnect")
        )
        assertEquals(
            TailscaleTargetPolicy.OFFICIAL_PACKAGE,
            TailscaleTransactionToken.restoreTarget("restore")
        )
    }

    @Test
    fun unknownAndMalformedPackagesAreRejected() {
        assertNull(TailscaleTransactionToken.disconnectTarget("verify_disconnect:other.vpn"))
        assertNull(TailscaleTransactionToken.restoreTarget("restore:other.vpn"))
        assertNull(TailscaleTransactionToken.restoreTarget("restore:"))
        assertNull(TailscaleTransactionToken.restoreTarget("verify_disconnect"))
    }
}
