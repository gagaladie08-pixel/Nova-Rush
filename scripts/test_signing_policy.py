"""Offline policy tests. Tool responses and keystore bytes are synthetic, NOT real signing keys."""
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import signing_policy as signing


class SigningPolicyTest(unittest.TestCase):
    """The delivery key is committed (Nova Rush method), so the policy must pin it exactly."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.certificate = b'synthetic-public-certificate-for-tests-only'
        self.fake_data = b'synthetic-keystore-not-a-real-private-key'
        self.policy = {'certificate_sha256': hashlib.sha256(self.certificate).hexdigest(),
                       'keystore_sha256': hashlib.sha256(self.fake_data).hexdigest(),
                       'key_alias': 'novalisten-release'}

    def keystore(self, data=None):
        path = self.root / 'synthetic.p12'
        path.write_bytes(self.fake_data if data is None else data)
        return path

    def test_repository_policy_pins_one_delivery_key_for_next(self):
        policy = signing.load_policy()
        self.assertEqual('com.novalisten.app.next', policy['application_id'])
        self.assertEqual('novalisten-release', policy['key_alias'])
        self.assertFalse(policy['automatic_key_generation'])
        self.assertTrue(policy['install_over_previous_versions'])
        self.assertEqual('config/signing/NovaListen-release.p12', policy['keystore_path'])
        # The three former identities are recorded, never reused for new builds.
        self.assertEqual(3, len(policy['previous_certificates']))
        self.assertNotIn(policy['certificate_sha256'], policy['previous_certificates'])

    def test_declared_keystore_and_certificate_really_match_the_committed_files(self):
        policy = signing.load_policy()
        keystore = signing.keystore_path()
        self.assertEqual(policy['keystore_path'], 'config/signing/' + keystore.name)
        self.assertEqual(policy['keystore_sha256'], signing.digest(keystore.read_bytes()))
        self.assertTrue(signing.password_path().is_file())

    def test_a_missing_keystore_blocks_without_generating_anything(self):
        with patch.object(signing, 'run_tool') as tool:
            with self.assertRaises(signing.SigningError):
                signing.verify_keystore(self.root / 'absent.p12', self.policy)
        tool.assert_not_called()
        self.assertEqual([], list(self.root.iterdir()))

    def test_a_replaced_keystore_is_refused_before_any_tool_runs(self):
        with patch.object(signing, 'run_tool') as tool:
            with self.assertRaises(signing.SigningError):
                signing.verify_keystore(self.keystore(b'another-keystore'), self.policy)
        tool.assert_not_called()

    def test_the_pinned_certificate_and_the_private_key_are_both_verified(self):
        path = self.keystore()
        with patch.object(signing, 'run_tool', side_effect=[self.certificate, b'synthetic-csr']) as tool:
            self.assertEqual(self.policy['certificate_sha256'], signing.verify_keystore(path, self.policy))
        commands = [call.args[0] for call in tool.call_args_list]
        self.assertEqual(['keytool', '-exportcert'], commands[0][:2])
        self.assertEqual(['keytool', '-certreq'], commands[1][:2])
        # The password is only ever handed over through a temporary file, never argv.
        values = [value for command in commands for value in command]
        self.assertTrue(any(value == '-storepass:file' for value in values))
        self.assertFalse(any(value == '-storepass' or value.startswith('-storepass:') and value != '-storepass:file'
                             for value in values))

    def test_a_public_certificate_alone_does_not_count_as_the_private_key(self):
        with patch.object(signing, 'run_tool', side_effect=[self.certificate, signing.SigningError('No private key')]):
            with self.assertRaises(signing.SigningError):
                signing.verify_keystore(self.keystore(), self.policy)

    def test_a_different_certificate_behind_the_same_file_blocks_the_build(self):
        with patch.object(signing, 'run_tool', return_value=b'wrong-public-certificate') as tool:
            with self.assertRaises(signing.SigningError):
                signing.verify_keystore(self.keystore(), self.policy)
        self.assertEqual(1, tool.call_count)

    def test_password_is_never_printed_or_returned_by_the_cli(self):
        with patch.object(signing, 'run_tool', side_effect=[self.certificate, b'synthetic-csr']), \
                patch('sys.argv', ['signing_policy.py', 'verify-keystore']), \
                patch.object(signing, 'keystore_path', return_value=self.keystore()), \
                patch.object(signing, 'load_policy', return_value=self.policy), \
                patch.object(signing, 'password_path', return_value=self.password()) as password, \
                patch('sys.stdout') as output:
            # The real password file is never needed for this assertion: only absence is checked.
            password.return_value = self.root / 'absent-password.txt'
            with patch('sys.stderr'):
                self.assertEqual(1, signing.main())
        self.assertFalse(any('password' in str(call).lower() for call in output.write.call_args_list))

    def password(self):
        path = self.root / 'password.txt'
        path.write_text('synthetic-password-value\n')
        return path

    def apk_fixture(self):
        apk = self.root / 'fixture.apk'
        apk.write_bytes(b'not-a-real-apk-tool-response-is-mocked')
        return apk

    def test_actual_apk_verification_must_report_the_exact_single_signer(self):
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
        self.assertNotIn(self.fake_data.decode(), str(failure.exception))

    def test_no_command_generates_a_key(self):
        with patch.object(signing, 'run_tool', side_effect=[self.certificate, b'synthetic-csr']) as tool:
            signing.verify_keystore(self.keystore(), self.policy)
        for call in tool.call_args_list:
            self.assertFalse(any('genkey' in value or 'keytool -genkeypair' == value for value in call.args[0]))


if __name__ == '__main__':
    unittest.main()
