#!/usr/bin/env python3
"""Read allowlisted metadata from a CI emulator. Never create or switch a WebView."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess

CHANNELS = {'com.google.android.webview': 'stable', 'com.google.android.webview.beta': 'beta',
            'com.google.android.webview.dev': 'dev', 'com.google.android.webview.canary': 'canary',
            'com.android.webview': 'aosp'}
CURRENT = re.compile(r'^\s*Current WebView package(?: \(name, version\))?:\s*\(([\w]+(?:\.[\w]+)+),\s*([\w.+-]{1,96})\)\s*$', re.M)


def provider(text):
    match = CURRENT.search(text)
    if match:
        package, version = match.groups()
        return {'state': 'selected', 'package': package, 'version': version,
                'channel_by_package': CHANNELS.get(package, 'unknown'), 'latest_verified': False}
    if re.search(r'^\s*Current WebView package(?: is null|:\s*null)\s*$', text, re.M):
        return {'state': 'unavailable', 'package': None, 'version': None,
                'channel_by_package': None, 'latest_verified': False}
    return {'state': 'unknown', 'package': None, 'version': None,
            'channel_by_package': None, 'latest_verified': False}


def collect(shell):
    def prop(name):
        # Manufacturer/model/OS version only; no serial, fingerprint, SIM or network data.
        value = shell(['getprop', name]).strip()
        return ''.join(c for c in value if c.isprintable())[:96] or None
    api = prop('ro.build.version.sdk')
    if not api or not api.isdecimal():
        raise ValueError('Emulator API metadata unavailable')
    try:
        view = provider(shell(['dumpsys', 'webviewupdate']))
    except (subprocess.SubprocessError, OSError):
        view = provider('')
    return {'schema': 1, 'manufacturer': prop('ro.product.manufacturer'),
            'model': prop('ro.product.model'), 'android_release': prop('ro.build.version.release'),
            'api': int(api), 'one_ui_version': None, 'webview_provider': view,
            'webview_component_tests': 'not_applicable_native_ui',
            'physical_validation': 'not_applicable_ci_emulator'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-[0-9]+', args.serial):
        parser.error('Only an explicitly addressed CI emulator is supported; no physical-device discovery')
    def shell(command):
        return subprocess.run(['adb', '-s', args.serial, 'shell', *command], check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, timeout=4).stdout
    try:
        value = collect(shell)
        value['captured_at_utc'] = datetime.now(timezone.utc).isoformat()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(value, indent=2) + '\n')
    except (ValueError, OSError, subprocess.SubprocessError):
        parser.exit(1, 'CI emulator environment collection failed; raw device output was not retained\n')
    print('CI emulator environment saved (provider inventory, not Stable/Beta certification)')


if __name__ == '__main__':
    main()
