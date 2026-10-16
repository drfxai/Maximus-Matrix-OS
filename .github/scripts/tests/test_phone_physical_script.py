"""Static regression gates for the explicit physical ARM64 acceptance harness.

These tests do not claim that a phone was attached or an APK was built on-device.
"""
from pathlib import Path
import unittest

SCRIPT = Path(".github/scripts/verify-phone-physical.sh")


class PhysicalPhoneScriptTests(unittest.TestCase):
    def setUp(self):
        self.source = SCRIPT.read_text()

    def test_host_selects_exactly_one_arm64_split_and_instrumentation_apk(self):
        self.assertIn("'*arm64-v8a*debug*.apk'", self.source)
        self.assertIn('"${#app_apks[@]}" -ne 1', self.source)
        self.assertIn('"${#test_apks[@]}" -ne 1', self.source)

    def test_does_not_overwrite_existing_release_without_separate_approval(self):
        self.assertIn("MAXIMUS_APPROVE_TRUSTED_BUILD", self.source)
        self.assertIn("MAXIMUS_APPROVE_DEBUG_REPLACE", self.source)
        self.assertIn("pm path ai.drfx.maximus.matrixai", self.source)
        self.assertIn("ro.kernel.qemu", self.source)
        self.assertIn("ANDROID_SERIAL", self.source)
        self.assertIn("does not match explicit ANDROID_SERIAL", self.source)

    def test_physical_evidence_is_bound_to_source_and_sha256(self):
        self.assertIn("source_commit=\"$(git rev-parse HEAD)\"", self.source)
        self.assertIn("result.json", self.source)
        self.assertIn("phone-proof-debug.apk", self.source)
        self.assertIn("hashlib.file_digest", self.source)
        self.assertIn("report['apk_package']", self.source)
        self.assertIn("200 * 1024 * 1024", self.source)


if __name__ == "__main__":
    unittest.main()
