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
- News Intelligence includes manual and in-screen automatic refresh, an optional authorized calendar feed, daily and weekly views, and advance high-impact event reminders.
- Image attachments on authenticated 9Router routes may be attempted when vision support is unknown; explicitly text-only routes are rejected. Provider vision support remains unverified until tested.
- Project Studio supports locally persisted Android starter projects, safe ZIP and public GitHub import, source editing, explicit ZIP export and user-triggered AI project planning.
- Project Studio does not run imported code or perform remote builds. Cloud workers, signing of imported projects and unattended deployments require separate infrastructure and authorization.
- Added selectable English, Russian, Turkish, Arabic and Persian interface languages, persisted on-device. Navigation, settings and Project Studio are localized; Arabic/Persian use RTL. Some legacy screens still use English labels.
- Remote Runtime discovery can authenticate to a separately deployed Windows/Linux Worker over HTTPS. Credentials are encrypted locally; user code execution and remote builds remain disabled.
- Smart Combo adds user-opt-in, verified zero-price text routing across already configured provider accounts. Unknown pricing is never assumed free; no paid fallback occurs. Catalog verification does not establish account quota.
- Official OpenAI/Groq rate-limit headers are displayed as dated RPM/RPD/TPM snapshots when available, separately from unverified account credit. Streams lacking final provider token usage remain visibly estimated.
- Project Studio includes versioned private SHA-256 checkpoints, detects modified source files, and permits explicit model continuation planning. No untrusted code execution or automatic tool replay is enabled.
- Chat displays an animated jump-to-latest button, Copy/Download, local feedback and clearly marked unexecuted tool proposals.
- Verified News now supports RSS/Atom and publisher-authentic images with secure fallback and retry. Vision supports bounded, sampled image uploads using available Android codecs; actual provider vision capabilities remain subject to live verification.
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
