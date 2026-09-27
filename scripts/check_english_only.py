#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ARABIC_SCRIPT = re.compile(r"[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]")
SKIP_DIRS = {".git", ".gradle", "build", ".idea"}
BINARY_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".apk", ".jar", ".so", ".zip", ".gz"}

violations = []
for path in ROOT.rglob("*"):
    if not path.is_file() or any(part in SKIP_DIRS for part in path.parts):
        continue
    if path.suffix.lower() in BINARY_SUFFIXES:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    for line_no, line in enumerate(text.splitlines(), start=1):
        if ARABIC_SCRIPT.search(line):
            violations.append((path.relative_to(ROOT), line_no, line.strip()))

if violations:
    print("English-only policy violation: Arabic-script text found.")
    for path, line_no, line in violations:
        print(f"{path}:{line_no}: {line}")
    sys.exit(1)

print("English-only policy check passed.")
