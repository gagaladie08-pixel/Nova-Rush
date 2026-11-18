#!/usr/bin/env python3
"""Optional delivery bridge for environments unable to reach Actions blob storage.
Creates only unreferenced GitHub blobs: NO commit, tag, tree or branch is changed.
The ordinary Actions artifacts remain the canonical user-facing download.
Only the APK, CI log and test reports from this build are eligible; never secrets.
"""
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import zipfile


def publish(label, data):
    result = json.loads(subprocess.check_output(
        ['gh', 'api', f'repos/{os.environ["GITHUB_REPOSITORY"]}/git/blobs', '--input', '-'],
        input=json.dumps({'content': base64.b64encode(data).decode('ascii'), 'encoding': 'base64'}).encode(),
    ))
    info = {'file': label, 'blob': result['sha'], 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(), 'commit': os.environ['GITHUB_SHA']}
    print('::notice title=NovaListen delivery::' + json.dumps(info), flush=True)


def main():
    apk = Path('artifacts/NovaListen-0.1.0-debug.apk')
    if apk.is_file():
        publish(apk.name, apk.read_bytes())
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


if __name__ == '__main__':
    main()
