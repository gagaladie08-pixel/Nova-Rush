package com.novalisten.app.enrichment

import com.novalisten.app.data.artistNameKey
import com.novalisten.app.data.validMusicId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Order is part of the validated design; admission is NOT granted by possessing a token. */
enum class PortraitSource(val label: String, val wave: Int) {
    GENIUS("Genius", 1), AUDIODB("TheAudioDB", 1), COMMONS("Wikimedia Commons", 2),
    OPENVERSE("Openverse", 2), DISCOGS("Discogs Artist", 3), FANART("fanart.tv", 4),
}
data class PhotoSubject(val name: String, val mbid: String = "", val qid: String = "") {
    val key: String get() = when {
        validMusicId(mbid) -> "mbid:${mbid.lowercase(java.util.Locale.ROOT)}"
        qid.matches(Regex("Q[1-9][0-9]*")) -> "qid:$qid"
        else -> "name:${name.artistNameKey()}"
    }
}
data class PortraitAdmission(val allowed: Boolean, val reason: String = "")
interface PortraitCatalog {
    fun admission(source: PortraitSource, subject: PhotoSubject): PortraitAdmission
    suspend fun candidates(source: PortraitSource, subject: PhotoSubject, page: Int): List<ImageCandidate>
}
enum class PortraitStatus { WAITING, SKIPPED, RESULTS, EMPTY, FAILED }
data class PortraitReport(val source: PortraitSource, val status: PortraitStatus, val detail: String)
data class PortraitBatch(val subject: PhotoSubject, val photos: List<PreparedPhoto> = emptyList(), val candidates: List<ImageCandidate> = emptyList(),
                         val reports: List<PortraitReport> = emptyList(), val pages: Map<PortraitSource, Int> = emptyMap(),
                         val previews: Int = 0, val complete: Boolean = false)

/** Conditional branches remain registered, with reasons, rather than probing disallowed APIs. */
class RemotePortraitCatalog(private val remote: RemoteSources) : PortraitCatalog {
    override fun admission(source: PortraitSource, subject: PhotoSubject): PortraitAdmission = when (source) {
        PortraitSource.COMMONS, PortraitSource.OPENVERSE -> PortraitAdmission(true)
        PortraitSource.GENIUS -> PortraitAdmission(false, "Accès et conservation des portraits non établis ; un Client Access Token ne suffit pas.")
        PortraitSource.AUDIODB -> PortraitAdmission(false, "Gratuité de NovaListen et conservation non établies ; aucune clé de test ni offre payante utilisée.")
        PortraitSource.DISCOGS -> PortraitAdmission(false, "Images Artist en Restricted Data : admission de conservation/fraîcheur non établie.")
        PortraitSource.FANART -> PortraitAdmission(false, "Dernière vague seulement si les droits des portraits deviennent admissibles. Ni clé, likes ni MBID ne fournissent une licence de conservation.")
    }
    override suspend fun candidates(source: PortraitSource, subject: PhotoSubject, page: Int): List<ImageCandidate> {
        check(admission(source, subject).allowed) { "Source de portraits non admise." }
        return when (source) {
            PortraitSource.COMMONS -> remote.commons(subject.name, page)
            PortraitSource.OPENVERSE -> remote.openverse(subject.name, page)
            else -> emptyList() // No hidden/paid/unlicensed adapter is activated by configuration alone.
        }
    }
}

/** Small shared working cache, never a durable portrait assignment. Selection is handled separately. */
class PortraitCascade(private val scope: CoroutineScope, private val catalog: PortraitCatalog,
                      private val prepare: suspend (ImageCandidate) -> PreparedPhoto,
                      private val previewAvailable: (PreparedPhoto) -> Boolean = { true },
                      private val now: () -> Long = System::currentTimeMillis) {
    private data class Cached(val value: PortraitBatch, val until: Long)
    private data class Shared(val progress: MutableStateFlow<PortraitBatch>, val work: Deferred<PortraitBatch>, var observers: Int = 0)
    private val mutex = Mutex()
    private val cache = linkedMapOf<String, Cached>()
    private val active = mutableMapOf<String, Shared>()

    fun admissions(subject: PhotoSubject): List<PortraitReport> = PortraitSource.entries.map { source ->
        val admission = catalog.admission(source, subject)
        PortraitReport(source, if (admission.allowed) PortraitStatus.WAITING else PortraitStatus.SKIPPED, admission.reason)
    }

    suspend fun initial(subject: PhotoSubject, onProgress: (PortraitBatch) -> Unit = {}): PortraitBatch {
        require(subject.name.isNotBlank() && subject.name.length <= 300 && subject.name.none(Char::isISOControl))
        val shared = mutex.withLock {
            val saved = cache[subject.key]?.takeIf { it.until > now() }?.value
            val reusable = saved?.takeIf { it.photos.all(previewAvailable) }
            if (reusable != null) { onProgress(reusable); return reusable }
            active[subject.key]?.also { it.observers++ } ?: run {
                val initial = saved?.copy(photos = saved.photos.filter(previewAvailable), complete = false)
                    ?: PortraitBatch(subject, reports = admissions(subject))
                val progress = MutableStateFlow(initial)
                val work = scope.async(start = CoroutineStart.LAZY) {
                    val result = discover(initial) { progress.value = it }
                    mutex.withLock {
                        val hasFailure = result.reports.any { it.status == PortraitStatus.FAILED }
                        cache.remove(subject.key)
                        cache[subject.key] = Cached(result, now() + if (hasFailure) 120_000 else 600_000)
                        while (cache.size > 16) cache.remove(cache.keys.first())
                    }
                    result
                }
                Shared(progress, work, 1).also { active[subject.key] = it; work.start() }
            }
        }
        return coroutineScope {
            val observer = launch { shared.progress.collect { onProgress(it) } }
            try { shared.work.await().also(onProgress) }
            finally {
                observer.cancel()
                withContext(NonCancellable) { mutex.withLock {
                    shared.observers--
                    if (shared.observers == 0) {
                        if (shared.work.isActive) shared.work.cancel()
                        if (active[subject.key] === shared) active.remove(subject.key)
                    }
                } }
            }
        }
    }

    private suspend fun discover(initial: PortraitBatch, publish: (PortraitBatch) -> Unit): PortraitBatch {
        var batch = initial.copy(complete = false, previews = 0)
        publish(batch)
        // Restore missing cached previews from already-admitted metadata without rediscovering sources.
        suspend fun inspect(found: List<ImageCandidate>) {
            if (batch.photos.size >= 6 || batch.previews >= 12) return
            val before = batch.previews
            val result = PhotoDiscovery(prepare).discover(found.filter(::usefulPhoto), batch.photos, 6, 12 - before,
                onPhotos = { photos -> batch = batch.copy(photos = photos); publish(batch) })
            batch = batch.copy(photos = result.photos, previews = before + result.attempted)
            publish(batch)
        }
        inspect(initial.candidates)
        for (wave in 1..4) {
            if (batch.photos.size >= 6 || batch.previews >= 12) break
            val eligible = PortraitSource.entries.filter { it.wave == wave && catalog.admission(it, batch.subject).allowed && it !in batch.pages }
            val replies = coroutineScope {
                eligible.map { source -> async {
                    try { Triple(source, catalog.candidates(source, batch.subject, 1).take(24), null) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { Triple(source, emptyList(), error.publicMessage()) }
                } }.map { it.await() }
            }
            val found = replies.flatMap { it.second }
            replies.forEach { (source, images, error) ->
                val status = when { error != null -> PortraitStatus.FAILED; images.isEmpty() -> PortraitStatus.EMPTY; else -> PortraitStatus.RESULTS }
                val report = PortraitReport(source, status, error ?: if (images.isEmpty()) "Aucune proposition dans cette page." else "${images.size} proposition(s) ; sujet, licence et date restent à vérifier.")
                batch = batch.copy(reports = batch.reports.map { if (it.source == source) report else it }, pages = batch.pages + (source to 1))
            }
            batch = batch.copy(candidates = (batch.candidates + found).distinctBy { imageIdentity(it.original) }.take(120))
            publish(batch)
            inspect(found)
        }
        return batch.copy(complete = true).also(publish)
    }

    /** One voluntary page only. Does not restart all waves or silently paginate other providers. */
    suspend fun more(previous: PortraitBatch, source: PortraitSource, onProgress: (PortraitBatch) -> Unit = {}): PortraitBatch {
        require(catalog.admission(source, previous.subject).allowed) { "Cette source de portraits n’est pas admise." }
        val page = (previous.pages[source] ?: 0) + 1
        require(page in 1..5 && previous.photos.size < 60)
        val found = catalog.candidates(source, previous.subject, page).take(24)
        val candidates = (previous.candidates + found).distinctBy { imageIdentity(it.original) }.take(120)
        val result = PhotoDiscovery(prepare).discover(found.filter(::usefulPhoto), previous.photos, minOf(previous.photos.size + 6, 60),
            onPhotos = { onProgress(previous.copy(photos = it, candidates = candidates)) })
        val report = PortraitReport(source, if (found.isEmpty()) PortraitStatus.EMPTY else PortraitStatus.RESULTS, "Page $page : ${found.size} proposition(s).")
        val batch = previous.copy(photos = result.photos, candidates = candidates, reports = previous.reports.filterNot { it.source == source } + report,
            pages = previous.pages + (source to page), previews = result.attempted, complete = true)
        mutex.withLock { cache[batch.subject.key] = Cached(batch, now() + 600_000) }
        onProgress(batch)
        return batch
    }
}

private fun usefulPhoto(image: ImageCandidate): Boolean = !Regex("(?i)(?:^|[ _./-])(logo|signature|albumcover|coverart|placeholder|noimage|no-image|flag)(?:[ _./-]|$)").containsMatchIn(image.title)
