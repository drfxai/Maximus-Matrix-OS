"""Fail closed when a replacement would break Android in-place upgrades."""
from pathlib import Path
import re
import sys

def identity(badging):
    line = badging.splitlines()[0]
    return dict(re.findall(r"(name|versionCode|versionName)='([^']+)'", line))

def validate(old, new, old_cert, new_cert):
    if new.get('name') != 'ai.drfx.maximus.matrixai' or old.get('name') != new.get('name'):
        raise ValueError('Application ID changed')
    if old.get('versionName') != '1.0.0' or new.get('versionName') != '1.0.0':
        raise ValueError('Public version must remain 1.0.0')
    if int(new['versionCode']) <= int(old['versionCode']):
        raise ValueError('Replacement versionCode must increase')
    digest = r'^Signer #\d+ certificate SHA-256 digest: (.+)$'
    prior = re.findall(digest, old_cert, re.M)
    current = re.findall(digest, new_cert, re.M)
    if not prior or prior != current:
        raise ValueError('Permanent signing certificate mismatch')

if __name__ == '__main__':
    name = sys.argv[1]
    validate(identity(Path(f'previous/{name}.badging').read_text()),
             identity(Path(f'dist/{name}.badging').read_text()),
             Path(f'previous/{name}.certs').read_text(),
             Path(f'dist/{name}.certs').read_text())
