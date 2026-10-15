import concurrent.futures
import hashlib
import json
import pathlib
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import uuid
from unittest.mock import patch

from build_jobs import BuildCoordinator, RootlessBuilder
from job_journal import JobJournal
from tests.test_build_jobs import FakeIsolatedRunner, PROJECT, project_zip


class JournalTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.runner = FakeIsolatedRunner()
        self.coordinator = BuildCoordinator(self.runner, self.directory.name)
        self.blob = project_zip()
        self.digest = hashlib.sha256(self.blob).hexdigest()
        self.key = str(uuid.uuid4())

    def tearDown(self):
        self.coordinator.close()
        self.directory.cleanup()

    def submit(self, key=None):
        return self.coordinator.submit(PROJECT, key or self.key, self.digest, self.blob, True)

    def completed(self, job_id):
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            state = self.coordinator.status(job_id)
            if state['state'] not in ('queued', 'running'):
                return state
            time.sleep(0.005)
        self.fail('Build did not finish.')

    def reopen(self, runner=None):
        self.coordinator.close()
        self.coordinator = BuildCoordinator(runner or self.runner, self.directory.name)

    def test_restart_retains_identity_artifact_and_conflicting_digest_is_rejected(self):
        job = self.submit()
        self.assertEqual('succeeded', self.completed(job['id'])['state'])
        self.reopen()
        self.assertEqual(job['id'], self.submit()['id'])
        self.assertTrue(self.coordinator.status(job['id'])['artifact_ready'])
        self.assertIsNotNone(self.coordinator.artifact(job['id']))
        changed = project_zip({'settings.gradle.kts': '', 'app/build.gradle.kts': '// changed'})
        with self.assertRaises(ValueError):
            self.coordinator.submit(PROJECT, self.key, hashlib.sha256(changed).hexdigest(), changed, True)
        self.assertEqual(1, self.runner.runs)

    def test_artifact_expiry_does_not_reset_request_identity(self):
        job = self.submit()
        self.completed(job['id'])
        with patch('build_jobs.time.time', return_value=time.time() + 3601):
            self.assertFalse(self.coordinator.status(job['id'])['artifact_ready'])
            self.assertIsNone(self.coordinator.artifact(job['id']))
        self.reopen()
        self.assertEqual(job['id'], self.submit()['id'])
        self.assertEqual(1, self.runner.runs)

    def test_restart_missing_artifact_never_claims_ready_or_replays(self):
        job = self.submit()
        self.completed(job['id'])
        pathlib.Path(self.coordinator.jobs[job['id']]['apk_path']).unlink()
        self.reopen()
        self.assertFalse(self.coordinator.status(job['id'])['artifact_ready'])
        self.assertIsNone(self.coordinator.artifact(job['id']))
        self.assertEqual(job['id'], self.submit()['id'])
        self.assertEqual(1, self.runner.runs)

    def test_live_missing_artifact_is_not_reported_ready(self):
        job = self.submit()
        self.completed(job['id'])
        pathlib.Path(self.coordinator.jobs[job['id']]['apk_path']).unlink()
        self.assertFalse(self.coordinator.status(job['id'])['artifact_ready'])

    def test_tampered_retained_path_cannot_serve_a_host_file(self):
        job = self.submit()
        self.completed(job['id'])
        with tempfile.NamedTemporaryFile() as private:
            private.write(b'private host file' * 200)
            private.flush()
            item = self.coordinator.jobs[job['id']]
            item['apk_path'] = private.name
            item['apk_sha256'] = hashlib.sha256(pathlib.Path(private.name).read_bytes()).hexdigest()
            self.coordinator._save(item)
            self.reopen()
            self.assertFalse(self.coordinator.status(job['id'])['artifact_ready'])
            self.assertIsNone(self.coordinator.artifact(job['id']))

    def test_retained_output_symlink_is_not_followed(self):
        job = self.submit()
        self.completed(job['id'])
        item = self.coordinator.jobs[job['id']]
        output = pathlib.Path(item['root']) / 'output'
        pathlib.Path(item['apk_path']).unlink()
        output.rmdir()
        with tempfile.TemporaryDirectory() as private:
            (pathlib.Path(private) / 'project-debug.apk').write_bytes(b'APK' * 1024)
            output.symlink_to(private, target_is_directory=True)
            self.reopen()
            self.assertFalse(self.coordinator.status(job['id'])['artifact_ready'])
            self.assertIsNone(self.coordinator.artifact(job['id']))

    def test_simultaneous_duplicate_submissions_dispatch_once(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            jobs = list(pool.map(lambda _: self.submit(), range(16)))
        self.completed(jobs[0]['id'])
        self.assertEqual(1, len({job['id'] for job in jobs}))
        self.assertEqual(1, self.runner.runs)

    def test_process_ownership_is_exclusive(self):
        script = ('from job_journal import JobJournal; import sys; '
                  'JobJournal(sys.argv[1])')
        result = subprocess.run([sys.executable, '-c', script, self.directory.name],
                                capture_output=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.coordinator.close()
        journal = JobJournal(self.directory.name)
        journal.close()

    def interrupted_record(self, state):
        identity = uuid.uuid4().hex
        item = dict(id=identity, project_id=PROJECT, source_sha256=self.digest,
                    state=state, error='', apk_sha256='', apk_path='',
                    created_at=time.time(), root='', container_name='maximus-debug-' + identity)
        self.coordinator.close()
        journal = JobJournal(self.directory.name)
        journal.save(self.key, item)
        journal.close()
        return item

    def test_restart_marks_queued_and_running_failed_without_replay(self):
        for state in ('queued', 'running'):
            with self.subTest(state=state):
                self.key = str(uuid.uuid4())
                item = self.interrupted_record(state)
                self.coordinator = BuildCoordinator(self.runner, self.directory.name)
                self.assertEqual('failed', self.coordinator.status(item['id'])['state'])
                self.assertEqual(item['id'], self.submit()['id'])
                self.assertEqual(0, self.runner.runs)

    def test_unconfirmed_recovery_blocks_new_dispatch_until_recovery_succeeds(self):
        item = self.interrupted_record('running')
        broken = FakeIsolatedRunner()
        broken.cleanup = lambda *_: (_ for _ in ()).throw(RuntimeError('offline'))
        self.coordinator = BuildCoordinator(broken, self.directory.name)
        self.assertEqual('recovery_required', self.coordinator.status(item['id'])['state'])
        with self.assertRaises(RuntimeError):
            self.submit(str(uuid.uuid4()))
        self.assertEqual(0, broken.runs)
        with self.assertRaises(RuntimeError):
            self.coordinator.close()
        broken.cleanup = lambda *_: None
        self.reopen()
        self.assertEqual('failed', self.coordinator.status(item['id'])['state'])
        job = self.submit(str(uuid.uuid4()))
        self.completed(job['id'])

    def test_damaged_root_cannot_delete_another_jobs_directory(self):
        other_id = uuid.uuid4().hex
        with tempfile.TemporaryDirectory(prefix='maximus-debug-' + other_id + '-') as other:
            marker = pathlib.Path(other) / 'keep'
            marker.write_text('another job')
            item = self.interrupted_record('running')
            journal = JobJournal(self.directory.name)
            item['root'] = other
            journal.save(self.key, item)
            journal.close()
            self.coordinator = BuildCoordinator(self.runner, self.directory.name)
            self.assertEqual('recovery_required', self.coordinator.status(item['id'])['state'])
            self.assertEqual('another job', marker.read_text())
            with self.assertRaises(RuntimeError):
                self.coordinator.close()
            self.coordinator.jobs[item['id']]['root'] = ''

    def test_corrupt_journal_fails_closed_and_releases_lock(self):
        self.coordinator.close()
        database = pathlib.Path(self.directory.name) / 'jobs.sqlite3'
        database.write_bytes(b'not sqlite')
        with self.assertRaises(sqlite3.DatabaseError):
            BuildCoordinator(self.runner, self.directory.name)
        database.unlink()
        self.coordinator = BuildCoordinator(self.runner, self.directory.name)
        self.assertEqual(0, self.runner.runs)

    def test_close_refuses_to_release_ownership_while_building(self):
        started, release = threading.Event(), threading.Event()
        original = self.runner.run
        def waiting(*args, **kwargs):
            started.set()
            release.wait(timeout=3)
            return original(*args, **kwargs)
        self.runner.run = waiting
        job = self.submit()
        self.assertTrue(started.wait(timeout=1))
        try:
            with self.assertRaises(RuntimeError):
                self.coordinator.close()
            with self.assertRaises(OSError):
                JobJournal(self.directory.name)
        finally:
            release.set()
        self.completed(job['id'])

    def test_journal_write_failure_never_dispatches(self):
        with patch.object(self.coordinator.journal, 'save', side_effect=OSError('disk full')):
            with self.assertRaises(RuntimeError):
                self.submit()
        self.assertEqual(0, self.runner.runs)
        with self.assertRaises(RuntimeError):
            self.submit(str(uuid.uuid4()))


class ContainerOwnershipTests(unittest.TestCase):
    def test_foreign_container_is_never_removed(self):
        builder = RootlessBuilder('example/sdk@sha256:' + 'a' * 64)
        with patch('build_jobs.subprocess.run') as run:
            run.side_effect = [subprocess.CompletedProcess([], 0, stdout='c' * 12),
                               subprocess.CompletedProcess([], 0, stdout='foreign-owner')]
            with self.assertRaises(RuntimeError):
                builder.cleanup('maximus-debug-' + 'a' * 32, 'b' * 32)
            self.assertFalse(any('rm' in call.args[0] for call in run.call_args_list))

    def test_unavailable_daemon_is_not_treated_as_missing_container(self):
        builder = RootlessBuilder('example/sdk@sha256:' + 'a' * 64)
        with patch('build_jobs.subprocess.run', side_effect=subprocess.CalledProcessError(1, 'docker')):
            with self.assertRaises(subprocess.CalledProcessError):
                builder.cleanup('maximus-debug-' + 'a' * 32, 'b' * 32)

    def test_absent_container_requires_successful_listing(self):
        builder = RootlessBuilder('example/sdk@sha256:' + 'a' * 64)
        with patch('build_jobs.subprocess.run', return_value=subprocess.CompletedProcess([], 0, stdout='')) as run:
            builder.cleanup('maximus-debug-' + 'a' * 32, 'b' * 32)
            self.assertEqual(1, run.call_count)
