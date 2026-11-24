# Signature unique — méthode Nova Rush

## Le problème, en une phrase

Les APK 0.2.0, 0.2.1, 0.2.2, 0.2.3, Next 0.3.0 et la préparation 0.3.1 ont été signés par **six certificats différents**. Sans clé conservée, la CI créait à chaque fois un `~/.android/debug.keystore` neuf ; Android refuse alors d’installer une version par-dessus une autre (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Nova Rush n’a jamais eu ce problème parce que sa clé, `android/app/nova-rush-test.p12`, était versionnée : même certificat à chaque build.

## La décision

Appliquer la méthode Nova Rush à NovaListen Next : **une seule clé, conservée dans le dépôt, réutilisée à chaque version.**

| Élément | Valeur |
| --- | --- |
| Keystore | `config/signing/NovaListen-release.p12` (PKCS12, RSA 3072, alias `novalisten-release`) |
| Mot de passe | `config/signing/signing-password.txt` (32 caractères aléatoires, fichier séparé) |
| Certificat SHA-256 | `c38a837ee566ad95000af71cb13497e3d330ebcde5c6c8fcc4880265a77c68ad` |
| Keystore SHA-256 | `45fade40c40095b84b58809b44cd2a187ab38a8d641d00a95379c2c4fa206625` |
| Validité | 10 000 jours (jusqu’en 2054) |
| Identifiant | `com.novalisten.app.next`, nom « NovaListen Next » |
| Première version | 0.3.1 / code 7 |

Le certificat et l’empreinte du fichier sont épinglés dans `config/signing-policy.json`. Ils sont deux valeurs différentes : le premier identifie la clé, le second détecte son remplacement.

### Condition absolue : dépôt privé

Une clé versionnée n’est acceptable que si le dépôt est privé. Dans un dépôt public, n’importe qui pourrait signer un APK qui s’installe par-dessus la vraie application. En conséquence :

- le job de livraison `.github/workflows/novalisten-apk.yml` porte `github.event.repository.private == true` ;
- `config/signing/LISEZ-MOI.txt` rappelle la règle aux futurs contributeurs ;
- une clé devenue publique doit être considérée comme compromise : elle ne peut plus servir, une nouvelle identité et une réinstallation seraient nécessaires.

## Garde-fous (fail-closed)

1. **Aucune génération implicite.** `scripts/signing_policy.py verify-keystore` refuse un keystore absent, un fichier dont le SHA-256 diffère, un certificat différent, ou une clé privée inutilisable (`keytool -certreq` doit réussir). Aucune commande `-genkeypair` n’existe.
2. **Le mot de passe ne circule pas.** Il est transmis à `keytool` par `-storepass:file` dans un répertoire temporaire `0600`, jamais sur la ligne de commande, jamais journalisé.
3. **Gradle bloque avant de signer.** `app/build.gradle.kts` vérifie la présence du keystore et du mot de passe dans le graphe de tâches, pour toute tâche qui produit un binaire. Un keystore déplacé fait échouer la compilation au lieu de basculer sur une clé debug.
4. **Les deux variantes utilisent la même clé.** `debug` et `release` pointent sur la configuration `delivery` : aucune variante ne peut produire un binaire signé autrement.
5. **`apksigner` décide de la livraison.** `verify-apk` exige exactement un signataire, celui du certificat épinglé.
6. **La clé doit rester dans Git.** `.gitignore` n’exclut plus `*.p12` / `*.jks` / `*.keystore` ; le workflow de vérification des sources refuse la CI si le keystore redevient ignoré ou non suivi.
7. **Le mode « sources seules » reste sans signature** (`NOVALISTEN_VERIFY_SOURCES=true`), pour les contrôles qui ne doivent produire aucun binaire.

## Ce qui a été retiré

L’ancienne machinerie de conservation de secours n’a plus d’objet et est supprimée de cette branche : `scripts/parallel_signing.py`, `scripts/prepare_parallel_build.py`, `scripts/parallel_install_smoke.py`, leurs tests, `config/parallel-signing-policy.json`, ainsi que les workflows `novalisten-parallel-apk.yml`, `novalisten-next-device-check.yml` et `novalisten-next-download.yml` (épinglé à l’ancien binaire 0.3.0).

## Migration — une seule réinstallation, la dernière

| APK | Certificat | Statut |
| --- | --- | --- |
| 0.2.3 | `9c6531e7…` | figé, clé perdue, ne peut plus être mis à jour |
| Next 0.3.0 | `64dd5e74…` | sauvegarde introuvable, remplacé |
| 0.3.1 préparé | `e59d21a2…` | abandonné avant livraison, fichier privé disparu |
| **0.3.1 livré** | **`c38a837e…`** | identité définitive |

Procédure, à faire uniquement quand l’APK 0.3.1 signé est disponible et vérifié :

1. Noter ce qui doit éventuellement être recréé (favoris, playlists, réglages).
2. Désinstaller **NovaListen Next** (`com.novalisten.app.next`) — **pas** `com.novalisten.app`.
3. Installer le nouvel APK 0.3.1.
4. Vérifier que les versions suivantes s’installent par-dessus sans désinstallation.

Les fichiers audio du stockage partagé ne sont pas touchés. `android:allowBackup="false"` : aucune restauration automatique des données internes n’est possible.

## Limites honnêtes

- Aucune compilation Android n’est possible dans la session de travail actuelle (pas de JDK ni de SDK Android) : la configuration Gradle modifiée n’a **pas** été exécutée. Elle doit être validée par le premier run CI.
- La clé a été générée localement avec OpenSSL ; `keytool` tourne sur le runner CI (temurin 17) et c’est ce run qui constitue la première preuve réelle.
- Un dépôt privé n’est pas une sauvegarde éternelle : conserver une copie du keystore et du mot de passe hors de GitHub reste prudent, même si ce n’est plus la condition de survie de la clé.
