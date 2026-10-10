"""Exercise publication ordering and rollback with a deterministic GitHub CLI fake."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

SCRIPT = Path('.github/scripts/publish-release.sh').resolve()
FAKE_GH = '''#!/usr/bin/env python3
import json, os, pathlib, shutil, sys
args = sys.argv[1:]
with open('gh-calls.jsonl', 'a') as f: f.write(json.dumps(args)+'\\n')
if args[:2] == ['release','upload'] and any(a.startswith('dist/') for a in args) and os.environ.get('FAIL_UPLOAD') == '1': sys.exit(1)
if args[:2] == ['release','download']:
    for name in (os.environ['ARM64_APK'],os.environ['UNIVERSAL_APK'],'SHA256SUMS'):
        shutil.copyfile('dist/'+name,'published/'+name)
if args[:2] == ['release','view']: print(json.dumps({'isDraft':False,'isPrerelease':False}))
'''

class PublicationTests(unittest.TestCase):
    def run_case(self, verified=True, fail=False):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for folder in ('bin','dist','previous'): (root/folder).mkdir()
            gh = root/'bin/gh'; gh.write_text(FAKE_GH); gh.chmod(0o755)
            names = ('arm64.apk','universal.apk')
            for name in names:
                (root/'dist'/name).write_bytes(b'new validated fixture')
                (root/'previous'/name).write_bytes(b'old validated fixture')
            (root/'previous/SHA256SUMS').write_text('old checksum fixture')
            (root/'previous/release-notes.md').write_text('Previous release')
            (root/'dist/universal.apk.badging').write_text("package: name='ai.drfx.maximus.matrixai' versionCode='10003' versionName='1.0.0'")
            import hashlib
            checksum=hashlib.sha256(b'new validated fixture').hexdigest()
            (root/'dist/SHA256SUMS').write_text(''.join(f'{checksum}  {name}\n' for name in names))
            if verified: (root/'dist/upgrade-verified').touch()
            env=dict(os.environ, PATH=str(root/'bin')+os.pathsep+os.environ['PATH'], RELEASE_TAG='v1.0.0',ARM64_APK=names[0],UNIVERSAL_APK=names[1],GITHUB_SHA='tested-commit',GITHUB_REPOSITORY='owner/repo',GITHUB_STEP_SUMMARY=str(root/'summary'),FAIL_UPLOAD=str(int(fail)))
            result=subprocess.run(['bash',str(SCRIPT)],cwd=root,env=env,capture_output=True,text=True)
            import json
            calls=[json.loads(s) for s in (root/'gh-calls.jsonl').read_text().splitlines()] if (root/'gh-calls.jsonl').exists() else []
            return result,calls
    def test_unverified_build_never_writes_release(self):
        result,calls=self.run_case(verified=False)
        self.assertNotEqual(result.returncode,0); self.assertEqual(calls,[])
    def test_upload_failure_restores_old_assets_without_retargeting(self):
        result,calls=self.run_case(fail=True)
        self.assertNotEqual(result.returncode,0)
        self.assertTrue(any(c[:2]==['release','upload'] and 'previous/arm64.apk' in c for c in calls))
        self.assertFalse(any(c[0]=='api' for c in calls))
    def test_success_verifies_download_before_existing_tag_patch(self):
        result,calls=self.run_case()
        self.assertEqual(result.returncode,0,result.stderr)
        download=next(i for i,c in enumerate(calls) if c[:2]==['release','download'])
        patch=next(i for i,c in enumerate(calls) if c[0]=='api')
        self.assertLess(download,patch)
        self.assertFalse(any('delete' in c or 'create' in c for c in calls))

unittest.main()
