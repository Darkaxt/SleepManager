#!/usr/bin/env bash
set -Eeuo pipefail

SERIAL="${1:-${ADB_SERIAL:-emulator-5554}}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGE_DIR="$ROOT_DIR/build/ci-real-e2e"

echo "=== Standard emulator regression ==="
ADB_SERIAL="$SERIAL" SKIP_STATIC=1 \
  "$ROOT_DIR/scripts/test_emulator_regression.sh" "$SERIAL"

echo "=== Real SCREEN_OFF / SCREEN_ON E2E ==="
rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"

cp "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" \
  "$STAGE_DIR/SleepManager-debug.apk"
cp "$ROOT_DIR/helper/build/outputs/apk/debug/helper-debug.apk" \
  "$STAGE_DIR/SleepManager-Helper-debug.apk"
cp "$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" \
  "$STAGE_DIR/SleepManager-tests.apk"
cp "$ROOT_DIR/scripts/run_real_sleep_wake_tests.sh" \
  "$STAGE_DIR/run-real-e2e.sh"
chmod +x "$STAGE_DIR/run-real-e2e.sh"

E2E_REINSTALL_APP=0 \
  "$STAGE_DIR/run-real-e2e.sh" "$SERIAL"

echo "=== Helper version compatibility matrix ==="
"$ROOT_DIR/scripts/test_helper_version_compatibility.sh" "$SERIAL"
