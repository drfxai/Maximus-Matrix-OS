"""Owner-private durable request identities. Never stores source archives or credentials."""
import re
import json
import os
import pathlib
import sqlite3
import stat
import uuid


UUID_PATTERN = r'[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}'


class JobJournal:
    MAX_REQUESTS = 100_000

    def __init__(self, directory):
        self.directory = pathlib.Path(directory).absolute()
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        info = self.directory.lstat()
        if (not stat.S_ISDIR(info.st_mode) or self.directory.is_symlink() or
                (os.name == 'posix' and (info.st_uid != os.getuid() or info.st_mode & 0o077))):
            raise ValueError('Worker state directory must be private and owned by this user.')
        self.owner_file = None
        self.connection = None
        try:
            fd = os.open(self.directory / 'owner.lock', os.O_CREAT | os.O_RDWR |
                         getattr(os, 'O_NOFOLLOW', 0), 0o600)
            self.owner_file = os.fdopen(fd, 'r+b')
            if os.name == 'posix':
                import fcntl
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            elif os.name == 'nt':
                import msvcrt
                self.owner_file.write(b'0')
                self.owner_file.flush()
                self.owner_file.seek(0)
                msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)
            else:
                raise RuntimeError('Exclusive worker ownership is unsupported on this platform.')
            database = self.directory / 'jobs.sqlite3'
            if database.is_symlink():
                raise ValueError('Worker journal cannot be a symlink.')
            self.connection = sqlite3.connect(database, check_same_thread=False)
            if os.name == 'posix':
                database.chmod(0o600)
            self.connection.execute('PRAGMA synchronous=FULL')
            self.connection.execute('CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)')
            self.connection.execute('CREATE TABLE IF NOT EXISTS jobs (request_id TEXT UNIQUE NOT NULL, id TEXT PRIMARY KEY, payload TEXT NOT NULL)')
            self.connection.execute('INSERT OR IGNORE INTO metadata VALUES (?, ?)', ('owner_id', uuid.uuid4().hex))
            self.connection.commit()
            self.owner_id = self.connection.execute("SELECT value FROM metadata WHERE key='owner_id'").fetchone()[0]
            if not isinstance(self.owner_id, str) or not re.fullmatch(r'[a-f0-9]{32}', self.owner_id):
                raise ValueError('Invalid journal owner identity.')
        except Exception:
            self.close()
            raise

    def load(self):
        rows = self.connection.execute('SELECT request_id, id, payload FROM jobs').fetchmany(self.MAX_REQUESTS + 1)
        if len(rows) > self.MAX_REQUESTS:
            raise ValueError('Journal capacity exceeded.')
        result = []
        for key, identity, payload in rows:
            if not isinstance(payload, str) or len(payload) > 8192:
                raise ValueError('Oversized worker journal record.')
            item = json.loads(payload)
            if (not isinstance(item, dict) or not isinstance(identity, str) or not isinstance(key, str) or
                    any(not isinstance(item.get(field), str) for field in
                        ('id', 'container_name', 'project_id', 'source_sha256', 'root', 'apk_path', 'apk_sha256', 'error', 'state')) or
                    not re.fullmatch(r'[a-f0-9]{32}', identity) or
                    item.get('id') != identity or item.get('container_name') != 'maximus-debug-' + identity or
                    not re.fullmatch(UUID_PATTERN, key) or
                    not re.fullmatch(UUID_PATTERN, item.get('project_id', '')) or
                    not re.fullmatch(r'[a-f0-9]{64}', item.get('source_sha256', '')) or
                    item.get('state') not in ('queued', 'running', 'succeeded', 'failed', 'recovery_required') or
                    type(item.get('created_at')) not in (int, float) or not 0 <= item['created_at'] <= 1e15):
                raise ValueError('Invalid worker journal record; execution blocked.')
            result.append((key, item))
        if sum(item['state'] in ('queued', 'running', 'recovery_required') for _, item in result) > 1:
            raise ValueError('Journal has conflicting active owners; execution blocked.')
        return result

    def save(self, key, item):
        with self.connection:
            self.connection.execute('INSERT INTO jobs VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload',
                                    (key, item['id'], json.dumps(item)))

    def close(self):
        if self.connection is not None:
            self.connection.close()
            self.connection = None
        if self.owner_file is not None:
            self.owner_file.close()
            self.owner_file = None
