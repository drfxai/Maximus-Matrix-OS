#!/usr/bin/env bash
set -euo pipefail
[[ -f dist/upgrade-verified ]] || { echo 'Permanent signature verification required'; exit 1; }
# Bound every device command so lost emulators cannot hang release diagnostics.
adb_binary=$(type -P adb)
adb() { timeout --kill-after=5s 90s "$adb_binary" "$@"; }
PACKAGE=ai.drfx.maximus.matrixai
mkdir -p upgrade-check
timeout --kill-after=5s 8m "$adb_binary" logcat -v threadtime > upgrade-check/live-device.log 2>&1 &
log_pid=$!
trap 'kill "$log_pid" 2>/dev/null || true; wait "$log_pid" 2>/dev/null || true; timeout --kill-after=2s 10s sudo -n dmesg --ctime > upgrade-check/host-kernel.log 2>&1 || true; free -m > upgrade-check/host-memory.txt; timeout --kill-after=2s 15s python3 .github/scripts/collect-emulator-diagnostics.py upgrade-check || true' EXIT
adb install "previous/$UNIVERSAL_APK" | tee upgrade-check/old-install.txt
adb shell am start -W -n "$PACKAGE/.MainActivity" | tee upgrade-check/old-launch.txt
grep -q 'Status: ok' upgrade-check/old-launch.txt
adb shell dumpsys package "$PACKAGE" > upgrade-check/old-package.txt
adb shell am force-stop "$PACKAGE"
# -r must preserve the installed package and its data; never uninstall or clear.
adb install -r "dist/$UNIVERSAL_APK" | tee upgrade-check/new-install.txt
grep -q Success upgrade-check/new-install.txt
adb shell dumpsys package "$PACKAGE" > upgrade-check/new-package.txt
python3 - <<'PY'
from pathlib import Path
import re, os
old = Path('upgrade-check/old-package.txt').read_text()
new = Path('upgrade-check/new-package.txt').read_text()
expected = re.search(r"versionCode='(\d+)'", Path('dist/' + os.environ['UNIVERSAL_APK'] + '.badging').read_text()).group(1)
assert re.search(r'versionCode=(\d+)', new).group(1) == expected
assert re.search(r'versionName=([^\s]+)', new).group(1) == '1.0.0'
# Android package dumpsys exposes appId on newer platform versions and
# userId on some older versions. Either is the package's numeric Linux UID;
# require it in both snapshots and reject any change after an in-place install.
def required(pattern, payload, label):
    match = re.search(pattern, payload, re.M)
    assert match is not None, f'{label} missing from package state'
    return match.group(1)

uid_pattern = r'^\s*(?:userId|appId)=(\d+)(?:\s|$)'
assert required(uid_pattern, old, 'previous app UID') == required(uid_pattern, new, 'updated app UID'), 'Package UID changed'
data_dir = r'^\s*dataDir=([^\s]+)'
assert required(data_dir, old, 'previous data directory') == required(data_dir, new, 'updated data directory'), 'Package data directory changed'
PY
adb logcat -c
adb shell am start -W -n "$PACKAGE/.MainActivity" | tee upgrade-check/new-launch.txt
grep -q 'Status: ok' upgrade-check/new-launch.txt
# Allow startup work to complete before checking for process death/crashes.
for attempt in 1 2 3 4 5; do
  sleep 1
  adb shell pidof "$PACKAGE" > upgrade-check/app-pid.txt
done
pid=$(tr -d '\r\n' < upgrade-check/app-pid.txt)
[[ -n "$pid" ]]
adb logcat -d --pid="$pid" > upgrade-check/app-logcat.txt
if grep -E 'FATAL EXCEPTION|Fatal signal|ANR in ai.drfx.maximus.matrixai' upgrade-check/app-logcat.txt; then
  echo 'Updated APK crashed during launch'; exit 1
fi
adb exec-out screencap -p > upgrade-check/updated-app.png
touch dist/android-upgrade-verified
echo 'Permanent signed APK installed over previous V1.0.0 with package UID/data directory retained; updated app launched without observed startup crash.' >> "$GITHUB_STEP_SUMMARY"
