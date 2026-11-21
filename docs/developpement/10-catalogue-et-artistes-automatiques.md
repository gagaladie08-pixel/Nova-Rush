# Sources 0.3.0 — catalogue, artistes automatiques et ouverture du lecteur

## État de livraison

Lot autorisé par **« Je valide tout maintenant code les »**, implémenté après 0.2.3. La mise à jour de l’identifiant historique reste bloquée : sa clé originale n’est toujours pas retrouvée et aucune clé n’est substituée. **Après choix explicite de l’installation parallèle, un APK distinct NovaListen Next 0.3.0 est désormais signé et remis**, sans remplacer 0.2.3. Nouvelle identité, sauvegarde privée confirmée, binaire exact et essais de coexistence Android 10/15 : voir [l’installation parallèle](11-installation-parallele-et-cle-next.md). Les résultats ci-dessous décrivent le lot source et ses vérifications antérieures ; ceux du binaire Next sont détaillés dans cette fiche dédiée.

Un workflow distinct compile Kotlin (application et tests Android), exécute les tests JVM et lint. Son mode Gradle interdit assemblage, signature, installation et production d’APK/AAB. Le pont de transfert refuse aussi tout APK dans ce mode. Une tentative contrôlée d’`assembleDebug` est rejetée avant exécution des tâches ; l’absence de binaire et de clé debug implicite est contrôlée. Cela **ne remplace pas les essais d’interface sur appareil**.

## Artistes et collaborations

- Une fiche par participant identifié, en conservant le crédit complet original du morceau. Une référence audio n’est comptée qu’une fois dans une fiche, mais reste accessible depuis chacun de ses participants.
- **Corrections explicites → crédits structurés locaux → séparation prudente du crédit textuel**. Aucun tag réécrit, nouveau fichier audio ou copie de référence dans une même fiche.
- Reconnaître `feat.` / `ft.` et les champs multi-valeurs. Une barre oblique non espacée exige un nom déjà identifié localement avant séparation ; une barre espacée est un indice de crédit multiple, pas une preuve permettant d’attribuer un seul MBID à tout le crédit. Ne pas découper aveuglément virgules ou esperluettes : AC/DC, Earth, Wind & Fire, Tyler, the Creator et groupes ambigus restent entiers. Les cas incertains restent corrigeables, sans identités inventées.
- Les MBID natifs distinguent les homonymes. Un crédit sans MBID peut rejoindre un nom local dont le MBID est unique pour l’index, **mais cet héritage n’est pas utilisé comme preuve réseau de l’identité du fichier écouté**.
- Menu d’un morceau → **Corriger les artistes de ce morceau** : un participant par ligne, MBID facultatif pour distinguer un homonyme. **Aller dans l’artiste** propose les participants lorsqu’ils sont plusieurs.
- Les anciens profils collectifs restent conservés. **Examiner les anciens profils de collaboration** permet de vérifier puis attribuer volontairement un texte ou une image ; aucune attribution arbitraire à un participant et aucune suppression des originaux.

## Biographies et portraits pendant l’écoute

- Seulement les artistes du morceau réellement joué ; aucun scan réseau de la collection, aucun audio, fichier, URI locale ou historique envoyé.
- Interrupteurs indépendants dans **Réglages → API et clés**. Changements rapides différés, réponses périmées ignorées, annulation, délais et cache de travail bornés ; rien au simple affichage de la bibliothèque ou sur une file préparée mais non lue.
- L’identité doit être attribuable **au fichier courant** : MBID natif de ses propres crédits, association explicitement choisie, ou concordance MusicBrainz de son titre, album, durée et crédit d’artiste. Un identifiant d’enregistrement natif peut fournir le lien, après contrôle de sa réponse. Une recherche tronquée ou plusieurs artistes compatibles reste ambiguë. **Un nom exact unique, même avec une fiche réelle, ne suffit pas.** Seuls ces champs nécessaires du morceau courant peuvent être transmis pour lever un doute.
- Vérifier ensuite le lien MusicBrainz → Wikidata et le **retour P434 vers ce même MBID**. La biographie automatique vient de l’introduction de l’article **français de cette entité**, avec contrôle du QID de la page. Anglais proposé manuellement, aucune traduction automatique de biographie.
- Portrait automatique limité à une image Commons **P18 de cette identité vérifiée**, sous licence ouverte reconnue, avec attribution exploitable, aperçu décodable d’au moins 96 px et contrôles de format/type. Ni reconnaissance faciale ni garantie de récence. Un portrait n’est pas retenu parce qu’il est premier, populaire ou simplement associé au nom.
- Sans portrait direct admissible, la cascade conditionnelle ci-dessous prépare des propositions **manuelles**. Une biographie résolue n’est pas abandonnée à cause d’un simple échec de téléchargement du portrait.
- Les choix manuels restent prioritaires, indépendamment pour les deux champs, **y compris un effacement volontaire**. Les transactions relisent les champs et les choix d’identité récents avant attribution. Une image téléchargée mais non retenue est supprimée. Une modification de texte ne restaure pas un ancien portrait, et réciproquement.
- Texte, portrait retenu et crédits sont persistés dans SQLite / stockage privé pour la réouverture hors ligne. Aucun chargement distant pour afficher ensuite l’image sauvegardée. Les aperçus de travail restent distincts, bornés dans le cache ; fermer un écran ne supprime pas les aperçus encore utiles à l’automatisme.

### Cascade des propositions photo

Ordre implémenté et testé : **Genius + TheAudioDB → Commons + Openverse → Discogs Artist → fanart.tv → recours manuels**. Chaque route porte son état et sa raison ; les routes inadmissibles sont sautées **avant tout appel**, sans se transformer en « aucune photo ».

Dans l’état des accès/droits établis pour NovaListen, **Commons et Openverse sont les branches appelables**. Genius, TheAudioDB, Discogs et fanart.tv restent enregistrés mais sautés faute d’admission compatible établie ; un jeton ou l’usage personnel ne fournit pas une licence de conservation. Il ne faut pas présenter ces quatre routes comme des collecteurs déjà activés. Fanart reste en dernière vague, pas banni ni prioritaire. Les rôles distincts de métadonnées/pochettes sont inchangés.

- Premier lot visant **six vignettes utiles**, au plus douze aperçus examinés ; une page/fiche par fournisseur admissible, pas de pagination automatique ni de six photos garanties.
- Résultats progressifs, déduplication des URL et comparaison visuelle locale indicative ; logos/visuels manifestement inadéquats écartés. Le contexte ne certifie pas un visage ou la date. Les autres propositions restent examinables.
- Arrêt de l’élargissement à la cible ou à la fin du budget initial. Choisir une photo ne doit pas attendre la sixième : ce travail est annulé pour l’écran, sans annuler aveuglément une requête partagée encore utile ailleurs.
- Cache de travail et requête initiale partagés entre recherches automatique/manuelle d’une même identité. Pas de réutilisation de la galerie d’un autre homonyme.
- **Plus de photos** reprend une seule page volontaire d’une source admise, sans refaire toute la cascade. Les limites techniques de cette interface sont cinq pages par source et soixante aperçus conservés dans une session, pas un plafond universel de six résultats.
- Last.fm reste un repère/une consultation externe volontaire, **pas une collecte de portraits rétablie**. Les sites officiels/kits presse et fanart.tv sont consultables volontairement dans le navigateur, sans extraction de médias. Import local autorisé depuis la fiche artiste.
- **Deux requêtes simultanées au total**, via la même instance HTTP, pour identité, biographies, listes, aperçus et image choisie, partagées avec les paroles et les recherches existantes. Les quotas et `Retry-After` restent appliqués ; pas de repli payant.
- Sujet, provenance et droits de l’image choisie doivent être confirmés. Openverse exige notamment l’examen de l’origine ; aucune photo de recherche élargie n’est attribuée automatiquement.

## Albums : éditions et classification

Le modèle conserve les éditions, une édition d’origine, une édition représentative et une liste musicale commune. Ce n’est **pas uniquement un dialogue de fusion manuelle de cartes**.

### Regroupements locaux

- Les deux relations demandées sont intégrées : **The Fame + The Fame Monster**, pour Lady Gaga ; **Future Nostalgia + Moonlight Edition**, pour Dua Lipa, y compris les libellés parenthésés usuels. Elles fonctionnent **même si les release-groups/types des éditions diffèrent**, sans API, sous garde d’artiste d’album cohérent. Les singles, reprises par un autre artiste et identités d’artiste contradictoires ne sont pas assimilés arbitrairement à ces albums.
- Les autres éditions portant le même release-group MusicBrainz local sont regroupées sous garde d’artiste d’album. Des identifiants contradictoires ne sont pas effacés par un simple nom identique, même si Android attribue le même identifiant d’album.
- Pour les noms génériques Original / Deluxe / Expanded, **le suffixe seul ne suffit pas** : il faut aussi une identité commune d’enregistrement admissible. Un nom Live ou Remixes n’est pas supprimé pour fabriquer une parenté.
- Sans preuve suffisante, **Regrouper les éditions** reste une correction locale explicite et réversible, pas l’unique méthode. La séparation mémorisée interdit une fusion automatique immédiatement réappliquée.
- Les tags d’artiste d’album et d’édition sont distincts de ceux de l’artiste du morceau. Deux releases natives différentes restent des éditions identifiables, même si Android leur donne le même album ID. Les clés des anciennes pochettes choisies restent préservées.

### Présentation, liste et lecture

- Index **deux colonnes** ; une carte commune, nom/pochette d’origine par défaut, autre édition représentative locale choisissable sans changer les tags. Les choix restent conservés lorsque de nouvelles éditions natives liées sont détectées.
- Pistes de l’édition d’origine dans leur ordre disque/piste, **puis ajouts** des autres éditions. Pour les relations demandées, l’original nommé est prioritaire ; sinon, années connues, indications d’édition puis départage stable servent de choix local initial, sans certifier un ordre officiel absent des métadonnées. L’utilisateur peut le corriger.
- La liste unique déduplique par identifiant d’enregistrement / ISRC cohérent avec titre, crédit et durée, ou confirmation explicite. **Titre identique seul ≠ même enregistrement**. Vrais live/remix/acoustiques/interprétations distincts restent présents. Aucune empreinte audio ou identité universelle inventée : sans preuve, les fichiers restent distincts jusqu’à confirmation.
- **Vérifier les enregistrements communs** permet de confirmer des copies, garder des variantes distinctes malgré de mauvais tags, ou rétablir la détection native. Tous les fichiers restent dans Bibliothèque ; pas de fusion arbitraire des favoris, paroles, historiques ou playlists des copies.
- Page : grande pochette, titre, artistes ouvrables, année connue / compte unique / durée, Tout lire / Aléatoire / menu, puis liste unique. Informations conserve le détail de chaque édition, l’ordre original, les pistes, crédits et noms de fichiers.
- Menu : Lire ensuite, Ajouter à une playlist, Informations sur l’album, Aller dans l’artiste, Choisir l’édition représentative si plusieurs ; correction de l’origine, séparation et type accessibles aussi.
- Tout lire / Aléatoire / toucher un titre utilisent la **liste unique locale admissible non masquée entière** de l’album, remplacent la file et rendent actif le titre touché. Une recherche dans la page ne réduit pas cette file. Lire ensuite / Ajouter à une playlist utilisent le même ensemble, pas toutes les copies des éditions. La simple navigation ne touche pas la lecture.
- Même regroupement dans l’index, les sorties de la fiche artiste et l’accès depuis un morceau.

### Types et tags

- **Album / EP / Mixtape** : tags de type disponibles, choix de la relation demandée ou choix explicite **Classer cette sortie**. Aucun type déduit du nombre de fichiers ou de la durée : un album incomplet n’est pas un single. Inconnu/contradictoire : **Type à préciser**.
- Singles et compilations n’entrent pas dans l’index normal ; leurs fichiers et accès depuis Artistes / Bibliothèque sont conservés. Des filtres permettent aussi de préciser les types inconnus.
- Lecture des tags **read-only, 256 Kio maximum par fichier, en-têtes compris** : ID3v2.3/2.4 simples et commentaires FLAC. Formats/tags non pris en charge restent sur les métadonnées Android et corrections locales. Aucun fichier déplacé, supprimé, renommé ou réécrit.

## Tri et démarrage

- Artistes et Albums : nom A→Z / Z→A, **plus de titres → moins de titres**, **moins de titres → plus de titres** ; égalités alphabétiques stables, choix indépendants mémorisés. Collaborations une fois par artiste ; albums sur leur liste unique admissible. Le tri ne masque rien et ne change pas la file.
- **Bibliothèque au démarrage**, Accueil accessible. La reprise/recréation conserve la navigation plutôt que de la réinitialiser systématiquement.
- Lancement volontaire d’une piste dans l’application → événement d’ouverture du **plein écran**, même pour la même piste rejouée. Passage naturel, Lire ensuite et métadonnées ne produisent pas cet événement. Fermer conserve musique/mini-player ; le jeton consommé est sauvegardé avec l’état de présentation pour éviter sa réouverture après recréation.
- Protection des commandes, gestes validés, paroles persistantes, priorités manuelles VO/VF et karaoké natif conservés. Aucune traduction automatique ajoutée.

## Données et vérifications

Migration SQLite **v3/v4 → v5 additive** : choix du catalogue séparés et protections artiste, sans effacer les anciennes tables. Les anciennes fiches étaient manuelles : leurs champs sont protégés, y compris une valeur vide potentiellement volontaire. **Autoriser le remplissage des champs vides**, dans le menu artiste, permet explicitement de réautoriser leur complétion sans toucher aux contenus manuels présents.

Tests ajoutés : crédits/homonymes, tris/comptes, relations d’éditions demandées et génériques, origine/représentante, variantes, tags bornés, lien d’identité au morceau et retours croisés, licences, vagues photo conditionnelles et arrêts, cache/coalescence/annulation, automatisme limité à l’écoute, sauvegardes manuelles par champ, migrations SQLite et réouverture sous Robolectric Android 9. Le modèle de présentation teste explicitement fermeture/recréation, changement naturel et demandes de lecture successives.

**Vérification finale des sources :** [44945580241](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44945580241), job `136972042148`, sources `5d1bc7465c1a8614cb1df6a7f1a70a18a592783f` — **réussie**.

| Contrôle | Résultat |
| --- | --- |
| Tests JVM | **240 réussis**, zéro échec, erreur ou test ignoré ; 17 suites |
| Dont catalogue / présentation / artistes auto / cascade | 29 / 6 / 17 / 13 tests réussis |
| Politique de signature Python | **19 tests synthétiques réussis**, sans clé réelle |
| Kotlin application + tests Android | Compilation réussie, **pas d’exécution instrumentée** |
| Lint | **41 avertissements, zéro erreur/fatal** |
| Assemblage en mode sources | Refus contrôlé avant exécution des tâches |
| Assemblage local sans clé, hors mode sources/CI | Refus contrôlé avant exécution des tâches |
| Binaire ou clé debug implicite | Aucun APK/AAB ou keystore généré ; contrôle réussi |
| Workflow de livraison APK `44945580251` | Sauté, aucune signature ou publication |

Les rapports XML/lint téléchargés et vérifiés sont conservés dans `artifacts/ci-44945580241/` (ignoré par Git), avec le résumé, l’état des étapes du job et le journal de compilation. SHA-256 de l’archive de rapports : `3aaa253908e9060dedcdc33897c365c2a82cd94596fc76cb9de419f077bc8ccd`. Les vérifications précédentes `44944128510` (228 tests) et `44945029394` (236 tests) restent distinctes ; le dernier résultat couvre aussi les corrections de cache/reprise photo et les protections du lecteur.

Les tests d’interface sont adaptés et complétés pour Bibliothèque initiale, fermeture/recréation, Lire ensuite, passage naturel et tris, mais **ne sont pas exécutés sur émulateur dans ce mode sans APK**. Aucun essai sur le téléphone de l’utilisateur ni appel à une API musicale réelle avec ses clés. Aucune image ou musique des captures dans les fixtures. Les anciens résultats instrumentés 0.2.3 ne sont pas attribués à 0.3.0.
