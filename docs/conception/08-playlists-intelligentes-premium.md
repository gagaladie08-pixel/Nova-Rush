# NovaListen — Catalogue initial des 15 modèles de playlists intelligentes « premium »

> **Statut :** catalogue initial de conception, conservé sans renuméroter ses modèles et sans implémentation. L’utilisateur a retenu les numéros **1, 2, 3, 4, 7, 8, 9, 10, 12 et 14**. Les autres ne sont pas retenus dans la sélection courante. La [sélection mise à jour](09-playlists-intelligentes-selection.md) ajoute sept concepts retenus — Passerelle, Odyssée Nova, Caméléon, Atlas Nova, Résonance, Fil Rouge et Terrain d’Entente — soit dix-sept au total. Onde Continue est à discuter plus tard, ni retenu ni rejeté ; Double Exposition reste non retenu. Le nombre quinze décrit ce catalogue initial, pas une limite de la sélection courante. Des premiers réglages, contrôles ou objectifs sont désormais acquis par les réponses 33 à 52 pour les dix-sept recettes de la sélection courante. Ce premier tour est terminé, sans constituer des recettes entièrement spécifiées. Les dix recettes retenues de ce catalogue conservent les numéros 1, 2, 3, 4, 7, 8, 9, 10, 12 et 14 ; leurs réglages acquis figurent ci-dessous. Passerelle, Odyssée Nova, Caméléon, Atlas Nova, Résonance, Fil Rouge et Terrain d’Entente sont détaillées dans la sélection courante ; les moteurs d’analyse et leurs performances ne sont pas validés par ces réglages. Les numéros 5, 6, 11, 13 et 15 de ce catalogue ne sont pas réintégrés. Les compléments aux dix-sept recettes retenues et la présentation détaillée de la galerie restent à définir. Son accès est désormais validé après le nom, par À partir d’un modèle Nova, distinct de Règles personnalisées ; le socle de l’éditeur personnalisé est également acquis dans la [fiche Playlists](07-playlists.md).
> **Sens de « premium » :** expérience soignée, personnalisée et différenciante ; aucune offre payante, obligation d’abonnement ou promesse de qualité audio particulière n’est décidée.
> **Périmètre :** bibliothèque locale, métadonnées disponibles, favoris et historique réellement enregistré dans NovaListen. Aucun titre distant n’est ajouté pour compléter une recette.

## Principes communs

- Respecter le [masquage global et le filtre local de durée](02-bibliotheque.md) : exclure les fichiers masqués, indisponibles ou de durée connue inférieure à 60 secondes des résultats ordinaires et des nouvelles lectures. 60 secondes exactement reste admis ; aucune suppression audio. Une durée inconnue est signalée en attente, hors compteurs disponibles et sans lecture avant identification ; l’identification fiable et les anciennes références restent à préciser.
- « Jamais écouté » signifie désormais **aucune écoute validée dans l’historique disponible de NovaListen**, pas jamais entendu dans la vie ou dans une autre application. Une lecture non validée ne fait pas perdre ce statut et ne repousse pas la dernière écoute statistique ou les délais d’absence.
- **Comptabilisation commune acquise — réponses 53 à 68 :** **1 minute / 60 secondes réellement jouées cumulées dans un même passage**, portions réécoutées incluses, sans temps ajouté par les pauses ni sauts vers l’avant ; une écoute maximum par passage, avec nouveaux passages et boucles Répéter admissibles s’ils atteignent le seuil. Le curseur seul ne crée pas un nouveau passage. La pause simple conserve le même passage et son cumul ; l’écoute est horodatée au seuil atteint et les événements validés n’expirent pas automatiquement. Après arrêt complet de l’application, une reprise commence un nouveau passage statistique, sans cumul du passage précédent. Seules les écoutes validées figurent dans l’historique consultable. Les fenêtres en jours utilisent les dates du calendrier local : aujourd’hui et les N−1 dates précédentes, depuis minuit du premier jour jusqu’à maintenant, pas N × 24 heures. Les regroupements historiques gardent le fuseau, la date et l’heure locales à la validation ; ne pas reclasser le passé dans le fuseau actuel. Les événements de fichiers devenus inaccessibles restent conservés et contribuent aux totaux historiques, avec lignes masquées et signalement dans l’historique normal, sans rendre le fichier jouable. Les compléments acquis et les autres cas limites figurent dans l’[Accueil](01-accueil.md).
- Une règle historique n’invente pas d’écoutes antérieures à l’utilisation de NovaListen. Si l’historique est insuffisant, expliquer ce qui manque et proposer un autre modèle adapté, sans présenter un résultat arbitraire comme personnel.
- Les périodes, seuils, tailles et répartitions explicitement acquis ci-dessous sont des réglages de départ modifiables, pas une validation complète des recettes. Compléter les autres paramètres, les cas limites de la comptabilisation commune, les scores, les départages et les contrôles, hors priorités et ordres explicitement acquis ci-dessous. Les recettes proposées hors de ces accords restent des intentions ; aucun moteur d’exécution ou résultat de performance n’est validé.
- Les années, genres, versions et collaborations doivent provenir de données réellement identifiées. Ne pas promettre de détecter l’humeur, le tempo, les transitions harmoniques ou le contenu du son sans source et méthode distinctement choisies.
- Si une recette ne dispose pas d’assez de morceaux, afficher le résultat réel et sa limite plutôt que remplir avec des titres hors critères ou absents du téléphone.
- Pour une classique, l’ajout répété du même fichier est désormais ignoré avec information selon la fiche Playlists ; cela ne fusionne pas les variantes ni les fichiers distincts. Signature Nova et Constellation ne répètent pas le même fichier ; chaque trio de Triptyque utilise trois fichiers distincts ; Terrain d’Entente compte une seule fois un fichier commun aux deux ensembles. Ces contraintes sont acquises séparément ci-dessous ou dans la sélection courante, sans déduplication générale implicite des intelligentes. Les autres cas d’occurrences, dont les recouvrements entre plusieurs trios, et le choix entre plusieurs fichiers d’un même enregistrement restent à définir. Aucune recette n’autorise une suppression de fichier ou une fusion de véritables variantes.
- Les résultats des règles sont résolus au lancement selon le principe déjà défini pour les playlists intelligentes. La cadence de recalcul des aperçus reste à préciser ; modifier les règles ou recalculer l’aperçu ne réorganise ni n’alimente la file déjà lancée. Le masquage global garde ses effets immédiats.

## 01. Signature Nova — ton ADN musical du moment

**Promesse :** une sélection qui te ressemble, sans répéter uniquement tes plus gros titres.

**Recette proposée :** combiner favoris, morceaux souvent écoutés sur une période récente et titres moins explorés d’artistes ou de genres présents dans tes habitudes. Limiter la domination d’un seul artiste ou album.

**Réglage de départ validé — réponse 33 : profil Équilibrée, modifiable.**

- **30 titres maximum**, sans répéter le même fichier.
- Fenêtre des **écoutes récentes : 30 jours**.
- **Cibles : 10 favoris, 10 titres souvent écoutés récemment, 10 titres moins explorés d’artistes ou de genres familiers.** Ce sont des parts de sélection, pas trois blocs successifs de lecture ni une promesse d’obtenir trente titres.
- Les genres sont utilisés seulement s’ils sont renseignés ; aucun titre absent, masqué ou inaccessible ne complète la sélection.

**Restent ouverts :** cas limites de la comptabilisation commune et définition de Souvent écouté, Moins exploré et Familier ; attribution d’un fichier admissible à plusieurs catégories ; gestion des cibles insuffisantes sans remplissage hors critères ; classement et ordre final ; quotas de diversité par artiste ou album ; présentation et bornes des réglages. L’unicité du même fichier ne fusionne pas les variantes ou les fichiers distincts.

## 02. Diamants Cachés — les pépites de tes artistes préférés

**Promesse :** retrouver les morceaux discrets derrière les artistes que tu écoutes déjà beaucoup.

**Recette proposée :** repérer tes artistes familiers, puis privilégier leurs titres peu joués et non écoutés récemment, plutôt que sélectionner à nouveau leurs morceaux dominants.

**Réglage de départ validé — réponse 34 : profil Assez ouvert, modifiable.**

- Partir des **10 artistes les plus écoutés sur les 90 derniers jours**, selon les écoutes réellement enregistrées dans NovaListen.
- Pour leurs titres discrets : **de 0 à 5 écoutes dans tout l’historique NovaListen disponible**, et **aucune écoute pendant les 14 derniers jours**.
- Un titre jamais joué dans NovaListen peut être admis. Ne pas inventer de familiarité avec un artiste si les données d’écoute manquent.

**Restent ouverts :** cas limites de la comptabilisation commune, agrégation des écoutes par artiste, crédits multiples et départages du Top 10, historique suffisant, taille de la sélection, diversité, classement des titres et contrôles. Se distingue de Hors des Radars, qui donne de la place aux artistes eux-mêmes peu explorés. Aucun plafond de trente titres n’est ajouté ici par le seul choix de celui de Signature Nova.

## 03. Coup de Foudre — jamais joués, déjà dans ton univers

**Promesse :** des découvertes qui étaient déjà dans ton téléphone.

**Recette proposée :** sélectionner les titres sans écoute enregistrée dans NovaListen, puis les classer par affinité de métadonnées avec tes artistes favoris ou familiers et, si disponibles, tes genres habituels. Il ne s’agit pas d’une analyse de similarité sonore déjà disponible.

**Réglage de départ validé — réponse 35 : références Automatique, mais ajustable.**

- Les candidats restent des titres **sans écoute enregistrée dans l’historique disponible de NovaListen**, pas des titres supposés jamais entendus ailleurs.
- Commencer avec des références déduites des **artistes des morceaux favoris ou souvent joués**, ainsi que des **genres renseignés associés**.
- Permettre à l’utilisateur de **remplacer ces références par des artistes ou genres locaux choisis**, notamment si l’historique ou les références automatiques sont insuffisants. Ne pas inventer de préférences pour masquer ce manque.
- Le rapprochement repose ici sur des **métadonnées**, sans analyse de similarité sonore ajoutée implicitement.

**Restent ouverts :** cas limites de la comptabilisation commune, fenêtre et critères de référence automatique, pondération des affinités, taille et diversité de la sélection, ordre final, gestion des métadonnées absentes, forme et bornes des contrôles. Le choix Automatique, mais ajustable ne sélectionne pas un nouveau moteur d’IA.

## 04. Retour de Flamme — tes anciens numéros un reprennent vie

**Promesse :** retrouver les titres qui comptaient énormément, avant de disparaître de tes habitudes.

**Recette proposée :** comparer leur importance dans une période passée avec leur faible présence dans une période récente. Privilégier les titres ayant réellement connu une forte écoute personnelle, pas les morceaux jamais joués.

**Réglage de départ validé — réponse 36 : profil Retour rapide, modifiable.**

- Exiger **au moins 5 écoutes** dans la période située **entre 1 et 3 mois dans le passé**.
- Exiger **aucune écoute durant les 14 derniers jours**.
- Utiliser uniquement l’historique réellement enregistré dans NovaListen. Sans données assez anciennes ou fournies, expliquer la limite ; ne pas reconstituer des écoutes d’autres applications ni remplacer ces anciens titres appréciés par des morceaux jamais joués.
- Le cœur n’est pas ajouté comme condition par ce profil : il s’agit ici d’une importance passée dans les écoutes.

**Précision temporelle validée — réponse 65 : dates M−3 à M−1, bornes incluses.**

- Remonter depuis la date actuelle : **le 17 novembre, du 17 août au 17 octobre inclus**.
- Si le jour n’existe pas dans un mois cible, **prendre le dernier jour de ce mois**.
- Ne pas substituer trois mois civils complets précédents ou une conversion fixe en 30 et 90 jours. Les regroupements historiques suivent le fuseau de chaque validation, conformément à la réponse 66.

**Restent ouverts :** autres cas limites calendaires et de comptabilisation, classement de l’importance passée, historique suffisant, taille et diversité de la sélection, présentation et bornes des réglages. Les bornes inclusives et le traitement du jour inexistant sont acquis ; le score et l’ordre final ne le sont pas.

## 05. Coffre-Fort — les favoris que tu avais laissés dormir

**Promesse :** rouvrir ta collection personnelle de coups de cœur oubliés.

**Recette proposée :** partir uniquement des titres marqués Favori et privilégier ceux qui n’ont pas été écoutés depuis un certain temps, y compris les favoris jamais joués dans NovaListen selon une règle encore à choisir.

**Réglages possibles :** délai de retour, taille et équilibre entre artistes. Contrairement à Retour de Flamme, le cœur est la condition centrale ; une ancienne popularité élevée n’est pas nécessaire.

## 06. Capsule Temporelle — la bande-son de la même époque

**Promesse :** retrouver une période de ta vie à travers tes véritables écoutes.

**Recette proposée :** sélectionner les morceaux présents dans l’historique autour de la même date ou semaine d’une année passée, et toujours disponibles localement.

**Réglages possibles :** année, largeur de la fenêtre et ordre du souvenir. Ne pas fabriquer de souvenirs lorsque l’historique de la période n’existe pas.

## 07. Rendez-Vous — ta musique au bon moment

**Promesse :** une sélection adaptée à tes habitudes horaires, pas à une humeur supposée.

**Recette proposée :** retrouver les titres que tu écoutes habituellement à une heure comparable, éventuellement en tenant compte du jour de semaine. Utiliser les horodatages d’écoute locaux, sans géolocalisation nécessaire à cette recette.

**Réglage de départ validé — réponse 37 : Heure + semaine ou week-end.**

- Observer les **90 derniers jours** d’écoutes réellement enregistrées dans NovaListen.
- Prendre une plage de **2 heures avant à 2 heures après l’heure actuelle**.
- Distinguer **lundi–vendredi** de **samedi–dimanche**, puis utiliser les écoutes de la **même catégorie de jours que le jour actuel**, dans cette plage horaire. Ne pas remplacer ce choix par le même jour exact de la semaine.
- Utiliser les horodatages de validation des écoutes, conformément à la réponse 59, pas l’heure de début du morceau ; aucune géolocalisation n’est nécessaire à cette recette.

**Restent ouverts :** cas limites de la comptabilisation commune, minimum et couverture d’historique, traitement des plages traversant minuit et des changements d’heure ou de fuseau, classement des titres, taille et diversité de la sélection, présentation et bornes des réglages. Ne pas élargir silencieusement la plage ou les jours pour masquer un manque de données.

## 08. Constellation — les liens cachés dans ta collection

**Promesse :** voyager d’un artiste à un autre par leurs collaborations.

**Recette proposée :** partir d’un artiste choisi, sélectionner une collaboration locale réellement identifiée, puis poursuivre vers un autre artiste crédité et ses propres titres ou collaborations disponibles. Exemple de structure : artiste A → collaboration A/B → artiste B → collaboration B/C.

**Réglage de départ validé — réponse 38 : terminer le parcours à l’impasse.**

- Partir d’un **artiste choisi** et viser **10 titres maximum**, **sans répéter le même fichier**.
- Progresser par des **collaborations locales vérifiées**. Si aucune ne permet de rejoindre un nouvel artiste, **s’arrêter et expliquer que le parcours est plus court**.
- Ne pas inventer de collaboration, ne pas sauter vers un artiste sans lien vérifié et ne pas substituer automatiquement un retour vers un artiste déjà visité : l’option de retour n’a pas été retenue.

**Restent ouverts :** source et vérification des crédits multiples, choix entre plusieurs liens et artistes, place et choix des titres de répertoire entre les collaborations, ordre et stabilité du parcours, présentation et bornes des réglages. Aucun catalogue distant ou moteur de parcours exécuté n’est ajouté par ce choix.

## 09. Panorama — une porte d’entrée vers chaque album

**Promesse :** parcourir ta collection sans rester bloqué sur les mêmes albums.

**Principe et priorité retenus :** sélectionner un titre représentant chaque album, EP ou mixtape admissible, en tenant compte des [regroupements d’éditions](06-albums.md). Le représentant suit désormais la priorité Écoutes puis ordre de l’album détaillée ci-dessous, sans priorité au cœur.

**Réglage de départ validé — réponse 39 : Le plus écouté.**

- Pour chaque **album, EP ou mixtape local**, compter ses éditions regroupées comme **une seule sortie**, et utiliser sa liste unique locale disponible non masquée.
- Choisir le titre **le plus écouté dans l’historique NovaListen disponible**, **sans priorité au cœur**. Aucune fenêtre récente particulière n’est ajoutée ici.
- **À égalité ou sans historique**, prendre le **premier titre admissible dans l’ordre de la liste unique de l’album**. Ce choix de remplacement ne prétend pas connaître une préférence non enregistrée.

**Restent ouverts :** cas limites de la comptabilisation commune et agrégation des écoutes, identification de l’enregistrement et fichier à lire quand une ligne d’album représente plusieurs fichiers, périmètre ou nombre de sorties représentées, éventuels filtres, ordre des représentants entre eux et contrôles détaillés. Le départage interne validé ne fixe pas le tri de la playlist entière et n’ajoute pas de plafond de trente titres.

## 10. Triptyque — trois façons de redécouvrir un artiste

**Promesse :** des mini-voyages en trois morceaux, artiste après artiste.

**Recette proposée :** composer, pour un artiste, un morceau apprécié, un titre discret et un morceau sans écoute enregistrée, puis passer à un autre artiste.

**Réglage de départ validé — réponse 40 : uniquement les trios complets.**

- Pour chaque artiste retenu, conserver l’ordre **Apprécié → Discret → Jamais joué dans NovaListen**, avec **trois fichiers distincts** à l’intérieur du trio.
- Ne retenir que les artistes disposant réellement des trois rôles. **Si un rôle manque, ne pas conserver ce groupe incomplet** et expliquer si cela réduit le nombre de groupes.
- Ne pas répéter un fichier dans le trio ni remplir silencieusement un rôle absent avec un titre hors critères. Cette recette exige une composition ordonnée, pas seulement un filtre simple.

**Restent ouverts :** cas limites de la comptabilisation commune et définition des rôles Apprécié et Discret, attribution des fichiers admissibles à plusieurs rôles, sélection des artistes, nombre et ordre des trios, répétitions éventuelles entre trios de différents artistes, crédits multiples et contrôles détaillés. L’ordre des trois rôles et le refus des groupes incomplets ne sont pas à redemander.

## 11. Chronologie Vivante — traverse les années de ta musique

**Promesse :** écouter ta collection comme une histoire qui avance dans le temps.

**Recette proposée :** sélectionner des titres à partir de leurs années musicales connues, puis organiser le parcours du plus ancien au plus récent dans la période choisie. Prévoir une diversité d’artistes pour ne pas parcourir un seul catalogue.

**Réglages possibles :** années de départ et d’arrivée, taille et quotas par période. Ne pas substituer silencieusement une date d’ajout à une année de sortie ; les dates représentatives des groupes d’éditions restent un arbitrage distinct.

## 12. Backstage Pass — ta collection hors version standard

**Promesse :** donner la scène aux versions que l’on oublie souvent de lancer.

**Recette proposée :** sélectionner les versions explicitement identifiées comme live, acoustiques, remixes ou autres variantes choisies, à partir de métadonnées ou d’une identification fiable à définir.

**Réglage de départ validé — réponse 41 : Live, acoustiques et remixes.**

- Inclure par défaut les **versions live, acoustiques et remixes**, uniquement lorsque leur type est **identifié de façon fiable ou confirmé par l’utilisateur**.
- Conserver chaque véritable variante distincte, sans modifier, fusionner ni supprimer les fichiers. Aucun autre type de version n’est ajouté implicitement par ce choix.
- Ne pas présenter une déduction incertaine tirée du seul titre comme une identification fiable. Aucune reconnaissance audio, association automatique studio / live ou nouvelle analyse sonore n’est validée.

**Restent ouverts :** sources d’identification et contrôle des erreurs, parcours de confirmation ou correction, artistes concernés, taille, diversité, ordre et contrôles détaillés. Le choix des trois familles n’approuve pas une méthode de classification.

## 13. Anti-Routine — casse ta boucle sans quitter ta bibliothèque

**Promesse :** empêcher les mêmes morceaux de monopoliser chaque nouvelle sélection.

**Recette proposée :** exclure les titres trop récemment ou trop souvent écoutés dans une fenêtre choisie, puis diversifier les artistes et albums parmi les titres restants.

**Réglages possibles :** délai de repos, plafond d’écoutes récentes, taille et quotas. La sélection n’exige pas que tous les morceaux soient nouveaux pour l’utilisateur ; elle vise la variété actuelle, contrairement à Coup de Foudre.

## 14. Hors des Radars — les artistes que tu n’explores presque jamais

**Promesse :** faire respirer toute ta collection, pas seulement tes artistes habituels.

**Recette proposée :** privilégier les artistes les moins représentés dans tes écoutes personnelles, puis sélectionner leurs morceaux locaux avec une répartition équilibrée.

**Réglage de départ validé — réponse 42 : Les moins écoutés sur 90 jours.**

- Viser **jusqu’à 10 artistes**, avec **3 titres maximum par artiste**.
- Choisir les artistes ayant **le moins d’écoutes durant les 90 derniers jours dans NovaListen**, **y compris ceux à zéro**. Ne pas remplacer cette fenêtre par tout l’historique ou par la seule condition Jamais joué.
- Si les données ne permettent pas de départager les artistes, **signaler cette égalité plutôt qu’inventer une préférence**. Aucune popularité mondiale n’intervient.
- Respecter la quantité réellement disponible, sans inventer de titres ou dépasser silencieusement les plafonds pour remplir la liste.

**Restent ouverts :** cas limites de la comptabilisation commune et agrégation des écoutes, crédits multiples et attribution des titres, départages d’artistes à égalité, sélection et ordre des titres dans chaque artiste, ordre global, recouvrements et contrôles détaillés. Se distingue des titres discrets d’artistes déjà familiers de Diamants Cachés.

## 15. Chrono sur Mesure — une sélection pour le temps que tu as

**Promesse :** préparer une session adaptée à une durée choisie, sans couper le dernier morceau.

**Recette proposée :** composer une sélection locale visant une durée totale demandée — par exemple 20, 30 ou 45 minutes — à partir d’un ensemble choisi : favoris, artistes ou autres critères admis.

**Réglages possibles :** durée cible, marge acceptée, préférences et ordre. **Ne pas garantir une durée exacte** lorsqu’aucune combinaison de morceaux entiers ne la permet ; afficher la durée réelle. Cette recette nécessite une sélection sous contrainte de durée, pas simplement un tri, et ne programme pas implicitement une minuterie du player.

## Modèles Nova — accès validé, présentation détaillée encore à concevoir

- **Décision validée :** après le nom d’une intelligente, choisir **À partir d’un modèle Nova** ou **Règles personnalisées**. Le premier ouvre la galerie des dix-sept recettes retenues, puis les réglages de la recette choisie ; le second ouvre directement l’éditeur personnalisé. Ne pas mélanger cet accès en une galerie unique avec carte Personnalisée.
- La galerie conserve la sélection courante, sans imposer les cinq modèles initiaux non retenus ni ajouter Double Exposition ou Onde Continue reporté. Une création n’engendre pas automatiquement les dix-sept playlists.
- **Présentation encore proposée :** chaque modèle pourrait présenter son nom, une courte promesse et les données requises. Le détail des états de disponibilité reste à choisir. L’aperçu des morceaux et de la durée avant enregistrement reste acquis.
- Les premiers réglages de départ des dix recettes retenues de ce catalogue sont acquis ci-dessus (numéros 1, 2, 3, 4, 7, 8, 9, 10, 12 et 14) ; les premiers accords de Passerelle, Odyssée Nova, Caméléon, Atlas Nova, Résonance, Fil Rouge et Terrain d’Entente figurent dans la sélection courante. Les compléments aux dix-sept recettes restent à définir. L’accord sur la galerie n’est pas un accord sur les algorithmes ou moteurs d’analyse.
- **Socle personnalisé validé séparément :** artiste, album, favori, genre renseigné, année musicale, durée du morceau et date d’ajout ; Toutes les conditions uniquement ; tous les résultats ou nombre maximal de titres. Ni critères d’historique, ni OU ou groupes imbriqués, ni plafond de durée totale dans cet éditeur. Les recettes qui utilisent l’historique ne sont pas supprimées ou réduites à cet éditeur.
- Une explication courte des critères ayant retenu un morceau pourrait renforcer la lisibilité de la sélection ; cette fonction reste une proposition.

## Points à arbitrer avant un développement

- Dix-sept concepts sont retenus par l’utilisateur dans la sélection courante, dont dix proviennent de ce catalogue initial et trois des dernières propositions A, B et C. Onde Continue est à discuter plus tard, ni retenu ni rejeté ; Double Exposition reste non retenu. Les noms définitifs, paramètres détaillés et moteurs techniques restent à préciser. Les quinze recettes initiales ne sont pas toutes acquises.
- Opérateurs des sept familles personnalisées et ordre de leurs résultats ; pour les recettes statistiques, cas limites de la comptabilisation commune, modalités de gestion et d’effacement de l’historique et compléments de périodes, seuils et classements, sans rouvrir les réglages de départ explicitement acquis ci-dessus ou dans la sélection courante. Les familles personnalisées et l’absence de critères d’historique dans cet éditeur sont désormais acquises.
- Composition avancée : quotas par artiste ou album, parcours de collaborations, rôles successifs et durée cible. Ne pas présenter ces opérations comme déjà couvertes par l’éditeur personnalisé Toutes les conditions, limité au socle choisi.
- Identification des artistes et enregistrements, types de versions et sources de métadonnées fiables.
- Résultats insuffisants, dates ou genres absents, présentation des conditions et corrections possibles.
- Ordre, stabilité ou rotation des résultats et actualisation des aperçus, sans modifier la file déjà lancée.
- La page, ses commandes par type et les six entrées du menu de playlist sont désormais validées dans la [fiche Playlists](07-playlists.md). **Aléatoire est absent de toutes les intelligentes**, sans exception de modèle. Les gestes sur cartes et morceaux sont désormais validés séparément ; Dupliquer une intelligente copie son modèle de playlist, ses règles et ses réglages dans une nouvelle intelligente. Les comportements communs de lecture sont désormais précisés dans cette fiche : playlist entière avec titre touché actif, remplacement direct sans confirmation, Lire ensuite immédiatement après le titre actif et classique modifiable sans changer sa file lancée. Les catégories d’actions en lot et les garde-fous de suppression/retrait sont également validés dans la fiche Playlists, en distinguant cartes et morceaux et sans retrait manuel générique des résultats intelligents. Supprimer la playlist d’origine de la session arrête la lecture et vide sa file ; modifier les règles ou recalculer n’a pas cet effet. Les autres modalités de file, détails de sélection et paramètres encore ouverts restent à définir ; ce catalogue de recettes ne les décide pas implicitement.

Aucune playlist réelle n’a été générée, aucun fichier audio n’a été inspecté pour ce catalogue et aucun moteur de règles n’a été choisi ou implémenté.
