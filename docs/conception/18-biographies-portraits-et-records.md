# NovaListen — Biographies, portraits et records : socle retenu et cascade photo validée

> **Statut : socle retenu et cascade photo validée, 17 novembre 2026, sans implémentation.** Après l’ouverture par « Allons y », « Oui mais pour la photos artiste je veux d'autres piste » retient **Wikipédia pour les biographies, français prioritaire puis anglais proposé, et Wikidata pour l’identité et les faits/records à vérifier dans leur contexte**. Les photos restaient alors réservées. Le [comparatif multisource photo](19-photos-artistes-autres-sources.md) inclut ensuite Last.fm et d’autres pistes après le retour sur les images incorrectes ou anciennes de fanart.tv ; la priorité recommandée à fanart.tv est retirée. **Le « Je valide » ultérieur retient désormais la cascade photo dédiée dans ses rôles conditionnels, son ordre et ses mécanismes**, sans portraits API Last.fm rétablis ni accès/droits universels. Aucun accord n’autorise activation, compte, clé, acceptation de conditions, essai ou recherche sur la collection.
> **Cadre acquis :** [API gratuites et usage personnel](13-api-et-enrichissements.md), [page Artistes, choix des photos et biographie hors ligne](05-artistes.md). La [cascade des métadonnées/pochettes validée](17-cascade-api-metadonnees-et-pochettes.md), la [cascade de paroles](14-cascade-api-paroles.md) et la [traduction des paroles](15-traduction-francaise-paroles.md) restent inchangées.
> **Orchestration photo retenue — « Je valide », 17 novembre 2026 :** la [cascade photo dédiée](20-cascade-api-photos-artistes.md) réunit les rôles de toutes les pistes, avec sources sautées si non admissibles, **deux requêtes simultanées au total pour ce parcours photo**, **cible initiale de six vignettes**, découverte initiale bornée par fournisseur, résultats progressifs et arrêts utiles. L’usage personnel reste acquis, sans modifier les accords antérieurs ou fixer la concurrence des biographies, records et paroles.
> **Périmètre :** enrichir les artistes représentés dans la collection locale. Il ne s’agit ni d’ajouter une discographie distante, ni de modifier les crédits des fichiers, ni de remplacer le Top 10 personnel par un classement mondial.

## 1. Socle retenu et articulation avec la cascade photo validée

**Wikipédia pour les biographies et Wikidata pour relier les identités et repérer des faits structurés référencés sont retenus au niveau de la conception.** Réutiliser le contexte d’artiste MusicBrainz lorsqu’il est déjà disponible et cohérent ; ne pas imposer une nouvelle recherche à chaque morceau. La proposition initiale de Commons en source principale de portraits n’était pas retenue par implication. La validation ultérieure retient Commons dans la deuxième vague conditionnelle de la cascade photo, pas comme source principale unique.

| Besoin | Source et état de décision | Apport | Limite essentielle |
| --- | --- | --- | --- |
| Identifier le bon artiste | Association locale choisie, MusicBrainz et Wikidata ; socle d’identité retenu | Relier un artiste à sa notice et aux versions linguistiques disponibles. | Nom identique ≠ même personne ou groupe ; liens et identifiants peuvent manquer ou être erronés. |
| Biographie lisible | Wikipédia retenue, via ses API publiques | Texte encyclopédique dans les langues disponibles ; extrait puis contenu plus développé si recherché et choisi. | Couverture et longueur variables ; respecter la licence et l’attribution. Un résumé n’est pas l’article complet. |
| Portraits à choisir et sauvegarder | **Rôles conditionnels et ordre retenus** dans la [cascade photo validée](20-cascade-api-photos-artistes.md), comparés dans la [fiche multisource](19-photos-artistes-autres-sources.md) | Plusieurs photos pertinentes lorsque disponibles, choix manuel et sauvegarde locale autorisée ; résultats progressifs et recherche bornée. | Vérifier sujet, dates, accès et droits effectifs ; la validation ne rétablit pas les portraits API Last.fm et n’active aucun service. |
| Faits de carrière et pistes de records | Wikidata retenu comme point d’entrée ; références vérifiables et sources adaptées au fait | Déclarations, qualificatifs temporels et références lorsqu’ils sont présents. | Wikidata n’est ni un registre exhaustif de records ni une certification de chaque valeur. |

Les API publiques de l’écosystème Wikimedia sont une piste sans abonnement pour ce besoin ciblé ; ne pas en déduire un accès illimité ou un engagement de service. Le compte du lecteur n’est pas proposé comme une condition du fonctionnement local. La lecture publique, l’identification du client et une éventuelle authentification pour certains besoins sont des sujets distincts ; aucun compte ni jeton n’est créé.

Sources officielles : https://www.mediawiki.org/wiki/Wikimedia_APIs ; https://www.mediawiki.org/wiki/Wikimedia_APIs/Access_policy ; https://www.wikidata.org/wiki/Wikidata:Data_access.

## 2. Identifier l’artiste une fois avant de choisir ses enrichissements

Contexte d’identité réutilisé dans la cascade photo validée ; routes exactes à définir :

```text
Artiste local + corrections et associations déjà choisies
                         ↓
Contexte MusicBrainz d’artiste disponible et cohérent
                         ↓
Lien Wikidata existant, ou recherche ciblée de candidats si nécessaire
                         ↓
Confronter personne/groupe, alias et éléments de carrière disponibles
                         ↓
Contexte commun aux branches biographie, portraits et faits de carrière
```

- Réutiliser un identifiant correctement typé et une association déjà retenue plutôt que rechercher de nouveau le même artiste pour tous ses morceaux. Un MBID d’enregistrement, d’édition ou de groupe d’éditions n’est pas un MBID d’artiste.
- MusicBrainz peut fournir des relations vers des ressources externes ; suivre un lien Wikidata pertinent lorsqu’il existe, puis les liens linguistiques de l’entité. Ne pas supposer la présence de tous ces liens pour chaque artiste.
- En cas d’absence ou d’ambiguïté, proposer les candidats et leurs différences. Ne pas prendre automatiquement le premier résultat d’une recherche sur un nom courant.
- Distinguer groupe, membre du groupe, projet solo et collaboration ; ne pas reprendre la biographie ou le portrait d’un membre pour représenter automatiquement tout le groupe.
- Une association distante ne fusionne ni artistes locaux, ni fichiers, ni historiques ; elle ne réécrit pas les crédits musicaux. Les règles détaillées de correspondance restent à préciser.

La documentation d’accès Wikidata recommande de choisir une méthode adaptée : récupération d’une entité connue plutôt qu’une requête SPARQL générale pour chaque affichage. Les méthodes de données complètes, Action API et REST existent ; le choix précis des routes reste à définir selon leur stabilité au moment de l’intégration. Aucune requête d’entité artistique n’est exécutée ici.

## 3. Biographies — Wikipédia en premier

### 3.1 Texte retenu et détails de parcours à préciser

- Rechercher le texte de l’article correspondant à l’artiste identifié, dans une langue choisie par l’utilisateur. Afficher les candidats et leur provenance si plusieurs pages ou versions sont pertinentes.
- Conserver l’aperçu acquis de **trois lignes sous la photo** et **Voir plus**. Les trois lignes sont une limite d’affichage, pas une raison de tronquer la sauvegarde à trois lignes.
- Sauvegarder le texte effectivement choisi et ses crédits pour la lecture hors ligne. Voir plus montre le contenu sauvegardé disponible ; ne pas annoncer un article complet si seul un résumé a été récupéré.
- Pour une recherche de biographie plus développée, récupérer un contenu d’article adapté par une API documentée et vérifier le texte retenu. Ne pas confondre un petit résumé, une description Wikidata d’une phrase et une biographie complète ; ne pas promettre une longueur absente de la source.
- Garder la biographie actuelle pendant une nouvelle recherche. Le remplacement reste un choix de l’utilisateur ; ni actualisation, ni réindexation, ni réponse tardive ne l’écrase automatiquement.
- Si aucune biographie adaptée n’est trouvée, garder le texte existant ou signaler l’absence de contenu. Ne pas générer une carrière fictive à partir de quelques tags.

### 3.2 API documentaire : résumé ≠ texte complet

Le Page Content Service documente un endpoint de résumé `page/summary` comme stable : il comprend notamment une description et le premier paragraphe sous forme texte/HTML, ainsi que la langue et des métadonnées de page. C’est une piste de présentation courte, pas la preuve qu’une biographie longue sera fournie. Les autres formats de contenu nécessitent un choix d’intégration et de nettoyage adapté.

L’extension TextExtracts documente des extraits en texte brut ou HTML limité, mais se déclare non activement maintenue et renvoie vers le Page Content Service pour les résumés Wikimedia. Ne pas choisir aveuglément un ancien exemple `prop=extracts` comme fondation durable, ni conclure que tous les endpoints sont fermés. Ne pas rendre NovaListen dépendant d’une route expérimentale sans examen ; aucun endpoint n’est définitivement choisi ici.

Sources : https://www.mediawiki.org/wiki/Page_Content_Service ; https://www.mediawiki.org/wiki/Extension:TextExtracts.

### 3.3 Langue — français prioritaire et anglais proposé, retenus

**L’accord partiel retient le français en priorité, puis une version anglaise proposée si le français manque ou si l’utilisateur demande un autre texte.** L’anglais doit être identifié comme tel ; ne pas présenter une version anglaise comme une biographie française. Un texte dans une autre langue reste une option à discuter, sans imposer une langue selon la nationalité supposée de l’artiste.

La langue n’est donc plus à redemander comme choix initial. Pas de traduction automatique décidée pour les biographies : **ML Kit par défaut et Azure F0 sur demande ont été validés pour les paroles**, pas étendus implicitement aux biographies. Toute extension demanderait de définir le choix utilisateur, les droits du texte, les crédits et les éventuelles données transmises ; aucun transfert ou nouveau moteur n’est ajouté.

### 3.4 Droits : permettre le hors ligne avec les obligations du texte

Le guide Wikimedia indique que la plupart des Wikipédia utilisent **CC BY-SA 4.0**, avec vérification sur le projet et le contenu concernés. Cette licence permet la réutilisation sous ses conditions : le hors ligne est donc un objectif cohérent, pas une copie sans obligations.

Pour le texte choisi, prévoir titre, langue, page source, identification de la version/révision lorsque disponible, date de récupération, crédit aux contributeurs selon le mécanisme applicable, licence et lien ou copie de ses termes. Indiquer les modifications ou traductions et respecter les obligations ShareAlike applicables au contenu adapté. L’emplacement des crédits reste à concevoir ; ne pas supprimer leur disponibilité en mode hors ligne.

Les images présentes dans un article n’héritent pas de la licence de son texte. Ne pas télécharger tous les médias d’une page en les traitant comme un seul texte libre. Guide officiel : https://www.mediawiki.org/wiki/Wikimedia_APIs/Content_reuse.

## 4. Portraits — cascade validée, choix manuel maintenu

**L’utilisateur demande d’autres pistes photo, puis l’ajout de Last.fm**, en signalant des photos incorrectes ou anciennes chez fanart.tv. Le [comparatif multisource](19-photos-artistes-autres-sources.md) retire ma recommandation de fanart.tv en premier et détaille Last.fm demandé, Genius, Discogs, TheAudioDB, Openverse/Commons et les sources officielles/import autorisé. L’ajout demandé de Last.fm est enregistré, mais le retrait des portraits documenté par son support et l’exclusion d’images des conditions API empêchent de le promettre comme collecteur photo. L’orchestration fait désormais l’objet d’une [cascade photo validée](20-cascade-api-photos-artistes.md) par **« Je valide »** ; droits/accès effectifs, seuils détaillés, filtres/tri/rejets et réalisation restent à préciser. Commons y est retenu en deuxième vague conditionnelle, pas imposé en source unique. Les règles ci-dessous détaillent cette sous-branche ; elles ne remplacent pas l’ordre de la fiche de cascade.

Zoom sur la sous-branche Commons conditionnelle, au sein de la cascade validée :

```text
Photo déjà choisie → conserver
        ↓ si changement demandé ou recherche volontaire
Pistes de l’artiste identifié : image Wikidata / médias Commons associés
        ↓
Recherche ciblée d’autres photos Commons si utile
        ↓
Contrôle du sujet, de la licence et des crédits, puis aperçus admissibles
        ↓
Plusieurs propositions si disponibles → choix manuel → sauvegarde locale
        ↓ si aucune image adaptée
Garder l’existant / état sans portrait ; poursuivre les branches admissibles si utile
```

- La propriété Wikidata **image (P18)** renvoie à un fichier Commons ; elle peut fournir un point de départ mais ne garantit ni plusieurs photos ni un portrait adapté au cadrage rond. Le simple fait de recevoir un nom de fichier sous CC0 ne donne pas la licence de l’image elle-même.
- Rechercher des alternatives liées au même sujet pour respecter le choix parmi plusieurs propositions lorsque disponibles. Une catégorie peut contenir des groupes, membres, pochettes ou images de scène ; tous ses fichiers ne sont pas des portraits équivalents. Ne pas promettre plusieurs photos à tous les artistes.
- Vérifier le fichier Commons et ses métadonnées, notamment auteur/crédit, source et licence. Distinguer auteur et compte ayant téléversé le fichier. Si l’information nécessaire est absente ou contradictoire, ne pas présenter la sauvegarde comme déjà autorisée.
- L’Action API `imageinfo` expose URL de fichier et page descriptive, dimensions et métadonnées étendues. La documentation précise que `extmetadata` est coûteux : ne le demander que pour les quelques candidats utiles, pas pour une catégorie entière sans limite. Ces champs peuvent contenir du HTML à traiter comme non fiable, pas du code à exécuter.
- Les fichiers Commons sont généralement réutilisables avec leur licence propre, souvent CC BY, CC BY-SA ou domaine public. Il n’est pas nécessaire de demander une autorisation individuelle supplémentaire si l’usage respecte une licence ouverte applicable ; contrôler néanmoins les conditions et éventuelles restrictions non liées au copyright, notamment les droits de la personnalité.
- Conserver la photo choisie et les informations de crédit nécessaires, pas seulement son URL. La miniature/l’image de taille adaptée, les dimensions cibles et le stockage restent à définir ; ne pas télécharger tous les originaux.
- Le portrait rond reste acquis. Préciser ultérieurement cadrage/recadrage, signalement de modification et conservation de l’image source selon licence ; ne pas effacer les informations de provenance.
- Une image présente sur une Wikipédia peut être hébergée localement sous des conditions différentes, notamment fair use. Ne pas la traiter automatiquement comme un fichier Commons réutilisable ; la branche conditionnelle retenue privilégie des fichiers Commons dont les conditions conviennent.
- Ni nouveau portrait, ni remplacement de biographie ne modifient les pochettes des fichiers audio ou l’édition représentative d’un groupe d’albums. L’import local autorisé est retenu en principe comme recours volontaire de la cascade ; commande, présentation et modalités restent à concevoir, sans import depuis une source qui refuse le stockage.

Sources : https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia ; https://commons.wikimedia.org/wiki/Commons:FAQ ; https://www.mediawiki.org/wiki/API:Imageinfo ; https://www.wikidata.org/wiki/Property:P18.

## 5. Records — Wikidata comme point d’entrée, pas certificat universel

### 5.1 Séparer les types d’informations

| Type | Ce qui peut être proposé | Ce qu’il ne faut pas en déduire |
| --- | --- | --- |
| Distinction ou récompense | Prix reçu, catégorie et date lorsque présents et sourcés. | Une récompense n’est pas automatiquement un record ; nomination ≠ victoire. |
| Fait de carrière | Première sortie, participation, événement ou autre fait vérifiable dans son contexte. | Une date ou une liste partielle ne prouve pas une primauté mondiale. |
| Record au sens strict | Affirmation explicitement étayée, avec mesure, territoire/catégorie, période et source adaptés. | Un superlatif actuel ne se déduit pas d’un texte vague ou d’un simple compteur de popularité. |
| Statistique personnelle | Top 10 et nombre d’écoutes qualifiées locales déjà définis. | Ces données ne viennent pas de Wikidata, Last.fm ou d’un classement public. |

Cette séparation est une recommandation de qualité ; elle ne valide pas une nouvelle rubrique de récompenses à la place des records demandés ni une modification de la structure de la page Artistes.

### 5.2 Ce que Wikidata peut aider à faire

- Ses **données structurées sont sous CC0**, réutilisables notamment hors ligne ; cela ne couvre ni les images liées ni le texte des publications référencées. Mentionner Wikidata reste utile pour la traçabilité, même lorsque l’attribution n’est pas une obligation de CC0.
- Les déclarations peuvent porter des qualificatifs temporels et des références. **P166 signifie distinction reçue**, pas Record mondial. Ne pas compter naïvement les déclarations comme un nombre certifié de trophées : catégories, éditions, doublons et manques peuvent changer le résultat.
- Préférer une récupération des déclarations conservant qualificatifs et références. Le mode RDF simplifié/truthy documenté omet ces éléments ; une valeur isolée ne suffit pas à notre vérification.
- Évaluer la référence, le contexte et les éventuelles contradictions. La présence d’un lien ne prouve pas que la page soutient exactement l’affirmation ; absence de référence, d’information ou de résultat ne signifie pas absence de record ou de prix.
- Pour un record, proposer la consultation de la source officielle compétente, ou d’une publication fiable qui cite explicitement cette source, avec les conditions d’accès et de réutilisation adaptées. Ne pas inventer un connecteur officiel gratuit universel à partir d’un lien Wikidata.

Sources : https://www.wikidata.org/wiki/Wikidata:Data_access ; https://www.wikidata.org/wiki/Help:Sources ; https://www.wikidata.org/wiki/Property:P166.

### 5.3 Limite franche et parcours proposé

**Cette étude n’établit pas une API gratuite unique, exhaustive et autorisée qui fournirait tous les records musicaux de tous les artistes.** Le socle Wikipédia/Wikidata retenu ne donne donc pas une garantie de records partout ; une source de photos n’y change rien.

Parcours recommandé : données locales déjà retenues → faits/références Wikidata pertinents → vérification des affirmations dans des sources adaptées, notamment officielles → propositions sourcées → choix et sauvegarde des informations autorisées. Les textes Wikipédia peuvent orienter vers une référence, pas servir de prétexte à extraire automatiquement toute une carrière sans contrôle.

- Enregistrer pour chaque information retenue : artiste identifié, type de fait, formulation fidèle, valeur/unité si pertinente, territoire/classement/catégorie, date ou période du fait, source, version/date de récupération et éventuelles réserves. Le schéma technique n’est pas encore choisi.
- Distinguer **date du fait**, **date de la mesure** et **date de consultation**. Une consultation aujourd’hui ne rend pas actuel un total ancien ; ne pas afficher une ancienne valeur comme un record encore détenu si cela n’est pas établi.
- Un fait bref retenu et sa provenance ne sont pas l’autorisation de copier intégralement une publication, ses photos, logos ou bases de données. Respecter les conditions de chaque source externe et de l’accès utilisé.
- Si la vérification ou la conservation compatible n’est pas possible, signaler l’absence de record vérifié ou garder l’information déjà sauvegardée avec sa date, sans fabriquer une réalisation et sans effacer silencieusement les choix de l’utilisateur.
- L’automatisation de vérification, le choix des sources selon les catégories et une éventuelle saisie manuelle de faits restent à définir. Ni scraping de sites, ni contournement d’accès, ni achat, ni abonnement ne sont retenus.

## 6. Place des autres API déjà discutées

| Source | Rôles selon le besoin et limites de l’accord |
| --- | --- |
| MusicBrainz | Réutiliser l’identité et les liens externes pertinents ; pas une biographie complète ou un registre universel de records. Son rôle déjà validé pour les métadonnées reste acquis. |
| Last.fm | Éventuel complément textuel distinct de Wikipédia, non retenu pour une biographie longue : `artist.getInfo` annonce 300 caractères. **Ajout photo explicitement demandé ensuite**, détaillé dans le comparatif dédié : portraits retirés de l’API selon le support et images exclues de l’accord, donc pas d’accès photo autorisé promis. L’identité/réutilisation conditionnelle et la consultation distincte sont désormais retenues en principe dans la cascade photo, pas un complément de biographie choisi. Le rôle acquis pour les tags et le plafond/cache de métadonnées ne donnent pas les droits des images. |
| fanart.tv | Priorité recommandée retirée après le retour utilisateur ; ultime complément photo désormais retenu dans la cascade, seulement si identité, accès gratuit et droits d’image sont compatibles. L’API documente des images d’artiste, mais un champ copyright absent ou Unknown n’est pas une licence. Cet accord photo est distinct du rôle déjà validé pour les pochettes. |
| TheAudioDB | Vague photo 1 conditionnelle retenue, sans dépendance gratuite garantie : usage gratuit d’application et droits restent à confirmer. Les compléments textuels de biographies ne sont pas choisis par cet accord photo. Ni v2 Premium ni abonnement n’est retenu. |
| Discogs | Vague photo 3 ciblée retenue sous conditions, distincte des informations de catalogue déjà acquises et sans choix par défaut d’une biographie hors ligne. Les réserves de conservation et les images restreintes restent applicables. |
| Deezer / iTunes Search | Garder leurs rôles de catalogue acquis sous conditions ; pas de nouvelle banque automatique de portraits conservables ou de records. |
| Cover Art Archive | Pochettes d’éditions, pas substitut automatique à une photo d’artiste. |

Les détails d’accès et les sources de ces réserves restent dans le [comparatif des métadonnées et pochettes](16-metadonnees-et-pochettes.md). Documentation d’images d’artiste fanart.tv consultée : https://api.fanart.tv/. L’accord initial retenait Wikipédia et Wikidata sans choisir les autres sources photo ; le « Je valide » ultérieur retient séparément les rôles photo conditionnels de la [cascade dédiée](20-cascade-api-photos-artistes.md). Le [comparatif des alternatives](19-photos-artistes-autres-sources.md) reste la référence des constats et réserves, pas une activation ni un choix de compléments textuels de biographies.

## 7. Accès, efficacité et fonctionnement local

- **Local d’abord :** afficher les contenus déjà sauvegardés ; une ouverture de page, une lecture ou une réindexation ne devient pas une recherche systématique. Le déclenchement précis des recherches initiales reste à définir ; les commandes existantes de changement de photo et de recherche d’une autre biographie restent acquises.
- **Mutualiser par artiste et identité choisie :** une récupération de l’entité Wikidata peut alimenter liens linguistiques, pistes d’image et faits pertinents. Dédupliquer les requêtes ; ne pas réinterroger l’artiste pour chacun de ses morceaux. Les droits propres à chaque contenu restent attachés à celui-ci.
- **Branches indépendantes après identification :** une biographie disponible ne doit pas attendre une recherche de record ; une absence de photo n’empêche ni lecture ni consultation du texte local. L’ordre et le plafond de concurrence du parcours photo sont désormais acquis dans sa fiche dédiée ; seuils détaillés, budgets d’attente, reprises, autres branches et articulation d’ensemble restent à concevoir.
- **Ne pas étendre un accord par défaut :** le maximum de deux requêtes simultanées de la cascade de métadonnées/pochettes ne fixe pas à lui seul celui des autres parcours. **Le plafond photo de deux requêtes simultanées au total a désormais sa validation propre** ; il n’est pas étendu aux biographies, records ou paroles. Un ordonnanceur partagé reste à concevoir.
- **Wikimedia :** User-Agent explicite avec contact, respect des politiques et limites globales par utilisateur à travers les sites et familles d’API. La documentation 2026 annonce notamment 10 requêtes/minute pour un trafic non identifié et 200 pour un bot non authentifié correctement identifié, sous réserves et évolution ; elle recommande au plus trois requêtes concurrentes. Ce sont des informations du fournisseur, pas des performances mesurées ni des paramètres validés de NovaListen ; ne pas multiplier le quota en changeant de wiki ou de domaine.
- **Erreurs et quotas :** distinguer absence de contenu, homonyme, droits insuffisants, erreur, coupure et quota. Respecter `Retry-After` sur 429/503 lorsqu’il est présent et prévoir une reprise espacée et bornée, sans blocage du lecteur, rotation de clés, relais de contournement ou bascule payante.
- **Cache et sauvegarde :** séparer réponses de travail et contenu choisi persistant autorisé, avec source/langue/identité/version. Respecter les en-têtes pour les requêtes et la revalidation ; conserver les crédits hors ligne. La durée exacte de cache et les règles d’actualisation restent à définir, pas une obligation de rester connecté pour lire le texte choisi.
- **HTML et liens externes :** nettoyer les extraits et métadonnées, ne pas exécuter de scripts ; ne pas laisser un article ou une miniature charger à l’insu de l’utilisateur des ressources distantes après sauvegarde. L’ouverture d’une source externe est distincte de la consultation hors ligne des éléments sauvegardés.
- **Données minimales :** seulement identifiants et termes d’artiste nécessaires à une recherche volontaire autorisée ; aucun audio, empreinte, historique, scrobbling, collection entière ou compte de plateforme envoyé implicitement. Aucun serveur, proxy ou hébergement n’est choisi.

Documentation de limites consultée dans ses deux parties : https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits. Aucun quota ou accès durable n’est garanti par cette fiche.

## 8. Accords enregistrés et sujets encore ouverts

**Retenus par « Oui mais pour la photos artiste je veux d'autres piste », 17 novembre 2026 :**

1. **Wikipédia comme source principale de biographies**, avec texte choisi, provenance et conservation hors ligne conforme à la licence.
2. **Wikidata comme lien d’identité et point d’entrée vers des faits référencés**, sans certification automatique de records ; vérification contextuelle avant de présenter un record.
3. **Français prioritaire et anglais proposé en recours pour les biographies**, sans traduction automatique ajoutée par défaut.
4. Réutilisation des associations d’artiste, recherches ciblées et préservation des contenus choisis ; pas de remplacement silencieux ni de dépendance réseau du lecteur. Les mécanismes détaillés restent à concevoir.

**Historique photo — demande élargie avant validation de la cascade :** après la réserve sur Commons, l’utilisateur signale les images incorrectes ou anciennes de fanart.tv et demande **Last.fm et d’autres sources**. Cette inclusion de Last.fm est acquise comme demande ; son usage photo effectif reste non admissible dans l’accord API consulté, pas une intégration opérationnelle. Le [comparatif multisource](19-photos-artistes-autres-sources.md) retire ma priorité recommandée à fanart.tv et détaille Genius, Discogs, TheAudioDB, Openverse/Commons et les sources officielles/import autorisé sous conditions. Cette demande seule n’acceptait ni leur ordre, ni leur admission effective, ni les filtres/tri/rejets ou la commande d’import. Commons restait candidat ; pas de bannissement total de fanart.tv inféré ni de modification de son rôle acquis pour les pochettes. Plusieurs propositions lorsque disponibles, choix manuel, sauvegarde locale autorisée et remplacement volontaire restent acquis.

**Cascade photo validée ensuite — « Je valide », 17 novembre 2026 :** la [fiche dédiée aux photos](20-cascade-api-photos-artistes.md) retient une place conditionnelle pour toutes les pistes et conserve Last.fm sans promettre ses portraits. Ordre acquis : identité/réutilisation → Genius + TheAudioDB admissibles → Commons + Openverse → Discogs admissible → fanart.tv en ultime complément admissible ; sources officielles/kits presse/import autorisé comme recours volontaires retenus en principe. Sont acquis **deux requêtes simultanées au total pour le parcours photo**, une **cible initiale de six vignettes distinctes**, une découverte initiale bornée à une page ou fiche par fournisseur admissible avec consultation ciblée si nécessaire, résultats progressifs, poursuite volontaire bornée de type Plus de photos, arrêts des travaux inutiles, déduplication/réutilisation/cache compatibles et principes de contrôle d’identité/type/provenance/dates. L’usage personnel réaffirmé ne devient pas un accès ou une permission de sauvegarde universels ; aucune activation.

**Restent ouverts :** admission effective des fournisseurs, routes et formats exacts d’API, critères/seuils détaillés de correspondance, contenu/longueur des biographies développées, catégories de records, sources et méthode de vérification selon catégorie, interface des crédits, cadrage et dimensions des photos, filtres/tri/rejets proposés, commandes et modalités des recours/import et de poursuite, portée initiale et éventuels lots d’artistes, concurrence des branches biographies/records et articulation entre cascades, durées de cache, délais, reprises, intégration et essais. Les rôles, l’ordre et les mécanismes du parcours photo, dont son plafond de deux requêtes simultanées, la cible de six vignettes et la borne de découverte initiale, ne sont plus à redemander. Les compléments de biographies Last.fm/TheAudioDB et une traduction des biographies restent des extensions distinctes à discuter. La validation des traducteurs de paroles ne couvre pas automatiquement une traduction de biographies.

La poursuite « Allons y » ouvrait l’étude sans validation ; **c’est la réponse conditionnelle ultérieure qui accepte le socle hors photos**. Les trois validations antérieures — paroles, traduction des paroles, métadonnées/pochettes — restent acquises et distinctes ; les questions numérotées restées sans réponse le restent. La cascade photo a désormais son accord distinct ; aucun budget de biographies/records ou de paroles n’est décidé par extension des plafonds validés des photos ou des métadonnées/pochettes. Les thèmes visuels viennent après la discussion des API selon l’ordre demandé, pas par clôture implicite de tous les détails.

**Travail réalisé : documentation, rédaction et enregistrement de la validation uniquement.** Aucun appel d’API sur un artiste de la collection, recherche de biographie ou de portrait pour cette collection, récupération de fait de carrière pour l’application, téléchargement d’image, création de compte ou clé, acceptation de conditions, dépense, installation, traduction, transfert d’audio/empreinte/historique, modification musicale ou ajout de code d’application. Les exemples des documentations n’ont pas été exécutés et aucune performance ou couverture n’a été mesurée.
