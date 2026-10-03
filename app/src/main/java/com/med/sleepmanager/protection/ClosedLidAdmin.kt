package com.med.sleepmanager.protection

import android.content.ComponentName
import android.content.Context

/**
 * Generic access point for the closed-lid Device Admin component.
 *
 * The receiver class keeps its historical Thor name because Android persists
 * Device Admin grants by component name. Renaming that component would revoke
 * existing users' permission during an upgrade.
 */
object ClosedLidAdmin {
    fun component(context: Context): ComponentName =
        ComponentName(context, ThorDeviceAdminReceiver::class.java)
}
