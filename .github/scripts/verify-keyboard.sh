#!/usr/bin/env bash
set -uo pipefail
# Emulator-runner executes each script line in a separate shell. Keep the test
# result and screenshot collection in one process so the exit code is preserved.
test_status=0
gradle connectedDebugAndroidTest --no-daemon --stacktrace || test_status=$?
mkdir -p keyboard-check
adb pull /data/local/tmp/keyboard-check keyboard-check || true
adb exec-out screencap -p > keyboard-check/final-screen.png || true
adb logcat -d > keyboard-check/device.log || true
exit "$test_status"
