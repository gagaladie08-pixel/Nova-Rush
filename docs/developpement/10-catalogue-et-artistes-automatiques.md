# Sources 0.3.0 — catalogue, artistes automatiques et ouverture du lecteur

## État de livraison

Implémentation du lot validé après 0.2.3. **Vérifications des nouvelles sources en cours ; aucun nouvel APK livré.** La signature exigée reste celle de 0.2.3. Le keystore original n’est toujours pas retrouvé : la chaîne de livraison d’un APK reste bloquée, sans clé de remplacement.

Un workflow distinct vérifie uniquement les sources : compilation Kotlin (application et tests Android), tests JVM et lint. Son mode Gradle interdit assemblage, signature, installation et génération d’APK/AAB. Le pont de transfert refuse aussi tout APK dans ce mode. Une tentative contrôlée d’`assembleDebug` doit être rejetée avant exécution des tâches. Il ne remplace pas les essais sur appareil.

## Artistes et collaborations

- Indexer les participants séparément en conservant le crédit complet du morceau. Une même référence audio n’est comptée qu’une fois dans une fiche, mais reste accessible depuis chaque participant.
- Priorité aux crédits structurés locaux, puis aux corrections de l’utilisateur, sans réécrire les tags.
- Reconnaître les marqueurs `feat.` / `ft.` et les crédits multi-valeurs ; traiter prudemment les barres obliques. Ne pas découper automatiquement toutes les virgules ou esperluettes : AC/DC, Earth, Wind & Fire et Tyler, the Creator restent des noms entiers.
- Un MBID natif distingue les homonymes ; un nom seul n’est rattaché à ce MBID que lorsque l’association est unique dans la collection. Aucune fusion approximative de deux noms.
- Menu d’un morceau → **Corriger les artistes de ce morceau** : un participant par ligne, MBID facultatif pour distinguer un homonyme. **Aller dans l’artiste** propose les participants lorsqu’ils sont plusieurs.
- Les anciens profils collectifs restent conservés. Depuis une fiche concernée, **Examiner les anciens profils de collaboration** permet de vérifier puis attribuer volontairement un texte ou une image ; aucune attribution arbitraire ni suppression.

## Biographies et portraits pendant l’écoute

- Seulement les artistes identifiés du morceau réellement joué ; aucun scan réseau de la collection, aucun audio, fichier ou historique envoyé.
- Interrupteurs indépendants dans **Réglages → API et clés** ; chargement différé pour les changements rapides, annulation/ignorance des réponses périmées, délais et cache négatif bornés.
- Réutiliser les données sauvegardées. Les choix manuels de texte et de portrait sont prioritaires indépendamment, y compris un effacement volontaire. La transaction finale relit les choix récents avant toute attribution ; une image téléchargée mais non retenue est supprimée.
- Identité : MBID natif/choisi, ou correspondance exacte unique MusicBrainz ; lien MusicBrainz vers Wikidata, puis **retour P434 vers ce même MBID**. Une correspondance de nom ou le premier résultat ne suffisent pas.
- Biographie : introduction de l’article **français lié à l’entité**, avec contrôle du QID de la page et conservation des crédits/licence. Pas de traduction automatique. En l’absence de français, la recherche anglaise reste proposée manuellement dans Biographie en ligne.
- Portrait automatique : seulement une image Commons **P18 de cette identité vérifiée**, sous licence ouverte reconnue, avec attribution exploitable, aperçu décodable d’au moins 96 px et contrôles de format. Pas de reconnaissance faciale ni de garantie que la photo soit récente. Une incohérence ou un doute bloque l’attribution automatique.
- Sans portrait direct admissible : un premier lot Commons + Openverse borné reste à choisir manuellement ; il est réutilisable depuis Photos en ligne. Aucun choix automatique du premier résultat élargi. Sources inadmissibles et droits non établis restent sautés ; aucun abonnement, nouveau compte ou secret ajouté.
- Même instance HTTP et **deux requêtes au total** partagées avec les paroles, l’identité, les listes et les images. Les versions anglaises et les commandes de remplacement restent volontaires.
- Texte, image retenue et crédits sont persistés dans SQLite / stockage privé pour la réouverture hors ligne. Les aperçus de travail restent distincts des portraits conservés.

## Albums : éditions et classification

Ce n’est pas seulement une concaténation de cartes : le modèle conserve les éditions, une édition d’origine, une édition représentative et une liste musicale commune.

- Grouper les éditions portant un même identifiant local MusicBrainz de release-group ; sinon, sélectionner explicitement plusieurs cartes puis **Regrouper les éditions** et confirmer l’édition d’origine. Ni le seul titre d’album ni un suffixe Deluxe ne suffisent à une fusion silencieuse. Des noms différents peuvent être réunis explicitement, conformément à la conception.
- Une seule carte ; nom/pochette de l’édition d’origine par défaut. Sans choix explicite, l’ordre local initial utilise les années connues puis les indications d’édition et un départage stable ; cela ne certifie pas une édition officielle d’origine. Le menu permet de corriger ce choix.
- **Liste unique : pistes de l’édition d’origine dans leur ordre, puis ajouts des autres éditions.** La déduplication native exige un identifiant d’enregistrement ou ISRC cohérent avec titre, crédit et durée. Un même titre sans preuve reste distinct : aucune reconnaissance audio inventée. Live, remix et autres variantes ne sont pas supprimés par simple ressemblance.
- **Vérifier les enregistrements communs** permet de confirmer les copies d’un même enregistrement, de garder des fichiers distincts malgré de mauvais tags ou de rétablir la détection native. Les fichiers restent tous présents dans Bibliothèque ; pas de fusion des favoris/historiques ni de réécriture de playlists.
- Menu d’album : Lire ensuite, Ajouter à une playlist, Informations, Aller dans l’artiste, Choisir l’édition représentative ; choix de l’ordre d’origine et séparation réversible également accessibles. La séparation est mémorisée pour éviter une fusion native immédiatement réappliquée.
- Classification lue dans les tags de type fiables disponibles ou choisie explicitement avec **Classer cette sortie**. **Aucun type déduit du nombre de fichiers ni de la durée.** Types inconnus ou contradictoires : **Type à préciser**.
- Index Albums : Albums / EP / Mixtapes et types à préciser ; filtres correspondants. Singles et compilations ne sont pas supprimés, restent consultables depuis Bibliothèque et Artistes. La page artiste présente les types et participations sans discographie distante.
- Le même regroupement est réutilisé dans les cartes, pages et accès depuis un morceau. Lire / Lire ensuite / Ajouter à une playlist utilisent la liste commune admissible et non masquée, pas l’ensemble des copies des éditions.
- Lecteur de tags **strictement en lecture seule et borné à 256 Kio** : ID3v2.3/2.4 simples et commentaires FLAC. Formats/tags non pris en charge restent sur les métadonnées Android et les corrections locales ; pas de classification devinée. Aucun fichier déplacé, supprimé ou modifié.

## Tri et démarrage

- Artistes et Albums : nom A→Z / Z→A, **plus de titres → moins de titres**, **moins de titres → plus de titres**. Départage alphabétique stable ; choix indépendants mémorisés. Compteurs sur les résultats locaux admissibles, sans réintroduire les fichiers masqués ni ceux de moins de 60 secondes.
- **Bibliothèque au démarrage**, Accueil toujours accessible. Une simple reprise d’activité ne réinitialise pas la navigation en cours.
- Un lancement volontaire de lecture dans l’application émet une demande d’ouverture du **lecteur plein écran**, y compris si le même morceau était déjà actif. Un changement naturel de piste, un ajout Lire ensuite ou un simple changement de métadonnées ne l’émettent pas. Fermer le lecteur conserve la musique dans le mini-player ; une recréation ne rejoue pas une demande déjà consommée.
- Gestes validés, protection des commandes plein écran, paroles persistantes et karaoké natif inchangés.

## Données et tests

Migration SQLite **v4 → v5 additive** : choix du catalogue séparés, protections manuelles des champs artiste. Aucun effacement des anciennes tables.

Tests ajoutés : crédits et homonymes, tris et compteurs, familles d’éditions, ordre, types inconnus, variantes et corrections, lecture bornée des tags, identité croisée et droits, automatisme limité à l’écoute/cancellation/cache, migrations et réouverture SQLite sous Robolectric. Les tests d’interface sont adaptés et complétés pour Bibliothèque initiale, ouverture plein écran, transition naturelle sans réouverture et tris ; leur compilation ne vaut pas leur exécution sur appareil.

Les résultats définitifs des vérifications seront ajoutés ici après leur exécution. Aucune API musicale réelle, clé utilisateur, image ou musique des captures n’est utilisée dans les fixtures.
