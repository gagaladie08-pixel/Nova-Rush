# 0.5.2 — Un seul nom pour un artiste, et un diagnostic des doublons

## Ce qui n’allait pas

**« ANTI (Deluxe) » affiché « Rihanna/ Drake »** : deux fonctions nettoyaient un nom d’artiste de
deux façons différentes. La fiche du morceau coupait sur `/` quel que soit l’espacement ; l’identité
d’album ne coupait que sur « / » entouré d’espaces. Avec `Rihanna/ Drake`, l’album recevait donc un
troisième nom qui n’existait nulle part, et « ANTI » se séparait de « ANTI (Deluxe) ».

**Deux cartes « Beyoncé », « Rihanna »…** : une carte n’est pas identifiée par son nom mais par une
clé — `mbid:…` quand le fichier porte un identifiant MusicBrainz, le nom normalisé sinon. Un nom sans
identifiant n’est rattaché à un identifiant que s’il n’en existe **qu’un seul** pour ce nom. Dès
qu’il y en a deux, le code refuse de fusionner : deux artistes homonymes ne doivent jamais être
confondus. Le refus est voulu ; ce qui manquait, c’était de le rendre visible.

## Correctifs

1. **Une seule règle.** `albumHead(raw, rules)` applique `ArtistSplitter.split`, exactement comme la
   fiche artiste. `Rihanna/ Drake` donne `Rihanna` des deux côtés, donc ANTI et ANTI (Deluxe) se
   rejoignent. Un nom protégé (`AC/DC`, `Earth, Wind & Fire`) reste entier.
2. **Diagnostic des noms en double.** Dans le catalogue Artistes, une ligne « N nom(s) en double —
   Voir pourquoi » apparaît **seulement** s’il y en a. La boîte de dialogue montre, pour chaque
   carte : son origine (identifiant MusicBrainz distinct / nom seul), son nombre de morceaux et de
   sorties, sa **clé réelle**, et deux titres d’exemple pour la reconnaître.

Aucun tag, fichier audio, favori, historique ou playlist n’est modifié.

## Ce que le diagnostic a appris pendant sa mise au point

Un nom sans identifiant **est déjà** réuni à l’identifiant unique qui lui correspond : ce n’est pas
un doublon. Le seul cas réellement dédoublé est donc celui de **deux identifiants distincts** pour un
même nom — et dans ce cas le nom seul forme une **troisième** carte, puisqu’il ne peut être rattaché
à aucun des deux avec certitude. Deux des premiers tests écrits affirmaient le contraire ; ils ont
été corrigés, pas contournés.

## Vérification

- `AlbumIdentityConsistencyTest` : barre oblique collée, ANTI / ANTI (Deluxe), noms protégés,
  séparateur désactivé, featuring.
- `ArtistIdentityReportTest` : fusion réelle non signalée, deux identifiants signalés, casse et
  accents non confondus, compteurs et exemples présents, compteurs honnêtes à zéro sans index.
- Suite complète verte avant livraison (318 tests au moment du correctif).
