# NovaListen 0.1.0 — Vérification du livrable

## Provenance

- Dépôt : `gagaladie08-pixel/Nova-Rush`.
- Branche de développement : `arena/216bd0ac-nova-rush`.
- Commit de code compilé : `1da6c447840010a5063ae59d3e042fe5c4be7174`.
- [GitHub Actions : exécution 44268439182, conclusion success](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44268439182).
- Artefact de téléchargement : **NovaListen-0.1.0-debug**, conservation Actions de 30 jours selon le workflow. L’APK a aussi été récupéré dans l’espace de livraison de la conversation ; il n’est pas commis dans Git.

Les premières exécutions ont permis de corriger l’initialisation du runner, une contrainte de forme Compose, un attribut incompatible avec API 26 et un défaut de couleur de texte constaté sur les captures. Les résultats suivants se rapportent **à la dernière exécution réussie**, pas aux tentatives précédentes.

## Résultats automatisés effectivement obtenus

| Contrôle | Résultat |
| --- | --- |
| Intégrité des palettes | 15 palettes, 120 codes dans l’ordre exact de la fiche 21. |
| Polices demandées | 25 familles, fichiers et licences présents ; hachages d’origine revérifiés dans l’APK final. Une ressource numérique Roboto est également apportée par les bibliothèques. |
| Tests unitaires Bibliothèque / références / dates | 12 réussis. |
| Tests unitaires compteur de passage | 8 réussis. |
| Tests unitaires LRC | 5 réussis. |
| Lint Android | Zéro erreur ; avertissements restants, notamment versions de dépendances et cible Android plus récente, à traiter avant une diffusion de production. |
| Assemblage | APK debug universel produit et signé. |
| Émulateur Android 10 (API 29), x86_64 | Installation et 3 tests instrumentés réussis ; aucun échec, erreur ou test ignoré. |
| Navigation / thème | Sept onglets accessibles ; choix Cloud Nine conservé après recréation d’activité ; retour à Cyber Nova. |
| Médias réels de test | WAV synthétiques 65 s / 12 s créés uniquement sur l’émulateur ; le court est filtré, le long se lit et ouvre mini-player / player / paroles. |
| Régression de lisibilité | Vérification de pixels clairs sur le titre Bibliothèque de Cyber Nova, après correction de LocalContentColor. |
| Éditeur personnalisé | Présence et accès aux six catégories. Cela ne teste pas exhaustivement toutes leurs combinaisons. |

Les captures de l’Accueil/du sélecteur, de la Bibliothèque, des paroles et de l’éditeur sont conservées avec les rapports Actions. Les captures sont des preuves ponctuelles d’écrans, pas un audit visuel exhaustif des quinze univers.

## Inspection de l’APK remis

- Fichier : `NovaListen-0.1.0-debug.apk`.
- Taille : **24 642 420 octets**, environ 24,6 Mo.
- Nom : **NovaListen**.
- Identifiant : `com.novalisten.app`.
- Version : `0.1.0`, code `1`.
- minSdk : **26** (Android 8.0), targetSdk : **35** (Android 15).
- Architectures embarquées : `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
- Signature : APK v2 présente ; installation réussie sur l’émulateur. Certificat debug, pas signature de publication.
- SHA-256 de l’APK :

```text
df8d40e7825cdcc1ad87c70403f419ffd7ddd04d06abe45c552b0c3123931692
```

Empreinte SHA-256 du certificat public de test :

```text
9f5c25a954d54bbea9a3cd9ab1757038c942753588759ea52c9613bf6312a446
```

Le fichier récupéré correspond au SHA-256 annoncé par le job et à l’objet de transfert GitHub. Le ZIP interne de l’APK est lisible sans erreur. Le manifeste compilé ne contient ni `INTERNET`, ni `RECORD_AUDIO`, ni permission de localisation. Il contient `ACCESS_NETWORK_STATE` (consultation de l’état réseau), distinct de l’autorisation Internet et n’autorisant pas une connexion réseau à lui seul. Accès audio, notification, service de lecture et verrou de réveil sont déclarés ; l’écriture externe est limitée à Android 9 et antérieur et n’est demandée que pour la suppression correspondante. `allowBackup=false` et exclusions de sauvegarde/transfert sont incluses. Aucun fichier musical MP3/WAV/FLAC de démonstration n’est embarqué.

## Ce que ces tests ne prouvent pas

- Pas encore d’installation ou d’écoute sur le téléphone de l’utilisateur, ni de validation sur toutes les versions d’Android, tous les constructeurs, formats, casques et sorties.
- Le test de lecture sur émulateur ne constitue pas une mesure subjective du son ni un test d’une minute entière du compteur dans toutes les situations réelles ; la logique du compteur est vérifiée séparément par les tests unitaires.
- Pas de test exhaustif de suppression sur chaque version Android, catalogues volumineux, longue durée en arrière-plan, accessibilité, contraste, consommation ou fréquence d’affichage.
- Les fonctions avancées et écarts de périmètre restent ceux du [README](../../README.md) et de la [première phase de code](01-premiere-version.md). Aucun résultat d’API, traduction ou modèle d’IA n’a été simulé comme fonctionnel.
- Le certificat debug du runner peut changer à la prochaine compilation. Une désinstallation pour conflit de signature efface les données privées de NovaListen ; stratégie de signature durable et export restent à prévoir avant les mises à jour régulières.
