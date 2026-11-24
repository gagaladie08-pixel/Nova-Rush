#!/usr/bin/env python3
"""Fail closed on signing continuity. Never generate, upload or log a private key.

Méthode Nova Rush : the delivery key lives in the repository, so every build is
signed with the same certificate and each version installs over the previous one.
The key must be identical to the pinned fingerprint in config/signing-policy.json;
a replaced or missing file blocks the build instead of silently generating a new
debug key (the cause of the 0.2.x update failures).
"""
import argparse
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


class SigningError(Exception):
    pass


def load_policy():
    policy = json.loads(POLICY_FILE.read_text())
    if not re.fullmatch(r'[0-9a-f]{64}', policy.get('certificate_sha256', '')):
        raise SigningError('Empreinte de certificat de référence invalide.')
    if not re.fullmatch(r'[0-9a-f]{64}', policy.get('keystore_sha256', '')):
        raise SigningError('Empreinte de keystore de référence invalide.')
    return policy


def keystore_path():
    return (ROOT / 'config/signing/NovaListen-release.p12').resolve()


def password_path():
    return (ROOT / 'config/signing/signing-password.txt').resolve()


def read_password():
    """Local password file only. The password is never printed or logged."""
    path = password_path()
    if not path.is_file():
        raise SigningError('Mot de passe de signature absent ; build bloqué.')
    return path.read_text().strip()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def run_tool(command, binary=False):
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    except OSError:
        raise SigningError('Outil de vérification de signature indisponible.') from None
    if result.returncode != 0:
        # Never echo tool stderr: it can contain private paths or keystore details.
        raise SigningError('Vérification de signature impossible ; aucune clé de remplacement ne sera créée.')
    return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')


def file_password(path, content):
    """keytool/apksigner accept -storepass:file, so the password never appears in argv."""
    path = Path(path)
    with path.open('xb') as output:
        os.chmod(path, 0o600)
        output.write(content.encode())
    return path


def verify_keystore(keystore=None, policy=None):
    """Check the pinned certificate AND that the private key is really usable."""
    policy = policy or load_policy()
    keystore = Path(keystore).resolve() if keystore else keystore_path()
    if not keystore.is_file():
        raise SigningError('Keystore de livraison absent ; build bloqué, aucune génération implicite.')
    if digest(keystore.read_bytes()) != policy['keystore_sha256']:
        raise SigningError('Keystore différent de celui épinglé ; build bloqué.')
    password = read_password()
    with tempfile.TemporaryDirectory(prefix='novalisten-verify-') as folder:
        secret = file_password(Path(folder) / 'store.pass', password)
        common = ['-keystore', str(keystore), '-storetype', 'PKCS12',
                  '-alias', policy['key_alias'], '-storepass:file', str(secret)]
        certificate = run_tool(['keytool', '-exportcert', *common], binary=True)
        if digest(certificate) != policy['certificate_sha256']:
            raise SigningError('Certificat différent de la référence ; build bloqué.')
        # A public certificate alone cannot produce a CSR: this uses the real private key.
        run_tool(['keytool', '-certreq', *common, '-keypass:file', str(secret)], binary=True)
    return policy['certificate_sha256']


def verify_apk(apk, apksigner, policy=None):
    policy = policy or load_policy()
    apk = Path(apk)
    if not apk.is_file():
        raise SigningError('APK à vérifier absent ; livraison bloquée.')
    report = run_tool([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk)])
    fingerprints = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$', report, re.MULTILINE)
    if [value.lower() for value in fingerprints] != [policy['certificate_sha256']]:
        raise SigningError('Signature APK différente ou ambiguë. Livraison bloquée avant tout transfert.')
    return policy['certificate_sha256']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('verify-keystore', help='vérifie la clé épinglée et sa clé privée')
    verify = commands.add_parser('verify-apk')
    verify.add_argument('apk')
    args = parser.parse_args()
    try:
        policy = load_policy()
        if args.command == 'verify-keystore':
            verify_keystore(policy=policy)
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
