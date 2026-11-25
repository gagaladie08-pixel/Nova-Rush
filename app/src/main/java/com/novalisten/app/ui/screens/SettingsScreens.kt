package com.novalisten.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.novalisten.app.BuildConfig
import com.novalisten.app.MainViewModel
import com.novalisten.app.data.*
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.theme.LocalUniverse
import com.novalisten.app.ui.theme.NovaIcon

@Composable
fun SettingsScreen(data: LocalData, vm: MainViewModel, onBack: () -> Unit, onAppearance: () -> Unit, onHidden: () -> Unit, onHistory: () -> Unit, onSystem: () -> Unit, onNotifications: () -> Unit, onApi: () -> Unit, onHelp: () -> Unit, onSplit: () -> Unit, onJournal: () -> Unit, onHealth: () -> Unit) {
    val context = LocalContext.current
    var licenceDirectory by remember { mutableStateOf<String?>(null) }
    var licenceText by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Réglages", "NovaListen ${BuildConfig.VERSION_NAME} · version de test") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        item { SettingRow("API et clés", "Paroles · artistes automatiques · identifiants chiffrés", Icons.Outlined.Key, onApi) }
        item { SettingRow("Votre univers", LocalUniverse.current.name, Icons.Outlined.Palette, onAppearance) }
        item { SettingRow("Découpage des artistes", "${data.catalog.split.protectedNames.size} noms protégés · ${data.catalog.split.separators.size} séparateurs", Icons.Outlined.ContentCut, onSplit) }
        item { SettingRow("Morceaux masqués", "${data.hidden.size} référence(s) · fichiers conservés", Icons.Outlined.VisibilityOff, onHidden) }
        item { SettingRow("Historique local", "Une minute réellement jouée par écoute", Icons.Outlined.History, onHistory) }
        item { SettingRow("Relire la bibliothèque", "Réindexer l’affichage, sans toucher aux fichiers", Icons.Outlined.Refresh) { vm.repository.refresh(); vm.message("Actualisation de la bibliothèque demandée") } }
        item { SettingRow("Autorisation de notification", "Commandes de lecture et écran verrouillé", Icons.Outlined.NotificationsNone, onNotifications) }
        item { SettingRow("Autorisations Android", "Gérer l’accès à votre musique", Icons.Outlined.Security, onSystem) }
        item { SettingRow("Suivi de l’application", "Journal des erreurs et des arrêts · rien ne quitte l’appareil", Icons.Outlined.BugReport, onJournal) }
        item { SettingRow("Santé", "Catalogue, bibliothèque, cache des tags et état du jour", Icons.Outlined.MonitorHeart, onHealth) }
        item { SettingRow("Informations et aide", "Gestes, catalogue, sources et confidentialité", Icons.Outlined.Info, onHelp) }
        item { SettingRow("Licences des polices", "25 familles embarquées · OFL / Ubuntu Font Licence", Icons.Outlined.Description) { licenceDirectory = "font-licenses" } }
        item { SettingRow("Licences des bibliothèques principales", "AndroidX, Media3, Kotlin, Coil et Guava", Icons.Outlined.Description) { licenceDirectory = "third-party" } }
    }
    licenceDirectory?.let { directory -> AlertDialog(onDismissRequest = { licenceDirectory = null }, title = { Text(if (directory == "font-licenses") "Polices libres" else "Bibliothèques principales") }, text = {
        LazyColumn { items(context.assets.list(directory)?.filter { it.endsWith(".txt") }?.toList().orEmpty()) { name -> TextButton({ licenceText = context.assets.open("$directory/$name").bufferedReader().use { it.readText() } }) { Text(name.removeSuffix(".txt")) } } }
    }, confirmButton = { TextButton({ licenceDirectory = null }) { Text("Fermer") } }) }
    licenceText?.let { text -> AlertDialog(onDismissRequest = { licenceText = null }, title = { Text("Licence de la police") }, text = { LazyColumn { item { Text(text, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { TextButton({ licenceText = null }) { Text("Fermer") } }) }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) }, supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) }, leadingContent = { NovaIcon(icon, tint = LocalUniverse.current.accent) }, trailingContent = { NovaIcon(Icons.Outlined.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp), colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
}

@Composable
fun HiddenScreen(allTracks: List<Track>, data: LocalData, vm: MainViewModel, onBack: () -> Unit) {
    val hidden = allTracks.filter { it.id in data.hidden }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("Morceaux masqués", "Réafficher ne modifie aucun fichier") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        if (hidden.isEmpty()) item { EmptyState("Rien à réafficher", "Les morceaux cachés apparaîtront ici lorsqu’ils seront accessibles.", Icons.Outlined.VisibilityOff) }
        items(hidden, key = { it.id }) { track -> ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(track.artist) }, leadingContent = { Artwork(track.artwork, track.title, Modifier.size(46.dp)) }, trailingContent = { IconButton({ vm.unhide(track) }) { NovaIcon(Icons.Outlined.Visibility, "Réafficher ${track.title}") } }) }
    }
}

@Composable
fun HistoryScreen(tracks: List<Track>, data: LocalData, onBack: () -> Unit, onPlay: (Track) -> Unit) {
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val displayed = data.events.filter { it.trackId in byId }
    val unavailable = data.events.count { it.trackId !in byId && it.trackId !in data.hidden }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("Historique", "Uniquement les écoutes validées · conservées localement") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        if (unavailable > 0) item { Notice("$unavailable événement(s) de fichiers non affichables conservé(s). Leurs lignes ne sont pas proposées à la lecture ; les événements ne sont pas effacés.") }
        if (displayed.isEmpty()) item { EmptyState("L’histoire reste à écrire", "Écoutez un morceau pendant une minute réelle. Les pauses et déplacements du curseur n’ajoutent pas de temps.", Icons.Outlined.History) }
        items(displayed, key = { it.id }) { event ->
            byId[event.trackId]?.let { track -> ListItem(headlineContent = { Text(track.title, maxLines = 1) }, supportingContent = { Text("${track.artist}\n${event.localDate} · ${java.time.Instant.ofEpochMilli(event.at).atZone(java.time.ZoneId.of(event.zone)).toLocalTime().withSecond(0).withNano(0)} · ${event.zone}", style = MaterialTheme.typography.bodySmall) }, leadingContent = { Artwork(track.artwork, track.title, Modifier.size(48.dp)) }, modifier = Modifier.clickable { onPlay(track) }) }
        }
    }
}
