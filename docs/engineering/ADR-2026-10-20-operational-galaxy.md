# ADR: Native Galaxy layouts and operational evidence

Date: 2026-10-20
Baseline: 21761c7062f9fe59eb0716d7add5da3ab3a27a71 (PR137, unmerged)
Status: Proposed; no merge or publication authorization.

## User priority and language decision
The owner now prioritizes Smart Combo, six native Galaxy modes, operational dashboards and
GitHub/Cloudflare developer connections. New UI is English-only to save engineering time.
Existing translations, app-language selection, preferences and five-language regression gates
are retained. No localization expansion is included in this increment.

## Decision
Preserve the original Brain as default. Adapt one read-only snapshot of explicit module-map
and persisted Room nodes/edges to Rings, Circle, Areas, Links, Timeline and 3D Orbit.
Module-map relationships are declared navigation topology, not observed execution.
Only real persisted/event timestamps enter dated timeline lanes; undated records stay undated.
Keep quaternion camera projection; flat modes share stable identity, filtering and inspection.
Cache/bound force-layout work outside per-frame rendering; no new rendering framework.

Project Smart Combo's existing StateFlow into route-aware, independently expiring evidence.
Configured provider/model is separate from dispatched identity. Missing, stale, mismatched and
future counters do not become balances. Preserve free-only admission and explicit paid policy.
Connection changes invalidate old rate snapshots even when provider/model IDs match.

Add explicit read-only GitHub inspection (repo/ref/SHA/protection/latest workflow), independently
dated credential/discovery evidence and Unknown/Stale/Unavailable/Error states. Latest workflow
success is not proof that every required check passed. Keep existing staged Cloudflare activation
and rollback approvals; no cloud service is provisioned or deployed by this change.

## Verification and deliverables
Run deterministic JVM tests, lint/debug assembly, both existing Android emulator suites and new
real-screen acceptance captures. Screenshots are original UiDevice PNG bytes from the candidate
application, with source checkout/run provenance and SHA-256. No AI-generated screenshot,
mock connected account, fake inference activity or deployment success.
Export only allowlisted frames from passing tests; retain standard workflow artifacts.
Screenshot source may be committed separately as repository-backed review evidence after CI.

## Preserved boundaries and limits
No package/version/signature/Room migration or settings reset. PRoot/device/kernel capabilities
are unchanged. No credentials are embedded or forwarded. No remote mutation occurs on render.
A process-local quota ledger is still not a durable multi-device ledger; live credentials,
physical ARM64 acceptance, graph frame profiling and owner release acceptance remain separate.
