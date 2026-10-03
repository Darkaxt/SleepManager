package com.med.sleepmanager

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.med.sleepmanager.data.AppPreferences
import com.med.sleepmanager.data.DiagnosticsCycleStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.integration.BasicSyncController
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.integration.JamesDspController
import com.med.sleepmanager.integration.SyncthingController
import com.med.sleepmanager.integration.TailscaleController
import com.med.sleepmanager.service.PeriodicSyncReceiver
import com.med.sleepmanager.service.SleepManagerService
import com.med.sleepmanager.sync.SyncStopOwnershipStore
import com.med.sleepmanager.sync.SyncTransitionStore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Local emulator end-to-end control surface.
 *
 * The normal CI instrumentation run invokes this test without a "mode" argument,
 * in which case it intentionally does nothing. The prebuilt local-emulator
 * bundle invokes it explicitly between real screen-off / screen-on cycles to
 * configure SleepManager without fragile UI coordinate automation.
 */
@RunWith(AndroidJUnit4::class)
class LocalE2EControlTest {

    @Test
    fun applyConfiguration() {
        val mode =
            InstrumentationRegistry.getArguments()
                .getString("mode")
                ?.trim()
                .orEmpty()

        if (mode.isEmpty()) return

        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        when (mode) {
            "fire_periodic_sync" -> {
                PeriodicSyncReceiver().onReceive(
                    context,
                    Intent(context, PeriodicSyncReceiver::class.java)
                        .setAction(PeriodicSyncReceiver.ACTION_PERIODIC_SYNC)
                )
                return
            }
        }

        resetScenarioState(context)
        configureCommon(context)

        when (mode) {
            "core" -> configureRealIntegrations(context)
            "grace5" -> AppPreferences.setSleepGraceMs(context, 5_000L)
            "custom60" -> {
                AppPreferences.setCustomDelayEnabled(context, true)
                AppPreferences.setCustomDelayMs(context, 60_000L)
            }
            "battery50" -> {
                AppPreferences.setBatteryConditionEnabled(context, true)
                AppPreferences.setBatteryBelowPercent(context, 50)
            }
            "not_charging" ->
                AppPreferences.setNotChargingOnly(context, true)
            "battery_saver_on" ->
                AppPreferences.setBatterySaverMode(
                    context,
                    AppPreferences.BATTERY_SAVER_ON
                )
            "schedule_inside" ->
                configureSchedule(context, inside = true)
            "schedule_outside" ->
                configureSchedule(context, inside = false)
            "combined" -> {
                AppPreferences.setBatteryConditionEnabled(context, true)
                AppPreferences.setBatteryBelowPercent(context, 50)
                AppPreferences.setNotChargingOnly(context, true)
                AppPreferences.setBatterySaverMode(
                    context,
                    AppPreferences.BATTERY_SAVER_ON
                )
                configureSchedule(context, inside = true)
            }
            "sync_then_stop" -> {
                configureSyncProviders(context)
                AppPreferences.setManageWifi(context, true)
                AppPreferences.setSyncThenStopOnSleepWake(context, true)
            }
            "periodic" -> {
                configureSyncProviders(context)
                AppPreferences.setManageWifi(context, true)
                AppPreferences.setPeriodicSyncWhileSleeping(context, true)
            }
            else -> error("Unknown local E2E mode: $mode")
        }

        flushScenarioPreferences(context)
    }

    private fun flushScenarioPreferences(context: Context) {
        check(
            context.getSharedPreferences("sleep_manager", Context.MODE_PRIVATE)
                .edit()
                .commit()
        ) {
            "Failed to synchronously flush local E2E preferences"
        }
    }

    private fun resetScenarioState(context: Context) {
        AppPreferences.setEnabled(context, false)
        context.stopService(Intent(context, SleepManagerService::class.java))

        context.getSharedPreferences("sleep_manager", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        SleepCycleStore.clear(context)
        DiagnosticsCycleStore.clear(context)
        SyncTransitionStore.clear(context)
        SyncStopOwnershipStore.clearBasicSync(context)

        context.getSharedPreferences("device_control_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        BasicSyncController.clearObservedState()
        HelperController.forgetPendingState(context)
    }

    private fun configureCommon(context: Context) {
        AppPreferences.setSetupComplete(context, true)

        AppPreferences.setManageWifi(context, true)
        AppPreferences.setManageBluetooth(context, false)
        AppPreferences.setManageBatterySaver(context, false)
        AppPreferences.setManageChargingSeparationWithLid(context, false)

        AppPreferences.setManageSyncthing(context, false)
        AppPreferences.setManageTailscale(context, false)
        AppPreferences.setManageJamesDsp(context, false)
        AppPreferences.setManageBasicSync(context, false)

        AppPreferences.setPeriodicSyncWhileSleeping(context, false)
        AppPreferences.setSyncThenStopOnSleepWake(context, false)

        AppPreferences.setManageClosedLidProtection(context, false)
        AppPreferences.setDockDisconnectSleeps(context, false)
        AppPreferences.setClosedLidPowerSleeps(context, false)

        AppPreferences.setSleepGraceMs(context, 0L)
        AppPreferences.setCustomDelayEnabled(context, false)

        AppPreferences.setBatteryConditionEnabled(context, false)
        AppPreferences.setBatteryBelowPercent(context, 50)
        AppPreferences.setNotChargingOnly(context, false)
        AppPreferences.setBatterySaverMode(
            context,
            AppPreferences.BATTERY_SAVER_IGNORE
        )
        AppPreferences.setScheduleEnabled(context, false)

        AppPreferences.setEnabled(context, true)
    }

    private fun configureRealIntegrations(context: Context) {
        AppPreferences.setManageBluetooth(context, true)

        val syncthing = SyncthingController.selectedTarget(context)
        AppPreferences.setManageSyncthing(context, syncthing != null)

        AppPreferences.setManageTailscale(
            context,
            TailscaleController.isInstalled(context)
        )
        AppPreferences.setManageJamesDsp(
            context,
            JamesDspController.isInstalled(context)
        )
        AppPreferences.setManageBasicSync(
            context,
            BasicSyncController.isInstalled(context)
        )
    }

    private fun configureSyncProviders(context: Context) {
        val syncthing = SyncthingController.selectedTarget(context)
        AppPreferences.setManageSyncthing(context, syncthing != null)
        AppPreferences.setManageBasicSync(
            context,
            BasicSyncController.isInstalled(context)
        )
    }

    private fun configureSchedule(
        context: Context,
        inside: Boolean
    ) {
        val calendar = Calendar.getInstance()
        val now =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                calendar.get(Calendar.MINUTE)

        val start =
            if (inside) {
                (now + 1439) % 1440
            } else {
                (now + 2) % 1440
            }

        val end =
            if (inside) {
                (now + 1) % 1440
            } else {
                (now + 3) % 1440
            }

        AppPreferences.setScheduleStartMinutes(context, start)
        AppPreferences.setScheduleEndMinutes(context, end)
        AppPreferences.setScheduleEnabled(context, true)
    }
}
