"""Collect bounded renderer diagnostics without publishing raw process-memory dumps."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import sys

out = Path(sys.argv[1]) / 'emulator-host'
out.mkdir(parents=True, exist_ok=True)
entries = []
# Crash database/report paths differ across emulator and runner versions.
roots = [Path('/tmp/android-runner'), Path('/tmp/android-' + os.environ.get('USER', 'runner'))]
for root in dict.fromkeys(roots):
    if not root.exists():
        continue
    for crash in root.glob('emu-crash*'):
        files = crash.rglob('*') if crash.is_dir() else [crash]
        for path in files:
            if not path.is_file() or path.is_symlink():
                continue
            size = path.stat().st_size
            item = {'path': str(path), 'size': size}
            if size <= 20 * 1024 * 1024:
                item['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
            # Raw dumps/database blobs can contain inherited authentication or
            # signing environment values; retain their inventory, not memory.
            if path.suffix in ('.log', '.txt') and size <= 2 * 1024 * 1024:
                shutil.copyfile(path, out / (hashlib.sha256(str(path).encode()).hexdigest()[:8] + '-' + path.name))
            entries.append(item)
(out / 'crash-file-inventory.json').write_text(json.dumps(entries, indent=2))
for proc in Path('/proc').iterdir():
    if not proc.name.isdigit():
        continue
    try:
        name = (proc / 'comm').read_text().strip()
        if not (name.startswith('qemu') or name.startswith('emulator')):
            continue
        target = out / proc.name
        target.mkdir(exist_ok=True)
        for filename in ('status', 'wchan', 'stat'):
            (target / filename).write_text((proc / filename).read_text())
        threads = []
        for task in (proc / 'task').iterdir():
            try:
                threads.append({'tid': task.name, 'comm': (task / 'comm').read_text().strip(), 'wait': (task / 'wchan').read_text().strip()})
            except OSError:
                pass
        (target / 'threads.json').write_text(json.dumps(threads, indent=2))
    except OSError:
        pass
