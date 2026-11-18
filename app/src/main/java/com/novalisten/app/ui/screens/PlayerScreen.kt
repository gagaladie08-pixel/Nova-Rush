@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.novalisten.app.ui.screens

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.novalisten.app.MainViewModel
import com.novalisten.app.PlaybackUi
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.theme.*
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
fun FullPlayer(track: Track, playback: PlaybackUi, data: LocalData, tracks: List<Track>, vm: MainViewModel, onDismiss: () -> Unit, onMenu: () -> Unit, onAdd: () -> Unit, onEditLyrics: () -> Unit, onSearchLyrics: () -> Unit, onTranslateLyrics: () -> Unit, blocked: Boolean) {
    var lyricsPage by rememberSaveable { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var touched by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    var french by rememberSaveable { mutableStateOf(false) }
    var provenance by remember { mutableStateOf<String?>(null) }
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
        val saved = data.lyrics[track.id] ?: SavedLyrics()
        val text = if (french && saved.french.isNotBlank()) saved.french else saved.original
        val timed = remember(text) { LrcParser.parse(text) }
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
                        if (dx > 0 && !currentLyrics) lyricsPage = true
                        else if (dx < 0 && currentLyrics) { lyricsPage = false; controls = true }
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
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { NovaIcon(Icons.Outlined.KeyboardArrowDown, "Réduire le player") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (lyricsPage) "PAROLES" else "LECTURE LOCALE", style = MaterialTheme.typography.labelMedium, color = t.accent)
                        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    IconButton(onMenu) { NovaIcon(Icons.Outlined.MoreVert, "Actions du morceau") }
                }
                if (lyricsPage) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ lyricsPage = false; controls = true }) { Icon(Icons.Outlined.ArrowBack, null); Text("Pochette") }
                        TextButton({ french = !french }, enabled = saved.french.isNotBlank()) { Text(if (french && saved.french.isNotBlank()) "FR → VO" else "VO → FR") }
                        IconButton(onEditLyrics) { Icon(Icons.Outlined.EditNote, "Modifier les paroles") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onSearchLyrics) { Icon(Icons.Outlined.Search, null); Text("Rechercher") }
                        TextButton(onTranslateLyrics, enabled = saved.original.isNotBlank()) { Icon(Icons.Outlined.Translate, null); Text("Traduire") }
                        val credit = if (french && saved.french.isNotBlank()) saved.frenchSource else saved.originalSource
                        if (credit.isNotBlank()) TextButton({ provenance = credit }) { Text("Source") }
                    }
                    if (french && saved.frenchSource.startsWith("Google Translate")) GoogleTranslationAttribution()
                    LyricsBody(text, timed, active, Modifier.weight(1f), vm::seek, onEditLyrics)
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth().clickable { controls = true; lastTouch = System.currentTimeMillis() }, contentAlignment = Alignment.Center) {
                        if (controls) Artwork(track.artwork, track.title, Modifier.fillMaxWidth(.86f).aspectRatio(1f))
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(track.title, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(12.dp)); Text(track.artist, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(32.dp)); Text(if (active >= 0) timed[active].text else "Touchez pour afficher les commandes", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (controls) {
                        Text(if (active >= 0) timed[active].text else if (text.isNotBlank()) "Paroles sauvegardées · ouvrir →" else "Ajoutez vos paroles, gardez-les hors ligne", Modifier.fillMaxWidth().clickable { lyricsPage = true }.padding(vertical = 16.dp), maxLines = 2, style = MaterialTheme.typography.bodyLarge, color = t.text)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton({ vm.favorite(track) }) { Icon(if (track.id in data.favorites) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Favori", tint = if (track.id in data.favorites) t.primary else t.text) }
                            IconButton(onAdd) { Icon(Icons.Outlined.PlaylistAdd, "Ajouter à une playlist") }
                            IconButton({ lyricsPage = true }) { Icon(Icons.Outlined.Lyrics, "Afficher les paroles") }
                            IconButton(onEditLyrics) { Icon(Icons.Outlined.EditNote, "Modifier les paroles") }
                        }
                    }
                }
                if (controls || lyricsPage) {
                    PlayerTransport(playback, vm, { queue = true })
                    TextButton({ lyricsPage = !lyricsPage; controls = true }, Modifier.align(Alignment.CenterHorizontally)) { Text(if (lyricsPage) "● Paroles   ○ Pochette" else "● Pochette   ○ Paroles", style = MaterialTheme.typography.labelSmall) }
                } else Spacer(Modifier.height(24.dp))
            }
            if (!controls && !lyricsPage) TextButton({ controls = true }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { Text("Afficher les commandes") }
        }
        provenance?.let { credit -> AlertDialog(onDismissRequest = { provenance = null }, title = { Text("Source des paroles") }, text = { LazyColumn { item { Text(credit) } } }, confirmButton = { TextButton({ provenance = null }) { Text("Fermer") } }) }
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
private fun PlayerTransport(playback: PlaybackUi, vm: MainViewModel, onQueue: () -> Unit) {
    var seek by remember(playback.currentId) { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Slider(value = seek ?: playback.position.toFloat().coerceIn(0f, playback.duration.toFloat().coerceAtLeast(1f)), onValueChange = { seek = it }, onValueChangeFinished = { seek?.let { vm.seek(it.toLong()) }; seek = null }, valueRange = 0f..playback.duration.toFloat().coerceAtLeast(1f), enabled = playback.duration > 0)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(durationLabel((seek ?: playback.position.toFloat()).toLong()), style = MaterialTheme.typography.labelSmall)
            Text(durationLabel(playback.duration), style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(vm::cycleRepeat) { Icon(if (playback.repeatMode == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, "Répétition : ${when(playback.repeatMode) { Player.REPEAT_MODE_ONE -> "un titre"; Player.REPEAT_MODE_ALL -> "toute la file"; else -> "désactivée" }}", tint = if (playback.repeatMode != Player.REPEAT_MODE_OFF) LocalUniverse.current.accent else LocalUniverse.current.text) }
            IconButton(vm::previous, Modifier.size(52.dp)) { Icon(Icons.Outlined.SkipPrevious, "Précédent", Modifier.size(32.dp)) }
            FilledIconButton(vm::toggle, Modifier.size(68.dp)) { Icon(if (playback.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playback.playing) "Pause" else "Lecture", Modifier.size(36.dp)) }
            IconButton(vm::forward, Modifier.size(52.dp)) { Icon(Icons.Outlined.SkipNext, "Suivant", Modifier.size(32.dp)) }
            IconButton(onQueue) { Icon(Icons.Outlined.QueueMusic, "File d’attente") }
        }
    }
}

@Composable
private fun LyricsBody(text: String, timed: List<LyricLine>, active: Int, modifier: Modifier, onSeek: (Long) -> Unit, onEdit: () -> Unit) {
    val list = rememberLazyListState()
    var follow by remember(text) { mutableStateOf(true) }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) follow = false }
    LaunchedEffect(active, follow) { if (follow && active >= 0) list.animateScrollToItem(active.coerceAtLeast(0)) }
    Column(modifier) {
        if (text.isBlank()) EmptyState("Vos mots, votre musique", "Collez un texte ou des paroles LRC horodatées. Les versions originale et française restent séparées. Recherche et traduction facultatives via les boutons ci-dessus ; aucun appel en ouvrant cette page.", Icons.Outlined.Lyrics, Modifier.weight(1f), action = { Button(onEdit) { Text("Ajouter les paroles") } })
        else {
            if (timed.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Synchro · Ligne (LRC)", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
                TextButton({ follow = true }) { Text("Recentrer") }
            } else Text("Texte non synchronisé", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 24.dp)) {
                if (timed.isEmpty()) item { Text(text, style = MaterialTheme.typography.headlineSmall) }
                else itemsIndexed(timed) { index, line ->
                    Text(line.text.ifBlank { "♪" }, Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }.padding(vertical = 12.dp), style = MaterialTheme.typography.headlineSmall, color = if (index == active) LocalUniverse.current.accent else LocalUniverse.current.text.copy(alpha = .55f))
                }
                item { Spacer(Modifier.height(130.dp)) }
            }
        }
    }
}
