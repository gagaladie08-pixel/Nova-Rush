@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.novalisten.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novalisten.app.data.Track
import com.novalisten.app.data.durationLabel
import com.novalisten.app.ui.theme.*
import kotlin.math.abs

@Composable
fun NovaIcon(image: ImageVector, description: String? = null, modifier: Modifier = Modifier, tint: Color = LocalUniverse.current.text) {
    val spec = LocalUniverse.current
    Box(modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val c = spec.accent.copy(alpha = .45f)
            when (spec.icons) {
                IconStyle.HUD, IconStyle.FRAME -> {
                    val d = size.width * .2f
                    listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).forEach { p ->
                        val sx = if (p.x == 0f) 1 else -1
                        val sy = if (p.y == 0f) 1 else -1
                        drawLine(c, p, p + Offset(sx * d, 0f), 1.dp.toPx())
                        drawLine(c, p, p + Offset(0f, sy * d), 1.dp.toPx())
                    }
                }
                IconStyle.ROUND, IconStyle.BUBBLE, IconStyle.HALO -> drawCircle(c.copy(alpha = .22f), style = Stroke(1.dp.toPx()))
                IconStyle.GEOMETRIC, IconStyle.PRISM, IconStyle.CRYSTAL -> {
                    val p = Path().apply { moveTo(size.width / 2, 0f); lineTo(size.width, size.height / 2); lineTo(size.width / 2, size.height); lineTo(0f, size.height / 2); close() }
                    drawPath(p, c.copy(alpha = .3f), style = Stroke(1.dp.toPx()))
                }
                IconStyle.ROYAL, IconStyle.SOLAR, IconStyle.STAGE -> drawCircle(c, 1.5.dp.toPx(), Offset(size.width / 2, 0f))
                IconStyle.METAL, IconStyle.SHARP -> drawLine(c, Offset(0f, size.height), Offset(size.width * .3f, size.height), 2.dp.toPx())
            }
        }
        Icon(image, description, tint = tint, modifier = Modifier.fillMaxSize().padding(3.dp))
    }
}

@Composable
fun NovaCard(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalUniverse.current
    Surface(modifier, shape = universeShape(t), color = t.surface.copy(alpha = .96f), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) t.accent else t.primary.copy(alpha = .23f))) {
        Column(content = content)
    }
}

@Composable
fun Artwork(uri: String?, title: String, modifier: Modifier = Modifier, round: Boolean = false, artist: Boolean = false) {
    val t = LocalUniverse.current
    Box(modifier.clip(if (round) CircleShape else universeShape(t)).background(Brush.linearGradient(listOf(t.primary.copy(alpha = .22f), t.secondary.copy(alpha = .13f), t.surface))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            drawCircle(t.accent.copy(alpha = .12f), style = Stroke(1.dp.toPx()))
            drawCircle(t.primary.copy(alpha = .17f), radius = size.minDimension * .35f, style = Stroke(1.dp.toPx()))
            drawCircle(t.accent.copy(alpha = .25f), radius = size.minDimension * .12f)
        }
        if (artist) Text(title.split(' ').take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase(), style = MaterialTheme.typography.headlineMedium, color = t.text)
        else Icon(Icons.Outlined.MusicNote, null, Modifier.size(28.dp), tint = t.text.copy(alpha = .55f))
        if (!uri.isNullOrEmpty()) AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun PageTitle(title: String, subtitle: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, modifier = Modifier.testTag("page_title"), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalUniverse.current.text.copy(alpha = .8f))
        }
        actions()
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String = "Titre, artiste, album, fichier") {
    OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).testTag("search_field"), singleLine = true,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Effacer la recherche") } })
}

@Composable
fun PlaybackButtons(enabled: Boolean, onPlay: () -> Unit, onShuffle: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onPlay, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Tout lire") }
        if (onShuffle != null) OutlinedButton(onShuffle, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Outlined.Shuffle, null, Modifier.size(20.dp)); Spacer(Modifier.width(5.dp)); Text("Aléatoire") }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalUniverse.current.text.copy(alpha = .75f))
        }
        if (action != null) TextButton(onAction) { Text(action); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp)) }
    }
}

@Composable
fun EmptyState(title: String, description: String, icon: ImageVector = Icons.Outlined.LibraryMusic, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(92.dp).clip(CircleShape).background(LocalUniverse.current.primary.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
            NovaIcon(icon, modifier = Modifier.size(42.dp), tint = LocalUniverse.current.accent)
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    NovaCard(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = LocalUniverse.current.accent)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SwipeActions(key: String, enabled: Boolean, onNext: () -> Unit, onInfo: () -> Unit, content: @Composable () -> Unit) {
    var displacement by remember(key) { mutableFloatStateOf(0f) }
    val next by rememberUpdatedState(onNext)
    val info by rememberUpdatedState(onInfo)
    val threshold = with(LocalDensity.current) { 78.dp.toPx() }
    Box(Modifier.fillMaxWidth().then(if (enabled) Modifier.pointerInput(key, threshold) {
        detectHorizontalDragGestures(onDragEnd = {
            if (displacement > threshold) next() else if (displacement < -threshold) info()
            displacement = 0f
        }, onDragCancel = { displacement = 0f }) { change, amount ->
            change.consume(); displacement = (displacement + amount).coerceIn(-threshold * 1.6f, threshold * 1.6f)
        }
    } else Modifier)) {
        if (abs(displacement) > 10) Row(Modifier.matchParentSize().background(LocalUniverse.current.primary.copy(alpha = .12f)).padding(20.dp), horizontalArrangement = if (displacement > 0) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(if (displacement > 0) "Lire ensuite" else "Informations", style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.graphicsLayer { translationX = displacement }) { content() }
    }
}

@Composable
fun TrackRow(track: Track, favorite: Boolean, current: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit, onMenu: () -> Unit, onNext: () -> Unit, onInfo: () -> Unit, selected: Boolean = false, selecting: Boolean = false, onSelect: () -> Unit = {}) {
    SwipeActions(track.id, enabled = !selecting && track.playable, onNext, onInfo) {
        Row(Modifier.fillMaxWidth().testTag("track_${track.id.substringAfterLast('/')}").background(if (current || selected) LocalUniverse.current.primary.copy(alpha = .11f) else Color.Transparent)
            .combinedClickable(onClick = { if (selecting) onSelect() else if (track.playable) onPlay() else onInfo() }, onLongClick = onSelect)
            .padding(start = 20.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Artwork(track.artwork, track.title, Modifier.size(54.dp))
                if (selected) Icon(Icons.Outlined.CheckCircle, "Sélectionné", Modifier.align(Alignment.Center).size(32.dp), tint = LocalUniverse.current.accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (current) LocalUniverse.current.accent else LocalUniverse.current.text)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (track.pending) "Durée inconnue · en attente" else track.album + if (track.year > 0) " · ${track.year}" else "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalUniverse.current.text.copy(alpha = .75f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (track.pending) "—" else durationLabel(track.durationMs), style = MaterialTheme.typography.labelSmall)
                IconButton(onFavorite, enabled = track.playable, modifier = Modifier.size(48.dp)) {
                    Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (favorite) "Retirer des favoris" else "Ajouter aux favoris", Modifier.size(21.dp), tint = if (favorite) LocalUniverse.current.primary else LocalUniverse.current.text.copy(alpha = .75f))
                }
            }
            IconButton(onMenu, Modifier.size(44.dp)) { Icon(Icons.Outlined.MoreVert, "Actions de ${track.title}", Modifier.size(22.dp)) }
        }
    }
}

@Composable
fun EntityCard(title: String, subtitle: String, art: String?, modifier: Modifier = Modifier, artist: Boolean = false, selected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {}, onNext: () -> Unit = {}, onInfo: () -> Unit = {}, onPlay: (() -> Unit)? = null, badge: String? = null) {
    SwipeActions(title, true, onNext, onInfo) {
        NovaCard(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), selected) {
            Box(Modifier.padding(if (artist) 12.dp else 8.dp)) {
                Artwork(art, title, Modifier.fillMaxWidth().aspectRatio(1f), round = artist, artist = artist)
                if (badge != null) Surface(Modifier.align(Alignment.TopEnd).padding(6.dp), color = LocalUniverse.current.background.copy(alpha = .9f), shape = MaterialTheme.shapes.small) { Text(badge, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall) }
                if (onPlay != null) FilledIconButton(onPlay, Modifier.align(Alignment.BottomEnd).padding(6.dp).size(42.dp)) { Icon(Icons.Outlined.PlayArrow, "Lire $title") }
                if (selected) Icon(Icons.Outlined.CheckCircle, "Sélectionné", Modifier.align(Alignment.TopStart).padding(6.dp), tint = LocalUniverse.current.accent)
            }
            Text(title, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, Modifier.padding(start = 12.dp, end = 12.dp, top = 3.dp, bottom = 14.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
