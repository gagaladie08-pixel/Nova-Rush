# NovaListen — Cascade adaptative des photos d’artistes : architecture validée

> **Statut : architecture validée, 17 novembre 2026, sans code ni activation.** Après la demande **« Fait maintenant une cascade hyper efficace en ajoutant tout ses api pour ce qui est des autorisations ne n'est que pour mon usage personnel »**, la proposition est présentée et l’utilisateur confirme **« Je valide »**. Sont retenus pour les **photos d’artistes** : les rôles conditionnels, l’ordre des vagues et les mécanismes d’efficacité décrits ici, dont deux requêtes simultanées au total, une cible initiale de six vignettes et une découverte initiale bornée par fournisseur. Cette validation libre ne crée pas d’accès ou de droits fournisseur, n’active aucun service et ne tranche pas les détails explicitement laissés ouverts.
> **Fondations acquises :** [portraits ronds, propositions multiples, choix manuel et sauvegarde dans Artistes](05-artistes.md), [API gratuites et usage personnel](13-api-et-enrichissements.md), [Wikipédia/Wikidata et langue des biographies retenus](18-biographies-portraits-et-records.md), [comparatif multisource et limites documentées](19-photos-artistes-autres-sources.md). Last.fm doit figurer dans le périmètre ; fanart.tv ne retrouve pas une priorité présumée après le retour utilisateur sur les mauvaises images et photos anciennes.
> **Accords antérieurs inchangés :** [paroles](14-cascade-api-paroles.md), [traduction des paroles](15-traduction-francaise-paroles.md), [métadonnées/pochettes](17-cascade-api-metadonnees-et-pochettes.md). L’architecture photo ne fusionne pas ces cascades et n’ouvre pas une discographie distante.

## 1. Réponse de principe : toutes les pistes ont une place, pas toutes un appel

**Local → identité commune → sources spécialisées admissibles → recherche élargie → compléments ciblés → secours et recherche manuelle.** Montrer les résultats au fur et à mesure et arrêter les appels devenus inutiles ; jamais choisir une photo à la place de l’utilisateur.

L’efficacité visée est une réduction des requêtes, de l’attente et des mauvaises correspondances. **Aucun benchmark, délai garanti ou taux de réussite n’est établi.** Une cascade plus longue n’est pas automatiquement plus rapide ou plus fiable.

L’usage **personnel, sans diffusion publique prévue**, est confirmé. Il évite d’inventer des besoins commerciaux, mais ne rétablit pas des portraits retirés d’une API, une clé indisponible ou un droit de conservation refusé. Il n’est pas nécessaire de demander une autorisation individuelle supplémentaire lorsqu’une licence applicable permet déjà l’usage envisagé, sous ses conditions. À l’inverse, l’usage personnel n’est pas traité comme une licence universelle.

**Principe retenu :** vérifier l’éligibilité d’une source à partir de sa configuration et des conditions connues, pas demander une autorisation à chaque vignette. Une source non utilisable pour ce besoin est sautée sans requête d’essai ni attente ; elle reste recensée, avec sa raison, dans le registre ci-dessous. Aucun abonnement ou recours payant automatique.

## 2. Registre complet des sources discutées

Une ligne du registre n’est pas un adaptateur implémenté ou activé. Pour chaque fournisseur, distinguer : identité connue ou ambiguë, accès configuré ou manquant, gratuité applicable, affichage autorisé, aperçus autorisés, sauvegarde durable compatible, quota/panne et véritable absence de résultat. **L’éligibilité de la source ne prouve pas celle de chaque image.**

| Source | Rôle retenu pour la conception | Place / état dans notre besoin de portraits hors ligne |
| --- | --- | --- |
| **MusicBrainz** | Réutiliser le MBID d’artiste et les relations pertinentes ; recherche d’identité ciblée seulement si nécessaire. | En amont, pas une banque de portraits. Identité et licences de données distinctes des photos. |
| **Wikidata** | Réutiliser l’entité, ses liens et une piste Commons P18 lorsqu’elle existe. | En amont ; CC0 des données ne donne pas la licence des images liées. |
| **Wikipédia** | Réutiliser les liens de médias pertinents d’un article déjà identifié pour la biographie. | Pas d’appel de biographie supplémentaire juste pour une photo, ni de copie automatique de tous ses médias. Les images hébergées localement n’ont pas toutes les conditions Commons. |
| **Last.fm** | Source explicitement demandée ; réutiliser une identité, un MBID ou un lien d’artiste autorisés déjà obtenus ; complément textuel ciblé uniquement si nécessaire à une ambiguïté et admissible. Consultation volontaire du site retenue en principe comme option distincte ; commande à préciser. | **Pas d’appel de collecte de portraits dans le cadre consulté** : retrait expliqué par le support et images exclues de l’accord API. Ne pas appeler `artist.getInfo` en espérant obtenir une galerie. Aucun scraping ou accès caché ajouté. |
| **Genius** | Piste spécialisée de portrait/en-tête, à rattacher à la bonne identité. | Vague 1 seulement si accès gratuit et usages d’image compatibles effectivement établis ; conservation non présumée permise. |
| **TheAudioDB** | Visuels d’artiste, dont Artist Thumb, s’ils sont disponibles et adaptés. | Vague 1 conditionnelle ; gratuité d’application non établie par les pages contradictoires. Pas de Premium ou de clé de test présumée autorisée en production. |
| **Wikimedia Commons** | Pistes ciblées de l’artiste, puis autres candidats utiles avec leurs conditions de réutilisation. | Vague 2, ou réutilisation immédiate d’un résultat autorisé déjà reçu. Pas de téléchargement de catégorie entière. |
| **Openverse** | Recherche élargie de photos annoncées sous licences ouvertes. | Vague 2 si l’accès sans frais est compatible ; vérifier l’origine, ses conditions et la licence, dédupliquer avec Commons/Flickr. Pas de certification de l’identité ou de la date. |
| **Discogs** | Images de la ressource Artist, non celles d’une sortie prise pour un portrait. | Vague 3 ciblée si authentification, droits et conservation/fraîcheur compatibles sont établis. Restricted Data, pas une banque CC0 par association. |
| **fanart.tv** | Ultime complément artistique possible, notamment `artistthumb`. | Vague 4 seulement si encore utile et compatible. **Pas prioritaire**, ni réputé exact/récent par MBID, likes ou date d’ajout. |
| **Deezer** | Réutiliser les repères textuels déjà disponibles et permis pour l’identité, si utile. | Pas de nouvelle collecte d’images pour sauvegarde : FAQ refusant leur cache ; accès de nouvelle application non confirmé. Une éventuelle consultation externe ne vaut pas import. |
| **TIDAL** | Piste recensée de catalogue et d’images d’artiste. | Non admise comme source de portraits permanents dans le cadre examiné : conservation indéfinie refusée et cache local limité ; accès/coût non établis ici. Pas de requête automatique de cette cascade. |
| **Spotify** | Piste de catalogue recensée. | Non admise dans le socle sans abonnement d’une nouvelle application en development mode : Premium du propriétaire requis. Aucun compte Premium existant supposé, aucun appel de remplacement caché. |
| **Flickr en direct** | Piste de photos recensée, distincte de résultats indexés par Openverse. | Pas de nouvelle intégration directe dans le cadre gratuit actuel : demande de clé réservée aux abonnés Pro selon l’aide. Les images trouvées via un autre index restent soumises à leurs conditions d’origine, sans contournement. |
| **Bandsintown** | Piste recensée de fiche/photo d’artiste dans le schéma historique. | Non admise sur la base de l’aide actuelle qui n’accepte pas les demandes pour projets personnels/éducatifs. Pas de clé publique ou empruntée. |
| **iTunes Search API** | Réutiliser une référence textuelle autorisée déjà obtenue et réellement utile à l’identité. | Pas de recherche d’illustrations de boutique en les assimilant à des portraits. Rôle de métadonnées déjà retenu conservé. |
| **Cover Art Archive** | Rôle acquis de pochettes d’édition/groupe dans sa cascade propre. | **Pas de branche portrait** : une pochette n’est pas une photo certaine de l’artiste. |
| **Sites officiels, labels et kits presse** | Recherche ciblée volontaire de photos dont la réutilisation est compatible, avec contexte éventuellement daté. | Recours volontaire retenu en principe, pas d’API universelle gratuite ou de collecteur de pages décidé. Officiel ne veut pas dire récent ou librement réutilisable. |
| **Import local autorisé** | Choisir une image personnelle ou obtenue légalement, avec crédits si requis. | Recours manuel volontaire retenu en principe, pas une permission de réimporter des images dont le stockage est refusé. Commande et interface à concevoir. |

Les restrictions et preuves sont documentées dans les [comparatifs de métadonnées/pochettes](16-metadonnees-et-pochettes.md) et de [photos d’artistes](19-photos-artistes-autres-sources.md). Une source peut être admise pour du texte et non pour une photo : ne pas retirer tous ses rôles ni rendre toute l’application dépendante d’elle.

## 3. Préparation : éviter les appels avant même la première vague

### P0 — Photo existante et contexte local

- Afficher immédiatement la photo déjà choisie et conservée de manière compatible, même hors ligne. Une ouverture de page ou une lecture ne déclenche pas à elle seule une nouvelle recherche.
- Chercher seulement après une commande volontaire de recherche/changement. La portée initiale, les éventuels lots et le libellé de commande restent à définir ; aucune recherche de toute la collection n’est choisie.
- Réutiliser les identités, résultats et images autorisés déjà reçus par les autres enrichissements. Un résultat pertinent disponible n’attend pas sa vague nominale pour être présenté ; ce n’est pas une nouvelle priorité imposée à son fournisseur.
- Une image déjà téléchargée sans droits compatibles ne devient pas admissible du seul fait qu’elle est locale. Conserver les choix autorisés sans effacement arbitraire lors d’une recherche.

### P1 — Une seule identité d’artiste cohérente

```text
Association locale choisie et corrections → priorité
         ↓ si contexte insuffisant
MusicBrainz artiste / Wikidata pertinents, en réutilisant les liens connus
         ↓ si ambiguïté persiste
Comparaison ciblée des noms, alias, personne/groupe/projet et contexte
Last.fm / repères Deezer ou iTunes seulement si utiles et admissibles
         ↓
Identité retenue ou candidats distincts à faire choisir
```

Un nom exact, un MBID ou une correction automatique ne prouve pas que chaque photo associée représente le bon sujet. Ne pas intervertir identifiants d’artiste, d’enregistrement, d’édition ou de groupe d’éditions ; ne pas fusionner artiste solo, membre et groupe.

Réutiliser le résultat d’identité déjà satisfaisant : Last.fm, Deezer ou iTunes n’ont **pas un appel obligé** à cette étape. Si une nouvelle information réellement discriminante justifie de préciser la recherche, limiter la préparation à **un retour enrichi vers le pivot d’identité par recherche**, sans boucle entre fournisseurs. Aucun nombre d’appels total de préparation n’est garanti par cette règle.

Si l’identité reste ambiguë, présenter les candidats séparément plutôt qu’une seule galerie mêlant plusieurs personnes. Aucun crédit musical, historique ou regroupement local n’est réécrit pour résoudre une recherche photo.

### P2 — Filtrer les sources avant la mise en file

Retenir seulement celles dont l’accès gratuit et l’opération envisagée sont compatibles. Réutiliser cet état d’éligibilité ; ne pas appeler chaque API au démarrage ou à chaque affichage pour revérifier une restriction connue. Une réévaluation documentaire/configuration reste possible ultérieurement sans inventer une date de déblocage.

Ne pas confondre **non configuré**, **usage non admissible**, **quota**, **panne**, **aucune photo**, **photo non pertinente** et **licence manquante**. Un fournisseur exclu n’épuise pas le budget d’attente du lecteur.

## 4. Cascade photo validée : quatre vagues, franchies seulement si utile

```text
Photo choisie / résultats autorisés existants → afficher et préserver
                         ↓ recherche volontaire
Identité commune + éligibilité connue des sources
                         ↓
VAGUE 1 — Genius + TheAudioDB
          uniquement ceux qui sont admissibles ; sinon saut immédiat
                         ↓ si le premier lot reste insuffisant
VAGUE 2 — Commons ciblé + Openverse
          résultats progressifs ; doublons et droits d’origine vérifiés
                         ↓ si davantage de choix reste utile
VAGUE 3 — Discogs Artist ciblé, si admissible
                         ↓ si encore nécessaire
VAGUE 4 — fanart.tv, ultime complément admissible
                         ↓
Recherche officielle / kit presse / import local autorisé : recours volontaires
                         ↓
Choix manuel → sauvegarde locale de l’image autorisée et de ses crédits
```

**Last.fm est bien inclus dans l’architecture**, en préparation/réutilisation et comme consultation séparée retenue en principe, pas omis ni compté comme collecteur de portraits rétabli. Les autres sources recensées mais actuellement non admissibles ont une route désactivée avec raison ; elles ne sont pas ajoutées artificiellement à la file réseau.

### Pourquoi cet ordre, sans classement de qualité inventé

- La première vague essaie des références musicales ciblées si les identifiants et les usages le permettent, sans prétendre que Genius ou TheAudioDB fournissent des portraits plus récents, plus fiables ou plus rapides. **Leur éligibilité photo n’est pas établie aujourd’hui pour NovaListen** ; elles peuvent donc être entièrement sautées, sans requête d’essai.
- La deuxième vague élargit le choix ; Commons peut être ciblé par un lien d’artiste déjà connu, Openverse par le nom et le contexte retenus. Un agrégateur et une source d’origine ne comptent pas comme deux photographies indépendantes.
- Discogs n’est consulté que pour des choix supplémentaires réellement utiles et compatibles. Ne pas demander toute la discographie pour récupérer la liste d’images de l’artiste.
- fanart.tv reste en fin de recherche pour respecter le retour utilisateur ; son inclusion n’annule pas le risque signalé de mauvais sujet ou de photo ancienne.
- Les recours manuels restent volontaires, jamais un robot de collecte automatique de sites ou de réseaux sociaux. Ouvrir une page externe ne sauvegarde pas ses médias dans NovaListen.

**Effet pratique dans l’état documentaire actuel :** ne pas présenter Genius/TheAudioDB/Discogs comme trois services photo déjà utilisables. Si leur éligibilité n’est pas établie, la recherche passe directement aux sources admissibles restantes, notamment Commons/Openverse lorsque les accès et les images conviennent. Aucun écran n’attend un appel Last.fm de portraits impossible dans le cadre décrit.

## 5. Un premier lot utile, pas une collecte exhaustive

**Réglages retenus par « Je valide », sans performances mesurées :**

- **Jusqu’à six vignettes distinctes et utiles pour le premier lot**, récupérées progressivement. C’est une cible de présentation et de limitation des téléchargements d’aperçus, pas une garantie de six photos, un nombre d’appels ni la limite de tout le catalogue de résultats.
- Un candidat peut compter dans ce lot si son contexte d’identité n’est pas contradictoire, si ce n’est pas un visuel générique ou un type manifestement inadéquat et si l’aperçu/l’usage visé sont compatibles. Ces vérifications ne certifient pas le visage ; la sélection humaine reste nécessaire.
- **Borne de découverte initiale retenue : une page de recherche ou une fiche Artist par fournisseur admissible**, selon sa route. Si la recherche ne retourne que des identifiants, une consultation ciblée de la fiche correspondante peut compléter ce résultat ; pas de boucle sur tous les artistes trouvés. Pas de pagination ni de reformulations automatiques en série pour atteindre artificiellement six photos. Cette borne ne vaut pas nombre total de requêtes : les métadonnées nécessaires aux droits, aperçus et image choisie sont distinctes, limitées aux candidats utiles et soumises au plafond de concurrence.
- Lorsque ce premier lot est atteint sans problème bloquant pour le besoin demandé, **suspendre l’élargissement automatique** ; ne pas finir toutes les vagues pour le principe. Si le lot reste incomplet, poursuivre les seules sources admissibles jusqu’à épuisement de ce budget initial, annulation ou budget d’attente à préciser. Un lot de moins de six photos reste un résultat valable, pas un motif de collecte sans fin.
- Une poursuite volontaire, par une commande de type **Plus de photos**, est retenue en principe pour reprendre les sources non explorées ou la page suivante utile d’une source choisie, sans refaire toutes les requêtes. Chaque reprise reste bornée : pas d’exploration automatique de toutes les pages. Le libellé exact, l’emplacement, la taille des lots suivants et une éventuelle commande Toutes les sources admissibles restent à définir.
- Si une photo est choisie ou la recherche annulée avant la fin, annuler si possible les travaux inutiles et ignorer leurs réponses tardives pour l’application de l’image. La sélection ne doit pas attendre le sixième aperçu.

Aucune photo n’est appliquée parce qu’elle arrive la première, qu’elle a le plus de likes ou que le lot est complet. Aucun seuil chiffré de confiance, de récence ou de ressemblance n’est adopté.

## 6. Qualité : identité d’abord, date réelle plutôt que date d’ajout

| Vérification / mécanisme | Effet recherché | Limite |
| --- | --- | --- |
| Sujet et contexte | Séparer homonymes, groupe/membre/projet solo et images manifestement incohérentes. | MBID et nom ne certifient pas chaque photo ; pas de reconnaissance faciale ajoutée. |
| Type d’image | Distinguer portrait/groupe, scène, logo, pochette, affiche et visuel de remplacement. | Une photo de scène peut être un choix voulu ; pas d’exclusion arbitraire de toutes les photos non studio. |
| Provenance et droits | Prévoir source, page d’origine, auteur/crédits et licence/conditions nécessaires. | Un champ copyright inconnu, une URL publique ou le choix utilisateur ne valent pas autorisation générale. |
| Doublons | Regrouper le même contenu et ses tailles quand la correspondance est établie. | Ne pas fusionner arbitrairement des photos différentes ou utiliser un service de vision distant. |
| Date du portrait | Afficher prise de vue/événement réellement sourcé, ou **Date inconnue**. | Publication/ajout chez le fournisseur et récupération dans NovaListen sont deux autres dates. |
| Choix de période | Comparer les photos datées et permettre de rechercher une autre époque si souhaité. | Aucun seuil d’ancienneté ou classement absolu des dates inconnues n’est fixé ; une photo ancienne choisie volontairement reste légitime. |

Ne pas classer une ancienne photo comme récente parce qu’elle a été ajoutée hier à un catalogue. La photo d’une page officielle peut aussi être ancienne. Une absence de date n’est ni une preuve de récence ni une preuve d’ancienneté.

Le tri/filtre par date fiable, la présentation groupée ou mélangée des fournisseurs et une commande **Ce n’est pas cet artiste** restent proposés. La mémorisation des rejets et leur portée sont à préciser ; ne pas inventer de liste noire définitive ou de signalement automatique au fournisseur.

## 7. Mécanismes d’efficacité retenus

### Ordonnancement

- **Plafond retenu pour ce parcours photo : au plus deux requêtes réseau simultanées au total**, en comptant préparation d’identité, listes de résultats, métadonnées d’image, aperçus et image choisie. Pas deux par fournisseur, vague ou artiste. Deux fournisseurs de la même vague peuvent avancer en parallèle, ou un seul pendant le chargement d’un aperçu.
- Le chiffre deux est validé spécifiquement pour ce parcours photo, **pas une extension automatique du plafond validé pour les métadonnées/pochettes**, ni un optimum mesuré. L’articulation simultanée des différentes cascades reste à concevoir ; la concurrence des paroles ne change pas.
- Réguler chaque fournisseur à travers tous ses usages dans NovaListen, pas une nouvelle enveloppe par cascade. Les limites Wikimedia ne se multiplient pas en changeant de projet ; deux places libres ne permettent pas de dépasser un débit fournisseur.
- Donner la priorité aux travaux de l’artiste actuellement demandé ; dans cette recherche, **image explicitement choisie avant aperçus visibles, puis découverte de candidats supplémentaires**. La sélection n’attend pas qu’une file d’aperçus se vide. Les éventuelles tâches en lot ou en arrière-plan ne sont pas décidées ici ; elles ne doivent pas bloquer la lecture locale.

### Réutilisation, déduplication et cache

- Mutualiser par identité d’artiste choisie, type de recherche et contexte. Un artiste de vingt morceaux ne nécessite pas vingt recherches photo.
- Partager une requête identique déjà en cours et ses réponses réutilisables, dans les limites applicables ; ne pas relancer automatiquement une source parce qu’elle apparaît aussi dans une autre fiche.
- Réutiliser les résultats encore compatibles avant un nouvel appel. Les vignettes d’une même image et les résultats Commons repris par Openverse ne deviennent pas plusieurs téléchargements utiles.
- **Ne récupérer que les petits aperçus utiles et autorisés**, puis une version adaptée de l’image sélectionnée. La cible de six vignettes ne limite pas forcément le nombre d’objets renvoyés par une API ; ne pas prétendre maîtriser une réponse distante entière si son endpoint ne le permet pas.
- Séparer réponses de travail, aperçus temporaires et contenu sélectionné dont la conservation est compatible. Respecter plafonds/en-têtes et conditions propres aux contenus ; pas de durée de cache uniforme inventée.
- Un cache négatif temporaire ne concerne que les absences de résultat valables pour cette identité et cette requête ; panne, quota, accès interdit ou licence non établie ne signifient pas définitivement Aucune photo.
- Après correction d’identité ou de recherche, ne pas réutiliser comme certain le lot d’un autre homonyme. Préserver néanmoins le choix utilisateur autorisé jusqu’à une décision volontaire de remplacement.

### Arrêts et erreurs

- Fournisseur non configuré ou usage non admissible : saut immédiat avec état compréhensible, pas d’appels d’attente ou de réessais automatiques en boucle.
- `429` : respecter `Retry-After` lorsqu’il existe et différer ce fournisseur sans immobiliser les autres résultats ; pas de rotation de clés ou bascule payante.
- `401/403` : signaler l’accès/configuration, sans répéter la même demande indéfiniment. Un refus n’est pas mémorisé comme une absence de portrait.
- Panne, timeout ou `503` : respecter `Retry-After` lorsqu’il est fourni, puis prévoir une reprise espacée et bornée à définir ; ne pas annoncer un délai total avant des essais ultérieurement autorisés. Aucun timeout numérique ou taux de reprise n’est validé ici.
- Annulation ou sélection : arrêter les travaux devenus inutiles quand possible ; une opération partagée encore utile à une autre recherche ne doit pas être annulée aveuglément. Ignorer les réponses tardives pour toute application automatique.

## 8. Choix, sauvegarde et fonctionnement hors ligne

1. Présenter les propositions reçues, leur provenance, dates connues et incertitudes, sans prétendre un portrait certifié. Disposition précise et détails des crédits restent à définir.
2. Laisser choisir ; vérifier que le contenu sélectionné et son usage sont compatibles avant de le déclarer sauvegardable. Une source ou un candidat seulement consultable ne remplit pas un objectif de portrait persistant hors ligne.
3. Récupérer uniquement la version utile choisie, conserver les crédits et conditions nécessaires hors ligne et garder l’ancienne sélection jusqu’à réussite effective de la sauvegarde. Une erreur de téléchargement n’efface pas le portrait actuel.
4. Ne pas charger ensuite des ressources distantes à l’insu de l’utilisateur pour afficher la photo sauvegardée. Les liens d’origine restent une consultation distincte nécessitant une connexion.
5. Permettre le remplacement volontaire acquis, sans modifier les pochettes des titres, les tags des fichiers audio, les crédits musicaux, le Top 10 personnel ou la discographie locale. Le cadrage rond reste acquis ; détails de recadrage et conservation des sources restent à choisir selon leurs droits.
6. Si aucune nouvelle photo pertinente et conservable n’est trouvée, garder l’existant ou l’état sans portrait. Proposer les recours manuels volontaires retenus en principe, avec une mise en œuvre à définir, pas une mauvaise personne ou une pochette imposée.

Pas de téléchargement ou d’analyse d’audio, d’historique ou de collection entière. Une recherche volontaire future peut transmettre les identifiants et termes d’artiste nécessaires ; ni leur envoi maintenant ni un service de vision, une empreinte faciale, un backend ou une reconnaissance d’image ne sont autorisés par cette conception.

## 9. Validation enregistrée, détails ouverts et sources

**Acquis avant cette proposition :** gratuit uniquement, usage personnel réaffirmé, compte développeur gratuit acceptable en principe ; portraits ronds, plusieurs propositions lorsque disponibles, choix manuel, persistance compatible et remplacement volontaire ; inclusion demandée de Last.fm et retrait de la priorité recommandée à fanart.tv. Biographies Wikipédia, Wikidata et français prioritaire/anglais proposé restent acquis hors photos, sans traduction automatique de biographies.

**Validation libre acquise — photos d’artistes, 17 novembre 2026 :** après présentation de cette cascade, **« Je valide »** retient le registre complet avec sources appelées seulement si admissibles ; la préparation d’identité commune et la réutilisation conditionnelle Last.fm/repères de catalogue ; l’ordre Genius + TheAudioDB → Commons + Openverse → Discogs → fanart.tv → recours manuels volontaires ; **deux requêtes simultanées au total pour le parcours photo**, une **cible initiale de six vignettes** et une **découverte initiale bornée à une page ou fiche par fournisseur admissible**, avec éventuelle consultation ciblée si seuls des identifiants sont renvoyés ; les résultats progressifs, arrêts, annulations utiles, déduplication/cache compatibles et principes de contrôle d’identité, de type, de provenance et de dates. La poursuite volontaire de type Plus de photos et les recours manuels sont retenus en principe, sans interface imposée. Cet ordre est un choix de conception, pas une mesure de qualité ou une permission fournisseur.

**Restent ouverts :** admission effective des fournisseurs, clés et formalités, détails de licence/cadrage/crédits, libellés/emplacements et interface des commandes, portée initiale et éventuels lots d’artistes, critères/seuils détaillés d’identité et d’arrêt, tri/filtres de dates et rejets proposés, durées de cache, budgets d’attente, délais et reprises, articulation entre cascades, routes exactes, intégration et essais. **Les rôles conditionnels, l’ordre, le plafond photo de deux requêtes simultanées, la cible de six vignettes et la borne de découverte initiale sont acquis, pas à redemander comme choix ouverts.** Les questions numérotées laissées sans réponse restent sans réponse ; cet accord non numéroté ne les remplit pas rétroactivement. Il ne rétablit pas l’API de portraits Last.fm, ne vaut pas création de compte/clé, acceptation de conditions, recherche ou envoi sur la collection, ni activation ou code. Les détails des autres cascades restent distincts. Cette validation photo ne choisissait aucun thème. Le [système de quinze univers visuels et l’option personnalisée](21-systeme-de-themes-visuels.md) est fourni séparément ; l’utilisateur y précise ensuite Cyber Nova par défaut et les six catégories personnalisables. Ces choix visuels ne modifient pas cette cascade et ne commencent pas le code.

Les preuves complètes et dates de consultation sont dans le [comparatif photo](19-photos-artistes-autres-sources.md). Références principales déjà examinées :

- Last.fm : https://www.last.fm/api/tos ; https://www.last.fm/api/show/artist.getInfo ; annonce de l’équipe https://support.last.fm/t/api-announcement-usage-of-audio-audiovisual-images-or-artwork/202.
- Genius : https://docs.genius.com/ ; https://genius.com/static/terms.
- TheAudioDB : https://www.theaudiodb.com/free_music_api ; https://www.theaudiodb.com/api_apply.php ; https://www.theaudiodb.com/docs_artwork.php.
- Commons/Wikidata : https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia ; https://www.mediawiki.org/wiki/API:Imageinfo ; https://www.wikidata.org/wiki/Wikidata:Data_access.
- Openverse : https://docs.openverse.org/terms_of_service.html ; https://docs.openverse.org/api/reference/made_with_ov.html.
- Discogs : https://www.discogs.com/developers/resources/database/artist.html ; https://support.discogs.com/hc/en-us/articles/360009334593-API-Terms-of-Use.
- fanart.tv : https://api.fanart.tv/.
- Autres routes : https://support.deezer.com/hc/en-gb/articles/360011538897-Deezer-FAQs-For-Developers ; https://developer.tidal.com/documentation/guidelines-developer-terms-1_0 ; https://developer.spotify.com/documentation/web-api/concepts/quota-modes ; https://www.flickrhelp.com/hc/en-us/articles/4404070036884-Flickr-API ; https://help.artists.bandsintown.com/en/articles/7053475-what-is-the-bandsintown-api.

**Travail réalisé : conception, rédaction et enregistrement de la validation uniquement à partir des sources déjà consultées.** Aucun nouveau test de service, recherche d’artiste de la collection, téléchargement de portrait pour l’application, compte/client/clé/jeton créé, condition fournisseur acceptée, dépense, abonnement, installation, modèle téléchargé, transfert d’audio/empreinte/historique/photo, modification musicale ou code d’application. Aucune couverture, vitesse ou optimisation sur téléphone n’a été mesurée.
