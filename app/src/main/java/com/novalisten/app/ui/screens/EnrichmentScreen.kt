package com.novalisten.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novalisten.app.MainViewModel
import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import com.novalisten.app.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class EnrichmentMode(val label: String) { LYRICS("Rechercher des paroles"), CATALOG("Comparer les métadonnées"), BIOGRAPHY("Biographie en ligne"), PHOTOS("Photos d’artiste"), COVERS("Pochettes en ligne"), TRANSLATE("Traduire les paroles") }
data class EnrichmentRequest(val mode: EnrichmentMode, val track: Track? = null, val artistId: String = "", val name: String = "", val albumKey: String = "")

@Stable
internal class RemoteWork(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf("")
    private var job: Job? = null
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = ""
        job = scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { message = e.publicMessage() }
            finally { busy = false }
        }
    }
    fun cancel() { job?.cancel(); busy = false; message = "Opération annulée. Les contenus sauvegardés restent intacts." }
}
@Composable internal fun rememberRemoteWork(): RemoteWork { val scope = rememberCoroutineScope(); return remember(scope) { RemoteWork(scope) } }

@Composable
fun EnrichmentDialog(request: EnrichmentRequest, vm: MainViewModel, onDismiss: () -> Unit, onKeys: () -> Unit) {
    DisposableEffect(Unit) { onDispose { vm.clearRemotePreviews() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("enrichment_screen")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { Icon(Icons.Outlined.ArrowBack, "Fermer la recherche") }
                    Text(request.mode.label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onKeys) { Icon(Icons.Outlined.Key, "Configurer mes clés API") }
                }
                HorizontalDivider()
                when (request.mode) {
                    EnrichmentMode.LYRICS -> LyricsSearch(request.track!!, vm, onDismiss)
                    EnrichmentMode.CATALOG -> CatalogSearch(request.track!!, vm)
                    EnrichmentMode.BIOGRAPHY -> BiographySearch(request, vm, onDismiss)
                    EnrichmentMode.PHOTOS -> ArtistPhotoSearch(request, vm, onDismiss)
                    EnrichmentMode.COVERS -> CoverSearch(request, vm, onDismiss)
                    EnrichmentMode.TRANSLATE -> TranslationScreen(request.track!!, vm, onDismiss)
                }
            }
        }
    }
}

@Composable
internal fun QueryFields(title: String, onTitle: (String) -> Unit, artist: String, onArtist: (String) -> Unit, album: String? = null, onAlbum: (String) -> Unit = {}, enabled: Boolean = true) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title, { onTitle(it.take(300)) }, Modifier.fillMaxWidth().testTag("remote_title"), label = { Text("Titre / album recherché") }, singleLine = true, enabled = enabled)
        OutlinedTextField(artist, { onArtist(it.take(300)) }, Modifier.fillMaxWidth().testTag("remote_artist"), label = { Text("Artiste") }, singleLine = true, enabled = enabled)
        if (album != null) OutlinedTextField(album, { onAlbum(it.take(300)) }, Modifier.fillMaxWidth(), label = { Text("Album (facultatif)") }, singleLine = true, enabled = enabled)
    }
}
@Composable
internal fun WorkStatus(work: RemoteWork) {
    if (work.busy) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Recherche en cours…", Modifier.weight(1f)); TextButton(work::cancel) { Text("Annuler") }
    }
    if (work.message.isNotBlank()) Notice(work.message)
}
@Composable
internal fun CreditView(credit: Credit) {
    val handler = LocalUriHandler.current
    Text(credit.provider, style = MaterialTheme.typography.labelLarge)
    if (credit.licence.isNotBlank()) Text(credit.licence, style = MaterialTheme.typography.bodySmall)
    if (credit.author.isNotBlank()) Text(credit.author, style = MaterialTheme.typography.bodySmall)
    if (credit.details.isNotBlank()) Text(credit.details, style = MaterialTheme.typography.bodySmall)
    if (credit.url.isNotBlank()) TextButton({ runCatching { RemoteSafety.https(credit.url); handler.openUri(credit.url) } }) { Text("Ouvrir la source") }
}

@Composable
private fun LyricsSearch(track: Track, vm: MainViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    var results by remember { mutableStateOf(emptyList<LyricCandidate>()) }
    var notices by remember { mutableStateOf(emptyList<String>()) }
    var preview by remember { mutableStateOf<LyricCandidate?>(null) }
    var rights by remember(preview?.id) { mutableStateOf(false) }
    val work = rememberRemoteWork()
    val saveWork = rememberRemoteWork()
    fun search(providers: List<Provider>, clear: Boolean) {
        val query = MusicQuery(title, artist, album, track.durationMs)
        work.run {
            query.checked()
            if (clear) { results = emptyList(); notices = emptyList() }
            coroutineScope {
                providers.forEach { provider -> launch {
                    try {
                        val found = vm.remote.lyrics(provider, query)
                        // Identical texts are not counted as independent confirmations; provenance remains visible.
                        results = (results + found).distinctBy { it.credit.provider + ":" + it.id }
                        notices = notices + "${provider.label} : ${found.size} proposition(s)."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) { notices = notices + "${provider.label} : ${e.publicMessage()}" }
                } }
            }
            if (results.isEmpty()) work.message = "Aucune proposition exploitable dans les sources interrogées. Vous pouvez essayer un recours ou conserver/importer vos paroles locales."
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("lyrics_search"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Notice("Recherche volontaire : les champs ci-dessous et la durée locale (${durationLabel(track.durationMs)}) seront envoyés aux sources demandées. Aucun audio ni historique. Comparez l’artiste et la version ; le premier résultat n’est jamais appliqué.") }
        item { QueryFields(title, { title = it }, artist, { artist = it }, album, { album = it }, !work.busy) }
        item { Button({ search(listOf(Provider.LRCRED, Provider.LRCLIB, Provider.LIRIQO), true) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("search_lyrics_core"), enabled = !work.busy) { Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(8.dp)); Text("Rechercher · noyau de 3 sources") } }
        item { WorkStatus(work) }
        items(notices) { Notice(it) }
        items(results, key = { it.credit.provider + it.id }) { candidate ->
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                    Text("${candidate.artist} · ${candidate.album}", style = MaterialTheme.typography.bodyMedium)
                    Text("${candidate.timing}${if (candidate.durationMs > 0) " · ${durationLabel(candidate.durationMs)}" else ""}", style = MaterialTheme.typography.labelMedium)
                    CreditView(candidate.credit)
                    TextButton({ work.run { preview = vm.remote.loadLyrics(candidate) } }, enabled = !work.busy) { Text(if (candidate.text.isBlank()) "Charger cette version" else "Prévisualiser cette version") }
                }
            }
        }
        item { SectionHeader("Compléments sur demande") }
        item { Notice("Better Lyrics ne consulte que son accès public : une réponse protégée est signalée, jamais contournée. Textyl et lyrics.ovh sont des recours secondaires ; plusieurs services peuvent reprendre la même source. Aucun minutage mot à mot n’est inventé.") }
        for (provider in listOf(Provider.BETTER, Provider.TEXTYL, Provider.OVH)) item {
            OutlinedButton({ search(listOf(provider), false) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), enabled = !work.busy) { Text("Essayer ${provider.label}") }
        }
        item { val uri = LocalUriHandler.current
            TextButton({ runCatching { uri.openUri("https://www.google.com/search?q=" + java.net.URLEncoder.encode("$artist $title paroles", "UTF-8")) } }, Modifier.padding(horizontal = 20.dp)) { Text("Dernier recours : recherche Web volontaire") }
        }
        item { Notice("Les textes choisis restent dans NovaListen, pas dans l’audio. Les droits d’afficher, conserver, traduire ou analyser les paroles sont distincts. Le Web n’est jamais extrait automatiquement ; le collage reste disponible dans l’éditeur local.") }
    }
    preview?.let { candidate -> AlertDialog(onDismissRequest = { if (!saveWork.busy) preview = null }, title = { Text("Choisir ces paroles ?") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("${candidate.title} — ${candidate.artist}", style = MaterialTheme.typography.titleMedium)
            CreditView(candidate.credit)
            Text(candidate.timing, style = MaterialTheme.typography.labelLarge)
            Text(candidate.text, Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
            Text("Remplace la VO sauvegardée uniquement après confirmation. Si le texte change, la VF liée à l’ancienne VO est retirée pour éviter un faux alignement. Aucun fichier audio n’est modifié.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rights, { rights = it }); Text("J’ai vérifié la version et les droits de conserver ce texte pour mon usage.", style = MaterialTheme.typography.bodySmall) }
            WorkStatus(saveWork)
        }
    }, confirmButton = { TextButton({ saveWork.run {
        val previous = vm.repository.data.value.lyrics[track.id] ?: SavedLyrics()
        val same = previous.original == candidate.text
        vm.repository.mutate { saveLyrics(track.id, SavedLyrics(candidate.text, if (same) previous.french else "", candidate.credit.text(), if (same) previous.frenchSource else "")) }
        vm.message("Paroles choisies sauvegardées hors ligne dans NovaListen")
        preview = null; onDismiss()
    } }, enabled = rights && !saveWork.busy) { Text("Choisir et sauvegarder") } }, dismissButton = { TextButton({ preview = null }, enabled = !saveWork.busy) { Text("Annuler") } }) }
}

@Composable
private fun CatalogSearch(track: Track, vm: MainViewModel) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    var country by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<CatalogCandidate>()) }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    val work = rememberRemoteWork()
    fun query() = MusicQuery(title, artist, album, track.durationMs)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Notice("Consultation et comparaison seulement. Les champs saisis sont envoyés au fournisseur demandé. Pas d’écriture dans les tags, pas de fusion de variantes et aucune modification de durée ou de disponibilité du fichier local.") }
        item { Notice("Valeurs locales : ${track.title} — ${track.artist}\n${track.album} · ${durationLabel(track.durationMs)}\nLe pays de boutique ne détermine pas votre localisation.") }
        item { QueryFields(title, { title = it }, artist, { artist = it }, album, { album = it }, !work.busy) }
        item { Button({ work.run { results = vm.remote.recordings(query()) } }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = !work.busy) { Text("Chercher dans MusicBrainz") } }
        item { WorkStatus(work) }
        items(results, key = { it.id }) { candidate -> CatalogCard(candidate) }
        if (tags.isNotEmpty()) item { Notice("Tags Last.fm (pas tous des genres) : ${tags.joinToString(" · ")}\nConsultation de travail uniquement ; aucun cache permanent ni écriture automatique.") }
        item { SectionHeader("Comparer une autre source si utile") }
        item { OutlinedTextField(country, { country = it.take(2).uppercase(java.util.Locale.ROOT) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Boutique iTunes · pays à 2 lettres, ex. FR") }, singleLine = true, enabled = !work.busy) }
        item { TextButton({ work.run { results = vm.remote.itunes(query(), country) } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy && country.length == 2) { Text("Comparer avec iTunes Search") } }
        item { TextButton({ work.run { tags = vm.remote.lastFmTags(query()); if (tags.isEmpty()) work.message = "Last.fm : aucun tag retourné pour cette recherche." } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Compléter les tags · Last.fm (clé)") } }
        item { TextButton({ work.run { results = vm.remote.discogs(query()) } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Édition / crédits difficiles · Discogs (jeton)") } }
        item { TextButton({ work.run { results = vm.remote.genius("$artist $title") } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Références Genius (jeton)") } }
        item { Notice("MusicBrainz est le pivot ; les autres consultations sont volontaires et bornées. Deezer et TheAudioDB sont sautés faute d’accès gratuit confirmé. Les images Last.fm/Deezer ne sont jamais conservées. Les données de travail ne sont pas un nouveau catalogue local.") }
    }
}

@Composable
internal fun CatalogCard(candidate: CatalogCandidate, action: (@Composable () -> Unit)? = null) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(candidate.title, style = MaterialTheme.typography.titleMedium)
            Text(listOf(candidate.artist, candidate.album, candidate.year, if (candidate.durationMs > 0) durationLabel(candidate.durationMs) else "").filter { it.isNotBlank() }.joinToString(" · "))
            if (candidate.details.isNotBlank()) Text(candidate.details, style = MaterialTheme.typography.bodySmall)
            CreditView(candidate.credit)
            action?.invoke()
        }
    }
}
