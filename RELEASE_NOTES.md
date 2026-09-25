# SleepManager 0.6.0.1

This fork release adds state-aware **TailDNS** support while retaining official
Tailscale support. SleepManager disconnects the client that owns the active
Tailscale VPN before managed Wi-Fi shutdown and reconnects only that same client
after wake.

The built-in updater now follows the Darkaxt fork and verifies this fork's
permanent signing certificate. Both the main app and Helper must use the paired
0.6.0.1 / 1.1.0.1 fork builds because their control permission is
signature-protected.

SleepManager 0.6 focuses on **smarter syncing, better battery information and improved reliability**.

The biggest addition is new sync automation with BasicSync 3.19+, together with faster Syncthing-Fork wake behavior, more precise sleep battery stats and better diagnostics.

**No root, Shizuku or ADB is required for normal use.**

## What's new

### Smarter sync with BasicSync

With **BasicSync 3.19+**, SleepManager now supports two new options:

- **Periodic sync while sleeping** — let BasicSync sync occasionally during long sleep sessions, then return the device to its previous sleep state.
- **Sync then stop on sleep & wake** — sync before sleep and after wake, then stop BasicSync again when the sync is finished.

SleepManager also remembers the BasicSync state it changed and restores it without overwriting newer manual changes.

BasicSync 3.18+ remains supported for the normal state-aware sleep/wake integration.

### Faster Syncthing-Fork wake

Syncthing-Fork still uses the familiar STOP/FOLLOW sleep/wake integration.

In 0.6, FOLLOW is sent as soon as SleepManager has restored the managed connection instead of waiting for Android's internet-validation delay. This makes Syncthing-Fork resume much faster on affected devices.

### Better battery statistics

SleepManager can now use more precise battery data when Android provides it.

This improves:

- **Last sleep**
- drain per hour
- 7-day averages
- best / worst drain
- standby estimates

Short sessions still appear in Last sleep, while long-term averages continue to use eligible non-charging sessions of at least **3 hours**.

### Better diagnostics

On Android 11+, copied diagnostics can now include the reason Android stopped the previous SleepManager process when that information is available.

This can help identify problems such as low-memory kills, crashes or system/user stops.

### AYN Thor improvements

The new sync features work with Thor closed-lid protection without treating false wakes as real wakes or interrupting an active sync.

Existing Thor dock controls remain available:

- **Sleep when external display disconnects**
- **Power button sleeps with lid closed**

### Helper 1.1

The optional Helper has been updated to support the temporary Wi-Fi changes needed by the new sleep-sync features.

## Compatibility

- Android **9 / API 28 or newer**
- BasicSync **3.18+** for normal state-aware sleep/wake control
- BasicSync **3.19+** for the new advanced sync modes
- Syncthing-Fork STOP/FOLLOW sleep/wake control remains supported
- Helper **1.1.0.1**

## Installation

### 1. Install SleepManager

Download and install:

**SleepManager-0.6.0.1.apk**

### 2. Install the Helper if you use Wi-Fi / Bluetooth control

Inside SleepManager, open:

**About → Updates → Install Helper**

or install:

**SleepManager-Helper-1.1.0.1.apk**

### 3. Configure SleepManager

Choose the actions and optional rules you want, enable SleepManager, then tap **Finish setup**.

For **Syncthing-Fork**, enable:

**Settings → Behaviour → Service Control by Broadcast**

For **BasicSync**, enable:

**Allow remote control**

For **AYN Thor closed-lid protection**, enable the option in SleepManager and approve the Device Admin permission when prompted.

## Updating

SleepManager 0.5.1 and newer can check for stable updates from:

**About → Updates**

Official updates preserve your existing settings.
