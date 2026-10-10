package com.med.sleepmanager.ui.activity

import com.med.sleepmanager.data.EventHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTimelineBuilderTest {
    @Test
    fun groupsSleepAndWakeAndKeepsStepsChronological() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        timestamp = 400L,
                        message =
                            "Wake → Wi-Fi restored to previous state · Bluetooth restored to previous state"
                    ),
                    EventHistoryStore.Event(
                        timestamp = 300L,
                        message = "Wake → BasicSync restored · RUNNING"
                    ),
                    EventHistoryStore.Event(
                        timestamp = 200L,
                        message = "Sleep → Wi-Fi off · Bluetooth off"
                    ),
                    EventHistoryStore.Event(
                        timestamp = 100L,
                        message = "Sleep → Tailscale disconnected"
                    )
                )
            )

        assertEquals(2, groups.size)
        assertEquals(ActivityTimelineKind.WAKE, groups[0].kind)
        assertEquals(
            listOf(
                "BasicSync restored",
                "Wi-Fi restored",
                "Bluetooth restored"
            ),
            groups[0].steps.map { it.text }
        )

        assertEquals(ActivityTimelineKind.SLEEP, groups[1].kind)
        assertEquals(
            listOf(
                "Tailscale disconnected",
                "Wi-Fi turned off",
                "Bluetooth turned off"
            ),
            groups[1].steps.map { it.text }
        )
    }

    @Test
    fun hidesTechnicalConfirmationButKeepsRawSequenceUseful() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Sleep → RAOfflineProxy STOP accepted · restore owned"
                    ),
                    EventHistoryStore.Event(
                        110L,
                        "Sleep → RAOfflineProxy gate complete · STOP confirmed"
                    ),
                    EventHistoryStore.Event(
                        120L,
                        "Sleep → Wi-Fi off"
                    )
                )
            )

        assertEquals(1, groups.size)
        assertEquals(
            listOf(
                "RAOfflineProxy stopped",
                "Wi-Fi turned off"
            ),
            groups.single().steps.map { it.text }
        )
    }

    @Test
    fun hidesRadioBackendAndExternalPowerImplementationDetails() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Sleep → Battery Saver deferred · external power · " +
                            "Wi-Fi off · Bluetooth off · radio=PServer"
                    )
                )
            )

        assertEquals(
            listOf(
                "Battery Saver not used while charging",
                "Wi-Fi turned off",
                "Bluetooth turned off"
            ),
            groups.single().steps.map { it.text }
        )
    }

    @Test
    fun directRadioFailureUsesPlainLanguageWithoutBackendName() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Sleep → Wi-Fi unchanged · Bluetooth unchanged · " +
                            "radio=PServer · radioError"
                    )
                )
            )

        assertEquals(
            listOf(
                "Wi-Fi already in the right state",
                "Bluetooth already in the right state",
                "Wi-Fi and Bluetooth could not be changed"
            ),
            groups.single().steps.map { it.text }
        )
    }

    @Test
    fun pendingRestoresHideTechnicalBackendDetails() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Wake → PServer radios restore pending"
                    ),
                    EventHistoryStore.Event(
                        110L,
                        "Wake → RAOfflineProxy restore pending · endpoint timeout"
                    ),
                    EventHistoryStore.Event(
                        120L,
                        "Recovery → PServer sleep radio state re-apply failed"
                    )
                )
            )

        val texts = groups.flatMap { it.steps }.map { it.text }
        assertTrue("Wi-Fi and Bluetooth could not be restored yet" in texts)
        assertTrue("RAOfflineProxy could not be restored yet" in texts)
        assertTrue(
            "Wi-Fi and Bluetooth sleep state could not be restored" in texts
        )
        assertTrue(texts.none { "PServer" in it })
        assertTrue(texts.none { "endpoint" in it })
    }

    @Test
    fun recoveryMessagesUsePlainLanguage() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Helper reconciliation → replayed cycle 42 attempt 1"
                    ),
                    EventHistoryStore.Event(
                        110L,
                        "Helper reconciliation → no acknowledgement after 3 attempts"
                    )
                )
            )

        assertEquals(ActivityTimelineKind.RECOVERY, groups.single().kind)
        assertTrue(
            groups.single().steps.map { it.text }.containsAll(
                listOf(
                    "Radio restoration retried",
                    "Radio restoration could not be confirmed"
                )
            )
        )
    }
    @Test
    fun confirmedSyncthingIsShownBeforeDisruptiveSleepActions() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Sleep → RAOfflineProxy STOP accepted · restore owned"
                    ),
                    EventHistoryStore.Event(
                        110L,
                        "Sleep → Syncthing STOP confirmed"
                    ),
                    EventHistoryStore.Event(
                        120L,
                        "Sleep → Battery Saver deferred · external power"
                    ),
                    EventHistoryStore.Event(
                        130L,
                        "Sleep → Wi-Fi off · Bluetooth off · Syncthing STOP confirmed · radio=PServer"
                    )
                )
            )

        assertEquals(
            listOf(
                "RAOfflineProxy stopped",
                "Syncthing paused",
                "Battery Saver not used while charging",
                "Wi-Fi turned off",
                "Bluetooth turned off"
            ),
            groups.single().steps.map { it.text }
        )
    }

    @Test
    fun legacyCombinedSleepSummaryMovesSyncthingAheadOfRadios() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Sleep → Wi-Fi off · Bluetooth off · Syncthing STOP confirmed · radio=PServer"
                    )
                )
            )

        assertEquals(
            listOf(
                "Syncthing paused",
                "Wi-Fi turned off",
                "Bluetooth turned off"
            ),
            groups.single().steps.map { it.text }
        )
    }

    @Test
    fun trimCallbackIsNotPresentedAsLowMemory() {
        val groups =
            ActivityTimelineBuilder.build(
                listOf(
                    EventHistoryStore.Event(
                        100L,
                        "Memory trim → onTrimMemory level=15"
                    ),
                    EventHistoryStore.Event(
                        200L,
                        "Memory pressure → Android lowMemory=true · onLowMemory"
                    )
                )
            )

        assertEquals(1, groups.size)
        assertEquals(
            listOf(
                "Android requested memory trim · level 15",
                "Android reported low memory"
            ),
            groups.single().steps.map { it.text }
        )
    }

}
