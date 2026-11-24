"""Vérifie que la continuité de mise à jour est réellement contrôlée."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import check_update_continuity as continuity


class UpdateContinuityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'config').mkdir()
        (self.root / 'config/signing').mkdir()
        self.keystore = self.root / 'config/signing/NovaListen-release.p12'
        self.keystore.write_bytes(b'pinned-keystore-bytes')
        import hashlib
        self.digest = hashlib.sha256(b'pinned-keystore-bytes').hexdigest()
        self.policy = {'application_id': 'com.novalisten.app.next',
                       'keystore_path': 'config/signing/NovaListen-release.p12',
                       'keystore_sha256': self.digest,
                       'certificate_sha256': 'a' * 64}
        (self.root / 'config/signing-policy.json').write_text(json.dumps(self.policy))
        (self.root / 'app').mkdir()
        self.gradle(code=8)
        self.deliver(code=7, certificate='a' * 64)

    def gradle(self, code):
        (self.root / 'app/build.gradle.kts').write_text(
            'android {\n defaultConfig {\n applicationId = "com.novalisten.app.next"\n'
            f' versionCode = {code}\n versionName = "0.3.{code}"\n }}\n')

    def deliver(self, code, certificate):
        folder = self.root / 'apk-livraison' / ('b' * 64)
        folder.mkdir(parents=True, exist_ok=True)
        (folder / 'manifeste.json').write_text(json.dumps(
            {'version_code': code, 'version_name': f'0.3.{code}', 'certificate_sha256': certificate}))

    def test_a_newer_version_with_the_same_key_passes(self):
        report = continuity.check(self.root)
        self.assertEqual([], report['problems'])
        self.assertEqual([], report['notices'])

    def test_a_changed_or_missing_keystore_blocks_the_build(self):
        self.keystore.write_bytes(b'other-key')
        self.assertTrue(any('keystore' in problem for problem in continuity.check(self.root)['problems']))
        self.keystore.unlink()
        self.assertTrue(any('keystore' in problem for problem in continuity.check(self.root)['problems']))

    def test_a_different_application_id_or_certificate_blocks_the_build(self):
        (self.root / 'config/signing-policy.json').write_text(json.dumps({**self.policy, 'application_id': 'other.app'}))
        self.assertTrue(continuity.check(self.root)['problems'])
        (self.root / 'config/signing-policy.json').write_text(json.dumps(self.policy))
        self.deliver(code=7, certificate='c' * 64)
        self.assertTrue(any('ertificat' in problem for problem in continuity.check(self.root)['problems']))

    def test_a_non_increasing_version_code_is_refused(self):
        self.gradle(code=7)
        report = continuity.check(self.root)
        self.assertEqual([], report['problems'])
        self.assertTrue(report['notices'])
        self.gradle(code=6)
        self.assertTrue(any('inférieur' in problem for problem in continuity.check(self.root)['problems']))

    def test_recording_a_delivery_names_the_folder_after_the_file_hash(self):
        import record_delivery
        apk = self.root / 'NovaListen-Next-0.3.1.apk'
        apk.write_bytes(b'signed-apk-bytes')
        import hashlib
        manifest = record_delivery.record(apk, run=42, root=self.root, version_code=8, version_name='0.3.2')
        folder = self.root / 'apk-livraison' / hashlib.sha256(b'signed-apk-bytes').hexdigest()
        self.assertEqual(manifest['sha256'], folder.name)
        self.assertTrue((folder / 'NovaListen-Next-0.3.1.apk').is_file())
        self.assertEqual(self.policy['certificate_sha256'], manifest['certificate_sha256'])


if __name__ == '__main__':
    unittest.main()
