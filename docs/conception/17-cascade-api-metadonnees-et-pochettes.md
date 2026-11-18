# NovaListen — Cascade des métadonnées et pochettes validée

> **Statut : architecture validée, 17 novembre 2026.** Après la proposition demandée par « Propose moi un cascade api très efficace avec tout ses api », l’utilisateur confirme « Je valide ». Les huit sources et leurs rôles conditionnels, les trois branches, l’ordre adaptatif, le choix manuel et les mécanismes d’efficacité sont retenus pour la conception. Cette validation ne confirme pas les accès ou les droits encore incertains et n’autorise ni activation, compte, clé, acceptation de conditions, essai ou enrichissement de la collection.
> **Périmètre :** les huit sources discutées pour les métadonnées et pochettes : **MusicBrainz, Cover Art Archive, fanart.tv, Last.fm, Discogs, TheAudioDB, Deezer et iTunes Search API**. Les sources ont toutes un rôle dans l’architecture retenue, sans imposer huit appels à chaque morceau ni prétendre qu’elles sont toutes actuellement accessibles ou compatibles avec tous les usages.
> **Fondations :** [cadre gratuit et personnel](13-api-et-enrichissements.md), [comparatif documentaire des huit sources](16-metadonnees-et-pochettes.md), [édition volontaire dans la Bibliothèque](02-bibliotheque.md) et [regroupements d’éditions dans Albums](06-albums.md). La [cascade de paroles](14-cascade-api-paroles.md) et la [traduction des paroles](15-traduction-francaise-paroles.md) déjà validées restent distinctes et inchangées.

## 1. Principe retenu

**Une cascade adaptative à trois branches : identifier → compléter les champs utiles → proposer des pochettes compatibles.** Les branches se rejoignent dans une sélection manuelle, pas dans une fusion automatique de toutes les réponses.

L’efficacité visée est de limiter les requêtes inutiles, les attentes et les erreurs de version. Il ne s’agit pas d’un résultat de benchmark : aucun débit, taux de couverture, délai de recherche sur le téléphone ou pourcentage de correspondances correctes n’a été mesuré.

1. **Local d’abord :** relire les informations disponibles, corrections, associations et contenus déjà choisis et conservés avec des droits compatibles. Ne pas rechercher à chaque lecture ni remplacer une correction lors d’une actualisation.
2. **MusicBrainz comme pivot d’identité :** chercher des enregistrements et éditions candidats ; réutiliser les identifiants correctement typés lorsqu’ils existent et sont cohérents.
3. **Compléments par besoin :** catalogues pour une identité incertaine, Discogs pour une édition ou des crédits, Last.fm pour des tags, TheAudioDB pour des manques résiduels si son accès gratuit et les usages sont confirmés.
4. **Images sur une branche séparée :** partir de l’édition représentative locale et des pochettes existantes ; ne pas transformer toutes les URL d’image reçues en images téléchargées ou stockées.
5. **Choix utilisateur :** présenter candidats, différences, incertitudes et provenance ; conserver ou écrire seulement ce qui est choisi et autorisé, selon la commande et les permissions. L’ordre d’arrivée des réponses ne décide pas de la vérité.

## 2. Avant chaque fournisseur : vérifier s’il est utilisable pour cette opération

Un adaptateur prévu dans la conception peut être **admissible**, **non configuré**, **bloqué par quota ou panne**, ou **non admissible pour l’opération demandée**. Ne pas traiter ces états comme Aucun résultat.

- **Gratuité :** accès sans abonnement ni appel facturé ; pas de VIP, Premium d’API ou bascule payante implicite. L’offre et les conditions doivent réellement convenir à NovaListen.
- **Accès :** fournisseur disponible, identifiants nécessaires obtenus légalement, configuration compatible. Un accord de principe sur les comptes développeur ne crée pas ces comptes ni ces clés maintenant.
- **Droits par opération et par champ :** requête, affichage, cache temporaire, sauvegarde durable et écriture dans l’audio sont distincts. Un résultat consultable mais non conservable selon les besoins ne complète pas un objectif de sauvegarde hors ligne.
- **Données minimales :** n’envoyer que les éléments utiles à la recherche autorisée. Pas de fichier audio, historique ou collection entière joint implicitement ; pas de scrobbling ni d’accès aux playlists personnelles des plateformes.
- **Déclenchement retenu en principe :** la cascade doit partir d’une commande volontaire d’enrichissement et de son périmètre choisi. Le nom de la commande, son emplacement et l’éventuel traitement en lot restent à définir ; aucune recherche sur la collection n’est lancée ici.

**Si un service est inéligible, le sauter sans attente réseau.** Une source manquante ne doit pas rendre le lecteur inutilisable ; son indisponibilité ou son incompatibilité doit rester compréhensible.

## 3. Cascade A — identifier le morceau et son édition

### Vue d’ensemble retenue

```text
Données locales + choix antérieurs compatibles
                    ↓
       MusicBrainz : recherche ou lookup précis
                    ↓
     Quelle information reste à identifier ?
       ├─ Enregistrement candidat cohérent
       │     ├─ Édition encore ambiguë → Discogs ciblé si admissible
       │     └─ Identité suffisante pour le besoin → branche B / C
       │
       └─ Identité absente, contradictoire ou insuffisante
             → iTunes Search + Deezer en parallèle si admissibles
             → nouvelle comparaison des candidats
             → Discogs si besoin d’édition/crédits non résolu
             → TheAudioDB en dernier complément si admissible
             → choix manuel ou conservation des données existantes
```

Les flèches sont un **ordre architectural retenu sous conditions**, pas des services déjà activés ou des appels obligatoires. Un fournisseur déjà utilisé pour le même besoin n’est pas rappelé à l’identique parce qu’une autre branche le mentionne.

### A0. Préparer une seule recherche cohérente

- Relire titre, artistes du morceau et de l’album, album/édition, mentions de version, disque/piste, durée locale réellement connue et identifiants disponibles.
- Normaliser pour comparer, pas pour réécrire les tags : ne pas supprimer Live, Remix, Acoustic, instrumental, version censurée ou autres éléments discriminants. Ne pas convertir d’office les noms coréens en une translittération unique.
- Séparer **fichier local**, **enregistrement candidat**, **édition candidate** et **groupe local d’albums**. Un même enregistrement peut exister sur plusieurs éditions ou masters ; un identifiant de catalogue ne prouve pas l’identité binaire des fichiers.
- Ne pas mélanger MBID d’artiste, de recording, de release et de release-group ; ne pas utiliser un ID iTunes ou Deezer comme si c’était un MBID. Un ISRC isolé reste un indice, pas une preuve d’édition exacte.

### A1. MusicBrainz en premier

**But retenu :** obtenir une base d’identité et des éditions candidates, pas appliquer le premier résultat.

- Privilégier un lookup d’identifiant existant et cohérent plutôt qu’une nouvelle recherche libre lorsque cela suffit.
- Sinon, chercher avec les informations locales utiles et comparer les résultats ; ne pas confondre score du moteur de recherche et certitude que le fichier est le bon.
- Ne demander les détails d’éditions et listes de pistes que pour les candidats pertinents et le besoin actuel. Plusieurs appels peuvent être nécessaires ; cette architecture ne promet pas une identification complète en une requête.
- Si l’enregistrement paraît cohérent mais l’édition reste ambiguë, **passer directement à Discogs pour ce besoin**, sans payer en latence une recherche générale iTunes + Deezer inutile.

### A2. iTunes Search et Deezer en recours de catalogue

**Condition retenue :** résultat MusicBrainz absent, contradictoire, insuffisant, ou demande explicite de comparaison supplémentaire.

- Lancer les recherches **en parallèle entre les deux services seulement s’ils sont admissibles**, dans la limite globale retenue en section 7. Si Deezer est indisponible ou non autorisé, iTunes peut être consulté seul, et inversement.
- **iTunes Search :** proposer d’autres références de morceaux/albums et les champs effectivement disponibles ; tenir compte de la boutique, de la version et des informations manquantes. Aucune boutique par défaut de NovaListen n’est choisie ici.
- **Deezer :** proposer les correspondances et champs dont l’utilisation est permise. L’autorisation explicite de sa FAQ pour les noms de titres/artistes n’est pas étendue à tout le catalogue ou à toutes les opérations.
- Conserver séparément les candidats, leur source et leurs contradictions. Ne pas créer un faux résultat en prenant le titre d’une version studio, l’année d’une réédition et la pochette d’une version live.
- Si un résultat apporte **réellement** un nouvel identifiant compatible ou un contexte discriminant, limiter la suite à au plus **un retour enrichi vers MusicBrainz pour cette recherche**, sans répéter la même requête ni créer une boucle de fournisseurs. Ne pas supposer qu’iTunes ou Deezer renvoient toujours des identifiants interopérables.

### A3. Discogs pour les éditions et crédits difficiles

**Condition retenue :** l’édition, les positions de pistes ou les crédits demandés restent à vérifier, ou l’utilisateur demande une comparaison spécialisée.

- Comparer, lorsque disponibles, formats, dates d’édition, labels, codes-barres, listes de pistes et crédits aux informations du fichier ; ne pas annoncer un pressage exact sans éléments suffisants.
- Ne pas appeler Discogs systématiquement lorsque l’identité et les champs demandés sont déjà satisfaisants ; le routage direct A1 → A3 évite des étapes inutiles.
- Les données de catalogue CC0 et les images restreintes ont des régimes distincts. Les clauses de conservation et fraîcheur de l’API restent à clarifier pour les usages hors ligne et les tags durables ; ce fournisseur peut donc rester non admissible à ces opérations tant que ce point n’est pas réglé.
- Sauter cette étape si elle n’apporte aucun champ utile autorisé. Ne pas transformer sa réserve juridique en un effacement automatique des informations locales ou des historiques.

### A4. TheAudioDB comme dernier complément conditionnel

**Condition retenue :** des informations demandées manquent encore après les sources adaptées au besoin, avec un accès gratuit effectivement confirmé pour notre application et des usages compatibles.

- Réutiliser les identifiants disponibles quand la méthode attend réellement le même type d’identifiant ; les références d’album au niveau release-group ne prouvent pas l’édition précise.
- Proposer seulement les compléments présents et pertinents, sans réinterroger tous les services pour un champ toujours absent.
- Si l’accès gratuit à NovaListen n’est pas confirmé, l’étape est **prévue mais non admissible**, pas remplacée par une souscription ni exécutée avec une clé de test présumée autorisée en production.

## 4. Cascade B — compléter les tags et genres sans changer d’identité

Cette branche utilise le contexte de l’enregistrement candidat cohérent ou retenu. En cas d’identité incertaine, ses propositions restent liées au candidat, pas appliquées au fichier.

```text
Genre/correction locale déjà choisi → conserver
Sinon : données MusicBrainz déjà reçues et réutilisables
     → Last.fm ciblé pour tags et informations complémentaires
     → genre principal iTunes déjà reçu et pertinent
     → TheAudioDB si admissible et champ encore manquant
     → choix manuel ou champ non renseigné
```

- **Last.fm a ici un rôle actif retenu**, même si MusicBrainz a déjà trouvé l’enregistrement : une identité trouvée ne signifie pas que le genre demandé est renseigné. Le consulter pour les champs utiles, pas comme intermédiaire obligé avant de chercher une pochette.
- Ne pas remplacer le genre choisi par le premier tag populaire. Certains tags Last.fm désignent une époque, une humeur ou une autre notion ; leur filtrage et la gestion d’un ou plusieurs genres restent à définir.
- Les associations de genres MusicBrainz relèvent de données supplémentaires sous conditions, différentes du noyau CC0 ; respecter aussi les obligations de Last.fm et des autres champs utilisés.
- Réutiliser les réponses déjà obtenues dans A quand cela est autorisé et pertinent, plutôt qu’appeler de nouveau iTunes ou TheAudioDB pour chaque champ.
- Ne pas transférer automatiquement le genre d’un artiste à tous ses morceaux ni appliquer un tag d’une version à une autre version contradictoire. Pas de vote majoritaire entre API : des résultats peuvent partager des identifiants ou des sources.
- Ces tags ne deviennent ni une analyse acoustique, ni un BPM mesuré, ni les thèmes de Fil Rouge, ni un remplacement de ses moteurs locaux. Les similarités et compteurs Last.fm ne remplacent pas les statistiques personnelles.

## 5. Cascade C — choisir une pochette de la bonne édition

```text
Pochette locale ou choix sauvegardé compatible → conserver
                 ↓ si recherche voulue ou image absente
Cover Art Archive : release précise candidate ou retenue
                 ↓ si aucune image adaptée admissible, ou plus de choix demandé
CAA release-group / fanart.tv : alternatives de groupe clairement signalées
                 ↓ si encore utile et accès/droits compatibles
TheAudioDB : complément visuel conditionnel
                 ↓
Conserver l’existant, visuel neutre ou choix manuel d’une image autorisée
```

- **Édition avant résolution :** chercher la pochette de l’édition locale représentative, pas simplement l’image la plus grande d’un album homonyme. Si l’édition est incertaine, l’indiquer ; une image de groupe ou l’approbation communautaire CAA ne constitue pas une preuve de bonne édition ou de droits.
- **CAA release-group et fanart.tv** constituent un recours au niveau du groupe d’éditions ; les proposer comme tels. Ils ne remplacent pas silencieusement une pochette d’édition exacte. Selon les données manquantes, leurs aperçus admissibles peuvent être recherchés parallèlement, dans le même budget d’appels global.
- **TheAudioDB** peut compléter le choix visuel seulement si l’accès gratuit et la conservation de l’image sont confirmés. Il n’est pas un recours gratuit garanti aujourd’hui.
- **Deezer et Last.fm ne sont pas utilisés pour les pochettes à sauvegarder** dans cette architecture : cache d’images refusé dans la FAQ Deezer et images exclues de l’accord API Last.fm. Discogs et iTunes restent des sources de comparaison de données ; leurs images ne sont pas téléchargées ou incorporées automatiquement au titre de cette cascade hors ligne.
- Toutes les images, y compris celles de CAA et fanart.tv, restent soumises à des droits compatibles avec l’affichage, la sauvegarde et l’incorporation éventuelle. Pas de permission générale déduite de leur accès public ou de la licence des métadonnées.
- Préférer de petits aperçus autorisés ; récupérer la version utile de l’image choisie plutôt que toutes les images originales. Réutiliser l’image admissible d’une même édition entre ses pistes, sans la télécharger pour chaque morceau.
- **Piste distincte non validée :** un import manuel d’image reste un recours à préciser, pas une nouvelle commande acquise par la validation de la cascade. Le choix manuel n’annule pas les droits d’une image provenant d’un service.

### Préserver les groupes locaux déjà acquis

**The Fame + The Fame Monster** et **Future Nostalgia + Moonlight Edition** restent regroupés selon la demande de l’utilisateur, quelle que soit la classification des catalogues. Nom et pochette d’origine par défaut, ou autre édition représentative **parmi les éditions regroupées localement**, restent les règles acquises.

Choisir une édition représentative de groupe ne réécrit pas les pochettes de tous les fichiers. La liste unique du même enregistrement, les variantes distinctes et tous les fichiers restent intacts ; cette cascade n’impose aucune fusion d’historiques, favoris ou playlists.

## 6. Correspondance, affichage progressif et principe d’arrêt retenu

Trois états de travail, **pas des badges d’interface déjà choisis** :

| État du candidat | Suite retenue en principe |
| --- | --- |
| Informations cohérentes pour le besoin demandé | Montrer le candidat et les différences ; lancer seulement les branches de champs encore utiles ; arrêter l’élargissement automatique de ce besoin quand les propositions sont suffisantes et non contradictoires. |
| Enregistrement plausible, édition incertaine | Garder l’incertitude visible ; recours spécialisé à Discogs ou comparaison d’éditions, sans certifier une pochette ou un master. |
| Contradiction de version/artiste ou informations insuffisantes | Ne pas appliquer ; élargir aux sources admissibles prévues, permettre de corriger la recherche ou garder les données locales. |

- Comparer titre et qualificatifs, crédits, album/édition, numéros de piste et durée locale connue lorsqu’ils existent. L’absence d’un champ n’est pas une concordance démontrée ; une durée voisine ou un seul ISRC ne suffit pas à prouver toute l’identité.
- Afficher les propositions au fur et à mesure ; ne pas attendre TheAudioDB ou un service indisponible pour permettre de consulter les résultats déjà reçus.
- **L’arrêt limite les appels, pas le choix humain :** ne pas sauvegarder ou écrire le premier résultat. Principe retenu : permettre une recherche de davantage de résultats même lorsqu’un candidat semble bon, sans imposer de parcourir les huit API.
- Une fois le choix fait ou la recherche annulée, annuler les travaux devenus inutiles si possible et ignorer les réponses tardives pour l’application des champs. Une réponse tardive n’écrase pas le choix.
- Aucun score de confiance numérique, marge de durée, nombre maximal de candidats, seuil d’arrêt automatique ou délai d’attente n’est validé. Les critères détaillés de ces états doivent être précisés et évalués ultérieurement ; pas de promesse de reconnaissance parfaite.

## 7. Mécanismes d’efficacité retenus

- **Ordonnanceur commun : au plus deux requêtes réseau simultanées pour cette cascade**, tous fournisseurs et branches confondus ; prioriser l’identité et les résultats demandés par l’utilisateur. Cette limite est validée pour la conception de cette cascade uniquement ; ce n’est ni un optimum mesuré ni une autorisation de traiter toute la bibliothèque. Elle ne fixe pas la concurrence de la cascade de paroles.
- **Débits propres à chaque fournisseur :** une file globale par fournisseur, y compris pour plusieurs recherches ou albums. Deux requêtes possibles dans l’ordonnanceur ne permettent pas de doubler les limites d’une même API.
- **Dépendances respectées :** attendre un contexte d’identité cohérent avant les compléments spécifiques. Puis Last.fm et CAA peuvent avancer indépendamment sur des besoins différents, dans la limite commune ; ne pas lancer une vague de huit services pour chaque morceau.
- **Dédupliquer les requêtes identiques en cours** et partager les réponses réutilisables pour le même objet et contexte. Un album ou artiste commun n’exige pas sa propre requête pour chacun de ses morceaux ; ne pas confondre pour autant leurs crédits, éditions ou enregistrements.
- **Cache compatible avec les droits :** distinguer choix persistants autorisés, réponses de travail et absence de résultat. Inclure source, type d’identifiant, requête/version, boutique/pays si pertinent et contexte de version dans la clé ; corriger une recherche ne doit pas réutiliser un mauvais résultat.
- **Cache négatif temporaire retenu en principe** pour les véritables absences de résultat, selon contexte et règles du fournisseur ; ne jamais mémoriser définitivement Pas de résultat après un timeout, un refus d’authentification, une coupure ou un quota.
- **Pas de délais de cache inventés communs à tous :** respecter notamment en-têtes et plafond global Last.fm ; clarifier les clauses Discogs avant de promettre des tags durables ; ne pas inclure les images bloquées dans des caches de réponses ou des téléchargements automatiques.
- **Quotas et erreurs :** `429` → respecter `Retry-After` quand présent ; `503` ou panne → reprise espacée et bornée à définir ; `401/403` → indiquer le problème d’accès sans répéter en boucle. L’état local reste utilisable, aucune rotation de clés pour contourner les limites et aucun recours payant.
- **Budget d’attente borné à définir :** une recherche lente ne bloque ni la lecture ni l’accès aux autres résultats. Ne pas promettre un temps total avant des essais ultérieurement autorisés sur téléphone et services effectivement disponibles.

### Rappels documentaires, pas paramètres validés de NovaListen

| Source | Cadre à prendre en compte |
| --- | --- |
| MusicBrainz | Lecture publique sans clé, User-Agent pertinent, au plus une requête/seconde ; données de base CC0 et données supplémentaires sous conditions distinctes. |
| Cover Art Archive | Recherche d’images par édition ou groupe ; disponibilité et droits d’images distincts des métadonnées MusicBrainz ; aucune garantie d’accès illimité. |
| fanart.tv | Clé gratuite selon accès Project/Personal ; délais sur nouvelles images et éventuels quotas ; pas de VIP choisi, droits d’image à contrôler. |
| Last.fm | Clé, cadre non commercial, crédits, cache conforme aux en-têtes et plafond global publié de 100 MB ; images exclues de l’accord. |
| Discogs | User-Agent et authentification selon méthode ; limites publiées de 60 requêtes/minute authentifiées et 25 sans authentification, avec exceptions ; conservation et fraîcheur à clarifier pour le besoin durable. |
| TheAudioDB | Documentation v1 Free à 30 requêtes/minute, mais usage gratuit de l’application non confirmé ; v2 Premium exclue du périmètre gratuit. |
| Deezer | Accès de nouvelle application non confirmé, noms de titres/artistes conservables selon FAQ, pas de cache d’images ; pas de quota illimité déduit de sa gratuité annoncée. |
| iTunes Search API | Requêtes publiques sans clé, environ 20 appels/minute sous réserve d’évolution ; pochettes promotionnelles, pas licence générale pour les fichiers locaux. |

## 8. Validation utilisateur et sauvegarde

1. Montrer les différences avec les valeurs locales et la provenance **par champ**, les éventuelles contradictions et les restrictions d’usage pertinentes. L’emplacement et le détail de l’interface restent à valider.
2. Laisser choisir les champs et l’image autorisés ; conserver les corrections précédentes, même lorsqu’une API est en désaccord. Ne pas appliquer un vote de sources ni remplacer un album entier par la première référence de boutique.
3. La commande **Éditer** vise toujours les métadonnées du fichier audio d’origine, selon format et permissions. Les options de confirmation, protections, échecs d’écriture et éventuelles opérations en lot restent à définir ; une donnée seulement enregistrée dans NovaListen ne doit pas être annoncée comme écrite dans le fichier.
4. Distinguer cette écriture du choix d’affichage d’un groupe d’albums. Aucune modification du son, renommage, déplacement, effacement ou fusion des fichiers n’est ajouté par la cascade.
5. Conserver hors ligne les choix dont l’usage et la persistance sont compatibles, avec les crédits nécessaires. Si la conservation d’un contenu n’est pas permise ou clarifiée, ne pas la promettre ni rendre la lecture locale dépendante de son téléchargement.

Les fichiers de durée connue inférieure à 60 secondes restent exclus sans effacement ; ceux de durée inconnue restent en attente, hors compteurs disponibles et sans lecture. Aucune durée de catalogue ne change cet état ; le masquage et les règles de portée locale restent respectés.

## 9. Ce que cette validation ne décide pas

- **AcoustID/Chromaprint :** option distincte de reconnaissance par empreinte, pas étape automatique ajoutée par « toutes ces API ». Aucun calcul ou transfert d’empreinte n’est retenu ou exécuté par cette validation ; une empreinte serait une donnée dérivée envoyée, même sans envoyer l’audio.
- **Spotify :** pas ajouté à la cascade gratuite ; la contrainte Premium du propriétaire d’une nouvelle application en development mode, relevée dans le comparatif, ne correspond pas à une dépendance sans abonnement choisie ici.
- **Biographies, portraits et records :** fournisseurs, langues, droits et parcours restent à discuter séparément. Un endpoint artistique ou un compteur de popularité n’est pas une source universelle de records ni une sélection de biographie déjà faite.
- **Paroles, traduction, Fil Rouge :** aucune modification des choix déjà acquis ni des paramètres encore ouverts ; ne pas transposer l’ordre retenu ici à la cascade de paroles.
- **Activation et réalisation :** comptes, clés, acceptation de conditions, SDK, backend éventuel, cache exact, seuils, délais, écriture et essais restent distincts de la validation architecturale acquise et nécessitent des décisions ou autorisations ultérieures adaptées.

## 10. Sources et validation enregistrée

Le [comparatif des métadonnées et pochettes](16-metadonnees-et-pochettes.md) conserve les pages consultées, les limites et les nuances de droits. Références officielles utiles :

- MusicBrainz : https://musicbrainz.org/doc/MusicBrainz_API et https://musicbrainz.org/doc/About/Data_License.
- Cover Art Archive : https://musicbrainz.org/doc/Cover_Art_Archive et https://musicbrainz.org/doc/Cover_Art_Archive/API.
- fanart.tv : https://api.fanart.tv/.
- Last.fm : https://www.last.fm/api/tos et https://www.last.fm/api/show/track.getInfo.
- Discogs : https://www.discogs.com/developers et https://support.discogs.com/hc/en-us/articles/360009334593-API-Terms-of-Use.
- TheAudioDB : https://www.theaudiodb.com/free_music_api et https://www.theaudiodb.com/api_apply.php.
- Deezer : https://support.deezer.com/hc/en-gb/articles/360011538897-Deezer-FAQs-For-Developers et https://developers.deezer.com/termsofuse.
- iTunes Search : https://performance-partners.apple.com/search-api.

**Validation libre enregistrée — « Je valide », 17 novembre 2026 :** le pivot MusicBrainz, le recours de catalogue iTunes + Deezer en parallèle si admissibles, le routage spécialisé Discogs — directement après MusicBrainz si seule l’édition est incertaine —, la branche Last.fm, TheAudioDB conditionnel et la branche de pochettes CAA/fanart.tv puis TheAudioDB compatible sont retenus. Sont également acquis les données locales prioritaires, le choix manuel par champ, les résultats progressifs, l’arrêt des appels inutiles, la déduplication, la réutilisation et le cache autorisés, ainsi que **le maximum de deux requêtes simultanées au total pour cette cascade**, dans les limites de chaque fournisseur. Les huit API ne sont pas toutes appelées pour chaque morceau.

**Portée de l’accord :** choix d’architecture et rôles conditionnels, pas confirmation de gratuité, de droits ou de disponibilité, ni mise en service. Les réserves concernant notamment l’accès Deezer, l’usage gratuit de TheAudioDB, la persistance Discogs/Last.fm et les droits d’images restent applicables. Aucun compte, clé, condition contractuelle, transfert, test ou traitement de collection n’est autorisé par ce seul accord. Cette validation non numérotée ne répond pas rétroactivement aux questions restées ouvertes.

**Restent à préciser :** critères de correspondance et d’arrêt détaillés, marges de durée, scores éventuels, délais et reprises, durées de cache compatibles, boutique iTunes, traitement des genres et dates, interface des commandes, formats et protections d’écriture. Les biographies, portraits et records restent à discuter séparément ; l’ordre précis et la concurrence des paroles restent ouverts, et la traduction validée reste inchangée.

**Travail réalisé : conception et rédaction uniquement.** Aucun appel de recherche de catalogue, pochette ou reconnaissance sur la collection, aucune création de compte ou de clé, acceptation de conditions, dépense, installation, téléchargement de modèle, envoi d’audio ou d’empreinte, modification de fichier musical ou ajout de code d’application. Aucun essai de performance n’a été exécuté.
