package com.novalisten.app

import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryArtistsTest {
    private val bio = Biography("1", "Singer", "fr", "Texte FR de test", Credit("Fixture", "https://fr.wikipedia.org/?curid=1"))
    private fun work(id: String) = ArtistWork("track-$id", ArtistRequest(id, id))
    private fun loader(scope: CoroutineScope, lookup: ArtistLookup, saved: MutableList<Pair<String, ArtistNote>>) =
        AutomaticArtists(scope, lookup, { emptyList() }, { error("No photo fixture") }, {}, { _, request, note -> saved += request.id to note; false })
    @Test fun libraryArtistsDoNotRequirePlaybackAndEachIsProcessedOnceDespiteRepositoryEmissions() = runTest {
        val requests = mutableListOf<String>(); val saved = mutableListOf<Pair<String, ArtistNote>>()
        val lookup = object : ArtistLookup { override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult {
            requests += request.id; return ArtistResult(biography = bio, identityVerified = true)
        } }
        val auto = loader(this, lookup, saved)
        val works = listOf(work("one"), work("two"), work("three"))
        auto.onLibrary(works, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        auto.onLibrary(works, ArtistAutomation(), saved.toMap(), true); advanceUntilIdle()
        assertEquals(listOf("one", "two", "three"), requests)
        assertEquals(requests, saved.map { it.first })
    }
    @Test fun aSlowOrTimedOutPortraitCannotHoldAnAlreadyResolvedBiography() = runTest {
        val saved = mutableListOf<Pair<String, ArtistNote>>()
        val lookup = object : ArtistLookup {
            override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean) = error("Use progressive adapter")
            override suspend fun progressively(request: ArtistRequest, biography: Boolean, portrait: Boolean, emit: suspend (ArtistResult) -> Unit) {
                emit(ArtistResult(biography = bio, identityVerified = true))
                delay(60_000)
            }
        }
        val auto = loader(this, lookup, saved)
        auto.onLibrary(listOf(work("one")), ArtistAutomation(), emptyMap(), true)
        advanceTimeBy(1000); runCurrent()
        assertEquals(bio.text, saved.single().second.biography)
        advanceUntilIdle()
        assertEquals(1, saved.size); assertFalse(auto.state.value.getValue("one").loading)
    }
    @Test fun hidingAnArtistOrDisablingAutomationCancelsLateWorkWithoutSavingIt() = runTest {
        val saved = mutableListOf<Pair<String, ArtistNote>>()
        val lookup = object : ArtistLookup { override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult {
            withContext(NonCancellable) { delay(2000) }; return ArtistResult(biography = bio, identityVerified = true)
        } }
        val auto = loader(this, lookup, saved)
        auto.onLibrary(listOf(work("one")), ArtistAutomation(), emptyMap(), true); advanceTimeBy(1000)
        auto.onLibrary(emptyList(), ArtistAutomation(false, false), emptyMap(), true); advanceUntilIdle()
        assertTrue(saved.isEmpty())
    }
    @Test fun intentionallyClearedFieldsAndDisabledFeaturesDoNotTriggerLibraryLookups() = runTest {
        var calls = 0; val saved = mutableListOf<Pair<String, ArtistNote>>()
        val lookup = object : ArtistLookup { override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult { calls++; return ArtistResult() } }
        val auto = loader(this, lookup, saved)
        auto.onLibrary(listOf(work("one")), ArtistAutomation(), mapOf("one" to ArtistNote(biographyLocked = true, portraitLocked = true)), true); advanceUntilIdle()
        auto.onLibrary(listOf(work("two")), ArtistAutomation(false, false), emptyMap(), true); advanceUntilIdle()
        assertEquals(0, calls)
    }
    @Test fun priorityIsPlayingThenVisibleThenRemainingAndNeverContainsHiddenOrShortFiles() {
        fun song(id: String) = Track(id, "Song $id", id, "Album $id", id.hashCode().toLong(), id, 65000, 0, 2020, 1, "$id.flac", 100, "audio/flac")
        val songs = listOf("A", "B", "C", "D", "Hidden").map(::song) + song("Short").copy(durationMs = 30000)
        val index = CatalogSnapshot.build(songs, setOf("Hidden"), CatalogChoices())
        val works = artistLibraryWorks(index, LocalData(), "C", listOf("b"))
        assertEquals(listOf("c", "b", "a", "d"), works.map { it.artist.id })
    }
    @Test fun foregroundReadinessIsRequiredForLibraryNetworkWork() = runTest {
        var calls = 0
        val lookup = object : ArtistLookup { override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult { calls++; return ArtistResult() } }
        val auto = loader(this, lookup, mutableListOf())
        auto.onLibrary(listOf(work("one")), ArtistAutomation(), emptyMap(), false); advanceUntilIdle()
        assertEquals(0, calls)
    }
    @Test fun biographyPhaseNeverStartsPortraitFallbackBeforeTheLinkedPhotoWasChecked() = runTest {
        var cascade = 0; var photoPhase = false
        val saved = mutableListOf<ArtistNote>()
        val lookup = object : ArtistLookup {
            override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult = error("Progressive")
            override suspend fun progressively(request: ArtistRequest, biography: Boolean, portrait: Boolean, emit: suspend (ArtistResult) -> Unit) {
                emit(ArtistResult(biography = bio, identityVerified = true, verifiedMbid = "11111111-1111-1111-1111-111111111111", portraitComplete = false))
                assertEquals(0, cascade); assertEquals(bio.text, saved.single().biography)
                photoPhase = true
                emit(ArtistResult(identityVerified = true, verifiedMbid = "11111111-1111-1111-1111-111111111111"))
            }
        }
        val auto = AutomaticArtists(this, lookup, { emptyList() }, { error("No image") }, {}, { _, _, note -> saved += note; false },
            discoverPortraits = { _, _ -> assertTrue(photoPhase); cascade++; null })
        auto.onLibrary(listOf(work("one")), ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        assertEquals(1, cascade); assertEquals(1, saved.size)
    }
    @Test fun aPlayingOrVisibleArtistPreemptsAnUnfinishedBackgroundSearch() = runTest {
        for (playing in listOf(true, false)) {
            val started = mutableListOf<String>(); val saved = mutableListOf<Pair<String, ArtistNote>>()
            val lookup = object : ArtistLookup { override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult {
                started += request.id
                if (request.id == "background") delay(10_000)
                return ArtistResult(biography = bio, identityVerified = true)
            } }
            val auto = loader(this, lookup, saved)
            val background = work("background"); val priority = work("priority")
            auto.onLibrary(listOf(background, priority), ArtistAutomation(), emptyMap(), true)
            advanceTimeBy(1000); runCurrent(); assertEquals(listOf("background"), started)
            auto.onLibrary(listOf(priority, background), ArtistAutomation(), emptyMap(), true,
                priorityPlaying = if (playing) setOf("priority") else emptySet(), priorityVisible = if (playing) emptySet() else setOf("priority"))
            advanceTimeBy(1000); runCurrent()
            assertEquals("priority", saved.first().first)
            auto.stop(); advanceUntilIdle()
        }
    }
    @Test fun aManualClearingDuringARequestMasksBothFieldsBeforeAnyImageDownload() = runTest {
        var prepared = 0; val saved = mutableListOf<ArtistNote>()
        val lookup = object : ArtistLookup { override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult {
            delay(1000); return ArtistResult(biography = bio, identityVerified = true)
        } }
        val auto = AutomaticArtists(this, lookup, { prepared++; emptyList() }, { error("No image") }, {}, { _, _, note -> saved += note; false })
        auto.onLibrary(listOf(work("one")), ArtistAutomation(), emptyMap(), true); advanceTimeBy(1000)
        auto.onLibrary(listOf(work("one")), ArtistAutomation(), mapOf("one" to ArtistNote(biographyLocked = true, portraitLocked = true)), true)
        advanceUntilIdle()
        assertTrue(saved.isEmpty()); assertEquals(0, prepared)
    }

}
