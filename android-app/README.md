# MAXIMUS AI for Android

Version: 1.1.0

Package: `ai.drfx.maximus.matrixai`

Target ABI: `arm64-v8a`

MAXIMUS AI is the Android product interface for MAXIMUS MATRIX OS. The package remains
stable for upgrade compatibility while all visible product branding uses MAXIMUS AI.

## Product surface

The Compose application provides Matrix, Chat, Missions, Agents, Research, Trading,
Knowledge, Memory, Tools, Activity, and Settings destinations. A persistent mission dock
is available throughout the app. Secondary capability domains are reached from the More
destination so phone navigation remains touch-friendly.

The Chat screen intentionally reports that no AI provider is configured. It does not
simulate responses. Research, trading, memory, and settings screens distinguish available,
limited, planned, and unavailable functionality.

## Runtime and Android tools

The current execution path is:

`Mission -> Memory -> Planner -> Policy -> Android Tool -> Validation -> Artifact -> Matrix Event Stream`

Available deterministic commands include device status, web search, URL launch, settings,
camera, alarms, calendar, dialer, SMS composer, share, clipboard, and app launch. Sensitive
actions use visible Android interfaces rather than silent execution.

## Build

The release workflow tests the app, builds an ARM64 APK, verifies signing and ABI metadata,
checks the launcher icon and English-only policy, and creates SHA-256 checksums.

Production releases require these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Without them, a build is preview-only and not upgrade-compatible with a stable production
key.
