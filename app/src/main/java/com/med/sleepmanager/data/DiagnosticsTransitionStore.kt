package com.med.sleepmanager.data

import android.content.Context
import android.content.SharedPreferences
import com.med.sleepmanager.device.DeviceControlController
import com.med.sleepmanager.device.DeviceControlStore

/**
 * Compact persisted state-transition diagnostics derived from the same events
 * SleepManager already records. This is support-only state and never
 * participates in restore ownership or sleep/wake decisions.
 */
object DiagnosticsTransitionStore {
    const val COMPONENT_HELPER = "helper"
    const val COMPONENT_SYNCTHING = "syncthing"
    const val COMPONENT_BASIC_SYNC = "basicsync"
    const val COMPONENT_RA_OFFLINE_PROXY = "raofflineproxy"
    const val COMPONENT_TAILSCALE = "tailscale"
    const val COMPONENT_JAMES_DSP = "jamesdsp"
    const val COMPONENT_BATTERY_SAVER = "battery_saver"
    const val COMPONENT_CHARGING_SEPARATION = "charging_separation"

    private const val PREFS = "diagnostics_transition_state"
    private const val MAX_VALUE_LENGTH = 220

    private val orderedComponents =
        listOf(
            COMPONENT_HELPER,
            COMPONENT_SYNCTHING,
            COMPONENT_BASIC_SYNC,
            COMPONENT_RA_OFFLINE_PROXY,
            COMPONENT_TAILSCALE,
            COMPONENT_JAMES_DSP,
            COMPONENT_BATTERY_SAVER,
            COMPONENT_CHARGING_SEPARATION
        )

    data class Transition(
        val componentId: String,
        val initialState: String?,
        val sleepRequest: String?,
        val sleepResult: String?,
        val sleepState: String?,
        val restoreTarget: String?,
        val restoreResult: String?,
        val finalState: String?,
        val note: String?,
        val updatedAt: Long
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordEvent(
        context: Context,
        event: String
    ) {
        if (!AppPreferences.advancedDiagnosticsEnabled(context)) return

        val lower = event.lowercase()

        if ("battery saver" in lower) {
            val batterySaverCurrent =
                DeviceControlController.batterySaverEnabled(context)
            val batterySaverOwned =
                DeviceControlStore.batterySaver(context)
            when {
                "restore retries exhausted" in lower -> record(
                    context,
                    COMPONENT_BATTERY_SAVER,
                    restoreResult = event,
                    finalState = onOff(batterySaverCurrent),
                    note = event
                )
                "restore failed" in lower -> record(
                    context,
                    COMPONENT_BATTERY_SAVER,
                    restoreResult = event,
                    finalState = onOff(batterySaverCurrent),
                    note = event
                )
                "enable failed" in lower -> record(
                    context,
                    COMPONENT_BATTERY_SAVER,
                    reset = true,
                    initialState = onOff(batterySaverCurrent),
                    sleepRequest = "ENABLE_FOR_SLEEP",
                    sleepResult = event,
                    sleepState = onOff(batterySaverCurrent),
                    restoreTarget = "none",
                    restoreResult = "NOT_OWNED",
                    finalState = onOff(batterySaverCurrent),
                    note = event
                )
                "deferred" in lower -> record(
                    context,
                    COMPONENT_BATTERY_SAVER,
                    reset = true,
                    initialState =
                        onOff(
                            if (batterySaverOwned.owned) {
                                batterySaverOwned.previous
                            } else {
                                batterySaverCurrent
                            }
                        ),
                    sleepRequest = "DEFER_EXTERNAL_POWER",
                    sleepResult = event,
                    sleepState = onOff(batterySaverCurrent),
                    restoreTarget =
                        onOff(
                            if (batterySaverOwned.owned) {
                                batterySaverOwned.previous
                            } else {
                                batterySaverCurrent
                            }
                        ),
                    finalState = onOff(batterySaverCurrent),
                    note = event
                )
                "enabled" in lower && ("sleep" in lower || "power disconnected" in lower) -> record(
                    context,
                    COMPONENT_BATTERY_SAVER,
                    reset = true,
                    initialState =
                        onOff(
                            if (batterySaverOwned.owned) {
                                batterySaverOwned.previous
                            } else {
                                !batterySaverCurrent
                            }
                        ),
                    sleepRequest = "ENABLE_FOR_SLEEP",
                    sleepResult = event,
                    sleepState = onOff(batterySaverCurrent),
                    restoreTarget =
                        if (batterySaverOwned.owned) {
                            onOff(batterySaverOwned.previous)
                        } else {
                            "previous state"
                        },
                    finalState = onOff(batterySaverCurrent),
                    note = event
                )
                "restored" in lower -> record(
                    context,
                    COMPONENT_BATTERY_SAVER,
                    restoreResult = event,
                    finalState = onOff(batterySaverCurrent),
                    note = event
                )
                else -> record(
                    context,
                    COMPONENT_BATTERY_SAVER,
                    note = event
                )
            }
        }

        if ("charging separation" in lower) {
            val chargingSeparationCurrent =
                DeviceControlController.chargingSeparationState(context)
            val chargingSeparationOwned =
                DeviceControlStore.chargingSeparation(context)
            when {
                "restore retries exhausted" in lower -> record(
                    context,
                    COMPONENT_CHARGING_SEPARATION,
                    restoreResult = event,
                    finalState = onOff(chargingSeparationCurrent),
                    note = event
                )
                "dock" in lower && "unchanged" in lower -> record(
                    context,
                    COMPONENT_CHARGING_SEPARATION,
                    reset = true,
                    initialState = onOff(chargingSeparationCurrent),
                    sleepRequest = "DOCK_BYPASS",
                    sleepResult = event,
                    sleepState = onOff(chargingSeparationCurrent),
                    restoreTarget = "none",
                    restoreResult = "NOT_REQUIRED",
                    finalState = onOff(chargingSeparationCurrent),
                    note = event
                )
                "restore failed" in lower -> record(
                    context,
                    COMPONENT_CHARGING_SEPARATION,
                    restoreResult = event,
                    finalState = onOff(chargingSeparationCurrent),
                    note = event
                )
                "disable failed" in lower -> record(
                    context,
                    COMPONENT_CHARGING_SEPARATION,
                    reset = true,
                    initialState = onOff(chargingSeparationCurrent),
                    sleepRequest = "DISABLE_LID_CLOSED",
                    sleepResult = event,
                    sleepState = onOff(chargingSeparationCurrent),
                    restoreTarget = "none",
                    restoreResult = "NOT_OWNED",
                    finalState = onOff(chargingSeparationCurrent),
                    note = event
                )
                "disabled" in lower -> record(
                    context,
                    COMPONENT_CHARGING_SEPARATION,
                    reset = true,
                    initialState =
                        onOff(
                            if (chargingSeparationOwned.owned) {
                                chargingSeparationOwned.previous
                            } else {
                                chargingSeparationCurrent
                            }
                        ),
                    sleepRequest = "DISABLE_LID_CLOSED",
                    sleepResult = event,
                    sleepState = onOff(chargingSeparationCurrent),
                    restoreTarget =
                        if (chargingSeparationOwned.owned) {
                            onOff(chargingSeparationOwned.previous)
                        } else {
                            "previous state"
                        },
                    finalState = onOff(chargingSeparationCurrent),
                    note = event
                )
                "restored" in lower -> record(
                    context,
                    COMPONENT_CHARGING_SEPARATION,
                    restoreResult = event,
                    finalState = onOff(chargingSeparationCurrent),
                    note = event
                )
                else -> record(
                    context,
                    COMPONENT_CHARGING_SEPARATION,
                    note = event
                )
            }
        }

        if ("syncthing" in lower) {
            when {
                event.startsWith("Sleep") -> record(
                    context,
                    COMPONENT_SYNCTHING,
                    reset = true,
                    initialState = "UNKNOWN / probe-dependent",
                    sleepRequest = "STOP",
                    sleepResult = event,
                    sleepState =
                        when {
                            "confirmed" in lower && "not confirmed" !in lower -> "STOPPED_CONFIRMED"
                            "not confirmed" in lower -> "UNKNOWN"
                            "not sent" in lower -> "UNCHANGED"
                            else -> "STOP_REQUESTED"
                        },
                    restoreTarget =
                        if ("not sent" in lower || "not confirmed" in lower) "none"
                        else "FOLLOW",
                    note = event
                )
                event.startsWith("Wake") || event.startsWith("Disable") -> record(
                    context,
                    COMPONENT_SYNCTHING,
                    restoreResult = event,
                    finalState =
                        if ("follow sent" in lower) "FOLLOW_REQUESTED"
                        else "RESTORE_PENDING",
                    note = event
                )
                else -> record(context, COMPONENT_SYNCTHING, note = event)
            }
        }

        if ("basicsync" in lower || "pre-sleep sync" in lower || "wake sync" in lower) {
            when {
                "restore retries exhausted" in lower -> record(
                    context,
                    COMPONENT_BASIC_SYNC,
                    restoreResult = event,
                    finalState = "RESTORE_PENDING",
                    note = event
                )
                event.startsWith("Wake") || event.startsWith("Disable") || "wake sync" in lower -> record(
                    context,
                    COMPONENT_BASIC_SYNC,
                    restoreResult = event,
                    finalState =
                        if ("restored" in lower || "clients stopped" in lower) "RESTORE_REQUESTED"
                        else "RESTORE_PENDING",
                    note = event
                )
                else -> record(
                    context,
                    COMPONENT_BASIC_SYNC,
                    reset = "stop sent" in lower,
                    sleepRequest =
                        when {
                            "stop" in lower -> "STOP"
                            "sync" in lower -> "SYNC / WAIT"
                            else -> null
                        },
                    sleepResult = event,
                    sleepState =
                        when {
                            "stop not confirmed" in lower -> "UNKNOWN"
                            "stop sent" in lower -> "STOP_REQUESTED"
                            "sync finished" in lower -> "SYNC_FINISHED"
                            "timed out" in lower -> "TIMEOUT"
                            else -> null
                        },
                    restoreTarget =
                        event.substringAfter("restore ", "")
                            .takeIf { it.isNotBlank() },
                    note = event
                )
            }
        }

        if ("raofflineproxy" in lower) {
            when {
                "stop accepted" in lower -> record(
                    context,
                    COMPONENT_RA_OFFLINE_PROXY,
                    reset = true,
                    initialState = "RUNNING / intended running",
                    sleepRequest = "STOP",
                    sleepResult = event,
                    sleepState = "STOPPING",
                    restoreTarget = "START",
                    note = event
                )
                "queue busy" in lower -> record(
                    context,
                    COMPONENT_RA_OFFLINE_PROXY,
                    reset = true,
                    initialState = "RUNNING",
                    sleepRequest = "WAIT_FOR_QUEUE",
                    sleepResult = event,
                    sleepState = "QUEUE_BUSY / NETWORK_PRESERVED",
                    restoreTarget = "START_AFTER_OWNED_STOP",
                    note = event
                )
                "gate complete" in lower -> record(
                    context,
                    COMPONENT_RA_OFFLINE_PROXY,
                    sleepResult = event,
                    sleepState =
                        if ("stop confirmed" in lower) {
                            "STOPPED_CONFIRMED"
                        } else {
                            "UNCHANGED"
                        },
                    note = event
                )
                "restored" in lower -> record(
                    context,
                    COMPONENT_RA_OFFLINE_PROXY,
                    restoreResult = event,
                    finalState = "RUNNING",
                    note = event
                )
                "restore pending" in lower -> record(
                    context,
                    COMPONENT_RA_OFFLINE_PROXY,
                    restoreResult = event,
                    finalState = "RESTORE_PENDING",
                    note = event
                )
                "timed out" in lower -> record(
                    context,
                    COMPONENT_RA_OFFLINE_PROXY,
                    sleepResult = event,
                    sleepState = "STOP_CONFIRM_TIMEOUT",
                    note = event
                )
                else -> record(
                    context,
                    COMPONENT_RA_OFFLINE_PROXY,
                    note = event
                )
            }
        }

        if ("tailscale" in lower) {
            when {
                event.startsWith("Sleep") -> record(
                    context,
                    COMPONENT_TAILSCALE,
                    reset = true,
                    initialState = "CONNECTED_OR_UNKNOWN",
                    sleepRequest = "DISCONNECT",
                    sleepResult = event,
                    sleepState =
                        if ("disconnected" in lower) "DISCONNECTED"
                        else "UNCHANGED_OR_UNKNOWN",
                    restoreTarget =
                        if ("disconnected" in lower) "CONNECT" else "none",
                    note = event
                )
                event.startsWith("Wake") || event.startsWith("Disable") -> record(
                    context,
                    COMPONENT_TAILSCALE,
                    restoreResult = event,
                    finalState =
                        if ("restored" in lower) "CONNECTED"
                        else "RESTORE_PENDING",
                    note = event
                )
                else -> record(context, COMPONENT_TAILSCALE, note = event)
            }
        }

        if ("jamesdsp" in lower) {
            when {
                event.startsWith("Sleep") -> record(
                    context,
                    COMPONENT_JAMES_DSP,
                    reset = true,
                    initialState = "UNKNOWN · no public state API",
                    sleepRequest =
                        if ("off sent" in lower) "POWER_OFF" else "none",
                    sleepResult = event,
                    sleepState =
                        if ("off sent" in lower) "OFF_REQUESTED"
                        else "UNCHANGED_OR_UNKNOWN",
                    restoreTarget =
                        if ("off sent" in lower) "POWER_ON" else "none",
                    note = event
                )
                event.startsWith("Wake") || event.startsWith("Disable") -> record(
                    context,
                    COMPONENT_JAMES_DSP,
                    restoreTarget = "POWER_ON",
                    restoreResult = event,
                    finalState =
                        if ("restored" in lower) "ON_REQUESTED"
                        else "RESTORE_PENDING",
                    note = event
                )
                else -> record(context, COMPONENT_JAMES_DSP, note = event)
            }
        }

        if ("helper" in lower) {
            val restoring = event.startsWith("Wake") || event.startsWith("Disable")
            record(
                context,
                COMPONENT_HELPER,
                sleepResult = if (!restoring) event else null,
                restoreResult = if (restoring) event else null,
                note = event
            )
        }

        if (
            "wi-fi" in lower ||
            "bluetooth" in lower ||
            "pserver radios" in lower
        ) {
            val restoring = event.startsWith("Wake") || event.startsWith("Disable")
            val directPServerSleep =
                !restoring && "radio=pserver" in lower
            val directPServerRestore =
                restoring && "pserver radios" in lower
            record(
                context,
                COMPONENT_HELPER,
                reset = directPServerSleep,
                initialState =
                    if (directPServerSleep) {
                        "reported by PServer"
                    } else {
                        null
                    },
                sleepRequest =
                    if (directPServerSleep) {
                        "OFF"
                    } else {
                        null
                    },
                sleepResult = if (!restoring) event else null,
                sleepState =
                    if (!restoring && ("wi-fi off" in lower || "bluetooth off" in lower)) {
                        "managed radios OFF"
                    } else {
                        null
                    },
                restoreTarget = "previous Wi-Fi / Bluetooth state",
                restoreResult = if (restoring) event else null,
                finalState =
                    when {
                        directPServerRestore && "restored" in lower ->
                            "previous radio state restored"
                        restoring && "restored" in lower ->
                            "previous radio state requested"
                        else ->
                            null
                    },
                note = event
            )
        }
    }

    fun recordWifiToggle(
        context: Context,
        phase: String,
        action: String,
        attempted: Boolean,
        success: Boolean,
        airplaneMode: Boolean
    ) {
        if (!AppPreferences.advancedDiagnosticsEnabled(context)) return

        val sleepPhase = phase.equals("SLEEP", ignoreCase = true)
        val result =
            when {
                !attempted -> "not required"
                success -> "success"
                airplaneMode -> "failed · airplane mode"
                else -> "failed"
            }

        record(
            context = context,
            componentId = COMPONENT_HELPER,
            reset = sleepPhase,
            initialState = if (sleepPhase) "reported by Helper" else null,
            sleepRequest = if (sleepPhase) action else null,
            sleepResult = if (sleepPhase) "Wi-Fi toggle $result" else null,
            sleepState = if (sleepPhase && success) action else null,
            restoreTarget = "previous Wi-Fi / Bluetooth state",
            restoreResult = if (!sleepPhase) "Wi-Fi toggle $result" else null,
            finalState = if (!sleepPhase && success) action else null,
            note = "Helper $phase · Wi-Fi action=$action · $result"
        )
    }

    fun record(
        context: Context,
        componentId: String,
        reset: Boolean = false,
        initialState: String? = null,
        sleepRequest: String? = null,
        sleepResult: String? = null,
        sleepState: String? = null,
        restoreTarget: String? = null,
        restoreResult: String? = null,
        finalState: String? = null,
        note: String? = null
    ) {
        if (!AppPreferences.advancedDiagnosticsEnabled(context)) return

        require(componentId in orderedComponents) {
            "Unknown diagnostics component: $componentId"
        }

        val previous = if (reset) null else transition(context, componentId)
        val next =
            Transition(
                componentId = componentId,
                initialState = normalized(initialState) ?: previous?.initialState,
                sleepRequest = normalized(sleepRequest) ?: previous?.sleepRequest,
                sleepResult = normalized(sleepResult) ?: previous?.sleepResult,
                sleepState = normalized(sleepState) ?: previous?.sleepState,
                restoreTarget = normalized(restoreTarget) ?: previous?.restoreTarget,
                restoreResult = normalized(restoreResult) ?: previous?.restoreResult,
                finalState = normalized(finalState) ?: previous?.finalState,
                note = normalized(note) ?: previous?.note,
                updatedAt = System.currentTimeMillis()
            )

        val editor = prefs(context).edit()
        put(editor, key(componentId, "initial"), next.initialState)
        put(editor, key(componentId, "sleep_request"), next.sleepRequest)
        put(editor, key(componentId, "sleep_result"), next.sleepResult)
        put(editor, key(componentId, "sleep_state"), next.sleepState)
        put(editor, key(componentId, "restore_target"), next.restoreTarget)
        put(editor, key(componentId, "restore_result"), next.restoreResult)
        put(editor, key(componentId, "final_state"), next.finalState)
        put(editor, key(componentId, "note"), next.note)
        editor.putLong(key(componentId, "updated_at"), next.updatedAt)
        editor.apply()
    }

    fun transition(
        context: Context,
        componentId: String
    ): Transition? {
        if (componentId !in orderedComponents) return null
        val p = prefs(context)
        val updatedAt = p.getLong(key(componentId, "updated_at"), 0L)
        if (updatedAt <= 0L) return null

        return Transition(
            componentId = componentId,
            initialState = p.getString(key(componentId, "initial"), null),
            sleepRequest = p.getString(key(componentId, "sleep_request"), null),
            sleepResult = p.getString(key(componentId, "sleep_result"), null),
            sleepState = p.getString(key(componentId, "sleep_state"), null),
            restoreTarget = p.getString(key(componentId, "restore_target"), null),
            restoreResult = p.getString(key(componentId, "restore_result"), null),
            finalState = p.getString(key(componentId, "final_state"), null),
            note = p.getString(key(componentId, "note"), null),
            updatedAt = updatedAt
        )
    }

    fun all(context: Context): List<Transition> =
        orderedComponents.mapNotNull { transition(context, it) }

    fun label(componentId: String): String =
        when (componentId) {
            COMPONENT_HELPER -> "Wi-Fi / Bluetooth"
            COMPONENT_SYNCTHING -> "Syncthing-Fork"
            COMPONENT_BASIC_SYNC -> "BasicSync"
            COMPONENT_RA_OFFLINE_PROXY -> "RAOfflineProxy"
            COMPONENT_TAILSCALE -> "Tailscale"
            COMPONENT_JAMES_DSP -> "JamesDSP"
            COMPONENT_BATTERY_SAVER -> "Battery Saver"
            COMPONENT_CHARGING_SEPARATION -> "Charging Separation"
            else -> componentId
        }

    private fun onOff(value: Boolean?): String =
        when (value) {
            true -> "ON"
            false -> "OFF"
            null -> "unknown"
        }

    private fun normalized(value: String?): String? =
        value?.trim()?.take(MAX_VALUE_LENGTH)?.takeIf { it.isNotEmpty() }

    private fun key(componentId: String, field: String) =
        "$componentId.$field"

    private fun put(
        editor: SharedPreferences.Editor,
        key: String,
        value: String?
    ) {
        if (value == null) editor.remove(key)
        else editor.putString(key, value)
    }
}
