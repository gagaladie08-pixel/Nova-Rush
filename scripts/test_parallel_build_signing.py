"""Public unsigned artifact validation; no real APK or signing key in fixtures."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import prepare_parallel_build as build


class ParallelBuildSigningTest(unittest.TestCase):
    text = "package: name='com.novalisten.app.next' versionCode='6' versionName='0.3.0' platformBuildVersionName='15'\nsdkVersion:'26'\napplication-label:'NovaListen Next'\n"

    def test_actual_binary_metadata_must_describe_next_not_legacy(self):
        info = build.badging(self.text)
        self.assertEqual('com.novalisten.app.next', info['application_id'])
        self.assertEqual(6, info['version_code'])
        self.assertEqual('0.3.0', info['version_name'])
        with self.assertRaises(ValueError): build.badging(self.text.replace('.app.next', '.app'))

    def test_distinct_launcher_label_and_minimum_version_are_checked(self):
        for text in [self.text.replace("'NovaListen Next'", "'NovaListen'"), self.text.replace("sdkVersion:'26'", "sdkVersion:'25'")]:
            with self.assertRaises(ValueError): build.badging(text)

    def test_missing_or_unsafe_version_metadata_is_rejected(self):
        for text in ['', self.text.replace("versionName='0.3.0'", "versionName='../private-signing/password.txt'")]:
            with self.assertRaises(ValueError): build.badging(text)

    def fixture(self):
        data = {'app': b'synthetic-unsigned-apk', 'signer': b'synthetic-public-tool', 'metadata': b'{}'}
        names = {'app': 'NovaListen-Next-0.3.0-unsigned.apk', 'signer': 'apksigner.jar', 'metadata': 'build-info.json'}
        index = {'commit': '1' * 40, 'application_id': build.APPLICATION_ID, 'files': {}}
        for role, raw in data.items():
            index['files'][role] = {'file': names[role], 'bytes': len(raw), 'sha256': build.digest(raw),
                                   'parts': [{'blob': role, 'bytes': len(raw), 'sha256': build.digest(raw)}]}
        return index, data

    def test_retrieval_only_accepts_three_fixed_public_file_roles(self):
        index, _ = self.fixture(); index['files']['password'] = {'file': 'password.txt'}
        with tempfile.TemporaryDirectory() as directory, patch.object(build, 'github_blob', return_value=json.dumps(index).encode()):
            with self.assertRaises(ValueError): build.retrieve('index', directory)
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_arbitrary_paths_and_private_archives_cannot_be_retrieved_via_the_bridge(self):
        for name in ['../../private-signing/password.txt', 'NovaListen-Next-signature-backup.zip']:
            index, _ = self.fixture(); index['files']['app']['file'] = name
            with tempfile.TemporaryDirectory() as directory, patch.object(build, 'github_blob', return_value=json.dumps(index).encode()):
                with self.assertRaises(ValueError): build.retrieve('index', directory)
                self.assertEqual([], list(Path(directory).iterdir()))

    def test_unsigned_intermediate_and_tool_have_each_chunk_and_whole_hash_verified(self):
        index, data = self.fixture(); data['index'] = json.dumps(index).encode()
        with tempfile.TemporaryDirectory() as directory, patch.object(build, 'github_blob', side_effect=data.__getitem__):
            build.retrieve('index', directory)
            self.assertEqual(data['app'], (Path(directory) / index['files']['app']['file']).read_bytes())
            self.assertTrue((Path(directory) / 'build-index.json').is_file())

    def test_tampered_chunk_is_not_promoted_to_a_ready_unsigned_apk(self):
        index, data = self.fixture(); data['index'] = json.dumps(index).encode(); data['app'] = b'changed'
        with tempfile.TemporaryDirectory() as directory, patch.object(build, 'github_blob', side_effect=data.__getitem__):
            with self.assertRaises(ValueError): build.retrieve('index', directory)
            self.assertFalse((Path(directory) / index['files']['app']['file']).exists())


if __name__ == '__main__':
    unittest.main()
