# Nova Rush

Jeu mobile de rythme musical à tuiles (Android, portrait, en français). Le joueur choisit une démo ou importe sa musique ; l'application l'analyse et génère un parcours de tuiles sur quatre voies. Le document de conception de référence est [`Conception_Nova_Rush.docx`](Conception_Nova_Rush.docx).

## Télécharger l'APK

Chaque push déclenche le workflow **APK Android** (`.github/workflows/android.yml`) qui :

1. lance les tests (règles de score, analyse musicale) ;
2. construit l'application web et la synchronise dans le projet Android (Capacitor) ;
3. compile un APK *release* signé ;
4. le publie dans l'onglet **Releases** du dépôt (`NovaRush-0.1.N.apk`) et comme artefact du workflow.

Sur le téléphone : télécharger l'APK depuis la page Releases, l'ouvrir et autoriser l'installation depuis cette source.

> L'APK est signé avec une **clé de test** versionnée (`android/app/nova-rush-test.p12`) pour que les mises à jour s'installent par-dessus. Pour une publication (Play Store), fournir une clé privée via les variables d'environnement `NOVA_KEYSTORE_FILE`, `NOVA_KEYSTORE_PASSWORD`, `NOVA_KEY_ALIAS`, `NOVA_KEY_PASSWORD` (secrets GitHub).

## Ce qui est implémenté

| Domaine | Détail |
| --- | --- |
| Bibliothèque | Écran d'accueil, onglets Facile / Moyen / Difficile / Expert triés par meilleur score, section Démos, bouton « + Importer », état vide, lecture en arrière-plan avec « Arrêter la musique ». |
| Import | MP3, M4A, WAV sans limite de durée ; copie locale (IndexedDB) ; titre, interprète et pochette lus dans les tags ID3 / MP4 (sinon pochette par défaut et « Artiste inconnu ») ; progression ; fichier illisible = explication, rien n'est ajouté. |
| Analyse | Flux spectral → attaques ; tempo par autocorrélation ; suivi des battements par programmation dynamique ; hauteur par saillance harmonique. Mélodie fiable → voies selon les hauteurs relatives ; sinon repli rythmique (battements + accents, motifs cohérents par section). Tuiles longues seulement pour des sons tenus nets, accords sur les accents forts. Calcul dans un Web Worker. |
| Difficulté | Densité des gestes, passages exigeants (90ᵉ centile sur fenêtres de 4 s), espacement, accords, changements de rythme, tuiles longues. **Seuils provisoires** regroupés dans `src/analysis/difficulty.ts` (à calibrer en essais). Le niveau règle densité et vitesse. |
| Jeu | 4 voies, ligne de jugement, multi-touch ; Perfect+ ±25 ms, Perfect ±70 ms, Great ; combo Perfect+ (150 × n) ; tuiles longues (moyenne des rangs, × durée en temps) ; relâchement trop tôt = Great ; accords avec remise à zéro après le groupe ; raté = fin de partie, la musique continue. |
| Partie | Compte à rebours 3-2-1 synchronisé sur l'horloge audio, 5 étoiles de progression temporelle, pause illimitée et reprise au même instant, résultats, records (meilleur score et meilleur combo), résultat partiel en cas d'abandon. |
| Thèmes | Cyber Nova (défaut), Survivor, African Confessions : palettes, typographies, rayons, animations du document. Thème de gameplay pour la prochaine partie seulement. Option « Réduire les animations ». |
| Paramètres | Thèmes, réduction des animations, décalage audio et test de synchronisation (clics à taper en rythme), aide, confidentialité. |
| Démos | 5 morceaux originaux composés pour le jeu et synthétisés sur l'appareil (aucun droit tiers) : Synthwave (Facile), Afropop et Éthio-jazz (Moyen), Vogue house (Difficile), Drum & bass (Expert), parcours écrits à la main. |

## Développement

```bash
npm install
npm run dev        # http://localhost:5173 (navigateur, mode portrait conseillé)
npm test           # tests Vitest
npm run build      # build web dans dist/
npx cap sync android && (cd android && ./gradlew assembleRelease)   # APK local (Android SDK + JDK 21)
```

Structure :

```
src/analysis/   analyse audio (dsp, analyzer, difficulty, worker)
src/audio/      moteur Web Audio, lecture des métadonnées
src/demos/      démos : partitions, synthèse hors ligne
src/game/       règles de score, moteur de jeu (canvas), thèmes
src/storage/    IndexedDB, paramètres
src/ui/         écrans (bibliothèque, aperçu, partie, résultats, paramètres, aide, synchro)
android/        projet Android (Capacitor)
```

## Reste à faire (cf. §10 du document)

- Calibrer les seuils numériques de difficulté et les tolérances de timing sur appareils réels.
- Affiner l'analyse automatique sur un corpus de morceaux variés.
- Vérifier la disponibilité commerciale du nom et les références visuelles avant publication.
