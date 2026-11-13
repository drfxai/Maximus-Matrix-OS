# Security policy

Security reports are handled privately and investigated based on severity and reproducibility. This is not a promise of a fixed response time or a statement that every integration is security-audited.

## Supported versions

The latest public release is the supported release candidate for fixes; check [GitHub Releases](https://github.com/drfxai/Maximus-Matrix-OS/releases) for the most recent published version. Older releases may not receive patches. New code in `main` is not automatically deployed to devices.

## Responsible disclosure

Do **not** open a public GitHub issue for vulnerabilities. If the repository's GitHub **Security → Report a vulnerability** workflow is enabled, submit a private advisory there. Otherwise contact the maintainers through a private channel identified on the [GitHub maintainer profile](https://github.com/drfxai) and request a secure reporting method before sending exploit details.

Useful reports include affected release/commit, impact, reproducible steps, conditions and minimal sanitized evidence. Never include credentials, private conversations, customer data, real trading-account identifiers or signing keys. Do not test against other users' infrastructure without permission.

## Priority security surfaces

- Model/provider tokens, API-key storage, account isolation and logging redaction.
- User-approved agent actions, scoped tool permissions and denial/replay boundaries.
- Remote worker authentication, Docker isolation, source ZIP integrity and restart behavior.
- Untrusted model output, prompt injection, project import/export and WebView/URL handling.
- Third-party native PRoot binaries, runtime archive integrity and provenance.
- Dependency, CI workflow, release-signing and APK verification supply chain.

## Maintainer workflow

Triage privately, reproduce in a controlled environment, fix with regression coverage, and independently review before publication. Coordinate a security advisory and upgrade guidance as appropriate. Never publish proof-of-concept secrets or production keys.

## User boundaries

Local and hosted providers have separate privacy and security policies. Do not assume that text, trades, project source or prompts remain entirely on-device when an external provider is enabled. Verify provider endpoints, requested permissions and exported data before use.
