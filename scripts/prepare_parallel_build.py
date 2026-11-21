#!/usr/bin/env python3
"""Unsigned Next intermediates only. Public artifacts, NEVER private signing files.

The build index allows retrieving the exact unsigned result over GitHub's API
when Actions/release blob hosts cannot be reached from the signing workspace.
"""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'artifacts/parallel-build'
APPLICATION_ID = 'com.novalisten.app.next'
LABEL = 'NovaListen Next'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def badging(text):
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", text, re.MULTILINE)
    # aapt2 may emit only application: label= for a literal manifest label,
    # rather than the application-label: entry used for a string resource.
    label = (re.search(r"^application-label:'([^']+)'", text, re.MULTILINE)
             or re.search(r"^application: label='([^']+)'", text, re.MULTILINE))
    minimum = re.search(r"^(?:sdkVersion|minSdkVersion):'([0-9]+)'", text, re.MULTILINE)
    if not package or not label or not minimum:
        raise ValueError('Métadonnées APK absentes/incomplètes.')
    info = {'application_id': package[1], 'version_code': int(package[2]), 'version_name': package[3],
            'label': label[1], 'min_sdk': int(minimum[1])}
    if info['application_id'] != APPLICATION_ID or info['label'] != LABEL or info['min_sdk'] < 26:
        raise ValueError('La préparation doit viser exclusivement NovaListen Next, pas l’application 0.2.3.')
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', info['version_name']):
        raise ValueError('Version APK inattendue.')
    return info


def prepare():
    if os.environ.get('NOVALISTEN_PREPARE_PARALLEL') != 'true' or (ROOT / 'private-signing').exists():
        raise ValueError('Préparation CI sans aucun répertoire privé uniquement.')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT']) / 'build-tools/35.0.0'
    source = ROOT / 'app/build/outputs/apk/debug/app-debug-unsigned.apk'
    binary_info = subprocess.check_output([str(sdk / 'aapt2'), 'dump', 'badging', str(source)], text=True, stderr=subprocess.STDOUT)
    if os.environ.get('GITHUB_ACTIONS') == 'true':
        public_lines = '\n'.join(line for line in binary_info.splitlines() if line.startswith(('package:', 'sdkVersion:', 'minSdkVersion:', 'targetSdkVersion:', 'application-label:', 'application:')))
        notice = public_lines.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print('::notice title=Next binary manifest::' + notice, flush=True)
    info = badging(binary_info)
    # An implicit runner debug certificate is a build error, not an acceptable fallback.
    check = subprocess.run([str(sdk / 'apksigner'), 'verify', str(source)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check.returncode == 0 or (Path.home() / '.android/debug.keystore').exists():
        raise ValueError('L’intermédiaire ne doit comporter aucune signature ou clé implicite.')
    OUT.mkdir(parents=True, exist_ok=True)
    app = OUT / f'NovaListen-Next-{info["version_name"]}-unsigned.apk'
    subprocess.run([str(sdk / 'zipalign'), '-P', '16', '-f', '4', str(source), str(app)], check=True)
    subprocess.run([str(sdk / 'zipalign'), '-c', '-P', '16', '4', str(app)], check=True)
    signer = OUT / 'apksigner.jar'
    shutil.copyfile(sdk / 'lib/apksigner.jar', signer)
    info.update({'file': app.name, 'bytes': app.stat().st_size, 'sha256': digest(app.read_bytes())})
    metadata = {'commit': os.environ['GITHUB_SHA'], 'build_run': int(os.environ['GITHUB_RUN_ID']),
                'private_key_in_ci': False, 'app': info,
                'signer': {'file': signer.name, 'bytes': signer.stat().st_size, 'sha256': digest(signer.read_bytes())}}
    (OUT / 'build-info.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(metadata, ensure_ascii=False))
    return metadata


def transfer():
    spec = importlib.util.spec_from_file_location('public_bridge', ROOT / 'scripts/github-transfer.py')
    bridge = importlib.util.module_from_spec(spec); spec.loader.exec_module(bridge)
    info_path = OUT / 'build-info.json'
    info = json.loads(info_path.read_text())
    if info['app']['application_id'] != APPLICATION_ID or info['private_key_in_ci'] is not False:
        raise ValueError('Intermédiaire Next incorrect.')
    app_name = info['app']['file']
    if not re.fullmatch(r'NovaListen-Next-[0-9]+\.[0-9]+\.[0-9]+-unsigned\.apk', app_name):
        raise ValueError('Nom de fichier inattendu.')
    # A fixed allowlist, never a recursive artifact upload or a user-provided private path.
    files = [('app', app_name), ('signer', 'apksigner.jar'), ('metadata', 'build-info.json')]
    index = {'commit': os.environ['GITHUB_SHA'], 'application_id': APPLICATION_ID, 'files': {}}
    for role, name in files:
        data = (OUT / name).read_bytes()
        if role in info and digest(data) != info[role]['sha256']:
            raise ValueError('Intermédiaire modifié avant transfert.')
        parts = [bridge.publish(f'{name}.part-{at // bridge.CHUNK_BYTES + 1:03d}', data[at:at + bridge.CHUNK_BYTES])
                 for at in range(0, len(data), bridge.CHUNK_BYTES)]
        index['files'][role] = {'file': name, 'bytes': len(data), 'sha256': digest(data), 'parts': parts}
    bridge.publish('NovaListen-Next-build-index.json', (json.dumps(index, indent=2) + '\n').encode())


def github_blob(sha):
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('Identifiant de blob invalide.')
    repository = os.environ.get('GITHUB_REPOSITORY', 'gagaladie08-pixel/Nova-Rush')
    data = json.loads(subprocess.check_output(['gh', 'api', f'repos/{repository}/git/blobs/{sha}']))
    return base64.b64decode(data['content'], validate=False)


def retrieve(blob, output):
    index = json.loads(github_blob(blob))
    if index['application_id'] != APPLICATION_ID or set(index['files']) != {'app', 'signer', 'metadata'}:
        raise ValueError('Index non admissible.')
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    for role, info in index['files'].items():
        name = info['file']
        allowed = (role == 'app' and re.fullmatch(r'NovaListen-Next-[0-9]+\.[0-9]+\.[0-9]+-unsigned\.apk', name)
                   or role == 'signer' and name == 'apksigner.jar' or role == 'metadata' and name == 'build-info.json')
        if not allowed or not 0 < info['bytes'] <= 120_000_000:
            raise ValueError('Fichier public inattendu.')
        destination = output / name
        if destination.exists() and digest(destination.read_bytes()) == info['sha256']:
            continue
        temporary = output / (name + '.partial')
        with temporary.open('wb') as handle:
            for part in info['parts']:
                data = github_blob(part['blob'])
                if len(data) != part['bytes'] or digest(data) != part['sha256']:
                    raise ValueError('Partie de téléchargement invalide.')
                handle.write(data)
        if temporary.stat().st_size != info['bytes'] or digest(temporary.read_bytes()) != info['sha256']:
            raise ValueError('Intermédiaire téléchargé invalide.')
        temporary.replace(destination)
    (output / 'build-index.json').write_text(json.dumps(index, indent=2) + '\n')
    print(json.dumps({'commit': index['commit'], 'output': str(output), 'hashes_verified': True}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('prepare'); commands.add_parser('transfer')
    download = commands.add_parser('retrieve'); download.add_argument('blob'); download.add_argument('output')
    args = parser.parse_args()
    if args.command == 'prepare': prepare()
    elif args.command == 'transfer': transfer()
    else: retrieve(args.blob, args.output)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        detail = str(error)
        if isinstance(error, subprocess.CalledProcessError) and error.output:
            detail += '\n' + str(error.output)[-2000:]
        message = 'Préparation Next interrompue : ' + detail
        print(message, file=sys.stderr)
        if os.environ.get('GITHUB_ACTIONS') == 'true':
            print('::error title=Next unsigned preparation::' + message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
        sys.exit(1)
