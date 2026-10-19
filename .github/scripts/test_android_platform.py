import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from verify_android_platform import MIN_API, MIN_ANDROID, verify, verify_apk


class AndroidPlatformTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write('app/build.gradle.kts', f'minSdk = {MIN_API}')
        for locale in ['values', 'values-en']:
            self.write(f'app/src/main/res/{locale}/strings.xml',
                       f'<resources><string name="version_label">Android {MIN_ANDROID}+ · %1$s</string></resources>')
        self.write('.github/workflows/android-instrumented.yml', "api: ['36', '37.0']")

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def test_valid_contract(self):
        self.write('docs/version.md', 'TGWS-Android 0.3.6-alpha / Android 16+')
        self.assertEqual(MIN_API, verify(self.root)['min_sdk'])

    def test_old_minimum_is_rejected(self):
        self.write('app/build.gradle.kts', f'minSdk = {MIN_API - 1}')
        with self.assertRaises(ValueError):
            verify(self.root)

    def test_each_localized_about_label_is_checked(self):
        for locale in ['values', 'values-en']:
            with self.subTest(locale=locale):
                path = self.root / f'app/src/main/res/{locale}/strings.xml'
                original = path.read_text()
                path.write_text(original.replace(f'Android {MIN_ANDROID}', f'Android {MIN_ANDROID - 1}'))
                with self.assertRaises(ValueError):
                    verify(self.root)
                path.write_text(original)

    def test_unsupported_ci_target_is_rejected(self):
        for matrix in [f"api: ['{MIN_API - 1}', '{MIN_API}']", f"api: ['{MIN_API}', {MIN_API - 1}]"]:
            self.write('.github/workflows/android-instrumented.yml', matrix)
            with self.subTest(matrix=matrix), self.assertRaises(ValueError):
                verify(self.root)

    def test_old_docs_and_svg_labels_are_rejected(self):
        for name in ['README.md', 'docs/guide.md', 'docs/images/cover.svg']:
            with self.subTest(name=name):
                self.write(name, f'Android {MIN_ANDROID - 1}+')
                with self.assertRaises(ValueError):
                    verify(self.root)
                (self.root / name).unlink()

    def test_old_resource_qualifier_is_rejected(self):
        (self.root / f'app/src/main/res/drawable-v{MIN_API - 1}').mkdir()
        with self.assertRaises(ValueError):
            verify(self.root)

    def test_built_manifest_minimum_is_checked(self):
        with patch('verify_android_platform.subprocess.check_output', return_value=f"sdkVersion:'{MIN_API}'\n"):
            verify_apk('app.apk', '/sdk')
        for text in ['', f"sdkVersion:'{MIN_API - 1}'\n"]:
            with self.subTest(text=text), patch('verify_android_platform.subprocess.check_output', return_value=text):
                with self.assertRaises(ValueError):
                    verify_apk('app.apk', '/sdk')
