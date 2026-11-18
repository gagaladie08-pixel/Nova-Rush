# NovaListen — Première étude de faisabilité des 17 playlists intelligentes

> **Statut :** analyse de conception demandée après la proposition de page d’une playlist. Ce document est un **avis d’ingénierie préliminaire**, pas un résultat d’essai. Aucune application, analyse audio ou analyse sémantique n’est implémentée ou exécutée. Le duo EmbeddingGemma 300M et multilingual-e5-small est désormais retenu pour la conception de Fil Rouge, avec téléchargement facultatif ; moteurs d’exécution, autres moteurs d’analyse, fournisseurs d’analyse distante, formats de stockage et calendrier de développement restent à choisir.
> **Périmètre :** les [dix-sept concepts retenus](09-playlists-intelligentes-selection.md), pour des fichiers disponibles sur le téléphone, avec un objectif de fonctionnement hors ligne. **Onde Continue reste à discuter plus tard**, sans étude supplémentaire ici. Double Exposition et les cinq modèles du catalogue initial non retenus ne sont pas réintroduits.
> **Interface :** la [page d’une playlist](07-playlists.md) et ses commandes par type sont désormais validées. **Aléatoire est absent de toutes les playlists intelligentes**, modèles et conditions personnalisées compris ; il reste disponible pour les classiques. Les gestes, comportements communs de file et premiers choix de l’éditeur personnalisé ont été validés séparément et sont consignés dans cette fiche Playlists. Les premiers réglages, contrôles ou objectifs des dix-sept recettes sont désormais acquis dans la fiche Playlists et la sélection courante, par les réponses 33 à 52. Ce premier tour n’achève pas leurs spécifications. Leurs compléments et les choix techniques de la présente étude ne sont pas tranchés par ces accords fonctionnels, qui ne prouvent ni performances ni qualité d’analyse.

> **Derniers accords pour Fil Rouge :** recherche d’un thème à travers le français, l’anglais, le coréen et l’espagnol, sans traduction française préalable obligatoire ; réutilisation des paroles trouvées via les API, choisies et sauvegardées. **Étude locale en priorité ; solution en ligne à envisager si aucun moteur local adapté n’est trouvé ou ne convient.** Cette option conditionnelle ne choisit aucun service et n’autorise aucun transfert implicite. L’utilisateur retient désormais **EmbeddingGemma 300M et multilingual-e5-small**, avec **téléchargement facultatif au premier lancement et disponible ensuite dans les paramètres**. Les [modèles retenus et le parcours de disponibilité](11-fil-rouge-moteurs.md) ne sont ni installés ni testés ; refuser le téléchargement ne bloque pas le lecteur et ne déclenche pas l’option distante.

## 1. Conclusion préliminaire

- **Quinze modèles ont une voie de réalisation crédible sans analyse audio ni compréhension sémantique des paroles**, à partir de règles, scores, métadonnées fiables, favoris, choix explicites et historique local réel. Cela ne veut pas dire quinze filtres simples ou quinze fonctions déjà prêtes.
- **Résonance et Fil Rouge nécessitent une preuve de faisabilité ciblée sur téléphone**, car leur intérêt dépend de la qualité d’analyse obtenue et de son coût réel, pas seulement de la capacité à exécuter un calcul.
- Les dix-sept modèles n’exigent pas dix-sept moteurs indépendants : plusieurs pourraient partager les mêmes données et opérations.
- Rien dans les quinze modèles de règles et de composition n’impose un serveur, un compte distant ou une IA générative. Le fonctionnement local des deux modèles d’analyse reste à vérifier. Pour **Fil Rouge uniquement**, une solution en ligne peut être étudiée en second choix si aucune voie locale adaptée ne convient ; cela ne rend pas le lecteur dépendant d’un catalogue distant et ne garantit pas une analyse disponible hors ligne dans ce mode.
- Aucune durée de calcul, consommation, taille installée finale, date de livraison ou compatibilité Android précise ne peut être annoncée sans choix d’artefacts et mesures complémentaires. Les tailles documentées de fichiers publiés ne sont pas la taille finale de l’application.

### Cible personnelle communiquée le 17 novembre 2026

| Élément | Information connue | Provenance et limite |
| --- | --- | --- |
| Téléphone | **TECNO Camon 40 Pro 4G** | Modèle déclaré par l’utilisateur ; ne pas le remplacer par la version Pro 5G. |
| Bibliothèque | **Environ 1 000 titres** | Ordre de grandeur déclaré, pas un inventaire exécuté ni une mesure du volume en Go ou de la durée totale. Sert d’échelle de travail, pas de plafond au nombre de titres pris en charge. |
| Processeur de référence | **MediaTek Helio G100 Ultimate** | Identifié pour la version 4G dans les fiches consultées ; ne constitue pas un test de NovaListen sur l’appareil. |
| RAM physique, mémoire disponible, espace libre | Non communiqués pour l’exemplaire de l’utilisateur. | Ne pas confondre une valeur commerciale incluant une extension de RAM avec la RAM physique effectivement disponible. |
| Version Android/HiOS actuellement installée | Non communiquée. | Ne pas déduire l’état logiciel actuel d’une fiche de lancement. |
| Langues de la collection | **Principalement anglais et français ; aussi un peu de coréen et d’espagnol.** | Répartition déclarée par l’utilisateur, sans proportions chiffrées ni inventaire. Ne prouve pas la disponibilité des paroles ou la qualité d’un moteur multilingue. |
| Source confirmée pour Fil Rouge | Réutiliser les paroles trouvées par API, choisies et sauvegardées dans le parcours Paroles. | La [cascade de paroles](14-cascade-api-paroles.md) est désormais retenue pour la conception, avec le noyau lrc.red / LRCLIB / LiriQo et ses compléments conditionnels. Cela ne prouve ni droits d’analyse ni couverture ; le nombre de textes effectivement disponibles reste inconnu, et aucun fournisseur d’analyse distante n’est choisi. |
| Formats, paroles enregistrées et qualité des métadonnées | Non inventoriés. | Le nombre de titres et les langues déclarées ne permettent pas de déduire la durée totale ou la couverture des paroles. |

**Sources matérielles consultées le 17 novembre 2026 :** page produit officielle TECNO, https://www.tecno-mobile.com/phones/product-detail/product/camon-40-pro/ — passage Performance identifiant le Helio G100 Ultimate et la version 4G ; fiche complémentaire [2](https://www.itechguides.com/tecno-camon-40-pro/) distinguant la version 4G de la 5G. Les caractéristiques commerciales ne sont pas des mesures de performance de l’application. Aucun accès au téléphone ou à ses fichiers n’a été effectué.

### Avis adapté à ce téléphone et à cet ordre de grandeur

**Avis d’ingénierie préliminaire : la cible est cohérente avec le projet ; il n’y a pas de raison, sur la seule base de 1 000 titres, de retirer un des dix-sept concepts.** Cette appréciation ne transforme pas Résonance et Fil Rouge en fonctions validées expérimentalement, et ne garantit pas une compatibilité de tous les formats ou de tout moteur d’analyse avec cet appareil.

- **Les quinze modèles de règles et de composition : avis favorable sous conditions de données.** À cet ordre de grandeur, une indexation des métadonnées et des calculs de scores, quotas, parcours et progression constituent une voie raisonnable. L’absence de collaborations identifiées, de variantes fiables ou d’un historique assez ancien peut davantage limiter le résultat que le nombre de fichiers. Ne pas promettre une latence chiffrée avant implémentation et mesure.
- **Terrain d’Entente : pas de besoin matériel distinct imposé par le concept.** Les deux sélections de référence et leur équilibre peuvent être étudiés avec le même socle local. Cela ne suppose ni un autre téléphone connecté ni une IA générative.
- **Résonance : piste locale plausible, à prototyper.** Séparer la préparation des caractéristiques audio de la recherche de ressemblances. Proposer une préparation progressive par fichier ou petits lots, avec résultats mémorisés, puis une recherche parmi les caractéristiques disponibles. Les nouveaux fichiers et les données modifiées demanderaient une mise à jour : ce n’est pas « analyser une fois pour toujours ». Ne pas charger les 1 000 fichiers audio simultanément ni les réanalyser à chaque ouverture de playlist.
- **La recherche et la préparation de Résonance sont deux coûts différents.** Comparer une référence à des représentations déjà produites peut être beaucoup moins coûteux que décoder et analyser l’audio de toute la bibliothèque ; ce principe ne prouve ni une réponse instantanée ni la qualité des similitudes. La durée totale des fichiers, le choix d’extraits, le moteur et le téléphone en conditions réelles restent déterminants.
- **Fil Rouge : piste locale à étudier avec un moteur de texte adapté.** Le nombre de titres, à lui seul, ne démontre pas un obstacle, mais il faut disposer des paroles enregistrées et d’une méthode suffisamment pertinente pour leurs langues. Préparer des représentations du texte puis les réutiliser est une direction possible. La présence d’une synchronisation ou d’un karaoké n’est pas requise ; une transcription automatique des 1 000 audios ne fait pas partie du concept et n’est pas ajoutée comme prérequis implicite.
- **Point technique décisif pour les deux analyses :** choisir éventuellement des composants compacts compatibles avec l’exécution locale, puis mesurer qualité, mémoire, batterie et impact sur la lecture sur cette cible. Ne pas supposer un accélérateur particulier, un service d’IA du constructeur disponible aux applications ou un grand modèle conversationnel nécessaire.

**Préparation : interface validée pour Fil Rouge, recommandations encore ouvertes pour Résonance.** Pour Fil Rouge, déclenchement explicite après disponibilité des modèles, progression visible, pause/reprise et couverture partielle annoncée sont acquis dans le parcours dédié. Les réglages liés à la charge, à la chauffe et à la batterie restent à concevoir ; ne pas les appliquer implicitement à Résonance. Préserver la réactivité de la navigation et la lecture reste un objectif à vérifier, pas une performance déjà obtenue. Android ne doit pas être supposé laisser une analyse lourde se poursuivre indéfiniment en arrière-plan.

Pour Fil Rouge, le téléchargement des deux modèles est désormais choisi comme une **proposition facultative au premier lancement, reportable dans les paramètres**, sans bloquer le lecteur. Il reste distinct de la préparation et de l’analyse locales ; aucune récupération de paroles, activation du micro ou sortie de fichiers vers un serveur n’est autorisée implicitement. Aucun fichier audio de la bibliothèque de l’utilisateur n’a été lu, décodé ou analysé dans cette étude.

**Bilan pour cette cible : conserver les 17 concepts, avec 15 voies de réalisation locales favorables et 2 analyses à valider par des essais futurs.** Les catégories R/C/P de la matrice restent inchangées. Les langues de la collection sont désormais précisées : anglais et français principalement, avec un peu de coréen et d’espagnol. La présence effective de paroles sauvegardées reste à vérifier ; aucune nouvelle sélection de modèles n’est nécessaire. Toute autorisation de prototypage reste à demander séparément.

## 2. Matrice modèle par modèle

Les repères sont ceux de la discussion, pas une renumérotation du catalogue initial.

- **R — Règles et scores courants :** faisabilité conceptuelle favorable, sous réserve des données et de l’intégration à la bibliothèque.
- **C — Composition spécifique :** voie algorithmique connue, mais logique de parcours, de séquencement ou de suivi à concevoir et tester.
- **P — Prototype nécessaire :** qualité utile et coût de l’analyse à démontrer avant une promesse fonctionnelle complète.

Ces catégories expriment un effort relatif et des risques, pas un engagement de délai.

| Repère | Modèle | Catégorie | Voie de réalisation envisagée | Prérequis ou risque principal |
| --- | --- | --- | --- | --- |
| 1 | Signature Nova | R | Scores de familiarité et de préférence, puis quotas de diversité. | Historique suffisant, équilibre et seuils encore à définir. |
| 2 | Diamants Cachés | R | Artistes familiers, puis sélection de leurs titres peu joués. | Définition de la familiarité et cas limites de la comptabilisation commune ; ne pas inventer un passé. |
| 3 | Coup de Foudre | R | Titres sans écoute locale enregistrée, classés par affinités de métadonnées connues. | Sans historique, références ou favoris choisis explicitement ; ce n’est pas une similarité audio. |
| 4 | Retour de Flamme | R | Comparaison entre importance passée et faible présence récente. | Un historique suffisamment ancien est indispensable. |
| 5 | Rendez-Vous | R | Agrégations d’écoutes par plages horaires et éventuellement jours. | Quantité de données, horaires comparables et changements de fuseau ; pas de géolocalisation requise. |
| 6 | Constellation | C | Graphe des crédits et collaborations réellement identifiés, puis recherche de parcours. | Crédits incomplets, homonymes ou absence de liens ; aucun réseau de collaborations inventé. |
| 7 | Panorama | R | Un représentant par groupe d’album, EP ou mixtape admissible. | Dépend du regroupement des éditions et d’une règle explicite de choix du représentant. |
| 8 | Triptyque | C | Attribution des trois rôles par artiste, puis composition de blocs ordonnés. | Éviter qu’un même titre remplisse plusieurs rôles ; gérer les trios incomplets sans faux résultat. |
| 9 | Backstage Pass | R | Filtrage de variantes correctement identifiées. | La fiabilité du classement live, acoustique ou remix est plus délicate que le filtre lui-même. |
| 10 | Hors des Radars | R | Classement des artistes selon leur exploration personnelle, puis répartition des titres. | Historique et règles d’agrégation des artistes ; aucune popularité mondiale supposée. |
| 11 | Passerelle | C | Recherche d’un chemin entre deux références locales dans un graphe de relations admises. | Un chemin peut ne pas exister ; la proximité des métadonnées ne garantit pas une transition sonore. |
| 12 | Odyssée Nova | C | Constitution de réserves de titres pour les cinq actes, puis séquencement avec contraintes. | Définir les rôles et les actes incomplets ; ne pas prétendre mesurer une émotion musicale. |
| 14 | Caméléon | C | Préférences propres à la playlist ; portée choisie à chaque retour : titre, artiste crédité ou genre renseigné. | Force, durée, méthode et correction des effets restent à concevoir, sans modifier la file lancée ni rendre les retours globaux. |
| 15 | Atlas Nova | C | Trois découpages retenus : artistes, albums locaux et genres renseignés ; suivi de couverture à concevoir. | Définition de l’exploration, articulation des modes, nouveaux fichiers, remise à zéro et dénominateurs de progression. |
| A | Résonance | P | Proximité sonore globale à partir de 1 à 3 références locales ; descripteurs ou représentations numériques encore à choisir. | Combinaison des références, qualité perçue, décodage, calcul initial, mémoire, stockage et batterie à mesurer. |
| B | Fil Rouge | P | Analyse thématique des paroles sauvegardées : duo local EmbeddingGemma 300M et multilingual-e5-small retenu, téléchargement facultatif ; solution en ligne à étudier seulement si aucune voie locale adaptée ne convient. | Couverture des paroles, langues, intégration, pertinence et coût ; refuser le téléchargement ne déclenche pas l’option distante, dont confidentialité, droits et connexion restent à examiner. |
| C | Terrain d’Entente | C | Deux ensembles de 1 à 5 références ; candidats parmi les références et les mêmes artistes locaux ; cible de 10 communs / 10 de chaque univers. | Définition du commun et attribution à concevoir ; sans point commun fiable, demander de revoir les références avant de générer, sans dépendre d’un autre téléphone. |

**Bilan de la matrice : 8 modèles R + 7 modèles C + 2 modèles P = 17.** Les difficultés transversales de bibliothèque, identité des fichiers et historique restent communes à ces catégories.

## 3. Un socle commun possible — proposition, pas choix de stack

### Données nécessaires

- Un index des fichiers réellement accessibles, de leurs métadonnées et durées, avec leurs identifiants. Le filtre de la réponse 57 exclut de la bibliothèque musicale admissible les fichiers de durée connue inférieure à 60 secondes, sans supprimer l’audio ; la réponse 61 retient une entrée en attente pour durée inconnue, hors compteurs disponibles et sans lecture ; identification fiable et réévaluations restent à concevoir.
- Les favoris et le masquage global, selon les règles déjà définies.
- Des écoutes locales réellement enregistrées selon le socle désormais acquis dans l’[Accueil](01-accueil.md), réponses 53 à 68 : **1 minute / 60 secondes réellement jouées cumulées par passage**, portions réécoutées incluses, sans temps ajouté par les pauses ni sauts vers l’avant ; une écoute maximum par passage, chaque nouveau passage suffisant pouvant compter, y compris les boucles Répéter. Dernière écoute, Jamais joué et les délais d’absence utilisent les seules écoutes validées. Une pause simple conserve le passage et son cumul ; l’horodatage est celui du seuil atteint et les événements validés sont conservés sans expiration automatique. Après arrêt complet, une reprise commence un nouveau passage statistique ; les lectures non validées ne créent pas d’entrée durable dans l’historique consultable. Les fenêtres en jours suivent les dates locales, aujourd’hui et les N−1 dates précédentes, pas N × 24 heures glissantes. Les regroupements historiques conservent le fuseau, la date et l’heure locales de chaque validation. Pour Retour de Flamme, M−3 à M−1 désigne deux dates inclusives, avec dernier jour du mois cible si nécessaire, pas trois mois civils complets. Les événements de fichiers devenus inaccessibles restent conservés et contribuent aux totaux historiques, avec lignes normales masquées et signalement, sans lecture rétablie. Identification fiable, réévaluations, réidentification des fichiers, autres bornes d’un passage, cas limites horaires, autres usages des mois, contribution des morceaux masqués ou hors filtre et effacement volontaire restent ouverts. Aucun historique réel n’est produit par ces choix.
- Les relations explicitement connues : artistes et collaborations, regroupements d’éditions, variantes identifiées. Une relation absente n’est pas remplacée par une supposition opaque.
- La définition propre à chaque playlist : modèle éventuel, conditions, paramètres et choix explicites. La portée des retours Caméléon est acquise, mais leur méthode et leur présentation restent à définir ; Atlas propose les trois découpages retenus, sans mesure de progression encore choisie.

Le traitement des copies d’un même enregistrement, des changements de fichier et des statistiques entre éditions reste ouvert dans les fiches existantes. Cette étude ne décide ni une fusion générale d’identités ni une propagation automatique des favoris, historiques ou masquages entre copies.

### Chaîne de calcul envisagée

1. Partir des fichiers admissibles : locaux, accessibles et non masqués ; exclure ceux dont la durée connue est inférieure à 60 secondes, mettre les durées inconnues en attente, hors compteurs disponibles et sans ajout aux nouvelles files jouables jusqu’à identification.
2. Appliquer les conditions, scores et données réellement disponibles.
3. Composer le résultat si nécessaire : quotas, blocs, actes ou parcours.
4. Construire un aperçu explicable, avec nombre et durée des titres effectivement retenus.
5. **Au lancement de la lecture, résoudre les règles et revérifier l’admissibilité**, puis construire la file, conformément au principe déjà défini.

Les calculs coûteux d’analyse pourraient être préparés et mémorisés avant cette résolution ; cela ne signifie pas réanalyser tout l’audio à chaque toucher sur Tout lire. Un résultat ancien ne doit jamais réintroduire un fichier devenu masqué ou inaccessible.

Une file déjà lancée ne reçoit pas silencieusement les résultats d’un recalcul. Le masquage global conserve néanmoins sa priorité : si le titre actif est masqué, passage immédiat au prochain titre non masqué ou arrêt, selon la règle acquise.

## 4. Résonance — faisable à explorer, qualité à prouver

**Choix fonctionnel acquis — réponse 47 :** partir de **1 à 3 morceaux locaux de référence** pour viser une **proximité sonore globale**. La méthode de combinaison, le moteur, la préparation et la qualité restent à étudier ; ce choix n’autorise aucune exécution et n’approuve pas des réglages séparés de rythme ou texture.

### Deux familles d’approches possibles

- **Descripteurs de signal :** comparer des caractéristiques mesurées, par exemple rythmiques et spectrales. Cette voie peut servir de première référence technique, mais elle ne constitue pas à elle seule une compréhension musicale fine.
- **Représentations apprises compactes :** comparer des vecteurs produits localement par un modèle audio adapté. Cette voie pourrait capter d’autres ressemblances, mais ajoute des contraintes de poids à distribuer, de licence, de mémoire, de calcul et d’interprétation.

Ces familles ne sont pas sélectionnées, et l’une ne garantit pas une meilleure expérience que l’autre sans essais. Aucun outil de reconnaissance du titre d’un morceau ni service distant n’est nécessairement requis pour chercher une similarité locale.

### Ce qu’il faudra vérifier

- La pertinence des rapprochements pour l’utilisateur, y compris entre artistes ou genres différents ; une distance mathématique seule n’est pas une preuve de qualité.
- L’effet des différences de production, de volume, des longues introductions ou du choix d’extraits analysés. Un seul extrait peut mal représenter un titre ; une analyse plus complète peut coûter davantage.
- Le décodage des formats retenus et les états d’échec, sans modifier les fichiers audio.
- Le coût de préparation sur le téléphone cible, la possibilité d’interrompre/reprendre et le coût des mises à jour de bibliothèque.
- La recherche parmi de nombreux titres sans comparer inutilement toutes les paires à chaque génération.
- La véracité des explications : un vecteur proche ne permet pas automatiquement d’affirmer « même instrument », « même émotion » ou une tonalité précise.

**Décision attendue après un éventuel prototype, pas maintenant :** quelles dimensions sont assez fiables pour devenir des réglages visibles ? Si la qualité est insuffisante, proposer de réduire explicitement la promesse ou de reporter le modèle, pas de remplacer silencieusement l’analyse sonore par un simple filtre de genre.

## 5. Fil Rouge — paroles sauvegardées, local prioritaire et option en ligne conditionnelle

### Décisions fonctionnelles confirmées

- Un même thème peut réunir des chansons dans les quatre langues déclarées, sans traduction française préalable obligatoire. Il s’agit d’une cible fonctionnelle acquise, pas d’une qualité de moteur déjà démontrée.
- **Objectif acquis — réponse 48 :** accepter un thème central ou secondaire réellement développé dans les paroles, mais pas une allusion vague ou un mot isolé. Cet objectif ne démontre pas que le duo sait distinguer ces cas ; méthode, agrégation et seuils restent à valider.
- Réutiliser notamment les paroles **déjà trouvées via les API, choisies et sauvegardées**, conformément au choix manuel du gestionnaire de paroles. Ne pas réinterroger automatiquement tous les fournisseurs pour chaque génération.
- Privilégier l’analyse **locale multilingue** et retenir **EmbeddingGemma 300M et multilingual-e5-small** pour Fil Rouge ; formats et moteurs d’exécution Android restent à valider.
- Proposer le téléchargement des deux au premier lancement ; le refus laisse le lecteur utilisable et le téléchargement reste accessible dans les paramètres. Le parcours est désormais validé : Wi-Fi par défaut avec accord pour les données mobiles, report mémorisé, blocs modèles/préparation, commandes contextuelles, première préparation par **Préparer maintenant / Plus tard** et recherche sur une couverture partielle explicitement indiquée. Ce choix de conception n’autorise aucun téléchargement dans cette étude.
- Si aucun moteur local adapté n’est trouvé ou ne répond aux contraintes, **envisager une solution en ligne si elle est réalisable**. L’étude est acceptée, pas un basculement automatique, un fournisseur précis ou un envoi effectif de données.

La [fiche des modèles de Fil Rouge](11-fil-rouge-moteurs.md) consigne le duo retenu, le téléchargement optionnel et une **proposition de combinaison par deux index séparés et fusion des classements**, à comparer aux deux modèles seuls. Le choix des modèles ne sélectionne pas leurs artefacts ou moteurs d’exécution Android, ne mélange pas leurs espaces vectoriels et ne garantit aucun gain de qualité. Aucun poids n’est téléchargé ni exécuté ; leur adaptation aux paroles ou au téléphone reste à vérifier. Le [parcours fonctionnel validé](12-fil-rouge-telechargement-et-preparation.md) sépare premier lancement, paramètres et préparation des paroles ; son déclenchement, ses réglages réseau et les rôles de ses commandes sont acquis, sans démontrer leur réalisation Android ou la qualité des résultats.

### Une voie locale possible

Une méthode sémantique locale pourrait rapprocher le thème demandé et les paroles sous forme de représentations numériques ou de thèmes contrôlés. Une recherche par mots peut fournir une base de comparaison technique, mais ne doit pas être présentée comme la compréhension du sens promise par le concept.

Il n’est pas nécessaire de choisir un grand modèle génératif conversationnel pour étudier ce rapprochement. Le duo de modèles d’embeddings textuels est retenu ; sa qualité réelle par langue, ses artefacts et les modalités de distribution sous les licences applicables restent à vérifier avant intégration.

### Principe multilingue validé et étude locale prioritaire

**Donnée acquise :** les morceaux sont principalement en **anglais et français**, avec aussi un peu de **coréen et d’espagnol**. Cette indication porte sur la collection déclarée, pas sur quatre ensembles de paroles déjà récupérées ou analysées. Les quatre langues font partie du périmètre à évaluer ; leur fréquence différente n’est pas une autorisation d’ignorer les langues minoritaires.

**Principe validé : pas de traduction française préalable obligatoire.** L’analyse multilingue des paroles originales est la direction retenue avec les deux modèles choisis ; aucun export, composant Android ou réglage de fusion n’est pour autant choisi.

Une voie à tester serait :

1. Partir des paroles réellement sauvegardées, notamment celles trouvées par les API puis choisies, avec conservation de leur source et de leur version originale.
2. Évaluer les deux modèles locaux retenus, chacun dans son propre espace de comparaison cohérent entre textes et thèmes. Les artefacts, ressources exactes et moteurs d’exécution Android ne sont pas choisis ; les conditions des licences restent à respecter.
3. **Objectif fonctionnel validé : un thème saisi en français, par exemple repartir à zéro, doit pouvoir rapprocher des paroles en français, anglais, coréen ou espagnol**, si les résultats de l’analyse le justifient. La recherche entre langues n’est pas une capacité démontrée par la seule présence d’un nom de langue dans la fiche d’un modèle.
4. Préparer et mémoriser les représentations utiles, puis les actualiser lorsque le texte ou le moteur pertinent change, au lieu de réencoder toute la collection à chaque recherche.

Chaque modèle choisi est multilingue : le duo ne signifie pas un modèle différent pour chacune des quatre langues. Ce choix ne garantit ni la même pertinence dans chaque langue, ni une taille ou une vitesse acceptables sur le Camon 40 Pro 4G. Les ressources nécessaires doivent être présentes pour une utilisation réellement hors ligne ; elles sont proposées en téléchargement facultatif, pas requises pour ouvrir le lecteur.

**La traduction française reste une aide éventuelle, pas un prérequis imposé.** Une traduction déjà sauvegardée pourrait être étudiée comme autre entrée, avec sa provenance et ses limites, mais elle ne remplace pas silencieusement l’original. Le choix VO/FR d’affichage des paroles ne doit pas être confondu avec la version effectivement analysée. Aucun appel de traduction ou de récupération de paroles en ligne n’est déclenché implicitement par cette proposition.

**Vérifications futures spécifiques :** pertinence séparée en français, anglais, coréen et espagnol ; recherche d’un thème français dans les quatre langues ; textes mêlant plusieurs langues ; négations, métaphores et différences de sens. Pour le coréen, distinguer textes en hangeul et éventuelles romanisations rencontrées, sans inventer leur présence dans la collection ni promettre une conversion automatique. Une bonne moyenne globale ne suffit pas si elle masque de mauvais résultats dans une langue moins représentée.

Les filtres de langue éventuels, la présentation des associations et les modalités fines de préparation restent à définir. L’accord sur la recherche commune entre langues ne valide pas implicitement ces contrôles ou une qualité de compréhension. Le choix ultérieur du duo est acquis séparément ; aucun essai multilingue n’a été exécuté.

### Solution en ligne — étude de second choix seulement

Si la voie locale ne convient pas, l’utilisateur accepte d’envisager une analyse thématique distante. Le [détail des conditions à examiner](11-fil-rouge-moteurs.md) couvre données transmises, droits des API de paroles, confidentialité, quotas et disponibilité. La réponse 73 impose désormais des [API gratuites uniquement](13-api-et-enrichissements.md), sans abonnement ni appels facturés ni bascule automatique vers du payant. Aucun fournisseur n’est choisi ; cette étude ne vaut pas accord pour envoyer la collection.

Le mode éventuel doit être explicite, sans basculement silencieux. Il pourrait nécessiter une connexion pour de nouvelles paroles ou un nouveau thème ; la lecture des fichiers audio locaux ne doit pas dépendre de ce service. La réutilisation d’analyses antérieures, leur fraîcheur et les états sans réseau restent à concevoir sans annoncer un nouveau calcul complet si des données manquent. Cette exception d’étude concerne Fil Rouge, pas Résonance ni l’ensemble des enrichissements.

### Limites à rendre visibles

- **Pas de paroles locales : pas d’association thématique inventée.** Aucun téléchargement de paroles, appel de traduction ou service distant ne doit se déclencher implicitement pour compléter une génération hors ligne.
- Les textes réellement conservés dans la [fiche Paroles](04-paroles.md), leurs langues, leurs sources et leurs corrections doivent rester identifiables.
- Le texte brut peut suffire à l’étude thématique : la synchronisation ligne par ligne et le karaoké mot à mot ne sont pas des prérequis de Fil Rouge.
- Ironie, métaphores, négations, langues mélangées et thèmes multiples peuvent provoquer de mauvaises associations.
- Une traduction sauvegardée peut aider dans certains cas, mais sa présence n’est pas garantie et elle peut modifier des nuances ; la version analysée devrait être identifiable.
- Une justification ne doit pas inventer des passages de paroles. Une correction de thème ne modifie pas le texte original.

**Décision attendue après un éventuel prototype :** quelles langues et quels types de thèmes donnent une pertinence suffisante sur les appareils visés ? Ne pas promettre une compréhension universelle hors ligne ni analyser l’humeur personnelle de l’auditeur.

## 6. Terrain d’Entente, Caméléon et Atlas — pas besoin d’une IA opaque

### Terrain d’Entente

Les réponses 49 à 52 retiennent deux ensembles de **1 à 5 références locales chacun**, choisis sur le même téléphone. Les candidats viennent de ces références et des autres titres locaux des mêmes artistes crédités, sans élargissement par genre seul. La cible initiale modifiable est de **30 titres : 10 communs / 10 univers 1 / 10 univers 2**, un fichier commun compté une seule fois, sans remplissage hors critères garanti. Définition du commun, affectation et ordre restent à concevoir ; aucun moteur ni résultat réel n’est produit par ces accords.

Il n’est pas nécessaire d’avoir un profil distant, une synchronisation ou un service Listening Party. Une référence réellement commune doit être distinguée d’un titre seulement rapproché par ses métadonnées. Si les deux ensembles n’ont aucun point commun fiable, demander de revoir les références avant de générer, pas de repli sur une sélection 50 / 50. Les cas partiellement fournis restent à concevoir ; cette décision ne tranche pas l’enregistrement d’un résultat vide de l’éditeur personnalisé.

### Caméléon

Des poids de préférence propres à la playlist peuvent être ajustés après un retour explicite et remis à zéro. Cette voie peut être étudiée avant tout apprentissage automatique complexe.

La portée est désormais choisie à chaque retour : ce titre, un artiste crédité ou un genre renseigné. Le point difficile reste d’expliquer la force et la méthode de l’effet, sans déduire une préférence universelle ni présenter les poids comme un algorithme déjà validé. Un saut de morceau ne devient pas automatiquement un retour négatif. Les préférences ne doivent pas masquer globalement un titre, retirer un favori ou modifier la file active.

### Atlas Nova

Un suivi local de couverture est une voie classique de gestion de données. Les trois découpages artistes, albums et genres renseignés sont acquis. Le vrai travail reste la définition de la progression : comment une écoute validée contribue à la couverture et comment articuler les modes, comment les nouveaux fichiers et les retraits influent sur la progression, et comment permettre une remise à zéro compréhensible.

Aucune exploration antérieure à l’historique disponible ne doit être inventée. La progression ne doit pas inciter à lire un morceau masqué ni être bloquée par un fichier devenu inaccessible.

## 7. Hors ligne, performances et confidentialité

Principes et points de vigilance, pas capacités déjà mesurées : le parcours fonctionnel dédié à Fil Rouge est validé ; les autres choix de réalisation ou recommandations non présentés restent ouverts.

- Distinguer le **fonctionnement de la playlist hors ligne** de la **mise à disposition initiale des modèles d’analyse**. Pour Fil Rouge, le téléchargement séparé facultatif au premier lancement et ultérieurement dans les paramètres est choisi ; les ressources de Résonance restent à définir. Ne pas annoncer une analyse immédiatement prête hors ligne si modèles ou représentations des paroles manquent. Refus, interruption ou absence de réseau ne bloquent pas le lecteur et ne déclenchent pas de service distant.
- Garder les fichiers, paroles, historiques et calculs sur l’appareil pour les voies locales étudiées ; aucun envoi vers un serveur, enregistrement du micro ou recours distant de secours n’est autorisé implicitement.
- Préparer et mémoriser les caractéristiques d’analyse lorsque c’est utile ; les invalider si les données pertinentes changent, plutôt que recommencer toute la bibliothèque à chaque ouverture.
- Prévoir des tâches progressives, interrompables et reprenables, sans supposer qu’Android laissera un traitement lourd fonctionner indéfiniment en arrière-plan.
- Mesurer la lecture pendant les calculs : pas de promesse de fluidité, de consommation ou de durée de préparation avant essais sur des appareils cibles.
- Respecter les autorisations et les accès réels aux fichiers ; traiter les refus, révocations, fichiers supprimés et supports devenus inaccessibles.
- Conserver les analyses et progressions séparément des fichiers audio d’origine. Aucune normalisation, réécriture de tags, suppression ou transformation sonore n’est nécessaire à ces recettes de playlists.
- Distinguer calcul possible et droits de distribution : licences d’éventuels poids de modèles, dépendances et conditions des sources de paroles restent à examiner avant une sélection technique.

## 8. Vérifications à prévoir avant de promettre la fonction

Cette liste décrit des **essais futurs**, non exécutés :

1. Masquage et indisponibilité respectés dans les candidats, aperçus, compteurs, durées et nouvelles files, même avec un résultat mémorisé.
2. Masquage du titre actif : passage immédiat au prochain titre admissible ou arrêt, y compris après masquage en lot.
3. Absence ou insuffisance d’historique : état explicite, sans préférences ou souvenirs inventés.
4. Collaborations absentes, crédits ambigus, parcours impossible ou rôles incomplets : résultat réel et explication, pas remplissage hors critères silencieux.
5. Regroupements d’éditions utilisés seulement où ils sont prévus, sans fusionner de vraies variantes ni réécrire les références des playlists.
6. **Absence d’Aléatoire sur toutes les playlists intelligentes**, y compris personnalisées : page, accès contextuels et contexte de lecture, sans action de remplacement cachée. Au lancement, respecter l’ordre produit par les règles sans hériter d’un mode aléatoire actif pour une autre source. Vérifier séparément qu’Aléatoire reste disponible pour les classiques. La génération ou les départages internes d’une recette ne sont pas un mode de mélange de sa lecture.
7. Recalcul d’aperçu, changement de règles ou retour Caméléon ne remaniant pas silencieusement une file déjà active.
8. Lecture réellement hors ligne après préparation des ressources nécessaires, sans appel réseau caché.
9. Résonance : pertinence jugée sur des références variées et coûts mesurés sur des téléphones visés, avec formats et bibliothèques représentatifs.
10. Fil Rouge : pertinence mesurée séparément en français, anglais, coréen et espagnol, thèmes recherchés entre langues, textes multilingues ou incomplets, erreurs d’interprétation, corrections et absence de contenu inventé. Aucun résultat par langue ne doit être présenté comme validé avant ces essais.
11. Temps de préparation, latence de génération, mémoire, espace de stockage, consommation et reprise après interruption, sans chiffres promis à l’avance.
12. Réutilisation des paroles API réellement choisies et sauvegardées ; si l’option distante de Fil Rouge est envisagée, absence de basculement automatique ou de transfert non explicité, droits compatibles et distinction entre lecture locale et analyse nécessitant le réseau.
13. Parcours validé de téléchargement facultatif au premier lancement, report mémorisé et accès aux paramètres, Wi-Fi par défaut, accord pour les données mobiles et lecteur utilisable après refus ; téléchargement partiel/interrompu ou espace insuffisant sans faux état prêt, préparation sur action explicite et recherche sur la couverture partielle annoncée. Vérifier la réalisation des commandes, pas redemander leur rôle déjà acquis.

## 9. Suite conseillée — aucune autorisation de développement implicite

1. Conserver les acquis d’interface : page validée et absence d’Aléatoire sur toutes les intelligentes. Préciser uniquement les comportements encore ouverts, notamment la constitution/remplacement de la file, les gestes et la mémorisation des modes entre sources.
2. Définir les fondations de données manquantes : écoute comptabilisée, identité des références, historique, versions et collaborations.
3. Utiliser la cible personnelle désormais communiquée — **TECNO Camon 40 Pro 4G et environ 1 000 titres** — pour cadrer les études, sans en faire un plafond ou une garantie pour tous les téléphones. Intégrer la répartition déclarée — anglais et français principalement, avec un peu de coréen et d’espagnol — sans la redemander. Vérifier ensuite la présence effective des paroles sauvegardées ; confirmer RAM physique, espace disponible et version Android de l’exemplaire au moment d’évaluer un moteur, plutôt que redemander le modèle déjà donné.
4. Si l’utilisateur demande ensuite des prototypes, tester **en priorité les risques de Résonance et Fil Rouge**, sur des données autorisées et sans transfert implicite, avant de promettre les dix-sept modèles dans leur forme complète.
5. Préserver le [duo retenu et le téléchargement optionnel pour Fil Rouge](11-fil-rouge-moteurs.md), puis valider artefacts, distribution et méthode de combinaison. Si une incompatibilité est constatée lors d’un futur essai autorisé, discuter toute révision du choix ; si aucune voie locale ne convient, examiner le second choix en ligne, sans fournisseur ou transfert implicite. Un refus de téléchargement ne justifie pas ce recours.
6. Choisir seulement après ces arbitrages les technologies, ressources, formats et étapes de réalisation.

**Conclusion : quinze modèles sont raisonnablement concevables par données, règles et composition locale ; deux demandent une validation expérimentale de leur analyse.** Les concepts restent retenus, mais ni leur qualité finale, ni leur disponibilité sur tout téléphone, ni leur livraison ne sont démontrées par cette étude. Onde Continue reste reporté.
