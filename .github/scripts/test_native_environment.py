import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import collect_android_environment as environment
import verify_native_ui as native


class NativeUiContractTest(unittest.TestCase):
    def test_reviewed_provider_metadata_call_does_not_allow_engine_or_javascript(self):
        with tempfile.TemporaryDirectory() as root:
            self.source(root, native.PROVIDER_FILE, native.PROVIDER_READ)
            self.assertFalse(native.verify(root)['embedded_webview'])
            for extra in ['WebView(context)', 'import android.webkit.WebView',
                          'android.webkit.WebView.setWebContentsDebuggingEnabled(true)',
                          'val info=android.webkit.WebView.getCurrentWebViewPackage(); WebView(context)']:
                self.source(root, native.PROVIDER_FILE, native.PROVIDER_READ + '\n' + extra)
                with self.assertRaises(ValueError):
                    native.verify(root)
            self.source(root, 'app/src/main/Other.kt', native.PROVIDER_READ)
            self.source(root, native.PROVIDER_FILE, native.PROVIDER_READ)
            with self.assertRaises(ValueError):
                native.verify(root)

    def source(self, root, path, text):
        target = Path(root, path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)

    def test_native_sources_and_test_only_provider_inventory_are_allowed(self):
        with tempfile.TemporaryDirectory() as root:
            self.source(root, 'app/src/main/Main.kt', 'fun main() = setContent { Text("Native") }')
            self.source(root, 'app/src/androidTest/Environment.kt', 'android.webkit.WebView.getCurrentWebViewPackage()')
            self.source(root, 'app/src/main/res/values/strings.xml', '<string name="provider">System WebView</string>')
            self.assertFalse(native.verify(root)['embedded_webview'])

    def test_new_embedded_integration_or_dependency_requires_review(self):
        for path, text in [('app/src/main/Main.kt', 'import android.webkit.WebView'),
                           ('app/build.gradle.kts', 'implementation("androidx.webkit:webkit:1.17.1")'),
                           ('app/src/main/res/layout/page.xml', '<android.webkit.WebView/>'),
                           ('app/src/debug/Bridge.java', '@JavascriptInterface public void bridge() {}')]:
            with self.subTest(path=path), tempfile.TemporaryDirectory() as root:
                self.source(root, path, text)
                with self.assertRaisesRegex(ValueError, 'TGWS-16-016'):
                    native.verify(root)

    def test_findings_do_not_echo_configuration(self):
        with tempfile.TemporaryDirectory() as root:
            self.source(root, 'app/src/main/Main.kt', 'WebView(); secret="do-not-print"')
            with self.assertRaises(ValueError) as caught:
                native.verify(root)
            self.assertNotIn('do-not-print', str(caught.exception))

    def test_empty_checkout_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                native.verify(root)


class EnvironmentInventoryTest(unittest.TestCase):
    def test_selected_provider_and_exact_version_not_other_installed_candidates(self):
        for package, channel in environment.CHANNELS.items():
            with self.subTest(channel=channel):
                value = environment.provider(f'Current WebView package (name, version): ({package}, 150.0.1234.56)\nValid package com.android.webview (versionName: 99)')
                self.assertEqual(package, value['package'])
                self.assertEqual('150.0.1234.56', value['version'])
                self.assertEqual(channel, value['channel_by_package'])
                self.assertFalse(value['latest_verified'])

    def test_missing_provider_and_unknown_output_are_distinct(self):
        self.assertEqual('unavailable', environment.provider('  Current WebView package is null\n')['state'])
        self.assertEqual('unknown', environment.provider('permission denied')['state'])
        self.assertEqual('unknown', environment.provider('Valid package com.google.android.webview')['state'])

    def test_unknown_oem_provider_does_not_imply_stable(self):
        value = environment.provider('Current WebView package (name, version): (vendor.browser, 1.2.3)')
        self.assertEqual('selected', value['state'])
        self.assertEqual('unknown', value['channel_by_package'])

    def test_allowlisted_properties_and_no_raw_dump_or_personal_identifiers(self):
        calls = []
        def shell(args):
            calls.append(args)
            if args == ['dumpsys', 'webviewupdate']:
                return 'private-junk-do-not-copy\nCurrent WebView package is null'
            return {'ro.build.version.sdk': '36', 'ro.build.version.release': '16',
                    'ro.product.manufacturer': 'Test manufacturer', 'ro.product.model': 'Test model'}[args[1]]
        value = environment.collect(shell)
        self.assertEqual(36, value['api'])
        self.assertEqual('Test manufacturer', value['manufacturer'])
        self.assertIsNone(value['one_ui_version'])
        self.assertEqual('not_applicable_ci_emulator', value['physical_validation'])
        self.assertNotIn('private-junk', json.dumps(value))
        self.assertEqual(5, len(calls))

    def test_provider_query_failure_is_unknown_not_a_false_absence(self):
        def shell(args):
            if args[0] == 'dumpsys':
                raise subprocess.TimeoutExpired('adb', 4)
            return '36' if args[1].endswith('sdk') else 'unknown'
        self.assertEqual('unknown', environment.collect(shell)['webview_provider']['state'])

    def test_missing_api_is_not_a_success(self):
        with self.assertRaises(ValueError):
            environment.collect(lambda args: '')

    def test_cli_rejects_physical_serial_before_any_adb_command(self):
        with tempfile.TemporaryDirectory() as root:
            output = Path(root, 'environment.json')
            result = subprocess.run([sys.executable, environment.__file__, '--serial', 'personal-phone',
                                     '--output', str(output)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Only an explicitly addressed CI emulator', result.stderr)
            self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
