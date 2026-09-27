# MAXIMUS MATRIX AI for Android

Version: 1.0.1

Package: `ai.drfx.maximus.matrixai`

Target ABI: `arm64-v8a`

This Android application is the device-agent runtime for Maximus Matrix OS. It combines a graph-native mission agent with a live Matrix interface driven by structured execution events.

## Agent pipeline

`Mission -> Memory -> Planner -> Policy -> Tool -> Validation -> Artifact`

## Live graph

The Compose Canvas interface visualizes active subsystems including:

- Maximus Agent
- Tool Registry
- Memory Core
- Policy Engine
- Research Graph
- Trading Genome
- Validation Lab
- Artifact Registry

The graph reacts to actual in-process agent events.

## Build

The GitHub release workflow installs Android SDK 36, NDK 27.2, CMake 3.22.1, Gradle 9.3.1, runs tests, builds the ARM64 release APK, verifies the native ABI, generates SHA-256 checksums, and publishes release `v1.0.1`.
