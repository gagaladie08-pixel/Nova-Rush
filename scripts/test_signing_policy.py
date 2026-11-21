"""Offline policy tests. Tool responses and keystore bytes are synthetic, NOT real signing keys."""
import base64
import hashlib
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import signing_policy as signing


class SigningPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.env_file = self.root / 'github-env'
        self.certificate = b'synthetic-public-certificate-for-tests-only'
        self.policy = {'certificate_sha256': hashlib.sha256(self.certificate).hexdigest(),
                       'key_alias': 'androiddebugkey'}
        self.fake_data = b'synthetic-keystore-not-a-real-private-key'
        self.encoded = base64.b64encode(self.fake_data).decode()

    def prepare(self, encoded=None):
        return signing.prepare_key(self.encoded if encoded is None else encoded,
                                   self.root, self.policy, self.env_file)

    def test_reference_is_the_inspected_023_certificate_not_the_apk_hash(self):
        policy = signing.load_policy()
        self.assertEqual('0.2.3', policy['reference_version'])
        self.assertEqual('9c6531e77ff9c802dc5622d2d041b07812e5790a56b159c27502214d4ff77d95',
                         policy['certificate_sha256'])

    @patch.object(signing, 'run_tool')
    def test_missing_key_blocks_without_generating_or_writing_anything(self, tool):
        for encoded in ['', ' \n\t']:
            with self.assertRaises(signing.SigningError):
                self.prepare(encoded)
        tool.assert_not_called()
        self.assertEqual([], list(self.root.iterdir()))

    @patch.object(signing, 'run_tool')
    def test_invalid_or_oversized_encoding_is_rejected_before_running_a_tool(self, tool):
        for encoded in ['invalid % value', base64.b64encode(b'x' * (signing.MAX_KEYSTORE_BYTES + 1)).decode()]:
            with self.assertRaises(signing.SigningError):
                self.prepare(encoded)
        tool.assert_not_called()
        self.assertEqual([], list(self.root.iterdir()))

    def test_matching_original_key_is_private_and_only_its_path_is_exported(self):
        with patch.object(signing, 'run_tool', return_value=self.certificate) as tool:
            path = self.prepare()
        self.assertEqual(self.fake_data, path.read_bytes())
        self.assertEqual(0o600, path.stat().st_mode & 0o777)
        self.assertEqual(0o700, path.parent.stat().st_mode & 0o777)
        self.assertEqual('NOVALISTEN_KEYSTORE_PATH=' + str(path) + '\n', self.env_file.read_text())
        self.assertNotIn(self.encoded, self.env_file.read_text())
        self.assertNotIn(self.fake_data.decode(), self.env_file.read_text())
        commands = [call.args[0] for call in tool.call_args_list]
        self.assertEqual(['keytool', '-exportcert'], commands[0][:2])
        self.assertEqual(['keytool', '-certreq'], commands[1][:2])
        self.assertFalse(any('genkey' in value for command in commands for value in command))

    def test_multiline_base64_is_accepted_without_changing_the_key(self):
        wrapped = self.encoded[:12] + '\n' + self.encoded[12:] + '\n'
        with patch.object(signing, 'run_tool', return_value=self.certificate):
            self.assertEqual(self.fake_data, self.prepare(wrapped).read_bytes())

    def test_different_certificate_is_removed_and_never_exported(self):
        with patch.object(signing, 'run_tool', return_value=b'wrong-public-certificate'):
            with self.assertRaises(signing.SigningError):
                self.prepare()
        self.assertEqual([], list(self.root.iterdir()))

    def test_unreadable_keystore_cannot_trigger_a_new_key_or_leave_a_private_copy(self):
        with patch.object(signing, 'run_tool', side_effect=signing.SigningError('Synthetic tool failure')) as tool:
            with self.assertRaises(signing.SigningError):
                self.prepare()
        self.assertEqual(1, tool.call_count)
        self.assertEqual([], list(self.root.iterdir()))

    def test_matching_public_certificate_alone_does_not_count_as_recovered_private_key(self):
        with patch.object(signing, 'run_tool', side_effect=[self.certificate, signing.SigningError('No private key')]):
            with self.assertRaises(signing.SigningError):
                self.prepare()
        self.assertEqual([], list(self.root.iterdir()))

    def test_even_a_matching_private_key_cannot_be_restored_inside_the_repository(self):
        with patch.object(signing, 'run_tool') as tool:
            with self.assertRaises(signing.SigningError):
                signing.prepare_key(self.encoded, signing.ROOT, self.policy, self.env_file)
        tool.assert_not_called()

    def test_environment_export_failure_removes_the_private_copy(self):
        with patch.object(signing, 'run_tool', return_value=self.certificate):
            with self.assertRaises(OSError):
                signing.prepare_key(self.encoded, self.root, self.policy, self.root / 'missing' / 'env')
        self.assertEqual([], list(self.root.iterdir()))

    def test_local_validation_of_an_absent_store_cannot_generate_or_export_a_key(self):
        with patch.object(signing, 'run_tool') as tool:
            with self.assertRaises(signing.SigningError):
                signing.validate_keystore(self.root / 'absent.keystore', self.policy)
        tool.assert_not_called()
        self.assertEqual([], list(self.root.iterdir()))

    def test_local_packaging_validation_checks_the_certificate_and_possession_of_the_private_key(self):
        path = self.root / 'synthetic.keystore'
        path.write_bytes(self.fake_data)
        with patch.object(signing, 'run_tool', side_effect=[self.certificate, b'synthetic-csr']) as tool:
            self.assertEqual(path, signing.validate_keystore(path, self.policy))
        self.assertEqual(2, tool.call_count)
        with patch.object(signing, 'run_tool', side_effect=[self.certificate, signing.SigningError('No private key')]):
            with self.assertRaises(signing.SigningError):
                signing.validate_keystore(path, self.policy)
        self.assertEqual(self.fake_data, path.read_bytes())

    def test_local_validation_never_accepts_a_key_path_inside_the_checkout(self):
        with patch.object(signing, 'run_tool') as tool:
            with self.assertRaises(signing.SigningError):
                signing.validate_keystore(signing.ROOT / 'do-not-create.keystore', self.policy)
        tool.assert_not_called()

    def apk_fixture(self):
        apk = self.root / 'fixture.apk'
        apk.write_bytes(b'not-a-real-apk-tool-response-is-mocked')
        return apk

    def test_actual_apk_verification_must_succeed_and_report_the_exact_signer(self):
        apk = self.apk_fixture()
        output = 'Signer #1 certificate SHA-256 digest: ' + self.policy['certificate_sha256'].upper() + '\n'
        with patch.object(signing, 'run_tool', return_value=output) as tool:
            self.assertEqual(self.policy['certificate_sha256'], signing.verify_apk(apk, 'apksigner', self.policy))
        self.assertEqual(['apksigner', 'verify', '--verbose', '--print-certs', str(apk)], tool.call_args.args[0])

    def test_missing_different_or_multiple_signers_block_delivery(self):
        apk = self.apk_fixture()
        outputs = ['No signer digest',
                   'Signer #1 certificate SHA-256 digest: ' + '0' * 64,
                   'Signer #1 certificate SHA-256 digest: ' + self.policy['certificate_sha256'] + '\n' +
                   'Signer #2 certificate SHA-256 digest: ' + self.policy['certificate_sha256']]
        for output in outputs:
            with self.subTest(output=output), patch.object(signing, 'run_tool', return_value=output):
                with self.assertRaises(signing.SigningError):
                    signing.verify_apk(apk, 'apksigner', self.policy)

    def test_apksigner_failure_cannot_be_mistaken_for_a_matching_certificate(self):
        with patch.object(signing, 'run_tool', side_effect=signing.SigningError('Invalid APK signature')):
            with self.assertRaises(signing.SigningError):
                signing.verify_apk(self.apk_fixture(), 'apksigner', self.policy)

    def test_missing_apk_never_gets_approved(self):
        with patch.object(signing, 'run_tool') as tool:
            with self.assertRaises(signing.SigningError):
                signing.verify_apk(self.root / 'absent.apk', 'apksigner', self.policy)
        tool.assert_not_called()

    def test_tool_errors_do_not_expose_the_input_or_stderr(self):
        from subprocess import CompletedProcess
        result = CompletedProcess(['keytool'], 1, b'', b'sensitive synthetic tool diagnostic')
        with patch.object(signing.subprocess, 'run', return_value=result):
            with self.assertRaises(signing.SigningError) as failure:
                signing.run_tool(['keytool'])
        self.assertNotIn('sensitive', str(failure.exception))
        self.assertNotIn(self.encoded, str(failure.exception))

    def test_cli_does_not_generate_a_key_when_the_required_secret_is_missing(self):
        environment = {'RUNNER_TEMP': str(self.root), 'GITHUB_ENV': str(self.env_file)}
        with patch.dict(os.environ, environment, clear=True), patch('sys.argv', ['signing_policy.py', 'prepare']):
            with patch.object(signing, 'run_tool') as tool, patch('sys.stderr'):
                self.assertEqual(1, signing.main())
        tool.assert_not_called()
        self.assertEqual([], list(self.root.iterdir()))


if __name__ == '__main__':
    unittest.main()
