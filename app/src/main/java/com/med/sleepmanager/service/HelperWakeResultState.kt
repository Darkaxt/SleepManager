package com.med.sleepmanager.service

/**
 * In-memory snapshot of the latest Compatibility Helper wake result.
 *
 * Helper IPC, wake-summary formatting and connector restore orchestration stay
 * in SleepManagerService. Reset methods intentionally preserve the legacy
 * field-by-field semantics used before this state was extracted.
 */
internal class HelperWakeResultState {
    var wifiManaged: Boolean = false
        private set

    var wifiChanged: Boolean = false
        private set

    var wifiAttempted: Boolean = false
        private set

    var wifiToggleSuccess: Boolean = true
        private set

    var wifiAirplaneMode: Boolean = false
        private set

    var bluetoothManaged: Boolean = false
        private set

    var bluetoothChanged: Boolean = false
        private set

    fun record(
        wifiManaged: Boolean,
        wifiChanged: Boolean,
        wifiAttempted: Boolean,
        wifiToggleSuccess: Boolean,
        wifiAirplaneMode: Boolean,
        bluetoothManaged: Boolean,
        bluetoothChanged: Boolean
    ) {
        this.wifiManaged = wifiManaged
        this.wifiChanged = wifiChanged
        this.wifiAttempted = wifiAttempted
        this.wifiToggleSuccess = wifiToggleSuccess
        this.wifiAirplaneMode = wifiAirplaneMode
        this.bluetoothManaged = bluetoothManaged
        this.bluetoothChanged = bluetoothChanged
    }

    /**
     * Matches the existing screen-on reset exactly. Diagnostic Wi-Fi fields are
     * retained until a new Helper result arrives; they are ignored while
     * wifiManaged is false.
     */
    fun beginWake() {
        wifiManaged = false
        wifiChanged = false
        bluetoothManaged = false
        bluetoothChanged = false
    }

    /**
     * Matches the existing Helper-send failure path, which only cleared the
     * managed flags after the normal wake reset.
     */
    fun markHelperUnavailable() {
        wifiManaged = false
        bluetoothManaged = false
    }
}
