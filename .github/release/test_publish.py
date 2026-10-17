import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish.py'))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublicationGuardsTest(unittest.TestCase):
    def test_upgrade_requires_matching_version_and_every_persistence_check(self):
        report = dict(result='passed', baseline_version='0.3.4-alpha', target_version_code=11,
                      install_replace=True, secret_preserved=True, ciphertext_unchanged=True,
                      keystore_preserved=True, settings_preserved=True)
        publisher.verify_upgrade(report, 11)
        for change in [dict(result='failed'), dict(target_version_code=10), dict(secret_preserved=False),
                       dict(keystore_preserved=None), dict(install_replace=False), dict(baseline_version='unknown')]:
            with self.assertRaises(ValueError):
                publisher.verify_upgrade(dict(report, **change), 11)
    def test_artifact_file_is_found_under_gh_pattern_directory_but_duplicates_fail(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            artifact = root / 'TGWS-Android-version-debug-commit'
            artifact.mkdir()
            apk = artifact / 'app-debug.apk'
            apk.write_bytes(b'checked')
            self.assertEqual(apk, publisher.single_file(root, 'app-debug.apk'))
            (root / 'app-debug.apk').write_bytes(b'unexpected duplicate')
            with self.assertRaises(ValueError):
                publisher.single_file(root, 'app-debug.apk')
            with self.assertRaises(ValueError):
                publisher.single_file(root, 'absent.apk')

    def test_valid_inputs_and_rejected_untrusted_urls(self):
        inputs = dict(source_commit='a' * 40, build_run='1', device_run='2',
                      apk_sha256='b' * 64, signer_sha256='c' * 64,
                      apk_url='https://files.oaiusercontent.com/file')
        publisher.validate_inputs(inputs)
        for url in ['http://files.oaiusercontent.com/file', 'https://evil.example/file',
                    'https://oaiusercontent.com.evil.example/file', 'file:///private']:
            with self.subTest(url=url), self.assertRaises(ValueError):
                publisher.validate_inputs(dict(inputs, apk_url=url))
        with self.assertRaises(ValueError):
            publisher.validate_inputs(dict(inputs, source_commit='main; command'))

    def test_ci_must_verify_exact_commit_and_repository(self):
        run = dict(conclusion='success', status='completed', name='Android CI', head_sha='a' * 40,
                   head_repository={'full_name': 'owner/project'})
        publisher.verify_run(run, 'a' * 40, 'Android CI', 'owner/project')
        for changes in [dict(conclusion='failure'), dict(status='in_progress'),
                        dict(head_sha='b' * 40), dict(name='Other CI'),
                        dict(head_repository={'full_name': 'fork/project'})]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                publisher.verify_run(dict(run, **changes), 'a' * 40, 'Android CI', 'owner/project')

    def test_signed_candidate_must_preserve_all_ci_payload_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            def apk(name, payload, extra=False):
                path = root / name
                with zipfile.ZipFile(path, 'w') as z:
                    z.writestr('classes.dex', payload)
                    if extra:
                        z.writestr('other', b'unchecked')
                return path
            original = apk('ci.apk', b'verified')
            publisher.verify_payload(original, apk('same.apk', b'verified'))
            for candidate in [apk('changed.apk', b'changed'), apk('extra.apk', b'verified', True)]:
                with self.assertRaises(ValueError):
                    publisher.verify_payload(original, candidate)
