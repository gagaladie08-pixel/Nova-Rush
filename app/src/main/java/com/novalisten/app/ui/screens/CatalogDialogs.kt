package com.novalisten.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.Artwork

fun albumInformation(group: AlbumGroup): String = buildString {
    appendLine("${group.name} · ${group.artist}")
    appendLine("${group.kind.label} · ${group.tracks.size} enregistrements locaux retenus")
    appendLine(if (group.automaticFamily) "Lien de groupe d’éditions lu dans les tags MusicBrainz locaux, ou choix local sur ce lien." else "Regroupement local explicite, ou édition Android isolée.")
    appendLine("Les titres seuls ne prouvent pas une identité d’enregistrement : les copies sans identifiant fiable restent distinctes jusqu’à votre confirmation.")
    group.editions.forEach { edition ->
        appendLine("\n${edition.name} · ${edition.artist} · ${edition.kind.label}")
        appendLine("${edition.tracks.size} fichier(s) admissible(s) · ${if (edition.id == group.originalId) "ordre d’origine retenu" else "édition supplémentaire"}${if (edition.id == group.representativeId) " · nom/pochette affichés" else ""}")
    }
    append("\nAucun fichier, tag, historique ou favori n’est fusionné ou supprimé. Les fichiers masqués ou de durée non admissible ne sont pas réintroduits.")
}

@Composable
fun GroupEditionsDialog(groups: List<AlbumGroup>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val editions = groups.flatMap { it.editions }.distinctBy { it.id }
    var original by remember(editions) { mutableStateOf(editions.first().id) }
    var confirmed by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Regrouper les éditions d’une même sortie") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("Une seule carte et une liste unique : pistes de l’édition d’origine, puis ajouts des autres éditions. Les fichiers sans preuve d’enregistrement commun restent distincts. Aucun fichier n’est modifié.")
            Text("Choisir l’édition d’origine et son ordre :", style = MaterialTheme.typography.titleSmall)
            editions.forEach { edition -> Row(Modifier.fillMaxWidth().clickable { original = edition.id }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(original == edition.id, { original = edition.id }); Column(Modifier.weight(1f)) { Text(edition.name); Text("${edition.artist} · ${edition.tracks.size} titres locaux", style = MaterialTheme.typography.bodySmall) }
            } }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(confirmed, { confirmed = it }); Text("Je confirme que ces éditions doivent partager une fiche. Ce choix est réversible.", style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton({ onConfirm(original) }, enabled = confirmed, modifier = Modifier.testTag("confirm_editions")) { Text("Regrouper les éditions") } }, dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
}

@Composable
fun EditionChoiceDialog(group: AlbumGroup, original: Boolean, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var chosen by remember(group.id, original) { mutableStateOf(if (original) group.originalId else group.representativeId) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original) "Édition d’origine et ordre" else "Édition représentative") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(if (original) "Ses pistes seront présentées d’abord, puis les ajouts. Ce choix définit aussi le nom et la pochette par défaut." else "Changer uniquement le nom et la pochette affichés, sans changer l’ordre d’origine ni les tags.")
            group.editions.forEach { edition -> Row(Modifier.fillMaxWidth().clickable { chosen = edition.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(chosen == edition.id, { chosen = edition.id }); Artwork(edition.artwork, edition.name, Modifier.size(48.dp)); Spacer(Modifier.width(8.dp)); Text(edition.name, Modifier.weight(1f))
            } }
        }
    }, confirmButton = { TextButton({ onChoose(chosen) }) { Text("Choisir") } }, dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
}

@Composable
fun ReleaseKindDialog(group: AlbumGroup, onDismiss: () -> Unit, onChoose: (ReleaseKind?) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Classer cette sortie") }, text = {
        Column {
            Text("Choix local explicite, pas une déduction du nombre de fichiers. Singles et compilations restent accessibles depuis la bibliothèque et les artistes, hors de l’index Albums / EP / Mixtapes.", style = MaterialTheme.typography.bodySmall)
            ReleaseKind.entries.forEach { kind -> TextButton({ onChoose(kind) }, Modifier.fillMaxWidth().testTag("release_kind_${kind.name}")) { Text((if (kind == group.kind) "✓ " else "") + kind.label) } }
            TextButton({ onChoose(null) }) { Text("Relire le type des métadonnées") }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Fermer") } })
}

@Composable
fun ArtistCreditsDialog(track: Track, choices: CatalogChoices, vm: MainViewModel, onDismiss: () -> Unit) {
    var names by remember(track.id) { mutableStateOf(ArtistCredits.forTrack(track, choices).joinToString("\n") { it.name + if (it.mbid.isNotBlank()) " | ${it.mbid}" else "" }) }
    val lines = names.lines().map(String::trim).filter(String::isNotBlank)
    val valid = lines.size in 1..16 && lines.all { line -> line.substringBefore('|').trim().isNotBlank() && (line.substringAfter('|', "").trim().let { it.isBlank() || validMusicId(it) }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Artistes de ce morceau") }, text = {
        Column {
            Text("Crédit du fichier conservé : ${track.artist}", style = MaterialTheme.typography.bodySmall)
            Text("Un artiste par ligne. Ne séparez pas un groupe de ses membres. Facultatif pour un homonyme : Nom | identifiant MusicBrainz.")
            OutlinedTextField(names, { names = it.take(5000) }, Modifier.fillMaxWidth().heightIn(max = 260.dp).testTag("artist_credits_input"), label = { Text("Participants identifiés") })
            TextButton({ vm.correctArtists(track.id, null); onDismiss() }) { Text("Relire les crédits des métadonnées") }
            Text("Modification de l’index local uniquement ; pas des tags ni des anciens profils sauvegardés.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({ vm.correctArtists(track.id, lines); onDismiss() }, enabled = valid) { Text("Enregistrer les artistes") } }, dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
}

@Composable
fun RecordingAssociationsDialog(group: AlbumGroup, choices: CatalogChoices, vm: MainViewModel, onDismiss: () -> Unit) {
    val tracks = group.editions.flatMap { it.tracks }.distinctBy { it.id }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var checked by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Enregistrements communs aux éditions") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("Un titre identique n’est pas une preuve. Sélectionnez uniquement les copies du même enregistrement, jamais un live, remix ou une autre interprétation. Tous les fichiers restent disponibles dans la bibliothèque.")
            tracks.forEach { track -> Row(Modifier.fillMaxWidth().clickable { selected = if (track.id in selected) selected - track.id else selected + track.id }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(track.id in selected, { selected = if (it) selected + track.id else selected - track.id })
                Column(Modifier.weight(1f)) { Text(track.title); Text("${track.album} · ${durationLabel(track.durationMs)}\n${track.fileName}${if (track.id in choices.recordings) " · choix local enregistré" else ""}", style = MaterialTheme.typography.bodySmall) }
            } }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { checked = it }); Text("J’ai vérifié : c’est le même enregistrement.", style = MaterialTheme.typography.bodySmall) }
            TextButton({ vm.associateRecordings(selected, together = false); onDismiss() }, enabled = selected.isNotEmpty()) { Text("Rétablir la détection native pour la sélection") }
            TextButton({ vm.keepRecordingsDistinct(selected); onDismiss() }, enabled = selected.isNotEmpty()) { Text("Garder ces fichiers distincts") }
        }
    }, confirmButton = { TextButton({ vm.associateRecordings(selected); onDismiss() }, enabled = selected.size >= 2 && checked) { Text("Même enregistrement") } }, dismissButton = { TextButton(onDismiss) { Text("Fermer") } })
}

@Composable
fun LegacyArtistProfilesDialog(group: ArtistGroup, ids: List<String>, data: LocalData, vm: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Anciens profils conservés") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("Ces données étaient rattachées au crédit collectif. Vérifiez leur sujet avant de les attribuer à ${group.name}. Les originaux ne seront pas supprimés.")
            ids.forEach { id -> data.artists[id]?.let { note ->
                Text(id, style = MaterialTheme.typography.titleSmall)
                note.portrait?.let { uri -> Artwork(uri, id, Modifier.size(100.dp)); TextButton({ vm.saveArtistPortrait(group.id, uri, note.portraitSource); onDismiss() }) { Text("Attribuer ce portrait à ${group.name}") } }
                if (note.biography.isNotBlank()) { Text(note.biography.take(500)); TextButton({ vm.saveArtistBiography(group.id, note.biography, note.source); onDismiss() }) { Text("Attribuer cette biographie à ${group.name}") } }
            } }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Fermer") } })
}
