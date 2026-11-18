# NovaListen 0.1 — Essai sur téléphone

## Installer

1. Télécharger le fichier **NovaListen-0.1.0-debug.apk**, pas seulement le ZIP d’artefact.
2. L’ouvrir sur un téléphone **Android 8.0 ou plus récent**. Si Android le demande, autoriser l’installation depuis le navigateur ou gestionnaire de fichiers utilisé, seulement si vous souhaitez installer cet APK de test.
3. Ouvrir NovaListen, toucher **Autoriser ma musique**, puis accepter l’accès aux fichiers audio.
4. Les morceaux doivent être présents sur le téléphone et indexés par Android. Un téléchargement protégé ou conservé seulement dans le stockage privé d’un autre service de streaming n’est pas nécessairement un fichier audio accessible à NovaListen.

La première installation ne nécessite aucun compte, clé API, pack de traduction ou téléchargement de modèle. **Cyber Nova** est le thème initial. Les sept onglets se parcourent en faisant défiler la barre de navigation inférieure.

## Parcours d’essai conseillé

Utiliser de préférence quelques **copies de fichiers de test**, surtout pour essayer la suppression.

| Vérification | Résultat à observer |
| --- | --- |
| Bibliothèque | Les fichiers musicaux admissibles s’affichent avec titre, artiste, album, durée, cœur et actions. Les fichiers connus de moins de 60 s restent sur le téléphone sans apparaître dans NovaListen. Une durée inconnue est en attente, non jouable. |
| Recherche | Chercher un titre ou artiste avec/sans accents, puis vider la recherche. Ne pas changer la file par la seule saisie. |
| Lecture | Toucher un titre ; retrouver le mini-player, ouvrir le plein écran, régler la position, essayer Pause/Suivant/Précédent. |
| Arrière-plan | Réduire l’application ou verrouiller l’écran pendant la lecture ; essayer les commandes multimédias disponibles sur le téléphone. Le comportement de batterie constructeur doit être vérifié sur cet appareil. |
| Player immersif | Ouvrir le plein écran et ne plus toucher pendant 7 s : commandes masquées, musique inchangée. Toucher pour réafficher. Les commandes restent visibles sur les paroles. |
| Lire ensuite | Depuis une ligne, glisser à droite ou utiliser son menu. Le titre en cours n’est pas interrompu. Sans morceau chargé, la nouvelle file est préparée en pause. |
| Favoris | Toucher un cœur, retrouver les favoris sur l’Accueil, fermer/réouvrir l’application et vérifier la conservation. Pas de raccourci Favoris dans Playlists. |
| Masquage | Cacher un morceau de test. Il disparaît des vues et nouvelles lectures, sans suppression audio. Réglages → Morceaux masqués permet de le réafficher. Cacher le titre actif passe au prochain non masqué ou arrête/vidange la lecture s’il n’y en a pas. |
| Playlists classiques | Créer une liste vide ou y ajouter des titres, essayer l’ajout à deux classiques et un ajout répété du même fichier. Les nouveaux titres arrivent au début, les existants ne sont pas déplacés. |
| File et playlist | Réorganiser ou retirer un titre de la classique pendant sa lecture : la file lancée reste un instantané. Supprimer sa playlist source : arrêt et file vide. Annuler restaure la liste seulement, pas la lecture. |
| Paroles | Coller un texte ou importer un TXT/LRC, sauvegarder puis consulter hors ligne. Une VF manuelle est distincte de l’original. Le badge par ligne n’apparaît que pour des horodatages LRC. |
| Thèmes | Icône Palette → choisir un univers ; vérifier les couleurs, polices, formes et décors sur plusieurs écrans. La musique ne doit pas redémarrer. Essayer le profil personnalisé et les six catégories. |
| Historique | Jouer réellement au moins une minute ; une pause ou un déplacement du curseur n’ajoute pas de temps joué. Un passage ne doit pas ajouter deux événements. Les événements restent locaux. |
| Suppression — facultatif | **Uniquement avec une copie jetable** : Effacer demande une confirmation de l’application et, si nécessaire, d’Android. Annuler ne doit pas annoncer un succès. Une réussite supprime réellement ce fichier du téléphone. |

## Limites de cette version

- Pas d’enrichissement par API, traduction automatique, Fil Rouge ou playlists intelligentes opérationnelles.
- Albums regroupés par métadonnées Android ; fusion des éditions et classification fiable des sorties non intégrées.
- Pas d’écriture de tags/pochettes dans les fichiers audio, amplification 200 %, égaliseur ou minuterie.
- Accueil, effets et glyphes : première interprétation, encore à compléter et ajuster après essai.
- Listening Party et YouTube sont des onglets explicites à concevoir, sans service activé.
- L’assemblage et les tests CI ne remplacent pas les essais sur votre téléphone, formats musicaux et casque. Aucune certification de contraste ou de performance n’est donnée.

## Signaler un problème utilement

Indiquer : marque/modèle, version Android, écran concerné, étapes exactes, message affiché, type de fichier (MP3/FLAC/M4A…), durée et résultat attendu. Une capture suffit souvent ; **ne pas transmettre de musique privée ou protégée si elle n’est pas nécessaire**.

## Attention aux mises à jour de test

Cet APK utilise la signature debug du runner GitHub, qui peut changer à la compilation suivante. Si Android refuse une mise à jour pour conflit de signature, ne pas désinstaller à l’aveugle : la désinstallation efface les favoris, playlists, paroles, biographies, images copiées et réglages privés de NovaListen. Les fichiers musicaux du stockage partagé ne sont pas effacés par cette désinstallation, mais aucun export des données de l’application n’est encore proposé. Une signature durable reste à mettre en place avant des mises à jour régulières.
