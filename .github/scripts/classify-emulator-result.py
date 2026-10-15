"""Describe evidence; never convert a transport or test failure into success."""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def read_evidence(directory, name):
    try:
        return (directory / name).read_text(errors='replace')
    except OSError:
        return ''


def has_assertion_failure(directory):
    # Transport failures also appear as JUnit failures. Only label explicit
    # assertion exceptions as assertions; interrupted runs need other evidence.
    for report in (directory / 'test-results').rglob('*.xml'):
        try:
            root = ET.parse(report).getroot()
        except (OSError, ET.ParseError):
            continue
        for node in root.iter():
            if node.tag not in ('failure', 'error'):
                continue
            evidence = node.get('type', '') + ' ' + node.get('message', '') + ' ' + (node.text or '')
            if 'AssertionError' in evidence or 'AssertionFailedError' in evidence:
                return True
    return False


def classify(directory, status):
    state = read_evidence(directory, 'device-state.txt').strip()
    live = read_evidence(directory, 'live-device.log')
    # Preserve proven test failures even when the emulator subsequently dies.
    if has_assertion_failure(directory):
        return 'application-or-test-assertion-failure'
    # logcat includes system and third-party processes. A generic fatal line
    # is only a diagnostic for a failed test run, not grounds to reject a
    # completed passing run by itself. Explicit JUnit assertions still win.
    if status != 0 and 'FATAL EXCEPTION' in live:
        return 'application-or-test-process-crash'
    if state and state != 'device':
        return 'infrastructure-device-lost'
    if not state:
        return 'unknown-missing-device-evidence'
    if status == 0:
        return 'passed'
    return 'test-failure-requires-report-review'


if __name__ == '__main__':
    directory = Path(sys.argv[1])
    status = int(sys.argv[2])
    classification = classify(directory, status)
    passed = status == 0 and classification == 'passed'
    result = {'test_exit_status': status, 'classification': classification,
              'release_gate_passed': passed}
    (directory / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
    sys.exit(0 if passed else 1)
