@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.novalisten.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.novalisten.app.MainViewModel
import com.novalisten.app.PlaybackUi
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.theme.*
import com.novalisten.app.ui.gestures.lyricsPageAfterSwipe
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun MiniPlayer(track: Track, playback: PlaybackUi, vm: MainViewModel, onOpen: () -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Surface(color = LocalUniverse.current.surface, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().testTag("mini_player").clickable(onClick = onOpen).pointerInput(track.id) {
        detectDragGestures(onDragEnd = {
            if (abs(dx) > 60.dp.toPx() && abs(dx) > abs(dy)) { if (dx < 0) vm.forward() else vm.previous() }
            else if (dy < -45.dp.toPx()) onOpen()
            dx = 0f; dy = 0f
        }, onDragCancel = { dx = 0f; dy = 0f }) { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
    }) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 74.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.artwork, track.title, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(vm::toggle, Modifier.size(48.dp)) { Icon(if (playback.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playback.playing) "Pause" else "Lecture") }
                IconButton(vm::forward, Modifier.size(48.dp)) { Icon(Icons.Outlined.SkipNext, "Morceau suivant") }
            }
            MiniProgress(playback, vm)
        }
    }
}

@Composable
private fun MiniProgress(playback: PlaybackUi, vm: MainViewModel) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val position = if (progress.trackId == playback.currentId) progress.position else playback.position
    val duration = if (progress.trackId == playback.currentId) progress.duration else playback.duration
    LinearProgressIndicator(progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().height(2.dp), color = LocalUniverse.current.accent, trackColor = LocalUniverse.current.primary.copy(alpha = .15f))
}

/** Same cached artwork on cover and lyrics pages; static layer does not depend on playback position. */
@Composable
internal fun PlayerBackdrop(artwork: String) {
    val t = LocalUniverse.current
    UniverseBackdrop()
    val context = LocalContext.current
    val blurred = remember(context, artwork) { coil.request.ImageRequest.Builder(context).data(artwork)
        .size(96, 96).allowHardware(false).transformations(ArtworkBlur).build() }
    AsyncImage(blurred, null, Modifier.fillMaxSize().alpha(.50f).testTag("player_background_artwork"), contentScale = ContentScale.Crop)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.background.copy(alpha = .55f), t.background.copy(alpha = .65f), t.background.copy(alpha = .93f)))))
}

@Composable
fun FullPlayer(track: Track, playbackState: PlaybackUi, data: LocalData, tracks: List<Track>, vm: MainViewModel,
               lyrics: SavedLyrics, automatic: com.novalisten.app.enrichment.AutoLyricState,
               onDismiss: () -> Unit, onMenu: () -> Unit, onAdd: () -> Unit, onEditLyrics: () -> Unit, onLyricsInfo: () -> Unit, blocked: Boolean) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val playback = if (progress.trackId == track.id) playbackState.copy(position = progress.position, duration = progress.duration) else playbackState
    var lyricsPage by rememberSaveable { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var touched by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    var french by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        val view = LocalView.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(view, lifecycle) {
            val previous = view.keepScreenOn
            fun applyKeepScreen() { view.keepScreenOn = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            val observer = LifecycleEventObserver { _, _ -> applyKeepScreen() }
            lifecycle.addObserver(observer); applyKeepScreen()
            onDispose { lifecycle.removeObserver(observer); view.keepScreenOn = previous }
        }
        BackHandler { if (lyricsPage) { lyricsPage = false; controls = true } else onDismiss() }
        LaunchedEffect(lastTouch, controls, lyricsPage, blocked, touched, queue) {
            if (controls && !lyricsPage && !blocked && !queue && !touched) { delay(7000); controls = false }
        }
        var dx by remember { mutableFloatStateOf(0f) }
        var dy by remember { mutableFloatStateOf(0f) }
        val currentControls by rememberUpdatedState(controls)
        val currentLyrics by rememberUpdatedState(lyricsPage)
        val t = LocalUniverse.current
        val saved = lyrics
        val showFrench = french && saved.french.isNotBlank()
        val text = if (showFrench) saved.french else saved.original
        val timed = remember(text, showFrench, saved.originalTiming) { if (showFrench) LrcParser.parse(text) else LyricTiming.decode(text, saved.originalTiming) }
        val active = LrcParser.activeIndex(timed, playback.position)
        Box(Modifier.fillMaxSize().background(t.background).testTag("full_player").semantics { paneTitle = "Lecteur plein écran" }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = System.currentTimeMillis()
                        touched = event.changes.any { it.pressed }
                    }
                }
            }
            .pointerInput(lyricsPage) {
                detectDragGestures(onDragEnd = {
                    if (abs(dx) > 75.dp.toPx() && abs(dx) > abs(dy)) {
                        val nextPage = lyricsPageAfterSwipe(currentLyrics, dx, dy, 75.dp.toPx())
                        if (nextPage != currentLyrics) { lyricsPage = nextPage; controls = true }
                    } else if (!currentControls && !currentLyrics && abs(dy) > 75.dp.toPx()) {
                        if (dy < 0) vm.previous() else vm.forward()
                    }
                    dx = 0f; dy = 0f
                }, onDragCancel = { dx = 0f; dy = 0f }) { change, amount ->
                    if (!currentLyrics || abs(amount.x) > abs(amount.y)) { change.consume(); dx += amount.x; dy += amount.y }
                }
            }) {
            PlayerBackdrop(track.artwork)
            PlayerFrame(
                insets = WindowInsets.safeDrawing.union(WindowInsets.safeGestures).union(rememberPlayerWindowInsets()),
                header = { compact -> PlayerHeader(track, lyricsPage, compact, onDismiss, onMenu) },
                footer = { compact ->
                    if (controls || lyricsPage) Column(Modifier.fillMaxWidth()) {
                        if (!lyricsPage) PlayerActions(track.id in data.favorites, { vm.favorite(track) }, onAdd, { lyricsPage = true }, onEditLyrics)
                        PlayerTransport(playback, compact, vm::seek, vm::toggle, vm::previous, vm::forward, vm::cycleRepeat) { queue = true }
                    }
                }
            ) { compact ->
                if (lyricsPage) Column(Modifier.fillMaxSize().testTag("player_lyrics_page")) {
                    LyricsBody(track.id, text, timed, active, playback.position, playback.playing, automatic, Modifier.weight(1f), vm::seek)
                    val credit = if (showFrench) saved.frenchSource else saved.originalSource
                    if (credit.isNotBlank() || saved.french.isNotBlank()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (credit.isNotBlank()) TextButton(onLyricsInfo, Modifier.weight(1f)) {
                            Text(credit.lineSequence().firstOrNull().orEmpty().take(36), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        } else Spacer(Modifier.weight(1f))
                        if (saved.french.isNotBlank()) {
                            TextButton({ french = false }, enabled = showFrench) { Text("VO") }
                            TextButton({ french = true }, enabled = !showFrench) { Text("FR") }
                        }
                    }
                    if (showFrench && saved.frenchSource.startsWith("Google Translate")) GoogleTranslationAttribution()
                } else PlayerCoverPage(track, controls, compact, timed.getOrNull(active)?.text,
                    text.isBlank() && automatic.trackId == track.id && automatic.status == com.novalisten.app.enrichment.AutoLyricStatus.LOADING,
                    onReveal = { controls = true; lastTouch = System.currentTimeMillis() }, onLyrics = { lyricsPage = true })
            }
        }
        if (queue) ModalBottomSheet(onDismissRequest = { queue = false }) {
            Text("File d’attente · ${playback.queue.size}", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.fillMaxHeight(.7f)) {
                itemsIndexed(playback.queue) { index, id ->
                    tracks.find { it.id == id }?.let { song -> ListItem(headlineContent = { Text(song.title, maxLines = 1) }, supportingContent = { Text(song.artist) }, leadingContent = { Text(if (index == playback.index) "▶" else "${index + 1}") }, modifier = Modifier.clickable { vm.jumpTo(index); queue = false }) }
                }
            }
        }
    }
}

@Composable
internal fun PlayerHeader(track: Track, lyricsPage: Boolean, compact: Boolean, onDismiss: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onDismiss) { NovaIcon(Icons.Outlined.KeyboardArrowDown, "Réduire le player") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!compact) Text(if (lyricsPage) "PAROLES" else "LECTURE LOCALE", style = MaterialTheme.typography.labelMedium, color = LocalUniverse.current.accent, maxLines = 1)
            Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onMenu) { NovaIcon(Icons.Outlined.MoreVert, "Actions du morceau") }
    }
}

@Composable
internal fun PlayerActions(favorite: Boolean, onFavorite: () -> Unit, onAdd: () -> Unit, onLyrics: () -> Unit, onEditLyrics: () -> Unit) {
    val t = LocalUniverse.current
    Row(Modifier.fillMaxWidth().testTag("player_actions"), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onFavorite, Modifier.size(48.dp)) { Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Favori", tint = if (favorite) t.primary else t.text) }
        IconButton(onAdd, Modifier.size(48.dp)) { Icon(Icons.Outlined.PlaylistAdd, "Ajouter à une playlist") }
        IconButton(onLyrics, Modifier.size(48.dp)) { Icon(Icons.Outlined.Lyrics, "Afficher les paroles") }
        IconButton(onEditLyrics, Modifier.size(48.dp)) { Icon(Icons.Outlined.EditNote, "Modifier les paroles") }
    }
}

@Composable
internal fun PlayerCoverPage(track: Track, controls: Boolean, compact: Boolean, lyricLine: String?, loading: Boolean,
                             onReveal: () -> Unit, onLyrics: () -> Unit = {}) {
    val t = LocalUniverse.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("player_cover_page").clickable(onClickLabel = "Afficher les commandes", onClick = onReveal)) {
        val coverSize = minOf(maxWidth * .86f, maxHeight * .65f)
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().then(if (controls) Modifier.verticalScroll(scroll) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (controls) {
                if (coverSize > 0.dp) Artwork(track.artwork, track.title, Modifier.size(coverSize).testTag("player_artwork"))
                if (lyricLine != null || loading) Text(lyricLine ?: "Chargement des paroles…",
                    Modifier.fillMaxWidth().clickable(onClick = onLyrics).padding(vertical = if (compact) 8.dp else 16.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = t.text, textAlign = TextAlign.Center)
            } else if (lyricLine != null) {
                Text(lyricLine, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
