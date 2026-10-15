#!/usr/bin/env bash
set -euo pipefail
# Requires an owner-controlled physical test phone with the candidate DEBUG app,
# its matching test APK, and the pinned runtime already installed by the user.
# Never replace or clear an installed production application to run this test.
[[ "${APPROVE_OWNER_PHONE_BUILD:-}" == true ]] || { echo 'Set APPROVE_OWNER_PHONE_BUILD=true to execute the trusted starter on the test phone.'; exit 1; }
: "${ANDROID_SERIAL:?Select the physical test phone explicitly with ANDROID_SERIAL}"
adb_binary=$(type -P adb)
adb() { timeout --kill-after=5s 30s "$adb_binary" -s "$ANDROID_SERIAL" "$@"; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r\n')" != 1 ]] || { echo 'Emulators cannot satisfy this gate'; exit 1; }
adb shell getprop ro.product.cpu.abilist | grep -q arm64-v8a
adb shell run-as ai.drfx.maximus.matrixai true
source_commit=$(git rev-parse HEAD)
mkdir -p physical-check
rm -f physical-check/result.json physical-check/phone-proof-debug.apk
timeout --kill-after=10s 20m "$adb_binary" -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class ai.drfx.maximus.matrixai.studio.phonehost.PhoneNativeApkBuildAcceptanceTest \
  -e approvedOwnerBuild true -e sourceCommit "$source_commit" -e timeout_msec 1200000 \
  ai.drfx.maximus.matrixai.test/androidx.test.runner.AndroidJUnitRunner | tee physical-check/instrumentation.txt
grep -q 'OK (1 test)' physical-check/instrumentation.txt
adb pull /sdcard/Android/data/ai.drfx.maximus.matrixai/files/physical-acceptance/result.json physical-check/result.json
adb pull /sdcard/Android/data/ai.drfx.maximus.matrixai/files/physical-acceptance/phone-proof-debug.apk physical-check/phone-proof-debug.apk
python3 - "$source_commit" <<'PY'
import hashlib, json, pathlib, re, sys
root = pathlib.Path('physical-check')
proof = json.loads((root / 'result.json').read_text())
assert proof['source_commit'] == sys.argv[1]
assert proof['physical_device'] is True and proof['abi'] == 'arm64-v8a'
assert proof['build_and_export'] == 'passed'
assert proof['apk_sha256'] == hashlib.sha256((root / 'phone-proof-debug.apk').read_bytes()).hexdigest()
assert re.fullmatch(r'ai\.drfx\.generated\.phoneproof[0-9]+', proof['apk_package'])
print('Physical ARM64 build and export passed; installation and voice acceptance remain separate gates.')
PY
