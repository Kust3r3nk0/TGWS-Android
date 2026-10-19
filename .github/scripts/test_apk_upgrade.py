import unittest
from pathlib import Path
import tempfile
import zipfile
from check_apk_upgrade import checked_phase, emulator_serial, check_runner, RUNNER, failure_detail, signer_digest, verify_signed_payload


class UpgradeGateTest(unittest.TestCase):
    def test_prepared_upgrade_apk_must_retain_every_build_payload_byte(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name, payload in [('build.apk', b'checked'), ('same.apk', b'checked'), ('changed.apk', b'changed')]:
                with zipfile.ZipFile(root / name, 'w') as apk:
                    apk.writestr('classes.dex', payload)
            verify_signed_payload(root / 'build.apk', root / 'same.apk')
            with self.assertRaises(ValueError):
                verify_signed_payload(root / 'build.apk', root / 'changed.apk')
    def test_signer_check_requires_single_sha256_certificate(self):
        certificate = 'Signer #1 certificate SHA-256 digest: ' + 'a' * 64 + '\n'
        self.assertEqual('a' * 64, signer_digest(certificate))
        for output in ['', 'Signer #1 certificate SHA-256 digest: invalid', certificate + certificate]:
            with self.assertRaises(ValueError):
                signer_digest(output)
    def test_compiled_manifest_must_expose_the_upgrade_runner(self):
        check_runner('A: android:name(0x01010003)="' + RUNNER + '" (Raw: "' + RUNNER + '")')
        for text in ['', 'A: android:name(0x01010003)="androidx.test.runner.AndroidJUnitRunner"', RUNNER]:
            with self.assertRaises(ValueError):
                check_runner(text)
    def test_only_explicit_emulator_serial_allowed(self):
        self.assertEqual('emulator-5554', emulator_serial('emulator-5554'))
        for value in ['', 'phone-123', 'all', 'emulator-5554;echo']:
            with self.assertRaises(ValueError):
                emulator_serial(value)

    def test_instrumentation_success_requires_expected_phase_and_android_result_ok(self):
        checked_phase('INSTRUMENTATION_RESULT: TGWS_UPGRADE=verify:passed\nINSTRUMENTATION_CODE: -1\n', 'verify')
        for output in ['', 'INSTRUMENTATION_CODE: -1\n',
                       'INSTRUMENTATION_RESULT: TGWS_UPGRADE=seed:passed\nINSTRUMENTATION_CODE: -1\n',
                       'INSTRUMENTATION_RESULT: TGWS_UPGRADE=verify:failed\nINSTRUMENTATION_CODE: -1\n',
                       'INSTRUMENTATION_RESULT: TGWS_UPGRADE=verify:passed\nINSTRUMENTATION_CODE: 0\n']:
            with self.assertRaises(ValueError):
                checked_phase(output, 'verify')

    def test_instrumentation_failure_exports_only_fixed_public_categories(self):
        text = ('Permission Denial: private.example/token=secret\n'
                'INSTRUMENTATION_RESULT: TGWS_UPGRADE=seed:failed\n'
                'INSTRUMENTATION_RESULT: TGWS_UPGRADE_CHECK=VERSION\nINSTRUMENTATION_CODE: 0\n')
        self.assertEqual('PERMISSION_DENIED,seed:failed,RESULT_0,VERSION', failure_detail(text))
        self.assertEqual('NO_PUBLIC_STATUS', failure_detail('private.example/token=secret'))
        self.assertEqual('PERMISSION_DENIED,SIGNATURE_MISMATCH', failure_detail(
            'Permission Denial: package test does not have a signature matching the target app'))
