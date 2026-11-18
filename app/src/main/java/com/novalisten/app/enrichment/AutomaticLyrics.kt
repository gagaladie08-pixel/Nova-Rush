package com.novalisten.app.enrichment

import com.novalisten.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.abs

interface LyricLookup {
    suspend fun availableLyricProviders(): List<Provider>
    suspend fun lyrics(provider: Provider, input: MusicQuery): List<LyricCandidate>
    suspend fun loadLyrics(candidate: LyricCandidate): LyricCandidate
}

data class LyricPlayback(val trackId: String, val query: MusicQuery, val playing: Boolean)
enum class AutoLyricStatus { IDLE, LOADING, READY, SAVED, DISABLED, NO_MATCH, UNAVAILABLE }
data class AutoLyricState(val trackId: String = "", val status: AutoLyricStatus = AutoLyricStatus.IDLE, val lyrics: SavedLyrics? = null,
                          val note: String = "", val diagnostics: List<String> = emptyList(), val quality: Int = 0)

/** Conservative metadata check, not audio fingerprinting. A rejected match remains selectable manually. */
object LyricMatch {
    private fun key(text: String): String = text.normalized().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    fun queryAllowed(q: MusicQuery): Boolean = runCatching {
        q.checked()
        key(q.title).isNotEmpty() && key(q.artist).isNotEmpty() && q.durationMs in MIN_TRACK_DURATION_MS..86_400_000 &&
            key(q.artist) !in setOf("artiste inconnu", "unknown artist", "unknown", "inconnu")
    }.getOrDefault(false)
    fun accepts(q: MusicQuery, result: LyricCandidate): Boolean = queryAllowed(q) && result.hasMatchMetadata && result.durationMs > 0 &&
        abs(result.durationMs - q.durationMs) <= 3000 && key(q.title) == key(result.title) && key(q.artist) == key(result.artist)
    fun quality(candidate: LyricCandidate): Int {
        val saved = candidate.asSavedLyrics()
        val lines = LyricTiming.decode(saved.original, saved.originalTiming)
        return when { lines.any { it.words.isNotEmpty() } -> 3; lines.isNotEmpty() -> 2; else -> 1 }
    }
}

/** Current-track only. Memory cache, cancellation, no polling/retry, no write over a user's saved VO/VF. */
class AutomaticLyrics(private val scope: CoroutineScope, private val lookup: LyricLookup, private val now: () -> Long = System::currentTimeMillis) {
    private data class Key(val id: String, val query: MusicQuery)
    private data class Cached(val state: AutoLyricState, val until: Long)
    private val cache = java.util.LinkedHashMap<Key, Cached>(12, .75f, true)
    private val _state = MutableStateFlow(AutoLyricState())
    val state = _state.asStateFlow()
    private var current: Key? = null
    private var job: Job? = null
    private var generation = 0
    private var bestQuality = 0

    fun onPlayback(request: LyricPlayback?, enabled: Boolean, saved: SavedLyrics?, localDataReady: Boolean = true) {
        val key = request?.let { Key(it.trackId, it.query) }
        if (key != current) { cancel(); current = key; _state.value = AutoLyricState(trackId = request?.trackId.orEmpty()); bestQuality = 0 }
        if (request == null) return
        if (!localDataReady) return // never race the initial SQLite snapshot and mistake a saved choice for absence
        if (!saved?.original.isNullOrBlank()) {
            cancel(); _state.value = AutoLyricState(request.trackId, AutoLyricStatus.SAVED); return
        }
        val cached = cache[key]?.takeIf { it.until > now() }
        if (!enabled) {
            cancel()
            val displayed = _state.value.takeIf { it.trackId == request.trackId && it.lyrics != null }
            _state.value = cached?.state ?: displayed ?: AutoLyricState(request.trackId, AutoLyricStatus.DISABLED, note = "Chargement automatique désactivé.")
            return
        }
        if (job?.isActive == true) return
        if (cached != null) { _state.value = cached.state; return }
        if (!request.playing) return // a prepared queue or an opened lyrics page is not an instruction to send metadata
        if (!LyricMatch.queryAllowed(request.query)) {
            _state.value = AutoLyricState(request.trackId, AutoLyricStatus.NO_MATCH, note = "Métadonnées insuffisantes pour un choix automatique. Utilisez les trois points pour chercher une version.")
            return
        }
        val token = ++generation
        bestQuality = 0
        _state.value = AutoLyricState(request.trackId, AutoLyricStatus.LOADING, note = "Chargement des paroles…")
        job = scope.launch {
            val diagnostics = mutableListOf<String>()
            var failed = 0
            try {
                kotlinx.coroutines.delay(500) // fast skips cancel before any metadata leaves the phone
                val providers = lookup.availableLyricProviders().distinct().filter { it in setOf(Provider.LRCRED, Provider.LRCLIB, Provider.LIRIQO) }.take(3)
                val slots = Semaphore(2)
                coroutineScope {
                    providers.forEach { provider -> launch {
                        slots.withPermit {
                            try {
                                val results = lookup.lyrics(provider, request.query).filter { LyricMatch.accepts(request.query, it) }
                                val candidate = results.maxWithOrNull(compareBy<LyricCandidate> { LyricMatch.quality(it) }.thenBy { if (it.album.normalized() == request.query.album.normalized()) 1 else 0 })
                                if (candidate != null) {
                                    val loaded = lookup.loadLyrics(candidate)
                                    val quality = LyricMatch.quality(loaded)
                                    if (token == generation && quality > bestQuality && loaded.text.isNotBlank()) {
                                        bestQuality = quality
                                        _state.value = AutoLyricState(request.trackId, AutoLyricStatus.READY, loaded.asSavedLyrics(), "Version chargée automatiquement ; une autre version reste disponible dans les trois points.", diagnostics.toList(), quality)
                                    }
                                }
                                diagnostics += "${provider.label} : ${results.size} correspondance(s) prudente(s)."
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (e: Exception) { failed++; diagnostics += "${provider.label} : ${e.publicMessage()}" }
                        }
                    } }
                }
                if (token != generation) return@launch
                val previous = _state.value
                val completed = if (previous.lyrics != null) previous.copy(diagnostics = diagnostics.toList()) else AutoLyricState(request.trackId,
                    if (failed > 0) AutoLyricStatus.UNAVAILABLE else AutoLyricStatus.NO_MATCH,
                    note = if (failed > 0) "Sources indisponibles ou limitées. Réessayez depuis les trois points ; la lecture reste locale." else "Aucune version suffisamment sûre. Les trois points permettent de chercher ou d’importer vos paroles.", diagnostics = diagnostics.toList())
                _state.value = completed
                cacheResult(key!!, completed, if (completed.lyrics != null) 3_600_000 else if (failed > 0) 120_000 else 600_000)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                if (token == generation) {
                    _state.value = AutoLyricState(request.trackId, AutoLyricStatus.UNAVAILABLE, note = e.publicMessage())
                    cacheResult(key!!, _state.value, 120_000)
                }
            }
        }
    }
    private fun cacheResult(key: Key, state: AutoLyricState, ttlMs: Long) {
        cache[key] = Cached(state, now() + ttlMs)
        while (cache.size > 8 || cache.values.sumOf { it.state.lyrics?.let { text -> text.original.length + text.originalTiming.length } ?: 0 } > 3_000_000) cache.remove(cache.keys.first())
    }
    private fun cancel() { generation++; job?.cancel(); job = null }
    fun stop() { cancel(); current = null; _state.value = AutoLyricState() }
}

fun resolveLyrics(id: String, local: SavedLyrics?, automatic: AutoLyricState): SavedLyrics =
    local?.takeIf { it.original.isNotBlank() } ?: automatic.lyrics?.takeIf { automatic.trackId == id } ?: local ?: SavedLyrics()
