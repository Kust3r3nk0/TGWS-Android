"""Verify that the emulator entry point stops before unsafe follow-up steps."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class AndroidEntryPointTest(unittest.TestCase):
    def run_script(self, api='37', gradle_status=0, report_status=0, pull_status=0):
        script = Path(__file__).with_name('run_android_tests.sh').resolve()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name, content in {
                'adb': f'#!/bin/sh\nif [ "$3" = "pull" ]; then echo screenshots >> steps; exit {pull_status}; fi\nprintf "{api}\\r\\n"\n',
                'gradlew': f'#!/bin/sh\necho gradle >> steps\nexit {gradle_status}\n',
                'python3': f'#!/bin/sh\necho report-gate >> steps\nexit {report_status}\n',
            }.items():
                path = root / name
                path.write_text(content)
                path.chmod(0o755)
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'])
            result = subprocess.run(['bash', str(script), '37.0'], cwd=root, env=env, capture_output=True, text=True)
            steps = (root / 'steps').read_text().splitlines() if (root / 'steps').exists() else []
            return result.returncode, steps

    def test_wrong_api_stops_before_gradle(self):
        status, steps = self.run_script(api='34')
        self.assertNotEqual(0, status)
        self.assertEqual([], steps)

    def test_gradle_failure_stops_before_report_gate(self):
        status, steps = self.run_script(gradle_status=1)
        self.assertNotEqual(0, status)
        self.assertEqual(['gradle', 'screenshots'], steps)

    def test_failed_screenshot_pull_cannot_mask_gradle_failure(self):
        status, steps = self.run_script(gradle_status=9, pull_status=7)
        self.assertEqual(9, status)
        self.assertEqual(['gradle', 'screenshots'], steps)

    def test_success_checks_actual_reports(self):
        status, steps = self.run_script()
        self.assertEqual(0, status)
        self.assertEqual(['gradle', 'report-gate', 'screenshots'], steps)

    def test_failed_report_gate_cannot_be_hidden_by_screenshot_pull(self):
        status, steps = self.run_script(report_status=1)
        self.assertNotEqual(0, status)
        self.assertEqual(['gradle', 'report-gate'], steps)

    def test_screenshot_collection_failure_is_visible(self):
        status, steps = self.run_script(pull_status=1)
        self.assertNotEqual(0, status)
        self.assertEqual(['gradle', 'report-gate', 'screenshots'], steps)
