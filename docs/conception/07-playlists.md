# NovaListen — Conception de l’onglet Playlists

> **Statut :** conception partiellement validée, sans implémentation. La grille, l’en-tête, les filtres, les cartes, les deux parcours généraux de création, la page, les six entrées du menu et les comportements communs explicitement acquis dans les sections 1 à 6 sont validés. L’accès séparé aux Modèles Nova et aux règles personnalisées, les sept familles de critères personnalisés, Toutes les conditions et le plafond facultatif de titres sont désormais acquis. Des premiers réglages, contrôles ou objectifs sont acquis pour les dix-sept recettes : repères 1 à 12, 14, 15, A, B et C de la sélection courante, sans renumérotation ni réintégration de Double Exposition. Le premier tour des recettes est terminé, pas leur spécification complète. Leurs compléments, les opérateurs et l’ordre des résultats personnalisés, des détails de sélection, de présentation et de gestion de la file restent à concevoir ; le récapitulatif de la section 7 les distingue des décisions à ne pas redemander.
> **Sélection :** dix-sept concepts retenus, selon la sélection courante ; Onde Continue à discuter plus tard, ni retenu ni rejeté ; Double Exposition non retenu. Les concepts retenus ne sont pas dix-sept playlists déjà créées.
> **Règles acquises majeures :** aucune commande Aléatoire sur une intelligente, quel que soit son modèle ou ses règles personnalisées ; Aléatoire conservé pour les classiques. Aucun accès Favoris dans cet onglet, sans suppression des cœurs. Le masquage reste global. Modifier une classique ou recalculer une intelligente ne change pas sa file lancée ; supprimer la playlist d’origine de la session arrête celle-ci et vide sa file.
> **Périmètre :** playlists de morceaux de la bibliothèque locale, utilisables hors ligne. Aucun service distant, compte, partage synchronisé, import YouTube ou intégration à Listening Party n’est ajouté implicitement. Les décisions de file prises ici concernent les lectures et demandes depuis une playlist ; ne pas trancher implicitement les confirmations des autres onglets.
> **Dernières réponses, 65 à 68 :** Retour de Flamme prend les dates M−3 à M−1 incluses depuis la date actuelle, avec dernier jour du mois si nécessaire ; les regroupements historiques conservent le fuseau au moment de chaque validation ; les événements de fichiers devenus inaccessibles sont conservés, leurs lignes masquées dans l’historique normal avec signalement, et leur contribution aux totaux historiques maintenue. Cela ne les rend pas jouables. Les vingt-huit décisions communes de Playlists, le premier tour des dix-sept recettes et les réponses 53 à 64 restent acquis.
> **Étape d’historique documentée :** après le premier tour des dix-sept recettes Modèles Nova, l’utilisateur a choisi de travailler les règles communes d’écoute et d’historique : définition d’une écoute, lectures partielles ou répétées, puis conservation et périodes. Les réponses 53 à 68 valident le socle décrit ci-dessous, dont l’historique qualifié sans expiration, les jours calendaires, le fuseau à la validation, les bornes mensuelles de Retour de Flamme et le maintien des faits historiques pour les fichiers inaccessibles ; autres cas limites, effacement, contribution des fichiers masqués ou hors filtre et autres usages des mois restent ouverts. Les questions 29 à 32 sur les opérateurs personnalisés, les comparaisons de valeurs et dates, le tri des résultats et l’enregistrement d’un résultat vide ont été passées sans réponse : elles restent ouvertes, sans option validée ni choix par défaut ajouté. Ce report ne retire pas le socle personnalisé déjà acquis.
> **Dernier questionnaire d’historique, 69 à 72 :** présenté sans réponse. La contribution historique des morceaux volontairement masqués ou devenus hors filtre de durée, les périmètres d’effacement volontaire de l’historique et ses effets statistiques restent ouverts, sans option validée ni choix par défaut. Les réponses acquises sur ce socle d’historique s’arrêtent à 68 ; voir le suivi en [section 18 de l’Accueil](01-accueil.md#18-règles-communes-découte-et-dhistorique--acquis-et-points-ouverts).
> **Feuille de route actuelle :** accords [API](13-api-et-enrichissements.md) enregistrés séparément, puis conception des [thèmes visuels désormais fournis](21-systeme-de-themes-visuels.md), avant une première phase de code et les deux onglets restants. La réponse 73 retient Gratuit uniquement pour les API ; ni ces accords ni les thèmes ne répondent aux questions d’historique ou aux règles personnalisées restées ouvertes.
> **Limites :** la [première étude de faisabilité](10-playlists-faisabilite-technique.md) reste préliminaire. Aucun prototype, résultat de performance ou développement n’est autorisé par ces choix d’interface ; les modèles de Fil Rouge et leur parcours sont acquis séparément dans les fiches dédiées.
> **Habillage transversal :** le [système de quinze univers et l’option personnalisée supplémentaire](21-systeme-de-themes-visuels.md) habille cartes, couvertures, listes, création, menus et états. Les rayons des cartes ne sont pas uniformisés par les captures. Types, recettes, contenus, gestes et files restent inchangés ; aucun thème ne réintroduit Aléatoire sur une intelligente, n’invente de progression ou ne modifie une couverture choisie.

## 1. Fondations déjà définies

- L’[Accueil](01-accueil.md) prévoit des **playlists classiques et intelligentes**, avec accès au détail et bouton de lecture distinct.
- Les playlists peuvent être **épinglées à l’Accueil**. La section des éléments épinglés y suit la date d’épinglage, du plus récent au plus ancien ; cela ne fixe pas le tri de l’onglet Playlists ni l’emplacement de l’action d’épinglage.
- Pour une playlist classique, l’Accueil décrit le nom, un aperçu composé de pochettes, le nombre de titres et la durée totale. Leur réemploi dans cet onglet est désormais validé, selon la présentation des cartes détaillée en section 2.
- Pour une playlist intelligente, la nature intelligente est explicite. **Au lancement, résoudre ses règles puis construire la file** est déjà décrit pour l’Accueil. Les recettes retenues peuvent utiliser notamment métadonnées, favoris, dates et statistiques ; l’éditeur personnalisé est désormais limité aux sept familles choisies en section 3, sans critères d’historique.
- **Ajouter à une playlist** existe déjà pour les morceaux et sélections de la [Bibliothèque](02-bibliotheque.md), ainsi que dans les menus d’artiste et d’album selon leurs périmètres validés. **Destinations désormais validées : une ou plusieurs classiques existantes, ou création rapide d’une classique**, avec ajout des morceaux concernés au début dans leur ordre ; les intelligentes restent gouvernées par leurs règles. Les détails sont distingués en section 3.
- Le **masquage est global** : aucun morceau masqué ne doit réapparaître dans une playlist, les favoris, une recherche ou une lecture lancée depuis cette page. Le masquage ne supprime pas le fichier ; la conservation des références sous-jacentes reste un sujet distinct.
- Les favoris existent via le cœur des morceaux. **Aucune carte ni aucun raccourci Favoris dans l’onglet Playlists**, selon le choix explicite de l’utilisateur ; les cœurs, les favoris enregistrés et les éventuelles conditions intelligentes fondées sur les favoris restent inchangés.
- Le [mini-player](03-player-et-mini-player.md) reste disponible pendant la navigation selon ses règles habituelles.
- Consulter une playlist ne doit pas implicitement lancer sa lecture ni remplacer la file en cours.
- **Décision propre à l’onglet Playlists : grille à deux playlists par ligne**, avec défilement vertical. La présentation en liste compacte n’est pas retenue pour cet index.
- **En-tête validé :** Playlists avec nombre de listes, bouton + de création, Recherche par nom et bouton Tri.
- **Filtres validés : Toutes / Classiques / Intelligentes**, affichés uniquement lorsque les deux types sont présents. **Toutes au départ, puis dernier filtre choisi mémorisé**, y compris après fermeture de l’application.
- **Cartes validées :** couverture carrée aux coins arrondis, mosaïque automatique par défaut avec image locale personnalisable, nom, nombre de titres locaux disponibles non masqués, durée, badge Intelligente et petit bouton Lecture distinct du toucher d’ouverture. La personnalisation de la couverture ne modifie pas les pochettes des fichiers audio.
- **Parcours de création validés :** le bouton + propose Classique ou Intelligente. Une classique peut être créée vide ou avec une sélection locale. Une intelligente suit nom → choix distinct Modèle Nova ou Règles personnalisées → réglages → aperçu des morceaux et de leur durée → enregistrement. Les familles personnalisées, Toutes les conditions et le plafond facultatif de titres sont acquis ; les opérateurs et les paramètres propres aux recettes restent à préciser.

**Gestes désormais validés dans l’onglet Playlists**, sur cartes et morceaux : droite Lire ensuite, gauche Informations, appui long de sélection multiple. Cet accord est explicite, pas une extension automatique des gestes d’artistes ou d’albums. **Sur l’Accueil, l’appui long des playlists conserve ses actions de gestion** ; la réorganisation des morceaux d’une classique garde sa poignée dédiée.

## 2. Écran principal — décisions validées et options restantes

### En-tête et filtres — validés

- Titre **Playlists** à gauche, avec le nombre de listes affichables.
- Bouton **+** pour créer une playlist.
- Icônes **Recherche** et **Tri**.
- Rechercher par **nom de playlist** ; la recherche ne lance pas de musique et ne modifie pas la file active.
- Filtres **Toutes / Classiques / Intelligentes**, affichés **uniquement lorsque les deux types sont présents**. Au premier affichage, sélectionner **Toutes** ; ensuite retrouver le dernier filtre choisi, y compris après fermeture de l’application.

Le bouton Tri, les **quatre critères inversables**, le **réglage initial Nom A → Z et la mémorisation du dernier tri choisi** sont validés. Ce tri de la grille ne change pas l’ordre des morceaux à l’intérieur des playlists. Le filtre initial Toutes et sa mémorisation ultérieure sont également acquis. Les parcours de création depuis + et la création rapide d’une classique depuis Ajouter à une playlist sont distingués en section 3.

### Présentation validée

- **Grille à deux playlists par ligne**, à défilement vertical.
- L’utilisateur a retenu la recommandation de grille ; la liste compacte n’est plus une alternative à choisir pour cet index.

### Contenu visuel des cartes — validé

- Couverture carrée aux coins arrondis : **mosaïque automatique de pochettes par défaut**, avec possibilité de choisir une **image locale personnalisée**. Ce choix concerne la couverture de la playlist, pas les pochettes des fichiers audio.
- Sous la couverture : **nom, nombre de titres locaux disponibles non masqués et durée correspondante**.
- Badge **Intelligente** pour distinguer les listes à règles.
- Petit bouton **Lecture** distinct du toucher de navigation : **toucher la carte ouvre la playlist sans lancer de musique**. Le bouton Lecture suit l’ordre admissible de la playlist et remplace directement la file, sans confirmation, selon la section 4.

Une éventuelle indication d’épinglage sur la carte et le visuel d’une playlist sans morceau restent à choisir.

Les compteurs d’une liste intelligente décrivent ses résultats locaux admissibles courants ; la fréquence de recalcul et l’affichage pendant le calcul restent à préciser. Ne pas afficher comme disponibles des titres masqués ou des fichiers inaccessibles.

### Tri et absence d’accès Favoris — décisions validées

- **Critères retenus : Nom ; date de création ; nombre de titres locaux disponibles non masqués ; durée correspondante.**
- **Chaque critère est inversable** : A → Z / Z → A, récent / ancien, nombre ou durée croissants / décroissants. Le tri par date de modification n’est pas retenu dans cette liste de critères.
- **Nom A → Z au départ, puis dernier tri choisi mémorisé**, y compris pour les ouvertures suivantes. Ne pas revenir systématiquement au classement alphabétique si l’utilisateur l’a changé. Les égalités et valeurs manquantes restent à préciser.
- **Aucun accès Favoris ici** : ni carte spéciale en tête ni raccourci au-dessus de la grille. Cela ne supprime ni les favoris enregistrés, ni les cœurs des morceaux, ni leur usage éventuel dans des règles intelligentes.

## 3. Créer une playlist — parcours validés et options restantes

Le bouton **+** ouvre le choix entre **Classique** et **Intelligente**. L’utilisateur a validé les deux parcours ci-dessous.

### Playlist classique — parcours validé

1. Donner un **nom** à la liste.
2. **Sélectionner des morceaux locaux**, ou les ajouter plus tard.
3. **Créer la playlist**.

Une playlist classique peut donc être **créée vide**, puis remplie ensuite. La couverture suit la règle déjà validée : mosaïque de pochettes par défaut, avec image locale personnalisable. L’emplacement de cette personnalisation dans le parcours de création reste à choisir.

Précisions acquises et détails de manipulation encore ouverts :

- Ordre manuel et réorganisation par poignée dédiée validés en section 4 ; les détails de manipulation restent à préciser. Les changements sont enregistrés dans la classique mais ne modifient pas sa file déjà lancée.
- **Créer une classique depuis Ajouter à une playlist est désormais validé séparément** : saisir son nom, puis y ajouter directement les morceaux concernés, selon le parcours détaillé ci-dessous.

Une playlist est proposée comme **une liste de références aux morceaux**, pas comme une copie supplémentaire des fichiers audio. **Décision validée pour une classique : si le même fichier est déjà présent, ignorer son ajout répété et informer l’utilisateur des ajouts ignorés.** Cette règle concerne le même fichier, pas une comparaison du titre ou de l’artiste : ne pas fusionner live, remix, acoustique ou fichiers distincts. Elle n’autorise ni suppression audio ni nettoyage rétroactif non demandé. **Les nouveaux titres sont insérés au début de la classique, dans l’ordre fourni par la source ou la sélection affichée, sans inverser le bloc ni réordonner les titres existants.** Un fichier ignoré parce qu’il est déjà présent n’est pas déplacé en tête. L’identité entre plusieurs fichiers d’un même enregistrement reste ouverte.

### Sélecteur Ajouter à une playlist — destinations et création rapide validées

- Proposer les **playlists classiques existantes comme destinations**. **Cocher une ou plusieurs classiques, puis valider** pour ajouter les mêmes morceaux à chacune. Les intelligentes ne sont pas des destinations d’ajout manuel dans ce sélecteur : leur contenu reste déterminé par leurs règles. L’option de les afficher comme destinations désactivées n’est pas retenue par ce choix.
- Proposer **Créer une classique** : saisir le nom, créer la liste et y ajouter directement le ou les morceaux concernés par l’action d’origine. Ne pas imposer de retourner au bouton + puis de refaire la sélection.
- Ce parcours s’applique depuis un morceau, une sélection, un artiste ou un album. Il conserve les périmètres déjà validés : tous les titres locaux admissibles de l’artiste, ou la liste unique admissible de l’album, sans récupérer de fichier absent ou masqué.
- Appliquer la règle d’ajout répété **séparément pour chaque classique destinataire** : ignorer les fichiers déjà présents avec information, sans copie ni suppression audio. La présence du fichier dans une liste ne bloque pas son ajout à une autre où il manque.
- Insérer les nouveaux morceaux **au début de chaque classique**, en conservant leur ordre interne. Ne pas remonter les fichiers ignorés déjà présents ; ne pas modifier les files déjà lancées par le seul ajout à ces listes.
- **Restent ouverts :** présentation et recherche dans le sélecteur, détail du bilan par destination et des éventuelles erreurs. Le choix de plusieurs destinations et l’insertion en tête ne sont plus à redemander.

### Playlist intelligente — parcours validé

1. Donner un **nom** à la liste.
2. Choisir entre **À partir d’un modèle Nova** et **Règles personnalisées**, deux entrées distinctes.
3. Depuis la première, choisir une recette dans la galerie des dix-sept modèles puis ouvrir ses réglages ; depuis la seconde, ouvrir directement l’éditeur personnalisé décrit ci-dessous.
4. Voir un **aperçu des morceaux correspondants et de leur durée**.
5. **Enregistrer la playlist**.

L’accès à la galerie est validé ; des premiers réglages, contrôles ou objectifs sont désormais acquis pour les dix-sept recettes détaillées ci-dessous, et les paramètres détaillés de l’ensemble restent à compléter. L’utilisateur a retenu dix modèles du [premier catalogue de quinze propositions](08-playlists-intelligentes-premium.md) — numéros 1, 2, 3, 4, 7, 8, 9, 10, 12 et 14 — puis Passerelle, Odyssée Nova, Caméléon, Atlas Nova et enfin A Résonance, B Fil Rouge et C Terrain d’Entente : **dix-sept concepts au total**, sans imposer la cible initiale de quinze. La [sélection courante](09-playlists-intelligentes-selection.md) conserve les repères de discussion sans les confondre avec la numérotation du catalogue initial. D Onde Continue reste à discuter plus tard, ni retenu ni rejeté ; Double Exposition est non retenu. La sélection seule ne valide pas les seuils, méthodes de calcul, moteurs d’analyse ou contrôles détaillés. L’accès à la galerie fait maintenant l’objet d’un choix distinct ; sa présentation détaillée et les paramètres de chaque recette ne sont pas approuvés par ce seul choix d’accès.

#### Accès aux Modèles Nova — validé, présentation détaillée restante

- Après le nom, **À partir d’un modèle Nova** ouvre la galerie des **dix-sept recettes retenues**. Ce sont des recettes de playlists, pas de nouveaux modèles d’IA.
- **Règles personnalisées** ouvre directement l’éditeur, sans imposer de passer par cette galerie. La galerie commune mélangeant les dix-sept recettes et une carte Personnalisée n’est pas retenue.
- Le nom, les prérequis, l’état de disponibilité et la présentation de chaque recette dans la galerie restent à préciser. L’accès validé ne garantit ni que les données sont présentes ni que les moteurs d’analyse ont été préparés.
- Ni les modèles non retenus du catalogue initial, ni Double Exposition, ni Onde Continue reporté ne sont ajoutés à cette galerie par défaut. Le choix d’une recette ne crée pas automatiquement les seize autres playlists.

#### Premiers réglages, contrôles et objectifs — premier tour des dix-sept recettes terminé

- **1 Signature Nova :** profil Équilibrée, 30 titres maximum, fenêtre récente de 30 jours, cibles de 10 favoris / 10 souvent écoutés / 10 moins explorés d’artistes ou de genres familiers ; pas de répétition du même fichier. Les cibles ne définissent pas trois blocs de lecture ni un remplissage garanti.
- **2 Diamants Cachés :** parmi les 10 artistes les plus écoutés sur 90 jours, titres de 0 à 5 écoutes dans l’historique NovaListen disponible et aucune écoute depuis 14 jours ; titres jamais joués admis.
- **3 Coup de Foudre :** références déduites des artistes des favoris ou titres souvent joués et des genres renseignés associés, remplaçables par des artistes ou genres locaux choisis ; candidats sans écoute NovaListen et rapprochement de métadonnées, sans similarité sonore.
- **4 Retour de Flamme :** au moins 5 écoutes entre 1 et 3 mois dans le passé, aucune depuis 14 jours, uniquement selon l’historique NovaListen réel.

- **5 Rendez-Vous** (catalogue initial 7) : 90 derniers jours, de 2 heures avant à 2 heures après l’heure actuelle, en distinguant lundi–vendredi et samedi–dimanche et en utilisant la même catégorie de jours que le jour actuel ; aucune géolocalisation.
- **6 Constellation** (catalogue initial 8) : artiste de départ choisi, 10 titres maximum sans répéter le même fichier ; terminer et expliquer le parcours plus court si aucune collaboration locale vérifiée ne mène à un nouvel artiste, sans retour automatique à un artiste déjà visité.
- **7 Panorama** (catalogue initial 9) : un représentant par album, EP ou mixtape local, éditions regroupées comptées une seule fois ; le plus écouté dans l’historique NovaListen disponible, sans priorité au cœur ; à égalité ou sans historique, premier admissible dans l’ordre de la liste unique de l’album.
- **8 Triptyque** (catalogue initial 10) : Apprécié → Discret → Jamais joué dans NovaListen ; trois fichiers distincts par trio ; garder uniquement les trios complets, expliquer si un rôle manquant réduit le nombre de groupes.

- **9 Backstage Pass** (catalogue initial 12) : live, acoustiques et remixes, uniquement lorsqu’ils sont identifiés de façon fiable ou confirmés ; véritables variantes conservées distinctes, sans modifier ni fusionner les fichiers.
- **10 Hors des Radars** (catalogue initial 14) : jusqu’à 10 artistes parmi les moins écoutés sur 90 jours dans NovaListen, y compris ceux à zéro ; 3 titres maximum par artiste ; signaler les égalités de données sans inventer de préférence.
- **11 Passerelle :** relier deux titres locaux choisis par 10 titres maximum, départ et arrivée compris ; artiste commun, collaboration identifiée ou genre renseigné commun autorisés ; aucune garantie de lien acoustique, harmonique ou de transition sonore.
- **12 Odyssée Nova :** cible de 25 titres, 5 par acte dans l’ordre Prologue → Élan → Détour → Apogée → Finale ; rôles narratifs de sélection, pas mesure d’émotion ou d’énergie ; aucun remplissage hors critères garanti.

- **14 Caméléon :** à chaque Plus comme ça / Moins de ça, choisir Ce titre, Un artiste crédité ou Un genre renseigné ; effet propre aux prochains résultats de cette playlist, sans modifier les cœurs, le masquage global, les autres listes ni sa file déjà lancée. Force et méthode d’adaptation restent ouvertes.
- **15 Atlas Nova :** proposer les trois découpages, par artistes, albums locaux (avec les regroupements d’éditions acquis) ou genres réellement renseignés. La réponse « Les 3 » ne choisit pas un mode initial unique et ne décide pas de les mélanger automatiquement. Mesure de progression encore ouverte.
- **A Résonance :** de 1 à 3 morceaux locaux de référence pour viser une proximité sonore globale ; moteur, combinaison des références, seuils et qualité restent à étudier. Aucun téléchargement ou essai n’est autorisé.
- **B Fil Rouge :** viser un thème central ou secondaire réellement développé dans les paroles ; une allusion vague ou un mot isolé ne suffit pas. Objectif de pertinence, pas capacité démontrée du duo choisi ni seuil numérique validé ; parcours de téléchargement et de préparation acquis séparément.

- **C Terrain d’Entente :** deux ensembles de 1 à 5 références chacun, choisis sur le même téléphone ; candidats parmi les références et les autres titres locaux des mêmes artistes crédités, pas par genre seul. Cible initiale modifiable de 30 titres : 10 communs / 10 univers 1 / 10 univers 2, sans remplissage hors critères garanti ; un fichier commun n’est compté qu’une fois. Sans point commun fiable, demander de modifier les références avant de générer, sans remplacer ce choix par un équilibre 50 / 50. Définition exacte du commun, affectation des titres et ordre restent ouverts.

Ces réglages de départ sont **modifiables**. Les contrôles et objectifs explicitement acquis ne constituent pas des méthodes de calcul déjà validées. Le catalogue initial et la sélection courante précisent leur portée et les compléments ouverts : cas limites de la comptabilisation commune, historique suffisant, chevauchements et frontières temporelles, crédits vérifiés, scores, départages non fixés, critères des rôles, ordres entre groupes, progression, adaptation, analyses et contrôles détaillés. Ne pas généraliser le plafond ou l’unicité de Signature Nova ou Constellation aux autres modèles ; Triptyque impose trois fichiers distincts dans chaque trio, pas encore entre trios ; Terrain d’Entente ne compte qu’une fois un fichier commun aux deux ensembles. Ces accords n’établissent pas une déduplication générale de toutes les intelligentes. Ne pas modifier une file déjà lancée. Les questions 29 à 32 sur l’éditeur personnalisé restent reportées, sans validation.

#### Comptabilisation et historique communs — réponses 53 à 68 acquises

Les règles détaillées figurent dans l’[Accueil](01-accueil.md) : **1 minute / 60 secondes réellement jouées cumulées dans un passage**, portions réécoutées incluses, sans temps ajouté par les pauses ou sauts vers l’avant ; **une écoute maximum par passage**, chaque nouveau passage suffisant pouvant compter, y compris une boucle Répéter. Un déplacement du curseur seul ne crée pas un nouveau passage.

Pour les recettes, **Dernière écoute, Jamais joué et les délais d’absence utilisent seulement les écoutes validées**. Une pause simple conserve le passage et le cumul, sans deuxième comptage d’un passage déjà validé. **Horodater au seuil atteint** ; conserver localement les événements validés **sans expiration automatique**. Les lectures non validées ne modifient pas ces données et ne créent pas d’entrée durable dans l’historique consultable. Après arrêt complet de l’application, une éventuelle reprise est un **nouveau passage statistique**, à zéro vers le seuil, sans autoriser une lecture automatique ou valider la restauration de position. Les fenêtres de N jours utilisent les dates locales : aujourd’hui et les N−1 dates précédentes, depuis minuit du premier jour jusqu’à maintenant ; les regroupements historiques gardent les dates et heures dans le fuseau de chaque validation, pas celui du téléphone après un changement ultérieur. Pour Retour de Flamme, la fenêtre M−3 à M−1 inclut ses deux dates limites, avec dernier jour du mois cible si nécessaire ; les autres usages des mois restent à définir.

Les événements de fichiers devenus inaccessibles sont **conservés**, leurs lignes **masquées dans l’historique normal avec signalement** et leur **contribution aux bilans et compteurs historiques maintenue**. Cela ne modifie ni les compteurs de morceaux disponibles ni l’éligibilité à une nouvelle lecture. La contribution des fichiers volontairement masqués ou hors filtre reste distincte ; aucune fusion d’identités n’est décidée.

**Filtre local de durée acquis :** exclure les fichiers de durée connue inférieure à 60 secondes des résultats, compteurs disponibles et nouvelles lectures, classiques comme intelligentes, sans effacer l’audio. Ne pas assimiler ces fichiers à des fichiers physiquement indisponibles ou à Cacher. Une durée inconnue est signalée en attente dans la Bibliothèque, hors compteurs disponibles et sans lecture avant identification ; aucun résultat ou bloc jouable ne doit l’ajouter provisoirement. L’identification fiable, les réévaluations, les anciennes références, l’effacement volontaire et les effets sur une file déjà lancée restent ouverts ; aucune purge automatique n’est décidée. Ces choix ne réintroduisent pas de critères d’historique dans l’éditeur personnalisé.

#### Éditeur de règles personnalisées — socle validé

**Sept familles de critères retenues :**

| Famille | Portée du choix acquis |
| --- | --- |
| Artiste | Artiste musical renseigné ; opérateurs et gestion des crédits multiples encore ouverts. |
| Album | Album renseigné ; articulation détaillée avec les groupes d’éditions encore ouverte. |
| Favori | État du cœur du morceau ; opérateurs à préciser. |
| Genre | Genre réellement renseigné, sans inventer un classement sonore. |
| Année musicale | Année issue des métadonnées musicales, distincte de la date d’ajout. |
| Durée du morceau | Durée individuelle du fichier, pas plafond de durée totale de la playlist. |
| Date d’ajout | Date d’entrée dans la bibliothèque ; source exacte et opérateurs encore à préciser. |

- **Toutes les conditions sont obligatoires.** Pas de mode Au moins une condition / OU et pas de groupes imbriqués dans cet éditeur, conformément au choix de simplicité.
- **Taille : tous les résultats ou un nombre maximal de titres**, modifiable dans les règles. Aucun plafond de durée totale n’est retenu ici. La valeur initiale, les bornes admises et la sélection des titres lorsque le plafond est atteint restent à préciser.
- **Pas de critères de nombre d’écoutes, dernière écoute ou jamais écouté dans cet éditeur personnalisé.** Cette décision ne retire ni l’historique de NovaListen ni les recettes qui s’appuient explicitement sur les écoutes.
- Toujours exclure les morceaux masqués ou inaccessibles avant d’appliquer les règles. **Aucune commande Aléatoire**, comme pour toutes les intelligentes.
- **Restent ouverts :** opérateurs par famille, valeurs et données manquantes, ordre des résultats et départages, conditions valides sans résultat, présentation et actualisation de l’aperçu.

#### Ne pas confondre éditeur personnalisé et réglages des recettes

Les modèles avancés peuvent demander quotas, classements, parcours, références, analyses ou contraintes propres à leur intention : ces capacités ne sont pas réalisées par le seul éditeur personnalisé. Son choix Toutes les conditions et son plafond facultatif de titres ne réduisent pas implicitement les dix-sept concepts à ces seuls contrôles. Leurs réglages se conçoivent séparément ; les réglages de départ explicitement acquis sont détaillés ci-dessus, et leurs compléments comme ceux des autres recettes restent ouverts.

Une liste intelligente est définie par ses règles. **Décision validée : l’ajout manuel direct via Ajouter à une playlist est réservé aux classiques**, avec création rapide possible d’une classique. Aucun ajout forcé dans une intelligente n’est introduit par ce parcours. Une éventuelle action distincte de conversion ou de fixation d’un résultat en classique n’est pas décidée ; Dupliquer conserve déjà le type intelligent.

## 4. Page d’une playlist — interface et comportements de lecture validés, modalités restantes

L’utilisateur valide l’organisation présentée et corrige la portée d’Aléatoire : **la commande est absente de toutes les playlists intelligentes**, sans exception, et pas seulement de celles construites autour d’un parcours. La validation porte sur l’interface et les rôles décrits ci-dessous ; elle ne choisit ni stack, ni moteur de modèle, ni palette ou dimensions définitives.

### Structure commune validée, de haut en bas

1. **Retour**, pour retrouver l’écran précédent sans lancer de lecture.
2. **Grande couverture carrée arrondie**, reprenant la mosaïque ou l’image personnalisée de la carte. Les dimensions précises restent à définir.
3. **Nom de la playlist**, avec badge **Intelligente** pour une liste à règles.
4. **Nombre de titres locaux disponibles non masqués et durée correspondante** ; description personnelle facultative.
5. Pour une intelligente seulement : **nom du modèle, s’il y en a un, et bref résumé de sa logique**, sans exposer tous les réglages dans l’en-tête.
6. **Classique : Tout lire · Aléatoire · ⋮. Intelligente : Tout lire · ⋮**, sans bouton ni entrée Aléatoire.
7. **Action propre au type** : Ajouter des morceaux pour une classique ; Modifier les règles et une action secondaire Recalculer pour une intelligente.
8. **Liste des morceaux**, à défilement vertical.

Le mini-player habituel reste disponible au-dessus de la navigation, selon ses règles déjà définies. Ouvrir ou parcourir la page reste sans effet sur la lecture active.

### Commandes de la page — rôles validés

| Commande | Effet retenu | Ce qu’elle ne fait pas |
| --- | --- | --- |
| Retour | Revenir à l’écran précédent. | Ni arrêter ni relancer la musique. |
| Tout lire | Lancer la liste locale admissible dans son ordre. | Ne pas réécrire l’ordre enregistré ou les règles. |
| Aléatoire — classique uniquement | Lire les mêmes titres admissibles dans un ordre mélangé dans la file. | Ne pas changer l’affichage ou l’ordre sauvegardé ; ne pas apparaître sur une intelligente. |
| ⋮ | Ouvrir les six actions de la playlist retenues en section 5. | Ne pas lancer de lecture par le seul fait d’ouvrir le menu. |
| Ajouter des morceaux — classique | Ouvrir une sélection de titres locaux admissibles, insérer les nouveaux choix au début dans leur ordre et ignorer les fichiers déjà présents avec information. | Ne pas copier les fichiers audio, déplacer les entrées ignorées, fusionner de vraies variantes ou modifier la file déjà lancée. |
| Modifier les règles — intelligente | Retrouver les paramètres actuels, voir un aperçu, puis enregistrer explicitement les modifications. | Ne pas changer silencieusement une file déjà lancée. |
| Recalculer — intelligente | Refaire l’aperçu à partir des règles et des données disponibles. | Ni lancer la lecture, ni injecter des titres dans la file active, ni garantir un résultat différent. |

La création intelligente suit désormais nom, choix distinct Modèle Nova ou Règles personnalisées, réglages, aperçu puis enregistrement. Le socle personnalisé de la section 3 est acquis ; les opérateurs, les écrans détaillés et les compléments aux paramètres propres aux recettes restent à concevoir au-delà des réglages de départ acquis ci-dessus.

### Classique et intelligente — différences validées

| Élément | Classique | Intelligente |
| --- | --- | --- |
| Origine des titres | Choix manuels de l’utilisateur. | Résultat des règles et du modèle éventuel. |
| Action principale de modification | Ajouter des morceaux. | Modifier les règles. |
| Ordre manuel | Réorganisation par poignée dédiée. | Pas de poignée sur les résultats calculés. |
| Retrait individuel | Retirer de cette playlist, après confirmation, sans effacer le fichier. | Pas de retrait manuel permanent générique ; modifier les règles. La portée des retours Caméléon est acquise en section 3 ; leur méthode et leur interface détaillée restent à concevoir. |
| Aléatoire | Disponible. | Absent, quel que soit le modèle ou la règle personnalisée. |
| Mise à jour | Après modification de la liste et prise en compte de la disponibilité/du masquage. | Résultat recalculable ; résolution des règles au lancement déjà prévue. |

Les dix-sept concepts retenus sont des **modèles possibles**, pas dix-sept playlists déjà créées. Une liste intelligente personnalisée peut également ne pas provenir d’un modèle nommé : elle suit la même absence d’Aléatoire.

### Présentation des lignes — validée ; interactions restantes précisées séparément

- Reprendre la présentation de la [Bibliothèque](02-bibliotheque.md) : **pochette, titre, artiste, informations musicales, durée, cœur et ⋮**. Les dimensions restent à préciser ; cela ne tranche pas le choix pochette ou numéro laissé ouvert pour les lignes d’album.
- Le cœur garde le sens du favori du morceau ; il n’est pas un retour Plus comme ça propre à une playlist.
- Le ⋮ de ligne concerne le morceau, pas la gestion de la playlist entière. Pour une classique, **Retirer de cette playlist** demande confirmation puis enlève l’entrée, sans supprimer son fichier ni la retirer des autres listes.
- Pour réordonner une classique, utiliser une **poignée de déplacement dédiée** : ne pas transformer implicitement l’appui long de sélection en geste de réorganisation.
- Retirer, Cacher globalement et Effacer réellement un fichier sont trois opérations différentes. Le retrait local ne doit pas effacer l’audio ; le masquage global conserve ses effets acquis.
- Les trois gestes des cartes et lignes et les catégories d’actions en lot sont désormais validés en section 5 ; le contenu précis des informations et les modalités détaillées de sélection restent à définir.

### Lecture et ordre — règles confirmées

- **Tout lire** et le petit bouton Lecture d’une carte lancent les titres locaux admissibles selon l’ordre de la playlist. **Toucher un morceau** charge toute la playlist admissible dans son ordre, avec ce morceau actif ; les titres précédents restent dans la file, accessibles en revenant en arrière. Ces trois lancements remplacent directement la file, sans confirmation.
- Pour une **classique uniquement**, **Aléatoire** utilise le même ensemble, mélangé, sans modifier l’ordre enregistré ou l’affichage.
- **Aucune commande Aléatoire pour une intelligente**, qu’elle provienne d’un des dix-sept modèles ou de conditions personnalisées. Ce n’est pas un bouton désactivé ni une action déplacée dans le menu : la commande est absente. La règle vaut aussi pour ses accès depuis l’Accueil et son contexte de lecture.
- Une lecture lancée depuis une intelligente ne doit pas être mélangée silencieusement par un mode aléatoire hérité d’une autre source. L’intégration au player doit respecter cette contrainte ; la mémorisation des modes entre sources et les files ensuite mixtes restent à concevoir.
- **Recalculer n’est pas un mode de lecture aléatoire** : il réévalue les règles et respecte la logique du modèle. Il ne garantit pas une nouvelle sélection. Les choix et départages internes des recettes restent à définir ; l’absence de la commande ne décide pas à elle seule de tous leurs algorithmes.
- Pour une intelligente, **résoudre les règles au lancement**, conformément au principe déjà décrit sur l’Accueil. Des analyses coûteuses peuvent être préparées auparavant ; cela ne dispense jamais de revérifier disponibilité et masquage avant de construire la file.
- Un changement de règles ou un recalcul d’aperçu ne réorganise pas silencieusement une file déjà en lecture. Cela ne suspend pas le masquage global : cacher le titre actif impose toujours le passage immédiat au prochain titre non masqué ou l’arrêt.

### Constitution et évolution de la file — décisions validées

- **Toucher un morceau :** prendre toute la playlist locale admissible comme file, dans son ordre, avec ce morceau actif. Les morceaux placés avant lui restent dans cette file ; ils ne sont pas lus immédiatement mais restent accessibles via Précédent. Ne pas réduire la file au seul titre touché ou à sa seule suite.
- **Remplacement sans confirmation :** Tout lire, le petit bouton Lecture d’une carte et le toucher d’un morceau remplacent directement la file existante et démarrent la nouvelle lecture. Consulter la page ou la carte hors bouton Lecture reste sans effet sur la file. L’éventuelle confirmation des autres sources, notamment Albums, n’est pas décidée par cette réponse propre aux playlists.
- **Lire ensuite pendant une lecture :** placer chaque nouvelle demande immédiatement après le titre actif, sans l’interrompre. La demande la plus récente passe donc avant les demandes antérieures encore en attente. Pour un ajout de plusieurs titres, conserver leur ordre interne ; ne pas inverser le bloc. Les autres demandes antérieures sont conservées, sous réserve du déplacement des occurrences du même fichier défini ci-dessous.
- **Lire ensuite sans morceau chargé :** préparer les titres concernés comme file **en pause**, dans leur ordre, sans lancer de musique. L’utilisateur appuie ensuite sur Lecture pour démarrer. Une lecture simplement en pause avec un morceau chargé n’est pas ce cas d’absence de morceau actif.
- **Même fichier déjà prévu parmi les titres à venir :** retirer ses occurrences encore à venir, puis le programmer **une seule fois à sa nouvelle place** dans la demande Lire ensuite, en conservant l’ordre interne du nouveau bloc. Ne pas modifier le morceau actif ni les écoutes passées. Cette décision porte sur le même fichier, pas sur une fusion de variantes ou de fichiers distincts.
- **Lire ensuite de plusieurs playlists sélectionnées :** assembler un seul bloc dans **l’ordre affiché de la grille, de gauche à droite puis de haut en bas**, selon le tri affiché, en conservant l’ordre interne de chaque playlist. Insérer ce bloc après le titre actif sans inverser les listes ; ne pas suivre la chronologie des touchers de sélection. Sans morceau chargé, préparer ce bloc en pause.
- **Modifier une classique pendant son écoute :** enregistrer ajouts, retraits et réorganisation dans la playlist, sans modifier la sélection ni l’ordre de la file déjà lancée. Ces changements s’appliquent à un lancement ultérieur. Retirer de la playlist le titre en cours ne l’arrête donc pas, tant qu’il reste lisible et non masqué ; une action explicite Lire ensuite reste une action sur la file, pas une modification de la classique.
- **Supprimer la playlist d’origine de la session :** après suppression effective de cette liste, **arrêter la lecture et vider toute la file de cette session**. Cette décision vaut pour les classiques et les intelligentes, y compris si des titres ont été ajoutés à leur file par Lire ensuite. Ne pas la confondre avec un ajout, retrait d’entrées, réorganisation ou recalcul : ces modifications laissent la file déjà lancée intacte. Une suppression annulée avant validation ne supprime pas la source. **Annuler après la suppression d’une seule playlist restaure uniquement la playlist enregistrée : la file reste vide et la lecture ne reprend pas.** Un nouveau lancement explicite reste nécessaire.
- **La file est un instantané de sélection et d’ordre, pas une copie de l’audio ni une exemption d’admissibilité :** le masquage global conserve tous ses effets. Cacher le titre actif provoque toujours le passage immédiat au prochain non masqué ou l’arrêt. Conserver une référence en file ne rend pas lisible un fichier supprimé ou devenu inaccessible.
- Dans les classiques, un nouvel ajout du même fichier déjà présent est ignoré avec information. Cette règle de contenu reste distincte du déplacement des occurrences à venir lors de Lire ensuite, désormais acquis. Les occurrences internes aux autres résultats intelligents, hors unicité du même fichier acquise pour Signature Nova et Constellation, distinction des trois fichiers dans chaque trio de Triptyque et comptage unique d’un fichier commun dans Terrain d’Entente, les recouvrements entre plusieurs playlists d’un même nouveau bloc et l’identité entre fichiers distincts restent à préciser ; ne pas appliquer implicitement la règle des albums fusionnés.

**Restent ouverts :** occurrences communes à plusieurs playlists d’un même nouveau bloc, autres cas de répétitions, gestion manuelle de la file et interactions fines avec les modes de lecture. La préparation en pause sans morceau chargé, le déplacement des occurrences déjà à venir, l’ordre de grille en lot et la restauration de la seule playlist par Annuler sont acquis. Le recalcul d’une intelligente reste sans injection ni réorganisation de la file active. Les décisions de lecture acquises pour Albums ne sont pas modifiées implicitement.

### États de la page — références indisponibles validées et autres recommandations ouvertes

- **Classique créée vide :** invitation Ajouter des morceaux, lecture indisponible tant qu’aucun titre admissible n’existe.
- **Références de fichiers indisponibles dans une classique — acquis :** conserver les références mais **masquer leurs lignes dans la liste normale**, en signalant leur existence. Ne pas les lire ni les inclure dans les compteurs et durées disponibles. Une classique dont aucun fichier n’est accessible n’est pas pour autant une liste dont les références ont été supprimées. Un morceau masqué globalement reste invisible selon sa règle distincte ; ne pas l’annoncer à tort comme fichier indisponible.
- **Intelligente sans résultat :** distinguer conditions sans correspondance, historique insuffisant, relations manquantes et analyses non préparées. Proposer l’action adaptée plutôt que remplir avec des titres hors critères.
- **Calcul en cours :** état visible ; ne pas afficher comme actuels un compteur et une durée issus d’un résultat antérieur non identifié. La conservation et la présentation éventuelles d’un ancien aperçu restent à choisir.
- **Accès aux fichiers refusé ou révoqué :** état explicite, distinct d’une bibliothèque réellement vide.

Le fonctionnement d’éventuelles analyses locales et leurs coûts sont examinés dans la [première étude de faisabilité des modèles](10-playlists-faisabilite-technique.md). Aucun recalcul de cette page n’autorise implicitement un téléchargement de modèle, une récupération de paroles ou un transfert de fichiers vers un service distant.

## 5. Menu et gestes validés ; précisions restantes

### Menu ⋮ — six actions retenues, précisions ouvertes

- **Lire ensuite** — insérer les titres locaux admissibles de la liste, dans leur ordre, immédiatement après le titre actif, sans l’interrompre. Une nouvelle demande passe avant les précédentes ; si le même fichier est déjà à venir, déplacer ses occurrences à venir pour le programmer une seule fois à la nouvelle place, sans toucher au titre actif ni au passé. Sans morceau chargé, préparer la file en pause, sans démarrer la musique.
- **Renommer** — modifier le nom de la playlist, pas les métadonnées des morceaux.
- **Changer la couverture** — personnalisation et accès depuis ce menu validés, sans écraser les pochettes des fichiers audio.
- **Épingler à l’Accueil / Désépingler** — accès depuis ce menu validé, selon la capacité d’épinglage déjà prévue.
- **Dupliquer** — créer une autre liste, pas dupliquer les fichiers audio. **Pour une intelligente : copier le modèle de playlist, les règles et les réglages vers une nouvelle intelligente**, recalculable et toujours sans Aléatoire ; ne pas figer son résultat dans une classique par cette action. Le nom de copie, la couverture et le traitement de données propres à certaines recettes, comme les retours Caméléon, restent à préciser. Le choix de copier les règles ne duplique pas physiquement les ressources des moteurs d’analyse.
- **Supprimer la playlist** — supprimer la liste, **pas les fichiers audio**. Pour une seule : suppression directe avec Annuler temporaire ; pour plusieurs : confirmation préalable. Si la liste supprimée est à l’origine de la session active, arrêter la lecture et vider sa file. Ce libellé reste distinct d’Effacer un morceau ; les garde-fous sont détaillés ci-dessous.

Ajouter des morceaux et Modifier les règles sont validés sur la page. Leur duplication éventuelle dans le menu n’est pas fixée. Aucun menu de playlist intelligente ne propose Aléatoire. Aucune carte Favoris n’est prévue dans cet onglet ; ne pas en réintroduire une par ce menu.

### Gestes validés dans l’onglet Playlists

- Sur une carte : droite → Lire ensuite ; gauche → Informations sur la playlist ; appui long → sélection multiple de cartes.
- Sur une ligne de morceau : reprendre droite Lire ensuite, gauche Informations et appui long de sélection multiple de morceaux.
- Les informations exactes d’une playlist et les modalités détaillées de sélection restent à définir ; les actions en lot sont désormais validées ci-dessous.
- Ces gestes sont **explicitement validés pour cet onglet**, sans remplacer l’appui long de gestion des playlists épinglées de l’Accueil. La poignée de réorganisation reste propre aux classiques ; ne pas confondre appui long de sélection et déplacement.

### Sélection multiple de cartes — actions validées

- **Lire ensuite** : assembler les playlists sélectionnées en un seul bloc dans **l’ordre affiché de la grille, de gauche à droite puis de haut en bas**, en conservant l’ordre interne de chaque liste. Placer ce bloc après le titre actif, sans l’interrompre ; déplacer les occurrences du même fichier déjà à venir selon la règle commune. Sans morceau chargé, préparer le bloc en pause. Les occurrences communes entre plusieurs listes à l’intérieur du même nouveau bloc restent à préciser.
- **Épingler à l’Accueil / Désépingler** : agir sur les playlists sélectionnées. Le cas d’une sélection mêlant listes déjà épinglées et non épinglées reste à préciser.
- **Dupliquer** : dupliquer les listes sélectionnées en conservant le type de chacune ; une intelligente reste intelligente avec son modèle de playlist, ses règles et ses réglages, recalculable et sans Aléatoire. Aucun fichier audio n’est copié.
- **Supprimer les playlists** : supprimer les listes sélectionnées, jamais leurs fichiers audio. **Demander confirmation si plusieurs listes sont concernées** ; si la sélection ne contient qu’une liste, suppression directe avec Annuler temporaire. Si la playlist d’origine de la session fait partie des suppressions effectives, arrêter la lecture et vider la file.

Cet accord ne crée ni Aléatoire pour une sélection contenant des intelligentes, ni effacement des fichiers des listes. Il ne décide pas des actions de sélection de cartes d’artistes ou d’albums.

### Sélection multiple de morceaux — actions validées

Dans une playlist, proposer les quatre actions de la Bibliothèque :

1. **Lire ensuite**, sans interrompre le titre actif.
2. **Ajouter à une playlist**, via le sélecteur de classiques avec destinations multiples et création rapide. Les nouveaux titres sont placés au début de chaque classique dans leur ordre, avec ignorance des ajouts déjà présents.
3. **Cacher**, globalement et de façon réversible. Si la sélection comprend le titre actif, appliquer immédiatement le passage au prochain non masqué ou l’arrêt.
4. **Effacer**, suppression réelle des fichiers audio du téléphone avec confirmation, distincte d’un retrait de playlist.

**Dans une classique seulement**, ajouter **Retirer de cette playlist** : **demander confirmation**, puis retirer les entrées sélectionnées de cette liste, sans supprimer les fichiers ni les retirer des autres listes, et sans modifier la file déjà lancée. Ne pas proposer ce retrait manuel générique dans les résultats intelligents ; les retours propres à Caméléon restent distincts.

Le fonctionnement détaillé du mode sélection, Tout sélectionner et l’ordre de traitement en lot restent à définir. La confirmation de tout retrait est acquise. Ne pas confondre les actions sur des morceaux et la suppression des cartes de playlists.

### Suppression de listes et retrait d’entrées — garde-fous validés

- **Une seule playlist :** suppression sans confirmation préalable, suivie d’une possibilité temporaire **Annuler** pour restaurer **la playlist uniquement**. Si sa suppression a arrêté la session et vidé la file, Annuler ne rétablit ni cette file, ni le morceau et sa position ; il ne relance pas la musique.
- **Plusieurs playlists :** confirmation préalable, puis Supprimer / Annuler. Le contenu détaillé de cette confirmation reste à préciser. Ne rien supprimer si l’utilisateur annule cette confirmation. Aucune restauration après suppression en lot n’est ajoutée implicitement par ce choix.
- **Retirer un ou plusieurs morceaux d’une classique :** toujours demander confirmation avant de retirer les références de cette liste, sans effacer l’audio, toucher aux autres listes ou modifier la file en cours. Annuler la confirmation conserve les entrées. Ce garde-fou n’ajoute pas une commande de retrait manuel dans les intelligentes.
- **Playlist d’origine de la session supprimée :** arrêter la session et vider sa file, sans supprimer les fichiers. Ce n’est pas une exception au masquage global ni une nouvelle règle sur Effacer un fichier.
- **Effacer un morceau :** la confirmation de suppression réelle des fichiers audio demeure obligatoire, pour un morceau comme pour une sélection. Elle est distincte des confirmations de suppression de listes ou de retrait local.

**Reste à préciser :** durée de la possibilité temporaire Annuler. Son effet est acquis : restaurer la playlist enregistrée seulement, sans rétablir la session arrêtée ou sa file ; l’utilisateur doit relancer explicitement une lecture.

## 6. Sauvegarde, références et états particuliers

Les décisions acquises sont signalées ; les autres points restent des recommandations ou des arbitrages ouverts :

- Conserver les noms, couvertures, règles et, pour les classiques, le contenu et l’ordre choisis, pour les retrouver hors ligne après fermeture. Aucun format de stockage technique n’est choisi.
- Le [regroupement des éditions d’un album](06-albums.md) ne déduplique pas automatiquement les entrées d’une playlist et ne réécrit pas ses références. L’ajout répété du même fichier est ignoré dans une classique avec information ; l’identité entre enregistrements ou fichiers distincts et les occurrences éventuelles dans les autres résultats intelligents restent à définir. Signature Nova et Constellation ne répètent pas le même fichier selon leurs réglages acquis ; Triptyque impose trois fichiers distincts dans chaque trio, sans décider encore les recouvrements entre trios ; Terrain d’Entente compte une seule fois un fichier commun aux deux ensembles. Aucun de ces accords ne fusionne les variantes ou les fichiers distincts.
- **Décision validée pour une classique :** conserver les références des fichiers supprimés ou devenus inaccessibles, masquer leurs lignes de la liste normale et signaler la présence de références indisponibles. Ne pas nettoyer automatiquement la liste, ne pas prétendre que ces fichiers restent lisibles et ne pas les inclure dans les compteurs disponibles. Le détail du signalement, l’accès à leur gestion et la reconnaissance d’un fichier déplacé ou revenu restent à concevoir.
- Respecter le masquage global dans les compteurs et les nouvelles lectures ; ne pas supprimer implicitement les références ou les historiques par le seul fait de cacher un morceau.
- Distinguer aucune playlist, playlist classique vide, intelligente sans résultat, absence d’historique pour une règle statistique, calcul en cours et accès aux fichiers refusé.
- Une modification de nom ou de couverture de playlist ne doit pas devenir une édition des métadonnées des fichiers audio.
- Import, export, partage de listes, formats de fichiers et synchronisation éventuelle ne sont pas choisis par cette première proposition.

## 7. Choix encore à discuter

- Dimensions et espacements de la grille, éventuelle indication d’épinglage et visuel d’une playlist sans morceau ; le contenu des cartes et la personnalisation de leur couverture sont validés.
- Départage des égalités et valeurs manquantes dans les tris. Les quatre critères inversables (nom, création, nombre de titres, durée), le tri initial Nom A → Z puis dernier choix mémorisé, Toutes au départ puis filtre mémorisé et l’absence d’accès Favoris dans cet onglet sont acquis.
- Opérateurs, ordre et départages, valeurs manquantes et présentation de l’éditeur personnalisé ; présentation détaillée de la galerie, compléments aux réglages, contrôles et objectifs explicitement acquis ci-dessus et faisabilité technique de l’ensemble. Le choix distinct Modèle Nova / Règles personnalisées, les sept familles sans historique, Toutes les conditions et tous les résultats ou nombre maximal de titres sont acquis. Présentation du sélecteur Ajouter à une playlist et détail du bilan par destination restent à préciser. Les destinations classiques multiples, la création rapide et l’insertion des nouveaux titres au début dans leur ordre sont validées. Onde Continue est à reprendre ultérieurement, sans décision d’inclusion. Les deux parcours de création depuis + restent acquis.
- Occurrences communes entre plusieurs playlists d’un même nouveau bloc, autres répétitions, gestion manuelle de la file, interactions fines avec les modes et présentation détaillée des états de calcul. Sont acquis : playlist entière avec morceau touché actif ; remplacement direct sans confirmation ; Lire ensuite après le titre actif avec déplacement des occurrences du même fichier déjà à venir ; ordre affiché de la grille pour plusieurs listes ; préparation en pause sans morceau chargé ; modifications d’une classique sans effet sur sa file lancée ; suppression de sa source arrêtant et vidant la session, sans restauration de file par Annuler. La page et l’absence d’Aléatoire sur toutes les intelligentes restent acquises.
- Contenu des Informations, fonctionnement détaillé de la sélection multiple, gestion des épinglages mixtes, nom/couverture et données propres aux copies, durée d’Annuler. Les gestes, catégories d’actions en lot de la section 5, six entrées du menu et garde-fous de suppression/retrait sont retenus ; Dupliquer une intelligente conserve son type et ses règles.
- Détail du signalement et de la gestion des références indisponibles, reconnaissance de fichiers déplacés ou revenus. Leur conservation avec lignes masquées et signalement dans une classique est acquise, comme l’insertion en tête et l’ignorance des ajouts répétés. Supprimer la playlist d’origine de la session arrête celle-ci et vide sa file ; cela ne signifie jamais effacer l’audio.

**Acquis, à ne pas redemander :** fondations déjà décrites ; socle commun des réponses 53 à 68 : seuil d’une minute, cumul réellement joué, une écoute maximum par passage, boucles admissibles, pause simple conservant le passage, horodatage à la validation, seules écoutes validées pour les statuts historiques, aucun effacement automatique lié à l’âge et exclusion locale des fichiers de durée connue inférieure à 60 secondes sans effacement audio ; durée inconnue en attente hors compteurs et sans lecture, nouveau passage après arrêt complet, aucune entrée durable de lecture partielle et fenêtres en jours calendaires locaux aujourd’hui inclus, fuseau à la validation, dates M−3 à M−1 incluses de Retour de Flamme et maintien des événements et contributions historiques des fichiers inaccessibles avec lignes normales masquées et signalement ; premiers réglages, contrôles ou objectifs des dix-sept recettes détaillées en section 3, sans les confondre avec des recettes entièrement spécifiées ; après le nom d’une intelligente, accès distinct À partir d’un modèle Nova (galerie des dix-sept recettes) ou Règles personnalisées (éditeur direct) ; sept familles personnalisées artiste / album / favori / genre renseigné / année musicale / durée individuelle / date d’ajout, sans critères d’historique ; Toutes les conditions, sans OU ni groupes imbriqués ; tous les résultats ou nombre maximal de titres, sans plafond de durée totale dans cet éditeur ; Lire ensuite sans morceau chargé préparant une file en pause sans démarrage ; déplacement des occurrences du même fichier déjà à venir vers sa nouvelle place, sans toucher au titre actif ni au passé ; assemblage en lot selon l’ordre affiché de la grille (gauche à droite puis haut en bas), en un bloc conservant l’ordre interne des listes ; Annuler restaurant la seule playlist, sans sa file ni reprise de lecture ; suppression directe d’une seule playlist avec Annuler temporaire, confirmation avant suppression de plusieurs listes ; confirmation de tout retrait d’entrées d’une classique ; références indisponibles conservées avec lignes masquées et signalement, hors compteurs disponibles ; suppression de la playlist d’origine de la session entraînant arrêt et vidage de sa file, distincte des modifications qui la conservent ; plusieurs classiques destinataires par opération, avec vérification des ajouts répétés séparée pour chacune ; nouveaux morceaux placés au début dans leur ordre, sans déplacer les fichiers déjà présents ; actions en lot sur cartes Lire ensuite / Épingler–Désépingler / Dupliquer en conservant le type / Supprimer les listes sans effacer l’audio ; actions en lot sur morceaux Lire ensuite / Ajouter à une classique / Cacher globalement / Effacer les fichiers avec confirmation, plus Retirer de cette playlist dans une classique seulement ; quatre tris inversables nom / création / nombre de titres / durée, sans date de modification ; filtre Toutes au départ puis dernier choix mémorisé même après fermeture ; aucun accès Favoris dans cet onglet, sans suppression des cœurs ou des favoris ; Ajouter à une playlist vers les classiques avec création rapide d’une classique et ajout des morceaux concernés ; playlist entière avec le morceau touché actif ; remplacement direct de la file sans confirmation par Tout lire, Lecture de carte et toucher d’un morceau ; nouvelle demande Lire ensuite immédiatement après le titre en cours, avec ordre interne conservé ; ajouts, retraits et réorganisation d’une classique sans effet sur la file déjà lancée, sous réserve du masquage et de la disponibilité des fichiers ; tri initial Nom A → Z puis dernier tri mémorisé ; gestes droite Lire ensuite / gauche Informations / appui long de sélection multiple sur cartes et morceaux de cet onglet, sans changement sur l’Accueil ; ajout répété du même fichier ignoré avec information dans une classique ; duplication d’une intelligente par copie du modèle de playlist, des règles et des réglages dans une nouvelle intelligente ; grille à deux playlists par ligne ; en-tête avec compteur, +, Recherche par nom et bouton Tri ; filtres Toutes / Classiques / Intelligentes uniquement lorsque les deux types sont présents ; cartes et couverture personnalisable selon la section 2, avec ouverture sans lecture distincte du bouton Lecture et sans modification des pochettes des fichiers audio ; parcours de création classique et intelligente de la section 3 ; organisation de la page, commandes par type, présentation des lignes, réorganisation des classiques par poignée, retrait sans suppression audio et six actions du menu ; **absence d’Aléatoire sur toutes les intelligentes, disponible sur les classiques** ; dix-sept concepts de playlists intelligentes retenus dans la sélection courante, dont Résonance, Fil Rouge et Terrain d’Entente. Onde Continue reste à discuter plus tard, sans être retenu ni rejeté. Les réglages détaillés et les autres propositions propres à cet onglet attendent des précisions ou une validation ; aucune interface ni fonction n’est implémentée par cette fiche.
