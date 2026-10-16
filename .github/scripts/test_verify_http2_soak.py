import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('h2_soak', Path(__file__).with_name('verify_http2_soak.py'))
soak = importlib.util.module_from_spec(spec)
spec.loader.exec_module(soak)


class Http2SoakGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.report = self.root / 'TEST-Http2SoakTest.xml'
        self.stats = dict(seconds=60, completedPackets=300, completedSessions=100,
                          resets=59, expectedDisconnects=3, maxSessions=8, maxH2Sessions=4,
                          maxHttpCalls=8, totalHttpRequests=400, peakHeapBytes=10000000)

    def write(self, **attrs):
        root = ET.Element('testsuite', name=soak.CLASS, tests='1', failures='0', errors='0', skipped='0')
        root.attrib.update(attrs)
        ET.SubElement(root, 'testcase', name=soak.METHOD, classname=soak.CLASS, time='60.1')
        ET.SubElement(root, 'system-out').text = 'H2_SOAK ' + ' '.join(f'{key}={value}' for key, value in self.stats.items())
        ET.ElementTree(root).write(self.report)
        return root

    def test_accepts_measured_result(self):
        self.write()
        self.assertEqual(self.stats, soak.verify(self.root, 60))

    def test_rejects_cached_short_run_or_false_duration(self):
        self.stats['seconds'] = 2
        self.write()
        with self.assertRaises(ValueError): soak.verify(self.root, 60)
        self.stats['seconds'] = 60
        root = self.write()
        root.find('testcase').set('time', '1')
        ET.ElementTree(root).write(self.report)
        with self.assertRaises(ValueError): soak.verify(self.root, 60)

    def test_rejects_missing_duplicate_and_renamed_tests(self):
        with self.assertRaises(ValueError): soak.verify(self.root, 60)
        self.write()
        extra = self.root / 'TEST-duplicateHttp2SoakTest.xml'
        extra.write_bytes(self.report.read_bytes())
        with self.assertRaises(ValueError): soak.verify(self.root, 60)
        extra.unlink()
        root = self.write()
        root.find('testcase').set('name', 'wrong_test')
        ET.ElementTree(root).write(self.report)
        with self.assertRaises(ValueError): soak.verify(self.root, 60)

    def test_rejects_failures_errors_and_skips(self):
        for key in ['failures', 'errors', 'skipped']:
            self.write(**{key: '1'})
            with self.assertRaises(ValueError): soak.verify(self.root, 60)
        root = self.write()
        ET.SubElement(root.find('testcase'), 'skipped')
        ET.ElementTree(root).write(self.report)
        with self.assertRaises(ValueError): soak.verify(self.root, 60)

    def test_rejects_unexercised_routes_and_exceeded_limits(self):
        for key, value in [('completedPackets', 0), ('completedSessions', 0), ('resets', 0),
                           ('maxSessions', 9), ('maxH2Sessions', 0), ('maxH2Sessions', 9),
                           ('maxHttpCalls', 0), ('maxHttpCalls', 17), ('totalHttpRequests', 1),
                           ('peakHeapBytes', 0)]:
            original = self.stats[key]
            self.stats[key] = value
            self.write()
            with self.assertRaises(ValueError): soak.verify(self.root, 60)
            self.stats[key] = original

    def test_rejects_missing_or_duplicate_summary_fields(self):
        root = self.write()
        root.find('system-out').text += ' maxHttpCalls=4'
        ET.ElementTree(root).write(self.report)
        with self.assertRaises(ValueError): soak.verify(self.root, 60)
        self.stats.pop('maxHttpCalls')
        self.write()
        with self.assertRaises(ValueError): soak.verify(self.root, 60)
