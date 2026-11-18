# NovaListen — Traduction française des paroles : choix et fonctionnement validés

> **Statut :** choix de conception **validé par l’utilisateur le 17 novembre 2026**, par « Oui je valide » en réponse à la proposition du duo **ML Kit par défaut en local + Azure Translator standard F0 uniquement sur demande explicite**. « Allons y » avait ouvert la discussion ; cette confirmation ultérieure retient les moteurs et leurs rôles. Aucun compte, clé, téléchargement, essai, envoi de texte ou activation de facturation n’est lancé par cet accord.
> **Cadre :** API gratuites uniquement, usage personnel, compte développeur gratuit acceptable en principe ; voir la [fiche API et enrichissements](13-api-et-enrichissements.md). Un crédit d’essai non renouvelable, un logiciel gratuit à héberger et une offre gratuite récurrente sont trois situations différentes.
> **Liens :** complète le [parcours Paroles, VO/FR et synchronisation](04-paroles.md). La [cascade de récupération des paroles](14-cascade-api-paroles.md) est déjà validée au niveau de son architecture ; elle ne sélectionne pas un moteur de traduction.

## 1. Ce qui est acquis et ce qui ne l’est pas

- Bouton pour traduire les paroles choisies en français ; original conservé, VF sauvegardée et bascule réversible, une seule version affichée à la fois.
- Relecture des données sauvegardées hors ligne lorsque les droits le permettent, sans nouvelle traduction à chaque bascule VO/FR.
- Conservation des associations temporelles utiles ; la VF ne devient pas un alignement phonétique exact sur un chant dans une autre langue.
- Pas d’abonnement ou d’appels facturés, pas de bascule automatique vers une offre payante.
- Anglais, français, coréen et espagnol sont les langues déclarées de la collection ; il ne s’agit pas d’un inventaire ou d’une garantie de qualité.
- **Moteurs et stratégie de traduction acquis : ML Kit par défaut en local ; Azure Translator standard F0 comme alternative uniquement sur demande explicite.** Aucune bascule automatique vers le distant après refus de packs, erreur locale ou résultat jugé insuffisant ; aucun recours payant. Ce choix de traduction est distinct de la priorité locale déjà acquise pour l’analyse de Fil Rouge.
- **Portée :** réutiliser une VF déjà sauvegardée, traduire localement si les packs sont disponibles, permettre une autre traduction via Azure sur commande, sauvegarder la VF retenue sans écraser la VO. Intégration, versions, packs requis, UX détaillée de téléchargement et de consentement, authentification, droits et qualité restent à vérifier ou préciser avant activation.
- EmbeddingGemma 300M et multilingual-e5-small sont les modèles retenus pour Fil Rouge, **pas les moteurs de traduction de NovaListen**. Leur téléchargement facultatif ne choisit ni ne télécharge des packs ML Kit.

## 2. Comparaison des principales options

Les appréciations ci-dessous sont un avis de conception, pas un classement mesuré de fidélité sur des chansons.

| Solution | Gratuité et accès documentés | Atout pour NovaListen | Limite déterminante |
| --- | --- | --- | --- |
| **ML Kit — traduction classique sur l’appareil** | SDK proposé sans frais ; packs de langues à télécharger, puis traitement local. Pas un quota de caractères cloud à renouveler. | Autonomie hors ligne pour produire de nouvelles VF après disponibilité des packs ; pas d’envoi du texte pour calculer la traduction. | Google décrit une traduction simple et courante, sans fidélité poétique garantie ; coréen → français et espagnol → français passent par l’anglais. Diagnostics du SDK distincts du contenu traduit. |
| **Azure Translator — texte standard, niveau F0** | Offre gratuite récurrente publiée à **2 millions de caractères par mois**, avec compte Azure, ressource et authentification. | Alternative en ligne retenue sur demande pour EN/KO/ES → FR ; quota mensuel utile sans assimiler un nombre de caractères à un nombre garanti de chansons. | Connectivité, conditions d’inscription et gestion de clé à examiner ; sélectionner réellement F0, pas S1, un service documentaire payant ou une offre LLM. Aucune qualité supérieure mesurée ici. |
| **DeepL** | L’aide officielle indique que l’ancien **API Free à 500 000 caractères/mois n’est plus ouvert à la souscription**. Le plan Developer gratuit propose **1 million de caractères au total, non renouvelable**. | Peut intéresser une comparaison ultérieure si un accès compatible existe. | Ne pas le recommander comme nouveau moteur gratuit mensuel durable sur la base d’anciens comparatifs. Aucun compte ancien de l’utilisateur n’est supposé. |
| **LibreTranslate / Argos** | Code libre et auto-hébergement possible sans redevance logicielle ; le serveur officiel libretranslate.com demande une clé payante pour cet usage d’API. | Option d’indépendance à étudier si un déploiement maîtrisé est souhaité. | Hébergement, modèles et maintenance ne sont pas gratuits par définition ; ce serveur Python n’est pas un SDK Android prêt à intégrer. Les instances publiques ont leurs propres limites, langues et conditions. |
| **MyMemory** | Documentation : **5 000 caractères/jour** en anonyme, **50 000 avec un courriel de contact valide** ; maximum **500 octets UTF-8 par requête**. | Accès simple pour des segments courts. | Limites peu adaptées au contexte d’un couplet ; conditions de conservation et de réutilisation de segments à examiner. Pas mon premier choix pour ces paroles. |
| **Google Cloud Translation** | Franchise NMT publiée à **500 000 caractères/mois**, appliquée comme crédit ; dépassements facturés dans cette offre cloud. | Autre moteur distant documenté. | Différent de ML Kit et de l’application Google Traduction. Pas de recommandation d’activation sans garanties de plafonnement compatibles avec Gratuit uniquement. Une alerte budgétaire n’est pas traitée comme un blocage des frais. |
| **API d’IA générative avec quota gratuit** | Certaines proposent un quota gratuit, variable selon modèle, compte, région et conditions. | Pourraient faire l’objet d’une étude séparée sur le contexte et les expressions. | Pas un service de traduction illimité ni un droit sur les paroles. Contrôler omissions, reformulations, segments inventés, filtres et politique de données ; aucune IA générative distante n’est choisie ici. |

### 2.1 ML Kit — moteur local retenu, pas preuve de fidélité

Sources officielles : gratuité du SDK [1](https://developers.google.com/ml-kit/guides), fonctions et limites [1](https://developers.google.com/ml-kit/language/translation), langues [4](https://developers.google.com/ml-kit/language/translation/translation-language-support), intégration et packs https://developers.google.com/ml-kit/language/translation/android.

- EN, FR, KO et ES figurent dans la liste publiée. Les modèles sont orientés vers l’anglais ; le passage intermédiaire peut dégrader les nuances entre deux autres langues.
- Le guide Android indique des modèles d’environ **30 Mo**. C’est un ordre de grandeur documentaire par modèle, pas le volume final de NovaListen ni une mesure de RAM ; les packs requis et leurs versions restent à préciser.
- Le traitement de traduction se fait sur l’appareil, mais le SDK déclare des données de diagnostic, appareil, application et performances : https://developers.google.com/ml-kit/android-data-disclosure. Ne pas annoncer Aucune communication réseau de l’application sous prétexte que le texte n’est pas envoyé pour être traduit.
- Respecter les obligations d’attribution et de présentation : [7](https://developers.google.com/ml-kit/language/translation/translation-terms).
- Il s’agit de l’API classique de traduction de texte, pas d’une exigence Gemini Nano ou d’une analyse du chant. Compatibilité, vitesse, batterie et qualité sur le TECNO restent non testées.

### 2.2 Azure F0 — alternative en ligne retenue, sous conditions

Sources officielles : tarifs F0 [1](https://azure.microsoft.com/en-us/pricing/details/translator/), langues https://learn.microsoft.com/en-us/azure/ai-services/translator/language-support, création de ressource https://learn.microsoft.com/en-us/azure/ai-services/translator/how-to/create-translator-resource?tabs=foundry.

- Examiner **Text Translation standard sur une ressource F0** ; ne pas étendre ce quota aux options LLM, à la traduction documentaire, au stockage ou à d’autres services Azure.
- Une ressource, un compte actif et une authentification sont nécessaires ; l’acceptation de principe d’un compte développeur gratuit ne vaut ni inscription effectuée ni acceptation de conditions particulières. Disponibilité de l’offre et formalités du compte à vérifier avant activation.
- La documentation distingue quota gratuit dépassé (`403001`), authentification et limitation de débit. Ne pas assimiler une erreur à un résultat vide, et ne pas changer automatiquement de niveau tarifaire : https://learn.microsoft.com/en-us/azure/ai-services/translator/text-translation/reference/status-response-codes.
- D’après sa politique, le service de traduction de texte ne conserve pas le contenu client soumis pour traduction. Il reste cependant un **traitement distant**, distinct du mode local : [1](https://learn.microsoft.com/en-us/azure/foundry/responsible-ai/translator/data-privacy-security).
- Aucun secret ne doit être embarqué comme clé privée dans l’APK. La gestion de l’authentification reste à concevoir sans imposer implicitement un backend ou des frais.

### 2.3 Pourquoi ne pas choisir les autres par simple réputation de gratuité

- **DeepL :** la page d’aide officielle a été lue directement : elle distingue l’ancien API Free et le nouveau quota Developer non renouvelable. C’est cette information, et non des listes anciennes promettant 500 000 caractères/mois à tout nouveau projet, qui fonde la réserve : [1](https://support.deepl.com/hc/en-us/articles/360021200939-DeepL-API-plans). Le crédit ponctuel n’autorise aucune bascule vers Growth ; toute autre condition de compte et de données resterait à examiner.
- **LibreTranslate :** FAQ officielle [7](https://docs.libretranslate.com/guides/faq/), documentation https://docs.libretranslate.com/ et miroirs https://docs.libretranslate.com/community/mirrors/. Le logiciel libre, l’instance officielle payante et les instances communautaires ne sont pas la même offre. Aucun serveur ni modèle Argos n’est installé ici.
- **MyMemory :** quotas [1](https://mymemory.translated.net/doc/usagelimits.php), spécification https://mymemory.translated.net/doc/spec.php, conditions https://mymemory.translated.net/terms-and-conditions. Les conditions annoncent la conservation à long terme de segments soumis et des usages par des partenaires ; distinguer recherche `get`, contribution `set`, données privées et publiques, sans affirmer que chaque recherche publie automatiquement une contribution. Ne pas utiliser `set` ou importer des paroles dans une mémoire partagée. **500 octets ne signifie pas 500 caractères**, particulièrement en coréen ; l’API et les conditions ne sont pas adaptées à l’envoi arbitraire d’une chanson entière en un bloc.
- **Google Cloud Translation :** la tarification NMT comporte un crédit mensuel, puis un prix au caractère : https://cloud.google.com/products/translate/pricing. Cette franchise n’établit pas un plafond automatique de dépense ni une autorisation d’activer la facturation.
- **IA générative :** à titre d’exemple, les conditions gratuites de Gemini API consultées prévoient des usages des entrées et sorties pour améliorer les produits et une possible revue humaine, avec des distinctions régionales et de compte ; elles prévoient aussi des restrictions d’usage à examiner pour ce projet personnel : https://aistudio.google.com/docs/terms. Ne pas généraliser cette politique à tous les fournisseurs, ni adopter une offre payante pour obtenir d’autres garanties de confidentialité. Aucun modèle, quota ou fournisseur génératif n’est retenu.

## 3. Fonctionnement validé — portée et détails à préciser

**Duo retenu : ML Kit pour la traduction locale par défaut, Azure Translator standard F0 comme alternative en ligne uniquement sur demande explicite**, sous réserve de conditions d’accès et de traitement compatibles. Ce choix n’active pas les moteurs maintenant et ne constitue pas une garantie de qualité sur les paroles.

1. Pour afficher une VF déjà choisie et sauvegardée, réutiliser les données locales autorisées sans appeler un moteur.
2. Pour une nouvelle VF, utiliser ML Kit par défaut si les packs nécessaires sont disponibles. La disponibilité des packs est un prérequis technique, pas une autorisation de les télécharger ici ou automatiquement au lancement. Les détails d’un téléchargement facultatif et non bloquant restent à concevoir séparément des modèles de Fil Rouge ; le lecteur et la VO restent utilisables sans ces packs.
3. Permettre de demander une autre traduction en ligne via Azure F0, avec information claire sur l’envoi du texte et accord applicable. **Pas de transfert automatique après refus de packs, erreur locale ou résultat jugé insuffisant.** Le détail de cette commande et de l’accord reste à choisir.
4. Sauvegarder la VF retenue sans écraser la VO ni les corrections. Une nouvelle proposition ne remplace pas silencieusement une VF choisie ; les détails d’aperçu et de remplacement restent à définir. En cas d’échec, de quota ou d’indisponibilité, garder les contenus existants ; ni paiement ni changement silencieux de moteur ou de version.
5. Une VF déjà fournie par une source de paroles pourrait aussi être proposée après contrôle de la langue, de la provenance et de sa correspondance avec la VO sélectionnée ; ce complément n’est pas un remplacement automatique ni une décision acquise.

Ce fonctionnement validé n’est **pas** une cascade automatique de transmission des paroles à plusieurs traducteurs. La confirmation retient le duo avec priorité locale et option distante sur commande ; elle n’adopte ni une traduction en ligne par défaut ni l’obligation de n’offrir que du local. Les autres moteurs du comparatif ne sont pas activés en secours par cette validation.

## 4. Fidélité et synchronisation

- Rechercher une VF fidèle au sens, aux négations, aux répétitions et aux noms, pas une adaptation rimée inventée ; ni ML Kit ni Azure ne sont déclarés vainqueurs sur la collection sans évaluation.
- Les paroles comportent argot, ambiguïtés, métaphores et mélanges de langues. La détection de langue et le traitement de chansons bilingues restent à concevoir sans traduction arbitraire de tout le texte dans une seule langue source erronée.
- Conserver les identifiants et les instants des segments de VO lorsque ceux-ci existent. Traduire du texte ne fournit pas le minutage des mots français ; le surlignage français fin reste éventuellement estimé et doit être présenté comme tel.
- La segmentation doit concilier contexte et correspondance des lignes. Une sortie qui fusionne, omet ou décale des segments ne doit pas être présentée comme parfaitement synchronisée ; méthodes de découpage, contrôle et correction restent ouvertes.
- Enregistrer la VF retenue avec sa provenance et son association à la bonne VO, sans écraser l’original. Formats internes, écriture dans l’audio, export et remplacement d’une VF existante restent à définir conformément aux choix déjà acquis.

## 5. Vérifications avant intégration

- Vérifier séparément les droits de traduire, conserver, afficher, analyser ou transmettre les paroles. Un moteur gratuit, local ou sans conservation déclarée ne fournit pas ces droits sur le texte d’origine.
- Pour une option distante, limiter les données au texte utile et aux paramètres de langue ; aucun envoi implicite d’audio, de bibliothèque complète ou d’historique. Les données exactes et le déclenchement restent à documenter avant activation.
- Les moteurs et leurs rôles sont acquis ; préciser versions, packs requis, UX d’information et de téléchargement, commande et consentement pour Azure, authentification, crédits et gestion détaillée des quotas. Ne pas imposer de compte au lecteur par simple existence d’un compte développeur. La validation ne crée ni ressource Azure, ni clé, ni backend et n’accepte pas les conditions d’un fournisseur à la place de l’utilisateur.
- Prévoir, après accord d’essais distinct, une comparaison qualitative EN/KO/ES → FR, des chansons multilingues, des segments alignés et de la conservation hors ligne. Aucune métrique ni estimation de volume traduisible de la collection n’est acquise.
- Les paramètres encore ouverts de la cascade de récupération des paroles ne sont pas tranchés par ce comparatif. Les métadonnées, pochettes, portraits, biographies et records restent à discuter avant les thèmes visuels suivant l’[ordre de travail](01-accueil.md).

**Travail réalisé : consultation de documentation et rédaction seulement.** Aucun texte traduit ni endpoint de traduction appelé, compte créé, clé générée, condition acceptée, facturation activée, frais engagés, fichier musical transmis, SDK installé, pack ou modèle téléchargé, code d’application ajouté ou expérience exécutée.
