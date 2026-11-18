# NovaListen — Système de thèmes visuels : quinze univers et personnalisation en plus

> **Statut : spécification visuelle fournie par l’utilisateur, 17 novembre 2026 ; conception, sans implémentation.** Les quinze univers ci-dessous, leurs palettes, leurs typographies, leurs formes, leurs effets et leurs styles de courbes sont définis par le message « Voici le système de thème ». Ils ne sont pas une sélection de suggestions à refaire valider. **« Le thème personnalisé est une option en plus »** : il s’ajoute aux quinze univers, sans les remplacer et sans être déjà un seizième préréglage décrit.
> **Exigence majeure : « le thème choisi transforme totalement l’appli dans son univers ».** Il s’agit d’un habillage complet et cohérent, pas d’un simple changement de couleur d’accent. **Cyber Nova est désormais le thème par défaut**, à la suite du choix explicite de l’utilisateur, et non d’une déduction à partir de sa première place dans la liste.
> **Précisions acquises, 17 novembre 2026 :** « Le thème par défaut est cyber nova ». Pour l’option personnalisée, l’utilisateur reprend les catégories proposées : **couleurs, polices, icônes, formes, animations et styles de courbes**. La personnalisation porte donc sur ces six catégories, pas uniquement sur une couleur d’accent ; les commandes et les réglages détaillés restent à concevoir.
> **Cadre inchangé :** [fonctions et feuille de route](01-accueil.md), [player et mini-player](03-player-et-mini-player.md), [artistes](05-artistes.md), [playlists](07-playlists.md) et [accords API](13-api-et-enrichissements.md). La phase thèmes est désormais ouverte ; la première phase de code vient ensuite, avant la conception puis le développement de Listening Party et YouTube. Leurs fonctions ne sont pas définies par ce catalogue.

## 1. Portée de la transformation : toute l’application, mêmes fonctions

Le thème choisi doit donner son identité aux écrans et composants de NovaListen : Accueil, Bibliothèque, Artistes, Albums, Playlists, mini-player, player, paroles, menus, panneaux, recherches, paramètres et états de chargement, de sélection, d’absence de données ou d’erreur. Les futurs onglets hériteront du système visuel lorsqu’ils seront conçus ; ce n’est pas une définition anticipée de leurs fonctions.

| Dimension | Exigence reçue |
| --- | --- |
| Palette | Couleurs P/S/G/F/Surf/T/T2/A de l’univers, conservées exactement comme fournies. |
| Typographie | Polices nommées pour l’univers ; rôles titres/corps repris lorsqu’ils sont explicités. Les tailles, graisses, interlignages et replis de glyphes restent à définir. |
| Iconographie | Même sens et même action, mais dessin, traits, contours, ornements et halos propres au thème. |
| Formes | Rayons ou angles coupés des cartes selon le thème ; bordures, matières et reliefs cohérents avec son univers. |
| Ambiance | Fonds, particules, lumières, reflets et autres décors décrits dans le catalogue. |
| Mouvement | Transitions, arrivées de pages, réactions et effets événementiels propres au thème ; durées approximatives telles que fournies. |
| Courbes | Traits, couleurs, remplissages, halos et animations décrits, là où une courbe sera effectivement utilisée. Le thème n’invente pas une nouvelle page de statistiques. |

**La transformation visuelle ne change pas la logique musicale :** mêmes commandes, gestes, navigation fonctionnelle, données locales, masquage, historique qualifié, regroupements d’albums, files, choix manuels et règles de playlists. Elle ne redonne pas Aléatoire aux playlists intelligentes, ne remet pas les boutons T-shirt/Cool retirés du player et ne réactive aucune API. Une animation ne devient pas une action de lecture.

Les rayons du catalogue s’appliquent aux **cartes** : les arrondis illustratifs des anciennes références n’imposent pas un rayon unique à tous les univers. Villain Era conserve ses 0 dp, Chaos Born ses 2 dp et Pink Y2K ses 28 dp. La déclinaison exacte sur boutons, champs, panneaux et masques de pochettes reste à préciser. Cela ne change pas implicitement les formats structurels acquis : grille à deux colonnes, portraits d’artistes ronds, pochettes de référence carrées, organisation des pages et zones tactiles. Les références antérieures décrivent les fonctions et l’organisation ; leur habillage doit pouvoir entrer dans chacun des univers.

Le player conserve notamment ses présentations avec illustration dominante, son mode immersif et sa page paroles. L’articulation visuelle précise entre illustration, fond du thème, effets et voiles reste à maqueter ; ne pas supprimer l’illustration pour imposer un fond uni, ni laisser une couleur extraite de pochette remplacer arbitrairement toute l’identité du thème.

## 2. Légende et conventions communes

| Code reçu | Rôle |
| --- | --- |
| **P** | Couleur principale. |
| **S** | Couleur secondaire. |
| **G** | Couleur du halo lumineux. |
| **F** | Fond. |
| **Surf** | Surface des cartes. |
| **T** | Texte. |
| **T2** | Texte secondaire. |
| **A** | Accent. |

- Les codes hexadécimaux fournis sont la **référence de conception**, sans correction, permutation ou couleur inventée. Aucun ancien fichier de code de thème n’est présumé exister dans ce dépôt de conception.
- Les valeurs de rayons, de traits et de halos sont en **dp** ; les durées sont en **ms**. Un halo de 12 dp est une indication visuelle à décliner, pas une mesure de performance ou un nombre de pixels fixe sur tous les écrans.
- La couleur **G** n’est pas l’intensité du halo. Les halos particuliers des courbes, leurs remplissages et leurs couleurs de trait sont conservés comme décrits ; ne pas les réduire à une seule propriété G.
- Des noms de couleurs comme « cyan », « or », « rose », « bordeaux » décrivent les courbes et effets. Leur association précise à un token lorsqu’il existe plusieurs nuances, ainsi que les opacités et dégradés non chiffrés, restent à préciser sans créer de faux codes fournis par l’utilisateur.
- Les durées annoncées « autour de » ou « environ » restent des cibles de conception, pas des garanties chronométrées. Survivor n’a pas de durée chiffrée et Pink Venom n’a pas de profondeur chiffrée pour ses angles coupés ; ne pas leur attribuer une valeur par défaut tacite.
- Pink Y2K et Cloud Nine sont explicitement clairs ; les autres univers décrits ont un fond sombre. Aucune variante claire/sombre de chacun, alternance automatique ou synchronisation au système n’est choisie par cette liste. Bad Angel ne bascule pas entièrement en mode clair.

## 3. Vue d’ensemble des quinze univers fournis

Les duos de polices sont reportés dans l’ordre reçu. Lorsque le message ne distribue pas explicitement les deux polices entre titres et corps, cette affectation fine n’est pas inventée ici.

| Nº | Univers | Polices | Cartes | Transition décrite |
| --- | --- | --- | --- | --- |
| 1 | 🌌 Cyber Nova — **par défaut** | Orbitron / Rajdhani | 6 dp | Environ 280 ms ; glitch bref. |
| 2 | 🟣 Neon Disco | Audiowide / Poppins | 24 dp | Environ 300 ms ; reflet chromé. |
| 3 | 🖤 Villain Era | Bebas Neue / Inter | 0 dp | Environ 150 ms ; coupe sèche, sans fondu. |
| 4 | 👑 Slay Queen | Playfair Display / Montserrat | 18 dp | Environ 350 ms ; léger dépassement élastique. |
| 5 | 🍭 Pink Y2K | Baloo 2 / Fredoka | 28 dp | Environ 320 ms ; rebond. |
| 6 | 🎭 Velvet Stage | Abril Fatface / Poppins | 12 dp | Environ 500 ms ; ouverture de rideau. |
| 7 | 🖤 Pink Venom | Anton / Inter | Angles coupés, mesure non précisée | Environ 150 ms ; réaction noir et blanc vers couleur. |
| 8 | ☁️ Cloud Nine | Comfortaa / Quicksand | 26 dp | Environ 450 ms ; fondu doux. |
| 9 | ☀️ Solara | DM Serif Display / Poppins | 20 dp | Environ 300 ms. |
| 10 | 🦋 Chaos Born | Syne / Unica One | 2 dp | Variation aléatoire d’environ 100 à 400 ms. |
| 11 | 🏳️‍🌈 Survivor | Righteous / Nunito | 16 dp | Arrivée par le bas sous un ruban arc-en-ciel ; durée non précisée. |
| 12 | 🌈 Rainbow Pop | Bungee / Baloo 2 | 22 dp | Environ 250 ms. |
| 13 | 🎤 Pop Revolution | Bowlby One SC / Inter | 10 dp | Environ 180 ms ; flash bleu-blanc. |
| 14 | 🌍 African Confessions | Fraunces / Ubuntu | 8 dp | Environ 320 ms ; révélation géométrique diagonale. |
| 15 | 😇 Bad Angel | Cinzel / Cormorant | 14 dp | Environ 300 ms ; alternance d’accents clairs/rouges. |

## 4. Catalogue détaillé — contenu fourni par l’utilisateur

Les artistes, albums et univers cités ci-dessous sont des **références stylistiques données par l’utilisateur**, pas l’annonce d’un partenariat ou d’images, logos, pochettes et polices déjà fournis ou téléchargés.

### 1. 🌌 Cyber Nova — l’interface de science-fiction

**Palette :** P `#FF006E` · S `#00B4FF` · G `#BD00FF` · F `#050510` · Surf `#0D0D2B` · T `#F0F0FF` · T2 `#A0A0C0` · A `#00FFF0`.

- **Identité :** fuchsia, bleu électrique et cyan sur fond presque noir ; inspirations aespa, Lisa et univers cyberpunk/IA. Titres **Orbitron**, texte **Rajdhani**. Icônes HUD avec coins de viseur et légers reflets ; cartes peu arrondies, **6 dp**.
- **Animations :** scanlines restant visibles ; grille en perspective et faisceau de scan en mouvement discret. Courtes bandes glitch au changement d’onglet ; transitions autour de **280 ms**.
- **Courbes :** trait cyan, halo de **8 dp**, remplissage fuchsia et dévoilement par balayage de gauche à droite.

### 2. 🟣 Neon Disco — le dancefloor chromé

**Palette :** P `#9B00FF` · S `#FFD700` · G `#FF69B4` · F `#0A0010` · Surf `#1A0030` · T `#E8E8FF` · T2 `#B0A0C0` · A `#C0C0C0`.

- **Identité :** violet et or dominants, rose et reflets argentés ; références à *Renaissance* de Beyoncé et *Future Nostalgia* de Dua Lipa. Titres **Audiowide**, corps **Poppins**. Icônes rondes évoquant chrome et rollers ; surfaces très arrondies, **24 dp**.
- **Animations :** taches lumineuses tournantes évoquant une boule à facettes ; reflet chromé balayant l’écran au changement d’onglet et périodiquement. Transitions d’environ **300 ms**.
- **Courbes :** trait or, halo de **6 dp**, remplissage violet et points qui clignotent.

### 3. 🖤 Villain Era — le jump-cut rebelle

**Palette :** P `#CC0000` · S `#4A4A4A` · G `#8B0000` · F `#080808` · Surf `#111111` · T `#EEEEEE` · T2 `#888888` · A `#FF0000`.

- **Identité :** presque noir, rouge et gris, contraste visuel affirmé ; références à *Reputation* de Taylor Swift et *Anti* de Rihanna. Titres condensés **Bebas Neue**, texte courant **Inter**. Icônes brutes, comme du papier déchiré ; cartes à angles francs, **0 dp**.
- **Animations :** **pas de transition fondue** ; coupes très rapides avec bref flash de bandes sombres et rouges. Braises rouges montant et vacillant en arrière-plan. Mouvement autour de **150 ms**, coupe volontairement sèche.
- **Courbes :** anguleuses, rouges, halo presque nul de **1 dp**. Remplissage et épaisseur non chiffrés dans la description.

### 4. 👑 Slay Queen — le glamour royal

**Palette :** P `#FFD700` · S `#FF1493` · G `#FFA500` · F `#0A0A0A` · Surf `#1A1500` · T `#FFFFFF` · T2 `#D4AF37` · A `#B76E79`.

- **Identité :** or central, rose vif, orange et or rose ; références à Beyoncé, Nicki Minaj et BLACKPINK, registre luxe/royauté. Titres **Playfair Display**, textes **Montserrat**. Icônes royales avec couronnes et contours dorés ; cartes à **18 dp**.
- **Animations :** poussière d’or tombant sur l’écran et reflets traversant les badges. Transitions d’environ **350 ms** avec léger dépassement élastique.
- **Courbes :** dorées, halo fort de **12 dp**, remplissage allant du rose à l’or.

### 5. 🍭 Pink Y2K — le bubblegum lumineux

**Palette :** P `#FF69B4` · S `#87CEEB` · G `#FF1493` · F `#FFF0F5` · Surf `#FFE4E1` · T `#8B0045` · T2 `#C06080` · A `#DA70D6`.

- **Identité :** thème clair rose pâle et bleu ciel, bubblegum/Y2K ; références à Katy Perry et *Pink Friday 2* de Nicki Minaj. Polices **Baloo 2** et **Fredoka**, rondes et amicales. Icônes de bulles, cœurs et étoiles gonflées ; cartes les plus rondes de la sélection, **28 dp**.
- **Animations :** bulles et étoiles montant en continu ; transitions avec rebond, autour de **320 ms**.
- **Courbes :** très arrondies, halo léger de **3 dp**, remplissage rose translucide et petites étoiles décoratives.

### 6. 🎭 Velvet Stage — l’entrée en scène

**Palette :** P `#8B0000` · S `#722F37` · G `#CFB53B` · F `#0C0008` · Surf `#1A0010` · T `#FFF8DC` · T2 `#D4AF37` · A `#FFD700`.

- **Identité :** bordeaux, rouge profond et or, théâtre sombre ; références à *Ruby* de Jennie et *The Life of a Showgirl* de Taylor Swift. Titres de spectacle **Abril Fatface**, texte **Poppins**. Icônes de rideaux et de plumes ; arrondi modéré, **12 dp**.
- **Animations :** entrée de page évoquant un rideau qui s’ouvre ; deux projecteurs balayant le fond. Transitions d’environ **500 ms**, les plus lentes des durées fournies pour ces univers.
- **Courbes :** halo or très prononcé de **14 dp**, effet projecteur, remplissage bordeaux.

### 7. 🖤 Pink Venom — le contraste rock

**Palette :** P `#FF0080` · S `#FF0030` · G `#FF69B4` · F `#000000` · Surf `#0D0008` · T `#FFFFFF` · T2 `#FFB6C1` · A `#FF007F`.

- **Identité :** rose vif et rouge sur noir, palette intense ; références à BLACKPINK et Rihanna. Titres massifs **Anton**, textes **Inter**. Icônes pointues et épaisses avec accents vifs ; cartes à **angles coupés**, et non à arrondis doux. La mesure de coupe reste non précisée.
- **Animations :** au toucher, passage rapide du noir et blanc à la couleur ; coulures roses descendant en arrière-plan. Transitions autour de **150 ms**.
- **Courbes :** anguleuses, trait rose épais de **4 dp**, halo faible de **2 dp**.

### 8. ☁️ Cloud Nine — le pastel apaisant

**Palette :** P `#FFB6C1` · S `#E6E6FA` · G `#FFC0CB` · F `#FAFAFA` · Surf `#FFF5F7` · T `#555555` · T2 `#9090A0` · A `#C8A2C8`.

- **Identité :** rose pastel et lavande sur fond presque blanc ; référence à *Thank U, Next* d’Ariana Grande. Polices **Comfortaa** et **Quicksand**, douces. Icônes de nuages et de bulles ; cartes très arrondies, **26 dp**.
- **Animations :** nuages dérivant lentement et apparition des pages par fondus doux ; transitions d’environ **450 ms**.
- **Courbes :** fines, **1,5 dp**, très arrondies, remplissage rose très transparent et halo diffus de **16 dp**.

### 9. ☀️ Solara — la chaleur du soleil

**Palette :** P `#FF6B35` · S `#C4622D` · G `#FFA500` · F `#1A0A00` · Surf `#2D1200` · T `#FFF3E0` · T2 `#D4A056` · A `#FFD700`.

- **Identité :** orange, terre cuite et or sur brun très sombre ; été et soleil associés à Aya Nakamura. Titres **DM Serif Display**, corps **Poppins**. Icônes avec rayons, motifs solaires et détails dorés ; cartes à **20 dp**.
- **Animations :** halo chaud respirant lentement et rayons tournant en arrière-plan ; transitions d’environ **300 ms**.
- **Courbes :** orange, halo de **10 dp**, remplissage dégradé or-orange.

### 10. 🦋 Chaos Born — le glitch imprévisible

**Palette :** P `#C0C0C0` · S `#1A1A1A` · G `#E8E8E8` · F `#050505` · Surf `#111111` · T `#F0F0F0` · T2 `#909090` · A `#DFDFDF`.

- **Identité :** argent, gris et noir, univers métallique monochrome ; référence à *Born This Way* de Lady Gaga et son esthétique avant-gardiste. Polices **Syne** et **Unica One**, expérimentales. Icônes de métal, rivets et chaînes ; cartes presque carrées, **2 dp**.
- **Animations :** éclats en mouvement par à-coups et petits glitches faisant varier position ou opacité. Durées de transition variant aléatoirement d’environ **100 à 400 ms** ; distribution et autres détails non définis.
- **Courbes :** volontairement irrégulières, argentées, halo discret de **3 dp**. La fidélité aux données reste impérative, voir section 6.

### 11. 🏳️‍🌈 Survivor — la Ballroom et la fierté

**Palette :** P `#F77FB4` · S `#5BCEFA` · G `#9B4F96` · F `#080808` · Surf `#121018` · T `#FFFFFF` · T2 `#FFB7D5` · A `#26CEAA`.

- **Identité :** rose et bleu rappelant le drapeau trans, complétés par violet et vert menthe. Polices **Righteous** et **Nunito**. Glyphes d’onglets spécialement dessinés, halo arc-en-ciel et chevron Progress ; surfaces modérément arrondies, **16 dp**. Certains classements et composants peuvent reprendre différents drapeaux.
- **Animations :** voile très transparent de bandes colorées, halos façon aurore et éclats prismatiques. À chaque changement d’onglet, arrivée de page par le bas sous un ruban arc-en-ciel. Bouquet de confettis lors d’un déblocage. **Durée de transition non précisée.**
- **Courbes :** arc-en-ciel et animées, halo de **6 dp** ; le texte secondaire peut aussi prendre un dégradé mouvant. Les événements de déblocage et les détails d’emploi des drapeaux restent à définir, sans nouveau mécanisme d’accès imposé.

### 12. 🌈 Rainbow Pop — les paillettes pop

**Palette :** P `#FF4DA6` · S `#4D79FF` · G `#9B4DFF` · F `#0A0010` · Surf `#100020` · T `#FFFFFF` · T2 `#FFB3D9` · A `#FFD700`.

- **Identité :** rose, bleu, violet et or sur violet-noir ; référence à *Chromatica* de Lady Gaga, registre camp et inclusif. Titres affirmés **Bungee**, texte adouci par **Baloo 2**. Icônes de cristaux, planètes et bonbons brillants ; cartes à **22 dp**.
- **Animations :** paillettes scintillant en continu et cercles colorés flous dérivant en arrière-plan ; transitions d’environ **250 ms**.
- **Courbes :** halo fort de **12 dp**, remplissage rose-bleu et points disco clignotants.

### 13. 🎤 Pop Revolution — le flash K-pop

**Palette :** P `#4A90D9` · S `#FF6B9D` · G `#00BFFF` · F `#050A1A` · Surf `#0A1428` · T `#FFFFFF` · T2 `#A0C4FF` · A `#F0F8FF`.

- **Identité :** bleu, rose et cyan sur bleu nuit ; références à BABYMONSTER et *1989* de Taylor Swift. Titres massifs **Bowlby One SC**, texte courant **Inter**. Icônes façon polaroid, cadres photo et accents pop ; cartes à **10 dp**.
- **Animations :** flash bleu-blanc au changement d’onglet ; égaliseur d’environ **28 barres** pulsant en bas de l’écran. Transitions très courtes, autour de **180 ms**.
- **Courbes :** bleues, épaisses de **3 dp**, peu de halo, **1 dp**. Le lien des barres avec le signal audio réel n’est pas défini par leur seul aspect.

### 14. 🌍 African Confessions — les motifs géométriques

**Palette :** P `#C4622D` · S `#2D5A27` · G `#D4A017` · F `#120800` · Surf `#1E0E00` · T `#FFF3E0` · T2 `#C4922D` · A `#FFB347`.

- **Identité :** terre cuite, vert profond, or et ambre sur fond brun. Références fournies : **Ebony — Menelik**, mythologie éthiopienne et Reine de Saba. Polices **Fraunces** et **Ubuntu**, icônes géométriques de losanges et références royales ; cartes peu arrondies, **8 dp**.
- **Animations :** losanges glissant doucement en arrière-plan ; motif géométrique révélé en diagonale puis effacé au changement de page. Transitions d’environ **320 ms**.
- **Courbes :** terre cuite, halo de **5 dp**, remplissage allant de l’ocre au vert.

### 15. 😇 Bad Angel — l’ange et le démon

**Palette :** P `#CC0033` · S `#FFFFFF` · G `#FF3366` · F `#080808` · Surf `#141414` · T `#FFFFFF` · T2 `#AAAAAA` · A `#FF0044`.

- **Identité :** rouge et rose opposés au blanc et au noir ; références à *Dangerous Woman* d’Ariana Grande et *Good Girl Gone Bad* de Rihanna. Titres élégants **Cinzel**, texte raffiné **Cormorant**. Icônes opposant halo fin et cornes épaisses ; cartes à **14 dp**.
- **Animations :** plumes tombant devant un halo ; au changement d’onglet, alternance de phase claire/rouge dans certains accents, **sans basculement complet en mode clair**. Transitions autour de **300 ms**.
- **Courbes :** rouges, halo de **8 dp**, dont l’intensité s’accentue alternativement sur une moitié du tracé.

## 5. Thème personnalisé — option supplémentaire et catégories modifiables acquises

**Acquis :** l’option Thème personnalisé s’ajoute aux quinze univers. Elle ne les remplace pas et n’impose pas de reconstruire soi-même chaque thème pour y accéder. « En plus » n’établit pas une option payante ou un verrouillage ; aucun modèle économique n’est demandé.

Après la question sur l’étendue de la personnalisation, l’utilisateur précise : « Le theme personaliser on modifier les couleurs, les polices, icônes, formes, animations et styles de courbes ? ». **Oui : ces six catégories sont retenues comme personnalisables**, pas seulement la palette.

| Catégorie modifiable acquise | Portée de conception | Détails non encore choisis |
| --- | --- | --- |
| **Couleurs** | Personnaliser les couleurs du thème, selon les rôles P/S/G/F/Surf/T/T2/A déjà définis. | Sélecteurs, dégradés, opacités, combinaisons et règles précises de contrôle du contraste. |
| **Polices** | Modifier les familles typographiques de l’habillage. | Catalogue disponible, attribution titres/corps, tailles/graisses et prise en charge éventuelle de fichiers externes. |
| **Icônes** | Modifier leur style, famille ou dessin visuel, sans modifier le sens de leurs commandes. | Jeux proposés, variantes et import éventuel ; aucun éditeur de glyphes ou format externe n’est choisi. |
| **Formes** | Modifier les formes visuelles, notamment arrondis et angles des composants concernés. | Composants réglables, plages de valeurs et déclinaison sur cartes, boutons, panneaux et masques d’images. Les formats structurels antérieurs ne sont pas changés implicitement. |
| **Animations** | Modifier l’habillage animé et les transitions du thème. | Choix d’effets, réglages de durée, intensité, densité, activation et combinaison ; pas de scripts arbitraires ou de moteur d’effets illimité décidé. |
| **Styles de courbes** | Modifier leur présentation : trait, remplissage, halo et animation, sans changer les données représentées. | Styles disponibles et paramètres précis ; pas de nouvelle mesure statistique ou méthode de visualisation audio choisie. |

Le thème personnalisé permet donc une transformation de l’univers visuel, tout en conservant les mêmes fonctions. Cette étendue ne choisit pas à elle seule un système de mélange libre entre les quinze préréglages, une banque illimitée de ressources ou leur modification destructive.

**Thème par défaut distinct de l’éditeur :** Cyber Nova est l’univers de départ lorsqu’aucun autre thème n’a encore été choisi, notamment au premier lancement. Ce choix ne vaut pas obligation de le réappliquer à chaque ouverture en écrasant une sélection ultérieure. Le parcours de sélection, la mémorisation, la restauration et le point de départ d’un thème personnalisé restent à préciser ; Cyber Nova n’est pas imposé comme base unique de l’éditeur.

**Restent à discuter :** interface et contrôles de ces six catégories, point de départ éventuel depuis un univers, limites de modification, aperçu, application/rétablissement, mémorisation, nombre de profils, import/export éventuel et emplacement dans les paramètres. Les catégories modifiables et le défaut Cyber Nova ne sont plus des choix ouverts ; les détails listés ici ne sont pas des commandes déjà validées.

## 6. Cohérence des données, des événements et des composants

- **Courbes statistiques :** la forme du trait, le halo et le dévoilement changent, pas les valeurs. L’irrégularité de Chaos Born et les lignes anguleuses des autres thèmes ne doivent pas déplacer des points, inventer des écoutes ou lisser une valeur au point de fausser son sens. Interpolation, échelles, légendes et pages utilisant réellement ces courbes restent à concevoir ; une courbe décorative n’est pas présentée comme un bilan réel.
- **Visualisation audio :** les 28 barres de Pop Revolution décrivent son animation. Cela ne choisit ni méthode de spectre/onde du player immersif, ni analyse d’audio, capture de microphone ou nouvelle permission. Préciser ultérieurement si l’effet est décoratif ou lié au signal disponible ; ne pas le présenter comme une mesure réelle sans l’implémenter.
- **Déblocages de Survivor :** l’animation de confettis est décrite, mais ni événement, ni mesure de progression, ni condition d’accès aux thèmes ne sont définis. Elle ne complète pas à elle seule les [règles ouvertes d’Atlas Nova](09-playlists-intelligentes-selection.md) ou d’autres recettes.
- **Drapeaux et inspirations :** l’iconographie et les motifs sont des choix esthétiques. Ils ne changent pas les valeurs des classements, ne déduisent pas une identité personnelle à partir des écoutes et ne classent pas automatiquement artistes ou utilisateurs par orientation, genre, origine ou croyance.
- **Images et textes choisis :** conserver pochettes, portraits, biographies, paroles et crédits retenus. Changer d’univers n’est pas une autorisation de remplacement d’image, de réécriture de tags ou de téléchargement de photos d’artistes de référence.
- **États et interactions :** les icônes restent identifiables et les statuts ne se réduisent pas à une couleur ou une décoration. Les scans, coulures, particules et rubans sont des décors, pas de nouvelles cibles gestuelles ; leur composition ne doit pas masquer les commandes ou les paroles.
- **Contenus et ressources :** les références artistiques ne fournissent pas les fichiers de police, les glyphes HUD/Progress ou les illustrations. Choisir les fichiers/licences, poids, glyphes français/anglais/coréens/espagnols et éventuels replis typographiques sans remplacer silencieusement les familles nommées. Aucun achat, téléchargement, génération d’images, collecte de logos ou d’assets n’est réalisé ici.

## 7. Lisibilité, mouvements et coût graphique — recommandations à traiter, pas refonte des palettes

Les palettes sont conservées exactement. Les recommandations ci-dessous ne sont pas une modification déjà approuvée des univers, ni l’ajout silencieux d’un mode imposé.

### 7.1 Vérification préliminaire de contraste

Un calcul local de luminance relative sRGB et du rapport `(Lclair + 0,05) / (Lsombre + 0,05)` a été effectué sur des **couleurs opaques unies**, sans rendu de texte ou d’écran. À titre de repère de conception, un texte courant vise au moins **4,5:1** ; grands textes et éléments non textuels ont des critères différents. Aucun thème n’est déclaré conforme par ce seul calcul.

| Texte secondaire tel que fourni | Sur F | Sur Surf | Point à traiter |
| --- | --- | --- | --- |
| Pink Y2K — T2 `#C06080` | Environ 3,64:1 | Environ 3,34:1 | Insuffisant pour utiliser ce T2 indistinctement sur tout petit texte courant. |
| Cloud Nine — T2 `#9090A0` | Environ 3,01:1 | Environ 2,94:1 | Même réserve, notamment sur les cartes. |

Les valeurs T principales de ces deux univers donnent des rapports plus élevés dans les mêmes conditions ; cela ne garantit ni tous les boutons, ni les dégradés, ni le texte sur une pochette. **Aucun code n’est changé.** Prévoir des propositions à valider pour les petits textes concernés : usage de T, variante de lisibilité ou autre traitement conservant l’univers. Les couleurs des textes sur P/S/A, les états inactifs/erreur/sélection et le contraste des graphiques demandent encore une définition ; blanc sur toutes les couleurs n’est pas une règle acquise.

### 7.2 Mouvement et lecture

**Recommandations à valider/détailler :** respecter la réduction des animations du système, proposer un niveau d’effets ou un mode plus sobre, encadrer fréquence/contraste des flashes et clignotements, éviter les effets stroboscopiques, conserver la lecture et les commandes pendant une transition, et rendre les effets décoratifs non bloquants. Un éventuel mode sobre doit garder palette, polices, formes et identité du thème ; il ne doit pas tout remplacer par une interface générique.

Les opacités, fréquences, densités, amplitudes et courbes temporelles ne sont pas toutes fournies. Aucun nombre de flashes par seconde, durée Survivor, intensité globale par défaut, option de désactivation ou fréquence de particules n’est inventé comme choix utilisateur. Ne pas déduire de « transition 150 ms » une obligation de répéter des flashes six fois par seconde.

### 7.3 Batterie et rendu

**Recommandations à valider/détailler :** limiter le travail graphique aux composants visibles, suspendre les décors continus hors écran/en arrière-plan, mutualiser les ressources et protéger la fluidité du défilement et du lecteur. Le mode immersif et le maintien de l’écran allumé suivent leurs décisions fonctionnelles propres, pas une nouvelle règle de veille du thème.

Aucun moteur graphique, format d’animation, budget d’images, shader, bibliothèque, fréquence d’affichage, stratégie de chargement ou mode économie n’est encore choisi. Pas de promesse de 60/120 images par seconde, de faible consommation ou de performance mesurée sur le téléphone.

## 8. État des décisions et prochaine discussion

### Acquis par le message de l’utilisateur

1. **Quinze univers nommés**, avec leurs huit codes de palette, polices, cartes, identités et animations telles que décrites.
2. **Transformation globale de NovaListen** dans l’univers choisi, et non simple recoloration.
3. **Thème personnalisé en option supplémentaire**, permettant de modifier **couleurs, polices, icônes, formes, animations et styles de courbes** ; interface et paramètres détaillés encore à concevoir.
4. Valeurs fournies des rayons, halos, traits et durées ; conserver les cas explicitement ouverts ou approximatifs, notamment angles coupés de Pink Venom et durée de Survivor.
5. **Cyber Nova comme thème par défaut**, selon le choix explicite ultérieur de l’utilisateur, notamment pour l’univers initial en l’absence d’autre choix.

### À préciser sans refaire choisir le catalogue

- Parcours d’accès au choix de thème, sans refaire choisir le défaut : **Cyber Nova est acquis**. Un écran de choix obligatoire avant de pouvoir utiliser le lecteur n’est pas ajouté par cette précision.
- Interface, contrôles et réglages précis des **six catégories personnalisables acquises** ; l’étendue ne se limite plus aux couleurs.
- Point d’entrée du sélecteur, aperçu avant application, mémorisation, changement en cours de lecture et articulation d’ensemble des réglages. Un changement purement visuel ne devient pas une interruption musicale ou une modification de file.
- Répartition des polices entre titres/corps lorsque non explicitée, poids/tailles/replis, fichiers et licences ; glyphes et dessins réels des icônes.
- Déclinaison des tokens sur composants, états, textes de boutons, illustrations du player, masques d’images et graphiques ; mesures non données et détails des dégradés.
- Événements de déblocage, emploi précis des drapeaux, lien éventuel des barres de Pop Revolution au signal et paramètres des effets.
- Garde-fous de lisibilité, mouvement et performance proposés en section 7, à préciser sans altérer silencieusement les palettes.

Les questions numérotées anciennement laissées sans réponse le restent. Les validations API, le socle artistique et les fonctions hors thème ne sont pas réouverts par ce catalogue. **La conception des thèmes n’est pas encore entièrement terminée et aucun passage au code n’est décidé par ce seul message.**

**Travail réalisé : enregistrement documentaire du système fourni, de Cyber Nova par défaut et des six catégories personnalisables ; le calcul local préliminaire de contraste de l’étape précédente est conservé.** Aucun code d’application, thème implémenté, maquette exécutable, serveur, build APK, installation, police/asset/modèle téléchargé, image générée, service activé, compte ou clé créé, condition acceptée, dépense, envoi de contenu ou modification de fichier musical. Les palettes n’ont pas été modifiées ; aucun rendu sur téléphone ou test de performance n’a été effectué.
