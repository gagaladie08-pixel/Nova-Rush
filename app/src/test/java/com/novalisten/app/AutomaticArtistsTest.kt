package com.novalisten.app

import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticArtistsTest {
    private val artist = ArtistRequest("singer", "Singer")
    private val playback = ArtistPlayback("track", listOf(artist), true)
    private val bio = Biography("1", "Singer", "fr", "Texte synthétique", Credit("Fixture", "https://fr.wikipedia.org/?curid=1"))
    private val image = ImageCandidate("commons:1", "Singer.jpg", "https://upload.wikimedia.org/test.jpg", "https://upload.wikimedia.org/test.jpg", Credit("Wikimedia Commons", "https://commons.wikimedia.org/wiki/File:Singer.jpg", "CC BY 4.0\nhttps://creativecommons.org/licenses/by/4.0/", "Fixture"), true)
    private class Fake(var block: suspend (ArtistRequest, Boolean, Boolean) -> ArtistResult) : ArtistLookup {
        val requests = mutableListOf<ArtistRequest>()
        override suspend fun resolve(request: ArtistRequest, biography: Boolean, portrait: Boolean): ArtistResult { requests += request; return block(request, biography, portrait) }
    }
    private suspend fun prepared(images: List<ImageCandidate>) = images.map { PreparedPhoto(it, "file:///synthetic-preview.jpg", PhotoFingerprint(0, 128, 128, 128, 1000)) }
    private fun loader(scope: CoroutineScope, fake: Fake, saved: MutableList<Pair<String, ArtistNote>> = mutableListOf(), deleted: MutableList<String> = mutableListOf(), save: suspend (ImageCandidate) -> String = { "file:///synthetic.jpg" }, now: () -> Long = { 0 }) =
        AutomaticArtists(scope, fake, ::prepared, save, { deleted += it }, { _, id, note -> saved += id to note; note.portrait != null }, now)

    @Test fun nothingIsSearchedForAPreparedQueuePauseMissingSnapshotOrDisabledFeatures() = runTest {
        val fake = Fake { _, _, _ -> ArtistResult() }; val auto = loader(this, fake)
        auto.onPlayback(playback.copy(playing = false), ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), false); advanceUntilIdle()
        auto.onPlayback(playback, ArtistAutomation(false, false), emptyMap(), true); advanceUntilIdle()
        assertTrue(fake.requests.isEmpty())
    }
    @Test fun savedAndIntentionallyClearedManualFieldsAreNeverResearched() = runTest {
        val fake = Fake { _, _, _ -> ArtistResult() }; val auto = loader(this, fake)
        auto.onPlayback(playback, ArtistAutomation(), mapOf("singer" to ArtistNote(biographyLocked = true, portraitLocked = true)), true); advanceUntilIdle()
        auto.onPlayback(playback, ArtistAutomation(), mapOf("singer" to ArtistNote("Saved bio", portrait = "file:///manual.jpg")), true); advanceUntilIdle()
        assertTrue(fake.requests.isEmpty())
    }
    @Test fun quickTrackSkipsAreDebouncedBeforeAnyNetwork() = runTest {
        val fake = Fake { _, _, _ -> ArtistResult() }; val auto = loader(this, fake)
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), true); advanceTimeBy(500)
        auto.onPlayback(null, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        assertTrue(fake.requests.isEmpty())
    }
    @Test fun verifiedFrenchBioAndPortraitAreCommittedWithCreditsOnlyForCurrentArtists() = runTest {
        val fake = Fake { _, _, _ -> ArtistResult(bio, image, identityVerified = true) }; val saved = mutableListOf<Pair<String, ArtistNote>>()
        val auto = loader(this, fake, saved)
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        assertEquals(listOf(artist), fake.requests); assertEquals("singer", saved.single().first)
        assertEquals(bio.text, saved.single().second.biography); assertTrue(saved.single().second.portraitSource.contains("Wikimedia Commons"))
        assertFalse(saved.single().second.portraitLocked)
    }
    @Test fun noMatchIsCachedAcrossTracksOfTheSameArtistWithoutScanningTheLibrary() = runTest {
        val fake = Fake { _, _, _ -> ArtistResult(note = "Ambiguous") }; val auto = loader(this, fake)
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        auto.onPlayback(playback.copy(trackId = "another"), ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        assertEquals(1, fake.requests.size)
    }
    @Test fun unverifiedIdentityCannotApplyEvenAValidLookingPortrait() = runTest {
        val fake = Fake { _, _, _ -> ArtistResult(bio, image, identityVerified = false) }; val saved = mutableListOf<Pair<String, ArtistNote>>()
        val auto = loader(this, fake, saved)
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        assertTrue(saved.isEmpty())
    }
    @Test fun englishIsNotSilentlySavedOrTranslated() = runTest {
        val fake = Fake { _, _, _ -> ArtistResult(biography = bio.copy(language = "en"), identityVerified = true) }; val saved = mutableListOf<Pair<String, ArtistNote>>()
        val auto = loader(this, fake, saved)
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        assertTrue(saved.isEmpty())
    }
    @Test fun broadPhotoProposalsStayManualEvenWhenTheArtistIdentityIsKnown() = runTest {
        val fake = Fake { _, _, _ -> ArtistResult(proposals = listOf(image), identityVerified = true) }; val saved = mutableListOf<Pair<String, ArtistNote>>()
        val auto = loader(this, fake, saved)
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        assertTrue(saved.isEmpty()); assertEquals(1, auto.state.value.getValue("singer").proposals.size)
    }
    @Test fun failedOrDeclinedCommitDoesNotLeakAnUnusedImage() = runTest {
        val fake = Fake { _, _, _ -> ArtistResult(portrait = image, identityVerified = true) }; val deleted = mutableListOf<String>()
        val auto = AutomaticArtists(this, fake, ::prepared, { "file:///temporary.jpg" }, { deleted += it }, { _, _, _ -> false })
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        assertEquals(listOf("file:///temporary.jpg"), deleted)
    }
    @Test fun lateResultsOfPreviousArtistsNeverGetCommitted() = runTest {
        val fake = Fake { _, _, _ -> withContext(NonCancellable) { delay(1500) }; ArtistResult(bio, image, identityVerified = true) }
        val saved = mutableListOf<Pair<String, ArtistNote>>(); val auto = loader(this, fake, saved)
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), true); advanceTimeBy(800)
        auto.onPlayback(null, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        assertTrue(saved.isEmpty())
    }
    @Test fun failuresAreTemporaryAndRetryOnlyAfterTheNegativeCacheExpires() = runTest {
        val fake = Fake { _, _, _ -> throw RemoteFailure("Temporary fixture outage") }; var now = 0L
        val auto = loader(this, fake, now = { now })
        auto.onPlayback(playback, ArtistAutomation(), emptyMap(), true); advanceUntilIdle()
        auto.onPlayback(playback.copy(trackId = "two"), ArtistAutomation(), emptyMap(), true); advanceUntilIdle(); assertEquals(1, fake.requests.size)
        now = 120001
        auto.onPlayback(playback.copy(trackId = "three"), ArtistAutomation(), emptyMap(), true); advanceUntilIdle(); assertEquals(2, fake.requests.size)
    }
    @Test fun onlyAnOpenLicensedLinkedCommonsPhotoIsEligibleNotAnOpenverseOrFanartGuess() {
        assertTrue(automaticPortraitAllowed(image, "Singer.jpg"))
        assertFalse(automaticPortraitAllowed(image.copy(licenceKnown = false), "Singer.jpg"))
        assertFalse(automaticPortraitAllowed(image.copy(needsOriginCheck = true), "Singer.jpg"))
        assertFalse(automaticPortraitAllowed(image, "Different person.jpg"))
        assertFalse(automaticPortraitAllowed(image.copy(title = "Singer logo.jpg"), "Singer logo.jpg"))
        assertFalse(automaticPortraitAllowed(image.copy(credit = image.credit.copy(provider = "Openverse")), "Singer.jpg"))
    }
}
