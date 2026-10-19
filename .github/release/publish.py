"""Publish an already signed APK only when its payload matches passing CI."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


def validate_inputs(inputs):
    for name, pattern in [('source_commit', r'[a-f0-9]{40}'),
                          ('build_run', r'[0-9]+'), ('device_run', r'[0-9]+'),
                          ('apk_sha256', r'[a-f0-9]{64}'), ('signer_sha256', r'[a-f0-9]{64}')]:
        if not re.fullmatch(pattern, inputs.get(name, '')):
            raise ValueError('Invalid publication input: ' + name)
    url = urllib.parse.urlsplit(inputs.get('apk_url', ''))
    if url.scheme != 'https' or not (url.hostname or '').endswith('.oaiusercontent.com') or url.username or url.password:
        raise ValueError('Expected an HTTPS runtime file reference')


def verify_run(run, source, name, repository):
    if (run.get('conclusion') != 'success' or run.get('status') != 'completed' or
            run.get('head_sha') != source or run.get('name') != name or
            run.get('head_repository', {}).get('full_name', '').lower() != repository.lower()):
        raise ValueError('CI run does not verify this application commit')


def verify_payload(original, candidate):
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(candidate) as after:
        if set(before.namelist()) != set(after.namelist()):
            raise ValueError('APK entries differ from CI')
        for name in before.namelist():
            if before.read(name) != after.read(name):
                raise ValueError('APK payload differs from CI')


def verify_upgrade(report, version_code):
    required = ['install_replace', 'secret_preserved', 'ciphertext_unchanged', 'keystore_preserved', 'settings_preserved', 'payload_matches_build']
    if (report.get('result') != 'passed' or report.get('target_version_code') != version_code or
            report.get('baseline_version') != '0.3.4-alpha' or
            not re.fullmatch(r'[a-f0-9]{64}', report.get('ci_signer_sha256', '')) or
            any(report.get(key) is not True for key in required)):
        raise ValueError('Missing or unsuccessful APK upgrade regression')


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def single_file(directory, name):
    matches = list(Path(directory).rglob(name))
    if len(matches) != 1:
        raise ValueError('Expected exactly one CI artifact file: ' + name)
    return matches[0]


def main():
    inputs = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())['inputs']
    validate_inputs(inputs)
    repository = os.environ['GITHUB_REPOSITORY']
    source = inputs['source_commit']
    # Publisher/docs may be newer; application and build inputs must be identical.
    changed = command('git', 'diff', '--name-only', source, 'HEAD', '--', 'app', 'gradle',
                      'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat')
    if changed:
        raise ValueError('Checked-out application/build files differ from verified source')
    for key, name in [('build_run', 'Android CI'), ('device_run', 'Android device tests')]:
        run = json.loads(command('gh', 'api', f'repos/{repository}/actions/runs/{inputs[key]}'))
        verify_run(run, source, name, repository)
    gradle = Path('app/build.gradle.kts').read_text()
    version = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+-alpha)"', gradle).group(1)
    version_code = int(re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1))
    min_sdk = int(re.search(r'minSdk\s*=\s*(\d+)', gradle).group(1))
    output = Path('debug-release-assets')
    output.mkdir()
    candidate = output / f'TGWS-Android-{version.removesuffix("-alpha")}-debug.apk'
    # SHA and payload are checked before any publication; cap transfer size.
    try:
        with urllib.request.urlopen(inputs['apk_url'], timeout=60) as response, candidate.open('wb') as dest:
            total = 0
            while chunk := response.read(1024 * 1024):
                total += len(chunk)
                if total > 100 * 1024 * 1024:
                    raise ValueError('APK exceeds publication limit')
                dest.write(chunk)
    except urllib.error.URLError:
        raise ValueError('APK file reference download failed') from None
    if hashlib.sha256(candidate.read_bytes()).hexdigest() != inputs['apk_sha256']:
        raise ValueError('Signed APK checksum mismatch')
    command('gh', 'run', 'download', inputs['build_run'], '--repo', repository,
            '--pattern', 'TGWS-Android-*-debug-*', '--dir', 'ci-apk')
    verify_payload(single_file('ci-apk', 'app-debug.apk'), candidate)
    signers = sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/apksigner'))
    if not signers:
        raise ValueError('Android signature verifier is unavailable')
    certificate = command(str(signers[-1]), 'verify', '--print-certs', '--min-sdk-version', str(min_sdk), str(candidate))
    if f'certificate SHA-256 digest: {inputs["signer_sha256"]}' not in certificate:
        raise ValueError('APK signing certificate mismatch')
    command('gh', 'run', 'download', inputs['build_run'], '--repo', repository,
            '--pattern', 'Android-JVM-lint-reports-*', '--dir', 'jvm-reports')
    suites = [ET.parse(path).getroot() for path in Path('jvm-reports').rglob('TEST-*.xml')]
    counts = {key: sum(int(s.get(key, '0')) for s in suites) for key in ['tests', 'failures', 'errors', 'skipped']}
    if not counts['tests'] or any(counts[key] for key in ['failures', 'errors', 'skipped']):
        raise ValueError('JVM reports are empty or not passing')
    soak_spec = importlib.util.spec_from_file_location('soak_gate', '.github/scripts/verify_soak.py')
    soak_gate = importlib.util.module_from_spec(soak_spec)
    soak_spec.loader.exec_module(soak_gate)
    soak = soak_gate.verify('jvm-reports', 60)
    h2_spec = importlib.util.spec_from_file_location('h2_gate', '.github/scripts/verify_http2_soak.py')
    h2_gate = importlib.util.module_from_spec(h2_spec)
    h2_spec.loader.exec_module(h2_gate)
    h2_soak = h2_gate.verify('jvm-reports', 60)
    command('gh', 'run', 'download', inputs['device_run'], '--repo', repository,
            '--pattern', 'Android-device-test-reports-*', '--dir', 'device-reports')
    spec = importlib.util.spec_from_file_location('gate', '.github/scripts/verify_android_tests.py')
    gate = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gate)
    devices = {}
    upgrades = {}
    for api in ['36', '37.0']:
        directories = list(Path('device-reports').glob(f'Android-device-test-reports-API-{api}-*'))
        if len(directories) != 1:
            raise ValueError('Missing or duplicate device artifact')
        devices[api] = gate.verify('app/src/androidTest/java', directories[0])
        upgrades[api] = json.loads(single_file(directories[0], 'result.json').read_text())
        verify_upgrade(upgrades[api], version_code)
    meta = json.loads(single_file('ci-apk', 'build-info.json').read_text())
    meta.update(source_commit=source, jvm_tests=counts, android_instrumentation=devices, local_soak=soak, http2_soak=h2_soak,
                min_sdk=min_sdk, apk_upgrade=upgrades, signer_certificate_sha256=inputs['signer_sha256'],
                signature='Preserved workspace debug key; not production signing',
                apk_sha256=inputs['apk_sha256'], payload_matches_ci=True)
    (output / 'build-info.json').write_text(json.dumps(meta, indent=2) + '\n')
    report = Path(f'docs/TEST_REPORT_{version}.md').read_text()
    report += f'\nCI: https://github.com/{repository}/actions/runs/{inputs["build_run"]}\n'
    report += f'\nAndroid: https://github.com/{repository}/actions/runs/{inputs["device_run"]}\n'
    report += f'\nVerified device test counts: {devices}; source: {source}.\n'
    (output / 'REPORT.md').write_text(report)
    (output / 'CHANGELOG.md').write_text(Path('CHANGELOG.md').read_text())
    with zipfile.ZipFile(output / 'test-reports.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for folder in ['jvm-reports', 'device-reports']:
            for path in Path(folder).rglob('*'):
                if path.is_file() and (path.suffix in ('.xml', '.json') or 'lint-results-debug' in path.name):
                    archive.write(path)
    (output / 'SHA256SUMS').write_text(''.join(
        f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n'
        for p in sorted(output.iterdir()) if p.is_file()))
    tag = f'v{version}-debug'
    notes = Path(f'docs/releases/{version.removesuffix("-alpha")}-debug.md')
    command('gh', 'release', 'create', tag, *map(str, sorted(output.iterdir())), '--repo', repository,
            '--target', source, '--prerelease', '--latest=false', '--title',
            f'TGWS-Android {version.removesuffix("-alpha")} — Debug (Android 16+)', '--notes-file', str(notes))
    print(f'Published checked debug release: https://github.com/{repository}/releases/tag/{tag}')


if __name__ == '__main__':
    main()
