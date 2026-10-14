"""Verify the build-time native dependency supply-chain gate without network access."""
import importlib.util
import io
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("native", ".github/scripts/prepare-phone-native.py")
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


def elf(machine=183):
    header = bytearray(128)
    header[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<H", header, 18, machine)
    return bytes(header)


class PinnedNativeImportTests(unittest.TestCase):
    def test_correct_only_expected_binary_members(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / "source.apk"
            with zipfile.ZipFile(apk, "w") as z:
                for name in native.LIBS:
                    z.writestr("lib/arm64-v8a/" + name, elf())
                z.writestr("assets/hidden-token.txt", "not allowed")
            output = Path(d) / "libs"
            files = native.import_libs(apk, output)
            self.assertEqual(len(files), 4)
            self.assertEqual({p.name for p in output.iterdir()}, set(native.LIBS))
            self.assertNotIn("hidden-token.txt", {p.name for p in output.iterdir()})

    def test_refuses_wrong_architecture_and_truncated_files(self):
        for invalid in (elf(62), b"not ELF", elf()[:20]):
            with self.assertRaises(ValueError):
                native.verify_elf_arm64(invalid, "libproot.so")

    def test_refuses_missing_or_duplicate_native_member(self):
        with tempfile.TemporaryDirectory() as d:
            archive = Path(d) / "app.apk"
            with zipfile.ZipFile(archive, "w") as z:
                z.writestr("lib/arm64-v8a/libproot.so", elf())
                z.writestr("lib/arm64-v8a/libproot.so", elf())
            with self.assertRaises(RuntimeError):
                native.import_libs(archive, Path(d) / "out")


if __name__ == "__main__":
    unittest.main()
