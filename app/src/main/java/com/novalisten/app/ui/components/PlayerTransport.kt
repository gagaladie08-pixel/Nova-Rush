package com.novalisten.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.novalisten.app.PlaybackUi
import com.novalisten.app.data.durationLabel
import com.novalisten.app.ui.theme.LocalUniverse
import com.novalisten.app.ui.theme.NovaIcon

/** Playback controls keep their touch targets; optional content yields space, never these buttons. */
@Composable
internal fun PlayerTransport(playback: PlaybackUi, compactHeight: Boolean = false,
                             onSeek: (Long) -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit,
                             onNext: () -> Unit, onRepeat: () -> Unit, onQueue: () -> Unit) {
    var seek by remember(playback.currentId) { mutableStateOf<Float?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("player_transport")) {
        val availableWidth = maxWidth
        val compact = compactHeight || maxWidth < 320.dp
        val playSize = if (compact) 56.dp else 68.dp
        val skipSize = if (compact) 48.dp else 52.dp
        val separateExtras = maxWidth < 96.dp + skipSize * 2 + playSize
        val compactTimeline = compactHeight || LocalDensity.current.fontScale > 1.3f
        val value = seek ?: playback.position.toFloat().coerceIn(0f, playback.duration.toFloat().coerceAtLeast(1f))
        val slider: @Composable (Modifier) -> Unit = { modifier ->
            // Barre de progression de l'univers : trait, halo, remplissage et dévoilement.
            UniverseSlider(value = value, range = 0f..playback.duration.toFloat().coerceAtLeast(1f),
                enabled = playback.duration > 0, onValueChange = { seek = it },
                onFinished = { it2 -> onSeek(it2.toLong()); seek = null },
                modifier = modifier, tag = "player_seek")
        }
        Column(Modifier.fillMaxWidth()) {
            if (compactTimeline) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(durationLabel(value.toLong()), Modifier.widthIn(max = availableWidth / 4), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                slider(Modifier.weight(1f).padding(horizontal = 4.dp))
                Text(durationLabel(playback.duration), Modifier.widthIn(max = availableWidth / 4), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                slider(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(durationLabel(value.toLong()), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(durationLabel(playback.duration), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            if (separateExtras) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RepeatControl(playback, onRepeat)
                QueueControl(onQueue)
            }
            Row(Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 8.dp).testTag("player_controls"),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                if (!separateExtras) RepeatControl(playback, onRepeat)
                IconButton(onPrevious, Modifier.size(skipSize).testTag("player_previous")) { NovaIcon(Icons.Outlined.SkipPrevious, "Précédent", Modifier.size(32.dp)) }
                FilledIconButton(onToggle, Modifier.size(playSize).testTag("player_toggle"),
                    shape = com.novalisten.app.ui.theme.buttonShape(LocalUniverse.current), colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = LocalUniverse.current.primary, contentColor = com.novalisten.app.ui.theme.onColor(LocalUniverse.current.primary))) {
                    NovaIcon(if (playback.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playback.playing) "Pause" else "Lecture", Modifier.size(36.dp))
                }
                IconButton(onNext, Modifier.size(skipSize).testTag("player_next")) { NovaIcon(Icons.Outlined.SkipNext, "Suivant", Modifier.size(32.dp)) }
                if (!separateExtras) QueueControl(onQueue)
            }
        }
    }
}

@Composable
private fun RepeatControl(playback: PlaybackUi, onRepeat: () -> Unit) {
    IconButton(onRepeat, Modifier.size(48.dp).testTag("player_repeat")) {
        NovaIcon(if (playback.repeatMode == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
            "Répétition : ${when (playback.repeatMode) { Player.REPEAT_MODE_ONE -> "un titre"; Player.REPEAT_MODE_ALL -> "toute la file"; else -> "désactivée" }}",
            tint = if (playback.repeatMode != Player.REPEAT_MODE_OFF) LocalUniverse.current.accent else LocalUniverse.current.text)
    }
}

@Composable
private fun QueueControl(onQueue: () -> Unit) {
    IconButton(onQueue, Modifier.size(48.dp).testTag("player_queue")) { NovaIcon(Icons.Outlined.QueueMusic, "File d’attente") }
}
