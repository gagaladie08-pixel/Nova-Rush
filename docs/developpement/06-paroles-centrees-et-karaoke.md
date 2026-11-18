# NovaListen 0.2.1 — paroles centrées, chargement et karaoké

## Demande traitée

L’écran paroles de 0.2 cumulait plusieurs rangées de commandes. Sa liste plaçait la ligne active en haut, le chargement était seulement manuel et le modèle de données ne retenait aucun temps de mot. Les spans TTML et les mots normalisés de LiriQo étaient aplatis en simples lignes LRC : colorer la ligne entière n’était donc pas un rendu karaoké.

### Interface

- Retrait de la rangée « ← Pochette », de son libellé et de l’indicateur textuel inférieur redondant. Le retour Android et le glissement vers l’illustration restent disponibles ; réduire le lecteur reste possible dans l’en-tête.
- **Trois points → Rechercher d’autres paroles**, **Traduire les paroles**, **Modifier / importer**, **Source et synchronisation**. La recherche de remplacement prévisualise les résultats avant choix. Traduire n’est actif que si une VO existe, y compris une VO chargée automatiquement.
- Seuls le crédit de source, le passage VO/FR lorsqu’une VF existe et l’attribution Google obligatoire restent à proximité du texte. Pas de commande de traduction permanente dans la page.
- La ligne active est centrée dans la **zone de paroles disponible**, selon la hauteur réellement mesurée de l’item et du viewport. Le padding permet aussi de centrer la première et la dernière ligne. Les lignes longues, les déplacements de lecture et les changements de hauteur sont pris en compte.
- Le défilement manuel suspend le suivi. Un petit bouton de recentrage apparaît seulement alors ; toucher une ligne déplace la lecture à son instant et réactive le suivi.

## Chargement automatique borné au morceau écouté

Activé par défaut conformément à la demande ; désactivable dans **Réglages → API et clés → Charger les paroles pendant l’écoute**. Cette préférence ne modifie aucun accord de traduction ou autre parcours API.

Le service Media3 pilote le chargement, même si la page paroles n’est pas ouverte. Préparer une file sans lancer la lecture, ouvrir une page, lancer simplement l’application ou enregistrer une clé ne constitue pas un départ de recherche. Le chargement attend la première photographie SQLite : une sauvegarde locale n’est pas confondue avec une absence pendant son initialisation.

1. Une **VO déjà sauvegardée a toujours priorité**, avec sa VF et ses sources. Ni les paroles choisies/importées ni une sauvegarde arrivée pendant la recherche ne sont écrasées.
2. Sans VO locale, seul le morceau réellement écouté est candidat. Une stabilisation de 500 ms permet d’annuler les changements très rapides avant l’envoi. Changer de morceau annule les requêtes précédentes ; un numéro de génération écarte leurs éventuelles réponses tardives.
3. Noyau **lrc.red + LRCLIB + LiriQo**, avec au plus deux opérations simultanées et le limiteur HTTP partagé. lrc.red est sauté si aucun courriel de contact valable n’est configuré ; aucun courriel de projet n’est inventé. Les deux autres branches restent utilisables.
4. Pour le choix automatique, titre et artiste normalisés doivent correspondre et la durée retournée doit être connue, à ±3 secondes de la durée locale. Les métadonnées remplies seulement pour présenter un aperçu manuel ne servent pas de preuve de correspondance. Un artiste inconnu ou une durée locale inconnue ne déclenche pas de choix automatique. **Ce contrôle n’est pas une empreinte audio et ne certifie pas la version.**
5. Une seule proposition correspondante par source est chargée. Les temps de segments exploitables sont préférés aux lignes seules, elles-mêmes préférées au texte ; une réponse moins détaillée ne rétrograde pas un résultat déjà reçu. Ce choix d’implémentation ne remplace pas les questions de conception restées ouvertes et ne réduit pas le noyau à LRCLIB.
6. Les propositions automatiques sont **temporaires en mémoire**, pas écrites dans SQLite : huit entrées au maximum et budget de trois millions de caractères pour les textes/minutages. Validité maximale d’une heure pour un succès, dix minutes pour une absence, deux minutes pour une erreur. Pas de boucle de polling ni de répétition immédiate après un quota. Une nouvelle action de lecture peut réévaluer une entrée expirée ; la recherche manuelle reste disponible.

Les seuls champs de recherche transmis sont titre, artiste, album et durée du morceau en cours. **Aucun audio, historique, URI locale ou envoi de la collection. Aucun pack ni aucune traduction ne démarre automatiquement.** Le nom de fichier n’est pas un champ de requête distinct ; le titre affiché peut cependant provenir du nom du fichier lorsque le tag de titre manque. Une proposition erronée reste remplaçable depuis les trois points. Sauvegarder volontairement dans l’éditeur ou choisir un résultat manuel permet de conserver les paroles hors ligne, après vérification de leur version et des droits.

Better Lyrics public, Textyl, lyrics.ovh, Web volontaire et collage restent des compléments manuels ; aucun accès protégé ou payant n’est contourné. La disponibilité réelle des services n’est pas garantie par les tests synthétiques.

## Temps natifs et rendu karaoké

- `LyricLine` peut porter des segments avec début, fin facultative et offsets UTF-16 exacts dans le texte. Les temps doivent être cohérents et le texte des segments doit se retrouver exactement dans la ligne ; sinon les mots sont abandonnés sans perdre le texte de la ligne.
- TTML : conservation des spans minutés exploitables et de leurs fins/durées explicites, plutôt qu’aplatissement en LRC uniquement. Le sous-ensemble des horloges absolues courant dans les fichiers musicaux est pris en charge, pas un moteur TTML complet. Les conteneurs séquentiels/syncbases non interprétables ne deviennent pas de faux temps absolus. Les déclarations externes XML restent interdites.
- lrc.red : préfère le TTML annoncé au LRC lorsqu’il existe ; les chemins restent reconstruits depuis l’ISRC validé, jamais suivis depuis une URL arbitraire de réponse.
- LiriQo : conserve les millisecondes normalisées des segments, leurs pauses et fins réelles ; formats imbriqués et tableaux par identifiant de ligne. Les déclarations explicitement non natives et l’ancien `lrclib_wordsync` proportionnel ne deviennent pas du karaoké. Une fin marquée `derivedEnd` n’est pas animée comme une fin native. La durée de métadonnées en secondes ou `mm:ss` est distinguée des millisecondes des mots.
- Enhanced LRC : les balises `<mm:ss.xxx>` sont retirées du texte visible et leurs débuts sont conservés. Sans fin native, le segment change de couleur à son début ; sa durée n’est pas déduite de la ligne ou du mot suivant.
- Un segment avec début **et fin** reçoit un remplissage progressif selon cette fenêtre réelle. Le rendu gère le retour de lecture, les segments enveloppés sur plusieurs lignes et le sens RTL de la ligne. L’interpolation entre échantillons du lecteur est bornée ; elle n’invente pas les temps de segments.
- **Un LRC par ligne reste un LRC par ligne** : ligne active accentuée et centrée, sans découpage proportionnel présenté comme du mot-à-mot. Le menu Source et synchronisation explique le niveau réellement disponible. Les segments peuvent être des syllabes ou des groupes de mots, pas forcément des mots linguistiques.

### Conservation et traduction

SQLite passe à la **version 3** en ajoutant un champ de minutage à la VO. Les migrations v1 → v3 et v2 → v3 conservent textes, VF, sources et autres tables. Le minutage supplémentaire est borné, validé et lié au SHA-256 du texte original. Un texte modifié ou un payload corrompu invalide seulement ce supplément ; le LRC par ligne reste lisible.

Les temps des mots ne sont pas copiés sur les mots français : le plan de traduction retire les balises Enhanced LRC, conserve les temps de **ligne** et les métadonnées utiles. Traduire une VO temporaire est possible sur demande ; conserver la VF conserve explicitement sa VO et sa source aussi. Une VO locale modifiée pendant l’opération bloque la sauvegarde. L’attribution officielle Google et les confirmations ML Kit/Azure restent inchangées.

## Vérification et livraison

**[Compilation et tests réussis — exécution 44292086637](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44292086637)**, job/check `134759576591`, sources `bcdd86f7da19086d85fb8aede3bd7cf028c564be` de la branche de session. Les rapports XML ont été récupérés et relus : **127 tests unitaires + 15 tests Android 10, zéro échec, erreur ou test ignoré**. Lint : **0 erreur / 0 fatal / 32 avertissements**, sans baseline ajoutée. Une première exécution de la correction (`44290728828`, commit `de13ef4`) avait déjà réussi 123 + 15 tests ; elle n’est pas le binaire livré.

APK final `NovaListen-0.2.1-debug.apk` : **89 526 523 octets**, SHA-256 `22187621c86aac3732d19b791a3a407babb9b179a076c14a4984a7db5a1a3e2d`. Les cinq fragments ont été vérifiés puis réassemblés sans recompilation ni nouvelle signature. Archive ZIP, package `com.novalisten.app`, version 0.2.1 / code 3, minSdk 26, cible 35 et signature v2 inspectés. Manifeste : Internet/HTTPS, backup désactivé, ni microphone/localisation ni provider d’initialisation ML Kit. Quatre ABI, 26 fichiers de polices, zéro fichier audio de démonstration ; badge officiel Google inchangé.

Preuves locales hors Git : `artifacts/APK-INFO-0.2.1.json`, `artifacts/NovaListen-0.2.1-SHA256SUMS.txt` et `artifacts/ci-44292086637/` (rapports, journaux, manifeste de fragments et sept captures synthétiques). Les tests de pixels vérifient réellement le changement de couleur avant/milieu/fin d’un mot natif et sa remise à zéro après retour arrière ; la capture 07 montre la ligne active centrée dans le lecteur complet. La distribution HTTPS de ce même binaire est en cours, sans modifier les releases précédentes.

Tests ajoutés : annulation/génération, absence de réseau avant lecture ou avant le snapshot local, priorité aux sauvegardes, budget de deux sources, cache sans polling, refus des correspondances imprudentes, TTML/LiriQo/Enhanced LRC, payloads invalides, traduction sans faux mots français, migration SQLite v2, centrage réel des premières/dernières lignes et lignes longues, viewport redimensionné, défilement/recentrage/seek, et progression des pixels du karaoké dans une fenêtre native synthétique.

Les tests utilisent des textes, images et sons synthétiques, sans clé réelle ni appel musical réel, sans téléchargement de pack. Ils ne prouvent pas la justesse des paroles d’un artiste, les droits, tous les formats TTML, la disponibilité des services ou le fonctionnement sur un téléphone physique.

**Installation : la signature 0.2.1 diffère effectivement de 0.2.0.** Certificat SHA-256 `aa54571fed590c5ffef3022546cee17708c714b39c753548c9a4ddd519b7962c`, contre `ee8c88dff11b6550d320f2d35412937424a487580cb2759a123c3e8b0f4cd18b` pour 0.2.0. Android refuse normalement leur mise à jour directe. Le binaire 0.2.0 publié n’est pas remplacé ; la clé debug des runners n’est pas une clé stable de publication. Ne pas désinstaller aveuglément : cela efface favoris, playlists, historique, paroles, portraits, clés et réglages privés, sans export intégré ; les fichiers musicaux partagés sont distincts et restent sur le téléphone.
