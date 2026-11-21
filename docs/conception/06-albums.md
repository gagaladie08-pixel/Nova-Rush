# NovaListen — Conception de l’onglet Albums

> **Implémentation ultérieure autorisée :** les sources 0.3.0 implémentent le regroupement local d’éditions, dont les deux relations explicitement demandées, la liste unique avec preuves/corrections d’enregistrement, la classification et la navigation communes. Voir le [périmètre, les limites et les vérifications](../developpement/10-catalogue-et-artistes-automatiques.md). Le statut et les questions ci-dessous restent la trace de conception, pas une nouvelle demande de validation des acquis.

> **Statut :** conception partiellement validée, sans implémentation. Sont confirmés : **grille à deux colonnes**, contenu **Albums, EP et Mixtapes**, **regroupement des différentes versions d’un album en une seule carte**, et **liste unique avec une seule occurrence du même enregistrement**, en gardant les véritables variantes distinctes et tous les fichiers d’origine. Les règles transversales et les gestes déjà acquis sont repris en section 1. L’ordre commun est confirmé : **pistes de l’édition d’origine d’abord, puis titres supplémentaires des autres éditions**, dans la même liste. Le groupe utilise **le nom et la pochette de l’édition d’origine par défaut**, avec possibilité de choisir une autre édition représentative parmi celles regroupées localement, sans réécrire les fichiers. La disposition de la page d’album et l’accès à la page de l’artiste sont également validés en section 4. Le menu ⋮ de l’album, ses cinq actions et la portée de Lire ensuite / Ajouter à une playlist sur la liste unique locale disponible non masquée sont confirmés en section 5. Les règles de lecture depuis l’album sont validées en section 4 : liste unique locale disponible non masquée, ordre retenu ou aléatoire, titre touché actif et remplacement de la file. La confirmation éventuelle avant remplacement, l’ordre relatif des ajouts, l’identification des enregistrements, le choix du fichier lu et les autres détails de présentation restent à préciser.
> **Périmètre :** collection locale du téléphone, utilisable hors ligne. Aucun catalogue distant, téléchargement de pistes manquantes ou service de streaming n’est ajouté par cette fiche. Le choix Albums / EP / Mixtapes concerne l’index de cet onglet ; il ne supprime pas les fichiers ni les rubriques Singles et Participations de la page d’artiste.
> **Habillage transversal :** le [système de quinze univers, plus l’option de thème personnalisé](21-systeme-de-themes-visuels.md), définit l’habillage global. Les formes/rayons des cartes suivent le thème plutôt qu’un arrondi unique ; le format carré des pochettes et la structure de grille restent acquis, avec masques et détails de composants à préciser. Ni fichiers, regroupements, choix de pochette représentative, ordre des pistes ou gestes ne changent avec le thème.

## 1. Règles déjà acquises

- **Durée minimale locale — réponse 57 :** les fichiers de durée connue inférieure à 60 secondes n’entrent pas dans les titres, compteurs, listes uniques admissibles ou nouvelles lectures. 60 secondes exactement reste admis. Les fichiers d’origine restent intacts ; ce filtre ne fusionne ni ne supprime de variante. Les durées inconnues sont signalées en attente dans la Bibliothèque, hors compteurs disponibles et sans lecture avant identification ; elles ne sont pas des titres jouables. L’identification fiable et la présentation d’une sortie sans titre admissible restent ouvertes.

- **Aller dans l’album** depuis le menu d’un morceau sélectionne l’onglet Albums et ouvre la page de son album ; ce n’est pas un filtre de la Bibliothèque. Voir la [fiche Bibliothèque](02-bibliotheque.md).
- Depuis les rubriques de la page d’artiste, toucher une carte de sortie ouvre sa page dans l’onglet Albums, sans lancer de musique. Le petit bouton de lecture est distinct de cette consultation. Voir la [fiche Artistes](05-artistes.md).
- Les gestes s’appliquent **aussi aux cartes d’albums** : droite → **Lire ensuite**, gauche → **Informations**, appui long → **sélection multiple de cartes**, pas ouverture du menu.
- Lire ensuite sur une carte concerne les morceaux locaux admissibles associés à la sortie ou au groupe d’éditions. L’ordre d’insertion, le traitement d’un morceau déjà présent dans la file et le cas sans titre actif restent à préciser. La liste commune aux éditions n’affiche qu’une occurrence du même enregistrement, selon le choix validé en section 6 ; cette règle d’affichage ne tranche pas les répétitions dans une file existante.
- Le **masquage global** exclut les titres masqués des vues normales, résultats et lectures lancées ici. Il ne supprime ni ne déplace les fichiers. Le sort visuel d’un album dont tous les titres sont masqués reste ouvert.
- Consulter un album ne doit pas implicitement interrompre la musique ou remplacer la file d’attente.
- Le [mini-player](03-player-et-mini-player.md) reste disponible pendant la navigation selon ses règles déjà validées.
- Les pochettes et métadonnées retenues doivent être conservées pour rester disponibles hors ligne, conformément au principe de sauvegarde décrit dans la [fiche Paroles](04-paroles.md). Une pochette d’album n’est pas une photo d’artiste.
- **Décisions propres à cet onglet :** deux cartes par ligne ; index consacré aux albums, EP et mixtapes ; différentes versions d’un album regroupées ; liste unique sans répétition du même enregistrement, avec titres supplémentaires et véritables variantes distinctes ; pistes de l’édition d’origine dans leur ordre, puis ajouts des autres éditions. Les sections 2, 3 et 6 distinguent ces décisions de leurs modalités encore ouvertes.
- **Identité du groupe validée :** nom et pochette de l’édition d’origine par défaut ; choix possible d’une autre édition représentative parmi les éditions regroupées. Cela ne modifie pas les métadonnées des fichiers d’origine.
- **Disposition de la page d’album validée :** grande pochette, titre puis artiste(s) avec accès à leur page, année connue / nombre de titres locaux / durée, commandes Tout lire / Aléatoire / ⋮, puis liste unique ; le mini-player reste disponible selon ses règles habituelles.
- **Menu ⋮ de l’album validé :** Lire ensuite, Ajouter à une playlist, Informations sur l’album, Aller dans l’artiste et Choisir l’édition représentative. Les deux actions musicales portent sur la liste unique locale disponible et non masquée ; l’action Choisir l’édition représentative est disponible lorsqu’il y a plusieurs éditions regroupées.
- **Lecture depuis l’album validée :** Tout lire et le petit bouton Lecture de la carte lancent la liste unique dans l’ordre retenu ; Aléatoire utilise les mêmes titres mélangés sans changer l’affichage ; toucher un titre le rend actif dans la file de cet album. Ces lancements remplacent la file actuelle. La confirmation éventuelle avant remplacement reste ouverte.

## 2. Écran principal — présentation partiellement validée

### En-tête proposé

- Titre **Albums** à gauche.
- Nombre d’albums, EP et mixtapes affichables, après regroupement des versions, sous le titre.
- Icônes **Recherche** et **Tri** à droite.
- Proposition : la loupe déploie un champ de recherche compact, sur le modèle de la Bibliothèque.
- Rechercher par nom d’album ou artiste associé, sans lancer de lecture et sans faire apparaître de morceau masqué.

### Présentation validée

- **Grille à deux cartes par ligne**, à défilement vertical.
- L’utilisateur a retenu cette recommandation ; l’alternative en liste compacte n’est pas retenue pour cet index.
- Les différentes éditions regroupées d’un même album n’occupent pas chacune une carte distincte.

Compléments visuels proposés :

- Pochettes **carrées aux coins arrondis**, pas portraits ronds.
- Réutiliser l’esprit des cartes de sorties de la page artiste.
- Les dimensions, espacements et détails graphiques restent à choisir.

La [capture de référence](references/artiste-discographie.jpg) concerne les cartes de la page artiste. Le réemploi de leur contenu et de leur style dans cet index reste proposé ; le choix de la grille à deux colonnes, lui, est confirmé.

### Contenu proposé d’une carte

- Pochette de l’édition représentative dans la partie haute, avec un petit bouton de lecture distinct. Le choix de la pochette du groupe est validé en section 6 ; sa disposition dans la carte reste proposée ici.
- Titre de l’album ou du groupe d’éditions, selon l’édition représentative choisie, puis artiste ou artistes associés.
- Nombre de titres locaux disponibles non masqués et durée correspondante. Si ces compteurs sont affichés, ils décrivent la **liste unique** : ne pas additionner toutes les copies d’un même enregistrement entre éditions. Le calcul exact de la durée dépendra notamment du choix du fichier représentatif, encore ouvert.
- Badge de date ou d’année lorsqu’une métadonnée pertinente existe. La date de sortie musicale est proposée, mais sa signification n’est pas encore arrêtée ; ne pas la confondre avec la date d’ajout. La date représentative d’un groupe d’éditions reste à choisir.
- Si aucune pochette n’est disponible, utiliser un visuel neutre ; ne pas transformer arbitrairement un portrait d’artiste en pochette certaine de l’album.
- Rendre les noms complets consultables dans le détail lorsqu’ils sont tronqués sur la carte.

Les interactions déjà définies des cartes restent valables dans la grille. Le mécanisme précis des gestes reste à concevoir, notamment près des bords réservés aux gestes Android. Dans les carrousels de la page artiste, la distinction entre défilement horizontal et swipe d’action demeure un point ouvert ; une grille verticale ici ne résout pas ce conflit ailleurs.

## 3. Contenu validé, recherche et tris proposés

### Types retenus pour l’index

- **Albums**.
- **EP**.
- **Mixtapes**.

La proposition précédente d’ajouter Singles et Compilations à cet index n’est pas retenue. Les morceaux correspondants ne sont pas supprimés de la bibliothèque. Les raccourcis déjà définis vers la page d’une sortie depuis un morceau ou la page d’artiste restent valables, même si cette sortie n’est pas une carte du présent index.

### Organisation et tris encore proposés

- Proposition : filtres **Tous / Albums / EP / Mixtapes**. La présence de ces filtres et leur disposition ne sont pas encore choisies ; les trois types de contenus, eux, sont acquis.
- Identifier les types à partir de métadonnées suffisamment fiables. Un album dont un seul titre est présent ne devient pas automatiquement un single ; le traitement d’un type inconnu reste à définir.
- Pour un groupe réunissant des éditions classées différemment, préciser le type représentatif sans empêcher les regroupements explicitement demandés en section 6.
- Proposition : rechercher aussi dans les noms des éditions regroupées, pour retrouver un album à partir du nom d’une réédition.
- **Tri initial proposé : titre A → Z**.
- Autres tris possibles : artiste d’album, date de sortie connue, dernier ajout local, nombre de titres ou durée locale.
- Ordre inversable et mémorisation du dernier tri proposés.
- La définition du dernier ajout d’un album, les égalités et les valeurs manquantes restent à préciser.
- Une recherche ou un changement de tri ne modifie pas la musique ni la file en cours. Les compteurs décriraient les résultats actifs.
- Pas de commande Tout lire globale sur l’index dans cette proposition : la lecture principale se fait par album. Ce choix reste à valider.

## 4. Page d’un album — disposition validée, détails à préciser

Ordre d’affichage validé par l’utilisateur :

1. **Grande pochette** de l’édition représentative.
2. **Titre de l’album ou du groupe d’éditions**, selon l’édition représentative choisie.
3. **Artiste ou artistes** : toucher un artiste ouvre sa page dans l’onglet Artistes.
4. **Année lorsqu’elle est connue · nombre de titres locaux disponibles non masqués · durée totale correspondante**. La date représentative d’un groupe d’éditions reste à préciser ; sa source n’est pas fixée par la validation de cette disposition.
5. Commandes **Tout lire · Aléatoire · ⋮**.
6. **Liste unique des morceaux locaux** des éditions regroupées, avec titres supplémentaires et véritables variantes distinctes, selon le choix validé en section 6.

Le mini-player reste disponible en bas selon ses règles habituelles. La disposition, la présence des commandes, la navigation vers l’artiste, les règles de lecture ci-dessous et le menu décrit en section 5 sont validés. Le détail des lignes et la confirmation éventuelle avant remplacement de la file restent à préciser.

### Liste des titres — principe et ordre validés, présentation proposée

- **Ordre validé : pistes de l’édition d’origine dans leur ordre, puis titres supplémentaires des autres éditions**, à la suite dans la même liste unique.
- Les numéros de disque puis de piste, lorsqu’ils sont fiables, peuvent aider à retrouver l’ordre de l’édition d’origine. Ne pas entrelacer automatiquement les numéros identiques de plusieurs éditions.
- L’ordre relatif des titres supplémentaires et le cas où l’édition d’origine est inconnue ou absente localement restent à préciser, sans créer de pistes absentes du téléphone.
- Présenter les albums multidisques avec des séparations par disque si elles sont utiles.
- Champs proposés sur chaque ligne : **titre, artiste du morceau, durée, Favori et menu ⋮**.
- **À gauche, présentation encore à choisir :** petite pochette comme dans la Bibliothèque, ou numéro de piste pour une ligne plus compacte. La grande pochette du haut ne tranche pas ce détail.
- Ce choix est laissé en attente lors du passage à Playlists : ni la petite pochette ni le numéro de piste n’ont été explicitement sélectionnés.
- Proposer sur ces lignes les gestes déjà employés dans la Bibliothèque : droite Lire ensuite, gauche Informations, appui long de sélection multiple de morceaux. Il s’agit ici de la proposition pour les lignes de cette nouvelle page ; les gestes des cartes, eux, sont déjà acquis.
- Réutiliser le menu individuel du morceau défini dans la Bibliothèque, sans transformer ces actions individuelles en modifications de tout l’album.

### Lecture et constitution de la file — validées

L’utilisateur a validé les règles suivantes :

- **Tout lire** et le **petit bouton Lecture de la carte** lancent les titres de la **liste unique locale disponible et non masquée**, dans l’ordre retenu : édition d’origine, puis titres supplémentaires.
- **Aléatoire** utilise le même ensemble de morceaux, mélangé, sans réordonner visuellement la liste.
- **Toucher un titre** lance ce morceau et utilise la liste unique locale admissible de l’album comme file, avec ce titre comme **position active**.
- **Ces lancements remplacent la file actuelle.** L’inclusion de la liste entière ne signifie pas que les pistes précédant le titre actif sont immédiatement rejouées.
- Aucun titre absent du téléphone, indisponible ou masqué n’est inclus.

Points encore à préciser :

- **Confirmation éventuelle avant remplacement de la file**, à coordonner avec les autres écrans ; aucune décision avec ou sans confirmation n’est encore prise.
- Si des numéros manquent ou sont contradictoires, définir le départage sans inventer un ordre officiel.

Le tri de l’index Albums reste distinct de l’ordre des pistes à l’intérieur d’un album. La validation de ces règles pour Albums ne clôt pas implicitement les arbitrages de lecture encore ouverts dans la page Artistes.

## 5. Menu validé et sélection multiple à préciser

### Menu ⋮ de la page d’album — validé

L’utilisateur a validé les cinq actions suivantes :

1. **Lire ensuite** — insérer les morceaux locaux admissibles de l’album ou du groupe d’éditions après le titre en cours.
2. **Ajouter à une playlist** — choisir une ou plusieurs classiques existantes ou en créer rapidement une avec ces morceaux, selon le [sélecteur désormais validé](07-playlists.md) ; nouveaux titres au début dans leur ordre, ajouts déjà présents ignorés séparément pour chaque destination. Les intelligentes ne sont pas des destinations d’ajout manuel ; la portée de la liste unique de l’album reste inchangée.
3. **Informations sur l’album** — consulter les métadonnées disponibles ; le contenu exact, la présentation des éditions d’origine et la forme de cette vue restent à définir.
4. **Aller dans l’artiste** — ouvrir sa page dans l’onglet Artistes ; le parcours exact lorsqu’il y a plusieurs artistes réellement identifiés reste à préciser.
5. **Choisir l’édition représentative** — lorsqu’il y a plusieurs éditions locales regroupées, choisir celle dont le nom et la pochette représentent le groupe dans NovaListen. L’accès à ce choix depuis le menu ⋮ est validé ; le détail de l’interface de sélection reste à définir.

**Portée validée :** Lire ensuite et Ajouter à une playlist utilisent **la liste unique des morceaux locaux disponibles et non masqués** de l’album ou du groupe d’éditions, pas toutes les copies de chaque enregistrement.

La validation du menu ne décide pas des actions de sélection multiple de cartes. Le comportement des commandes de lecture est désormais validé séparément en section 4. L’import d’une nouvelle pochette ou l’édition en lot des métadonnées ne sont pas ajoutés implicitement : il faudrait définir les fichiers concernés, les autorisations et les effets d’une écriture dans les originaux. Choisir une édition représentative reste un choix d’affichage du groupe, sans réécriture des fichiers.

### Sélection multiple de cartes

- L’appui long pour sélectionner plusieurs cartes est déjà demandé.
- Propositions pour la barre d’actions : **Lire ensuite** et **Ajouter à une playlist**, sur les titres locaux admissibles des cartes sélectionnées.
- L’ordre des albums et de leurs pistes lors d’une action en lot, les éventuels doublons entre différentes cartes ou par rapport à la file existante, et les commandes de sélection restent à préciser.
- Ne pas importer automatiquement **Cacher** et **Effacer** depuis la sélection de morceaux. Aucune suppression de tous les fichiers d’un album n’est décidée par le seul fait de permettre la sélection de sa carte.
- Les décisions existantes sur Cacher et Effacer restent valables lorsqu’elles sont appliquées à des morceaux : masquage global réversible, ou suppression réelle du téléphone avec confirmation.

## 6. Fusion des versions — principe validé, modalités ouvertes

### Regroupements demandés

Les différentes versions d’un album sont **fusionnées dans la présentation de NovaListen : une seule carte commune**, plutôt que plusieurs albums séparés pour chaque édition. Le regroupement concerne les éditions et fichiers présents localement, sans récupérer les pistes d’une édition absente.

Exemples explicitement donnés par l’utilisateur :

| Versions locales à regrouper | Résultat demandé |
| --- | --- |
| **The Fame** et **The Fame Monster** | Une seule carte commune. |
| **Future Nostalgia** et **Future Nostalgia — Moonlight Edition** | Une seule carte commune. |

Ces exemples expriment les regroupements voulus par l’utilisateur : ne pas les refuser au motif que les titres, types ou classifications d’une source peuvent différer. Ils montrent aussi qu’une règle limitée au retrait d’un suffixe comme Deluxe serait insuffisante. Le nom et la pochette de la carte commune suivent l’édition d’origine par défaut, ou l’autre édition représentative choisie, selon la règle validée ci-dessous.

Cette fusion est un **regroupement dans l’application**, pas une concaténation du son : elle ne supprime, ne déplace, ne renomme ni ne réécrit les fichiers audio. Les capacités d’édition volontaire d’un fichier restent distinctes.

### Liste unique et pistes communes — choix validé

L’utilisateur a choisi **l’option 1 : une liste unique**, et non des sections séparées par édition.

- Le **même enregistrement n’apparaît qu’une seule fois** dans cette liste, même s’il figure dans plusieurs éditions regroupées.
- Inclure les **titres supplémentaires** présents localement dans les autres éditions.
- Garder distinctes les **véritables variantes** — remix, live, acoustique ou autre interprétation — plutôt que les supprimer au motif d’un titre similaire.
- **Conserver tous les fichiers d’origine**. La déduplication de l’affichage ne supprime ni ne réécrit les copies locales.

Ce choix fixe la présentation commune ; il ne choisit pas automatiquement une méthode de reconnaissance des enregistrements ni le fichier audio préféré lorsqu’il existe plusieurs copies.

### Ordre commun — validé

L’utilisateur a validé cet ordre dans la même liste unique :

1. **Les pistes de l’édition d’origine, dans leur ordre.**
2. **Puis les titres supplémentaires des autres éditions, à la suite.**

Cette validation ne fixe pas encore l’ordre relatif de plusieurs ajouts provenant de différentes éditions, ni le comportement lorsque l’édition d’origine n’est pas connue ou présente localement.

### Nom et pochette du groupe — validés

- Utiliser **le nom et la pochette de l’édition d’origine par défaut**.
- Permettre de **choisir une autre édition représentative parmi celles regroupées localement** ; son nom et sa pochette représentent alors le groupe dans NovaListen.
- Ce choix concerne la **présentation du groupe dans l’application** : ne pas réécrire automatiquement les tags ou pochettes de tous les fichiers. L’édition volontaire d’un fichier reste une action distincte.
- L’accès à ce choix depuis le **menu ⋮ est validé** en section 5 lorsqu’il y a plusieurs éditions regroupées. Ce choix ne fixe pas la date ni le type représentatifs du groupe, et ne désigne pas non plus le fichier audio à lire pour chaque piste commune.

### Modalités encore ouvertes

- Ordre relatif des titres supplémentaires et cas de l’édition d’origine inconnue ou absente localement.
- Méthode pour identifier un même enregistrement et fichier à lire lorsqu’il en existe plusieurs. Un titre identique ne suffit pas à conclure à l’identité audio ; aucune fiabilité parfaite n’est promise.
- Date et type représentatifs du groupe ; détail de l’interface permettant de choisir son édition représentative. Le principe du choix du nom et de la pochette et son accès depuis le menu ⋮ sont validés.
- Reconnaissance automatique, recherche assistée ou correction manuelle des regroupements d’éditions ; aucune méthode n’est encore choisie.
- Présentation des éditions d’origine dans les informations, sauvegarde des choix et possibilité de corriger un regroupement erroné.
- Calcul de la durée et gestion des favoris, statistiques, playlists et du masquage lorsqu’une ligne représente plusieurs fichiers. Les compteurs de titres suivent la liste unique admissible ; ne pas réintroduire un fichier masqué par le seul effet du regroupement et ne pas fusionner arbitrairement les historiques.

Recommandation : réutiliser le même regroupement lorsqu’un album est représenté dans plusieurs vues, plutôt que recréer ses éditions comme autant d’albums indépendants dans une autre page. Les modalités de propagation restent à préciser.

## 7. Identification, disponibilité et actualisation — recommandations

- Identifier prudemment les familles d’éditions : ne pas fusionner arbitrairement des albums homonymes d’artistes différents ou des albums distincts du même artiste. Préserver les regroupements explicitement demandés en section 6.
- Exploiter les crédits d’artiste d’album lorsqu’ils sont disponibles, sans éclater automatiquement une sortie pour chaque artiste de morceau.
- Ne pas inventer de date, de catégorie ou de nombre total de pistes lorsqu’ils ne sont pas connus.
- **« 3 titres » décrit ici les enregistrements de la liste unique locale admissible**, pas une promesse que l’album officiel comporte seulement trois pistes. Aucune complétude ni liste de titres manquants n’est garantie.
- Définir le regroupement des fichiers sans nom d’album sans fusionner arbitrairement toutes les sorties inconnues.
- Actualiser les groupes et compteurs lorsqu’un fichier est détecté ou qu’une métadonnée change, sans interrompre la lecture ni ajouter automatiquement de nouveaux titres à une file déjà lancée.
- Distinguer : absence de permission, analyse en cours, aucune sortie locale, aucun résultat de recherche et album sans titre actuellement visible ou disponible.
- Préciser si un album sans aucun titre visible disparaît de l’index normal ; ne pas effacer ses données enregistrées par cette seule conséquence du masquage.

## 8. Choix encore à discuter

- **Ordre relatif des titres supplémentaires**, cas de l’édition d’origine inconnue ou absente, choix du fichier lu, identification d’un même enregistrement et effets sur les données associées. La liste unique, les véritables variantes distinctes et l’ordre général édition d’origine puis ajouts sont validés.
- Identification et correction des regroupements ; date et type représentatifs ; détail de l’interface de sélection d’une autre édition représentative. Le nom et la pochette de l’édition d’origine par défaut, avec choix d’une autre édition depuis le menu ⋮, sont validés.
- Filtres éventuels Albums / EP / Mixtapes, contenu des cartes, recherche et tri initial.
- Présentation détaillée des lignes de morceaux et confirmation éventuelle avant remplacement de la file ; la disposition de la page, l’accès à l’artiste et les règles de lecture et de constitution de la file sont validés.
- Contenu détaillé des informations et actions de sélection multiple de cartes ; les cinq actions du menu d’album et la portée de ses deux actions musicales sont validées.
- Métadonnées manquantes et albums sans titre visible.

**Acquis, à ne pas redemander :** grille à deux colonnes ; contenus Albums / EP / Mixtapes ; fusion des versions ; liste unique sans répétition du même enregistrement ; ordre édition d’origine puis titres supplémentaires ; nom et pochette d’origine par défaut avec autre édition représentative possible ; disposition de la page et accès à l’artiste ; menu ⋮ à cinq actions et portée de ses deux actions musicales sur la liste unique locale disponible non masquée ; règles de lecture depuis l’album et remplacement de la file, avec confirmation éventuelle encore ouverte. Les véritables variantes restent distinctes et aucun fichier n’est supprimé par cette présentation. Les autres propositions attendent une validation ou les précisions de l’utilisateur. La palette générale de NovaListen n’est pas fixée par cette fiche.

La conception se poursuit avec la [fiche Playlists](07-playlists.md). Les arbitrages encore ouverts ci-dessus restent en attente.
