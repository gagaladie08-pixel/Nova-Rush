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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import kotlinx.coroutines.flow.distinctUntilChanged
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
import com.novalisten.app.ui.theme.NovaIcon

@Composable
fun CatalogScreen(artists: Boolean, index: CatalogSnapshot, data: LocalData, vm: MainViewModel, onArtist: (String) -> Unit, onAlbum: (String) -> Unit, onAdd: (List<Track>) -> Unit, active: Boolean = true) {
    val artistList = index.artists
    val albumList = index.albums
    val grid = rememberLazyGridState()
    LaunchedEffect(grid, artists, artistList, active) {
        if (artists && active) snapshotFlow { grid.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .distinctUntilChanged().collect { vm.prioritizeArtists(it) }
    }
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
    var issues by remember { mutableStateOf<List<ArtistKeyIssue>?>(null) }
    // Diagnostic des noms en double : ne dépend que de l'index, jamais de la recherche.
    val duplicates = remember(index) {
        if (artists) splitArtistIdentities(index.sourceTracks, index.sourceChoices, index.albumsByArtist) else emptyList()
    }
    val artistResults = remember(artistList, query, sort, artists) {
        val key = query.normalized()
        if (artists) sortCatalog(artistList.filter { key in it.name.normalized() }, sort, { it.name }, { it.tracks.size }) else emptyList()
    }
    val albumResults = remember(albumList, query, sort, kindName, artists) { if (artists) emptyList() else sortCatalog(albumList.filter { group ->
        group.kind !in setOf(ReleaseKind.SINGLE, ReleaseKind.COMPILATION) && (kindName == "ALL" || group.kind.name == kindName) &&
            group.editions.any { query.normalized() in (it.name + " " + it.artist).normalized() }
    }, sort, { it.name }, { it.tracks.size }) }
    val selectedAlbums = albumResults.filter { it.id in selected }
    val selectedTracks = (if (artists) artistResults.filter { it.id in selected }.flatMap { it.tracks } else selectedAlbums.flatMap { it.tracks }).distinctBy { it.id }
    fun select(id: String) { selected = if (id in selected) selected - id else selected + id }
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().testTag(if (artists) "artist_catalog" else "album_catalog"), state = grid, contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column {
                PageTitle(if (selected.isEmpty()) if (artists) "Artistes" else "Albums" else "${selected.size} sélectionné(s)", "${if (artists) artistResults.size else albumResults.size} ${if (artists) "artistes" else "sorties locales"}") {
                    if (selected.isNotEmpty()) IconButton({ selected = emptySet() }) { NovaIcon(Icons.Outlined.Close, "Quitter la sélection") }
                    else {
                        IconButton({ searching = !searching }) { NovaIcon(Icons.Outlined.Search, "Rechercher") }
                        Box {
                            IconButton({ sortOpen = true }, Modifier.testTag("catalog_sort")) { NovaIcon(Icons.Outlined.Sort, "Trier par nom ou nombre de titres") }
                            DropdownMenu(sortOpen, { sortOpen = false }) {
                                CatalogSort.entries.forEach { value -> DropdownMenuItem({ Text((if (sort == value) "✓ " else "") + value.label) }, {
                                    sortName = value.name; preferences.edit().putString(preferenceKey, value.name).apply(); sortOpen = false
                                }, modifier = Modifier.testTag("catalog_sort_${value.name}")) }
                            }
                        }
                        if (!artists) Box {
                            IconButton({ kindOpen = true }) { NovaIcon(Icons.Outlined.FilterList, "Filtrer les types de sorties") }
                            DropdownMenu(kindOpen, { kindOpen = false }) {
                                DropdownMenuItem({ Text("Tous les types de cet index") }, { kindName = "ALL"; kindOpen = false; selected = emptySet() })
                                listOf(ReleaseKind.ALBUM, ReleaseKind.EP, ReleaseKind.MIXTAPE, ReleaseKind.UNKNOWN).forEach { value -> DropdownMenuItem({ Text(value.label) }, { kindName = value.name; kindOpen = false; selected = emptySet() }) }
                            }
                        }
                    }
                }
                if (searching) SearchField(query, { query = it; selected = emptySet() }, if (artists) "Nom de l’artiste" else "Album, édition ou artiste")
                if (selected.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ vm.next(selectedTracks); selected = emptySet() }) { Text("Lire ensuite") }
                        OutlinedButton({ onAdd(selectedTracks); selected = emptySet() }) { Text("Ajouter à une playlist") }
                    }
                    if (!artists && selectedAlbums.size > 1) TextButton({ grouping = true }, Modifier.testTag("group_editions")) { Text("Regrouper les éditions") }
                }
            }
        }
        if (artists && duplicates.isNotEmpty()) item(span = { GridItemSpan(2) }) {
            // Un doublon parfait du nom vient d'une clé différente. Le diagnostic est factuel :
            // il montre la clé de chaque carte au lieu de supposer laquelle est la bonne.
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${duplicates.size} nom(s) en double", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton({ issues = duplicates }, Modifier.testTag("artist_duplicates")) { Text("Voir pourquoi") }
            }
        }
        if (artists) itemsIndexed(artistResults, key = { _, group -> group.id }) { position, group ->
            Box(Modifier.padding(start = if (position % 2 == 0) 20.dp else 0.dp, end = if (position % 2 != 0) 20.dp else 0.dp)) {
                val note = artistNote(group, data)
                // Sans portrait : la pochette d'un album de cet artiste, pas des initiales ambiguës.
                val albumArt = note.portrait?.takeIf { it.isNotBlank() }
                    ?: index.albumsByArtist[group.id].orEmpty().firstNotNullOfOrNull { it.artwork }
                EntityCard(group.name, "${itemCount(group.tracks.size, "titre")} · ${itemCount(index.albumsByArtist[group.id].orEmpty().size, "sortie")}", albumArt, artist = true, selected = group.id in selected,
                    onClick = { if (selected.isNotEmpty()) select(group.id) else onArtist(group.id) }, onLongClick = { select(group.id) }, onNext = { vm.next(group.tracks) }, onInfo = { info = "${group.name}\n${group.tracks.size} morceaux locaux\n${totalDurationLabel(group.tracks.sumOf { it.durationMs })}" })
            }
        } else itemsIndexed(albumResults, key = { _, group -> group.id }) { position, group ->
            Box(Modifier.padding(start = if (position % 2 == 0) 20.dp else 0.dp, end = if (position % 2 != 0) 20.dp else 0.dp)) {
                EntityCard(group.name, "${group.artist}\n" + listOfNotNull(group.kind.label.takeIf { group.kind != ReleaseKind.UNKNOWN }, itemCount(group.tracks.size, "titre"), itemCount(group.editionCount, "édition").takeIf { group.editionCount > 1 }).joinToString(" · "), group.artwork, selected = group.id in selected,
                    onClick = { if (selected.isNotEmpty()) select(group.id) else onAlbum(group.id) }, onLongClick = { select(group.id) }, onNext = { vm.next(group.tracks) }, onInfo = { info = albumInformation(group) }, onPlay = { vm.play(group.tracks) })
            }
        }
        if ((artists && artistResults.isEmpty()) || (!artists && albumResults.isEmpty())) item(span = { GridItemSpan(2) }) {
            EmptyState(if (query.isBlank()) "Aucun ${if (artists) "artiste" else "album"} local" else "Aucun résultat", if (query.isBlank()) "" else "Essayez un autre nom.", if (artists) Icons.Outlined.PersonOutline else Icons.Outlined.Album)
        }
    }
    if (grouping) GroupEditionsDialog(selectedAlbums, { grouping = false }) { original ->
        vm.groupEditions(selectedAlbums.flatMap { it.editions }.map { it.id }.toSet(), original) { id -> grouping = false; selected = emptySet(); onAlbum(id) }
    }
    issues?.let { list -> AlertDialog(
        onDismissRequest = { issues = null },
        modifier = Modifier.testTag("artist_duplicate_details"),
        title = { Text("Même nom, deux identités") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("Une carte est identifiée par un identifiant MusicBrainz, l'autre par son nom seul, ou par un identifiant différent. Tant que deux identités existent pour un même nom, rien n'est fusionné : deux artistes homonymes ne doivent jamais être confondus. Aucun tag n'est modifié.", style = MaterialTheme.typography.bodySmall)
            list.sortedByDescending { it.cards.size }.forEach { issue ->
                Spacer(Modifier.height(14.dp))
                Text(issue.name, style = MaterialTheme.typography.titleSmall)
                issue.cards.forEach { card ->
                    Spacer(Modifier.height(6.dp))
                    Text("• ${card.origin}", style = MaterialTheme.typography.bodySmall)
                    Text("  ${card.tracks} morceau(x) · ${card.albums} sortie(s)", style = MaterialTheme.typography.bodySmall)
                    Text("  clé : ${card.key}", style = MaterialTheme.typography.bodySmall)
                    if (card.sample.isNotEmpty()) Text("  ex. : ${card.sample.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } },
        confirmButton = { TextButton({ issues = null }) { Text("Fermer") } },
    ) }
    info?.let { value -> AlertDialog(onDismissRequest = { info = null }, title = { Text("Informations locales") }, text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text(value) } }, confirmButton = { TextButton({ info = null }) { Text("Fermer") } }) }
}

@Composable
fun ArtistDetail(group: ArtistGroup, data: LocalData, playback: PlaybackUi, vm: MainViewModel, actions: TrackActions, onBack: () -> Unit, onAlbum: (String) -> Unit, onLibrary: () -> Unit, onPortrait: () -> Unit, onBiography: () -> Unit, onPhotos: () -> Unit, allAlbums: List<AlbumGroup>) {
    val note = artistNote(group, data)
    val automatic by vm.automaticArtists.state.collectAsStateWithLifecycle()
    var legacyOpen by remember { mutableStateOf(false) }
    val oldProfiles = group.tracks.map { it.artistKey }.distinct().filter { it != group.id && it != group.legacyId && it in data.artists }
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var bio by remember(group.id) { mutableStateOf(note.biography) }
    var source by remember(group.id) { mutableStateOf(note.source) }
    var albumInfo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note, editing) {
        if (!editing) { bio = note.biography; source = note.source }
    }
    val albums = allAlbums
    val counts = remember(data.events) { data.events.groupingBy { it.trackId }.eachCount() }
    val top = group.tracks.filter { counts.getOrDefault(it.id, 0) > 0 }.sortedByDescending { counts[it.id] }.take(10)
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("Artiste", "Collection locale") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(note.portrait, group.name, Modifier.size(180.dp), round = true, artist = true)
                Spacer(Modifier.height(16.dp)); Text(group.name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                if (note.biography.isNotBlank()) Text(note.biography, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                if (note.biography.isNotBlank()) TextButton({ expanded = !expanded }) { Text(if (expanded) "Voir moins" else "Voir plus") }
                automatic[group.id]?.let { status ->
                    if (status.loading) ThemedLinearProgress({ 0.45f }, Modifier.fillMaxWidth(), "automatic_artist_loading")
                    if (expanded && status.note.isNotBlank()) Text(status.note, style = MaterialTheme.typography.bodySmall)
                }

                if (note.source.isNotBlank() || note.portraitSource.isNotBlank()) TextButton({ sourcesOpen = true }) { Text("Crédits et sources") }
            }
        }
        albums.groupBy { album -> if (album.editions.any { edition -> ArtistCredits.parse(edition.artist).any { it.name.artistNameKey() == group.legacyId } }) album.kind.label else "Participations" }.forEach { (category, releases) ->
            item { SectionHeader(if (category == ReleaseKind.UNKNOWN.label) "Sorties" else category) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(releases, key = { it.id }) { album -> Box(Modifier.width(160.dp)) {
                        EntityCard(album.name, "${album.tracks.size} titres locaux", album.artwork, onClick = { onAlbum(album.id) }, onNext = { vm.next(album.tracks) }, onInfo = { albumInfo = albumInformation(album) }, onPlay = { vm.play(album.tracks) })
                    } }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PlaybackButtons(group.tracks.isNotEmpty(), { vm.play(group.tracks) }, { vm.play(group.tracks, shuffled = true) }) }
                Box {
                    IconButton({ menu = true }) { NovaIcon(Icons.Outlined.MoreVert, "Actions de l’artiste") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Lire ensuite") }, { menu = false; vm.next(group.tracks) })
                        DropdownMenuItem({ Text("Ajouter à une playlist") }, { menu = false; actions.add(group.tracks) })
                        DropdownMenuItem({ Text("Rechercher des photos en ligne") }, { menu = false; onPhotos() })
                        DropdownMenuItem({ Text("Rechercher une biographie en ligne") }, { menu = false; onBiography() })
                        DropdownMenuItem({ Text("Choisir une photo locale") }, { menu = false; onPortrait() })
                        DropdownMenuItem({ Text("Modifier la biographie") }, { menu = false; bio = note.biography; source = note.source; editing = true })
                        DropdownMenuItem({ Text("Informations et sources") }, { menu = false; sourcesOpen = true })
                        if (oldProfiles.isNotEmpty()) DropdownMenuItem({ Text("Anciens profils") }, { menu = false; legacyOpen = true })
                        if ((note.biographyLocked && note.biography.isBlank()) || (note.portraitLocked && note.portrait == null)) {
                            DropdownMenuItem({ Text("Autoriser le remplissage des champs vides") }, { menu = false; vm.allowMissingArtistFields(group.id) })
                        }

                    }
                }
            }
        }
        if (top.isNotEmpty()) item { SectionHeader("Les plus écoutés") }
        items(top, key = { it.id }) { track -> TrackRow(track, track.id in data.favorites, playback.currentId == track.id, { vm.play(top, track) }, { vm.favorite(track) }, { actions.menu(track) }, { vm.next(listOf(track)) }, { actions.info(track) }, onSelect = { actions.add(listOf(track)) }) }
        item { OutlinedButton(onLibrary, Modifier.fillMaxWidth().padding(20.dp)) { NovaIcon(Icons.Outlined.LibraryMusic, null); Spacer(Modifier.width(8.dp)); Text("Bibliothèque de l’artiste") } }
        item { Spacer(Modifier.height(16.dp)) }
    }
    albumInfo?.let { value -> AlertDialog(onDismissRequest = { albumInfo = null }, title = { Text("Informations sur l’album") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text(value) } },
        confirmButton = { TextButton({ albumInfo = null }) { Text("Fermer") } }) }
    if (sourcesOpen) AlertDialog(onDismissRequest = { sourcesOpen = false }, title = { Text("Crédits et sources") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            if (note.source.isNotBlank()) SavedAttribution(note.source)
            if (note.portraitSource.isNotBlank()) SavedAttribution("Portrait :\n${note.portraitSource}")
            automatic[group.id]?.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (note.source.isBlank() && note.portraitSource.isBlank()) Text("Aucune source sauvegardée.")
        }
    }, confirmButton = { TextButton({ sourcesOpen = false }) { Text("Fermer") } })
    if (legacyOpen) LegacyArtistProfilesDialog(group, oldProfiles, data, vm) { legacyOpen = false }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Biographie locale") }, text = {
        Column { OutlinedTextField(bio, { bio = it.take(30_000) }, label = { Text("Votre texte") }, modifier = Modifier.heightIn(max = 240.dp)); Spacer(Modifier.height(8.dp)); OutlinedTextField(source, { source = it.take(1000) }, label = { Text("Source / crédits") }); Text("Sauvegardé dans NovaListen, sans réécrire les fichiers audio.", style = MaterialTheme.typography.bodySmall) }
    }, confirmButton = { TextButton({ vm.saveArtistBiography(group.id, bio, source + if (bio != note.biography && source.isNotBlank()) "\nAdaptation manuelle dans NovaListen." else ""); editing = false }) { Text("Sauvegarder") } }, dismissButton = { TextButton({ editing = false }) { Text("Annuler") } })
}

@Composable
fun AlbumDetail(group: AlbumGroup, index: CatalogSnapshot, data: LocalData, playback: PlaybackUi, vm: MainViewModel, actions: TrackActions, onBack: () -> Unit, onArtist: (String) -> Unit, onCovers: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    TrackListScreen("Album", group.tracks, data, playback, vm, actions, onBack = onBack, fixedOrder = true, fullQueueWhenSearching = true, header = {
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(group.artwork, group.name, Modifier.fillMaxWidth(.72f).aspectRatio(1f))
                Spacer(Modifier.height(18.dp)); Text(group.name, style = MaterialTheme.typography.headlineMedium)
                // Un invité chante SUR un morceau, il n'est pas l'artiste du disque. L'ancienne
                // version listait tous les participants de toutes les pistes : la page de
                // « The Fame Monster » affichait donc six noms, dont Beyoncé et Flo Rida.
                // Les invités restent visibles sur leur piste, et la liste complète reste
                // accessible par le menu « Artistes de cette sortie ».
                val head = index.artistsByAlbum[group.id].orEmpty()
                    .firstOrNull { it.name.artistNameKey() == group.artist.artistNameKey() }
                if (head != null) TextButton({ onArtist(head.id) }) { Text(head.name) }
                else if (group.artist.isNotBlank()) Text(group.artist)
                Text(listOfNotNull(group.year.takeIf { it > 0 }?.toString(), group.kind.label.takeIf { group.kind != ReleaseKind.UNKNOWN }, itemCount(group.tracks.size, "titre"), totalDurationLabel(group.tracks.sumOf { it.durationMs })).joinToString(" · "))
                if (group.editionCount > 1) Text(itemCount(group.editionCount, "édition"), style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onCovers) { NovaIcon(Icons.Outlined.ImageSearch, null); Text("Pochette") }
                    Box {
                        IconButton({ menu = true }) { NovaIcon(Icons.Outlined.MoreVert, "Actions de l’album") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Lire ensuite") }, { menu = false; vm.next(group.tracks) })
                            DropdownMenuItem({ Text("Ajouter à une playlist") }, { menu = false; actions.add(group.tracks) })
                            DropdownMenuItem({ Text("Informations sur l’album") }, { menu = false; dialog = "info" })
                            DropdownMenuItem({ Text("Aller dans l’artiste") }, { menu = false; dialog = "artists" })
                            if (group.editions.size > 1) {
                                DropdownMenuItem({ Text("Choisir la source représentative") }, { menu = false; dialog = "representative" })
                                DropdownMenuItem({ Text("Choisir l’édition d’origine / ordre") }, { menu = false; dialog = "original" })
                                DropdownMenuItem({ Text("Séparer les sources regroupées") }, { menu = false; dialog = "separate" })
                                DropdownMenuItem({ Text("Vérifier les enregistrements communs") }, { menu = false; dialog = "recordings" })
                            }
                            DropdownMenuItem({ Text("Classer cette sortie") }, { menu = false; dialog = "kind" })
                        }
                    }
                }

            }
        }
    })
    when (dialog) {
        "info" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Éditions et sources locales") }, text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text(albumInformation(group) + data.covers[group.representative.tracks.first().albumKey]?.let { "\n\n${it.source}" }.orEmpty()) } }, confirmButton = { TextButton({ dialog = "" }) { Text("Fermer") } })
        "representative", "original" -> EditionChoiceDialog(group, dialog == "original", { dialog = "" }) { edition -> vm.chooseEdition(group, edition, dialog == "original"); dialog = "" }
        "kind" -> ReleaseKindDialog(group, { dialog = "" }) { kind -> vm.classifyRelease(group.id, kind); dialog = "" }
        "artists" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Artistes de cette sortie") }, text = { Column { index.artistsByAlbum[group.id].orEmpty().forEach { artist -> TextButton({ dialog = ""; onArtist(artist.id) }) { Text(artist.name) } } } }, confirmButton = { TextButton({ dialog = "" }) { Text("Fermer") } })
        "separate" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Séparer les éditions ?") }, text = { Text("Chaque édition retrouve sa carte. Les fichiers, associations d’enregistrements, favoris, paroles et playlists restent intacts. Aucune fusion automatique de ce groupe ne sera réappliquée.") }, confirmButton = { TextButton({ vm.separateEditions(group); dialog = ""; onBack() }) { Text("Séparer") } }, dismissButton = { TextButton({ dialog = "" }) { Text("Annuler") } })
        "recordings" -> RecordingAssociationsDialog(group, data.catalog, vm) { dialog = "" }
    }
}
