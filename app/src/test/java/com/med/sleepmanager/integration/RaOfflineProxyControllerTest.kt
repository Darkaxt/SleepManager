package com.med.sleepmanager.integration

import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyPendingAwardState
import com.med.sleepmanager.integration.raofflineproxy.RaOfflineProxyStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaOfflineProxyStatusParserTest {
    @Test
    fun parsesApiV2PendingAwards() {
        val status =
            RaOfflineProxyStatusParser.parse(
                """
                {
                  "version":2,
                  "running":true,
                  "shouldBeRunning":true,
                  "online":true,
                  "queue":{"count":0,"state":"idle","nextWindowAt":null},
                  "pendingAwards":{"count":3,"state":"syncing","error":null}
                }
                """.trimIndent()
            )

        assertEquals(2, status?.version)
        assertTrue(status?.online == true)
        assertEquals(3, status?.pendingAwards?.count)
        assertEquals(
            RaOfflineProxyPendingAwardState.SYNCING,
            status?.pendingAwards?.state
        )
        assertNull(status?.pendingAwards?.error)
    }

    @Test
    fun apiV1WithoutPendingAwardsRemainsValid() {
        val status =
            RaOfflineProxyStatusParser.parse(
                """
                {
                  "version":1,
                  "running":true,
                  "shouldBeRunning":true,
                  "online":false,
                  "queue":{"count":0,"state":"idle","nextWindowAt":null}
                }
                """.trimIndent()
            )

        assertEquals(1, status?.version)
        assertNull(status?.pendingAwards)
    }

    @Test
    fun parsesBlockedPendingAwardError() {
        val status =
            RaOfflineProxyStatusParser.parse(
                """
                {
                  "version":2,
                  "running":true,
                  "shouldBeRunning":true,
                  "online":true,
                  "queue":{"count":0,"state":"idle","nextWindowAt":null},
                  "pendingAwards":{"count":1,"state":"blocked","error":"upload_failed"}
                }
                """.trimIndent()
            )

        assertEquals(
            RaOfflineProxyPendingAwardState.BLOCKED,
            status?.pendingAwards?.state
        )
        assertEquals("upload_failed", status?.pendingAwards?.error)
    }
}
