# NovaListen 0.2.0 — Vérification de l’intégration API

## Binaire réellement compilé et testé

- Sources applicatives : **`63702262c6d32b9b0356eaa038d9e5a3af256ba3`**, branche `arena/216bd0ac-nova-rush`.
- [Exécution de compilation et tests **44282482614**](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44282482614) : **success** ; job/check `134726508634`.
- **92 tests unitaires + 10 tests Android 10**, zéro échec, zéro erreur, zéro test ignoré.
- Android lint : **zéro erreur / zéro fatal, 31 avertissements**. Ils concernent surtout versions de dépendances/cible à actualiser, usages KTX, emplacement d’un paramètre Modifier et le service Media3 exporté ; pas une certification de production.
- Fichier : **`NovaListen-0.2.0-debug.apk`**.
- **89 460 991 octets** (environ 89,5 Mo / 85,3 Mio).
- **SHA-256 : `deff51cbbcccfd4676c646cf8544760dcfca23f1c68b023ca1204ea9c1d82497`**.
- `com.novalisten.app`, version **0.2.0**, code **2**, minSdk **26 / Android 8**, targetSdk **35**.
- Bibliothèques natives : `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` ; APK universel. L’augmentation de poids inclut les moteurs natifs du SDK ML Kit, pas des packs de langues préinstallés.

Le fichier a été reconstitué à partir des cinq fragments de transfert du job. Taille et SHA-256 de chaque fragment, puis du fichier complet, ont été revérifiés. Le ZIP interne de l’APK est intègre. Le pont ne crée que des blobs GitHub non référencés : aucun APK ni secret n’est ajouté à un commit. La publication directe est effectuée séparément sans nouvelle compilation/signature.

## Téléchargement public du même binaire

- **[NovaListen-0.2.0-debug.apk](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.0/NovaListen-0.2.0-debug.apk)** · [SHA256SUMS.txt](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.0/SHA256SUMS.txt).
- [Version publique de test `novalisten-v0.2.0`](https://github.com/gagaladie08-pixel/Nova-Rush/releases/tag/novalisten-v0.2.0), release `449811770`, cible du tag : commit applicatif vérifié ci-dessus.
- [Publication sans recompilation **44284217987**](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44284217987), job `134732573228` : **success**.
- Métadonnées GitHub de l’asset contrôlées : `uploaded`, **89 460 991 octets**, digest identique au fichier inspecté. Pas de nouvelle signature entre test et publication.
- La version publique 0.1 et son empreinte ont aussi été contrôlées : elles sont inchangées. Les commits ultérieurs de documentation et de distribution ne modifient pas l’APK 0.2 testé.

## Tests effectivement passés

### 92 tests unitaires

| Suite | Nombre | Couverture principale |
| --- | ---: | --- |
| `LibraryRulesTest` | 12 | Filtre ≥60 s, attente des durées inconnues, recherche sans accents, références, file indépendante. |
| `PassageCounterTest` | 8 | Écoute réellement jouée, pause/seek, seuil de validation et passage. |
| `LyricsTest` | 5 | LRC existant, ordre et position active, absence de faux minutage. |
| `RemoteSourcesTest` | 43 | Réponses synthétiques LRCLIB/lrc.red/LiriQo/Textyl/Better, unités secondes/ms, TTML et blocage XXE, erreurs HTTP 200, métadonnées, biographies, licences, Commons/Openverse/CAA/fanart, identifiants et plan de traduction. |
| `RemoteHttpTest` | 9 | Intercepteurs en mémoire sans réseau : redirects privés refusés, 307 CAA suivi sans secrets, quota, tailles, 404 Better distinct de 401, images privées/interdites ou faux type. |
| `PhotoDiscoveryTest` | 15 | Cible de six aperçus réels, réutilisation/P18, doublons URL/visuels, droits non établis sautés, échecs/quota visibles, budget de douze, annulation et deux préparations maximum. |

### 10 tests instrumentés — émulateur Android 10/API 29

1. Navigation aux sept onglets et persistance d’un thème après recréation.
2. Fichiers WAV synthétiques originaux 65 s et 12 s : filtrage du court, vraie lecture locale, ouverture du player/paroles. Extension 0.2 : ouverture de la traduction sans réseau/ML Kit, langue seule insuffisante, accord local distinct, F0 seul insuffisant pour envoyer à Azure, consentement consommé même avec clé absente, ancienne VF intacte. **Aucun pack ni appel Azure réel**.
3. Six catégories de l’éditeur de thème accessibles.
4. Saisie d’une fausse clé de test via l’UI, texte non présent en clair dans les préférences, persistance après recréation, suppression, `FLAG_SECURE` actif puis retiré, aucun appel réseau.
5. Saisie non enregistrée non restaurée après recréation.
6. Coffre Keystore : déplacer un chiffrement entre deux fournisseurs échoue grâce à l’AAD ; statut illisible et ancien secret valide conservés ; remplacement/suppression possibles.
7. Base SQLite v1 migrée en v2 sans perdre favoris, paroles VO/VF ou biographie ; nouvelles sources sauvegardables.
8. Images synthétiques : cache de preview réutilisé, empreinte locale de doublon, sélection dédupliquée et fichier choisi enregistré dans le répertoire privé.
9. Échec SQLite provoqué volontairement : erreur signalée, ancien portrait et crédit conservés.
10. Manifeste fusionné : pas de `MlKitInitProvider`, Internet présent, ni microphone ni localisation fine.

Les fixtures sont synthétiques et les identifiants faux, exclusivement sur l’émulateur. Les tests ne font pas passer une clé enregistrée pour une clé valide. Les captures de navigation et de consentements ont été relues ; elles ne constituent pas un essai sur téléphone physique.

## Inspection séparée du fichier livré

Le manifeste binaire et les ressources ont aussi été inspectés après téléchargement :

- `INTERNET` et `ACCESS_NETWORK_STATE` présents ; **`usesCleartextTraffic=false`**.
- **Aucune permission microphone ni localisation**, fine ou approximative.
- **`allowBackup=false`** ; règles d’exclusion de sauvegarde/transfert conservées.
- Provider restant : `androidx.startup.InitializationProvider` ; **pas de provider automatique ML Kit**.
- 26 fichiers de polices : les 25 familles du projet et la police numérique Material, avec les licences/provenances déjà incluses.
- Aucun audio de démonstration dans l’APK.
- Badge Google Translate présent, **identique octet pour octet** au PNG officiel conservé : SHA-256 `44aab849963bc32493c1b2ff4ed9d47aea8d16cdfa12d3aaebef22feb2ba2ca9`.
- Signature APK v2 présente ; pas de signature v3 ni de chaîne de rotation de clé annoncée.

Certificat debug SHA-256 du binaire 0.2 :

```text
ee8c88dff11b6550d320f2d35412937424a487580cb2759a123c3e8b0f4cd18b
```

Le manifeste JSON d’inspection, les rapports et le fichier récupéré sont dans les livrables de la session (`artifacts/APK-INFO-0.2.0.json`, `artifacts/ci-44282482614/`), pas ajoutés à Git. Les preuves historiques 0.1 restent séparées.

## Installation et signature : attention aux données existantes

Le certificat **diffère de 0.1.0** (`9f5c25…`). Si cette ancienne application est installée, Android refusera normalement la mise à jour directe du même identifiant avec la nouvelle clé. La migration SQLite testée ne contourne pas la vérification de signature d’Android.

**Ne pas conseiller une désinstallation aveugle.** Elle effacerait favoris, playlists, historique, paroles, biographies, images copiées, identifiants et réglages privés. Les musiques du stockage partagé restent, mais aucun export des données de NovaListen n’est disponible. Utiliser un appareil/profil de test si l’ancienne bibliothèque privée doit être préservée ; une stratégie de signature durable reste nécessaire.

Pour une première installation, ouvrir le fichier APK sur Android 8+, autoriser son installation depuis le navigateur/gestionnaire utilisé si souhaité, puis accorder l’accès aux fichiers audio dans NovaListen. Aucune clé, compte, recherche distante ou pack n’est requis pour scanner et lire la musique locale.

## Ce que cette validation ne prouve pas

- Aucun service musical réellement interrogé avec les données d’un utilisateur, aucune clé réelle validée et aucun compte créé.
- Aucun téléchargement de pack ni exécution réelle du moteur de traduction ML Kit ou Azure dans ces tests ; pas de promesse d’exactitude poétique ou de disponibilité régionale. Les diagnostics déclarés de ML Kit sont distincts de la traduction locale.
- Admission/gratuité effective des comptes et licences de contenus demeurent séparées. TheAudioDB/Deezer non activés ; Last.fm n’est pas utilisé pour des portraits ; Genius/Discogs restent références de consultation, pas collecte de paroles/images.
- La galerie vise six aperçus distincts mais ne certifie ni le sujet, ni la date de prise de vue, ni six résultats utiles pour tout artiste. La déduplication visuelle est une heuristique examinable manuellement.
- Aucun essai sur le téléphone réel de l’utilisateur, ni couverture exhaustive API 26–35, formats réels, gros catalogues, casque, batterie constructeur ou accessibilité.
- Pas d’écriture dans les tags audio originaux, fusion d’albums, Fil Rouge ou playlists intelligentes opérationnelles. Listening Party et YouTube restent à concevoir.

[Guide API et clés, confidentialité et limites des connecteurs](04-api-et-cles.md). Les questions de conception restées ouvertes ne sont pas déclarées résolues par cette implémentation.
