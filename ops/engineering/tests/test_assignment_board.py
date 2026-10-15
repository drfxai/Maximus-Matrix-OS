"""Fail-closed tests for the deterministic engineering assignment control-plane."""
from __future__ import annotations

import copy
import datetime as dt
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "assignment_board", ROOT / "ops" / "engineering" / "assignment_board.py"
)
board = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(board)
MANIFEST = json.loads((ROOT / "ops/engineering/team.json").read_text(encoding="utf-8"))
ORDERS = json.loads((ROOT / "ops/engineering/work_orders.json").read_text(encoding="utf-8"))
NOW = dt.datetime(2026, 10, 15, 11, 0, tzinfo=dt.timezone.utc)


def observed(issues=(36, 40, 24, 22, 38, 19, 39, 35, 20), prs=()):
    return {
        "main_sha": "a" * 40,
        "issues": [{"number": n, "title": f"Work #{n}"} for n in issues],
        "prs": list(prs),
    }


def pr(number, body, updated="2026-10-15T10:00:00Z"):
    return {"number": number, "head": {"sha": "b" * 40},
            "body": body, "updated_at": updated, "title": "Source changes"}


class AssignmentBoardTests(unittest.TestCase):
    def test_valid_manifest_and_distinct_reviewers(self):
        board.validate_orders(ORDERS, MANIFEST)
        result = board.build_board(ORDERS, MANIFEST, observed(), [], NOW)
        self.assertEqual("PLANNING_ONLY", result["execution_claim"])
        self.assertEqual(9, len(result["orders"]))
        self.assertLessEqual(result["proposed"], 4)
        for item in result["orders"]:
            self.assertNotEqual(item["lead"], item["independent_reviewer"])
            self.assertEqual("NOT_RUNNING_UNTIL_VERIFIED_WORKER_CONNECTED",
                             item["execution"])
        self.assertIn("not continuously running coding agents",
                      board.render_board(result))

    def test_parallel_limit_and_no_double_assignment_to_same_role(self):
        result = board.build_board(ORDERS, MANIFEST, observed(), [], NOW)
        assigned = [i for i in result["orders"]
                    if i["stage"] == "PROPOSED_FOR_NEXT_BRANCH"]
        self.assertLessEqual(len(assigned), 4)
        self.assertEqual(len(assigned), len({i["lead"] for i in assigned}))

    def test_open_pr_uses_slot_without_claiming_live_coding(self):
        candidate = pr(74, "Refs #36")
        report = [{"number": 74, "status": "AWAITING_CHECKS"}]
        result = board.build_board(ORDERS, MANIFEST,
                                   observed(prs=[candidate]), report, NOW)
        issue = next(i for i in result["orders"] if i["issue"] == 36)
        self.assertEqual("VERIFYING", issue["stage"])
        self.assertEqual([74], issue["linked_prs"])
        self.assertEqual("NOT_RUNNING_UNTIL_VERIFIED_WORKER_CONNECTED",
                         issue["execution"])

    def test_green_ci_requires_independent_review_and_no_self_approval(self):
        candidate = pr(75, "Fixes #22")
        report = [{"number": 75, "status": "CHECKS_GREEN_AWAITING_INDEPENDENT_REVIEW"}]
        result = board.build_board(ORDERS, MANIFEST,
                                   observed(prs=[candidate]), report, NOW)
        issue = next(i for i in result["orders"] if i["issue"] == 22)
        self.assertEqual("AWAITING_INDEPENDENT_REVIEW", issue["stage"])
        self.assertNotEqual(issue["lead"], issue["independent_reviewer"])

    def test_ci_failure_is_blocked_and_stale_review_is_flagged(self):
        candidate = pr(76, "Closes #24", updated="2026-10-11T09:00:00Z")
        report = [{"number": 76, "status": "BLOCKED_CI"}]
        result = board.build_board(ORDERS, MANIFEST,
                                   observed(prs=[candidate]), report, NOW)
        issue = next(i for i in result["orders"] if i["issue"] == 24)
        self.assertEqual("BLOCKED_CI", issue["stage"])
        self.assertEqual([76], issue["stale_prs"])

    def test_dependency_prevents_unsafe_new_implementation(self):
        result = board.build_board(ORDERS, MANIFEST, observed(), [], NOW)
        issue = next(i for i in result["orders"] if i["issue"] == 39)
        self.assertEqual("BLOCKED_DEPENDENCY", issue["stage"])
        self.assertEqual([40], issue["dependencies_open"])

    def test_missing_issues_are_unknown_not_assumed_closed(self):
        result = board.build_board(ORDERS, MANIFEST, observed(issues=(36,)), [], NOW)
        missing = next(i for i in result["orders"] if i["issue"] == 22)
        self.assertEqual("ISSUE_NOT_OBSERVED", missing["stage"])

    def test_unsafe_config_is_rejected(self):
        def altered():
            return copy.deepcopy(ORDERS)
        for change in (
            lambda o: o["principles"].update(execution_mode="UNSUPERVISED"),
            lambda o: o["principles"].update(max_concurrent_coding_branches=80),
            lambda o: o["orders"][0].update(reviewer=o["orders"][0]["owner"]),
            lambda o: o["orders"][0].update(issue=o["orders"][1]["issue"]),
            lambda o: o["orders"][0].update(depends_on=[36]),
            # Issue 39 already depends on issue 40, creating a real cycle.
            lambda o: o["orders"][1].update(depends_on=[39]),
            lambda o: o["orders"][0].update(evidence=[]),
        ):
            data = altered()
            change(data)
            with self.subTest(data=data["orders"][0]), self.assertRaises(ValueError):
                board.validate_orders(data, MANIFEST)

    def test_no_raw_pr_comments_interpreted_as_operational_commands(self):
        self.assertEqual({36}, board.linked_issues(
            {"body": "Refs #36. Also do not write secrets. #40 unrelated."}
        ))
        self.assertEqual(set(), board.linked_issues({"body": "issue #36, #40"}))

    def test_invalid_clock_and_untrusted_empty_fields_fail_closed(self):
        with self.assertRaises(ValueError):
            board.build_board(ORDERS, MANIFEST, observed(), [],
                              dt.datetime(2026, 10, 15))
        self.assertEqual([], board.build_board(
            ORDERS, MANIFEST, observed(issues=()), [], NOW
        )["orders"][:0])


if __name__ == "__main__":
    unittest.main()
