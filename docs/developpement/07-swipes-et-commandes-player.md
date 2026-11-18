# NovaListen 0.2.2 — swipes et commandes du lecteur

## Corrections demandées

| Zone | Nouveau comportement |
| --- | --- |
| Lecteur plein écran | Glisser **à gauche ouvre les paroles**, glisser **à droite revient à la pochette**. Pas de boucle : droite sur la pochette ou gauche sur les paroles ne change pas de page. Le bouton paroles et le retour Android restent disponibles. |
| Bibliothèque | Glisser **à gauche : Lire ensuite** ; **à droite : Informations**. Le libellé affiché pendant le geste correspond à l’action exécutée. Les lignes et les cartes utilisent le même composant. Le sens reste physique même avec une mise en page RTL. |
| Commandes plein écran | Toutes les commandes de lecture restent dans la zone protégée, avec une **marge supplémentaire de 20 dp au-dessus de la navigation Android**. |

Les gestes précédent/suivant du mini-player, les gestes verticaux du mode immersif et le réordonnancement des playlists sont distincts et ne sont pas inversés. La sélection et les morceaux en attente gardent leurs swipes de ligne désactivés.

## Disposition

Le lecteur avait **déjà** `safeDrawingPadding()` : il ne s’agissait pas d’une absence totale de gestion des barres Android. La correction réserve d’abord la hauteur des commandes, puis de l’en-tête, et donne seulement l’espace restant au contenu central. Les insets protégés de dessin et de gestes sont réunis (maximum par côté, pas additionnés deux fois), puis une vraie marge basse est ajoutée.

- La pochette carrée est bornée par la largeur **et** la hauteur disponible, au lieu d’imposer un carré calculé seulement depuis la largeur.
- Le contenu secondaire de la page pochette peut défiler **à l’intérieur de sa zone**, sans pousser ni recouvrir les commandes. Cette zone est également découpée à ses limites de dessin.
- Les fenêtres courtes utilisent une présentation compacte ; les fenêtres étroites réduisent l’espacement/les grandes icônes avant d’envisager une seconde rangée pour répétition et file. Aucune cible tactile des commandes n’est inférieure à 48 dp.
- La chronologie reste accessible, avec les temps à côté du curseur sur petite hauteur ou grande taille de police.
- Les paroles conservent leur viewport adaptatif et le centrage de la ligne active, le défilement manuel, le recentrage et le déplacement par toucher. Les crédits, VO/FR et l’attribution Google restent présents lorsqu’ils sont nécessaires.
- Le masquage immersif reste à 7 secondes ; son bouton « Afficher les commandes » utilise maintenant la même place réservée, sans recouvrir le contenu.

Aucun changement du moteur audio, de SQLite, des clés/API, des données privées, des thèmes ou de la cascade des paroles. La version applicative devient **0.2.2 / code 4** pour distinguer ce binaire des APK historiques.

## Vérifications ajoutées

- `HorizontalSwipesTest` : directions, absence de boucle, seuil strict, diagonales/verticales et entrées non valides.
- `LibrarySwipesTest` : vrais gestes sur ligne et carte, aperçu pendant que le doigt reste posé, callbacks correspondants, RTL, sélection/durée inconnue, annulation, petits gestes et callbacks à jour.
- `PlayerLayoutTest` : commandes entièrement contenues (bornes **non découpées**, pour ne pas confondre un bouton partiellement visible avec un bouton entier), marge basse et latérale, cibles tactiles, seek et callbacks ; pochette bornée et actions secondaires défilantes ; centrage des paroles après changement de dimensions/insets. Le contenu immersif laisse aussi les gestes verticaux à son parent, sans les intercepter dans un défilement.
- Matrice de contraintes simulées : 320×568, 320×480, paysage 640×320 avec encoche, 280×400 avec police ×2, 240×480 avec police ×1,5, paysage 640×360 avec navigation latérale. Les deux pages passent dans la matrice.
- `NovaSmokeTest` : glissements dans les **deux sens du vrai dialogue plein écran**, bornes des commandes avec les vrais insets de l’émulateur, ouverture par bouton toujours possible et capture de la pochette. Les tests existants de centrage, scroll et pixels du karaoké sont conservés.

### État de validation

**[Validation finale réussie — exécution 44305382562](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44305382562)**, job/check `134805469099`, sources `d7721e1eb2a9d3c44b29ab3b16a0448a041f26c7` de la branche de session. Les rapports XML ont été récupérés et relus : **133 tests unitaires + 24 tests Android 10, zéro échec, erreur ou test ignoré**. Lint : **0 erreur / 0 fatal / 32 avertissements**, sans nouvelle baseline. `python3 scripts/verify-project.py` et `git diff --check` ont également réussi. Le poste local n’a pas de JDK/SDK Android : la compilation et les tests ont bien été exécutés dans le workflow GitHub existant, pas prétendument en local.

Deux exécutions préliminaires n’étaient pas livrables : `44303710736` a détecté l’utilisation de propriétés de rectangle indisponibles dans l’API Compose des tests ; `44304288282` a compilé et passé les 133 tests unitaires, mais seulement 22/24 tests Android. Le repérage de la pochette a été corrigé pour consulter son arbre sémantique non fusionné. Le test des lignes désactivées distingue désormais l’absence de reconnaissance du swipe du clic d’information intentionnel sur un morceau en attente. Aucune APK de ces exécutions en échec n’est remise ; ce sont les sources et rapports de la validation finale ci-dessus qui font foi.

### APK remis et inspection

`NovaListen-0.2.2-debug.apk` : **89 542 970 octets**, SHA-256 `3c0eb1f9132c5d53f46fdbcdcb3cb57f69f724bc658c80b108a9c14859aff13a`. Les cinq fragments de livraison et le fichier assemblé ont été vérifiés, sans recompilation ni nouvelle signature après la validation. Le ZIP et le manifeste binaire ont été inspectés : `com.novalisten.app`, **0.2.2 / code 4**, Android 8+ / cible 35, signature v2, quatre ABI et 26 fichiers de polices, zéro audio de démonstration. HTTPS uniquement, backup désactivé, ni microphone/localisation ni provider d’initialisation automatique ML Kit ; badge officiel Google inchangé.

Preuves locales hors Git : `artifacts/APK-INFO-0.2.2.json`, `artifacts/NovaListen-0.2.2-SHA256SUMS.txt` et `artifacts/ci-44305382562/` (run/job/annotations, journal, rapports, manifeste de transfert et `verification-summary.json`). Les captures finales 08 (pochette/commandes dégagées) et 07 (paroles centrées) ont été ouvertes et contrôlées. L’artefact `NovaListen-0.2.2-debug` reste aussi téléchargeable depuis l’exécution Actions, avec la rétention prévue par le workflow. Les anciennes releases ne sont pas remplacées.

**[Téléchargement direct de l’APK 0.2.2 (89,5 Mo)](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.2/NovaListen-0.2.2-debug.apk)** · [SHA256SUMS](https://github.com/gagaladie08-pixel/Nova-Rush/releases/download/novalisten-v0.2.2/SHA256SUMS.txt) · [page de version](https://github.com/gagaladie08-pixel/Nova-Rush/releases/tag/novalisten-v0.2.2). Publication distincte réussie par [l’exécution 44308982773](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44308982773), job `134817875427`, sources de publication `8ca15b70dbea46791f6a6882eee9f64dba5da69e`. Le workflow de livraison est épinglé au binaire vérifié, sans recompilation ni nouvelle signature. Release `449988714`, publique et prerelease ; asset `763417897`, taille et digest SHA-256 GitHub vérifiés à l’identique du fichier local. Les tailles et digests des APK 0.2.0/0.2.1 restent inchangés. Preuve : `artifacts/ci-44305382562/publication.json`.

Les changements de dimensions/police/insets des tests sont des simulations de contraintes Compose, pas une prétendue rotation de téléphone physique. Les vérifications sur émulateur et les fixtures synthétiques ne prouvent pas le rendu sur tous les appareils, tailles de police extrêmes, modes multi-fenêtre ou surcouches Android.

## Essai sur téléphone

1. Ouvrir un morceau local, puis son lecteur plein écran. Glisser à gauche sur la zone centrale : paroles. Glisser à droite : pochette. Vérifier aussi le bouton paroles et le retour Android.
2. Dans la bibliothèque, glisser un morceau à gauche : l’aperçu indique Lire ensuite et le morceau est inséré sans interrompre la lecture. À droite : l’aperçu indique Informations puis la fiche s’ouvre. Refaire sur une carte d’album/artiste.
3. En portrait, puis en paysage et avec une police agrandie, vérifier que curseur et cinq commandes sont entièrement visibles et dégagés de la navigation Android. Les actions secondaires de la pochette peuvent nécessiter un défilement sur petite hauteur.
4. Sur les paroles synchronisées, vérifier la ligne centrale, le défilement manuel, le recentrage, puis le retour à la pochette. Attendre 7 secondes sur la pochette pour essayer le mode immersif et le réaffichage.

**Signature :** le certificat SHA-256 de 0.2.2 est `a67525d0f16441114825c63117218af905e104441bfa7fe3aab8f3afa0312372`, **différent de 0.2.0 et de 0.2.1**. Aucune clé privée 0.2.0 n’a été retrouvée lors de la recherche précédente. Ce correctif ne résout pas cette incompatibilité : le workflow utilise toujours la clé debug générée sur le runner. Ne pas désinstaller aveuglément une version existante pour contourner le refus de mise à jour Android ; les favoris, playlists, historique, paroles, images, clés et réglages privés seraient effacés et aucun export intégré n’existe. La musique du stockage partagé est distincte. Les APK 0.2.0 et 0.2.1 publiés ne sont pas remplacés.
