# Mises à jour : chaque compilation s’installe par-dessus

## La règle, en une phrase

Tant que **trois valeurs** restent identiques, Android installe la nouvelle version par-dessus l’ancienne sans rien effacer : le **certificat de signature**, l’**applicationId** et un **versionCode croissant**.

## Les trois valeurs verrouillées

| Valeur | Actuelle | Où elle vit |
| --- | --- | --- |
| Certificat | `c38a837ee566ad95000af71cb13497e3d330ebcde5c6c8fcc4880265a77c68ad` | `config/signing-policy.json`, keystore `config/signing/NovaListen-release.p12` |
| applicationId | `com.novalisten.app.next` | `app/build.gradle.kts` |
| versionCode | 7 (0.3.1) | `app/build.gradle.kts` |

La clé est **versionnée** : elle survit aux sessions et aux nouvelles machines. La preuve a déjà été faite — supprimée localement, elle a été restaurée depuis Git à l’identique (SHA-256 `45fade40…`).

## Ce qui bloque désormais une compilation

`scripts/check_update_continuity.py` s’exécute **avant** la compilation dans le workflow de livraison et refuse de signer si :

- le keystore a changé, disparu, ou ne correspond plus au SHA-256 épinglé ;
- l’applicationId a été modifié (ce serait une autre application, pas une mise à jour) ;
- le certificat épinglé diffère de celui de la dernière version livrée ;
- le versionCode est **inférieur** à celui déjà livré.

Il **avertit** (sans bloquer) quand le versionCode est égal : Android accepte alors la réinstallation, mais ce n’est pas une nouvelle version.

## Ce qui doit être fait à chaque nouvelle version

1. Incrémenter `versionCode` (8, 9, 10…) et `versionName` (0.3.2, 0.3.3…) dans `app/build.gradle.kts`.
2. Ne jamais toucher au keystore ni au mot de passe : ils sont l’identité de l’application.
3. Ne jamais changer `applicationId`.
4. Laisser la CI vérifier : `verify-keystore`, puis `verify-apk` sur un seul signataire, puis le contrôle de continuité.
5. La livraison enregistre son manifeste dans `apk-livraison/<sha256>/manifeste.json`, qui sert de référence au contrôle suivant.

## Vérifier soi-même la continuité

```sh
python3 scripts/check_update_continuity.py            # échoue si la mise à jour serait refusée
python3 scripts/check_update_continuity.py --report    # affiche seulement le diagnostic
python3 -m unittest scripts/test_update_continuity.py -v
```

## Limites à connaître

- **Cette clé est l’identité définitive.** On ne peut plus en changer sans une nouvelle réinstallation complète, sauf si Android abandonne un jour la vérification de signature (`apk install --dont-kill` ou les tests avec `-t` ne changent rien à cela).
- **La clé actuelle est publique**, car elle a été versionnée alors que le dépôt était encore public : quelqu’un pourrait signer un faux APK qui s’installerait par-dessus cette application. Voir la section « Dette de sécurité » du README avant de distribuer l’application au-delà de ton téléphone.
- **La réinstallation 0.2.3 / Next 0.3.0 → 0.3.1 est unique** : ces deux versions ont d’autres certificats. Après 0.3.1, plus aucune réinstallation ne sera nécessaire.
