#!/usr/bin/env python3
"""Fail closed on signing continuity. Never generate, upload or log a private key."""
import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
POLICY_FILE = ROOT / 'config/signing-policy.json'
MAX_KEYSTORE_BYTES = 32_768


class SigningError(Exception):
    pass


def load_policy():
    policy = json.loads(POLICY_FILE.read_text())
    if not re.fullmatch(r'[0-9a-f]{64}', policy['certificate_sha256']):
        raise SigningError('Empreinte de certificat de référence invalide.')
    return policy


def run_tool(command, binary=False):
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    except OSError:
        raise SigningError('Outil de vérification de signature indisponible.') from None
    if result.returncode != 0:
        # In particular, do not echo tool stderr, credentials, encoded data or command bodies.
        raise SigningError('Vérification de signature impossible ; aucune clé de remplacement ne sera créée.')
    return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')


def prepare_key(encoded, runner_temp, policy, github_env):
    """Restore ONLY an existing original debug keystore, privately and outside the repository."""
    if not encoded or not encoded.strip():
        raise SigningError('Clé privée de référence non configurée. Build bloqué : aucune nouvelle clé automatique.')
    compact = ''.join(encoded.split())
    if len(compact) > 4 * ((MAX_KEYSTORE_BYTES + 2) // 3):
        raise SigningError('Keystore trop volumineux ; build bloqué.')
    try:
        data = base64.b64decode(compact, validate=True)
    except (binascii.Error, ValueError):
        raise SigningError('Keystore encodé invalide ; build bloqué.') from None
    if not data or len(data) > MAX_KEYSTORE_BYTES:
        raise SigningError('Keystore vide ou trop volumineux ; build bloqué.')
    temporary_root = Path(runner_temp).resolve()
    if temporary_root == ROOT or ROOT in temporary_root.parents:
        raise SigningError('La clé privée doit rester hors du dépôt.')
    folder = Path(tempfile.mkdtemp(prefix='novalisten-signing-', dir=temporary_root))
    try:
        keystore = folder / 'original-debug.keystore'
        with keystore.open('xb') as output:
            os.chmod(keystore, 0o600)
            output.write(data)
        # 0.2.3 used Android's default debug alias/store password. These public defaults are
        # NOT the private key; a newly generated store with the same defaults is rejected.
        certificate = run_tool(['keytool', '-exportcert', '-keystore', str(keystore),
                                '-alias', policy['key_alias'], '-storepass', 'android'], binary=True)
        if hashlib.sha256(certificate).hexdigest() != policy['certificate_sha256']:
            raise SigningError('Certificat différent de la référence. Build bloqué ; aucun changement de signature autorisé.')
        # A certificate-only store is not a recovered signing key. Require an operation using
        # the existing private key. The public CSR stays in memory and is never published.
        run_tool(['keytool', '-certreq', '-keystore', str(keystore), '-alias', policy['key_alias'],
                  '-storepass', 'android', '-keypass', 'android'], binary=True)
        path = str(keystore)
        if '\n' in path or '\r' in path:
            raise SigningError('Chemin de keystore invalide.')
        # Only the path is passed to later steps, never the key or its base64 representation.
        with Path(github_env).open('a') as output:
            output.write('NOVALISTEN_KEYSTORE_PATH=' + path + '\n')
        return keystore
    except BaseException:
        shutil.rmtree(folder)
        raise


def verify_apk(apk, apksigner, policy):
    apk = Path(apk)
    if not apk.is_file():
        raise SigningError('APK à vérifier absent ; livraison bloquée.')
    # apksigner verifies the actual APK signature, not just a copied public certificate file.
    report = run_tool([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk)])
    fingerprints = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$', report, re.MULTILINE)
    if [value.lower() for value in fingerprints] != [policy['certificate_sha256']]:
        raise SigningError('Signature APK différente ou ambiguë. Livraison bloquée avant tout transfert.')
    return policy['certificate_sha256']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('prepare')
    verify = commands.add_parser('verify-apk')
    verify.add_argument('apk')
    args = parser.parse_args()
    try:
        policy = load_policy()
        if args.command == 'prepare':
            if not os.environ.get('RUNNER_TEMP') or not os.environ.get('GITHUB_ENV'):
                raise SigningError('Préparation réservée au runner privé ; aucune clé n’est créée localement.')
            prepare_key(os.environ.get('NOVALISTEN_DEBUG_KEYSTORE_BASE64', ''),
                        os.environ['RUNNER_TEMP'], policy, os.environ['GITHUB_ENV'])
        else:
            sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
            signer = Path(sdk) / 'build-tools/35.0.0/apksigner' if sdk else shutil.which('apksigner')
            if not signer:
                raise SigningError('apksigner indisponible ; livraison bloquée.')
            verify_apk(args.apk, signer, policy)
        print('Signature de référence vérifiée : ' + policy['certificate_sha256'])
        return 0
    except SigningError as error:
        print('Signature NovaListen : ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
