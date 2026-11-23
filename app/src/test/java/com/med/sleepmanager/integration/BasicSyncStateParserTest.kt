package com.med.sleepmanager.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BasicSyncStateParserTest {
    private val action = "com.chiller3.basicsync.STATE_CHANGED"

    private val counterKeys = listOf(
        "folders_idle_count",
        "folders_scanning_count",
        "folders_syncing_count",
        "folders_cleaning_count",
        "folders_errored_count",
        "folders_starting_count",
        "devices_connected_count",
        "devices_syncing_count",
        "devices_pending_count"
    )

    private fun full(): MutableMap<String, Any?> =
        mutableMapOf<String, Any?>(
            "mode" to "AUTO_MODE",
            "run_state" to "RUNNING",
            "blocked_reasons" to arrayOf<String>()
        ).apply {
            counterKeys.forEachIndexed { index, key -> put(key, index + 1) }
        }

    private fun parse(values: Map<String, Any?>, act: String? = action) =
        BasicSyncController.parseStateValues(act, values)

    @Test
    fun validFullPayloadParses() {
        val state = parse(full())
        assertNotNull(state)
        assertEquals(BasicSyncController.Mode.AUTO_MODE, state!!.mode)
        assertEquals(1, state.syncCounters!!.foldersIdle)
        assertEquals(9, state.syncCounters!!.devicesPending)
    }

    @Test
    fun legacyPayloadWithoutCountersParses() {
        val state = parse(mapOf("mode" to "MANUAL_MODE_STOPPED", "run_state" to "PAUSED"))
        assertNotNull(state)
        assertNull(state!!.syncCounters)
        assertEquals(emptySet<String>(), state.blockedReasons)
    }

    @Test
    fun wrongActionOrUnknownEnumsAreRejected() {
        assertNull(parse(full(), act = "com.example.STATE_CHANGED"))
        assertNull(parse(full().apply { put("mode", "SOMETHING") }))
        assertNull(parse(full().apply { put("run_state", "running") }))
        assertNull(parse(full().apply { put("mode", 1) }))
        assertNull(parse(full().apply { remove("run_state") }))
    }

    @Test
    fun partialOrInvalidCountersRejectTheWholePayload() {
        assertNull(parse(full().apply { remove("devices_pending_count") }))
        assertNull(parse(full().apply { put("folders_syncing_count", -1) }))
        assertNull(parse(full().apply { put("folders_syncing_count", "0") }))
        assertNull(parse(full().apply { put("folders_idle_count", 2_000_000) }))
    }

    @Test
    fun malformedBlockedReasonsAreRejected() {
        assertNull(parse(full().apply { put("blocked_reasons", "MANUAL") }))
        assertNull(parse(full().apply { put("blocked_reasons", arrayOf<Any?>("MANUAL", null)) }))
        assertNull(parse(full().apply { put("blocked_reasons", arrayOf("")) }))
        assertNull(parse(full().apply { put("blocked_reasons", Array(33) { "R$it" }) }))
        assertNull(parse(full().apply { put("blocked_reasons", arrayOf("X".repeat(65))) }))
        assertEquals(
            setOf("METERED_NETWORK"),
            parse(full().apply { put("blocked_reasons", arrayOf("METERED_NETWORK")) })!!
                .blockedReasons
        )
    }
}
