#!/usr/bin/env bash
set -euo pipefail
[[ -f dist/upgrade-verified && -f dist/android-upgrade-verified ]] || { echo 'Upgrade verification required'; exit 1; }
# Keep the release/tag and previous assets intact until all checks have passed.
# GitHub replaces assets individually; restore the complete prior set on failure.
restore_previous() {
  echo 'Publication failed; restoring previous validated APK assets.' >&2
  gh release edit "$RELEASE_TAG" --notes-file previous/release-notes.md || true
  gh release upload "$RELEASE_TAG" "previous/$ARM64_APK" "previous/$UNIVERSAL_APK" previous/SHA256SUMS --clobber || {
    echo '::error::Rollback failed; previous APKs are preserved in the previous-release artifact.' >&2
  }
}
trap restore_previous ERR
gh release upload "$RELEASE_TAG" "dist/$ARM64_APK" "dist/$UNIVERSAL_APK" dist/SHA256SUMS --clobber
mkdir -p published
gh release download "$RELEASE_TAG" --pattern "$ARM64_APK" --pattern "$UNIVERSAL_APK" --pattern SHA256SUMS --dir published
(cd published && sha256sum --check SHA256SUMS)
cmp "dist/$ARM64_APK" "published/$ARM64_APK"
cmp "dist/$UNIVERSAL_APK" "published/$UNIVERSAL_APK"
gh release view "$RELEASE_TAG" --json isDraft,isPrerelease > published/release.json
python3 - <<'PYVERIFY'
import json
release = json.load(open('published/release.json'))
assert not release['isDraft'] and not release['isPrerelease'], 'Published release is not Stable'
PYVERIFY
version_code=$(python3 - <<'PYVERSION'
import re, os
text = open('dist/' + os.environ['UNIVERSAL_APK'] + '.badging').read()
print(re.search(r"versionCode='([0-9]+)'", text).group(1))
PYVERSION
)
cat > dist/release-notes.md <<NOTES
# MAXIMUS MATRIX OS V1.0.0

- Public Android version: 1.0.0; internal version code: $version_code.
- Application ID: ai.drfx.maximus.matrixai.
- Provider credentials and conversation contexts are isolated.
- News and chart analysis show verified sources or explicit unavailable states.
- Permanent release signing certificate and Android upgrade identity verified against the previous release.
- Unit tests, Android lint and emulator smoke checks passed before publication.
- Validated source commit: $GITHUB_SHA.
- ARM64 and universal APKs include SHA-256 checksums.
NOTES
gh release edit "$RELEASE_TAG" --notes-file dist/release-notes.md
# Retarget the existing tag only after every replacement APK is verified.
# Source archives then describe the exact validated build source.
gh api --method PATCH "repos/$GITHUB_REPOSITORY/git/refs/tags/$RELEASE_TAG" -f sha="$GITHUB_SHA" -F force=true
trap - ERR
echo "Validated source commit: $GITHUB_SHA" >> "$GITHUB_STEP_SUMMARY"
echo "Updated existing Stable $RELEASE_TAG assets; public version remains 1.0.0." >> "$GITHUB_STEP_SUMMARY"
