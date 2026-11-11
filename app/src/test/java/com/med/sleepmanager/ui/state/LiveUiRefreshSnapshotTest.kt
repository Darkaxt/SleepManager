package com.med.sleepmanager.ui.state

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveUiRefreshSnapshotTest {
    private val initial = UiRefreshSignature(
        metadata = listOf("installed", true),
        dashboard = listOf(80, false),
        stats = "80.01%",
        events = listOf("Wake")
    )

    @Test fun firstRefreshUpdatesAllSections() {
        assertEquals(
            UiRefreshChanges(true, true, true, true),
            initial.changesSince(null)
        )
    }

    @Test fun identicalThreeSecondPollDoesNotInvalidateAnything() {
        val identical = initial.copy(
            metadata = listOf("installed", true),
            dashboard = listOf(80, false),
            stats = "80.01%",
            events = listOf("Wake")
        )
        assertEquals(
            UiRefreshChanges(false, false, false, false),
            identical.changesSince(initial)
        )
    }

    @Test fun updatingOneSectionDoesNotInvalidateUnrelatedSections() {
        assertEquals(
            UiRefreshChanges(false, false, true, false),
            initial.copy(stats = "80.02%").changesSince(initial)
        )
        assertEquals(
            UiRefreshChanges(false, false, false, true),
            initial.copy(events = listOf("Wake", "Sleep")).changesSince(initial)
        )
        assertEquals(
            UiRefreshChanges(true, false, false, false),
            initial.copy(metadata = listOf("installed", false)).changesSince(initial)
        )
        assertEquals(
            UiRefreshChanges(false, true, false, false),
            initial.copy(dashboard = listOf(79, false)).changesSince(initial)
        )
    }
}
