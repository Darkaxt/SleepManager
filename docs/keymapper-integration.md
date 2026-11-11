# Key Mapper integration

SleepManager supports optional **Key Mapper 4.7.0+** mapping control. This integration is off by default.

## Setup
1. Install Key Mapper **4.7.0 or newer** and enable its accessibility service.
2. Open Key Mapper at least once so it reports whether mappings are active or paused.
3. Enable **Key Mapper** under SleepManager's **App Integrations**.

## Sleep and wake
- When mappings are active at sleep, SleepManager sends a pause request.
- On wake, SleepManager requests a resume only if it previously requested that pause.
- Mappings that were already paused remain paused after wake.
- When the mapping state is unknown, SleepManager leaves it unchanged instead of sending a blind pause request.
- These controls never stop Key Mapper's process, disable its accessibility service or remove mappings.

## State protocol
Key Mapper announces its current mapping state when it changes, its process starts or the accessibility service starts:

- Action: `io.github.sds100.keymapper.ACTION_MAPPINGS_STATE_CHANGED`
- Boolean extra: `io.github.sds100.keymapper.EXTRA_MAPPINGS_PAUSED`

SleepManager sends explicit `ACTION_PAUSE_MAPPINGS` and `ACTION_RESUME_MAPPINGS` requests to Key Mapper's public `PauseMappingsBroadcastReceiver`.

Mapping-state observations are valid only for the current Android boot. SleepManager records when it owns a pause request and preserves that restore ownership through service recovery. An outgoing request does not itself confirm successful delivery.

## Troubleshooting
If the integration displays **State unknown**, open Key Mapper again to trigger a fresh mapping-state announcement. Verify the accessibility service is enabled and Key Mapper is version 4.7.0 or newer.

Use SleepManager's **Activity log** to inspect pause and restore requests. Confirm actual state changes using Key Mapper's own **Running / Paused** status.

Protocol reference: https://github.com/keymapperorg/KeyMapper/issues/2279
