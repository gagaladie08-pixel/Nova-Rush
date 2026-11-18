# NovaListen — Fil Rouge : deux modèles locaux retenus et téléchargement optionnel

> **Statut :** conception et étude documentaire du 17 novembre 2026, sans installation, téléchargement de poids, prototype ni exécution d’un modèle. L’utilisateur retient désormais **EmbeddingGemma 300M et multilingual-e5-small** pour Fil Rouge, avec téléchargement facultatif proposé au premier lancement et accessible ensuite dans les paramètres. Le parcours fonctionnel de téléchargement et de préparation est désormais validé. Les artefacts, moteurs d’exécution Android et détails de fusion restent à valider ; aucun service distant n’est configuré. La qualité sur les chansons et les performances sur le téléphone ne sont pas mesurées.
> **Références :** complète la [faisabilité des playlists](10-playlists-faisabilite-technique.md), la [sélection des modèles](09-playlists-intelligentes-selection.md) et la [gestion des paroles](04-paroles.md).

## 1. Décisions acquises et portée de l’étude

L’utilisateur confirme pour **Fil Rouge** :

- Un même thème peut rapprocher des chansons **en français, anglais, coréen ou espagnol**, sans traduction française préalable obligatoire.
- **Objectif de pertinence acquis ultérieurement — réponse 48 :** accepter un thème **central ou secondaire réellement développé dans les paroles**. Une allusion vague ou un mot isolé ne suffit pas. C’est une cible de sélection, pas une capacité démontrée des modèles ni un seuil numérique choisi.
- Réutiliser les paroles **déjà trouvées via les API, choisies et sauvegardées**, selon le parcours de la fiche Paroles. Cette source n’exclut pas implicitement les textes locaux issus des autres parcours autorisés.
- **Privilégier l’analyse locale multilingue et retenir les deux modèles : EmbeddingGemma 300M et multilingual-e5-small.** Ce choix de conception ne sélectionne pas automatiquement les conversions quantifiées citées plus bas, un moteur Android ou les paramètres de fusion.
- **Proposer le téléchargement des deux modèles au premier lancement, sans l’imposer.** Un refus laisse utiliser le lecteur et le téléchargement reste disponible dans les paramètres, sans relance à chaque ouverture. Le parcours détaillé est désormais validé : Wi-Fi par défaut avec accord explicite pour les données mobiles, deux blocs modèles/préparation, demande **Préparer maintenant / Plus tard** après téléchargement et recherche sur une couverture partielle annoncée. Voir section 3.3 ; aucun téléchargement n’est demandé dans cet espace de travail.
- **Si aucun moteur local adapté n’est trouvé ou ne convient aux contraintes, envisager une analyse en ligne si elle est réalisable.** Il s’agit d’une piste conditionnelle, pas d’un fournisseur choisi ou d’une autorisation générale d’envoyer des données.

La cible personnelle reste le **TECNO Camon 40 Pro 4G**, environ **1 000 titres**, principalement anglais et français avec un peu de coréen et d’espagnol. RAM physique de l’exemplaire, espace libre, version Android installée et nombre de paroles déjà sauvegardées restent inconnus.

**Complément transversal ultérieur — réponse 57 :** les fichiers musicaux locaux de durée connue inférieure à 60 secondes sont exclus des candidats et de la couverture admissible de Fil Rouge, même si des paroles ou des représentations ont été sauvegardées. 60 secondes exactement reste admis ; aucune suppression audio ni purge de caches n’est décidée. La réponse 61 retient une entrée en attente pour durée inconnue, hors compteurs disponibles et sans lecture ; ne pas la réintroduire dans un résultat jouable. Identification fiable, représentation des anciennes références et gestion des caches restent à concevoir. Ce filtre ne déclenche aucun téléchargement ou recalcul.

Le lecteur et les fichiers musicaux restent locaux. L’option distante éventuelle ne concerne ici que **l’analyse thématique de Fil Rouge** ; elle n’autorise ni analyse distante de Résonance, ni transfert audio, ni modification du périmètre Listening Party ou YouTube.

## 2. Deux modèles locaux retenus pour la conception, non testés

Un modèle d’**embeddings textuels** transforme le thème et les paroles en représentations numériques comparables. Cette piste vise la recherche de proximité sémantique ; elle ne nécessite pas en soi un assistant conversationnel qui génère des réponses.

### A. EmbeddingGemma 300M — piste orientée appareil local

Google présente cette version comme un modèle multilingue conçu pour l’exécution sur l’appareil, y compris les téléphones, et le fonctionnement hors ligne. La publication annonce 308 millions de paramètres, une fenêtre de 2K tokens et un entraînement sur plus de 100 langues. Il s’agit d’annonces de la publication, pas de mesures sur le Camon. [1](https://developers.googleblog.com/en/introducing-embeddinggemma/)

**Intérêt pour l’étude :** orientation mobile/hors ligne explicitement documentée et recherche sémantique correspondant au besoin général de Fil Rouge. La publication cite notamment une voie d’intégration LiteRT ; le modèle est désormais retenu pour la conception, mais cette voie d’exécution n’est pas choisie pour NovaListen. [1](https://developers.googleblog.com/en/introducing-embeddinggemma/)

**À vérifier avant intégration :**

- L’artefact exact, le moteur Android compatible, le tokenizer et les opérations réellement prises en charge.
- Qualité sur les thèmes de chansons dans les quatre langues et sur leurs mélanges.
- Coût mémoire total de l’intégration, temps d’encodage, batterie et effet sur la lecture. Les chiffres publiés avec quantification ou sur EdgeTPU ne doivent pas être transposés au Helio G100 Ultimate.
- Conditions détaillées d’accès et de redistribution à respecter : cette version relève des **Gemma Terms of Use**, pas d’une attribution automatique de licence Apache issue d’une autre version. Le modèle est utilisable localement sans facturation par requête, sous ces conditions. La fiche indique un accord aux conditions Google pour accéder aux fichiers ; aucun accord n’a été accepté dans cette étude.

Fiche exacte consultée : https://huggingface.co/google/embeddinggemma-300m

Cette étude vise précisément ce modèle texte, pas automatiquement toute version plus récente ou multimodale figurant sur une page générale de documentation. Ne pas déduire sa licence de celle d’un autre modèle.

### B. multilingual-e5-small — second modèle retenu pour la recherche multilingue

Le dépôt Microsoft E5 répertorie **multilingual-e5-small**, avec 12 couches et des vecteurs de 384 dimensions. [1](https://github.com/microsoft/unilm/blob/master/e5/README.md)

La fiche du modèle annonce 100 langues, prescrit des préfixes distincts pour les requêtes et passages selon le type de tâche, et indique une troncature des textes longs à 512 tokens. Elle avertit aussi que certaines langues moins dotées peuvent perdre en performance.

Fiche exacte consultée, y compris les limites : https://huggingface.co/intfloat/multilingual-e5-small

**Intérêt pour l’étude :** point de comparaison documenté pour rapprocher une requête et des textes multilingues, avec un protocole de préparation explicite.

**À vérifier avant intégration :**

- Une voie d’export/exécution Android compatible et la cohérence du tokenizer, des préfixes, de l’agrégation et de la normalisation.
- Le traitement des longues paroles : ne pas analyser uniquement le début sans l’indiquer ; étudier une segmentation et une agrégation adaptées.
- Les mêmes critères de qualité, mémoire, latence et consommation que pour le premier modèle, sur le téléphone cible.
- La fiche amont déclare la licence **MIT** pour ce modèle ; les notices des poids, du tokenizer, des dépendances et d’éventuelles conversions restent à vérifier et à préserver pour l’intégration. Aucun artefact communautaire n’est considéré comme automatiquement fiable ou autorisé à redistribuer.

### Gratuité des modèles et coûts distincts — vérification du 17 novembre 2026

**Réponse à la question de l’utilisateur : oui, les deux modèles eux-mêmes peuvent être utilisés gratuitement en local, sous leurs licences.** Leur exécution sur le téléphone n’est pas, en soi, un appel d’API facturé à chaque thème ou morceau.

| Version examinée | Coût du modèle pour l’exécution locale | Conditions et limites à conserver |
| --- | --- | --- |
| intfloat/multilingual-e5-small | Modèle gratuit ; pas d’abonnement ou de paiement par requête requis pour l’exécution locale des poids. | Fiche amont indiquant `license: mit`. Respecter la licence et conserver les notices applicables ; vérifier séparément les artefacts du tokenizer, conversions et dépendances distribués. |
| google/embeddinggemma-300m | Modèle disponible gratuitement pour une utilisation locale conforme aux conditions Gemma ; pas de facturation par requête inhérente au modèle local. | Conditions Google Gemma et politique d’usages autorisés/interdits, ainsi que les obligations de redistribution. L’accès aux fichiers sur Hugging Face peut nécessiter l’acceptation des conditions ; ce n’est pas une preuve d’abonnement payant. |

**Sources documentaires vérifiées :**

- Métadonnées de la fiche originale E5 indiquant MIT : https://huggingface.co/intfloat/multilingual-e5-small/raw/main/README.md — en-tête consulté, sans téléchargement des poids. Cette indication concerne le modèle amont, pas automatiquement tous les artefacts convertis ou leurs dépendances.
- Conditions Google : https://gemma.ai/resources/terms/ — version affichée comme modifiée le 1er avril 2026, dont l’annexe cite EmbeddingGemma ; elles permettent l’utilisation et la distribution sous conditions, sans prévoir de redevance d’utilisation. Fiche du modèle exact : https://huggingface.co/google/embeddinggemma-300m. Ne pas leur substituer la licence d’une autre version Gemma.

**Ce qui n’est pas automatiquement gratuit :**

- Le téléchargement initial et les mises à jour peuvent consommer le forfait Internet ; l’exécution utilise le stockage, la mémoire et la batterie du téléphone.
- Un fournisseur qui héberge ces modèles, ou un autre moteur, peut facturer son API ou son infrastructure. Des poids gratuits ne rendent pas un service en ligne gratuit ou illimité.
- Les **API de paroles** ont leurs propres conditions, quotas et tarifs éventuels, distincts du coût du moteur de Fil Rouge. La réponse 73 écarte désormais les offres facturées pour NovaListen : **API gratuites uniquement**, sans abonnement ni appels payants ni bascule automatique vers du payant ; voir la [fiche API et enrichissements](13-api-et-enrichissements.md).
- Développement, distribution, maintenance et intégration ne sont pas budgétés ici. La gratuité du modèle n’est pas une estimation du coût total du projet.

La gratuité ne fixe pas le budget global, n’accepte aucun abonnement ou contrat et ne démontre pas les performances sur le Camon. Le choix ultérieur du duo pour la conception est distinct de cette vérification de coût ; aucun téléchargement de poids dans cet espace de travail ni prototype n’est autorisé par ces décisions.

### Stockage — tailles de fichiers publiées, vérification du 17 novembre 2026

Les tailles ci-dessous sont celles affichées par les dépôts pour des **fichiers précis**, consultées sans les télécharger. Ce ne sont ni une taille d’application Android, ni une mesure de RAM sur le téléphone. Unités décimales : 1 Mo = 1 000 000 octets, 1 Go = 1 000 Mo.

| Modèle retenu | Fichier principal d’origine | Exemple quantifié publié, non sélectionné |
| --- | --- | --- |
| EmbeddingGemma 300M | `google/embeddinggemma-300m`, `model.safetensors` : **environ 1,21 Go**. | Conversion `ggml-org/embeddinggemma-300M-GGUF`, `embeddinggemma-300M-Q8_0.gguf` : **environ 334 Mo**. |
| multilingual-e5-small | `intfloat/multilingual-e5-small`, `model.safetensors` : **environ 471 Mo**. | Conversion `Xenova/multilingual-e5-small`, `onnx/model_int8.onnx` : **environ 118 Mo**. |

**Addition des deux fichiers cités :** environ **1,68 Go** pour les fichiers principaux d’origine, ou **452 Mo** pour ces deux exemples quantifiés. Ce sont deux scénarios alternatifs de comparaison, pas des ressources toutes nécessaires ensemble, ni un choix de moteurs d’exécution. Le total de 452 Mo n’est pas le budget complet de Fil Rouge.

**Sources de tailles consultées :**

- Origine Google : https://huggingface.co/google/embeddinggemma-300m/tree/main — fichier principal, dossiers de couches denses séparés et ressources du tokenizer. L’accès effectif aux poids reste soumis à l’accord aux conditions ; aucun accord n’a été accepté.
- Origine E5 : https://huggingface.co/intfloat/multilingual-e5-small/tree/main — `model.safetensors`, formats alternatifs et ressources du tokenizer.
- Conversion Gemma Q8 : https://huggingface.co/ggml-org/embeddinggemma-300M-GGUF/tree/main — fichier affiché à 334 Mo ; l’historique visible mentionne une mise à jour pour inclure les couches denses. Ce n’est pas le dépôt amont Google.
- Conversion E5 INT8 : https://huggingface.co/Xenova/multilingual-e5-small/tree/main/onnx — plusieurs variantes alternatives ; `model_int8.onnx` affiché à 118 Mo. Ce n’est pas une intégration Android déjà validée, et ce constat ne sélectionne pas Transformers.js.

**Espace supplémentaire à prévoir, sans total inventé :**

- Fichiers associés requis par l’intégration : tokenizer, configuration et éventuelles couches séparées. Par exemple, les dépôts d’origine affichent `tokenizer.json` à environ 33,4 Mo pour Gemma et 17,1 Mo pour E5, ainsi que d’autres ressources ; les besoins exacts dépendent du format choisi. Ne pas compter deux fois des ressources déjà embarquées dans un artefact.
- Bibliothèques d’exécution et ressources natives Android : une conversion GGUF et une conversion ONNX peuvent impliquer des composants différents, dont le coût n’a pas été mesuré. Les exemples de tailles ne constituent pas une stack choisie.
- Paroles conservées, représentations calculées par passage, index, métadonnées et caches ; leur volume dépend de la couverture réelle, de la segmentation et du stockage retenu, pas du seul nombre d’environ 1 000 titres.
- Marge temporaire pendant un téléchargement ou une mise à jour éventuels. Ne pas supposer qu’une future installation remplacera un fichier sans espace de travail supplémentaire.

Un dépôt peut contenir plusieurs copies ou formats du même modèle : sa taille totale ne représente pas ce que NovaListen devrait embarquer. À l’inverse, les seuls poids ne suffisent pas toujours à exécuter le modèle.

**Quantifié** signifie ici une représentation numérique réduite des poids, pas une simple archive sans perte. Compatibilité, préservation de la qualité thématique dans les quatre langues, mémoire et vitesse restent à vérifier pour chaque artefact. Une annonce de consommation RAM inférieure à 200 Mo ne doit pas être transformée en taille de téléchargement garantie.

**Réponse pratique :** les deux exemples quantifiés représentent ensemble environ **450 Mo de fichiers de modèle**, puis il faut ajouter les ressources nécessaires à l’application et aux analyses. On ne peut pas encore annoncer la taille installée finale de NovaListen. Aucun de ces fichiers n’a été téléchargé, converti ou exécuté dans cette étude.

### Ce que cette recherche permet de conclure

**Des candidats locaux existent : le besoin n’impose pas, à ce stade, de passer en ligne.** Il reste à démontrer leur adaptation à NovaListen. Un bon résultat sur un benchmark général de recherche documentaire n’est pas une preuve de bonne compréhension des métaphores ou des thèmes de chansons.

Cette comparaison n’est pas un classement exhaustif ni une affirmation que l’un des deux est le meilleur modèle actuel. **Les deux sont désormais retenus par l’utilisateur pour la conception ; aucun n’est installé ou testé.** Les variantes de fichiers et l’intégration Android restent à valider.

## 3. Chaîne locale à étudier — proposition technique

### 3.1. Préparation commune aux deux modèles retenus

1. **Réutiliser la donnée choisie et sauvegardée** par le gestionnaire de paroles : texte, source, version et langues connues. Ne pas réinterroger toutes les API à chaque création de playlist ni choisir silencieusement d’autres paroles.
2. Vérifier que les conditions du fournisseur permettent la sauvegarde et l’analyse envisagées. La présence du texte sur le téléphone n’accorde pas automatiquement tous les droits de réutilisation ou de transfert à un tiers.
3. Préparer le texte sans détruire l’original ni ses horodatages. La synchronisation et le karaoké ne sont pas des prérequis de cette analyse textuelle.
4. Si nécessaire, travailler par passages adaptés à la limite du modèle, puis étudier une manière de rapprocher le thème de la chanson entière. Les répétitions, négations et thèmes multiples peuvent fausser une moyenne naïve.
5. Mémoriser les représentations avec la version du texte, du modèle et du traitement. Une modification de paroles ou un changement de moteur peut imposer une nouvelle préparation.
6. Encoder le thème avec une méthode compatible, comparer et classer, puis revérifier que les morceaux sont **locaux, disponibles, non masqués et admissibles au filtre de durée** ; ne pas réintroduire un fichier de durée connue inférieure à 60 secondes à partir d’un ancien index.
7. Présenter l’aperçu réel, avec ses limites. Une éventuelle explication doit être fondée sur les données utilisées, pas sur des passages de paroles inventés.

L’objectif thématique central ou secondaire développé est acquis ; les filtres de langue, les seuils, l’agrégation des passages, le nombre de résultats, les corrections et leur interface restent à concevoir. La meilleure similarité d’un passage isolé ne démontre pas que le thème y est réellement développé. Les scores de similarité ne sont pas des probabilités de vérité du thème.

**Ne pas mélanger des représentations provenant de moteurs ou versions incompatibles**, même si leur nombre de dimensions est identique. Le thème et les paroles doivent être traités dans un espace de comparaison cohérent.

### 3.2. Combiner les deux modèles retenus — méthode encore proposée

En réponse à la question sur leur combinaison, la piste conseillée à comparer est une **fusion tardive de deux classements indépendants** : chaque moteur cherche le thème dans son propre index local, puis leurs résultats sont rapprochés au niveau des morceaux. Les poids des modèles ne sont ni fusionnés ni réentraînés.

**Deux modèles ne garantissent pas de meilleurs résultats qu’un seul.** Ils peuvent compléter leurs erreurs, mais aussi commettre les mêmes, ou dégrader le meilleur classement. Aucune supériorité de Gemma sur E5, ou de l’ensemble sur chacun, n’est démontrée sur les paroles de cette collection. Le choix de proposer les deux modèles dans l’application est désormais acquis ; il ne prouve pas leur compatibilité Android et n’autorise pas une installation ou une exécution dans cette étude.

#### A. Préparer deux index séparés et réutilisables

- Réutiliser **la même version choisie des paroles originales**, avec source et corrections identifiables. L’option VO/FR de l’affichage ne doit pas changer silencieusement le texte analysé. La traduction française ne devient pas un prérequis.
- Créer une copie de travail sans horodatages ou balisage non utiles à l’analyse, sans réécrire le texte sauvegardé. Préserver les négations et le contexte ; retirer des répétitions à l’aveugle peut changer le sens.
- Découper les textes longs en passages contextualisés adaptés **au tokenizer et à la limite de chaque modèle**. Les deux segmentations peuvent différer ; ne pas tronquer tous les textes à leur début pour simplifier. La prise en compte des couplets, refrains et éventuels chevauchements reste à tester.
- Produire les représentations E5 avec sa préparation requête/passage documentée, et celles d’EmbeddingGemma avec les consignes de tâche de cette version. **Index E5 et index Gemma restent distincts**, y compris leurs vecteurs de thème.
- Associer chaque représentation au morceau, à la version du texte, au modèle/artefact et au traitement utilisés. Mémoriser les résultats ; actualiser les entrées touchées par un changement pertinent plutôt que réencoder la bibliothèque à chaque demande.

Le travail initial comprend donc **deux encodages** des paroles couvertes, pas une analyse gratuite en calcul. La segmentation ajoute des représentations ; ni la taille des index ni la durée de préparation ne sont mesurées ici.

#### B. Chercher le thème dans les deux index

Pour un thème comme **repartir à zéro**, saisi en français :

1. Encoder le thème avec E5, puis comparer ce vecteur uniquement aux représentations E5.
2. Encoder le même thème avec EmbeddingGemma, puis comparer ce second vecteur uniquement aux représentations Gemma.
3. Dans chaque moteur, regrouper les résultats des passages pour obtenir **un score et une position par morceau**, avant toute fusion. Un refrain répété ou plusieurs segments d’une même chanson ne doivent pas compter comme plusieurs votes indépendants.

L’agrégation par chanson reste à évaluer : le meilleur passage seul peut survaloriser une phrase isolée, tandis qu’une moyenne de tous les passages peut diluer un thème réel. Comparer des agrégations tenant compte du contexte et des répétitions, sans présenter l’une comme déjà optimale ni produire automatiquement un résumé généré.

Pour la référence de qualité, faire porter les deux recherches sur **tous les morceaux admissibles disposant des représentations nécessaires**, et non sur les seuls titres présélectionnés par le premier modèle. L’ordre des opérations dans le temps peut être séquentiel : deux avis indépendants ne nécessitent pas deux modèles chargés simultanément.

#### C. Fusionner les positions, pas les vecteurs ni les scores bruts

Une première référence simple est la **Reciprocal Rank Fusion (RRF)**. Cette méthode additionne des contributions fondées sur les rangs, sans exiger que les scores bruts des moteurs soient comparables. Référence primaire consultée : Cormack, Clarke et Büttcher, SIGIR 2009, https://cormack.uwaterloo.ca/cormacksigir09-rrf.pdf — formule de fusion, pas validation sur NovaListen.

Pour deux listes complètes, une base à poids égaux serait :

`fusion(morceau) = 1 / (k + rang_E5) + 1 / (k + rang_Gemma)`

Les rangs commencent à 1. **`k = 60` peut servir de valeur initiale de comparaison**, comme dans l’article ; ce n’est pas une valeur optimale démontrée pour des chansons. Des poids différents ne seraient retenus qu’après évaluation, pas parce qu’un modèle est plus gros ou supposé meilleur.

- Un morceau bien placé par les deux reçoit deux contributions favorables.
- Un morceau repéré surtout par un seul reste candidat : ne pas imposer une intersection qui éliminerait les découvertes complémentaires.
- Si une variante ne conserve que les premiers résultats de chaque moteur, prendre leur **union**, sans inventer de rang pour un morceau absent d’une liste : cette liste ne lui apporte aucune contribution. La profondeur conservée doit être testée, car elle peut faire perdre des titres pertinents.
- Ne pas additionner directement les similarités cosinus des deux moteurs : leurs distributions ne sont pas calibrées entre elles. **Réduire deux vecteurs à la même dimension ne rend pas leurs espaces compatibles.**

La RRF produit un ordre, **pas une probabilité ni une preuve que le thème est présent**. Même deux listes entièrement hors sujet ont un premier résultat. Il faut donc étudier séparément une règle de pertinence minimale, calibrée sur des exemples jugés dans les quatre langues à partir des signaux propres à chaque moteur ; ne pas fixer un seuil de fusion ou de cosinus arbitraire. Autoriser un résultat vide ou peu fourni plutôt que remplir une playlist avec des associations faibles.

L’accord de deux modèles n’est pas une explication du sens. Ne pas les présenter comme un vérificateur infaillible ou comme un troisième modèle de relecture conjointe thème/paroles ; les rapprochements et d’éventuels extraits doivent rester fidèles au texte réel.

#### D. Adapter le coût au Camon, sans changer la promesse silencieusement

- **Préparation par lots et modèles chargés successivement** : piste pour limiter la coexistence des ressources en mémoire. Cela ne prouve pas que chacun, le chargement/déchargement ou l’ensemble respectera le budget mémoire réel.
- Après préparation, réutiliser les deux index ; pour un nouveau thème, les deux encodages de requête restent à produire, avec éventuellement deux chargements de modèle. Comparer les vecteurs mémorisés est une opération distincte de l’encodage des paroles ; ne pas annoncer un résultat instantané.
- Davantage de ressources à distribuer, de stockage et de travail initial que pour un seul moteur ; mesurer aussi chauffe, batterie et continuité de lecture. Les licences et notices des deux intégrations restent à respecter.
- Si une préparation est incomplète ou un moteur indisponible, rendre la couverture et le mode réellement utilisés identifiables. La recherche sur les titres déjà préparés pour les deux modèles est validée avec indication de couverture partielle. Un morceau non préparé n’est pas un morceau jugé hors sujet ; un mode à un seul moteur reste à décider, sans afficher une analyse à deux moteurs quand un seul a servi.
- **Aucun recours réseau n’est nécessaire au principe de fusion locale.** Il faut néanmoins disposer des modèles et textes sur le téléphone, avec les droits nécessaires. Un échec ne déclenche ni téléchargement implicite de paroles, ni envoi à un service distant.

Une **cascade E5 → Gemma** est une autre variante à comparer si le coût de préparation Gemma est trop élevé : présélection avec E5, puis encodage/réévaluation Gemma des seuls candidats non encore préparés. Elle peut réduire ce travail, mais Gemma ne récupère pas les titres écartés par E5. Elle ne peut donc pas être déclarée meilleure pour la pertinence ; la taille de présélection et le gain de coût doivent être mesurés. Elle ne transforme pas EmbeddingGemma en modèle de relecture conjointe ni en arbitre supérieur.

Si l’index Gemma complet est déjà préparé, limiter ses comparaisons aux seuls candidats E5 ne supprime pas à lui seul l’encodage du thème ou le chargement de Gemma : le gain n’est pas celui d’éviter toute l’analyse des paroles.

#### E. Choisir sur une comparaison réelle, pas sur le nombre de modèles

Pour valider la méthode de combinaison du duo choisi, comparer sur les mêmes textes, critères et conditions :

1. **E5 seul.**
2. **EmbeddingGemma seul.**
3. **Deux recherches indépendantes + fusion RRF.**
4. Éventuellement la cascade, si son compromis de coût justifie son étude.

Constituer un petit ensemble de thèmes et de chansons jugés manuellement, en distinguant thèmes centraux, thèmes secondaires développés, simples allusions, rapprochements trompeurs et absences de correspondance. Couvrir français, anglais, coréen, espagnol, mélanges de langues, longues paroles, métaphores et négations. Vérifier ce que chaque moteur apporte réellement de différent ; une bonne moyenne ne doit pas cacher la dégradation d’une langue minoritaire.

Évaluer la pertinence en tête de liste, les chansons pertinentes manquées et les faux positifs, puis mémoire, stockage, préparation, latence et batterie. Régler éventuellement fusion et seuils sur une partie des exemples puis vérifier sur d’autres, sans annoncer un progrès mesuré sur les exemples ayant servi aux réglages.

**Recommandation de conception : le duo est retenu, tandis que la double recherche avec fusion reste une méthode à évaluer, pas une gagnante acquise. Si le gain est insuffisant ou le coût excessif sur le téléphone, présenter les résultats et discuter une révision avec l’utilisateur plutôt que retirer silencieusement un modèle choisi.** Aucun modèle n’est installé et aucun essai n’est lancé par cette explication.

Quel que soit le résultat technique : exclure du résultat les fichiers masqués ou indisponibles, ne rien inventer sans paroles, revérifier l’admissibilité avant lecture, conserver l’ordre de la playlist intelligente **sans Aléatoire** et ne pas remanier silencieusement une file active. Cette étude de combinaison ne valide pas les contrôles sémantiques encore ouverts ; le parcours de téléchargement et de préparation est acquis séparément en section 3.3.

### 3.3. Téléchargement et préparation — parcours fonctionnel validé

L’utilisateur valide **tout le parcours présenté**, consigné dans la [fiche de téléchargement et de préparation](12-fil-rouge-telechargement-et-preparation.md). L’accord dépasse donc le seul déclenchement de la préparation, sans s’étendre aux choix techniques non arrêtés.

1. **Premier lancement** : explication de Fil Rouge, taille réelle des ressources à déterminer et actions **Télécharger les modèles / Plus tard**, sans bloquer le lecteur ni répéter l’invitation à chaque ouverture après report.
2. **Réseau** : **Wi-Fi uniquement par défaut** ; passage volontaire aux données mobiles avec confirmation du volume restant. Ne pas transférer silencieusement le lot sur un autre type de réseau.
3. **Paramètres → Modèles de Fil Rouge** : blocs distincts modèles/préparation, état de chaque ressource, tailles, progressions et commandes contextuelles de téléchargement, pause, reprise, annulation ou nouvelle tentative. Le rôle des commandes est acquis, leur réalisation et la continuité exacte après fermeture restent à vérifier sur Android.
4. **Première préparation des paroles** : demander **Préparer maintenant / Plus tard** une fois les deux modèles disponibles ; ne pas lancer automatiquement toute l’analyse après téléchargement. Le report conserve l’accès volontaire dans les paramètres.
5. **Couverture réelle** : distinguer modèles disponibles et titres préparés ; compteur en titres avec paroles exploitables et recherche possible sur la couverture déjà préparée pour les **deux modèles**, annoncée comme partielle. Un mode à un seul modèle n’est pas décidé.

Le téléchargement reste distinct de la recherche/du choix des paroles et de la préparation locale. Les principes de retrait et de mise à jour conservent confirmation/information et protection des données ; gestion précise des caches, fichiers temporaires et anciennes générations encore ouverte. Les **452 Mo** d’exemples ne deviennent pas une taille de lot garantie.

Une coupure, un refus ou l’absence de modèle ne déclenchent aucun service distant. **Le refus d’un téléchargement n’est pas la preuve qu’aucune solution locale ne convient.** Les modèles déjà présents ne deviennent pas inutilisables simplement parce que le réseau manque. La lecture musicale reste indépendante, sans remaniement silencieux de la file active.

L’accord porte sur le parcours fonctionnel, pas sur les artefacts, la fusion RRF, le circuit de distribution, les licences/notices et l’éventuel consentement Gemma, l’hébergement, les seuils, l’apparence finale ou les performances. Ces modalités restent à préciser ou vérifier. Aucun compte obligatoire supplémentaire, acceptation de licence, téléchargement, développement ou essai n’est autorisé dans cet espace de travail.

## 4. Critères d’un éventuel essai futur

Aucun des essais ci-dessous n’a été exécuté ; un prototype nécessite une demande distincte.

- Comparaison E5 seul, EmbeddingGemma seul et fusion des deux classements, avec jugements communs et exemples de vérification distincts de ceux utilisés pour les réglages. Le duo est choisi ; ces mesures doivent guider les réglages et signaler toute difficulté nécessitant une révision discutée avec l’utilisateur, pas retirer implicitement un modèle.
- Pertinence d’un thème français face à des textes français, anglais, coréens et espagnols, évaluée séparément par langue ; faux positifs et cas sans résultat pertinent, pas uniquement les meilleurs exemples réussis.
- Chansons multilingues, métaphores, négations et thèmes voisins qui ne doivent pas être confondus.
- Cas sans paroles : état explicite, sans déduire le sens du seul titre ou de l’artiste. Le morceau reste disponible ailleurs dans la bibliothèque.
- Paroles sélectionnées correspondant à la bonne version du morceau ; conservation des corrections et de la provenance.
- Temps de préparation initiale et incrémentale, mémoire de pointe, taille des ressources, batterie, chauffe et réactivité pendant une lecture.
- Parcours facultatif au premier lancement, refus non bloquant, accès ultérieur aux paramètres, coupure, manque de place, intégrité, téléchargement partiel et distinction entre modèles présents et analyse prête. Le parcours fonctionnel et les rôles des commandes sont validés ; leur réalisation technique reste à tester.
- Fonctionnement hors ligne une fois les ressources locales nécessaires présentes ; aucun appel réseau de secours caché.
- Respect du masquage global, de l’absence d’Aléatoire sur toutes les intelligentes et de l’absence de remaniement silencieux de la file active.

Le nombre d’environ 1 000 titres est une échelle de travail, pas un plafond ni la preuve d’une latence ou d’une consommation donnée.

## 5. Solution en ligne — seulement comme piste de second choix

### Conditions avant de l’envisager concrètement

Si aucune solution locale adaptée ne convient après l’évaluation des voies locales, comparer un service distant sur la qualité multilingue, les quotas, la disponibilité, les langues, les conditions d’utilisation et la confidentialité. Le refus de télécharger le duo n’est pas cette situation. La réponse 73 impose désormais un service gratuit, sans abonnement ni appels facturés ni bascule automatique vers du payant. Aucun fournisseur n’est choisi ; l’existence d’une offre gratuite adaptée n’est pas démontrée, et aucun accès illimité ou disponible dans toutes les régions n’est supposé.

L’accord pour **étudier** cette option ne vaut pas envoi effectif des 1 000 textes ou du thème à un tiers.

### Garde-fous à prévoir

- Un mode distant explicite et compréhensible, pas de basculement automatique après une erreur locale, un manque de paroles ou une coupure réseau.
- Informer des données envoyées et du fournisseur. Étudier le minimum nécessaire de texte et le thème ; **aucun fichier audio, microphone, historique ou collection entière n’est à transmettre implicitement**.
- Vérifier que les conditions des API de paroles permettent l’usage et le transfert envisagés, séparément de leur affichage ou sauvegarde.
- Examiner conservation, utilisation pour l’entraînement, suppression et localisation des données auprès du fournisseur retenu, sans promettre des garanties non vérifiées.
- Examiner les limites de l’offre gratuite et la gestion sûre des éventuels secrets de service ; ne pas adopter un service facturé pour contourner un quota. Aucun mot de passe ou jeton n’est demandé dans cette conversation.

### Limites du hors ligne à expliquer

- Si le service est nécessaire pour analyser de nouvelles paroles ou encoder un nouveau thème, cette opération nécessite une connexion. Une préparation distante ne rend pas magiquement tout futur thème calculable hors ligne.
- Les fichiers musicaux déjà présents restent lisibles localement, indépendamment de l’accès au service.
- La réutilisation de caractéristiques ou d’un résultat déjà préparé peut être étudiée, mais ne doit pas être présentée comme un nouveau calcul complet si des données manquent. État de fraîcheur, couverture et comportement sans réseau restent à définir.
- Dans tous les cas, les règles de disponibilité et de masquage doivent être revérifiées avant une nouvelle lecture ; ne pas injecter silencieusement un résultat dans la file active.

La traduction française, la recherche de paroles par API et l’analyse thématique distante sont **trois opérations distinctes**. Autoriser l’une n’active pas implicitement les autres.

## 6. Recommandation actuelle

**Conserver le duo choisi — EmbeddingGemma 300M et multilingual-e5-small — et le téléchargement facultatif proposé au premier lancement, toujours accessible ensuite dans les paramètres.** Valider les artefacts et moteurs d’exécution, puis la méthode de combinaison, seulement sur demande ultérieure d’un essai autorisé sur la cible. La fusion RRF reste proposée ; le parcours fonctionnel de téléchargement/préparation est validé, mais son apparence finale, sa réalisation, sa qualité d’analyse et son coût ne sont pas mesurés ou arrêtés. Toute difficulté conduisant à modifier le duo doit être discutée avec l’utilisateur. Conserver l’analyse en ligne comme alternative à étudier si aucune solution locale ne convient, jamais en réponse automatique au refus de télécharger.

Le principe multilingue, la réutilisation des paroles API sauvegardées, le choix des deux modèles et leur mise à disposition facultative au démarrage ou ultérieurement dans les paramètres sont acquis. La possibilité conditionnelle d’étudier le second choix en ligne reste acquise, sans transfert autorisé. Les modèles eux-mêmes sont utilisables gratuitement en local sous leurs licences. Les formats exacts, moteurs d’exécution, réglages sémantiques, apparence finale, qualité obtenue, budgets d’intégration, choix d’un éventuel service distant gratuit et accord pour un éventuel transfert de données restent ouverts ; les API facturées sont exclues par la réponse 73. Les commandes fonctionnelles de téléchargement et de préparation sont désormais acquises dans la fiche dédiée.

**Travail réalisé ici : consultation de documentation et mise à jour de conception uniquement.** Aucun logiciel installé, modèle téléchargé, compte créé, condition d’accès acceptée, fichier musical/parole envoyé, application exécutée ou APK produit.
