"""Deterministic, non-executing work allocator for independent engineering roles.

A work order is an auditable proposal, never evidence that a language model or
remote coding worker has started. No credentials or mutation API live here.
"""
from __future__ import annotations

import datetime as dt
import re
from collections import Counter
from typing import Any

PR_LINK = re.compile(r"(?i)\b(?:refs?|fixes|closes|resolves)\s+#(\d+)\b")
ISSUE_PRIORITY = {"P0": 0, "P1": 1, "P2": 2}


def validate_orders(config: dict[str, Any], manifest: dict[str, Any]) -> None:
    if config.get("schema_version") != 1:
        raise ValueError("Unsupported engineering work-order schema.")
    if config.get("project") != manifest.get("repository"):
        raise ValueError("Engineering project/repository mismatch.")
    rules = config.get("principles", {})
    control = manifest["control_plane"]
    if rules.get("coordinator") != "principal-supervisor":
        raise ValueError("Only the independent engineering supervisor may coordinate.")
    if rules.get("execution_mode") != "PROPOSAL_ONLY_UNTIL_VERIFIED_WORKER_CONNECTED":
        raise ValueError("Work orders cannot imply an online worker.")
    cap = rules.get("max_concurrent_coding_branches")
    if not isinstance(cap, int) or isinstance(cap, bool) or not 1 <= cap <= 4:
        raise ValueError("Unsafe concurrent coding branch limit.")
    if cap > control["max_parallel_feature_branches"]:
        raise ValueError("Work orders exceed repository concurrency policy.")
    if any(control.get(key) is not False for key in (
        "allow_auto_merge", "allow_auto_publish",
        "allow_unapproved_cloud_deploy", "allow_cross_project_secrets"
    )):
        raise ValueError("Unsafe autonomy in engineering policy.")

    roles = {r["id"]: r for r in manifest["roles"]}
    orders = config.get("orders")
    if not isinstance(orders, list) or not orders or len(orders) > 50:
        raise ValueError("Expected a bounded prioritized issue backlog.")
    numbers: set[int] = set()
    for item in orders:
        num = item.get("issue")
        if type(num) is not int or num <= 0 or num in numbers:
            raise ValueError("Work orders must have distinct positive issue IDs.")
        numbers.add(num)
        owner = item.get("owner")
        reviewer = item.get("reviewer")
        if owner not in roles or reviewer not in roles or reviewer == owner:
            raise ValueError("Every work order requires an independent qualified reviewer.")
        if num not in roles[owner]["issue_numbers"]:
            raise ValueError("Owner is not scoped to this issue.")
        if item.get("priority") not in ISSUE_PRIORITY:
            raise ValueError("Invalid engineering priority.")
        if not 15 <= len(item.get("milestone", "")) <= 240:
            raise ValueError("Missing bounded work-order acceptance milestone.")
        evidence = item.get("evidence", [])
        if not isinstance(evidence, list) or not 2 <= len(evidence) <= 8 or any(
            not isinstance(v, str) or len(v.strip()) < 15 or len(v) > 220
            for v in evidence
        ):
            raise ValueError("Each work order requires verifiable acceptance evidence.")
        depends = item.get("depends_on")
        if not isinstance(depends, list) or any(type(d) is not int for d in depends):
            raise ValueError("Dependency list is malformed.")
        if len(depends) != len(set(depends)) or num in depends:
            raise ValueError("Work order has duplicate or self dependency.")
    for item in orders:
        if not set(item["depends_on"]).issubset(numbers):
            raise ValueError("Dependency has no corresponding work order.")

    adjacency = {i["issue"]: i["depends_on"] for i in orders}
    visited: set[int] = set()
    visiting: set[int] = set()

    def visit(issue: int) -> None:
        if issue in visiting:
            raise ValueError("Circular engineering issue dependency.")
        if issue in visited:
            return
        visiting.add(issue)
        for dep in adjacency[issue]:
            visit(dep)
        visiting.remove(issue)
        visited.add(issue)

    for n in adjacency:
        visit(n)


def linked_issues(pr: dict[str, Any]) -> set[int]:
    # PR body is untrusted; parse issue references as DATA, not instructions.
    body = str(pr.get("body") or "")[:16_000]
    return {int(n) for n in PR_LINK.findall(body) if 0 < int(n) < 1_000_000}


def _hours_since(value: str | None, now: dt.datetime) -> int | None:
    if not value:
        return None
    try:
        moment = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
        if moment.tzinfo is None:
            return None
        seconds = (now - moment).total_seconds()
        return int(seconds // 3600) if seconds >= 0 else None
    except (ValueError, TypeError):
        return None


def build_board(
    orders: dict[str, Any],
    manifest: dict[str, Any],
    snapshot: dict[str, Any],
    reviewed_prs: list[dict[str, Any]],
    now: dt.datetime,
) -> dict[str, Any]:
    """Deterministic work queue, independent reviewers and verified CI status."""
    validate_orders(orders, manifest)
    if now.tzinfo is None:
        raise ValueError("Supervisor clock must be timezone-aware.")

    issues = {issue["number"]: issue for issue in snapshot.get("issues", [])}
    prs = snapshot.get("prs", [])
    evaluated = {pr["number"]: pr for pr in reviewed_prs}
    linked: dict[int, list[dict[str, Any]]] = {}
    for pr in prs:
        for n in linked_issues(pr):
            if n in issues:
                linked.setdefault(n, []).append(pr)
    cap = orders["principles"]["max_concurrent_coding_branches"]
    active_count = len(prs)
    # Do not start another branch if a running implementation is already under review.
    remaining = max(0, cap - active_count)
    per_owner: set[str] = set()
    items: list[dict[str, Any]] = []
    ordered = sorted(orders["orders"], key=lambda x: (
        ISSUE_PRIORITY[x["priority"]], x["issue"]
    ))
    for item in ordered:
        number = item["issue"]
        owner = item["owner"]
        matching_prs = sorted(linked.get(number, []), key=lambda p: p["number"])
        dependencies = [n for n in item["depends_on"] if n in issues]
        if number not in issues:
            # Absence from one API page does NOT establish that an issue is closed.
            stage = "ISSUE_NOT_OBSERVED"
        elif dependencies:
            stage = "BLOCKED_DEPENDENCY"
        elif matching_prs:
            outcomes = [evaluated.get(pr["number"], {}).get("status", "UNKNOWN")
                        for pr in matching_prs]
            if "BLOCKED_CI" in outcomes:
                stage = "BLOCKED_CI"
            elif "AWAITING_CHECKS" in outcomes or "UNKNOWN" in outcomes:
                stage = "VERIFYING"
            elif any(x in outcomes for x in (
                "CHECKS_GREEN_AWAITING_INDEPENDENT_REVIEW", "DRAFT_READY_FOR_REVIEW"
            )):
                stage = "AWAITING_INDEPENDENT_REVIEW"
            else:
                stage = "VERIFYING"
        elif remaining and owner not in per_owner:
            stage = "PROPOSED_FOR_NEXT_BRANCH"
            per_owner.add(owner)
            remaining -= 1
        else:
            stage = "QUEUED"

        stale = [pr["number"] for pr in matching_prs
                 if (_hours_since(pr.get("updated_at"), now) or 0) >= 48]
        items.append({
            "issue": number,
            "title": str(issues.get(number, {}).get("title", item["milestone"]))[:180],
            "priority": item["priority"],
            "lead": owner,
            "independent_reviewer": item["reviewer"],
            "stage": stage,
            "dependencies_open": dependencies,
            "linked_prs": [pr["number"] for pr in matching_prs],
            "stale_prs": stale,
            "acceptance_evidence": item["evidence"],
            "milestone": item["milestone"],
            # An explicit checkpoint/worker report is required to claim execution.
            "execution": "NOT_RUNNING_UNTIL_VERIFIED_WORKER_CONNECTED",
        })
    return {
        "schema_version": 1,
        "main_sha": snapshot.get("main_sha"),
        "execution_claim": "PLANNING_ONLY",
        "max_parallel": cap,
        "open_prs_consume_slots": active_count,
        "proposed": sum(i["stage"] == "PROPOSED_FOR_NEXT_BRANCH" for i in items),
        "blocked": sum(i["stage"].startswith("BLOCKED") for i in items),
        "by_owner": dict(sorted(Counter(i["lead"] for i in items).items())),
        "orders": items,
    }


def render_board(board: dict[str, Any]) -> str:
    lines = [
        "# Engineering Team — evidence-based assignment proposals",
        "",
        "Main SHA: \`" + str(board.get("main_sha") or "UNKNOWN")[:12] + "\`",
        "",
        "Worker status: **PLANNING_ONLY** — roles are not continuously running coding agents.",
        "Concurrent proposed branches: " + str(board["proposed"]) +
        " / " + str(board["max_parallel"]),
        "",
        "| Priority | Issue | Lead | Independent reviewer | State | Linked PRs |",
        "|---|---|---|---|---|---|",
    ]
    for item in board["orders"]:
        prs = ", ".join("#" + str(n) for n in item["linked_prs"]) or "—"
        lines.append(
            "| " + item["priority"] + " | #" + str(item["issue"]) + " | " +
            item["lead"] + " | " + item["independent_reviewer"] + " | " +
            item["stage"] + " | " + prs + " |"
        )
    lines.extend(["", "No automatic code execution, provider billing, self-approval, "
                  "GitHub merge, signing or Cloudflare deployment is authorized.", ""])
    return "\n".join(lines)
