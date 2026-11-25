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
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novalisten.app.data.Track
import com.novalisten.app.data.durationLabel
import com.novalisten.app.ui.theme.*
import com.novalisten.app.ui.gestures.LibrarySwipeAction
import com.novalisten.app.ui.gestures.librarySwipeAction
import kotlin.math.abs
import com.novalisten.app.ui.theme.NovaIcon

@Composable
fun NovaCard(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalUniverse.current
    Surface(modifier, shape = cardShape(t), color = t.surface.copy(alpha = .97f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) t.accent else t.primary.copy(alpha = .28f)),
        shadowElevation = if (t.light) 2.dp else 0.dp) {
        Column(content = content)
    }
}

@Composable
fun Artwork(uri: String?, title: String, modifier: Modifier = Modifier, round: Boolean = false, artist: Boolean = false, fallback: String? = null) {
    val t = LocalUniverse.current
    val shapes = universeShapes(t)
    val shape = if (round) CircleShape else shapes.card
    // Une image secondaire (pochette d'un album de l'artiste) vaut mieux que des initiales :
    // deux cartes « R » côte à côte ne se distinguent pas.
    var source by remember(uri, fallback) { mutableStateOf(uri?.takeIf { it.isNotBlank() } ?: fallback?.takeIf { it.isNotBlank() }) }
    var initialsOnly by remember(uri, fallback) { mutableStateOf(source == null) }
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(t.primary.copy(alpha = .30f), t.secondary.copy(alpha = .18f), t.surface))), contentAlignment = Alignment.Center) {
        // Ornement propre à la famille d'icônes : la pochette vide porte l'identité de l'univers.
        if (t.motion.decoratedArtwork) Canvas(Modifier.fillMaxSize()) { drawArtworkOrnament(t) }
        if (initialsOnly) {
            if (artist) Text(title.split(' ').take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase(),
                style = MaterialTheme.typography.headlineMedium, color = t.text)
            else NovaIcon(Icons.Outlined.MusicNote, null, Modifier.size(28.dp), tint = t.text.copy(alpha = .62f))
        }
        source?.let { model ->
            AsyncImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onState = { state ->
                    if (state is coil.compose.AsyncImagePainter.State.Error) {
                        val next = fallback?.takeIf { it != model }
                        if (next != null) source = next else initialsOnly = true
                    }
                })
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArtworkOrnament(t: com.novalisten.app.ui.theme.ThemeSpec) {
    val w = size.width
    val h = size.height
    when (t.glyphs) {
        com.novalisten.app.ui.theme.GlyphFamily.HUD -> {
            val step = w / 9
            var y = 0f
            while (y < h) { drawLine(t.accent.copy(alpha = .05f), Offset(0f, y), Offset(w, y), .8f); y += step }
            var x = 0f
            while (x < w) { drawLine(t.accent.copy(alpha = .04f), Offset(x, 0f), Offset(x, h), .8f); x += step }
        }
        com.novalisten.app.ui.theme.GlyphFamily.CHROME -> repeat(4) { i ->
            drawArc(t.secondary.copy(alpha = .10f), -20f + i * 18, 240f, false, topLeft = Offset(w * (.1f + i * .07f), h * (.08f + i * .05f)),
                size = Size(w * (.8f - i * .12f), h * (.8f - i * .12f)), style = Stroke(2.dp.toPx()))
        }
        com.novalisten.app.ui.theme.GlyphFamily.SHARD -> repeat(7) { i ->
            val p = Path().apply { moveTo(w * i / 7, h); lineTo(w * (.3f + i * .08f), 0f); lineTo(w * (.42f + i * .07f), 0f); close() }
            drawPath(p, t.primary.copy(alpha = .06f))
        }
        com.novalisten.app.ui.theme.GlyphFamily.CROWN -> repeat(5) { i ->
            val x = w * (i + .5f) / 5
            val p = Path().apply { moveTo(x - w * .05f, h * .22f); lineTo(x, h * .1f); lineTo(x + w * .05f, h * .22f); close() }
            drawPath(p, t.accent.copy(alpha = .12f))
        }
        com.novalisten.app.ui.theme.GlyphFamily.BUBBLE, com.novalisten.app.ui.theme.GlyphFamily.CLOUD -> repeat(6) { i ->
            drawCircle((if (i % 2 == 0) t.primary else t.secondary).copy(alpha = .10f),
                w * (.05f + (i % 3) * .05f), Offset(w * (.15f + i * .14f), h * (.25f + (i % 4) * .18f)), style = Stroke(1.6.dp.toPx()))
        }
        com.novalisten.app.ui.theme.GlyphFamily.DRAPE -> repeat(6) { i ->
            val p = Path().apply { moveTo(w * i / 6, 0f); cubicTo(w * (i + .5f) / 6, h * .4f, w * (i - .3f) / 6, h * .6f, w * (i + .3f) / 6, h) }
            drawPath(p, t.primary.copy(alpha = .07f), style = Stroke(w * .04f))
        }
        com.novalisten.app.ui.theme.GlyphFamily.FANG -> repeat(5) { i ->
            val p = Path().apply { moveTo(w * (.1f + i * .19f), 0f); lineTo(w * (.15f + i * .19f), h * .3f); lineTo(w * (.2f + i * .19f), 0f); close() }
            drawPath(p, t.primary.copy(alpha = .12f))
        }
        com.novalisten.app.ui.theme.GlyphFamily.RAY -> repeat(12) { i ->
            val a = i * Math.PI / 6
            drawLine(t.accent.copy(alpha = .07f), Offset(w / 2, h / 2),
                Offset(w / 2 + (Math.cos(a).toFloat() * w), h / 2 + (Math.sin(a).toFloat() * h)), 3.dp.toPx())
        }
        com.novalisten.app.ui.theme.GlyphFamily.RIVET -> repeat(8) { i ->
            drawCircle(t.primary.copy(alpha = .14f), 2.4.dp.toPx(), Offset(w * ((i % 4 + .5f) / 4), h * ((i / 4 + .5f) / 2)))
        }
        com.novalisten.app.ui.theme.GlyphFamily.PRIDE -> listOf(t.primary, t.secondary, t.glow, t.accent).forEachIndexed { i, c ->
            drawRect(c.copy(alpha = .09f), Offset(0f, h * i / 4), Size(w, h / 4))
        }
        com.novalisten.app.ui.theme.GlyphFamily.GEM -> repeat(4) { i ->
            val p = Path().apply { moveTo(w * .5f, h * (i * .12f)); lineTo(w * (.8f - i * .06f), h * .5f); lineTo(w * .5f, h * (1f - i * .1f)); lineTo(w * (.2f + i * .06f), h * .5f); close() }
            drawPath(p, t.accent.copy(alpha = .06f), style = Stroke(1.4.dp.toPx()))
        }
        com.novalisten.app.ui.theme.GlyphFamily.POLAROID -> drawRect(t.text.copy(alpha = .07f), Offset(w * .16f, h * .14f), Size(w * .68f, h * .62f))
        com.novalisten.app.ui.theme.GlyphFamily.DIAMOND -> repeat(9) { i ->
            val x = w * ((i % 3) + .5f) / 3
            val y = h * ((i / 3) + .5f) / 3
            val p = Path().apply { moveTo(x, y - w * .09f); lineTo(x + w * .09f, y); lineTo(x, y + w * .09f); lineTo(x - w * .09f, y); close() }
            drawPath(p, (if (i % 2 == 0) t.primary else t.secondary).copy(alpha = .07f))
        }
        com.novalisten.app.ui.theme.GlyphFamily.WING -> repeat(3) { i ->
            drawArc(t.secondary.copy(alpha = .07f), 200f, 140f, false, topLeft = Offset(w * (.05f + i * .1f), h * (.02f + i * .08f)),
                size = Size(w * (.9f - i * .2f), h * (.7f - i * .12f)), style = Stroke(2.dp.toPx()))
        }
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
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { NovaIcon(Icons.Outlined.Search, null) },
        trailingIcon = { IconButton(onClick = { onQuery("") }) { NovaIcon(Icons.Outlined.Close, "Effacer la recherche") } })
}

@Composable
fun PlaybackButtons(enabled: Boolean, onPlay: () -> Unit, onShuffle: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onPlay, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { NovaIcon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Tout lire") }
        if (onShuffle != null) OutlinedButton(onShuffle, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { NovaIcon(Icons.Outlined.Shuffle, null, Modifier.size(20.dp)); Spacer(Modifier.width(5.dp)); Text("Aléatoire") }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalUniverse.current.text.copy(alpha = .75f))
        }
        if (action != null) TextButton(onAction) { Text(action); NovaIcon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp)) }
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
            NovaIcon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = LocalUniverse.current.accent)
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
        detectHorizontalDragGestures(onDragStart = { displacement = 0f }, onDragEnd = {
            when (librarySwipeAction(displacement, threshold)) {
                LibrarySwipeAction.PLAY_NEXT -> next()
                LibrarySwipeAction.INFORMATION -> info()
                null -> Unit
            }
            displacement = 0f
        }, onDragCancel = { displacement = 0f }) { change, amount ->
            change.consume(); displacement = (displacement + amount).coerceIn(-threshold * 1.6f, threshold * 1.6f)
        }
    } else Modifier)) {
        if (abs(displacement) > 10) Row(Modifier.matchParentSize().background(LocalUniverse.current.primary.copy(alpha = .12f)).padding(20.dp), horizontalArrangement = if (displacement > 0) Arrangement.Absolute.Left else Arrangement.Absolute.Right, verticalAlignment = Alignment.CenterVertically) {
            Text(if (displacement < 0) "Lire ensuite" else "Informations", Modifier.testTag("library_swipe_hint"), style = MaterialTheme.typography.labelLarge)
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
                if (selected) NovaIcon(Icons.Outlined.CheckCircle, "Sélectionné", Modifier.align(Alignment.Center).size(32.dp), tint = LocalUniverse.current.accent)
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
                    NovaIcon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (favorite) "Retirer des favoris" else "Ajouter aux favoris", Modifier.size(21.dp), tint = if (favorite) LocalUniverse.current.primary else LocalUniverse.current.text.copy(alpha = .75f))
                }
            }
            IconButton(onMenu, Modifier.size(44.dp)) { NovaIcon(Icons.Outlined.MoreVert, "Actions de ${track.title}", Modifier.size(22.dp)) }
        }
    }
}

@Composable
fun EntityCard(title: String, subtitle: String, art: String?, modifier: Modifier = Modifier, artist: Boolean = false, selected: Boolean = false, fallbackArt: String? = null, onClick: () -> Unit, onLongClick: () -> Unit = {}, onNext: () -> Unit = {}, onInfo: () -> Unit = {}, onPlay: (() -> Unit)? = null, badge: String? = null) {
    SwipeActions(title, true, onNext, onInfo) {
        NovaCard(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), selected) {
            Box(Modifier.padding(if (artist) 12.dp else 8.dp)) {
                Artwork(art, title, Modifier.fillMaxWidth().aspectRatio(1f), round = artist, artist = artist, fallback = fallbackArt)
                if (badge != null) Surface(Modifier.align(Alignment.TopEnd).padding(6.dp), color = LocalUniverse.current.background.copy(alpha = .9f), shape = MaterialTheme.shapes.small) { Text(badge, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall) }
                if (onPlay != null) FilledIconButton(onPlay, Modifier.align(Alignment.BottomEnd).padding(6.dp).size(42.dp)) { NovaIcon(Icons.Outlined.PlayArrow, "Lire $title") }
                if (selected) NovaIcon(Icons.Outlined.CheckCircle, "Sélectionné", Modifier.align(Alignment.TopStart).padding(6.dp), tint = LocalUniverse.current.accent)
            }
            // Deux lignes avant troncature : « Recovery (Deluxe Edition) » ne doit pas devenir
            // « Recovery (Deluxe Edi… », c'est justement l'édition qu'on cherche à distinguer.
            // La hauteur est réservée pour que deux cartes voisines s'alignent, même quand
            // l'une tient sur une ligne et l'autre sur deux.
            Text(title, Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 42.dp),
                style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 3.dp, bottom = 14.dp).heightIn(min = 44.dp),
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
