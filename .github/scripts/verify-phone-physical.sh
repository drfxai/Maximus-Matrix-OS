#!/usr/bin/env bash
# Real ARM64 acceptance. Run locally with an attached, user-owned physical Android device.
set -euo pipefail

if [[ "${MAXIMUS_APPROVE_TRUSTED_BUILD:-}" != "yes" ]]; then
  echo "Set MAXIMUS_APPROVE_TRUSTED_BUILD=yes to explicitly approve the trusted starter build." >&2
  exit 2
fi
for binary in adb gradle git python3; do command -v "$binary" >/dev/null || { echo "Missing $binary" >&2; exit 2; }; done
cd "$(git rev-parse --show-toplevel)"
source_commit="$(git rev-parse HEAD)"
[[ "$source_commit" =~ ^[a-f0-9]{40}$ ]] || exit 2
mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ "${#devices[@]}" -ne 1 ]]; then
  echo "Connect exactly one authorized physical Android device, or disconnect extras." >&2
  exit 2
fi
serial="${devices[0]}"
adb -s "$serial" shell getprop ro.product.cpu.abilist | grep -q 'arm64-v8a' || { echo "ARM64 is required." >&2; exit 2; }
api="$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$api" =~ ^[0-9]+$ ]] && (( api >= 28 )) || { echo "Android 9+ required." >&2; exit 2; }
model="$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')"
printf 'Device: %s (API %s, serial hidden)\n' "$model" "$api"
# The Java/Android tools below must already have been installed and verified on the phone.
gradle :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --stacktrace
mapfile -t app_apks < <(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | sort)
mapfile -t test_apks < <(find app/build/outputs/apk/androidTest/debug -maxdepth 1 -type f -name '*.apk' | sort)
if [[ "${#app_apks[@]}" -ne 1 || "${#test_apks[@]}" -ne 1 ]]; then
  echo "Expected one Debug APK and one instrumentation APK." >&2
  exit 2
fi
adb -s "$serial" install -r "${app_apks[0]}"
adb -s "$serial" install -r "${test_apks[0]}"
out="phone-physical-evidence/$source_commit"
mkdir -p "$out"
adb -s "$serial" shell am instrument -w \
  -e class ai.drfx.maximus.matrixai.studio.phonehost.PhoneNativeApkBuildAcceptanceTest \
  -e approvedOwnerBuild true \
  -e sourceCommit "$source_commit" \
  ai.drfx.maximus.matrixai.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$out/instrumentation.txt"
grep -Eq 'OK \(1 test\)' "$out/instrumentation.txt" || { echo "Physical acceptance test did not pass." >&2; exit 1; }
remote="/sdcard/Android/data/ai.drfx.maximus.matrixai/files/physical-acceptance"
adb -s "$serial" pull "$remote/result.json" "$out/result.json"
adb -s "$serial" pull "$remote/phone-proof-debug.apk" "$out/phone-proof-debug.apk"
python3 - "$out" "$source_commit" <<'PYVERIFY'
import hashlib, json, pathlib, sys
directory, revision = pathlib.Path(sys.argv[1]), sys.argv[2]
report = json.loads((directory / 'result.json').read_text())
apk = directory / 'phone-proof-debug.apk'
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
assert report['physical_device'] is True and report['abi'] == 'arm64-v8a'
assert report['source_commit'] == revision and report['build_and_export'] == 'passed'
assert report['apk_sha256'] == digest and apk.stat().st_size >= 1024
print('Physical ARM64 source, APK artifact and digest verified:', digest)
PYVERIFY
