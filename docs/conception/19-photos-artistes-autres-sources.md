# NovaListen — Photos d’artistes : multisource, Last.fm et qualité des résultats

> **Statut : comparatif synchronisé après validation de la cascade photo, 17 novembre 2026, sans implémentation.** Après « Oui mais pour la photos artiste je veux d'autres piste », l’utilisateur précise : **« Fanart donne de fausses image ou d'anciennes image ajoute last fm et d'autres sources aussi »**. **Last.fm est ajouté au périmètre demandé pour les photos** ; son accès effectif et les droits ne sont pas présumés acquis. La recommandation précédente de fanart.tv en première source est retirée au profit d’une étude multisource. Le retour de l’utilisateur sur les erreurs et l’ancienneté est enregistré, pas transformé en taux d’erreur mesuré ni en affirmation que toutes les images du fournisseur sont fausses.
> **Fondations :** [choix manuel et sauvegarde des portraits dans Artistes](05-artistes.md), [socle artistique retenu](18-biographies-portraits-et-records.md), [API gratuites et usage personnel](13-api-et-enrichissements.md). Wikipédia, Wikidata et français prioritaire puis anglais proposé restent retenus hors photos. La [cascade de métadonnées/pochettes validée](17-cascade-api-metadonnees-et-pochettes.md) n’est pas modifiée par cette réserve sur les portraits.
> **Limite de portée :** ajouter Last.fm à la conception ne rétablit pas une fonction retirée de l’API et ne donne pas les droits des photos. L’utilisateur demande ensuite une cascade avec toutes les pistes et réaffirme l’usage personnel ; la [cascade adaptative photo](20-cascade-api-photos-artistes.md) est désormais **validée par « Je valide »**, dans ses rôles conditionnels, son ordre et ses mécanismes d’efficacité. Accès/droits effectifs, réalisation et options explicitement laissées ouvertes restent distincts de cet accord. Aucun compte, clé, autorisation fournisseur, récupération de portrait pour l’application, test de collection ou code n’est lancé.

## 1. Ce qui change réellement

- **Demande acquise : élargir les sources et inclure Last.fm dans leur étude**, au lieu de dépendre de fanart.tv pour les portraits. Ne pas redemander si Last.fm doit figurer dans le périmètre.
- **Priorité fanart.tv retirée de la recommandation :** ce fournisseur n’est plus présenté comme la première référence fiable ou récente par défaut. Son rôle d’ultime complément photo admissible est désormais retenu dans la cascade validée, pas un retour en priorité ni une suppression de son rôle déjà validé pour les pochettes.
- **Objectif de qualité explicite :** éviter une photo du mauvais artiste et mieux distinguer les images récentes des anciennes. Aucune source n’est promise infaillible ou systématiquement à jour.
- **Approche retenue : galerie multisource admissible**, avec provenance visible et choix manuel parmi les candidats pertinents et réutilisables. Il s’agit de retenir des rôles conditionnels, pas d’admettre automatiquement tous les accès/usages ni d’appeler toutes les API pour chaque artiste.
- Les rôles conditionnels et l’ordre de la cascade photo sont désormais acquis ; leur admission effective reste à vérifier. Commons est retenu en deuxième vague, pas imposé en source unique ; recours officiels/import autorisé et poursuite volontaire sont retenus en principe, avec commandes/interface détaillées à concevoir.

| Source / piste | Position dans la conception | Limite à ne pas masquer |
| --- | --- | --- |
| **Last.fm** | **Ajout explicitement demandé**, examiné en section 2. | Retrait des portraits de l’API expliqué par le support ; images exclues de l’accord API. Pas un collecteur automatique de photos rendu utilisable par cette demande. |
| **Genius** | Rôle de première vague photo retenu sous conditions d’accès et de droits. | Des URL de portrait/en-tête dans des exemples ne garantissent ni galerie multiple, ni photo récente, ni droit de conservation. |
| **Discogs** | Rôle photo ciblé de troisième vague retenu sous conditions, distinct de son rôle d’éditions/crédits déjà retenu. | Photos Restricted Data, authentification et conditions de fraîcheur/conservation à rendre compatibles ; pas une banque libre de portraits permanents. |
| **TheAudioDB** | Première vague photo conditionnelle retenue. | Accès gratuit d’application non établi ; visuels et droits à vérifier. |
| **Openverse** | Recherche élargie de deuxième vague retenue, médias annoncés sous licences ouvertes à vérifier à l’origine. | Ni identité musicale native garantie, ni certification des licences ou des dates ; doublons possibles avec d’autres sources. |
| **Wikimedia Commons** | Deuxième vague retenue pour des fichiers dont les conditions permettent la réutilisation. | Vérification du sujet, de la date et de la licence de chaque fichier ; pas nécessairement une photo actuelle. |
| **Sites officiels / kits presse et image locale autorisée** | Recours volontaire de recherche ciblée et d’import autorisé retenu en principe ; modalités à définir. | Pas d’API universelle choisie ; téléchargement et statut officiel ne signifient pas réutilisation libre. |
| **fanart.tv** | Ultime complément photo admissible retenu, jamais prioritaire. | Mauvais sujet ou ancienne photo possibles selon le retour utilisateur ; likes, MBID et date d’ajout ne certifient pas le portrait. |

## 2. Last.fm — ajouté à la demande, pas promis comme API photo fonctionnelle

### 2.1 Ce que montrent la documentation et le support

La page officielle `artist.getInfo` contient toujours un ancien exemple avec des champs `image`. **Un exemple de documentation n’est ni un essai actuel ni la preuve qu’un portrait réel est fourni aujourd’hui.** L’API demande une clé ; la lecture publique de cette méthode n’exige pas de session personnelle. Cela ne justifie ni compte obligatoire pour chaque lecteur, ni création d’une clé maintenant.

L’annonce de l’équipe **LAST.HQ du 1er mai 2019**, consultée sur le support officiel et réépinglée en mai 2025, explique que les accès indirects à des images ont été corrigés pour faire respecter les conditions API. Le fil « Broken [artist] images » contient une explication de modération : les portraits d’artistes ne sont plus fournis comme auparavant et les appels concernés renvoient notamment un visuel générique. Les échanges de 2025 renvoient encore à cette annonce. Ce sont des preuves documentaires et des retours du support, **pas un test de NovaListen en novembre 2026**.

Sources reconsultées :

- Méthode : https://www.last.fm/api/show/artist.getInfo.
- Annonce de l’équipe : https://support.last.fm/t/api-announcement-usage-of-audio-audiovisual-images-or-artwork/202.
- Fil explicatif, dont messages de modération : https://support.last.fm/t/broken-artist-images-in-user-gettopartists-and-user-gettoptracks/1558.
- Échange de 2025 et lien vers l’annonce : https://support.last.fm/t/user-getweeklyalbumchart-lacks-album-artwork/116378.

### 2.2 Droits et rôle honnête dans NovaListen

- Les conditions **§5.1.8 excluent les images et artwork de l’accord API**, qu’ils soient ou non accessibles techniquement. Le site visible dans un navigateur, une URL ou le caractère personnel du projet ne donnent pas une autorisation de copie durable dans NovaListen.
- Les obligations applicables aux métadonnées — dont plafond global de 100 MB et cache selon les en-têtes — ne deviennent pas une permission de conserver les images. Le rôle Last.fm pour les tags dans la cascade validée reste acquis et distinct.
- **Last.fm figure désormais dans les sources demandées. Son utilisation photo effective reste non admissible dans le cadre API consulté**, sauf évolution ou autorisation compatible explicitement établie ultérieurement. Ne pas le compter comme source active, annoncer des portraits récents garantis ou attendre indéfiniment son résultat avant de montrer les autres propositions.
- Une consultation volontaire de la fiche sur le site est retenue en principe comme option séparée pour comparaison ; la commande et son emplacement restent à décider. **Ouvrir une page n’importe pas ses photos et ne crée pas un droit de téléchargement.**
- Pas de scraping de la galerie, reprise d’un collecteur tiers, changement de domaine ou agrandissement d’une image générique pour reconstituer un accès retiré. Pas d’API cachée, de clé empruntée ni de proxy de contournement.
- Une réponse vide, un visuel générique et une photo réelle doivent rester distincts. Ne pas transformer le visuel de remplacement en portrait choisi ; aucun algorithme de détection ou liste de signatures n’est validé ici.
- Cet ajout ne change ni la biographie principale Wikipédia, ni Fil Rouge, ni les paroles, ni le Top 10 personnel ; aucun scrobbling, historique distant ou connexion de compte personnel n’est ajouté.

Conditions reconsultées dans les parties relatives à l’accès, au cache et aux images : https://www.last.fm/api/tos.

## 3. Autres sources musicales : comparer, pas promettre une meilleure fraîcheur

### 3.1 Genius

- La documentation fournit une lecture d’artiste et un jeton client pour les données non personnelles. Des exemples de la bibliothèque tierce `geniusr` montrent **`image_url` et `header_image_url`** pour un artiste. Une image d’en-tête n’est pas nécessairement un portrait rond adapté et deux tailles du même fichier ne sont pas deux photos différentes.
- Ce constat de schéma ne garantit ni la couverture actuelle des artistes de la collection, ni plusieurs portraits, ni une date de prise de vue ou une photo plus récente que chez fanart.tv.
- La documentation exige un accord pour l’usage commercial. Les conditions générales consultées, datées du 13 janvier 2026, protègent images et photographies et limitent copie, modification et extraction hors autorisation. **Les droits de conservation hors ligne doivent donc être établis avant d’en faire une source effective.**
- Rôle conditionnel de première vague retenu dans la cascade, pas source gratuite de portraits permanents déjà vérifiée. Aucun scraping, client, jeton ou compte créé ; aucun appel d’artiste exécuté.

Sources : https://docs.genius.com/ ; https://genius.com/static/terms ; exemples tiers déjà consultés, pas exécutés : https://ewenme.github.io/geniusr/articles/geniusr.html.

### 3.2 Discogs

- La ressource **Artist** documente une liste `images`, avec notamment type primary/secondary, dimensions et URL ; elle permet donc d’étudier des photos d’artiste, pas seulement des pochettes. La ressource **Images** demande une authentification et le respect des limites. Ne pas construire, modifier ou deviner une URL signée.
- Les images d’artistes font partie des **Restricted Data**, pas des données de catalogue CC0 par association. La licence et les conditions du fichier restent à vérifier.
- Les conditions de mai 2025 imposent notamment de ne pas afficher un contenu de plus de **six heures de retard sur le service** et de ne pas le conserver plus longtemps que nécessaire. Cette clause de fraîcheur du contenu API **n’est ni la date de prise de vue du portrait ni une règle imposant d’effacer chaque photo exactement après six heures**. Sa compatibilité avec une sauvegarde durable hors ligne reste à clarifier.
- Rôle photo de troisième vague retenu sous ces réserves ; pas d’accès ou d’intégration activée par cette validation. Son rôle d’éditions/crédits dans la cascade de métadonnées reste inchangé.

Documentation Artist et Images reconsultée : https://www.discogs.com/developers/resources/database/artist.html. Conditions reconsultées : https://support.discogs.com/hc/en-us/articles/360009334593-API-Terms-of-Use.

### 3.3 TheAudioDB

Le guide distingue **Artist Thumb**, Cutout, WideThumb, Banner, Logo et Fanart. Plusieurs formats ne signifient pas plusieurs portraits indépendants ou récents ; un logo ou une scène large n’est pas automatiquement un portrait.

- La documentation v1 présente un accès Free et une clé publique/de test, tandis que la demande de clé invite les applications vers une offre à 8 dollars. La gratuité effectivement autorisée pour NovaListen reste non établie ; ni v2 Premium, ni abonnement, ni utilisation d’une clé de test comme droit de production présumé.
- La description des URL et aperçus ne constitue pas une licence universelle d’images. Vérifier affichage, cadrage et conservation hors ligne.

Complément conditionnel, sans garantie de fraîcheur : https://www.theaudiodb.com/docs_artwork.php ; https://www.theaudiodb.com/free_music_api ; https://www.theaudiodb.com/api_apply.php. Les nuances d’accès restent détaillées dans le [comparatif des métadonnées/pochettes](16-metadonnees-et-pochettes.md).

## 4. Recherche élargie et sources officielles

### 4.1 Openverse et Commons

- **Openverse** agrège des métadonnées de médias annoncés sous licences Creative Commons ou dans le domaine public, dont des images venant notamment de Flickr et Commons. Ce n’est pas une nouvelle photographie indépendante à chaque fournisseur : dédupliquer les résultats.
- L’agrégateur ne certifie **ni la licence, ni le sujet, ni la date**. Vérifier la page d’origine, les conditions de l’hébergeur, le créateur et le contexte. Pas de correspondance native par MBID ou de reconnaissance faciale présumée.
- Respecter les quotas, formalités, mentions d’utilisation d’Openverse sans approbation et conditions des sources d’origine. Aucune nouvelle clé ou intégration directe Flickr n’est automatiquement requise ou autorisée par cette piste. **Openverse ne sert pas à contourner les conditions Flickr ou celles d’un autre hébergeur.**
- La page documentaire consommateur `https://api.openverse.org/v1/` avait retourné HTTP 500 à l’outil documentaire lors de l’étude précédente. Ce constat isolé n’était pas un essai d’endpoint de recherche et ne prouve ni fermeture du service ni disponibilité sur le téléphone. Aucun quota tiers n’est adopté ; rester dans un accès effectivement sans frais et compatible, sinon sauter la source.
- **Commons** est retenu comme branche conditionnelle de deuxième vague, pas comme solution unique imposée. Vérifier le sujet, la date lorsqu’elle est renseignée, la source et la licence de chaque fichier. Les médias d’un article Wikipédia hébergés sous d’autres conditions, notamment localement, ne deviennent pas tous réutilisables.
- Une licence ouverte adaptée permet une réutilisation conforme sans demander une autorisation individuelle supplémentaire ; conserver les crédits et respecter les autres droits applicables. Elle ne garantit pas que la photo représente l’apparence actuelle de l’artiste.

Sources : https://docs.openverse.org/api/reference/made_with_ov.html ; https://docs.openverse.org/terms_of_service.html ; https://docs.openverse.org/api/reference/authentication_and_throttling.html ; https://docs.openverse.org/catalog/reference/DAGs.html ; guide et API Commons détaillés dans la [fiche artistique](18-biographies-portraits-et-records.md).

### 4.2 Sites officiels, labels, kits presse et import local autorisé

C’est une piste ciblée pour rechercher une photo publiée par l’artiste, son équipe ou son label, avec un contexte éventuellement daté. **Officiel ne veut pas dire récent, exact pour tous les crédits ou librement réutilisable.** Vérifier l’identité de la page et les conditions du kit ou de l’image ; pas de banque universelle gratuite supposée.

La recherche volontaire de sources officielles et l’import local autorisé d’une image personnelle ou obtenue légalement sont retenus en principe comme recours de la cascade. Leurs commandes, présentation et modalités restent à concevoir. Ne pas automatiser la collecte de réseaux sociaux, transformer un résultat de moteur d’images en licence, ou présenter comme import autorisé un téléchargement interdit provenant de Last.fm/Deezer ou d’une autre source. Aucun collecteur de sites officiels, navigateur automatisé, nouveau backend ou hébergement n’est choisi.

## 5. fanart.tv — pas de priorité ni de fiabilité présumée

**Le retour de l’utilisateur motive le retrait de ma recommandation précédente « fanart.tv en premier ».** Il ne demande pas explicitement la suppression complète de fanart.tv, ni la révocation de son rôle de pochettes déjà retenu. Le conserver comme ultime complément photo admissible est désormais retenu par la validation de la cascade, sans garantie de sujet ou de récence.

Les constats techniques précédents restent valables sans en faire une garantie de qualité :

- Endpoint artistique et type `artistthumb`, autres catégories de fonds, logos et bannières. Vérifier l’identité de l’artiste et le contenu de l’image ; ne pas prendre un membre pour son groupe, un homonyme ou une pochette comme portrait.
- Réutiliser le bon MBID d’artiste aide à chercher, **mais un identifiant correct ne certifie pas chaque image associée**.
- Les champs `added`, `likes`, dimensions ou rang ne prouvent pas la date de prise de vue, l’exactitude ou le caractère actuel. Une photo ancienne peut avoir été ajoutée récemment.
- Accès Project/Personal annoncés gratuits avec délais de 7/2 jours pour les nouvelles publications ; ce ne sont ni des latences réseau ni des garanties de photos récentes. Aucun VIP payant choisi.
- `copyright` et éventuel `copyright_link` à examiner ; `Unknown` n’est pas une licence. Ne pas appliquer la licence de la documentation aux œuvres.
- Aperçus documentés jusqu’à 200/400 px et originaux ; tailles cibles non choisies. Ne récupérer que ce qui est utile et autorisé, avec gestion des éventuels 429/`Retry-After`.

Documentation précédemment consultée : https://api.fanart.tv/. Aucun portrait de la collection n’a été recherché pour confirmer ou quantifier le retour utilisateur.

## 6. Principes de qualité retenus et options encore ouvertes

**Les principes de contrôle d’identité, type d’image, provenance, doublons et dates sont retenus dans la cascade validée.** Ils ne garantissent pas une détection parfaite ; tri/filtres, rejets mémorisés, seuils et détails d’interface restent proposés comme indiqué ci-dessous.

1. **Vérifier l’identité avant l’image :** réutiliser l’association d’artiste choisie et confronter personne/groupe/projet, alias et contexte. Nom exact ou forte popularité ne suffisent pas. En cas de doute, montrer les différences plutôt que fusionner des artistes ou appliquer le premier résultat.
2. **Distinguer les types d’images :** portrait, photo de groupe, logo, pochette, affiche et photo de scène. Ne pas afficher un visuel générique comme portrait trouvé. Une photo de scène peut rester un choix pertinent ; aucun filtre automatique universel n’est établi.
3. **Rendre la provenance explicite :** source d’accès, page d’origine, créateur/crédit et état des droits nécessaires. Plusieurs sites peuvent reprendre le même fichier ; dédupliquer plutôt que compter chaque URL comme une photo différente.
4. **Séparer les dates :** date de prise de vue ou d’événement lorsqu’elle est réellement sourcée ; date de publication/ajout chez le fournisseur ; date de récupération dans NovaListen. Ne pas convertir les deux dernières en date de prise de vue. Si elle n’est pas connue, afficher **« Date inconnue »**, pas « récente ».
5. **Permettre une comparaison de fraîcheur honnête :** proposer un tri ou filtre par date fiable quand les données le permettent, ou une recherche de contexte/époque. Pas de seuil d’ancienneté, année minimale, ordre des dates inconnues ou préférence de période validé ici. Une image ancienne choisie volontairement reste légitime et n’est pas supprimée du seul fait de son âge.
6. **Garder le choix manuel :** plusieurs propositions lorsque disponibles, prévisualisation adaptée au portrait rond, sélection puis sauvegarde locale de l’image autorisée et des crédits. Le cadrage détaillé et l’emplacement de ces informations restent à choisir.
7. **Proposer un rejet de candidat et une recherche corrigée :** par exemple « Ce n’est pas cet artiste » ou « Autres photos ». Ces commandes, la mémorisation éventuelle des rejets et leur portée restent à valider ; ne pas inventer une liste noire définitive ou une modération distante automatique.
8. **Préserver l’existant :** nouvelle recherche, fichier réindexé ou réponse tardive ne remplacent pas silencieusement la photo choisie. La photo actuelle reste visible jusqu’au nouveau choix confirmé ; aucun rafraîchissement périodique obligatoire n’est décidé.

Pas de reconnaissance faciale, d’envoi des photos à un service de vision, de modèle téléchargé, de score chiffré de certitude, d’analyse d’âge ou de détecteur d’image générée retenu par cette demande. La vérification du bon sujet ne garantit pas non plus la licence.

## 7. Autres pistes vérifiées : pourquoi je ne les promets pas comme API gratuites de portraits permanents

| Piste | Constat documentaire | Conséquence dans le cadre actuel |
| --- | --- | --- |
| **Flickr en intégration directe** | L’aide actuelle réserve la demande de clé aux abonnés **Pro**. Les conditions API imposent notamment le respect des restrictions des photographes, un stockage limité à des périodes raisonnables et des retraits dans certaines situations. | **Pas de nouvelle intégration directe gratuite garantie** pour NovaListen ; ne pas emprunter une clé. Les résultats Flickr trouvés par Openverse restent soumis aux conditions d’origine. |
| **Bandsintown** | Ancien schéma API avec lien de photo d’artiste, mais l’aide actuelle indique des clés liées à un artiste, hors autorisation, et **n’accepte pas les demandes pour projets personnels ou éducatifs**. | Non admissible comme dépendance de ce projet personnel sur la base de ces conditions ; ne pas promettre une clé publique ou reprendre celle d’un autre projet. |
| **TIDAL** | Les Developer Terms mentionnent des images d’artistes, mais refusent la conservation indéfinie et limitent l’hébergement local au cache temporaire nécessaire de métadonnées/pochettes. Accès, compte et quotas distincts. | Pas une nouvelle banque de portraits durablement hors ligne autorisée ; aucune gratuité effective d’intégration ou sélection de source établie par ce seul examen des conditions. |
| **Deezer** | La FAQ répond non au stockage des images pour raisons juridiques ; accès actuel d’application non confirmé. | Pas de portraits durables par défaut ; un autre nom de fichier, une URL modifiée ou un import ne contournent pas la restriction. |
| **Spotify** | Les nouvelles applications commencent en development mode et le propriétaire doit avoir Premium. | Pas de nouvelle dépendance sans abonnement dans notre cadre ; aucun compte Premium existant supposé. |
| **iTunes Search / Cover Art Archive** | Illustrations de boutique ou pochettes d’albums selon le service et l’objet. | Ne pas transformer une pochette en portrait certain de l’artiste ; usages et licences restent distincts. |

Sources reconsultées le 17 novembre 2026 :

- Flickr : https://www.flickrhelp.com/hc/en-us/articles/4404070036884-Flickr-API ; https://www.flickr.com/help/terms/api. Les pages `flickr.photos.search` et `misc.api_keys` ont retourné HTTP 403 à l’outil documentaire ; ce n’est pas un test de recherche de photos. L’aide lisible établit la restriction Pro ; les extraits indexés de l’ancienne documentation ne l’annulent pas.
- Bandsintown : https://help.artists.bandsintown.com/en/articles/7053475-what-is-the-bandsintown-api ; schéma consulté : https://app.swaggerhub.com/apis-docs/Bandsintown/PublicAPI/3.0.1. L’aide actuelle prévaut sur une vieille présentation générale pour déterminer l’éligibilité de NovaListen.
- TIDAL, texte affiché version 1.0 du 12 septembre 2023, parties pertinentes lues : https://developer.tidal.com/documentation/guidelines-developer-terms-1_0.
- Deezer : https://support.deezer.com/hc/en-gb/articles/360011538897-Deezer-FAQs-For-Developers.
- Spotify : https://developer.spotify.com/documentation/web-api/concepts/quota-modes.

Ces constats ne déclarent pas tous les usages de ces services impossibles. Ils évitent de promettre une galerie hors ligne gratuite que les conditions ou l’accès actuels n’établissent pas. Les rôles antérieurement retenus pour les métadonnées/pochettes ne sont pas retirés par ce tableau.

## 8. Principe multisource et cascade dédiée validée

La nouvelle demande « Fait maintenant une cascade hyper efficace en ajoutant tout ses api pour ce qui est des autorisations ne n'est que pour mon usage personnel » aboutit à une [fiche d’orchestration photo](20-cascade-api-photos-artistes.md), désormais **validée par « Je valide », le 17 novembre 2026**. Elle fixe les rôles conditionnels, l’ordre, les mécanismes d’efficacité et leurs premières bornes, sans appels aux sources non admissibles. Le schéma ci-dessous reste le principe général, pas un second ordre concurrent.

```text
Photo déjà choisie et associations locales → priorité, sans remplacement automatique
            ↓ si recherche / changement volontaire
Identité de l’artiste vérifiée autant que possible ; ambiguïtés présentées
            ↓
Sources de photos admissibles : candidats Openverse/Commons,
Genius/Discogs/TheAudioDB seulement si accès gratuit et usages compatibles
Sources officielles / import autorisé : recours volontaires retenus en principe
            ↓
Résultats progressifs, provenance et dates connues, doublons écartés
            ↓
Choix manuel → sauvegarde locale de l’image autorisée et des crédits
```

**À côté de ce parcours :** Last.fm est une source demandée, mais n’entre pas comme collecteur API de portraits dans le cadre consulté ; une consultation volontaire du site est retenue en principe comme option distincte. fanart.tv n’a plus la première place : ultime complément admissible retenu, sans application automatique ni obligation de l’utiliser.

- Le schéma général ci-dessus ne remplace pas **l’ordre et le plafond photo fixés dans la cascade validée** ; il ne prouve pas l’admission effective de tous ces fournisseurs. Elle ne signifie pas six appels systématiques ou un téléchargement de chaque image en original.
- Afficher des propositions admissibles dès qu’elles arrivent ; une source inéligible ne bloque pas la galerie. Respecter limites, `Retry-After`, arrêts des travaux devenus inutiles et absence de recours payant. Ne pas employer une source secondaire ou un proxy pour récupérer le même contenu en contournant sa restriction d’origine.
- Mutualiser par artiste, réutiliser les résultats autorisés et ne récupérer que les aperçus utiles puis l’image choisie dans une taille adaptée. La [cascade dédiée](20-cascade-api-photos-artistes.md) retient désormais la cible initiale de six vignettes et une découverte initiale bornée par fournisseur ; dimensions d’image, durées de cache, délais et reprises restent à définir.
- Le maximum de deux requêtes simultanées des métadonnées/pochettes n’est pas automatiquement étendu aux autres parcours. **Le parcours photo a désormais sa validation propre de deux requêtes simultanées au total**, sans choisir la concurrence des paroles ou des branches biographies/records. Aucun benchmark de vitesse ou de couverture.
- Si aucune image pertinente et sauvegardable n’est disponible, conserver l’existant ou l’état sans portrait. Ne pas substituer silencieusement une mauvaise personne, une pochette, une image générée ou un logo.

## 9. État des décisions et travail réalisé

**Acquis hors photos :** Wikipédia pour les biographies, français prioritaire puis anglais proposé ; Wikidata pour l’identité et les faits/records référencés à vérifier, pas une certification exhaustive. Texte choisi, crédits et hors ligne autorisé, sans traduction automatique des biographies ajoutée.

**Acquis pour les photos :** plusieurs propositions lorsque disponibles, choix manuel, sauvegarde de l’image autorisée, remplacement volontaire, portraits ronds et séparation des pochettes. **Ajout demandé de Last.fm et d’autres pistes**, avec correction de ma recommandation qui plaçait fanart.tv en premier.

**Non établi ou encore ouvert :** récupération de portraits Last.fm autorisée/techniquement exploitable ; admission effective des fournisseurs et droits d’image ; Genius/Discogs/Openverse/Commons/TheAudioDB comme sources activées ; commandes/modalités d’import, de consultation externe et de poursuite, filtres/tri par dates, rejet mémorisé, interface, dimensions/cadrage, durées de cache, délais, reprises et articulation entre cascades. **Les rôles conditionnels, l’ordre, les mécanismes d’efficacité et leurs premières bornes sont désormais acquis**, y compris fanart.tv en ultime complément admissible ; ils ne sont plus à redemander comme choix ouverts. **Last.fm n’est plus omis de la demande**, mais il n’est pas annoncé comme intégration de portraits opérationnelle. Aucune source ne garantit à elle seule un portrait exact et récent pour chaque artiste.

**Validation libre acquise — « Je valide », 17 novembre 2026 :** la [cascade dédiée](20-cascade-api-photos-artistes.md) organise identité/réutilisation → Genius + TheAudioDB admissibles → Commons + Openverse → Discogs admissible → fanart.tv admissible en ultime complément → recours manuels volontaires retenus en principe. Last.fm reste explicitement inclus, sans collecte de portraits rétablie ; les autres routes non admissibles sont recensées et sautées. **Deux requêtes simultanées au total pour le parcours photo, un premier lot cible de six vignettes et une découverte initiale bornée à une page ou fiche par fournisseur admissible avec consultation ciblée si nécessaire sont désormais validés**, avec résultats progressifs, poursuite volontaire bornée de type Plus de photos, arrêts, préservation du choix et réutilisation/déduplication/cache compatibles. Il s’agit d’un accord photo distinct, pas d’une extension automatique du plafond des métadonnées/pochettes ni d’une réponse aux questions numérotées laissées ouvertes. Les limites d’accès et d’images documentées restent pertinentes même pour un usage personnel ; aucune autorisation supplémentaire n’est à inventer quand une licence applicable permet déjà l’usage.

**Travail réalisé : documentation, rédaction et enregistrement de la validation uniquement.** Aucun compte, client, clé ou jeton créé, condition contractuelle acceptée, dépense ou installation ; aucun appel d’API d’artiste ou recherche d’image sur la collection, téléchargement de portrait pour l’application, envoi d’audio/empreinte/historique/photo à un modèle, modification musicale ou ajout de code. Les exemples des documentations ne sont pas exécutés.
