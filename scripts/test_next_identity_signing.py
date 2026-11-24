"""One-time reinstall provisioning gates. All keys/certificates are synthetic."""
import base64
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

import next_signing_identity as identity
import parallel_signing as legacy


class NextIdentitySigningTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name); (self.root / 'config').mkdir()
        self.old_next = {'application_id': legacy.APPLICATION_ID, 'label': legacy.LABEL,
                         'certificate_sha256': '1' * 64, 'key_alias': legacy.ALIAS, 'private_key_in_ci': False}
        self.historical = {'certificate_sha256': '9' * 64}
        self.old_policy = self.root / 'config/parallel-signing-policy.json'
        self.historical_policy = self.root / 'config/signing-policy.json'
        self.old_policy.write_text(json.dumps(self.old_next))
        self.historical_policy.write_text(json.dumps(self.historical))
        self.old_bytes = self.old_policy.read_bytes(), self.historical_policy.read_bytes()
        self.private_root = self.root / 'private-signing'
        self.private = self.private_root / 'novalisten-next-0.3.1'
        self.custody_path = self.root / 'config/next-signing-custody.json'
        self.custody = {'schema': 1, 'application_id': legacy.APPLICATION_ID,
                        'repository': 'gagaladie08-pixel/Nova-Rush',
                        'secret_name': 'NOVALISTEN_NEXT_SIGNING_BACKUP_B64',
                        'status': 'pending_secret_access', 'certificate_sha256': None,
                        'backup_sha256': None, 'owner_recovery_copy_confirmed': False}
        self.custody_path.write_text(json.dumps(self.custody))
        paths = {'ROOT': self.root, 'POLICY_PATH': self.root / 'config/next-signing-policy.json',
                 'CUSTODY_PATH': self.custody_path, 'PRIVATE_ROOT': self.private_root, 'PRIVATE': self.private,
                 'KEY': self.private / 'novalisten-next.p12', 'PASSWORD': self.private / 'password.txt',
                 'BACKUP': self.private_root / 'new-backup.zip', 'SECRET_FILE': self.private_root / 'private-secret.txt'}
        patches = [patch.multiple(identity, **paths), patch.multiple(legacy, ROOT=self.root, POLICY_PATH=self.old_policy),
                   patch.dict(os.environ, {}, clear=True)]
        for item in patches:
            item.start(); self.addCleanup(item.stop)
        self.git_patch = patch.object(legacy.subprocess, 'run', return_value=SimpleNamespace(returncode=0))
        self.git = self.git_patch.start(); self.addCleanup(self.git_patch.stop)
        self.tool_patch = patch.object(legacy, 'tool', side_effect=lambda name: name)
        self.tool_patch.start(); self.addCleanup(self.tool_patch.stop)
        self.runner_patch = patch.object(legacy, 'run', side_effect=self.fake_run)
        self.runner = self.runner_patch.start(); self.addCleanup(self.runner_patch.stop)

    def fake_run(self, command):
        if '-genkeypair' in command:
            self.assertEqual(str(identity.KEY), command[command.index('-keystore') + 1])
            legacy.private_write(identity.KEY, b'synthetic-next-031-private-store-not-a-key')
        if '-exportcert' in command:
            return b'synthetic-next-031-public-certificate'
        return b'synthetic-public-tool-result'

    def provision(self):
        return identity.provision(self.old_next['certificate_sha256'], True, True)

    def test_provisioning_creates_separate_pinned_identity_and_preserves_both_old_policies(self):
        receipt = self.provision()
        self.assertEqual(self.old_bytes, (self.old_policy.read_bytes(), self.historical_policy.read_bytes()))
        self.assertNotIn(receipt['certificate_sha256'], ['1' * 64, '9' * 64])
        policy = identity.load_policy()
        self.assertTrue(policy['requires_reinstall_from_0_3_0'])
        self.assertEqual(self.old_next['certificate_sha256'], policy['previous_certificate_sha256'])
        self.assertEqual(self.old_next, legacy.load_policy())
        self.assertFalse(policy['automatic_key_generation'])
        self.assertTrue(policy['private_key_in_ci'])
        self.assertFalse(receipt['durable_secret_read_verified'])
        self.assertFalse(receipt['owner_recovery_copy_confirmed'])
        self.assertFalse(receipt['apk_signed_or_delivered'])
        self.assertNotIn(identity.PASSWORD.read_text().strip(), json.dumps(receipt))

    def test_backup_and_secret_file_are_private_and_contain_the_exact_same_recovery_archive(self):
        self.provision()
        for path in [identity.KEY, identity.PASSWORD, identity.BACKUP, identity.SECRET_FILE]:
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
        for path in [identity.PRIVATE_ROOT, identity.PRIVATE]:
            self.assertEqual(0o700, path.stat().st_mode & 0o777)
        self.assertEqual(identity.BACKUP.read_bytes(), base64.b64decode(identity.SECRET_FILE.read_text().strip(), validate=True))
        with zipfile.ZipFile(identity.BACKUP) as archive:
            self.assertEqual({'novalisten-next.p12', 'password.txt', 'public-policy.json', 'RESTORE.txt'}, set(archive.namelist()))
            self.assertEqual(identity.KEY.read_bytes(), archive.read('novalisten-next.p12'))
            self.assertEqual(identity.PASSWORD.read_bytes(), archive.read('password.txt'))
            self.assertEqual(identity.load_policy(), json.loads(archive.read('public-policy.json')))
        self.assertFalse(list(identity.PRIVATE.glob('copy-check-*')))
        self.assertGreaterEqual(sum('-certreq' in c.args[0] for c in self.runner.call_args_list), 2)

    def test_custody_remains_unverified_after_local_provisioning(self):
        receipt = self.provision()
        custody = json.loads(self.custody_path.read_text())
        self.assertEqual('awaiting_remote_verification', custody['status'])
        self.assertEqual(receipt['backup_sha256'], custody['backup_sha256'])
        self.assertFalse(custody['owner_recovery_copy_confirmed'])

    def test_both_explicit_owner_confirmations_are_required_before_generation(self):
        for data_loss, can_store in [(False, True), (True, False), (False, False)]:
            with self.subTest(flags=(data_loss, can_store)):
                with self.assertRaises(identity.IdentityError):
                    identity.provision(self.old_next['certificate_sha256'], data_loss, can_store)
        self.runner.assert_not_called()
        self.assertFalse(self.private_root.exists())

    def test_actions_cannot_generate_a_key_even_with_all_confirmations(self):
        os.environ['GITHUB_ACTIONS'] = 'true'
        with self.assertRaises(identity.IdentityError): self.provision()
        self.runner.assert_not_called()
        self.assertFalse(self.private_root.exists())

    def test_expected_previous_certificate_must_match_before_any_generation(self):
        with self.assertRaises(identity.IdentityError): identity.provision('2' * 64, True, True)
        self.runner.assert_not_called()
        self.assertFalse(self.private_root.exists())

    def test_provisioning_never_runs_again_after_success(self):
        self.provision()
        before = identity.KEY.read_bytes(), identity.BACKUP.read_bytes(), identity.POLICY_PATH.read_bytes()
        self.runner.reset_mock()
        with self.assertRaises(identity.IdentityError): self.provision()
        self.runner.assert_not_called()
        self.assertEqual(before, (identity.KEY.read_bytes(), identity.BACKUP.read_bytes(), identity.POLICY_PATH.read_bytes()))

    def test_missing_key_cannot_regenerate_an_existing_identity(self):
        self.provision(); identity.KEY.unlink(); self.runner.reset_mock()
        with self.assertRaises(identity.IdentityError): self.provision()
        self.runner.assert_not_called()

    def test_an_interrupted_partial_private_directory_also_blocks_regeneration(self):
        self.private_root.mkdir(mode=0o700); self.private.mkdir(mode=0o700)
        with self.assertRaises(identity.IdentityError): self.provision()
        self.runner.assert_not_called()

    def test_existing_backup_or_payload_blocks_new_identity(self):
        for path in [identity.BACKUP, identity.SECRET_FILE]:
            self.private_root.mkdir(mode=0o700, exist_ok=True)
            legacy.private_write(path, b'existing-private-copy')
            with self.subTest(path=path.name):
                with self.assertRaises(identity.IdentityError): self.provision()
            path.unlink()
        self.runner.assert_not_called()

    def test_existing_legacy_private_files_are_not_touched(self):
        self.private_root.mkdir(mode=0o700)
        old_dir = self.private_root / 'novalisten-next'; old_dir.mkdir(mode=0o700)
        old_key = old_dir / 'novalisten-next.p12'
        legacy.private_write(old_key, b'legacy-key-not-to-be-replaced')
        self.provision()
        self.assertEqual(b'legacy-key-not-to-be-replaced', old_key.read_bytes())

    def test_shared_or_symlink_directory_blocks_generation(self):
        self.private_root.mkdir(mode=0o755); os.chmod(self.private_root, 0o755)
        with self.assertRaises(identity.IdentityError): self.provision()
        self.private_root.rmdir()
        other = self.root / 'other'; other.mkdir()
        self.private_root.symlink_to(other, target_is_directory=True)
        with self.assertRaises(identity.IdentityError): self.provision()
        self.runner.assert_not_called()
        self.assertEqual([], list(other.iterdir()))

    def test_all_private_paths_must_be_ignored_before_generation(self):
        self.git.side_effect = [SimpleNamespace(returncode=0), SimpleNamespace(returncode=1)]
        with self.assertRaises(identity.IdentityError): self.provision()
        self.runner.assert_not_called()
        self.assertFalse(self.private_root.exists())

    def test_preprovisioned_or_wrong_custody_never_triggers_generation(self):
        for change in [{'status':'ready'}, {'certificate_sha256':'1'*64}, {'backup_sha256':'a'*64},
                       {'owner_recovery_copy_confirmed':True}, {'application_id':'com.novalisten.app'},
                       {'secret_name':'OTHER'}, {'repository':'other/repo'}]:
            with self.subTest(change=change):
                self.custody_path.write_text(json.dumps({**self.custody, **change}))
                with self.assertRaises(identity.IdentityError): self.provision()
        self.runner.assert_not_called()

    def test_changed_new_policy_is_rejected_without_using_a_private_key(self):
        self.provision(); original = identity.load_policy(); self.runner.reset_mock()
        for change in [{'application_id':'com.novalisten.app'}, {'private_key_in_ci':False},
                       {'automatic_key_generation':True}, {'certificate_sha256':'1'*64},
                       {'certificate_sha256':'9'*64}, {'previous_certificate_sha256':'3'*64},
                       {'requires_reinstall_from_0_3_0':False}, {'first_version':'0.3.0'},
                       {'storage':'local-only'}, {'key_alias':'other'}]:
            with self.subTest(change=change):
                identity.POLICY_PATH.write_text(json.dumps({**original, **change}))
                with self.assertRaises(identity.IdentityError): identity.load_policy()
        self.runner.assert_not_called()

    def test_missing_new_policy_is_not_implicitly_the_old_policy(self):
        with self.assertRaises(identity.IdentityError): identity.load_policy()
        self.runner.assert_not_called()
        self.assertFalse(identity.POLICY_PATH.exists())

    def test_tool_failure_is_not_printed_or_followed_by_automatic_regeneration(self):
        marker = 'synthetic-password-in-tool-stderr-must-not-appear'
        self.runner.side_effect = RuntimeError(marker)
        args = ['provision-reinstall', '--expected-previous-certificate', self.old_next['certificate_sha256'],
                '--owner-accepted-next-data-loss', '--owner-can-create-repository-secret']
        with contextlib.redirect_stderr(io.StringIO()) as err, contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(1, identity.main(args))
        self.assertNotIn(marker, err.getvalue())
        self.assertNotIn('Traceback', err.getvalue())
        self.assertEqual('', out.getvalue())
        self.runner.reset_mock()
        with self.assertRaises(identity.IdentityError): self.provision()
        self.runner.assert_not_called()


if __name__ == '__main__':
    unittest.main()
