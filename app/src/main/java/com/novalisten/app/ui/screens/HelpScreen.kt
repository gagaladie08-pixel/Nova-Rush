package com.novalisten.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.novalisten.app.ui.components.Notice
import com.novalisten.app.ui.components.PageTitle
import com.novalisten.app.ui.components.SectionHeader
import com.novalisten.app.ui.theme.NovaIcon

/** Explanations live here, rather than between the user's music and its controls. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("help_screen"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Informations et aide", "NovaListen") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        item { SectionHeader("Votre bibliothèque") }
        item { Notice("Seuls les fichiers locaux alimentent les titres, compteurs et discographies. Les durées connues de moins d’une minute ne sont pas affichées ; une durée inconnue n’entre pas dans une file jouable. Masquer un morceau le retire de toutes les vues. Rien de cela ne supprime les fichiers.") }
        item { Notice("Les crédits structurés des fichiers et vos corrections locales priment. Les collaborations ambiguës demandent une concordance avec le morceau ; un signe & ou une virgule ne suffit pas à découper un groupe. Les anciens profils collectifs restent accessibles dans le menu de l’artiste, sans être attribués arbitrairement à ses participants.") }
        item { SectionHeader("Albums et éditions") }
        item { Notice("Des fragments Android compatibles d’une même sortie peuvent partager une carte et un ordre de pistes. Le titre ou la pochette seuls ne suffisent pas. Les véritables éditions restent identifiables dans Informations sur l’album. Les actions Choisir la source représentative, Séparer les sources regroupées et Vérifier les enregistrements communs sont réversibles et enregistrées localement.") }
        item { Notice("Le regroupement visuel n’efface aucune copie. Sans preuve fiable d’un même enregistrement, les fichiers restent distincts, même si leurs titres se ressemblent. Live, remix et interprétations différentes ne sont pas dédupliqués sur leur seul nom. Aucun tag, fichier audio, favori, historique ou playlist n’est fusionné ou réécrit.") }
        item { SectionHeader("Lecture et gestes") }
        item { Notice("Dans le lecteur : glissez à gauche pour les paroles, à droite pour la pochette. Après l’effacement des commandes, touchez le fond pour les retrouver ; les gestes verticaux changent de morceau. Dans les listes et les cartes : gauche pour Lire ensuite, droite pour Informations. Les actions du lecteur restent juste au-dessus de la progression.") }
        item { Notice("Les paroles synchronisées suivent la ligne en cours. Faites défiler pour suspendre le suivi, puis utilisez Recentrer ; toucher une ligne rejoint son instant. Le karaoké utilise seulement les temps natifs disponibles. L’édition, la recherche d’une autre version et la traduction volontaire sont accessibles dans les actions du morceau. Une écoute entre dans l’historique après une minute réellement jouée, pas après un déplacement du curseur.") }
        item { SectionHeader("Artistes automatiques et confidentialité") }
        item { Notice("Selon les deux interrupteurs d’API et clés, les biographies FR et portraits manquants sont recherchés progressivement pour la bibliothèque, y compris les artistes jamais écoutés. Priorité à l’artiste joué, puis aux cartes visibles, puis aux autres. Le travail s’arrête quand l’application est en arrière-plan sans lecture. Il utilise un cache et partage une limite totale de deux requêtes avec les autres recherches et les images.") }
        item { Notice("Ces recherches transmettent aux sources admissibles les noms/identifiants des artistes locaux et, si nécessaire pour les identifier, le titre, l’album et la durée d’un morceau de leur contexte. Ce n’est pas limité au morceau écouté : une partie de la bibliothèque peut donc être décrite progressivement aux fournisseurs. Aucun audio, fichier, URI locale, historique ni liste complète en un seul envoi. Les paroles automatiques restent limitées au morceau réellement écouté.") }
        item { Notice("Une identité doit être vérifiée avant attribution : contexte MusicBrainz, lien Wikidata réciproque, puis page Wikipédia FR ou portrait Commons lié, licence ouverte et crédits. La biographie peut être sauvegardée avant la photo. En cas de doute, aucun premier résultat n’est imposé. Les textes, photos et effacements choisis manuellement ont priorité, séparément pour chaque champ. Les sources et licences restent accessibles depuis la fiche ; les contenus retenus sont conservés hors ligne.") }
        item { Notice("Commons et Openverse sont les seules branches de collecte photo actuellement admissibles. Une clé, un MBID ou la popularité d’une image ne prouve pas ses droits. Genius, TheAudioDB, Discogs et fanart.tv ne deviennent pas des collecteurs autorisés par l’ajout d’une clé. La cascade propose un lot borné, sans garantie de six photos. Last.fm reste une consultation externe volontaire, pas un collecteur rétabli. Aucun service de reconnaissance faciale.") }
        item { SectionHeader("Réseau et données locales") }
        item { Notice("La lecture fonctionne sans compte et hors ligne. Les recherches en ligne ont des quotas et peuvent ne rien trouver. Le français est prioritaire pour les biographies ; l’anglais se demande volontairement, sans traduction automatique ajoutée. Vos réglages, identifiants chiffrés et enrichissements restent dans l’espace privé de l’application ; les sauvegardes Android automatiques sont désactivées.") }
        item { Notice("Azure ne reçoit des paroles qu’après une confirmation dédiée pour une ressource F0. ML Kit est facultatif : les packs se téléchargent sur demande et le calcul est local, mais le SDK Google déclare des diagnostics après activation. Ce n’est donc pas une promesse de zéro réseau. Les informations et liens officiels détaillés figurent dans API et clés.") }
        item { SectionHeader("Version Next") }
        item { Notice("NovaListen Next possède son propre identifiant et une clé de signature stable, distincts de NovaListen 0.2.3. Une mise à jour Next doit conserver cette identité et cette clé. Aucune désinstallation de 0.2.3 n’est nécessaire. La publication 0.3.0 reste inchangée ; les corrections suivantes exigent une nouvelle version vérifiée.") }
        item { Notice("Listening Party, YouTube, Fil Rouge, les modèles de playlists intelligentes et l’écriture des tags audio ne sont pas intégrés. Les regroupements présentés ici modifient l’index d’affichage uniquement. Les licences des polices et bibliothèques sont accessibles dans Réglages.") }
    }
}
