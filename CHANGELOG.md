# Changelog

Release notes are organized by version and focus on user-visible behavior first.

## Unreleased

### Improved

- Added state-aware TailDNS support alongside official Tailscale. SleepManager
  now disconnects the active supported client, records that exact package, and
  reconnects only the client it changed.

## 0.6.0 — 2026-09-25

### New

- Added **Periodic sync while sleeping** for completion-aware sync providers.
- Added **Sync then stop on sleep & wake**.
- Added BasicSync 3.19 completion tracking using its official folder/device counters.
- Added Helper 1.1 temporary Wi-Fi control for sleep-maintenance syncs.
- Added Android 11+ process-exit information to copyable diagnostics.

### Improved

- BasicSync completion is accepted only after a stable completed state.
- BasicSync's previous Auto / Manual ownership is preserved and restored only while SleepManager still owns the change.
- Managed sync clients are stopped before SleepManager removes managed Wi-Fi.
- Syncthing-Fork FOLLOW is now sent immediately after managed radio restoration instead of waiting up to 15 seconds for Android internet validation.
- Advanced battery, charging, Battery Saver and schedule conditions also apply to maintenance syncs.
- AYN Thor closed-lid false wakes no longer interrupt active pre-sleep or periodic sync maintenance.
- Advanced sync and BasicSync runtime/completion status are clearer in the UI.

### Battery and diagnostics

- Sleep sessions can use charge-counter data for more precise battery-change measurements.
- **Last sleep** shows two decimal places when a precise measured value is available.
- 7-day drain, Best/Worst drain and standby estimates prefer measured precision when available.
- Recent historical sessions with measured mAh can be re-evaluated using the current capacity estimate without clearing history.
- Ambiguous legacy 0%-change sessions no longer create false best-drain records.
- Diagnostics can now report recent Android process exits, including system reason, process importance and sampled memory information when Android provides it.

### Reliability and battery use

- Periodic maintenance uses a one-shot 24-hour alarm rather than permanent background polling.
- Network readiness uses Android callbacks.
- Synchronization polling runs only during an active maintenance session.
- Partial wake locks are bounded to active transitions / maintenance.

### Compatibility

- Main app: **0.6.0 / versionCode 531**.
- Helper: **1.1.0 / versionCode 1100**.
- BasicSync 3.19+ supports completion-aware maintenance.
- BasicSync 3.18+ remains supported for state-aware normal sleep/wake control.
- Syncthing-Fork STOP/FOLLOW sleep/wake control remains supported; completion-aware maintenance remains unavailable until a supported completion API exists.
- **No root, Shizuku or ADB is required for normal use.**

### Validation

0.6.0 was validated on emulator and real AYN Thor hardware, including BasicSync maintenance, ownership restoration, real periodic transfer behavior, Syncthing-Fork sleep/wake restoration, Thor closed-lid behavior, foreground-service recovery and short-session precise battery/deep-sleep measurement.

---

## 0.5.5 — 2026-09-23

### Highlights

- Added a fresh **Main + Helper update check whenever SleepManager enters the foreground**, in addition to the existing daily background job.
- Foreground checks are deduplicated, wait briefly for validated connectivity without polling or wake locks, and do not repeat on simple Activity recreation such as rotation/resizing.
- Failed background checks no longer suppress the next foreground check for 24 hours.
- Existing short in-app state refreshes remain unchanged for a snappy, live UI.
- Includes all updater, Helper-versioning, BasicSync compatibility, setup, UI and battery-statistics improvements from **0.5.4**.

### Notes

- Main app version code is **530**.
- Helper remains **1.0.0 / versionCode 1000**.
- Package IDs and the permanent signing certificate remain unchanged.
- Existing settings are preserved when updating an official signed build.


---

## 0.5.4 — 2026-09-23

### Highlights

- Added a complete **independent Helper update flow**: SleepManager can detect, download, verify, install and update the optional Helper separately from the main app.
- The Helper now has its own release version and versionCode. It is bumped only when the Helper itself changes; SleepManager 0.5.4 ships the unchanged **Helper 1.0.0 / versionCode 1000**.
- Main and Helper updates are combined into one Home card/notification when both are available, while Helper-only and Main-only updates remain independent.
- Fresh Helper installation is available directly from **About → Updates**. An absent Helper is offered as an install, not incorrectly reported as an outdated update.
- Helper APK verification checks the trusted GitHub release URL, SHA-256, package identity, version metadata and permanent signing certificate before Android's installer opens.
- BasicSync 3.18+ state-API compatibility now uses Android's **versionCode** instead of parsing the display version string, making detection reliable across version-name formats.
- Added BasicSync detection/version information to **Quick setup**.
- Added a Syncthing-Fork activation reminder for **Settings → Behaviour → Service control by broadcast**.
- Quick setup now explicitly tells new users to enable SleepManager and then tap **Finish setup**.
- Active option/integration icons now follow the active switch/theme primary color, including Material You themes.
- Battery-statistics UI now consistently uses the same **3-hour minimum** as the statistics engine for long-term averages and standby estimates.
- Fixed stale updater status text after returning from Android's package installer.

### Notes

- Main app version code is **529**.
- Helper remains **1.0.0 / versionCode 1000** because the Helper code did not change in this release.
- Package IDs and the permanent signing certificate remain unchanged.
- Existing settings are preserved when updating an official signed build.
- No root, Shizuku or ADB is required for normal use.


---

## 0.5.3 — 2026-09-22

### Highlights

- Added **BasicSync** integration using its official Android remote-control API.
- With **BasicSync 3.18+**, SleepManager observes the live mode/run state before sleep, stops BasicSync only when it is actually active, and restores the exact previous mode on wake.
- Added live BasicSync status in the Integrations UI, including Auto/Manual mode and Running/Stopped/Paused/Starting/Stopping states.
- Older BasicSync versions remain supported with the legacy **STOP during sleep → AUTO mode on wake** policy.
- Improved battery reporting with a precise current percentage when charge-counter/full-charge data is available and better capacity estimation using learned full-charge capacity before design/fallback values.
- Added an optional **Use system colors** setting for Material You dynamic colors on Android 12+, while keeping SleepManager's fixed palette as the default.
- Refined the fixed light/dark palettes and unified active integration status colors so Running, Starting and Connected states use the same highlighted treatment.
- Updated Syncthing/BasicSync integration icons and related UI polish.

### Notes

- Stable version code is **528**.
- BasicSync state-aware control requires **BasicSync 3.18+** and **Allow remote control** enabled in BasicSync.
- Package IDs and the permanent signing certificate remain unchanged.
- Existing settings are preserved when updating an official signed build.
- No root, Shizuku or ADB is required for normal use.


---

## 0.5.2 — 2026-09-21

### Highlights

- Added dock-aware AYN Thor closed-lid protection so an active external display is treated as intentional docked use instead of a false wake.
- Added **Sleep when external display disconnects** for an optional normal sleep cycle when a docked display is unplugged while the lid remains closed.
- Added **Power button sleeps with lid closed** for an optional normal sleep cycle while the Thor is awake with the lid closed.
- Improved external-display handling so a connected display entering OFF state during sleep is not mistaken for a physical disconnect.
- Battery statistics now use only eligible sleep sessions of **3 hours or longer** for long-term drain and standby estimates; shorter sleeps remain visible in Last sleep/history.
- Improved Wi-Fi diagnostics to distinguish an unchanged state from a failed toggle attempt and report Airplane-mode state when relevant.
- Copyable diagnostics now include the attempted Wi-Fi action, result and Airplane-mode state.

### Notes

- Stable version code is **525**.
- Package IDs and the permanent signing certificate remain unchanged.
- Existing settings are preserved when updating an official signed build.
- No root, Shizuku or ADB is required for normal use.


---

## 0.5.1 — 2026-09-21

### Highlights

- Added a built-in secure updater with automatic/manual checks, update notifications and direct APK download.
- Direct updates verify SHA-256, package name, version metadata and the permanent SleepManager signing certificate before opening Android's official installer.
- Added stable-release `update.json` metadata while keeping a GitHub-release fallback when direct install is unavailable.
- Fixed app-initiated external navigation so permission screens, App info and GitHub links no longer remove the SleepManager task.
- Restored hidden-from-Recents behavior after confirming on the AYN Thor that swiping the task can terminate the foreground service.
- Added a dedicated monochrome SleepManager status-bar/notification icon.
- Expanded the battery gauge to cycle through capacity, standby estimate, sleep drain rate and deep-sleep percentage.
- Includes all major 0.5.0 additions: JamesDSP sleep/wake control, sleep battery statistics, stronger Thor recovery, more durable sleep/wake transactions, improved Syncthing-Fork verification and responsive UI refinements.

### Notes

- Stable version code is **522**.
- Package IDs and the permanent signing certificate remain unchanged.
- Existing settings are preserved when updating an official signed build.
- No root, Shizuku or ADB is required for normal use.


---

## 0.5.0 — 2026-09-20

### Highlights

- Added sleep battery statistics with last-session drain, duration, drain rate, 7-day averages and measured mAh when available.
- Added JamesDSP sleep/wake control for O2P JamesDSP Manager and RootlessJamesDSP.
- JamesDSP management is explicitly **OFF during sleep / ON while awake** because JamesDSP does not expose a reliable public power-state query.
- Strengthened AYN Thor Hall-state startup and closed-lid recovery.
- Hardened service startup, Helper idempotency and persistent restore transactions.
- Added pending-restore recovery and clearer diagnostics.
- Improved Syncthing-Fork STOP verification when its running state can be confirmed.
- Added responsive UI regression coverage and multiple handheld/phone layout refinements.

### Notes

- Stable version code is **501**, allowing direct updates from the 0.5.0 beta.
- Package IDs and the permanent signing certificate remain unchanged.
- No root, Shizuku or ADB is required on the device.


---

## 0.5.0-beta1 — 2026-09-19 — Preview

### Highlights

- New Home battery dashboard with current level, last-sleep drain, duration, drain rate and 7-day average.
- Sleep battery sessions survive AYN Thor closed-lid false wakes and exclude charging sessions from averages.
- Current Thor Hall-switch state is queried when closed-lid protection starts, with persisted fallback state for service recovery.
- Service startup ordering is hardened so screen state is applied only after the start request has been validated.
- Main ↔ Helper sleep/restore protocol is now idempotent and critical Helper state is persisted synchronously.
- Pending restore failures can be surfaced to the user and manually forgotten when recovery is impossible.
- Syncthing STOP is verified when its pre-sleep running state can be confirmed.
- Visible status polling is reduced and Helper setup has a direct download shortcut.
- Initial automated regression tests cover closed-lid false wakes during Grace period.

### Notes

- This is a beta build intended for real-device validation before stable 0.5.x.
- Package IDs and the permanent release signing identity are unchanged, so updates preserve existing app data.


---

## 0.4.1 — 2026-09-19

### UI polish

- Compact App integrations layout while keeping the same functionality.
- Integration icons match the Wi-Fi/Bluetooth action icon sizing.
- Restored the outlined Open action style.
- AYN Thor closed-lid protection is shown above Grace period.
- Settings cards now consistently use the full available width.
- Minor wording cleanup around Grace period and About.

The 0.4.0 release notes otherwise remain unchanged.

---

## 0.4.0 — 2026-09-19

### Highlights

- New modern UI with permanent side navigation, clearer states, Activity log and expanded About page.
- Advanced sleep rules: grace period, custom delay, battery, charging, Battery Saver and schedule conditions.
- Tailscale integration with state-aware disconnect/reconnect.
- Improved Syncthing-Fork state detection and network-ready restore.
- Persistent sleep/wake transactions that restore only what SleepManager actually changed.
- Quick Settings tile for enabling or disabling SleepManager.
- Standard Android haptic and click feedback throughout the UI.
- AYN Thor closed-lid protection: if the Thor wakes while the lid is still closed, SleepManager puts it back to sleep instead of running the normal wake sequence.

### Notes

- Fresh installs default to **Immediate** grace period, **Custom delay OFF**, sleep actions OFF and Advanced conditions OFF.
- Advanced conditions use **AND logic**.
- SleepManager does not require root, Shizuku or ADB on the device.


---

## 0.4.0-dev1 — 2026-09-18 — Preview

### Highlights

- New modern UI with permanent side navigation, clearer states, Activity log and expanded About page.
- Advanced sleep rules: grace period, custom delay, battery, charging, Battery Saver and schedule conditions.
- Tailscale integration with state-aware disconnect/reconnect.
- Syncthing-Fork current state detection and network-ready restore.
- Persistent sleep/wake transactions that restore only what SleepManager actually changed.
- Quick Settings tile.
- Standard Android haptic and click feedback throughout the UI.
- AYN Thor closed-lid protection remains active: if the Thor wakes while the lid is still closed, SleepManager puts it back to sleep instead of running the normal wake sequence.

### Notes

- Fresh installs default to **Immediate** grace period, **Custom delay OFF**, sleep actions OFF and Advanced conditions OFF.
- Advanced conditions use **AND logic**.
- This is a development preview. Final regression testing on emulator and AYN Thor is still in progress before stable 0.4.0.


---

## 0.3.2 — 2026-09-18

### Highlights

- More reliable Syncthing shutdown before managed radios are disabled.
- AYN Thor closed-lid false wakes remain blocked without restoring connectivity.
- Wi-Fi and Bluetooth restore only the state SleepManager actually changed.
- Complete Android launcher icon support, including Android 13+ themed icons and legacy fallbacks.
- Stable build identity and signing guidance for seamless APK updates.
- README and release notes reorganized for clarity.

### Fixed

#### Syncthing sleep sequence

SleepManager now sends Syncthing `STOP` before disabling managed radios.

When Syncthing `STOP` is sent and Wi-Fi or Bluetooth also needs to be disabled:

1. `STOP` is sent immediately.
2. SleepManager waits **1 second**.
3. The Helper applies the Wi-Fi/Bluetooth sleep action.

A temporary one-shot partial wake lock keeps the CPU alive during this short transition and has a safety timeout. It is released as soon as the Helper completes the sleep action, when the transition is cancelled by a real wake, or when the service stops.

This prevents the network from being removed so quickly that Syncthing cannot complete its remote disconnect cleanly.

#### AYN Thor false wakes

If Android reports `SCREEN_ON` while the Thor Hall sensor still reports `SW_LID = CLOSED`:

- Wi-Fi/Bluetooth are not restored.
- Syncthing `FOLLOW` is not scheduled or sent.
- SleepManager calls `lockNow()`.
- The resulting duplicate `SCREEN_OFF` does not run the sleep actions a second time.

Repeated closed-lid false wakes remain protected.

### Connectivity behavior

Wi-Fi and Bluetooth remain state-aware.

SleepManager records whether each radio was already on and whether SleepManager actually changed it. On wake, it restores only the radios it changed.

Validated cases include:

- Wi-Fi ON / Bluetooth ON -> both are restored.
- Wi-Fi ON / Bluetooth OFF -> only Wi-Fi is restored.
- Wi-Fi OFF / Bluetooth ON -> only Bluetooth is restored.
- Radios that were already off are never treated as app-managed changes.

### Syncthing wake behavior

On a normal wake, managed connectivity is restored first and Syncthing `FOLLOW` is currently scheduled **2.5 seconds** later.

### Launcher icon

The 0.3.2 icon setup includes:

- adaptive icon foreground/background
- Android 13+ monochrome/themed icon
- round icon support
- legacy `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi` launcher fallbacks
- separate store icon source

### Build and update consistency

The project keeps stable Android application IDs:

- `com.med.sleepmanager`
- `com.med.sleepmanager.helper`

Version metadata is centralized so the main app and Helper cannot drift accidentally.

The build documentation now makes the Android update requirements explicit:

- same application ID
- same signing certificate
- compatible/increasing `versionCode`

The main app and Helper must use the same signer because the Helper control permission is signature-protected.

### Documentation

- README reorganized into features, installation, setup, architecture, update identity, troubleshooting, and roadmap.
- Syncthing requirements are easier to find.
- AYN Thor behavior is documented separately.
- Build signing and update compatibility are documented explicitly.

### Known limitation

Syncthing wake currently uses a fixed **2.5-second** delay before `FOLLOW`.

A future release may replace this with a network-ready check so `FOLLOW` is sent when connectivity is actually usable rather than after a fixed timer.

---

## 0.3.1

### Highlights

- Event-driven foreground sleep/wake service.
- Wi-Fi and Bluetooth sleep/wake management through the compatibility Helper.
- Syncthing-Fork `STOP` / `FOLLOW` integration.
- AYN Thor Hall-sensor closed-lid protection.
- Duplicate sleep-event protection.
- State-aware radio restoration.
- Device Admin used only for Thor `lockNow()` protection.

### Notes

0.3.1 is the baseline from which the 0.3.2 reliability and polish work was developed.
