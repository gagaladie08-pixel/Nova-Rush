# Prochains APK : conserver la signature de 0.2.3

## État : livraison protégée, clé privée originale non retrouvée

La demande est de conserver **exactement le certificat de l’APK 0.2.3 déjà livré**, afin de permettre les prochaines mises à jour sans désinstaller l’application ni effacer ses données privées.

Référence publique, dans `config/signing-policy.json` :

- Certificat SHA-256 : `9c6531e77ff9c802dc5622d2d041b07812e5790a56b159c27502214d4ff77d95`.
- APK de référence : `NovaListen-0.2.3-debug.apk`, SHA-256 `4992b71f5634b90338504211e25541e8cbde5aa6c577a0c79e229996d0f87612`.
- Sources : `386642a01593a6bea0bb5ecb0e9d891ac0873049` ; [exécution originale 44930520156](https://github.com/gagaladie08-pixel/Nova-Rush/actions/runs/44930520156).

**L’empreinte du certificat et celle du fichier APK sont deux valeurs différentes.** L’APK contient le certificat public, pas la clé privée nécessaire à une nouvelle signature. Recréer un keystore avec le même nom, alias et mot de passe ne recrée pas cette clé.

Aucun nouveau build Android, aucune nouvelle clé et aucune publication d’APK n’ont été lancés pour ce changement. L’APK 0.2.3 et ses anciennes preuves ne sont pas modifiés.

## Audit dédié à la clé 0.2.3

- Aucun fichier candidat `.keystore`, `.jks`, `.p12` ou `.pfx` retrouvé dans l’espace de travail, le répertoire Android local, le répertoire Gradle local ou les fichiers temporaires examinés. Aucun chemin de keystore suivi trouvé dans l’historique Git accessible.
- Deux artefacts déclarés par l’exécution originale : l’APK et les rapports. L’archive locale des rapports a été inventoriée : **333 entrées, aucun nom de keystore**. Les scripts de transfert ne sauvegardaient que l’APK, les journaux et les rapports, pas la clé privée.
- Les sources exactes du build de référence ne restauraient ni ne sauvegardaient de clé ; Gradle utilisait sa signature debug par défaut.
- **43 caches Actions inventoriés**, dont le cache Gradle de ce commit. Le contenu binaire de ces caches n’a pas été téléchargé. Le [descripteur de `setup-gradle@v6`](https://github.com/gradle/actions/blob/v6/setup-gradle/action.yml) limite ses inclusions par défaut à `caches` et `notifications` dans le répertoire utilisateur Gradle ; le workflow n’ajoutait pas le répertoire Android contenant normalement `debug.keystore`.
- L’API de liste des secrets a répondu **403, accès insuffisant**. Cela ne démontre pas qu’un secret est absent ; leur contenu n’a pas été demandé ni affiché. Aucun environnement Actions déclaré.

Les détails non sensibles sont conservés localement dans `artifacts/signature-audit-0.2.3.json` (ignoré par Git). Cette recherche est distincte de celle de la clé 0.2.0.

**Résultat : la clé privée de 0.2.3 n’est pas retrouvée. Une prochaine mise à jour compatible n’est donc pas encore garantie.** Il ne faut pas annoncer qu’une nouvelle clé est équivalente, ni présenter la simple conservation de l’APK comme une sauvegarde de la clé.

## Garde-fous ajoutés

1. La CI exige le secret Actions `NOVALISTEN_DEBUG_KEYSTORE_BASE64`, qui devra contenir **le keystore original retrouvé**, pas une clé nouvellement générée. Base64 est un encodage, pas un chiffrement ; le fichier ne doit jamais être ajouté au dépôt, au chat ou à un artefact public.
2. `scripts/signing_policy.py prepare` refuse une valeur absente/invalide. Il restaure temporairement le fichier **hors du dépôt**, avec répertoire privé `0700` et fichier `0600`.
3. `keytool` doit exporter un certificat dont le SHA-256 correspond exactement à la référence, puis réussir une demande de certificat utilisant **la clé privée existante**. Un magasin ne contenant que le certificat public ne suffit pas. Aucune génération de clé n’est prévue.
4. Gradle reçoit uniquement le chemin de cette copie. En CI, un chemin absent est bloquant ; un chemin fourni doit désigner un fichier existant. Aucun repli automatique sur la clé debug d’un nouveau runner n’est autorisé en CI.
5. Après compilation, `apksigner verify --verbose --print-certs` doit valider la signature réelle de l’APK et retourner **un seul signataire, celui de 0.2.3**, avant sa copie parmi les livrables ou son transfert.
6. La copie temporaire est supprimée après les opérations Gradle, même en cas d’échec. Ni son contenu ni son encodage ne sont journalisés ou publiés.

**Tant que le vrai keystore n’est pas disponible, la CI s’arrêtera avant la compilation.** Il ne faut pas supprimer le contrôle ou remplacer l’empreinte pour obtenir artificiellement un build vert.

La signature locale par défaut reste possible pour le développement hors CI, mais un tel APK n’est pas un livrable compatible : il ne doit pas être remis comme mise à jour de 0.2.3. Toute livraison doit passer par le contrôle du certificat de référence.

## Vérifications réalisées et limites

- **16 tests Python synthétiques** : secret absent/invalide/trop volumineux, permissions du fichier, certificat différent, certificat sans clé privée, échec d’outil, nettoyage, absence d’export de données sensibles, APK absent/invalide et signataires absents/différents/multiples.
- Contrôle d’intégrité du projet et vérification des différences Git.
- Les réponses de `keytool` et d’`apksigner` sont simulées dans ces tests. Le JDK et le SDK Android ne sont pas disponibles localement : **pas de nouvelle exécution Gradle, de validation réelle de keystore ou d’installation de mise à jour**. Les résultats Android historiques de 0.2.3 ne valident pas ces nouveaux changements de CI.

Commandes hors compilation :

```sh
python3 -m unittest discover -s scripts -p 'test_signing_policy.py' -v
python3 scripts/verify-project.py
git diff --check
```

## Déblocage nécessaire

Retrouver une sauvegarde du keystore original et la conserver dans un emplacement sécurisé. Une fois vérifiée, elle pourra alimenter le secret Actions prévu ; garder aussi une sauvegarde privée indépendante. Ne jamais demander sa valeur dans le chat.

Si la clé originale ne peut pas être récupérée, signer un nouvel APK avec ce même certificat n’est pas possible. Une autre clé serait une rupture de compatibilité qui exige une décision explicite distincte ; **aucune désinstallation, remise à zéro des données ou substitution de clé n’est autorisée par cette demande**.
