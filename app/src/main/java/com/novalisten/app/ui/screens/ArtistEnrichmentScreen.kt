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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import com.novalisten.app.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun BiographySearch(request: EnrichmentRequest, vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(request.name) }
    var results by remember { mutableStateOf(emptyList<Biography>()) }
    var selected by remember { mutableStateOf<Biography?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val work = rememberRemoteWork()
    val saveWork = rememberRemoteWork()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Notice("Le nom saisi est envoyé à Wikipédia seulement après votre demande. Français prioritaire ; l’anglais est une recherche distincte. Pas de traduction automatique des biographies, ni d’envoi des fichiers musicaux.") }
        item { OutlinedTextField(name, { name = it.take(300) }, Modifier.fillMaxWidth().padding(20.dp), label = { Text("Artiste à rechercher") }, singleLine = true, enabled = !work.busy) }
        item { Button({ work.run { results = vm.remote.biographies(name, "fr"); if (results.isEmpty()) work.message = "Aucune introduction française. Vous pouvez proposer l’anglais ou corriger le nom." } }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = !work.busy && name.isNotBlank()) { Text("Rechercher en français") } }
        item { TextButton({ work.run { results = vm.remote.biographies(name, "en"); if (results.isEmpty()) work.message = "Aucune introduction anglaise pour ces termes." } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy && name.isNotBlank()) { Text("Proposer aussi une version anglaise") } }
        item { WorkStatus(work) }
        items(results, key = { it.language + it.id }) { bio ->
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(bio.title, style = MaterialTheme.typography.titleMedium)
                    Text(bio.text.take(500), style = MaterialTheme.typography.bodyMedium)
                    CreditView(bio.credit)
                    Row {
                        TextButton({ selected = bio; confirm = false }) { Text("Prévisualiser") }
                        TextButton({ work.run { selected = vm.remote.fullBiography(bio); confirm = false } }, enabled = !work.busy) { Text("Texte développé") }
                    }
                }
            }
        }
        item { Notice("Une introduction n’est pas une biographie exhaustive. Le texte développé peut être limité à 30 000 caractères. Vérifiez le sujet, conservez source/licence/crédits ; une recherche ne remplace jamais votre texte local.") }
    }
    selected?.let { bio -> AlertDialog(onDismissRequest = { if (!saveWork.busy) selected = null }, title = { Text(bio.title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(if (bio.full) "Texte développé de l’article (extrait limité)" else "Introduction Wikipédia", style = MaterialTheme.typography.labelLarge)
            Text(bio.text, Modifier.heightIn(max = 270.dp).verticalScroll(rememberScrollState()))
            CreditView(bio.credit)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(confirm, { confirm = it }); Text("C’est le bon artiste. Remplacer ma biographie locale en conservant ces crédits.", style = MaterialTheme.typography.bodySmall) }
            WorkStatus(saveWork)
        }
    }, confirmButton = { TextButton({ saveWork.run {
        val old = vm.repository.data.value.artists[request.artistId] ?: ArtistNote()
        vm.repository.mutate { saveArtist(request.artistId, old.copy(biography = bio.text, source = bio.credit.text())) }
        vm.message("Biographie choisie sauvegardée hors ligne avec ses crédits")
        onDismiss()
    } }, enabled = confirm && !saveWork.busy) { Text("Choisir cette biographie") } }, dismissButton = { TextButton({ selected = null }, enabled = !saveWork.busy) { Text("Annuler") } }) }
}

@Composable
internal fun ArtistPhotoSearch(request: EnrichmentRequest, vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(request.name) }
    var identities by remember { mutableStateOf(emptyList<ArtistCandidate>()) }
    var identity by remember { mutableStateOf<ArtistCandidate?>(null) }
    var images by remember { mutableStateOf(emptyList<ImageCandidate>()) }
    var notice by remember { mutableStateOf(emptyList<String>()) }
    var wikidata by remember { mutableStateOf(emptyList<ArtistCandidate>()) }
    var commonsPage by remember { mutableIntStateOf(1) }
    var openversePage by remember { mutableIntStateOf(1) }
    var shown by remember { mutableIntStateOf(6) }
    var chosen by remember { mutableStateOf<ImageCandidate?>(null) }
    val work = rememberRemoteWork()
    val knownMbid = vm.repository.data.value.identities["artist:${request.artistId}"]
    fun append(incoming: List<ImageCandidate>) { images = (images + incoming).distinctBy { it.original.removePrefix("http://").removePrefix("https://") } }
    fun searchImages() {
        val artist = identity ?: return
        work.run {
            images = emptyList(); notice = emptyList(); shown = 6; commonsPage = 1; openversePage = 1
            // Vague 1 (Genius/TheAudioDB) is deliberately skipped: photo rights/access unresolved.
            coroutineScope {
                launch {
                    try { val found = vm.remote.commons(artist.name); append(found); notice = notice + "Commons : ${found.size} proposition(s)." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) { notice = notice + "Commons : ${e.publicMessage()}" }
                }
                launch {
                    try { val found = vm.remote.openverse(artist.name); append(found); notice = notice + "Openverse : ${found.size} proposition(s)." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) { notice = notice + "Openverse : ${e.publicMessage()}" }
                }
            }
            if (images.isEmpty()) work.message = "Aucune proposition reçue. Conservez le portrait actuel, corrigez l’identité ou choisissez une photo locale autorisée."
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Notice("D’abord identifier l’artiste pour ne pas mélanger les homonymes, groupes et membres. Le nom / identifiant est envoyé aux sources demandées, pas votre collection. La photo actuelle reste inchangée jusqu’au choix final.") }
        item { OutlinedTextField(name, { name = it.take(300); identity = null; images = emptyList() }, Modifier.fillMaxWidth().padding(20.dp), label = { Text("Nom et contexte de l’artiste") }, singleLine = true, enabled = !work.busy) }
        item { Button({ work.run { identities = vm.remote.artists(name); if (identities.isEmpty()) work.message = "Aucune identité MusicBrainz. Vous pouvez comparer Wikidata." } }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = !work.busy && name.isNotBlank()) { Text("Identifier avec MusicBrainz") } }
        if (knownMbid != null && identity == null) item { TextButton({
            identity = ArtistCandidate(knownMbid, request.name, "Association choisie précédemment", Credit("MusicBrainz", "https://musicbrainz.org/artist/$knownMbid", "Données de base CC0"), mbid = knownMbid)
        }, Modifier.padding(horizontal = 20.dp)) { Text("Réutiliser mon association MusicBrainz") } }
        items(identities, key = { it.id }) { artist ->
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(artist.name, style = MaterialTheme.typography.titleMedium); Text(artist.description)
                    CreditView(artist.credit)
                    TextButton({ identity = artist; images = emptyList(); vm.saveIdentity("artist:${request.artistId}", artist.mbid) }, enabled = !work.busy) { Text("C’est cet artiste · mémoriser l’association") }
                }
            }
        }
        identity?.let { artist ->
            item { Notice("Identité retenue pour cette recherche : ${artist.name}\n${artist.description}\nUn identifiant ne certifie pas chaque photographie.") }
            item { Button(::searchImages, Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("search_artist_images"), enabled = !work.busy) { Text("Chercher des photos · Commons + Openverse") } }
        }
        item { WorkStatus(work) }
        items(notice) { Notice(it) }
        items(images.take(shown), key = { it.id }) { image -> ImageResultCard(image, vm, work.busy) { chosen = image } }
        if (images.size > shown) item { TextButton({ shown += 6 }, Modifier.padding(horizontal = 20.dp)) { Text("Afficher les autres propositions déjà reçues") } }
        identity?.let { artist ->
            item { Notice("Premier lot : jusqu’à six propositions, pas six résultats garantis. Les aperçus sont chargés sur demande et soumis au même plafond de deux requêtes. Les pages suivantes sont volontaires et bornées.") }
            item { TextButton({ work.run { val page = commonsPage + 1; val found = vm.remote.commons(artist.name, page); commonsPage = page; append(found); shown = images.size; work.message = "Commons · page $page : ${found.size} proposition(s)." } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Plus de photos · une page Commons") } }
            item { TextButton({ work.run { val page = openversePage + 1; val found = vm.remote.openverse(artist.name, page); openversePage = page; append(found); shown = images.size; work.message = "Openverse · page $page : ${found.size} proposition(s)." } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Plus de photos · une page Openverse") } }
            if (artist.mbid != null) item { TextButton({ work.run { val found = vm.remote.fanart(artist.mbid, true); append(found); shown = images.size; work.message = "fanart.tv : ${found.size} proposition(s), ni exactitude ni récence garanties." } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Ultime complément · fanart.tv (clé)") } }
        }
        item { SectionHeader("Comparer une identité Wikidata") }
        item { TextButton({ work.run { wikidata = vm.remote.wikidata(name) } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy && name.isNotBlank()) { Text("Chercher l’identité dans Wikidata") } }
        items(wikidata, key = { it.id }) { artist ->
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${artist.name} · ${artist.id}", style = MaterialTheme.typography.titleMedium); Text(artist.description); CreditView(artist.credit)
                    TextButton({ work.run {
                        val details = vm.remote.wikidataDetails(artist.id)
                        identity = details; images = emptyList(); shown = 6
                        if (details.mbid != null) vm.saveIdentity("artist:${request.artistId}", details.mbid)
                        details.imageTitle?.let { append(vm.remote.commons(details.name, fileTitle = it)) }
                        work.message = "Identité choisie : ${details.name}. ${if (details.imageTitle == null) "Pas de photo P18 renseignée." else "Piste P18 proposée, pas appliquée."} Les faits/records ne sont pas certifiés par Wikidata."
                    } }, enabled = !work.busy) { Text("Choisir cette identité et examiner P18") }
                }
            }
        }
        item { Notice("Last.fm sert aux informations musicales, pas à collecter des portraits. Aucun scraping. Genius/TheAudioDB/Discogs restent sautés pour les photos faute d’admission établie. Les dates de publication/récupération ne sont jamais appelées dates de prise de vue.") }
    }
    chosen?.let { image -> ImageSaveDialog(image, vm, { chosen = null }) { uri ->
        val old = vm.repository.data.value.artists[request.artistId] ?: ArtistNote()
        vm.repository.mutate { saveArtist(request.artistId, old.copy(portrait = uri, portraitSource = image.credit.text() + "\n" + image.date)) }
        vm.message("Portrait choisi sauvegardé hors ligne avec ses crédits")
        onDismiss()
    } }
}

@Composable
internal fun CoverSearch(request: EnrichmentRequest, vm: MainViewModel, onDismiss: () -> Unit) {
    val track = request.track!!
    val albumKey = request.albumKey.ifBlank { track.albumKey }
    var album by remember { mutableStateOf(request.name.ifBlank { track.album }) }
    var artist by remember { mutableStateOf(track.albumArtist.ifBlank { track.artist }) }
    var releases by remember { mutableStateOf(emptyList<CatalogCandidate>()) }
    var selectedRelease by remember { mutableStateOf<CatalogCandidate?>(null) }
    var images by remember { mutableStateOf(emptyList<ImageCandidate>()) }
    var chosen by remember { mutableStateOf<ImageCandidate?>(null) }
    val work = rememberRemoteWork()
    val knownRelease = vm.repository.data.value.identities["album:$albumKey"]
    fun searchEdition(release: CatalogCandidate) {
        work.run {
            val id = release.releaseId ?: throw RemoteFailure("Identifiant d’édition absent.")
            selectedRelease = release
            images = vm.remote.covers(id)
            vm.saveIdentity("album:$albumKey", id)
            if (images.isEmpty()) work.message = "Aucune face avant disponible pour cette édition. Vous pouvez comparer une autre édition ou demander un recours de groupe."
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Notice("Choisissez l’édition avant sa pochette. Le titre d’album et l’artiste saisis sont envoyés à MusicBrainz, puis seul l’identifiant choisi à Cover Art Archive. La pochette choisie changera l’affichage de cet album dans NovaListen, pas les tags des fichiers ni vos regroupements.") }
        item { QueryFields(album, { album = it }, artist, { artist = it }, enabled = !work.busy) }
        item { Button({ work.run { releases = vm.remote.releases(album, artist); selectedRelease = null; images = emptyList(); if (releases.isEmpty()) work.message = "Aucune édition candidate. Corrigez l’album ou l’artiste, sans modifier vos fichiers." } }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = !work.busy) { Text("Chercher les éditions · MusicBrainz") } }
        if (knownRelease != null) item { TextButton({ searchEdition(CatalogCandidate(knownRelease, album, artist, releaseId = knownRelease, credit = Credit("MusicBrainz", "https://musicbrainz.org/release/$knownRelease"), details = "Association d’édition choisie précédemment")) }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Réutiliser mon édition associée · CAA") } }
        item { WorkStatus(work) }
        items(releases, key = { it.id }) { release -> CatalogCard(release) { TextButton({ searchEdition(release) }, enabled = !work.busy) { Text("Choisir cette édition pour chercher sa pochette") } } }
        selectedRelease?.let { release ->
            item { Notice("Recherche pour : ${release.title} · ${release.artist} · ${release.year}\n${release.details}\nUne face avant approuvée par CAA n’est pas une autorisation générale d’utilisation.") }
            if (release.groupId != null) {
                item { TextButton({ work.run { images = (images + vm.remote.covers(release.groupId, true)).distinctBy { it.original } } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Autre pochette représentative du groupe · CAA") } }
                item { TextButton({ work.run { images = (images + vm.remote.fanart(release.groupId, false)).distinctBy { it.original } } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Complément de groupe · fanart.tv (clé)") } }
            }
        }
        items(images, key = { it.id }) { image -> ImageResultCard(image, vm, work.busy) { chosen = image } }
        if (vm.repository.data.value.covers.containsKey(albumKey)) item { TextButton({ vm.saveCover(albumKey, null); onDismiss() }, Modifier.padding(horizontal = 20.dp)) { Text("Revenir à la pochette du fichier dans l’affichage") } }
        item { Notice("Les illustrations iTunes, Last.fm, Deezer et Discogs ne deviennent pas des pochettes permanentes. TheAudioDB est sauté faute d’accès gratuit confirmé. Aucun écrasement de la pochette actuelle après une recherche ou un échec.") }
    }
    chosen?.let { image -> ImageSaveDialog(image, vm, { chosen = null }) { uri ->
        vm.repository.mutate { saveCover(albumKey, SavedCover(uri, image.credit.text())) }
        vm.message("Pochette d’affichage sauvegardée dans NovaListen, pas dans l’audio")
        onDismiss()
    } }
}

@Composable
internal fun ImageResultCard(image: ImageCandidate, vm: MainViewModel, blocked: Boolean, onChoose: () -> Unit) {
    var localPreview by remember(image.id) { mutableStateOf<String?>(null) }
    var allowPreview by remember(image.id) { mutableStateOf(false) }
    val work = rememberRemoteWork()
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(image.title, style = MaterialTheme.typography.titleMedium)
            Text(image.date, style = MaterialTheme.typography.bodySmall)
            if (localPreview != null) Artwork(localPreview, image.title, Modifier.fillMaxWidth().height(190.dp))
            CreditView(image.credit)
            if (!image.licenceKnown) {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allowPreview, { allowPreview = it }); Text("J’ai vérifié les droits de consulter cet aperçu.", style = MaterialTheme.typography.bodySmall) }
            }
            Row {
                TextButton({ work.run { localPreview = vm.remoteImages.preview(image) } }, enabled = !blocked && !work.busy && (image.licenceKnown || allowPreview)) { Text(if (localPreview == null) "Charger l’aperçu" else "Recharger l’aperçu") }
                TextButton(onChoose, enabled = !blocked && !work.busy) { Text("Examiner ce choix") }
            }
            WorkStatus(work)
        }
    }
}

@Composable
internal fun ImageSaveDialog(image: ImageCandidate, vm: MainViewModel, onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    var confirmed by remember(image.id) { mutableStateOf(false) }
    val work = rememberRemoteWork()
    AlertDialog(onDismissRequest = { if (!work.busy) onDismiss() }, title = { Text("Sauvegarder cette image ?") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(image.title, style = MaterialTheme.typography.titleMedium)
            CreditView(image.credit)
            Text(image.date)
            Text(if (image.licenceKnown) "Licence ouverte annoncée : vérifiez le sujet, la provenance et les conditions, notamment sur la page d’origine pour Openverse." else "Cette source ne fournit pas ici de licence générale. N’enregistrez que si vous disposez réellement des droits de conserver et d’afficher cette image.", style = MaterialTheme.typography.bodySmall)
            Text("Seule l’image choisie est téléchargée (8 Mo maximum), redimensionnée à 1 600 px et enregistrée en JPEG avec ses crédits. L’ancienne image reste en place en cas d’échec.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(confirmed, { confirmed = it }); Text("J’ai vérifié le sujet et les droits sur la source ; je peux conserver cette image et ses crédits.", style = MaterialTheme.typography.bodySmall) }
            WorkStatus(work)
        }
    }, confirmButton = { TextButton({ work.run {
        val uri = vm.remoteImages.save(image)
        try { onSave(uri) } catch (e: Exception) { runCatching { java.io.File(java.net.URI(uri)).delete() }; throw e }
    } }, enabled = confirmed && !work.busy) { Text("Choisir et sauvegarder") } }, dismissButton = { TextButton(onDismiss, enabled = !work.busy) { Text("Annuler") } })
}
