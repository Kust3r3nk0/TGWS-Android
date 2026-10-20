"""Verify that the emulator entry point stops before unsafe follow-up steps."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class AndroidEntryPointTest(unittest.TestCase):
    def run_script(self, api='37', gradle_status=0, report_status=0, pull_status=0, before_status=0, after_status=0, requested_api='37.0', install_mode='default', restore_status=0, target_present=True):
        script = Path(__file__).with_name('run_android_tests.sh').resolve()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name, content in {
                'adb': f'''#!/bin/sh
case "$3 $4 $5" in
  'install --no-incremental -r') echo unexpected-install >> steps; exit 6;;
  'shell appops get') echo 'REQUEST_INSTALL_PACKAGES: {install_mode}'; exit 0;;
  'shell appops set')
    echo install-restored-$8 >> steps; exit {restore_status};;
  'shell pm path') if [ {1 if target_present else 0} = 1 ]; then echo package:/data/app/test/base.apk; fi; exit 0;;
esac
if [ "$3" = pull ]; then echo screenshots >> steps; exit {pull_status}; fi
printf "{api}\\r\\n"
''',
                'gradlew': f'#!/bin/sh\necho gradle >> steps\nexit {gradle_status}\n',
                'python3': f'#!/bin/sh\ncase "$1" in *collect_android_environment.py) case "$3" in *before.json) echo environment-before >> steps; exit {before_status};; *) echo environment-after >> steps; exit {after_status};; esac;; esac\necho report-gate >> steps\nexit {report_status}\n',
            }.items():
                path = root / name
                path.write_text(content)
                path.chmod(0o755)
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'])
            result = subprocess.run(['bash', str(script), requested_api], cwd=root, env=env, capture_output=True, text=True)
            steps = (root / 'steps').read_text().splitlines() if (root / 'steps').exists() else []
            return result.returncode, steps

    def test_wrong_api_stops_before_gradle(self):
        status, steps = self.run_script(api=str(36 - 1))
        self.assertNotEqual(0, status)
        self.assertEqual([], steps)

    def test_unsupported_matching_api_cannot_run_tests(self):
        retired = str(36 - 1)
        status, steps = self.run_script(api=retired, requested_api=retired)
        self.assertNotEqual(0, status)
        self.assertEqual([], steps)

    def test_gradle_failure_stops_before_report_gate(self):
        status, steps = self.run_script(gradle_status=1)
        self.assertNotEqual(0, status)
        self.assertEqual(['environment-before', 'gradle', 'environment-after', 'screenshots', 'install-restored-default'], steps)

    def test_failed_screenshot_pull_cannot_mask_gradle_failure(self):
        status, steps = self.run_script(gradle_status=9, pull_status=7)
        self.assertEqual(9, status)
        self.assertEqual(['environment-before', 'gradle', 'environment-after', 'screenshots', 'install-restored-default'], steps)

    def test_success_checks_actual_reports(self):
        status, steps = self.run_script()
        self.assertEqual(0, status)
        self.assertEqual(['environment-before', 'gradle', 'environment-after', 'report-gate', 'screenshots', 'install-restored-default'], steps)

    def test_failed_report_gate_cannot_be_hidden_by_screenshot_pull(self):
        status, steps = self.run_script(report_status=1)
        self.assertNotEqual(0, status)
        self.assertEqual(['environment-before', 'gradle', 'environment-after', 'report-gate', 'install-restored-default'], steps)

    def test_screenshot_collection_failure_is_visible(self):
        status, steps = self.run_script(pull_status=1)
        self.assertNotEqual(0, status)
        self.assertEqual(['environment-before', 'gradle', 'environment-after', 'report-gate', 'screenshots', 'install-restored-default'], steps)

    def test_failed_before_inventory_does_not_start_tests(self):
        status, steps = self.run_script(before_status=7)
        self.assertEqual(7, status)
        self.assertEqual(['environment-before'], steps)

    def test_failed_after_inventory_cannot_mask_test_failure(self):
        status, steps = self.run_script(gradle_status=9, after_status=7)
        self.assertEqual(9, status)
        self.assertEqual(['environment-before', 'gradle', 'environment-after', 'screenshots', 'install-restored-default'], steps)

    def test_after_inventory_failure_is_visible_when_tests_pass(self):
        status, steps = self.run_script(after_status=7)
        self.assertEqual(7, status)
        self.assertEqual(['environment-before', 'gradle', 'environment-after', 'install-restored-default'], steps)

    def test_prior_denied_install_permission_is_restored_after_failure(self):
        status, steps = self.run_script(gradle_status=9, install_mode='deny')
        self.assertEqual(9, status)
        self.assertEqual('install-restored-deny', steps[-1])

    def test_restore_failure_is_visible_and_does_not_mask_test_failure(self):
        self.assertNotEqual(0, self.run_script(restore_status=7)[0])
        self.assertEqual(9, self.run_script(gradle_status=9, restore_status=7)[0])

    def test_only_gradle_installs_the_test_target(self):
        status, steps = self.run_script()
        self.assertEqual(0, status)
        self.assertNotIn('unexpected-install', steps)
        self.assertEqual(1, steps.count('gradle'))

    def test_clean_emulator_does_not_query_app_ops_for_an_absent_package(self):
        status, steps = self.run_script(target_present=False)
        self.assertEqual(0, status)
        self.assertEqual(['environment-before', 'gradle', 'environment-after', 'report-gate', 'screenshots'], steps)
