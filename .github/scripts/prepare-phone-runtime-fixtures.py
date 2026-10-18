#!/usr/bin/env python3
"""Materialize exact pinned tar fixtures for the production extraction regression test."""
import hashlib
import pathlib
import re
import subprocess
import sys
import urllib.request


def prepare(destination):
    destination.mkdir(parents=True, exist_ok=True)
    pins = pathlib.Path('app/src/main/java/ai/drfx/maximus/matrixai/studio/phonehost/PhoneRuntimeBundlePins.kt').read_text()
    release = re.search(r'const val RELEASE = "([\w.-]+)"', pins).group(1)
    repository = re.search(r'const val OWNER_REPO = "([\w.-]+/[\w.-]+)"', pins).group(1)
    bundles = re.findall(r'"(pocketdev-[\w.-]+\.tar\.zst)",\s*([\d_]+)L,\s*"([a-f0-9]{64})",\s*([\d_]+)L', pins)
    if len(bundles) != 2:
        raise ValueError('Expected exactly two pinned runtime bundles')
    for name, size, digest, maximum in bundles:
        size, maximum = int(size.replace('_', '')), int(maximum.replace('_', ''))
        archive = destination / name
        request = urllib.request.Request(f'https://github.com/{repository}/releases/download/{release}/{name}')
        sha, transferred = hashlib.sha256(), 0
        with urllib.request.urlopen(request, timeout=60) as response, archive.open('wb') as output:
            if not response.url.startswith(('https://github.com/', 'https://release-assets.githubusercontent.com/', 'https://objects.githubusercontent.com/')):
                raise ValueError('Untrusted runtime artifact redirect')
            while chunk := response.read(131072):
                transferred += len(chunk)
                if transferred > size:
                    raise ValueError('Pinned runtime archive exceeded its size')
                sha.update(chunk)
                output.write(chunk)
        if transferred != size or sha.hexdigest() != digest:
            raise ValueError(f'Runtime checksum or size mismatch: {name}')
        tar = destination / name.removesuffix('.zst')
        try:
            with tar.open('wb') as output, subprocess.Popen(['zstd', '-dc', str(archive)], stdout=subprocess.PIPE) as process:
                expanded = 0
                while chunk := process.stdout.read(131072):
                    expanded += len(chunk)
                    # Includes tar headers/padding, unlike the production file-content limit.
                    if expanded > maximum * 2:
                        process.kill()
                        raise ValueError('Tar fixture exceeded its materialization bound')
                    output.write(chunk)
                if process.wait(timeout=30) != 0:
                    raise ValueError('Runtime decompression failed')
        except Exception:
            tar.unlink(missing_ok=True)
            raise
        archive.unlink()
        print(f'Verified pinned fixture: {name} ({transferred} bytes, SHA-256 {digest})', flush=True)


if __name__ == '__main__':
    prepare(pathlib.Path(sys.argv[1]))
