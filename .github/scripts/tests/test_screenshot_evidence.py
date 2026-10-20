import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    "screenshot_evidence", Path(__file__).resolve().parents[1] / "emit-screenshot-evidence.py")
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class ScreenshotEvidenceTests(unittest.TestCase):
    def test_rejects_non_allowlisted_path(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                evidence.frame(root, "../private.png")

    def test_rejects_missing_and_non_png_files(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                evidence.frame(root, "galaxy-rings.png")
            Path(root, "galaxy-rings.png").write_bytes(b"not a PNG")
            with self.assertRaises(ValueError):
                evidence.frame(root, "galaxy-rings.png")

    def test_preserves_original_bytes_and_records_integrity(self):
        import base64
        import hashlib
        with tempfile.TemporaryDirectory() as root:
            # Header-only fixture validates the envelope, not a rendered image.
            data = b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + struct.pack(">II", 1080, 1920) + b"\0" * 9
            Path(root, "galaxy-rings.png").write_bytes(data)
            item = evidence.frame(root, "galaxy-rings.png")
            self.assertEqual(data, base64.b64decode(item["base64"]))
            self.assertEqual(hashlib.sha256(data).hexdigest(), item["sha256"])
            self.assertEqual((1080, 1920), (item["width"], item["height"]))

    def test_rejects_symlink_and_oversized_frame(self):
        with tempfile.TemporaryDirectory() as root:
            target = Path(root, "other.png")
            target.write_bytes(b"x")
            path = Path(root, "galaxy-rings.png")
            path.symlink_to(target)
            with self.assertRaises(ValueError):
                evidence.frame(root, "galaxy-rings.png")
            path.unlink()
            with path.open("wb") as output:
                output.truncate(evidence.MAX_BYTES + 1)
            with self.assertRaises(ValueError):
                evidence.frame(root, "galaxy-rings.png")
