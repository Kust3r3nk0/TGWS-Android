#!/usr/bin/env python3
"""Reject empty, incomplete or failing Android instrumentation reports."""
import argparse
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def expected_tests(source):
    expected = set()
    for path in Path(source).rglob('*.kt'):
        text = path.read_text()
        package = re.search(r'^package\s+(\S+)', text, re.M)
        classname = re.search(r'^class\s+(\w+)', text, re.M)
        methods = re.findall(r'^\s*@Test\s+fun\s+(\w+)\s*\(', text, re.M)
        if methods and (not package or not classname):
            raise ValueError('Cannot identify instrumentation class: ' + str(path))
        for method in methods:
            expected.add((package[1] + '.' + classname[1], method))
    if not expected:
        raise ValueError('No required instrumentation tests found in source')
    return expected


def verify(source, results):
    expected = expected_tests(source)
    observed = []
    reports = list(Path(results).rglob('TEST-*.xml'))
    if not reports:
        raise ValueError('No JUnit XML report: instrumentation did not complete')
    for path in reports:
        root = ET.parse(path).getroot()
        for suite in root.iter():
            if suite.tag in ('testsuite', 'testsuites'):
                if any(int(suite.get(key, '0')) for key in ('failures', 'errors', 'skipped')):
                    raise ValueError('Instrumentation contains failures, errors or skipped tests')
        for case in root.iter('testcase'):
            if any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped')):
                raise ValueError('Instrumentation testcase did not pass')
            observed.append((case.get('classname'), case.get('name')))
    if len(observed) != len(set(observed)):
        raise ValueError('Duplicate testcases cannot substitute for required tests')
    if set(observed) != expected:
        missing = sorted(expected - set(observed))
        extra = sorted(set(observed) - expected)
        raise ValueError(f'Incomplete instrumentation: missing={missing}, unexpected={extra}')
    return len(observed)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', default='app/src/androidTest/java')
    parser.add_argument('--results', default='app/build/outputs/androidTest-results/connected')
    args = parser.parse_args()
    try:
        count = verify(args.source, args.results)
    except (ValueError, ET.ParseError) as error:
        parser.exit(1, str(error) + '\n')
    print(f'Instrumentation verified: all {count} required tests passed, zero skipped')


if __name__ == '__main__':
    main()
