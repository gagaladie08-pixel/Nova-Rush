# Nouvelle signature Next — conservation durable avant réinstallation

## Autorisations et état réel

L’utilisateur a confirmé, après avertissement sur les données :

- **Réinstaller NovaListen Next uniquement** avec une nouvelle signature stable.
- **Ne pas toucher à NovaListen 0.2.3**, à son identifiant, à sa politique de clé,
  à son APK publié ou à ses données.
- Conserver la nouvelle clé dans **les secrets chiffrés du dépôt, avec une copie
  privée indépendante**. Cela autorise son utilisation dans un travail de signature
  Actions séparé de la compilation, pas sa publication.
- La page propriétaire des secrets est accessible et le bouton **New repository
  secret** est visible. Le secret doit être ajouté manuellement : l’API de
  l’intégration retourne toujours `403 Resource not accessible by integration`.
  Ne pas demander de mot de passe, token ou code 2FA dans le chat.

La clé de Next 0.3.0 n’est plus accessible. Sa copie indépendante n’a pas été
retrouvée par l’utilisateur. La raison précise de son absence locale n’est pas
établie. L’ancien APK ne permet pas de reconstruire sa clé privée.

**Une nouvelle clé a été créée une seule fois, après ces confirmations.** Sa copie
a été restaurée et réellement utilisée localement. **La lecture depuis Actions et
la copie indépendante du propriétaire ne sont pas encore confirmées. Aucun APK
0.3.1 signé ou remis.** Le registre reste à `awaiting_remote_verification`.

**Dernier contrôle après confirmation de l’ajout du secret :** le secret est
présent, mais sa valeur ne se décode pas en base64 valide. Le run
[45450316869](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/45450316869)
au commit `b1221d4` échoue avec ce diagnostic même après acceptation des seuls
espaces ASCII et retours à la ligne de copier-coller. Aucun caractère invalide
n’est ignoré, et le hash exact du ZIP reste obligatoire. Le contenu n’est jamais
affiché ; seules des erreurs fixes et assainies sont exposées en annotations.

Les fichiers privés créés précédemment ne sont **plus présents dans l’espace
local actuel**, contrairement aux fichiers sources retrouvés identiques à la
branche distante. Ne pas prétendre les rouvrir, ne pas régénérer la clé épinglée
et ne pas passer le registre à `ready`. Pour corriger le secret, retrouver le
contenu du TXT d’origine ou sa sauvegarde privée auprès du propriétaire. GitHub
ne permet pas de relire un secret en clair par son API de gestion. Une lecture
réussie par Actions devra toujours être prouvée avant toute signature/livraison.

## Empreintes publiques et séparation des historiques

- Nouvelle politique, à partir de 0.3.1 : `config/next-signing-policy.json`.
- Identifiant inchangé : `com.novalisten.app.next` ; alias `novalisten-next`,
  PKCS12 / RSA 3072, mot de passe privé aléatoire.
- **Nouveau certificat stable** :
  `e59d21a28a6a32f335248e742a319e9e82a1b871d60cd2d3f703f6dc1798644f`.
- **SHA-256 de la nouvelle sauvegarde privée** :
  `2365a6d5713021eb0a7bfb3d668ea9044edf204772e8ef6a4ca36b6e2efe33bd`.
- Ancienne politique Next 0.3.0 : `config/parallel-signing-policy.json`,
  certificat `64dd5e7487e743a4af30a8a7c7f045251e54730d01009295783d98ace87fde17`,
  **inchangée**. Les scripts/workflows et l’index de téléchargement 0.3.0 restent
  épinglés à cet ancien binaire et à cette politique historique.
- `config/signing-policy.json` (0.2.3), les fichiers applicatifs, les publications
  et leurs assets restent inchangés. Aucun troisième identifiant applicatif.
- `config/next-signing-custody.json` ne contient que ces empreintes publiques et
  les états de vérification, jamais une clé, un mot de passe ou une archive.

Les nouveaux privés sont isolés sous le répertoire ignoré `private-signing/` :

- `novalisten-next-0.3.1/{novalisten-next.p12,password.txt}` ;
- `NovaListen-Next-0.3.1-signature-backup.zip` ;
- `NovaListen-Next-0.3.1-GitHub-secret.txt` (valeur privée à copier uniquement dans
  le champ **Secret**, pas dans **Name**, une variable publique ou le chat).

Le ZIP et sa base64 contiennent **la clé ET son mot de passe**. Ils ne sont pas
chiffrés par eux-mêmes. Répertoires `0700`, fichiers `0600`, aucun transfert via le
pont de blobs publics et aucune inclusion dans les artefacts de compilation.

## Pourquoi attendre avant de désinstaller

Next n’a pas d’export intégré de ses données. `android:allowBackup="false"` et les
règles de sauvegarde excluent la sauvegarde/transfert Android. Ne pas promettre
une restauration automatique après désinstallation : favoris, playlists internes,
paroles enregistrées, portraits/corrections et réglages privés peuvent être perdus.
Les fichiers audio du stockage partagé ne sont pas effacés par cette procédure.

Cette nouvelle clé ne permet **pas** une mise à jour de Next 0.3.0 par-dessus,
même avec le même identifiant. Le changement autorisé est une **réinstallation**,
pas une rotation compatible de signature. Le futur APK et ses instructions devront
l’annoncer clairement. **Attendre le nouvel APK vérifié avant de désinstaller.**

## Terminer le stockage durable avant toute livraison

1. Dans les [secrets Actions du dépôt](https://github.com/gagaladie08-pixel/Nova-Rush/settings/secrets/actions),
   ajouter un **repository secret** nommé exactement
   `NOVALISTEN_NEXT_SIGNING_BACKUP_B64`. Coller dans son champ **Secret** le contenu
   complet du fichier privé `NovaListen-Next-0.3.1-GitHub-secret.txt`. Ne jamais le
   coller dans le chat, le code, une issue ou une variable publique.
2. Après confirmation de l’ajout, exécuter **NovaListen Next — vérifier la
   récupération du coffre**. Déclenchement par push `[next-vault-check]` sur la
   branche de travail ou workflow dispatch si permis. Le dispatch de l’intégration
   est actuellement refusé ; ne pas le répéter sans changement de droits.
3. Vérifier le succès et les empreintes dans le **vrai run Actions**, via l’API du
   dépôt. Une variable locale imitant Actions ne prouve aucune persistance.
4. Remettre aussi le ZIP privé au propriétaire et obtenir la confirmation de son
   stockage indépendant du dépôt **et** de la session, idéalement dans un coffre
   privé et une seconde copie hors ligne. Une copie perdue dans Téléchargements
   seule ne constitue pas la stratégie de récupération retenue.
5. Alors seulement passer le registre à `ready`, copie du propriétaire confirmée,
   et répéter la récupération distante. Ne pas inventer une confirmation de copie.
6. Terminer la chaîne de signature séparée avec la même clé récupérée du coffre,
   puis vérifier le vrai APK : identifiant Next, version/code, un seul certificat
   épinglé, alignement et installation/réinstallation sur émulateur. Cette chaîne
   doit exiger ces preuves ; elle n’est pas encore livrée dans cette préparation.
   Ne remettre l’APK installable qu’après les contrôles. Pour toutes les versions
   suivantes : réutiliser cette clé, jamais en générer une automatiquement.

## Provisionnement explicite, jamais un fallback

`scripts/next_signing_identity.py provision-reinstall` exige l’ancienne empreinte
Next exacte et deux confirmations explicites : perte des données Next acceptée et
capacité propriétaire à créer le secret. Il est **interdit dans Actions**.

Une identité nouvelle existante, une sauvegarde, un payload ou même un répertoire
partiellement créé bloque toute nouvelle génération. Ne pas effacer ces verrous
pour « réparer » un build. Le script ne modifie pas les deux politiques historiques
ni les anciens chemins privés. Il crée une politique distincte et vérifie la vraie
clé et sa copie restaurée avant de produire le fichier privé destiné au coffre.

## Contrôle de récupération

`scripts/next_key_vault.py verify` et
`.github/workflows/novalisten-next-key-vault.yml` ne font **ni génération, ni
signature, ni publication d’APK** :

- secret absent/invalide : arrêt, jamais de clé implicite ;
- politique 0.3.1 explicite, empreinte de certificat et hash du ZIP épinglés ;
- base64 et archive bornées ; noms fixes, doublons, liens symboliques, chemins
  inattendus, compression non admise et tailles décompressées excessives refusés ;
- secret retiré de l’environnement avant les sous-processus ; aucun contenu privé,
  stderr de keytool ou traceback affiché ;
- restauration temporaire sous le répertoire ignoré `private-signing/`,
  répertoires `0700`, fichiers `0600`, nettoyage après succès **et** échec ;
- export du certificat **et** `keytool -certreq` avec la vraie clé privée restaurée ;
- dépôt/branche autorisés, événements push/dispatch, `contents: read`, pas d’upload ;
- réussite locale distincte d’une lecture du secret distant ; récupération depuis
  le dépôt distincte de la copie indépendante du propriétaire. Sans les deux,
  `custody_ready_for_signing` reste faux.

Les secrets Actions sont chiffrés au repos mais un workflow autorisé peut les lire.
Limiter les droits d’écriture et auditer les modifications de workflow ; ne pas
exposer le secret aux contributions non fiables. La copie indépendante protège
aussi contre la perte d’accès au dépôt. Aucun stockage unique ne permet de promettre
une disponibilité éternelle.

## Preuves disponibles et limites

- **102 tests Python de signature locaux réussis**, dont 53 nouveaux tests de
  conservation/provisionnement sur fixtures synthétiques. Aucune vraie clé ni
  requête réseau pendant ces tests. La création réelle autorisée est distincte.
- Récupération réelle locale de la nouvelle clé : certificat et CSR vérifiés avec
  Java/keytool ; contenu du fichier destiné au secret décodé/restauré et utilisé
  à nouveau. `durable_secret_read_verified` reste **faux**, aucune signature d’APK.
- Validation des sources au commit `b1221d4` :
  [45450316847](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/45450316847),
  job `138645538574`, succès en 3 min 8 s. 102 tests Python, 288 JVM/Robolectric
  (22 suites, zéro échec/erreur/ignoré), lint zéro erreur / 43 avertissements,
  tests Android compilés seulement. Rapports téléchargés et hachages vérifiés.
  Ce succès ne valide pas le secret : le contrôle du coffre est un run distinct
  et reste en échec pour format invalide.
- Validation précédente du vérificateur au commit `96f0787` :
  [45395059786](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/45395059786),
  80 tests Python + 288 JVM/Robolectric, lint zéro erreur / 43 avertissements,
  compilation des tests Android, aucun APK.
- L’intermédiaire 0.3.1 reste **non signé**, compilation
  [45390401550](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/45390401550)
  au commit `7d75d21`, pas un livrable installable. Les sources de l’application
  n’ont pas changé. Aucune suite instrumentée exécutée ni essai téléphone.
- **Pas encore de récupération de la nouvelle clé depuis Actions ni de copie du
  propriétaire confirmée.** Ne pas présenter les essais synthétiques, la preuve
  locale ou les anciens essais 0.3.0 comme ces validations manquantes.
