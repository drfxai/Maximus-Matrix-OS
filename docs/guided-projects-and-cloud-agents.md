# Guided projects and cloud agents

Product direction, 2026-10-15. Public Android version remains **V1.0.0**.

## User journey

Connect a service → select a repository or account → describe the goal → review
the proposed plan → approve a specific change → inspect real results.

The main screen should show the next useful action and a short explanation.
Advanced fields belong behind an expandable control. Defaults are suggestions,
not permission to spend money, publish code or change an existing deployment.
Keep a manual path available when account discovery is not allowed by a scoped
token. A saved key, verified key, uploaded script and healthy running agent are
different states and must have different labels.

## Delivery backlog

| Priority | Work | Current evidence / remaining acceptance |
|---|---|---|
| P0 | Guided GitHub connection | Encrypted tokens, confirmed branch/file operations, repository-link parsing and progressive controls exist. The mobile draft-PR flow adds actual default-branch discovery, bounded change preview, stale-preview checks and explicit submission. Full repository picker, OAuth and independent review/merge workflow remain. |
| P0 | Guided Cloudflare key setup | Official permission-prefilled dashboard URL. The user completes account selection and token creation in Cloudflare. No credential capable of minting arbitrary tokens is requested. |
| P0 | Account and Worker setup | Account discovery with manual ID fallback; role-based names, source preview and explicit create/replace confirmation. Real account permission and upload acceptance require authorized staging validation. |
| P0 | Worker chat starters | Assistant, research and project-planning prompts use a Workers AI binding, separate access secret and bounded requests. These are stateless text assistants; research does not imply live web search. |
| P0 | Reliable Android CI | Keep real keyboard reopen and credential/history assertions. Current-head unit/lint/build and both emulator suites must pass; distinguish transport loss from proven assertions. |
| P0 | Persian voice and five locales | Resource parity and selected panel migration exist. Complete remaining UI, real Persian STT/TTS, interruptions and physical-device acceptance under #36/#35. |
| P0 | Conversational Brain | Preserve confirmation/cancel and exact-command routing. Finish provider/device integration, conversation continuity and real-device action acceptance under #40. |
| P0 | Smart Combo | Verify provider/model prices and quotas, accurate used/remaining accounting, free-first routing and checkpointed failover. Unknown quota must remain unknown. |
| P1 | Automatic project execution | Goal → bounded task plan → isolated branch → scoped coding worker → tests → reviewable PR. Debug-build requests now have durable identity/restart reconciliation and manual mobile draft PR creation is available in source. A complete coding-worker queue, task leases, budgets, stop/revoke and independent review remain under #38. |
| P1 | Intelligent Worker provisioning | Choose bindings from actual task needs, preview changes, staged versions, route activation, health checks, logs and rollback. Stateful teams, queues and schedules need durable infrastructure; uploading a script does not provide them. |
| P1 | Shared Hindsight | Connect approved workers and models to redacted task checkpoints, evidence and artifacts. Verify cross-model recovery without exposing credentials or private conversations. |
| P1 | Local/private execution | Verify GGUF/LiteRT inference, signed model downloads, resource checks and authenticated private PC model connections under #22/#19. |
| P1 | Physical ARM64 Mobile Harness | Build, export and install a real APK on a physical phone, record hashes, ABI and data-preserving upgrade evidence under #24. |
| P1 | Permission-scoped vision/actions | Camera and optional screen analysis with per-session consent and visible cancellation under #39. |
| P1 | App-wide visual polish | Consistent spacing, typography, surfaces, status chips, RTL, large text, touch targets and short state transitions. Verify actual screens across narrow devices and all five locales. |
| P0 release gate | Signed release update | Keep the permanent certificate and public 1.0.0; verify internal-code upgrade, install/data preservation, hashes and complete CI before publication. |

## Connection and deployment controls

- Start with the official Cloudflare Workers Scripts token template. Ask for a
  specific account scope in the dashboard. Account discovery may need additional
  account-read permission; manual account ID entry must work without it.
- Treat service API credentials and Worker invocation secrets separately. They
  never belong in generated source, prompts, feedback, screenshots or logs.
- A Worker upload targets the exact account and name shown in its approval
  screen. Existing code/bindings may be replaced at that name. Suggest a unique
  name, retain source preview, and do not configure routes or domains implicitly.
  Existing routes can immediately serve replacement code; an upload is not a
  guaranteed private preview. The approval screen must explain this distinction.
- Workers AI usage depends on the account plan and available allowance. Do not
  promise free usage or available quota; creating the script does not invoke AI.
- The starter accepts authenticated chat requests, limits input and output, and
  cannot modify GitHub, deploy other Workers or execute Android actions.
- GitHub file writes check the repository's real default and protected branch
  metadata, then use the loaded blob SHA for optimistic concurrency.
- API errors must be bounded and must not display response bodies or tokens.
  Preserve a clear retry path and never report an uncertain write as confirmed.

## Visual and interaction rules

Use Material surfaces, a clear primary action, concise localized help, status
icons with text, and expandable advanced settings. Prefer wrapping/vertical
controls over crowded button rows. Use brief content-size and visibility
transitions; make loading, success and failure visible without relying on color
or vibration alone. Follow system animation settings and keep text selectable
where users need to copy an identifier. Avoid continuous decorative animation.

The cloud setup panel is the first implementation of this direction. A complete
application redesign and a production autonomous project worker remain separate
acceptance items, not claims implied by the new interface.

## References

- [Cloudflare API token template URLs](https://developers.cloudflare.com/fundamentals/api/how-to/account-owned-token-template/)
- [Token creation through API and its initial privileged credential](https://developers.cloudflare.com/fundamentals/api/how-to/create-via-api/)
- [Worker multipart metadata](https://developers.cloudflare.com/workers/configuration/multipart-upload-metadata/)
- [Workers best practices](https://developers.cloudflare.com/workers/best-practices/workers-best-practices/)
- [Workers AI model catalog](https://developers.cloudflare.com/workers-ai/models/)

## Verification boundary

Local source/policy/runtime tests and Android CI are distinct from live account
deployment and physical-device acceptance. No live Cloudflare resource is
created while authoring this change. Deployment requires the app user's scoped
credentials and explicit target confirmation. Independent engineering review
does not substitute for those runtime checks.
