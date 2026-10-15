# Phone Harness — five-language UI follow-up

**Scope:** UI localization only; no change to the optional Linux installer, PRoot checksums, Android permission model, foreground-service behavior, API keys, or Gradle build execution.

This patch converts 38 previously untranslated **Mobile Harness UI and transient status strings** to Android resource entries for English, Persian, Arabic, Turkish, and Russian.

## Screen coverage

- Server state: live/offline, on-phone/online/standby, localhost-only address and launch failure.
- Toolchain installation: setup start and failure, verified archive transfer counts and checksum labels.
- Debug APK export: selected-destination error, success/failure and verified SHA-256 status.
- Project authorization: copy-key label and warning, selected project, revocation result and sensitive-source consent text.
- Android readiness: API/CPU/RAM/storage metrics, ARM64 compatibility, the four actual native Linux readiness headlines and security limitations.
- High-impact consent dialogs: user-approved Gradle execution risk, limited source ZIP sharing and stop/revoke controls; localized Cancel.
- Localized Android foreground-service startup/build messages emitted from the Compose screen.

Technical identifiers (SHA-256 digest, ARM64, API, architecture, file paths, ZIP, MIME, PORT) are intentionally not translated.

## Security constraints

Only a user-visible Start or Build action launches a foreground operation. No Internet/LAN server, Gradle permission, shared provider credential, source upload or signing key is added by this patch. The local authenticated phone-host API remains loopback-only.

PRoot does **not** isolate Gradle scripts from the app's Android UID. The build confirmation now states this risk in each of the five UI languages; do not claim trusted sandboxing or a physical-device build without actual verification.

## Remaining gaps

- `PhoneRuntimeInstaller`, `PhoneOnDeviceApkBuilder` and `PhoneRuntimeSetupService` emit some dynamic English runtime diagnostics; the PhoneRuntimeInspector headline is now translated by the UI from its typed flags. Remaining dynamic statuses should migrate to typed resource status IDs in a separate audited change, rather than blindly translating untrusted process logs.
- Android platform errors, Gradle stderr and arbitrary imported-project logs remain in their original language by design.
- The complete application and independent agent UI still have legacy untranslated sections; issue #36 tracks all app-wide localization.
- Physical Samsung ARM64 Android 15 trial must verify long-running downloads, failure/retry, PRoot process viability and creation of a real Debug APK. This patch changes no binary runtime behavior.

## Quality gates

All five `strings.xml` packs must have identical resource names and formatting placeholder types; Run existing UI resource parity tests, JVM tests, Android Lint and two emulator suites. Persian/Arabic RTL screenshot review should exercise status, safe-build confirmation, source sharing, and overflow on Samsung's narrow displays. Ship only after monotonic versionCode and the existing signing certificate pass the release upgrade gate.

Public versionName remains V1.0.0; this patch intentionally does not touch versionCode or the release branch while Build 10020 is under validation.
