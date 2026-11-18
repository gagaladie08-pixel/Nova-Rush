# NovaListen

Lecteur musical Android local et hors ligne. **Première version : 0.1.0, APK de test**.
Le document historique `Conception_Nova_Rush.docx` décrit un ancien jeu et n’est pas la spécification de cette application.

## Compiler et obtenir l’APK

La compilation est exécutée par **GitHub Actions**, workflow [NovaListen — APK et tests](.github/workflows/novalisten-apk.yml), sur la branche de travail `arena/216bd0ac-nova-rush`.

1. Ouvrir l’exécution correspondant au dernier commit dans l’onglet **Actions** du dépôt.
2. Télécharger l’artefact **NovaListen-0.1.0-debug**.
3. Extraire `NovaListen-0.1.0-debug.apk` du ZIP et le transférer sur le téléphone.
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

## Fonctionnel dans 0.1

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

- API de paroles, métadonnées/pochettes, biographies/portraits ; aucune API activée ou clé incluse.
- Traduction ML Kit / Azure F0, Fil Rouge, modèles locaux et les 17 playlists intelligentes.
- Fusion des éditions d’un album, reconnaissance de l’identité des enregistrements, classification fiable Album/EP/Mixtape/Single/Participation.
- Écriture de métadonnées dans les fichiers audio ; définition de sonnerie ; gain 200 %, égaliseur, minuterie et analyse audio.
- Tous les modules et options de personnalisation de l’Accueil ; paramètres historiques restés ouverts dans la conception.
- Rendu final de tous les effets, iconographie dessinée intégralement sur mesure, licences des bibliothèques présentées dans une page exhaustive, import/export ou plusieurs profils personnalisés.
- **Listening Party et YouTube** : onglets explicites « à concevoir », sans connexion ni faux contenu.

## Confidentialité

Le manifeste ne demande **pas Internet**. Pas de publicité, compte, télémétrie, microphone ou localisation. Musique lue via les URI Android ; fichiers non déplacés ni modifiés par la lecture, les favoris, les thèmes, les playlists ou le masquage. Seule l’action explicite **Effacer** peut supprimer de l’audio. Les images choisies sont copiées dans le stockage privé de NovaListen. Sauvegarde automatique Android désactivée pour ne pas envoyer la base locale.

Les 21 [fiches de conception](docs/conception/01-accueil.md) conservent leur statut documentaire historique ; leur rédaction avant ce premier développement ne prouve pas qu’une fonction est déjà implémentée.
