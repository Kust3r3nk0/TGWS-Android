import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('avd', Path(__file__).with_name('configure_avd.py'))
avd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(avd)


class AvdConfigurationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'test.avd').mkdir()
        (self.root / 'test.avd/config.ini').write_text('image.sysdir.1=system-images/android-37.0/google_apis/x86_64/\n')
        self.descriptor = self.root / 'test.ini'
        self.descriptor.write_text('path=/avd/test.avd\ntarget=android-0\n')

    def test_corrects_minor_version_target_and_preserves_other_metadata(self):
        avd.configure(self.root, '37.0')
        self.assertEqual('path=/avd/test.avd\ntarget=android-37.0\n', self.descriptor.read_text())

    def test_matching_metadata_is_unchanged(self):
        original = 'target=android-37.0\npath=/avd/test.avd\n'
        self.descriptor.write_text(original)
        avd.configure(self.root, '37.0')
        self.assertEqual(original, self.descriptor.read_text())

    def test_rejects_wrong_actual_image_without_rewriting_target(self):
        for api in ('34', '37'):
            with self.subTest(api=api), self.assertRaises(ValueError):
                avd.configure(self.root, api)
        self.assertIn('target=android-0', self.descriptor.read_text())

    def test_rejects_absent_or_ambiguous_target(self):
        for data in ('path=/avd\n', 'target=android-0\ntarget=android-37.0\n'):
            self.descriptor.write_text(data)
            with self.assertRaises(ValueError):
                avd.configure(self.root, '37.0')

    def test_rejects_invalid_api(self):
        for api in ('', '../37', '0', '37.0/extra'):
            with self.subTest(api=api), self.assertRaises(ValueError):
                avd.configure(self.root, api)
