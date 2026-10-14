"""Maximus Remote Runtime discovery gateway (Python 3.11+, standard library).

No arbitrary code, project uploads, Gradle builds, or credentials are accepted.
Bind only to the local loopback interface; expose via an authenticated HTTPS reverse
proxy or private tunnel with a trusted certificate, never an unauthenticated port.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import ipaddress
import json
import os
import platform
import re
import secrets
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

API_VERSION = 1
TOKEN_ENV = "MAXIMUS_WORKER_TOKEN"
MAX_TOKEN_CHARS = 4096


def validate_configuration(host: str, token: str) -> None:
    try:
        require_loopback = ipaddress.ip_address(host).is_loopback
    except ValueError as exc:
        raise ValueError("Bind to a literal loopback IP only.") from exc
    if not require_loopback:
        raise ValueError("Remote binding is disabled. Put an HTTPS proxy in front of loopback.")
    if not 32 <= len(token) <= MAX_TOKEN_CHARS:
        raise ValueError(f"{TOKEN_ENV} must contain a strong 32+ character bearer token.")
    if "\n" in token or "\r" in token:
        raise ValueError("Bearer token cannot contain line breaks.")


def make_handler(token: str, worker_id: str, coordinator=None):
    worker_platform = platform.system().lower()
    if worker_platform not in {"windows", "linux", "darwin"}:
        worker_platform = "unknown"

    class WorkerHandler(BaseHTTPRequestHandler):
        server_version = "MaximusWorker/1"
        sys_version = ""

        def log_message(self, fmt: str, *args) -> None:
            # Never log authorization headers or credentials.
            pass

        def _reply(self, code: int, obj: dict) -> None:
            content = json.dumps(obj, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(content)))
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(content)

        def _authorized(self) -> bool:
            given = self.headers.get("Authorization", "")
            expected = "Bearer " + token
            return len(given) <= MAX_TOKEN_CHARS + 7 and hmac.compare_digest(given, expected)

        def do_GET(self) -> None:
            if not self._authorized():
                self._reply(401, {"error": "UNAUTHORIZED"})
                return
            if self.path.startswith("/v1/jobs/"):
                if coordinator is None:
                    self._reply(404, {"error": "EXECUTION_NOT_CONFIGURED"})
                    return
                parts = self.path.split("/")
                if len(parts) not in (4, 5) or not re.fullmatch(r"[a-f0-9]{32}", parts[3]):
                    self._reply(404, {"error": "NOT_FOUND"})
                    return
                if len(parts) == 5 and parts[4] == "artifact":
                    try:
                        data = coordinator.artifact(parts[3])
                    except Exception:
                        self._reply(500, {"error": "ARTIFACT_INVALID"})
                        return
                    if data is None:
                        self._reply(404, {"error": "ARTIFACT_NOT_READY"})
                        return
                    self.send_response(200)
                    self.send_header("Content-Type", "application/vnd.android.package-archive")
                    self.send_header("Content-Disposition", 'attachment; filename="project-debug.apk"')
                    self.send_header("X-Content-Type-Options", "nosniff")
                    self.send_header("Cache-Control", "no-store")
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                    return
                if len(parts) == 4:
                    status = coordinator.status(parts[3])
                    self._reply(200, status) if status else self._reply(404, {"error": "NOT_FOUND"})
                    return
                self._reply(404, {"error": "NOT_FOUND"})
                return
            if self.path != "/v1/capabilities":
                self._reply(404, {"error": "NOT_FOUND"})
                return
            self._reply(200, {
                "protocol": "maximus-runtime",
                "api_version": API_VERSION,
                "worker_id": worker_id,
                "platform": worker_platform,
                "connection": "ready",
                "execution_enabled": coordinator is not None,
                "supported_operations": ["capabilities.read"] +
                    (["jobs.submit", "jobs.status", "jobs.artifact"] if coordinator else []),
                "message": "Debug builds require explicit approval and a rootless offline sandbox." if coordinator
                    else "Authenticated discovery only. Isolated project execution is not configured.",
            })

        def do_POST(self) -> None:
            if self.path != "/v1/jobs" or coordinator is None:
                self._reply(405, {"error": "EXECUTION_NOT_CONFIGURED"})
                return
            if not self._authorized():
                self._reply(401, {"error": "UNAUTHORIZED"})
                return
            if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json":
                self._reply(415, {"error": "JSON_REQUIRED"})
                return
            try:
                length = int(self.headers.get("Content-Length", "-1"))
                if not 0 < length <= 12 * 1024 * 1024:
                    self._reply(413, {"error": "REQUEST_TOO_LARGE"})
                    return
                payload = json.loads(self.rfile.read(length))
                if type(payload) is not dict:
                    raise ValueError("Invalid request.")
                archive = base64.b64decode(payload["zip_base64"], validate=True)
                result = coordinator.submit(
                    payload["project_id"], payload["request_id"],
                    payload["source_sha256"], archive, payload.get("approved") is True
                )
            except (ValueError, KeyError, TypeError) as exc:
                self._reply(400, {"error": "INVALID_PROJECT_REQUEST"})
                return
            except RuntimeError:
                self._reply(429, {"error": "WORKER_UNAVAILABLE"})
                return
            self._reply(202, result)

        def do_PUT(self) -> None:
            self._reply(405, {"error": "EXECUTION_NOT_CONFIGURED"})

        def do_DELETE(self) -> None:
            self._reply(405, {"error": "EXECUTION_NOT_CONFIGURED"})

    return WorkerHandler


def make_server(host: str, port: int, token: str, coordinator=None) -> ThreadingHTTPServer:
    validate_configuration(host, token)
    if not 0 <= port <= 65535:
        raise ValueError("Port out of range.")
    return ThreadingHTTPServer((host, port), make_handler(token, secrets.token_hex(16), coordinator))


def main() -> None:
    parser = argparse.ArgumentParser(description="Maximus secure discovery-only runtime gateway")
    parser.add_argument("--host", default="127.0.0.1", help="Loopback IP only")
    parser.add_argument("--port", type=int, default=8767)
    args = parser.parse_args()
    token = os.environ.get(TOKEN_ENV, "")
    builder_image = os.environ.get("MAXIMUS_BUILDER_IMAGE", "")
    try:
        coordinator = None
        if builder_image:
            from build_jobs import BuildCoordinator, RootlessBuilder
            coordinator = BuildCoordinator(RootlessBuilder(builder_image))
        server = make_server(args.host, args.port, token, coordinator)
    except ValueError as error:
        parser.error(str(error))
    print(f"Maximus discovery gateway listening on {args.host}:{server.server_port}", flush=True)
    try:
        server.serve_forever(poll_interval=0.2)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
