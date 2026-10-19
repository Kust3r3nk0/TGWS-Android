import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('privacy', Path(__file__).with_name('verify_public_metadata.py'))
privacy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(privacy)
ROOT = Path(__file__).resolve().parents[2]


class PublicMetadataTest(unittest.TestCase):
    def fixture(self, root):
        for name in ['app/src/main/res/values/strings.xml', 'app/src/main/res/values-en/strings.xml',
                     '.github/workflows/android-ci.yml', 'docs/BUILD_INFO_TEMPLATE.md']:
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)

    def test_product_metadata_is_anonymous(self):
        privacy.verify(ROOT)

    def test_reintroduced_report_resource_in_either_locale_is_rejected(self):
        for locale in ['values', 'values-en']:
            with self.subTest(locale=locale), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                self.fixture(root)
                path = root / f'app/src/main/res/{locale}/strings.xml'
                path.write_text(path.read_text().replace('</resources>', '<string name="owner_validation_report">private fixture</string></resources>'))
                with self.assertRaises(ValueError):
                    privacy.verify(root)

    def test_build_metadata_cannot_copy_private_validation_data(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            path = root / '.github/workflows/android-ci.yml'
            path.write_text(path.read_text() + "\n# 'owner_validation': private fixture\n")
            with self.assertRaises(ValueError):
                privacy.verify(root)

    def test_personal_report_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            (root / 'docs/owner-validation.json').write_text('{}')
            with self.assertRaises(ValueError):
                privacy.verify(root)
