# NovaListen

Lecteur musical Android local, utilisable hors ligne. **Nouveau : une seule clé de livraison versionnée** — méthode Nova Rush — pour que chaque version s’installe par-dessus la précédente, sans désinstallation.

## Clé de livraison unique — plus de réinstallation

La cause des échecs précédents est connue : la clé de signature n’était pas conservée, chaque compilation en CI générait une nouvelle clé debug, et Android refuse alors d’installer une « mise à jour » signée différemment. À partir de **0.3.1**, la clé `config/signing/NovaListen-release.p12` est **versionnée avec son mot de passe**, comme la clé de test de Nova Rush : le certificat restera identique pour toutes les prochaines versions.

- Certificat épinglé : `c38a837ee566ad95000af71cb13497e3d330ebcde5c6c8fcc4880265a77c68ad` — SHA-256 du keystore : `45fade40c40095b84b58809b44cd2a187ab38a8d641d00a95379c2c4fa206625`.
- **Ce dépôt doit rester privé.** Une clé versionnée dans un dépôt public permettrait à n’importe qui de signer un faux APK qui s’installe par-dessus la vraie application.
- Garde-fous : aucune génération implicite de clé, contrôle de l’empreinte du certificat et de la possession de la clé privée avant chaque compilation, `apksigner` exigé sur un seul signataire avant livraison, workflow de livraison refusé sur un dépôt public.
- **Une dernière réinstallation est nécessaire** : 0.2.3 (`9c6531e7…`), Next 0.3.0 (`64dd5e74…`) et l’identité 0.3.1 abandonnée (`e59d21a2…`) ont des certificats différents. Après cette réinstallation, les mises à jour se feront normalement.
- Les fichiers audio du stockage partagé ne sont pas effacés. Les données internes de Next (favoris, playlists, paroles enregistrées, réglages) sont perdues lors de la désinstallation : **attendre l’APK 0.3.1 vérifié avant de désinstaller**. NovaListen 0.2.3 n’est pas modifiée et ne peut plus être mise à jour, sa clé étant perdue.

**[Périmètre, tests et limites de 0.3.1](docs/developpement/12-huit-corrections-next-0.3.1.md)** · **[Signature unique et réinstallation](docs/developpement/13-signature-unique-nova-rush.md)**.

## Sources 0.3.1 — huit corrections, pas encore un nouvel APK

Le lot corrige les crédits de collaborations, partage l’index du catalogue hors interface, regroupe les fragments d’albums compatibles et complète progressivement les artistes de la bibliothèque, **même jamais écoutés**, selon les options réseau. Les actions du lecteur sont rapprochées de la progression, le doublon immersif retiré, les paroles centrées sur un fond de pochette assombri/flouté et les explications regroupées dans Informations et aide.

**288 tests JVM/Robolectric + 49 tests Python réussis**, compilation Next et tests Android réussie, lint **zéro erreur / 43 avertissements** : [validation 45389015009](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/45389015009), code `f5fa872`. Les rendus API 28/35 sous Robolectric ne remplacent pas un essai sur téléphone ; la suite instrumentée est compilée, pas exécutée.

**[Périmètre, confidentialité, tests et limites de 0.3.1](docs/developpement/12-huit-corrections-next-0.3.1.md).** Version source `0.3.1 / code 7` ; même identité et politique de clé Next. Aucun nouvel APK signé/publication 0.3.1 dans ce lot : **le lien ci-dessous reste 0.3.0 et ne contient pas ces huit corrections**. Aucune modification de 0.2.3 ni nouvelle clé.

## APK 0.3.0 — NovaListen Next, installation parallèle

**[Télécharger NovaListen Next 0.3.0 — APK signé (89,8 Mo)](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-next-v0.3.0/NovaListen-Next-0.3.0-debug.apk)** · [empreinte SHA-256](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-next-v0.3.0/SHA256SUMS.txt) · [page de version](https://github.com/gagaladie08-pixel/Nova-Rush/releases/tag/novalisten-next-v0.3.0). Exactement le binaire signé et testé, publié sans recompilation ni nouvelle signature.

- Fichier remis : **`NovaListen-Next-0.3.0-debug.apk`**, **89 793 676 octets** (89,8 Mo), Android 8+.
- Nom de lanceur **NovaListen Next**, identifiant stable `com.novalisten.app.next` ; l’ancienne application reste installée, sans transfert automatique de ses choix/réglages.
- Nouvelle clé privée conservée hors de Git/GitHub, **restauration de sa sauvegarde vérifiée et copie indépendante confirmée par l’utilisateur**. Empreinte publique épinglée ; aucune nouvelle clé automatique pour les mises à jour Next.
- SHA-256 du fichier : `3a15fc41a8beffcb02c262b6759867df6c4113262f3812a99811fce6f01c52ec`.
- **[Binaire exact, récupération par API, signature, sauvegarde et preuves](docs/developpement/11-installation-parallele-et-cle-next.md)**. Le fichier signé est remis dans la session ; les liens de téléchargement 0.2.3 et antérieurs ci-dessous restent historiques, pas des liens vers Next.

## Sources 0.3.0 — historique du binaire publié

- Artistes et collaborations séparés, corrections locales et préservation des anciens profils.
- Biographies FR et portraits pendant l’écoute, lien d’identité au morceau local puis vérification croisée et droits avant attribution automatique ; choix manuels prioritaires, réglages indépendants. Cascade photo conditionnelle avec raisons des sources sautées, résultats progressifs et cache partagé.
- Tri Artistes / Albums par nombre de titres dans les deux sens, éditions d’albums et liste commune, édition représentative, classification locale Albums / EP / Mixtapes et type à préciser. Les relations The Fame / The Fame Monster et Future Nostalgia / Moonlight Edition demandées sont reconnues localement, sous garde d’artiste d’album.
- Bibliothèque au démarrage et lecteur plein écran sur lancement volontaire, pas sur un passage naturel de piste.
- Migration SQLite v5 additive ; aucun tag ou fichier audio modifié.

**240 tests JVM + 49 tests Python réussis**, application et tests Android compilés. [Compilation Next 44949752140](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44949752140), sources `b65348d` : lint sans erreur / 42 avertissements (41 pour le mode sources historique). **[Installation côte à côte et démarrage réussis sur émulateurs Android 10 et 15](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44950357575)** avec exactement l’APK signé remis : deux UID distincts, données synthétiques privées de l’ancien préservées, celles de Next conservées après réinstallation du même APK signé.

Ce sont des essais ciblés d’installation/démarrage, **pas la suite instrumentée complète 0.3.0**, qui reste compilée mais non exécutée. Aucun essai sur téléphone ni appel API musicale réel validé. La découverte photo est actuellement admise pour Commons/Openverse ; les autres branches conditionnelles affichent leur motif de saut.

**[Fonctionnement, limites des tags/identités et vérifications](docs/developpement/10-catalogue-et-artistes-automatiques.md).** Next est une installation parallèle explicitement choisie ; les résultats instrumentés complets ci-dessous concernent exclusivement l’ancien APK 0.2.3.

## Deux corrections — 0.2.3

**[Télécharger NovaListen 0.2.3 — APK de test (89,6 Mo)](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.3/NovaListen-0.2.3-debug.apk)** · [empreinte SHA-256](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.3/SHA256SUMS.txt) · [page de version](https://github.com/gagaladie08-pixel/Nova-Rush/releases/tag/novalisten-v0.2.3). Binaire testé publié sans recompilation ni nouvelle signature.

- **Commandes** dans la fenêtre mesurée de l'activité, protégées aussi par les insets Android racine ; marge basse de 20 dp en plus de la zone protégée, sur pochette et paroles, y compris après rotation/recréation sur les émulateurs testés.
- **Paroles automatiques, source et vrais temps natifs sauvegardés ensemble dans SQLite v4**, retrouvés à la réouverture hors ligne sans nouvelle recherche. Choix/modifications VO/VF prioritaires ; recherche de remplacement toujours dans les trois points. Aucun temps de mot inventé.
- Gestes confirmés conservés : lecteur gauche → paroles / droite → pochette ; bibliothèque gauche → Lire ensuite / droite → Informations. Pas d'extension aux autres choix visuels.
- Binaire vérifié : **89 559 354 octets**, sources `386642a01593a6bea0bb5ecb0e9d891ac0873049`, SHA-256 `4992b71f5634b90338504211e25541e8cbde5aa6c577a0c79e229996d0f87612`. [Preuves, limites et essai téléphone](docs/developpement/08-commandes-fenetre-et-paroles-persistantes.md).
- **Certificat différent de 0.2.0/0.2.1/0.2.2 : mise à jour directe refusée. Ne pas désinstaller aveuglément**, car cela efface les données privées, sans export intégré. Les anciens APK ne sont pas remplacés.

## Signature des prochains APK

Pour l’identifiant historique `com.novalisten.app`, le certificat de **0.2.3 reste obligatoire** : sa chaîne de livraison bloque sans la clé privée originale, toujours non retrouvée. Aucune substitution ou désinstallation n’est effectuée. [Audit et blocage historique](docs/developpement/09-continuite-signature.md).

**Next est une identité distincte**, autorisée ensuite pour l’installation parallèle : clé privée créée une seule fois, sauvegarde récupérable vérifiée et conservée par l’utilisateur, certificat épinglé. Compilation sans clé en CI, signature locale contrôlée ; cette nouvelle clé ne permet pas de mettre à jour 0.2.3. [Procédure et limites Next](docs/developpement/11-installation-parallele-et-cle-next.md).

## Historique 0.2.2 — swipes et commandes plein écran

La capture ultérieure du téléphone montrait des commandes encore coupées malgré les tests 0.2.2. Le correctif de fenêtre et les tests physiques renforcés sont dans 0.2.3, pas dans cet ancien binaire.

**[Télécharger NovaListen 0.2.2 — APK de test (89,5 Mo)](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.2/NovaListen-0.2.2-debug.apk)** · [empreinte SHA-256](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.2/SHA256SUMS.txt) · [page de version](https://github.com/gagaladie08-pixel/Nova-Rush/releases/tag/novalisten-v0.2.2).

- Lecteur : **gauche → paroles ; droite → pochette**, sans boucle entre les deux pages.
- Bibliothèque (lignes et cartes) : **gauche → Lire ensuite ; droite → Informations**, aperçu compris.
- Les commandes de lecture ont une place réservée et une marge basse de **20 dp en plus des zones système protégées**. La pochette s’adapte à la hauteur restante ; les actions secondaires peuvent défiler dans cette zone sans déplacer les commandes. Disposition compacte pour les fenêtres courtes/étroites, cibles tactiles d’au moins 48 dp conservées.
- Les gestes du mini-player, les gestes verticaux immersifs, la file, les données et le fonctionnement des paroles/API ne sont pas inversés ou réinitialisés.
- **APK 0.2.2 vérifié** : artefact `NovaListen-0.2.2-debug` dans [l’exécution réussie](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44305382562), sources `d7721e1eb2a9d3c44b29ab3b16a0448a041f26c7` ; fichier de 89 542 970 octets remis sans recompilation. [Changements, hachage, tests et limites](docs/developpement/07-swipes-et-commandes-player.md).
- **Certificat différent de 0.2.0 et 0.2.1** : pas de mise à jour directe avec ces APK. Ne pas désinstaller aveuglément : effacement des données privées, sans export intégré. Les anciens APK et leurs preuves restent inchangés.

## Correction des paroles vérifiée — 0.2.1

**[Télécharger NovaListen 0.2.1 — APK de test (89,5 Mo)](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.1/NovaListen-0.2.1-debug.apk)** · [empreinte SHA-256](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.1/SHA256SUMS.txt) · [page de version](https://github.com/gagaladie08-pixel/Nova-Rush/releases/tag/novalisten-v0.2.1).

**Attention : certificat debug différent de 0.2.0.** Android refusera normalement la mise à jour directe. Ne pas désinstaller sans préserver les données privées ; aucun export intégré. [Précautions et preuves](docs/developpement/06-paroles-centrees-et-karaoke.md).


- Écran allégé : Rechercher / Traduire / Modifier / Source dans les trois points ; ligne active centrée, y compris aux extrémités et après déplacement de lecture.
- Chargement automatique limité au morceau réellement écouté, activable/désactivable ; priorité absolue aux paroles déjà sauvegardées, cache temporaire en mémoire et aucun envoi de la collection.
- Conservation et rendu progressif des temps de segments TTML/LiriQo lorsqu’ils existent ; Enhanced LRC avec débuts natifs. Un LRC par ligne ne devient pas un faux minutage mot à mot. Aucune traduction automatique.
- [Périmètre, confidentialité et vérifications distinctes de 0.2.1](docs/developpement/06-paroles-centrees-et-karaoke.md). [Vérification réussie 44292086637](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44292086637), sources `bcdd86f7da19086d85fb8aede3bd7cf028c564be` ; APK publié sans recompilation par [l’exécution 44293391407](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44293391407), taille et digest GitHub identiques au binaire inspecté. Les preuves historiques ci-dessous restent distinctes.

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

1. Ouvrir une exécution **réussie du workflow de compilation** dans Actions (0.2.3 : [44930520156](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44930520156), sources `386642a01593a6bea0bb5ecb0e9d891ac0873049`). Un commit documentaire ou de publication ne recompile pas cet APK.
2. Télécharger l’artefact **NovaListen-0.2.3-debug** seulement après le succès de la compilation **et des tests**.
3. Extraire `NovaListen-0.2.3-debug.apk` du ZIP et le transférer sur le téléphone, en tenant compte de l'avertissement de signature ci-dessus.
4. Autoriser l’installation depuis le navigateur/gestionnaire de fichiers utilisé, puis installer.
5. Dans NovaListen, toucher **Autoriser ma musique** et accorder l’accès aux fichiers audio.

**Android 8.0 minimum (API 26), cible Android 15 (API 35).** APK universel, sans ABI native imposée. L’APK publié 0.2.3 a été signé avec une clé **debug générée sur le runner**. Pour les prochains APK, la CI exige désormais cette clé originale et ce même certificat ; elle restera bloquée tant que le keystore n’aura pas été retrouvé et configuré de façon sécurisée. [État réel et garde-fous](docs/developpement/09-continuite-signature.md). Ne pas contourner un refus de mise à jour par une désinstallation aveugle : celle-ci efface les données privées de NovaListen, sans export intégré, mais pas les fichiers musicaux partagés.

Le dépôt étant public, les sources et artefacts Actions sont publics. **Aucune musique de l’utilisateur, clé API ou donnée du téléphone n’est incluse.** Les sons de test sont synthétisés uniquement sur l’émulateur et supprimés après les tests.

### Développement local

Installer JDK 17 et le SDK Android 35, renseigner `ANDROID_HOME` ou `local.properties`, puis vérifier sans générer de binaire ni clé :

```sh
python3 scripts/verify-project.py
python3 -m unittest discover -s scripts -p 'test_*signing*.py'
NOVALISTEN_VERIFY_SOURCES=true ./gradlew -PnovalistenParallel=true testDebugUnitTest compileDebugAndroidTestKotlin lintDebug
```

Les tests JVM comprennent SQLite et des rendus Compose sous Robolectric ; `compileDebugAndroidTestKotlin` **compile**, mais n’exécute pas, la suite instrumentée sur appareil. Les dépendances sont téléchargées sur la machine de compilation, pas au lancement de l’application.

Le mode sources refuse assemblage, signature et installation. L’identité historique exige toujours sa clé originale 0.2.3. La préparation non signée Next, sa signature séparée avec **la clé Next existante** et les validations nécessaires sont décrites dans [la procédure de continuité Next](docs/developpement/11-installation-parallele-et-cle-next.md). Aucun keystore de remplacement ne doit être créé pour contourner ces protections.

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
- Reconnaissance audio universelle des enregistrements et identification automatique de toutes les éditions/types sans métadonnées fiables : non réalisées. Les sources 0.3.0 utilisent les identifiants locaux admissibles et les confirmations/corrections explicites, sans déduction arbitraire.
- Écriture de métadonnées dans les fichiers audio ; définition de sonnerie ; gain 200 %, égaliseur, minuterie et analyse audio.
- Tous les modules et options de personnalisation de l’Accueil ; paramètres historiques restés ouverts dans la conception.
- Rendu final de tous les effets, iconographie dessinée intégralement sur mesure, licences des bibliothèques présentées dans une page exhaustive, import/export ou plusieurs profils personnalisés.
- **Listening Party et YouTube** : onglets explicites « à concevoir », sans connexion ni faux contenu.

## Confidentialité

Les sources actuelles demandent **Internet** et utilisent HTTPS uniquement. Le scan et la lecture restent locaux ; aucun audio, fichier, URI locale ou historique n’est envoyé. Les paroles automatiques transmettent seulement titre/artiste/album/durée du morceau réellement écouté si aucune VO n’est sauvegardée et que l’option est active. **À partir des sources 0.3.1, les artistes de la bibliothèque peuvent être recherchés progressivement même sans écoute**, selon les options indépendantes de biographies et portraits : noms/identifiants et, si nécessaire pour l’identité, titre/album/durée d’un morceau de contexte sont transmis aux sources admissibles. Cela peut donc décrire progressivement une partie de la collection aux fournisseurs ; ce n’est pas limité au morceau joué ni un envoi de toute la liste en une seule fois. Les deux options peuvent être désactivées dans API et clés, sans supprimer les contenus locaux déjà sauvegardés. Les recherches restantes restent volontaires. Azure envoie la VO seulement après un consentement dédié. **ML Kit est facultatif mais ses diagnostics Google ne sont pas une promesse de zéro réseau** ; son initialisation automatique au lancement est retirée. Pas de compte obligatoire, publicité, microphone ou localisation.

Les fichiers audio ne sont pas modifiés par les recherches, traductions, favoris, thèmes, playlists, pochettes d’affichage ou masquages. Seule l’action explicite **Effacer** peut supprimer de l’audio. Les images choisies, textes et crédits sont conservés dans le stockage privé. Sauvegarde/transfert automatique Android désactivés. Les secrets sont chiffrés sur le téléphone, jamais intégrés au dépôt, à l’APK ou aux workflows.

La traduction locale est fournie par **Google Translate via ML Kit** ; [Google Cloud Translation](https://cloud.google.com/translate) et [déclaration des diagnostics](https://developers.google.com/ml-kit/android-data-disclosure). Le badge officiel et le disclaimer Google sont accessibles dans l’application.

Les 21 [fiches de conception](docs/conception/01-accueil.md) conservent leur statut documentaire historique ; leur rédaction avant ce premier développement ne prouve pas qu’une fonction est déjà implémentée.
