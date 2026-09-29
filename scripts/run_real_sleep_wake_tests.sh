#!/usr/bin/env bash
set -Eeuo pipefail

SERIAL="${1:-${ADB_SERIAL:-emulator-5554}}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$SDK_ROOT/platform-tools/adb}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_DIR="$SCRIPT_DIR/real-e2e-report"

MAIN_PACKAGE="com.med.sleepmanager"
TEST_RUNNER="com.med.sleepmanager.test/androidx.test.runner.AndroidJUnitRunner"
CONTROL_TEST="com.med.sleepmanager.LocalE2EControlTest#applyConfiguration"

SYNCTHING_PACKAGES=(
  "com.github.catfriend1.syncthingfork"
  "com.github.catfriend1.syncthingfork.debug"
  "com.github.catfriend1.syncthingandroid"
  "com.github.catfriend1.syncthingandroid.debug"
)
TAILSCALE_PACKAGE="com.tailscale.ipn"
BASICSYNC_PACKAGE="com.chiller3.basicsync"
JAMES_PACKAGES=("james.dsp" "me.timschneeberger.rootlessjamesdsp")

PASS_COUNT=0
WARN_COUNT=0
BT_TESTABLE=0
SYNCTHING_FORWARD_PORT=""
SYNCTHING_PACKAGE=""
JAMES_PACKAGE=""

mkdir -p "$REPORT_DIR"

adb_target() {
  "$ADB_BIN" -s "$SERIAL" "$@"
}

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  printf '✓ %s\n' "$*"
}

warn() {
  WARN_COUNT=$((WARN_COUNT + 1))
  printf '⚠ %s\n' "$*"
}

fail() {
  printf '\n✗ %s\n' "$*" >&2
  adb_target logcat -d > "$REPORT_DIR/logcat-failure.txt" 2>/dev/null || true
  exit 1
}

package_installed() {
  adb_target shell pm path "$1" 2>/dev/null | grep -q '^package:'
}

wake_screen() {
  adb_target shell input keyevent 224 >/dev/null 2>&1 || true
  sleep 1
}

sleep_screen() {
  adb_target shell input keyevent 223 >/dev/null 2>&1 || true
  sleep 1
}

wifi_is_on() {
  [ "$(adb_target shell settings get global wifi_on 2>/dev/null | tr -d '\r')" = "1" ]
}

bluetooth_is_on() {
  [ "$(adb_target shell settings get global bluetooth_on 2>/dev/null | tr -d '\r')" = "1" ]
}

ensure_wifi_on() {
  if wifi_is_on; then
    return 0
  fi

  adb_target shell svc wifi enable >/dev/null 2>&1 || true
  for _ in $(seq 1 12); do
    wifi_is_on && return 0
    sleep 1
  done
  return 1
}

ensure_bluetooth_on() {
  if bluetooth_is_on; then
    BT_TESTABLE=1
    return 0
  fi

  adb_target shell cmd bluetooth_manager enable >/dev/null 2>&1 || true
  adb_target shell svc bluetooth enable >/dev/null 2>&1 || true

  for _ in $(seq 1 12); do
    if bluetooth_is_on; then
      BT_TESTABLE=1
      return 0
    fi
    sleep 1
  done

  BT_TESTABLE=0
  return 1
}

cycle_active() {
  adb_target shell run-as "$MAIN_PACKAGE"     cat shared_prefs/sleep_cycle_state.xml 2>/dev/null     | grep -q 'boolean name="active" value="true"'
}

wait_cycle_active() {
  local timeout="${1:-12}"
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    cycle_active && return 0
    sleep 1
  done
  return 1
}

wait_cycle_inactive() {
  local timeout="${1:-20}"
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    if ! cycle_active; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_wifi_state() {
  local wanted="$1"
  local timeout="${2:-12}"
  local deadline=$((SECONDS + timeout))

  while (( SECONDS < deadline )); do
    if [ "$wanted" = "on" ] && wifi_is_on; then
      return 0
    fi
    if [ "$wanted" = "off" ] && ! wifi_is_on; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_bluetooth_state() {
  local wanted="$1"
  local timeout="${2:-12}"
  local deadline=$((SECONDS + timeout))

  while (( SECONDS < deadline )); do
    if [ "$wanted" = "on" ] && bluetooth_is_on; then
      return 0
    fi
    if [ "$wanted" = "off" ] && ! bluetooth_is_on; then
      return 0
    fi
    sleep 1
  done
  return 1
}

log_has() {
  adb_target logcat -d 2>/dev/null | grep -E "$1" >/dev/null
}

wait_log() {
  local pattern="$1"
  local timeout="${2:-15}"
  local deadline=$((SECONDS + timeout))

  while (( SECONDS < deadline )); do
    log_has "$pattern" && return 0
    sleep 1
  done
  return 1
}

run_config() {
  local mode="$1"
  local output

  set +e
  output="$(adb_target shell am instrument -w -r     -e class "$CONTROL_TEST"     -e mode "$mode"     "$TEST_RUNNER" 2>&1)"
  local status=$?
  set -e

  printf '%s\n' "$output" > "$REPORT_DIR/config-$mode.txt"

  [ "$status" -eq 0 ] || fail "Configuration '$mode' instrumentation failed"
  printf '%s\n' "$output" | grep -Eq '^OK \([0-9]+ tests?\)'     || fail "Configuration '$mode' did not report OK"
}

start_manager() {
  local output
  output="$(adb_target shell am start -W -n "$MAIN_PACKAGE/.MainActivity" 2>&1)"
  printf '%s\n' "$output" | grep -q 'Status: ok'     || fail "Unable to launch SleepManager"
  sleep 2
  adb_target shell input keyevent 3 >/dev/null 2>&1 || true
  sleep 1
}

prepare_scenario() {
  local mode="$1"
  wake_screen
  ensure_wifi_on || fail "Unable to enable Wi-Fi before '$mode' scenario"
  adb_target logcat -c
  run_config "$mode"
  start_manager
}

restore_after_allowed_cycle() {
  wake_screen
  wait_cycle_inactive 25 || fail "Sleep transaction did not finish restoring on wake"
  wait_wifi_state on 15 || fail "Wi-Fi was not restored after wake"
}

assert_blocked_sleep() {
  local label="$1"
  sleep_screen

  wait_log 'Sleep actions skipped ->' 8     || fail "$label: no Advanced-condition skip was observed"

  if cycle_active; then
    fail "$label: a sleep transaction started even though the condition should block it"
  fi

  wifi_is_on || fail "$label: Wi-Fi changed even though sleep actions were blocked"
  pass "$label blocks sleep actions"
  wake_screen
  sleep 1
}

battery_unplug_level() {
  local level="$1"
  adb_target shell dumpsys battery unplug >/dev/null 2>&1 || true
  adb_target shell dumpsys battery set ac 0 >/dev/null 2>&1 || true
  adb_target shell dumpsys battery set usb 0 >/dev/null 2>&1 || true
  adb_target shell dumpsys battery set wireless 0 >/dev/null 2>&1 || true
  adb_target shell dumpsys battery set status 3 >/dev/null 2>&1 || true
  adb_target shell dumpsys battery set level "$level" >/dev/null 2>&1 || true
  sleep 1
}

battery_charging() {
  local level="$1"
  adb_target shell dumpsys battery set level "$level" >/dev/null 2>&1 || true
  adb_target shell dumpsys battery set ac 1 >/dev/null 2>&1 || true
  adb_target shell dumpsys battery set status 2 >/dev/null 2>&1 || true
  sleep 1
}

battery_reports_ac_powered() {
  adb_target shell dumpsys battery 2>/dev/null     | grep -q 'AC powered: true'
}

power_saver_is_on() {
  [ "$(adb_target shell settings get global low_power 2>/dev/null | tr -d '\r')" = "1" ]
}

tailscale_connected() {
  adb_target shell ip addr show 2>/dev/null     | grep -Eiq '100\.(6[4-9]|[78][0-9]|9[0-9]|1[01][0-9]|12[0-7])\.|fd7a:115c:a1e0'
}

latest_basicsync_state() {
  adb_target logcat -d 2>/dev/null     | grep 'STATE_CHANGED observed: mode='     | tail -n 1     | sed -E 's/.*mode=([^ ]+) runState=([^ ]+).*/\1 \2/'
}

wait_basicsync_stopped() {
  wait_log 'STATE_CHANGED observed: mode=MANUAL_MODE_STOPPED runState=(NOT_RUNNING|PAUSED|STOPPING)' 15
}

wait_basicsync_mode() {
  local mode="$1"
  local timeout="${2:-20}"
  wait_log "STATE_CHANGED observed: mode=$mode runState=" "$timeout"
}

setup_syncthing_forward() {
  [ -n "$SYNCTHING_PACKAGE" ] || return 1
  command -v curl >/dev/null 2>&1 || return 1

  SYNCTHING_FORWARD_PORT="$(adb_target forward tcp:0 tcp:8384 2>/dev/null | tr -d '\r')"
  [ -n "$SYNCTHING_FORWARD_PORT" ]
}

syncthing_running() {
  [ -n "$SYNCTHING_FORWARD_PORT" ] || return 1

  curl -fsS --max-time 1     "http://127.0.0.1:$SYNCTHING_FORWARD_PORT/rest/noauth/health"     >/dev/null 2>&1     || curl -kfsS --max-time 1       "https://127.0.0.1:$SYNCTHING_FORWARD_PORT/rest/noauth/health"       >/dev/null 2>&1
}

wait_syncthing_state() {
  local wanted="$1"
  local timeout="${2:-15}"
  local deadline=$((SECONDS + timeout))

  while (( SECONDS < deadline )); do
    if [ "$wanted" = "running" ] && syncthing_running; then
      return 0
    fi
    if [ "$wanted" = "stopped" ] && ! syncthing_running; then
      return 0
    fi
    sleep 1
  done
  return 1
}

fire_custom_delay_now() {
  adb_target shell run-as "$MAIN_PACKAGE" /system/bin/am broadcast     -a com.med.sleepmanager.action.SLEEP_DELAY_ELAPSED     -n "$MAIN_PACKAGE/.service.SleepDelayReceiver"     >/dev/null 2>&1
}

fire_periodic_sync_now() {
  adb_target shell run-as "$MAIN_PACKAGE" /system/bin/am start-foreground-service     -a com.med.sleepmanager.action.PERIODIC_SYNC     -n "$MAIN_PACKAGE/.service.SleepManagerService"     >/dev/null 2>&1
}

ORIGINAL_WIFI="$(adb_target shell settings get global wifi_on 2>/dev/null | tr -d '\r')"
ORIGINAL_BT="$(adb_target shell settings get global bluetooth_on 2>/dev/null | tr -d '\r')"
ORIGINAL_LOW_POWER="$(adb_target shell settings get global low_power 2>/dev/null | tr -d '\r')"

cleanup() {
  set +e

  wake_screen
  sleep 3

  adb_target shell dumpsys battery reset >/dev/null 2>&1 || true

  if [ "$ORIGINAL_LOW_POWER" = "1" ]; then
    adb_target shell cmd power set-mode 1 >/dev/null 2>&1 || true
  elif [ "$ORIGINAL_LOW_POWER" = "0" ]; then
    adb_target shell cmd power set-mode 0 >/dev/null 2>&1 || true
  fi

  if [ "$ORIGINAL_WIFI" = "1" ]; then
    adb_target shell svc wifi enable >/dev/null 2>&1 || true
  elif [ "$ORIGINAL_WIFI" = "0" ]; then
    adb_target shell svc wifi disable >/dev/null 2>&1 || true
  fi

  if [ "$ORIGINAL_BT" = "1" ]; then
    adb_target shell cmd bluetooth_manager enable >/dev/null 2>&1 || true
  elif [ "$ORIGINAL_BT" = "0" ]; then
    adb_target shell cmd bluetooth_manager disable >/dev/null 2>&1 || true
  fi

  if [ -n "$SYNCTHING_FORWARD_PORT" ]; then
    adb_target forward --remove "tcp:$SYNCTHING_FORWARD_PORT" >/dev/null 2>&1 || true
  fi

  adb_target logcat -d > "$REPORT_DIR/final-logcat.txt" 2>/dev/null || true
}
trap cleanup EXIT

if [[ "$SERIAL" != emulator-* ]]; then
  fail "Real end-to-end suite is intentionally limited to an Android emulator"
fi

for packageName in "${SYNCTHING_PACKAGES[@]}"; do
  if package_installed "$packageName"; then
    SYNCTHING_PACKAGE="$packageName"
    break
  fi
done

for packageName in "${JAMES_PACKAGES[@]}"; do
  if package_installed "$packageName"; then
    JAMES_PACKAGE="$packageName"
    break
  fi
done

printf '\n========================================\n'
printf 'SleepManager REAL sleep/wake E2E suite\n'
printf 'Target: %s\n' "$SERIAL"
printf '========================================\n\n'

printf '%s\n' '--- Real Home sleep/wake cycle ---'
wake_screen
ensure_wifi_on || fail "Wi-Fi cannot be enabled on this emulator"
if ensure_bluetooth_on; then
  pass "Bluetooth precondition available"
else
  warn "Bluetooth adapter cannot be enabled by this emulator; Bluetooth ON→OFF→ON will be skipped"
fi

prepare_scenario core

BASIC_INITIAL=""
if package_installed "$BASICSYNC_PACKAGE"; then
  for _ in $(seq 1 8); do
    BASIC_INITIAL="$(latest_basicsync_state)"
    [ -n "$BASIC_INITIAL" ] && break
    sleep 1
  done
fi
BASIC_INITIAL_MODE="${BASIC_INITIAL%% *}"

SYNCTHING_INITIAL="unavailable"
if [ -n "$SYNCTHING_PACKAGE" ] && setup_syncthing_forward; then
  if syncthing_running; then
    SYNCTHING_INITIAL="running"
  else
    SYNCTHING_INITIAL="stopped"
  fi
fi

TAILSCALE_INITIAL="unavailable"
if package_installed "$TAILSCALE_PACKAGE"; then
  if tailscale_connected; then
    TAILSCALE_INITIAL="connected"
  else
    TAILSCALE_INITIAL="disconnected"
  fi
fi

adb_target logcat -c
sleep_screen

wait_cycle_active 12 || fail "Core cycle: SleepManager did not create a sleep transaction"
pass "Core cycle starts on real SCREEN_OFF"

wait_wifi_state off 12 || fail "Core cycle: Wi-Fi did not turn OFF"
pass "Wi-Fi ON → OFF during sleep"

if [ "$BT_TESTABLE" = "1" ]; then
  wait_bluetooth_state off 12 || fail "Core cycle: Bluetooth did not turn OFF"
  pass "Bluetooth ON → OFF during sleep"
fi

if [ "$SYNCTHING_INITIAL" = "running" ]; then
  wait_syncthing_state stopped 15 || fail "Syncthing-Fork did not stop during sleep"
  pass "Syncthing-Fork Running → Stopped"
elif [ "$SYNCTHING_INITIAL" = "stopped" ]; then
  pass "Syncthing-Fork initial stopped state preserved"
else
  warn "Syncthing-Fork health probe unavailable; command path only"
fi

if package_installed "$BASICSYNC_PACKAGE"; then
  if wait_basicsync_stopped; then
    pass "BasicSync → MANUAL_MODE_STOPPED during sleep"
  else
    fail "BasicSync did not confirm stopped state during core sleep"
  fi
else
  warn "BasicSync not installed; real BasicSync transition skipped"
fi

if [ "$TAILSCALE_INITIAL" = "connected" ]; then
  local_deadline=$((SECONDS + 15))
  while (( SECONDS < local_deadline )) && tailscale_connected; do sleep 1; done
  tailscale_connected && fail "Tailscale remained connected during sleep"
  pass "Tailscale Connected → Disconnected"
elif [ "$TAILSCALE_INITIAL" = "disconnected" ]; then
  tailscale_connected && fail "Tailscale connected even though it started disconnected"
  pass "Tailscale initial disconnected state preserved"
else
  warn "Tailscale not installed; real transition skipped"
fi

if [ -n "$JAMES_PACKAGE" ]; then
  wait_log 'Sent JamesDSP power=OFF' 10     || fail "JamesDSP OFF control was not delivered"
  pass "JamesDSP real OFF command delivered"
else
  warn "JamesDSP not installed; real control skipped"
fi

wake_screen
wait_cycle_inactive 25 || fail "Core cycle: restore transaction did not complete"
wait_wifi_state on 15 || fail "Core cycle: Wi-Fi did not return ON"
pass "Wi-Fi OFF → ON on wake"

if [ "$BT_TESTABLE" = "1" ]; then
  wait_bluetooth_state on 15 || fail "Core cycle: Bluetooth did not return ON"
  pass "Bluetooth OFF → ON on wake"
fi

if [ "$SYNCTHING_INITIAL" = "running" ]; then
  wait_syncthing_state running 20 || fail "Syncthing-Fork did not return to running"
  pass "Syncthing-Fork stopped → running on wake"
fi

if package_installed "$BASICSYNC_PACKAGE" && [ -n "$BASIC_INITIAL_MODE" ]; then
  wait_basicsync_mode "$BASIC_INITIAL_MODE" 20     || fail "BasicSync did not restore initial mode $BASIC_INITIAL_MODE"
  pass "BasicSync initial mode restored ($BASIC_INITIAL_MODE)"
fi

if [ "$TAILSCALE_INITIAL" = "connected" ]; then
  local_deadline=$((SECONDS + 20))
  until tailscale_connected; do
    (( SECONDS >= local_deadline )) && fail "Tailscale did not reconnect on wake"
    sleep 1
  done
  pass "Tailscale reconnects on wake"
elif [ "$TAILSCALE_INITIAL" = "disconnected" ]; then
  tailscale_connected && fail "Tailscale did not preserve initial disconnected state on wake"
fi

if [ -n "$JAMES_PACKAGE" ]; then
  wait_log 'Sent JamesDSP power=ON' 12     || fail "JamesDSP ON control was not delivered on wake"
  pass "JamesDSP real ON command delivered on wake"
fi

printf '\n%s\n' '--- Grace period / Custom delay ---'
prepare_scenario grace5
adb_target logcat -c
sleep_screen
sleep 2
cycle_active && fail "5 s grace: actions ran before the grace period elapsed"
wifi_is_on || fail "5 s grace: Wi-Fi changed before expiry"
pass "5 s grace suppresses early sleep actions"

wake_screen
sleep 1
cycle_active && fail "5 s grace: early wake left an active transaction"
pass "Wake before 5 s cancels pending sleep actions"

adb_target logcat -c
sleep_screen
wait_cycle_active 8 || fail "5 s grace: actions did not run after expiry"
wait_wifi_state off 8 || fail "5 s grace: Wi-Fi did not turn OFF after expiry"
pass "Real 5 s grace expires and applies actions"
restore_after_allowed_cycle

prepare_scenario custom60
adb_target logcat -c
sleep_screen
sleep 2
cycle_active && fail "Custom delay: actions ran before the 60 s delay expired"
wifi_is_on || fail "Custom delay: Wi-Fi changed before expiry"
pass "60 s Custom delay is really pending"

fire_custom_delay_now || fail "Unable to simulate Custom delay alarm expiry"
wait_cycle_active 8 || fail "Custom delay: simulated alarm did not start sleep actions"
wait_wifi_state off 8 || fail "Custom delay: Wi-Fi did not turn OFF after simulated expiry"
pass "Custom delay expiry simulated instantly (no 60 s wait)"
restore_after_allowed_cycle

printf '\n%s\n' '--- Advanced sleep conditions ---'
battery_unplug_level 20
prepare_scenario battery50
adb_target logcat -c
sleep_screen
wait_cycle_active 8 || fail "Battery <50% should allow sleep actions at 20%"
pass "Battery level condition allows sleep below threshold"
restore_after_allowed_cycle

battery_unplug_level 80
prepare_scenario battery50
adb_target logcat -c
assert_blocked_sleep "Battery level >= threshold"

battery_unplug_level 50
prepare_scenario not_charging
adb_target logcat -c
sleep_screen
wait_cycle_active 8 || fail "Not charging should allow sleep while unplugged"
pass "Not charging condition allows sleep while unplugged"
restore_after_allowed_cycle

battery_charging 50
if battery_reports_ac_powered; then
  prepare_scenario not_charging
  adb_target logcat -c
  assert_blocked_sleep "Charging state"
else
  warn "Emulator did not expose simulated AC charging; charging-block test skipped"
fi

battery_unplug_level 50
adb_target shell cmd power set-mode 1 >/dev/null 2>&1 || true
sleep 1
if power_saver_is_on; then
  prepare_scenario battery_saver_on
  adb_target logcat -c
  sleep_screen
  wait_cycle_active 8 || fail "Battery Saver ON condition should allow sleep while saver is ON"
  pass "Battery Saver ON condition allows sleep"
  restore_after_allowed_cycle

  adb_target shell cmd power set-mode 0 >/dev/null 2>&1 || true
  sleep 1
  prepare_scenario battery_saver_on
  adb_target logcat -c
  assert_blocked_sleep "Battery Saver OFF"
else
  warn "Emulator cannot toggle Battery Saver; Battery Saver condition E2E skipped"
fi

battery_unplug_level 50
prepare_scenario schedule_inside
adb_target logcat -c
sleep_screen
wait_cycle_active 8 || fail "Inside schedule should allow sleep actions"
pass "Schedule allows actions inside configured window"
restore_after_allowed_cycle

prepare_scenario schedule_outside
adb_target logcat -c
assert_blocked_sleep "Outside schedule"

if adb_target shell cmd power set-mode 1 >/dev/null 2>&1 && power_saver_is_on; then
  battery_unplug_level 20
  prepare_scenario combined
  adb_target logcat -c
  sleep_screen
  wait_cycle_active 8 || fail "Combined conditions should allow sleep when all are satisfied"
  pass "Combined Advanced conditions use AND semantics (all satisfied)"
  restore_after_allowed_cycle

  battery_unplug_level 80
  prepare_scenario combined
  adb_target logcat -c
  assert_blocked_sleep "Combined conditions with one failed condition"
else
  warn "Combined condition E2E skipped because Battery Saver cannot be simulated"
fi

printf '\n%s\n' '--- Advanced sync modes with real BasicSync ---'
if package_installed "$BASICSYNC_PACKAGE"; then
  battery_unplug_level 50
  ensure_wifi_on || fail "Unable to enable Wi-Fi for sync-then-stop"
  prepare_scenario sync_then_stop
  adb_target logcat -c
  sleep_screen

  if wait_log 'Maintenance finished: trigger=BEFORE_SLEEP outcome=' 45; then
    pass "Sync then stop: real BEFORE_SLEEP maintenance completes"
    wait_wifi_state off 15       || fail "Sync then stop: Wi-Fi did not turn OFF after maintenance"

    wake_screen
    if wait_log 'Maintenance finished: trigger=AFTER_WAKE outcome=' 45; then
      pass "Sync then stop: real AFTER_WAKE maintenance completes"
    else
      fail "Sync then stop: AFTER_WAKE maintenance did not complete within 45 s"
    fi
    wait_cycle_inactive 20 || fail "Sync then stop: wake restore remained pending"
  else
    fail "Sync then stop: BEFORE_SLEEP maintenance did not complete within 45 s"
  fi

  ensure_wifi_on || fail "Unable to enable Wi-Fi for periodic sync"
  prepare_scenario periodic
  adb_target logcat -c
  sleep_screen
  wait_cycle_active 12 || fail "Periodic sync: base sleep transaction did not start"
  wait_wifi_state off 15 || fail "Periodic sync: Wi-Fi did not enter sleep state"

  fire_periodic_sync_now || fail "Unable to trigger periodic sync immediately"
  if wait_log 'Maintenance finished: trigger=PERIODIC_SLEEP outcome=' 45; then
    pass "Periodic sync: real maintenance executes without waiting 24 h"
    wait_wifi_state off 15       || fail "Periodic sync: temporary Wi-Fi was not returned OFF"
    pass "Periodic sync: temporary Wi-Fi cleaned up"
  else
    fail "Periodic sync did not finish within 45 s"
  fi
  restore_after_allowed_cycle
else
  warn "BasicSync not installed; Advanced real sync-mode tests skipped"
fi

adb_target shell dumpsys battery reset >/dev/null 2>&1 || true

printf '\n========================================\n'
printf 'REAL E2E PASS\n'
printf 'Passed checks: %d\n' "$PASS_COUNT"
printf 'Warnings/skips: %d\n' "$WARN_COUNT"
printf 'Hardware-only functions intentionally not tested here:\n'
printf '  - lid sensor / closed-lid protection\n'
printf '  - external-display dock disconnect\n'
printf '  - clamshell Power button\n'
printf '  - Charging Separation / PServerBinder\n'
printf '========================================\n'
