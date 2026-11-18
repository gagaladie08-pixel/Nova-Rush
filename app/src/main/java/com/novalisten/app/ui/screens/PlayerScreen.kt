@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.novalisten.app.ui.screens

import android.view.WindowManager
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
            LinearProgressIndicator(progress = { if (playback.duration > 0) (playback.position.toFloat() / playback.duration).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = LocalUniverse.current.accent, trackColor = LocalUniverse.current.primary.copy(alpha = .15f))
        }
    }
}

@Composable
fun FullPlayer(track: Track, playback: PlaybackUi, data: LocalData, tracks: List<Track>, vm: MainViewModel,
               lyrics: SavedLyrics, automatic: com.novalisten.app.enrichment.AutoLyricState,
               onDismiss: () -> Unit, onMenu: () -> Unit, onAdd: () -> Unit, onEditLyrics: () -> Unit, onLyricsInfo: () -> Unit, blocked: Boolean) {
    var lyricsPage by rememberSaveable { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var touched by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    var french by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(window, lifecycle) {
            fun applyKeepScreen() { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val observer = LifecycleEventObserver { _, _ -> applyKeepScreen() }
            lifecycle.addObserver(observer); applyKeepScreen()
            onDispose { lifecycle.removeObserver(observer); window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
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
        Box(Modifier.fillMaxSize().background(t.background).testTag("full_player")
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
            UniverseBackdrop()
            if (!lyricsPage) {
                AsyncImage(track.artwork, null, Modifier.fillMaxSize().alpha(.55f), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.background.copy(alpha = .5f), Color.Transparent, t.background.copy(alpha = .92f)))))
            }
            PlayerFrame(
                header = { compact -> PlayerHeader(track, lyricsPage, compact, onDismiss, onMenu) },
                footer = { compact ->
                    if (controls || lyricsPage) PlayerTransport(playback, compact, vm::seek, vm::toggle, vm::previous, vm::forward, vm::cycleRepeat) { queue = true }
                    else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton({ controls = true }) { Text("Afficher les commandes") }
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
                    track.id in data.favorites, onReveal = { controls = true; lastTouch = System.currentTimeMillis() },
                    onFavorite = { vm.favorite(track) }, onAdd = onAdd, onLyrics = { lyricsPage = true }, onEditLyrics = onEditLyrics)
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
internal fun PlayerCoverPage(track: Track, controls: Boolean, compact: Boolean, lyricLine: String?, loading: Boolean,
                             favorite: Boolean, onReveal: () -> Unit, onFavorite: () -> Unit, onAdd: () -> Unit,
                             onLyrics: () -> Unit, onEditLyrics: () -> Unit) {
    val t = LocalUniverse.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("player_cover_page").clickable(onClick = onReveal)) {
        // Bound the square by both dimensions. Optional content can scroll inside this region
        // on a small screen, but can never displace or draw over transport.
        val coverSize = minOf(maxWidth * .86f, maxHeight * .65f)
        val scroll = rememberScrollState()
        // Immersive vertical swipes still belong to the parent (previous/next track).
        Column(Modifier.fillMaxSize().then(if (controls) Modifier.verticalScroll(scroll) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (controls) {
                if (coverSize > 0.dp) Artwork(track.artwork, track.title, Modifier.size(coverSize).testTag("player_artwork"))
                Text(lyricLine ?: if (loading) "Chargement des paroles…" else "Ouvrir les paroles",
                    Modifier.fillMaxWidth().clickable(onClick = onLyrics).padding(vertical = if (compact) 8.dp else 16.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = t.text)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onFavorite) { Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Favori", tint = if (favorite) t.primary else t.text) }
                    IconButton(onAdd) { Icon(Icons.Outlined.PlaylistAdd, "Ajouter à une playlist") }
                    IconButton(onLyrics) { Icon(Icons.Outlined.Lyrics, "Afficher les paroles") }
                    IconButton(onEditLyrics) { Icon(Icons.Outlined.EditNote, "Modifier les paroles") }
                }
            } else {
                Text(track.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp)); Text(track.artist, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp)); Text(lyricLine ?: "Touchez pour afficher les commandes", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
