import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish.py'))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublicationGuardsTest(unittest.TestCase):
    def test_worker_publication_requires_matching_ci_source_checked_commit_and_module_digest(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);artifact=root/'worker';artifact.mkdir()
            module=b'export default {};\n';digest=hashlib.sha256(module).hexdigest()
            (artifact/'worker.js').write_bytes(module)
            checksum=artifact/'SHA256SUMS';checksum.write_text(f'{digest}  worker.js\n')
            metadata=dict(source_commit='a'*40,checked_commit='b'*40,sha256=digest,deployed=False)
            info=artifact/'build-info.json';info.write_text(json.dumps(metadata))
            result=root/'worker.zip'
            publisher.package_worker(artifact,result,'a'*40,'b'*40)
            with zipfile.ZipFile(result) as archive:
                self.assertEqual({'worker.js','SHA256SUMS','build-info.json'},set(archive.namelist()))
                self.assertEqual(module,archive.read('worker.js'))
            for change in [dict(source_commit='c'*40),dict(checked_commit='c'*40),dict(sha256='c'*64),dict(deployed=True)]:
                info.write_text(json.dumps(dict(metadata,**change)))
                with self.subTest(change=change),self.assertRaises(ValueError):
                    publisher.package_worker(artifact,result,'a'*40,'b'*40)
            info.write_text(json.dumps(metadata));checksum.write_text('wrong checksum\n')
            with self.assertRaises(ValueError):publisher.package_worker(artifact,result,'a'*40,'b'*40)
            checksum.write_text(f'{digest}  worker.js\n');(artifact/'worker.js').write_bytes(module+b'changed')
            with self.assertRaises(ValueError):publisher.package_worker(artifact,result,'a'*40,'b'*40)

    def test_debug_revision_uses_a_distinct_tag_without_accepting_arbitrary_refs(self):
        self.assertEqual('v0.4.2-debug', publisher.debug_tag('0.4.2'))
        self.assertEqual('v0.4.2+rev.2-debug', publisher.debug_tag('0.4.2', '2'))
        self.assertEqual('v0.4.2+rev.999-debug', publisher.debug_tag('0.4.2', '999'))
        for revision in ['1', '0', '02', '-2', '1000', 'stable', '2/unsafe', '2; command', ' 2']:
            with self.subTest(revision=revision), self.assertRaises(ValueError):
                publisher.debug_tag('0.4.2', revision)

    def test_debug_publisher_accepts_stable_named_candidate_without_claiming_production(self):
        self.assertEqual('0.4.0',publisher.build_version('versionName = "0.4.0"'))
        self.assertEqual('0.3.10-alpha',publisher.build_version('versionName = "0.3.10-alpha"'))
        for text in ['versionName = "0.4.0-debug;unsafe"','', 'versionName = "0.4.0-SNAPSHOT"']:
            with self.assertRaises(ValueError):publisher.build_version(text)
    def test_upgrade_requires_matching_version_and_every_persistence_check(self):
        report = dict(result='passed', baseline_version='0.3.10-alpha', target_version_code=16,
                      install_replace=True, secret_preserved=True, ciphertext_unchanged=True, update_options_preserved=True,
                      keystore_preserved=True, settings_preserved=True, payload_matches_build=True,
                      ci_signer_sha256='a' * 64)
        publisher.verify_upgrade(report, 16)
        for change in [dict(result='failed'), dict(target_version_code=15), dict(secret_preserved=False),
                       dict(keystore_preserved=None), dict(update_options_preserved=False), dict(install_replace=False), dict(baseline_version='unknown'),
                       dict(payload_matches_build=False), dict(ci_signer_sha256='invalid')]:
            with self.assertRaises(ValueError):
                publisher.verify_upgrade(dict(report, **change), 16)
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
