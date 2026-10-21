#!/usr/bin/env bash
set -euo pipefail
[[ "${RELEASE_PUBLICATION_APPROVED:-}" == true ]] || { echo "Explicit release publication authorization required"; exit 1; }
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
test -s dist/MAXIMUS-MATRIX-OS-PRoot-Source-V1.0.0.tar.gz
(cd dist && sha256sum --check NATIVE-SOURCE-SHA256SUMS)
gh release upload "$RELEASE_TAG" "dist/$ARM64_APK" "dist/$UNIVERSAL_APK" dist/SHA256SUMS \
  dist/MAXIMUS-MATRIX-OS-PRoot-Source-V1.0.0.tar.gz dist/NATIVE-SOURCE-SHA256SUMS --clobber
mkdir -p published
gh release download "$RELEASE_TAG" --pattern "$ARM64_APK" --pattern "$UNIVERSAL_APK" \
  --pattern SHA256SUMS --pattern MAXIMUS-MATRIX-OS-PRoot-Source-V1.0.0.tar.gz \
  --pattern NATIVE-SOURCE-SHA256SUMS --dir published
(cd published && sha256sum --check SHA256SUMS && sha256sum --check NATIVE-SOURCE-SHA256SUMS)
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
- Chat can explicitly open Pine source in a separate persisted Pine Studio draft; imported source and existing drafts are preserved. A limited SMA/EMA(close) preview uses exchange candles; general Pine compilation and backtesting remain unavailable.
- Provider discovery is authenticated and bound to the selected API key and endpoint. Listed candidates remain distinct from per-model inference tests. Dated successful, unsuccessful and inconclusive tests are shown; API changes revoke old evidence. Explicit tests are bounded to 30 seconds and 128 output tokens with no automatic replay.
- Chat has a compact toolbar and smaller margins. Hub exposes Smart Combo/API Control, developer connections and operational panels; all Control sections are reachable. Chat displays the actual dispatched model and unknown context remains unknown.
- The real saved Chat-to-Pine navigation, Hub/Control access and six Galaxy mode switching are covered by emulator tests. Live user-provider acceptance and physical-device performance remain separate verification gates.
- Smart Combo now preserves device-local request and confirmed-usage evidence across restarts. Interrupted dispatches remain uncertain and are never automatically replayed; settled evidence can be archived after review.
- Neural Galaxy offers Rings, Circle, Areas, Links, Timeline and 3D Orbit over the same graph while preserving Brain as the default. Timeline shows recorded evidence and explicitly unavailable history; sparse timelines and dense labels still need visual refinement.
- Hub includes Operations views for Agents, Pulse, Memory, Runtime and Security, with observation timestamps and explicit UNKNOWN states. Saved agent definitions are not proof of running agents; Android schedules remain subject to Doze and battery restrictions.
- GitHub source approval shows a captured diff and rejects account changes during review/write. Cloudflare uncertain writes stay blocked until an explicit same-account evidence review; no automatic deployment or write replay occurs.
- New operational UI is English-first; existing language settings, project data, conversation history and the permanent application identity are preserved. Live account acceptance and physical ARM64 performance/build testing remain separate, unverified gates.
- Pinned Ubuntu runtime installation accepts valid Unix multiarch filenames such as libc6:arm64.list; existing verified downloads are reusable with Resume. Runtime cleanup does not follow guest symlinks on the host.
- Complete checksum-verified Linux and Android toolchain archives are extracted by the production extractor in required CI; physical ARM64 tool execution and on-phone build acceptance remain separate.
- AMOLED workspaces use a pure-black canvas, balanced navy cards, white text, gold confirmations, orange activity and red warnings, with fixed Brain, Chat, Agents, Studio and More navigation.
- Smart Combo includes an animated accessible context gauge, provider selection cards and observed token transitions. Context capacity, local budgets and dated provider rate snapshots remain separate; unknown quota is not fabricated.
- Chat includes message arrival, staggered generation, persisted reaction spring/haptic feedback and animated copy confirmation. Studio uses compact project shortcuts and press feedback. Native animations follow Android animation settings.
- Navigation accessibility names remain exposed after keyboard dismissal; direct Chat, Studio and independent-agent navigation are covered by emulator tests.
- Bundled native plugins require explicit permission review. The Linux workspace runs manually approved, bounded noninteractive commands in the installed PRoot userspace; it does not provide a separate kernel or Android root.
- Provider credentials and conversation contexts are isolated.
- News and chart analysis show verified sources or explicit unavailable states.
- News Intelligence includes manual and in-screen automatic refresh, an optional authorized calendar feed, daily and weekly views, and advance high-impact event reminders.
- Image attachments on authenticated 9Router routes may be attempted when vision support is unknown; explicitly text-only routes are rejected. Provider vision support remains unverified until tested.
- Project Studio supports locally persisted Android starter projects, safe ZIP and public GitHub import, source editing, explicit ZIP export and user-triggered AI project planning.
- Brain home assistant now routes ordinary questions to the selected authenticated streaming Chat model instead of a limited device-mission planner; compact real messages and a link to full chat are visible in Brain. Explicit phone instructions can open Chrome/Google search, camera, Telegram and the Android Wi-Fi settings panel only through the existing user-approval gate. No hidden photo capture, Accessibility privilege abuse, silent Wi-Fi toggle or arbitrary shell command.
- Brain now offers optional spoken replies for NEW completed assistant responses. Uses the effective user-selected speech locale, including fa-IR, and checks Android TextToSpeech voice availability. Audio stops on pause or when disabled; no automatic microphone access or paid voice fallback.
- Dictation and Talk mode now keep recognition candidates for human review when Android ignores fa-IR/ar-SA/ru-RU and returns text in an incompatible script. Such a transcript is NEVER auto-sent. The recognizer service itself must support the selected language; this app does not bundle a guaranteed Persian ASR engine.
- Chat voice settings include an explicit Follow App Language option, localized language-mismatch guidance and translated permission/recording messages. Existing manual language overrides are respected.
- ElevenLabs Voice Studio is now localized into English, Persian, Arabic, Turkish and Russian. The user-approved voice sample follows the selected speech language; account credits may be consumed by explicit synthesis requests.
- Brain navigation and primary 3D hub labels now use all five app-language resource packs. Remaining legacy strings are tracked separately; full app localization is not yet claimed.
- Brain graph now also localizes 16 module-group labels, graph search, node inspection, navigation actions, camera/view controls, accessibility descriptions and live mission status across en/fa/ar/tr/ru. Canonical technical IDs remain unchanged, and users can search graph groups using their selected language.
- Smart Combo now presents typed, localized free-price catalog and provider eligibility checks across English, Persian, Arabic, Turkish and Russian. Verified zero catalog price is explicitly distinguished from unknown live account quota. The chat manual-provider switch always requires explicit cost confirmation and never auto-resends a prompt.
- AI Engine / Token Telemetry dashboard now uses five-language resources for provider state, model context estimates, usage, rate-limit snapshots, local budgets and cost notices. Upstream IDs, credentials and user content remain untranslated; estimated context capacity is not remaining API credit.
- Agent Teams lets users configure bounded real on-phone publisher-report and device-health workers on 15-minute, 3-hour or daily best-effort Android schedules. Android battery policies prohibit a guarantee of uninterrupted 24/7 local background processing. No unattended paid LLM execution or permanent server is bundled.
- Developer Connections uses separate user-provided Android Keystore-encrypted scoped credentials: GitHub authentication, source reading, manually approved feature-branch creation and conflict-checked individual file commits; Cloudflare verification and read-only Worker listing. No automatic GitHub main-branch write, pull-request merge, Cloudflare deployment or secret forwarding to models.

- Optional native ARM64 Android 9+ Debug APK builds may run on the phone after the user installs both pinned SHA256-verified Ubuntu/Android toolchain bundles, passes real Java/Gradle/AAPT2 probes, and explicitly approves execution of trusted project source. PRoot is not a security sandbox. Physical-device compatibility is unverified unless a real phone successfully builds a project.
- Mobile Harness toolchain setup now distinguishes an explicit Cancel tap from an Android-interrupted foreground service; it preserves verified/partial downloads and provides a visible Resume action and non-secret setup outcome. Interruptions are not falsely reported as user cancellation.
- Smart Combo shows per-provider eligibility diagnostics when no catalog-confirmed zero-cost text route is found; Chat offers Refresh or a separately confirmed manual model choice. Manual provider routing may consume provider credits or incur charges; no automatic fallback or retry is performed. Context token capacity is not a provider's remaining free credit.
- Complete corresponding third-party GPL/LGPL/BSD source for bundled PRoot native binaries is included as a separate release asset.
- Project Studio never silently executes imported code. An opt-in remote Debug APK build requires a separate authenticated, rootless Docker worker, explicit project-source approval and artifact SHA-256 verification; production signing keys are never sent to the worker.
- Mobile Harness Phone Server is user-started and binds only to 127.0.0.1, exposes authenticated read-only workspace APIs and allows bounded source ZIP export for only a separately approved project. Its redesigned dashboard displays the phone runtime/ABI/RAM/storage state; the opt-in Ubuntu/PRoot userspace and toolchain require explicit installation and approval. Physical ARM64 APK build/export acceptance is still outstanding.
- Added selectable English, Russian, Turkish, Arabic and Persian interface languages, persisted on-device. Navigation, settings and Project Studio are localized; Arabic/Persian use RTL. Some legacy screens still use English labels.
- Remote Runtime discovery authenticates to user-managed Worker servers over HTTPS. Debug APK builds may be enabled only with an explicitly configured rootless, offline and digest-pinned Linux builder; no production APK signing, automatic deployment or general shell commands are enabled.
- Smart Combo supports user-opt-in, verified zero-price text routing across already configured provider accounts. It shows eligible catalog routes, applies conservative quota admission and non-replaying cooldown on failed dispatched routes, and learns first-token latency from verified stream observations. Unknown pricing never becomes free and no paid fallback occurs; catalog verification is NOT evidence of account quota or live provider access.
- Official OpenAI/Groq rate-limit headers are displayed as dated RPM/RPD/TPM snapshots when available, separately from unverified account credit. Streams lacking final provider token usage remain visibly estimated.
- Project Studio includes versioned private SHA-256 checkpoints, detects modified source files, offers Develop/Research/Study/Review modes, and permits explicit model continuation planning without silent tool execution.
- Local Model Hub accepts user-selected SAF file references for GGUF v2/v3 header validation and unverified LiteRT-LM inventory. Real GGUF/LiteRT inference and model downloads are NOT installed.
- Chat displays an animated jump-to-latest button, Copy/Download, local feedback and clearly marked unexecuted tool proposals.
- Verified News now supports RSS/Atom and publisher-authentic images with secure fallback and retry. Vision supports bounded, sampled image uploads using available Android codecs; actual provider vision capabilities remain subject to live verification.
- Hindsight Shared Project Memory supports opt-in per-project memory banks, encrypted user-configured credentials, background recall across model changes, and user-approved checkpoint Retain. Short model-progress summaries are encrypted on-device as offline continuity, while cloud auto-sharing requires separate consent; local validated checkpoints remain offline.
- ElevenLabs Voice Studio supports separately encrypted user-owned credentials, verified subscription and voice discovery, and explicit text-to-speech playback in Chat. Automatic voice remains on-device; account allowance and model token usage are separate.
- Added a policy-governed ZimZilla-inspired native terminal workspace with observed Matrix runtime events, Smart Combo diagnostics and agent-team panels; no unrestricted shell or third-party Python executable is bundled.
- Terminal sessions retain a bounded, encrypted local command/output history and an opt-in Linux/Remote Worker panel. Project sharing and executable actions remain subject to the existing explicit approval controls.
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
