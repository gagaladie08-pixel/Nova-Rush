# 0.6.1 — Artistes d’album, lenteur de l’onglet Artistes, mémoire des pochettes

## 1. La page d’album n’affiche plus les invités comme artistes du disque

Sur « The Fame Monster », la page listait six noms : Lady Gaga, Beyoncé, Lady Gaga, Colby O’Donis,
Flo Rida, Space Cowboy. Le code prenait **tous les artistes de toutes les pistes** de l’album.

C’est l’inverse de la règle voulue : un invité chante **sur un morceau**, il n’est pas l’artiste du
disque. L’en-tête n’affiche plus que l’artiste de l’album ; les invités restent visibles sur leur
piste, et la liste complète demeure accessible par le menu « Artistes de cette sortie ».

Effet secondaire utile : « Lady Gaga » n’apparaît plus deux fois dans l’en-tête, même quand deux
identités portent ce nom. Le diagnostic des doublons (`0.5.2`) reste le moyen de les expliquer.

## 2. Lenteur de l’onglet Artistes

Trois causes cumulées.

- **Expressions régulières reconstruites à chaque morceau.** Les six séparateurs connus créaient un
  objet `Regex` neuf à chaque appel, pour chaque morceau, à chaque recalcul. Ils sont désormais figés
  une fois pour toutes ; un séparateur libre reste mémorisé à sa première utilisation.
- **Noms protégés normalisés à chaque comparaison.** La liste était reparcourue et renormalisée à
  chaque morceau. Les clés sont précalculées.
- **Calcul complet relancé à chaque défilement.** La liste des cartes visibles servait à la fois au
  calcul des contextes d’artiste **et** au tri. Or une liste de cartes visibles ne peut pas changer
  un contexte d’artiste : elle n’a donc plus qu’un rôle de tri. Le calcul complet est mémorisé et
  n’est refait que si l’index du catalogue, l’état local ou les réglages changent.

## 3. Pochettes et mémoire

Les pochettes étaient décodées en **pleine résolution** pour des vignettes de carte. Quelques images
de plusieurs milliers de pixels suffisent à épuiser la mémoire et à faire tomber l’application —
scénario le plus probable du plantage de l’onglet Artistes.

Les images sont maintenant demandées à Coil **à la taille affichée (320 dp)**, donc réduites pendant
le décodage : la pleine résolution n’occupe jamais la mémoire.

**Ce qui n’est pas prouvé** : ce correctif traite l’hypothèse la plus probable, pas une certitude.
Le rapport de plantage (`Réglages → Suivi de l’application`) reste le seul moyen de confirmer. S’il
désigne autre chose, ce sera à traiter séparément.

## 4. CI

Une panne passagère de l’API GitHub (HTTP 503) sur l’étape qui publie les rapports faisait échouer
des vérifications pourtant réussies. Cette étape ne bloque plus : les tests ont déjà tranché.
