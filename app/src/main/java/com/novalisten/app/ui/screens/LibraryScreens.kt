@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.novalisten.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.PlaybackUi
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.*

data class TrackActions(
    val menu: (Track) -> Unit,
    val info: (Track) -> Unit,
    val add: (List<Track>) -> Unit,
    val delete: (List<Track>) -> Unit,
)

@Composable
fun TrackListScreen(
    title: String,
    tracks: List<Track>,
    data: LocalData,
    playback: PlaybackUi,
    vm: MainViewModel,
    actions: TrackActions,
    onBack: (() -> Unit)? = null,
    sourcePlaylist: String? = null,
    fixedOrder: Boolean = false,
    header: LazyListScope.() -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nova_lists", 0) }
    val key = if (title == "Bibliothèque") "library" else title
    var query by rememberSaveable(title) { mutableStateOf("") }
    var searching by rememberSaveable(title) { mutableStateOf(false) }
    var sort by remember(title) { mutableStateOf(runCatching { TrackSort.valueOf(prefs.getString("${key}_sort", "ADDED")!!) }.getOrDefault(TrackSort.ADDED)) }
    var descending by remember(title) { mutableStateOf(prefs.getBoolean("${key}_descending", true)) }
    var sortOpen by remember { mutableStateOf(false) }
    var selected by remember(title) { mutableStateOf(emptySet<String>()) }
    val filtered = remember(tracks, query, sort, descending, fixedOrder) {
        val found = searchTracks(tracks, query)
        if (fixedOrder) found else sortTracks(found, sort, descending)
    }
    val playable = filtered.filter { it.playable }
    val chosen = tracks.filter { it.id in selected && it.playable }
    fun select(track: Track) { if (track.playable) selected = if (track.id in selected) selected - track.id else selected + track.id }
    LazyColumn(Modifier.fillMaxSize().testTag("track_list")) {
        item {
            PageTitle(if (selected.isEmpty()) title else "${chosen.size} sélectionné(s)", "${playable.size} morceaux · ${totalDurationLabel(playable.sumOf { it.durationMs })}") {
                if (onBack != null && selected.isEmpty()) IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Retour") }
                if (selected.isNotEmpty()) IconButton({ selected = emptySet() }) { Icon(Icons.Outlined.Close, "Quitter la sélection") }
                else {
                    IconButton({ searching = !searching }) { Icon(Icons.Outlined.Search, "Rechercher") }
                    if (!fixedOrder) Box {
                        IconButton({ sortOpen = true }) { Icon(Icons.Outlined.Sort, "Trier") }
                        DropdownMenu(sortOpen, { sortOpen = false }) {
                            TrackSort.entries.forEach { value -> DropdownMenuItem(text = { Text((if (sort == value) "✓ " else "") + value.label) }, onClick = {
                                sort = value; prefs.edit().putString("${key}_sort", value.name).apply(); sortOpen = false
                            }) }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(if (descending) "Décroissant ↓" else "Croissant ↑") }, onClick = {
                                descending = !descending; prefs.edit().putBoolean("${key}_descending", descending).apply(); sortOpen = false
                            })
                        }
                    }
                    extraActions()
                }
            }
        }
        if (searching) item { SearchField(query, { query = it; selected = emptySet() }) }
        if (selected.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                TextButton({ selected = playable.map { it.id }.toSet() }) { Text("Tout sélectionner") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton({ actions.add(chosen); selected = emptySet() }) { Icon(Icons.Outlined.PlaylistAdd, "Ajouter la sélection à une playlist") }
                    IconButton({ vm.next(chosen); selected = emptySet() }) { Icon(Icons.Outlined.QueueMusic, "Lire la sélection ensuite") }
                    IconButton({ vm.hide(chosen); selected = emptySet() }) { Icon(Icons.Outlined.VisibilityOff, "Cacher la sélection") }
                    IconButton({ actions.delete(chosen); selected = emptySet() }) { Icon(Icons.Outlined.DeleteOutline, "Effacer les fichiers sélectionnés") }
                }
            }
        }
        header()
        item { PlaybackButtons(playable.isNotEmpty(), { vm.play(playable, sourcePlaylist = sourcePlaylist) }, { vm.play(playable, shuffled = true, sourcePlaylist = sourcePlaylist) }) }
        if (filtered.isEmpty()) item {
            EmptyState(if (query.isNotBlank()) "Aucun résultat" else "Votre collection commence ici", if (query.isNotBlank()) "Essayez un autre titre, artiste ou album." else "Ajoutez des fichiers musicaux sur le téléphone. Ils apparaîtront ici dès leur indexation par Android. Les fichiers de moins d’une minute restent intacts, mais ne sont pas affichés.")
        }
        items(filtered, key = { it.id }) { track ->
            TrackRow(track, track.id in data.favorites, playback.currentId == track.id,
                onPlay = { vm.play(playable, track, sourcePlaylist = sourcePlaylist) }, onFavorite = { vm.favorite(track) },
                onMenu = { actions.menu(track) }, onNext = { vm.next(listOf(track)) }, onInfo = { actions.info(track) },
                selected = track.id in selected, selecting = selected.isNotEmpty(), onSelect = { select(track) })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
