# NovaListen — Cascade d’API de paroles : architecture validée

> **Statut :** architecture révisée de cascade de paroles **validée par l’utilisateur le 17 novembre 2026**, par « Je valide tes cascade ». Les sources et leurs rôles ci-dessous sont retenus pour la conception ; l’ordre précis des appels, les conditions de poursuite et d’arrêt et les critères de correspondance restent à définir. Cette validation ne lance ni intégration ni essais et ne prouve pas la disponibilité ou les droits de chaque source.
> **Cadre acquis :** gratuit uniquement, usage personnel, compte développeur gratuit acceptable en principe — réponses 73 à 75 dans la [fiche API et enrichissements](13-api-et-enrichissements.md).
> **Historique des choix :** les questions 76 et 77 ont été présentées sans réponse. La validation libre ultérieure porte sur l’architecture révisée ci-dessous ; elle ne crée pas de réponses rétroactives à ces questions et ne choisit pas LRCLIB comme source principale unique.

## 1. Architecture retenue — validation de l’utilisateur

La première liste a été élargie à **LiriQo, lrc.red, Better Lyrics API, Lyrica API, KuGou, QQ Music, NetEase Cloud Music, Migu Music et Kuwo Music**. L’utilisateur valide ensuite la proposition révisée, avec les rôles et réserves présentés :

| Rôle retenu | Services | Portée de la validation |
| --- | --- | --- |
| **Noyau de la cascade** | **lrc.red + LRCLIB + LiriQo** | Sources retenues au niveau de la conception : deux accès directs à des catalogues et un agrégateur proposant plusieurs formats et candidats. Aucun ordre total d’appels, délai ou gain mesuré n’est acquis. |
| **Complément ciblé** | **Better Lyrics API** | Selon le cache et les routes accessibles gratuitement et légitimement ; pas d’accès universel hors cache promis, ni d’usage de la clé privée de l’extension. Ne pas compter lrc.red comme un catalogue indépendant une seconde fois. |
| **Connecteurs directs éventuels** | **KuGou / QQ Music / NetEase / Migu / Kuwo** | Sources déjà annoncées via LiriQo. Un accès direct reste conditionné à une autonomie ou à des résultats supplémentaires utiles et à des conditions compatibles ; ce ne sont pas cinq étapes obligatoires ni cinq intégrations immédiatement autorisées. |
| **Réserve** | **Lyrica** | Rôle de réserve retenu compte tenu de la fragilité annoncée des instances publiques gratuites ; aucun hébergement ou déploiement n’est choisi. |
| **Recours secondaires** | **Textyl / lyrics.ovh** | Compléments secondaires retenus sous les réserves documentaires d’accès et de droits, pas les principaux compléments du noyau. |
| **Dernier recours** | **Recherche Web → collage** | Parcours acquis si aucun résultat adéquat ; pas d’extraction automatique de sites. |

**Vagalume reste une piste documentaire conditionnelle, hors des services retenus par cette validation.** Son accès API gratuit ne transfère pas les droits d’utilisation des paroles.

Les rôles sont validés, **pas un classement mesuré de vitesse, de disponibilité ou de couverture**. La validation ne prouve ni gratuité permanente, ni droits universels sur les textes, ni compatibilité de tous les services avec Fil Rouge. Elle conserve le choix manuel des résultats, le niveau réel de synchronisation, la provenance, la maîtrise des doublons et la réutilisation hors ligne des contenus choisis lorsque les droits le permettent.

## 2. Les neuf pistes ajoutées par l’utilisateur

### 2.1 Services et agrégateurs

| Nom | Nature | Ce que la documentation indique | Conséquence pour NovaListen |
| --- | --- | --- | --- |
| **LiriQo** | Agrégateur et normalisateur | Requêtes publiques standard sans clé ; 11 adaptateurs annoncés ; texte, ligne, mot, caractère ou syllabe selon source et morceau ; candidats et provenance dans la réponse. | Agrégateur retenu dans le noyau pour le karaoké et le choix multisource. Ne pas confondre 11 adaptateurs avec 11 résultats garantis, ni le `primary` choisi par le service avec le choix de l’utilisateur. |
| **lrc.red** | Catalogue avec API publique | API gratuite avec identification de l’application et attribution visible ; recherche de titres/artistes, accès par ISRC et fichiers TTML, LRC ou Lyricsfile selon disponibilité. | Accès direct retenu dans le noyau pour les paroles finement synchronisées. Conserver l’attribution et vérifier la correspondance avec la bonne version du fichier local. |
| **Better Lyrics API** | Résolveur, cache et accès à plusieurs routes | Gratuité annoncée ; réponses déjà en cache sans clé. Certaines recherches TTML hors cache nécessitent une clé, non distribuée actuellement aux nouveaux projets ; routes KuGou et QQ documentées sans clé. Le backend utilise notamment lrc.red. | Complément ciblé retenu, pas recherche universelle gratuite garantie. Ne pas demander ni récupérer la clé réservée à l’extension Better Lyrics ; ne pas compter son cache et lrc.red comme deux catalogues indépendants. |
| **Lyrica API** | Moteur d’agrégation open source et instances publiques | Texte, LRC et mot à mot selon les sources ; repli vers la ligne si le mot à mot manque. Le portail avertit que les instances publiques gratuites peuvent cesser de fonctionner et conseille l’auto-hébergement. Traduction et romanisation via Groq séparées de la récupération. | Réserve retenue ; ni garantie de service hébergé, ni nouveau moteur de traduction choisi, ni autorisation de transférer les paroles à une IA distante. |

**Sources et précisions :**

- **LiriQo :** [1](https://github.com/AlFarrizi-Studio/LiriQo). Le dépôt documente recherche par titre/artiste, durée, album ou ISRC, donc pas uniquement par identifiant vidéo. La documentation actuelle annonce Kuwo LRCX et Migu MRC en plus de KuGou, QQ et NetEase. Le mode Musixmatch richsync en direct est indiqué **désactivé dans le déploiement de production actuel**, bien que l’adaptateur soit décrit. Disponibilité et limites publiques peuvent évoluer ; respecter `Retry-After` et éviter les requêtes dupliquées. La migration du tableau de suivi annoncée le 12 novembre concerne le site de monitoring, **pas un arrêt de l’API**. La licence annoncée pour le parseur client ne remplace pas les conditions des sources ou une licence des paroles.
- **lrc.red :** API officielle [1](https://lrc.red/docs), redirigée lors de la consultation vers https://lrc.red/api. Conditions publiées : User-Agent identifiant l’application avec lien et contact, ou X-Client-ID pour un client Web ; crédit et lien clairement affichés là où les données sont montrées. Le service applique des limites. La recherche titre/artiste et la recherche dans le texte sont distinctes : `in=lyrics` ne doit pas être utilisé comme s’il recherchait les métadonnées. Le nombre de pistes annoncé sur le site n’est pas une mesure de couverture de la collection de l’utilisateur.
- **Better Lyrics :** [1](https://github.com/better-lyrics/api), introduction https://docs.betterlyrics.org/, authentification https://docs.betterlyrics.org/docs/authentication et limites https://docs.betterlyrics.org/docs/rate-limiting. Pour `/getLyrics` et `/ttml/getLyrics`, distinguer cache accessible sans clé et nouvelle récupération protégée ; un 401 n’est pas Aucun résultat. La documentation indique que les clés ne sont pas émises actuellement ; le README réserve celle de l’extension à son usage propre. Les routes KuGou et QQ ont des conditions d’accès différentes selon la documentation. Ne pas généraliser la protection du TTML à tous les endpoints ni traiter l’acceptation d’un compte développeur gratuit comme une clé effectivement disponible. Le README décrit un cache, puis lrc.red, puis un fournisseur amont ; les résultats récupérés ailleurs peuvent être reversés à lrc.red selon la configuration. Aucune contribution de NovaListen à un catalogue tiers n’est décidée ici.
- **Lyrica :** [1](https://github.com/Wilooper/Lyrica) et portail [1](https://wilooper.github.io/lyrica-api/developers.html). Le portail avertit de limites d’hébergement et d’une possible indisponibilité des instances gratuites. Les listes de fournisseurs diffèrent entre certaines pages/version du portail et le README : ne pas transformer un nombre d’adaptateurs en promesse de service. Les performances de cache annoncées par le projet ne sont pas des mesures réseau ni des résultats sur le Camon. Auto-hébergement, intégration Python et services IA ne sont pas choisis pour NovaListen.

### 2.2 Les cinq plateformes musicales

Les formats ci-dessous sont **ceux annoncés par les adaptateurs de LiriQo**, pas une garantie officielle de toutes les API ou de tous les morceaux de ces plateformes : [1](https://github.com/AlFarrizi-Studio/LiriQo).

| Plateforme | Formats ou données annoncés dans cette intégration | État de l’étude |
| --- | --- | --- |
| **KuGou** | KRC, synchronisation mot et ligne selon disponibilité. | Source techniquement pertinente à examiner ; accès direct de NovaListen, conditions, identification et stabilité non validés. |
| **QQ Music** | QRC, mot et ligne ; traduction ou romanisation possibles selon les données. | Même réserve : l’existence d’un connecteur ne constitue pas un contrat d’API publique gratuite. |
| **NetEase Cloud Music** | YRC pour les mots lorsqu’il existe ; LRC et texte comme autres possibilités. | Comparer la voie agrégée et un éventuel connecteur direct, sans supposer que tous les titres ont du YRC. |
| **Migu Music** — « Migu Mus » dans la question | MRC, minutage caractère/mot et ligne annoncé ; texte selon disponibilité. | Capacité déclarée par l’intégration, à confirmer indépendamment avant choix. |
| **Kuwo Music** | LRCX, minutage caractère/mot et ligne annoncé, avec alternatives LRC/texte. | Capacité déclarée par l’intégration, à confirmer indépendamment avant choix. |

L’existence de bibliothèques communautaires de récupération ne valide pas automatiquement la gratuité officielle, la permanence d’accès, les droits de sauvegarde ou l’absence d’authentification de chaque route. **Aucune offre officielle gratuite autorisant l’ensemble de nos usages n’a été validée pour ces cinq plateformes dans cette étude.** Cela ne signifie pas que toute utilisation est impossible ou payante ; cela signifie que ces conditions ne sont pas établies par la seule liste des connecteurs.

Ne pas ajouter de cookies récupérés, identifiants empruntés, jetons privés ou contournement de blocage pour faire fonctionner une source. Les questions d’accès, de région et de quotas doivent être traitées explicitement. Aucun compte de plateforme musicale n’est connecté ici.

Une granularité par caractère n’est pas nécessairement une segmentation par mot ; une phrase minutée n’est pas un mot. Les instants disponibles doivent être vérifiés avant d’annoncer un karaoké mot à mot. **Format riche ne signifie pas forcément minutage natif :** une contribution technique du projet Better Lyrics décrit des fichiers lrc.red marqués `byModel` lorsqu’ils ont été synchronisés par un modèle, ainsi qu’un mécanisme proposé pour les écarter : [3](https://github.com/better-lyrics/api/pull/52). Cette observation ne permet pas d’affirmer que tous les fichiers sont estimés ni que ce mécanisme est actif sur chaque instance ; elle impose de ne pas appeler natif un horodatage seulement parce qu’il est en TTML. La traduction française ne devient pas phonétiquement alignée sur un chant étranger par simple conservation de ces instants.

## 3. Chevauchements : ne pas empiler des cascades aveuglément

- **LiriQo** annonce déjà KuGou, QQ Music, NetEase, Migu, Kuwo et LRCLIB parmi ses adaptateurs.
- **Better Lyrics** utilise notamment **lrc.red** et son propre cache ; les routes spécifiques KuGou et QQ recoupent aussi des sources de LiriQo.
- **Lyrica** réutilise notamment LRCLIB et NetEase, ainsi que d’autres fournisseurs décrits par son projet.
- **lyrics.ovh** consulte déjà LRCLIB avant d’autres sources.

Ces liens proviennent des documentations citées ci-dessus et du dépôt [1](https://github.com/NTag/lyrics.ovh). Les formats et fournisseurs exposés dépendent de la version et de l’instance consultée.

**Conséquence d’ingénierie proposée :** conserver à la fois le service interrogé et la provenance amont lorsqu’elle est fournie. Un texte reçu par LiriQo puis par un connecteur KuGou peut provenir du même enregistrement de paroles ; deux réponses ne sont pas deux confirmations indépendantes. L’ajout d’un connecteur direct peut néanmoins apporter une autonomie ou d’autres candidats : ce gain devra être démontré, pas supposé.

Le choix du `primary` d’un agrégateur ne remplace pas le choix manuel de l’utilisateur. Ne pas activer automatiquement ses fonctions de traduction, romanisation, analyse d’humeur, contribution à une base ou autres enrichissements annexes.

## 4. Sources du premier comparatif — observations conservées

### LRCLIB — source directe retenue dans le noyau

- Le projet officiel décrit un service gratuit de recherche et contribution de paroles synchronisées : [1](https://github.com/tranxuanthang/lrclib).
- Documentation officielle consultée : https://lrclib.net/docs. Les champs `plainLyrics`, `syncedLyrics`, `lyricsfile` et `hasWordSync` ne doivent être utilisés que selon les données réellement reçues. La présence d’un texte ne prouve pas la présence d’un minutage.
- Les requêtes doivent identifier NovaListen et respecter les limites variables ainsi que `Retry-After` ; ne pas annoncer Gratuit = Illimité.
- La recherche par signature et la recherche de candidats sont à articuler sans supprimer le choix manuel. Un résultat exact du point de vue de l’API n’est pas une preuve absolue de correspondance avec un fichier local, une reprise ou une version live.
- La gratuité du service et la licence de son code ne constituent pas une licence générale de toutes les paroles pour l’affichage, la sauvegarde, la traduction, Fil Rouge ou un transfert à un tiers.

### Textyl — recours synchronisé secondaire, sous réserves

Le site officiel documente `https://api.textyl.co/api/lyrics?q=...`, annonce des requêtes gratuites sans authentification et décrit des paroles liées à des instants : [1](https://textyl.co/).

- Cette documentation a été obtenue par l’index de recherche ; sa lecture directe avec l’outil de récupération n’a pas abouti. Ne pas transformer cette observation en preuve de fonctionnement ou d’arrêt de l’API.
- Les temps de réponse annoncés par l’auteur ne sont pas des mesures de NovaListen et ne sont pas repris comme promesse.
- Aucun taux de réussite sur les quatre langues de la collection n’est connu. Vérifier particulièrement les erreurs de correspondance ; une API trouvant un texte ne démontre pas qu’il s’agit des bonnes paroles.
- Conditions de sauvegarde, provenance amont, quota et stabilité à examiner avant une intégration. Aucun texte n’est recherché ici.

### lyrics.ovh — recours textuel secondaire, sous réserves

Le dépôt officiel présente `https://api.lyrics.ovh/v1/{artist}/{title}`, qui retourne `lyrics` ou une absence de résultat. Il décrit aussi l’interrogation de LRCLIB en premier, puis d’autres sources en cas d’échec : [1](https://github.com/NTag/lyrics.ovh).

- Son intérêt potentiel est le complément de résultats textuels, pas une synchronisation inventée.
- Sa consultation peut reproduire du travail déjà réalisé auprès de LRCLIB ; le gain réel de couverture et le coût de cette répétition devront être évalués.
- La sélection interne du fournisseur ne dispense pas NovaListen de proposer le texte reçu à l’utilisateur, ni de respecter les droits des sources amont.

### Vagalume — gratuité de l’API, droits séparés

La documentation officielle indexée annonce une API gratuite avec clé et demande logo et lien : [1](https://api.vagalume.com.br/docs/). La page consacrée aux paroles précise que l’API **ne transfère pas les droits d’utilisation des paroles**, qui doivent être obtenus auprès des propriétaires ; elle propose sinon un lien vers la page Vagalume : [1](https://api.vagalume.com.br/docs/letras/).

- Cette distinction empêche de considérer l’inscription gratuite comme une autorisation acquise d’afficher, conserver, traduire et analyser tous les textes.
- Les récupérations directes de ces deux pages documentaires ont renvoyé une page 503 ; les informations ci-dessus proviennent de leur indexation. Cela ne mesure ni la disponibilité de l’API de paroles ni la possibilité actuelle de créer une clé.
- Aucun compte, clé, acceptation de conditions ou accord de droits n’est obtenu ici. Ne pas demander de clé dans le chat.

## 5. Sources à ne pas présenter comme équivalentes

| Source ou famille | Pourquoi elle n’entre pas dans le cœur recommandé à ce stade |
| --- | --- |
| **Genius** | Son API officielle documente recherche, chansons, artistes et annotations, pas un endpoint général livrant toutes les paroles complètes. Une extraction de pages Web par une bibliothèque est une autre opération ; ne pas la présenter comme l’accès normal de l’API. Documentation : [1](https://docs.genius.com/). |
| **Musixmatch** | La page Pro API consultée affiche des offres payantes à partir de 49 USD par mois ; le cache des paroles n’est pas inclus dans les premiers forfaits. Ce n’est pas une solution retenable comme API complète et hors ligne gratuite sur cette base. Ne pas reprendre comme faits actuels des comparatifs annonçant sans vérification un ancien quota gratuit ou 30 % du texte. [1](https://www.musixmatch.com/pro/api/pricing). |
| **ChartLyrics** | API historique souvent citée ; la documentation attendue n’a pas pu être confirmée lors de cette recherche : page HTTP en 404, récupération HTTPS en erreur. Cela ne démontre pas l’arrêt de tous ses endpoints, mais ne justifie pas d’en faire un pilier fiable. Documentation historique recherchée : http://www.chartlyrics.com/api.aspx. |
| **Lololyrics** | La FAQ renvoie toujours à une API, mais la racine de sa documentation a répondu 404 pendant cette consultation. Disponibilité, conditions et endpoints actuels à confirmer avant de l’ajouter à une cascade. [1](https://www.lololyrics.com/faq) ; https://api.lololyrics.com/. |
| **Lyrist** | Projet communautaire documentant une limite de 50 requêtes par heure et par IP, insuffisant pour le présenter sans étude comme une source de préparation massive. Disponibilité, provenance et droits à vérifier. Son existence ne prouve ni une source indépendante ni un service durable : [1](https://github.com/asrvd/lyrist). |
| **Wrappers de services privés et agrégateurs open source** | Du code gratuit ne garantit ni les droits des paroles, ni une API amont gratuite, ni un accès sans authentification. Ne pas ajouter de cookies récupérés, jetons d’une autre application ou contournement d’accès pour augmenter artificiellement la liste. Les projets publiant une cascade ne prouvent pas que tous leurs fournisseurs sont compatibles avec NovaListen. |

## 6. Exécution de l’architecture validée — paramètres encore ouverts

Le premier ordre proposé **LRCLIB → Textyl → lyrics.ovh** n’avait pas été validé. La validation porte sur **l’architecture révisée de la section 1**, pas sur le rétablissement de cet ancien ordre comme cascade complète.

Pour une **nouvelle recherche demandée**, le noyau retenu est **lrc.red + LRCLIB + LiriQo**, avec Better Lyrics en complément ciblé selon cache et route, puis les recours secondaires admissibles. La hiérarchie des rôles est acquise ; ni l’ordre exact des fournisseurs, ni le choix séquentiel ou parallèle, ni la règle d’arrêt, ni la présence systématique de tous ces services ne sont fixés.

- Les connecteurs directs KuGou, QQ, NetEase, Migu et Kuwo restent des options techniques à évaluer séparément, pas cinq étapes obligatoires après LiriQo.
- Lyrica reste une piste de réserve tant que son déploiement et les conditions de service ne sont pas adaptés ; Textyl, lyrics.ovh et Vagalume conservent les réserves détaillées ci-dessus.
- La recherche Web puis le collage restent le parcours acquis si aucune parole adéquate n’est trouvée ; aucune extraction automatique de sites n’est choisie.
- Pour la simple relecture de paroles déjà choisies et sauvegardées, réutiliser les données locales autorisées. Cela ne bloque pas la commande explicite de rechercher ou choisir une autre version.

### Principes conservés et détails de réalisation à préciser

1. **Chercher le bon enregistrement**, pas seulement un titre proche : artiste, titre, album, durée, version live/remix/reprise et langue sont des indices à examiner sans score arbitraire déjà fixé.
2. **Conserver des candidats comparables** et leur provenance, puis le choix manuel déjà demandé ; ne pas appliquer automatiquement le premier texte renvoyé. La cascade n’abroge pas l’exigence multisource par son seul nom.
3. **Préférer des horodatages réellement disponibles** sans transformer des paroles par ligne en données mot à mot. Le niveau de synchronisation doit être annoncé honnêtement.
4. **Éviter les doublons et les appels répétés inutiles**, sans confondre deux variantes légitimes et sans écraser la sélection actuelle par un nouveau résultat.
5. **Borner l’attente et respecter les quotas**, différencier aucun résultat, coupure réseau, fournisseur indisponible et limite atteinte. Délais, nombre de tentatives, concurrence et mémorisation temporaire des échecs restent à définir.
6. **Préserver les données choisies** et ne pas faire de recherche réseau à chaque lecture. Droits de stockage et provenance doivent rester associés au contenu.
7. **Mesurer ultérieurement la couverture et la justesse** en français, anglais, coréen et espagnol, sur la collection concernée et après autorisation d’essais. Le nombre de fournisseurs n’est pas un taux de réussite ; un résultat incorrect compte comme une erreur, pas comme un succès.

**Acquis : architecture de cascade et rôles de la section 1.** Restent ouverts : ordre exact, séquentiel/parallèle, scores, délais chiffrés, tentatives et stratégie d’arrêt. Aucun taux de couverture, garantie de disponibilité ou droit de mise en service n’est établi par la validation. Les droits et conditions d’accès doivent être compatibles avant activation ; aucun compte, clé, installation, essai, envoi de fichier ou dépense n’est autorisé par ce seul accord de conception. Les autres API, les thèmes visuels et la phase de code suivent toujours l’ordre fixé dans l’[Accueil](01-accueil.md).

**Travail réalisé : recherche documentaire et rédaction.** Consultation de pages publiques et de README, notamment via GitHub ; aucun endpoint de recherche de paroles ou de traduction appelé, compte créé, clé générée, frais engagés, logiciel installé, modèle téléchargé, application développée ou fichier musical de l’utilisateur transmis. Les affirmations des fournisseurs sur leurs performances ne sont pas des essais réalisés ici.
