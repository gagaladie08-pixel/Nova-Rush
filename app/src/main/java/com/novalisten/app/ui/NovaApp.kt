package com.novalisten.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novalisten.app.INITIAL_TAB
import com.novalisten.app.MainViewModel
import com.novalisten.app.PlayerPresentation
import com.novalisten.app.data.*
import com.novalisten.app.enrichment.resolveLyrics
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.screens.*
import com.novalisten.app.ui.theme.*
import kotlinx.coroutines.launch

private data class MainTab(val name: String, val icon: ImageVector)
private val mainTabs = listOf(MainTab("Accueil", Icons.Outlined.Home), MainTab("Bibliothèque", Icons.Outlined.LibraryMusic), MainTab("Artistes", Icons.Outlined.PeopleOutline), MainTab("Albums", Icons.Outlined.Album), MainTab("Playlists", Icons.Outlined.QueueMusic), MainTab("Listening Party", Icons.Outlined.Groups), MainTab("YouTube", Icons.Outlined.OndemandVideo))

@Composable
fun NovaApp(vm: MainViewModel, onRequestAudio: () -> Unit, onRequestNotifications: () -> Unit, onSystemSettings: () -> Unit, onDeleteFiles: (List<Track>) -> Unit, onChooseImage: (String, String) -> Unit) {
    val library by vm.repository.library.collectAsStateWithLifecycle()
    val data by vm.repository.data.collectAsStateWithLifecycle()
    val catalog by vm.repository.catalog.collectAsStateWithLifecycle()
    val tabStates = rememberSaveableStateHolder()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val playerRequest by vm.playerOpenRequests.collectAsStateWithLifecycle()
    val automatic by vm.automaticLyrics.state.collectAsStateWithLifecycle()
    fun lyricsFor(id: String) = resolveLyrics(id, data.lyrics[id], automatic,
        data.automaticLyrics[id]?.forTrack(library.tracks.firstOrNull { it.id == id }))
    val settings by vm.themes.state.collectAsStateWithLifecycle()
    val visible = remember(library.tracks, data.hidden) { visibleTracks(library.tracks, data.hidden) }
    val playable = remember(library.tracks, data.hidden) { playableTracks(library.tracks, data.hidden) }
    var tab by rememberSaveable { mutableIntStateOf(INITIAL_TAB) }
    var route by rememberSaveable { mutableStateOf("") }
    var routeParent by rememberSaveable { mutableStateOf("") }
    var artistId by rememberSaveable { mutableStateOf<String?>(null) }
    var artistLibrary by rememberSaveable { mutableStateOf(false) }
    var albumId by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var presentation by rememberSaveable(stateSaver = listSaver<PlayerPresentation, Any>(
        save = { listOf(it.open, it.consumedToken) }, restore = { PlayerPresentation(it[0] as Boolean, it[1] as String) }
    )) { mutableStateOf(PlayerPresentation()) }
    val playerOpen = presentation.open
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var artistChoiceTrack by remember { mutableStateOf<Track?>(null) }
    var creditEditor by remember { mutableStateOf<Track?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var lyricsInfo by remember { mutableStateOf<Track?>(null) }
    var addTracks by remember { mutableStateOf<List<Track>?>(null) }
    var deleteTracks by remember { mutableStateOf<List<Track>?>(null) }
    var editingLyrics by remember { mutableStateOf<Track?>(null) }
    var enrichment by remember { mutableStateOf<EnrichmentRequest?>(null) }
    var createPlaylist by remember { mutableStateOf(false) }
    var pickPlaylist by remember { mutableStateOf<String?>(null) }
    var removeTrack by remember { mutableStateOf<Pair<String, String>?>(null) }
    val artists = catalog.artists
    val albums = catalog.albums
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tab, route, artistId) {
        vm.prioritizeArtists(if (tab == 2 && route.isEmpty()) listOfNotNull(artistId) else emptyList())
    }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(playback.currentId) { presentation = presentation.onPlayback(playback.currentId) }
    LaunchedEffect(playerRequest.token) { presentation = presentation.accept(playerRequest) }
    fun openArtist(id: String) { tab = 2; artistId = artists.firstOrNull { it.id == id || (it.inheritLegacy && id in it.legacyIds) }?.id ?: id; artistLibrary = false; route = ""; presentation = presentation.dismiss() }
    fun openAlbum(id: String) { tab = 3; albumId = id; route = ""; presentation = presentation.dismiss() }
    fun openPlaylist(id: String) { tab = 4; playlistId = id; route = "" }
    fun goBack() {
        when {
            route == "custom" -> route = "themes"
            route.isNotEmpty() -> { route = routeParent; routeParent = "" }
            artistLibrary && tab == 2 -> artistLibrary = false
            artistId != null && tab == 2 -> artistId = null
            albumId != null && tab == 3 -> albumId = null
            playlistId != null && tab == 4 -> playlistId = null
            tab != INITIAL_TAB -> tab = INITIAL_TAB
        }
    }
    BackHandler(route.isNotEmpty() || tab != INITIAL_TAB || artistId != null || albumId != null || playlistId != null) { goBack() }
    val actions = TrackActions(menu = { menuTrack = it }, info = { infoTrack = it }, add = { addTracks = it }, delete = { deleteTracks = it })
    val current = catalog.tracks[playback.currentId]
    NovaTheme(settings.universe, settings.effects) {
        Box(Modifier.fillMaxSize().testTag("app_window")) {
            if (!playerOpen || current == null) UniverseBackdrop()
            Scaffold(modifier = if (playerOpen && current != null) Modifier.clearAndSetSemantics {} else Modifier,
                containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NovaIcon(Icons.Outlined.GraphicEq, modifier = Modifier.size(33.dp), tint = LocalUniverse.current.accent)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("NovaListen", style = MaterialTheme.typography.titleLarge)
                            Text("LOCAL / ${settings.universe.name.uppercase()}", style = MaterialTheme.typography.labelSmall, color = LocalUniverse.current.accent, maxLines = 1)
                        }
                        IconButton({ routeParent = ""; route = "themes" }) { NovaIcon(Icons.Outlined.Palette, "Choisir le thème") }
                        IconButton({ routeParent = ""; route = "settings" }) { NovaIcon(Icons.Outlined.Settings, "Réglages") }
                    }
                    HorizontalDivider(color = LocalUniverse.current.primary.copy(alpha = .2f))
                }
            }, bottomBar = {
                Column(Modifier.navigationBarsPadding().background(LocalUniverse.current.background)) {
                    val current = catalog.tracks[playback.currentId]
                    if (current != null && !playerOpen) MiniPlayer(current, playback, vm) { presentation = presentation.openManually() }
                    HorizontalDivider(color = LocalUniverse.current.primary.copy(alpha = .2f))
                    LazyRow(Modifier.fillMaxWidth().testTag("navigation"), contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        itemsIndexed(mainTabs) { index, item ->
                            Column(Modifier.widthIn(min = 76.dp).selectable(selected = tab == index && route.isEmpty(), role = Role.Tab, onClick = {
                                if (tab == index && route.isEmpty()) when (index) {
                                    2 -> { artistId = null; artistLibrary = false }
                                    3 -> albumId = null
                                    4 -> playlistId = null
                                }
                                tab = index; route = ""; routeParent = ""
                            }).testTag("tab_$index").padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                NovaIcon(item.icon, tint = if (tab == index && route.isEmpty()) LocalUniverse.current.accent else LocalUniverse.current.text.copy(alpha = .6f))
                                Spacer(Modifier.height(4.dp)); Text(item.name, style = MaterialTheme.typography.labelSmall, color = if (tab == index && route.isEmpty()) LocalUniverse.current.accent else LocalUniverse.current.text.copy(alpha = .8f))
                            }
                        }
                    }
                }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (route) {
                        "themes" -> AppearanceScreen(settings, vm.themes, ::goBack) { route = "custom" }
                        "custom" -> CustomThemeScreen(settings, vm.themes, ::goBack)
                        "settings" -> SettingsScreen(data, vm, ::goBack,
                            onAppearance = { routeParent = "settings"; route = "themes" }, onHidden = { routeParent = "settings"; route = "hidden" },
                            onHistory = { routeParent = "settings"; route = "history" }, onSystem = onSystemSettings, onNotifications = onRequestNotifications,
                            onApi = { routeParent = "settings"; route = "api" }, onHelp = { routeParent = "settings"; route = "help" })
                        "api" -> ApiSettingsScreen(vm, ::goBack)
                        "help" -> HelpScreen(::goBack)
                        "hidden" -> HiddenScreen(library.tracks, data, vm, ::goBack)
                        "history" -> HistoryScreen(playable, data, ::goBack) { vm.play(playable, it) }
                        "favorites" -> TrackListScreen("Favoris", playable.filter { it.id in data.favorites }, data, playback, vm, actions, ::goBack)
                        else -> {
                            val t = settings.universe
                            AnimatedContent(targetState = tab, label = "Onglet", transitionSpec = {
                                if (!settings.effects) EnterTransition.None togetherWith ExitTransition.None
                                else when (t.effect) {
                                    "villain", "venom" -> slideInHorizontally(tween(t.transitionMs ?: 150)) { 12 } togetherWith slideOutHorizontally(tween(t.transitionMs ?: 150)) { -12 }
                                    "survivor" -> slideInVertically(if (t.id == "custom" && t.transitionMs != null) tween(t.transitionMs) else spring(dampingRatio = .9f)) { it / 4 } togetherWith ExitTransition.None
                                    "y2k", "queen" -> (scaleIn(spring(dampingRatio = .65f), initialScale = .95f) + fadeIn(tween(t.transitionMs ?: 320))) togetherWith fadeOut(tween(120))
                                    "velvet" -> slideInHorizontally(if (t.transitionMs != null) tween(t.transitionMs) else spring()) { it / 3 } togetherWith fadeOut(if (t.transitionMs != null) tween(t.transitionMs / 2) else spring())
                                    "chaos" -> slideInHorizontally(if (t.id == "custom" && t.transitionMs != null) tween(t.transitionMs) else tween((100..400).random())) { 14 } togetherWith ExitTransition.None
                                    else -> fadeIn(if (t.transitionMs != null) tween(t.transitionMs) else spring()) togetherWith fadeOut(if (t.transitionMs != null) tween(t.transitionMs) else spring())
                                }
                            }) { activeTab ->
                                val destination = when (activeTab) {
                                    2 -> artistId?.let { "artist/detail/$it/$artistLibrary" } ?: "artist/index"
                                    3 -> albumId?.let { "album/detail/$it" } ?: "album/index"
                                    4 -> playlistId?.let { "playlist/detail/$it" } ?: "playlist/index"
                                    else -> "root"
                                }
                                tabStates.SaveableStateProvider("$activeTab/$destination") {
                                if (!library.permission && activeTab < 5) {
                                    EmptyState("Votre musique vous attend", "Autorisez l’accès à vos fichiers audio.", Icons.Outlined.Headphones, Modifier.fillMaxSize(), action = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Button(onRequestAudio, Modifier.testTag("grant_audio")) { Text("Autoriser ma musique") }
                                            TextButton(onSystemSettings) { Text("Ouvrir les paramètres Android") }
                                            Text("NovaListen ${com.novalisten.app.BuildConfig.VERSION_NAME} · votre lecteur local", style = MaterialTheme.typography.bodySmall)
                                        }
                                    })
                                } else if (((library.loading && library.tracks.isEmpty()) || !catalog.ready) && activeTab < 5 && library.error == null) {
                                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator(); Spacer(Modifier.height(18.dp)); Text("Lecture de votre bibliothèque…") }
                                } else if (library.error != null && activeTab < 5) {
                                    EmptyState("Bibliothèque indisponible", library.error!!, Icons.Outlined.ErrorOutline, Modifier.fillMaxSize(), action = { Button({ vm.repository.refresh() }) { Text("Réessayer") } })
                                } else when (activeTab) {
                                    0 -> HomeScreen(playable, data, vm, onLibrary = { tab = 1 }, onFavorites = { routeParent = ""; route = "favorites" }, onHistory = { routeParent = ""; route = "history" }, onPlaylist = ::openPlaylist)
                                    1 -> TrackListScreen("Bibliothèque", visible, data, playback, vm, actions)
                                    2 -> {
                                        val artist = catalog.artistsById[artistId]
                                        if (artist != null && artistLibrary) TrackListScreen(artist.name, artist.tracks, data, playback, vm, actions, onBack = { artistLibrary = false })
                                        else if (artist != null) ArtistDetail(artist, data, playback, vm, actions, onBack = { artistId = null }, onAlbum = ::openAlbum, onLibrary = { artistLibrary = true }, onPortrait = { onChooseImage("artist", artist.id) }, onBiography = { enrichment = EnrichmentRequest(EnrichmentMode.BIOGRAPHY, artistId = artist.id, name = artist.name) }, onPhotos = { enrichment = EnrichmentRequest(EnrichmentMode.PHOTOS, artistId = artist.id, name = artist.name) }, allAlbums = catalog.albumsByArtist[artist.id].orEmpty())
                                        else CatalogScreen(true, catalog, data, vm, ::openArtist, ::openAlbum, { addTracks = it }, active = activeTab == tab)
                                    }
                                    3 -> {
                                        val album = catalog.albumsById[albumId]
                                        if (album != null) AlbumDetail(album, catalog, data, playback, vm, actions, { albumId = null }, ::openArtist, onCovers = { enrichment = EnrichmentRequest(EnrichmentMode.COVERS, album.representative.tracks.first(), name = album.name, albumKey = album.representative.tracks.first().albumKey) })
                                        else CatalogScreen(false, catalog, data, vm, ::openArtist, ::openAlbum, { addTracks = it })
                                    }
                                    4 -> {
                                        val playlist = data.playlists.find { it.id == playlistId }
                                        if (playlist != null) PlaylistDetail(playlist, playable, data, playback, vm, actions, { playlistId = null }, { pickPlaylist = playlist.id }, { onChooseImage("playlist", playlist.id) }) {
                                            vm.deletePlaylist(playlist) {
                                                playlistId = null
                                                scope.launch { if (snackbar.showSnackbar("Playlist supprimée · fichiers conservés", "Annuler", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) vm.savePlaylist(playlist) }
                                            }
                                        } else PlaylistIndex(playable, data, vm, ::openPlaylist) { createPlaylist = true }
                                    }
                                    5 -> EmptyState("Listening Party", "Indisponible dans cette version.", Icons.Outlined.Groups, Modifier.fillMaxSize())
                                    else -> EmptyState("YouTube", "Indisponible dans cette version.", Icons.Outlined.OndemandVideo, Modifier.fillMaxSize())
                                }
                                }
                            }
                        }
                    }
                }
            }
            // Same activity window and real measured viewport as the application. A second
            // edge-to-edge Dialog can report a different height/inset set on some devices.
            if (playerOpen && current != null) FullPlayer(current, playback, data, playable, vm, lyricsFor(current.id), automatic,
                onDismiss = { presentation = presentation.dismiss() }, onMenu = { menuTrack = current }, onAdd = { addTracks = listOf(current) },
                onEditLyrics = { editingLyrics = current }, onLyricsInfo = { lyricsInfo = current },
                blocked = menuTrack != null || addTracks != null || editingLyrics != null || infoTrack != null || lyricsInfo != null || deleteTracks != null || enrichment != null)
        }
        menuTrack?.let { track -> TrackMenu(track, tab == 4 && playlistId != null, vm, { menuTrack = null }, { infoTrack = track }, { addTracks = listOf(track) }, { val matches = catalog.artistsByTrack[track.id].orEmpty(); if (matches.size == 1) openArtist(matches.single().id) else artistChoiceTrack = track }, { openAlbum(catalog.albumByTrack[track.id]?.id ?: track.albumKey) }, { deleteTracks = listOf(track) }, { playlistId?.let { removeTrack = it to track.id } }, onOnlineLyrics = { enrichment = EnrichmentRequest(EnrichmentMode.LYRICS, track) }, onCatalog = { enrichment = EnrichmentRequest(EnrichmentMode.CATALOG, track) }, onCover = { enrichment = EnrichmentRequest(EnrichmentMode.COVERS, track) },
            hasLyrics = lyricsFor(track.id).original.isNotBlank(), onTranslateLyrics = { enrichment = EnrichmentRequest(EnrichmentMode.TRANSLATE, track) },
            onEditLyrics = { editingLyrics = track }, onLyricsInfo = { lyricsInfo = track }, onCorrectArtists = { creditEditor = track }) }
        artistChoiceTrack?.let { track -> AlertDialog(onDismissRequest = { artistChoiceTrack = null }, title = { Text("Artistes du morceau") }, text = {
            Column { catalog.artistsByTrack[track.id].orEmpty().forEach { artist -> TextButton({ artistChoiceTrack = null; openArtist(artist.id) }) { Text(artist.name) } }
                TextButton({ artistChoiceTrack = null; creditEditor = track }) { Text("Corriger les participants") }
            }
        }, confirmButton = { TextButton({ artistChoiceTrack = null }) { Text("Fermer") } }) }
        creditEditor?.let { track -> ArtistCreditsDialog(track, data.catalog, vm) { creditEditor = null } }
        infoTrack?.let { TrackInfoDialog(it) { infoTrack = null } }
        lyricsInfo?.let { LyricsDetailsDialog(it, lyricsFor(it.id), automatic) { lyricsInfo = null } }
        addTracks?.let { AddToPlaylistDialog(it, data.playlists, vm) { addTracks = null } }
        editingLyrics?.let { track -> LyricsEditor(track, lyricsFor(track.id), vm, onDismiss = { editingLyrics = null }, onSearch = { editingLyrics = null; enrichment = EnrichmentRequest(EnrichmentMode.LYRICS, track) }, onTranslate = { editingLyrics = null; enrichment = EnrichmentRequest(EnrichmentMode.TRANSLATE, track) }) }
        enrichment?.let { request -> EnrichmentDialog(request, vm, onDismiss = { enrichment = null }, onKeys = { enrichment = null; presentation = presentation.dismiss(); routeParent = "settings"; route = "api" }) }
        if (createPlaylist) CreatePlaylistDialog(vm, { createPlaylist = false }) { playlist, pick -> openPlaylist(playlist.id); if (pick) pickPlaylist = playlist.id }
        data.playlists.find { it.id == pickPlaylist }?.let { playlist -> TrackPickerDialog(playable, playlist, vm) { pickPlaylist = null } }
        removeTrack?.let { (listId, trackId) -> AlertDialog(onDismissRequest = { removeTrack = null }, title = { Text("Retirer de cette playlist ?") }, text = { Text("Le fichier reste sur le téléphone, dans vos autres playlists et dans la file déjà lancée.") }, confirmButton = { TextButton({ data.playlists.find { it.id == listId }?.let { vm.savePlaylist(it.copy(trackIds = it.trackIds.filterNot { id -> id == trackId })) }; removeTrack = null }) { Text("Retirer") } }, dismissButton = { TextButton({ removeTrack = null }) { Text("Annuler") } }) }
        deleteTracks?.let { list -> AlertDialog(onDismissRequest = { deleteTracks = null }, title = { Text("Effacer du téléphone ?") }, text = { Text("Cette action supprime réellement ${list.size} fichier(s) audio, pas seulement leurs entrées dans NovaListen. Android peut demander une confirmation supplémentaire. Pas de restauration garantie.\n\n${list.take(4).joinToString("\n") { it.title }}") }, confirmButton = { TextButton({ onDeleteFiles(list); deleteTracks = null }) { Text("Effacer les fichiers") } }, dismissButton = { TextButton({ deleteTracks = null }) { Text("Annuler") } }) }
    }
}
