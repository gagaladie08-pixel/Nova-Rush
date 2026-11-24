#!/usr/bin/env python3
"""Verify recovery from the explicitly authorized Next Actions secret.

This command NEVER generates a key, changes a pin, signs an APK or publishes a
file. The secret must contain the base64 of the private recovery ZIP. All tools
run only after that secret has been removed from the process environment. The
restored files live in an ignored, owner-only temporary directory and are always
removed. Only public fingerprints and recovery status may reach stdout.
"""
import argparse
import base64
import binascii
import io
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
import zipfile

import parallel_signing as signing

SECRET_NAME = 'NOVALISTEN_NEXT_SIGNING_BACKUP_B64'
REPOSITORY = 'gagaladie08-pixel/Nova-Rush'
BRANCH_REF = 'refs/heads/arena/216bd0ac-nova-rush'
CUSTODY_PATH = signing.ROOT / 'config/next-signing-custody.json'
MAX_ARCHIVE_BYTES = 32768
MAX_SECRET_CHARS = 4 * ((MAX_ARCHIVE_BYTES + 2) // 3)
ENTRY_LIMITS = {
    'novalisten-next.p12': (1, 32768),
    'password.txt': (32, 512),
    'public-policy.json': (2, 8192),
    'RESTORE.txt': (1, 16384),
}


class VaultError(Exception):
    """Only fixed, non-secret error messages may be placed in this exception."""


def load_configuration():
    try:
        policy = signing.load_policy()
        custody = json.loads(CUSTODY_PATH.read_text())
    except Exception:
        raise VaultError('Politique Next ou registre de conservation absent/invalide.') from None
    if policy.get('private_key_in_ci') is not True:
        raise VaultError('La politique Next n’autorise pas encore ce stockage Actions ; aucune clé utilisée.')
    if (not isinstance(custody, dict) or type(custody.get('schema')) is not int or custody['schema'] != 1
            or custody.get('application_id') != signing.APPLICATION_ID
            or custody.get('repository') != REPOSITORY or custody.get('secret_name') != SECRET_NAME
            or not isinstance(custody.get('status'), str)
            or custody['status'] not in {'awaiting_remote_verification', 'ready'}
            or custody.get('certificate_sha256') != policy['certificate_sha256']
            or not isinstance(custody.get('backup_sha256'), str)
            or not re.fullmatch(r'[0-9a-f]{64}', custody['backup_sha256'])
            or type(custody.get('owner_recovery_copy_confirmed')) is not bool
            or (custody['status'] == 'ready' and not custody['owner_recovery_copy_confirmed'])):
        raise VaultError('Conservation durable non provisionnée ou incohérente ; aucune clé utilisée.')
    return policy, custody


def decode_secret(value):
    if not isinstance(value, str) or not value:
        raise VaultError('Secret de récupération Next absent : aucune génération de remplacement.')
    # Bound the input before stripping or decoding it. Never include its value in an error.
    if len(value) > MAX_SECRET_CHARS + 2:
        raise VaultError('Secret de récupération Next trop volumineux.')
    value = value.strip()
    if not value:
        raise VaultError('Secret de récupération Next absent : aucune génération de remplacement.')
    try:
        data = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError):
        raise VaultError('Secret Next invalide : base64 stricte attendue.') from None
    if not 1 <= len(data) <= MAX_ARCHIVE_BYTES:
        raise VaultError('Taille de l’archive privée Next invalide.')
    return data


def archive_entries(data, policy):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            entries = archive.infolist()
            # Reject duplicates, path traversal and directories, not only unexpected names.
            if len(entries) != len(ENTRY_LIMITS) or {e.filename for e in entries} != set(ENTRY_LIMITS):
                raise VaultError('Contenu de l’archive Next inattendu.')
            for entry in entries:
                low, high = ENTRY_LIMITS[entry.filename]
                if (entry.is_dir() or not low <= entry.file_size <= high
                        or entry.flag_bits & 1
                        or entry.compress_type not in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}
                        or stat.S_IFMT(entry.external_attr >> 16) not in {0, stat.S_IFREG}):
                    raise VaultError('Entrée de récupération Next non admissible.')
            contents = {entry.filename: archive.read(entry) for entry in entries}
        if json.loads(contents['public-policy.json']) != policy:
            raise VaultError('L’archive ne correspond pas à la politique Next épinglée.')
    except VaultError:
        raise
    except Exception:
        raise VaultError('Archive de récupération Next illisible/invalide.') from None
    return contents


def verification_origin():
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        return {'location': 'local', 'durable_secret_read_verified': False}
    if (os.environ.get('GITHUB_REPOSITORY') != REPOSITORY
            or os.environ.get('GITHUB_REF') != BRANCH_REF
            or not re.fullmatch(r'[1-9][0-9]*', os.environ.get('GITHUB_RUN_ID', ''))
            or not re.fullmatch(r'[0-9a-f]{40}', os.environ.get('GITHUB_SHA', ''))
            or os.environ.get('GITHUB_EVENT_NAME') not in {'push', 'workflow_dispatch'}):
        raise VaultError('Origine de vérification Actions non autorisée.')
    return {'location': 'github-actions', 'durable_secret_read_verified': True,
            'run_id': int(os.environ['GITHUB_RUN_ID']), 'commit': os.environ['GITHUB_SHA']}


def verify_from_environment():
    # The secret must not be inherited by keytool or by any subprocess.
    value = os.environ.pop(SECRET_NAME, None)
    data = decode_secret(value)
    del value
    origin = verification_origin()
    policy, custody = load_configuration()
    if signing.digest(data) != custody['backup_sha256']:
        raise VaultError('Le secret ne contient pas la copie de récupération épinglée.')
    contents = archive_entries(data, policy)
    try:
        signing.guard_private_directory()
        root = signing.PRIVATE_ROOT
        if root.is_symlink() or (root.exists() and (not root.is_dir() or root.stat().st_mode & 0o077)):
            raise VaultError('Répertoire temporaire de récupération non privé.')
        root.mkdir(mode=0o700, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='vault-restore-', dir=root) as folder:
            key = Path(folder) / 'novalisten-next.p12'
            password = Path(folder) / 'password.txt'
            signing.private_write(key, contents['novalisten-next.p12'])
            signing.private_write(password, contents['password.txt'])
            # Export the pinned public certificate AND use the restored private key.
            signing.verify_store(key, password, policy)
    except VaultError:
        raise
    except Exception:
        raise VaultError('La clé privée restaurée n’a pas pu être vérifiée/utilisée ; aucun remplacement.') from None
    # A local test is NOT proof of persistence in GitHub. Nor is repository
    # recovery alone proof of the owner's independent recovery copy.
    return {'application_id': signing.APPLICATION_ID,
            'certificate_sha256': policy['certificate_sha256'],
            'backup_sha256': custody['backup_sha256'],
            'private_key_and_recovered_copy_verified': True,
            'owner_recovery_copy_confirmed': custody['owner_recovery_copy_confirmed'],
            'custody_ready_for_signing': (origin['durable_secret_read_verified']
                                         and custody['status'] == 'ready'
                                         and custody['owner_recovery_copy_confirmed']),
            **origin}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['verify'])
    parser.parse_args(argv)
    try:
        result = verify_from_environment()
    except VaultError as error:
        print('Coffre Next : ' + str(error), file=sys.stderr)
        return 1
    except Exception:
        # Never expose JSON input, archive contents, tool stderr or a traceback.
        print('Coffre Next : contrôle interrompu ; aucun fichier privé publié.', file=sys.stderr)
        return 1
    prefix = '::notice title=Next key recovery::' if os.environ.get('GITHUB_ACTIONS') == 'true' else ''
    print(prefix + json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())
