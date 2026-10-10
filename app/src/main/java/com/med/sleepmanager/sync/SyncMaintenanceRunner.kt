package com.med.sleepmanager.sync

import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.med.sleepmanager.data.DirectRadioStore
import com.med.sleepmanager.data.SleepCycleStore
import com.med.sleepmanager.device.RadioBackend
import com.med.sleepmanager.device.RadioController
import com.med.sleepmanager.integration.HelperController
import com.med.sleepmanager.network.NetworkReadyGate

/**
 * Android wrapper around [SyncMaintenanceCoordinator].
 *
 * Android-specific network, wake-lock and temporary Wi-Fi work is active only
 * while a maintenance session is running.
 */
class SyncMaintenanceRunner(
    context: Context,
    private val handler: Handler,
    private val providersFactory: () -> List<SyncCompletionProvider> = {
        ManagedSyncProviders.selected(context.applicationContext)
    },
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) {
    companion object {
        private const val TAG = "SleepManagerSync"
        private const val NETWORK_TIMEOUT_MS = 60_000L
        private const val POLL_INTERVAL_MS = 1_000L
        private const val WIFI_CLEANUP_TIMEOUT_MS = 5_000L
        private const val STOP_CONFIRM_POLL_INTERVAL_MS = 250L
        private const val STOP_CONFIRM_TIMEOUT_MS = 5_000L
        private const val WAKE_LOCK_TIMEOUT_MS =
            SyncMaintenanceCoordinator.DEFAULT_SYNC_TIMEOUT_MS +
                NETWORK_TIMEOUT_MS +
                STOP_CONFIRM_TIMEOUT_MS +
                WIFI_CLEANUP_TIMEOUT_MS +
                10_000L
    }

    private val appContext = context.applicationContext

    private var coordinator: SyncMaintenanceCoordinator? = null
    private var activeProviders: List<SyncCompletionProvider> = emptyList()
    private var networkGate: NetworkReadyGate? = null
    private var completionCallback: ((SyncMaintenanceSnapshot) -> Unit)? = null
    private var trigger: SyncMaintenanceTrigger? = null
    private val cleanupState = SyncMaintenanceCleanupState()
    private var helperReceiverRegistered = false
    private var wakeLock: PowerManager.WakeLock? = null

    val active: Boolean
        get() = coordinator != null

    val activeTrigger: SyncMaintenanceTrigger?
        get() = if (active) trigger else null

    private val pollRunnable = object : Runnable {
        override fun run() {
            val current = coordinator ?: return
            val snapshot = current.poll(elapsedRealtime())
            if (snapshot.phase == SyncMaintenancePhase.FINISHED) {
                finishWithCleanup(snapshot)
            } else {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private val stopConfirmRunnable = Runnable {
        val snapshot = cleanupState.pendingFinalSnapshot ?: return@Runnable
        finishWithCleanup(snapshot)
    }

    private val cleanupTimeoutRunnable = Runnable {
        if (!cleanupState.cleanupPending) return@Runnable
        Log.w(TAG, "Temporary Wi-Fi cleanup result timed out")
        val snapshot = cleanupState.pendingFinalSnapshot ?: return@Runnable
        finishNow(snapshot)
    }

    private val helperResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (
                intent?.action != HelperController.ACTION_RESULT &&
                intent?.action != HelperController.ACTION_RESULT_V2
            ) {
                return
            }
            if (
                intent.getStringExtra(HelperController.EXTRA_PHASE) !=
                HelperController.PHASE_MAINTENANCE_WIFI
            ) {
                return
            }

            val action = intent.getStringExtra(HelperController.EXTRA_WIFI_ACTION) ?: "NONE"
            val attempted =
                intent.getBooleanExtra(HelperController.EXTRA_WIFI_ATTEMPTED, false)
            val success =
                intent.getBooleanExtra(HelperController.EXTRA_WIFI_TOGGLE_SUCCESS, true)
            val status =
                intent.getStringExtra(HelperController.EXTRA_STATUS)
                    ?: HelperController.STATUS_OK

            Log.i(
                TAG,
                "Temporary Wi-Fi result: action=$action attempted=$attempted " +
                    "success=$success status=$status"
            )

            if (cleanupState.cleanupPending && action == "OFF") {
                handler.removeCallbacks(cleanupTimeoutRunnable)
                val snapshot = cleanupState.pendingFinalSnapshot ?: return
                finishNow(snapshot)
            }
        }
    }

    private val helperResultReceiverV2 = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            helperResultReceiver.onReceive(context, intent)
        }
    }

    fun start(
        trigger: SyncMaintenanceTrigger,
        onFinished: (SyncMaintenanceSnapshot) -> Unit
    ): Boolean {
        if (active) return false

        this.trigger = trigger
        completionCallback = onFinished

        val providers = providersFactory()
        activeProviders = providers
        val current = SyncMaintenanceCoordinator(
            trigger = trigger,
            providers = providers
        )
        coordinator = current

        // Begin with network=false so the completion-capability guard and
        // no-target guard are evaluated before any Android side effect.
        val initial = current.begin(
            networkReady = false,
            nowMs = elapsedRealtime()
        )

        if (initial.phase == SyncMaintenancePhase.FINISHED) {
            finishNow(initial)
            return true
        }

        acquireWakeLock()

        if (trigger == SyncMaintenanceTrigger.PERIODIC_SLEEP) {
            maybeRequestTemporarySleepWifi()
        }

        startNetworkWait()
        return true
    }

    /**
     * Cancel an active session.
     *
     * restoreSleepWifi should be true only when the device is still meant to
     * remain asleep. On a real wake transition it must be false; the normal
     * Helper wake transaction owns restoration and clears its sleep cycle.
     */
    fun cancel(restoreSleepWifi: Boolean): SyncMaintenanceSnapshot? {
        val current = coordinator ?: return null

        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(stopConfirmRunnable)
        cleanupState.clearStopConfirmation()
        networkGate?.cancel()
        networkGate = null

        val snapshot = current.cancel()
        if (
            restoreSleepWifi &&
            trigger == SyncMaintenanceTrigger.PERIODIC_SLEEP
        ) {
            finishWithCleanup(snapshot)
        } else {
            finishNow(snapshot)
        }
        return snapshot
    }

    private fun startNetworkWait() {
        networkGate?.cancel()
        val gate = NetworkReadyGate(
            context = appContext,
            handler = handler,
            timeoutMs = NETWORK_TIMEOUT_MS
        ) { result ->
            networkGate = null
            val current = coordinator ?: return@NetworkReadyGate

            val snapshot =
                if (result == NetworkReadyGate.Result.VALIDATED) {
                    current.onNetworkReady(elapsedRealtime())
                } else {
                    current.onNetworkUnavailable()
                }

            if (snapshot.phase == SyncMaintenancePhase.FINISHED) {
                finishWithCleanup(snapshot)
            } else {
                handler.removeCallbacks(pollRunnable)
                handler.post(pollRunnable)
            }
        }
        networkGate = gate
        gate.start()
    }

    private fun maybeRequestTemporarySleepWifi() {
        val cycle = SleepCycleStore.current(appContext)
        val backend = RadioController.backend(appContext)
        val eligible =
            cycle.active &&
                cycle.wifiManaged &&
                when (backend) {
                    RadioBackend.PSERVER ->
                        DirectRadioStore.isPendingForCycle(
                            appContext,
                            cycle.cycleId
                        ) &&
                            RadioController.directSleepWifiOwned(
                                appContext,
                                cycle.cycleId
                            )

                    RadioBackend.HELPER ->
                        cycle.helperExpected &&
                            !cycle.helperRestored &&
                            HelperController.isInstalled(appContext)

                    RadioBackend.NONE ->
                        false
                }

        if (!eligible) {
            Log.i(TAG, "Periodic maintenance has no owned sleep Wi-Fi to open")
            return
        }

        if (backend == RadioBackend.HELPER) {
            registerHelperReceiver()
        }
        cleanupState.recordTemporaryWifiRequest(
            RadioController.setTemporaryWifi(appContext, enabled = true)
        )

        if (cleanupState.temporaryWifiOnRequested) {
            Log.i(
                TAG,
                "Requested temporary Wi-Fi ON for periodic maintenance via $backend"
            )
        }
    }

    private fun finishWithCleanup(snapshot: SyncMaintenanceSnapshot) {
        handler.removeCallbacks(pollRunnable)
        networkGate?.cancel()
        networkGate = null

        if (
            trigger == SyncMaintenanceTrigger.PERIODIC_SLEEP &&
            cleanupState.temporaryWifiOnRequested &&
            !cleanupState.cleanupPending
        ) {
            if (waitForProviderStopsBeforeWifiCleanup(snapshot)) {
                return
            }

            val backend = RadioController.backend(appContext)
            if (backend == RadioBackend.HELPER) {
                registerHelperReceiver()
            }
            cleanupState.beginCleanup(snapshot)

            if (RadioController.setTemporaryWifi(appContext, enabled = false)) {
                Log.i(
                    TAG,
                    "Requested temporary Wi-Fi OFF after maintenance via $backend"
                )
                if (backend == RadioBackend.PSERVER) {
                    finishNow(snapshot)
                    return
                }
                handler.postDelayed(
                    cleanupTimeoutRunnable,
                    WIFI_CLEANUP_TIMEOUT_MS
                )
                return
            }

            cleanupState.clearCleanupPending()
        }

        finishNow(snapshot)
    }

    private fun waitForProviderStopsBeforeWifiCleanup(
        snapshot: SyncMaintenanceSnapshot
    ): Boolean {
        val waitingIds =
            activeProviders
                .filter { it.id !in snapshot.failedProviderIds }
                .mapNotNull { provider ->
                    val confirmed =
                        runCatching { provider.isStopConfirmed() }
                            .getOrElse { error ->
                                Log.w(
                                    TAG,
                                    "Unable to confirm ${provider.id} STOP before Wi-Fi cleanup",
                                    error
                                )
                                null
                            }
                    if (confirmed == false) provider.id else null
                }

        if (waitingIds.isEmpty()) {
            handler.removeCallbacks(stopConfirmRunnable)
            cleanupState.clearStopConfirmation()
            return false
        }

        val now = elapsedRealtime()
        val startedAt = cleanupState.stopConfirmationStartedAtMs ?: now.also {
            cleanupState.startStopConfirmation(it)
            Log.i(
                TAG,
                "Waiting for provider STOP confirmation before Wi-Fi cleanup: " +
                    waitingIds.joinToString()
            )
        }

        if (now - startedAt >= STOP_CONFIRM_TIMEOUT_MS) {
            handler.removeCallbacks(stopConfirmRunnable)
            cleanupState.clearStopConfirmation()
            Log.w(
                TAG,
                "Provider STOP confirmation timed out before Wi-Fi cleanup: " +
                    waitingIds.joinToString()
            )
            return false
        }

        cleanupState.rememberPendingFinalSnapshot(snapshot)
        handler.removeCallbacks(stopConfirmRunnable)
        handler.postDelayed(
            stopConfirmRunnable,
            STOP_CONFIRM_POLL_INTERVAL_MS
        )
        return true
    }

    private fun finishNow(snapshot: SyncMaintenanceSnapshot) {
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(stopConfirmRunnable)
        handler.removeCallbacks(cleanupTimeoutRunnable)
        cleanupState.clearStopConfirmation()
        networkGate?.cancel()
        networkGate = null

        cleanupState.reset()
        unregisterHelperReceiver()
        releaseWakeLock()

        coordinator = null
        activeProviders = emptyList()
        trigger = null

        val callback = completionCallback
        completionCallback = null

        Log.i(
            TAG,
            "Maintenance finished: trigger=${snapshot.trigger} outcome=${snapshot.outcome} " +
                "completed=${snapshot.completedProviderIds} failed=${snapshot.failedProviderIds}"
        )
        callback?.invoke(snapshot)
    }

    private fun registerHelperReceiver() {
        if (helperReceiverRegistered) return

        ContextCompat.registerReceiver(
            appContext,
            helperResultReceiver,
            IntentFilter(HelperController.ACTION_RESULT),
            HelperController.PERMISSION,
            handler,
            ContextCompat.RECEIVER_EXPORTED
        )
        ContextCompat.registerReceiver(
            appContext,
            helperResultReceiverV2,
            IntentFilter(HelperController.ACTION_RESULT_V2),
            HelperController.PERMISSION_V2,
            handler,
            ContextCompat.RECEIVER_EXPORTED
        )
        helperReceiverRegistered = true
    }

    private fun unregisterHelperReceiver() {
        if (!helperReceiverRegistered) return
        helperReceiverRegistered = false
        runCatching {
            appContext.unregisterReceiver(helperResultReceiver)
        }
        runCatching {
            appContext.unregisterReceiver(helperResultReceiverV2)
        }
    }

    private fun acquireWakeLock() {
        val powerManager =
            appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return

        if (wakeLock?.isHeld == true) return
        wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SleepManager:SyncMaintenance"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        wakeLock = null
    }
}
