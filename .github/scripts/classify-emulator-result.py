"""Describe evidence; never convert a transport or test failure into success."""
import json
from pathlib import Path
import sys


def classify(directory, status):
    state = (directory / 'device-state.txt').read_text(errors='replace').strip()
    live = (directory / 'live-device.log').read_text(errors='replace')
    if status == 0:
        return 'passed'
    if state != 'device':
        return 'infrastructure-device-lost'
    if 'FATAL EXCEPTION' in live:
        return 'application-or-test-process-crash'
    return 'test-failure-requires-report-review'


if __name__ == '__main__':
    directory = Path(sys.argv[1])
    status = int(sys.argv[2])
    result = {'test_exit_status': status, 'classification': classify(directory, status),
              'release_gate_passed': status == 0}
    (directory / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
