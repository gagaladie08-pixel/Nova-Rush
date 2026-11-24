package com.novalisten.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.novalisten.app.enrichment.ArtistAutomation
import com.novalisten.app.enrichment.ArtistRequest
import com.novalisten.app.enrichment.artistRequestsForTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context, private val scope: CoroutineScope,
                      private val automaticArtistPolicy: () -> ArtistAutomation = { ArtistAutomation() }) {
    private val database = NovaDatabase(context)
    private val _library = MutableStateFlow(LibraryState())
    val library = _library.asStateFlow()
    private val _data = MutableStateFlow(LocalData())
    val data = _data.asStateFlow()
    private val _localDataReady = MutableStateFlow(false)
    val localDataReady = _localDataReady.asStateFlow()
    private val tagCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, NativeCatalogTags>>()
    private var refreshJob: Job? = null
    private var observerRegistered = false
    private val dataMutex = Mutex()
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh(debounce = true) }
    }
    val catalog = combine(library.map { it.tracks to it.permission }.distinctUntilChanged(),
        data.map { it.hidden to it.catalog }.distinctUntilChanged(), localDataReady) { source, choices, localReady -> Triple(source, choices, localReady) }
        .mapLatest { (source, choices, localReady) -> withContext(Dispatchers.Default) {
            if (source.second && localReady) CatalogSnapshot.build(source.first, choices.first, choices.second) else CatalogSnapshot()
        } }
        .stateIn(scope, SharingStarted.Eagerly, CatalogSnapshot())
    private var decoratedCovers: Map<String, SavedCover>? = null
    init {
        scope.launch(Dispatchers.IO) { dataMutex.withLock { updateLocalData() } }
    }
    private fun updateLocalData() {
        _data.value = database.snapshot()
        _localDataReady.value = true
        if (decoratedCovers != _data.value.covers) {
            decoratedCovers = _data.value.covers
            _library.value = _library.value.copy(tracks = decorate(_library.value.tracks))
        }
    }
    private fun decorate(tracks: List<Track>): List<Track> = tracks.map { it.copy(displayCover = _data.value.covers[it.albumKey]?.uri) }
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED
    fun audioPermission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun refresh(debounce: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (debounce) delay(450)
            if (!hasPermission()) {
                _library.value = LibraryState(permission = false)
                return@launch
            }
            if (!observerRegistered) {
                runCatching { context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer) }
                    .onSuccess { observerRegistered = true }
            }
            _library.value = _library.value.copy(permission = true, loading = true, error = null)
            try {
                val tracks = withContext(Dispatchers.IO) { scan() }
                _library.value = LibraryState(permission = true, tracks = decorate(tracks))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                _library.value = LibraryState(permission = false, error = "L’accès à la musique a été retiré. Accordez-le dans les paramètres Android.")
            } catch (_: Exception) {
                _library.value = _library.value.copy(loading = false, error = "La bibliothèque n’a pas pu être lue. Réessayez sans modifier vos fichiers.")
            }
        }
    }
    private fun scan(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf("_id", "title", "artist", "album", "album_id", if (Build.VERSION.SDK_INT >= 30) "album_artist" else "artist", "duration", "date_added", "year", "track", "_display_name", "_size", "mime_type", "date_modified")
        val result = ArrayList<Track>()
        context.contentResolver.query(collection, columns, "is_music != 0", null, "date_added DESC, _id DESC")?.use { c ->
            while (c.moveToNext()) {
                fun text(i: Int) = if (c.isNull(i)) "" else c.getString(i).orEmpty()
                fun known(value: String, fallback: String) = if (value.isBlank() || value == "<unknown>") fallback else value
                val duration = c.getLong(6)
                if (duration in 1 until MIN_TRACK_DURATION_MS) continue
                val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                val id = uri.toString()
                val revision = "${c.getLong(11)}:${c.getLong(13)}"
                val tags = tagCache[id]?.takeIf { it.first == revision }?.second ?: runCatching {
                    context.contentResolver.openInputStream(uri)?.use(NativeCatalogTagReader::read) ?: NativeCatalogTags()
                }.getOrDefault(NativeCatalogTags()).also {
                    if (tagCache.size >= 5000) tagCache.clear()
                    tagCache[id] = revision to it
                }
                result += Track(
                    id,
                    known(text(1), text(10).substringBeforeLast('.')), known(text(2), "Artiste inconnu"),
                    known(text(3), "Album inconnu"), c.getLong(4), known(text(5), ""), duration,
                    c.getLong(7) * 1000, c.getInt(8), c.getInt(9) % 1000, text(10), c.getLong(11), text(12),
                    credits = tags.credits, releaseGroupId = tags.releaseGroupId, releaseId = tags.releaseId, releaseKind = tags.kind,
                    recordingId = tags.recordingId, isrc = tags.isrc, discNumber = tags.disc.takeIf { it > 0 } ?: (c.getInt(9) / 1000), originalYear = tags.originalYear, albumCredits = tags.albumCredits, nativeAlbumArtist = tags.albumArtist,
                )
            }
        }
        return result
    }
    suspend fun mutate(block: NovaDatabase.() -> Unit) = withContext(Dispatchers.IO) {
        dataMutex.withLock { database.block(); updateLocalData() }
    }
    suspend fun saveAutomaticLyrics(id: String, record: AutomaticLyricRecord): Boolean = withContext(Dispatchers.IO) {
        dataMutex.withLock {
            val track = _library.value.tracks.firstOrNull { it.id == id }
            if (track == null || track.lyricIdentity() != record.identity) return@withLock false
            database.saveAutomaticLyrics(id, record).also { updateLocalData() }
        }
    }
    suspend fun saveAutomaticArtist(trackId: String, request: ArtistRequest, note: ArtistNote): Boolean = withContext(Dispatchers.IO) {
        dataMutex.withLock {
            val local = _data.value
            val track = _library.value.tracks.firstOrNull { it.id == trackId && it.id !in local.hidden } ?: return@withLock false
            val index = catalog.value
            if (!index.isCurrent(track, local)) return@withLock false
            val groups = index.artistsByTrack[trackId].orEmpty()
            if (artistRequestsForTrack(track, local, groups).none { it == request }) return@withLock false
            val artistId = request.id
            val group = groups.firstOrNull { it.id == artistId } ?: return@withLock false
            val seed = artistNote(group, local)
            if (seed != (local.artists[artistId] ?: ArtistNote())) database.saveArtist(artistId, seed)
            // Re-read the live switches AFTER waiting for the mutex, not only at request start.
            val allowed = automaticArtistPolicy()
            val incoming = note.copy(biography = note.biography.takeIf { allowed.biography }.orEmpty(),
                source = note.source.takeIf { allowed.biography }.orEmpty(),
                portrait = note.portrait.takeIf { allowed.portrait }, portraitSource = note.portraitSource.takeIf { allowed.portrait }.orEmpty())
            if (incoming.biography.isBlank() && incoming.portrait == null) return@withLock false
            database.saveAutomaticArtist(artistId, incoming).also { updateLocalData() }
        }
    }
    suspend fun saveResolvedArtists(trackId: String, request: ArtistRequest, credits: List<ArtistCredit>): Boolean = withContext(Dispatchers.IO) {
        dataMutex.withLock {
            val local = _data.value
            val track = _library.value.tracks.firstOrNull { it.id == trackId && it.id !in local.hidden } ?: return@withLock false
            if (track.id in local.catalog.artists || track.credits.isNotEmpty() || request.name != CatalogText.display(track.artist) ||
                request.context != com.novalisten.app.enrichment.ArtistLocalContext(track.title, track.album, track.durationMs, track.recordingId)) return@withLock false
            val policy = automaticArtistPolicy()
            if (!policy.biography && !policy.portrait) return@withLock false
            val index = catalog.value
            if (!index.isCurrent(track, local) || artistRequestsForTrack(track, local, index.artistsByTrack[trackId].orEmpty()).none { it == request }) return@withLock false
            database.saveResolvedArtists(track, credits)
            updateLocalData()
            true
        }
    }
    suspend fun createPlaylist(name: String, ids: List<String>): LocalPlaylist = withContext(Dispatchers.IO) {
        dataMutex.withLock {
            database.createPlaylist(name, ids).also { updateLocalData() }
        }
    }
    suspend fun addToPlaylists(destinations: Set<String>, ids: List<String>): Int = withContext(Dispatchers.IO) {
        dataMutex.withLock {
            var ignored = 0
            val current = database.snapshot()
            current.playlists.filter { it.id in destinations }.forEach { playlist ->
                ignored += ids.distinct().count { it in playlist.trackIds }
                database.savePlaylist(playlist.copy(trackIds = prependUnique(playlist.trackIds, ids)))
            }
            updateLocalData()
            ignored
        }
    }
    fun recordListen(id: String) { scope.launch { mutate { addEvent(eventAt(id)) } } }
}
