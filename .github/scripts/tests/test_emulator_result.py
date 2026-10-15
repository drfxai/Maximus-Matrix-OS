import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('result', '.github/scripts/classify-emulator-result.py')
result = importlib.util.module_from_spec(spec)
spec.loader.exec_module(result)


class EmulatorResultTest(unittest.TestCase):
    def classify(self, status, state='device', log=''):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            (path / 'device-state.txt').write_text(state)
            (path / 'live-device.log').write_text(log)
            return result.classify(path, status)

    def test_disconnected_emulator_is_not_an_assertion_failure(self):
        self.assertEqual(self.classify(1, "error: device 'emulator-5554' not found"),
                         'infrastructure-device-lost')

    def test_connected_crash_requires_app_investigation(self):
        self.assertEqual(self.classify(1, log='FATAL EXCEPTION: main'),
                         'application-or-test-process-crash')

    def test_connected_assertion_is_not_hidden(self):
        self.assertEqual(self.classify(1), 'test-failure-requires-report-review')

    def test_success_requires_successful_test_exit(self):
        self.assertEqual(self.classify(0), 'passed')
