# Découpage des artistes : réglable, vérifiable, réversible

**Version 0.5.1.** (La 0.5.0 contenait le moteur mais pas le câblage de navigation : l’écran ne s’ouvrait pas. Voir la [note 0.5.1](../developpement/16-route-split-corrigee-0.5.1.md).) Le code ne devine plus où finit un nom d’artiste. Il applique deux listes que l’utilisateur voit et modifie dans **Paramètres → Découpage des artistes**.

## Le problème, en une phrase

Un fichier peut contenir `Rihanna, Drake` : le tag dit vrai, mais un seul champ texte ne permet pas de savoir si la virgule sépare deux artistes ou appartient à un nom comme `Earth, Wind & Fire`. Aucune heuristique ne peut trancher seule — dans les deux cas la forme est identique.

## Ce qui décide, dans l’ordre

1. **Un choix manuel** pour ce morceau gagne toujours, sans exception.
2. **Des crédits structurés déjà multiples** (MusicBrainz, ou une résolution automatique) font foi : deux identifiants, deux artistes. Ils ne sont pas écrasés par les règles de texte.
3. **Un crédit unique n’est qu’un texte de plus** : ce sont alors les deux listes qui décident. C’est exactement le cas de `Rihanna, Drake` écrit dans un seul tag.
4. **La liste protégée** coupe court : un nom qui s’y trouve reste entier, quoi qu’il contienne.
5. À défaut, on garde le crédit d’origine, identifiant compris.

## Les deux listes

| Liste | Contenu de départ | Où c’est réglé |
| --- | --- | --- |
| Noms protégés | 12 noms, dont `AC/DC`, `Earth, Wind & Fire`, `Tyler, the Creator`, `Simon & Garfunkel` | Paramètres, ajout et retrait |
| Séparateurs | `;` `feat.` `featuring` `ft.` `/` `,` — **`&` désactivé** | Paramètres, cases à cocher + ajout libre |

- `&` est le seul séparateur éteint au départ, parce que c’est celui qui ressemble le plus à un nom de groupe (`Anyma & LISA`).
- Un séparateur **saisi librement** (deux caractères au plus) est toujours **échappé** : taper `.` coupe sur un vrai point, jamais sur « n’importe quel caractère ».
- `Artistes divers`, `Various Artists`, `Artiste inconnu` et `Unknown Artist` ne sont **jamais** découpés, même si l’on retire tous les noms protégés.

## Trois garde-fous qui évitent les coupures absurdes

- Un morceau de **moins de deux caractères** annule la coupure du parent : `A/BC` reste entier.
- Un **nom protégé qui réapparaîtrait** après découpe annule aussi la coupure.
- La comparaison ignore **casse et accents** : protéger `ac/dc` protège `AC/DC`.

## L’écran, et ce qu’il montre vraiment

L’aperçu ne travaille pas sur des exemples inventés : il applique les règles **à la bibliothèque réelle** et liste les noms qui seraient découpés.

- **Garder entier** ajoute le nom complet à la liste protégée. C’est la seule action, et elle est **réversible** : on peut retirer le nom ensuite.
- Rien n’est **jamais** écrit dans les fichiers audio ni dans leurs tags. Seuls l’affichage, les cartes d’artistes et le regroupement des albums changent.

## L’artiste d’album suit les mêmes listes

Un featuring dans l’artiste d’album est un invité, pas une identité : `Katy Perry feat. Doechii` reste l’album de Katy Perry. Une fois découpé, `Rihanna, Drake` devient l’album de Rihanna et ne crée pas un second disque ; protégé, il redevient une identité distincte. `Artistes divers` et `Various Artists` ne sont pas fusionnés.

## Effet immédiat, sans redémarrage

Les deux listes sont enregistrées avec les choix de catalogue (table `catalog_choices`, clés `split_protected` et `split_separators`, en JSON). Le catalogue est recalculé à chaud : ni réindexation, ni réanalyse des fichiers.

## Ce qui est verrouillé par les tests

`SplitRulesTest` fige le contrat : `&` éteint par défaut, les **douze noms protégés** intacts même avec tous les séparateurs actifs, séparateur libre échappé, coupure annulée sous deux caractères, albums collectifs intacts, artiste d’album soumis aux mêmes listes, et crédits MusicBrainz toujours prioritaires.
