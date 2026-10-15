import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from xml.sax.saxutils import escape

spec = importlib.util.spec_from_file_location('result', '.github/scripts/classify-emulator-result.py')
result = importlib.util.module_from_spec(spec)
spec.loader.exec_module(result)


class EmulatorResultTest(unittest.TestCase):
    def classify(self, status, state='device', log='', failure=None):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            if state is not None:
                (path / 'device-state.txt').write_text(state)
            (path / 'live-device.log').write_text(log)
            if failure is not None:
                (path / 'test-results').mkdir()
                (path / 'test-results' / 'TEST-ui.xml').write_text(
                    '<testsuite><testcase><failure>' + escape(failure) + '</failure></testcase></testsuite>')
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

    def test_successful_tests_ignore_unrelated_system_process_crash(self):
        self.assertEqual(self.classify(0, log='FATAL EXCEPTION: main\nProcess: com.android.systemui'),
                         'passed')

    def test_unrelated_crash_cannot_hide_missing_or_lost_device(self):
        for state, expected in ((None, 'unknown-missing-device-evidence'),
                                ('offline', 'infrastructure-device-lost')):
            self.assertEqual(self.classify(0, state, log='FATAL EXCEPTION: main'), expected)

    def test_missing_or_empty_evidence_is_unknown_even_on_zero_exit(self):
        for state in (None, ''):
            self.assertEqual(self.classify(0, state), 'unknown-missing-device-evidence')

    def test_device_lost_after_zero_exit_does_not_pass(self):
        self.assertEqual(self.classify(0, 'offline'), 'infrastructure-device-lost')

    def test_proven_assertion_survives_later_device_loss(self):
        self.assertEqual(self.classify(1, 'offline', failure='java.lang.AssertionError: composer overlaps'),
                         'application-or-test-assertion-failure')

    def test_transport_report_does_not_prove_app_assertion(self):
        self.assertEqual(self.classify(1, 'offline', failure='Test run failed to complete. Expected 2 tests, received 1'),
                         'infrastructure-device-lost')

    def test_crash_evidence_survives_later_device_loss(self):
        self.assertEqual(self.classify(1, 'offline', log='FATAL EXCEPTION: main'),
                         'application-or-test-process-crash')

    def test_cli_unknown_emits_result_and_blocks_gate(self):
        with tempfile.TemporaryDirectory() as root:
            completed = subprocess.run(['python3', '.github/scripts/classify-emulator-result.py', root, '0'],
                                       capture_output=True, text=True)
            self.assertEqual(completed.returncode, 1)
            report = json.loads((Path(root) / 'result.json').read_text())
            self.assertFalse(report['release_gate_passed'])
            self.assertEqual(report['classification'], 'unknown-missing-device-evidence')

    def test_cli_confirmed_success_passes(self):
        with tempfile.TemporaryDirectory() as root:
            (Path(root) / 'device-state.txt').write_text('device\n')
            completed = subprocess.run(['python3', '.github/scripts/classify-emulator-result.py', root, '0'],
                                       capture_output=True, text=True)
            self.assertEqual(completed.returncode, 0)
            self.assertTrue(json.loads((Path(root) / 'result.json').read_text())['release_gate_passed'])


class KeyboardScriptEvidenceTest(unittest.TestCase):
    def run_script(self, initial_state, final_state, capture_mode="none"):
        import os
        import shutil
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            scripts = path / '.github/scripts'
            scripts.mkdir(parents=True)
            shutil.copy('.github/scripts/classify-emulator-result.py', scripts)
            (scripts / 'collect-emulator-diagnostics.py').write_text('')
            binaries = path / 'bin'
            binaries.mkdir()
            commands = {
                'adb': '''#!/bin/bash
printf '%s\\n' "$*" >> adb-commands
if [[ "$1" == get-state ]]; then
  if [[ -f adb-state-called ]]; then printf '%s\\n' "$FINAL_STATE"; else touch adb-state-called; printf '%s\\n' "$INITIAL_STATE"; fi
  exit 0
fi
if [[ "$1" == shell && "$2" == rm ]]; then
  for source_path in "${@:4}"; do
    capture="${source_path##*/}"
    if [[ "$source_path" == /data/local/tmp/* ]]; then rm -f "retained/$capture"; else rm -f "external/$capture"; fi
  done
fi
if [[ "$1" == pull ]]; then
  capture="${2##*/}"
  if [[ "$2" == /data/local/tmp/* ]]; then source="retained/$capture"; else source="external/$capture"; fi
  [[ -f "$source" ]] || exit 1
  cp "$source" "$3"
fi
exit 0
''',
                'gradle': '''#!/bin/bash
touch gradle-called
mkdir -p retained external
for capture in home-graph.png chat-focus.png; do
  if [[ "$CAPTURE_MODE" == retained ]]; then printf captured > "retained/maximus-keyboard-$capture"; fi
  if [[ "$CAPTURE_MODE" == external ]]; then printf fallback > "external/$capture"; fi
done
# The retained case models a Gradle uninstall that removed app-owned screenshots.
if [[ "$CAPTURE_MODE" == retained ]]; then rm -rf external; fi
exit 0
''',
                'sudo': '#!/bin/bash\nexit 1\n',
            }
            for name, code in commands.items():
                executable = binaries / name
                executable.write_text(code)
                executable.chmod(0o755)
            emulator = path / 'sdk/emulator/emulator'
            emulator.parent.mkdir(parents=True)
            emulator.write_text('#!/bin/bash\nprintf fake-emulator\n')
            emulator.chmod(0o755)
            # Seed both storage locations to prove cleanup cannot leak a previous run.
            for storage, prefix in (('retained', 'maximus-keyboard-'), ('external', '')):
                (path / storage).mkdir()
                for capture in ('home-graph.png', 'chat-focus.png'):
                    (path / storage / (prefix + capture)).write_text('stale previous run')
            env = dict(os.environ, PATH=str(binaries) + ':' + os.environ['PATH'],
                       ANDROID_HOME=str(path / 'sdk'), INITIAL_STATE=initial_state, FINAL_STATE=final_state,
                       ANDROID_TEST_CLASSES='ai.drfx.maximus.matrixai.ui.KeyboardLayoutTest',
                       CAPTURE_MODE=capture_mode)
            script = Path('.github/scripts/verify-keyboard.sh').resolve()
            completed = subprocess.run(['bash', str(script)], cwd=path, env=env,
                                       capture_output=True, text=True, timeout=15)
            report = json.loads((path / 'keyboard-check/result.json').read_text())
            captures = {name: (path / 'keyboard-check' / name).read_text()
                        for name in ('home-graph.png', 'chat-focus.png')
                        if (path / 'keyboard-check' / name).exists()}
            return completed.returncode, report, (path / 'gradle-called').exists(), captures

    def test_initial_adb_failure_still_emits_evidence_without_running_gradle(self):
        status, report, ran_gradle, _ = self.run_script('offline', 'offline')
        self.assertNotEqual(status, 0)
        self.assertFalse(ran_gradle)
        self.assertFalse(report['release_gate_passed'])
        self.assertEqual(report['classification'], 'infrastructure-device-lost')

    def test_device_loss_after_gradle_success_fails_script_gate(self):
        status, report, ran_gradle, _ = self.run_script('device', 'offline')
        self.assertNotEqual(status, 0)
        self.assertTrue(ran_gradle)
        self.assertFalse(report['release_gate_passed'])

    def test_workspace_captures_survive_gradle_uninstall(self):
        status, _, ran_gradle, captures = self.run_script('device', 'device', 'retained')
        self.assertEqual(status, 0)
        self.assertTrue(ran_gradle)
        self.assertEqual(captures, {'home-graph.png': 'captured', 'chat-focus.png': 'captured'})

    def test_workspace_captures_fall_back_to_app_storage(self):
        status, _, _, captures = self.run_script('device', 'device', 'external')
        self.assertEqual(status, 0)
        self.assertEqual(captures, {'home-graph.png': 'fallback', 'chat-focus.png': 'fallback'})

    def test_previous_workspace_captures_are_cleared_from_both_locations(self):
        status, _, _, captures = self.run_script('device', 'device')
        self.assertEqual(status, 0)
        self.assertEqual(captures, {})
