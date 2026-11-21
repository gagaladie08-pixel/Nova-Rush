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
    fun lyricsFor(id: String): SavedLyrics {
        val local = repository.data.value
        val track = repository.library.value.tracks.firstOrNull { it.id == id }
        return resolveLyrics(id, local.lyrics[id], automaticLyrics.state.value, local.automaticLyrics[id]?.forTrack(track))
    }
    val remoteImages = app.remoteImages
    val translation = app.translation
    private val _playback = MutableStateFlow(PlaybackUi())
    val playback = _playback.asStateFlow()
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
        controllerFuture.addListener({
            runCatching {
                controller = controllerFuture.get().also { it.addListener(listener) }
                updatePlayer()
            }.onFailure { message("Le lecteur n’a pas pu démarrer. Fermez puis rouvrez NovaListen.") }
        }, androidx.core.content.ContextCompat.getMainExecutor(application))
        viewModelScope.launch {
            while (isActive) { updatePlayer(); delay(250) }
        }
    }
    fun message(text: String) { _messages.tryEmit(text) }
    private fun updatePlayer() {
        val c = controller ?: return
        _playback.value = PlaybackUi(true, c.currentMediaItem?.mediaId, c.isPlaying, c.currentPosition.coerceAtLeast(0), c.duration.coerceAtLeast(0), (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }, c.currentMediaItemIndex.coerceAtLeast(0), c.repeatMode, c.currentMediaItem?.mediaMetadata?.extras?.getString(SOURCE_PLAYLIST))
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
        c.setMediaItems(list.map { item(it, sourcePlaylist) }, list.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0), 0)
        c.prepare(); c.play(); updatePlayer()
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
    fun cycleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }; updatePlayer() }
    fun stop() { controller?.let { it.stop(); it.clearMediaItems() }; updatePlayer() }
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
    fun saveLyrics(id: String, lyrics: SavedLyrics) { persist("Paroles sauvegardées dans NovaListen, hors ligne") { repository.mutate { saveLyrics(id, lyrics) } } }
    fun saveCover(id: String, cover: SavedCover?) { persist(if (cover == null) "Pochette du fichier rétablie dans l’affichage" else "Pochette d’affichage sauvegardée dans NovaListen, pas dans le fichier audio") { repository.mutate { saveCover(id, cover) } } }
    fun saveIdentity(id: String, mbid: String?) { persist("") { repository.mutate { saveIdentity(id, mbid) } } }
    fun clearRemotePreviews() { viewModelScope.launch { remoteImages.clearPreviews() } }
    fun saveArtist(id: String, note: ArtistNote) { persist("") { repository.mutate { saveArtist(id, note) } } }
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
