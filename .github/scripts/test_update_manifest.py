import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('manifest',Path(__file__).with_name('create_update_manifest.py'))
manifest=importlib.util.module_from_spec(spec);spec.loader.exec_module(manifest)

class UpdateManifestTest(unittest.TestCase):
    def test_real_build_uses_actual_version_code_and_package(self):
        result=manifest.create('app/build.gradle.kts','TGWS-Android-0.6.1-debug.apk','PREVIEW')
        self.assertEqual(23,result['version_code'])
        self.assertEqual('0.6.1',result['version_name'])
        self.assertEqual(36,result['min_sdk'])
        self.assertEqual(manifest.PACKAGE,result['package_name'])
    def test_alpha_debug_cannot_be_marked_stable(self):
        with self.assertRaises(ValueError):manifest.create('app/build.gradle.kts','TGWS-Android-0.6.1-debug.apk','STABLE')
    def test_paths_and_foreign_package_are_rejected(self):
        for name in ['../TGWS-Android-x.apk','wrong.apk','https://example.test/app.apk']:
            with self.subTest(name=name),self.assertRaises(ValueError):manifest.create('app/build.gradle.kts',name,'PREVIEW')
