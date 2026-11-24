package com.novalisten.app.enrichment

import com.novalisten.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ArtistLookup {
    suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult
    /** Adapters can commit FR text before a slow photo completes. Legacy/test adapters emit once. */
    suspend fun progressively(request: ArtistRequest, biography: Boolean, portrait: Boolean, emit: suspend (ArtistResult) -> Unit) {
        emit(resolve(request, biography, portrait))
    }
}
data class ArtistRequest(val id: String, val name: String, val mbid: String = "", val context: ArtistLocalContext? = null)
data class ArtistResult(val biography: Biography? = null, val portrait: ImageCandidate? = null,
                        val proposals: List<ImageCandidate> = emptyList(), val note: String = "", val identityVerified: Boolean = false, val verifiedMbid: String = "", val portraitComplete: Boolean = true)
data class ArtistAutomation(val biography: Boolean = true, val portrait: Boolean = true)
data class ArtistPlayback(val trackId: String, val artists: List<ArtistRequest>, val playing: Boolean)
data class ArtistWork(val trackId: String, val artist: ArtistRequest)
data class AutoArtistState(val loading: Boolean = false, val note: String = "", val proposals: List<PreparedPhoto> = emptyList(), val verifiedMbid: String = "")

/** One progressive library worker, shared with playback. Network concurrency is bounded globally by RemoteHttp. */
class AutomaticArtists(private val scope: CoroutineScope, private val lookup: ArtistLookup,
                       private val prepare: suspend (List<ImageCandidate>) -> List<PreparedPhoto>,
                       private val saveImage: suspend (ImageCandidate) -> String,
                       private val discardImage: suspend (String) -> Unit,
                       private val persist: suspend (String, ArtistRequest, ArtistNote) -> Boolean,
                       private val now: () -> Long = System::currentTimeMillis,
                       private val discoverPortraits: suspend (ArtistRequest, String) -> PortraitBatch? = { _, _ -> null },
                       private val resolveCredits: suspend (ArtistWork) -> Boolean = { false }) {
    private data class CacheKey(val request: ArtistRequest, val bio: Boolean, val photo: Boolean)
    private val cache = linkedMapOf<CacheKey, Long>()
    private val _state = MutableStateFlow<Map<String, AutoArtistState>>(emptyMap())
    val state = _state.asStateFlow()
    private var wanted = emptyList<ArtistWork>()
    private var notes = emptyMap<String, ArtistNote>()
    private var settings = ArtistAutomation()
    private var active: ArtistWork? = null
    private var activeFields: CacheKey? = null
    private var job: Job? = null
    private var generation = 0
    private var playbackKey = ""

    private fun cacheRequest(request: ArtistRequest): ArtistRequest = if (validMusicId(request.mbid)) request.copy(context = null) else request
    private fun rememberAttempt(key: CacheKey, duration: Long) {
        val request = cacheRequest(key.request)
        if (key.bio) cache[CacheKey(request, true, false)] = now() + duration
        if (key.photo) cache[CacheKey(request, false, true)] = now() + duration
    }
    private fun needed(work: ArtistWork): CacheKey? {
        val saved = notes[work.artist.id] ?: ArtistNote()
        val identity = cacheRequest(work.artist)
        val bio = settings.biography && !saved.biographyLocked && saved.biography.isBlank() && (cache[CacheKey(identity, true, false)] ?: 0) <= now()
        val photo = settings.portrait && !saved.portraitLocked && saved.portrait == null && (cache[CacheKey(identity, false, true)] ?: 0) <= now()
        return CacheKey(work.artist, bio, photo).takeIf { bio || photo }
    }

    /** Kept for callers/tests restricted to current listening; the application uses onLibrary. */
    fun onPlayback(playback: ArtistPlayback?, settings: ArtistAutomation, notes: Map<String, ArtistNote>, ready: Boolean) {
        val key = playback?.takeIf { it.playing }?.let { "${it.trackId}|${it.artists}|$settings" }.orEmpty()
        if (key != playbackKey) { cancel(); playbackKey = key }
        onLibrary(playback?.takeIf { it.playing }?.let { p -> p.artists.map { ArtistWork(p.trackId, it) } }.orEmpty(), settings, notes, ready)
    }

    /** Sorted by the caller: playing artist, visible cards, remaining artists. No pause/queue requirement. */
    fun onLibrary(works: List<ArtistWork>, settings: ArtistAutomation, notes: Map<String, ArtistNote>, ready: Boolean,
                  priorityPlaying: Set<String> = emptySet(), priorityVisible: Set<String> = emptySet()) {
        val desired = if (ready && (settings.biography || settings.portrait)) works.distinctBy { it.artist.id } else emptyList()
        val changedSettings = this.settings != settings
        this.settings = settings; this.notes = notes
        val higherPriority = desired.firstOrNull { needed(it) != null }
        val preempt = active?.let { current ->
            current.artist.id !in priorityPlaying && higherPriority?.artist?.id in priorityPlaying ||
                (current.artist.id !in priorityPlaying && current.artist.id !in priorityVisible && higherPriority?.artist?.id in priorityVisible)
        } == true
        if (changedSettings || (active != null && active !in desired) || preempt) cancel()
        wanted = desired
        if (job?.isActive == true || desired.none { needed(it) != null }) return
        val token = ++generation
        job = scope.launch {
            delay(750)
            val attempted = mutableSetOf<CacheKey>()
            while (token == generation) {
                val next = wanted.firstNotNullOfOrNull { work -> needed(work)?.takeUnless { it in attempted }?.let { work to it } } ?: break
                val (work, fields) = next
                attempted += fields; active = work; activeFields = fields
                rememberAttempt(fields, 600_000) // mark before persistence emits another repository snapshot
                publish(work.artist.id, AutoArtistState(loading = true))
                try {
                    withTimeout(45_000) {
                        if (!resolveCredits(work)) lookup.progressively(work.artist, fields.bio, fields.photo) { result ->
                            if (token == generation && work in wanted) apply(work, fields, result, token)
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    rememberAttempt(fields, 120_000)
                    if (token == generation) publish(work.artist.id, stateFor(work).copy(note = "Recherche temporairement indisponible"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    rememberAttempt(fields, 120_000)
                    if (token == generation) publish(work.artist.id, stateFor(work).copy(note = error.publicMessage()))
                } finally {
                    if (token == generation) { publish(work.artist.id, stateFor(work).copy(loading = false)); active = null; activeFields = null }
                }
                while (cache.size > maxOf(64, wanted.size * 2).coerceAtMost(20_000)) cache.remove(cache.keys.first())
                yield()
            }
        }
    }

    private suspend fun apply(work: ArtistWork, fields: CacheKey, result: ArtistResult, token: Int) {
        if (!result.identityVerified) {
            publish(work.artist.id, AutoArtistState(loading = true, note = result.note))
            return
        }
        val request = work.artist
        var pending: String? = null
        var selected: ImageCandidate? = null
        fun photoWanted(): Boolean {
            val note = notes[work.artist.id] ?: ArtistNote()
            return fields.photo && settings.portrait && !note.portraitLocked && note.portrait == null
        }
        var photos = emptyList<PreparedPhoto>()
        var failed = false
        var notice = result.note
        try {
            if (photoWanted()) try { photos = prepare(result.proposals) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failed = true; notice += "\n${error.publicMessage()}" }
            if (photoWanted() && result.portrait != null) try {
                photos = prepare(listOf(result.portrait))
                selected = result.portrait.takeIf { candidate -> photos.any { it.image.id == candidate.id } && automaticPortraitAllowed(candidate, candidate.title) }
                if (selected != null) pending = saveImage(selected)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failed = true; notice += "\n${error.publicMessage()}" }
            ensureCurrent(token, work)
            val currentNote = notes[request.id] ?: ArtistNote()
            val bio = result.biography?.takeIf { fields.bio && settings.biography && !currentNote.biographyLocked && currentNote.biography.isBlank() && it.language == "fr" }
            val note = ArtistNote(bio?.text.orEmpty(), bio?.credit?.text().orEmpty(), pending,
                selected?.let { it.credit.text() + "\n" + it.date + "\nPortrait de l’identité vérifiée." }.orEmpty())
            // Persist independently of discovery of alternative pictures, which can be slow.
            if (note.biography.isNotBlank() || note.portrait != null) withContext(NonCancellable) {
                if (token == generation && work in wanted && persist(work.trackId, request, note)) pending = null
            }
            if (photoWanted() && result.portraitComplete && selected == null && validMusicId(result.verifiedMbid)) try {
                discoverPortraits(request, result.verifiedMbid)?.let { batch -> photos = batch.photos; failed = batch.reports.any { it.status == PortraitStatus.FAILED } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failed = true; notice += "\n${error.publicMessage()}" }
            if (failed) rememberAttempt(CacheKey(request, false, true), 120_000)
            if (token == generation) publish(request.id, AutoArtistState(true,
                (stateFor(work).note.lines() + notice.lines()).filter(String::isNotBlank).distinct().joinToString("\n"),
                photos.ifEmpty { stateFor(work).proposals }, result.verifiedMbid))
        } finally { pending?.let { withContext(NonCancellable) { discardImage(it) } } }
    }
    private fun ensureCurrent(token: Int, work: ArtistWork) { if (token != generation || work !in wanted) throw CancellationException() }
    private fun stateFor(work: ArtistWork) = _state.value[work.artist.id] ?: AutoArtistState()
    private fun publish(id: String, state: AutoArtistState) {
        val copy = _state.value.toMutableMap().apply { remove(id); put(id, state) }
        while (copy.size > 64) copy.remove(copy.keys.first())
        _state.value = copy
    }
    private fun cancel() {
        // Clear only the interrupted attempt, before a replacement job starts. A late cancelled
        // job must never erase the cache of its successor.
        activeFields?.let { fields ->
            val request = cacheRequest(fields.request)
            if (fields.bio) cache.remove(CacheKey(request, true, false))
            if (fields.photo) cache.remove(CacheKey(request, false, true))
        }
        generation++; job?.cancel(); job = null; active = null; activeFields = null
        _state.value = _state.value.mapValues { it.value.copy(loading = false) }
    }
    fun stop() { cancel(); wanted = emptyList(); playbackKey = "" }
}
