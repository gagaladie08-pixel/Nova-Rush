package com.novalisten.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.theme.LocalUniverse
import java.time.LocalDate
import com.novalisten.app.ui.theme.NovaIcon

@Composable
fun HomeScreen(tracks: List<Track>, data: LocalData, vm: MainViewModel, onLibrary: () -> Unit, onFavorites: () -> Unit, onHistory: () -> Unit, onPlaylist: (String) -> Unit) {
    val theme = LocalUniverse.current
    val added = remember(tracks) { tracks.sortedByDescending { it.dateAdded }.take(40) }
    val favorites = tracks.filter { it.id in data.favorites }
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val recent = remember(tracks, data.events) { data.events.filter { inLocalDayWindow(it, 3) }.mapNotNull { byId[it.trackId] }.distinctBy { it.id }.take(40) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(20.dp)) {
                Text("VOTRE UNIVERS, VOTRE MUSIQUE", style = MaterialTheme.typography.labelMedium, color = theme.accent)
                Spacer(Modifier.height(9.dp))
                Text("Loin du bruit.\nPrès de vous.", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(20.dp))
                NovaCard {
                    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(theme.primary.copy(alpha = .14f), theme.secondary.copy(alpha = .08f))))) {
                        Column(Modifier.padding(20.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                NovaIcon(Icons.Outlined.GraphicEq, modifier = Modifier.size(35.dp), tint = theme.accent)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("TOUT EST DÉJÀ LÀ", style = MaterialTheme.typography.labelMedium, color = theme.accent)
                                    Text("${tracks.size} morceaux · ${totalDurationLabel(tracks.sumOf { it.durationMs })}", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Votre bibliothèque. Sans compte. Sans réseau.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { vm.play(tracks, shuffled = true) }, enabled = tracks.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                NovaIcon(Icons.Outlined.Shuffle, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Laisser Nova choisir")
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Mixes", "À partir des fichiers de votre téléphone") }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NovaCard(Modifier.weight(1f).clickable(onClick = onFavorites)) {
                    Column(Modifier.padding(16.dp)) {
                        NovaIcon(Icons.Outlined.FavoriteBorder, tint = theme.primary)
                        Spacer(Modifier.height(14.dp)); Text("Vos favoris", style = MaterialTheme.typography.titleMedium)
                        Text("${favorites.size} morceaux", style = MaterialTheme.typography.bodySmall)
                        TextButton(onFavorites) { Text("Ouvrir →") }
                    }
                }
                NovaCard(Modifier.weight(1f).clickable { vm.play(tracks, shuffled = true) }) {
                    Column(Modifier.padding(16.dp)) {
                        NovaIcon(Icons.Outlined.AutoAwesome, tint = theme.accent)
                        Spacer(Modifier.height(14.dp)); Text("Au hasard", style = MaterialTheme.typography.titleMedium)
                        Text("100 % local", style = MaterialTheme.typography.bodySmall)
                        TextButton({ vm.play(tracks, shuffled = true) }, enabled = tracks.isNotEmpty()) { Text("Écouter →") }
                    }
                }
            }
        }
        item { SectionHeader("Écoutes récentes", "Les 3 derniers jours · écoutes validées", "Historique", onHistory) }
        if (recent.isEmpty()) item { Notice("Vos prochaines écoutes apparaîtront ici.") }
        else item { TrackCarousel(recent, vm) }
        item { SectionHeader("Ajoutés récemment", "Votre musique, juste arrivée", "Tout voir", onLibrary) }
        if (added.isEmpty()) item { EmptyState("Aucun morceau local", "Ajoutez votre musique sur le téléphone.") }
        else item { TrackCarousel(added, vm) }
        item { SectionHeader("Éléments épinglés", "Vos playlists à portée de main") }
        val pinned = data.playlists.filter { it.pinnedAt != null }.sortedByDescending { it.pinnedAt }
        if (pinned.isEmpty()) item { Notice("Épinglez une playlist depuis son menu pour la retrouver sur l’Accueil.") }
        else items(pinned, key = { it.id }) { list ->
            ListItem(headlineContent = { Text(list.name) }, supportingContent = { Text("${list.trackIds.count { it in byId }} morceaux disponibles") },
                leadingContent = { NovaIcon(Icons.Outlined.PushPin) }, trailingContent = { NovaIcon(Icons.Outlined.ChevronRight, null) },
                modifier = Modifier.padding(horizontal = 12.dp).clickable { onPlaylist(list.id) }, colors = ListItemDefaults.colors(containerColor = theme.surface))
        }
        item { Spacer(Modifier.height(22.dp)) }
    }
}

@Composable
private fun TrackCarousel(tracks: List<Track>, vm: MainViewModel) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tracks, key = { it.id }) { track ->
            Column(Modifier.width(142.dp).clickable { vm.play(tracks, track) }) {
                Artwork(track.artwork, track.title, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(9.dp))
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
