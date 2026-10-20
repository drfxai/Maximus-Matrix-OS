"""Export only allowlisted emulator frames with source provenance for review tooling.

No device access, image generation, transformation, source upload or credentials.
The original PNG bytes remain in the normal workflow evidence artifact.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import struct
import sys

CAPTURES = (
    "galaxy-brain.png", "galaxy-circle.png", "galaxy-links.png",
    "galaxy-timeline.png", "studio-overview.png",
    "galaxy-rings.png",
    "galaxy-areas.png",
    "galaxy-orbit.png",
    "smart-combo.png",
    "developer-operations.png",
)
EXPORTED = {"galaxy-rings.png", "galaxy-areas.png", "galaxy-orbit.png",
            "smart-combo.png", "developer-operations.png"}
MAX_BYTES = 2 * 1024 * 1024


def frame(directory, name):
    if name not in CAPTURES:
        raise ValueError("Unknown screenshot name")
    root = Path(directory).resolve()
    path = root / name
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_BYTES:
        raise ValueError("Missing or oversized emulator frame: " + name)
    data = path.read_bytes()
    if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("Invalid screenshot PNG: " + name)
    width, height = struct.unpack(">II", data[16:24])
    if not (1 <= width <= 8192 and 1 <= height <= 8192):
        raise ValueError("Invalid screenshot dimensions")
    return {
        "name": name, "width": width, "height": height,
        "sha256": hashlib.sha256(data).hexdigest(),
        "checkout_sha": os.environ.get("GITHUB_SHA", "UNKNOWN"),
        "run_id": os.environ.get("GITHUB_RUN_ID", "UNKNOWN"),
        "capture": "Android UiDevice.takeScreenshot; unmodified PNG",
        "base64": base64.b64encode(data).decode("ascii"),
    }


if __name__ == "__main__":
    # Read and validate every image before emitting any encoded evidence.
    frames = [frame(sys.argv[1], name) for name in CAPTURES]
    for item in frames:
        if item["name"] not in EXPORTED:
            continue
        print("MAXIMUS_SCREENSHOT_V1 " + json.dumps(item, separators=(",", ":")))
