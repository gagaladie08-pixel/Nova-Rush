package com.novalisten.app

import com.novalisten.app.enrichment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortraitCascadeTest {
    private val subject = PhotoSubject("Synthetic artist", "11111111-1111-1111-1111-111111111111")
    private fun image(index: Int) = ImageCandidate("image:$index", "Portrait $index.jpg", "https://upload.wikimedia.org/preview/$index.jpg", "https://upload.wikimedia.org/original/$index.jpg",
        Credit("Wikimedia Commons", "https://commons.wikimedia.org/wiki/File:$index.jpg", "https://creativecommons.org/licenses/by/4.0/", "Fixture"), true)
    private class Catalog : PortraitCatalog {
        var allowed = PortraitSource.entries.toSet()
        var pause = 0L
        val images = mutableMapOf<Pair<PortraitSource, Int>, List<ImageCandidate>>()
        val failures = mutableSetOf<PortraitSource>()
        val calls = mutableListOf<Pair<PortraitSource, Int>>()
        override fun admission(source: PortraitSource, subject: PhotoSubject) = PortraitAdmission(source in allowed, "Synthetic source not admitted")
        override suspend fun candidates(source: PortraitSource, subject: PhotoSubject, page: Int): List<ImageCandidate> {
            calls += source to page
            if (pause > 0) delay(pause)
            if (source in failures) throw RemoteFailure("Synthetic quota/outage")
            return images[source to page].orEmpty()
        }
    }
    private val prepared = mutableListOf<Int>()
    private suspend fun prepare(image: ImageCandidate): PreparedPhoto {
        val id = image.id.substringAfter(':').toInt()
        prepared += id
        // Distinct aspect ratios keep the synthetic fixtures perceptually distinct without real photos.
        return PreparedPhoto(image, "file:///preview-$id.jpg", PhotoFingerprint(id.toLong(), 128, 128, 128, 1000 + id * 100))
    }

    @Test fun fourConditionalWavesHaveTheValidatedOrderAndOneMetadataPagePerProvider() = runTest {
        val catalog = Catalog()
        PortraitSource.entries.forEachIndexed { index, source -> catalog.images[source to 1] = listOf(image(index)) }
        val result = PortraitCascade(this, catalog, ::prepare).initial(subject)
        assertEquals(PortraitSource.entries.toList(), catalog.calls.map { it.first })
        assertTrue(catalog.calls.all { it.second == 1 }); assertEquals(6, result.photos.size); assertEquals(6, result.previews)
    }
    @Test fun sufficientUsefulResultsStopBeforeLaterWavesInsteadOfCollectingEverySource() = runTest {
        val catalog = Catalog().apply {
            images[PortraitSource.GENIUS to 1] = (0..2).map(::image)
            images[PortraitSource.AUDIODB to 1] = (3..5).map(::image)
        }
        val result = PortraitCascade(this, catalog, ::prepare).initial(subject)
        assertEquals(6, result.photos.size)
        assertEquals(listOf(PortraitSource.GENIUS, PortraitSource.AUDIODB), catalog.calls.map { it.first })
        assertTrue(result.reports.filter { it.source.wave > 1 }.all { it.status == PortraitStatus.WAITING })
    }
    @Test fun skippedSourcesAreNotQueriedAndDoNotBecomeFalseNoResultMessages() = runTest {
        val catalog = Catalog().apply {
            allowed = setOf(PortraitSource.COMMONS, PortraitSource.OPENVERSE)
            images[PortraitSource.COMMONS to 1] = (0..5).map(::image)
            images[PortraitSource.OPENVERSE to 1] = (6..11).map(::image)
        }
        val result = PortraitCascade(this, catalog, ::prepare).initial(subject)
        assertEquals(listOf(PortraitSource.COMMONS, PortraitSource.OPENVERSE), catalog.calls.map { it.first })
        assertEquals(6, prepared.size)
        assertTrue(result.reports.filter { it.source !in catalog.allowed }.all { it.status == PortraitStatus.SKIPPED && it.detail.contains("not admitted") })
    }
    @Test fun productionAdmissionDoesNotTreatStoredKeysAsPortraitLicencesOrCallRestrictedApis() = runTest {
        val calls = mutableListOf<Provider>()
        val transport = object : RemoteTransport {
            override suspend fun request(provider: Provider, path: String, parameters: Map<String, String>, headers: Map<String, String>, jsonBody: String?): String {
                calls += provider
                return when (provider) { Provider.COMMONS -> "{\"query\":{\"pages\":[]}}"; Provider.OPENVERSE -> "{\"results\":[]}"; else -> error("Forbidden portrait probe") }
            }
            override suspend fun image(url: String, maxBytes: Int): ByteArray = error("Empty results must not load images")
        }
        val remote = RemoteSources(transport) { error("No credential can establish image rights") }
        val result = PortraitCascade(this, RemotePortraitCatalog(remote), ::prepare).initial(subject)
        assertEquals(setOf(Provider.COMMONS, Provider.OPENVERSE), calls.toSet())
        assertEquals(4, result.reports.count { it.status == PortraitStatus.SKIPPED })
    }
    @Test fun progressivelyPreparedResultsCanBeShownBeforeTheSixthPreviewOrCompletion() = runTest {
        val catalog = Catalog().apply { images[PortraitSource.GENIUS to 1] = (0..5).map(::image) }
        val progress = mutableListOf<PortraitBatch>()
        PortraitCascade(this, catalog, ::prepare).initial(subject) { progress += it }
        assertTrue(progress.any { it.photos.size in 1..5 && !it.complete })
        assertEquals(6, progress.last().photos.size); assertTrue(progress.last().complete)
    }
    @Test fun aQuotaOrOutageIsNotCachedOrDisplayedAsARealAbsenceOfPhotos() = runTest {
        val catalog = Catalog().apply { failures += PortraitSource.COMMONS }
        val result = PortraitCascade(this, catalog, ::prepare).initial(subject)
        assertEquals(PortraitStatus.FAILED, result.reports.single { it.source == PortraitSource.COMMONS }.status)
        assertEquals(PortraitStatus.EMPTY, result.reports.single { it.source == PortraitSource.OPENVERSE }.status)
    }
    @Test fun duplicatesUnlicensedImagesAndLogosDoNotCountTowardSixUsefulPreviews() = runTest {
        val catalog = Catalog().apply {
            images[PortraitSource.GENIUS to 1] = listOf(image(0), image(0), image(1).copy(licenceKnown = false), image(2).copy(title = "Artist logo.jpg"), image(3))
        }
        val result = PortraitCascade(this, catalog, ::prepare).initial(subject)
        assertEquals(listOf(0, 3), result.photos.map { it.image.id.substringAfter(':').toInt() })
        assertEquals(listOf(0, 3), prepared)
    }
    @Test fun fewerThanSixAndNoAutomaticPaginationIsAValidCompletedResult() = runTest {
        val catalog = Catalog().apply { images[PortraitSource.COMMONS to 1] = listOf(image(0)) }
        val result = PortraitCascade(this, catalog, ::prepare).initial(subject)
        assertEquals(1, result.photos.size); assertTrue(result.complete)
        assertEquals(6, catalog.calls.size); assertTrue(catalog.calls.all { it.second == 1 })
    }
    @Test fun moreIsOneVoluntaryPageAndTheInitialSixAreNotAUniversalCatalogueCeiling() = runTest {
        val catalog = Catalog().apply {
            images[PortraitSource.COMMONS to 1] = (0..5).map(::image)
            images[PortraitSource.COMMONS to 2] = (6..11).map(::image)
        }
        val cascade = PortraitCascade(this, catalog, ::prepare)
        val first = cascade.initial(subject)
        val before = catalog.calls.size
        val more = cascade.more(first, PortraitSource.COMMONS)
        assertEquals(12, more.photos.size)
        assertEquals(listOf(PortraitSource.COMMONS to 2), catalog.calls.drop(before))
        assertEquals(2, more.pages[PortraitSource.COMMONS])
    }
    @Test fun simultaneousAutomaticAndManualSearchesShareWorkAndCancellingOneDoesNotCancelTheOther() = runTest {
        val catalog = Catalog().apply { allowed = setOf(PortraitSource.COMMONS, PortraitSource.OPENVERSE); pause = 100; images[PortraitSource.COMMONS to 1] = listOf(image(0)) }
        val cascade = PortraitCascade(this, catalog, ::prepare)
        val first = async { cascade.initial(subject) }
        val second = async { cascade.initial(subject) }
        advanceTimeBy(50); first.cancel()
        assertEquals(1, second.await().photos.size)
        assertEquals(2, catalog.calls.size)
        assertEquals(1, cascade.initial(subject).photos.size)
        assertEquals(2, catalog.calls.size)
    }
    @Test fun differentHomonymIdentifiersDoNotReuseTheSameGallery() = runTest {
        val catalog = Catalog()
        val cascade = PortraitCascade(this, catalog, ::prepare)
        cascade.initial(subject)
        cascade.initial(subject.copy(mbid = "22222222-2222-2222-2222-222222222222"))
        assertEquals(12, catalog.calls.size)
    }
    @Test fun missingCachedPreviewsAreRestoredWithoutRepeatingMetadataDiscovery() = runTest {
        val catalog = Catalog().apply { images[PortraitSource.COMMONS to 1] = listOf(image(0)) }
        var available = true
        val cascade = PortraitCascade(this, catalog, ::prepare, { available })
        cascade.initial(subject); available = false
        cascade.initial(subject)
        assertEquals(6, catalog.calls.size); assertEquals(2, prepared.size)
    }
    @Test fun failedDiscoveryDoesNotSkipTheUnreceivedFirstPageWhenTheUserRetries() = runTest {
        val catalog = Catalog().apply { failures += PortraitSource.COMMONS }
        val cascade = PortraitCascade(this, catalog, ::prepare)
        val first = cascade.initial(subject)
        assertFalse(PortraitSource.COMMONS in first.pages)
        catalog.failures.clear(); catalog.images[PortraitSource.COMMONS to 1] = listOf(image(0))
        val retried = cascade.more(first, PortraitSource.COMMONS)
        assertEquals(PortraitSource.COMMONS to 1, catalog.calls.last())
        assertEquals(1, retried.photos.size)
    }

}
