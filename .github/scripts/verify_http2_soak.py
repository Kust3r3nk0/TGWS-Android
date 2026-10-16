#!/usr/bin/env python3
"""Require the real native-MTProto/TLS/H2 soak, including reset and resource evidence."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

CLASS = 'io.github.kust3r3nk0.tgwsandroid.core.Http2SoakTest'
METHOD = 'concurrentNativeMediaSessionsUseH2ThroughNetworkResetsWithoutCipherReplay'
FIELDS = {'seconds', 'completedPackets', 'completedSessions', 'resets', 'expectedDisconnects',
          'maxSessions', 'maxH2Sessions', 'maxHttpCalls', 'totalHttpRequests', 'peakHeapBytes'}


def verify(directory, seconds):
    reports = list(Path(directory).rglob('TEST-*Http2SoakTest.xml'))
    if len(reports) != 1:
        raise ValueError('Expected exactly one H2 soak JUnit report')
    root = ET.parse(reports[0]).getroot()
    cases = list(root.iter('testcase'))
    if (len(cases) != 1 or root.get('name') != CLASS or cases[0].get('name') != METHOD or
            cases[0].get('classname') != CLASS):
        raise ValueError('Missing or duplicate required H2 soak testcase')
    if any(int(root.get(key, '0')) for key in ['failures', 'errors', 'skipped']):
        raise ValueError('H2 soak did not pass')
    if any(cases[0].find(key) is not None for key in ['failure', 'error', 'skipped']):
        raise ValueError('H2 soak testcase did not pass')
    lines = [line for node in root.iter('system-out') for line in (node.text or '').splitlines()
             if line.startswith('H2_SOAK ')]
    if len(lines) != 1:
        raise ValueError('Expected exactly one measured H2 soak summary')
    pairs = re.findall(r'(\w+)=(\d+)', lines[0])
    stats = {key: int(value) for key, value in pairs}
    if len(pairs) != len(FIELDS) or set(stats) != FIELDS:
        raise ValueError('Invalid or incomplete H2 soak summary')
    if stats['seconds'] != seconds or float(cases[0].get('time', '0')) < seconds:
        raise ValueError('H2 soak duration was not executed')
    if not (stats['completedPackets'] >= stats['completedSessions'] > 0 and stats['resets'] > 0 and
            0 < stats['maxSessions'] <= 8 and 0 < stats['maxH2Sessions'] <= 8 and
            0 < stats['maxHttpCalls'] <= 16 and stats['totalHttpRequests'] > stats['completedPackets'] and
            stats['peakHeapBytes'] > 0):
        raise ValueError('H2 soak did not exercise required paths or exceeded resource bounds')
    return stats


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--results', default='app/build/test-results/testDebugUnitTest')
    parser.add_argument('--seconds', type=int, required=True)
    parser.add_argument('--output')
    args = parser.parse_args()
    try:
        stats = verify(args.results, args.seconds)
    except (ValueError, ET.ParseError) as error:
        parser.exit(1, str(error) + '\n')
    if args.output:
        Path(args.output).write_text(json.dumps(stats, indent=2) + '\n')
    print('H2 soak verified: ' + json.dumps(stats, sort_keys=True))


if __name__ == '__main__':
    main()
