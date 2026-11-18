# NovaListen

Lecteur musical Android local, utilisable hors ligne. **Sources : 0.2.1, paroles du morceau écouté automatiques et autres enrichissements sur demande**.
La version 0.2.1 a été compilée et vérifiée : **127 tests unitaires + 15 tests Android 10 réussis**, lint sans erreur (32 avertissements). [Compte rendu 0.2.1](docs/developpement/06-paroles-centrees-et-karaoke.md).
Le document historique `Conception_Nova_Rush.docx` décrit un ancien jeu et n’est pas la spécification de cette application.


## Correction des paroles vérifiée — 0.2.1

- Écran allégé : Rechercher / Traduire / Modifier / Source dans les trois points ; ligne active centrée, y compris aux extrémités et après déplacement de lecture.
- Chargement automatique limité au morceau réellement écouté, activable/désactivable ; priorité absolue aux paroles déjà sauvegardées, cache temporaire en mémoire et aucun envoi de la collection.
- Conservation et rendu progressif des temps de segments TTML/LiriQo lorsqu’ils existent ; Enhanced LRC avec débuts natifs. Un LRC par ligne ne devient pas un faux minutage mot à mot. Aucune traduction automatique.
- [Périmètre, confidentialité et vérifications distinctes de 0.2.1](docs/developpement/06-paroles-centrees-et-karaoke.md). [Vérification réussie 44292086637](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44292086637), sources `bcdd86f7da19086d85fb8aede3bd7cf028c564be` ; distribution HTTPS du même binaire en cours. Les preuves historiques ci-dessous restent distinctes.

## Livraison précédente vérifiée — 0.2.0

**[Télécharger NovaListen 0.2.0 — APK de test (89,5 Mo)](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.0/NovaListen-0.2.0-debug.apk)** · [empreinte SHA-256](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.0/SHA256SUMS.txt) · [page de version](https://github.com/gagaladie08-pixel/Nova-Rush/releases/tag/novalisten-v0.2.0).

- [Compilation et tests réussis — exécution 44282482614](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44282482614), code applicatif `63702262c6d32b9b0356eaa038d9e5a3af256ba3`.
- APK de **89 460 991 octets**, SHA-256 `deff51cbbcccfd4676c646cf8544760dcfca23f1c68b023ca1204ea9c1d82497`, Android 8+ universel.
- Manifeste binaire contrôlé : Internet/HTTPS pour les commandes volontaires, pas de microphone/localisation, pas de provider automatique ML Kit. Aucun secret réel ou musique de démonstration inclus.
- **Attention : signature debug différente de 0.1**. Android refuse normalement sa mise à jour directe ; une désinstallation effacerait les données privées sans export disponible. Voir le [compte rendu et les précautions d’installation](docs/developpement/05-verification-api-0.2.md).
- [Mode d’emploi des API et clés](docs/developpement/04-api-et-cles.md). Les tests synthétiques ne prouvent pas la disponibilité/admission de chaque service sur le téléphone.

## Livraison historique vérifiée — 0.1.0

- [Compilation GitHub Actions réussie — exécution 44268439182](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44268439182).
- Sources de l’APK : commit `1da6c447840010a5063ae59d3e042fe5c4be7174` de la branche de session.
- **25 tests unitaires + 3 tests sur émulateur Android 10 réussis**, zéro échec ou test ignoré ; lint sans erreur.
- `NovaListen-0.1.0-debug.apk` : **24 642 420 octets**, signature APK v2, ARMv7 / ARM64 / x86 / x86_64.
- SHA-256 : `df8d40e7825cdcc1ad87c70403f419ffd7ddd04d06abe45c552b0c3123931692`.
- Le manifeste compilé a été inspecté : **pas de permission Internet, microphone ou localisation**. Les 25 familles de polices demandées sont présentes avec leurs licences et leurs hachages d’origine. Pas de musique de démonstration dans l’APK.
- [Guide d’installation et d’essai sur téléphone](docs/developpement/02-guide-essai.md) · [compte rendu de vérification et limites](docs/developpement/03-verification-livraison.md).

Ces preuves concernent exclusivement **0.1.0**, sans API. Les sources 0.2 ajoutent volontairement Internet et ML Kit : elles ne sont pas couvertes par cette ancienne inspection. Le prototype n’est pas une version complète ou certifiée sur tous les téléphones.

## Nouveautés des sources 0.2

- **Réglages → API et clés** : identifiants chiffrés AES-GCM/Android Keystore, saisie masquée, suppression individuelle/globale ; aucun secret embarqué ni appel de vérification à l’enregistrement.
- Recherches volontaires de paroles, comparaison de métadonnées, biographies FR puis anglais sur demande, identité et photos, pochettes d’affichage. Aperçu et choix explicite, sources/crédits sauvegardés, fichiers audio intacts.
- Google Translate via **ML Kit sur l’appareil**, packs volontaires et diagnostics déclarés ; **Azure standard F0 uniquement sur demande explicite**, jamais fallback automatique.
- Budget partagé de deux requêtes ; premier lot photo borné visant six vignettes décodables et distinctes, avec déduplication locale, pas six portraits garantis.
- Migration SQLite v1 → v2 sans effacement volontaire des données.

**[Mode d’emploi, identifiants nécessaires, confidentialité et limites exactes](docs/developpement/04-api-et-cles.md).** Aucun compte n’a été créé ni aucune clé réelle incluse. TheAudioDB/Deezer restent non activés faute d’accès gratuit confirmé. Les autres conditions d’admission et droits des contenus restent distincts de la possession d’une clé.

## Compiler et obtenir l’APK

La compilation est exécutée par **GitHub Actions**, workflow [NovaListen — APK et tests](.github/workflows/novalisten-apk.yml), sur la branche de travail `arena/216bd0ac-nova-rush`.

1. Ouvrir l’exécution correspondant au dernier commit dans l’onglet **Actions** du dépôt.
2. Télécharger l’artefact **NovaListen-0.2.1-debug** seulement après le succès de la compilation **et des tests**.
3. Extraire `NovaListen-0.2.1-debug.apk` du ZIP et le transférer sur le téléphone.
4. Autoriser l’installation depuis le navigateur/gestionnaire de fichiers utilisé, puis installer.
5. Dans NovaListen, toucher **Autoriser ma musique** et accorder l’accès aux fichiers audio.

**Android 8.0 minimum (API 26), cible Android 15 (API 35).** APK universel, sans ABI native imposée. Il s’agit d’un APK signé avec une clé **debug générée sur le runner**, pas d’une signature de publication. Une compilation ultérieure peut avoir un autre certificat et nécessiter une désinstallation : celle-ci efface les données locales de NovaListen, pas les fichiers musicaux. Ne pas présenter cette signature comme stable pour les mises à jour.

Le dépôt étant public, les sources et artefacts Actions sont publics. **Aucune musique de l’utilisateur, clé API ou donnée du téléphone n’est incluse.** Les sons de test sont synthétisés uniquement sur l’émulateur et supprimés après les tests.

### Développement local

Installer JDK 17 et le SDK Android 35, renseigner `ANDROID_HOME` ou `local.properties`, puis :

```sh
python3 scripts/verify-project.py
./gradlew testDebugUnitTest lintDebug assembleDebug
# Avec un émulateur/appareil Android démarré :
./gradlew connectedDebugAndroidTest
```

Le résultat local est `app/build/outputs/apk/debug/app-debug.apk`. Les téléchargements de dépendances ont lieu sur la machine de compilation, **pas au lancement de l’application**.

## Socle local conservé de 0.1

- MediaStore Android : indexation musicale, actualisation par ContentObserver, recherche insensible aux accents, six tris avec mémorisation.
- Durées connues inférieures à 60 s exclues ; 60 s admises ; durées inconnues en attente et non jouables.
- Media3 : lecture locale, pause, précédent/suivant, déplacement, répétition, file ; service de premier plan, audio focus, notification et commandes compatibles d’écran verrouillé/casque.
- Mini-player et player communs ; paroles, retour, gestes principaux, mode immersif après 7 s ; maintien d’écran uniquement pendant le player plein écran au premier plan.
- Favoris persistants ; masquage global réversible ; Lire ensuite en bloc, sans doublons à venir du même fichier et sans démarrage s’il n’y a aucun morceau chargé.
- Accueil essentiel ; grilles d’artistes/album ; pages locales ; portraits et biographies ajoutés manuellement et sauvegardés hors ligne.
- Playlists **classiques** : créer, rechercher/trier, ajouter à plusieurs listes, insertion en tête sans doublon du même fichier, renommer, réordonner par poignée/flèches, retirer, couverture locale, épingler, dupliquer, supprimer avec Annuler. Suppression de la source : lecture arrêtée et file vidée ; Annuler ne relance rien.
- Historique SQLite : écoute validée après 60 s réellement jouées cumulées par passage ; pas d’ajout par pause/seek ; événements conservés sans expiration avec fuseau/date locale de validation. Références devenues inaccessibles conservées sans être proposées à la lecture.
- Paroles collées ou importées TXT/LRC ; versions originale et française **manuelle** distinctes ; minutage LRC par ligne quand il existe, sans faux mot-à-mot. Sauvegarde dans l’application, pas dans les tags audio.
- **Cyber Nova par défaut**, 15 palettes exactes, 25 familles de polices incluses avec licences, formes et premiers dessins/décors propres aux univers. Un profil personnalisé pour les six catégories. Changement visuel sans relancer la lecture.
- Partage par autorisation URI Android ; suppression réelle de fichiers seulement après confirmation explicite et, si nécessaire, validation Android.

## Ce qui n’est PAS encore réalisé

Ce n’est pas l’ensemble des 21 fiches de conception en une version. Voir [le périmètre et les choix techniques provisoires](docs/developpement/01-premiere-version.md).

- Admission gratuite TheAudioDB/Deezer, collecte de portraits Genius/TheAudioDB/Discogs, cascade entièrement adaptative et moteurs directs privés non activés ; les limites des connecteurs actuels sont détaillées dans le guide API.
- Fil Rouge, modèles locaux intelligents et les 17 playlists intelligentes.
- Fusion des éditions d’un album, reconnaissance de l’identité des enregistrements, classification fiable Album/EP/Mixtape/Single/Participation.
- Écriture de métadonnées dans les fichiers audio ; définition de sonnerie ; gain 200 %, égaliseur, minuterie et analyse audio.
- Tous les modules et options de personnalisation de l’Accueil ; paramètres historiques restés ouverts dans la conception.
- Rendu final de tous les effets, iconographie dessinée intégralement sur mesure, licences des bibliothèques présentées dans une page exhaustive, import/export ou plusieurs profils personnalisés.
- **Listening Party et YouTube** : onglets explicites « à concevoir », sans connexion ni faux contenu.

## Confidentialité

Les sources actuelles demandent **Internet** et utilisent HTTPS uniquement. Le scan et la lecture restent locaux ; aucune collection, musique ou historique n’est envoyé automatiquement. Les paroles automatiques transmettent seulement titre/artiste/album/durée du morceau réellement écouté si aucune VO n’est sauvegardée et que l’option est active. Les autres recherches transmettent les champs affichés seulement sur commande ; Azure envoie la VO seulement après un consentement dédié. **ML Kit est facultatif mais ses diagnostics Google ne sont pas une promesse de zéro réseau** ; son initialisation automatique au lancement est retirée. Pas de compte obligatoire, publicité, microphone ou localisation.

Les fichiers audio ne sont pas modifiés par les recherches, traductions, favoris, thèmes, playlists, pochettes d’affichage ou masquages. Seule l’action explicite **Effacer** peut supprimer de l’audio. Les images choisies, textes et crédits sont conservés dans le stockage privé. Sauvegarde/transfert automatique Android désactivés. Les secrets sont chiffrés sur le téléphone, jamais intégrés au dépôt, à l’APK ou aux workflows.

La traduction locale est fournie par **Google Translate via ML Kit** ; [Google Cloud Translation](https://cloud.google.com/translate) et [déclaration des diagnostics](https://developers.google.com/ml-kit/android-data-disclosure). Le badge officiel et le disclaimer Google sont accessibles dans l’application.

Les 21 [fiches de conception](docs/conception/01-accueil.md) conservent leur statut documentaire historique ; leur rédaction avant ce premier développement ne prouve pas qu’une fonction est déjà implémentée.
