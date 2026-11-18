# NovaListen 0.2 — API facultatives et identifiants locaux

## État et portée

Cette itération répond à la demande d’intégrer les API sans clé et de saisir les autres identifiants **dans l’application**, gratuitement. Elle ne crée aucun compte, n’active aucun abonnement et ne contient aucune clé de fournisseur. Les 21 fiches de conception restent la référence ; cette première interface n’en réalise pas toutes les fonctions avancées.

La version des sources est **0.2.0 / code 2**. Le résultat d’une compilation et les essais sur émulateur doivent être vérifiés dans ses rapports ; l’ancienne validation de 0.1.0 ne valide pas ce code. Le compte rendu de livraison 0.2 sera ajouté une fois les tests terminés. Les documents 01–03 décrivent historiquement 0.1.

## Où trouver les fonctions

- **Réglages → API et clés** : renseigner, remplacer, supprimer un identifiant, ou tous les effacer. Le site officiel de chaque service est accessible. Une valeur sauvegardée apparaît « non testée » : son enregistrement ne déclenche aucune requête.
- **Menu d’un morceau → Rechercher les paroles en ligne**, ou **lecteur → Paroles → Rechercher** : revoir titre, artiste et album avant la recherche. Les résultats affichent source et type de synchronisation ; prévisualiser, vérifier les droits puis choisir. Ni le premier résultat ni un échec ne remplacent la VO locale.
- **Lecteur → Paroles → Traduire**, ou éditeur local : travaille sur la VO déjà sauvegardée. L’import TXT/LRC et le collage restent possibles sans API.
- **Menu d’un morceau → Comparer les métadonnées en ligne** : références de MusicBrainz, comparaison volontaire d’une autre source. Consultation seulement, sans écriture de tags ni faux enregistrement dans l’audio.
- **Page artiste → Biographie en ligne / Photos en ligne** : identifier le sujet, lire et choisir. Le français est prioritaire ; demander l’anglais reste une action séparée. Pas de traduction automatique des biographies.
- **Page album / menu d’un morceau → Chercher une pochette** : choisir une édition MusicBrainz, puis une proposition CAA ; recours de groupe et fanart volontaires. Changement de l’affichage dans NovaListen seulement, pas de fusion d’albums ni d’édition des fichiers.

Les contenus choisis et leurs crédits sont sauvegardés hors ligne. Les recherches non choisies sont des données de travail, pas un catalogue ajouté à la bibliothèque.

## Sources intégrées et limites

| Fonction | Disponible par commande | Limites importantes |
| --- | --- | --- |
| Paroles, noyau | lrc.red + LRCLIB + LiriQo | lrc.red nécessite un courriel de contact. LiriQo est un agrégateur : origine amont indiquée lorsqu’elle existe. Aucun réglage définitif des questions de cascade restées ouvertes n’est imposé par cette interface. |
| Compléments de paroles | Better Lyrics public, Textyl, lyrics.ovh | Une commande par complément. Better : aucun jeton privé d’extension extrait, 401 distingué de 404, quotas respectés. Textyl/ovh ne certifient ni la version ni les droits ; pas de faux consensus entre agrégateurs. |
| Catalogue | MusicBrainz ; iTunes Search avec boutique choisie | Identité/version à comparer ; pas de téléchargement permanent des pochettes de boutique. La boutique ne déduit pas la localisation. |
| Identifiants configurables | Last.fm, fanart.tv, Discogs, Genius, Azure | Last.fm : tags de consultation, aucun scrobbling/portrait. Discogs et Genius : références de consultation, pas de collecte de paroles/portraits. |
| Biographies / identité | Wikipédia FR, EN volontaire, MusicBrainz, Wikidata | Introduction et texte développé distingués. Article limité à 30 000 caractères et crédit CC BY-SA conservé. Faits/records non automatiquement certifiés par Wikidata. |
| Photos | Réutilisation de l’association/portrait ; P18 volontaire ; Commons + Openverse ; fanart ultime complément manuel | Identité, sujet, licence et date à vérifier. Les likes et dates d’ajout ne sont pas des dates de prise de vue. Un résultat d’Openverse n’est pas une certification de droits. |
| Pochettes | CAA par édition ; CAA/fanart de groupe sur demande | Édition vs groupe distingués. Approbation CAA ≠ licence générale. Conservation seulement après vérification explicite des droits par l’utilisateur. |
| Traduction | Google Translate via ML Kit sur l’appareil ; Azure standard F0 explicitement demandé | Pas de repli distant automatique. Aucun pack téléchargé par défaut. Voir ci-dessous. |

**Non activés** : TheAudioDB et Deezer faute d’accès gratuit/admission confirmés ; TheAudioDB dispose seulement d’un emplacement de clé en réserve, sans appel. Lyrica reste en réserve. Connecteurs directs privés KuGou/QQ/NetEase/Migu/Kuwo, collecte de portraits Genius/TheAudioDB/Discogs, faits/records vérifiés et cascade entièrement adaptative ne sont pas simulés. Posséder une clé et avoir un usage personnel ne règlent pas tous les droits d’image/texte.

### Identifiants à saisir, jamais dans le chat ou le dépôt

- **Last.fm** : clé API publique de lecture, pas Shared Secret ni session.
- **fanart.tv** : clé Project ; clé Personal facultative. L’API v3.2 documente aussi l’utilisation Personal seule. Pas de VIP requis ni proposé comme repli.
- **Discogs** : Personal Access Token. Ce jeton peut avoir des pouvoirs sur le compte ; rester prudent même si NovaListen ne fait que des lectures.
- **Genius** : Client Access Token, pas Client Secret ; pas une API autorisant toutes les paroles complètes.
- **Azure** : clé de ressource Text Translation **standard F0** et région si nécessaire. La clé ne prouve pas le forfait : vérifier celui-ci dans Azure, ne pas utiliser une ressource facturable.
- **lrc.red** : courriel de contact demandé par ses conditions ; pas une clé. Il est transmis seulement dans l’identification de NovaListen auprès de lrc.red. Aucun courriel de projet n’a été inventé ou prérempli.

## Réseau, budget et photos

Un seul `RemoteHttp` applicatif dessert recherches et octets d’images. Son ordonnanceur impose **au maximum deux requêtes en vol**, y compris les aperçus, avec espacement par fournisseur (notamment MusicBrainz) et regroupement Wikimedia. C’est un budget de réalisation conservateur ; il ne transforme pas les limites de cascade validées en une nouvelle règle universelle de conception.

- Pas d’appels à l’ouverture de l’application, d’une page, ni lors de la saisie/sauvegarde d’une clé. Le scan MediaStore ne dépend d’aucun service distant.
- HTTPS uniquement, DNS public vérifié, pas de cookies, cache HTTP disque, journal des requêtes ou retry automatique. Les en-têtes et erreurs des fournisseurs ne sont pas exposés en clair dans les messages utilisateur.
- Réponses JSON/texte limitées à 2 Mo ; aperçus à 2 Mo et images choisies à 8 Mo. Les téléchargements sont annulables ; le rendu est borné en pixels.
- Redirections API refusées, sauf le `307` documenté de CAA vers un `mbid-…/index.json` d’Internet Archive, suivi avec une nouvelle requête sans les en-têtes privés. Les redirections d’images sont bornées et revérifiées. Pas de contournement d’un accès protégé.
- `429` / `503` suspendent la source selon `Retry-After` ; un quota, un refus ou une réponse invalide ne sont pas présentés comme « aucun résultat ». Aucun basculement payant.
- Photos : un premier lot de **six métadonnées par source admissible**, puis **au plus douze aperçus** pour viser **six vignettes réellement décodables d’au moins 96 px**, distinctes par URL et par une empreinte visuelle locale. Arrêt à la cible, réutilisation des aperçus/P18 déjà chargés, pas de pagination automatique. Ce n’est ni six appels, ni une garantie de six bons portraits. L’empreinte compare composition/couleurs approximatives, pas des identités de visage.
- Les doublons probables et propositions à vérifier restent examinables manuellement, sans remplir artificiellement le compteur de six. Pages supplémentaires sur commande, cinq pages par source dans cette première interface. fanart n’est jamais la première vague photo.
- Les aperçus sont en cache privé temporaire (réutilisation jusqu’à 24 h au plus ; nettoyage à la fermeture du parcours). Seule l’image choisie est sauvegardée durablement en JPEG redimensionné, avec source/auteur/licence et mention de l’adaptation. Le fichier distant original n’est pas modifié.

## Traduction : quatre actions séparées

1. **Choisir la langue source** EN/ES/KO et reconnaître les droits et diagnostics pour ML Kit. Aucune détection/traduction de biographie implicite.
2. **Télécharger les packs** par le bouton dédié, Wi-Fi coché par défaut. Environ 30 Mo par modèle, volume réel variable. Si les packs sont absents, Traduire s’arrête ; pas de téléchargement silencieux ni fallback Azure. Leur suppression est séparée des textes sauvegardés.
3. **Translate with Google · sur l’appareil** utilise Google Translate via ML Kit, sans envoyer la VO à Azure. Le SDK Google déclare néanmoins des diagnostics d’appareil, d’application, de performances et d’usage après activation. Le provider d’initialisation automatique est retiré du manifeste ; « local » ne signifie pas zéro communication du SDK. Voir [les diagnostics ML Kit](https://developers.google.com/ml-kit/android-data-disclosure), [les conditions de traduction ML Kit](https://developers.google.com/ml-kit/language/translation/translation-terms) et [Google Cloud Translation](https://cloud.google.com/translate).
4. **Azure**, si désiré, demande à nouveau confirmation de la ressource F0 et demande explicite d’envoi de cette VO. Les lignes et la langue sont envoyées à Microsoft, pas l’audio, le titre ni l’historique. Consentement d’envoi consommé à chaque tentative ; pas de reprise automatique. L’application ne peut pas vérifier le forfait d’une clé.

Les instants LRC existants, balises et lignes vides sont conservés ; les répétitions identiques ne sont traduites qu’une fois. Limite technique initiale : 20 000 caractères et 500 lignes, refus au-delà sans troncature. Les mots français n’héritent pas d’un faux minutage phonétique. La proposition reste en prévisualisation : sauvegarder la VF demande un choix séparé, laisse la VO intacte et vérifie qu’elle n’a pas changé. Changer la VO invalide l’ancienne VF non corrigée.

Le badge officiel **powered by Google Translate**, récupéré sans modification depuis le ZIP officiel Google, est inclus localement et affiché à côté des résultats Google (aperçu, player et éditeur). Les crédits et le disclaimer restent accessibles dans l’application. Une VF adaptée manuellement est indiquée comme telle. Aucun logo Google recréé ou issu d’un site tiers n’est utilisé.

## Stockage et confidentialité

- Identifiants chiffrés **AES-256-GCM**, IV aléatoire et AAD propre à chaque fournisseur, clé dans **Android Keystore**. Pas de repli en clair si Keystore échoue ; valeur illisible signalée sans effacement silencieux.
- Saisie masquée et non `rememberSaveable`, pas de retour automatique de l’ancien secret ; `FLAG_SECURE` et dialogue sécurisé. La saisie est vidée au passage en arrière-plan et après enregistrement. Aucun mécanisme ne rend un appareil compromis ou un clavier tiers inviolable.
- Effacement individuel/global sur le téléphone ; ne révoque pas une clé chez le fournisseur. Musiques et choix sauvegardés ne sont pas supprimés.
- Sauvegarde/transfert Android désactivés ; pas de secrets dans BuildConfig, ressources, GitHub Actions ou APK. La clé est envoyée uniquement à son fournisseur, pas à GitHub ni à NovaListen.
- SQLite passe de v1 à v2 par migration **sans réinitialiser la bibliothèque** : sources VO/VF/portraits, pochettes d’affichage et associations MusicBrainz. Les URI, favoris, playlists, événements et textes existants sont conservés.
- Aucun envoi automatique d’audio, de collection complète, d’historique ou de fichiers de tags. Les thèmes et autorisations de suppression restent indépendants des accords API.

## Vérifications et limites

Tests prévus dans cette itération : contrats JSON synthétiques, unités de temps, TTML/XXE, 404 vs refus/quota, redirections sûres, limites de taille, filtrage d’URL, plan LRC et traduction, plafond/annulation de l’ordonnanceur, budget photo et déduplication. Tests Android : coffre/Keystore, persistance et suppression, saisie non restaurée, migration SQLite, erreurs de sauvegarde, cache d’aperçus et manifeste fusionné. **Aucun secret réel n’est utilisé dans ces tests**.

Ces tests ne prouvent pas la disponibilité du service sur le téléphone, l’admission d’un compte, l’exactitude d’un texte/portrait, ni les droits du fournisseur. Les appels réels de traduction et packs, les quotas d’un compte et les essais sur appareil physique restent à effectuer volontairement. Listening Party, YouTube, Fil Rouge, modèles intelligents, tags audio originaux et autres fonctions non réalisées restent hors de cette livraison.

### Contrats relus pour les adaptateurs

- [lrc.red](https://lrc.red/api) : contact et attribution obligatoires, ISRC et formats annoncés.
- [LRCLIB](https://lrclib.net/docs) ; [LiriQo](https://github.com/AlFarrizi-Studio/LiriQo) : millisecondes des lignes vs secondes de la requête.
- [Better Lyrics](https://docs.betterlyrics.org/reference/get-lyrics) : quatre paramètres, cache public et distinction 401/404/429/503.
- [Textyl](https://textyl.co/) : schéma `seconds`/`lyrics` et 404 décrits par la documentation indexée ; disponibilité non éprouvée par ce prototype.
- [CAA](https://musicbrainz.org/doc/Cover_Art_Archive/API) : redirection JSON normale et droits distincts.
- [fanart.tv v3.2](https://api.fanart.tv/) : `api-key`/`client-key`, albums **tableau** avec `release_group_id` (objet dans v3/v3.1), aperçus dérivés par `/preview/`.
- [Attribution Google](https://cloud.google.com/translate/attribution) ; provenance et SHA-256 du badge dans `app/src/main/assets/third-party/Google-Translate-Attribution.txt`.
