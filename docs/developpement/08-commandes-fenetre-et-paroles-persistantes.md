# NovaListen 0.2.3 — commandes dans la fenêtre réelle et paroles persistantes

## Deux corrections, sans élargissement visuel

La capture du téléphone après 0.2.2 montre des commandes encore coupées. Les tests alors réussis ne validaient donc pas ce téléphone. Le problème visuel retenu ici est **uniquement la visibilité complète des commandes**, avec une marge confortable au-dessus de la navigation Android, sur les pages pochette et paroles. Ni les polices, ni les retours de mots, ni le contraste ne sont redessinés.

Le second problème est distinct : les paroles chargées automatiquement n'étaient gardées qu'en mémoire. Fermer le processus pouvait perdre à la fois leur texte et leurs temps natifs.

## Commandes : même fenêtre que l'activité

- Le lecteur plein écran devient un panneau superposé à l'application **dans la même fenêtre d'activité et avec la même hauteur réellement mesurée**. Ce n'est plus un second `Dialog` edge-to-edge.
- Cela évite de dépendre d'une seconde fenêtre pouvant annoncer une hauteur ou des insets différents. **La cause exacte sur le téléphone n'a pas été démontrée** ; il ne s'agit pas d'une prétendue absence initiale d'insets.
- `PlayerFrame` conserve sa mesure prioritaire des commandes, les marges latérales et les **20 dp supplémentaires en bas**. Ses protections Compose sont réunies avec les insets de la **fenêtre Android racine**, rapportés à la position réelle de la vue. Sur l'émulateur Android 15, les premières mesures après recréation ont révélé une protection de geste insuffisante malgré les insets Compose ; cette seconde mesure sert de garde-fou. Elle est observée avant dessin, sans remplacer ni consommer les listeners d'AndroidX, et sans marge arbitraire propre à un téléphone.
- L'arrière-plan est masqué aux sémantiques d'accessibilité pendant l'ouverture ; le panneau est annoncé comme lecteur plein écran. Retour Android et maintien de l'écran au premier plan sont conservés.
- Lecteur : gauche → paroles, droite → pochette, sans boucle. Bibliothèque : gauche → Lire ensuite, droite → Informations. Mini-player, gestes verticaux immersifs et réordonnancement ne sont pas inversés.

## Paroles automatiques : conservation locale durable

SQLite passe de **v3 à v4** par ajout d'une table séparée `automatic_lyrics` :

- **Texte VO, source/crédit et payload de temps natifs sont enregistrés ensemble dans une transaction**. Le payload reste lié au SHA-256 du texte exact ; il n'est pas reconstruit proportionnellement.
- La clé locale combine l'identifiant de piste et une empreinte des titre/artiste/album/durée normalisés. Un identifiant MediaStore réutilisé avec des métadonnées différentes ne récupère pas automatiquement l'ancien texte. Ce n'est pas une empreinte audio.
- Après réouverture, une version automatique présente sur disque se lit **sans recherche automatique ni découverte des fournisseurs**, même en pause, hors ligne ou avec le chargement automatique désactivé. L'initialisation attend toujours le premier snapshot SQLite.
- Une première réponse texte/ligne peut être enregistrée immédiatement **sans annuler les autres fournisseurs de la recherche déjà en cours**. Une réponse ultérieure avec de vrais mots natifs peut encore l'améliorer. Ensuite, la version conservée évite une nouvelle recherche automatique au retour sur le morceau.
- Une VO choisie/importée/modifiée manuellement garde la priorité. Sa sauvegarde retire le résultat automatique dans la même transaction ; les écritures automatiques refusent de la remplacer. Une VF locale isolée reste dans sa table et se combine à la VO automatique sans perdre son crédit.
- Une réponse moins détaillée ne dégrade pas des mots natifs conservés pour la même identité. Les pistes masquées et les résultats tardifs d'un ancien morceau ne sont pas sauvegardés par le parcours automatique.
- Si l'écriture échoue, le résultat peut rester visible pour la session mais **n'est pas annoncé comme conservé**. Ni une erreur réseau ni une absence de résultat ne devient une sauvegarde vide.

**Trois points → Rechercher d'autres paroles** reste disponible pour remplacer volontairement la version. Modifier/importer, Traduire et Source et synchronisation restent dans ce menu. Une version par ligne reste par ligne ; aucun faux temps mot à mot n'est fabriqué. Le centrage, le scroll manuel et le recentrage sont inchangés.

Le cache mémoire borné reste un complément de session pour les succès non sauvegardés et les absences/erreurs ; il n'est plus le seul stockage des succès automatiques. Les migrations v1/v2 restent additives. Playlists, favoris, historique, clés, images, fichiers audio et accords API ne sont pas réinitialisés. Aucun pack ou traduction automatique n'est ajouté, et seules les métadonnées du morceau réellement écouté sont concernées lorsqu'aucune version n'existe.

## Vérifications prévues et état réel

**Validation finale en cours, aucun APK livré pour l'instant.** La [première exécution 44927888844](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44927888844), sources `daf8773349f6b68e2c3b120bf4279b244a147a8d`, a compilé, réussi **145 tests unitaires et 30 tests Android 10**, mais a détecté **2 échecs sur 30 sur Android 15 gestuel**, tous deux sur la marge physique après recréation de l'activité. Les nouveaux tests ont donc réellement détecté un cas que la matrice synthétique seule ne couvrait pas. La protection par les insets de la fenêtre racine a été ajoutée ensuite ; elle n'est pas encore validée. Le binaire de cette exécution en échec n'est pas remis. Les tests de persistance/minutage/migration ont réussi ; le second mode Android 15 n'a pas été exécuté dans ce premier run. Lint : 34 avertissements, sans erreur/fatal ni baseline.

Les résultats historiques de 0.2.2 ne constituent pas la validation de 0.2.3. Les contrôles de structure `scripts/verify-project.py`, de syntaxe des scripts et `git diff --check` ont réussi localement ; le poste ne dispose pas de JDK/SDK Android.

Nouveaux tests :

- Unitaires : attente de la sauvegarde atomique, amélioration ligne → mots malgré l'émission du snapshot disque, nouvelle instance sans découverte/réseau, option désactivée/pause, priorité manuelle et VF, identité, réponses tardives, erreur disque et absence de résultat.
- SQLite sur Android : fermeture du helper puis nouvelle instance et nouveau moteur, restitution exacte des temps et du crédit sans requête, écriture interrompue, refus de dégradation/écrasement, conservation de VF et migration v3 → v4 sans perte des autres tables. Les migrations v1/v2 restent testées.
- Lecteur complet : bornes **non découpées** de chaque commande et du curseur, converties en coordonnées de fenêtre physique, comparées à la taille Android réelle et à ses insets de navigation/statut/encoche/gestes ; pas seulement à un parent Compose. Coordonnées et insets sont lus dans un même snapshot du thread UI. Les mesures sont archivées ; un échec prend aussi une capture. Les deux pages et une vraie rotation/recréation d'activité sur émulateur sont exercées.
- Interface de paroles : sauvegarde native préalable, vidage de l'état du moteur, recréation de l'activité, texte/minutage toujours disponibles et compteur HTTP inchangé.
- Workflow : Android 10 puis Android 15 en navigation gestuelle et à trois boutons. Les journaux, XML et captures de chaque passage sont archivés séparément avant le passage suivant. Les tests existants des swipes, du centrage et des pixels du karaoké sont conservés.

Les fixtures sont synthétiques : pas de clé réelle, de musique de l'utilisateur, de recherche musicale réelle ni de pack ML Kit. Les émulateurs ne certifient pas le rendu sur le téléphone de l'utilisateur ou toutes les surcouches Android. Une validation visuelle sur ce téléphone reste nécessaire.

## APK et installation

Version prévue **0.2.3 / code 5**. Aucun nouvel APK validé n'est encore remis à ce stade ; le lien 0.2.2 reste historique.

La signature n'est pas corrigée par ces deux changements. Le workflow utilise toujours une clé debug créée par le runner : **ne pas supposer la compatibilité de mise à jour avec les APK précédents**. Le certificat du nouveau binaire devra être contrôlé. Ne pas désinstaller aveuglément : les données privées seraient effacées (favoris, playlists, historique, paroles, images, clés, réglages), sans export intégré. La musique du stockage partagé est distincte.

## Essai ciblé sur téléphone

1. Ouvrir un morceau local puis le plein écran ; vérifier le curseur et toutes les commandes, sur la pochette puis les paroles, avec un espace visible au-dessus de la navigation Android.
2. Essayer portrait/paysage et le mode de navigation habituel. Vérifier les deux sens de swipe et le retour Android.
3. Laisser une version automatique s'afficher. Vérifier dans **Source et synchronisation** si elle comporte de vrais temps de mots ou seulement des lignes.
4. Fermer l'application, couper le réseau, la rouvrir et relire **le même fichier** : même texte/source et même niveau de synchronisation, sans recherche. Un LRC par ligne ne doit pas devenir artificiellement mot à mot.
5. Vérifier qu'une VO/VF choisie ou corrigée reste intacte et que la recherche des trois points permet toujours un remplacement volontaire.
