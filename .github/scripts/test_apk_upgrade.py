import unittest
from check_apk_upgrade import checked_phase, emulator_serial


class UpgradeGateTest(unittest.TestCase):
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
