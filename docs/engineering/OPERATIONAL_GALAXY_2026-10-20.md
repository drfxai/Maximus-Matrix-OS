# Operational Galaxy increment — engineering report

Base: PR137 head21761c7062f9fe59eb0716d7add5da3ab3a27a71, unmerged.
Decision: ADR-2026-10-20-operational-galaxy.md.
Owner priority: Smart Combo, six Galaxy modes, operational dashboards and developer connections.
New controls are English-only by explicit owner instruction; existing language preferences,
translations and regression tests are preserved.

## Integrated behavior
- Smart Combo distinguishes configured from actual dispatched provider/model and observes
  preparing/dispatched/streaming phases. Minute counters expire after60seconds; daily request
  evidence after300seconds. A screen-local lifecycle clock cannot refresh upstream evidence.
  Mismatched/unknown/stale/error evidence does not become remaining account credit.
- Catalog failures and expiry do not imply zero eligible routes. Verifiedfree requests do not
  require paid dollar credit; independent token/request windows and paid approval remain enforced.
- Explicit API connection changes and provider presets clear previous credential/endpoint rate
  evidence even if provider/model IDs match. Local token accounting is preserved.
- Original Brain remains initial default and preserves its existing native/quaternion appearance.
  Rings/Circle/Areas/Links/Timeline/3D Orbit are views of one module-map + Room snapshot with stable
  IDs, explicit edges, filters, selection, provenance, inspection, labels/motion/fit and focus.
  Geometry is bounded/prepared outside drawing. Corrupt saved IDs/coordinates/radii are bounded
  or excluded without rewriting Room.
- Timeline dates are actual Room/event evidence attached to matching graph nodes, with undated
  nodes shown separately. This is an aggregated linked-node timeline, not a complete durable
  task/run event-history service.
- Developer operations presents independently dated token verification and Cloudflare discovery,
  plus explicit read-only GitHub repo/ref/SHA/protection/latest-workflow inspection. Credential
  revision and branch head are rechecked. Latestworkflow success is not all-required-CI proof.
  No automatic network probe or credential mutation occurs while rendering this dashboard.

## Tests and screenshots
Existing JVM/lint/build/security/resource gates and BOTH emulator suites remain mandatory.
New regressions cover Smart Combo evidence, free-vs-paid accounting, rate reset, developer
observation/ref/SHA/provenance, graph identity/layout/invalidinput/bounds and screenshot export.
New OperationalAcceptanceTest selects all six modes through real visible controls, waits for
asynchronous geometry readiness, checks Brain accessibility and captures genuine UiDevice PNGs.
An independent test composes the actual DeveloperConnectionsPanel without remote/mock connectivity.
It captures an explicitly labeled acceptance view of actual unconfigured local state.
Existing KeyboardLayoutTest still captures SmartCombo/Work/Chat/Pine/Agents in the real app shell.

Capture files are cleared before a run, copied before application uninstall and checked after a
passing suite. Allowlisted original PNGs may be exported with run/checkout SHA and SHA-256 for
review tooling; the standard artifact retains captures/reports. No generated/painted screenshots.
Results at this source checkpoint: PENDING final combined-head CI. Refresh PR current-head checks
before relying on the result; the PR body will record completed evidence without changing source.

## Independent review and known limits
Independent security and QA source reviews found/fixed exact Canvas accessibility compatibility
and same-ID credential-reconfiguration rate provenance; final combined review remains required.
No live user-provider inference, real-account GitHub/Cloudflare acceptance or deployment is claimed.
No new cloud runtime, durable cross-process quota ledger, generic autonomous team DAG or Pulse
service is provisioned. Existing Cloudflare staged deployment/rollback approval gates are retained.
No physical ARM64 build or graph60FPS benchmark was performed. Emulator animations are disabled,
so screenshots and tests do not prove sustained gesture frame pacing on physical devices.

## Preservation
No versionCode/versionName/applicationId/signing change. No Room migration or data/preferences
reset. No public release, protected-branch write, merge, cloud deployment or owner acceptance.
Approved Chat/Work/Pine/Agents shell proportions are preserved; Smart Combo adds runtime evidence
below the existing premium hero. The compact Galaxy selector keeps the Brain graph dominant.
