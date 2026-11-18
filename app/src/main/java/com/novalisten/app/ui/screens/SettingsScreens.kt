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

@Composable
fun SettingsScreen(data: LocalData, vm: MainViewModel, onBack: () -> Unit, onAppearance: () -> Unit, onHidden: () -> Unit, onHistory: () -> Unit, onSystem: () -> Unit, onNotifications: () -> Unit, onApi: () -> Unit) {
    val context = LocalContext.current
    var licenceDirectory by remember { mutableStateOf<String?>(null) }
    var licenceText by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Réglages", "NovaListen ${BuildConfig.VERSION_NAME} · version de test") { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Retour") } } }
        item { SettingRow("API et clés", "Recherches volontaires · identifiants chiffrés sur ce téléphone", Icons.Outlined.Key, onApi) }
        item { SettingRow("Votre univers", LocalUniverse.current.name, Icons.Outlined.Palette, onAppearance) }
        item { SettingRow("Morceaux masqués", "${data.hidden.size} référence(s) · fichiers conservés", Icons.Outlined.VisibilityOff, onHidden) }
        item { SettingRow("Historique local", "Une minute réellement jouée par écoute", Icons.Outlined.History, onHistory) }
        item { SettingRow("Relire la bibliothèque", "Réindexer l’affichage, sans toucher aux fichiers", Icons.Outlined.Refresh) { vm.repository.refresh(); vm.message("Actualisation de la bibliothèque demandée") } }
        item { SettingRow("Autorisation de notification", "Commandes de lecture et écran verrouillé", Icons.Outlined.NotificationsNone, onNotifications) }
        item { SettingRow("Autorisations Android", "Gérer l’accès à votre musique", Icons.Outlined.Security, onSystem) }
        item { SectionHeader("Privé par construction") }
        item { Notice("La lecture reste locale et sans compte obligatoire. Les API sont appelées par des recherches volontaires avec les champs affichés, jamais par un envoi de collection ou d’historique. Azure n’envoie les paroles qu’après confirmation dédiée. ML Kit peut émettre les diagnostics Google déclarés après activation. Les sauvegardes automatiques Android sont désactivées.") }
        item { SectionHeader("Ce qui arrive ensuite") }
        item { Notice("Recherche de paroles, références de métadonnées, biographies, photos et pochettes disponibles sur commande ; traduction locale ML Kit et Azure F0 volontaire. Fil Rouge, modèles intelligents, édition des tags dans l’audio et fusion d’albums ne sont pas encore intégrés. Listening Party et YouTube restent à concevoir.") }
        item { SettingRow("Licences des polices", "25 familles embarquées · OFL / Ubuntu Font Licence", Icons.Outlined.Description) { licenceDirectory = "font-licenses" } }
        item { SettingRow("Licences des bibliothèques principales", "AndroidX, Media3, Kotlin, Coil et Guava", Icons.Outlined.Description) { licenceDirectory = "third-party" } }
        item { Notice("APK de test signé automatiquement, pas une version de production. Les signatures des compilations peuvent différer ; conservez vos données avant toute désinstallation. Les réglages et enrichissements sont stockés seulement dans NovaListen.") }
    }
    licenceDirectory?.let { directory -> AlertDialog(onDismissRequest = { licenceDirectory = null }, title = { Text(if (directory == "font-licenses") "Polices libres" else "Bibliothèques principales") }, text = {
        LazyColumn { items(context.assets.list(directory)?.filter { it.endsWith(".txt") }?.toList().orEmpty()) { name -> TextButton({ licenceText = context.assets.open("$directory/$name").bufferedReader().use { it.readText() } }) { Text(name.removeSuffix(".txt")) } } }
    }, confirmButton = { TextButton({ licenceDirectory = null }) { Text("Fermer") } }) }
    licenceText?.let { text -> AlertDialog(onDismissRequest = { licenceText = null }, title = { Text("Licence de la police") }, text = { LazyColumn { item { Text(text, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { TextButton({ licenceText = null }) { Text("Fermer") } }) }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) }, supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) }, leadingContent = { NovaIcon(icon, tint = LocalUniverse.current.accent) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp), colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
}

@Composable
fun HiddenScreen(allTracks: List<Track>, data: LocalData, vm: MainViewModel, onBack: () -> Unit) {
    val hidden = allTracks.filter { it.id in data.hidden }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("Morceaux masqués", "Réafficher ne modifie aucun fichier") { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Retour") } } }
        if (hidden.isEmpty()) item { EmptyState("Rien à réafficher", "Les morceaux cachés apparaîtront ici lorsqu’ils seront accessibles.", Icons.Outlined.VisibilityOff) }
        items(hidden, key = { it.id }) { track -> ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(track.artist) }, leadingContent = { Artwork(track.artwork, track.title, Modifier.size(46.dp)) }, trailingContent = { IconButton({ vm.unhide(track) }) { Icon(Icons.Outlined.Visibility, "Réafficher ${track.title}") } }) }
    }
}

@Composable
fun HistoryScreen(tracks: List<Track>, data: LocalData, onBack: () -> Unit, onPlay: (Track) -> Unit) {
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val displayed = data.events.filter { it.trackId in byId }
    val unavailable = data.events.count { it.trackId !in byId && it.trackId !in data.hidden }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("Historique", "Uniquement les écoutes validées · conservées localement") { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Retour") } } }
        if (unavailable > 0) item { Notice("$unavailable événement(s) de fichiers non affichables conservé(s). Leurs lignes ne sont pas proposées à la lecture ; les événements ne sont pas effacés.") }
        if (displayed.isEmpty()) item { EmptyState("L’histoire reste à écrire", "Écoutez un morceau pendant une minute réelle. Les pauses et déplacements du curseur n’ajoutent pas de temps.", Icons.Outlined.History) }
        items(displayed, key = { it.id }) { event ->
            byId[event.trackId]?.let { track -> ListItem(headlineContent = { Text(track.title, maxLines = 1) }, supportingContent = { Text("${track.artist}\n${event.localDate} · ${java.time.Instant.ofEpochMilli(event.at).atZone(java.time.ZoneId.of(event.zone)).toLocalTime().withSecond(0).withNano(0)} · ${event.zone}", style = MaterialTheme.typography.bodySmall) }, leadingContent = { Artwork(track.artwork, track.title, Modifier.size(48.dp)) }, modifier = Modifier.clickable { onPlay(track) }) }
        }
    }
}
