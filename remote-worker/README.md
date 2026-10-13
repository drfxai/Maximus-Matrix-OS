# Maximus Remote Runtime — authenticated discovery gateway

## Phase B0: real connectivity, not remote build execution

This module is a minimal Python 3.11+ runtime gateway for Linux/Windows desktops. It exposes only authenticated discovery at GET /v1/capabilities and rejects all mutating methods. The Android Project Studio validates a verified HTTPS connection to a manually configured Worker, displays its platform/ID, and stores the token encrypted with Android Keystore.

No user files, API provider keys, Gradle commands, model prompts, or tasks are uploaded. **No remote execution, Docker sandbox, signing, or cloud build pipeline has been installed.** The main application's privileged GitHub Actions signing workflow must never execute user-supplied source.

## Start on PC/server

1. Install Python 3.11 or newer.
2. Generate an independent random token using: python -c "import secrets; print(secrets.token_urlsafe(48))".
3. Store it privately outside your repository; never embed it in the APK.
4. Set MAXIMUS_WORKER_TOKEN through a secure environment/secret facility. Run: python remote-worker/worker.py --host 127.0.0.1 --port 8767.
5. Terminate TLS at a trusted HTTPS reverse proxy or authenticated private tunnel. Forward only /v1/capabilities to http://127.0.0.1:8767. Restrict network access, protect with authentication and rate limits, and avoid logging authorization headers. Never disable TLS certificate verification.
6. In Android Project Studio -> Remote Runtime, enter the HTTPS origin and Worker token. Tap Test & Save. Certificate failures and redirects are rejected. A token can only be reused for exactly the same saved endpoint.

For LAN deployments, configure trusted private DNS/TLS certificates or a suitably authenticated tunnel. Direct HTTP to a LAN IP is intentionally unsupported.

## Protocol v1

Authenticated request: GET /v1/capabilities, Authorization: Bearer <worker-specific-secret>

Response fields: protocol=maximus-runtime, api_version=1, worker_id=<generated>, platform=linux/windows/darwin/unknown, connection=ready, execution_enabled=false, supported_operations=[capabilities.read].

There is no /jobs execution endpoint. POST, PUT, and DELETE return HTTP 405, unauthenticated capabilities returns 401, and the service refuses non-loopback binding.

## Tests

Run: python -m unittest discover -s remote-worker/tests -t remote-worker -v

Server tests reject untrusted tokens, commands and unsafe bind addresses. Android tests cover strict HTTPS endpoint policy, protocol validation, credential encryption, endpoint binding and revocation.

## Next isolated execution milestone (NOT implemented)

- Scoped approvals per device/project, signed ZIP transfer with strict archive validation, immutable SHA256 content references.
- Short-lived locked-down VMs/containers with CPU, memory, network and time limits and audited dependency acquisition; not untrusted PRoot or bare subprocesses.
- Durable job IDs, logs, cancellation, confirmation gates, trusted Gradle/Emulator output, and independently signed artifacts.
- No Maximus app release signing secret reuse; separate opt-in signing for user projects.
- Real Windows/Linux integration tests before enabling execution.
