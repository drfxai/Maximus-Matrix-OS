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

    def run(self, source: pathlib.Path, output: pathlib.Path):
        # Constant command and offline cache: project content cannot modify host argv.
        script = ('mkdir -p /tmp/build && cp -R /workspace/. /tmp/build && '
                  'cd /tmp/build && gradle --offline --no-daemon --max-workers=1 '
                  ':app:assembleDebug --project-cache-dir /tmp/gradle-project '
                  '>/tmp/gradle.log 2>&1 && '
                  'find app/build/outputs/apk/debug -type f -name "*.apk" '
                  r'-exec cp {} /out/project-debug.apk \;')
        container_name = 'maximus-debug-' + uuid.uuid4().hex
        argv = [
            "docker", "run", "--rm", "--pull=never", "--network=none",
            "--name=" + container_name,
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
                subprocess.run(['docker', 'rm', '--force', container_name],
                               capture_output=True, timeout=15, check=False)
            except (OSError, subprocess.TimeoutExpired):
                raise RuntimeError('Build container cleanup was not confirmed.') from None
        apk = output / "project-debug.apk"
        read_artifact(apk)
        return apk


class BuildCoordinator:
    def __init__(self, runner):
        runner.check()
        self.runner = runner
        self.lock = threading.Lock()
        self.jobs = {}
        self.requests = {}

    def submit(self, project_id: str, key: str, checksum: str, data: bytes, approved: bool):
        if not approved or not PROJECT_ID.fullmatch(project_id) or not PROJECT_ID.fullmatch(key):
            raise ValueError("Explicit approval and valid project/request IDs required.")
        digest = hashlib.sha256(data).hexdigest()
        if checksum != digest:
            raise ValueError("ZIP SHA256 mismatch.")
        files = validate_zip(data)
        with self.lock:
            self._expire_locked()
            if key in self.requests:
                item = self.jobs[self.requests[key]]
                if item["source_sha256"] != digest or item["project_id"] != project_id:
                    raise ValueError("Conflicting idempotency key.")
                return self._public(item)
            if any(j["state"] in ("queued", "running") for j in self.jobs.values()):
                raise RuntimeError("Build worker is busy.")
            if len(self.jobs) >= 16:
                raise RuntimeError("Job capacity reached; operator cleanup required.")
            job_id = uuid.uuid4().hex
            item = dict(id=job_id, project_id=project_id, source_sha256=digest,
                        state="queued", error="", apk_sha256="", apk_path="",
                        created_at=time.time(), root="")
            self.jobs[job_id] = item
            self.requests[key] = job_id
        threading.Thread(target=self._execute, args=(item, files), daemon=True).start()
        return self._public(item)

    @staticmethod
    def _public(item):
        return {key: item[key] for key in
                ("id", "project_id", "source_sha256", "state", "error", "apk_sha256")} | {
                "signed": False, "artifact_ready": item["state"] == "succeeded"
            }

    def _execute(self, job, files):
        root = pathlib.Path(tempfile.mkdtemp(prefix="maximus-debug-"))
        # The unprivileged container UID must traverse the bind-mount parent;
        # no secrets reside under this temporary directory.
        root.chmod(0o755)
        job["root"] = str(root)
        try:
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
            apk = self.runner.run(source, output)
            shutil.rmtree(source, ignore_errors=True)
            if apk != output / 'project-debug.apk':
                raise RuntimeError('Unexpected artifact path.')
            digest = hashlib.sha256(read_artifact(apk)).hexdigest()
            with self.lock:
                job["apk_path"] = str(apk)
                job["apk_sha256"] = digest
                job["state"] = "succeeded"
        except Exception:
            with self.lock:
                job["error"] = "Isolated debug build failed; review host-side diagnostics."
                job["state"] = "failed"
            shutil.rmtree(root, ignore_errors=True)

    def _expire_locked(self):
        expired = [
            identity for identity, job in self.jobs.items()
            if job["state"] not in ("queued", "running")
            and time.time() - job["created_at"] > 3600
        ]
        for identity in expired:
            job = self.jobs.pop(identity)
            self.requests = {
                key: value for key, value in self.requests.items() if value != identity
            }
            if job["root"]:
                shutil.rmtree(job["root"], ignore_errors=True)

    def status(self, job_id: str):
        with self.lock:
            self._expire_locked()
            item = self.jobs.get(job_id)
            return self._public(item) if item else None

    def artifact(self, job_id: str):
        with self.lock:
            self._expire_locked()
            item = self.jobs.get(job_id)
            if not item or item["state"] != "succeeded":
                return None
            path, digest = item["apk_path"], item["apk_sha256"]
        data = read_artifact(pathlib.Path(path))
        if len(data) > MAX_APK or hashlib.sha256(data).hexdigest() != digest:
            raise RuntimeError("Debug APK checksum mismatch.")
        return data
