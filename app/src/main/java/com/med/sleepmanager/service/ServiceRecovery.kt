package com.med.sleepmanager.service

import com.med.sleepmanager.data.RecoveryEvent
import com.med.sleepmanager.data.RestoreParticipants

/**
 * Service operations used by process-death recovery. Every action keeps its
 * own internal guards (for example the RAOfflineProxy restore only runs while
 * its integration is unmanaged); recovery only decides which actions run.
 */
internal interface ServiceRecoveryHost {
    fun isRealWake(): Boolean
    fun isEffectivelySleeping(): Boolean
    fun isPending(participantId: String): Boolean

    /** Restores BasicSync stop ownership when the app or its integration is no longer managing it. */
    fun restoreOwnedBasicSyncIfUnmanaged()

    /** Restores the RAOfflineProxy connector when its integration is no longer managed. */
    fun restoreRaOfflineProxyIfUnmanaged()

    /** Owned Battery Saver: restore on a real wake or re-apply while asleep, per BatterySaverPolicy. */
    fun recoverBatterySaver()

    /** Owned Charging Separation: restore or re-apply for the closed lid, per ChargingSeparationPolicy. */
    fun recoverChargingSeparation()

    fun resumeRaOfflineProxySleepGate()
    fun reapplyDirectRadioSleepState(event: RecoveryEvent)
    fun reapplyHelperSleepWifi(event: RecoveryEvent)
    fun restoreKeyMapper()

    /** Replays the current screen state through the regular screen-on / screen-off flows. */
    fun replayScreenState()
}

/** The hand-wired recovery of 0.8.0.12, kept verbatim as the characterization baseline. */
internal object LegacyServiceRecovery {
    fun run(event: RecoveryEvent, host: ServiceRecoveryHost) {
        when (event) {
            RecoveryEvent.CREATE -> {
                host.restoreOwnedBasicSyncIfUnmanaged()
                host.restoreRaOfflineProxyIfUnmanaged()
            }

            RecoveryEvent.CREATE_ENABLED -> {
                host.recoverBatterySaver()
                host.recoverChargingSeparation()
                if (!host.isEffectivelySleeping()) return
                if (host.isPending(RestoreParticipants.RA_OFFLINE_PROXY_SLEEP_GATE)) {
                    host.resumeRaOfflineProxySleepGate()
                    return
                }
                if (host.isPending(RestoreParticipants.DIRECT_RADIO)) {
                    host.reapplyDirectRadioSleepState(event)
                    return
                }
                if (host.isPending(RestoreParticipants.HELPER)) {
                    host.reapplyHelperSleepWifi(event)
                }
            }

            RecoveryEvent.START_ENABLED -> {
                host.restoreOwnedBasicSyncIfUnmanaged()
                if (host.isRealWake()) host.restoreKeyMapper()
            }

            RecoveryEvent.SCREEN_STATE_REPLAY -> host.replayScreenState()

            RecoveryEvent.DESTROY_ENABLED -> {
                if (!host.isEffectivelySleeping()) return
                when {
                    host.isPending(RestoreParticipants.DIRECT_RADIO) ->
                        host.reapplyDirectRadioSleepState(event)

                    host.isPending(RestoreParticipants.HELPER) ->
                        host.reapplyHelperSleepWifi(event)
                }
            }
        }
    }
}
