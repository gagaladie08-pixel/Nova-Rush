# NovaListen — Fil Rouge : téléchargement des modèles et préparation des paroles

> **Statut :** parcours fonctionnel présenté puis **validé par l’utilisateur le 17 novembre 2026 : « Je valide tout »**. Sont acquis le premier lancement facultatif, le Wi-Fi par défaut, la gestion des modèles dans les paramètres, la préparation séparée sur action explicite et la recherche sur une couverture partielle annoncée. Aucune implémentation, maquette exécutable, récupération de poids ou analyse de paroles n’est réalisée.
> **Portée :** cette validation fixe le parcours et les rôles des commandes, pas une stack, un artefact quantifié, une méthode de fusion, une performance ou une autorisation de prototypage. Les détails explicitement laissés ouverts restent tels, notamment apparence, hébergement, caches et limites Android. Les [modèles retenus, licences, tailles indicatives et méthodes de combinaison](11-fil-rouge-moteurs.md) sont documentés séparément ; aucune condition de licence n’est acceptée par cet accord de conception.

## 1. Acquis à conserver

- **EmbeddingGemma 300M et multilingual-e5-small** sont tous deux retenus pour la conception de Fil Rouge. Les artefacts quantifiés précis, moteurs d’exécution Android et réglages de fusion ne sont pas encore choisis ou testés.
- Proposer le téléchargement des modèles **au premier lancement, de manière facultative**.
- En cas de refus ou de report, le lecteur musical local reste utilisable et le téléchargement demeure possible **dans les paramètres**, sans répéter l’invitation à chaque ouverture.
- **Wi-Fi uniquement par défaut**, avec changement volontaire et confirmation du volume restant avant utilisation des données mobiles.
- Rubrique **Paramètres → Modèles de Fil Rouge**, avec deux blocs distincts : modèles et préparation des paroles. États, tailles, progressions et commandes contextuelles du parcours sont validés dans leur rôle ; leur réalisation reste à vérifier.
- Après disponibilité des deux modèles, demander **Préparer maintenant / Plus tard**, sans démarrer automatiquement la première analyse des paroles.
- Afficher la couverture réelle en titres et permettre une recherche sur les titres déjà préparés pour les deux modèles, en indiquant explicitement une couverture partielle.
- Réutiliser les paroles réellement trouvées, choisies et sauvegardées selon la [fiche Paroles](04-paroles.md), avec analyse multilingue sans traduction française obligatoire.
- Local en priorité. Un refus, une erreur ou une préparation incomplète ne déclenchent ni service distant, ni recherche de paroles, ni traduction, ni transmission de fichiers.
- Une solution en ligne ne peut être étudiée en second choix que si aucune voie locale adaptée ne convient, avec un examen et des accords distincts. Refuser les modèles ne remplit pas cette condition.
- Le [masquage global](02-bibliotheque.md), la disponibilité des fichiers et les règles de la [page Playlists](07-playlists.md), notamment l’absence d’Aléatoire sur toutes les intelligentes, restent applicables.

Ce parcours ne définit ni un huitième onglet, ni une nouvelle page d’Accueil, ni le moteur de Résonance, de traduction ou de synchronisation des paroles. Aucun développement ou essai n’est lancé ici.

## 2. Parcours général validé

1. Au premier lancement, présenter l’intérêt de Fil Rouge et le téléchargement facultatif des deux modèles.
2. **Plus tard** laisse entrer dans l’application et mémorise le report ; ne pas imposer à nouveau cette invitation à chaque ouverture.
3. **Télécharger les modèles** lance, après les vérifications nécessaires, une récupération dont l’état est accessible dans les paramètres. L’utilisateur peut rejoindre le lecteur sans attendre la fin.
4. Une fois les deux modèles présents et vérifiés, proposer une étape distincte : **Préparer maintenant** / **Plus tard** pour les paroles sauvegardées.
5. Préparer progressivement les représentations locales, puis rendre identifiable la couverture réellement utilisable par Fil Rouge. Les recherches de thèmes ne doivent pas prétendre couvrir des titres non préparés.

**Décision validée : demander avant de commencer la première préparation des paroles.** L’accord pour télécharger ne devient pas silencieusement un accord pour lancer immédiatement toute l’analyse de la bibliothèque.

## 3. Premier lancement — organisation et commandes validées

### Texte principal

**Fil Rouge — vos chansons réunies par leurs thèmes**

> Téléchargez les deux modèles pour rechercher des thèmes dans les paroles sauvegardées sur votre téléphone. L’analyse pourra ensuite fonctionner localement, une fois les paroles préparées. Vous pouvez continuer sans les modèles et les télécharger plus tard dans les paramètres.

Sous ce texte, afficher :

- Les noms **EmbeddingGemma 300M** et **multilingual-e5-small**.
- Le volume réel restant à télécharger et l’espace nécessaire, fichiers associés et marge temporaire compris lorsque ces valeurs sont déterminées. Ne pas présenter les **452 Mo** d’exemples quantifiés comme une taille définitive de NovaListen.
- Un accès aux informations de licence et aux conditions applicables ; ne pas accepter automatiquement un accord requis à la place de l’utilisateur.
- **Wi-Fi uniquement** : option validée, activée par défaut ; les données mobiles nécessitent un choix volontaire et une confirmation du volume restant.

### Actions

| Commande validée | Rôle dans le parcours | Ce qu’elle ne fait pas |
| --- | --- | --- |
| **Télécharger les modèles** | Vérifier conditions d’accès, ressources, réseau choisi et espace, puis lancer ou mettre en attente le téléchargement autorisé. | Ne recherche pas les paroles et ne démarre pas automatiquement leur première préparation. |
| **Plus tard** | Fermer l’invitation et continuer dans NovaListen ; conserver l’accès volontaire aux paramètres. | Ne désactive pas définitivement Fil Rouge et ne lance aucun transfert en arrière-plan. |

Si le Wi-Fi uniquement est choisi sans Wi-Fi disponible : afficher **En attente de Wi-Fi**. Ne pas passer aux données mobiles silencieusement ; permettre de changer volontairement ce choix avec confirmation du volume restant. Le report doit rester possible même si l’espace, le réseau ou les conditions nécessaires ne sont pas réunis.

La forme de présentation, les couleurs et la position exacte dans l’éventuel accueil initial de l’application ne sont pas fixées par cette fiche.

## 4. Paramètres — organisation et commandes validées

Rubrique validée : **Paramètres → Modèles de Fil Rouge**. L’organisation complète des paramètres généraux et leur accès dans le reste de l’application ne sont pas définis par cette validation.

### Bloc A — modèles

- Une explication courte de leur rôle et de l’indépendance du lecteur musical.
- Une ligne ou carte par modèle : nom, version de ressource, taille utile et état réel.
- Une progression par modèle et un bilan du lot. La progression globale doit représenter le volume réel, pas la moyenne trompeuse de deux pourcentages lorsque les fichiers ont des tailles différentes.
- Le réglage réseau et un accès aux licences/informations techniques.
- Les commandes adaptées à la situation, sans afficher toutes les commandes en permanence.

| État | Commandes validées dans leur rôle | Précision |
| --- | --- | --- |
| **Non téléchargés** | Télécharger les modèles | Ne télécharger que les ressources nécessaires manquantes, pas toutes les variantes d’un dépôt. |
| **En attente de Wi-Fi** | Modifier le choix réseau · Annuler | Aucun gros transfert sur données mobiles sans accord correspondant. |
| **Téléchargement en cours** | Pause · Annuler | Continuer à naviguer et écouter ; ne pas remplacer les commandes du mini-player par celles du téléchargement. |
| **En pause / Interrompu** | Reprendre · Annuler | Réutiliser uniquement les données partielles dont la version et l’intégrité peuvent être vérifiées ; sinon expliquer ce qui doit être récupéré à nouveau. |
| **Erreur** | Réessayer · Voir le problème | Distinguer réseau, accès aux fichiers, manque de place, ressource corrompue ou incompatible. |
| **Vérification** | Consulter l’état | Un transfert terminé ne signifie pas encore que le duo est utilisable. |
| **Disponibles** | Voir les détails · Supprimer les modèles | Les deux modèles et leurs ressources nécessaires sont présents et vérifiés ; la préparation des paroles reste un état séparé. |

Les rôles des commandes pause/reprise sont validés ; leur réalisation et la continuité après fermeture restent à vérifier selon les contraintes Android. Ce ne sont pas des fonctions déjà disponibles. Si un seul modèle est prêt, afficher **Téléchargement partiel** avec le détail des deux ; un mode d’analyse à un seul moteur reste à décider, pas à activer silencieusement.

### Bloc B — préparation des paroles

Le conserver distinct du téléchargement :

- État : **À préparer**, **Préparation en cours**, **En pause**, **Partiellement préparé** ou **Préparation à jour**.
- Couverture réelle en titres, et non en nombre de passages de paroles.
- Action contextuelle : **Préparer les paroles**, **Pause**, **Reprendre** ou **Préparer les nouveaux titres**, selon les données présentes.
- Explication visible lorsqu’aucune parole exploitable n’est disponible, lorsqu’un modèle manque ou qu’une version d’index est incompatible.

## 5. Après le téléchargement — préparation sur action explicite validée

### Première demande de préparation

Une fois les deux modèles utilisables :

**Les modèles sont disponibles**

> Vous pouvez maintenant préparer les paroles déjà sauvegardées pour Fil Rouge. Cette étape se fait sur le téléphone et peut prendre du temps. Elle ne télécharge pas de paroles supplémentaires.

Deux boutons validés : **Préparer maintenant** / **Plus tard**.

- **Préparer maintenant** démarre le travail local sur les textes exploitables et admissibles. La possibilité de lire sa musique reste indépendante ; vitesse, chauffe et effet sur la lecture devront être mesurés.
- **Plus tard** conserve les modèles et laisse démarrer la préparation depuis les paramètres ou depuis un message contextuel de Fil Rouge. Ne pas redemander à chaque ouverture de l’application.
- Si aucun texte exploitable n’existe, expliquer le parcours de recherche et de choix déjà prévu dans la fiche Paroles. Ne pas lancer une recherche multi-API pour toute la bibliothèque sans demande distincte, ni afficher une préparation fictive.

### Compteurs et couverture

Présentation validée : **Titres préparés / Titres avec paroles exploitables**, accompagnée du nombre de titres sans paroles exploitables parmi les morceaux locaux disponibles et non masqués. **Complément ultérieur — réponse 57 :** les compteurs et la couverture admissible excluent les fichiers de durée connue inférieure à 60 secondes. La réponse 61 concerne un autre état : les fichiers de **durée inconnue** sont signalés **en attente**, hors compteurs disponibles et sans lecture avant identification. Ces fichiers en attente ne sont pas comptés dans la couverture actuellement admissible ni réintroduits dans un résultat jouable ; les détails d’identification et de présentation restent à concevoir. Ce filtre n’efface pas l’audio et ne vaut pas accord de nettoyage automatique des représentations.

- Un titre est préparé pour le duo lorsque les représentations nécessaires aux **deux modèles** correspondent à la bonne version du texte et du traitement. Une réussite E5 seule ne devient pas une réussite du duo.
- Plusieurs passages d’une chanson ne font pas plusieurs titres. Les textes partiels, erreurs et absences doivent rester distinguables.
- Le nombre d’environ 1 000 titres déclaré pour la collection n’est pas un inventaire : ne pas l’utiliser comme dénominateur réel affiché.
- Un titre masqué, devenu inaccessible ou de durée connue inférieure à 60 secondes ne doit plus figurer dans la couverture admissible affichée ou dans les résultats, même si des représentations existent encore. Les règles de nettoyage des caches restent à décider séparément.
- Sans estimation fiable, ne pas inventer de durée restante. Une progression en titres est un bilan de couverture, pas la garantie que toutes les chansons prennent le même temps.

**Décision validée :** permettre une recherche sur les titres déjà préparés pour les deux modèles, avec une indication explicite de couverture partielle, plutôt que forcer l’attente de toute la collection. Ne pas mélanger cet état avec un mode à un seul modèle. Un choix ultérieur (réponse 48) fixe l’objectif de pertinence : thème central ou secondaire réellement développé, pas simple allusion ou mot isolé. La méthode permettant de viser cet objectif, les seuils sémantiques et le détail graphique de l’indication restent à préciser ; ni l’accord de couverture partielle ni cet objectif ne prouvent la qualité du moteur.

### Préparation progressive et mises à jour

- Préparer par petits lots, mémoriser les étapes valides et prévoir pause/reprise. Ne pas promettre un traitement indéfini en arrière-plan après fermeture de l’application.
- Privilégier la réactivité du lecteur ; les critères de pause liés à la chauffe, à la batterie ou à la charge restent à concevoir et mesurer.
- Après ajout ou correction de paroles, ne refaire que le travail pertinent. Le parcours retenu signale les titres à préparer et laisse une action explicite ; une extension future vers une préparation automatique n’est pas décidée par cet accord.
- Une modification de version de modèle ou de traitement peut invalider certaines représentations. Ne pas mélanger des espaces incompatibles ni recommencer silencieusement l’ensemble des analyses à chaque lancement.
- Ne modifier ni les fichiers audio, ni les paroles originales, ni leurs horodatages. Les représentations calculées sont des données dérivées séparées.

## 6. Interruptions, espace et retrait des ressources — règles de parcours et limites

- **Réseau coupé :** conserver l’état exact et proposer une reprise ; ne pas compter un fichier partiel comme disponible. Des modèles déjà opérationnels restent utilisables hors ligne avec les données locales nécessaires.
- **Espace insuffisant :** expliquer le besoin estimé et laisser reporter. Ne supprimer ni musique ni autres données pour faire de la place sans décision spécifique de l’utilisateur.
- **Source ou conditions d’accès indisponibles :** rendre le problème compréhensible, conserver le lecteur utilisable et ne contourner aucun contrôle d’accès. Le circuit autorisé de distribution et l’hébergement ne sont pas encore choisis.
- **Fichier corrompu/incompatible :** ne pas activer le lot concerné ; une nouvelle récupération doit rester identifiable. Ne jamais prétendre que les deux modèles sont prêts pour masquer un échec.
- **Annuler un téléchargement :** arrêter le travail autorisé sans supprimer un modèle déjà valide. Proposer d’indiquer puis de retirer les seuls fichiers temporaires inutilisables ; règle de conservation exacte à confirmer.
- **Supprimer les modèles :** demander confirmation, montrer l’espace réellement libérable lorsque connu et expliquer l’indisponibilité des nouvelles analyses qui en dépendent. Les musiques, paroles, favoris et définitions de playlists ne sont pas supprimés. Le devenir des caches et des anciennes générations de Fil Rouge reste un arbitrage distinct ; ne pas promettre leur recalcul sans ressources.
- **Mise à jour :** afficher taille et conséquence sur la préparation, respecter le choix réseau et ne pas interpréter un accord initial comme un consentement illimité à tous les transferts futurs. Préserver une version encore valide jusqu’au remplacement vérifié est une piste à étudier avec sa marge d’espace, pas un mécanisme déjà choisi.

Une file déjà lancée n’est pas remaniée silencieusement par téléchargement, préparation, mise à jour ou suppression de modèle. Le masquage du titre actif garde sa priorité : prochain titre non masqué ou arrêt selon la règle acquise. Aucune commande Aléatoire n’est ajoutée à Fil Rouge.

## 7. Récapitulatif des validations et des points encore ouverts

| Point | Décision ou limite | Statut |
| --- | --- | --- |
| Modèles et téléchargement | Les deux modèles retenus ; proposition facultative au premier lancement et accès ultérieur aux paramètres. | Validé. |
| Déclenchement de la première préparation | Demander **Préparer maintenant / Plus tard** après disponibilité des deux modèles ; ne pas la lancer automatiquement. | Validé. |
| Réseau par défaut | Wi-Fi uniquement, avec changement volontaire et confirmation du volume sur données mobiles. | Validé. |
| Rappels après report | Ne pas répéter à chaque lancement ; conserver paramètres et message contextuel à la demande de Fil Rouge. | Validé. |
| Paramètres et commandes | Deux blocs distincts, états réels, progressions et rôles de téléchargement, pause, reprise, annulation et nouvelle tentative. | Validé comme parcours ; faisabilité à vérifier. |
| Préparation incomplète | Rechercher sur la couverture préparée pour le duo, explicitement indiquée comme partielle. | Validé ; qualité et seuils sémantiques à vérifier. |
| Retrait et mises à jour | Confirmation, information sur l’espace et les conséquences, protection des musiques/paroles et respect du choix réseau selon la section 6. | Principes de parcours retenus ; gestion précise des caches, fichiers temporaires et anciennes générations encore ouverte. |
| Réalisation technique | Artefacts, moteurs Android, fusion, hébergement autorisé, ressources et contraintes d’arrière-plan. | Non choisis ou non démontrés ; aucun essai autorisé ici. |
| Présentation finale | Couleurs, dimensions, détails visuels et organisation globale des paramètres. | À préciser, sans remettre en cause le parcours validé. |

Le parcours présenté est validé dans son ensemble : ne pas redemander le choix du duo, le téléchargement facultatif, le Wi-Fi par défaut, l’organisation des paramètres, le déclenchement explicite de la préparation ou la recherche sur une couverture partielle annoncée. **Cette validation ne sélectionne pas les formats quantifiés, l’algorithme de fusion ou un fournisseur, n’accepte pas les licences à la place de l’utilisateur et n’autorise aucun téléchargement effectif, développement ou prototype.**

## 8. Vérifications futures, non exécutées

- Refus/report au premier lancement et accès ultérieur aux paramètres, sans bloquer l’écoute ni ajouter une relance forcée.
- Accord réseau respecté, attente de Wi-Fi, coupure et réouverture, sans changement silencieux vers les données mobiles.
- Taille du lot et espace de travail cohérents ; contrôle des ressources, licences et intégrité avant disponibilité.
- Un seul modèle téléchargé, lot incomplet, erreur ou absence de paroles ne produisant pas de faux état prêt.
- Distinction visible entre téléchargement terminé, préparation partielle et couverture à jour pour les deux modèles.
- Pause/reprise et préparation incrémentale sans perdre les étapes valides, réanalyser inutilement toute la collection ou dégrader la lecture au-delà des limites qui seront fixées.
- Aucun appel implicite de récupération de paroles, traduction, service distant, microphone ou transfert audio/paroles.
- Masquage global, disponibilité, absence d’Aléatoire et stabilité de la file active respectés dans tous les états.

**Travail réalisé : conception et documentation uniquement. Aucun modèle téléchargé, condition acceptée, texte analysé, logiciel installé ou APK produit.**
