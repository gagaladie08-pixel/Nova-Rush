# 0.5.0 — Découpage des artistes réglable

## Ce qui change pour toi

Les réglages **Paramètres → Découpage des artistes** contiennent désormais deux listes que tu peux modifier :

- **Noms protégés** — un nom qui s’y trouve n’est jamais coupé. Les 12 noms de départ sont conservés.
- **Séparateurs** — ceux qui découpent réellement un nom. `&` est **éteint** au départ.

L’écran applique tes règles à **ta bibliothèque réelle**, pas à des exemples : il liste les noms qui seraient coupés. Le bouton **Garder entier** ajoute le nom à la liste protégée, et c’est réversible.

**Aucun fichier audio n’est réécrit.** Seuls l’affichage, les cartes d’artistes et le regroupement des albums changent, immédiatement, sans redémarrage.

## L’ordre de décision (important)

1. Un choix manuel pour un morceau gagne toujours.
2. Deux crédits structurés (MusicBrainz) font foi : ce sont deux artistes.
3. Un crédit **unique** n’est qu’un texte : tes listes décident. C’est le cas de `Rihanna, Drake` dans un seul tag.
4. Sinon, le crédit d’origine est conservé, identifiant compris.

## Trois pièges traités

- Un séparateur saisi librement est **échappé** : taper `.` ne coupe pas sur n’importe quel caractère.
- Une coupure qui produirait un morceau de **un caractère** est annulée.
- `Artistes divers` et `Various Artists` ne sont **jamais** découpés ni fusionnés.

## Vérification

- Nouveau `SplitRulesTest` (9 tests) : `&` éteint, les 12 noms protégés intacts **même avec tous les séparateurs actifs**, échappement, coupure annulée, albums collectifs, artiste d’album, priorité des crédits MusicBrainz.
- Garde-fous existants conservés : `CatalogTest`, `CatalogCorrectionsTest`, `CatalogStorageTest`, `FeatureingAlbumIdentityTest`. Un seul a été **réécrit sciemment** : `verifiedAutomaticCreditsAreContextBoundAndManualChoicesAlwaysWin`, dont le texte de test contenait une virgule — désormais séparateur actif — ce qui masquait ce qu’il vérifiait réellement (crédits résolus liés au contexte). Son intention est intacte, sa fixture est neutre.
- Suite complète verte en CI avant livraison.

## Fiche de conception

[22 — Découpage des artistes réglable](../conception/22-decoupage-des-artistes-reglable.md).
