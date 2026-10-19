#!/usr/bin/env python3
"""Check the Android 16+ baseline -> current install -r on an explicit CI emulator."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.request
import zipfile
from verify_android_platform import verify_apk

BASELINE_URL = 'https://github.com/Kust3r3nk0/TGWS-Android/releases/download/v0.3.6-alpha-debug/TGWS-Android-0.3.6-debug.apk'
BASELINE_SHA = '063373a78944f23265fcee73558d7c5e5a0c2a8b08199ced920faac0fdc75cab'
PACKAGE = 'io.github.kust3r3nk0.tgwsandroid'
RUNNER = PACKAGE + '.UpgradeInstrumentation'


def check_runner(manifest):
    # AGP may replace instrumentation declarations with testInstrumentationRunner.
    if not re.search(r'android:name\([^\n]*="' + re.escape(RUNNER) + r'"', manifest):
        raise ValueError('Upgrade runner is absent from the compiled test APK')


def signer_digest(output):
    matches = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})\s*$', output, re.M)
    if len(matches) != 1:
        raise ValueError('Expected one verified APK signing certificate')
    return matches[0].lower()


def verify_signed_payload(original, signed):
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(signed) as after:
        if set(before.namelist()) != set(after.namelist()) or any(
                before.read(name) != after.read(name) for name in before.namelist()):
            raise ValueError('Upgrade fixture signing changed the APK payload')


def checked_phase(output, phase):
    if not re.search(r'^INSTRUMENTATION_RESULT: TGWS_UPGRADE=' + re.escape(phase) + r':passed\s*$', output, re.M):
        raise ValueError('Upgrade phase did not execute successfully: ' + failure_detail(output))
    # Android Instrumentation success is Activity.RESULT_OK (-1), not shell exit code 0.
    if re.findall(r'^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$', output, re.M) != ['-1']:
        raise ValueError('Upgrade instrumentation did not finish successfully: ' + failure_detail(output))


def failure_detail(output):
    details = []
    for text, category in [('Unable to find instrumentation info', 'RUNNER_NOT_FOUND'),
                           ('Permission Denial', 'PERMISSION_DENIED'), ('not signed with', 'SIGNATURE_MISMATCH'),
                           ('does not have a signature matching', 'SIGNATURE_MISMATCH'),
                           ('Process crashed', 'PROCESS_CRASHED')]:
        if text in output:
            details.append(category)
    details += re.findall(r'^INSTRUMENTATION_RESULT: TGWS_UPGRADE=((?:seed|verify):(?:passed|failed))\s*$', output, re.M)
    details += ['RESULT_' + code for code in re.findall(r'^INSTRUMENTATION_CODE:\s*(-?\d{1,3})\s*$', output, re.M)]
    for stage in ['ENVIRONMENT', 'VERSION', 'EMPTY_SETTINGS', 'SAVE_FIXTURE', 'LOAD_FIXTURE', 'CIPHERTEXT', 'APPEARANCE', 'THEME', 'GLASS', 'TRANSPARENCY']:
        if re.search(r'^INSTRUMENTATION_RESULT: TGWS_UPGRADE_CHECK=' + stage + r'\s*$', output, re.M):
            details.append(stage)
    return ','.join(details) or 'NO_PUBLIC_STATUS'


def emulator_serial(value):
    if not re.fullmatch(r'emulator-\d+', value):
        raise ValueError('An explicit CI emulator is required')
    return value


def run(*args, allow_failure=False):
    result = subprocess.run(args, text=True, capture_output=True, timeout=120)
    if result.returncode and not allow_failure:
        operation = next((name for name in ('instrument', 'install', 'uninstall', 'sign') if name in args), 'command')
        detail = failure_detail(result.stdout + '\n' + result.stderr) if operation == 'instrument' else 'COMMAND_FAILED'
        raise ValueError('Upgrade ' + operation + ' failed: ' + detail)
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
        signer = str(Path(args.sdk) / 'build-tools/36.0.0/apksigner')
        check_runner(run(str(Path(args.sdk) / 'build-tools/36.0.0/aapt'), 'dump', 'xmltree',
                         'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk', 'AndroidManifest.xml'))
        with urllib.request.urlopen(BASELINE_URL, timeout=60) as response:
            payload = response.read(100 * 1024 * 1024 + 1)
        if hashlib.sha256(payload).hexdigest() != BASELINE_SHA:
            raise ValueError('Baseline release checksum mismatch')
        old.write_bytes(payload)
        verify_apk(old, args.sdk)
        print('Pinned Android 16+ baseline downloaded and compiled upgrade runner verified')
        return
    if not old.is_file() or hashlib.sha256(old.read_bytes()).hexdigest() != BASELINE_SHA:
        raise ValueError('Missing prepared upgrade fixture')
    adb = [str(Path(args.sdk) / 'platform-tools/adb'), '-s', args.serial]
    current = stage / 'current-ci-signed.apk'
    tests = stage / 'tests-ci-signed.apk'
    # Prepare all three copies with a known common certificate, preserving every
    # ZIP payload byte; never import the publishing key into CI.
    signer = str(Path(args.sdk) / 'build-tools/36.0.0/apksigner')
    pairs = [(old, signed), (Path('app/build/outputs/apk/debug/app-debug.apk'), current),
             (Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'), tests)]
    certificates = []
    for original, prepared in pairs:
        run(signer, 'sign', '--ks', args.keystore, '--ks-key-alias', 'androiddebugkey',
            '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--v1-signing-enabled', 'false',
            '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
            '--out', str(prepared), str(original))
        verify_signed_payload(original, prepared)
        certificates.append(signer_digest(run(signer, 'verify', '--print-certs', str(prepared))))
    if len(set(certificates)) != 1:
        raise ValueError('Upgrade APKs use different temporary CI signing certificates')
    version = re.search(r'versionCode\s*=\s*(\d+)', Path('app/build.gradle.kts').read_text()).group(1)
    run(*adb, 'uninstall', PACKAGE + '.test', allow_failure=True)
    run(*adb, 'uninstall', PACKAGE, allow_failure=True)
    run(*adb, 'install', '--no-incremental', '-t', str(signed))
    run(*adb, 'install', '--no-incremental', '-t', str(tests))
    runner = PACKAGE + '.test/' + PACKAGE + '.UpgradeInstrumentation'
    checked_phase(run(*adb, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'phase', 'seed',
                      '-e', 'version', '12', runner), 'seed')
    run(*adb, 'install', '--no-incremental', '-r', '-t', str(current))
    checked_phase(run(*adb, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'phase', 'verify',
                      '-e', 'version', version, runner), 'verify')
    output.mkdir(parents=True, exist_ok=True)
    (output / 'result.json').write_text(json.dumps({
        'baseline_version': '0.3.6-alpha', 'baseline_sha256': BASELINE_SHA,
        'target_version_code': int(version), 'install_replace': True,
        'secret_preserved': True, 'ciphertext_unchanged': True, 'keystore_preserved': True,
        'settings_preserved': True, 'signing': 'temporary CI debug key on both APKs',
        'payload_matches_build': True, 'ci_signer_sha256': certificates[0],
        'physical_testing': 'not_applicable_ci_emulator', 'result': 'passed'}, indent=2) + '\n')
    # Cleanup occurs only AFTER install -r and verification. The normal JUnit
    # suite then installs the original build APKs into this same CI emulator.
    run(*adb, 'uninstall', PACKAGE + '.test')
    run(*adb, 'uninstall', PACKAGE)
    print('APK upgrade verified: encrypted credentials, port, relay and appearance preserved')


if __name__ == '__main__':
    main()
