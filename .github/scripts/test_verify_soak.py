import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('soak', Path(__file__).with_name('verify_soak.py'))
soak = importlib.util.module_from_spec(spec)
spec.loader.exec_module(soak)


class SoakGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.report = self.root / 'TEST-MtprotoSoakTest.xml'
        self.stats = dict(seconds=60, completedPackets=300, completedSessions=100, wssAttempts=50,
                          tcpAttempts=60, resets=59, expectedDisconnects=3, maxSessions=8,
                          maxPool=4, maxPeerSockets=12, peakHeapBytes=10000000,
                          maxWssSessions=4, maxTcpSessions=4, poolLeases=10)

    def write(self, **attrs):
        root = ET.Element('testsuite', name=soak.CLASS, tests='1', failures='0', errors='0', skipped='0')
        root.attrib.update(attrs)
        ET.SubElement(root, 'testcase', name=soak.METHOD, classname=soak.CLASS, time='60.1')
        ET.SubElement(root, 'system-out').text = 'SOAK ' + ' '.join(f'{key}={value}' for key, value in self.stats.items())
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
        extra = self.root / 'TEST-duplicateMtprotoSoakTest.xml'
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
        for key, value in [('completedPackets', 0), ('completedSessions', 0), ('wssAttempts', 0),
                           ('tcpAttempts', 0), ('resets', 0), ('maxSessions', 9), ('maxPool', 5),
                           ('maxWssSessions', 0), ('maxTcpSessions', 0), ('poolLeases', 0)]:
            original = self.stats[key]
            self.stats[key] = value
            self.write()
            with self.assertRaises(ValueError): soak.verify(self.root, 60)
            self.stats[key] = original

    def test_rejects_missing_or_duplicate_summary_fields(self):
        root = self.write()
        root.find('system-out').text += ' maxPool=4'
        ET.ElementTree(root).write(self.report)
        with self.assertRaises(ValueError): soak.verify(self.root, 60)
        self.stats.pop('maxPool')
        self.write()
        with self.assertRaises(ValueError): soak.verify(self.root, 60)
