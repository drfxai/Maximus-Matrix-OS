# Build and test MAXIMUS MATRIX OS

This is a Kotlin / Jetpack Compose Android application with Python and Node-based support tests. Use the exact revision you intend to validate. This guide is derived from the repository's Gradle files and `.github/workflows/android-release.yml`; it is not a claim that the commands were executed during documentation preparation.

## Prerequisites

- JDK 21 (the existing GitHub Actions build uses Temurin 21).
- Android SDK platform 36, build-tools 36.0.0 and platform-tools.
- Gradle 9.3.1 installed (CI uses `gradle/actions/setup-gradle@v4`). This repository currently has **no checked-in Gradle wrapper**; use `gradle`, not `./gradlew`.
- Python 3 and Node.js for the auxiliary checks.
- Network access to Android/Kotlin dependency repositories and to separately checksum-pinned upstream phone runtime assets.
- Optional: a configured Android emulator for instrumentation tests; a separate test phone for physical ARM64 acceptance.

## Clone and build

```bash
git clone https://github.com/drfxai/Maximus-Matrix-OS.git
cd Maximus-Matrix-OS

# Install SDK 36/build-tools 36.0.0 via Android SDK manager first.
# Prepare checksum-pinned ARM64 native PRoot files.
python3 .github/scripts/prepare-phone-native.py

# Unit tests and static checks; Gradle resolves dependencies.
gradle testDebugUnitTest lintRelease assembleDebug --no-daemon --stacktrace
```

The native preparation script downloads `techjarves/Mobile-Harness` APK `v1.0.7` and checks its complete SHA-256 and ELF architecture. Do not relax integrity verification if the download fails. Inspect [third-party license obligations](../THIRD_PARTY_NOTICES.md) before redistributing extracted native components.

## Auxiliary tests

```bash
python3 -m unittest discover -s .github/scripts/tests -p "test_*.py" -v
python3 .github/scripts/check-ui-locales.py
python3 .github/scripts/test-release-identity.py
python3 .github/scripts/test-release-publication.py
python3 -m unittest discover -s remote-worker/tests -t remote-worker -v
node --test ops/cloud-agent/tests/*.test.mjs
for script in .github/scripts/*.sh; do bash -n "$script"; done
```

The repository's CI also materializes pinned large runtime archive fixtures with `.github/scripts/prepare-phone-runtime-fixtures.py`; see the [workflow](../.github/workflows/android-release.yml) for complete exact-head validation.

## Instrumented, device and release gates

Android emulator tests use `.github/scripts/verify-keyboard.sh` and the `emulator-smoke` GitHub Actions matrix. Device-specific ARM64/PRoot acceptance requires a dedicated test phone and explicit owner authorization; see [physical-phone acceptance](physical-phone-build-acceptance.md). Emulator success **does not** establish physical-device behavior. Debug APKs are not production-signed.

Do not introduce signing credentials into commits. Release publishing, production signing, changing APK version identity and upgrades on a personal device require separate authorization. Documentation-only changes do not publish or update a release.
