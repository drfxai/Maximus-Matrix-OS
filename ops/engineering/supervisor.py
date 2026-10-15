#!/usr/bin/env python3
"""Read-only, deterministic engineering supervisor for MAXIMUS MATRIX OS.

This is an auditable control plane, not an untrusted LLM with GitHub write
permissions. It reports real PR/check/release evidence and work ownership.
It NEVER reviews, merges, deploys, publishes or changes repository state.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

# Keep direct-script and importlib-based unit test entrypoints identical.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from assignment_board import build_board, render_board, validate_orders


def validate_config(config: dict[str, Any]) -> None:
    if config.get("schema_version") != 1:
        raise ValueError("Unsupported engineering team manifest")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", config.get("repository", "")):
        raise ValueError("Invalid repository")
    policy = config["control_plane"]
    if any(policy.get(flag) is not False for flag in (
        "allow_auto_merge", "allow_auto_publish", "allow_unapproved_cloud_deploy",
        "allow_cross_project_secrets"
    )):
        raise ValueError("Unsafe autonomous production permissions in manifest")
    if not policy.get("requires_signed_upgrade") or not policy.get("requires_manual_release_approval"):
        raise ValueError("Missing release approval/identity gate")
    if config.get("public_version") != "1.0.0":
        raise ValueError("Do not change public version without owner authorization")
    roles = config["roles"]
    ids = [role["id"] for role in roles]
    if len(ids) < 2 or len(ids) != len(set(ids)) or "principal-supervisor" not in ids:
        raise ValueError("Expected unique role IDs and one principal supervisor")
    for role in roles:
        if role.get("may_approve_own_work") is not False:
            raise ValueError("Self-approval is prohibited")
        if not role.get("paths") or not role.get("issue_numbers"):
            raise ValueError("Every role needs a bounded scope and issue references")


class GitHubReadClient:
    def __init__(self, repo: str, token: str = ""):
        self.repo = repo
        self.token = token

    def get(self, path: str) -> Any:
        if not path.startswith("/") or path.startswith("//"):
            raise ValueError("Only absolute GitHub API paths are accepted")
        endpoint = "https://api.github.com/repos/" + self.repo + path
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "MatrixEngineeringSupervisor/1.0",
        }
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        request = urllib.request.Request(endpoint, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                if response.status != 200:
                    raise RuntimeError("GitHub returned HTTP " + str(response.status))
                if int(response.headers.get("Content-Length", "0")) > 3_000_000:
                    raise ValueError("Unexpected oversized GitHub response")
                payload = response.read(3_000_001)
                if len(payload) > 3_000_000:
                    raise ValueError("Unexpected oversized GitHub response")
                return json.loads(payload)
        except urllib.error.HTTPError as ex:
            # Never include API error bodies: some may contain private URLs.
            raise RuntimeError("GitHub metadata request failed (HTTP " + str(ex.code) + ")") from None


def fetch_snapshot(client: GitHubReadClient, pr_limit: int = 12) -> dict[str, Any]:
    head = client.get("/branches/main")
    release = client.get("/releases/latest")
    prs = client.get("/pulls?state=open&per_page=50")
    issues = client.get("/issues?state=open&per_page=100")
    runs = client.get("/actions/runs?per_page=25")
    checks: dict[str, list[dict[str, Any]]] = {}
    for pr in prs[:pr_limit]:
        sha = pr.get("head", {}).get("sha", "")
        if not re.fullmatch(r"[a-f0-9]{40}", sha):
            continue
        try:
            result = client.get("/commits/" + sha + "/check-runs?per_page=100")
            checks[sha] = result.get("check_runs", [])
        except RuntimeError:
            checks[sha] = []  # UNKNOWN, never treated as success
    return {
        "main_sha": head["commit"]["sha"],
        "release": release,
        "prs": prs,
        "issues": [item for item in issues if "pull_request" not in item],
        "runs": runs.get("workflow_runs", []),
        "checks": checks,
    }


def evaluate_pr(pr: dict[str, Any], observed: list[dict[str, Any]],
                required_names: list[str]) -> dict[str, Any]:
    sha = pr.get("head", {}).get("sha", "")
    names: dict[str, list[str | None]] = {}
    for check in observed:
        if check.get("head_sha") not in (sha, None):
            continue
        names.setdefault(check.get("name", ""), []).append(check.get("conclusion"))
    failing = [name for name, values in names.items()
               if any(value in ("failure", "timed_out", "cancelled", "action_required")
                      for value in values)]
    required = {
        prefix: [result for name, values in names.items()
                 if name.startswith(prefix) for result in values]
        for prefix in required_names
    }
    missing = [prefix for prefix, results in required.items()
               if not results or "success" not in results]
    if failing:
        status = "BLOCKED_CI"
    elif missing:
        status = "AWAITING_CHECKS"
    elif pr.get("draft", False):
        status = "DRAFT_READY_FOR_REVIEW"
    else:
        status = "CHECKS_GREEN_AWAITING_INDEPENDENT_REVIEW"
    return {
        "number": pr.get("number"),
        "title": str(pr.get("title", ""))[:140],
        "head_sha": sha,
        "status": status,
        "failed_checks": sorted(failing)[:15],
        "unconfirmed_required_checks": missing,
        "draft": bool(pr.get("draft", False)),
        "url": str(pr.get("html_url", ""))[:250],
    }


def _release_internal_version(release: dict[str, Any]) -> int | None:
    body = release.get("body") or ""
    m = re.search(r"internal version code\s*:\s*(\d+)", body, re.IGNORECASE)
    return int(m.group(1)) if m else None


def summarize(manifest: dict[str, Any], snapshot: dict[str, Any],
              now: dt.datetime, checkout_gradle: str = "") -> dict[str, Any]:
    validate_config(manifest)
    expected = manifest["control_plane"]["required_check_names"]
    prs = [evaluate_pr(p, snapshot.get("checks", {}).get(
        p.get("head", {}).get("sha", ""), []), expected)
        for p in snapshot["prs"]]

    runs = snapshot.get("runs", [])
    current = [r for r in runs if r.get("head_sha") == snapshot["main_sha"]
               and r.get("head_branch") == "main"]
    main_ci = next((r.get("conclusion") or "IN_PROGRESS" for r in current), "UNKNOWN")
    release = snapshot.get("release", {})
    release_version = _release_internal_version(release)
    gradle_match = re.search(r"versionCode\s*=\s*(\d+)", checkout_gradle)
    source_version = int(gradle_match.group(1)) if gradle_match else None
    public_match = re.search(r'versionName\s*=\s*"([^"]+)"', checkout_gradle)
    public_app_version = public_match.group(1) if public_match else None

    issues_by_number = {i["number"]: i for i in snapshot.get("issues", [])}
    role_summary = []
    for role in manifest["roles"]:
        open_issues = [n for n in role["issue_numbers"] if n in issues_by_number]
        role_summary.append({
            "id": role["id"],
            "label": role["label"],
            "open_issues": open_issues,
            "scope": role["paths"],
            # Planned role, not evidence an autonomous coder is online.
            "execution": "UNASSIGNED_RUNTIME",
        })
    warnings = []
    if public_app_version is not None and public_app_version != manifest["public_version"]:
        warnings.append("PUBLIC_VERSION_CHANGED")
    if main_ci != "success":
        warnings.append("MAIN_CI_NOT_VERIFIED_GREEN")
    if any(p["status"] == "BLOCKED_CI" for p in prs):
        warnings.append("OPEN_PULL_REQUEST_WITH_FAILED_CHECKS")
    if source_version is not None and release_version is not None:
        if source_version > release_version:
            warnings.append("MAIN_BUILD_NOT_PUBLISHED")
        if source_version < release_version:
            warnings.append("RELEASE_AHEAD_OF_CHECKOUT")
    if release.get("prerelease") or release.get("draft"):
        warnings.append("RELEASE_NOT_STABLE")
    if not release.get("assets"):
        warnings.append("NO_VERIFIED_RELEASE_ASSETS")

    return {
        "schema_version": 1,
        "generated_at_utc": now.astimezone(dt.timezone.utc).isoformat(),
        "repository": manifest["repository"],
        "main_sha": snapshot["main_sha"],
        "main_ci": main_ci,
        "release": {
            "tag": release.get("tag_name", ""),
            "published_internal_version": release_version,
            "checkout_internal_version": source_version,
            "public_version": public_app_version,
            "asset_count": len(release.get("assets", [])),
        },
        "open_prs": prs,
        "roles": role_summary,
        "warnings": sorted(set(warnings)),
        "authorization": {
            "automatic_merge": False,
            "automatic_release": False,
            "automatic_cloud_deploy": False,
            "self_review": False,
        },
    }


def render_markdown(report: dict[str, Any]) -> str:
    lines = [
        "# Engineering Supervisor — read-only audit",
        "",
        "- Repository: `" + report["repository"] + "`",
        "- Main SHA: `" + report["main_sha"][:12] + "`",
        "- Latest main CI: **" + report["main_ci"] + "**",
        "- Published build: **" +
        str(report["release"]["published_internal_version"] or "UNKNOWN") + "**",
        "- Checkout build: **" +
        str(report["release"]["checkout_internal_version"] or "UNKNOWN") + "**",
        "",
        "## Warnings",
    ]
    lines.extend("- " + item for item in report["warnings"])
    if not report["warnings"]:
        lines.append("- None from available metadata; physical-device tests are separate.")
    lines += ["", "## Open pull requests"]
    for pr in report["open_prs"][:30]:
        lines.append("- #" + str(pr["number"]) + " " + pr["status"] + " — " +
                     pr["title"].replace("\n", " ") +
                     (" | failed: " + ", ".join(pr["failed_checks"])
                      if pr["failed_checks"] else ""))
    if not report["open_prs"]:
        lines.append("- No open pull requests.")
    lines += ["", "## Workstreams (not proof of live agents)"]
    for role in report["roles"]:
        lines.append("- " + role["label"] + ": open issues " +
                     ", ".join("#" + str(n) for n in role["open_issues"]) +
                     " — " + role["execution"])
    lines += ["", "The supervisor does not merge, approve, deploy, publish, grant permissions, "
             "or assert that a model is executing. Release signing and independent review "
             "remain mandatory.", ""]
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", default="ops/engineering/team.json")
    ap.add_argument("--gradle", default="app/build.gradle.kts")
    ap.add_argument("--output-dir", default="engineering-audit")
    ap.add_argument("--work-orders", default="ops/engineering/work_orders.json")
    args = ap.parse_args()
    config = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    orders = json.loads(Path(args.work_orders).read_text(encoding="utf-8"))
    validate_config(config)
    validate_orders(orders, config)
    checkout_gradle = Path(args.gradle).read_text(encoding="utf-8")
    snapshot = fetch_snapshot(GitHubReadClient(config["repository"], os.getenv("GITHUB_TOKEN", "")))
    now = dt.datetime.now(dt.timezone.utc)
    report = summarize(config, snapshot, now, checkout_gradle)
    board = build_board(orders, config, snapshot, report["open_prs"], now)
    # This is a planning board, not evidence of independently deployed coding agents.
    report["assignment_board"] = board
    path = Path(args.output_dir)
    path.mkdir(parents=True, exist_ok=True)
    (path / "status.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                                      encoding="utf-8")
    (path / "assignment-board.json").write_text(json.dumps(board, indent=2, ensure_ascii=False) + "\n",
                                                encoding="utf-8")
    summary = render_markdown(report)
    board_text = render_board(board)
    (path / "summary.md").write_text(summary, encoding="utf-8")
    (path / "assignment-board.md").write_text(board_text, encoding="utf-8")
    print(summary + "\n" + board_text)
    github_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if github_summary:
        with open(github_summary, "a", encoding="utf-8") as f:
            f.write(summary + "\n" + board_text)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, KeyError, OSError, RuntimeError) as exc:
        # Do not print API payloads, headers or configured secrets.
        print("Engineering supervisor unavailable: " + str(exc), file=sys.stderr)
        raise SystemExit(2)
