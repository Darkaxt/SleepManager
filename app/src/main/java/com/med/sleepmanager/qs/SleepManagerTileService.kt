package com.med.sleepmanager.qs

import android.content.ComponentName
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.DiagnosticsStateStore
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.protection.ClosedLidAdmin
import com.med.sleepmanager.protection.LidMonitor
import com.med.sleepmanager.service.SleepManagerService

class SleepManagerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (isLocked) {
            unlockAndRun { toggleManager() }
        } else {
            toggleManager()
        }
    }

    private fun toggleManager() {
        if (AppPreferences.isEnabled(this)) {
            disableManager()
        } else {
            enableManager()
        }
        updateTile()
    }

    private fun enableManager() {
        val helperNeeded =
            AppPreferences.manageWifi(this) || AppPreferences.manageBluetooth(this)

        if (helperNeeded && !HelperController.isInstalled(this)) {
            Toast.makeText(
                this,
                "SleepManager helper required for Wi-Fi / Bluetooth",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (AppPreferences.manageClosedLidProtection(this)) {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ClosedLidAdmin.component(this)

            if (!LidMonitor.isSupported() || !dpm.isAdminActive(admin)) {
                Toast.makeText(
                    this,
                    "Open SleepManager to finish closed-lid protection setup",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        AppPreferences.setEnabled(this, true)

        try {
            val service = Intent(this, SleepManagerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
            else startService(service)

            DiagnosticsStateStore.recordEvent(this, "SleepManager enabled • Quick Settings")
        } catch (t: Throwable) {
            AppPreferences.setEnabled(this, false)
            DiagnosticsStateStore.recordEvent(
                this,
                "Quick Settings → Unable to start ${t.javaClass.simpleName}"
            )
            Toast.makeText(
                this,
                "Unable to start SleepManager",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun disableManager() {
        AppPreferences.setEnabled(this, false)

        val service = Intent(this, SleepManagerService::class.java)
            .setAction(SleepManagerService.ACTION_DISABLE_AND_RESTORE)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service)
        else startService(service)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = AppPreferences.isEnabled(this)

        tile.label = "SleepManager"
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        fun requestRefresh(context: Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, SleepManagerTileService::class.java)
            )
        }
    }
}
