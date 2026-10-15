"""Unit tests for read-only engineering oversight and fail-closed review rules."""
import copy
import datetime as dt
import importlib.util
import json
from pathlib import Path
import unittest

root = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location(
    "engineering_supervisor", root / "ops" / "engineering" / "supervisor.py"
)
supervisor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(supervisor)
MANIFEST = json.loads((root / "ops/engineering/team.json").read_text())


def snapshot():
    main = "a" * 40
    pr_head = "b" * 40
    return {
        "main_sha": main,
        "release": {
            "tag_name": "v1.0.0", "body": "internal version code: 10019",
            "assets": [{"name": "signed-arm64.apk"}, {"name": "sha256sums"}],
            "draft": False, "prerelease": False,
        },
        "prs": [{
            "number": 51, "title": "Five-language graph",
            "head": {"sha": pr_head}, "draft": False,
            "html_url": "https://github.com/drfxai/Maximus-Matrix-OS/pull/51",
        }],
        "issues": [{"number": 36}, {"number": 40}, {"number": 38}],
        "runs": [{"head_sha": main, "head_branch": "main",
                  "conclusion": "success", "status": "completed"}],
        "checks": {pr_head: []},
    }


def check(name, conclusion):
    return {"name": name, "conclusion": conclusion, "head_sha": "b" * 40}


class EngineeringSupervisorTests(unittest.TestCase):
    def test_manifest_and_release_gates_are_safe(self):
        supervisor.validate_config(MANIFEST)
        for forbidden in ("allow_auto_merge", "allow_auto_publish",
                          "allow_unapproved_cloud_deploy", "allow_cross_project_secrets"):
            unsafe = copy.deepcopy(MANIFEST)
            unsafe["control_plane"][forbidden] = True
            with self.subTest(forbidden=forbidden), self.assertRaises(ValueError):
                supervisor.validate_config(unsafe)
        unsafe = copy.deepcopy(MANIFEST)
        unsafe["roles"][1]["may_approve_own_work"] = True
        with self.assertRaises(ValueError):
            supervisor.validate_config(unsafe)

    def test_missing_checks_never_mean_success(self):
        result = supervisor.summarize(
            MANIFEST, snapshot(), dt.datetime.now(dt.timezone.utc),
            'versionName = "1.0.0"\nversionCode = 10019'
        )
        self.assertEqual("AWAITING_CHECKS", result["open_prs"][0]["status"])
        self.assertFalse(result["authorization"]["automatic_release"])
        self.assertFalse(result["authorization"]["self_review"])
        self.assertEqual([], result["warnings"])

    def test_emulator_failure_blocks_pr_regardless_of_green_validate(self):
        state = snapshot()
        state["checks"]["b" * 40] = [
            check("validate", "success"),
            check("emulator-smoke (keyboard, TestClass)", "failure"),
            check("emulator-smoke (credentials-and-history, TestClass)", "success"),
        ]
        result = supervisor.summarize(
            MANIFEST, state, dt.datetime.now(dt.timezone.utc),
            'versionName = "1.0.0"\nversionCode = 10019'
        )
        self.assertEqual("BLOCKED_CI", result["open_prs"][0]["status"])
        self.assertIn("OPEN_PULL_REQUEST_WITH_FAILED_CHECKS", result["warnings"])

    def test_green_checks_still_require_independent_review(self):
        state = snapshot()
        state["checks"]["b" * 40] = [
            check("validate", "success"),
            check("emulator-smoke (keyboard, TestClass)", "success"),
            check("emulator-smoke (credentials-and-history, TestClass)", "success"),
        ]
        result = supervisor.summarize(
            MANIFEST, state, dt.datetime.now(dt.timezone.utc),
            'versionName = "1.0.0"\nversionCode = 10019'
        )
        self.assertEqual(
            "CHECKS_GREEN_AWAITING_INDEPENDENT_REVIEW",
            result["open_prs"][0]["status"]
        )

    def test_detects_unpublished_internal_build(self):
        result = supervisor.summarize(
            MANIFEST, snapshot(), dt.datetime.now(dt.timezone.utc),
            'versionName = "1.0.0"\nversionCode = 10020'
        )
        self.assertIn("MAIN_BUILD_NOT_PUBLISHED", result["warnings"])
        self.assertEqual(10019, result["release"]["published_internal_version"])

    def test_public_version_change_is_reported(self):
        result = supervisor.summarize(
            MANIFEST, snapshot(), dt.datetime.now(dt.timezone.utc),
            'versionName = "1.0.1"\nversionCode = 10020'
        )
        self.assertIn("PUBLIC_VERSION_CHANGED", result["warnings"])

    def test_only_configured_open_issue_ids_are_assigned(self):
        result = supervisor.summarize(
            MANIFEST, snapshot(), dt.datetime.now(dt.timezone.utc),
            'versionName = "1.0.0"\nversionCode = 10019'
        )
        voice = next(x for x in result["roles"] if x["id"] == "brain-voice-i18n")
        # Only configured issue IDs which are also OPEN belong in this list.
        # Voice owns both issue #36 and the cross-functional Brain issue #40.
        self.assertEqual([36, 40], voice["open_issues"])
        self.assertEqual("UNASSIGNED_RUNTIME", voice["execution"])

        modified = snapshot()
        modified["issues"].append({"number": 9999})
        verified = supervisor.summarize(
            MANIFEST, modified, dt.datetime.now(dt.timezone.utc),
            'versionName = "1.0.0"\nversionCode = 10020'
        )
        voice2 = next(x for x in verified["roles"] if x["id"] == "brain-voice-i18n")
        self.assertEqual([36, 40], voice2["open_issues"])
        self.assertNotIn(9999, voice2["open_issues"])

    def test_report_explicitly_discloses_not_live_agents(self):
        report = supervisor.summarize(
            MANIFEST, snapshot(), dt.datetime.now(dt.timezone.utc),
            'versionName = "1.0.0"\nversionCode = 10019'
        )
        markdown = supervisor.render_markdown(report)
        self.assertIn("not proof of live agents", markdown)
        self.assertIn("does not merge", markdown)


if __name__ == "__main__":
    unittest.main()
