# Native MAPS audit and first stabilization increment

Source audited: d13a199ed95cb5ad232d1782acae91445ebc4652, 2026-10-20.
This is an evidence inventory, not full runtime acceptance of all milestones.
Primary references: [MAPS guide](https://github.com/pavrus117/ai-os-maps-guide),
[approved UI](../ui/MAXIMUS_APPROVED_REFERENCE_V2.md), [ADR](ADR-2026-10-20-native-maps.md).

## Baseline and verification
Main matches the supplied baseline. app/build.gradle.kts has versionName 1.0.0,
versionCode 10029 and applicationId ai.drfx.maximus.matrixai. No changes to these.
The current v1.0.0 Git tag points to the audited main commit. Latest release API reports v1.0.0, assets updated 2026-10-20; release target_commitish
is 0e7f7dc148f905ed4e2aebe8a3d07479c0120c7d. That metadata alone does not establish
the current asset source SHA. Release body reports build 10029.

[Main run 39421264404](https://github.com/drfxai/Maximus-Matrix-OS/actions/runs/39421264404)
completed validation, both API35 x86_64 emulator suites, required-ci, signed build,
permanent-key upgrade and publication steps successfully according to job summaries.
This is existing baseline evidence, not evidence that this PR passed.
No local shell/Android environment or physical device was available in this engineering session.

## Capability matrix
Paths below are relative to app/src/main/java/ai/drfx/maximus/matrixai unless prefixed.
IMPLEMENTED means inspected source establishes the narrow listed operation, not complete
product acceptance. PARTIAL means implemented subset; UNVERIFIED needs external runtime
evidence; MISSING means not found in the inspected subsystem, not an exhaustive absence proof.

| Capability / classification | Source evidence | Runtime evidence / gap | Security / required change | Tests / dependencies | Priority / risk |
|---|---|---|---|---|---|
| Android shell / IMPLEMENTED | MainActivity.kt; ui/MatrixScreen.kt | Existing CI navigation; full manual acceptance pending | Preserve default Brain and routes; legacy hardcoded accessibility text remains | keyboard suite, five-language visual review | P0 / medium |
| Native graph / PARTIAL | ui/MatrixGraphScreen.kt buildTopology/NeuralScene; ui/GraphSpace.kt; database/repository/MatrixGraphRepository.kt; database/entities/MatrixNodeEntity.kt and MatrixEdgeEntity.kt | Quaternion projection exists; home topology includes UI-built module nodes, not a universal runtime projection | Preserve default renderer; add provenance projection before six-layout integration; do not label module links as observed jobs | GraphSpaceTest; identity/filter/layout/performance tests needed | P1 / high |
| Six common graph layouts / MISSING | Inspected MatrixGraphScreen and GraphSpace | No six-layout shared-source implementation verified | Typed layout geometry over stable identities; timeline needs actual event timestamps | canonical projection first; genuine screenshots and frame profiling | P1 / high |
| Smart Combo selection/admission / PARTIAL | llm/SmartComboPlanner.kt, SmartComboAdmissionController.kt, SmartComboQuotaLedger.kt, SmartComboFreeExecutor.kt | free executor wired through MatrixViewModel; existing JVM CI. Live account capacity is not established | Keep verified-free-first and no replay; process-local reservations not durable/cloud-safe | existing planner/ledger/executor tests; restart-safe journal needed | P0 / high |
| Chat/team ownership / IMPLEMENTED, defect found | llm/SmartComboSessionBroker.kt; ui/MatrixViewModel.kt | shared owner in source; cancellation-before-registration can be lost | This PR retains cancellation intent and delivers once outside lock | new broker JVM regressions; no distributed lease claimed | P0 / medium |
| Hindsight isolation / PARTIAL | memory/HindsightMemoryClient.kt, SharedProjectMemory.kt; team handoff | HTTPS/scoped consent and untrusted memory wrapping in source; live recall not tested here | Keep project/account snapshot checks; no instruction or approval transfer | existing Hindsight tests; live service credentials required for acceptance | P0 / high |
| Local checkpoints / PARTIAL, defect found | studio/ProjectContinuityStore.kt; ProjectWorkspaceStore.kt | hashes detect workspace drift; saved revision was not checked against manifest | This PR validates revision; preserve schema and corrupt evidence. Full repository/task/approval manifest still pending | existing Android continuity class plus 3 corruption regressions | P0 / medium |
| Master signposts / MISSING | shared-memory/continuity inspected | No unified two-hop project knowledge index verified | Build references over existing stores, not duplicate facts | stable source identities and bounded graph traversal | P1 / medium |
| Nightly memory integrity / MISSING | existing memory clients/cache | no bounded signpost checker or dashboard report verified | Report-only; never silently delete knowledge | WorkManager constraints and reference validator needed | P1 / medium |
| Team planning / PARTIAL | agent/teams/ProjectTeamExecutor.kt, ProjectTeamRun.kt, ProjectTeamRunStore.kt, ProjectTeamHandoff.kt | two approved text-only roles; UI/recording adapter tested, not autonomous code editing | Journal blocks/marks uncertain recovery; do not replay dispatch or grant tools from text | ProjectTeamRunTest, approval UI instrumentation | P0 / high |
| Autonomous DAG / MISSING | bounded team executor/store inspected | no general independent tool-enabled DAG/heartbeat/dead-letter engine verified | Durable scoped command contract, atomic claims, cancellation and ambiguity policy first | backend transaction and restart/lease tests | P1 / high |
| Android scheduling / PARTIAL | agent/teams/AgentTeamScheduler.kt, AgentTeamStore.kt, AgentTeamSupervisor.kt | WorkManager health/news jobs with evidence, not unlimited 24/7 inference | Keep explicit enabled vs healthy distinctions; budgets and general routines pending | supervisor/spec tests; device Doze testing | P1 / medium |
| Cloud Pulse / UNVERIFIED | docs/cloud-agent-team-supervisor.md; ops/cloud-agent/; studio/connections/CloudTeamHealthClient.kt | D1/Cron monitor source/tests, no live deployment verified here | No Linux/VPS claim; cloud team UI and execution backend acceptance pending | real owner-selected account/token/Cron reports | P1 / high |
| Work and GitHub / PARTIAL | studio/ProjectWorkspaceStore.kt; studio/connections/DeveloperServiceClient.kt; GithubPullRequestPolicy.kt; ui/ProjectStudioScreen.kt | local edit/diff/checkpoint and scoped branch/file/PR code; account operations not tested live | preserve feature branch checks, snapshot approval, conflict handling; full staging/rollback backend verification needed | developer connection/UI tests; real authorized test repo | P1 / high |
| Cloudflare development / UNVERIFIED | studio/connections/CloudflareDeploymentPolicy.kt; docs/cloudflare-staged-deployment.md | staged policy/source exists; real deploy not performed | scoped credential separation, preview/current-snapshot check and explicit approval | deployment policy tests plus approved staging account | P1 / high |
| Pine Studio / PARTIAL | ui/PineStudioScreen.kt; approved UI contract | native draft/code/version/export and chart UI; full runtime acceptance not re-captured | many hardcoded English strings remain; no native Pine compiler/backtester claim | five-language visual tests; real market fetch tests | P1 / medium |
| Phone host / UNVERIFIED | studio/phonehost/PhoneHostPolicy.kt; PhoneOnDeviceApkBuilder.kt; PhoneRuntimeCoordinator.kt; docs/physical-phone-build-acceptance.md | CI archive/extraction coverage; no physical ARM64 successful build verified | retain loopback-only host, trusted-source approval and no production secrets in guest | physical toolchain build/export/checksum test device required | P1 / high |
| Terminal / PARTIAL | terminal/TerminalCommandPolicy.kt; studio/phonehost/PhoneLinuxSession.kt | bounded approved command process source; physical runtime unverified | PRoot is userspace, not hostile-code isolation | policy tests and physical cancel/recovery | P1 / high |
| Existing other modules / UNVERIFIED for this audit | MatrixScreen retains News/Signals/Vision/API/agents/terminal navigation; docs and existing tests | preserving routes does not prove live data/model/device behavior | no replacement or destructive migration in this PR | module-specific live/visual acceptance remains open | P1 / medium |
| Localization/accessibility / PARTIAL | resources + workflow locale checker; graph localization in source | resource checks pass baseline; modified broker/store have no new rendered UI text | existing resource presence does not prove five-language rendering; Pine/legacy gaps remain | RTL, TalkBack, font scaling and IME visual acceptance | P0 / medium |
| Release/upgrade / PARTIAL | .github/workflows/android-release.yml | baseline permanent-key in-place upgrade job passed | PR cannot publish; no code/signing bump. Seeded Room/preferences upgrade-content assertions still needed | certificate/UID/launch checks exist; data-content assertions needed | P0 / high |

## Conflicting state/documentation
- Approved UI contract still names internal 10028; actual audited build is 10029. Its visual
  requirements are retained; this PR corrects only the stale baseline number.
- docs/smart-combo-core-architecture.md includes early isolated-design prose and later
  build10009 activation notes. Treat current executor + ViewModel as evidence for integration.
- docs/phase-1-audit-and-migration-map.md describes an old V1.0.3-era inventory and version
  migration; it is historical, not current release authority.
- Room graph and UI-built home topology are distinct. Do not migrate/delete either until
  source adapters and ownership are explicit.

## Scope and security assessment
Two incremental fixes, no substantial storage migration or new execution privileges.
Cancellation is process-local and once-per-owner; callbacks run outside the synchronized
monitor. Manifest consistency is checked before verified continuity is returned.
The hash is not an authentication signature, and does not validate generated prose.
No credentials, cloud resources, Room data, preferences or release identity changed.
No new screen, visual proportions, animations, rendering framework or remote calls.

## Verification and remaining evidence
Independent security and QA source review of the first code commit found no blocking defects.
The new integrity error is localized through the existing saved app-language mechanism.
New tests: six broker JVM cases, three Android checkpoint corruption cases and a five-language error-selection case in the existing
credential/history suite. Both normal emulator suites remain mandatory. Tests are NOT_RUN
until the feature head's CI reports success. Independent security/QA source reviewers
identified these defects and will review the concrete patch. No owner acceptance is implied.
No new UI screenshot or performance measurements: UI/rendering unchanged.
No physical ARM64 result, live model inference, live memory server, deployment or graph
60 FPS claim. New UI milestones must capture the 14 requested real application views.

## Next authorized engineering work
1. Resolve this PR's checks and source review; owner merge remains separate.
2. Add seeded Room/preferences upgrade-data assertions.
3. Specify canonical read-only graph/memory signpost projection with explicit source freshness.
4. Define durable request/uncertainty journal before restart-safe Smart Combo handoffs.
5. Implement Memory Matrix indexing/integrity, then Agent/Pulse contracts and six layouts.
See maps-progress.json for machine-readable continuation and incomplete milestones.
