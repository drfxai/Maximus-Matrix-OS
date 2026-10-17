# Implementation priorities — 2026-10-17

This order supersedes older roadmap ordering. A milestone is complete only with implementation and verification evidence. Public version remains V1.0.0; preserve the permanent Android signing identity.

| Order | Milestone | Acceptance evidence |
| --- | --- | --- |
| 1 | Maximus Smart Combo | Verified-free-first routing, quota-aware admission and proactive standby selection; no ambiguous post-dispatch replay; provider-confirmed versus estimated token/cost accounting visible to users; continuity checkpoints across model changes. |
| 2 | Agent teams and independent agents | User-defined teams and groups with coordinated tasks and shared Hindsight memory; each agent can also run separately with isolated state, cancellation and results. Dependency handling, leases, restart recovery and credential scopes must be tested. |
| 3 | Build APK on a phone | Physical ARM64 Android device installs the supported toolchain, builds a real project and exports an installable APK with reproducible logs and hashes. |
| 4 | Linux environment on a phone | Install, start, stop and recover the supported Linux userspace and services; disclose Android background and kernel limitations; verify terminal persistence and access controls on a physical device. |
| 5 | Application UI redesign | Large node graph, compact chat margins, professional Smart Combo and team dashboards; active provider/model/agent and token evidence readable at a glance; five-language and RTL verification. |
| 6 | GitHub, Cloudflare and plugin store | Scoped connections, reviewable project changes and deployment approval; plugin discovery, installation, permissions, integrity verification and removal. |
| 7 | MAXIMUS Brain | Conversational assistant with voice and user-approved Android actions, cancellation and permission-aware failure handling. |
| 8 | Local models and installation | Compatible model catalog, verified downloads, storage/RAM/ABI checks and real offline inference; report actual modality support. |
| 9 | News, signals and Vision | Working sources with timestamps, provenance and relevant images; explicit signal origin and freshness; image decoding and real vision-provider acceptance tests. |

## Current verified scope

- The existing project-team executor is a bounded planner/reviewer text workflow. It does not yet satisfy the general independent-agent and collaborative-group milestone.
- Local monitoring teams support allowlisted news/device probes; labels alone do not establish general agent execution or continuous availability.
- Smart Combo quota settlement now accepts delayed provider-confirmed usage after an unverified stream completion, preserving the original completion timestamp so newer snapshots are not charged twice.
- Physical-device, full Android, emulator, cloud deployment and release acceptance require their own evidence. A pure Kotlin unit-test run does not establish those outcomes.

## Verification for the first change

Run the existing SmartComboQuotaLedgerTest, SmartComboPlannerTest and SmartComboAdmissionControllerTest suites. Regression cases cover delayed usage both before and after a newer quota snapshot, immutable confirmed usage and rejection of callbacks for unexecuted requests. Full Android validation remains mandatory before merging or releasing.
