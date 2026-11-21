@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.novalisten.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.PlaybackUi
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.*

@Composable
fun CatalogScreen(artists: Boolean, tracks: List<Track>, data: LocalData, vm: MainViewModel, onArtist: (String) -> Unit, onAlbum: (String) -> Unit, onAdd: (List<Track>) -> Unit) {
    val artistList = remember(tracks, data.catalog) { artistGroups(tracks, data.catalog) }
    val albumList = remember(tracks, data.catalog) { albumGroups(tracks, data.catalog) }
    val preferences = LocalContext.current.getSharedPreferences("nova_catalog", 0)
    val preferenceKey = if (artists) "artists_sort" else "albums_sort"
    var query by rememberSaveable(artists) { mutableStateOf("") }
    var searching by rememberSaveable(artists) { mutableStateOf(false) }
    var sortName by rememberSaveable(artists) { mutableStateOf(preferences.getString(preferenceKey, CatalogSort.NAME_ASC.name)!!) }
    val sort = runCatching { CatalogSort.valueOf(sortName) }.getOrDefault(CatalogSort.NAME_ASC)
    var sortOpen by remember { mutableStateOf(false) }
    var kindOpen by remember { mutableStateOf(false) }
    var kindName by rememberSaveable { mutableStateOf("ALL") }
    var selected by remember(artists) { mutableStateOf(emptySet<String>()) }
    var info by remember { mutableStateOf<String?>(null) }
    var grouping by remember { mutableStateOf(false) }
    val artistResults = sortCatalog(artistList.filter { query.normalized() in it.name.normalized() }, sort, { it.name }, { it.tracks.size })
    val albumResults = sortCatalog(albumList.filter { group ->
        group.kind !in setOf(ReleaseKind.SINGLE, ReleaseKind.COMPILATION) && (kindName == "ALL" || group.kind.name == kindName) &&
            group.editions.any { query.normalized() in (it.name + " " + it.artist).normalized() }
    }, sort, { it.name }, { it.tracks.size })
    val selectedAlbums = albumResults.filter { it.id in selected }
    val selectedTracks = (if (artists) artistResults.filter { it.id in selected }.flatMap { it.tracks } else selectedAlbums.flatMap { it.tracks }).distinctBy { it.id }
    fun select(id: String) { selected = if (id in selected) selected - id else selected + id }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().testTag(if (artists) "artist_catalog" else "album_catalog"), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column {
                PageTitle(if (selected.isEmpty()) if (artists) "Artistes" else "Albums" else "${selected.size} sélectionné(s)", "${if (artists) artistResults.size else albumResults.size} ${if (artists) "artistes" else "sorties locales"}") {
                    if (selected.isNotEmpty()) IconButton({ selected = emptySet() }) { Icon(Icons.Outlined.Close, "Quitter la sélection") }
                    else {
                        IconButton({ searching = !searching }) { Icon(Icons.Outlined.Search, "Rechercher") }
                        Box {
                            IconButton({ sortOpen = true }, Modifier.testTag("catalog_sort")) { Icon(Icons.Outlined.Sort, "Trier par nom ou nombre de titres") }
                            DropdownMenu(sortOpen, { sortOpen = false }) {
                                CatalogSort.entries.forEach { value -> DropdownMenuItem({ Text((if (sort == value) "✓ " else "") + value.label) }, {
                                    sortName = value.name; preferences.edit().putString(preferenceKey, value.name).apply(); sortOpen = false
                                }, modifier = Modifier.testTag("catalog_sort_${value.name}")) }
                            }
                        }
                        if (!artists) Box {
                            IconButton({ kindOpen = true }) { Icon(Icons.Outlined.FilterList, "Filtrer les types de sorties") }
                            DropdownMenu(kindOpen, { kindOpen = false }) {
                                DropdownMenuItem({ Text("Tous les types de cet index") }, { kindName = "ALL"; kindOpen = false; selected = emptySet() })
                                listOf(ReleaseKind.ALBUM, ReleaseKind.EP, ReleaseKind.MIXTAPE, ReleaseKind.UNKNOWN).forEach { value -> DropdownMenuItem({ Text(value.label) }, { kindName = value.name; kindOpen = false; selected = emptySet() }) }
                            }
                        }
                    }
                }
                if (searching) SearchField(query, { query = it; selected = emptySet() }, if (artists) "Nom de l’artiste" else "Album, édition ou artiste")
                if (!artists) Notice("Une carte par groupe d’éditions identifié ou confirmé. Relations demandées, identifiants locaux et preuves d’enregistrement partagées ; types lus dans les tags ou choisis localement : un album avec un seul fichier ne devient pas un single. Appui long sur plusieurs cartes pour regrouper leurs éditions.")
                if (selected.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ vm.next(selectedTracks); selected = emptySet() }) { Text("Lire ensuite") }
                        OutlinedButton({ onAdd(selectedTracks); selected = emptySet() }) { Text("Ajouter à une playlist") }
                    }
                    if (!artists && selectedAlbums.size > 1) TextButton({ grouping = true }, Modifier.testTag("group_editions")) { Text("Regrouper les éditions") }
                }
            }
        }
        if (artists) items(artistResults, key = { it.id }) { group ->
            Box(Modifier.padding(start = if (artistResults.indexOf(group) % 2 == 0) 20.dp else 0.dp, end = if (artistResults.indexOf(group) % 2 != 0) 20.dp else 0.dp)) {
                EntityCard(group.name, "${group.tracks.size} titres · ${albumList.count { album -> album.editions.any { edition -> edition.tracks.any { song -> group.tracks.any { it.id == song.id } } } }} sorties", artistNote(group, data).portrait, artist = true, selected = group.id in selected,
                    onClick = { if (selected.isNotEmpty()) select(group.id) else onArtist(group.id) }, onLongClick = { select(group.id) }, onNext = { vm.next(group.tracks) }, onInfo = { info = "${group.name}\n${group.tracks.size} morceaux locaux\n${totalDurationLabel(group.tracks.sumOf { it.durationMs })}" })
            }
        } else items(albumResults, key = { it.id }) { group ->
            Box(Modifier.padding(start = if (albumResults.indexOf(group) % 2 == 0) 20.dp else 0.dp, end = if (albumResults.indexOf(group) % 2 != 0) 20.dp else 0.dp)) {
                EntityCard(group.name, "${group.artist}\n${group.kind.label} · ${group.tracks.size} titres · ${group.editions.size} édition(s)", group.artwork, selected = group.id in selected,
                    onClick = { if (selected.isNotEmpty()) select(group.id) else onAlbum(group.id) }, onLongClick = { select(group.id) }, onNext = { vm.next(group.tracks) }, onInfo = { info = albumInformation(group) }, onPlay = { vm.play(group.tracks) })
            }
        }
        if ((artists && artistResults.isEmpty()) || (!artists && albumResults.isEmpty())) item(span = { GridItemSpan(2) }) {
            EmptyState(if (query.isBlank()) "Aucun ${if (artists) "artiste" else "album"} local" else "Aucun résultat", "Les métadonnées de vos fichiers musicaux apparaîtront ici. Les types inconnus restent à préciser, sans classement inventé.", if (artists) Icons.Outlined.PersonOutline else Icons.Outlined.Album)
        }
    }
    if (grouping) GroupEditionsDialog(selectedAlbums, { grouping = false }) { original ->
        vm.groupEditions(selectedAlbums.flatMap { it.editions }.map { it.id }.toSet(), original) { id -> grouping = false; selected = emptySet(); onAlbum(id) }
    }
    info?.let { value -> AlertDialog(onDismissRequest = { info = null }, title = { Text("Informations locales") }, text = { Text(value) }, confirmButton = { TextButton({ info = null }) { Text("Fermer") } }) }
}

@Composable
fun ArtistDetail(group: ArtistGroup, data: LocalData, playback: PlaybackUi, vm: MainViewModel, actions: TrackActions, onBack: () -> Unit, onAlbum: (String) -> Unit, onLibrary: () -> Unit, onPortrait: () -> Unit, onBiography: () -> Unit, onPhotos: () -> Unit, allAlbums: List<AlbumGroup>) {
    val note = artistNote(group, data)
    val automatic by vm.automaticArtists.state.collectAsStateWithLifecycle()
    var legacyOpen by remember { mutableStateOf(false) }
    val oldProfiles = group.tracks.map { it.artistKey }.distinct().filter { it != group.id && it != group.legacyId && it in data.artists }
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var bio by remember(note) { mutableStateOf(note.biography) }
    var source by remember(note) { mutableStateOf(note.source) }
    val albums = remember(group, allAlbums) { val ids = group.tracks.map { it.id }.toSet(); allAlbums.filter { album -> album.editions.any { edition -> edition.tracks.any { it.id in ids } } } }
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
                automatic[group.id]?.let { status ->
                    if (status.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("automatic_artist_loading"))
                    if (expanded && status.note.isNotBlank()) Text(status.note, style = MaterialTheme.typography.bodySmall)
                }
                if (oldProfiles.isNotEmpty()) TextButton({ legacyOpen = true }) { Text("Examiner les anciens profils de collaboration") }
                if (expanded && note.source.isNotBlank()) SavedAttribution(note.source)
                if (expanded && note.portraitSource.isNotBlank()) SavedAttribution("Crédits du portrait :\n${note.portraitSource}")
            }
        }
        albums.groupBy { album -> if (album.editions.any { edition -> ArtistCredits.parse(edition.artist).any { it.name.artistNameKey() == group.legacyId } }) album.kind.label else "Participations" }.forEach { (category, releases) ->
            item { SectionHeader(category, "Sorties présentes sur le téléphone · éditions regroupées") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(releases, key = { it.id }) { album -> Box(Modifier.width(160.dp)) {
                        EntityCard(album.name, "${album.tracks.size} titres locaux", album.artwork, onClick = { onAlbum(album.id) }, onNext = { vm.next(album.tracks) }, onInfo = { vm.message(albumInformation(album)) }, onPlay = { vm.play(album.tracks) })
                    } }
                }
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
                        if ((note.biographyLocked && note.biography.isBlank()) || (note.portraitLocked && note.portrait == null)) {
                            DropdownMenuItem({ Text("Autoriser le remplissage des champs vides") }, { menu = false; vm.allowMissingArtistFields(group.id) })
                        }

                    }
                }
            }
        }
        item { SectionHeader("Top 10 personnel", "Cumul des écoutes validées · période provisoire v0.1") }
        if (top.isEmpty()) item { Notice("Pas encore d’écoute validée pour cet artiste. Une minute réellement jouée est nécessaire ; aucun classement mondial n’est utilisé.") }
        items(top, key = { it.id }) { track -> TrackRow(track, track.id in data.favorites, playback.currentId == track.id, { vm.play(top, track) }, { vm.favorite(track) }, { actions.menu(track) }, { vm.next(listOf(track)) }, { actions.info(track) }, onSelect = { actions.add(listOf(track)) }) }
        item { OutlinedButton(onLibrary, Modifier.fillMaxWidth().padding(20.dp)) { Icon(Icons.Outlined.LibraryMusic, null); Spacer(Modifier.width(8.dp)); Text("Bibliothèque de l’artiste") } }
        item { Notice("Biographies FR et portraits admissibles peuvent être recherchés pendant l’écoute. Vos choix manuels restent prioritaires. Sources et crédits sont conservés hors ligne ; la recherche anglaise et les remplacements restent volontaires. Aucun record mondial inventé.") }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (legacyOpen) LegacyArtistProfilesDialog(group, oldProfiles, data, vm) { legacyOpen = false }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Biographie locale") }, text = {
        Column { OutlinedTextField(bio, { bio = it.take(30_000) }, label = { Text("Votre texte") }, modifier = Modifier.heightIn(max = 240.dp)); Spacer(Modifier.height(8.dp)); OutlinedTextField(source, { source = it.take(1000) }, label = { Text("Source / crédits") }); Text("Sauvegardé dans NovaListen, sans réécrire les fichiers audio.", style = MaterialTheme.typography.bodySmall) }
    }, confirmButton = { TextButton({ vm.saveArtistBiography(group.id, bio, source + if (bio != note.biography && source.isNotBlank()) "\nAdaptation manuelle dans NovaListen." else ""); editing = false }) { Text("Sauvegarder") } }, dismissButton = { TextButton({ editing = false }) { Text("Annuler") } })
}

@Composable
fun AlbumDetail(group: AlbumGroup, data: LocalData, playback: PlaybackUi, vm: MainViewModel, actions: TrackActions, onBack: () -> Unit, onArtist: (String) -> Unit, onCovers: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    TrackListScreen("Album", group.tracks, data, playback, vm, actions, onBack = onBack, fixedOrder = true, fullQueueWhenSearching = true, header = {
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(group.artwork, group.name, Modifier.fillMaxWidth(.72f).aspectRatio(1f))
                Spacer(Modifier.height(18.dp)); Text(group.name, style = MaterialTheme.typography.headlineMedium)
                artistGroups(group.editions.flatMap { it.tracks }, data.catalog).forEach { artist ->
                    TextButton({ onArtist(artist.id) }) { Text(artist.name) }
                }
                Text("${if (group.year > 0) "${group.year} · " else ""}${group.kind.label} · ${group.tracks.size} titres locaux · ${totalDurationLabel(group.tracks.sumOf { it.durationMs })}")
                Text("${group.editions.size} édition(s) · ordre d’origine retenu, puis ajouts", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onCovers) { Icon(Icons.Outlined.ImageSearch, null); Text("Pochette") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Actions de l’album") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Lire ensuite") }, { menu = false; vm.next(group.tracks) })
                            DropdownMenuItem({ Text("Ajouter à une playlist") }, { menu = false; actions.add(group.tracks) })
                            DropdownMenuItem({ Text("Informations sur l’album") }, { menu = false; dialog = "info" })
                            DropdownMenuItem({ Text("Aller dans l’artiste") }, { menu = false; dialog = "artists" })
                            if (group.editions.size > 1) {
                                DropdownMenuItem({ Text("Choisir l’édition représentative") }, { menu = false; dialog = "representative" })
                                DropdownMenuItem({ Text("Choisir l’édition d’origine / ordre") }, { menu = false; dialog = "original" })
                                DropdownMenuItem({ Text("Séparer les éditions") }, { menu = false; dialog = "separate" })
                                DropdownMenuItem({ Text("Vérifier les enregistrements communs") }, { menu = false; dialog = "recordings" })
                            }
                            DropdownMenuItem({ Text("Classer cette sortie") }, { menu = false; dialog = "kind" })
                        }
                    }
                }
                data.covers[group.representative.tracks.first().albumKey]?.let { cover -> SavedAttribution("Pochette d’affichage seulement · fichiers intacts\n${cover.source}") }
            }
        }
    })
    when (dialog) {
        "info" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Éditions et sources locales") }, text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text(albumInformation(group)) } }, confirmButton = { TextButton({ dialog = "" }) { Text("Fermer") } })
        "representative", "original" -> EditionChoiceDialog(group, dialog == "original", { dialog = "" }) { edition -> vm.chooseEdition(group, edition, dialog == "original"); dialog = "" }
        "kind" -> ReleaseKindDialog(group, { dialog = "" }) { kind -> vm.classifyRelease(group.id, kind); dialog = "" }
        "artists" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Artistes de cette sortie") }, text = { Column { artistGroups(group.editions.flatMap { it.tracks }, data.catalog).forEach { artist -> TextButton({ dialog = ""; onArtist(artist.id) }) { Text(artist.name) } } } }, confirmButton = { TextButton({ dialog = "" }) { Text("Fermer") } })
        "separate" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Séparer les éditions ?") }, text = { Text("Chaque édition retrouve sa carte. Les fichiers, associations d’enregistrements, favoris, paroles et playlists restent intacts. Aucune fusion automatique de ce groupe ne sera réappliquée.") }, confirmButton = { TextButton({ vm.separateEditions(group); dialog = ""; onBack() }) { Text("Séparer") } }, dismissButton = { TextButton({ dialog = "" }) { Text("Annuler") } })
        "recordings" -> RecordingAssociationsDialog(group, data.catalog, vm) { dialog = "" }
    }
}
