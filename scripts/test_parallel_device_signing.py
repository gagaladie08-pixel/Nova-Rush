"""Safety gates for the optional coexistence check — never touch a real device."""
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import parallel_install_smoke as smoke


class ParallelDeviceSigningTest(unittest.TestCase):
    def test_a_real_phone_is_rejected_before_any_download_or_install(self):
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'true'}), patch.object(smoke, 'adb', return_value=b'0\n') as adb, patch.object(smoke, 'fetch') as fetch:
            with self.assertRaises(ValueError): smoke.smoke()
            fetch.assert_not_called()
            adb.assert_called_once_with('shell', 'getprop', 'ro.kernel.qemu')

    def test_non_ci_invocation_does_not_even_query_a_connected_device(self):
        with patch.dict(os.environ, {'GITHUB_ACTIONS': 'false'}), patch.object(smoke, 'adb') as adb:
            with self.assertRaises(ValueError): smoke.smoke()
            adb.assert_not_called()

    def test_a_private_key_or_recovery_zip_can_never_be_published_as_the_apk(self):
        for name in ['novalisten-next.p12', 'password.txt', 'NovaListen-Next-signature-backup.zip']:
            with patch.object(smoke.signing, 'verify_apk') as verify:
                with self.assertRaises(ValueError): smoke.publish(Path('private-signing') / name, 'signer.jar', 'source.json')
                verify.assert_not_called()

    def test_a_legacy_package_or_changed_certificate_cannot_be_fetched(self):
        policy = {'certificate_sha256': '1' * 64}
        for package, fingerprint in [('com.novalisten.app', '1' * 64), (smoke.APPLICATION_ID, '2' * 64)]:
            with tempfile.TemporaryDirectory() as folder:
                index = Path(folder) / 'index.json'
                index.write_text(json.dumps({'application_id': package, 'certificate_sha256': fingerprint}))
                with patch.object(smoke, 'INDEX', index), patch.object(smoke.signing, 'load_policy', return_value=policy), patch.object(smoke, 'github_blob') as download:
                    with self.assertRaises(ValueError): smoke.fetch()
                    download.assert_not_called()

    def test_fetch_cannot_overwrite_an_arbitrary_private_path(self):
        policy = {'certificate_sha256': '1' * 64}
        with tempfile.TemporaryDirectory() as folder:
            index = Path(folder) / 'index.json'
            index.write_text(json.dumps({'application_id': smoke.APPLICATION_ID, 'certificate_sha256': '1' * 64,
                                         'file': '../../private-signing/password.txt'}))
            with patch.object(smoke, 'INDEX', index), patch.object(smoke.signing, 'load_policy', return_value=policy), patch.object(smoke, 'github_blob') as download:
                with self.assertRaises(ValueError): smoke.fetch()
                download.assert_not_called()


if __name__ == '__main__':
    unittest.main()
