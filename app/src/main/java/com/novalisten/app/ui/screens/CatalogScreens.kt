@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.novalisten.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.PlaybackUi
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.*

data class ArtistGroup(val id: String, val name: String, val tracks: List<Track>)
data class AlbumGroup(val id: String, val name: String, val artist: String, val tracks: List<Track>)
fun artistGroups(tracks: List<Track>): List<ArtistGroup> = tracks.groupBy { it.artistKey }.map { (id, songs) -> ArtistGroup(id, songs.first().artist, songs) }.sortedBy { it.name.normalized() }
fun albumGroups(tracks: List<Track>): List<AlbumGroup> = tracks.groupBy { it.albumKey }.map { (id, songs) -> AlbumGroup(id, songs.first().album, songs.first().albumArtist.ifBlank { songs.first().artist }, songs.sortedWith(compareBy<Track> { it.number }.thenBy { it.title.normalized() })) }.sortedBy { it.name.normalized() }

@Composable
fun CatalogScreen(artists: Boolean, tracks: List<Track>, data: LocalData, vm: MainViewModel, onArtist: (String) -> Unit, onAlbum: (String) -> Unit, onAdd: (List<Track>) -> Unit) {
    val artistList = remember(tracks) { artistGroups(tracks) }
    val albumList = remember(tracks) { albumGroups(tracks) }
    var query by rememberSaveable(artists) { mutableStateOf("") }
    var searching by rememberSaveable(artists) { mutableStateOf(false) }
    var reversed by rememberSaveable(artists) { mutableStateOf(false) }
    var selected by remember(artists) { mutableStateOf(emptySet<String>()) }
    var info by remember { mutableStateOf<String?>(null) }
    val artistResults = artistList.filter { query.normalized() in it.name.normalized() }.let { if (reversed) it.reversed() else it }
    val albumResults = albumList.filter { query.normalized() in (it.name + " " + it.artist).normalized() }.let { if (reversed) it.reversed() else it }
    val selectedTracks = if (artists) artistResults.filter { it.id in selected }.flatMap { it.tracks } else albumResults.filter { it.id in selected }.flatMap { it.tracks }
    fun select(id: String) { selected = if (id in selected) selected - id else selected + id }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column {
                PageTitle(if (selected.isEmpty()) if (artists) "Artistes" else "Albums" else "${selected.size} sélectionné(s)", "${if (artists) artistResults.size else albumResults.size} ${if (artists) "artistes" else "sorties locales"}") {
                    if (selected.isNotEmpty()) IconButton({ selected = emptySet() }) { Icon(Icons.Outlined.Close, "Quitter la sélection") }
                    else {
                        IconButton({ searching = !searching }) { Icon(Icons.Outlined.Search, "Rechercher") }
                        IconButton({ reversed = !reversed }) { Icon(Icons.Outlined.SortByAlpha, "Inverser l’ordre alphabétique") }
                    }
                }
                if (searching) SearchField(query, { query = it }, if (artists) "Nom de l’artiste" else "Album ou artiste")
                if (!artists) Notice("Regroupement par métadonnées Android dans cette version. Fusion d’éditions et classification Album/EP/Mixtape à venir.")
                if (selected.isNotEmpty()) Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ vm.next(selectedTracks); selected = emptySet() }) { Text("Lire ensuite") }
                    OutlinedButton({ onAdd(selectedTracks); selected = emptySet() }) { Text("Ajouter à une playlist") }
                }
            }
        }
        if (artists) items(artistResults, key = { it.id }) { group ->
            Box(Modifier.padding(start = if (artistResults.indexOf(group) % 2 == 0) 20.dp else 0.dp, end = if (artistResults.indexOf(group) % 2 != 0) 20.dp else 0.dp)) {
                EntityCard(group.name, "${group.tracks.size} titres · ${group.tracks.map { it.albumKey }.distinct().size} sorties", data.artists[group.id]?.portrait, artist = true, selected = group.id in selected,
                    onClick = { if (selected.isNotEmpty()) select(group.id) else onArtist(group.id) }, onLongClick = { select(group.id) }, onNext = { vm.next(group.tracks) }, onInfo = { info = "${group.name}\n${group.tracks.size} morceaux locaux\n${totalDurationLabel(group.tracks.sumOf { it.durationMs })}" })
            }
        } else items(albumResults, key = { it.id }) { group ->
            Box(Modifier.padding(start = if (albumResults.indexOf(group) % 2 == 0) 20.dp else 0.dp, end = if (albumResults.indexOf(group) % 2 != 0) 20.dp else 0.dp)) {
                EntityCard(group.name, "${group.artist}\n${group.tracks.size} titres · ${totalDurationLabel(group.tracks.sumOf { it.durationMs })}", group.tracks.firstOrNull()?.artwork, selected = group.id in selected,
                    onClick = { if (selected.isNotEmpty()) select(group.id) else onAlbum(group.id) }, onLongClick = { select(group.id) }, onNext = { vm.next(group.tracks) }, onInfo = { info = "${group.name}\n${group.artist}\nMétadonnées locales · ${group.tracks.size} titres" }, onPlay = { vm.play(group.tracks) })
            }
        }
        if ((artists && artistResults.isEmpty()) || (!artists && albumResults.isEmpty())) item(span = { GridItemSpan(2) }) {
            EmptyState(if (query.isBlank()) "Aucun ${if (artists) "artiste" else "album"} local" else "Aucun résultat", "Les métadonnées de vos fichiers musicaux apparaîtront ici.", if (artists) Icons.Outlined.PersonOutline else Icons.Outlined.Album)
        }
    }
    info?.let { value -> AlertDialog(onDismissRequest = { info = null }, title = { Text("Informations locales") }, text = { Text(value) }, confirmButton = { TextButton({ info = null }) { Text("Fermer") } }) }
}

@Composable
fun ArtistDetail(group: ArtistGroup, data: LocalData, playback: PlaybackUi, vm: MainViewModel, actions: TrackActions, onBack: () -> Unit, onAlbum: (String) -> Unit, onLibrary: () -> Unit, onPortrait: () -> Unit, onBiography: () -> Unit, onPhotos: () -> Unit) {
    val note = data.artists[group.id] ?: ArtistNote()
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var bio by remember(note) { mutableStateOf(note.biography) }
    var source by remember(note) { mutableStateOf(note.source) }
    val albums = remember(group) { albumGroups(group.tracks) }
    val counts = remember(data.events) { data.events.groupingBy { it.trackId }.eachCount() }
    val top = group.tracks.filter { counts.getOrDefault(it.id, 0) > 0 }.sortedByDescending { counts[it.id] }.take(10)
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("Artiste", "Collection locale") { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Retour") } } }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(note.portrait, group.name, Modifier.size(180.dp), round = true, artist = true)
                Spacer(Modifier.height(16.dp)); Text(group.name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(note.biography.ifBlank { "Aucune biographie sauvegardée. Recherchez une version en ligne ou ajoutez votre texte localement." }, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton({ expanded = !expanded }) { Text(if (expanded) "Voir moins" else "Voir plus") }
                    TextButton({ editing = true }) { Text("Éditer le texte local") }
                }
                Row {
                    TextButton(onBiography) { Icon(Icons.Outlined.Search, null); Text("Biographie en ligne") }
                    TextButton(onPhotos) { Icon(Icons.Outlined.ImageSearch, null); Text("Photos en ligne") }
                }
                if (expanded && note.source.isNotBlank()) SavedAttribution(note.source)
                if (expanded && note.portraitSource.isNotBlank()) SavedAttribution("Crédits du portrait :\n${note.portraitSource}")
            }
        }
        item { SectionHeader("Sorties locales", "Le type des sorties n’est pas encore vérifié") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(albums, key = { it.id }) { album -> Box(Modifier.width(160.dp)) {
                    EntityCard(album.name, "${album.tracks.size} titres locaux", album.tracks.firstOrNull()?.artwork, onClick = { onAlbum(album.id) }, onNext = { vm.next(album.tracks) }, onInfo = { vm.message("${album.name} · ${album.tracks.size} titres locaux") }, onPlay = { vm.play(album.tracks) })
                } }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PlaybackButtons(group.tracks.isNotEmpty(), { vm.play(group.tracks) }, { vm.play(group.tracks, shuffled = true) }) }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Actions de l’artiste") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Lire ensuite") }, { menu = false; vm.next(group.tracks) })
                        DropdownMenuItem({ Text("Ajouter à une playlist") }, { menu = false; actions.add(group.tracks) })
                        DropdownMenuItem({ Text("Rechercher des photos en ligne") }, { menu = false; onPhotos() })
                        DropdownMenuItem({ Text("Rechercher une biographie en ligne") }, { menu = false; onBiography() })
                        DropdownMenuItem({ Text("Choisir une photo locale") }, { menu = false; onPortrait() })
                        DropdownMenuItem({ Text("Biographie locale et source") }, { menu = false; editing = true })
                    }
                }
            }
        }
        item { SectionHeader("Top 10 personnel", "Cumul des écoutes validées · période provisoire v0.1") }
        if (top.isEmpty()) item { Notice("Pas encore d’écoute validée pour cet artiste. Une minute réellement jouée est nécessaire ; aucun classement mondial n’est utilisé.") }
        items(top, key = { it.id }) { track -> TrackRow(track, track.id in data.favorites, playback.currentId == track.id, { vm.play(top, track) }, { vm.favorite(track) }, { actions.menu(track) }, { vm.next(listOf(track)) }, { actions.info(track) }, onSelect = { actions.add(listOf(track)) }) }
        item { OutlinedButton(onLibrary, Modifier.fillMaxWidth().padding(20.dp)) { Icon(Icons.Outlined.LibraryMusic, null); Spacer(Modifier.width(8.dp)); Text("Bibliothèque de l’artiste") } }
        item { Notice("Biographies et portraits choisis sont conservés localement avec leurs sources. Recherches en ligne volontaires uniquement ; records vérifiés et catégories détaillées restent à développer.") }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Biographie locale") }, text = {
        Column { OutlinedTextField(bio, { bio = it.take(30_000) }, label = { Text("Votre texte") }, modifier = Modifier.heightIn(max = 240.dp)); Spacer(Modifier.height(8.dp)); OutlinedTextField(source, { source = it.take(1000) }, label = { Text("Source / crédits") }); Text("Sauvegardé dans NovaListen, sans réécrire les fichiers audio.", style = MaterialTheme.typography.bodySmall) }
    }, confirmButton = { TextButton({ vm.saveArtist(group.id, note.copy(biography = bio, source = source + if (bio != note.biography && source.isNotBlank()) "\nAdaptation manuelle dans NovaListen." else "")); editing = false }) { Text("Sauvegarder") } }, dismissButton = { TextButton({ editing = false }) { Text("Annuler") } })
}

@Composable
fun AlbumDetail(group: AlbumGroup, data: LocalData, playback: PlaybackUi, vm: MainViewModel, actions: TrackActions, onBack: () -> Unit, onArtist: (String) -> Unit, onCovers: () -> Unit) {
    TrackListScreen("Album", group.tracks, data, playback, vm, actions, onBack = onBack, fixedOrder = true, header = {
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(group.tracks.firstOrNull()?.artwork, group.name, Modifier.fillMaxWidth(.72f).aspectRatio(1f))
                Spacer(Modifier.height(18.dp)); Text(group.name, style = MaterialTheme.typography.headlineMedium)
                TextButton({ group.tracks.firstOrNull()?.let { onArtist(it.artistKey) } }) { Text(group.artist) }
                Text(listOfNotNull(group.tracks.firstOrNull()?.year?.takeIf { it > 0 }?.toString(), "${group.tracks.size} titres locaux", totalDurationLabel(group.tracks.sumOf { it.durationMs })).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                Row { TextButton({ vm.next(group.tracks) }) { Text("Lire ensuite") }; TextButton({ actions.add(group.tracks) }) { Text("Ajouter à une playlist") } }
                TextButton(onCovers) { Icon(Icons.Outlined.ImageSearch, null); Text("Chercher une pochette en ligne") }
                data.covers[group.id]?.let { cover -> SavedAttribution("Pochette d’affichage seulement · fichiers intacts\n${cover.source}") }
            }
        }
    })
}
