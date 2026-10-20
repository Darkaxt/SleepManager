# SleepManager read-only Status API v1

Available starting in SleepManager **0.7.3**; **not present in 0.7.2**.

This public Android `ContentProvider` lets launchers such as slot. for KONKR
display SleepManager's **configured enabled state** and **live foreground service
state**. It never changes settings, runs maintenance, or requires a control
permission.

| Item | Value |
|---|---|
| Authority | `com.med.sleepmanager.status` |
| URI | `content://com.med.sleepmanager.status` |
| Method | `status` |
| Returned Bundle key | `status` (JSON string) |
| Notifications | `ContentResolver.notifyChange` at the same URI |
| API version | `1` |

Example payload:

```json
{"apiVersion":1,"enabled":true,"serviceRunning":true}
```

- `enabled` is **the user's toggle**, not proof of service execution.
- `serviceRunning` reports the Android service's live `onCreate` /
  `onDestroy` state **in the SleepManager application process**. It is not
  inferred from a wake/sleep transaction, deep-sleep condition or radio status.
- If `enabled=true` but `serviceRunning=false`, the launcher may indicate
  that the manager is configured on but **not currently running**.
- An unavailable provider, absent app, API version mismatch or exception
  means **unknown**, not `disabled`.
- Version 1 does **not** expose phase, errors, sync details, statistics or
  remote control. Do not infer those fields.

Android 11+ consumers should add:

```xml
<queries>
    <provider android:authorities="com.med.sleepmanager.status" />
</queries>
```

Kotlin example (run off UI/GL render thread):

```kotlin
val uri = Uri.parse("content://com.med.sleepmanager.status")
val raw = contentResolver.call(uri, "status", null, null)?.getString("status")
val json = raw?.let(::JSONObject)
val supported = json?.optInt("apiVersion") == 1
val enabled: Boolean? = if (supported) json!!.getBoolean("enabled") else null
val serviceRunning: Boolean? =
    if (supported) json!!.getBoolean("serviceRunning") else null
```

While active, register a `ContentObserver` on the URI to refresh after
changes. Also query once on launch/resume and on package replacement. There is
no need to poll continuously, and calling `status` must not wake the manager
service. Observers must be unregistered when the consumer is paused or closed.

Both the enabled preference setter and the foreground service lifecycle emit
change notifications. Process crashes cannot emit `onDestroy`; after a
process death, a cold provider instance naturally reports
`serviceRunning=false`. Never interpret a momentary gap between enabled
and running as an instruction to modify SleepManager settings.
