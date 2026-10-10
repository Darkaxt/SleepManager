package com.med.sleepmanager.integration.raofflineproxy

import org.json.JSONObject

/**
 * Pure status payload parser kept separate from the Android ContentProvider
 * adapter so API v1/v2 compatibility can be unit-tested on the JVM.
 */
object RaOfflineProxyStatusParser {
    fun parse(raw: String?): RaOfflineProxyStatus? {
        if (raw.isNullOrBlank()) return null

        return runCatching {
            val json = JSONObject(raw)
            val queue = json.optJSONObject("queue") ?: JSONObject()
            val nextWindowAt =
                if (queue.isNull("nextWindowAt")) {
                    null
                } else {
                    queue.optLong("nextWindowAt")
                }
            val pendingAwards =
                json.optJSONObject("pendingAwards")?.let { pending ->
                    RaOfflineProxyPendingAwardsStatus(
                        count = pending.optInt("count", 0),
                        state =
                            RaOfflineProxyPendingAwardState.fromWire(
                                pending.optString("state", null)
                            ),
                        error =
                            pending.optString("error", null)
                                ?.takeUnless { it.isBlank() || it == "null" }
                    )
                }

            RaOfflineProxyStatus(
                version = json.optInt("version", -1),
                running = json.optBoolean("running", false),
                shouldBeRunning =
                    json.optBoolean("shouldBeRunning", false),
                online = json.optBoolean("online", false),
                queue =
                    RaOfflineProxyQueueStatus(
                        count = queue.optInt("count", 0),
                        state =
                            RaOfflineProxyQueueState.fromWire(
                                queue.optString("state", null)
                            ),
                        nextWindowAt = nextWindowAt
                    ),
                pendingAwards = pendingAwards
            )
        }.getOrNull()
    }
}
