# MAXIMUS MATRIX AI for Android

Version: 1.0.3

Package: `ai.drfx.maximus.matrixai`

Target ABI: `arm64-v8a`

MAXIMUS MATRIX AI is the Android device-agent runtime for Maximus Matrix OS.

## Real Android capabilities

Version 1.0.3 connects Matrix tools to Android APIs and system intents:

- Device and battery information
- Web search
- Open URL
- Android settings
- Camera launcher
- Alarm editor
- Calendar event editor
- Dialer
- SMS composer
- Android share sheet
- Clipboard
- Application launch by package name
- Local mission notes
- Research retrieval placeholder with explicit local-only evidence
- Static strategy validation with no false TradingView execution claim

Sensitive communication actions open Android system UI rather than silently sending messages or placing calls.

## Agent pipeline

`Mission -> Memory -> Planner -> Policy -> Android Tool -> Validation -> Artifact -> Matrix Event Stream`

## Mobile-first UI

Version 1.0.3 redesigns the Android surface for small screens with a scrollable LazyColumn, safe system insets, a navigation-bar-aware command dock, 48-56 dp touch targets, a constrained responsive graph card, horizontal quick actions, and a dedicated mission event feed.

## Live graph

The Compose Canvas Matrix reacts to real in-process mission events. Active tool, policy, memory, validation, and artifact nodes illuminate as work moves through the runtime.

## Build

GitHub Actions builds and verifies the ARM64 APK, runs tests, checks the English-only policy, validates the APK signature and ABI, generates SHA-256 checksums, and publishes the release.
