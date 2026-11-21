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

## Binaire signé remis

- Fichier : **`NovaListen-Next-0.3.0-debug.apk`**, **89 793 676 octets** (89,8 Mo).
- Version **0.3.0 / code 6**, Android **8+ (API 26)**, cible API 35.
- SHA-256 du fichier : `3a15fc41a8beffcb02c262b6759867df6c4113262f3812a99811fce6f01c52ec`.
- Certificat : celui de Next épinglé ci-dessus ; **un seul signataire, v2 + v3**.
  Ni la signature 0.2.3 ni un keystore debug implicite n’ont été employés.
- Sources exactes du binaire : `b65348dc73075eb2a21b74cdcfe319b769175e8b`.
  La correction locale du passage du mot de passe PKCS12 et les références de
  cet APK sont dans `ffbc43671ae710c9efcbc5d7bb05067eb9089339` ; pas de recompilation
  ni de remplacement du binaire après signature/essais.
- Inspection indépendante du manifeste binaire et de son certificat avec
  Androguard, puis vérification cryptographique par apksigner. Nom de lanceur,
  activité réelle, version, niveaux SDK et permissions contrôlés ; aucune clé
  privée, mot de passe de signature ou musique de démonstration embarqués.

L’APK signé est remis comme fichier dans la session. Pour récupérer exactement
ce binaire public par l’API GitHub, avec GitHub CLI configuré :

```sh
python3 scripts/parallel_install_smoke.py fetch
# .cache/next-device-check/NovaListen-Next-0.3.0-debug.apk
```

`config/next-apk-under-test.json` épingle son SHA-256, son certificat, son build et
ses parties publiques ; le téléchargement vérifie chaque partie puis le tout.
Ce mécanisme ne lit ni ne transfère la sauvegarde privée de signature.

## Résultats vérifiés

- [Compilation Next 44949752140](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44949752140) : **240 tests JVM et 49 tests Python réussis**, compilation des tests Android, lint **42 avertissements / zéro erreur**, contrôle de l’identifiant réel, de l’alignement et de l’absence de signature/clé implicite avant signature locale.
- [Sources seules 44949752175](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44949752175), puis [44950357594](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44950357594) au commit de l’outil final : **240 tests JVM + 49 tests Python réussis**, compilation des tests Android, lint 41 avertissements / zéro erreur et refus des assemblages historiques vérifiés. Le 42e avertissement Next est la ressource de l’ancien nom `app_name` devenue inutilisée par son nom de lanceur distinct.
- [Installation côte à côte 44950357575](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44950357575) : **Android 10 / API 29** (job `136986697682`) et **Android 15 / API 35** (job `136986698030`) réussis sur émulateurs. C’est le **même APK signé et haché** que celui remis, pas une reconstruction de test.
- Sur chaque émulateur : certificats des deux vrais APK vérifiés, installation de 0.2.3 puis Next sans désinstallation, deux UID Android distincts, fichier synthétique privé de 0.2.3 préservé, réinstallation `-r` **du même APK Next signé** préservant aussi son fichier privé, lancement de l’activité Next réussi, navigation Bibliothèque visible et aucun plantage de démarrage Next détecté.

Les rapports téléchargés/hachés sont conservés sous `artifacts/ci-44949752140/`,
`artifacts/ci-44949752175/`, `artifacts/ci-44950357594/` et
`artifacts/ci-44950357575/` (ignorés par Git), avec
`artifacts/APK-INFO-Next-0.3.0.json` et la somme du fichier remis.

**Limites :** ces deux essais d’installation/démarrage ne sont **pas la suite
instrumentée complète** ni un essai des galeries/API réelles. Les tests Android
complets de 0.3.0 sont compilés, pas exécutés. Aucun essai sur le téléphone de
l’utilisateur, aucune migration automatique des données 0.2.3 vers Next ni
validation d’une future version différente ne sont prétendus. Les anciens
résultats instrumentés 0.2.3 restent historiques et distincts.
