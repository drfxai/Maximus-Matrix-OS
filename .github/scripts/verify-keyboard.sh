#!/usr/bin/env bash
set -uo pipefail
mkdir -p keyboard-check
# Capture evidence while the emulator is alive, rather than only after a failure.
timeout --kill-after=5s 16m adb logcat -v threadtime > keyboard-check/live-device.log 2>&1 &
log_pid=$!
trap 'kill "$log_pid" 2>/dev/null || true; wait "$log_pid" 2>/dev/null || true' EXIT
# Keep the original transport outcome. A lost emulator must fail the gate,
# rather than being retried until an application assertion happens to pass.
initial_status=0
timeout --kill-after=2s 10s adb get-state > keyboard-check/initial-device-state.txt 2>&1 || initial_status=$?
"${ANDROID_HOME}/emulator/emulator" -version > keyboard-check/emulator-version.txt 2>&1 || true
# The CI matrix runs the emulator-compatible instrumentation classes, isolating memory/IME
# interactions by running each selected suite on a fresh emulator.
# The full keyboard reopen cycle remains mandatory; no test is excluded.
# PhoneNativeApkBuildAcceptanceTest is a separate physical ARM64 gate and must
# never be represented as passing merely because these x86 suites pass.
test_status=0
test_args=(-Pandroid.testInstrumentationRunnerArguments.timeout_msec=120000)
if [[ -n "${ANDROID_TEST_CLASSES:-}" ]]; then
  test_args+=("-Pandroid.testInstrumentationRunnerArguments.class=${ANDROID_TEST_CLASSES}")
fi
# Remove both copies of every test-owned capture so fallback cannot publish stale evidence.
if [[ -z "${ANDROID_TEST_CLASSES:-}" || "${ANDROID_TEST_CLASSES:-}" == *"KeyboardLayoutTest"* ]]; then
  timeout --kill-after=2s 10s adb shell rm -f \
    /data/local/tmp/maximus-keyboard-home-graph.png \
    /data/local/tmp/maximus-keyboard-chat-focus.png \
    /data/local/tmp/maximus-keyboard-studio-overview.png \
    /data/local/tmp/maximus-keyboard-pine-studio.png \
    /data/local/tmp/maximus-keyboard-agents-my-agents.png \
    /data/local/tmp/maximus-keyboard-smart-combo.png \
    /data/local/tmp/maximus-keyboard-tools-hub.png \
    /sdcard/Android/data/ai.drfx.maximus.matrixai/files/home-graph.png \
    /sdcard/Android/data/ai.drfx.maximus.matrixai/files/chat-focus.png \
    /sdcard/Android/data/ai.drfx.maximus.matrixai/files/studio-overview.png \
    /sdcard/Android/data/ai.drfx.maximus.matrixai/files/pine-studio.png \
    /sdcard/Android/data/ai.drfx.maximus.matrixai/files/agents-my-agents.png \
    /sdcard/Android/data/ai.drfx.maximus.matrixai/files/smart-combo.png \
    /sdcard/Android/data/ai.drfx.maximus.matrixai/files/tools-hub.png || true
fi
if [[ "$initial_status" -eq 0 ]] && [[ "$(cat keyboard-check/initial-device-state.txt)" == device ]]; then
  timeout --kill-after=30s 15m gradle connectedDebugAndroidTest --no-daemon --stacktrace \
    "${test_args[@]}" || test_status=$?
else
  # Still collect bounded diagnostics and emit result.json on initial ADB failure.
  test_status=1
fi
printf '%s\n' "$test_status" > keyboard-check/test-exit-status.txt
timeout --kill-after=2s 10s adb get-state > keyboard-check/device-state.txt 2>&1 || true
# KeyboardLayoutTest saves its failure screenshot in the app external files area.
timeout --kill-after=2s 15s adb pull /sdcard/Android/data/ai.drfx.maximus.matrixai/files/failure-state.png keyboard-check/test-failure-screen.png || true
if [[ -z "${ANDROID_TEST_CLASSES:-}" || "${ANDROID_TEST_CLASSES:-}" == *"KeyboardLayoutTest"* ]]; then
  for capture in home-graph.png chat-focus.png studio-overview.png pine-studio.png agents-my-agents.png smart-combo.png tools-hub.png; do
    # The test copies these before Gradle's uninstall; retain external storage as a fallback.
    if ! timeout --kill-after=2s 10s adb pull "/data/local/tmp/maximus-keyboard-$capture" "keyboard-check/$capture"; then
      timeout --kill-after=2s 10s adb pull "/sdcard/Android/data/ai.drfx.maximus.matrixai/files/$capture" "keyboard-check/$capture" || true
    fi
  done
fi

if [[ -d app/build/outputs/androidTest-results/connected ]]; then
  cp -R app/build/outputs/androidTest-results/connected keyboard-check/test-results
fi
timeout --kill-after=2s 10s adb exec-out screencap -p > keyboard-check/final-screen.png || true
timeout --kill-after=2s 10s adb logcat -d > keyboard-check/device.log 2>&1 || true
timeout --kill-after=2s 10s sudo -n dmesg --ctime > keyboard-check/host-kernel.log 2>&1 || true
timeout --kill-after=2s 15s python3 .github/scripts/collect-emulator-diagnostics.py keyboard-check || true
free -m > keyboard-check/host-memory.txt
ps -eo pid,ppid,rss,comm > keyboard-check/host-processes.txt
classification_status=0
python3 .github/scripts/classify-emulator-result.py keyboard-check "$test_status" || classification_status=$?
if [[ "$test_status" -eq 0 ]]; then test_status=$classification_status; fi
if [[ "$test_status" -ne 0 ]]; then
  # Emit bounded test-only hierarchy/inset diagnostics for remote investigation.
  grep -E "KeyboardLayoutTest|FATAL EXCEPTION" keyboard-check/live-device.log | tail -n 90 || true
fi
exit "$test_status"
