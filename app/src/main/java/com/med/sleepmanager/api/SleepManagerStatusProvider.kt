package com.med.sleepmanager.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.service.SleepManagerService
import org.json.JSONObject

/**
 * Stable read-only Automation Status API v1.
 *
 * Applications can inspect the user's enabled preference and the foreground
 * service lifecycle without receiving logs, settings, connector credentials,
 * or any ability to change SleepManager's behavior.
 */
class SleepManagerStatusProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.med.sleepmanager.status"
        const val METHOD_STATUS = "status"
        const val RESULT_STATUS = "status"
        const val API_VERSION = 1
        val URI: Uri = Uri.parse("content://$AUTHORITY")

        /** Notifications are best effort and MUST NOT interfere with sleep/wake. */
        fun notifyChanged(context: Context) {
            runCatching {
                context.contentResolver.notifyChange(URI, null)
            }
        }

        internal fun snapshot(context: Context): JSONObject {
            val enabled = AppPreferences.isEnabled(context)
            val serviceRunning = SleepManagerService.running
            return JSONObject()
                .put("apiVersion", API_VERSION)
                .put("enabled", enabled)
                .put("serviceRunning", serviceRunning)
        }
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_STATUS) return null
        val appContext = context?.applicationContext ?: return null
        return Bundle().apply {
            putString(RESULT_STATUS, snapshot(appContext).toString())
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        throw UnsupportedOperationException("SleepManager status is read-only")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("SleepManager status is read-only")
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("SleepManager status is read-only")
    }
}
