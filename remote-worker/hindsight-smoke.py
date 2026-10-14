#!/usr/bin/env python3
"""User-authorized Hindsight Retain/Recall integration smoke test.

Requires HINDSIGHT_TEST_ENDPOINT and HINDSIGHT_TEST_TOKEN from a secure
environment. Creates two fresh disposable banks, tests cross-model continuity
and isolation, then deletes only those banks. Never uses production project IDs.
"""
from __future__ import annotations

import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.request
import uuid
from urllib.parse import urlsplit

MAX_RESPONSE = 128_000


def checked_origin(raw: str) -> str:
    parts = urlsplit(raw)
    if (parts.scheme != "https" or not parts.hostname or parts.username or
        parts.password or parts.query or parts.fragment or
        parts.path not in ("", "/") or parts.port not in (None, 443, 8443)):
        raise ValueError("Use a clean authenticated HTTPS origin.")
    return raw.rstrip("/")


def send(origin: str, token: str, method: str, bank: str, suffix: str = "",
         document: dict | None = None) -> dict:
    url = origin + "/v1/default/banks/" + bank + suffix
    body = None if document is None else json.dumps(document).encode()
    request = urllib.request.Request(
        url, data=body, method=method,
        headers={"Authorization": "Bearer " + token, "Accept": "application/json",
                 "Content-Type": "application/json"}
    )
    # urllib follows redirects by default; forbid forwarding bearer credentials.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    opener = urllib.request.build_opener(
        NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context())
    )
    with opener.open(request, timeout=20) as response:
        raw = response.read(MAX_RESPONSE + 1)
        if len(raw) > MAX_RESPONSE:
            raise RuntimeError("Oversized Hindsight API response.")
        return json.loads(raw) if raw else {}


def run() -> None:
    origin = checked_origin(os.environ["HINDSIGHT_TEST_ENDPOINT"])
    token = os.environ["HINDSIGHT_TEST_TOKEN"]
    if len(token) < 12 or any(x.isspace() for x in token):
        raise ValueError("A valid user-owned Hindsight test key is required.")
    run_id = uuid.uuid4().hex
    bank_a, bank_b = ("maximus-smoke-a-" + run_id, "maximus-smoke-b-" + run_id)
    marker = "mxsmoke" + run_id
    try:
        result = send(origin, token, "POST", bank_a, "/memories", {
            "items": [{"content": "Project Delta uses checkpoint marker " + marker +
                       ". The next action is to run a security review.",
                       "context": "test-only-project-continuity"}], "async": False
        })
        if result.get("success") is False:
            raise RuntimeError("Hindsight Retain was rejected.")
        texts = []
        for attempt in range(3):
            answer = send(origin, token, "POST", bank_a, "/memories/recall", {
                "query": "What is Project Delta's checkpoint marker and next action?",
                "max_tokens": 700
            })
            texts = [x.get("text", "") for x in answer.get("results", [])]
            if any(marker in text for text in texts):
                break
            time.sleep(2)
        if not any(marker in text for text in texts):
            raise RuntimeError("Retained checkpoint was not recalled by the second model.")
        other = send(origin, token, "POST", bank_b, "/memories/recall", {
            "query": "What is Project Delta's checkpoint marker?",
            "max_tokens": 300
        })
        if any(marker in x.get("text", "") for x in other.get("results", [])):
            raise RuntimeError("Cross-project memory isolation failed.")
        print("PASS: authenticated retain/recall across model identities; separate bank isolated.")
    finally:
        for bank in (bank_a, bank_b):
            try:
                send(origin, token, "DELETE", bank)
            except Exception:
                print("WARNING: disposable test bank cleanup failed: " + bank, file=sys.stderr)


if __name__ == "__main__":
    if "--run-live" not in sys.argv:
        raise SystemExit("Live test is opt-in. Supply --run-live and user-owned env credentials.")
    run()
