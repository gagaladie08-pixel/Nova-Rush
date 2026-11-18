@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.novalisten.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrackMenu(track: Track, inPlaylist: Boolean, vm: MainViewModel, onDismiss: () -> Unit, onInfo: () -> Unit, onAdd: () -> Unit, onArtist: () -> Unit, onAlbum: () -> Unit, onDelete: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxHeight(.78f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Artwork(track.artwork, track.title, Modifier.size(56.dp)); Spacer(Modifier.width(14.dp)); Column { Text(track.title, style = MaterialTheme.typography.titleMedium); Text(track.artist, style = MaterialTheme.typography.bodySmall) } } }
            item { MenuItem("Informations du morceau", Icons.Outlined.Info) { onDismiss(); onInfo() } }
            if (track.playable) {
                item { MenuItem("Lire ensuite", Icons.Outlined.QueueMusic) { vm.next(listOf(track)); onDismiss() } }
                item { MenuItem("Ajouter à une playlist", Icons.Outlined.PlaylistAdd) { onDismiss(); onAdd() } }
                item { MenuItem("Aller dans l’album", Icons.Outlined.Album) { onDismiss(); onAlbum() } }
                item { MenuItem("Aller dans l’artiste", Icons.Outlined.PersonOutline) { onDismiss(); onArtist() } }
                item { MenuItem("Cacher dans toute l’application", Icons.Outlined.VisibilityOff) { vm.hide(listOf(track)); onDismiss() } }
                if (inPlaylist) item { MenuItem("Retirer de cette playlist", Icons.Outlined.PlaylistRemove) { onDismiss(); onRemove() } }
                item { MenuItem("Partager le fichier audio", Icons.Outlined.Share) {
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(track.mimeType.ifBlank { "audio/*" }).putExtra(Intent.EXTRA_STREAM, Uri.parse(track.id)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Partager le morceau")) }.onFailure { vm.message("Aucune application de partage compatible.") }
                    onDismiss()
                } }
                item { MenuItem("Effacer le fichier du téléphone", Icons.Outlined.DeleteOutline) { onDismiss(); onDelete() } }
            }
            item { HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
            item { Text("Édition des métadonnées dans les fichiers et définition de sonnerie : non intégrées dans cette version. Aucun faux enregistrement local ne remplace ces fonctions.", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(icon, null) }, modifier = Modifier.clickable(onClick = onClick).heightIn(min = 52.dp))
}

@Composable
fun TrackInfoDialog(track: Track, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Informations du morceau") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Artwork(track.artwork, track.title, Modifier.size(92.dp))
            Spacer(Modifier.height(16.dp))
            listOf("Titre" to track.title, "Artiste" to track.artist, "Album" to track.album, "Durée" to if (track.pending) "Durée inconnue · en attente" else durationLabel(track.durationMs), "Année musicale" to track.year.takeIf { it > 0 }?.toString().orEmpty().ifBlank { "Non renseignée" }, "Piste" to track.number.takeIf { it > 0 }?.toString().orEmpty().ifBlank { "Non renseignée" }, "Fichier" to track.fileName, "Format" to track.mimeType, "Taille" to "%.2f Mo".format(track.size / 1_000_000.0), "Ajout dans l’index Android" to java.time.Instant.ofEpochMilli(track.dateAdded).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(), "URI locale" to track.id).forEach { (label, value) ->
                Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(10.dp))
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Fermer") } })
}

@Composable
fun AddToPlaylistDialog(tracks: List<Track>, lists: List<LocalPlaylist>, vm: MainViewModel, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ajouter à une playlist") }, text = {
        Column {
            Text("${tracks.size} morceau(x) · nouveaux titres au début, sans doublon du même fichier.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 230.dp)) {
                items(lists, key = { it.id }) { playlist -> Row(Modifier.fillMaxWidth().clickable { selected = if (playlist.id in selected) selected - playlist.id else selected + playlist.id }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(playlist.id in selected, { selected = if (it) selected + playlist.id else selected - playlist.id }); Text(playlist.name, Modifier.weight(1f))
                } }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(newName, { newName = it.take(100) }, label = { Text("Créer une classique") }, singleLine = true)
            TextButton({ vm.createPlaylist(newName, tracks); onDismiss() }, enabled = newName.isNotBlank()) { Icon(Icons.Outlined.Add, null); Text("Créer et y ajouter les morceaux") }
        }
    }, confirmButton = { TextButton({ vm.addToPlaylists(selected, tracks); onDismiss() }, enabled = selected.isNotEmpty()) { Text("Ajouter (${selected.size})") } }, dismissButton = { TextButton(onDismiss) { Text("Fermer") } })
}

@Composable
fun CreatePlaylistDialog(vm: MainViewModel, onDismiss: () -> Unit, onCreated: (LocalPlaylist, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pickTracks by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Créer une playlist") }, text = {
        Column {
            Text("Classique", style = MaterialTheme.typography.titleMedium)
            Text("Une liste locale, vide ou remplie par vos soins.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp)); OutlinedTextField(name, { name = it.take(100) }, label = { Text("Nom de la playlist") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pickTracks, { pickTracks = it }); Text("Choisir des morceaux après la création", style = MaterialTheme.typography.bodySmall) }
            TextButton({ models = true }) { Icon(Icons.Outlined.AutoAwesome, null); Text("Intelligente · modèles à venir") }
        }
    }, confirmButton = { TextButton({ vm.createPlaylist(name) { playlist -> onDismiss(); onCreated(playlist, pickTracks) } }, enabled = name.isNotBlank()) { Text("Créer") } }, dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
    if (models) AlertDialog(onDismissRequest = { models = false }, title = { Text("Modèles Nova · non intégrés") }, text = {
        LazyColumn { item { Text("Les 17 concepts et l’éditeur personnalisé sont conservés dans la conception. Ils ne sont pas encore générés par cette version.", style = MaterialTheme.typography.bodySmall) }
            items(listOf("Signature Nova", "Diamants Cachés", "Coup de Foudre", "Retour de Flamme", "Rendez-Vous", "Constellation", "Panorama", "Triptyque", "Backstage Pass", "Hors des Radars", "Passerelle", "Odyssée Nova", "Caméléon", "Atlas Nova", "Résonance", "Fil Rouge", "Terrain d’Entente")) { Text(it, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge) }
        }
    }, confirmButton = { TextButton({ models = false }) { Text("Compris") } })
}

@Composable
fun TrackPickerDialog(tracks: List<Track>, playlist: LocalPlaylist, vm: MainViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    val filtered = searchTracks(tracks, query)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ajouter à ${playlist.name}") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("Rechercher") }, singleLine = true)
            LazyColumn(Modifier.heightIn(min = 120.dp, max = 360.dp)) {
                items(filtered, key = { it.id }) { track ->
                    val exists = track.id in playlist.trackIds
                    Row(Modifier.fillMaxWidth().clickable(enabled = !exists) { chosen = if (track.id in chosen) chosen - track.id else chosen + track.id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(track.id in chosen || exists, { checked -> chosen = if (checked) chosen + track.id else chosen - track.id }, enabled = !exists)
                        Column(Modifier.weight(1f)) { Text(track.title, maxLines = 1); Text(if (exists) "Déjà présent" else track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton({ vm.addToPlaylists(setOf(playlist.id), tracks.filter { it.id in chosen }); onDismiss() }, enabled = chosen.isNotEmpty()) { Text("Ajouter (${chosen.size})") } }, dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
}

@Composable
fun LyricsEditor(track: Track, saved: SavedLyrics, vm: MainViewModel, onDismiss: () -> Unit) {
    var original by remember { mutableStateOf(saved.original) }
    var french by remember { mutableStateOf(saved.french) }
    var editingFrench by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val text = StringBuilder(); val buffer = CharArray(4096)
                    while (true) { val n = reader.read(buffer); if (n < 0) break; text.append(buffer, 0, n); require(text.length <= 200_000) { "Fichier trop volumineux" } }
                    text.toString()
                } ?: error("Lecture impossible")
            } }
            result.onSuccess { if (editingFrench) french = it else original = it }.onFailure { vm.message("Import impossible : choisissez un fichier texte ou LRC de moins de 200 000 caractères.") }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Paroles locales") }, text = {
        Column {
            Text(track.title, style = MaterialTheme.typography.titleSmall)
            Row { FilterChip(!editingFrench, { editingFrench = false }, { Text("Original") }); Spacer(Modifier.width(10.dp)); FilterChip(editingFrench, { editingFrench = true }, { Text("Français manuel") }) }
            OutlinedTextField(if (editingFrench) french else original, { if (editingFrench) french = it.take(200_000) else original = it.take(200_000) }, Modifier.fillMaxWidth().heightIn(min = 170.dp, max = 280.dp), label = { Text(if (editingFrench) "Votre version française" else "Texte ou LRC horodaté") })
            TextButton({ import.launch(arrayOf("text/*", "application/octet-stream")) }) { Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Importer TXT / LRC") }
            Text("Sauvegarde dans NovaListen uniquement, pas dans le fichier audio. Aucune traduction automatique ni synchronisation inventée.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({ vm.saveLyrics(track.id, SavedLyrics(original, french)); onDismiss() }) { Text("Sauvegarder") } }, dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
}
