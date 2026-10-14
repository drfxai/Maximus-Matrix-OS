#!/usr/bin/env python3
"""Require both Android debug APK variants to retain exact pinned ELF native bytes."""
from pathlib import Path
import hashlib
import zipfile

root = Path("app/src/main/jniLibs/arm64-v8a")
libnames = ("libproot.so", "libprootloader.so", "libtalloc.so", "libandroid-shmem.so")
reference = {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in libnames}

debug_apks = sorted(Path("app/build/outputs/apk/debug").glob("*.apk"))
assert debug_apks, "No Debug APK produced."
checked = 0
for apk in debug_apks:
    with zipfile.ZipFile(apk) as archive:
        content = set(archive.namelist())
        if "lib/arm64-v8a/libproot.so" not in content:
            continue
        checked += 1
        for name in libnames:
            location = "lib/arm64-v8a/" + name
            assert location in content, "Missing runtime library: " + location
            actual = hashlib.sha256(archive.read(location)).hexdigest()
            assert actual == reference[name], "Native carrier altered in APK: " + name
    print(apk.name, "contains exact verified ARM64 runtime carriers")
assert checked >= 2, "Expected ARM64-specific and universal Debug APKs with complete runtime carriers."
