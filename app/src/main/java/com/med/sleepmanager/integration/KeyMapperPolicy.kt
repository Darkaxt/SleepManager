package com.med.sleepmanager.integration

internal enum class KeyMapperMappingsState {
    UNKNOWN,
    ACTIVE,
    PAUSED
}

/**
 * Conservative state-aware policy. An unknown initial state must never be paused.
 * Restore ownership is created only after SleepManager requested a pause from ACTIVE.
 */
internal object KeyMapperPolicy {
    fun shouldPause(state: KeyMapperMappingsState): Boolean =
        state == KeyMapperMappingsState.ACTIVE

    fun shouldResume(owned: Boolean, state: KeyMapperMappingsState): Boolean =
        owned && state != KeyMapperMappingsState.ACTIVE

    fun supportsStateApi(versionName: String?): Boolean {
        val parts = versionName?.substringBefore('-')?.split('.') ?: return false
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        return major > 4 || (major == 4 && minor >= 7)
    }
}
