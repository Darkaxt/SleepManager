# SleepManager 0.6.1.1

This maintained fork release merges the complete upstream SleepManager 0.6.1
history while retaining state-aware **TailDNS** support alongside the official
Tailscale client. SleepManager selects the client that owns the active Tailscale
VPN, records the exact package it disconnected, and reconnects only that same
client after wake.

The built-in updater continues to use the Darkaxt release origin and permanent
signing identity. The Main app and Helper remain a paired, signature-protected
release.

SleepManager 0.6.1 focuses on **smarter sleep controls, broader clamshell support and improved reliability**.

## What's New

### Battery Saver During Sleep

SleepManager can now enable **Android Battery Saver** while the device sleeps on supported devices, then restore the previous state when the device wakes.

If Battery Saver was already enabled before sleep, SleepManager leaves it unchanged.

Battery Saver is applied only after any required pre-sleep sync and app shutdown work has finished.

### Better Clamshell Support

SleepManager's closed-lid features are no longer limited to the AYN Thor-specific lid path.

0.6.1 adds support for compatible Android clamshell handhelds that expose a standard lid sensor, while keeping full support for the **AYN Thor**.

Available features can include:

- **Closed-Lid Protection** — returns the device to sleep after an accidental wake while the lid is still closed.
- **Sleep When External Display Disconnects** — puts the device to sleep when a docked display is disconnected with the lid closed.
- **Power Button Sleeps With Lid Closed** — allows the Power button to return the device to sleep while the lid remains closed.
- **Disable Charging Separation With Lid Closed** — on supported devices, temporarily disables Charging Separation so the battery can charge while the lid is closed, then safely restores the previous setting.

External-display use remains dock-aware, so intentional docked sessions are not treated as false wakes.

### More Reliable Sleep and Wake Automation

SleepManager is now more careful about the order in which apps and system controls are changed.

- Managed sync apps are allowed to stop before Battery Saver or radio changes are applied.
- If **BasicSync** is already syncing when the device goes to sleep, SleepManager can let the active sync finish before stopping it.
- Closed-lid false wakes no longer interrupt an active sleep, sync or maintenance cycle.
- Wake restoration is more reliable after Android restarts the SleepManager background service.
- SleepManager continues to restore only the states it changed whenever that state can be tracked.

### BasicSync Improvements

**BasicSync 3.18+** continues to support state-aware normal sleep/wake control.

With **BasicSync 3.19+**:

- Completion-aware Advanced Sync works independently even when Syncthing-Fork is also enabled.
- An active sync can finish before SleepManager stops BasicSync for sleep.
- Advanced Sync modes continue to track sync completion before stopping the client.

**Syncthing-Fork** keeps its normal STOP/FOLLOW sleep and wake behavior. Completion-aware Advanced Sync support for Syncthing-Fork is still in progress.

### Background Reliability

A new **About → Background Reliability** section helps identify Android settings that may prevent SleepManager from running reliably during long sleep periods.

It checks:

- **Battery Optimization**
- **Unused App Restrictions**

On devices where active Device Admin already exempts SleepManager from unused-app restrictions, the app now recognizes and displays that exemption correctly.

### Cleaner Home and Settings

The Home screen is now organized more clearly into:

- **System Controls**
- **Sleep Behavior**
- **Clamshell Options**
- **App Integrations**

Battery Saver also shows its current Android state directly in the app.

The behavior summary has been expanded so it is easier to see what SleepManager is currently configured to do during sleep and wake.

### Better Activity and Diagnostics

Diagnostics now include more information about:

- Enabled sleep and clamshell options
- Battery Saver and Charging Separation state
- Lid-sensor support
- Integration and restore state
- Recent Android process exits when available

This should make troubleshooting and GitHub bug reports easier without requiring ADB.

### Update Experience

- Added direct access to **Release Notes** from the update section.
- SleepManager and the optional Helper continue to be checked and updated independently.
- Downloaded APKs are verified before Android's installer opens.

## SleepManager Helper 1.1.1.1

The optional Helper also receives an update in this release.

- Improved Wi-Fi and Bluetooth restore reliability across sleep/wake cycles.
- Restore requests are now tied more safely to the sleep cycle that created them.
- Repeated restore requests can recognize an already completed restore instead of incorrectly reporting a pending problem.
- Updated Bluetooth handling for better compatibility with current Android devices and handheld firmware.

The Helper is only required if you want SleepManager to manage **Wi-Fi or Bluetooth**. It has no launcher icon or separate interface.

## Compatibility

- Android **9 / API 28 or newer**
- **BasicSync 3.18+** for state-aware normal sleep/wake control
- **BasicSync 3.19+** for completion-aware Advanced Sync
- **Syncthing-Fork** STOP/FOLLOW sleep/wake control remains supported
- **SleepManager Helper 1.1.1.1**
- Clamshell-specific options appear only when the required compatible hardware is detected

## Installation

1. Download and install **SleepManager 0.6.1.1** from the release assets below.
2. If you use Wi-Fi or Bluetooth management, download and install **SleepManager Helper 1.1.1.1** as well. You can also install or update the Helper later from **About → Updates**.
3. Open SleepManager and review the sleep actions, integrations and new options you want to use.
4. Enable **SleepManager**, then tap **Finish setup**.

If you are updating from an earlier official release, install the new APK over the existing app. Your SleepManager settings are preserved.

Depending on the features you use, Android may ask for additional permissions or background-reliability settings.

**No root, Shizuku or ADB is required for normal use.**
