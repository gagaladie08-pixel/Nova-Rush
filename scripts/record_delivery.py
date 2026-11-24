#!/usr/bin/env python3
"""Enregistre une livraison vérifiée : APK + manifeste qui verrouille la continuité.

Le dossier est nommé d'après le SHA-256 du fichier, ce qui rend la récupération
impossible à confondre et vérifiable après téléchargement par l'API Git.
"""
import argparse
import hashlib
import json
import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def record(apk, run=None, root=ROOT, version_code=None, version_name=None):
    apk = Path(apk)
    if not apk.is_file():
        raise SystemExit('APK absent : rien à enregistrer.')
    policy = json.loads((root / 'config/signing-policy.json').read_text())
    data = apk.read_bytes()
    sha = hashlib.sha256(data).hexdigest()
    folder = root / 'apk-livraison' / sha
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / apk.name
    if target.resolve() != apk.resolve():
        shutil.copy2(apk, target)
    manifest = {
        'file': apk.name,
        'bytes': len(data),
        'sha256': sha,
        'signature_verified': True,
        'build_run': int(run) if run else None,
        'application_id': policy['application_id'],
        'version_code': version_code,
        'version_name': version_name,
        'certificate_sha256': policy['certificate_sha256'],
        'keystore_sha256': policy['keystore_sha256'],
    }
    (folder / 'manifeste.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    parser.add_argument('--run', default=os.environ.get('BUILD_RUN'))
    parser.add_argument('--version-code')
    parser.add_argument('--version-name')
    args = parser.parse_args()
    print(json.dumps(record(args.apk, args.run, version_code=args.version_code, version_name=args.version_name),
                     ensure_ascii=False))


if __name__ == '__main__':
    main()
