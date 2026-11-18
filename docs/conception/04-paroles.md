# NovaListen — Paroles, traduction et synchronisation

> **Statut :** conception en cours, sans implémentation. Les demandes explicites de l’utilisateur sont distinguées des recommandations et objectifs dont la faisabilité doit être vérifiée.
> **Lien :** complète la [fiche Player et mini-player](03-player-et-mini-player.md). Les règles déjà validées de navigation, de masquage des commandes et de maintien de l’écran allumé restent inchangées.

## 1. Intention

Proposer un gestionnaire de paroles pour les fichiers musicaux locaux : rechercher, comparer, choisir, conserver, afficher, traduire et, lorsque les données le permettent, synchroniser les paroles.

Le périmètre musical suit aussi le filtre local acquis par la réponse 57 : les fichiers de durée connue inférieure à 60 secondes sont exclus de l’application, sans effacement audio ; leurs paroles ne les réintroduisent pas dans les résultats ou préparations admissibles. Une durée inconnue est signalée en attente, hors compteurs disponibles et sans lecture avant identification ; l’identification fiable, les actions sur cette entrée et le nettoyage d’anciennes données dérivées restent à définir.

Le cœur musical reste local. La recherche multi-API et la traduction distante sur demande sont des opérations d’enrichissement connectées ; elles ne doivent pas rendre nécessaire une connexion pour relire un contenu déjà sauvegardé lorsque les droits du fournisseur le permettent.

## 2. Demandes fonctionnelles confirmées

### Recherche et choix manuel

- Un bouton **Rechercher les paroles** est présent dans les paramètres propres aux paroles.
- La recherche consulte plusieurs API.
- Une fois la recherche terminée, présenter les résultats des différentes sources pour que **l’utilisateur choisisse** les paroles à appliquer.
- Marquer **Synchro** les résultats disposant de paroles synchronisées.
- Si aucune parole n’est trouvée, permettre une **recherche sur le Web**, puis le retour dans l’application pour **coller les paroles**.
- Ne pas sélectionner ou appliquer silencieusement le premier résultat à la place du choix utilisateur.

### Traduction française réversible

- Un bouton permet de **traduire les paroles en français**.
- En mode français, le texte original disparaît de l’affichage au profit du texte français ; il ne s’agit pas d’un affichage bilingue simultané.
- Le passage inverse réaffiche l’original.
- Conserver les deux versions sans écraser l’original.
- Conserver la synchronisation lorsque l’original en possède une, avec les limites de précision détaillées en section 5.
- La VF concerne le texte affiché ; elle ne remplace pas la voix de l’enregistrement par une interprétation chantée en français.
- **Moteurs et rôles validés par « Oui je valide » : ML Kit par défaut en local ; Azure Translator standard F0 uniquement sur demande explicite.** Réutiliser une VF sauvegardée sans nouvelle traduction ; pas de bascule automatique vers Azure après refus de packs, erreur locale ou qualité jugée insuffisante. Compte, téléchargements, commandes détaillées, droits et intégration restent distincts de ce choix de conception ; voir section 8.

### Sauvegarde durable

- Les paroles retenues sont sauvegardées pour ne pas devoir les rechercher à chaque lecture.
- La traduction française est également sauvegardée pour permettre les bascules VO/FR sans retraduction systématique.
- Le même principe de sauvegarde concerne les métadonnées et les pochettes acquises ou choisies dans les parcours d’enrichissement.
- « Sauvegarder une fois trouvées » s’applique aux paroles **choisies par l’utilisateur**, conformément à l’exigence de choix manuel entre résultats. Une liste de candidats ne doit pas écraser les données actuelles.
- L’écriture des métadonnées dans le fichier audio d’origine a déjà été choisie dans la [fiche Bibliothèque](02-bibliotheque.md). Les adaptations nécessaires aux paroles multilingues et synchronisées sont à vérifier selon les formats ; voir section 6.

### Réutilisation par Fil Rouge — principe confirmé

- L’utilisateur confirme que **Fil Rouge peut réutiliser les paroles trouvées via les API, choisies et sauvegardées** dans ce parcours, plutôt que refaire une recherche à chaque génération. Cela ne signifie pas que les 1 000 titres possèdent déjà des paroles disponibles.
- Conserver le choix manuel, la version originale, la traduction éventuelle, la provenance, les corrections et les horodatages existants ; l’analyse thématique ne les remplace pas. Le texte brut peut suffire, sans synchronisation obligatoire.
- Un même thème peut rapprocher des chansons françaises, anglaises, coréennes ou espagnoles, sans traduction française préalable obligatoire. Ce principe fonctionnel est acquis ; sa réalisation et sa pertinence restent à vérifier.
- **Local en priorité pour l’analyse de Fil Rouge ; solution en ligne à étudier si aucune solution locale adaptée ne convient**, conformément à la [fiche des modèles de Fil Rouge](11-fil-rouge-moteurs.md). L’utilisateur retient **EmbeddingGemma 300M et multilingual-e5-small**, proposés en téléchargement facultatif au premier lancement et toujours accessibles ensuite dans les paramètres. Refuser ne bloque pas le lecteur et ne déclenche aucun service distant ; les artefacts, l’intégration et la qualité restent à vérifier.
- Télécharger ces modèles ne recherche, ne choisit, ne traduit et n’envoie pas automatiquement les paroles. Leur préparation locale pour Fil Rouge est une opération distincte du téléchargement et de la sauvegarde du texte. Ce choix ne sélectionne pas un moteur de traduction ou de synchronisation.
- Recherche de paroles par API, traduction et analyse thématique distante sont des opérations distinctes. La possibilité de l’une n’active pas implicitement les autres ; les conditions de sauvegarde, d’analyse et d’un éventuel transfert doivent être compatibles avec les sources.

Les autres parcours de paroles déjà décrits, notamment le collage après recherche Web, ne sont pas supprimés par la confirmation de cette source API. Le [parcours fonctionnel de Fil Rouge](12-fil-rouge-telechargement-et-preparation.md) est désormais validé : demander **Préparer maintenant / Plus tard** avant la première préparation locale des textes, séparément du téléchargement des modèles ; affichage de la couverture réelle et recherche sur les titres déjà préparés pour les deux modèles avec indication explicite de couverture partielle. Les commandes de ce parcours sont acquises, pas leur performance ni un moteur de traduction ou de synchronisation.

## 3. Parcours de recherche proposé

Les détails d’ergonomie ci-dessous sont des recommandations à valider.

1. Préremplir la recherche avec titre et artiste ; utiliser album et durée comme indications supplémentaires lorsqu’ils sont disponibles.
2. Permettre de corriger la requête, notamment pour les reprises, remixes, versions live ou éditions différentes.
3. Consulter les fournisseurs configurés, sans supposer que toutes les API sont disponibles ou équivalentes.
4. Attendre la fin des recherches ou leurs délais limites avant de présenter le bilan, en distinguant absence de résultat, erreur et fournisseur indisponible.
5. Présenter les candidats avec source, titre, artiste, langue, type de synchronisation et aperçu ; afficher une durée lorsqu’elle est réellement fournie.
6. L’utilisateur consulte puis choisit un résultat.
7. Sauvegarder le texte retenu, ses informations de source et ses horodatages disponibles, puis actualiser l’aperçu et la page paroles.

### Badges recommandés

| Badge proposé | Signification |
| --- | --- |
| **Synchro · Ligne** | Le résultat fournit des horodatages par ligne. |
| **Synchro · Mot à mot** | Le résultat fournit des horodatages au niveau des mots. |
| **Texte** | Aucun minutage exploitable n’est fourni. |
| **Synchro estimée** | Minutage créé ou adapté automatiquement par NovaListen, à distinguer d’un minutage fourni. |

Un badge indique le type de données, pas une garantie de correspondance parfaite avec toutes les versions audio du même titre. Ne pas inventer de scores de fiabilité ou de minutages absents de la source.

### Recherche Web et collage

- Proposition : ouvrir le navigateur avec une recherche modifiable portant sur le titre et l’artiste.
- Au retour, permettre le collage et la prévisualisation avant validation.
- Un texte collé reste non synchronisé tant qu’aucun minutage réel ou estimé n’a été ajouté.
- Un import de fichier de paroles local, par exemple LRC, est une extension proposée au parcours de collage ; il n’est pas encore explicitement validé.
- Aucun contournement d’accès ni extraction automatique de pages Web n’est décidé.

## 4. Traduction et affichage — moteurs validés, détails proposés

### Préserver les segments synchronisés

- Traduire en conservant l’identité des segments de l’original, plutôt que perdre leur association avec les instants du morceau.
- Quand l’original possède une synchronisation par ligne, réutiliser les instants de ces segments pour les phrases françaises correspondantes.
- Une ligne française longue peut occuper plusieurs lignes visuelles sans inventer de nouveaux instants musicaux pour chaque retour à la ligne d’affichage.
- Basculer VO/FR à la position actuelle, sans redémarrer la musique ni remplacer la file.
- Appliquer le choix de langue à l’aperçu et à la page paroles, avec une seule version visible à la fois.
- Si l’original est non synchronisé, ne pas prétendre que sa traduction est automatiquement synchronisée ; elle reste textuelle jusqu’à un traitement de synchronisation.

### Coût, connexion et échecs

- **ML Kit est retenu par défaut pour la traduction locale ; Azure Translator standard F0 est retenu comme alternative uniquement sur demande explicite.** Ce choix ne constitue pas une activation ni une autorisation de transmettre maintenant les paroles.
- La création locale d’une VF nécessite les packs appropriés. Le détail de leur téléchargement et de la commande Azure reste à concevoir ; ni refus de packs ni erreur locale ne déclenche un envoi distant. Compatibilité, qualité et performances ne sont pas encore vérifiées ; la VF déjà sauvegardée reste distincte d’une nouvelle traduction.
- Une traduction sauvegardée n’est pas redemandée à chaque bascule.
- En cas d’échec, conserver et laisser consulter l’original ; ne pas le remplacer par un résultat vide ou partiel présenté comme complet.
- Proposition : si les paroles sont déjà en français, l’indiquer plutôt que facturer ou relancer une traduction inutile.

## 5. Karaoké et création de synchronisation — objectifs conditionnels

L’utilisateur souhaite du **mot à mot en VO et en français, si possible**, ainsi qu’un moyen de synchroniser les paroles non minutées. Ce sont des objectifs de conception à prototyper et tester, pas une promesse de précision universelle.

### 5.1 Mot à mot en langue originale

Deux sources possibles à étudier :

- récupérer un résultat qui fournit déjà les instants de chaque mot ;
- aligner le texte original avec la voix de l’enregistrement pour estimer ces instants.

Un fichier seulement synchronisé par ligne ne contient pas automatiquement le minutage de chaque mot. Répartir uniformément les mots dans la durée d’une ligne produit une animation estimée, pas un alignement exact.

### 5.2 Mot à mot français sur un audio d’une autre langue

Une traduction change le nombre, l’ordre et la durée apparente des mots. Les mots français ne sont pas ceux prononcés dans cet audio.

- Une correspondance **par ligne ou segment** est réalisable en conservant les instants de l’original synchronisé.
- Un surlignage mot à mot français peut être étudié comme adaptation sémantique et temporelle, mais il demeure potentiellement **estimé**.
- Ne pas qualifier ce résultat d’alignement phonétique exact en français lorsque l’enregistrement chante une autre langue.
- Recommandation : garantir d’abord la cohérence par ligne ; n’activer le mot à mot que lorsque les données le permettent ou en signalant clairement son caractère estimé.

Si une version audio réellement chantée en français est utilisée, son alignement doit être calculé ou fourni pour cette version précise, et non hérité sans contrôle d’une autre interprétation.

### 5.3 Créer le minutage d’un texte non synchronisé

Pistes proposées, à choisir et tester :

1. **Synchronisation assistée** : l’utilisateur marque les débuts de lignes pendant l’écoute et peut corriger les instants. Une édition mot à mot plus fine peut être étudiée séparément.
2. **Synchronisation automatique** : alignement du texte et de la voix, éventuellement avec séparation vocale ou reconnaissance adaptée au chant. Le résultat nécessite une vérification et un moyen de correction.
3. **Parcours semi-automatique** : produire une première estimation, puis permettre corrections et réglage global d’avance/retard.

Contraintes à prendre en compte : langue, chant rapide, chœurs, bruit, paroles différentes de l’enregistrement, longues parties instrumentales, versions live, temps de calcul et capacités du téléphone. La seule détection du rythme ou du tempo ne suffit pas à déterminer quand chaque mot est chanté.

Les estimations et corrections doivent être sauvegardées sans détruire la version source. En cas d’échec, garder le texte consultable au lieu de fabriquer une synchronisation présentée comme certaine.

L’exécution locale ou distante n’est pas encore choisie. Un éventuel envoi d’audio à un service distant doit être explicite et consenti ; il ne découle pas automatiquement de la recherche de paroles par titre et artiste.

## 6. Sauvegarde : cible confirmée et stratégie complémentaire proposée

### Cible existante à respecter

Les métadonnées modifiées visent le **fichier audio d’origine**, et pas seulement l’affichage dans NovaListen. Cette décision reste en vigueur.

Cependant, tous les formats et lecteurs ne prennent pas en charge de la même façon les paroles, plusieurs langues, les pochettes et le minutage mot à mot. L’accès en écriture peut aussi être refusé. La demande de persistance ne garantit donc pas que toute donnée pourra être intégrée universellement dans un seul champ du fichier audio.

### Stratégie proposée à valider

- Conserver localement dans NovaListen la sélection, la source, la langue, le texte, les instants disponibles, la traduction, les corrections et le type de minutage. Lier les données au bon enregistrement, pas seulement à un titre homonyme.
- Écrire dans le fichier audio les champs compatibles et autorisés, conformément à la cible choisie.
- Pour les données non représentables dans ce format, étudier un fichier de paroles associé et/ou une conservation complémentaire dans NovaListen. Les formats d’export et emplacements restent à décider.
- Distinguer clairement **sauvegardé dans NovaListen**, **écrit dans le fichier audio** et **enregistré dans un fichier associé**. Ne pas annoncer une écriture dans l’original si seul le cache local a été mis à jour.
- Ne pas écraser la VO par la VF si le format ne possède qu’un champ exploitable ; préserver les deux versions au moyen de la stratégie choisie.
- Sauvegarder aussi la pochette choisie et ses informations utiles ; les parcours de recherche de pochettes restent à définir ailleurs.
- Ne pas remplacer automatiquement les autres métadonnées existantes par celles d’un candidat API : proposer les corrections ou compléments, et garder le choix utilisateur.
- Après sauvegarde réussie, réutiliser les données hors ligne et actualiser les affichages concernés.

## 7. Emplacement des commandes — proposition

Dans les paramètres propres aux paroles :

- **Rechercher les paroles** ;
- **Rechercher sur le Web** et **Coller des paroles**, notamment si les API ne donnent rien ;
- **Traduire en français**, puis **Afficher la VO / Afficher la VF** ;
- **Synchroniser**, avec distinction des méthodes réellement disponibles et de leur qualité ;
- réglages de texte et d’avance/retard déjà proposés dans la fiche player.

Un raccourci **VO/FR** directement sur la page paroles est proposé pour éviter de rouvrir les réglages à chaque bascule. Son emplacement exact et les icônes ne sont pas encore validés. La suppression des boutons T-shirt et Cool ne signifie pas que ces fonctions doivent être placées à leurs anciens emplacements.

## 8. Choix et vérifications avant développement

- **Cadre de coût acquis — réponse 73 : Gratuit uniquement**, sans abonnement ni appels facturés, et sans bascule automatique vers du payant. Vérifier accès réel, couverture, langues, données ligne/mot, quotas et conditions d’utilisation ; la [fiche API et enrichissements](13-api-et-enrichissements.md) distingue désormais la cascade de paroles retenue pour la conception et les candidats des autres enrichissements.
- **Architecture de cascade validée :** par « Je valide tes cascade », l’utilisateur retient le noyau **lrc.red / LRCLIB / LiriQo**, **Better Lyrics** en complément ciblé, **Textyl / lyrics.ovh** en recours secondaires et **Lyrica** en réserve, puis **Web → collage** si nécessaire. Les connecteurs directs KuGou / QQ Music / NetEase / Migu / Kuwo restent conditionnels à un apport utile et à des conditions compatibles, pas cinq appels obligatoires après LiriQo. Le [comparatif dédié](14-cascade-api-paroles.md) consigne la portée de cet accord, sans activation ni essais. Ne pas compter deux services partageant une source comme deux confirmations indépendantes. Ordre précis, séquentiel/parallèle, poursuite ou arrêt de la recherche et critères de correspondance restent à définir ; le choix manuel, la provenance et l’affichage du niveau réel de synchronisation sont conservés.
- Vérifier les droits nécessaires d’affichage, de sauvegarde hors ligne, de traduction et de réutilisation pour l’analyse thématique de Fil Rouge ; vérifier séparément ceux d’un éventuel transfert à un service tiers. Ne pas promettre de stocker sans limite les données d’un fournisseur qui ne l’autorise pas ; choisir des sources compatibles avec le parcours souhaité.
- **Traduction validée par « Oui je valide » : ML Kit par défaut en local + Azure Translator standard F0 uniquement sur demande explicite.** Le [comparatif et le fonctionnement de traduction française](15-traduction-francaise-paroles.md) consignent cet accord et les réserves de confidentialité et de droits. Définir packs, téléchargement, commande Azure et authentification sans bascule distante ou payante automatique ; aucun compte, clé, essai, téléchargement ou envoi de texte n’est lancé par cette validation. Une éventuelle clé privée de fournisseur ne doit pas être distribuée comme secret dans l’APK.
- Tester séparément la synchronisation par ligne, le mot à mot original et le surlignage français estimé.
- Définir les formats, autorisations et confirmations de sauvegarde sans remettre silencieusement en cause la cible fichier d’origine.
- Définir les états d’attente, d’erreur, de résultats partiels, de données absentes et de reprise hors ligne.

Aucun fournisseur, modèle, abonnement, envoi de fichier ou résultat de synchronisation n’est présenté comme déjà configuré ou fonctionnel.
