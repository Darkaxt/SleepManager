package com.med.sleepmanager.data

import android.content.Context

/**
 * Persisted clamshell runtime state.
 *
 * The historical thor_* keys intentionally remain unchanged so existing 0.6.x
 * installs retain their last-known lid state after the code-level generic
 * clamshell naming cleanup.
 */
object ClamshellStateStore {
    private const val PREFS = "sleep_manager"
    private const val KEY_LEGACY_THOR_LID_CLOSED_LAST_KNOWN =
        "thor_lid_closed_last_known"
    private const val KEY_LEGACY_THOR_LID_STATE_KNOWN =
        "thor_lid_state_known"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastKnownLidClosed(context: Context): Boolean? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_LEGACY_THOR_LID_STATE_KNOWN, false)) return null
        return p.getBoolean(KEY_LEGACY_THOR_LID_CLOSED_LAST_KNOWN, false)
    }

    fun setLastKnownLidClosed(context: Context, closed: Boolean): Boolean =
        prefs(context).edit()
            .putBoolean(KEY_LEGACY_THOR_LID_CLOSED_LAST_KNOWN, closed)
            .putBoolean(KEY_LEGACY_THOR_LID_STATE_KNOWN, true)
            .commit()
}
