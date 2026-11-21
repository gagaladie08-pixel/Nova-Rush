"""Synthetic fixtures only: never create real private keys during tests."""
import hashlib
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

import parallel_signing as signing


class ParallelSigningTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / 'config').mkdir()
        original = {'reference_version': '0.2.3', 'certificate_sha256': '9' * 64, 'key_alias': 'androiddebugkey'}
        self.original = json.dumps(original)
        (self.root / 'config/signing-policy.json').write_text(self.original)
        private_root = self.root / 'private-signing'
        private = private_root / 'novalisten-next'
        paths = {'ROOT': self.root, 'POLICY_PATH': self.root / 'config/parallel-signing-policy.json',
                 'PRIVATE_ROOT': private_root, 'PRIVATE': private, 'KEY': private / 'novalisten-next.p12',
                 'PASSWORD': private / 'password.txt', 'BACKUP': private_root / 'backup.zip',
                 'ACKNOWLEDGEMENT': private / 'owner-backup-confirmed.json'}
        self.paths = patch.multiple(signing, **paths); self.paths.start(); self.addCleanup(self.paths.stop)
        self.git = patch.object(signing.subprocess, 'run', return_value=SimpleNamespace(returncode=0))
        self.git.start(); self.addCleanup(self.git.stop)
        self.cert = b'synthetic-next-certificate-not-a-real-key'
        self.tool = patch.object(signing, 'tool', side_effect=lambda name: name)
        self.tool.start(); self.addCleanup(self.tool.stop)
        self.runner = patch.object(signing, 'run', side_effect=self.fake_run)
        self.run = self.runner.start(); self.addCleanup(self.runner.stop)

    def fake_run(self, command):
        if '-genkeypair' in command:
            signing.private_write(signing.KEY, b'synthetic-private-store-not-a-key')
        if '-exportcert' in command:
            return self.cert
        if command[:2] == ['java', '-jar']:
            if 'sign' in command:
                Path(command[command.index('--out') + 1]).write_bytes(b'synthetic-signed-apk')
            else:
                return ('Signer #1 certificate SHA-256 digest: ' + hashlib.sha256(self.cert).hexdigest() + '\n').encode()
        return b'synthetic-public-tool-output'

    def initialized(self, acknowledge=False):
        receipt = signing.initialize()
        if acknowledge:
            signing.acknowledge_backup()
        return receipt

    def fixture(self, package=signing.APPLICATION_ID, target=None):
        source = self.root / 'unsigned.apk'; source.write_bytes(b'synthetic-unsigned-apk')
        role = 'test' if target is not None else 'app'
        info = {role: {'application_id': package, 'sha256': signing.digest(source.read_bytes())}}
        if target is not None: info[role]['target_package'] = target
        metadata = self.root / 'metadata.json'; metadata.write_text(json.dumps(info))
        return source, self.root / 'signed.apk', self.root / 'apksigner.jar', metadata

    def test_initial_identity_is_parallel_and_does_not_replace_023(self):
        receipt = self.initialized()
        self.assertEqual(signing.APPLICATION_ID, receipt['application_id'])
        self.assertEqual(self.original, (self.root / 'config/signing-policy.json').read_text())
        self.assertNotIn(signing.PASSWORD.read_text().strip(), json.dumps(receipt))
        self.assertFalse(signing.ACKNOWLEDGEMENT.exists())

    def test_existing_key_policy_or_recovery_copy_is_never_overwritten(self):
        self.initialized()
        before = signing.KEY.read_bytes(), signing.PASSWORD.read_bytes(), signing.BACKUP.read_bytes()
        self.run.reset_mock()
        with self.assertRaises(signing.SigningError): signing.initialize()
        self.run.assert_not_called()
        self.assertEqual(before, (signing.KEY.read_bytes(), signing.PASSWORD.read_bytes(), signing.BACKUP.read_bytes()))

    def test_a_missing_key_after_provisioning_cannot_trigger_regeneration(self):
        self.initialized(); signing.KEY.unlink(); self.run.reset_mock()
        with self.assertRaises(signing.SigningError): signing.initialize()
        with self.assertRaises(signing.SigningError): signing.verify_backup()
        self.run.assert_not_called()

    def test_password_key_and_backup_are_owner_only_and_copy_can_recover_private_key(self):
        self.initialized()
        for path in (signing.KEY, signing.PASSWORD, signing.BACKUP):
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
        self.assertEqual(0o700, signing.PRIVATE.stat().st_mode & 0o777)
        self.assertTrue(signing.verify_backup()['private_key_and_recovered_copy_verified'])
        self.assertFalse(list(signing.PRIVATE.glob('recovery-check-*')))
        self.assertGreaterEqual(sum('-certreq' in call.args[0] for call in self.run.call_args_list), 4)

    def test_password_and_archive_also_need_git_ignore_not_only_the_key_extension(self):
        with patch.object(signing.subprocess, 'run', side_effect=[SimpleNamespace(returncode=0), SimpleNamespace(returncode=1)]):
            with self.assertRaises(signing.SigningError): signing.initialize()
        self.run.assert_not_called()
        self.assertFalse(signing.PRIVATE.exists())

    def test_symlink_directory_is_rejected_before_any_generation(self):
        target = self.root / 'target'; target.mkdir()
        signing.PRIVATE_ROOT.symlink_to(target, target_is_directory=True)
        with self.assertRaises(signing.SigningError): signing.initialize()
        self.run.assert_not_called()
        self.assertEqual([], list(target.iterdir()))

    def test_a_shared_password_file_blocks_private_key_use(self):
        self.initialized(); os.chmod(signing.PASSWORD, 0o644); self.run.reset_mock()
        with self.assertRaises(signing.SigningError): signing.verify_backup()
        self.run.assert_not_called()

    def test_different_private_recovery_copy_is_not_silently_accepted(self):
        self.initialized()
        with zipfile.ZipFile(signing.BACKUP) as archive:
            items = {name: archive.read(name) for name in archive.namelist()}
        items['novalisten-next.p12'] = b'different-synthetic-key'
        with zipfile.ZipFile(signing.BACKUP, 'w') as archive:
            for name, data in items.items(): archive.writestr(name, data)
        with self.assertRaises(signing.SigningError): signing.verify_backup()

    def test_a_public_certificate_without_the_private_key_cannot_validate_recovery(self):
        self.initialized()
        def missing_private(command):
            if '-certreq' in command: raise signing.SigningError('No private key fixture')
            return self.fake_run(command)
        self.run.side_effect = missing_private
        with self.assertRaises(signing.SigningError): signing.verify_backup()

    def test_signing_is_blocked_until_owner_confirms_independent_backup(self):
        self.initialized(); args = self.fixture()
        with self.assertRaises(signing.SigningError): signing.sign_apk(*args)
        self.assertFalse(args[1].exists())
        self.assertFalse(any(call.args[0][0] == 'java' for call in self.run.call_args_list))

    def test_old_application_id_cannot_be_signed_even_with_matching_source_hash(self):
        self.initialized(acknowledge=True); args = self.fixture(package='com.novalisten.app')
        with self.assertRaises(signing.SigningError): signing.sign_apk(*args)
        self.assertFalse(args[1].exists())

    def test_changed_unsigned_apk_is_not_signed(self):
        self.initialized(acknowledge=True); args = self.fixture(); args[0].write_bytes(b'changed')
        with self.assertRaises(signing.SigningError): signing.sign_apk(*args)
        self.assertFalse(args[1].exists())

    def test_test_apk_must_also_target_the_new_identity(self):
        self.initialized(acknowledge=True)
        args = self.fixture(package=signing.APPLICATION_ID + '.test', target='com.novalisten.app')
        with self.assertRaises(signing.SigningError): signing.sign_apk(*args, test_apk=True)
        self.assertFalse(args[1].exists())

    def test_successful_signing_uses_password_files_and_verifies_the_single_pinned_signer(self):
        self.initialized(acknowledge=True); args = self.fixture()
        result = signing.sign_apk(*args)
        self.assertEqual(signing.digest(b'synthetic-signed-apk'), result['sha256'])
        command = next(call.args[0] for call in self.run.call_args_list if call.args[0][:2] == ['java', '-jar'] and 'sign' in call.args[0])
        self.assertIn('file:' + str(signing.PASSWORD), command)
        self.assertNotIn(signing.PASSWORD.read_text().strip(), ' '.join(command))
        with self.assertRaises(signing.SigningError): signing.sign_apk(*args)

    def test_multiple_or_wrong_apk_certificates_are_rejected(self):
        self.initialized(); policy = signing.load_policy()
        apk = self.root / 'fixture.apk'; apk.write_bytes(b'not-a-real-apk')
        for report in ['Signer #1 certificate SHA-256 digest: ' + '0' * 64,
                       'Signer #1 certificate SHA-256 digest: ' + policy['certificate_sha256'] + '\nSigner #2 certificate SHA-256 digest: ' + policy['certificate_sha256']]:
            self.run.return_value = report.encode(); self.run.side_effect = None
            with self.assertRaises(signing.SigningError): signing.verify_apk(apk, 'signer.jar')

    def test_failed_signature_does_not_leave_an_unverified_deliverable(self):
        self.initialized(acknowledge=True); args = self.fixture()
        def failure(command):
            if 'verify' in command: raise signing.SigningError('Invalid synthetic signature')
            return self.fake_run(command)
        self.run.side_effect = failure
        with self.assertRaises(signing.SigningError): signing.sign_apk(*args)
        self.assertFalse(args[1].exists())


if __name__ == '__main__':
    unittest.main()
