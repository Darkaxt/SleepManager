# SleepManager 0.7.0.1 / Helper 1.1.2.1

This maintained fork incorporates the complete upstream SleepManager 0.7.0
release while preserving support for both official Tailscale and TailDNS.
Sleep control selects the active supported VPN owner, records the exact package
successfully disconnected, and restores only that package on wake.

## Upstream improvements

- Refactored sleep/wake engine with improved service-restart recovery.
- Closed-lid false-wake handling preserves the original cycle and pending restores.
- Cycle-aware Main/Helper communication rejects delayed or mismatched responses.
- Improved Wi-Fi and Bluetooth restoration.
- Battery Saver accounts for external power and restores its original state.
- Invalid battery-capacity readings are detected; unavailable Thor lid sensors
  produce a visible warning.
- Expanded multi-cycle diagnostics remain opt-in and off by default.

## Fork compatibility

- Main: `com.med.sleepmanager`, versionCode **551**.
- Helper: `com.med.sleepmanager.helper`, versionCode **1116**.
- The permanent fork certificate and signature-protected Helper remain unchanged.
- Updates and both matching APKs come only from `Darkaxt/SleepManager`.
- Android 9 / API 28 or newer; no root required for normal use.

Install both APKs over the previous fork versions to preserve settings. The Helper
is required only for Wi-Fi/Bluetooth management. Updates are also available from
**About → Updates**. Open SleepManager once after updating.

This release does not install or modify anything on your devices automatically.
