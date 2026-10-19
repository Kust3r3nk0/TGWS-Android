import unittest
from check_apk_upgrade import checked_phase, emulator_serial, check_runner, RUNNER


class UpgradeGateTest(unittest.TestCase):
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

    def test_instrumentation_success_requires_expected_phase_and_zero_code(self):
        checked_phase('INSTRUMENTATION_RESULT: TGWS_UPGRADE=verify:passed\nINSTRUMENTATION_CODE: 0\n', 'verify')
        for output in ['', 'INSTRUMENTATION_CODE: 0\n',
                       'INSTRUMENTATION_RESULT: TGWS_UPGRADE=seed:passed\nINSTRUMENTATION_CODE: 0\n',
                       'INSTRUMENTATION_RESULT: TGWS_UPGRADE=verify:failed\nINSTRUMENTATION_CODE: 0\n',
                       'INSTRUMENTATION_RESULT: TGWS_UPGRADE=verify:passed\nINSTRUMENTATION_CODE: -1\n']:
            with self.assertRaises(ValueError):
                checked_phase(output, 'verify')
