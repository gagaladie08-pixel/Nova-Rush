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

## Vérification finale

**[Validation réussie 44930520156](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44930520156)**, job/check `136925900026`, sources **`386642a01593a6bea0bb5ecb0e9d891ac0873049`** de la branche de session. Les rapports XML ont été récupérés et relus :

| Configuration | Tests réussis | Échecs / erreurs / ignorés |
| --- | ---: | --- |
| Unitaires JVM | 145 | 0 / 0 / 0 |
| Android 10, fenêtre 320×640 à 160 dpi | 30 | 0 / 0 / 0 |
| Android 15, Pixel 2 1080×1920 à 420 dpi, gestes | 30 | 0 / 0 / 0 |
| Android 15, même émulateur, trois boutons | 30 | 0 / 0 / 0 |

Ce sont **30 tests Android distincts répétés sur trois configurations**, soit 90 exécutions Android, et non 90 scénarios différents. Lint : **0 erreur / 0 fatal / 34 avertissements**, sans baseline ajoutée. Chaque passage conserve ses journaux, XML, captures et mesures avant le suivant. Dix snapshots de bornes physiques par configuration couvrent les deux pages, la rotation et la recréation. En portrait Android 15, le bouton de lecture le plus bas est mesuré **53 px (~20,2 dp) au-dessus de la zone protégée** dans chacun des deux modes de navigation. Les captures finales 07 (paroles), 08 (pochette), 09 (texte natif conservé) et 10 (paysage/navigation latérale) ont été ouvertes et contrôlées.

### Exécutions préliminaires non livrées

Deux exécutions préliminaires ne sont pas livrables :

- [44927888844](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44927888844), sources `daf8773349f6b68e2c3b120bf4279b244a147a8d` : compilation, **145 tests unitaires et 30 tests Android 10 réussis**, mais **2 échecs sur 30 sur Android 15 gestuel**, sur la marge physique après recréation. Les nouveaux tests ont réellement détecté un cas que la matrice synthétique ne couvrait pas ; la protection par les insets racine a été ajoutée ensuite. Le mode trois boutons n'a pas été exécuté dans ce run.
- [44928987697](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44928987697), sources `d1e69329187c33f83338042801600ecee7438ec9` : compilation, **145 unitaires et 30 Android 10 réussis**, puis **29/30 dans chacun des deux modes Android 15**. Les vérifications de marge, y compris rotation et recréation, réussissent. Le seul échec de chaque passage attend l'inactivité Espresso pendant que le vrai karaoké joue une animation continue après recréation. Le test de conservation met désormais la lecture en pause **avant** d'ouvrir les paroles natives ; il vérifie toujours la restitution exacte du texte, de la source et des temps sans requête, sans supprimer l'animation de l'application ni assouplir les assertions de bornes. Les tests de pixels natifs et ceux de lecteur en lecture sont conservés.

Les tests de stockage/minutage/migration ont aussi réussi dans ces passages. Aucun binaire issu d'une exécution en échec n'est remis ; seul le binaire de la validation finale réussie ci-dessus est livré.

Les résultats historiques de 0.2.2 ne constituent pas la validation de 0.2.3. Les contrôles de structure `scripts/verify-project.py`, de syntaxe des scripts et `git diff --check` ont réussi localement ; le poste ne dispose pas de JDK/SDK Android.

### Couverture ajoutée

- Unitaires : attente de la sauvegarde atomique, amélioration ligne → mots malgré l'émission du snapshot disque, nouvelle instance sans découverte/réseau, option désactivée/pause, priorité manuelle et VF, identité, réponses tardives, erreur disque et absence de résultat.
- SQLite sur Android : fermeture du helper puis nouvelle instance et nouveau moteur, restitution exacte des temps et du crédit sans requête, écriture interrompue, refus de dégradation/écrasement, conservation de VF et migration v3 → v4 sans perte des autres tables. Les migrations v1/v2 restent testées.
- Lecteur complet : bornes **non découpées** de chaque commande et du curseur, converties en coordonnées de fenêtre physique, comparées à la taille Android réelle et à ses insets de navigation/statut/encoche/gestes ; pas seulement à un parent Compose. Coordonnées et insets sont lus dans un même snapshot du thread UI. Les mesures sont archivées ; un échec prend aussi une capture. Les deux pages et une vraie rotation/recréation d'activité sur émulateur sont exercées.
- Interface de paroles : sauvegarde native préalable, vidage de l'état du moteur, recréation de l'activité, texte/minutage toujours disponibles et compteur HTTP inchangé.
- Workflow : Android 10 puis Android 15 en navigation gestuelle et à trois boutons. Les journaux, XML et captures de chaque passage sont archivés séparément avant le passage suivant. Les tests existants des swipes, du centrage et des pixels du karaoké sont conservés.

Les fixtures sont synthétiques : pas de clé réelle, de musique de l'utilisateur, de recherche musicale réelle ni de pack ML Kit. Les émulateurs ne certifient pas le rendu sur le téléphone de l'utilisateur ou toutes les surcouches Android. Une validation visuelle sur ce téléphone reste nécessaire.

## APK et installation

**`NovaListen-0.2.3-debug.apk` remis**, version **0.2.3 / code 5**, **89 559 354 octets** ; SHA-256 **`4992b71f5634b90338504211e25541e8cbde5aa6c577a0c79e229996d0f87612`**. Les cinq fragments et l'APK assemblé ont été vérifiés à l'identique du manifeste du run réussi, **sans recompilation ni nouvelle signature après les tests**.

Manifeste binaire et certificat inspectés : `com.novalisten.app`, Android 8+ / cible 35, signature APK v2, RSA 2048, quatre ABI, 26 fichiers de polices, aucun audio de démonstration ; pixels du badge officiel Google identiques. HTTPS uniquement, backup désactivé, ni microphone/localisation ni provider d'initialisation automatique ML Kit. Cette inspection Python lit la signature ; l'installation de ce même binaire par les tests Android est distincte et n'est pas présentée comme une exécution locale d'`apksigner`.

Preuves locales hors Git : `artifacts/APK-INFO-0.2.3.json`, `artifacts/NovaListen-0.2.3-SHA256SUMS.txt`, `artifacts/ci-44930520156/verification-summary.json`, `apk-transfer.json`, run/jobs/annotations, journaux et `NovaListen-verifications.zip` contenant les rapports et captures des trois configurations. Le fichier a été présenté dans le viewer ; sa publication distincte a réussi sans rebuild ni resign.

**[Télécharger l'APK 0.2.3 (89,6 Mo)](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.3/NovaListen-0.2.3-debug.apk)** · [SHA256SUMS](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.3/SHA256SUMS.txt) · [page de version](https://github.com/gagaladie08-pixel/Nova-Rush/releases/tag/novalisten-v0.2.3). Publication réussie par [44931604453](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44931604453), job `136929224607`, commit de publication `dfac6e4d561e781a7062e1bb6fe7ca749c41ac6d`. Workflow distinct épinglé au binaire validé, sans compilation ni nouvelle signature. Release `453867019`, publique/prerelease ; asset APK `776363924`. Taille et digest SHA-256 GitHub vérifiés identiques au fichier remis, ainsi que le digest du fichier de sommes. Identifiants/tailles/digests des assets 0.2.0/0.2.1/0.2.2 contrôlés inchangés. Preuves : `publication.json`, `publication-run.json`, `publication-jobs.json` et `publication-summary.json` dans le répertoire du run final.

**Signature réellement différente de 0.2.0, 0.2.1 et 0.2.2** : certificat SHA-256 **`9c6531e77ff9c802dc5622d2d041b07812e5790a56b159c27502214d4ff77d95`**. Android refusera la mise à jour directe depuis ces APK. Le workflow utilise toujours une clé debug créée par le runner ; aucune clé originale retrouvée ni nouvelle clé présentée comme équivalente. **Ne pas désinstaller aveuglément** : les données privées seraient effacées (favoris, playlists, historique, paroles, images, clés, réglages), sans export intégré. La musique du stockage partagé est distincte. Les APK déjà publiés restent inchangés.

## Essai ciblé sur téléphone

1. Ouvrir un morceau local puis le plein écran ; vérifier le curseur et toutes les commandes, sur la pochette puis les paroles, avec un espace visible au-dessus de la navigation Android.
2. Essayer portrait/paysage et le mode de navigation habituel. Vérifier les deux sens de swipe et le retour Android.
3. Laisser une version automatique s'afficher. Vérifier dans **Source et synchronisation** si elle comporte de vrais temps de mots ou seulement des lignes.
4. Fermer l'application, couper le réseau, la rouvrir et relire **le même fichier** : même texte/source et même niveau de synchronisation, sans recherche. Un LRC par ligne ne doit pas devenir artificiellement mot à mot.
5. Vérifier qu'une VO/VF choisie ou corrigée reste intacte et que la recherche des trois points permet toujours un remplacement volontaire.
