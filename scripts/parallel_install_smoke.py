#!/usr/bin/env python3
"""Publish/fetch only a verified public Next APK; test coexistence on CI EMULATORS.
Never reads or transfers a private key/password. Never runs against a real phone.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

import parallel_signing as signing
from prepare_parallel_build import APPLICATION_ID, ROOT, badging, digest, github_blob

INDEX = ROOT / 'config/next-apk-under-test.json'
CACHE = ROOT / '.cache/next-device-check'
OLD_ID = 'com.novalisten.app'
OLD_SHA256 = '4992b71f5634b90338504211e25541e8cbde5aa6c577a0c79e229996d0f87612'


def publish(apk, signer, source_info):
    apk = Path(apk)
    if not re.fullmatch(r'NovaListen-Next-[0-9]+\.[0-9]+\.[0-9]+-debug\.apk', apk.name):
        raise ValueError('Seul l’APK public signé Next peut être publié ; aucun fichier privé.')
    verified = signing.verify_apk(apk, signer)
    source = json.loads(Path(source_info).read_text())
    if source['app']['application_id'] != APPLICATION_ID:
        raise ValueError('Mauvaise identité de compilation.')
    if INDEX.exists():
        if json.loads(INDEX.read_text())['sha256'] == verified['sha256']:
            return json.loads(INDEX.read_text())
        raise ValueError('Un index différent existe déjà ; aucun remplacement implicite.')
    os.environ.setdefault('GITHUB_REPOSITORY', 'gagaladie08-pixel/Nova-Rush')
    os.environ['GITHUB_SHA'] = source['commit']
    spec = importlib.util.spec_from_file_location('public_bridge', ROOT / 'scripts/github-transfer.py')
    bridge = importlib.util.module_from_spec(spec); spec.loader.exec_module(bridge)
    data = apk.read_bytes()
    parts = [bridge.publish(f'{apk.name}.part-{at // bridge.CHUNK_BYTES + 1:03d}', data[at:at + bridge.CHUNK_BYTES])
             for at in range(0, len(data), bridge.CHUNK_BYTES)]
    index = {'application_id': APPLICATION_ID, 'file': apk.name, 'bytes': len(data),
             'sha256': verified['sha256'], 'certificate_sha256': verified['certificate_sha256'],
             'source_commit': source['commit'], 'build_run': source['build_run'], 'parts': parts}
    INDEX.write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n')
    return index


def fetch():
    info = json.loads(INDEX.read_text())
    policy = signing.load_policy()
    if (info['application_id'] != APPLICATION_ID or info['certificate_sha256'] != policy['certificate_sha256']
            or not re.fullmatch(r'NovaListen-Next-[0-9]+\.[0-9]+\.[0-9]+-debug\.apk', info['file'])
            or not 0 < info['bytes'] <= 120_000_000):
        raise ValueError('Index APK signé incohérent.')
    CACHE.mkdir(parents=True, exist_ok=True)
    apk = CACHE / info['file']
    if apk.is_file() and digest(apk.read_bytes()) == info['sha256']:
        return apk, info
    temporary = CACHE / (info['file'] + '.partial')
    with temporary.open('wb') as output:
        for part in info['parts']:
            data = github_blob(part['blob'])
            if len(data) != part['bytes'] or digest(data) != part['sha256']:
                raise ValueError('Partie APK altérée.')
            output.write(data)
    if temporary.stat().st_size != info['bytes'] or digest(temporary.read_bytes()) != info['sha256']:
        raise ValueError('APK téléchargé altéré.')
    temporary.replace(apk)
    return apk, info


def command(*args, data=None):
    return subprocess.check_output(list(map(str, args)), input=data, stderr=subprocess.STDOUT)


def adb(*args, data=None):
    return command('adb', *args, data=data)


def marker(package):
    adb('shell', 'run-as', package, 'mkdir', '-p', 'files')
    adb('shell', 'run-as', package, 'tee', 'files/next-coexistence-fixture', data=b'SYNTHETIC-COEXISTENCE-ONLY\n')


def read_marker(package):
    return adb('shell', 'run-as', package, 'cat', 'files/next-coexistence-fixture')


def smoke():
    # This script creates synthetic files in the two app sandboxes. CI emulator ONLY.
    if os.environ.get('GITHUB_ACTIONS') != 'true' or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
        raise ValueError('Essai réservé à un émulateur CI jetable, jamais au téléphone utilisateur.')
    if (ROOT / 'private-signing').exists():
        raise ValueError('Aucune clé privée ne doit entrer dans cet essai CI.')
    apk, info = fetch()
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT']) / 'build-tools/35.0.0'
    signing.verify_apk(apk, sdk / 'lib/apksigner.jar')
    binary = badging(command(sdk / 'aapt2', 'dump', 'badging', apk).decode())
    command(sdk / 'zipalign', '-c', '-P', '16', '4', apk)
    legacy = CACHE / 'legacy'; legacy.mkdir(exist_ok=True)
    command('gh', 'release', 'download', 'novalisten-v0.2.3', '--pattern', 'NovaListen-0.2.3-debug.apk', '--dir', legacy)
    old_apk = legacy / 'NovaListen-0.2.3-debug.apk'
    if digest(old_apk.read_bytes()) != OLD_SHA256:
        raise ValueError('APK historique différent de la référence.')
    # Check the historical certificate without any old private key.
    import signing_policy
    signing_policy.verify_apk(old_apk, sdk / 'apksigner', signing_policy.load_policy())
    adb('install', '-r', old_apk)
    marker(OLD_ID); old_before = read_marker(OLD_ID)
    adb('install', '-r', apk)
    packages = adb('shell', 'pm', 'list', 'packages', '-U').decode()
    old_uid = re.search(r'^package:' + re.escape(OLD_ID) + r' uid:(\d+)', packages, re.MULTILINE)
    new_uid = re.search(r'^package:' + re.escape(APPLICATION_ID) + r' uid:(\d+)', packages, re.MULTILINE)
    if not old_uid or not new_uid or old_uid[1] == new_uid[1]:
        raise ValueError('Les deux installations ne disposent pas d’UID distincts.')
    marker(APPLICATION_ID); next_before = read_marker(APPLICATION_ID)
    adb('install', '-r', apk)  # same-key update must preserve the Next sandbox too
    if read_marker(OLD_ID) != old_before or read_marker(APPLICATION_ID) != next_before:
        raise ValueError('Une donnée synthétique de coexistence a été modifiée.')
    api = int(adb('shell', 'getprop', 'ro.build.version.sdk').strip())
    permission = 'android.permission.READ_MEDIA_AUDIO' if api >= 33 else 'android.permission.READ_EXTERNAL_STORAGE'
    adb('shell', 'pm', 'grant', APPLICATION_ID, permission)
    if api >= 33:
        adb('shell', 'pm', 'grant', APPLICATION_ID, 'android.permission.POST_NOTIFICATIONS')
    adb('logcat', '-c')
    launch = adb('shell', 'am', 'start', '-W', '-n', APPLICATION_ID + '/com.novalisten.app.MainActivity').decode()
    if 'Status: ok' not in launch:
        raise ValueError('Lancement Next non confirmé.')
    adb('shell', 'uiautomator', 'dump', '/sdcard/next-window.xml')
    window = adb('shell', 'cat', '/sdcard/next-window.xml')
    root = ET.fromstring(window)
    if not any('Bibliothèque' in node.get('text', '') for node in root.iter()):
        raise ValueError('La navigation Bibliothèque n’est pas visible après lancement.')
    pid = adb('shell', 'pidof', APPLICATION_ID).decode().strip()
    crash = adb('logcat', '-d', '-b', 'crash').decode(errors='replace')
    if not pid or 'Process: ' + APPLICATION_ID + ',' in crash:
        raise ValueError('Processus Next absent ou plantage au démarrage.')
    folder = ROOT / f'artifacts/device-verifications/next-api-{api}'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'window.xml').write_bytes(window)
    (folder / 'startup.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    report = {'api': api, 'apk_sha256': info['sha256'], 'certificate_sha256': info['certificate_sha256'],
              'application_id': APPLICATION_ID, 'launcher_label': binary['label'], 'version': binary['version_name'],
              'old_and_new_installed': True, 'distinct_android_uids': True, 'old_fixture_preserved': True,
              'next_fixture_preserved_after_same_key_reinstall': True, 'launch_status_ok': True,
              'library_navigation_visible': True, 'no_next_startup_crash': True,
              'full_instrumentation_suite_executed': False}
    (folder / 'summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    upload = commands.add_parser('publish'); upload.add_argument('apk')
    upload.add_argument('--signer', required=True); upload.add_argument('--source-info', required=True)
    commands.add_parser('fetch'); commands.add_parser('smoke')
    args = parser.parse_args()
    if args.command == 'publish': print(json.dumps(publish(args.apk, args.signer, args.source_info), ensure_ascii=False))
    elif args.command == 'fetch': print(json.dumps({'apk': str(fetch()[0]), 'hashes_verified': True}))
    else: smoke()


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Only public APK/emulator fixtures are handled by this script.
        message = str(error)
        if isinstance(error, subprocess.CalledProcessError) and error.output:
            message += '\n' + error.output.decode(errors='replace')[-3000:]
        print(message, file=sys.stderr)
        if os.environ.get('GITHUB_ACTIONS') == 'true':
            print('::error title=Next coexistence smoke::' + message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
        sys.exit(1)
