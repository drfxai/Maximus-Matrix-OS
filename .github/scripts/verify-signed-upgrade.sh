#!/usr/bin/env bash
set -euo pipefail
[[ -f dist/upgrade-verified ]] || { echo 'Permanent signature verification required'; exit 1; }
PACKAGE=ai.drfx.maximus.matrixai
mkdir -p upgrade-check
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
for field in ('userId', 'dataDir'):
    pattern = field + r'=([^\s]+)'
    assert re.search(pattern, old).group(1) == re.search(pattern, new).group(1), field + ' changed'
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
echo 'Permanent signed APK installed over previous V1.0.0 with package UID/data directory retained; updated app launched without observed startup crash.' >> "$GITHUB_STEP_SUMMARY"
