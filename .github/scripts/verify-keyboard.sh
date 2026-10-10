#!/usr/bin/env bash
set -uo pipefail
mkdir -p keyboard-check
# Capture evidence while the emulator is alive, rather than only after a failure.
timeout --kill-after=5s 16m adb logcat -v threadtime > keyboard-check/live-device.log 2>&1 &
log_pid=$!
trap 'kill "$log_pid" 2>/dev/null || true; wait "$log_pid" 2>/dev/null || true' EXIT
# AndroidJUnitRunner's timeout_msec applies to every AndroidJUnit4 test. Keep all
# instrumentation classes; bounded host timeout also handles a disconnected ADB.
test_status=0
timeout --kill-after=30s 15m gradle connectedDebugAndroidTest --no-daemon --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.timeout_msec=120000 || test_status=$?
printf '%s\n' "$test_status" > keyboard-check/test-exit-status.txt
timeout --kill-after=2s 10s adb get-state > keyboard-check/device-state.txt 2>&1 || true
timeout --kill-after=2s 15s adb pull /data/local/tmp/keyboard-check keyboard-check || true
timeout --kill-after=2s 10s adb exec-out screencap -p > keyboard-check/final-screen.png || true
timeout --kill-after=2s 10s adb logcat -d > keyboard-check/device.log 2>&1 || true
timeout --kill-after=2s 10s sudo -n dmesg --ctime > keyboard-check/host-kernel.log 2>&1 || true
free -m > keyboard-check/host-memory.txt
ps -eo pid,ppid,rss,comm > keyboard-check/host-processes.txt
exit "$test_status"
