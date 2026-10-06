package com.med.sleepmanager.integration.raofflineproxy

enum class RaOfflineProxyQueueState {
    IDLE,
    CACHING,
    WAITING,
    BLOCKED,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): RaOfflineProxyQueueState =
            when (value?.lowercase()) {
                "idle" -> IDLE
                "caching" -> CACHING
                "waiting" -> WAITING
                "blocked" -> BLOCKED
                else -> UNKNOWN
            }
    }
}

data class RaOfflineProxyQueueStatus(
    val count: Int,
    val state: RaOfflineProxyQueueState,
    val nextWindowAt: Long?
)

data class RaOfflineProxyStatus(
    val version: Int,
    val running: Boolean,
    val shouldBeRunning: Boolean,
    val online: Boolean,
    val queue: RaOfflineProxyQueueStatus
)

data class RaOfflineProxyCommandResult(
    val code: String?,
    val status: RaOfflineProxyStatus?,
    val detail: String? = null
) {
    val accepted: Boolean
        get() = code == RESULT_OK

    companion object {
        const val RESULT_OK = "ok"
        const val RESULT_FOREGROUND_SERVICE_NOT_ALLOWED =
            "foreground_service_not_allowed"
        const val RESULT_NO_EMULATOR_ENABLED = "no_emulator_enabled"
        const val RESULT_PORT_UNAVAILABLE = "port_unavailable"
        const val RESULT_PATCH_FAILED = "patch_failed"

        const val INTERNAL_SECURITY_EXCEPTION = "security_exception"
        const val INTERNAL_CALL_FAILED = "call_failed"
    }
}
