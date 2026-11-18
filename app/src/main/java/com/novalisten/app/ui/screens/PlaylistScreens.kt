@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.novalisten.app.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.PlaybackUi
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.*

@Composable
fun PlaylistIndex(tracks: List<Track>, data: LocalData, vm: MainViewModel, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("nova_lists", 0)
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sort by remember { mutableIntStateOf(prefs.getInt("playlist_sort", 0)) }
    var reverse by remember { mutableStateOf(prefs.getBoolean("playlist_reverse", false)) }
    var sortOpen by remember { mutableStateOf(false) }
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val lists = data.playlists.filter { query.normalized() in it.name.normalized() }.let { lists ->
        when (sort) {
            1 -> lists.sortedBy { it.createdAt }
            2 -> lists.sortedBy { it.trackIds.count { id -> id in byId } }
            3 -> lists.sortedBy { it.trackIds.sumOf { id -> byId[id]?.durationMs ?: 0 } }
            else -> lists.sortedBy { it.name.normalized() }
        }.let { if (reverse) it.reversed() else it }
    }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var info by remember { mutableStateOf<String?>(null) }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column {
                PageTitle("Playlists", "${lists.size} listes classiques · locales") {
                    IconButton(onCreate) { Icon(Icons.Outlined.Add, "Créer une playlist") }
                    IconButton({ searching = !searching }) { Icon(Icons.Outlined.Search, "Rechercher") }
                    Box {
                        IconButton({ sortOpen = true }) { Icon(Icons.Outlined.Sort, "Trier les playlists") }
                        DropdownMenu(sortOpen, { sortOpen = false }) {
                            listOf("Nom", "Date de création", "Nombre de titres", "Durée").forEachIndexed { index, name -> DropdownMenuItem({ Text((if (sort == index) "✓ " else "") + name) }, { sort = index; prefs.edit().putInt("playlist_sort", index).apply(); sortOpen = false }) }
                            DropdownMenuItem({ Text(if (reverse) "Décroissant ↓" else "Croissant ↑") }, { reverse = !reverse; prefs.edit().putBoolean("playlist_reverse", reverse).apply(); sortOpen = false })
                        }
                    }
                }
                if (searching) SearchField(query, { query = it }, "Nom de la playlist")
                if (selected.isNotEmpty()) Row(Modifier.padding(horizontal = 20.dp)) {
                    TextButton({ vm.next(lists.filter { it.id in selected }.flatMap { p -> p.trackIds.mapNotNull { byId[it] } }); selected = emptySet() }) { Text("Lire ensuite (${selected.size})") }
                    TextButton({ selected = emptySet() }) { Text("Quitter") }
                }
            }
        }
        items(lists, key = { it.id }) { playlist ->
            val available = playlist.trackIds.mapNotNull { byId[it] }
            Box(Modifier.padding(start = if (lists.indexOf(playlist) % 2 == 0) 20.dp else 0.dp, end = if (lists.indexOf(playlist) % 2 != 0) 20.dp else 0.dp)) {
                EntityCard(playlist.name, "${available.size} titres · ${totalDurationLabel(available.sumOf { it.durationMs })}", playlist.cover ?: available.firstOrNull()?.artwork,
                    selected = playlist.id in selected,
                    onClick = { if (selected.isEmpty()) onOpen(playlist.id) else selected = if (playlist.id in selected) selected - playlist.id else selected + playlist.id },
                    onLongClick = { selected = selected + playlist.id }, onNext = { vm.next(available) }, onInfo = { info = "${playlist.name}\n${available.size} titres disponibles\n${playlist.trackIds.size - available.size} référence(s) non affichée(s)\nLes fichiers audio ne sont pas copiés." },
                    onPlay = { vm.play(available, sourcePlaylist = playlist.id) }, badge = if (playlist.pinnedAt != null) "Épinglée" else null)
            }
        }
        if (lists.isEmpty()) item(span = { GridItemSpan(2) }) { EmptyState(if (query.isBlank()) "Faites place à vos envies" else "Aucun résultat", "Créez une playlist, ajoutez vos morceaux et retrouvez-la hors ligne. Aucun fichier n’est copié.", Icons.Outlined.PlaylistAdd, action = { Button(onCreate) { Text("Créer une playlist") } }) }
        item(span = { GridItemSpan(2) }) { Notice("Playlists intelligentes : les 17 modèles restent à développer. Aucune liste automatique ni résultat d’IA n’est simulé.") }
    }
    info?.let { AlertDialog(onDismissRequest = { info = null }, title = { Text("Informations") }, text = { Text(it) }, confirmButton = { TextButton({ info = null }) { Text("Fermer") } }) }
}

@Composable
fun PlaylistDetail(playlist: LocalPlaylist, tracks: List<Track>, data: LocalData, playback: PlaybackUi, vm: MainViewModel, actions: TrackActions, onBack: () -> Unit, onAddTracks: () -> Unit, onCover: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var newName by remember(playlist.name) { mutableStateOf(playlist.name) }
    var reorder by remember { mutableStateOf(false) }
    val available = playlist.trackIds.mapNotNull { id -> tracks.find { it.id == id } }
    val missing = playlist.trackIds.count { id -> data.hidden.let { id !in it } && tracks.none { it.id == id } }
    TrackListScreen("Playlist", available, data, playback, vm, actions, onBack = onBack, sourcePlaylist = playlist.id, fixedOrder = true,
        extraActions = {
            Box {
                IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Actions de la playlist") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Renommer") }, { menu = false; rename = true })
                    DropdownMenuItem({ Text("Réorganiser les morceaux") }, { menu = false; reorder = true })
                    DropdownMenuItem({ Text("Choisir une couverture locale") }, { menu = false; onCover() })
                    DropdownMenuItem({ Text(if (playlist.pinnedAt != null) "Désépingler de l’Accueil" else "Épingler à l’Accueil") }, { menu = false; vm.savePlaylist(playlist.copy(pinnedAt = if (playlist.pinnedAt != null) null else System.currentTimeMillis())) })
                    DropdownMenuItem({ Text("Dupliquer") }, { menu = false; vm.createPlaylist(playlist.name + " — copie", available); vm.message("Copie créée") })
                    DropdownMenuItem({ Text("Supprimer la playlist") }, { menu = false; onDelete() })
                }
            }
        }, header = {
            item {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(playlist.cover ?: available.firstOrNull()?.artwork, playlist.name, Modifier.fillMaxWidth(.7f).aspectRatio(1f))
                    Spacer(Modifier.height(16.dp)); Text(playlist.name, style = MaterialTheme.typography.headlineMedium)
                    if (playlist.pinnedAt != null) Text("Épinglée à l’Accueil", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onAddTracks, Modifier.padding(top = 12.dp)) { Icon(Icons.Outlined.PlaylistAdd, null); Spacer(Modifier.width(8.dp)); Text("Ajouter des morceaux") }
                }
            }
            if (missing > 0) item { Notice("$missing référence(s) non disponible(s) ou hors filtre. Elles restent enregistrées sans être proposées à la lecture.") }
        })
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Renommer la playlist") }, text = { OutlinedTextField(newName, { newName = it.take(100) }, singleLine = true, label = { Text("Nom") }) }, confirmButton = { TextButton({ vm.savePlaylist(playlist.copy(name = newName.trim())); rename = false }, enabled = newName.isNotBlank()) { Text("Enregistrer") } }, dismissButton = { TextButton({ rename = false }) { Text("Annuler") } })
    if (reorder) ReorderSheet(playlist, tracks, { reorder = false }) { ids -> vm.savePlaylist(playlist.copy(trackIds = ids)); reorder = false }
}

@Composable
private fun ReorderSheet(playlist: LocalPlaylist, tracks: List<Track>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var ids by remember { mutableStateOf(playlist.trackIds) }
    val titles = remember(tracks) { tracks.associate { it.id to it.title } }
    fun move(id: String, delta: Int) {
        val from = ids.indexOf(id)
        val to = from + delta
        if (from >= 0 && to in ids.indices) ids = ids.toMutableList().apply { add(to, removeAt(from)) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(.85f)) {
            Text("Réorganiser", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
            Text("Utilisez la poignée ou les flèches. La file en cours ne change pas.", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(ids, key = { _, id -> id }) { index, id ->
                    val latestMove by rememberUpdatedState<(Int) -> Unit> { delta -> move(id, delta) }
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        var drag by remember { mutableFloatStateOf(0f) }
                        Icon(Icons.Outlined.DragHandle, "Déplacer ce morceau", Modifier.size(48.dp).padding(12.dp).pointerInput(id) {
                            detectDragGestures(onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }) { change, amount ->
                                change.consume(); drag += amount.y
                                if (drag > 60.dp.toPx()) { latestMove(1); drag = 0f }
                                if (drag < -60.dp.toPx()) { latestMove(-1); drag = 0f }
                            }
                        })
                        Text(titles[id] ?: "Référence non affichée", Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        IconButton({ move(id, -1) }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, "Monter") }
                        IconButton({ move(id, 1) }, enabled = index < ids.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "Descendre") }
                    }
                }
            }
            Button({ onSave(ids) }, Modifier.fillMaxWidth().padding(20.dp)) { Text("Enregistrer l’ordre") }
        }
    }
}
