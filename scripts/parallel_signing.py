#!/usr/bin/env python3
"""Owner-only signing for NovaListen Next. The 0.2.3 key/policy are NEVER replaced.

Compilation can run without a private key in CI. Only this local, explicit signing
step uses the pinned Next key, after verifying its recoverable private backup.
No private file is sent to GitHub, logged, committed or included in APK artifacts.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
APPLICATION_ID = 'com.novalisten.app.next'
LABEL = 'NovaListen Next'
POLICY_PATH = ROOT / 'config/parallel-signing-policy.json'
PRIVATE_ROOT = ROOT / 'private-signing'
PRIVATE = PRIVATE_ROOT / 'novalisten-next'
KEY = PRIVATE / 'novalisten-next.p12'
PASSWORD = PRIVATE / 'password.txt'
BACKUP = PRIVATE_ROOT / 'NovaListen-Next-signature-backup.zip'
ACKNOWLEDGEMENT = PRIVATE / 'owner-backup-confirmed.json'
ALIAS = 'novalisten-next'


class SigningError(Exception):
    pass


def digest(data):
    return hashlib.sha256(data).hexdigest()


def private_write(path, data):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as output:
        output.write(data)


def tool(name):
    home = os.environ.get('JAVA_HOME')
    found = Path(home) / 'bin' / name if home else None
    if found and found.is_file():
        return str(found)
    found = shutil.which(name)
    if found:
        return found
    bundled = ROOT / '.cache/local-signing-jre/jdk4py/java-runtime/bin' / name
    if bundled.is_file():
        return str(bundled)
    raise SigningError('Runtime Java/keytool local absent ; aucune clé ne sera créée implicitement.')


def run(command):
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, umask=0o077)
    except OSError:
        raise SigningError('Outil local de signature indisponible.') from None
    if result.returncode:
        # Never print tool stderr, private data, command bodies or password file contents.
        raise SigningError('Vérification/signature locale impossible ; clé conservée sans remplacement.')
    return result.stdout


def guard_private_directory():
    if PRIVATE_ROOT.is_symlink() or PRIVATE.is_symlink():
        raise SigningError('Le répertoire privé ne peut pas être un lien symbolique.')
    for path in (KEY, PASSWORD, BACKUP, ACKNOWLEDGEMENT):
        result = subprocess.run(['git', 'check-ignore', '-q', str(path.relative_to(ROOT))], cwd=ROOT)
        if result.returncode:
            raise SigningError('Tous les fichiers privés doivent être ignorés par Git avant toute génération.')


def load_policy():
    if not POLICY_PATH.is_file():
        raise SigningError('Identité Next non provisionnée ; génération automatique interdite.')
    policy = json.loads(POLICY_PATH.read_text())
    original = json.loads((ROOT / 'config/signing-policy.json').read_text())
    if (policy.get('application_id') != APPLICATION_ID or policy.get('key_alias') != ALIAS
            or policy.get('label') != LABEL or not re.fullmatch(r'[0-9a-f]{64}', policy.get('certificate_sha256', ''))
            or policy['certificate_sha256'] == original['certificate_sha256']):
        raise SigningError('Politique Next incohérente ; la signature/identité 0.2.3 ne peut pas être remplacée.')
    return policy


def verify_store(key, password, policy):
    for path in (key, password):
        if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
            raise SigningError('Fichier privé absent, partagé ou non protégé ; signature bloquée.')
    if not 1 <= key.stat().st_size <= 32768 or not 32 <= password.stat().st_size <= 512:
        raise SigningError('Sauvegarde privée invalide ; aucune régénération.')
    command = [tool('keytool'), '-keystore', str(key), '-storetype', 'PKCS12',
               '-alias', policy['key_alias'], '-storepass:file', str(password)]
    certificate = run([command[0], '-exportcert', *command[1:]])
    if digest(certificate) != policy['certificate_sha256']:
        raise SigningError('La clé ne correspond pas à l’empreinte Next épinglée ; aucun remplacement.')
    # The existing private key must also be usable, not merely its public certificate.
    run([command[0], '-certreq', *command[1:], '-keypass:file', str(password)])
    return certificate


def initialize():
    guard_private_directory()
    if POLICY_PATH.exists() or PRIVATE.exists() or BACKUP.exists():
        raise SigningError('Une identité ou copie existe déjà. Aucune génération/écrasement autorisé ; restaurer la sauvegarde.')
    PRIVATE_ROOT.mkdir(mode=0o700, exist_ok=True)
    os.chmod(PRIVATE_ROOT, 0o700)
    PRIVATE.mkdir(mode=0o700)
    private_write(PASSWORD, (secrets.token_urlsafe(48) + '\n').encode())
    run([tool('keytool'), '-genkeypair', '-alias', ALIAS, '-keyalg', 'RSA', '-keysize', '3072',
         '-sigalg', 'SHA256withRSA', '-validity', '10000', '-storetype', 'PKCS12', '-keystore', str(KEY),
         '-storepass:file', str(PASSWORD), '-keypass:file', str(PASSWORD),
         '-dname', 'CN=NovaListen Next, OU=Personal Android, O=NovaListen', '-noprompt'])
    os.chmod(KEY, 0o600)
    certificate = run([tool('keytool'), '-exportcert', '-keystore', str(KEY), '-storetype', 'PKCS12',
                       '-alias', ALIAS, '-storepass:file', str(PASSWORD)])
    policy = {'application_id': APPLICATION_ID, 'label': LABEL, 'first_version': '0.3.0',
              'certificate_sha256': digest(certificate), 'key_alias': ALIAS, 'keystore_type': 'PKCS12',
              'key_algorithm': 'RSA-3072', 'automatic_key_generation': False,
              'installation': 'parallel-to-com.novalisten.app', 'private_key_in_ci': False}
    # Public policy only. An existing fingerprint is never silently rewritten.
    with POLICY_PATH.open('x') as output:
        output.write(json.dumps(policy, ensure_ascii=False, indent=2) + '\n')
    instructions = '''NOVALISTEN NEXT — SAUVEGARDE PRIVÉE DE SIGNATURE

Ce ZIP contient la clé privée ET son mot de passe. Gardez-le dans un coffre privé
et une seconde copie hors ligne. Ne le transmettez pas à des tiers et ne copiez
jamais son contenu dans des messages, un dépôt, une release, un ticket ou un
artefact GitHub. Ne publiez jamais password.txt.

Identité stable : com.novalisten.app.next (NovaListen Next), distincte de 0.2.3.
Ne changez jamais cette clé, son certificat ni l’application ID pour une mise à
jour Next. La clé de l’ancienne com.novalisten.app n’est pas dans cette archive.

Restauration : recréer private-signing/novalisten-next/ (0700, ignoré par Git),
y remettre novalisten-next.p12 et password.txt (0600). Comparer public-policy.json
à config/parallel-signing-policy.json : l’empreinte doit rester identique.
Exécuter python3 scripts/parallel_signing.py verify-backup avec un runtime Java.
La vérification exporte le certificat public et utilise la vraie clé privée ; elle
ne génère jamais de clé. Restaurer aussi ce ZIP dans private-signing/ si nécessaire.
Après confirmation de votre copie indépendante, acknowledge-backup réautorise la
signature locale. La compilation CI ne reçoit aucun de ces fichiers privés.
'''
    descriptor = os.open(BACKUP, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as output, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('novalisten-next.p12', KEY.read_bytes())
        archive.writestr('password.txt', PASSWORD.read_bytes())
        archive.writestr('public-policy.json', json.dumps(policy, ensure_ascii=False, indent=2) + '\n')
        archive.writestr('RESTORE.txt', instructions)
    return verify_backup()


def verify_backup():
    guard_private_directory()
    policy = load_policy()
    verify_store(KEY, PASSWORD, policy)
    if BACKUP.is_symlink() or not BACKUP.is_file() or BACKUP.stat().st_mode & 0o077:
        raise SigningError('Copie de récupération privée absente/non protégée ; signature interdite.')
    with zipfile.ZipFile(BACKUP) as archive:
        if set(archive.namelist()) != {'novalisten-next.p12', 'password.txt', 'public-policy.json', 'RESTORE.txt'}:
            raise SigningError('Archive de récupération inattendue.')
        if json.loads(archive.read('public-policy.json')) != policy:
            raise SigningError('La copie de récupération ne correspond pas à la politique publique.')
        key_data = archive.read('novalisten-next.p12')
        password_data = archive.read('password.txt')
        if key_data != KEY.read_bytes() or password_data != PASSWORD.read_bytes():
            raise SigningError('Les copies privées divergent ; aucun remplacement automatique.')
    # Actually recover and use the copy, rather than merely trusting a file's existence.
    with tempfile.TemporaryDirectory(prefix='recovery-check-', dir=PRIVATE) as folder:
        recovered_key = Path(folder) / 'recovered.p12'
        recovered_password = Path(folder) / 'password.txt'
        private_write(recovered_key, key_data)
        private_write(recovered_password, password_data)
        verify_store(recovered_key, recovered_password, policy)
    return {'application_id': APPLICATION_ID, 'certificate_sha256': policy['certificate_sha256'],
            'backup_sha256': digest(BACKUP.read_bytes()), 'private_key_and_recovered_copy_verified': True}


def acknowledge_backup():
    receipt = verify_backup()
    if ACKNOWLEDGEMENT.exists():
        if json.loads(ACKNOWLEDGEMENT.read_text()) != receipt:
            raise SigningError('Confirmation de sauvegarde incohérente.')
    else:
        private_write(ACKNOWLEDGEMENT, (json.dumps(receipt, indent=2) + '\n').encode())
    return receipt


def verify_apk(apk, signer):
    policy = load_policy()
    report = run([tool('java'), '-jar', str(signer), 'verify', '--verbose', '--print-certs', str(apk)]).decode(errors='replace')
    fingerprints = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$', report, re.MULTILINE)
    if [value.lower() for value in fingerprints] != [policy['certificate_sha256']]:
        raise SigningError('Signature APK Next incorrecte/ambiguë : livraison bloquée.')
    return {'file': Path(apk).name, 'sha256': digest(Path(apk).read_bytes()),
            'certificate_sha256': policy['certificate_sha256']}


def sign_apk(source, output, signer, metadata, test_apk=False):
    receipt = verify_backup()
    if not ACKNOWLEDGEMENT.is_file() or json.loads(ACKNOWLEDGEMENT.read_text()) != receipt:
        raise SigningError('Confirmer d’abord la sauvegarde indépendante du propriétaire ; aucun APK signé.')
    info = json.loads(Path(metadata).read_text())
    role = 'test' if test_apk else 'app'
    expected_id = APPLICATION_ID + ('.test' if test_apk else '')
    expected = info[role]
    if (expected['application_id'] != expected_id or digest(Path(source).read_bytes()) != expected['sha256']
            or (test_apk and expected['target_package'] != APPLICATION_ID)):
        raise SigningError('Mauvais APK/identifiant. Seule la nouvelle application parallèle peut être signée.')
    output = Path(output)
    if output.exists():
        raise SigningError('Sortie déjà présente ; aucun écrasement implicite.')
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        run([tool('java'), '-jar', str(signer), 'sign', '--ks', str(KEY), '--ks-type', 'PKCS12',
             '--ks-key-alias', ALIAS, '--ks-pass', 'file:' + str(PASSWORD), '--key-pass', 'file:' + str(PASSWORD),
             '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
             '--v4-signing-enabled', 'false', '--out', str(output), str(source)])
        return verify_apk(output, signer)
    except BaseException:
        output.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    create = commands.add_parser('init')
    create.add_argument('--new-parallel-identity', action='store_true', required=True)
    commands.add_parser('verify-backup')
    commands.add_parser('acknowledge-backup')
    signing = commands.add_parser('sign')
    signing.add_argument('source'); signing.add_argument('output')
    signing.add_argument('--signer', required=True); signing.add_argument('--metadata', required=True)
    signing.add_argument('--test-apk', action='store_true')
    verify = commands.add_parser('verify-apk')
    verify.add_argument('apk'); verify.add_argument('--signer', required=True)
    args = parser.parse_args()
    try:
        if args.command == 'init': result = initialize()
        elif args.command == 'verify-backup': result = verify_backup()
        elif args.command == 'acknowledge-backup': result = acknowledge_backup()
        elif args.command == 'verify-apk': result = verify_apk(args.apk, args.signer)
        else: result = sign_apk(args.source, args.output, args.signer, args.metadata, args.test_apk)
        print(json.dumps(result, ensure_ascii=False))  # public hashes only, NEVER private contents
        return 0
    except (SigningError, OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        # All tool errors are fixed text. File/JSON errors must not reveal data either.
        print('NovaListen Next : ' + (str(error) if isinstance(error, SigningError) else 'Fichier local absent/invalide ; opération bloquée.'), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
