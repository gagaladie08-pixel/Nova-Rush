# NovaListen — Conception de l’onglet Accueil

> **Statut :** fiche de conception fondée sur la description de l’utilisateur ; aucune implémentation.
> **Nom :** NovaListen. Les occurrences « Namida » et les variations de casse du texte de travail sont normalisées en « NovaListen ».
> **Périmètre :** lecteur de musique Android, dont le cœur repose sur la bibliothèque locale. Cette fiche décrit uniquement l’Accueil.
> **Document distinct :** `Conception_Nova_Rush.docx` décrit un jeu de rythme antérieur ; il n’est ni remplacé ni utilisé comme spécification du lecteur.
> **Habillage transversal :** les [quinze univers et l’option Thème personnalisé](21-systeme-de-themes-visuels.md) sont désormais définis par l’utilisateur. Le thème transforme toute l’interface — palette, typographie, icônes, formes, décors, transitions et courbes — sans changer les fonctions ni les données. **Cyber Nova est le thème par défaut** ; l’option personnalisée permet de modifier couleurs, polices, icônes, formes, animations et styles de courbes, avec interface et réglages détaillés encore à concevoir.

## 1. Vision

L’Accueil n’est pas une page statique. C’est un tableau de bord musical personnalisé, reconstruit à partir de :

- la bibliothèque locale et ses métadonnées ;
- l’historique et les statistiques d’écoute ;
- les favoris ;
- les playlists classiques et intelligentes ;
- les files d’attente récentes ;
- le morceau actuellement en lecture ;
- la date et l’heure, pour les souvenirs et les habitudes horaires ;
- la configuration de l’Accueil choisie par l’utilisateur.

Ses objectifs sont de retrouver rapidement sa musique, poursuivre des habitudes d’écoute, explorer sa bibliothèque et redécouvrir des titres oubliés. Les recommandations décrites ici proviennent de fichiers locaux : elles ne supposent ni catalogue de streaming ni service de recommandation distant.

### Règle transversale : morceaux masqués

Le masquage s’applique à toute l’application, donc à l’ensemble de l’Accueil :

- ne pas afficher les titres masqués dans les sections, mixes, recommandations, bilans et vues complètes associés ;
- ne pas les inclure dans les nouvelles files de lecture constituées depuis l’Accueil, ni les réintroduire lors de la reprise d’une ancienne sélection ;
- conserver la distinction entre masquer un titre et supprimer son fichier ;
- permettre leur réaffichage depuis la rubrique dédiée « Morceaux masqués », dont l’emplacement reste à définir.

Si le titre actuellement en lecture est masqué, NovaListen passe immédiatement au prochain morceau non masqué de la file en cours ; s’il n’y en a aucun, la lecture s’arrête. Les événements validés suivent la conservation sans expiration automatique acquise en section 18 ; la contribution des morceaux volontairement masqués aux compteurs agrégés reste à choisir. Voir la [fiche Bibliothèque](02-bibliotheque.md) pour les décisions et propositions relatives au masquage global.

### Règle transversale : durée minimale locale — réponse 57

Les fichiers musicaux locaux dont la **durée connue est inférieure à 60 secondes** ne font pas partie de la bibliothèque musicale affichée dans NovaListen, ni de ses sélections et nouvelles lectures. **60 secondes exactement reste admissible** sous les autres conditions. Ce filtre de durée n’efface ni ne déplace le fichier du téléphone et n’est pas l’action Cacher. Il s’applique aussi aux contenus dérivés de l’Accueil et à leurs compteurs de morceaux disponibles. La réponse 61 retient une entrée **Durée inconnue · en attente**, hors compteurs disponibles et sans lecture tant que la durée n’est pas établie. L’affichage détaillé, les durées réévaluées et les références ou données historiques préexistantes restent à préciser ; aucune purge de données n’est décidée implicitement.

## 2. Organisation générale

### En-tête

- Le titre **NovaListen** apparaît en haut à gauche.
- Les éventuels boutons à droite de l’en-tête ne sont pas encore définis.

### Bannière de bilan facultative

- Une bannière donne accès à un bilan hebdomadaire, mensuel ou annuel pertinent.
- Elle n’apparaît que lorsque l’historique permet de produire le bilan concerné.
- La page de bilan présente notamment les morceaux, artistes, albums et habitudes d’écoute de cette période.
- Les règles de disponibilité, les seuils de pertinence et la présentation de plusieurs bilans simultanés restent à définir.

### Défilement

- L’Accueil est une grande page à défilement vertical.
- Les sections se parcourent horizontalement.
- Des espacements importants distinguent les sections, comme des modules indépendants.
- Les vues complètes sont distinctes des aperçus limités affichés sur l’Accueil.

## 3. Sections et personnalisation

L’utilisateur choisit les sections actives et leur ordre. Il doit conserver **au moins trois sections actives**.

| Ordre par défaut | Section | Présente par défaut |
| --- | --- | --- |
| 1 | Mixes | Oui |
| 2 | Écoutes récentes | Oui |
| 3 | Tops récents | Oui |
| 4 | Souvenirs perdus | Oui |
| 5 | Files récentes | Oui |
| 6 | Ajoutés récemment | Oui |
| 7 | Albums récents | Oui |
| 8 | Artistes récents | Oui |
| 9 | Éléments épinglés | Oui |
| — | Albums les plus écoutés récemment | Non, ajoutable |
| — | Artistes les plus écoutés récemment | Non, ajoutable |

Le point d’entrée de la personnalisation et le geste de réorganisation restent à définir. Il faut également préciser le traitement des sections actives mais vides : la contrainte des trois sections ne doit pas être confondue avec la disponibilité de trois sections déjà remplies de données.

## 4. Chargement et actualisation

- Lors du premier chargement de la page, NovaListen attend que l’historique et les statistiques nécessaires soient disponibles.
- Pendant cette attente, des cartes temporaires avec effet de scintillement remplacent le contenu.
- Lorsque les données sont prêtes, les cartes temporaires laissent place aux données réelles ou aux états vides appropriés.
- Un geste de rafraîchissement permet de recalculer les listes de l’Accueil.
- Un historique réellement vide est un résultat valide, pas un chargement qui doit se poursuivre indéfiniment.

La fréquence de recalcul automatique et les événements qui actualisent immédiatement les modules restent à définir.

## 5. Mixes

### Présentation et interactions

Chaque mix est une sélection générée à partir de la bibliothèque locale.

Une grande carte illustrée présente :

- son nom ;
- une illustration généralement issue de la pochette d’un morceau représentatif ;
- éventuellement des couleurs dérivées de cette illustration ;
- un aperçu de quelques titres inclus ;
- un bouton de lecture accompagné du nombre de morceaux.

Toucher la carte ouvre le détail du mix : grande illustration, titre, bouton de lecture, actions supplémentaires et liste complète. Le mix peut être lancé comme une file d’attente entière.

### Catégories décrites

| Mix | Principe de sélection | Conditions ou exclusions |
| --- | --- | --- |
| **Nouveaux morceaux recommandés** | Combine des titres populaires récemment, des favoris présents dans les écoutes récentes, des titres récemment joués reclassés selon leur popularité récente et des titres appréciés à une période comparable l’année précédente. Le résultat est mélangé. | Ne désigne pas nécessairement de nouvelles sorties ni des morceaux jamais écoutés. Le libellé est à confirmer pour éviter cette ambiguïté. |
| **Suprématie** | Propose des morceaux apparentés ou recommandés à partir du titre actif. Le nom peut devenir « Nom du morceau — Suprématie ». | Visible seulement avec un morceau en cours et des recommandations pertinentes. La notion de proximité reste à définir ; aucune analyse audio avancée n’est présupposée. |
| **Tops récents** | Reprend les morceaux les plus présents dans les écoutes récentes. | La période de référence et son éventuel partage avec le filtre de la section Tops récents restent à préciser. |
| **Maintenant / à cette heure-ci** | Retrouve les titres habituellement écoutés à une heure similaire. | Largeur de la plage horaire et quantité minimale d’historique à définir. |
| **Partenaires perdus** | Retrouve des titres autrefois importants, désormais délaissés par rapport à leur rythme historique d’écoute. | Nécessite un nombre d’écoutes suffisant ; seuils et comparaison au rythme habituel à définir. |
| **Sous-estimés** | Retrouve des titres déjà écoutés, mais rarement. | Exclut les favoris, les ajouts trop récents et les titres venant d’être beaucoup joués. Fenêtres et seuils à définir. |
| **Découvrir** | Propose des titres très peu écoutés ou jamais écoutés, notamment ceux ayant reçu peu d’attention durant les derniers mois. | Doit pouvoir exploiter les titres locaux sans historique. |
| **Favoris** | Sélectionne aléatoirement un échantillon de la playlist des favoris. | Nécessite des favoris disponibles dans la bibliothèque. |
| **Choix aléatoires** | Produit une sélection aléatoire dans la bibliothèque locale. | Aucune habitude d’écoute n’est nécessaire. |

### Résultats vides et disponibilité des fichiers

- Les morceaux qui ne correspondent plus à des fichiers présents dans la bibliothèque sont retirés des sélections.
- Un mix vide peut être masqué ou placé en fin de liste ; le choix définitif entre ces deux comportements reste ouvert.
- La taille des mixes, les règles de dédoublonnage, le chevauchement autorisé entre mixes et la stabilité d’une sélection entre deux ouvertures restent à définir.

## 6. Écoutes récentes

- Source : écoutes des **trois derniers jours**.
- Aperçu : **environ quarante entrées maximum**.
- Ordre : chronologique récent ; le traitement des écoutes répétées d’un même titre reste à préciser.
- Carte : pochette, titre, artiste et éventuellement temps relatif, en minutes, heures ou jours.
- Toucher un morceau lance ce morceau avec la sélection affichée comme file d’attente.
- L’appui long ouvre les actions du morceau : informations, ajout à une playlist, favoris, partage, etc.
- Toucher le titre de la section ouvre l’historique complet correspondant.

## 7. Tops récents

- Classe les morceaux selon leur fréquence d’écoute sur une période récente, et non simplement selon leur dernière lecture.
- Chaque carte peut afficher un badge de nombre d’écoutes.
- Des filtres horizontaux proposent **1 jour**, puis **3, 6, 9 jours**, et ainsi de suite jusqu’à **environ 60 jours**.
- Changer de période recalcule le classement.
- Toucher l’en-tête ouvre la vue complète pour la période active.
- La période sélectionnée par défaut et sa mémorisation restent à définir.

## 8. Souvenirs perdus

- Retrouve les titres écoutés autour de la même date au cours d’une année passée.
- Exemple de principe : pour le 13 novembre 2026 et l’année 2023, rechercher approximativement du 8 au 18 novembre 2023.
- Classe les morceaux selon leurs écoutes dans cette fenêtre.
- Un sous-titre indique l’ancienneté du souvenir.
- Des boutons permettent de changer d’année parmi celles disponibles.
- L’année actuelle est exclue.
- La sélection initiale est l’année précédente si des données y sont disponibles ; sinon, l’année historique la plus ancienne disponible.
- Toucher l’en-tête ouvre les écoutes complètes de l’intervalle choisi.

La largeur exacte de la fenêtre, la disponibilité d’une année lorsque sa fenêtre est vide et les cas de changement d’année ou de 29 février restent à préciser. Sans historique ancien, un état vide est normal ; aucun historique antérieur à l’utilisation de l’application n’est supposé disponible automatiquement.

## 9. Files récentes

- Permet de retrouver les dernières files d’attente à partir de leur historique interne.
- Sources mentionnées : playlist, dossier, dossier musical ou dossier vidéo.
- Des filtres peuvent afficher toutes les sources ou seulement un type.
- Aperçu : **environ trente files maximum**.
- Déduplique par source : si une même source apparaît plusieurs fois, seule la version la plus récente est privilégiée sur l’Accueil.
- Chaque carte représente sa source.
- Toucher l’en-tête ouvre la gestion complète des files récentes.
- Toucher une carte permet généralement de revenir à la source d’origine.

À préciser : différence entre ouvrir la source actuelle et restaurer un instantané de file ; comportement si la source a été supprimée ; éventuelle prise en charge des dossiers vidéo dans un lecteur initialement musical.

## 10. Ajoutés récemment

- Source : morceaux récemment indexés dans la bibliothèque locale.
- Aperçu : **jusqu’à quarante morceaux**.
- Critère principal : date d’ajout ou de création du fichier, et non nombre d’écoutes ou date de sortie de l’album.
- Une date suffisamment récente peut être présentée sous forme relative, par exemple « il y a deux jours ».
- Toucher une carte lance le morceau dans la sélection récemment ajoutée affichée.
- Toucher l’en-tête ouvre la liste complète, sans la limite de l’aperçu.

La priorité entre date d’indexation, date d’ajout et date de création doit être fixée, notamment au premier scan et après une réindexation.

## 11. Albums récents

- Dérive des **morceaux récemment écoutés**, pas nécessairement des ajouts récents.
- Extrait une liste d’albums uniques, **environ vingt-cinq maximum**.
- Cartes compactes à défilement horizontal : pochette et nom de l’album.
- Toucher un album ouvre sa page détaillée et ses morceaux.
- L’identification d’un album doit être précisée pour éviter de fusionner des albums homonymes.

## 12. Albums les plus écoutés récemment — optionnel

- Dérive des morceaux des Tops récents pour la période sélectionnée.
- Agrège leur contribution au niveau de l’album.
- Peut afficher un compteur ou un score sur la carte.
- Le score représente le poids des morceaux de l’album présents dans cette sélection récente.

La formule d’agrégation et la prise en compte de tous les morceaux de la période ou seulement de la sélection des Tops restent ouvertes. Tant que cette règle n’est pas fixée, le score ne doit pas être assimilé à un nombre absolu d’écoutes de l’album.

## 13. Artistes récents

- Dérive des morceaux récemment écoutés.
- Extrait les artistes associés, **environ vingt-cinq maximum**.
- Évite notamment les doublons qui ne diffèrent que par la casse.
- Chaque carte présente un visuel d’artiste, ou une représentation générée, et son nom.
- Toucher la carte ouvre la page de l’artiste.

Les règles de séparation des collaborations et d’identification des artistes restent à définir. La provenance des visuels doit aussi être précisée ; un service distant n’est pas présupposé.

## 14. Artistes les plus écoutés récemment — optionnel

- Applique aux artistes le principe d’agrégation décrit pour les albums les plus écoutés récemment.
- Utilise la période active des Tops récents.
- Peut afficher un score sur chaque carte.
- Les règles de pondération, notamment pour les collaborations, restent à définir.

## 15. Éléments épinglés

### Contenu et ordre

- Centralise les playlists que l’utilisateur souhaite garder immédiatement accessibles.
- Types décrits : playlists locales classiques et playlists intelligentes.
- Tri par date d’épinglage, du plus récent au plus ancien.

### Playlist classique

- Nom, aperçu composé de pochettes, nombre de titres et durée totale.
- Petit bouton de lecture directe.
- Toucher la carte ouvre la playlist.
- L’appui long ouvre ses actions de gestion.

### Playlist intelligente

- Nom, couverture éventuelle et indication explicite de sa nature intelligente.
- Au lancement, résolution des règles puis construction de la file d’attente.
- Les règles peuvent notamment porter sur artistes, albums, favoris, dates ou statistiques ; leur définition complète appartient à la conception de l’onglet Playlists.
- Bouton de lecture directe et accès à la page détaillée.
- **Aucune commande Aléatoire pour une playlist intelligente**, y compris depuis ses accès épinglés. La lecture suit l’ordre calculé par ses règles, selon la [décision commune de Playlists](07-playlists.md). Cette règle ne supprime pas les mixes aléatoires ni l’aléatoire des autres sources.

### Filtres

- Si plusieurs types sont présents, afficher les filtres utiles : Tous, playlists classiques, playlists intelligentes, ou catégories effectivement disponibles.
- Si un seul type existe, masquer ces filtres pour alléger l’interface.

## 16. Règles communes aux cartes

### Morceaux

- Affichent pochette, titre et artiste.
- Ajoutent selon le contexte un temps relatif ou un compteur d’écoutes.
- Peuvent utiliser les couleurs de la pochette dans leur fond.
- Toucher une carte lance le morceau sélectionné ; toute la sélection affichée dans la rangée devient la file d’attente, avec ce morceau comme position active.
- Le morceau en lecture est visuellement distingué.
- L’appui long ouvre son menu contextuel.

La présence des morceaux précédents dans la file n’implique pas une lecture immédiate de ceux-ci : le comportement de répétition, d’aléatoire et de fin de file sera défini avec le lecteur.

### Albums, artistes, playlists et files

- Les cartes servent d’abord de points d’entrée vers leurs pages ou sources respectives.
- Elles ne déclenchent donc pas toutes une lecture immédiate.
- Un bouton de lecture distinct peut séparer clairement consultation et lecture.

### Effets visuels

- Si activés dans les réglages : flou, transitions, apparitions élastiques et animations légères au chargement.
- La palette précise et l’identité graphique générale ne sont pas encore choisies.
- Proposition à valider : prévoir une réduction des animations et préserver la lisibilité du texte sur les couleurs issues des pochettes.

## 17. États vides

Les messages dépendent du contexte :

| Contexte | Message ou intention |
| --- | --- |
| Historique | Aucun morceau dans l’historique. |
| Intervalle de dates | Aucun morceau trouvé entre les dates sélectionnées. |
| Bibliothèque | Aucun morceau trouvé. |
| Albums ou artistes dérivés de l’historique | Expliquer l’absence de données d’écoute. |
| Éléments épinglés | Aucun élément épinglé. |

Les libellés définitifs restent à rédiger. Une absence de permission, une bibliothèque en cours d’indexation et un historique vide sont des situations différentes ; leur présentation doit être précisée lors de la conception des premiers lancements et des autorisations.

## 18. Règles communes d’écoute et d’historique — acquis et points ouverts

Après le premier tour des dix-sept recettes Modèles Nova, l’utilisateur choisit de préciser ces règles communes. Elles concernent les compteurs d’écoute de NovaListen utilisés par l’Accueil, les Top 10 personnels et les recettes locales. Les écoutes de services connectés restent hors de cette discussion tant que leur périmètre n’est pas défini séparément. Aucun développement n’est autorisé par ces accords.

### Comptabilisation et historique validés — réponses 53 à 68

| Question | Choix acquis | Portée |
| --- | --- | --- |
| 53 | **À partir de 1 minute d’écoute effective, soit 60 secondes.** | Réponse libre de l’utilisateur : « À partir de 1mn d'écoute ». Ce n’est ni 50 %, ni 90 %, ni une minute par tranche donnant plusieurs écoutes dans le même passage. La réponse 57 précise ensuite que les fichiers de durée connue inférieure à une minute sont exclus de l’application, sans supprimer leur audio ; aucun seuil réduit n’est ajouté. |
| 54 | **Cumuler le temps réellement joué pendant un même passage.** | Réécouter deux fois les mêmes 30 secondes peut apporter 60 secondes vers le seuil. Les pauses et les sauts vers l’avant n’ajoutent aucun temps ; la seule position du curseur ne prouve pas que cette durée a été jouée. Aucune exigence de portions toutes différentes n’est retenue. |
| 55 | **Compter chaque nouveau passage validé, y compris les boucles Répéter.** | Un passage donne au maximum une écoute validée. Une relance manuelle ou une nouvelle boucle peut ajouter une écoute si elle atteint à son tour le seuil. Déplacer le curseur ne crée pas à lui seul un nouveau passage. |
| 56 | **Dernière écoute, Jamais joué et délais d’absence des recettes reposent sur les seules écoutes validées.** | Un passage non validé n’augmente pas le compteur, ne repousse pas la dernière écoute statistique et ne fait pas perdre le statut Jamais joué s’il n’existe aucune écoute validée dans l’historique NovaListen disponible. |
| 57 | **Exclure de l’application les morceaux de durée connue inférieure à une minute.** | Réponse libre : « Si un morceau vaut pas 1mn elle se trouve pas dans l'appli ». C’est un filtre de bibliothèque locale, pas une exception au seuil statistique ; 60 secondes exactement reste admis. Fichiers intacts sur le téléphone ; ni Cacher, ni Effacer. La réponse 61 règle l’attente pour durée inconnue ; les réévaluations restent à préciser. |
| 58 | **Une simple pause puis reprise conserve le même passage et son cumul.** | Sans changement de titre ni relance au début, 45 secondes puis pause puis 15 secondes atteignent ensemble la minute. Si le passage est déjà validé, la reprise ne donne pas une deuxième écoute. Le temps en pause n’est jamais ajouté. Après un arrêt complet, la réponse 62 impose en revanche un nouveau passage statistique, même si la position est reprise. |
| 59 | **Horodater l’écoute au moment où le seuil est atteint.** | Pas au début du passage ni à la fin du morceau. Une validation après minuit appartient à cette date pour les statistiques et recettes, dont Rendez-Vous ; les fenêtres en jours suivent désormais les dates du calendrier local (réponse 64). Les compléments 65 et 66 précisent ensuite Retour de Flamme et le fuseau retenu pour les regroupements. La reprise d’un passage déjà validé ne redéplace pas sa date. |
| 60 | **Conserver localement les événements détaillés d’écoutes validées sans expiration automatique.** | Aucun effacement automatique lié à l’âge. Le futur effacement choisi par l’utilisateur, ses commandes et garde-fous restent à concevoir ; aucun effacement réel n’est demandé ici. La réponse 63 exclut ensuite les entrées durables de lectures non validées dans l’historique consultable ; le suivi du passage actif reste distinct. Les éventuels totaux archivés restent à définir. |
| 61 | **Durée inconnue : afficher une entrée en attente, sans lecture.** | Signaler Durée inconnue / En attente, hors compteurs disponibles. Ne pas faire entrer le fichier dans une nouvelle file jouable avant identification de sa durée ; ne pas le déclarer supprimé ou masqué. Détails d’affichage, actions et identification fiable restent à concevoir. |
| 62 | **Après arrêt complet de l’application : nouveau passage statistique.** | Même si le même titre reprend à la même position, repartir de zéro vers la minute ; le temps non validé avant l’arrêt ne s’y ajoute pas. Un nouveau passage suffisant peut compter même si un passage antérieur avait déjà été validé. Cela ne décide ni d’une lecture automatique ni du mécanisme de restauration de position. Une simple pause conserve au contraire le passage (réponse 58). |
| 63 | **Historique consultable : uniquement les écoutes validées.** | Ne pas créer de ligne durable pour les lectures abandonnées avant le seuil. Le suivi du passage encore en cours, nécessaire au cumul et à la pause, reste un état distinct ; ce n’est pas un journal consultable des lectures partielles. Les événements validés restent conservés sans expiration automatique. |
| 64 | **Fenêtres en jours : dates du calendrier local, aujourd’hui inclus.** | N jours = aujourd’hui et les N−1 dates précédentes, de minuit du premier jour jusqu’à maintenant. Exemple : le 17 novembre, 14 jours commencent le 4 novembre à minuit. Ce n’est pas N × 24 heures glissantes. La règle vaut pour les fenêtres en jours des statistiques et recettes ; les compléments 65 et 66 règlent ensuite Retour de Flamme et le fuseau de qualification. Les autres usages des mois, les cas limites de calendrier ou de changement de zone et les opérateurs personnalisés reportés ne sont pas implicitement choisis. |
| 65 | **Retour de Flamme : dates calendaires M−3 à M−1, bornes incluses.** | En remontant depuis la date actuelle : le 17 novembre, du 17 août au 17 octobre inclus. Si le jour n’existe pas dans un mois cible, prendre son dernier jour. Ce n’est ni trois mois civils complets précédents ni une conversion automatique en 30/90 jours ; la règle concerne la fenêtre de cette recette. |
| 66 | **Regroupements historiques dans le fuseau au moment de chaque validation.** | Conserver la date et l’heure locales du téléphone à cet instant pour les statistiques et Rendez-Vous, même si le fuseau change ensuite. Ne pas reclasser automatiquement le passé dans le fuseau actuel ni déplacer l’instant de validation. Aucun besoin de géolocalisation ; format de stockage et cas limites horaires restent à étudier. |
| 67 | **Fichier devenu inaccessible : lignes masquées dans l’historique normal, avec signalement.** | Les événements validés restent conservés ; ne pas les supprimer, les rendre à nouveau lisibles ou afficher leurs lignes normalement par ce seul accord. Le détail du signalement et l’accès éventuel à leur gestion restent à concevoir. |
| 68 | **Conserver la contribution des écoutes de fichiers devenus inaccessibles aux bilans et compteurs historiques.** | Perdre l’accès au fichier ne diminue pas rétroactivement ces totaux. Les compteurs de morceaux disponibles et les résultats jouables restent limités aux fichiers admissibles. Masquage volontaire, filtre de durée, réidentification et effacement d’historique restent des sujets distincts ; aucune fusion de fichiers n’est décidée. |

Ces règles n’inventent ni écoute antérieure à NovaListen ni connaissance de ce que l’utilisateur a entendu dans une autre application. « Jamais joué » au sens des recettes signifie ici **aucune écoute validée enregistrée**, pas jamais entendu dans la vie.

### Questions 69 à 72 — présentées sans réponse

Aucune réponse n’a été enregistrée pour ce groupe. Les sujets restent ouverts, sans option validée, rejetée ou retenue par défaut. Les formulations du questionnaire ne constituent pas non plus de nouvelles décisions sur les confirmations ou les effets d’un effacement d’historique.

| Question | Sujet laissé ouvert |
| --- | --- |
| 69 | Contribution des écoutes d’un morceau volontairement masqué aux totaux historiques agrégés ; ne pas confondre ce cas avec le fichier devenu inaccessible de la réponse 68. |
| 70 | Contribution des écoutes déjà validées si une durée corrigée fait ensuite passer le morceau sous les 60 secondes. |
| 71 | Périmètres d’effacement volontaire de l’historique : global, par période ou par morceaux. |
| 72 | Effets de cet effacement sur les compteurs, la dernière écoute, le statut Jamais joué et les recettes ; conservation éventuelle d’agrégats. |

**Restent ouverts :** identification fiable des durées et détails des entrées en attente ; réévaluations, anciennes références et effets du filtre sur une file déjà lancée ; autres bornes d’un passage lors d’un changement de titre ou incident ; consultation, signalement des événements de fichiers inaccessibles, effacement volontaire et éventuels totaux archivés ; contribution des morceaux masqués ou hors filtre aux agrégats ; réidentification de fichiers déplacés ou revenus ; usages mensuels autres que la fenêtre acquise de Retour de Flamme, cas limites horaires lors de changements de zone, identité et agrégations. Les jours calendaires, le fuseau à la validation et la fenêtre M−3 à M−1 sont acquis. Aucune restauration de position, lecture automatique, purge liée au filtre ou fusion de fichiers n’est implicitement validée ; la progression d’Atlas et les scores restent distincts.

### Autres arbitrages transversaux

1. **Compléments à la comptabilisation commune :** les cas limites ci-dessus restent ouverts ; le seuil d’une minute, le cumul réellement joué, les répétitions admissibles et l’usage des seules écoutes validées ne sont pas à redemander.
2. **Historique :** absence d’expiration automatique acquise ; événements de fichiers devenus inaccessibles conservés, lignes normales masquées avec signalement, contribution historique maintenue. Présentation détaillée, effacement volontaire, export/import éventuel et réidentification des fichiers restent à préciser. Retirer un fichier d’un mix n’efface pas automatiquement son historique.
3. **Périodes et scores :** jours calendaires locaux, fuseau à la validation et fenêtre mensuelle de Retour de Flamme acquis ; autres usages des mois, cas limites horaires, seuils de pertinence, compléments à « récent », pondération des tops et agrégations d’artistes ou d’albums restent à préciser.
4. **Recommandations locales :** critères de proximité du mix Suprématie, volumes des mixes, aléatoire et cadence de renouvellement.
5. **Personnalisation :** accès au réglage des sections, réorganisation, masquage des sections ou mixes vides et application du minimum de trois sections.
6. **Lecture globale :** les interfaces, états et gestes sont décrits dans la [fiche Player et mini-player](03-player-et-mini-player.md). La gestion détaillée de la file et l’éventuelle confirmation de son remplacement restent à finaliser.
7. **Cohérence de la navigation :** différences entre consulter une source, lancer sa lecture et restaurer une ancienne file.
8. **Périmètre connecté :** les écoutes YouTube ou de Listening Party ne sont pas intégrées implicitement aux statistiques locales ; ce point sera décidé dans leurs fiches respectives.

## 19. Suite de la conception

Les sept onglets annoncés sont : **Accueil, Bibliothèque, Artistes, Albums, Playlists, Listening Party et YouTube**. La présente fiche documente uniquement l’Accueil. La [Bibliothèque](02-bibliotheque.md), le [Player et mini-player](03-player-et-mini-player.md) et les [Paroles](04-paroles.md) disposent de fiches dédiées. La fiche [Artistes](05-artistes.md) rassemble les décisions validées et les arbitrages encore ouverts. La fiche [Albums](06-albums.md) distingue désormais les décisions acquises — grille à deux colonnes, albums/EP/mixtapes et fusion des versions — des modalités encore à discuter. La fiche [Playlists](07-playlists.md) rassemble les décisions validées et les propositions restantes ; le [catalogue initial de quinze modèles](08-playlists-intelligentes-premium.md) et la [sélection courante](09-playlists-intelligentes-selection.md) distinguent désormais dix-sept concepts retenus, dont Résonance, Fil Rouge et Terrain d’Entente ; Onde Continue reste à discuter plus tard, ni retenu ni rejeté, tandis que Double Exposition reste non retenu. Listening Party et YouTube restent à définir entièrement, mais leur conception n’est plus un préalable à la première phase de code selon le nouvel ordre demandé ci-dessous.

### Ordre de travail actualisé — demande de l’utilisateur

1. **Discuter et choisir les API** d’enrichissement utiles aux fonctions déjà décrites : paroles, traduction, métadonnées, pochettes, portraits, biographies et records d’artistes, selon les besoins réels et les droits des sources.
2. **Discuter et définir les thèmes visuels** de l’application : le [système de quinze univers, plus l’option Thème personnalisé](21-systeme-de-themes-visuels.md), est désormais fourni par l’utilisateur. Les captures antérieures ne figeaient pas à elles seules les palettes ou typographies ; ce message les définit par univers. **Cyber Nova est désormais le thème par défaut** et le thème personnalisé couvre couleurs, polices, icônes, formes, animations et styles de courbes. Interface, contrôles et détails de déclinaison restent à préciser.
3. **Passer au code avant les deux onglets restants**, en définissant le périmètre concret des premiers lots et les choix techniques à partir des fonctions déjà conçues.
4. **Revenir à la conception puis au développement de Listening Party et de YouTube** ; aucune fonction de ces deux onglets n’est choisie par leur simple présence dans la navigation annoncée.

La discussion active porte désormais sur le système de thèmes fourni par l’utilisateur, après les accords API enregistrés ci-dessous. Les paragraphes suivants conservent leur chronologie ; le dernier état visuel figure dans la fiche de thèmes et en fin de section. Les questions 29 à 32 et 69 à 72 restent sans réponse, sans option validée ou retenue par défaut. Le nouvel ordre prévoit explicitement une phase de code après ces discussions ; à lui seul, il ne sélectionne pas de fournisseur, de stack, de thème, d’abonnement ni de transfert de données. La validation ultérieure de l’architecture de paroles est consignée ci-dessous. Aucun code d’application, installation, build APK, téléchargement ou exécution de modèle n’a été lancé à ce stade.

**API — réponses 73 à 75 acquises : Gratuit uniquement, usage personnel et compte développeur gratuit accepté en principe.** Écarter abonnements et appels facturés, sans bascule automatique vers du payant. NovaListen est destiné à l’usage personnel de l’utilisateur, sans diffusion publique prévue dans le périmètre actuel ; un enregistrement développeur gratuit est acceptable si un fournisseur le demande, mais aucun compte n’est créé ici et aucun compte utilisateur obligatoire n’est décidé. Les droits de sauvegarde restent à vérifier ; les sources de paroles sont désormais retenues au niveau de l’architecture décrite ci-dessous, sans activation. La [fiche API et enrichissements](13-api-et-enrichissements.md) distingue cet accord des autres candidats ; aucun moteur de traduction, autre fournisseur d’enrichissement ou thème visuel n’est choisi par la validation de la cascade de paroles.

**Cascade d’API de paroles — architecture validée :** après élargissement du comparatif, l’utilisateur confirme « Je valide tes cascade ». Sont retenus le **noyau lrc.red / LRCLIB / LiriQo**, **Better Lyrics en complément ciblé**, **Textyl / lyrics.ovh en recours secondaires**, **Lyrica en réserve**, puis **Web → collage** si nécessaire. Les connecteurs directs des cinq plateformes restent conditionnels à leur utilité et à des conditions compatibles ; Vagalume n’est pas ajouté par cet accord. Le [comparatif et l’architecture des API de paroles](14-cascade-api-paroles.md) distinguent cette validation des paramètres encore ouverts : ordre exact, séquentiel/parallèle, poursuite ou arrêt, délais et critères de correspondance. Les questions 76 et 77 restent historiquement sans réponse ; la validation libre ultérieure ne leur attribue pas d’option rétroactive et ne fait pas de LRCLIB une source principale unique. Choix manuel, niveau réel de synchronisation, provenance et fonctionnement local sont conservés. Aucun code, essai, compte, clé, hébergement, dépense ou transfert de fichier n’est lancé par cette validation. Les autres enrichissements restent à préciser avant les thèmes visuels.

**Traduction française des paroles — choix validé :** après la discussion ouverte par « Allons y », l’utilisateur confirme « Oui je valide » pour **ML Kit par défaut en local + Azure Translator standard F0 uniquement sur demande explicite**. Le [comparatif et le fonctionnement de traduction](15-traduction-francaise-paroles.md) consignent ces moteurs et leurs rôles : réutiliser une VF sauvegardée, traduire localement si les packs sont disponibles, proposer une autre traduction via Azure sur commande, sauvegarder la VF retenue sans écraser la VO. Aucun envoi automatique après refus de packs ou erreur locale, aucun recours payant. VO/FR réversible, provenance et niveau réel de synchronisation sont conservés ; les modèles de Fil Rouge ne deviennent pas des traducteurs. Les détails de packs, d’UX, d’authentification, les droits, la compatibilité et la qualité restent à vérifier ou définir ; aucun compte, clé, téléchargement, essai ou envoi de texte n’est lancé. Les autres enrichissements, notamment métadonnées et pochettes, restent à discuter avant les thèmes visuels.

**Métadonnées et pochettes — historique du comparatif avant validation de la cascade :** la nouvelle poursuite « Allons y » ouvre leur discussion, sans sélection ni activation de fournisseur. Le [comparatif des métadonnées et pochettes](16-metadonnees-et-pochettes.md) recommande **MusicBrainz pour les métadonnées, Cover Art Archive pour les pochettes de l’édition identifiée et fanart.tv en complément conditionnel**. Enregistrement, édition et groupe d’éditions sont distincts ; les regroupements locaux explicitement demandés, le nom et la pochette d’origine par défaut, le choix d’une autre édition représentative locale et les corrections manuelles restent préservés. L’édition volontaire des tags dans le fichier d’origine est distincte de l’affichage du groupe ; aucun écrasement automatique n’est ajouté. Accès gratuit et droit de sauvegarder une image ou de l’incorporer aux tags doivent être examinés séparément. AcoustID reste une option de reconnaissance à discuter, sans analyse ni transmission d’empreinte autorisée par cette poursuite. Aucun compte, clé, appel de recherche sur la collection, téléchargement de pochettes, envoi ou modification d’audio n’est exécuté. Biographies, portraits et records restent des sujets API distincts avant les thèmes.

**Précision sur Last.fm, TheAudioDB et Discogs — questions antérieures à la validation :** l’utilisateur demande « Et last fm, TheAudioDB, Discogs ? ». Le comparatif ci-dessus détaille désormais Last.fm pour les tags et informations musicales, Discogs pour les détails d’éditions/crédits avec conservation à clarifier et TheAudioDB en réserve tant que gratuité d’application et usages ne sont pas confirmés. Les réserves d’image ne rejettent pas toutes leurs métadonnées. Ni cette question ni l’approfondissement ne valident le trio proposé précédemment ou ces compléments, ne définissent un ordre automatique d’appels, n’ajoutent le scrobbling, ni n’autorisent comptes, clés, essais, transferts ou modifications de fichiers.

**Précision sur Deezer et iTunes — question antérieure à la validation :** la question « Et Deezer api et itunes api ? » conduit à approfondir Deezer comme candidat secondaire textuel sous accès autorisé disponible, et iTunes Search API comme candidat de recherche/comparaison de catalogue sous conditions compatibles. La FAQ Deezer distingue noms de titres/artistes conservables et images non autorisées en cache ; iTunes encadre les pochettes à des fins promotionnelles, sans que la clause de non-cache des extraits audio/vidéo soit étendue à toutes les métadonnées. Les pochettes durables ou écrites dans l’audio ne sont donc pas autorisées par le seul accès à ces API. Aucun de ces fournisseurs ni les propositions précédentes n’est accepté par cette question ; aucun compte, clé, abonnement, appel de catalogue, téléchargement de visuel, streaming ou modification de fichier n’est exécuté. Le cadre gratuit et personnel, les choix manuels et les sujets encore ouverts restent inchangés.

**Cascade de métadonnées et pochettes — architecture validée, 17 novembre 2026 :** après « Propose moi un cascade api très efficace avec tout ses api », l’utilisateur confirme « Je valide ». La [cascade adaptative validée](17-cascade-api-metadonnees-et-pochettes.md) retient les huit rôles sans huit appels systématiques par morceau : données locales prioritaires puis MusicBrainz pour l’identité ; iTunes + Deezer en parallèle en recours de catalogue si admissibles ; Discogs ciblé sur les éditions/crédits, directement après MusicBrainz si seule l’édition est incertaine ; Last.fm pour les tags ; TheAudioDB en dernier complément conditionnel. La branche de pochettes part du local puis CAA par édition, alternatives de groupe CAA/fanart.tv clairement signalées et TheAudioDB si compatible. Résultats progressifs, choix manuel par champ, arrêt des appels inutiles, déduplication/réutilisation/cache autorisés et **maximum de deux requêtes simultanées au total pour cette cascade** sont acquis, sans performance mesurée. La validation retient l’architecture, pas des accès gratuits ou droits déjà confirmés ; elle n’autorise aucune activation, compte, clé, acceptation de conditions, dépense, installation, recherche sur la collection, transmission ou modification audio. Corrections, fichiers d’origine, regroupements et choix d’édition représentative restent intacts. AcoustID, biographies, portraits et records restent distincts ; les détails de correspondance, délais, cache, commandes et écriture restent à préciser. Cet accord libre non numéroté ne répond pas aux questions 29 à 32, 69 à 72 ou 76 et 77 ; il ne fixe ni l’ordre ni la concurrence des paroles et ne change pas la traduction validée.

**Suite API avant les thèmes visuels :** poursuivre les choix propres aux biographies, portraits et records d’artistes, ainsi que les vérifications et détails encore ouverts, sans commencer le code ou activer les fournisseurs par ce seul accord.

**Biographies, portraits et records — historique de la proposition avant accord partiel :** après la validation de la cascade des métadonnées/pochettes, l’utilisateur répond « Allons y » à la suite annoncée pour les artistes. La [fiche de sources artistiques](18-biographies-portraits-et-records.md) proposait Wikipédia pour les textes, Commons pour les portraits et Wikidata pour l’identité et les faits référencés, en réutilisant le contexte MusicBrainz disponible. Français prioritaire puis anglais proposé était alors une recommandation, pas encore une langue enregistrée. Cette poursuite seule ne validait pas le socle et n’autorisait aucun essai. Biographie de trois lignes avec Voir plus, choix manuel des photos et sauvegarde hors ligne étaient déjà acquis ; récompense, record strict et statistique personnelle restaient distincts, sans API exhaustive de tous les records établie.

**Artistes — accord partiel et demande d’autres pistes photo, 17 novembre 2026 :** « Oui mais pour la photos artiste je veux d'autres piste » retient **Wikipédia pour les biographies, Wikidata pour l’identité et les faits/records référencés à vérifier, français prioritaire puis anglais proposé en recours**. Texte choisi, crédits, sauvegarde hors ligne autorisée et préservation des contenus restent le socle. **Les fournisseurs et l’ordre des portraits restent ouverts** : Commons n’est pas imposé comme source principale ni explicitement rejeté. Dans une première proposition désormais révisée, le [comparatif de pistes photo](19-photos-artistes-autres-sources.md) recommandait fanart.tv comme première piste spécialisée, Openverse et TheAudioDB conditionnel ; cela n’avait pas validé de nouveaux fournisseurs ou commandes. La correction ultérieure et l’ajout demandé de Last.fm sont consignés ci-dessous. Plusieurs propositions lorsque disponibles, choix manuel et remplacement volontaire restent acquis. Les catégories/sources et méthode des records, routes, crédits, déclenchement, cache, concurrence et détails d’interface restent ouverts ; le maximum de deux requêtes de la cascade de métadonnées/pochettes n’est pas étendu aux artistes ou aux paroles. Pas de traduction automatique des biographies ajoutée, pas de nouvel onglet ou discographie distante, pas de modification du Top 10 personnel ou des fichiers audio. Les accords antérieurs et questions sans réponse restent intacts. Aucune activation, compte, clé, acceptation de conditions, dépense, installation, recherche sur la collection, téléchargement d’image pour l’application, transfert, essai ou code ; les thèmes visuels ne sont pas commencés.

**Photos d’artistes — correction et élargissement demandé, 17 novembre 2026 :** « Fanart donne de fausses image ou d'anciennes image ajoute last fm et d'autres sources aussi ». **Last.fm est ajouté aux sources demandées dans la conception** ; ma recommandation de fanart.tv en premier est retirée, sans déduire un bannissement total ni révoquer son rôle de pochettes validé. Le [comparatif multisource révisé](19-photos-artistes-autres-sources.md) distingue l’ajout demandé de Last.fm de son usage réel : portraits retirés de l’API selon le support et images exclues de l’accord API, donc pas de récupération automatique promise ou de scraping. Genius, Discogs, TheAudioDB, Openverse/Commons, sources officielles et import autorisé sont comparés sous conditions ; les restrictions de Flickr direct, Bandsintown, TIDAL, Deezer et Spotify ne sont pas masquées comme des API gratuites de portraits permanents. Sont proposés des contrôles d’identité, type d’image, doublons, provenance et dates : prise de vue ≠ ajout/publication ≠ récupération, date inconnue restant signalée. Filtres/tri de fraîcheur, rejet mémorisé, interface, ordre/concurrence et admission effective des sources restent à valider ; aucune promesse d’images récentes partout ni seuil d’ancienneté n’est inventé. Plusieurs propositions lorsque disponibles, choix manuel, sauvegarde locale autorisée et préservation du choix restent acquis. Le socle Wikipédia/Wikidata et français prioritaire/anglais proposé, les trois accords API antérieurs, les regroupements locaux et questions ouvertes restent inchangés. Documentation seulement, sans compte, clé, condition acceptée, dépense, installation, test de collection, récupération de portrait pour l’application, envoi à un modèle, modification audio ou code ; thèmes visuels non commencés.

**Photos d’artistes — historique de la demande de cascade avant validation, 17 novembre 2026 :** « Fait maintenant une cascade hyper efficace en ajoutant tout ses api pour ce qui est des autorisations ne n'est que pour mon usage personnel ». Le cadre personnel est réaffirmé, sans diffusion publique prévue et sans renumérotation des réponses 73 à 75. La [cascade photo adaptative proposée](20-cascade-api-photos-artistes.md) donne une place à toutes les pistes : contexte local et identité commune MusicBrainz/Wikidata réutilisés, Last.fm et repères de catalogue seulement si utiles et admissibles ; Genius + TheAudioDB en première vague admissible, Commons + Openverse en élargissement, Discogs ciblé puis fanart.tv en ultime complément admissible, sources officielles/import autorisé comme recours manuels proposés. Le registre conserve Deezer, TIDAL, Spotify, Flickr direct, Bandsintown, iTunes Search et CAA avec leurs rôles ou raisons de saut ; il n’impose pas de faux appels photo aux services incompatibles. L’usage personnel ne rétablit ni portraits Last.fm retirés, ni clés inaccessibles, ni droits de sauvegarde refusés. Sont proposés deux requêtes simultanées au total pour le parcours photo, un premier lot cible de six vignettes, des résultats progressifs, arrêts, réutilisation, déduplication, cache compatible et contrôle des dates/provenances ; aucun optimum ou taux de réussite n’est mesuré. **Cette demande seule ne validait pas encore cet ordre ou ces réglages** ; elle ne rétablissait pas fanart.tv en priorité et n’étendait pas le plafond acquis des métadonnées/pochettes aux artistes ou aux paroles. La validation ultérieure est enregistrée ci-dessous. Choix manuel, sauvegarde locale autorisée, remplacement volontaire, Wikipédia pour les biographies, Wikidata pour l’identité et les faits/records à vérifier, et français prioritaire/anglais proposé restent acquis ; autres accords, fichiers musicaux et questions ouvertes intacts. Conception seulement, sans compte, clé, condition acceptée, dépense, test de service ou de collection, portrait téléchargé pour l’application, envoi, installation ou code ; thèmes visuels non commencés.

**Photos d’artistes — architecture validée, 17 novembre 2026 :** après présentation de la [cascade photo adaptative](20-cascade-api-photos-artistes.md), l’utilisateur confirme **« Je valide »**. Sont retenus les rôles conditionnels de toutes les pistes et l’ordre local/identité commune → Genius + TheAudioDB admissibles → Commons + Openverse → Discogs Artist admissible → fanart.tv admissible en ultime complément → recours volontaires aux sources officielles/kits presse ou à l’import local autorisé. Last.fm reste inclus pour l’identité/réutilisation et la consultation distincte, sans API de portraits rétablie ; les autres routes non admissibles sont recensées et sautées. Sont acquis **au plus deux requêtes simultanées au total pour le parcours photo**, une **cible initiale de six vignettes distinctes**, une découverte initiale bornée à une page ou fiche par fournisseur admissible avec consultation ciblée si seuls des identifiants sont renvoyés, résultats progressifs, poursuite volontaire bornée de type Plus de photos, réutilisation/déduplication/cache compatibles et arrêt des travaux inutiles. Choix manuel, sauvegarde autorisée, préservation de la photo choisie et principes de contrôle du sujet, type d’image, provenance et dates sont conservés ; une date de prise de vue inconnue reste Date inconnue. L’accord est propre aux photos : il ne fixe pas la concurrence des paroles ou biographies/records et ne change pas les autres validations API, le socle Wikipédia/Wikidata, les regroupements, l’historique ou les fichiers audio. **Cet accord non numéroté ne répond pas aux questions restées sans réponse.** Accès/droits effectifs, clés et formalités, dimensions/cadrage, seuils détaillés, filtres/tri/rejets proposés, commandes/interface des recours et de poursuite, portée initiale et éventuels lots d’artistes, cache, délais, reprises et ordonnanceur entre cascades restent ouverts, sans défaut implicite. Gratuit uniquement et usage personnel restent acquis ; aucun compte/clé, condition fournisseur acceptée, dépense, installation, essai de service ou de collection, portrait téléchargé pour l’application, transfert, modification musicale ou code. **Prochaine étape de la feuille de route : les thèmes visuels**, sans thème choisi ou commencé par cette validation.

**Thèmes visuels — système fourni par l’utilisateur, 17 novembre 2026 :** après la validation de la cascade photo, l’utilisateur définit [quinze univers complets](21-systeme-de-themes-visuels.md) : Cyber Nova, Neon Disco, Villain Era, Slay Queen, Pink Y2K, Velvet Stage, Pink Venom, Cloud Nine, Solara, Chaos Born, Survivor, Rainbow Pop, Pop Revolution, African Confessions et Bad Angel. Leurs huit codes de palette, polices, formes, animations et styles de courbes sont enregistrés fidèlement. **« Le thème personnalisé est une option en plus »** et **« le thème choisi transforme totalement l’appli dans son univers »** : personnalisation supplémentaire, pas remplacement du catalogue ni simple couleur d’accent. Les rayons de cartes du thème priment sur les arrondis illustratifs des références, sans modifier la structure ou les fonctions acquises. Ce premier message ne décidait pas encore du thème initial ou du périmètre de personnalisation ; les précisions ultérieures sont enregistrées ci-dessous. Aucun mode automatique ou nouveau mécanisme de déblocage n’est déduit. Les recommandations de lisibilité, mouvement et performance, les ressources réelles et les détails de déclinaison restent distincts des exigences reçues ; aucun code de palette n’est changé. Les validations API, choix manuels, contenus choisis, historique, regroupements, règles de lecture et questions sans réponse restent intacts. **La phase thèmes est ouverte, sans clôture implicite de sa conception ni passage au code.** Rédaction et calcul local préliminaire de contraste seulement : aucun asset/police/modèle téléchargé, image générée, application codée ou exécutée, API activée, compte/clé, acceptation de conditions, dépense ou modification musicale.

**Thèmes visuels — défaut et personnalisation précisés, 17 novembre 2026 :** l’utilisateur indique « Le thème par défaut est cyber nova » et reprend la personnalisation des couleurs, polices, icônes, formes, animations et styles de courbes. La [fiche du système de thèmes](21-systeme-de-themes-visuels.md) retient **Cyber Nova par défaut**, notamment pour l’univers initial en l’absence d’autre choix, et ces **six catégories modifiables** dans l’option personnalisée supplémentaire. Il ne s’agit pas d’un simple sélecteur de couleur. Ce défaut n’impose pas de réinitialiser le thème à chaque ouverture ni de prendre Cyber Nova comme base unique de l’éditeur. Les contrôles précis, jeux de ressources, paramètres, aperçu, mémorisation, profils et éventuel import/export restent à concevoir ; aucune interface, combinaison libre, ressource externe ou nouvelle permission n’est choisie par ces catégories. Les quinze univers, leurs 120 codes de palette et les fonctions musicales restent inchangés ; accords API, historique et questions sans réponse conservés. Enregistrement de conception seulement, sans clôture de la phase thèmes, code, installation, téléchargement, image générée, activation d’API, compte/clé, dépense, transfert ou modification de fichier musical.
