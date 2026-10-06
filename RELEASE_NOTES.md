# SleepManager 0.7.1.1 / Helper 1.1.2.1

SleepManager 0.7.1 adds **RAOfflineProxy integration**, improves battery measurement on devices with unreliable capacity reporting, and introduces **Battery Health**. It also includes several smaller reliability and diagnostics improvements.

## RAOfflineProxy Integration

SleepManager can now manage **RAOfflineProxy** as part of the normal sleep/wake cycle.

- SleepManager checks the real RAOfflineProxy state before changing it.
- When the cache queue is empty or in a safe state, SleepManager can stop RAOfflineProxy before managed Wi-Fi is turned off.
- If RAOfflineProxy is actively **caching** or **waiting** for its next cache window, SleepManager keeps the proxy and Wi-Fi available until the queue reaches a safe state.
- Queue waiting is event-driven and does not use permanent polling.
- RAOfflineProxy is restored on a real wake only when SleepManager performed the stop.
- Closed-lid false wakes do not incorrectly restart the proxy.
- Pending queue, stop and restore ownership are preserved across SleepManager service recovery.
- Diagnostics now include RAOfflineProxy state, queue and restore information.

For reliable background restart on Android 12+, set RAOfflineProxy battery usage to **Unrestricted**:

**Settings → Apps → RAOfflineProxy → App battery usage → Unrestricted**

RAOfflineProxy **v2.0.0-alpha1 or newer** is required for SleepManager integration.

## Better Battery Precision

Some devices can report an unrealistic full-charge capacity while also scaling the battery charge counter by the same incorrect amount. SleepManager can now detect this situation and normalize the charge counter against the trusted design capacity instead of immediately falling back to Android's whole-number battery percentage. This preserves sub-percent battery movement, so long sleep sessions can still report **measured mAh and precise drain** even when Android itself remains on the same displayed percentage.

## Battery Health

The Stats page now shows **Battery Health** when Android provides trustworthy battery-capacity values. Battery Health compares the reported full-charge capacity with the battery design capacity. If Android reports an implausible full-charge value, SleepManager shows **Unavailable** instead of displaying a misleading health percentage.

## Reliability and Diagnostics

- Improved handling of battery-capacity data that is clearly outside a realistic range.
- Diagnostics expose the raw and selected battery-capacity values used by SleepManager, making battery-reporting problems easier to identify.
- Sleep/wake ownership remains state-aware so SleepManager restores only what it actually changed.
- RAOfflineProxy queue and restore ownership are preserved safely across service recovery.

## Compatibility

- SleepManager **0.7.1.1 / versionCode 553**
- SleepManager Helper remains **1.1.2.1 / versionCode 1116**
- Android **9 / API 28 or newer**
- RAOfflineProxy **v2.0.0-alpha1 or newer** is required for SleepManager integration
- Existing settings are preserved when updating
- No root, Shizuku or ADB is required for normal use

## First Install

1. Download and install **SleepManager 0.7.1.1** from the release assets below.
2. If you use Wi-Fi or Bluetooth management, install **SleepManager Helper 1.1.2.1** as well.
3. Open SleepManager and choose what you want it to manage during sleep.
4. Enable **SleepManager**, then tap **Finish setup**.

## Updating

Install SleepManager 0.7.1.1 over your existing version or use the built-in updater from **About → Updates**. Your existing SleepManager settings are preserved.

If SleepManager Helper **1.1.2.1** is already installed, there is no Helper update required for this release.

## Maintained fork compatibility

Official Tailscale and TailDNS remain supported. Sleep control selects the active
supported VPN owner and restores only the exact package successfully disconnected.
Both APKs retain the permanent fork certificate and signature-protected Helper
contract. Updates come only from Darkaxt/SleepManager, not upstream-signed builds.
Install fork APKs over previous fork builds to preserve settings. No device is
installed or modified automatically by this release.
