# 0.6.0 — Fusion des éditions, retour réparé, démarrage accéléré, journal

## 1. Fusion des éditions — décision explicite

**Règle : même titre de base + même artiste d’album = même album.**

Le suffixe d’édition (`Deluxe`, `Deluxe Edition`, `Expanded`, `Special Edition`, `Bonus Tracks`,
`Anniversary`, `Reissue`, `Réédition`, `Remaster`, `Remastered`) est retiré pour comparer les titres.

Ce qui a été **retiré** volontairement : l’exigence d’une preuve supplémentaire (une piste commune,
des identifiants de sortie concordants, des types compatibles). Elle laissait deux cartes que rien
ne distinguait, parce que MediaStore n’offre souvent aucune correspondance exploitable.

**Ce qui reste vrai et qui n’est pas négociable :**

- `Live`, `Remixes`, `Remix` ne sont **pas** des suffixes d’édition : ce sont d’autres disques. Les
  confondre effacerait de vraies variantes, et aucun retour ne permettrait de les retrouver.
- **« Séparer les éditions »** reste le recours : la séparation est mémorisée et n’est pas défaite
  par un nouveau scan.
- **`Destinée` et `Destinée Supremacy (Deluxe)` ne sont pas réunis** : retirer `Deluxe` donne
  `Destinée Supremacy`, qui n’est pas `Destinée`. Aucune règle de suffixe ne peut les rapprocher ;
  c’est le regroupement manuel qui décide. Un test le verrouille explicitement.

Cinq garde-fous existants ont été **réécrits sciemment** pour verrouiller ce nouveau contrat, dont
`albumNamesAndDeluxeSuffixesAloneNeverCauseAnUnrequestedMerge` et
`genericEditionMarkersNeedSharedRecordingEvidence…`, qui défendaient la règle retirée. Les
garde-fous qui restent valables (autre artiste = autre album, séparation manuelle respectée) sont
conservés.

## 2. Retour impossible depuis une fiche artiste

`goBack()` exigeait que l’onglet et l’identifiant soient d’accord (`artistId` rempli **et** onglet 2).
Quand les deux se désynchronisaient — l’identifiant est sauvegardé entre les sessions, l’onglet peut
être restauré dans un autre ordre — aucune branche ne correspondait, et le retour ne faisait rien.

Le retour est désormais inconditionnel : un détail ouvert se ferme, où qu’il soit. Un identifiant
qui n’existe plus dans le catalogue est aussi nettoyé, sinon la liste s’affichait en gardant
l’identifiant et le retour semblait encore inopérant.

## 3. Démarrage lent

La cause principale : chaque fichier audio était **ouvert et relu** au démarrage pour en extraire les
tags internes. Le cache existait mais vivait en mémoire et se vidait à chaque lancement.

- Le cache est maintenant **conservé sur disque** (`nova-tag-cache.json`), écrit en fin de scan et
  borné à 6 000 entrées.
- Sa clé d’invalidation est **taille + date de modification** du fichier — les seules données qui
  survivent à un redémarrage du téléphone. L’ancienne clé incluant le numéro MediaStore changeait à
  chaque redémarrage et vidait le cache sans qu’aucun fichier n’ait bougé.
- Un cache illisible est supprimé et reconstruit : il ne peut pas empêcher le démarrage.

## 4. Journal et Santé (niveau 2)

**Réglages → Suivi de l’application** : journal persistant sur disque, deux fichiers de 200 Ko au
maximum, avec les erreurs, les étapes du démarrage et les **arrêts brutaux** (gestionnaire
d’exception non rattrapée). Filtre « erreurs seulement », effacement, et « copier le rapport » pour
transmettre sans outil externe.

**Réglages → Santé** : état au moment présent — catalogue, morceaux indexés, artistes, albums,
bibliothèque, cache des tags, règles de découpage, arrêt brutal.

**Limite assumée, écrite dans l’écran lui-même** : un défaut de logique qui ne lève aucune exception
(un bouton sans effet, un retour inopérant) n’apparaîtra jamais dans un journal. C’est un fait, pas
un manque de soin : rien n’est levé, donc rien n’est capturable. Les deux bugs ci-dessus en sont la
démonstration — ils ont été trouvés en lisant le code.

## 5. CI : échecs de test lisibles

Le stockage des journaux de GitHub est parfois inaccessible depuis l’atelier. Une étape publie
désormais **chaque test en échec** en annotation, avec son nom et son assertion
(`expected:<2> but was:<1>`). Elle a immédiatement servi à corriger deux tests fautifs lors de ce lot.
