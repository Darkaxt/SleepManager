package com.med.sleepmanager.data

import com.med.sleepmanager.integration.connector.AppConnector
import com.med.sleepmanager.integration.connector.RaOfflineProxyConnector
import com.med.sleepmanager.integration.connector.SyncthingConnector
import com.med.sleepmanager.integration.connector.TailscaleConnector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the restore participant registry. The design-audit bugs were restores
 * that one path (wake, disable, Forget, completion) did not know about; every
 * path now derives from this list, and these tests pin its invariants.
 */
class RestoreParticipantsTest {
    private val all = RestoreParticipants.all

    // Every AppConnector implementation; a new connector must be added here
    // and to RestoreParticipants.
    private val appConnectors: List<AppConnector> = listOf(
        RaOfflineProxyConnector,
        SyncthingConnector,
        TailscaleConnector
    )

    @Test
    fun idsAreUnique() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun connectorsAreRegisteredWithMatchingRoute() {
        appConnectors.forEach { connector ->
            val participant = all.singleOrNull { it.id == connector.id }
            assertNotNull("${connector.id} is not a restore participant", participant)
            participant!!
            assertTrue(participant.cycleConnector)
            assertEquals(
                "${connector.id} restore route must match wakeRequiresNetwork",
                if (connector.wakeRequiresNetwork) RestoreRoute.AFTER_NETWORK else RestoreRoute.IMMEDIATE,
                participant.route
            )
        }
        assertEquals(
            appConnectors.map { it.id }.toSet(),
            all.filter { it.cycleConnector }.map { it.id }.toSet()
        )
    }

    @Test
    fun everyCycleParticipantIsCoveredByForget() {
        all.filter { it.scope == RestoreScope.SLEEP_CYCLE }.forEach { participant ->
            assertTrue(
                "${participant.id} would survive Forget",
                participant.forget != null || participant.cycleConnector
            )
        }
        // Connector ownership is erased with the cycle record on Forget.
        assertNotNull(all.single { it.id == RestoreParticipants.SLEEP_CYCLE_RECORD }.forget)
    }

    @Test
    fun ownedStateSurvivesForgetAndUsesItsOwnRetries() {
        val owned = all.filter { it.scope == RestoreScope.OWNED_STATE }
        assertEquals(
            setOf(
                RestoreParticipants.BATTERY_SAVER,
                RestoreParticipants.CHARGING_SEPARATION
            ),
            owned.map { it.id }.toSet()
        )
        owned.forEach {
            assertNull(it.forget)
            assertEquals(RestoreRoute.OWNED_STATE_RETRY, it.route)
            assertFalse(it.blocksCycleCompletion)
        }
    }

    @Test
    fun everyRestorableCycleParticipantBlocksCompletion() {
        all.filter { it.scope == RestoreScope.SLEEP_CYCLE && it.route != RestoreRoute.NONE }
            .forEach {
                assertTrue("${it.id} must block cycle completion", it.blocksCycleCompletion)
            }
    }

    @Test
    fun networkAndHelperRoutesMatchTheirServiceFlows() {
        // SleepManagerService.waitForNetworkAndRestorePendingConnectors restores
        // exactly these; a new AFTER_NETWORK participant must be wired there.
        assertEquals(
            setOf(SyncthingConnector.id, TailscaleConnector.id),
            RestoreParticipants.withRoute(RestoreRoute.AFTER_NETWORK).map { it.id }.toSet()
        )
        assertEquals(
            listOf(RestoreParticipants.HELPER),
            RestoreParticipants.withRoute(RestoreRoute.HELPER_RESULT).map { it.id }
        )
    }

    @Test
    fun forgetOrderMatchesPreviousBehaviour() {
        val forgetting =
            all.filter { it.scope == RestoreScope.SLEEP_CYCLE && it.forget != null }
                .map { it.id }
        assertEquals(
            listOf(
                RestoreParticipants.HELPER,
                RestoreParticipants.SLEEP_CYCLE_RECORD,
                RestoreParticipants.DIRECT_RADIO,
                RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE
            ),
            forgetting
        )
    }

    @Test
    fun retiredConnectorsAreNoLongerRegistered() {
        RestoreParticipants.RETIRED_CONNECTOR_IDS.forEach { id ->
            assertFalse("$id must not be a participant", all.any { it.id == id })
            assertFalse(RestoreParticipants.isRegisteredConnector(id))
        }
    }
}
