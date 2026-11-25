# 0.5.1 — L’écran Découpage des artistes s’ouvre

## Ce qui n’allait pas en 0.5.0

Le bouton existait et changeait bien l’état de navigation, mais **aucune branche ne construisait
l’écran** : dans le `when (route)` de `NovaApp.kt`, la route `split` manquait. L’application tombait
dans le `else` et réaffichait l’onglet courant. Résultat : un clic sans effet visible.

L’écran lui-même était correct et compilé ; il n’était simplement jamais appelé.

## Cause réelle

Lors de la réparation d’un fichier écrasé par erreur, la moitié « affichage » du branchement a été
perdue et seule la moitié « bouton » a été reportée. La restitution des deux listes protégées et des
séparateurs était donc invisible, alors que le moteur et le stockage fonctionnaient.

## Correctif

Une ligne dans `NovaApp.kt` :

```kotlin
"split" -> SplitSettingsScreen(library.tracks, data.catalog, vm, ::goBack)
```

## Garde-fou ajouté

`NavigationRoutesTest` lit `NovaApp.kt` et vérifie deux choses :

1. **chaque route demandée** par un bouton possède une branche qui affiche un écran ;
2. la route `split` construit bien `SplitSettingsScreen`.

Une route demandée sans écran fait désormais échouer les tests. Vérifié : en retirant la branche, le
test signale `manquantes: ['split']`.

Pourquoi ce test lit le fichier au lieu de rendre l’interface : l’erreur est purement textuelle
(une chaîne sans branche), et un test de rendu exigerait de construire ViewModel et dépôt pour
vérifier la même chose. La suite existante couvre le rendu des écrans ; celle-ci couvre le câblage.
