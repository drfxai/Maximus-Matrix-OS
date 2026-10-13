"""Maximus Remote Runtime discovery gateway (Python 3.11+, standard library).

No arbitrary code, project uploads, Gradle builds, or credentials are accepted.
Bind only to the local loopback interface; expose via an authenticated HTTPS reverse
proxy or private tunnel with a trusted certificate, never an unauthenticated port.
"""
from __future__ import annotations

import argparse
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


def make_handler(token: str, worker_id: str):
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
            if self.path != "/v1/capabilities":
                self._reply(404, {"error": "NOT_FOUND"})
                return
            if not self._authorized():
                self._reply(401, {"error": "UNAUTHORIZED"})
                return
            self._reply(200, {
                "protocol": "maximus-runtime",
                "api_version": API_VERSION,
                "worker_id": worker_id,
                "platform": worker_platform,
                "connection": "ready",
                "execution_enabled": False,
                "supported_operations": ["capabilities.read"],
                "message": "Authenticated discovery only. Isolated project execution is not configured.",
            })

        def do_POST(self) -> None:
            self._reply(405, {"error": "EXECUTION_NOT_CONFIGURED"})

        def do_PUT(self) -> None:
            self._reply(405, {"error": "EXECUTION_NOT_CONFIGURED"})

        def do_DELETE(self) -> None:
            self._reply(405, {"error": "EXECUTION_NOT_CONFIGURED"})

    return WorkerHandler


def make_server(host: str, port: int, token: str) -> ThreadingHTTPServer:
    validate_configuration(host, token)
    if not 0 <= port <= 65535:
        raise ValueError("Port out of range.")
    return ThreadingHTTPServer((host, port), make_handler(token, secrets.token_hex(16)))


def main() -> None:
    parser = argparse.ArgumentParser(description="Maximus secure discovery-only runtime gateway")
    parser.add_argument("--host", default="127.0.0.1", help="Loopback IP only")
    parser.add_argument("--port", type=int, default=8767)
    args = parser.parse_args()
    token = os.environ.get(TOKEN_ENV, "")
    try:
        server = make_server(args.host, args.port, token)
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
