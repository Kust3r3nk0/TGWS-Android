#!/usr/bin/env python3
"""Check 0.3.4 -> current install -r on the explicit CI emulator, never a phone."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.request

BASELINE_URL = 'https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.4-alpha-debug/TGWS-Android-0.3.4-debug.apk'
BASELINE_SHA = '453c6676053c439709508a16024267f502f079b8ce7b8b8cc9952c2bd997eab8'
PACKAGE = 'io.github.kust3r3nk0.tgwsandroid'
RUNNER = PACKAGE + '.UpgradeInstrumentation'


def check_runner(manifest):
    # AGP may replace instrumentation declarations with testInstrumentationRunner.
    if not re.search(r'android:name\([^\n]*="' + re.escape(RUNNER) + r'"', manifest):
        raise ValueError('Upgrade runner is absent from the compiled test APK')


def checked_phase(output, phase):
    if not re.search(r'^INSTRUMENTATION_RESULT: TGWS_UPGRADE=' + re.escape(phase) + r':passed\s*$', output, re.M):
        raise ValueError('Upgrade phase did not execute successfully: ' + phase)
    if re.findall(r'^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$', output, re.M) != ['0']:
        raise ValueError('Upgrade instrumentation did not finish successfully')


def emulator_serial(value):
    if not re.fullmatch(r'emulator-\d+', value):
        raise ValueError('An explicit CI emulator is required')
    return value


def run(*args, allow_failure=False):
    result = subprocess.run(args, text=True, capture_output=True, timeout=120)
    if result.returncode and not allow_failure:
        operation = next((name for name in ('instrument', 'install', 'uninstall', 'sign') if name in args), 'command')
        raise ValueError('Upgrade ' + operation + ' failed: ' + Path(args[0]).name)
    return result.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True, type=emulator_serial)
    parser.add_argument('--sdk', required=True)
    parser.add_argument('--keystore', required=True)
    parser.add_argument('--prepare', action='store_true')
    args = parser.parse_args()
    output = Path('app/build/reports/apk-upgrade')
    stage = Path('app/build/upgrade-fixtures')
    stage.mkdir(parents=True, exist_ok=True)
    old = stage / 'baseline.apk'
    signed = stage / 'baseline-ci-signed.apk'
    if args.prepare:
        check_runner(run(str(Path(args.sdk) / 'build-tools/36.0.0/aapt'), 'dump', 'xmltree',
                         'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk', 'AndroidManifest.xml'))
        with urllib.request.urlopen(BASELINE_URL, timeout=60) as response:
            payload = response.read(100 * 1024 * 1024 + 1)
        if hashlib.sha256(payload).hexdigest() != BASELINE_SHA:
            raise ValueError('Baseline release checksum mismatch')
        old.write_bytes(payload)
        # Both APKs use the temporary CI debug key. The preserved publishing key never enters CI.
        run(str(Path(args.sdk) / 'build-tools/36.0.0/apksigner'), 'sign', '--ks', args.keystore,
            '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
            '--v1-signing-enabled', 'false', '--v4-signing-enabled', 'false', '--out', str(signed), str(old))
        print('Pinned 0.3.4 APK prepared for emulator upgrade test')
        return
    if not signed.is_file():
        raise ValueError('Missing prepared upgrade fixture')
    adb = [str(Path(args.sdk) / 'platform-tools/adb'), '-s', args.serial]
    current = Path('app/build/outputs/apk/debug/app-debug.apk')
    tests = Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    version = re.search(r'versionCode\s*=\s*(\d+)', Path('app/build.gradle.kts').read_text()).group(1)
    run(*adb, 'uninstall', PACKAGE + '.test', allow_failure=True)
    run(*adb, 'uninstall', PACKAGE, allow_failure=True)
    run(*adb, 'install', '-t', str(signed))
    run(*adb, 'install', '-t', str(tests))
    runner = PACKAGE + '.test/' + PACKAGE + '.UpgradeInstrumentation'
    checked_phase(run(*adb, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'phase', 'seed',
                      '-e', 'version', '10', runner), 'seed')
    run(*adb, 'install', '-r', '-t', str(current))
    checked_phase(run(*adb, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'phase', 'verify',
                      '-e', 'version', version, runner), 'verify')
    output.mkdir(parents=True, exist_ok=True)
    (output / 'result.json').write_text(json.dumps({
        'baseline_version': '0.3.4-alpha', 'baseline_sha256': BASELINE_SHA,
        'target_version_code': int(version), 'install_replace': True,
        'secret_preserved': True, 'ciphertext_unchanged': True, 'keystore_preserved': True,
        'settings_preserved': True, 'signing': 'temporary CI debug key on both APKs',
        'physical_testing': 'owner_pending', 'result': 'passed'}, indent=2) + '\n')
    print('APK upgrade verified: encrypted credentials, port, relay and appearance preserved')


if __name__ == '__main__':
    main()
