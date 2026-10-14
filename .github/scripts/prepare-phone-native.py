#!/usr/bin/env python3
"""Import only pinned ARM64 native PRoot components from a reproducible upstream APK.

No downloaded executable is trusted unless the COMPLETE upstream APK has the
expected SHA-256 and the exact expected ELF paths/architecture are present.
This runs in Android build CI; no unverified binaries are fetched by the app.
"""
import hashlib
import os
from pathlib import Path
import struct
import tempfile
import urllib.request
import zipfile

UPSTREAM = "https://github.com/techjarves/Mobile-Harness/releases/download/v1.0.7/mobile-harness-online-v1.0.7.apk"
APK_SHA256 = "c41795826ae1c9a9afec8b393d15ebd3c934d52b857d66eb62123a1079a98588"
MAX_APK_BYTES = 95_000_000
# GPL-2.0 PRoot; LGPL talloc; BSD libandroid-shmem. See docs/phone-build-third-party-notices.md.
LIBS = ("libproot.so", "libprootloader.so", "libtalloc.so", "libandroid-shmem.so")
DEST = Path("app/src/main/jniLibs/arm64-v8a")

def retrieve(url: str, destination: Path):
    digest = hashlib.sha256()
    length = 0
    request = urllib.request.Request(url, headers={"User-Agent": "Maximus-Matrix-AndroidBuild/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response, destination.open("wb") as output:
        assert response.status == 200, "Unexpected upstream HTTP response"
        while chunk := response.read(256 * 1024):
            length += len(chunk)
            if length > MAX_APK_BYTES:
                raise RuntimeError("Unexpected oversized runtime binary source")
            digest.update(chunk)
            output.write(chunk)
    if digest.hexdigest() != APK_SHA256:
        raise RuntimeError("Upstream PRoot artifact checksum mismatch")
    print("Pinned upstream release SHA-256 verified:", digest.hexdigest())

def verify_elf_arm64(data: bytes, library: str):
    if len(data) < 64 or len(data) > 25_000_000:
        raise ValueError(f"Invalid length for {library}")
    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        raise ValueError(f"Invalid 64-bit ELF header for {library}")
    if struct.unpack_from("<H", data, 18)[0] != 183:
        raise ValueError(f"Not ARM64: {library}")

def import_libs(apk: Path, destination: Path):
    destination.mkdir(parents=True, exist_ok=True)
    installed = []
    with zipfile.ZipFile(apk) as archive:
        for name in LIBS:
            path = "lib/arm64-v8a/" + name
            members = [item for item in archive.infolist() if item.filename == path]
            if len(members) != 1:
                raise RuntimeError(f"Required native runtime member absent or duplicated: {path}")
            item = members[0]
            if item.file_size > 25_000_000:
                raise RuntimeError(f"Native runtime member oversized: {name}")
            binary = archive.read(item)
            verify_elf_arm64(binary, name)
            target = destination / name
            temp = target.with_suffix(target.suffix + ".new")
            temp.write_bytes(binary)
            os.replace(temp, target)
            installed.append(str(target))
            print(name, hashlib.sha256(binary).hexdigest(), len(binary))
    return installed

def main():
    with tempfile.TemporaryDirectory(prefix="maximus-proot-") as td:
        apk = Path(td) / "upstream-online.apk"
        retrieve(UPSTREAM, apk)
        imported = import_libs(apk, DEST)
        assert len(imported) == len(LIBS)
        print("Four ARM64 native runtime components prepared. No model/agent files copied.")

if __name__ == "__main__":
    main()
