package com.novalisten.app.enrichment

import com.novalisten.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ArtistLookup {
    suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult
}
data class ArtistRequest(val id: String, val name: String, val mbid: String = "", val context: ArtistLocalContext? = null)
data class ArtistResult(val biography: Biography? = null, val portrait: ImageCandidate? = null,
                        val proposals: List<ImageCandidate> = emptyList(), val note: String = "", val identityVerified: Boolean = false, val verifiedMbid: String = "")
data class ArtistAutomation(val biography: Boolean = true, val portrait: Boolean = true)
data class ArtistPlayback(val trackId: String, val artists: List<ArtistRequest>, val playing: Boolean)
data class AutoArtistState(val loading: Boolean = false, val note: String = "", val proposals: List<PreparedPhoto> = emptyList(), val verifiedMbid: String = "")

/** Current listening only; bounded session cache, debounce, cancellation and transactional manual priority. */
class AutomaticArtists(private val scope: CoroutineScope, private val lookup: ArtistLookup,
                       private val prepare: suspend (List<ImageCandidate>) -> List<PreparedPhoto>,
                       private val saveImage: suspend (ImageCandidate) -> String,
                       private val discardImage: suspend (String) -> Unit,
                       private val persist: suspend (String, ArtistRequest, ArtistNote) -> Boolean,
                       private val now: () -> Long = System::currentTimeMillis,
                       private val discoverPortraits: suspend (ArtistRequest, String) -> PortraitBatch? = { _, _ -> null }) {
    private data class CacheKey(val request: ArtistRequest, val bio: Boolean, val photo: Boolean)
    private val cache = linkedMapOf<CacheKey, Long>()
    private val _state = MutableStateFlow<Map<String, AutoArtistState>>(emptyMap())
    val state = _state.asStateFlow()
    private var current = ""
    private var job: Job? = null
    private var generation = 0

    fun onPlayback(playback: ArtistPlayback?, settings: ArtistAutomation, notes: Map<String, ArtistNote>, ready: Boolean) {
        val key = playback?.takeIf { it.playing }?.let { "${it.trackId}|${it.artists}|$settings" }.orEmpty()
        if (key != current) { generation++; job?.cancel(); job = null; current = key; _state.value = _state.value.mapValues { it.value.copy(loading = false) } }
        if (key.isEmpty() || !ready || (!settings.biography && !settings.portrait) || job?.isActive == true) return
        val needed = playback!!.artists.distinctBy { it.id }.take(16).mapNotNull { request ->
            val saved = notes[request.id] ?: ArtistNote()
            val bio = settings.biography && !saved.biographyLocked && saved.biography.isBlank()
            val photo = settings.portrait && !saved.portraitLocked && saved.portrait == null
            CacheKey(request, bio, photo).takeIf { (bio || photo) && (cache[it] ?: 0) <= now() }
        }
        if (needed.isEmpty()) return
        val token = ++generation
        job = scope.launch {
            delay(750)
            for (wanted in needed) {
                if (token != generation) break
                val request = wanted.request
                publish(request.id, AutoArtistState(loading = true, note = "Recherche automatique : identité, biographie FR et portrait admissible…"))
                var pendingImage: String? = null
                try {
                    withTimeout(45_000) {
                        val result = lookup.resolve(request, wanted.bio, wanted.photo)
                        ensureActive()
                        if (!result.identityVerified || token != generation) {
                            publish(request.id, AutoArtistState(note = result.note.ifBlank { "Identité ambiguë : aucun contenu attribué automatiquement." }))
                            cache[wanted] = now() + 600_000
                            return@withTimeout
                        }
                        var prepared = emptyList<PreparedPhoto>()
                        var selected: ImageCandidate? = null
                        var photoNotice = ""
                        if (wanted.photo) try {
                            val images = listOfNotNull(result.portrait).ifEmpty { result.proposals }
                            prepared = if (images.isNotEmpty()) prepare(images) else emptyList()
                            ensureActive()
                            selected = result.portrait?.takeIf { candidate -> prepared.any { it.image.id == candidate.id } && automaticPortraitAllowed(candidate, candidate.title) }
                            if (selected != null) pendingImage = saveImage(selected)
                            else if (validMusicId(result.verifiedMbid)) {
                                val batch = discoverPortraits(request, result.verifiedMbid)
                                if (batch != null) prepared = batch.photos
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { photoNotice = "\nPortrait : ${error.publicMessage()}" }
                        ensureActive()
                        if (token != generation) return@withTimeout
                        val bio = result.biography?.takeIf { wanted.bio && it.language == "fr" }
                        val note = ArtistNote(bio?.text.orEmpty(), bio?.credit?.text().orEmpty(), pendingImage,
                            selected?.let { it.credit.text() + "\n" + it.date + "\nPortrait lié à l’identité vérifiée ; copie JPEG d’affichage NovaListen." }.orEmpty())
                        withContext(NonCancellable) {
                            if (token == generation) {
                                val imageUsed = if (note.biography.isNotBlank() || note.portrait != null) persist(playback.trackId, request, note) else false
                                if (imageUsed) pendingImage = null
                            }
                        }
                        if (token == generation) publish(request.id, AutoArtistState(note = result.note + photoNotice, proposals = prepared, verifiedMbid = result.verifiedMbid))
                        cache[wanted] = now() + 600_000
                    }
                } catch (_: TimeoutCancellationException) {
                    if (token == generation) publish(request.id, AutoArtistState(note = "Recherche automatique bornée interrompue. Les choix locaux restent disponibles ; les recherches manuelles restent accessibles."))
                    cache[wanted] = now() + 120_000
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (token == generation) publish(request.id, AutoArtistState(note = error.publicMessage()))
                    cache[wanted] = now() + 120_000
                } finally {
                    pendingImage?.let { image -> withContext(NonCancellable) { discardImage(image) } }
                }
                while (cache.size > 32) cache.remove(cache.keys.first())
            }
        }
    }
    private fun publish(id: String, state: AutoArtistState) {
        val copy = _state.value.toMutableMap().apply { remove(id); put(id, state) }
        while (copy.size > 8) copy.remove(copy.keys.first())
        _state.value = copy
    }
    fun stop() { generation++; job?.cancel(); job = null; current = "" }
}
