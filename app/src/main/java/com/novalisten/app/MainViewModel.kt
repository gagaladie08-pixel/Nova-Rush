package com.novalisten.app

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.novalisten.app.data.*
import com.novalisten.app.playback.PlaybackService
import com.novalisten.app.enrichment.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SOURCE_PLAYLIST = "source_playlist"

data class PlaybackProgress(val trackId: String? = null, val position: Long = 0, val duration: Long = 0)

data class PlaybackUi(
    val connected: Boolean = false,
    val currentId: String? = null,
    val playing: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val queue: List<String> = emptyList(),
    val index: Int = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sourcePlaylist: String? = null,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NovaApplication
    val repository = app.repository
    val themes = app.themes
    val credentials = app.credentials
    val remote = app.remote
    val lyricPreferences = app.lyricPreferences
    val automaticLyrics = app.automaticLyrics
    val automaticArtists = app.automaticArtists
    val artistPreferences = app.artistPreferences
    val artistPortraits = app.artistPortraits
    private val _playerOpenRequests = MutableStateFlow(PlayerOpenRequest())
    val playerOpenRequests = _playerOpenRequests.asStateFlow()
    fun lyricsFor(id: String): SavedLyrics {
        val local = repository.data.value
        val track = repository.library.value.tracks.firstOrNull { it.id == id }
        return resolveLyrics(id, local.lyrics[id], automaticLyrics.state.value, local.automaticLyrics[id]?.forTrack(track))
    }
    val remoteImages = app.remoteImages
    val translation = app.translation
    private val _playback = MutableStateFlow(PlaybackUi())
    val playback = _playback.asStateFlow()
    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress = _progress.asStateFlow()
    fun prioritizeArtists(ids: List<String>) { app.visibleArtists.value = ids }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val messages = _messages.asSharedFlow()
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { updatePlayer() }
        override fun onPlayerError(error: PlaybackException) {
            message("Ce fichier n’est pas lisible ou n’est plus accessible. Vos autres morceaux restent disponibles.")
        }
    }
    init {
        viewModelScope.launch { credentials.refresh() }
        repository.refresh()
        app.startArtistEnrichment()
        controllerFuture.addListener({
            runCatching {
                controller = controllerFuture.get().also { it.addListener(listener) }
                updatePlayer()
            }.onFailure { message("Le lecteur n’a pas pu démarrer. Fermez puis rouvrez NovaListen.") }
        }, androidx.core.content.ContextCompat.getMainExecutor(application))
        viewModelScope.launch {
            while (isActive) { updateProgress(); delay(250) }
        }
    }
    fun message(text: String) { _messages.tryEmit(text) }
    private fun updateProgress() {
        val c = controller ?: return
        _progress.value = PlaybackProgress(c.currentMediaItem?.mediaId, c.currentPosition.coerceAtLeast(0), c.duration.coerceAtLeast(0))
    }
    private fun updatePlayer() {
        val c = controller ?: return
        _playback.value = PlaybackUi(true, c.currentMediaItem?.mediaId, c.isPlaying, 0, c.duration.coerceAtLeast(0), (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }, c.currentMediaItemIndex.coerceAtLeast(0), c.repeatMode, c.currentMediaItem?.mediaMetadata?.extras?.getString(SOURCE_PLAYLIST))
        updateProgress()
    }
    private fun item(track: Track, source: String?): MediaItem = MediaItem.Builder().setMediaId(track.id).setUri(track.id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album)
            .setArtworkUri(android.net.Uri.parse(track.artwork)).setExtras(Bundle().apply { putLong("duration_ms", track.durationMs); source?.let { putString(SOURCE_PLAYLIST, it) } }).build()).build()

    private fun eligible(tracks: List<Track>): List<Track> {
        val live = playableTracks(repository.library.value.tracks, repository.data.value.hidden).associateBy { it.id }
        return tracks.mapNotNull { live[it.id] }
    }
    fun play(tracks: List<Track>, selected: Track? = null, shuffled: Boolean = false, sourcePlaylist: String? = null) {
        val c = controller ?: return message("Initialisation du lecteur…")
        val list = eligible(tracks).let { if (shuffled) it.shuffled() else it }
        if (list.isEmpty()) return message("Aucun morceau disponible dans cette sélection.")
        c.shuffleModeEnabled = false
        val index = list.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
        c.setMediaItems(list.map { item(it, sourcePlaylist) }, index, 0)
        c.prepare(); c.play(); updatePlayer()
        _playerOpenRequests.value = PlayerOpenRequest(java.util.UUID.randomUUID().toString(), list[index].id)
    }
    fun next(tracks: List<Track>) {
        val c = controller ?: return
        val incoming = eligible(tracks).distinctBy { it.id }
        if (incoming.isEmpty()) return
        if (c.mediaItemCount == 0) {
            c.setMediaItems(incoming.map { item(it, null) }); c.prepare(); c.pause()
        } else {
            val insertAt = c.currentMediaItemIndex + 1
            val ids = incoming.map { it.id }.toSet()
            val source = c.currentMediaItem?.mediaMetadata?.extras?.getString(SOURCE_PLAYLIST)
            for (i in c.mediaItemCount - 1 downTo insertAt) if (c.getMediaItemAt(i).mediaId in ids) c.removeMediaItem(i)
            c.addMediaItems(insertAt, incoming.map { item(it, source) })
        }
        updatePlayer(); message("${incoming.size} morceau(x) programmé(s) ensuite")
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition(); it.prepare(); it.play() } }; updatePlayer() }
    fun previous() { controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0) }; updatePlayer() }
    fun forward() { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }; updatePlayer() }
    fun seek(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0)); updatePlayer() }
    fun jumpTo(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.play() } }; updatePlayer() }
    /** Réordonne la file réellement jouée. Le morceau en cours reste le même. */
    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from == to || from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount) return
        c.moveMediaItem(from, to)
        updatePlayer()
    }
    fun cycleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }; updatePlayer() }
    fun stop() { controller?.let { it.stop(); it.clearMediaItems() }; _playerOpenRequests.value = PlayerOpenRequest(); updatePlayer() }
    fun favorite(track: Track) { viewModelScope.launch { repository.mutate { setFlag(track.id, "favorite", track.id !in repository.data.value.favorites) } } }
    fun hide(tracks: List<Track>) { viewModelScope.launch { repository.mutate { tracks.forEach { setFlag(it.id, "hidden", true) } }; message("Masqué dans toute l’application · fichier conservé") } }
    fun unhide(track: Track) { viewModelScope.launch { repository.mutate { setFlag(track.id, "hidden", false) } } }
    private fun persist(success: String, operation: suspend () -> Unit) {
        viewModelScope.launch {
            try { operation(); if (success.isNotBlank()) message(success) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { message("La sauvegarde locale a échoué. Vos fichiers musicaux sont intacts ; vérifiez l’espace disponible et réessayez.") }
        }
    }
    /** Enregistre les règles de découpage : le catalogue se recalcule immédiatement. */
    fun saveSplitRules(rules: SplitRules) { persist("Découpage mis à jour : ${rules.protectedNames.size} noms protégés, ${rules.separators.size} séparateurs") {
        repository.mutate { saveSplitRules(rules) }
    } }
    fun saveLyrics(id: String, lyrics: SavedLyrics) { persist("Paroles sauvegardées dans NovaListen, hors ligne") { repository.mutate { saveLyrics(id, lyrics) } } }
    fun saveCover(id: String, cover: SavedCover?) { persist(if (cover == null) "Pochette du fichier rétablie dans l’affichage" else "Pochette d’affichage sauvegardée dans NovaListen, pas dans le fichier audio") { repository.mutate { saveCover(id, cover) } } }
    fun saveIdentity(id: String, mbid: String?) { persist("") { repository.mutate { saveIdentity(id, mbid) } } }
    fun clearRemotePreviews() { viewModelScope.launch { remoteImages.clearPreviews() } }
    fun artistNoteFor(id: String): ArtistNote {
        val local = repository.data.value
        val group = repository.catalog.value.artists.find { it.id == id }
        return group?.let { artistNote(it, local) } ?: local.artists[id] ?: ArtistNote()
    }
    fun saveArtistBiography(id: String, biography: String, source: String) { persist("Biographie choisie sauvegardée hors ligne") {
        val seed = artistNoteFor(id)
        repository.mutate { saveArtistBiography(id, biography, source, seed) }
    } }
    fun saveArtistPortrait(id: String, portrait: String?, source: String) { persist("Portrait choisi sauvegardé hors ligne") {
        val seed = artistNoteFor(id)
        repository.mutate { saveArtistPortrait(id, portrait, source, seed) }
    } }
    fun allowMissingArtistFields(id: String) { persist("Les champs vides de cette fiche peuvent être complétés automatiquement, selon vos réglages") {
        val seed = artistNoteFor(id)
        repository.mutate { allowMissingArtistFields(id, seed) }
    } }
    fun correctArtists(track: String, names: List<String>?) { persist("Crédits corrigés dans NovaListen · fichiers inchangés") {
        repository.mutate { saveArtistCredits(track, names?.filter(String::isNotBlank)?.map { ArtistCredit(it.substringBefore('|').trim(), it.substringAfter('|', "").trim()) }) }
    } }
    fun groupEditions(editions: Set<String>, original: String, done: (String) -> Unit) {
        val id = "family:${java.util.UUID.randomUUID()}"
        persist("Éditions regroupées · fichiers et versions conservés") {
            repository.mutate {
                val db = writableDatabase; db.beginTransaction()
                try {
                    snapshot().catalog.families.values.filter { family -> family.editions.any(editions::contains) }.forEach { family ->
                        require(editions.containsAll(family.editions)); removeAlbumFamily(family.id)
                    }
                    saveAlbumFamily(AlbumFamily(id, editions, original)); db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            done(id)
        }
    }
    fun chooseEdition(group: AlbumGroup, edition: String, original: Boolean = false) { persist("Édition représentative enregistrée dans NovaListen") {
        repository.mutate { saveAlbumFamily(AlbumFamily(group.id, group.editions.map { it.id }.toSet(), if (original) edition else group.originalId, edition)) }
    } }
    fun separateEditions(group: AlbumGroup) { persist("Regroupement annulé · toutes les éditions sont conservées") {
        repository.mutate {
            val db = writableDatabase; db.beginTransaction()
            try { group.familyId?.let(::removeAlbumFamily); group.editions.forEach { separateEdition(it.id) }; db.setTransactionSuccessful() } finally { db.endTransaction() }
        }
    } }
    fun classifyRelease(id: String, kind: ReleaseKind?) { persist("Type de sortie enregistré localement") { repository.mutate { saveReleaseKind(id, kind) } } }
    fun keepRecordingsDistinct(ids: Set<String>) { persist("Variantes conservées séparément") {
        repository.mutate { ids.forEach { id -> saveRecordingAssociation(setOf(id), "distinct:${java.util.UUID.randomUUID()}") } }
    } }
    fun associateRecordings(ids: Set<String>, together: Boolean = true) { persist("Association d’enregistrement sauvegardée · aucun fichier supprimé") {
        repository.mutate { saveRecordingAssociation(ids, if (together) java.util.UUID.randomUUID().toString() else null) }
    } }
    fun createPlaylist(name: String, tracks: List<Track> = emptyList(), done: (LocalPlaylist) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch { done(repository.createPlaylist(name.take(100), eligible(tracks).map { it.id })) }
    }
    fun addToPlaylists(destinations: Set<String>, tracks: List<Track>) {
        viewModelScope.launch {
            val ignored = repository.addToPlaylists(destinations, eligible(tracks).map { it.id })
            message("Ajout enregistré${if (ignored > 0) " · $ignored ajout(s) déjà présent(s), ignoré(s)" else ""}")
        }
    }
    fun savePlaylist(playlist: LocalPlaylist) { viewModelScope.launch { repository.mutate { savePlaylist(playlist) } } }
    fun deletePlaylist(playlist: LocalPlaylist, done: () -> Unit = {}) {
        if (playback.value.sourcePlaylist == playlist.id) stop()
        viewModelScope.launch { repository.mutate { deletePlaylist(playlist.id) }; done() }
    }
    override fun onCleared() { controller?.removeListener(listener); MediaController.releaseFuture(controllerFuture); super.onCleared() }
}
