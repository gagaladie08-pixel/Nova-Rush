# NovaListen Next 0.3.0 — installation parallèle et clé conservée

## Décision explicite

Après information sur l’impossibilité de mettre à jour 0.2.3 sans sa clé originale,
l’utilisateur a choisi **Installer à côté de 0.2.3**. La nouvelle application porte
l’identifiant stable **`com.novalisten.app.next`**, avec le nom de lanceur
**NovaListen Next**. L’ancienne **`com.novalisten.app`** n’est ni remplacée ni
désinstallée. Ses données restent dans son propre espace ; les choix, paroles,
portraits, favoris et réglages ne sont pas transférés automatiquement à Next.

Les deux applications retrouvent les fichiers audio autorisés sur le téléphone,
mais leurs bases SQLite, préférences et clés Android Keystore sont isolées par
Android. Les fonctionnalités 0.3.0 décrites dans le [lot catalogue et artistes](10-catalogue-et-artistes-automatiques.md)
sont les mêmes dans Next ; ce n’est pas l’ancien APK renommé.

## Deux signatures distinctes, sans substitution

- **0.2.3 :** la clé originale reste non retrouvée ; `config/signing-policy.json`
  et ses contrôles restent inchangés. Une mise à jour de l’ancien identifiant
  reste interdite avec une autre clé.
- **Next :** nouvelle clé explicitement autorisée pour l’identifiant distinct.
  Empreinte SHA-256 du certificat :
  `64dd5e7487e743a4af30a8a7c7f045251e54730d01009295783d98ace87fde17`.
- Politique publique épinglée dans `config/parallel-signing-policy.json`.
  PKCS12, RSA 3072, alias `novalisten-next` ; mot de passe aléatoire privé,
  pas le mot de passe debug Android par défaut.
- Aucune génération implicite : si la clé manque, restaurer la sauvegarde.
  Ne jamais réinitialiser l’empreinte pour obtenir un build vert.

## Sauvegarde privée et restauration réellement vérifiée

La clé et son mot de passe sont conservés sous `private-signing/` : répertoires
`0700`, fichiers `0600`, ignorés par Git. Une archive de récupération privée
contient une copie PKCS12, le mot de passe, la politique publique et les
instructions de restauration. **Elle ne doit jamais être publiée** dans le dépôt,
une release, un ticket, des journaux ou des artefacts GitHub.

La sauvegarde a été reconstruite dans un répertoire temporaire : export du
certificat identique **et utilisation de la clé privée restaurée** par `keytool
-certreq`. Il ne s’agit pas seulement d’une copie du certificat public. L’utilisateur
a téléchargé l’archive et confirmé sa conservation indépendante dans le dialogue
**Oui, sauvegarde conservée**. La signature locale exige cette confirmation liée
à l’empreinte et au SHA-256 exacts de l’archive. Conserver idéalement une deuxième
copie hors ligne dans un coffre privé ; la session seule n’est pas une sauvegarde
indépendante et aucune conservation éternelle n’est promise.

## Chaîne sans clé privée en CI

L’accès aux secrets du dépôt répond 403 avec l’intégration actuelle. Aucun secret
n’est demandé dans le chat et aucune clé n’est déposée dans GitHub pour contourner
cette limite. Pour un futur coffre CI, il faudra réautoriser la connexion GitHub
avec les droits adaptés ; cette intégration n’est pas nécessaire à la signature
locale décrite ici.

1. `-PnovalistenParallel=true` choisit l’identifiant et le nom distincts.
2. `NOVALISTEN_PREPARE_PARALLEL=true` autorise exclusivement la préparation de
   l’APK **debug non signé de Next**. Il refuse l’identifiant historique et tout
   autre graphe de signature, installation ou variante. Le mode sources seules
   continue d’interdire tout APK, y compris Next.
3. La CI exécute intégrité, tests Python, tests JVM, compilation des tests Android
   et lint, puis assemble l’intermédiaire sans clé.
4. `aapt2` contrôle l’identifiant/nom/version/minSdk de ce vrai binaire ;
   `apksigner` doit constater l’absence de signature. `zipalign` aligne et vérifie
   le fichier. L’absence de `~/.android/debug.keystore` est contrôlée.
5. Seuls cet intermédiaire, ses métadonnées hachées et l’outil public apksigner
   peuvent emprunter le pont de transfert à liste fixe. Les fichiers privés
   ne sont jamais parcourus ni transmis.
6. `scripts/parallel_signing.py sign` vérifie la clé, la restauration de sa copie,
   la confirmation de sauvegarde et le hash de l’intermédiaire avant signature
   locale v2/v3. `apksigner verify` doit retrouver un seul signataire : Next.
7. Seul l’APK signé et vérifié peut être remis. Ni APK non signé, ni archive
   privée ne doivent être confondus avec le livrable installable.

Les résultats de compilation et les contrôles du binaire seront ajoutés après leur
exécution. Les anciens résultats instrumentés 0.2.3 ne prouvent pas le rendu Next.
