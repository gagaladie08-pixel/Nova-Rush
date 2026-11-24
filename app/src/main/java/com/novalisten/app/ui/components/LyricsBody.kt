package com.novalisten.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novalisten.app.data.LyricLine
import com.novalisten.app.enrichment.AutoLyricState
import com.novalisten.app.enrichment.AutoLyricStatus
import com.novalisten.app.ui.theme.LocalUniverse
import kotlin.math.abs
import com.novalisten.app.ui.components.NovaIcon

/** Padding lets the first and last rows reach the same center as all the other rows. */
private suspend fun LazyListState.centerLyric(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    // Wait for wrapped text / the new active weight to be measured, then correct any rounding.
    repeat(2) {
        withFrameNanos { }
        val info = layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val delta = row.offset + row.size / 2f - (info.viewportStartOffset + info.viewportEndOffset) / 2f
        if (abs(delta) > 1f) animateScrollBy(delta)
    }
}

@Composable
internal fun LyricsBody(trackKey: String, text: String, timed: List<LyricLine>, active: Int, positionMs: Long,
                        playing: Boolean, automatic: AutoLyricState, modifier: Modifier = Modifier, onSeek: (Long) -> Unit) {
    val list = rememberLazyListState()
    var follow by remember(trackKey, text) { mutableStateOf(true) }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) follow = false }
    val universe = LocalUniverse.current
    val typography = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center)
    BoxWithConstraints(modifier.fillMaxWidth().testTag("lyrics_viewport")) {
        if (text.isBlank()) {
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val loading = automatic.trackId == trackKey && automatic.status == AutoLyricStatus.LOADING
                if (loading) { UniverseSpinner(Modifier.size(28.dp)); Spacer(Modifier.height(18.dp)) }
                Text(if (loading) "Chargement des paroles…" else "Paroles indisponibles", style = MaterialTheme.typography.titleLarge)
            }
        } else {
            val height = maxHeight
            LaunchedEffect(trackKey, timed, active, follow, height, typography) {
                if (follow && timed.isNotEmpty()) list.centerLyric(active.coerceIn(0, timed.lastIndex))
            }
            LazyColumn(Modifier.fillMaxSize().testTag("lyrics_list"), state = list,
                contentPadding = PaddingValues(vertical = if (timed.isNotEmpty()) height / 2 else 24.dp)) {
                if (timed.isEmpty()) item { Text(text, style = typography, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) }
                else itemsIndexed(timed, key = { index, _ -> index }) { index, line ->
                    val current = index == active
                    Box(Modifier.fillMaxWidth().testTag("lyric_line_$index").semantics { selected = current }
                        .clickable { follow = true; onSeek(line.timeMs) }.padding(vertical = 12.dp)) {
                        KaraokeLine(line, current, positionMs, playing,
                            color = if (current && line.words.isEmpty()) universe.accent else universe.text.copy(alpha = if (current) .95f else .45f),
                            accent = universe.accent,
                            style = typography.copy(fontWeight = if (current) FontWeight.Bold else typography.fontWeight),
                            modifier = Modifier.fillMaxWidth().testTag("lyric_text_$index"))
                    }
                }
            }
            if (!follow && timed.isNotEmpty()) FilledTonalIconButton({ follow = true },
                Modifier.align(Alignment.BottomEnd).padding(8.dp).testTag("lyrics_recenter")) { NovaIcon(Icons.Outlined.MyLocation, "Recentrer la ligne en cours") }
        }
    }
}
