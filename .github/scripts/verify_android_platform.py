#!/usr/bin/env python3
"""Keep the declared platform, localized branding, resources and CI Android 16+."""
import argparse
import ast
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

MIN_API = 36
MIN_ANDROID = 16


def verify(root):
    root = Path(root)
    gradle = (root / 'app/build.gradle.kts').read_text()
    if re.findall(r'\bminSdk\s*=\s*(\d+)', gradle) != [str(MIN_API)]:
        raise ValueError('Application minimum must be API 36')
    for locale in ['values', 'values-en']:
        path = root / 'app/src/main/res' / locale / 'strings.xml'
        values = [n.text for n in ET.parse(path).getroot().findall('string') if n.get('name') == 'version_label']
        if values != [f'Android {MIN_ANDROID}+ · %1$s']:
            raise ValueError('About label differs from the platform minimum: ' + locale)
    workflow = (root / '.github/workflows/android-instrumented.yml').read_text()
    match = re.search(r'\bapi:\s*\[([^\]]+)\]', workflow)
    if not match:
        raise ValueError('Missing Android CI matrix')
    apis = [str(value) for value in ast.literal_eval('[' + match[1] + ']')]
    if not apis or any(not re.fullmatch(r'[0-9]+(?:\.[0-9]+)?', api) or
                       int(api.split('.')[0]) < MIN_API for api in apis):
        raise ValueError('Android CI matrix contains an unsupported platform')
    files = list((root / 'docs').rglob('*')) + list(root.glob('*.md'))
    files += list((root / 'app/src/main').rglob('*'))
    findings = []
    checked = 0
    for path in files:
        if not path.is_file() or path.suffix not in ['.md', '.svg', '.xml', '.kt', '.java']:
            continue
        checked += 1
        text = path.read_text()
        obsolete = (any(int(n) < MIN_ANDROID for n in re.findall(r'\bAndroid[ -]+(\d+)\b(?![.\d])', text, re.I)) or
                    any(int(n) < MIN_API for n in re.findall(r'\bAPI\s*(\d+)\b', text)) or
                    any(int(n) < MIN_API for n in re.findall(r'\bminSdk\s*[=:]?\s*(\d+)\b', text)))
        if obsolete:
            findings.append(str(path.relative_to(root)))
    for path in (root / 'app/src/main/res').iterdir():
        match = re.search(r'-v(\d+)(?:-|$)', path.name)
        if path.is_dir() and match and int(match[1]) < MIN_API:
            findings.append(str(path.relative_to(root)))
    if findings:
        raise ValueError('Retired platform resources or declarations: ' + ', '.join(sorted(set(findings))))
    return dict(schema=1, min_android=MIN_ANDROID, min_sdk=MIN_API, instrumented_apis=apis,
                about_locales=['ru', 'en'], checked_files=checked)


def verify_apk(apk, sdk):
    aapt = Path(sdk) / 'build-tools/36.0.0/aapt'
    output = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    if re.findall(r"^sdkVersion:'([0-9]+)'$", output, re.M) != [str(MIN_API)]:
        raise ValueError('Built APK minimum differs from API 36')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output', type=Path)
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    result = verify(args.root)
    if args.apk:
        verify_apk(args.apk, os.environ['ANDROID_HOME'])
        result['apk_minimum_verified'] = True
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print('Android 16+ platform contract verified')


if __name__ == '__main__':
    main()
