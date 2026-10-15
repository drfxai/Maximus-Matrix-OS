# Cloud Agent Team Supervisor — opt-in D1 + Cron

This is a narrow, production-oriented cloud **evidence monitor** for Maximus Matrix OS. It does not grant agents arbitrary execution, paid LLM access, GitHub write permissions, or Android privileges.

## Supported behavior
- User-authenticated `POST /v1/teams` registers at most eight cloud jobs of type `GITHUB_RELEASES` for a validated public `owner/repo`. Allowed cadence: 15, 180, or 1440 minutes.
- `GET /v1/teams` and `GET /v1/reports` provide authenticated team and latest evidence states.
- Cloudflare Cron every 15 minutes invokes `scheduled()`. A D1 uniqueness claim per team and schedule window avoids duplicate dispatch.
- Collector accesses only `https://api.github.com/repos/{owner}/{repo}/releases/latest` with redirect disabled. Validator accepts only the expected GitHub release URL. Reporter persists bounded evidence, and records UNAVAILABLE when source evidence is absent.
- Reports are pruned after 30 days. No prompts, private code, user credentials, or arbitrary URLs are stored.

This is not an always-running Linux process. Cloudflare Cron is periodic, and an active deployment (and available account quota) is required. The mobile Android WorkManager workers remain best-effort and independent.

## Deployment gate

Do **not** deploy without the owner's account selection and explicit cloud deployment approval. The app must not ship `MAXIMUS_AGENT_TOKEN` in its APK, source, logs, or project metadata.

1. Create a private D1 database in the selected Cloudflare account, bind it as `AGENT_DB`, and apply `ops/cloud-agent/schema.sql` before startup.
2. Configure a Worker module whose entrypoint is `ops/cloud-agent/agent-team-supervisor.mjs`, with a Cron trigger `*/15 * * * *` (UTC).
3. Add a distinct, randomly generated >=32-character bearer secret through Cloudflare secrets as `MAXIMUS_AGENT_TOKEN`. Never commit the value.
4. Restrict the Worker endpoint through your organization's network / access policy as appropriate. The bearer token is still mandatory for all HTTP reads and writes.
5. Verify `node --test ops/cloud-agent/tests/agent-team-supervisor.test.mjs`; test an authenticated registration, wait for a Cron report, and confirm a duplicate invocation does not repeat the slot.
6. Independently verify quota, monthly cost, availability, and persistence before claiming production-grade 24/7 reliability.

## Remaining work

- Android UI needs an explicit user-authorized Cloud Agent endpoint/token configuration with encrypted, scoped credentials. Existing local Agent Teams must **not** be automatically mirrored to a cloud account.
- Cloud provider D1 deployment must be verified with a real account, not just a unit-test fake.
- Expand the *allowlist* with signed, human-approved task definitions, coordination and per-project isolated Hindsight handoff, role budgets, cancellation and durable job execution only after security review.
- Real physical ARM64 build acceptance uses `.github/scripts/verify-phone-physical.sh` from a trusted developer machine with an explicitly connected Android 9+ ARM64 device. This cannot be claimed by x86 GitHub Actions.
