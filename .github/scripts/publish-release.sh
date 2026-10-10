#!/usr/bin/env bash
set -euo pipefail
[[ -f dist/upgrade-verified ]] || { echo 'Upgrade verification required'; exit 1; }
# Keep the release/tag and previous assets intact until all checks have passed.
# GitHub replaces assets individually; restore the complete prior set on failure.
restore_previous() {
  echo 'Publication failed; restoring previous validated APK assets.' >&2
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
trap - ERR
echo "Validated source commit: $GITHUB_SHA" >> "$GITHUB_STEP_SUMMARY"
echo "Updated existing Stable $RELEASE_TAG assets; public version remains 1.0.0." >> "$GITHUB_STEP_SUMMARY"
