# Next 0.3.1 — huit corrections

## Périmètre et livraison

Lot autorisé par **« J’adore tu peux coder les 8 correction mais attention aux erreurs »**. Les sources préparent **0.3.1 / code 7**. Ce document ne constitue pas une livraison d’APK : la publication signée Next 0.3.0 reste inchangée. Une livraison ultérieure devra employer l’identifiant `com.novalisten.app.next` et **la clé Next existante**, dont l’empreinte publique reste épinglée. Aucune nouvelle clé, aucune troisième application, aucune modification de NovaListen 0.2.3 ou de ses données.

## 1. Artistes et participations

Priorité : **corrections manuelles → crédits structurés des fichiers → résolution contextuelle sauvegardée → analyse prudente du texte**. Les crédits originaux ne sont jamais réécrits.

Les séparateurs explicites (`feat.`, champs multi-valeurs) sont conservés. Virgules et esperluettes peuvent réunir des participants déjà identifiés indépendamment dans la bibliothèque ; un seul nom connu ne suffit pas à découper un groupe inconnu. Les groupes ponctués et crédits natifs/manuels restent prioritaires.

Pour les cas encore collectifs comme `Anyma & LISA`, `Anitta, Shakira` ou `Ariana Grande & The Weeknd`, une recherche de **l’enregistrement**, pas un premier résultat de nom, vérifie le titre, la sortie, la durée et le crédit complet. Réponse non tronquée, ensemble complet des participants, identifiants cohérents et résultat non ambigu obligatoires. Un identifiant d’enregistrement natif est contrôlé contre la réponse reçue. Un vrai groupe peut ensuite être vérifié comme une entité entière si aucune collaboration ne correspond.

Les associations automatiques sont stockées séparément dans `catalog_choices`, liées au contexte du fichier (artiste, titre, album, durée, identifiant d’enregistrement). Un contexte changé les invalide ; une correction manuelle les masque toujours. La résolution ne distribue jamais la biographie ou la photo d’un crédit collectif à ses participants.

## 2. Index partagé et navigation

`MusicRepository.catalog` produit un `CatalogSnapshot` immutable sur `Dispatchers.Default`, indépendamment de l’interface. Il contient pistes, artistes, albums, associations piste/artistes, artiste/sorties, piste/album et album/artistes, ainsi que les compteurs d’éditions logiques. Il ne se reconstruit pas lors d’une mise à jour de position, d’un favori, d’une biographie ou d’un événement d’écoute sans changement de catalogue.

Les cartes ne croisent plus toute la bibliothèque pour calculer leur compteur de sorties. Les tris normalisent leurs clés une fois par élément ; recherche et tri des grilles sont mémorisés. La déduplication des albums utilise un index d’identifiants candidats plutôt qu’une comparaison exhaustive avec toute la liste déjà retenue.

La position/durée fréquente est séparée de `PlaybackUi` dans `PlaybackProgress`. Seuls le lecteur et la fine progression du mini-lecteur la collectent ; la racine et les catalogues ne la collectent pas. Les recherches, tris et défilements enregistrables possèdent des clés d’état distinctes par onglet et destination. Changer d’onglet ne réinitialise plus systématiquement une fiche ; retoucher l’onglet actif revient à son index.

**Limite de mesure :** les fixtures de 10 000 fichiers mesurent la construction synthétique de l’index sur le runner CI, pas le temps de changement d’onglet sur le téléphone. Sans profilage de ce téléphone, on ne certifie ni une cause unique des cinq secondes observées ni une latence réelle finale.

## 3. Fragments d’album, encodage et vraies éditions

`CatalogText` construit des alias d’affichage/index par décodage UTF-8 strict, uniquement si le score de corruption diminue, au plus deux fois. Les chaînes valides, non réparables ou ambiguës ne sont pas devinées. Cela ne réécrit ni tags, ni noms de fichiers, ni clés des anciennes pochettes.

Plusieurs identifiants Android ne prouvent pas plusieurs éditions. Des fragments de même titre réparé et même artiste d’album sont réunis lorsque leurs identifiants, type, année, disques/pistes et contenus sont compatibles. Des pistes intégralement numérotées et complémentaires peuvent confirmer des fragments ; sans numéros ou identifiants fiables, titre/année/pochette seuls restent insuffisants. Les contradictions communes de titre/version/durée et les releases natives distinctes restent des garde-fous. Les clusters exigent une compatibilité globale : un fragment de métadonnées inconnues ne peut pas relier transitivement deux éditions contradictoires.

- Une carte pour les fragments compatibles ; les sources restent inspectables et corrigeables à l’intérieur.
- Une édition logique n’est pas comptée quatre fois pour quatre lots Android. Les véritables éditions d’une famille restent distinguées.
- Les fragments de la même édition partagent l’ordre disque/piste global. Les vraies éditions ajoutent leurs pistes après l’original choisi.
- Les corrections d’origine, de source représentative, de regroupement et de séparation sont sauvegardées. Une nouvelle source compatible peut rejoindre une famille déjà corrigée sans réinitialiser sa représentante.
- Les fonctions de correction restent accessibles même lorsque plusieurs sources composent une seule édition logique.

**Regrouper visuellement n’est pas dédupliquer des fichiers.** Les variantes live/remix restent distinctes ; une identité d’enregistrement n’est admise que par identifiants cohérents ou choix explicite. Les fixtures de lots 15/4/2/1 vérifient aussi le cas de copies non prouvées : une seule carte, sans effacement arbitraire. Elles ne prouvent pas que les fichiers de la capture sont 22 enregistrements distincts ; leurs tags réels n’ont pas été fournis.

## 4. Biographies et portraits progressifs de bibliothèque

Un seul worker applicatif traite les artistes manquants lorsque l’application est visible ou qu’une lecture est active. **Priorité : artiste joué → cartes visibles → autres artistes**, y compris jamais écoutés. Le travail n’attend pas l’ouverture d’une fiche. Un travail de fond peut être interrompu pour une priorité plus élevée.

Identité : MBID natif du contexte sélectionné, association choisie ou concordance d’enregistrement local ; puis liens MusicBrainz/Wikidata croisés. Une identité d’un autre fichier homonyme n’est pas présentée comme une preuve native du fichier courant. Les pistes masquées, trop courtes ou de durée inconnue ne servent pas de contexte jouable.

La phase **biographie FR** peut être persistée avant le portrait. Une émission intermédiaire ne lance pas la cascade avant la vérification du portrait lié, et ne la lance pas deux fois. Les indisponibilités des deux champs sont indépendantes. Le portrait automatique exige toujours l’image Commons liée par Wikidata P18, une licence ouverte et une attribution exploitable ; aucun premier résultat de recherche élargie n’est imposé. Les propositions élargies restent manuelles. Anglais volontaire, aucune traduction automatique ajoutée.

Départ différé de 750 ms, délai maximal de 45 s par travail, cache de tentatives de 10 min et cache d’erreurs de 2 min, bornes mémoire et états de travail limités. Les caches expirés permettent une nouvelle tentative lors d’une nouvelle activation/mise à jour des priorités ou du catalogue, sans boucle de requêtes perpétuelle. La même instance HTTP conserve **deux requêtes simultanées au total**, images, paroles et recherches manuelles comprises, avec quotas/`Retry-After`.

La transaction relit sous mutex le contexte, le masquage, les choix et la fraîcheur de l’index. Les interrupteurs récents sont relus après l’attente du mutex. Les champs manuels, y compris volontairement vides, gardent leur priorité séparément. Les anciens profils individuels peuvent servir de graine après réparation d’un nom, mais pas les anciens profils collectifs. Une fiche déjà enregistrée et ses déverrouillages explicites restent prioritaires. Une image téléchargée mais non retenue est nettoyée. Le retour booléen de sauvegarde indique toujours **portrait utilisé**, pas « biographie enregistrée ».

### Confidentialité mise à jour

Les recherches artistes ne sont **plus limitées à l’écoute**. Selon les deux options indépendantes, noms/identifiants des artistes de la bibliothèque et titre/album/durée d’un morceau de contexte peuvent être transmis progressivement aux sources admissibles. Cela peut décrire progressivement une partie de la bibliothèque aux fournisseurs. Aucun audio, fichier, URI locale, historique ou liste complète en un seul envoi. Les paroles automatiques restent limitées au morceau écouté. Les écrans API et clés / Informations et aide et le README décrivent ce changement ; désactiver les deux options arrête ces recherches, pas les contenus locaux sauvegardés.

La cascade photo conserve ses rôles conditionnels. **Commons/Openverse restent les seules branches de collecte admises.** Genius, TheAudioDB, Discogs et fanart.tv ne deviennent pas admissibles par l’ajout d’une clé. Last.fm n’est pas rétabli comme collecteur. Pas de garantie de six portraits ni de reconnaissance faciale.

## 5–8. Lecteur et interface

5. Les actions Favori / Playlist / Paroles / Édition sont ancrées juste au-dessus de la progression, pas au milieu d’un bloc centré. Le transport reste réservé et mesuré avant le contenu, avec insets système + **20 dp bas / 16 dp côtés**, cibles d’au moins **48 dp**.
6. Le mode immersif ne répète plus titre et artiste au centre ; l’en-tête les conserve. Toucher le fond révèle toujours les commandes, les gestes existants restent disponibles.
7. Paroles brutes et synchronisées sont centrées horizontalement. La pochette assombrie et floutée est un fond commun aux pages pochette/paroles. Le flou logiciel est petit, borné et mis en cache par Coil, y compris Android 26–30 où `Modifier.blur` n’agit pas ; la grande pochette n’est jamais mutée. Suivi vertical, recentrage, clic pour seek, persistance des paroles, vrais temps natifs et remplissage karaoké sont conservés.
8. Les notices techniques récurrentes sont retirées de l’accueil, des grilles et des zones de lecture. **Réglages → Informations et aide** rassemble limites, fonctionnement, gestes et confidentialité. Les crédits/source/licence restent accessibles dans les fiches et Informations, les avertissements destructifs restent au moment de l’action. Les fonctions absentes ne sont pas simulées.

## Vérifications

La CI exécute le domaine et SQLite sous JVM/Robolectric, des mesures/rendus Compose natifs sous Android simulés **API 28 et 35**, compile également les tests Android instrumentés et exécute lint. Les tests de rendu contrôlent petites fenêtres, police agrandie, paysage simulé, cibles/insets, position des actions, absence de doublon immersif, alignements des paroles, seek et pixels karaoké. Le flou est testé sur de vrais bitmaps, sans mutation de la source.

### Résultats vérifiés

**[Validation Next 45389015009](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/45389015009)**, job `138427459407`, code applicatif **`f5fa872fdb9bfcb4d245ddfa51bdc770dddff235`** : **réussite**. Commande CI : `NOVALISTEN_VERIFY_SOURCES=true scripts/gradle-ci.sh -PnovalistenParallel=true testDebugUnitTest compileDebugAndroidTestKotlin lintDebug`.

| Contrôle | Résultat |
| --- | --- |
| JVM / Robolectric, 22 suites | **288 réussis**, zéro échec, erreur ou test ignoré |
| Dont nouveaux rendus Compose / bitmaps | **12 + 4 réussis**, chacun exécuté sous SDK 28 et 35 |
| Catalogue existant / nouvelles corrections / stockage | **29 / 14 / 9 réussis** |
| Artistes bibliothèque / crédits contextuels / lookup | **10 / 6 / 16 réussis** |
| Anciens automatismes artistes / cascade photo | **17 / 13 réussis**, non supprimés ni assouplis |
| Politiques de signature Python | **49 réussis**, en local et dans la CI, sans nouvelle clé |
| Kotlin application Next + tests Android instrumentés | Compilation réussie ; **pas d’exécution instrumentée** |
| Lint Next | **43 avertissements, zéro erreur ou fatal** |
| Intégrité du projet, XML, ressources et droits réseau | Réussite |
| Assemblage en mode sources / ancien identifiant sans clé | Refus contrôlés vérifiés |
| APK/AAB et keystore implicite | Absence contrôlée ; aucun binaire installable produit |

**Mesure synthétique observée :** `catalog-index fixture=10000 files elapsed_ms=963`. Elle couvre la construction de l’index dans cette fixture (100 artistes, 1 000 sorties, filtrage inclus), pas l’indexation MediaStore des fichiers réels, une latence d’onglet sur téléphone ou une promesse de performance universelle.

Rapports XML/lint et journal récupérés par l’API et hachages contrôlés dans `artifacts/ci-45389015009/` (ignoré par Git). Archive : SHA-256 **`47c494c54dbcfa2bab9242f34b9f96d3999b88ae574c1dc1e5f932273fee3eb6`**, blob public `6e049f76005ff983e41b28d11e42f63b30483432`. Journal : SHA-256 `597abab4dd219bc13dc244b497d3ad5c341d55d5420cc0faf9f6457f0a5274e8`, blob `e349f5ffa670e6bb11c0da5d4fc572bef6002400`.

Les essais ont notamment révélé un mauvais choix de copie originale après préférence excessive pour la source la plus complète : l’algorithme a été corrigé **sans affaiblir le test existant**. Le téléchargement de frameworks Robolectric a reçu des réponses Maven HTTP 429 ; le miroir officiel en lecture seule de Maven Central a permis la reprise complète. Le test de pixels sous Robolectric dessine le propriétaire Compose mesuré sur un Canvas logiciel natif (pas de vraie surface Window/PixelCopy dans ce moteur) ; le test instrumenté conserve sa capture sur surface pour un futur essai sur Android complet.

**Ces rendus Robolectric ne sont pas des essais sur émulateur Android complet ni sur le téléphone.** Pas d’appel réel à une API musicale, pas de musique ou image de l’utilisateur dans les fixtures. Les anciens essais signés/coexistence 0.3.0 ne sont pas attribués à 0.3.1. Aucun APK 0.3.1 n’a été signé ou publié dans ce lot.
