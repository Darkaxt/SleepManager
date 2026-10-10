package com.med.sleepmanager.ui.activity

import com.med.sleepmanager.data.EventHistoryStore

internal enum class ActivityTimelineKind {
    SLEEP,
    WAKE,
    MAINTENANCE,
    PROTECTION,
    RECOVERY,
    SYSTEM
}

internal data class ActivityTimelineStep(
    val timestamp: Long,
    val text: String
)

internal data class ActivityTimelineGroup(
    val kind: ActivityTimelineKind,
    val startedAt: Long,
    val endedAt: Long,
    val steps: List<ActivityTimelineStep>
)

/**
 * Converts the raw diagnostic event stream into a user-facing timeline.
 *
 * Raw EventHistoryStore messages are intentionally left untouched so Copy log
 * and diagnostics retain the full technical detail. This formatter is UI-only.
 */
internal object ActivityTimelineBuilder {
    private const val GROUP_GAP_MS = 5L * 60L * 1000L

    fun build(
        events: List<EventHistoryStore.Event>
    ): List<ActivityTimelineGroup> {
        if (events.isEmpty()) return emptyList()

        data class MutableGroup(
            val kind: ActivityTimelineKind,
            val startedAt: Long,
            var endedAt: Long,
            val steps: MutableList<ActivityTimelineStep>
        )

        val groups = mutableListOf<MutableGroup>()

        events
            .sortedBy { it.timestamp }
            .forEach { event ->
                val texts = friendlyTexts(event.message)
                if (texts.isEmpty()) return@forEach

                val kind = kindFor(event.message)
                val previous = groups.lastOrNull()
                val group =
                    if (
                        previous != null &&
                        previous.kind == kind &&
                        event.timestamp - previous.endedAt <= GROUP_GAP_MS
                    ) {
                        previous
                    } else {
                        MutableGroup(
                            kind = kind,
                            startedAt = event.timestamp,
                            endedAt = event.timestamp,
                            steps = mutableListOf()
                        ).also(groups::add)
                    }

                group.endedAt = event.timestamp
                texts.forEach { text ->
                    if (group.steps.none { it.text == text }) {
                        group.steps +=
                            ActivityTimelineStep(
                                timestamp = event.timestamp,
                                text = text
                            )
                    }
                }
            }

        return groups
            .map {
                ActivityTimelineGroup(
                    kind = it.kind,
                    startedAt = it.startedAt,
                    endedAt = it.endedAt,
                    steps = it.steps.toList()
                )
            }
            .asReversed()
    }

    private fun kindFor(message: String): ActivityTimelineKind =
        when {
            message.startsWith("Sleep →") ||
                message.startsWith("Sleep skipped →") ||
                message.startsWith("Pre-sleep sync →") ||
                message.startsWith("Sleep power ") ->
                ActivityTimelineKind.SLEEP

            message.startsWith("Wake →") ||
                message.startsWith("Wake sync →") ->
                ActivityTimelineKind.WAKE

            message.startsWith("Periodic sync") ->
                ActivityTimelineKind.MAINTENANCE

            message.startsWith("Closed-lid") ||
                message.startsWith("Lid closed") ||
                message.startsWith("Dock →") ||
                message.startsWith("Protection →") ->
                ActivityTimelineKind.PROTECTION

            message.startsWith("Recovery →") ||
                message.startsWith("Helper reconciliation →") ->
                ActivityTimelineKind.RECOVERY

            else -> ActivityTimelineKind.SYSTEM
        }

    private fun friendlyTexts(message: String): List<String> {
        when {
            message.startsWith("Helper result ignored · stale cycle") ->
                return emptyList()

            message.startsWith("Sleep → RAOfflineProxy gate complete") ->
                return emptyList()

            message.startsWith("SleepManager enabled • Quick Settings") ->
                return listOf("SleepManager enabled from Quick Settings")

            message == "SleepManager enabled" ->
                return listOf("SleepManager enabled")

            message == "SleepManager disabled" ->
                return listOf("SleepManager disabled")

            message.startsWith("Setup finished") ->
                return listOf("Setup completed")

            message.startsWith("Quick Settings → Unable to start") ->
                return listOf("SleepManager could not start from Quick Settings")

            message.startsWith("Started after ") ->
                return listOf("SleepManager restarted after device boot")

            message.startsWith("Closed-lid false wake suppressed") ->
                return listOf(
                    "False wake detected while the lid was closed",
                    "Device returned to sleep"
                )

            message.startsWith("Protection → device returned to sleep") ->
                return listOf("Device returned to sleep")

            message.startsWith("Dock → display disconnected") ->
                return listOf(
                    "External display disconnected",
                    "Device kept awake"
                )

            message.startsWith("Helper reconciliation → replayed cycle") ->
                return listOf("Radio restoration retried")

            message.startsWith(
                "Helper reconciliation → no acknowledgement"
            ) ->
                return listOf("Radio restoration could not be confirmed")

            message.startsWith(
                "Helper reconciliation → replay could not be sent"
            ) ->
                return listOf("Radio restoration retry failed")

            message.startsWith(
                "Recovery → PServer sleep radio state re-applied"
            ) ->
                return listOf("Wi-Fi and Bluetooth sleep state restored")

            message.startsWith(
                "Recovery → PServer sleep radio state re-apply failed"
            ) ->
                return listOf(
                    "Wi-Fi and Bluetooth sleep state could not be restored"
                )

            message.startsWith(
                "Recovery → service restarted · transaction resumed"
            ) ->
                return listOf(
                    "SleepManager restarted",
                    "Sleep protection resumed"
                )

            message.startsWith("Recovery → service restarted") ->
                return listOf("SleepManager restarted")

            message.startsWith("Recovery → pending restore forgotten") ->
                return listOf("Pending restoration cleared")

            message.startsWith("Pre-sleep sync →") ->
                return listOf(
                    syncOutcomeText(
                        label = "Pre-sleep sync",
                        outcome = message.substringAfter("→").trim()
                    )
                )

            message.startsWith("Wake sync →") ->
                return listOf(
                    syncOutcomeText(
                        label = "Wake sync",
                        outcome =
                            message.substringAfter("→")
                                .substringBefore("·")
                                .trim()
                    )
                )

            message.startsWith("Periodic sync →") ->
                return listOf(
                    syncOutcomeText(
                        label = "Periodic sync",
                        outcome = message.substringAfter("→").trim()
                    )
                )

            message.startsWith("Periodic sync skipped →") ->
                return listOf("Periodic sync skipped")

            message.startsWith("Sleep skipped →") ->
                return listOf("Sleep actions skipped")

            message.startsWith(
                "Sleep → waiting for active BasicSync sync to finish"
            ) ->
                return listOf("Waiting for BasicSync to finish")

            message.startsWith("Sleep → active BasicSync sync finished") ->
                return listOf("BasicSync finished syncing")

            message.startsWith("Sleep → BasicSync active sync wait timed out") ->
                return listOf("BasicSync did not finish in time")

            message.startsWith("Sleep → BasicSync STOP sent") ->
                return listOf("BasicSync paused")

            message.startsWith(
                "Sleep → BasicSync STOP not confirmed"
            ) ->
                return listOf("BasicSync pause could not be confirmed")

            message.startsWith("Sleep → JamesDSP OFF sent") ->
                return listOf("JamesDSP turned off")

            message.startsWith("Sleep → JamesDSP OFF not confirmed") ->
                return listOf("JamesDSP turn-off could not be confirmed")

            message.startsWith("Sleep → JamesDSP unchanged") ->
                return listOf("JamesDSP left unchanged")

            message.startsWith(
                "Sleep → Syncthing STOP not confirmed"
            ) ->
                return listOf("Syncthing pause could not be confirmed")

            message.startsWith(
                "Sleep → RAOfflineProxy STOP accepted"
            ) ->
                return listOf("RAOfflineProxy stopped")

            message.startsWith("Sleep → RAOfflineProxy queue busy") ->
                return listOf(
                    "Waiting for RAOfflineProxy to finish",
                    "Wi-Fi kept on"
                )

            message.startsWith(
                "Sleep → RAOfflineProxy STOP confirmation timed out"
            ) ->
                return listOf(
                    "RAOfflineProxy did not stop in time",
                    "Wi-Fi kept on"
                )

            message.startsWith("Sleep → RAOfflineProxy status unavailable") ->
                return listOf(
                    "Waiting for RAOfflineProxy status",
                    "Wi-Fi kept on"
                )

            message.startsWith("Sleep → unsupported RAOfflineProxy") ->
                return listOf(
                    "RAOfflineProxy automation is not compatible",
                    "Wi-Fi kept on"
                )

            message.startsWith("Sleep → RAOfflineProxy not installed") ->
                return listOf("RAOfflineProxy skipped")

            message.startsWith("Sleep → RAOfflineProxy unavailable") ->
                return listOf("RAOfflineProxy unavailable")

            message.startsWith("Sleep → Helper cycle mismatch") ->
                return listOf("Wi-Fi and Bluetooth sleep action skipped")

            message.startsWith("Sleep → radio control unavailable") ->
                return listOf("Wi-Fi and Bluetooth left unchanged")

            message.startsWith("Sleep power disconnected →") ->
                return listOf(
                    friendlyAction(
                        message.substringAfter("→").trim(),
                        ActivityTimelineKind.SLEEP
                    )
                )

            message.startsWith("Sleep power connected →") ->
                return listOf("Battery Saver not used while charging")

            message.startsWith("Lid closed dock →") ->
                return listOf("Charging Separation left unchanged while docked")

            message.startsWith("Lid closed →") ->
                return listOf(
                    friendlyAction(
                        message.substringAfter("→").trim(),
                        ActivityTimelineKind.PROTECTION
                    )
                )

            message.startsWith("Wake → BasicSync restored") ->
                return listOf("BasicSync restored")

            message.startsWith("Wake → RAOfflineProxy restore pending") ->
                return listOf("RAOfflineProxy could not be restored yet")

            message.startsWith("Wake → JamesDSP restore pending") ->
                return listOf("JamesDSP could not be restored yet")

            message.startsWith("Wake → BasicSync restore pending") ->
                return listOf("BasicSync could not be restored yet")

            message.startsWith("Wake → Syncthing restore pending") ->
                return listOf("Syncthing could not resume yet")

            message.startsWith("Wake → Tailscale restore pending") ->
                return listOf("Tailscale could not reconnect yet")

            message.startsWith("Wake →") ->
                return message.substringAfter("→")
                    .split(" · ")
                    .map { friendlyAction(it.trim(), ActivityTimelineKind.WAKE) }
                    .filter { it.isNotBlank() }

            message.startsWith("Sleep →") ->
                return orderedSleepActions(
                    message.substringAfter("→")
                        .split(" · ")
                        .map(String::trim)
                )
                    .map { friendlyAction(it, ActivityTimelineKind.SLEEP) }
                    .filter { it.isNotBlank() }

            message.startsWith("Disable → Battery Saver restore retries exhausted") ->
                return listOf("Battery Saver could not be restored")

            message.startsWith(
                "Disable → Charging Separation restore retries exhausted"
            ) ->
                return listOf("Charging Separation could not be restored")

            message.startsWith("Disable → system setting restore pending") ->
                return listOf("Some settings could not be restored yet")

            message.startsWith("Disable → PServer radios restored") ->
                return listOf("Wi-Fi and Bluetooth restored")

            message.startsWith("Disable → PServer radios restore pending") ->
                return listOf("Wi-Fi and Bluetooth could not be restored yet")

            message.startsWith("Disable → Helper restore pending") ->
                return listOf("Wi-Fi and Bluetooth restoration is still pending")

            message.startsWith("Disable → RAOfflineProxy restored") ->
                return listOf("RAOfflineProxy restored")

            message.startsWith("Disable → RAOfflineProxy restore pending") ->
                return listOf("RAOfflineProxy could not be restored yet")

            message.startsWith("Disable → JamesDSP restore pending") ->
                return listOf("JamesDSP could not be restored yet")

            message.startsWith("Disable → BasicSync") &&
                message.contains("restore pending") ->
                return listOf("BasicSync could not be restored yet")

            message.startsWith("Disable → Syncthing restore pending") ->
                return listOf("Syncthing could not resume yet")

            message.startsWith("Disable → Tailscale restore pending") ->
                return listOf("Tailscale could not reconnect yet")

            message.startsWith("Disable →") ->
                return listOf(
                    "SleepManager is restoring settings before turning off"
                )

            message.startsWith("BasicSync → original state restore retries exhausted") ->
                return listOf("BasicSync could not be restored")

            message == "Syncthing restore pending" ->
                return listOf("Syncthing could not be restored yet")

            message == "JamesDSP restore pending" ->
                return listOf("JamesDSP could not be restored yet")

            message == "BasicSync restore pending" ->
                return listOf("BasicSync could not be restored yet")

            message.startsWith("Memory pressure → Android lowMemory=true") ->
                return listOf("Android reported low memory")

            message.startsWith("Memory trim →") ||
                message.startsWith("Memory pressure → onTrimMemory") ||
                message.startsWith("Memory pressure → onLowMemory") ->
                return listOf(memoryTrimText(message))

            message.startsWith("Closed-lid monitoring unavailable") ->
                return listOf("Lid monitoring is unavailable")
        }

        return listOf(message)
    }

    private fun orderedSleepActions(actions: List<String>): List<String> {
        val syncthing =
            actions.filter {
                it.startsWith("Syncthing STOP") ||
                    it == "state unverified"
            }
        if (syncthing.isEmpty()) return actions

        val remaining =
            actions.filterNot {
                it.startsWith("Syncthing STOP") ||
                    it == "state unverified"
            }
        return syncthing + remaining
    }

    private fun memoryTrimText(message: String): String {
        val level =
            Regex("""level=(\d+)""")
                .find(message)
                ?.groupValues
                ?.getOrNull(1)
        return if (level != null) {
            "Android requested memory trim · level $level"
        } else {
            "Android requested memory trim"
        }
    }

    private fun friendlyAction(
        action: String,
        phase: ActivityTimelineKind
    ): String =
        when {
            action == "Wi-Fi off" -> "Wi-Fi turned off"
            action == "Wi-Fi unchanged" -> "Wi-Fi already in the right state"
            action.startsWith("Wi-Fi restored") -> "Wi-Fi restored"
            action.startsWith("Wi-Fi toggle failed") ->
                "Wi-Fi could not be changed"
            action == "Airplane mode is enabled" -> "Airplane mode is on"

            action == "Bluetooth off" -> "Bluetooth turned off"
            action == "Bluetooth unchanged" ->
                "Bluetooth already in the right state"
            action.startsWith("Bluetooth restored") -> "Bluetooth restored"

            action == "Battery Saver enabled" -> "Battery Saver enabled"
            action == "Battery Saver deferred · external power" ->
                "Battery Saver not used while charging"
            action == "Battery Saver deferred" ->
                "Battery Saver not used while charging"
            action == "external power" -> ""
            action == "Battery Saver enable failed" ->
                "Battery Saver could not be enabled"
            action == "Battery Saver restored" -> "Battery Saver restored"
            action == "Battery Saver restore failed" ->
                "Battery Saver could not be restored"

            action == "Charging Separation disabled" ->
                "Charging Separation disabled"
            action == "Charging Separation disable failed" ->
                "Charging Separation could not be disabled"
            action == "Charging Separation restored" ->
                "Charging Separation restored"
            action == "Charging Separation restore failed" ->
                "Charging Separation could not be restored"

            action == "Tailscale disconnected" -> "Tailscale disconnected"
            action == "Tailscale unchanged" ->
                "Tailscale already in the right state"
            action == "Tailscale restored" -> "Tailscale restored"

            action == "JamesDSP restored" -> "JamesDSP restored"
            action.startsWith("BasicSync restored") -> "BasicSync restored"

            action == "previous state unknown" -> ""
            action == "radio=PServer" -> ""
            action == "radioError" ->
                "Wi-Fi and Bluetooth could not be changed"

            action == "Syncthing STOP confirmed" -> "Syncthing paused"
            action == "Syncthing STOP not sent" -> "Syncthing left unchanged"
            action == "Syncthing STOP sent" -> "Pausing Syncthing"
            action.startsWith("Syncthing STOP sent") ->
                "Syncthing pause requested"
            action == "state unverified" ->
                "Syncthing state could not be confirmed"
            action.startsWith("Syncthing STOP not") ->
                "Syncthing pause could not be confirmed"
            action == "Syncthing FOLLOW sent" -> "Syncthing resumed"

            action.startsWith("PServer radios restore pending") ->
                "Wi-Fi and Bluetooth could not be restored yet"

            action.startsWith("PServer radios restored") ->
                if (phase == ActivityTimelineKind.WAKE) {
                    "Wi-Fi and Bluetooth restored"
                } else {
                    "Wi-Fi and Bluetooth prepared for sleep"
                }

            action == "Helper restore pending" ->
                "Restoring Wi-Fi and Bluetooth"

            action.startsWith("BasicSync original state restore") ->
                "Restoring BasicSync"

            else ->
                action
                    .replace("STOP", "stop")
                    .replace("FOLLOW", "resume")
                    .replace("restore owned", "will be restored on wake")
        }

    private fun syncOutcomeText(
        label: String,
        outcome: String
    ): String =
        when (outcome.trim().uppercase()) {
            "COMPLETED" -> "$label completed"
            "COMPLETED_WITH_ERRORS" -> "$label completed with some errors"
            "NO_TARGETS" -> "$label skipped — nothing to sync"
            "COMPLETION_UNAVAILABLE" ->
                "$label skipped — sync status unavailable"
            "NETWORK_UNAVAILABLE" ->
                "$label skipped — no network"
            "START_FAILED" -> "$label could not start"
            "SYNC_TIMEOUT" -> "$label timed out"
            "CANCELLED" -> "$label cancelled"
            "NONE" -> "$label finished"
            else -> "$label finished"
        }
}
