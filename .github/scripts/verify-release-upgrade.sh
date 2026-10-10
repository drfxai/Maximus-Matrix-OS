#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?}" "${RELEASE_TAG:?}" "${ARM64_APK:?}" "${UNIVERSAL_APK:?}"
TOOLS="$ANDROID_HOME/build-tools/36.0.0"
mkdir -p previous
# Fail closed if the established release or either permanent-key APK is missing.
gh release view "$RELEASE_TAG" --json isDraft,isPrerelease,body > previous/release.json
python3 - <<'PY'
import json
r=json.load(open('previous/release.json'))
assert not r['isDraft'] and not r['isPrerelease'], 'Existing release must be Stable'
open('previous/release-notes.md', 'w').write(r['body'] or '')
PY
gh release download "$RELEASE_TAG" --pattern "$ARM64_APK" --pattern "$UNIVERSAL_APK" --pattern SHA256SUMS --dir previous
(cd dist && sha256sum --check SHA256SUMS)
(cd previous && sha256sum --check SHA256SUMS)
for name in "$ARM64_APK" "$UNIVERSAL_APK"; do
  "$TOOLS/apksigner" verify --print-certs "previous/$name" > "previous/$name.certs"
  "$TOOLS/apksigner" verify --print-certs "dist/$name" > "dist/$name.certs"
  "$TOOLS/aapt" dump badging "previous/$name" > "previous/$name.badging"
  "$TOOLS/aapt" dump badging "dist/$name" > "dist/$name.badging"
  python3 .github/scripts/verify-apk-identity.py "$name"
done
# Both variants must share the same identity and signer.
diff <(grep 'certificate SHA-256 digest:' "dist/$ARM64_APK.certs") <(grep 'certificate SHA-256 digest:' "dist/$UNIVERSAL_APK.certs")
touch dist/upgrade-verified
