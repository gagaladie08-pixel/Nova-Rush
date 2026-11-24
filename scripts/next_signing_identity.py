#!/usr/bin/env python3
"""Explicit, one-time Next 0.3.1 reinstall identity. Never an automatic fallback.

The old Next/0.2.3 policies and private paths are not modified. An existing new
identity or partial provisioning blocks this command permanently: recovery, not
regeneration, is required. No private contents are printed, committed or sent to
GitHub by this script. The owner must place the private payload in Actions Secrets
and retain an independent copy before delivery can proceed.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import secrets
import sys
import tempfile
import zipfile

import parallel_signing as legacy

ROOT = legacy.ROOT
POLICY_PATH = ROOT / 'config/next-signing-policy.json'
CUSTODY_PATH = ROOT / 'config/next-signing-custody.json'
PRIVATE_ROOT = ROOT / 'private-signing'
PRIVATE = PRIVATE_ROOT / 'novalisten-next-0.3.1'
KEY = PRIVATE / 'novalisten-next.p12'
PASSWORD = PRIVATE / 'password.txt'
BACKUP = PRIVATE_ROOT / 'NovaListen-Next-0.3.1-signature-backup.zip'
SECRET_FILE = PRIVATE_ROOT / 'NovaListen-Next-0.3.1-GitHub-secret.txt'
STORAGE = 'github-actions-secret-and-independent-owner-backup'


class IdentityError(Exception):
    """Fixed, non-secret messages only."""


def load_policy():
    try:
        policy = json.loads(POLICY_PATH.read_text())
        old_next = legacy.load_policy()
        historical = json.loads((ROOT / 'config/signing-policy.json').read_text())
        if (policy.get('application_id') != legacy.APPLICATION_ID or policy.get('label') != legacy.LABEL
                or policy.get('key_alias') != legacy.ALIAS or policy.get('keystore_type') != 'PKCS12'
                or policy.get('key_algorithm') != 'RSA-3072' or policy.get('first_version') != '0.3.1'
                or policy.get('automatic_key_generation') is not False
                or policy.get('private_key_in_ci') is not True or policy.get('storage') != STORAGE
                or policy.get('requires_reinstall_from_0_3_0') is not True
                or policy.get('previous_certificate_sha256') != old_next['certificate_sha256']
                or not isinstance(policy.get('certificate_sha256'), str)
                or not re.fullmatch(r'[0-9a-f]{64}', policy['certificate_sha256'])
                or policy['certificate_sha256'] in {old_next['certificate_sha256'], historical['certificate_sha256']}):
            raise IdentityError('Politique de réinstallation Next incohérente ; aucune substitution historique.')
    except IdentityError:
        raise
    except Exception:
        raise IdentityError('Nouvelle identité Next absente/invalide ; aucune génération automatique.') from None
    return policy


def guard_private_paths():
    if PRIVATE_ROOT.is_symlink() or PRIVATE.is_symlink():
        raise IdentityError('Répertoire privé lié symboliquement ; provisionnement interdit.')
    if PRIVATE_ROOT.exists() and (not PRIVATE_ROOT.is_dir() or PRIVATE_ROOT.stat().st_mode & 0o077):
        raise IdentityError('Répertoire de signature non privé ; provisionnement interdit.')
    for path in [KEY, PASSWORD, BACKUP, SECRET_FILE]:
        result = legacy.subprocess.run(['git', 'check-ignore', '-q', str(path.relative_to(ROOT))], cwd=ROOT)
        if result.returncode:
            raise IdentityError('Fichier de récupération non ignoré par Git ; provisionnement interdit.')


def provision(expected_previous_certificate, owner_accepted_data_loss, owner_can_create_secret):
    if os.environ.get('GITHUB_ACTIONS') == 'true':
        raise IdentityError('Aucune génération de clé admise dans Actions.')
    if not owner_accepted_data_loss or not owner_can_create_secret:
        raise IdentityError('Réinstallation Next et accès propriétaire au coffre doivent être confirmés explicitement.')
    old_next = legacy.load_policy()
    if expected_previous_certificate != old_next['certificate_sha256']:
        raise IdentityError('L’ancienne empreinte Next ne correspond pas ; aucune clé créée.')
    guard_private_paths()
    if any(path.exists() or path.is_symlink() for path in [POLICY_PATH, PRIVATE, BACKUP, SECRET_FILE]):
        raise IdentityError('Une nouvelle identité ou copie existe déjà ; restaurer, ne jamais régénérer.')
    try:
        custody = json.loads(CUSTODY_PATH.read_text())
    except Exception:
        raise IdentityError('Registre de conservation absent/invalide ; aucune clé créée.') from None
    if (custody.get('schema') != 1 or custody.get('application_id') != legacy.APPLICATION_ID
            or custody.get('repository') != 'gagaladie08-pixel/Nova-Rush'
            or custody.get('secret_name') != 'NOVALISTEN_NEXT_SIGNING_BACKUP_B64'
            or custody.get('status') != 'pending_secret_access'
            or custody.get('certificate_sha256') is not None or custody.get('backup_sha256') is not None
            or custody.get('owner_recovery_copy_confirmed') is not False):
        raise IdentityError('Registre déjà provisionné ou incohérent ; aucune clé créée.')
    # Once this directory exists, even an interrupted operation cannot regenerate a key.
    PRIVATE_ROOT.mkdir(mode=0o700, exist_ok=True)
    PRIVATE.mkdir(mode=0o700)
    legacy.private_write(PASSWORD, (secrets.token_urlsafe(48) + '\n').encode())
    legacy.run([legacy.tool('keytool'), '-genkeypair', '-alias', legacy.ALIAS,
                '-keyalg', 'RSA', '-keysize', '3072', '-sigalg', 'SHA256withRSA', '-validity', '10000',
                '-storetype', 'PKCS12', '-keystore', str(KEY), '-storepass:file', str(PASSWORD),
                '-keypass:file', str(PASSWORD), '-dname', 'CN=NovaListen Next, OU=Stable Next Signing, O=NovaListen', '-noprompt'])
    os.chmod(KEY, 0o600)
    certificate = legacy.run([legacy.tool('keytool'), '-exportcert', '-keystore', str(KEY),
                              '-storetype', 'PKCS12', '-alias', legacy.ALIAS, '-storepass:file', str(PASSWORD)])
    policy = {'application_id': legacy.APPLICATION_ID, 'label': legacy.LABEL, 'first_version': '0.3.1',
              'certificate_sha256': legacy.digest(certificate), 'key_alias': legacy.ALIAS,
              'keystore_type': 'PKCS12', 'key_algorithm': 'RSA-3072', 'automatic_key_generation': False,
              'private_key_in_ci': True, 'storage': STORAGE, 'requires_reinstall_from_0_3_0': True,
              'previous_certificate_sha256': old_next['certificate_sha256']}
    if policy['certificate_sha256'] in {old_next['certificate_sha256'], json.loads((ROOT / 'config/signing-policy.json').read_text())['certificate_sha256']}:
        raise IdentityError('Nouvelle empreinte inattendue ; opération interrompue, aucun remplacement historique.')
    legacy.verify_store(KEY, PASSWORD, policy)
    with POLICY_PATH.open('x') as output:
        output.write(json.dumps(policy, ensure_ascii=False, indent=2) + '\n')
    instructions = '''NOVALISTEN NEXT 0.3.1 — SAUVEGARDE PRIVÉE DE LA NOUVELLE SIGNATURE

Ce ZIP contient la clé privée ET son mot de passe. Il n’est pas lui-même chiffré.
Conservez-le dans un coffre privé indépendant du dépôt et une copie hors ligne.
Ne publiez jamais le ZIP, son contenu ou sa base64 dans le code, une release,
un ticket, une variable publique, le chat ou un artefact Actions.

Application : com.novalisten.app.next (NovaListen Next), distincte de 0.2.3.
Cette NOUVELLE signature est autorisée pour une réinstallation de Next seulement.
Elle ne met PAS à jour Next 0.3.0 par-dessus et ne remplace PAS NovaListen 0.2.3.
La désinstallation efface les données privées de Next. Attendre le nouvel APK
vérifié avant de désinstaller ; aucun export intégré des données de l’ancien Next.

Pour toutes les prochaines mises à jour, conserver EXACTEMENT cette clé.
Politique publique : config/next-signing-policy.json. Ne pas changer le certificat.
Secret Actions : NOVALISTEN_NEXT_SIGNING_BACKUP_B64 = base64 exacte de ce ZIP.
La base64 contient aussi la clé et son mot de passe : ce n’est pas un chiffrement.
Restauration de contrôle : python3 scripts/next_key_vault.py verify avec le secret.
Elle vérifie le certificat et utilise la vraie clé privée, puis nettoie les fichiers.
Une copie locale seule n’est pas une preuve de stockage durable dans Actions.
Ne jamais générer une autre clé si un secret ou fichier manque : restaurer celle-ci.
'''
    descriptor = os.open(BACKUP, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as output, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('novalisten-next.p12', KEY.read_bytes())
        archive.writestr('password.txt', PASSWORD.read_bytes())
        archive.writestr('public-policy.json', json.dumps(policy, ensure_ascii=False, indent=2) + '\n')
        archive.writestr('RESTORE.txt', instructions)
    # Check the copy independently and use the recovered private key, not just its certificate.
    with zipfile.ZipFile(BACKUP) as archive, tempfile.TemporaryDirectory(prefix='copy-check-', dir=PRIVATE) as folder:
        restored_key = Path(folder) / 'novalisten-next.p12'
        restored_password = Path(folder) / 'password.txt'
        legacy.private_write(restored_key, archive.read('novalisten-next.p12'))
        legacy.private_write(restored_password, archive.read('password.txt'))
        legacy.verify_store(restored_key, restored_password, load_policy())
    if BACKUP.stat().st_size > 32768:
        raise IdentityError('Copie trop volumineuse pour le coffre prévu ; clé conservée sans régénération.')
    legacy.private_write(SECRET_FILE, base64.b64encode(BACKUP.read_bytes()) + b'\n')
    custody.update(status='awaiting_remote_verification', certificate_sha256=policy['certificate_sha256'],
                   backup_sha256=legacy.digest(BACKUP.read_bytes()), owner_recovery_copy_confirmed=False)
    # This JSON contains public hashes only; never add a password or an archive here.
    with tempfile.NamedTemporaryFile(mode='w', dir=CUSTODY_PATH.parent, delete=False) as output:
        temporary = Path(output.name)
        output.write(json.dumps(custody, ensure_ascii=False, indent=2) + '\n')
    try:
        temporary.replace(CUSTODY_PATH)
    finally:
        temporary.unlink(missing_ok=True)
    return {'application_id': legacy.APPLICATION_ID, 'certificate_sha256': policy['certificate_sha256'],
            'backup_sha256': custody['backup_sha256'], 'recovery_copy_locally_verified': True,
            'durable_secret_read_verified': False, 'owner_recovery_copy_confirmed': False,
            'status': custody['status'], 'apk_signed_or_delivered': False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    create = sub.add_parser('provision-reinstall')
    create.add_argument('--expected-previous-certificate', required=True)
    create.add_argument('--owner-accepted-next-data-loss', action='store_true', required=True)
    create.add_argument('--owner-can-create-repository-secret', action='store_true', required=True)
    args = parser.parse_args(argv)
    try:
        result = provision(args.expected_previous_certificate, args.owner_accepted_next_data_loss,
                           args.owner_can_create_repository_secret)
    except IdentityError as error:
        print('Identité Next : ' + str(error), file=sys.stderr)
        return 1
    except Exception:
        print('Identité Next : opération interrompue ; aucune donnée privée affichée. Ne pas régénérer.', file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())
