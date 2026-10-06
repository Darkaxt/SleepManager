# SleepManager

SleepManager helps Android handhelds, phones and tablets use less battery while they sleep.

This maintained fork adds state-aware [TailDNS](https://github.com/Darkaxt/TailDNS)
support alongside the official Tailscale client and publishes updates from the
[Darkaxt repository](https://github.com/Darkaxt/SleepManager). Upstream project
history and attribution remain with [Baggio94/SleepManager](https://github.com/Baggio94/SleepManager).

It can temporarily turn off Wi-Fi, Bluetooth and supported background services when the screen turns off, then restore only what it changed when the device wakes. It also includes sleep battery statistics, sync automation and extra features for compatible Android clamshell devices.

**No root, Shizuku or ADB is required for normal use.**

## Main Features

- Turn **Wi-Fi** and **Bluetooth** off during sleep and restore them safely on wake.
- Enable **Android Battery Saver** during sleep on supported devices and restore its previous state on wake.
- Manage **Syncthing-Fork, BasicSync, RAOfflineProxy, Tailscale / TailDNS and JamesDSP** during sleep and wake.
- Run optional syncs **before sleep, after wake or periodically while sleeping** with supported providers.
- Track sleep battery drain, measured mAh use, deep sleep, drain rate and standby estimates.
- Protect compatible **clamshell handhelds** from closed-lid false wakes.
- Add clamshell-specific **lid, dock, power-button and Charging Separation** behavior on supported devices.
- Use optional **grace periods, custom delays, battery, charging, Battery Saver and schedule conditions** to control when sleep actions run.
- Keep a detailed **Activity log** and generate copyable diagnostics for troubleshooting.
- Check, download, verify and install stable **SleepManager and Helper updates** directly from the app.

SleepManager is designed mainly for Android gaming handhelds such as **AYN**, **Retroid**, **ANBERNIC**, **AYANEO** and **KONKR** devices, but it also works on regular Android phones and tablets.

## Quick Start

1. Download and install the latest **SleepManager APK** from [Releases](https://github.com/Darkaxt/SleepManager/releases).
2. If you want Wi-Fi or Bluetooth control, download and install the optional **SleepManager Helper** from [Releases](https://github.com/Darkaxt/SleepManager/releases).
3. Open SleepManager and choose what you want it to manage during sleep.
4. Enable **SleepManager**, then tap **Finish setup**.

SleepManager always tries to **restore only what it changed**. For example, if Wi-Fi was already off before sleep, it stays off after wake.

Depending on the features you use, Android may ask for additional permissions. See [Permissions](#permissions) for details.

> **Note:** The Helper has no launcher icon or separate interface.

## System Controls

SleepManager can manage several Android system features during sleep.

- **Wi-Fi** — turns Wi-Fi off during sleep and restores it on wake only if SleepManager changed it. Requires the optional SleepManager Helper.
- **Bluetooth** — uses the same state-aware behavior as Wi-Fi: it is restored only if SleepManager turned it off. Requires the optional SleepManager Helper.
- **Battery Saver** — enables Android Battery Saver during sleep on supported devices and restores the previous state on wake.

SleepManager tracks ownership of these changes so it does not overwrite a state that was already set by the user before sleep.

## Sleep Behavior

SleepManager can delay sleep actions after the screen turns off, which helps avoid unnecessary state changes during very short screen-off periods.

- **Grace Period** — choose Immediate, 5 s or 10 s before SleepManager starts the configured sleep actions.
- **Custom Delay** — choose a longer delay of 1, 5, 10 or 30 minutes from Advanced settings.

If the device wakes before the delay finishes, the pending sleep actions are cancelled.

## Clamshell Options

On compatible clamshell handhelds, SleepManager can use the device lid sensor to add extra sleep and dock behavior.

- **Closed-Lid Protection** — if the device wakes while the lid is still closed, SleepManager returns it to sleep instead of running the normal wake sequence. An active external display is treated as intentional docked use.
- **Sleep When External Display Disconnects** — with the lid closed, put the device to sleep when an external display is disconnected.
- **Power Button Sleeps With Lid Closed** — when the device is awake with the lid closed, pressing Power puts it back to sleep, including while docked or after disconnecting an external display.
- **Disable Charging Separation With Lid Closed** — on supported devices, temporarily disables Charging Separation while the lid is closed so the battery can charge normally, then restores the previous setting when appropriate.

These options are shown only when SleepManager detects the required hardware support.

Compatible clamshell devices may include handhelds such as **AYN Thor**, **Retroid Pocket Duo** and **Retroid Pocket Flip** when they expose a supported lid sensor and the required system controls.

## App Integrations

SleepManager can temporarily control supported background apps during sleep and restore them on wake.

### Syncthing-Fork

SleepManager can stop Syncthing-Fork before sleep and send **FOLLOW** on wake so Syncthing-Fork can resume according to its own run conditions.

In Syncthing-Fork, enable:

**Settings → Behaviour → Service Control by Broadcast**

Then enable **Syncthing-Fork** in SleepManager.

### BasicSync

SleepManager can stop BasicSync during sleep and restore its previous mode on wake.

In BasicSync, enable:

**Allow remote control**

- **BasicSync 3.18+** supports state-aware sleep/wake control.
- **BasicSync 3.19+** also supports completion-aware advanced sync features.

See [Advanced Sync Behavior](#advanced-sync-behavior) for the additional sync modes.

### RAOfflineProxy

SleepManager can automatically stop **RAOfflineProxy** during sleep and restore it on wake. If RAOfflineProxy is still caching games or waiting for its next cache window, SleepManager keeps the proxy and Wi-Fi available until it is safe to stop them.

For reliable background restart on Android 12+, set RAOfflineProxy battery usage to **Unrestricted**:

**Settings → Apps → RAOfflineProxy → App battery usage → Unrestricted**

RAOfflineProxy **v2.0.0-alpha1 or newer** is required for SleepManager integration.

### Tailscale / TailDNS

SleepManager can disconnect Tailscale or TailDNS during sleep and reconnect it
on wake only when SleepManager performed the disconnect.

Install and sign in to either the official **Tailscale Android app** or
**TailDNS**, then enable **Tailscale / TailDNS** in SleepManager. If both are
installed, SleepManager controls the client that owns the active Tailscale VPN
and restores that same client after wake.

### JamesDSP

SleepManager can turn supported JamesDSP builds **OFF during sleep** and **ON again on wake**.

Supported apps include **JamesDSP Manager** and **RootlessJamesDSP**.

Unlike the other state-aware integrations, JamesDSP does not currently expose a reliable power-state query, so SleepManager uses explicit OFF/ON control when this integration is enabled.

## Advanced Sync Behavior

SleepManager can automate synchronization around sleep and wake transitions with supported completion-aware sync providers.

- **Periodic Sync While Sleeping** — while the device remains asleep, SleepManager can run a sync every 24 hours, wait for it to complete, then stop the sync app and restore the previous sleep state.
- **Sync Then Stop on Sleep & Wake** — runs a sync before sleep and again after wake. Once each sync completes, SleepManager stops the sync app to reduce unnecessary background activity. For example, when the device wakes, SleepManager starts the sync client, syncs your latest saves and states, waits for completion, then stops the client again to save battery while you play. When you're done playing and put the device back to sleep, SleepManager starts the client again, syncs the changes from your gaming session, waits for completion, stops the client, and then runs your configured sleep automation cycle.

These features currently require **BasicSync 3.19+** with **Allow remote control** enabled.

**Syncthing-Fork** continues to support normal STOP/FOLLOW sleep and wake control. Completion-aware Advanced Sync support for Syncthing-Fork is currently **pending / in progress**.

## Advanced Sleep Conditions

SleepManager can apply additional conditions before running the configured sleep actions.

All enabled conditions use **AND logic**: every enabled condition must be true for the sleep automation to run.

- **Custom Delay** — choose a longer delay before sleep actions start: 1, 5, 10 or 30 minutes.
- **Battery Level** — run sleep actions only when the battery is below the selected threshold.
- **Not Charging** — run sleep actions only when the device is unplugged.
- **Battery Saver** — run sleep actions only when Android Battery Saver is ON or OFF, or ignore its state.
- **Schedule** — restrict sleep actions to a selected time window.

If one enabled condition is not met, SleepManager skips the sleep actions for that sleep cycle.

## Battery Stats

SleepManager tracks battery behavior during sleep sessions and provides both recent and long-term standby information.

- **Last Sleep** — shows the battery change during the most recent sleep session, along with its duration and drain rate.
- **Measured mAh** — when Android exposes charge-counter data, SleepManager can estimate the actual amount of battery used during sleep.
- **Battery Health** — compares reported full-charge capacity with battery design capacity when Android exposes trustworthy values; suspect readings are shown as unavailable rather than as a misleading percentage.
- **Deep Sleep** — shows how much of the sleep session the device spent in deep sleep.
- **7-Day Average** — calculates the average sleep drain rate from eligible recent sleep sessions.
- **Estimated Standby** — estimates how long the device could remain in standby based on the measured average drain.
- **Best / Worst Drain** — shows the best and worst measured standby drain rates from eligible sessions.

Short sleep sessions still appear in **Last Sleep**, but only non-charging sleep sessions of at least **3 hours** are used for long-term averages and standby estimates.

When more precise battery data is available, SleepManager uses it automatically.

## Activity and Diagnostics

The **Activity** page shows recent SleepManager actions with timestamps, making it easier to see what happened during sleep and wake transitions.

Use **Copy log** to generate a detailed diagnostic report that includes:

- SleepManager version, enabled state and service status
- Device model, Android version and supported hardware capabilities
- Enabled system controls, clamshell options and app integrations
- Current Wi-Fi, Bluetooth, Battery Saver and integration states
- Active sleep/wake transaction and pending restore information
- Recent Wi-Fi toggle results and Airplane mode state
- BasicSync runtime state and sync counters when available
- RAOfflineProxy runtime, queue and restore state when available
- Recent SleepManager activity
- Recent Android process-exit information on Android 11+ when available

The diagnostic report is designed to make troubleshooting and bug reports easier without requiring ADB.

## Updates

SleepManager includes a built-in updater for both the main app and the optional Helper.

- **Automatic Checks** — when enabled, SleepManager checks for new stable releases when you open the app and periodically in the background.
- **Manual Checks** — use **About → Updates → Check for updates** at any time.
- **Main and Helper Updates** — SleepManager tracks the main app and Helper independently and can also offer the Helper for installation if it is not installed.
- **Update Notifications** — SleepManager can notify you when a new stable version is available.
- **Release Notes** — available directly from the update section before installing a new version.
- **Secure Installation** — downloaded APKs are verified before Android's installer opens, including the SHA-256 digest, package identity, version information and SleepManager signing certificate.

Official releases keep the same package IDs and signing identity, so normal updates preserve your settings.

## Permissions

SleepManager requests only the permissions needed for the features you choose to use.

- **Notifications** — used for SleepManager's foreground-service and update notifications. On Android 13+, Android may ask you to allow notifications.
- **Install Unknown Apps** — required only when installing SleepManager or Helper updates directly from inside the app. SleepManager verifies downloaded update packages before opening Android's installer.
- **Exact Alarms** — required when using a **Custom Delay** so SleepManager can apply the configured delay reliably while the device is sleeping.
- **Device Admin** — required only for **Closed-Lid Protection**. It allows SleepManager to immediately return a compatible clamshell device to sleep after an accidental closed-lid wake.

SleepManager also checks Android's **Battery Optimization** and **Unused App Restrictions** under **About → Background Reliability**. These are not required for every device, but restrictive Android background settings can prevent SleepManager from running reliably during long sleep periods.

The optional **SleepManager Helper** handles Wi-Fi and Bluetooth control. It has no launcher icon or separate interface and communicates only with the signed SleepManager app.

## Compatibility

- Android **9 / API 28 or newer**
- RAOfflineProxy **v2.0.0-alpha1 or newer** for SleepManager integration
- Main package: `com.med.sleepmanager`
- Helper package: `com.med.sleepmanager.helper`
- Clamshell-specific controls appear only when a compatible lid sensor and the required hardware support are detected

## Troubleshooting

### Wi-Fi or Bluetooth Does Not Change

Make sure the **SleepManager Helper** is installed and up to date.

Then check **Activity** for the latest sleep/wake actions and use **Copy log** if you need more details.

### Syncthing-Fork Does Not Stop or Resume

Make sure this option is enabled in Syncthing-Fork:

**Settings → Behaviour → Service Control by Broadcast**

Then confirm **Syncthing-Fork** is enabled in SleepManager.

### BasicSync Does Not Respond

Make sure **Allow remote control** is enabled in BasicSync.

For state-aware sleep/wake control, use **BasicSync 3.18+**. Advanced completion-aware sync features require **BasicSync 3.19+**.

### RAOfflineProxy Does Not Start on Wake

Confirm that RAOfflineProxy **v2.0.0-alpha1 or newer** is installed and that its Android battery usage is set to **Unrestricted** under **Settings → Apps → RAOfflineProxy → App battery usage**.

If SleepManager shows **Control permission missing**, install/update RAOfflineProxy first and then reinstall/update SleepManager so Android can grant RAOfflineProxy's control permission.

Use **Activity → Copy log** to check the proxy state, queue state, pending ownership and any background-start error.

### Tailscale / TailDNS Does Not Reconnect

Open the selected client and confirm that you are signed in and that it can connect normally.

SleepManager reconnects Tailscale only when it performed the sleep disconnect.

### JamesDSP Does Not Switch State

Make sure a supported **JamesDSP Manager** or **RootlessJamesDSP** build is installed and that the JamesDSP integration is enabled in SleepManager.

### Closed-Lid Protection Does Not Work

Make sure SleepManager detects a compatible lid sensor and that **Closed-Lid Protection** is enabled.

If Android asks for **Device Admin** permission, it must be granted for SleepManager to return the device to sleep after a closed-lid false wake.

### Custom Delay Does Not Trigger Correctly

Make sure Android has granted the required **Exact Alarm** permission.

### SleepManager Unexpectedly Stops or Does Not Run Reliably in the Background

Open:

**About → Background Reliability**

Check **Battery Optimization** and **Unused App Restrictions**.

On Android 11+, you can also use **Activity → Copy log** to include recent Android process-exit information when available.

### Still Having an Issue?

Use **Activity → Copy log**, then open a GitHub issue and include:

- your device model
- Android version
- SleepManager version
- Helper version, if installed
- the features and integrations you are using
- steps to reproduce the issue
- the copied diagnostics

[Open a GitHub issue](https://github.com/Darkaxt/SleepManager/issues)

## Build from Source

Requirements:

- JDK 17+
- Android SDK 36
- Android build-tools 36.0.0

On macOS:

```bash
chmod +x bootstrap_and_build.sh
./bootstrap_and_build.sh
```

Generated debug APKs:

```text
app/build/outputs/apk/debug/app-debug.apk
helper/build/outputs/apk/debug/helper-debug.apk
```

## More Information

- [Latest Release](https://github.com/Darkaxt/SleepManager/releases/latest)
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — what's new in the current release
- [CHANGELOG.md](CHANGELOG.md) — full version history
