import importlib.util
import unittest
spec = importlib.util.spec_from_file_location('identity', '.github/scripts/verify-apk-identity.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class UpgradeIdentityTests(unittest.TestCase):
    def setUp(self):
        self.old = dict(name='ai.drfx.maximus.matrixai', versionName='1.0.0', versionCode='10002')
        self.new = dict(self.old, versionCode='10003')
        self.cert = 'Signer #1 certificate SHA-256 digest: abc\n'
    def test_matching_identity_allows_upgrade(self):
        module.validate(self.old, self.new, self.cert, self.cert)
    def test_nonincreasing_code_rejected(self):
        with self.assertRaises(ValueError): module.validate(self.old, self.old, self.cert, self.cert)
    def test_changed_signer_rejected(self):
        with self.assertRaises(ValueError): module.validate(self.old, self.new, self.cert, self.cert.replace('abc', 'def'))
    def test_missing_signer_rejected(self):
        with self.assertRaises(ValueError): module.validate(self.old, self.new, '', '')
    def test_changed_app_id_rejected(self):
        with self.assertRaises(ValueError): module.validate(self.old, dict(self.new, name='other.app'), self.cert, self.cert)
    def test_changed_public_version_rejected(self):
        with self.assertRaises(ValueError): module.validate(self.old, dict(self.new, versionName='1.0.1'), self.cert, self.cert)

unittest.main()
