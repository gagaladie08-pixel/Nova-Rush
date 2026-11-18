#!/usr/bin/env python3
"""Delivery bridge when Actions blob storage is unreachable from the workspace.
Only unreferenced GitHub blobs: NO commit, tree, tag or branch is changed.
Eligible: the APK, CI log and test reports, never credentials or phone data.
Larger APKs are chunked to avoid oversized JSON requests to the Git Data API.
"""
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import zipfile

CHUNK_BYTES = 20_000_000


def publish(label, data):
    command = ['gh', 'api', f'repos/{os.environ["GITHUB_REPOSITORY"]}/git/blobs', '--input', '-']
    result = subprocess.run(command, input=json.dumps({'content': base64.b64encode(data).decode('ascii'), 'encoding': 'base64'}).encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # Fixed endpoint, no provider keys in this script, and never print request bodies/base64.
        error = result.stderr.decode(errors='replace')[:1200].replace('%', '%25').replace('\n', '%0A').replace('\r', '%0D')
        print(f'::error title=NovaListen delivery bridge::{label}: {error}', flush=True)
        raise RuntimeError(f'GitHub blob transfer failed: {label}')
    response = json.loads(result.stdout)
    info = {'file': label, 'blob': response['sha'], 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(), 'commit': os.environ['GITHUB_SHA']}
    print('::notice title=NovaListen delivery::' + json.dumps(info), flush=True)
    return info


def main():
    # Publish diagnostics before the binary, so a delivery problem cannot hide test failures.
    log = Path('artifacts/diagnostics/gradle.log')
    if log.is_file():
        publish('gradle.log', log.read_bytes())
    roots = [Path('app/build/reports'), Path('app/build/test-results'), Path('app/build/outputs/androidTest-results'), Path('app/build/outputs/connected_android_test_additional_output')]
    files = [file for root in roots if root.exists() for file in root.rglob('*') if file.is_file()]
    if files:
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as archive:
            for file in files:
                archive.write(file)
        publish('NovaListen-verifications.zip', buffer.getvalue())
    apks = sorted(Path('artifacts').glob('NovaListen-*-debug.apk'))
    if len(apks) > 1:
        raise RuntimeError('Ambiguous APK delivery: more than one build result')
    if apks:
        apk = apks[0]
        data = apk.read_bytes()
        parts = [publish(f'{apk.name}.part-{index // CHUNK_BYTES + 1:03d}', data[index:index + CHUNK_BYTES]) for index in range(0, len(data), CHUNK_BYTES)]
        manifest = {'file': apk.name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(), 'commit': os.environ['GITHUB_SHA'], 'parts': parts}
        print('::notice title=NovaListen APK manifest::' + json.dumps(manifest), flush=True)


if __name__ == '__main__':
    main()
