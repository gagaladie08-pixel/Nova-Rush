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
}
