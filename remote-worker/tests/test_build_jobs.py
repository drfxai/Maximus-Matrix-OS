import hashlib
import http.client
import io
import json
import pathlib
import subprocess
import tempfile
import threading
import time
import unittest
import uuid
import zipfile
from unittest.mock import patch

from build_jobs import BuildCoordinator, RootlessBuilder, validate_zip, read_artifact
from worker import make_server

TOKEN = "private-worker-test-token-that-is-long-enough"
PROJECT = str(uuid.uuid4())


def project_zip(files=None):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, content in (files or {
            "settings.gradle.kts": "include(\":app\")",
            "app/build.gradle.kts": "plugins { id(\"com.android.application\") }",
            "app/src/main/AndroidManifest.xml": "<manifest/>"
        }).items():
            archive.writestr(name, content)
    return output.getvalue()


class FakeIsolatedRunner:
    def __init__(self):
        self.runs = 0

    def check(self):
        pass

    def run(self, source, output):
        self.runs += 1
        assert (source / "settings.gradle.kts").is_file()
        path = output / "project-debug.apk"
        path.write_bytes(b"APK" * 1024)
        return path


class ArchiveTests(unittest.TestCase):
    def test_artifact_symlink_cannot_read_a_gateway_host_file(self):
        with tempfile.TemporaryDirectory() as root:
            private = pathlib.Path(root) / 'private'
            private.write_bytes(b'private host bytes' * 200)
            artifact = pathlib.Path(root) / 'project-debug.apk'
            artifact.symlink_to(private)
            with self.assertRaises(OSError):
                read_artifact(artifact)

    def test_timed_out_docker_cli_removes_only_its_named_container(self):
        builder = RootlessBuilder('example/sdk@sha256:' + 'a' * 64)
        with tempfile.TemporaryDirectory() as root, patch('build_jobs.subprocess.run') as run:
            run.side_effect = [subprocess.TimeoutExpired('docker', 600),
                               subprocess.CompletedProcess([], 0)]
            with self.assertRaises(subprocess.TimeoutExpired):
                builder.run(pathlib.Path(root), pathlib.Path(root))
            invocation = run.call_args_list[0].args[0]
            name = next(arg.split('=', 1)[1] for arg in invocation if arg.startswith('--name='))
            self.assertRegex(name, r'^maximus-debug-[a-f0-9]{32}$')
            self.assertEqual(['docker', 'rm', '--force', name], run.call_args_list[1].args[0])

    def test_only_bounded_android_sources_accepted(self):
        self.assertEqual(3, len(validate_zip(project_zip())))
        for path in ("../../private.txt", "/etc/passwd", "a/../b", "folder\\escape.kt"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                validate_zip(project_zip({
                    "settings.gradle.kts": "",
                    "app/build.gradle.kts": "",
                    path: "secrets"
                }))

    def test_rejects_duplicate_and_symlink_entries(self):
        blob = io.BytesIO()
        with zipfile.ZipFile(blob, "w") as archive:
            archive.writestr("settings.gradle.kts", "")
            archive.writestr("app/build.gradle.kts", "")
            archive.writestr("app/build.gradle.kts", "")
        with self.assertRaises(ValueError):
            validate_zip(blob.getvalue())
        blob = io.BytesIO()
        with zipfile.ZipFile(blob, "w") as archive:
            archive.writestr("settings.gradle.kts", "")
            archive.writestr("app/build.gradle.kts", "")
            link = zipfile.ZipInfo("link")
            link.create_system = 3
            link.external_attr = (0o120777 << 16)
            archive.writestr(link, "private")
        with self.assertRaises(ValueError):
            validate_zip(blob.getvalue())

    def test_builder_must_be_digest_pinned(self):
        with self.assertRaises(ValueError):
            RootlessBuilder("android-sdk:latest")
        assert RootlessBuilder("registry.example.org/sdk@sha256:" + "a" * 64)


class HttpBuildTests(unittest.TestCase):
    def setUp(self):
        self.runner = FakeIsolatedRunner()
        self.coordinator = BuildCoordinator(self.runner)
        self.server = make_server("127.0.0.1", 0, TOKEN, self.coordinator)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)

    def request(self, method, path, payload=None, auth=True):
        headers = {"Content-Type": "application/json"}
        if auth:
            headers["Authorization"] = "Bearer " + TOKEN
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        connection.request(method, path, body=json.dumps(payload) if payload else None, headers=headers)
        response = connection.getresponse()
        data = response.read()
        result = (response.status, data, response.getheader("Cache-Control"))
        connection.close()
        return result

    def payload(self):
        import base64
        blob = project_zip()
        return {
            "project_id": PROJECT, "request_id": str(uuid.uuid4()),
            "source_sha256": hashlib.sha256(blob).hexdigest(),
            "zip_base64": base64.b64encode(blob).decode(), "approved": True
        }

    def test_unauthenticated_requests_cannot_start_builds(self):
        code, _, _ = self.request("POST", "/v1/jobs", self.payload(), auth=False)
        self.assertEqual(401, code)
        self.assertEqual(0, self.runner.runs)

    def test_user_consent_is_required(self):
        payload = self.payload()
        payload["approved"] = False
        code, _, _ = self.request("POST", "/v1/jobs", payload)
        self.assertEqual(400, code)

    def test_authenticated_build_reports_digest_and_never_replays_request(self):
        code, body, _ = self.request("GET", "/v1/capabilities")
        capabilities = json.loads(body)
        self.assertTrue(capabilities["execution_enabled"])
        self.assertIn("jobs.submit", capabilities["supported_operations"])

        payload = self.payload()
        status, body, _ = self.request("POST", "/v1/jobs", payload)
        self.assertEqual(202, status)
        job = json.loads(body)
        for _ in range(100):
            code, body, _ = self.request("GET", "/v1/jobs/" + job["id"])
            state = json.loads(body)
            if state["state"] in ("succeeded", "failed"):
                break
            time.sleep(0.02)
        self.assertEqual("succeeded", state["state"])
        self.assertFalse(state["signed"])
        self.assertEqual(64, len(state["apk_sha256"]))
        code, binary, _ = self.request("GET", "/v1/jobs/" + job["id"] + "/artifact")
        self.assertEqual(200, code)
        self.assertEqual(state["apk_sha256"], hashlib.sha256(binary).hexdigest())
        code, body, _ = self.request("POST", "/v1/jobs", payload)
        self.assertEqual(202, code)
        self.assertEqual(job["id"], json.loads(body)["id"])
        self.assertEqual(1, self.runner.runs)

    def test_bad_digest_cannot_dispatch(self):
        payload = self.payload()
        payload["source_sha256"] = "0" * 64
        code, _, _ = self.request("POST", "/v1/jobs", payload)
        self.assertEqual(400, code)
        self.assertEqual(0, self.runner.runs)


if __name__ == "__main__":
    unittest.main()
