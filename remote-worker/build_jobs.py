"""Opt-in, bounded Android debug builds in rootless offline Docker.

Discovery remains the default. No user project is run in the gateway process,
and production signing secrets are never made available to the sandbox.
"""
from __future__ import annotations

import hashlib
import io
import os
import pathlib
import re
import shutil
import stat
import subprocess
import tempfile
import threading
import time
import uuid
import zipfile

from job_journal import JobJournal

MAX_ZIP = 8 * 1024 * 1024
MAX_APK = 64 * 1024 * 1024
PROJECT_ID = re.compile(r"^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
IMAGE = re.compile(r"^[a-zA-Z0-9._/-]+(?::[a-zA-Z0-9._-]+)?@sha256:[a-f0-9]{64}$")


def read_artifact(path: pathlib.Path) -> bytes:
    # Build scripts control output bytes. Never follow an output symlink into
    # the gateway host, and bound reads using the opened descriptor itself.
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or not 1024 <= info.st_size <= MAX_APK:
            raise RuntimeError('No bounded regular APK produced.')
        data = stream.read(MAX_APK + 1)
    if not 1024 <= len(data) <= MAX_APK:
        raise RuntimeError('Invalid artifact size.')
    return data


def validate_zip(data: bytes) -> list[tuple[str, bytes]]:
    if not data or len(data) > MAX_ZIP:
        raise ValueError("Project ZIP must be 1-8 MB.")
    items = []
    names = set()
    total = 0
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        entries = archive.infolist()
        if not entries or len(entries) > 500:
            raise ValueError("Project must contain at most 500 entries.")
        for info in entries:
            path = info.filename
            parts = path.rstrip("/").split("/")
            if (not path or path.startswith("/") or "\\" in path or ":" in path or
                    len(path) > 512 or any(part in ("", ".", "..") for part in parts)):
                raise ValueError("Unsafe archive path.")
            key = "/".join(parts)
            if key in names:
                raise ValueError("Duplicate archive entry.")
            names.add(key)
            mode = (info.external_attr >> 16) & 0o170000
            if mode not in (0, 0o100000, 0o040000):
                raise ValueError("Links and special files are rejected.")
            if info.is_dir():
                continue
            total += info.file_size
            if info.file_size > 4 * 1024 * 1024 or total > 32 * 1024 * 1024:
                raise ValueError("Expanded source is too large.")
            with archive.open(info) as input_file:
                bytes_ = input_file.read(info.file_size + 1)
            if len(bytes_) != info.file_size:
                raise ValueError("Invalid ZIP entry.")
            items.append((key, bytes_))
    if not {"settings.gradle.kts", "app/build.gradle.kts"}.issubset(names):
        raise ValueError("Expected an Android Gradle project.")
    return items


class RootlessBuilder:
    def __init__(self, image: str):
        if not IMAGE.fullmatch(image):
            raise ValueError("Builder image must be pinned to a SHA256 digest.")
        self.image = image

    def check(self):
        details = subprocess.run(
            ["docker", "info", "--format", "{{json .SecurityOptions}}"],
            capture_output=True, text=True, timeout=8, check=True
        )
        if "rootless" not in details.stdout.lower():
            raise RuntimeError("Rootless Docker is required.")

    def cleanup(self, container_name: str, owner_id: str):
        if not re.fullmatch(r'maximus-debug-[a-f0-9]{32}', container_name) or not re.fullmatch(r'[a-f0-9]{32}', owner_id):
            raise RuntimeError('Invalid container ownership identity.')
        # A failed inspect alone cannot distinguish an absent container from a
        # broken daemon. List successfully first, then verify the private label.
        listing = subprocess.run(['docker', 'ps', '-aq', '--filter', 'name=^/' + container_name + '$'],
                                 capture_output=True, text=True, timeout=15, check=True)
        if not listing.stdout.strip():
            return
        identity = listing.stdout.strip()
        if not re.fullmatch(r'[a-f0-9]{12,64}', identity):
            raise RuntimeError('Ambiguous container identity.')
        details = subprocess.run(['docker', 'inspect', '--format', '{{index .Config.Labels "maximus.owner"}}', identity],
                                 capture_output=True, text=True, timeout=15, check=True)
        if details.stdout.strip() != owner_id:
            raise RuntimeError('Container ownership mismatch; operator inspection required.')
        subprocess.run(['docker', 'rm', '--force', identity], capture_output=True, timeout=15, check=True)

    def run(self, source: pathlib.Path, output: pathlib.Path, *, container_name=None, owner_id=None):
        # Constant command and offline cache: project content cannot modify host argv.
        script = ('mkdir -p /tmp/build && cp -R /workspace/. /tmp/build && '
                  'cd /tmp/build && gradle --offline --no-daemon --max-workers=1 '
                  ':app:assembleDebug --project-cache-dir /tmp/gradle-project '
                  '>/tmp/gradle.log 2>&1 && '
                  'find app/build/outputs/apk/debug -type f -name "*.apk" '
                  r'-exec cp {} /out/project-debug.apk \;')
        container_name = container_name or 'maximus-debug-' + uuid.uuid4().hex
        owner_id = owner_id or uuid.uuid4().hex
        if not re.fullmatch(r'maximus-debug-[a-f0-9]{32}', container_name) or not re.fullmatch(r'[a-f0-9]{32}', owner_id):
            raise ValueError('Invalid container ownership identity.')
        argv = [
            "docker", "run", "--rm", "--pull=never", "--network=none",
            "--name=" + container_name, "--label=maximus.owner=" + owner_id,
            "--pids-limit=128", "--memory=4g", "--cpus=2", "--read-only",
            "--cap-drop=ALL", "--security-opt=no-new-privileges",
            "--user=65534:65534", "--env=HOME=/tmp",
            "--env=GRADLE_USER_HOME=/tmp/gradle-home",
            "--env=GRADLE_RO_DEP_CACHE=/opt/gradle-cache",
            "--tmpfs=/tmp:rw,nosuid,nodev,size=2g",
            f"--mount=type=bind,src={source},dst=/workspace,readonly",
            f"--mount=type=bind,src={output},dst=/out",
            self.image, "/bin/sh", "-c", script
        ]
        try:
            subprocess.run(argv, capture_output=True, timeout=600, check=True)
        finally:
            # Killing a timed-out Docker CLI does not stop its container.
            # Remove only this invocation's unpredictable, explicitly named job.
            try:
                self.cleanup(container_name, owner_id)
            except Exception:
                raise RuntimeError('Build container cleanup was not confirmed.') from None
        apk = output / "project-debug.apk"
        read_artifact(apk)
        return apk


class BuildCoordinator:
    def __init__(self, runner, state_dir=None):
        self.runner = runner
        self.lock = threading.Lock()
        self.closed = False
        self.recovery_blocked = False
        self.journal = JobJournal(state_dir or pathlib.Path.home() / '.local/state/maximus-worker')
        try:
            runner.check()
            loaded = self.journal.load()
            self.jobs = {item['id']: item for _, item in loaded}
            self.requests = {key: item['id'] for key, item in loaded}
            self.request_keys = {item['id']: key for key, item in loaded}
            for item in self.jobs.values():
                if item['state'] in ('queued', 'running', 'recovery_required'):
                    try:
                        runner.cleanup(item['container_name'], self.journal.owner_id)
                        item['state'] = 'failed'
                        item['error'] = 'Worker interrupted; build was not replayed. Submit a new approved request to retry.'
                        self._remove_root(item)
                    except Exception:
                        item['state'] = 'recovery_required'
                        item['error'] = 'Interrupted container cleanup unconfirmed; execution blocked.'
                        self.recovery_blocked = True
                    self._save(item)
                elif item['state'] == 'succeeded' and item['apk_path']:
                    self._reconcile_artifact(item)
        except Exception:
            self.journal.close()
            raise

    def _save(self, item):
        try:
            self.journal.save(self.request_keys[item['id']], item)
        except Exception:
            self.recovery_blocked = True
            raise RuntimeError('Job journal unavailable; execution blocked.') from None

    @staticmethod
    def _workspace_root(item):
        root = pathlib.Path(item['root'])
        if (not root.is_absolute() or root.parent != pathlib.Path(tempfile.gettempdir()) or
                not re.fullmatch(r'maximus-debug-' + item['id'] + r'-[a-zA-Z0-9_]+', root.name)):
            raise RuntimeError('Invalid retained build directory.')
        info = root.lstat()
        if not stat.S_ISDIR(info.st_mode) or (os.name == 'posix' and info.st_uid != os.getuid()):
            raise RuntimeError('Retained build directory must be an owned directory.')
        return root

    @classmethod
    def _artifact_bytes(cls, item):
        root = cls._workspace_root(item)
        output = root / 'output'
        if not stat.S_ISDIR(output.lstat().st_mode):
            raise RuntimeError('Invalid output directory.')
        expected = output / 'project-debug.apk'
        if item['apk_path'] != str(expected):
            raise RuntimeError('Invalid retained artifact path.')
        data = read_artifact(expected)
        if hashlib.sha256(data).hexdigest() != item['apk_sha256']:
            raise RuntimeError('Debug APK checksum mismatch.')
        return data

    def _reconcile_artifact(self, item):
        if item['state'] == 'succeeded' and item['apk_path']:
            try:
                self._artifact_bytes(item)
            except Exception:
                item['apk_path'] = ''
                item['error'] = 'Retained build artifact unavailable or invalid; build was not replayed.'
                self._save(item)

    @classmethod
    def _remove_root(cls, item):
        if item.get('root'):
            root = pathlib.Path(item['root'])
            # Never accept arbitrary deletion paths from a damaged journal.
            if root.parent != pathlib.Path(tempfile.gettempdir()) or not re.fullmatch(r'maximus-debug-' + item['id'] + r'-[a-zA-Z0-9_]+', root.name):
                raise RuntimeError('Invalid retained build directory.')
            if root.exists() or root.is_symlink():
                cls._workspace_root(item)
                shutil.rmtree(root)
            item['root'] = ''
        item['apk_path'] = ''

    def close(self):
        with self.lock:
            if any(j['state'] in ('queued', 'running') for j in self.jobs.values()):
                raise RuntimeError('Cannot release coordinator ownership during an active build.')
            for item in self.jobs.values():
                if item['state'] == 'recovery_required':
                    try:
                        self.runner.cleanup(item['container_name'], self.journal.owner_id)
                        self._remove_root(item)
                        item['state'] = 'failed'
                        self._save(item)
                    except Exception:
                        raise RuntimeError('Cannot release ownership: container cleanup unconfirmed.') from None
            self.closed = True
            self.journal.close()

    def submit(self, project_id: str, key: str, checksum: str, data: bytes, approved: bool):
        if not approved or not PROJECT_ID.fullmatch(project_id) or not PROJECT_ID.fullmatch(key):
            raise ValueError("Explicit approval and valid project/request IDs required.")
        digest = hashlib.sha256(data).hexdigest()
        if checksum != digest:
            raise ValueError("ZIP SHA256 mismatch.")
        files = validate_zip(data)
        with self.lock:
            if self.closed:
                raise RuntimeError('Coordinator closed.')
            self._expire_locked()
            if key in self.requests:
                item = self.jobs[self.requests[key]]
                if item["source_sha256"] != digest or item["project_id"] != project_id:
                    raise ValueError("Conflicting idempotency key.")
                return self._public(item)
            if self.recovery_blocked or any(j["state"] in ("queued", "running") for j in self.jobs.values()):
                raise RuntimeError("Build worker is busy.")
            if len(self.jobs) >= self.journal.MAX_REQUESTS or sum(bool(j['root']) for j in self.jobs.values()) >= 16:
                raise RuntimeError("Job capacity reached; operator cleanup required.")
            job_id = uuid.uuid4().hex
            item = dict(id=job_id, project_id=project_id, source_sha256=digest,
                        state="queued", error="", apk_sha256="", apk_path="",
                        created_at=time.time(), root="", container_name="maximus-debug-" + job_id)
            self.jobs[job_id] = item
            self.requests[key] = job_id
            self.request_keys[job_id] = key
            try:
                self._save(item)
            except Exception:
                del self.jobs[job_id]
                del self.requests[key]
                del self.request_keys[job_id]
                raise RuntimeError("Job journal unavailable; build was not dispatched.") from None
        try:
            threading.Thread(target=self._execute, args=(item, files), daemon=True).start()
        except Exception:
            with self.lock:
                item['state'] = 'failed'
                item['error'] = 'Build dispatch unavailable; submit a new approved request to retry.'
                self._save(item)
            raise RuntimeError('Build dispatch unavailable.') from None
        return self._public(item)

    @staticmethod
    def _public(item):
        return {key: item[key] for key in
                ("id", "project_id", "source_sha256", "state", "error", "apk_sha256")} | {
                "signed": False, "artifact_ready": item["state"] == "succeeded" and bool(item["apk_path"])
            }

    def _execute(self, job, files):
        root = None
        try:
            root = pathlib.Path(tempfile.mkdtemp(prefix="maximus-debug-" + job["id"] + "-"))
        except Exception:
            with self.lock:
                job["state"] = "failed"
                job["error"] = "Build workspace unavailable."
                self._save(job)
            return
        # The unprivileged container UID must traverse the bind-mount parent;
        # no secrets reside under this temporary directory.
        try:
            root.chmod(0o755)
            with self.lock:
                job["root"] = str(root)
                self._save(job)
            source, output = root / "source", root / "output"
            source.mkdir(mode=0o755)
            output.mkdir(mode=0o777)
            output.chmod(0o777)
            for name, data in files:
                path = source.joinpath(*name.split("/"))
                path.parent.mkdir(parents=True, exist_ok=True)
                path.parent.chmod(0o755)
                path.write_bytes(data)
                path.chmod(0o644)
            with self.lock:
                job["state"] = "running"
                self._save(job)
            apk = self.runner.run(source, output, container_name=job["container_name"], owner_id=self.journal.owner_id)
            shutil.rmtree(source, ignore_errors=True)
            if apk != output / 'project-debug.apk':
                raise RuntimeError('Unexpected artifact path.')
            digest = hashlib.sha256(read_artifact(apk)).hexdigest()
            with self.lock:
                job["apk_path"] = str(apk)
                job["apk_sha256"] = digest
                job["state"] = "succeeded"
                self._save(job)
        except Exception:
            with self.lock:
                job["error"] = "Isolated debug build failed; review host-side diagnostics."
                try:
                    self.runner.cleanup(job['container_name'], self.journal.owner_id)
                    job['state'] = 'failed'
                    self._remove_root(job)
                except Exception:
                    job['state'] = 'recovery_required'
                    self.recovery_blocked = True
                self._save(job)

    def _expire_locked(self):
        for job in self.jobs.values():
            if (job['state'] not in ('queued', 'running', 'recovery_required') and job['root']
                    and time.time() - job['created_at'] > 3600):
                self._remove_root(job)
                self._save(job)

    def status(self, job_id: str):
        with self.lock:
            self._expire_locked()
            item = self.jobs.get(job_id)
            if item:
                self._reconcile_artifact(item)
            return self._public(item) if item else None

    def artifact(self, job_id: str):
        with self.lock:
            self._expire_locked()
            item = self.jobs.get(job_id)
            if not item or item["state"] != "succeeded" or not item["apk_path"]:
                return None
            try:
                return self._artifact_bytes(item)
            except Exception:
                item['apk_path'] = ''
                item['error'] = 'Retained build artifact unavailable or invalid.'
                self._save(item)
                raise RuntimeError('Debug APK unavailable or invalid.') from None
