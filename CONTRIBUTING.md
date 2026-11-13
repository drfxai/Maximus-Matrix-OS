# Contributing to MAXIMUS MATRIX OS

Thank you for improving this Android AI workspace. Contributions are welcome through focused issues and pull requests. Please read [LICENSE](LICENSE), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [SECURITY.md](SECURITY.md) and [BUILDING.md](docs/BUILDING.md) first. Original material is proposed for release under MIT; third-party materials remain under their own terms.

## Before opening an issue

- Search existing issues/PRs and the [changelog](CHANGELOG.md).
- Include a reproducible sequence, expected and actual behavior, app version/build code, Android version, device/emulator and relevant redacted logs.
- Never publish provider API keys, session tokens, server credentials, signing keys, private chat or financial data.
- Report suspected security vulnerabilities **privately** according to [SECURITY.md](SECURITY.md).

## Pull-request workflow

1. Start from current `main`, create a narrowly scoped branch and describe the problem or improvement.
2. Keep the package identity, versionName/versionCode, user profiles, account data, and five-language resources intact unless explicitly authorized for a scoped change.
3. Add tests that reproduce the issue, including failure paths; document what was actually run.
4. Use the [build guide](docs/BUILDING.md) to validate Kotlin/Android and auxiliary Python/Node components. If you cannot run a hardware test, label it `NOT RUN`, not `PASSED`.
5. Update docs and user-facing instructions when behavior changes; include verified screenshots rather than mockups presented as actual app output.
6. Request review. An accepted PR requires maintainer review and applicable exact-head CI before merge. A green check is not device acceptance.

## Areas suited to new contributors

- UI localization and accessible Jetpack Compose interfaces.
- Android lifecycle/Room persistence regression tests.
- Credential isolation, model routing and quota evidence.
- Safe phone-runtime validation, provenance and reproducible build tests.
- Documentation, bug triage, CI reliability and release notes.

## Review and trust boundaries

Never bundle real credentials or proprietary provider datasets. Generated code is reviewed the same way as human-written code. No external deployment, paid provider call, destructive phone operation or production release is authorized merely because a PR was submitted.

By contributing, you affirm you have rights to submit your changes under the repository's applicable license; do not copy unlicensed third-party code.
