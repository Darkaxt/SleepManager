# SleepManager 0.8.0.12 / Helper 1.1.7.2

SleepManager 0.8.0 adds **state-aware Key Mapper integration** and makes the **Home screen smoother**, especially on high-refresh-rate Android handhelds.

> **Note**
>
> **Recent compatible AYN and Retroid devices:** Wi-Fi and Bluetooth can be controlled directly, without the Compatibility Helper.
>
> **Other devices:** the optional **SleepManager Helper 1.1.7.2** remains available for Wi-Fi/Bluetooth control. If prompted, exempt the Helper from battery optimization and allow Wi-Fi control access in Android settings.

> [!WARNING]
> **AYANEO / KONKR devices with DuraSpeed:** disable **DuraSpeed** in Android Settings. Battery usage set to Unrestricted does not override DuraSpeed's background restrictions.

## Audit follow-ups (0.8.0.12)

- **Stricter BasicSync and Key Mapper state checks.** Malformed state messages are ignored, and on Android 14+ a message Android attributes to a different app is rejected. Nothing is accepted while the integration's app is not installed.
- **Known limit:** BasicSync and Key Mapper do not sign their state messages, so an installed app could still imitate them. That app could already control BasicSync and Key Mapper directly; it can only skew SleepManager's own sleep and sync decisions.
- **One shared restore list.** Wake, disable, "Forget" and the restore-completion check now use a single registry of everything SleepManager restores, so a restore can no longer be missed by one of those paths. Disable now restores in the same order as wake.
- Syncthing is unchanged: stopping is already verified, and on wake Syncthing-Fork's own run conditions decide whether it runs, so there is no reliable success signal to check.

## Reliability and safety fixes (0.8.0.11)

This fork release fixes problems found in a design audit of the sleep/wake engine.

- **Restores keep retrying after you disable SleepManager.** If something it changed (radios, Battery Saver, charging separation, BasicSync) could not be put back at the moment you disabled it, SleepManager now retries in the background with a growing delay (1 minute up to 1 hour, for about a day, restarted on reboot). It never applies sleep actions while disabled.
- **Tailscale and TailDNS/TailD installed together now work.** Android hides which app owns a VPN, so the previous detection never identified the active client when both were installed and did nothing. SleepManager now disconnects the installed clients in order and reconnects only the one whose disconnect actually brought the tunnel down.
- **The Tailscale integration shows the installed app's real name** (for example TailD) instead of always saying "TailDNS".
- **"Forget" on a pending restore now clears every related record**, so no stale Wi-Fi/Bluetooth or RAOfflineProxy restore is left behind.
- **Separate wake locks per sleep step**, so one step finishing can no longer cut short another step's wake lock while the device is going to sleep.
- **Helper 1.1.7.2 ignores legacy commands unless SleepManager itself owns the legacy control permission** (checked by signing certificate), so another app cannot claim that permission and control Wi-Fi/Bluetooth if the Helper is installed without SleepManager. Update the Helper together with the main app.

Known limits, not changed in this release: JamesDSP commands are not confirmed after sending; the onCreate/onDestroy recovery paths are not yet routed through the shared restore list.

## Existing upstream0.8.0 features

### Key Mapper 4.7+ Integration

SleepManager can now manage **Key Mapper mappings** as part of a normal sleep/wake cycle.

- **Pause on sleep:** when mappings are running, SleepManager requests a pause when its sleep actions begin.
- **Resume on wake:** SleepManager requests a resume only when it was responsible for pausing those mappings.
- **Respect manual pauses:** mappings already paused before sleep are left untouched, including after wake.
- **Safe when the state is unknown:** SleepManager does not send a blind pause request before receiving Key Mapper's state.
- **Optional and off by default:** enable Key Mapper in **App Integrations**. Key Mapper's accessibility service and saved mappings are not modified.

Requires **Key Mapper 4.7.0 or newer**, which exposes the mapping-state API used by SleepManager.

### Smoother Home Screen

Home screen updates now run in the background and refresh only what has changed. This makes scrolling smoother while keeping battery, integration and Activity information up to date.

## Compatibility

- SleepManager **0.8.0.12 / versionCode 582**
- SleepManager Helper **1.1.7.2 / versionCode 1123** (unchanged)
- Android **9 / API 28 or newer**
- Key Mapper **4.7.0+** for the new integration
- Existing SleepManager settings are retained when updating an official signed build
- No root, Shizuku or ADB is required for normal use

## Installation and Updating

Install **SleepManager-0.8.0.12.apk** over your existing Darkaxt fork version, or use **About → Updates** to install the new stable release.

On a first install, open SleepManager, select the actions and integrations you want, enable SleepManager, then tap **Finish setup**. Install the optional Helper only when your device requires it.

To use Key Mapper integration, install **Key Mapper 4.7.0+** and enable its integration in SleepManager after Key Mapper reports its mapping state.

## Maintained fork compatibility

This release merges exact upstream 74d3bf5b91828adcb7fb3fab6de3e88698957ef6.
Official Tailscale and TailDNS retain package-aware active-VPN selection and exact
disconnect/restore ownership. Both APKs use the existing permanent fork certificate
and signature-protected Helper contract. Updates come only from Darkaxt/SleepManager;
fork settings/data remain compatible. No device installation or validation is claimed.
