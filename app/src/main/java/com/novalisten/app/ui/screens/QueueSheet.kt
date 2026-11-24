package com.novalisten.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.novalisten.app.PlaybackUi
import com.novalisten.app.data.Track
import com.novalisten.app.data.durationLabel
import com.novalisten.app.data.itemCount
import com.novalisten.app.ui.components.Artwork
import com.novalisten.app.ui.components.UniverseSpinner
import com.novalisten.app.ui.theme.LocalUniverse
import com.novalisten.app.ui.theme.NovaIcon

/**
 * File d'attente du player : en-tête informatif, pochettes, marqueur du morceau en cours et
 * réordonnancement par appui long. L'ordre affiché ne devient réel qu'au relâchement, ce qui
 * évite de déplacer la file pendant que le lecteur joue.
 */
@Composable
internal fun QueueSheet(playback: PlaybackUi, byId: Map<String, Track>, onDismiss: () -> Unit, onPlay: (Int) -> Unit, onMove: (Int, Int) -> Unit) {
    val universe = LocalUniverse.current
    // Ordre local : les identifiants restent l'identité, l'index affiché suit les déplacements.
    var order by remember(playback.queue) { mutableStateOf(playback.queue) }
    var current by remember(playback.queue, playback.index) { mutableIntStateOf(playback.index) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var pendingFrom by remember { mutableIntStateOf(-1) }
    var pendingTo by remember { mutableIntStateOf(-1) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rowHeight = with(density) { QueueRowHeight.toPx() }
    val known = order.mapNotNull { byId[it] }
    val currentTrack = order.getOrNull(current)?.let(byId::get)
    val totalMs = known.sumOf { it.durationMs }

    fun settle() {
        val from = pendingFrom
        val to = pendingTo
        dragging = null; pendingFrom = -1; pendingTo = -1; dragOffset = 0f
        if (from >= 0 && to >= 0 && from != to) {
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            current = when {
                current == from -> to
                from < current && to >= current -> current - 1
                from > current && to <= current -> current + 1
                else -> current
            }
            onMove(from, to)
        }
    }

    ModalBottomSheet(onDismissRequest = { if (dragging == null) onDismiss() }, containerColor = universe.background) {
        Column(Modifier.fillMaxHeight(.86f).navigationBarsPadding()) {
            // En-tête : information utile plutôt qu'un compteur géant.
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NovaIcon(Icons.Outlined.QueueMusic, null, Modifier.size(20.dp), tint = universe.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("FILE D’ATTENTE", style = MaterialTheme.typography.labelMedium, color = universe.accent, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Text(itemCount(order.size, "morceau") + " · " + totalDurationLabel(totalMs),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (currentTrack != null) {
                    Spacer(Modifier.height(3.dp))
                    Text("En lecture : ${currentTrack.title} — ${currentTrack.artist}",
                        style = MaterialTheme.typography.bodySmall, color = universe.readableSecondaryText(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Appui long puis glisser pour réordonner", style = MaterialTheme.typography.labelSmall,
                    color = universe.readableSecondaryText(), modifier = Modifier.weight(1f))
                if (order.isNotEmpty()) TextButton(onClick = { scope.launch { state.animateScrollToItem(current.coerceAtLeast(0)) } }) { Text("Morceau en cours") }
            }
            HorizontalDivider(color = universe.primary.copy(alpha = .22f))
            if (order.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { UniverseSpinner(Modifier.size(24.dp)); Spacer(Modifier.height(10.dp)); Text("La file est vide.") }
            } else LazyColumn(Modifier.weight(1f).testTag("queue_list"), state = state, contentPadding = PaddingValues(vertical = 6.dp)) {
                itemsIndexed(order, key = { index, id -> "$index:$id" }) { index, id ->
                    val track = byId[id]
                    val active = index == current
                    val lifted = dragging == index
                    val shift = when {
                        dragging == null -> 0f
                        index == dragging -> dragOffset
                        else -> when {
                            pendingTo > pendingFrom && index in (pendingFrom + 1)..pendingTo -> -rowHeight
                            pendingTo < pendingFrom && index in pendingTo until pendingFrom -> rowHeight
                            else -> 0f
                        }
                    }
                    QueueRow(
                        track = track, id = id, index = index, active = active,
                        modifier = Modifier
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = shift; shadowElevation = if (lifted) with(density) { 12.dp.toPx() } else 0f }
                            .pointerInput(order, dragging) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = index; pendingFrom = index; pendingTo = index; dragOffset = 0f },
                                    onDragEnd = { settle() },
                                    onDragCancel = { settle() },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    when {
                                        dragOffset > rowHeight * .6f -> { pendingTo = (pendingTo + 1).coerceAtMost(order.lastIndex); dragOffset -= rowHeight }
                                        dragOffset < -rowHeight * .6f -> { pendingTo = (pendingTo - 1).coerceAtLeast(0); dragOffset += rowHeight }
                                    }
                                }
                            }
                            .clickable(enabled = dragging == null) { onPlay(index) },
                    )
                }
            }
        }
    }
}

private val QueueRowHeight = 60.dp

@Composable
private fun QueueRow(track: Track?, id: String, index: Int, active: Boolean, modifier: Modifier = Modifier) {
    val universe = LocalUniverse.current
    val background: Brush = if (active)
        Brush.horizontalGradient(listOf(universe.primary.copy(alpha = .18f), universe.primary.copy(alpha = .02f)))
    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    Row(modifier.fillMaxWidth().height(QueueRowHeight).background(background).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Repère du morceau en cours : barre d'accent, jamais un caractère texte.
        Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(2.dp))
            .background(if (active) universe.accent else Color.Transparent))
        Spacer(Modifier.width(9.dp))
        if (track != null) Artwork(track.artwork, track.title, Modifier.size(38.dp))
        else Box(Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(universe.surface), contentAlignment = Alignment.Center) {
            NovaIcon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp), tint = universe.readableSecondaryText())
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track?.title ?: id, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = universe.text)
            Text(track?.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall, color = universe.readableSecondaryText())
        }
        Text(if (track != null) durationLabel(track.durationMs) else "", style = MaterialTheme.typography.labelSmall,
            color = universe.readableSecondaryText(), modifier = Modifier.padding(start = 8.dp))
        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = universe.readableSecondaryText(),
            modifier = Modifier.padding(start = 10.dp).widthIn(min = 22.dp), maxLines = 1)
        NovaIcon(Icons.Outlined.DragHandle, "Maintenir puis glisser", Modifier.size(20.dp), tint = universe.readableSecondaryText().copy(alpha = .7f))
    }
}

/** Total lisible : « 62 h 14 » plutôt que des millisecondes. */
internal fun totalDurationLabel(totalMs: Long): String {
    val minutes = totalMs / 60000
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 -> "${hours} h " + rest.toString().padStart(2, '0')
        minutes > 0 -> "$minutes min"
        else -> "moins d’une minute"
    }
}
