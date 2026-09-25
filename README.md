# SleepManager

SleepManager helps Android handhelds, phones and tablets use less battery while they sleep.

It can temporarily turn off Wi-Fi, Bluetooth and supported background services when the screen turns off, then restore only what it changed when the device wakes. It also includes sleep battery statistics, sync automation and extra features for the **AYN Thor**.

**No root, Shizuku or ADB is required for normal use.**

## Main features

- Turn **Wi-Fi** and **Bluetooth** off during sleep and restore them safely on wake.
- Pause and resume **Syncthing-Fork**.
- Manage **BasicSync**, **Tailscale / TailDNS** and **JamesDSP**.
- Run optional syncs before sleep, after wake or periodically while sleeping with supported providers.
- Track sleep battery drain, mAh use, deep sleep and standby estimates.
- Protect the **AYN Thor** from closed-lid false wakes.
- Add Thor-specific dock and power-button sleep behavior.
- Keep an activity log and copyable diagnostics.
- Check and install stable updates from inside the app.

SleepManager is designed mainly for Android gaming handhelds such as **AYN Thor**, **AYN Odin** and **Retroid** devices, but it also works on regular Android phones and tablets.

## Quick start

1. Install the latest **SleepManager** APK.
2. Install the optional **SleepManager Helper** if you want Wi-Fi or Bluetooth control.
3. Open SleepManager and choose what you want it to manage during sleep.
4. Enable SleepManager, then tap **Finish setup**.

SleepManager always tries to **restore only what it changed**. For example, if Wi-Fi was already off before sleep, it stays off after wake.

## Sleep actions

SleepManager can manage:

- **Wi-Fi** — off during sleep, restored only if SleepManager changed it.
- **Bluetooth** — same state-aware behavior as Wi-Fi.
- **Syncthing-Fork** — paused for sleep and resumed after wake.
- **BasicSync** — stopped during sleep when appropriate and restored to its previous mode.
- **Tailscale / TailDNS** — the active client is disconnected for sleep and only that same client is reconnected when SleepManager performed the disconnect.
- **JamesDSP** — optional OFF during sleep / ON after wake behavior.

You can also add conditions such as a grace period, battery level, charging state, Battery Saver state or a schedule.

## Advanced sync

With **BasicSync 3.19+**, SleepManager can use two optional sync modes:

- **Periodic sync while sleeping** — periodically wake the connection, sync, then return to the previous sleep state.
- **Sync then stop on sleep & wake** — sync before sleep and after wake, then stop BasicSync again when the sync is finished.

BasicSync 3.18+ still works for the normal state-aware sleep/wake integration.

Syncthing-Fork continues to support normal STOP/FOLLOW sleep/wake control, but the advanced sync modes currently require BasicSync 3.19+.

## Battery statistics

SleepManager can show:

- battery change during the last sleep
- sleep duration
- drain per hour
- measured mAh when available
- deep-sleep percentage
- 7-day average drain
- estimated standby time
- best / worst measured drain

When Android exposes more precise battery data, SleepManager uses it automatically.

Short sleeps are still shown in **Last sleep**. Only eligible, non-charging sessions of at least **3 hours** are used for long-term averages and standby estimates.

## AYN Thor

SleepManager adds extra options when it detects the Thor lid sensor.

### Closed-lid protection

If the Thor wakes unexpectedly while the lid is still closed, SleepManager can put it back to sleep instead of restoring Wi-Fi, Bluetooth and other services too early.

This feature needs Android **Device Admin** permission for the return-to-sleep action.

### Dock support

Closed-lid protection understands when an external display is connected, so docked use is not treated as a false wake.

Optional Thor controls include:

- **Sleep when external display disconnects**
- **Power button sleeps with lid closed**

## Integrations

### Syncthing-Fork

In Syncthing-Fork, enable:

**Settings → Behaviour → Service Control by Broadcast**

Then enable Syncthing-Fork in SleepManager.

### BasicSync

In BasicSync, enable:

**Allow remote control**

Use BasicSync 3.18+ for state-aware sleep/wake control, or **3.19+** for the advanced sync modes.

### Tailscale / TailDNS

Install and sign in to either the official Tailscale Android app or
[TailDNS](https://github.com/Darkaxt/TailDNS), then enable Tailscale / TailDNS
in SleepManager. If both are installed, SleepManager controls the client that
owns the active Tailscale VPN and restores that same client after wake.

### JamesDSP

Install a supported JamesDSP build, then enable JamesDSP in SleepManager if you want the OFF-during-sleep / ON-after-wake behavior.

## Activity and diagnostics

The Activity page shows recent SleepManager actions and can generate a copyable diagnostic report.

On Android 11+, diagnostics can also include the reason Android stopped the previous SleepManager process when that information is available. This can help identify cases such as low-memory kills, crashes or user/system stops.

## Installation

### 1. Install SleepManager

Download the latest stable APK from [GitHub Releases](https://github.com/Baggio94/SleepManager/releases):

`SleepManager-<version>.apk`

### 2. Install the Helper if needed

If you want Wi-Fi or Bluetooth control, open:

**About → Updates → Install Helper**

You can also install the matching `SleepManager-Helper-<version>.apk` manually.

The Helper has no launcher icon or separate interface.

### 3. Finish setup

Choose your sleep actions and optional rules, enable SleepManager, then tap **Finish setup**.

Depending on the features you use, Android may ask for notification permission, permission to install updates, or Device Admin for Thor closed-lid protection.

## Updates

SleepManager can check both the main app and Helper for updates from:

**About → Updates**

Official releases keep the same package IDs and signing identity, so normal updates preserve your settings.

## Compatibility

- Android **9 / API 28 or newer**
- Main package: `com.med.sleepmanager`
- Helper package: `com.med.sleepmanager.helper`
- Thor-specific controls appear only when the Thor lid sensor is detected

## Troubleshooting

### Wi-Fi or Bluetooth does not change

Make sure the **SleepManager Helper** is installed, then check the Activity page for details.

### Syncthing-Fork does not pause or resume

Make sure **Settings → Behaviour → Service Control by Broadcast** is enabled in Syncthing-Fork.

### BasicSync does not respond

Make sure **Allow remote control** is enabled in BasicSync.

### Tailscale / TailDNS does not reconnect

Open the selected client and confirm it is signed in and can connect normally.

### SleepManager unexpectedly stops

On Android 11+, reopen SleepManager and use **Activity → Copy diagnostics**. The process-exit section may show why Android stopped it.

## Build from source

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

## More information

- [Latest release](https://github.com/Baggio94/SleepManager/releases/latest)
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — what's new in the current release
- [CHANGELOG.md](CHANGELOG.md) — full version history
