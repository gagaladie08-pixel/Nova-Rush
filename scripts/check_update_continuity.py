#!/usr/bin/env python3
"""Bloque toute version qui casserait la mise à jour par-dessus la version livrée.

Android refuse d'installer un APK dont le certificat diffère de celui déjà installé.
Cette vérification compare la politique de signature du dépôt à l'identité réellement
livrée (apk-livraison/<sha>/manifeste.json) et refuse une versionCode qui reculerait.
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
POLICY = ROOT / 'config/signing-policy.json'
GRADLE = ROOT / 'app/build.gradle.kts'


class ContinuityError(Exception):
    pass


def latest_delivery(root=ROOT):
    """La livraison la plus récente est celle qui a le plus grand versionCode.

    Le dossier porte le SHA-256 du fichier : son ordre alphabétique ne dit rien de
    l'ordre des versions. Trier par dossier choisirait un jour une ancienne version.
    """
    manifests = list((root / 'apk-livraison').glob('*/manifeste.json'))
    if not manifests:
        raise ContinuityError('Aucune livraison enregistrée ; installer une première version.')
    def rank(path):
        try:
            data = json.loads(path.read_text())
        except ValueError:
            return (-1, 0)
        return (int(data.get('version_code') or 0), path.stat().st_mtime)
    latest = max(manifests, key=rank)
    return json.loads(latest.read_text()), latest


def gradle_identity(path=GRADLE):
    text = path.read_text()
    code = re.search(r'versionCode\s*=\s*(\d+)', text)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    application_id = re.search(r'applicationId\s*=\s*"([^"]+)"', text)
    if not (code and name and application_id):
        raise ContinuityError('Identité de version introuvable dans app/build.gradle.kts.')
    return {'application_id': application_id.group(1), 'version_code': int(code.group(1)),
            'version_name': name.group(1)}


def check(root=ROOT):
    policy = json.loads((root / 'config/signing-policy.json').read_text())
    delivered, source = latest_delivery(root)
    identity = gradle_identity(root / 'app/build.gradle.kts')
    keystore = root / policy['keystore_path']
    import hashlib
    digest = hashlib.sha256(keystore.read_bytes()).hexdigest() if keystore.is_file() else ''
    problems, notices = [], []
    if digest != policy['keystore_sha256']:
        problems.append('Le keystore a changé ou disparu : les mises à jour seraient refusées par Android.')
    if identity['application_id'] != policy['application_id']:
        problems.append('applicationId modifié : nouvelle application, pas une mise à jour.')
    if delivered.get('certificate_sha256') and delivered['certificate_sha256'] != policy['certificate_sha256']:
        problems.append('Certificat différent de la version déjà livrée.')
    delivered_code = delivered.get('version_code', 0)
    if identity['version_code'] < delivered_code:
        problems.append('versionCode inférieur à la version livrée : Android refuserait la mise à jour.')
    elif identity['version_code'] == delivered_code:
        notices.append('Même versionCode que la version livrée : réinstallation possible, pas une nouvelle version.')
    return {'delivered_version': delivered.get('version_name'), 'next_version': identity['version_name'],
            'version_code': identity['version_code'], 'certificate_sha256': policy['certificate_sha256'],
            'source': str(source.relative_to(root)), 'problems': problems, 'notices': notices}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', action='store_true', help='affiche la continuité sans échouer')
    args = parser.parse_args()
    try:
        report = check()
    except ContinuityError as error:
        print('Continuité : ' + str(error), file=sys.stderr)
        return 0 if args.report else 1
    print(json.dumps(report, ensure_ascii=False, indent=2))
    for notice in report.get('notices', []):
        print('Remarque : ' + notice, file=sys.stderr)
    if report['problems'] and not args.report:
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
