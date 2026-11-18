# NovaListen 0.1 — Première phase de code

## Mandat et portée

À la suite de « Codons l’appli et utiliser GitHub pour compiler et me donner le fichier apk », le dépôt contient désormais une **première implémentation Android**, distincte des documents de conception antérieurs. Leur mention « sans implémentation » décrit l’étape où ils ont été écrits ; le tableau du README et ce document sont l’état d’avancement logiciel.

La demande autorise le développement et la compilation sur la branche de session. Elle ne résout pas rétroactivement les questions passées sans réponse, n’autorise ni abonnement ni service payant et ne prouve pas l’accès/droit de conservation d’une API. Aucun fournisseur de musique ou d’enrichissement n’est contacté par cette version de l’application. Les polices libres et dépendances de développement sont téléchargées pour la compilation ; aucun modèle d’IA n’est récupéré.

## Architecture de la première itération

- Kotlin, Jetpack Compose Material 3, application Android native, minSdk 26 / target et compileSdk 35.
- MediaStore et ContentObserver pour les URI audio et les ajouts indexés par Android.
- ExoPlayer / MediaSessionService Media3 : même lecteur entre mini-player, plein écran et commandes système. Une lecture active peut continuer en arrière-plan ; retirer l’application des tâches n’arrête le service que s’il n’est plus en lecture. Le maintien d’écran du plein écran est retiré à la pause de l’activité.
- SQLiteOpenHelper, stockage privé pour favoris, masquages, références des playlists, événements qualifiés, paroles VO/FR et biographies. Pas de purge automatique d’historique.
- SharedPreferences pour thème/profil et tris, opérations de bibliothèque/base sur Dispatchers.IO, annulation/debounce du scan. La file est un instantané, sans ajout automatique de nouveaux fichiers.
- Pas de permission Internet. Notifications facultatives sur Android 13+, autorisation audio distincte ; suppression avec le parcours MediaStore Android et bilan non global en cas de refus/échec.
- Aucun fichier audio fourni en démonstration dans l’APK de l’application. Un WAV original simple est synthétisé uniquement par les tests d’émulateur, jamais téléchargé depuis une collection.

## Choix techniques provisoires, pas nouvelles réponses de conception

- Navigation à sept entrées dans une barre inférieure défilante, accessible au mini-player. Elle pourra évoluer après essai sur téléphone.
- La date d’ajout de cette itération provient de `MediaStore.DATE_ADDED`, explicitement nommée dans les informations ; l’année musicale reste un champ séparé. Pas d’affirmation d’une date d’arrivée historique exacte.
- Pour les familles dont les rôles n’étaient pas attribués, la première sert provisoirement aux titres et la seconde au texte dans le prototype ; ce n’est pas une décision utilisateur ajoutée à la fiche 21.
- Les angles coupés de Pink Venom utilisent un masque relatif de 12 % dans cette interprétation initiale ; aucune profondeur en dp n’a été inventée comme valeur reçue. Survivor utilise une transition à ressort sans durée fixe ; Chaos Born garde une durée variable de 100 à 400 ms. Les premières animations sont décoratives, pas une mesure du signal audio. Aucun microphone demandé. Bad Angel reste sombre.
- Le mode de décors désactivables est un premier garde-fou de réalisation, pas une approbation rétroactive des recommandations. Les animations Compose respectent aussi le réglage système ; les décors sont suspendus hors premier plan. Les palettes d’origine ne sont pas corrigées : les petits textes utilisent T plutôt que T2 dans les thèmes clairs, dont les réserves de contraste restent documentées.
- Les styles d’icônes sont une première famille de pictogrammes identifiables avec ornementation propre, pas l’intégralité des futurs glyphes sur mesure (notamment Progress). Les effets/transitions de chaque univers demandent encore une validation visuelle sur appareil.
- Un profil personnalisé non destructif, aperçu avant Enregistrer et appliquer ; palette 8 rôles, 25 polices, familles d’ornements, arrondis/coupe, ambiance/durée, style/épaisseur/halo des courbes. Les courbes actuelles sont des **aperçus décoratifs étiquetés**, pas des statistiques. Paramètres plus riches, animation/fill détaillés des courbes, profils et échanges restent à développer.
- Le Top 10 d’artiste utilise le cumul des événements enregistrés dans cette version, signalé « période provisoire v0.1 ». La période finale, les égalités et la contribution statistique des titres masqués/hors filtre ne sont pas décidées par ce code ; aucune page de bilan ne prétend les résoudre.
- Les artistes sont regroupés prudemment par crédit textuel normalisé, sans découpage automatique de collaborations. Les albums restent les groupes natifs de MediaStore ; fusion et identité audio ne sont pas simulées avec de simples titres égaux. Sorties de type inconnu affichées comme locales sans inventer une qualification de single.
- Les écrans avancés manquants restent explicitement signalés ; aucune intelligente n’est convertie silencieusement en classique et aucun résultat d’analyse n’est inventé.
- Les fichiers de durée inconnue ne sont pas jouables ; les détails de réévaluation d’une file active restent hors de cette première implémentation. Pas de purge des anciennes références par filtre de durée.

## Tests et limites de vérification

Le workflow exécute l’intégrité des 120 codes, licences/hachages des 25 polices, XML et absence d’Internet ; les tests unitaires des filtres, recherche, références, file, dates, compteur de passage et LRC ; Android lint ; l’assemblage d’un APK debug universel ; puis trois tests d’instrumentation sur un émulateur Android 10 : navigation aux sept onglets et persistance d’un thème, import de WAV de test 65 s/12 s avec filtre et lecture réelle, présence des six catégories de l’éditeur.

Une exécution de CI peut échouer : **seul le statut réel de l’exécution et ses rapports constituent le résultat**, pas la présence de ce fichier ou du workflow. Les captures de test sont conservées avec les rapports quand le runner les recueille. Ne pas confondre assemblage, installation sur émulateur et essai sur le téléphone de l’utilisateur.

Restent nécessaires les essais sur téléphone (notamment Android récent et variantes constructeur), formats audio réels, autorisations et suppressions partielles, gros catalogues, interruptions/casques, longévité des services, fiabilité temporelle sur appareils et accessibilité. Aucun résultat de performance ou certification n’est annoncé.

## Livraison et signature

L’APK de CI est installable et signé avec la clé debug du runner ; ce n’est pas une publication Play Store. Le nom d’application est NovaListen, identifiant `com.novalisten.app`. Les artefacts Actions ont une durée de rétention de 30 jours pour l’APK et 14 pour les rapports. Le fichier APK et son SHA-256 sont récupérables séparément. Pas de clé de signature de production commise au dépôt ; stratégie de signature stable à définir avant des mises à jour durables. La désinstallation efface les enrichissements/paramètres privés : l’utilisateur doit en être averti, sans promesse d’export encore absent.
