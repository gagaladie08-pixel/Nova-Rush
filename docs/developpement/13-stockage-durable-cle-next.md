# Nouvelle signature Next — conservation durable avant réinstallation

## Autorisations et état réel

L’utilisateur a confirmé, après avertissement sur les données :

- **Réinstaller NovaListen Next uniquement** avec une nouvelle signature stable.
- **Ne pas toucher à NovaListen 0.2.3**, à son identifiant, à sa politique de clé,
  à son APK publié ou à ses données.
- Conserver la future clé dans **les secrets chiffrés du dépôt, avec une copie
  de récupération privée indépendante**. Cela autorise son utilisation dans un
  travail de signature Actions séparé de la compilation, pas sa publication.

La clé de Next 0.3.0 n’est plus accessible dans l’espace de travail. Sa copie
indépendante n’a pas été retrouvée par l’utilisateur. La raison précise de son
absence locale n’est pas établie. L’ancien APK ne contient que le certificat
public : il ne permet pas de reconstruire cette clé privée.

**À ce stade : aucune nouvelle clé créée, aucun certificat remplacé, aucun APK
0.3.1 signé ou remis.** La politique active Next reste celle de 0.3.0. L’accès API
à `repos/gagaladie08-pixel/Nova-Rush/actions/secrets/public-key` retourne
`403 Resource not accessible by integration`. Ne pas répéter ce contrôle sans
changement de droits et ne pas demander de mot de passe, token ou code 2FA dans
le chat. L’accès Git au dépôt fonctionne, mais n’implique pas le droit de gérer
ses secrets Actions.

Le registre public `config/next-signing-custody.json` reste donc à
`pending_secret_access`, sans empreinte de remplacement ni confirmation de copie.
Ce registre ne contient et ne doit contenir aucun secret.

## Pourquoi attendre avant de désinstaller

Next n’a pas d’export intégré de ses données. `android:allowBackup="false"` et les
règles de sauvegarde excluent la sauvegarde/transfert Android. Ne pas promettre
une restauration automatique après désinstallation : favoris, playlists internes,
paroles enregistrées, portraits/corrections et réglages privés peuvent être perdus.
Les fichiers audio du stockage partagé ne sont pas effacés par cette procédure.

Une nouvelle clé ne permet **pas** une mise à jour de Next 0.3.0 par-dessus, même
avec le même `com.novalisten.app.next`. Le changement autorisé est une
**réinstallation**, pas une rotation compatible de signature. Le futur fichier
et ses instructions doivent l’annoncer clairement. Aucun troisième identifiant
applicatif n’est autorisé. Le téléchargement public 0.3.0 reste inchangé.

## Mise en place du coffre, dans cet ordre

1. Ouvrir les [secrets Actions du dépôt](https://github.com/gagaladie08-pixel/Nova-Rush/settings/secrets/actions)
   avec le compte propriétaire. Vérifier que **New repository secret** est
   disponible. Ne pas créer une variable publique ni un fichier dans le dépôt.
   Si la page est inaccessible, résoudre d’abord les droits avec le propriétaire
   ou la connexion du dépôt ; ne pas générer une clé en comptant sur la session
   pour la conserver.
2. Quand la configuration du coffre est effectivement possible, provisionner
   explicitement la nouvelle clé **une seule fois**, conformément à l’autorisation
   de réinstallation. Archiver la politique publique 0.3.0 sans modifier ses
   références historiques. Épingler le nouveau certificat pour Next seulement.
   L’ancien `parallel_signing.py init` doit continuer à refuser toute identité
   déjà provisionnée ; ne pas supprimer ses garde-fous pour contourner le refus.
3. Vérifier réellement la copie de récupération : exporter le certificat **et
   utiliser la clé privée restaurée**, pas seulement comparer des noms de fichiers.
   Le ZIP contient exactement `novalisten-next.p12`, `password.txt`,
   `public-policy.json` et `RESTORE.txt`.
4. Enregistrer le ZIP encodé en base64 dans **un secret Actions**, nommé exactement
   `NOVALISTEN_NEXT_SIGNING_BACKUP_B64`. La base64 n’est pas un chiffrement : ce
   contenu doit être traité comme la clé et son mot de passe. Aucun copier-coller
   dans le chat, une issue, un commit, une variable publique ou des journaux.
   Transférer le contenu par le mécanisme privé prévu ou directement vers l’API
   des secrets, jamais via le pont de blobs publics.
5. La politique de la nouvelle clé devra explicitement autoriser son utilisation
   Actions (`private_key_in_ci: true`). Épingler dans le registre de conservation
   le nouveau certificat et le SHA-256 exact du ZIP ; état
   `awaiting_remote_verification`, copie du propriétaire non encore confirmée.
   Ce changement n’est **pas encore effectué**.
6. Exécuter **NovaListen Next — vérifier la récupération du coffre** sur la
   branche de travail. Le workflow se déclenche manuellement ou par un push
   contenant `[next-vault-check]` ; si le dispatch est interdit, utiliser le push.
   Vérifier le succès et la preuve publique depuis le vrai run Actions, pas
   depuis une variable locale imitant son environnement.
7. Remettre aussi la copie privée de récupération au propriétaire. Obtenir la
   confirmation de son stockage **indépendant du dépôt et de la session** et
   vérifier sa restauration quand possible. Alors seulement passer le registre
   à `ready` avec `owner_recovery_copy_confirmed: true` et répéter le contrôle.
8. Terminer la chaîne de signature séparée avec la même clé récupérée du coffre,
   puis vérifier le vrai APK : identifiant Next, version/code, un seul certificat
   épinglé, alignement et installation/réinstallation sur émulateur. Cette chaîne
   de signature doit exiger ces preuves ; elle n’est pas encore livrée dans cette
   préparation. Ne remettre l’APK installable qu’après ces contrôles. Pour les
   versions suivantes : réutilisation de cette clé, jamais génération automatique.

## Contrôle de récupération ajouté

`scripts/next_key_vault.py verify` et
`.github/workflows/novalisten-next-key-vault.yml` préparent uniquement cette preuve
— **ni génération, ni signature, ni publication d’APK** :

- absence ou invalidité du secret : arrêt, jamais de clé de secours implicite ;
- opt-in Actions explicite dans la politique et registre provisionné requis ;
- archive et certificat comparés aux empreintes publiques épinglées ;
- entrée base64 bornée, archive bornée, noms fixes, doublons, liens symboliques,
  chemins inattendus, compression non admise et tailles décompressées excessives
  refusés avant utilisation de la clé ;
- secret retiré de l’environnement avant les sous-processus ; aucun contenu
  privé, stderr de keytool ou traceback affiché ;
- restauration temporaire sous le répertoire ignoré `private-signing/`,
  répertoires `0700`, fichiers `0600`, nettoyage après succès **et** échec ;
- export du certificat et `keytool -certreq` avec la vraie clé privée restaurée ;
- workflow limité au dépôt et à la branche autorisés, événements push/dispatch,
  permissions `contents: read`, aucun transfert de fichier ni artefact ;
- une réussite locale ne vaut jamais preuve de lecture du secret distant ;
- une récupération depuis le dépôt ne vaut pas confirmation d’une copie du
  propriétaire. Sans les deux, `custody_ready_for_signing` reste faux.

Les secrets Actions sont chiffrés au repos mais un workflow autorisé à les utiliser
peut les lire. Limiter les droits d’écriture du dépôt et auditer les modifications
de workflow ; ne pas exposer le secret aux workflows de contributions non fiables.
La copie privée indépendante protège aussi contre la perte d’accès au dépôt.
Aucun stockage unique ne permet de promettre une disponibilité éternelle.

## Preuves disponibles et limites

- 31 nouveaux tests Python sur fixtures synthétiques ; **80 tests de signature au
  total**, sans génération de vraie clé ni accès réseau pendant les tests.
- Le refus réel de la commande sans secret est vérifié : code de sortie 1, aucun
  répertoire privé créé et aucune donnée privée affichée.
- Le binaire 0.3.1 reste l’intermédiaire **non signé**, issu de la compilation
  [45390401550](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/45390401550)
  au commit `7d75d21`, pas un livrable installable. 288 tests JVM/Robolectric,
  compilation des tests Android et lint sans erreur / 43 avertissements lors de
  ce build. Aucune exécution de la suite instrumentée ni essai téléphone.
- **Pas encore de preuve de récupération d’une nouvelle clé depuis Actions** : le
  coffre n’est pas provisionné. Ne pas présenter les tests synthétiques ou les
  essais historiques 0.3.0 comme cette preuve.
