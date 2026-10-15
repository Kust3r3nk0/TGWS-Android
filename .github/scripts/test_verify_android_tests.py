import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('gate', Path(__file__).with_name('verify_android_tests.py'))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class AndroidReportGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.source = root / 'source'
        self.results = root / 'results'
        self.source.mkdir()
        self.results.mkdir()
        (self.source / 'ExampleTest.kt').write_text('package example\nclass ExampleTest {\n @Test fun first() {}\n @Test fun second() {}\n}\n')

    def report(self, body):
        (self.results / 'TEST-device.xml').write_text(body)

    def cases(self):
        return '<testcase classname="example.ExampleTest" name="first"/><testcase classname="example.ExampleTest" name="second"/>'

    def test_requires_actual_report(self):
        with self.assertRaises(ValueError):
            gate.verify(self.source, self.results)

    def test_rejects_green_zero_execution(self):
        self.report('<testsuites tests="0" failures="0"/>')
        with self.assertRaises(ValueError):
            gate.verify(self.source, self.results)

    def test_accepts_all_required_tests(self):
        self.report('<testsuites tests="2"><testsuite>' + self.cases() + '</testsuite></testsuites>')
        self.assertEqual(2, gate.verify(self.source, self.results))

    def test_rejects_partial_execution(self):
        self.report('<testsuite><testcase classname="example.ExampleTest" name="first"/></testsuite>')
        with self.assertRaises(ValueError):
            gate.verify(self.source, self.results)

    def test_rejects_duplicate_cases(self):
        self.report('<testsuite>' + self.cases() * 2 + '</testsuite>')
        with self.assertRaises(ValueError):
            gate.verify(self.source, self.results)

    def test_rejects_failed_or_skipped_cases(self):
        for outcome in ('failure', 'error', 'skipped'):
            with self.subTest(outcome=outcome):
                self.report('<testsuite><testcase classname="example.ExampleTest" name="first"><' + outcome + '/></testcase><testcase classname="example.ExampleTest" name="second"/></testsuite>')
                with self.assertRaises(ValueError):
                    gate.verify(self.source, self.results)

    def test_rejects_suite_error_without_testcase_error(self):
        self.report('<testsuite errors="1">' + self.cases() + '</testsuite>')
        with self.assertRaises(ValueError):
            gate.verify(self.source, self.results)

    def test_rejects_malformed_report(self):
        self.report('<testsuite')
        with self.assertRaises(gate.ET.ParseError):
            gate.verify(self.source, self.results)


if __name__ == '__main__':
    unittest.main()
