"""Synthetic vault fixtures only. No real key generation, secrets or network."""
import base64
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import warnings
import zipfile

import next_key_vault as vault
import next_signing_identity as identity
import parallel_signing as signing


class NextVaultSigningTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / 'config').mkdir()
        self.private_root = self.root / 'private-signing'
        self.private = self.private_root / 'novalisten-next'
        self.policy_path = self.root / 'config/next-signing-policy.json'
        self.old_policy_path = self.root / 'config/parallel-signing-policy.json'
        self.custody_path = self.root / 'config/next-signing-custody.json'
        self.policy = {'application_id': signing.APPLICATION_ID, 'label': signing.LABEL,
                       'certificate_sha256': hashlib.sha256(b'synthetic-certificate').hexdigest(),
                       'key_alias': signing.ALIAS, 'private_key_in_ci': True,
                       'keystore_type': 'PKCS12', 'key_algorithm': 'RSA-3072', 'first_version': '0.3.1',
                       'automatic_key_generation': False, 'storage': identity.STORAGE,
                       'requires_reinstall_from_0_3_0': True, 'previous_certificate_sha256': '1' * 64}
        self.policy_path.write_text(json.dumps(self.policy))
        self.old_policy_path.write_text(json.dumps({'application_id': signing.APPLICATION_ID,
            'label': signing.LABEL, 'key_alias': signing.ALIAS, 'certificate_sha256': '1' * 64}))
        (self.root / 'config/signing-policy.json').write_text(json.dumps({'certificate_sha256': '9' * 64}))
        paths = {'ROOT': self.root, 'POLICY_PATH': self.old_policy_path,
                 'PRIVATE_ROOT': self.private_root, 'PRIVATE': self.private,
                 'KEY': self.private / 'novalisten-next.p12', 'PASSWORD': self.private / 'password.txt',
                 'BACKUP': self.private_root / 'backup.zip',
                 'ACKNOWLEDGEMENT': self.private / 'owner-backup-confirmed.json'}
        patches = [patch.multiple(signing, **paths), patch.object(vault, 'CUSTODY_PATH', self.custody_path),
                   patch.multiple(identity, ROOT=self.root, POLICY_PATH=self.policy_path),
                   patch.dict(os.environ, {}, clear=True)]
        for item in patches:
            item.start(); self.addCleanup(item.stop)
        self.git_patch = patch.object(signing.subprocess, 'run', return_value=SimpleNamespace(returncode=0))
        self.git = self.git_patch.start(); self.addCleanup(self.git_patch.stop)
        self.tool_patch = patch.object(signing, 'tool', side_effect=lambda name: name)
        self.tool_patch.start(); self.addCleanup(self.tool_patch.stop)
        self.runner_patch = patch.object(signing, 'run', side_effect=self.public_tool_fixture)
        self.runner = self.runner_patch.start(); self.addCleanup(self.runner_patch.stop)
        self.files = {'novalisten-next.p12': b'synthetic-private-store-not-a-key',
                      'password.txt': b'synthetic-private-password-never-print-this-12345\n',
                      'public-policy.json': json.dumps(self.policy).encode(),
                      'RESTORE.txt': b'synthetic restoration instructions'}
        self.data = self.archive(self.files.items())
        self.custody = {'schema': 1, 'application_id': signing.APPLICATION_ID,
                        'repository': vault.REPOSITORY, 'secret_name': vault.SECRET_NAME,
                        'status': 'awaiting_remote_verification',
                        'certificate_sha256': self.policy['certificate_sha256'],
                        'backup_sha256': signing.digest(self.data),
                        'owner_recovery_copy_confirmed': False}
        self.store_custody()
        self.set_secret()

    def archive(self, entries):
        output = io.BytesIO()
        with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as z:
            for name, contents in entries:
                z.writestr(name, contents)
        return output.getvalue()

    def store_custody(self):
        self.custody_path.write_text(json.dumps(self.custody))

    def set_secret(self, data=None):
        if data is not None:
            self.data = data
            self.custody['backup_sha256'] = signing.digest(data)
            self.store_custody()
        os.environ[vault.SECRET_NAME] = base64.b64encode(self.data).decode('ascii')

    def public_tool_fixture(self, command):
        self.assertNotIn(vault.SECRET_NAME, os.environ)
        self.assertNotIn('-genkeypair', command)
        self.assertNotIn('sign', command)
        for flag in ['-keystore', '-storepass:file']:
            path = Path(command[command.index(flag) + 1])
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            self.assertEqual(0o700, path.parent.stat().st_mode & 0o777)
        return b'synthetic-certificate' if '-exportcert' in command else b'synthetic-public-csr'

    def github_origin(self):
        os.environ.update({'GITHUB_ACTIONS': 'true', 'GITHUB_REPOSITORY': vault.REPOSITORY,
                           'GITHUB_REF': vault.BRANCH_REF, 'GITHUB_RUN_ID': '123',
                           'GITHUB_SHA': 'a' * 40, 'GITHUB_EVENT_NAME': 'push'})

    def test_recovery_uses_private_key_and_cleans_files_without_claiming_remote_persistence(self):
        result = vault.verify_from_environment()
        self.assertTrue(result['private_key_and_recovered_copy_verified'])
        self.assertEqual(self.custody['backup_sha256'], result['backup_sha256'])
        self.assertFalse(result['durable_secret_read_verified'])
        self.assertFalse(result['custody_ready_for_signing'])
        self.assertEqual([], list(self.private_root.iterdir()))
        self.assertEqual(0o700, self.private_root.stat().st_mode & 0o777)
        self.assertEqual(2, self.runner.call_count)
        self.assertTrue(any('-certreq' in c.args[0] for c in self.runner.call_args_list))
        self.assertNotIn(self.files['password.txt'].decode().strip(), json.dumps(result))

    def test_github_recovery_is_not_an_owner_backup_confirmation(self):
        self.github_origin()
        result = vault.verify_from_environment()
        self.assertTrue(result['durable_secret_read_verified'])
        self.assertFalse(result['owner_recovery_copy_confirmed'])
        self.assertFalse(result['custody_ready_for_signing'])
        self.assertEqual(123, result['run_id'])
        self.assertEqual('a' * 40, result['commit'])

    def test_ready_requires_both_github_recovery_and_independent_owner_copy(self):
        self.github_origin()
        self.custody.update(status='ready', owner_recovery_copy_confirmed=True)
        self.store_custody()
        self.assertTrue(vault.verify_from_environment()['custody_ready_for_signing'])

    def test_ready_config_without_owner_confirmation_is_rejected(self):
        self.custody['status'] = 'ready'; self.store_custody()
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_local_success_never_becomes_remote_proof_even_with_a_ready_registry(self):
        self.custody.update(status='ready', owner_recovery_copy_confirmed=True)
        self.store_custody()
        self.assertFalse(vault.verify_from_environment()['custody_ready_for_signing'])

    def test_missing_secret_never_generates_a_replacement(self):
        del os.environ[vault.SECRET_NAME]
        with self.assertRaisesRegex(vault.VaultError, 'aucune génération'):
            vault.verify_from_environment()
        self.runner.assert_not_called()
        self.assertFalse(self.private_root.exists())

    def test_invalid_base64_is_removed_from_environment_and_not_echoed(self):
        marker = 'sensitive-not-base64-please-never-print'
        os.environ[vault.SECRET_NAME] = marker
        with contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(1, vault.main(['verify']))
        self.assertNotIn(marker, err.getvalue())
        self.assertNotIn(vault.SECRET_NAME, os.environ)
        self.runner.assert_not_called()

    def test_whitespace_only_or_too_large_or_non_ascii_payload_is_rejected(self):
        for value in [' \n', 'A' * (vault.MAX_SECRET_CHARS + 3), 'é']:
            with self.subTest(value_type=len(value)):
                os.environ[vault.SECRET_NAME] = value
                with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_empty_decoded_archive_is_rejected(self):
        os.environ[vault.SECRET_NAME] = '===='
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_oversized_decoded_archive_is_rejected(self):
        os.environ[vault.SECRET_NAME] = base64.b64encode(b'x' * (vault.MAX_ARCHIVE_BYTES + 1)).decode()
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_pending_or_corrupt_custody_blocks_key_use(self):
        for changes in [dict(status='pending_secret_access'), dict(application_id='com.novalisten.app'),
                        dict(repository='someone/else'), dict(secret_name='OTHER_SECRET'),
                        dict(certificate_sha256='f' * 64), dict(backup_sha256=None),
                        dict(backup_sha256='x' * 64), dict(owner_recovery_copy_confirmed='yes'),
                        dict(schema=2), dict(schema=True), dict(status=[]), dict(status=None)]:
            original = dict(self.custody)
            with self.subTest(changes=changes):
                self.custody.update(changes); self.store_custody(); self.set_secret()
                with self.assertRaises(vault.VaultError): vault.verify_from_environment()
                self.runner.assert_not_called()
            self.custody = original

    def test_historical_local_only_policy_cannot_be_silently_used_in_actions(self):
        self.policy['private_key_in_ci'] = False
        self.policy_path.write_text(json.dumps(self.policy))
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_missing_or_non_object_custody_has_no_secret_traceback(self):
        self.custody_path.unlink()
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.set_secret(); self.custody_path.write_text('[]')
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_historical_023_identity_or_certificate_cannot_be_restored_as_next(self):
        for change in [{'application_id': 'com.novalisten.app'}, {'certificate_sha256': '9' * 64}]:
            policy = dict(self.policy); policy.update(change)
            self.policy_path.write_text(json.dumps(policy)); self.set_secret()
            with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_another_backup_with_the_same_public_policy_does_not_match_pinned_hash(self):
        data = dict(self.files); data['password.txt'] = b'another-synthetic-password-not-real-123456789'
        os.environ[vault.SECRET_NAME] = base64.b64encode(self.archive(data.items())).decode()
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_invalid_zip_does_not_create_private_files(self):
        self.set_secret(b'not a zip archive')
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.assertFalse(self.private_root.exists())
        self.runner.assert_not_called()

    def test_unexpected_missing_or_traversing_zip_entries_are_rejected(self):
        for name in ['../password.txt', 'extra.txt', '/absolute-path', 'folder/password.txt']:
            files = list(self.files.items()) + [(name, b'unexpected')]
            with self.subTest(name=name):
                self.set_secret(self.archive(files))
                with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.set_secret(self.archive([(n, v) for n, v in self.files.items() if n != 'password.txt']))
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_duplicate_zip_entry_is_rejected_even_when_name_set_matches(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            self.set_secret(self.archive(list(self.files.items()) + [('password.txt', self.files['password.txt'])]))
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_compressed_bomb_is_rejected_before_decompression_or_key_use(self):
        files = dict(self.files); files['RESTORE.txt'] = b'A' * 1_000_000
        self.set_secret(self.archive(files.items()))
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()
        self.assertFalse(self.private_root.exists())

    def test_symlink_entry_is_not_treated_as_a_private_store(self):
        entries = []
        for name, value in self.files.items():
            if name == 'novalisten-next.p12':
                entry = zipfile.ZipInfo(name); entry.create_system = 3
                entry.external_attr = (stat.S_IFLNK | 0o777) << 16
                entries.append((entry, b'/not/a/key'))
            else:
                entries.append((name, value))
        self.set_secret(self.archive(entries))
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_wrong_or_unreadable_archived_public_policy_blocks_key_use(self):
        for content in [b'{not json', b'[]', json.dumps({**self.policy, 'key_alias': 'different'}).encode()]:
            files = dict(self.files); files['public-policy.json'] = content
            self.set_secret(self.archive(files.items()))
            with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_overly_short_password_and_oversized_key_are_rejected(self):
        for name, value in [('password.txt', b'short'), ('novalisten-next.p12', b'X' * 32769)]:
            files = dict(self.files); files[name] = value
            self.set_secret(self.archive(files.items()))
            with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_wrong_certificate_fails_and_temporary_files_are_cleaned(self):
        self.runner.side_effect = lambda command: b'wrong-certificate'
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.assertEqual([], list(self.private_root.iterdir()))

    def test_public_certificate_alone_is_not_proof_of_private_key_recovery(self):
        def public_only(command):
            if '-certreq' in command:
                raise signing.SigningError('synthetic-key-cannot-sign')
            return b'synthetic-certificate'
        self.runner.side_effect = public_only
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.assertEqual([], list(self.private_root.iterdir()))

    def test_shared_or_symlink_private_root_is_rejected(self):
        self.private_root.mkdir(mode=0o755)
        os.chmod(self.private_root, 0o755)
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.private_root.rmdir()
        other = self.root / 'other'; other.mkdir()
        self.private_root.symlink_to(other, target_is_directory=True); self.set_secret()
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()
        self.assertEqual([], list(other.iterdir()))

    def test_git_ignore_is_required_before_restoring_files(self):
        self.git.return_value = SimpleNamespace(returncode=1)
        with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.assertFalse(self.private_root.exists())
        self.runner.assert_not_called()

    def test_existing_local_key_and_backup_are_never_replaced(self):
        self.private_root.mkdir(mode=0o700); self.private.mkdir(mode=0o700)
        signing.private_write(signing.KEY, b'existing-local-key-must-not-be-replaced')
        signing.private_write(signing.BACKUP, b'existing-local-backup-must-not-be-replaced')
        before = signing.KEY.read_bytes(), signing.BACKUP.read_bytes()
        vault.verify_from_environment()
        self.assertEqual(before, (signing.KEY.read_bytes(), signing.BACKUP.read_bytes()))
        self.assertFalse(list(self.private_root.glob('vault-restore-*')))

    def test_untrusted_github_context_is_not_remote_proof(self):
        for field, value in [('GITHUB_REPOSITORY', 'fork/repo'), ('GITHUB_REF', 'refs/heads/main'),
                             ('GITHUB_EVENT_NAME', 'pull_request_target'), ('GITHUB_RUN_ID', ''),
                             ('GITHUB_SHA', 'not-a-commit')]:
            self.github_origin(); os.environ[field] = value; self.set_secret()
            with self.subTest(field=field):
                with self.assertRaises(vault.VaultError): vault.verify_from_environment()
        self.runner.assert_not_called()

    def test_cli_does_not_print_private_tool_errors_or_tracebacks(self):
        self.runner.side_effect = RuntimeError(self.files['password.txt'].decode())
        with contextlib.redirect_stderr(io.StringIO()) as err, contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(1, vault.main(['verify']))
        self.assertEqual('', out.getvalue())
        self.assertNotIn(self.files['password.txt'].decode().strip(), err.getvalue())
        self.assertNotIn('Traceback', err.getvalue())
        self.assertEqual([], list(self.private_root.iterdir()))

    def test_cli_success_emits_only_public_receipt_not_key_password_or_archive(self):
        encoded = os.environ[vault.SECRET_NAME]
        with contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(0, vault.main(['verify']))
        receipt = json.loads(out.getvalue())
        self.assertEqual(signing.APPLICATION_ID, receipt['application_id'])
        for secret in [encoded, self.files['password.txt'].decode().strip(), self.files['novalisten-next.p12'].decode()]:
            self.assertNotIn(secret, out.getvalue())


class NextVaultWorkflowTest(unittest.TestCase):
    def test_verification_workflow_is_trusted_read_only_and_never_uploads_private_files(self):
        workflow = (Path(__file__).resolve().parent.parent / '.github/workflows/novalisten-next-key-vault.yml').read_text()
        self.assertIn('contents: read', workflow)
        self.assertIn('persist-credentials: false', workflow)
        self.assertIn("github.repository == '" + vault.REPOSITORY + "'", workflow)
        self.assertIn("github.ref == '" + vault.BRANCH_REF + "'", workflow)
        self.assertIn('secrets.' + vault.SECRET_NAME, workflow)
        self.assertIn('python3 scripts/next_key_vault.py verify', workflow)
        for forbidden in ['pull_request', 'upload-artifact', 'github-transfer', '-genkeypair', 'parallel_signing.py init', 'assembleDebug']:
            self.assertNotIn(forbidden, workflow)


if __name__ == '__main__':
    unittest.main()
