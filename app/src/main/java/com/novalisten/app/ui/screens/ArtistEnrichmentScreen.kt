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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        item { Notice("Cette recherche manuelle envoie le nom saisi à Wikipédia sur votre demande, indépendamment de l’automatisme du morceau écouté. Français prioritaire ; l’anglais est une recherche distincte. Pas de traduction automatique des biographies, ni d’envoi des fichiers musicaux.") }
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
        val seed = vm.artistNoteFor(request.artistId)
        vm.repository.mutate { val old = snapshot().artists[request.artistId] ?: seed; saveArtist(request.artistId, old.copy(biography = bio.text, source = bio.credit.text(), biographyLocked = true)) }
        vm.message("Biographie choisie sauvegardée hors ligne avec ses crédits")
        onDismiss()
    } }, enabled = confirm && !saveWork.busy) { Text("Choisir cette biographie") } }, dismissButton = { TextButton({ selected = null }, enabled = !saveWork.busy) { Text("Annuler") } }) }
}

@Composable
internal fun ArtistPhotoSearch(request: EnrichmentRequest, vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(request.name) }
    var identities by remember { mutableStateOf(emptyList<ArtistCandidate>()) }
    var identity by remember { mutableStateOf<ArtistCandidate?>(null) }
    val automaticPhotos by vm.automaticArtists.state.collectAsStateWithLifecycle()
    var ready by remember(request.artistId) { mutableStateOf(automaticPhotos[request.artistId]?.proposals.orEmpty()) }
    var candidates by remember { mutableStateOf(emptyList<ImageCandidate>()) }
    var notices by remember { mutableStateOf(emptyList<String>()) }
    var wikidata by remember { mutableStateOf(emptyList<ArtistCandidate>()) }
    var commonsPage by remember { mutableIntStateOf(1) }
    var openversePage by remember { mutableIntStateOf(1) }
    var showOther by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<ImageCandidate?>(null) }
    val work = rememberRemoteWork()
    val localData by vm.repository.data.collectAsStateWithLifecycle()
    val knownMbid = localData.identities["artist:${request.artistId}"]
    val currentPortrait = localData.artists[request.artistId]?.portrait
    fun resetGallery() { ready = emptyList(); candidates = emptyList(); notices = emptyList(); commonsPage = 1; openversePage = 1; showOther = false }
    fun append(incoming: List<ImageCandidate>) { candidates = (candidates + incoming).distinctBy { imageIdentity(it.original) }.take(120) }
    suspend fun prepare(incoming: List<ImageCandidate>, target: Int) {
        append(incoming)
        val batch = PhotoDiscovery(vm.remoteImages::preparePhoto).discover(incoming, ready, target.coerceAtMost(60)) { count, attempts ->
            work.message = "$count vignette(s) exploitable(s) · $attempts aperçu(s) examiné(s) dans ce lot."
        }
        ready = batch.photos
        notices = notices + batch.warnings.map { "Aperçu : $it" }
        notices = notices + "Lot borné : ${batch.attempted} aperçu(s) chargé(s), ${batch.probableDuplicates} doublon(s) probable(s), ${batch.unavailable} image(s) indisponible(s) ou trop petite(s), ${batch.deferredRights} licence(s) à examiner manuellement."
        work.message = "${ready.size} vignette(s) prête(s), cible initiale : six distinctes. Sujet, identité et date restent à confirmer visuellement."
    }
    fun searchImages() {
        val artist = identity ?: return
        work.run {
            if (ready.size >= 6) { work.message = "Six vignettes sont déjà prêtes : aucun nouvel appel. Les compléments restent volontaires."; return@run }
            // Reuse P18/previews already examined. At most one metadata page per admissible source.
            val found = mutableListOf<ImageCandidate>()
            coroutineScope {
                launch {
                    try { val result = vm.remote.commons(artist.name); found += result; notices = notices + "Commons : ${result.size} proposition(s)." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) { notices = notices + "Commons : ${e.publicMessage()}" }
                }
                launch {
                    try { val result = vm.remote.openverse(artist.name); found += result; notices = notices + "Openverse : ${result.size} proposition(s)." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) { notices = notices + "Openverse : ${e.publicMessage()}" }
                }
            }
            prepare(found, 6)
            if (ready.isEmpty()) showOther = true
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Notice("D’abord identifier l’artiste pour éviter les homonymes, groupes et membres. Seul le nom / identifiant est envoyé aux sources demandées. La photo actuelle reste inchangée jusqu’au choix final.") }
        if (currentPortrait != null) item { Column(Modifier.padding(horizontal = 20.dp)) { Text("Votre portrait sauvegardé · réutilisé localement"); Artwork(currentPortrait, request.name, Modifier.size(96.dp)) } }
        item { OutlinedTextField(name, { name = it.take(300); identity = null; identities = emptyList(); wikidata = emptyList(); resetGallery() }, Modifier.fillMaxWidth().padding(20.dp), label = { Text("Nom et contexte de l’artiste") }, singleLine = true, enabled = !work.busy) }
        item { Button({ work.run { identities = vm.remote.artists(name); if (identities.isEmpty()) work.message = "Aucune identité MusicBrainz. Vous pouvez comparer Wikidata." } }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = !work.busy && name.isNotBlank()) { Text("Identifier avec MusicBrainz") } }
        if (knownMbid != null && identity == null) item { TextButton({
            resetGallery(); identity = ArtistCandidate(knownMbid, request.name, "Association choisie précédemment", Credit("MusicBrainz", "https://musicbrainz.org/artist/$knownMbid", "Données de base CC0"), mbid = knownMbid)
        }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Réutiliser mon association MusicBrainz · sans appel") } }
        items(identities, key = { it.id }) { artist ->
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(artist.name, style = MaterialTheme.typography.titleMedium); Text(artist.description); CreditView(artist.credit)
                    TextButton({ resetGallery(); identity = artist; vm.saveIdentity("artist:${request.artistId}", artist.mbid) }, enabled = !work.busy) { Text("C’est cet artiste · mémoriser l’association") }
                }
            }
        }
        identity?.let { artist ->
            item { Notice("Identité retenue : ${artist.name}\n${artist.description}\nUn identifiant ne certifie pas chaque photographie.") }
            item { Notice("La recherche charge un premier lot de métadonnées Commons + Openverse (six par source), puis au plus douze aperçus ouverts pour viser six vignettes décodables, d’au moins 96 px, distinctes par URL et par comparaison visuelle locale. Arrêt dès la cible, pas de pagination automatique. Aucun visage n’est envoyé à un moteur d’analyse.") }
            item { Button(::searchImages, Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("search_artist_images"), enabled = !work.busy) { Text("Chercher six vignettes · Commons + Openverse") } }
        }
        item { WorkStatus(work) }
        items(notices) { Notice(it) }
        items(ready, key = { it.image.id }) { photo -> ImageResultCard(photo.image, vm, work.busy, initialPreview = photo.preview) { chosen = photo.image } }
        identity?.let { artist ->
            item { Notice("Cible de six vignettes utiles, jamais une garantie d’exactitude ni de récence. Les doublons visuels sont une heuristique locale ; vous pouvez examiner les autres propositions. Les pages supplémentaires ne partent que sur commande, avec deux requêtes simultanées maximum pour tout ce parcours, aperçus compris.") }
            item { TextButton({ work.run { val page = commonsPage + 1; val found = vm.remote.commons(artist.name, page); commonsPage = page; prepare(found, ready.size + 6) } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy && commonsPage < 5 && ready.size < 60) { Text("Plus de photos · une page Commons") } }
            item { TextButton({ work.run { val page = openversePage + 1; val found = vm.remote.openverse(artist.name, page); openversePage = page; prepare(found, ready.size + 6) } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy && openversePage < 5 && ready.size < 60) { Text("Plus de photos · une page Openverse") } }
            if (artist.mbid != null) item { TextButton({ work.run { val found = vm.remote.fanart(artist.mbid, true); append(found); showOther = true; work.message = "fanart.tv : ${found.size} proposition(s) à examiner manuellement, ni exactitude ni récence garanties. Pas d’aperçu sans vérification des droits." } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Ultime complément · fanart.tv (clé)") } }
        }
        val others = candidates.filter { image -> ready.none { imageIdentity(it.image.original) == imageIdentity(image.original) } }
        if (others.isNotEmpty()) {
            item { TextButton({ showOther = !showOther }, Modifier.padding(horizontal = 20.dp)) { Text(if (showOther) "Masquer les autres propositions" else "Examiner les ${others.size} autres propositions / doublons probables") } }
            if (showOther) items(others, key = { it.id }) { image -> ImageResultCard(image, vm, work.busy) { chosen = image } }
        }
        item { SectionHeader("Comparer une identité Wikidata") }
        item { TextButton({ work.run { wikidata = vm.remote.wikidata(name) } }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy && name.isNotBlank()) { Text("Chercher l’identité dans Wikidata") } }
        items(wikidata, key = { it.id }) { artist ->
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${artist.name} · ${artist.id}", style = MaterialTheme.typography.titleMedium); Text(artist.description); CreditView(artist.credit)
                    TextButton({ work.run {
                        val details = vm.remote.wikidataDetails(artist.id)
                        resetGallery(); identity = details
                        vm.saveIdentity("artist:${request.artistId}", details.mbid)
                        details.imageTitle?.let { prepare(vm.remote.commons(details.name, fileTitle = it), 6) }
                        notices = notices + "Piste P18 ${if (details.imageTitle == null) "absente" else "examinée, pas appliquée"}. Les faits/records ne sont pas certifiés par Wikidata."
                    } }, enabled = !work.busy) { Text("Choisir cette identité et examiner P18") }
                }
            }
        }
        item { Notice("Last.fm sert aux informations musicales, pas à collecter des portraits. Aucun scraping. Genius/TheAudioDB/Discogs restent sautés pour les photos faute d’admission établie. Les dates de publication/récupération ne sont jamais appelées dates de prise de vue. Budget de cette première interface : cinq pages manuelles par source, pas de découverte illimitée.") }
    }
    chosen?.let { image -> ImageSaveDialog(image, vm, { chosen = null }) { uri ->
        val seed = vm.artistNoteFor(request.artistId)
        vm.repository.mutate { val old = snapshot().artists[request.artistId] ?: seed; saveArtist(request.artistId, old.copy(portrait = uri, portraitSource = image.credit.text() + "\n" + image.date + "\nCopie d’affichage JPEG redimensionnée par NovaListen.", portraitLocked = true)) }
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
    val localData by vm.repository.data.collectAsStateWithLifecycle()
    val knownRelease = localData.identities["album:$albumKey"]
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
        if (localData.covers.containsKey(albumKey)) item { TextButton({ vm.saveCover(albumKey, null); onDismiss() }, Modifier.padding(horizontal = 20.dp)) { Text("Revenir à la pochette du fichier dans l’affichage") } }
        item { Notice("Les illustrations iTunes, Last.fm, Deezer et Discogs ne deviennent pas des pochettes permanentes. TheAudioDB est sauté faute d’accès gratuit confirmé. Aucun écrasement de la pochette actuelle après une recherche ou un échec.") }
    }
    chosen?.let { image -> ImageSaveDialog(image, vm, { chosen = null }) { uri ->
        vm.repository.mutate { saveCover(albumKey, SavedCover(uri, image.credit.text() + "\nCopie d’affichage JPEG redimensionnée par NovaListen.")) }
        vm.message("Pochette d’affichage sauvegardée dans NovaListen, pas dans l’audio")
        onDismiss()
    } }
}

@Composable
internal fun ImageResultCard(image: ImageCandidate, vm: MainViewModel, blocked: Boolean, initialPreview: String? = null, onChoose: () -> Unit) {
    var localPreview by remember(image.id, initialPreview) { mutableStateOf(initialPreview) }
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
        // Once bytes are ready, finalize the tiny local DB assignment even if this dialog closes.
        // This prevents deleting a just-committed picture on coroutine cancellation during dispatch back.
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            try { onSave(uri) } catch (e: Exception) { runCatching { java.io.File(java.net.URI(uri)).delete() }; throw e }
        }
    } }, enabled = confirmed && !work.busy) { Text("Choisir et sauvegarder") } }, dismissButton = { TextButton(onDismiss, enabled = !work.busy) { Text("Annuler") } })
}
