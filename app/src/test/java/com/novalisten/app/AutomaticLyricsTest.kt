package com.novalisten.app

import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticLyricsTest {
    private val query = MusicQuery("Lumière originale", "Artiste de test", "Album de test", 65000)
    private fun playback(id: String = "local:one", playing: Boolean = true, q: MusicQuery = query) = LyricPlayback(id, q, playing)
    private fun candidate(text: String = "[00:01.000]Ligne de test", q: MusicQuery = query) =
        LyricCandidate("fixture", q.title, q.artist, q.album, q.durationMs, Credit("Fixture locale", "https://example.org/test"), text)
    private class Fake(val action: suspend (Provider, MusicQuery) -> List<LyricCandidate> = { _, _ -> emptyList() }) : LyricLookup {
        val calls = mutableListOf<Pair<Provider, MusicQuery>>()
        var active = 0; var peak = 0; var loads = 0
        override suspend fun availableLyricProviders() = listOf(Provider.LRCRED, Provider.LRCLIB, Provider.LIRIQO)
        override suspend fun lyrics(provider: Provider, input: MusicQuery): List<LyricCandidate> {
            calls += provider to input; active++; peak = maxOf(peak, active)
            try { return action(provider, input) } finally { active-- }
        }
        override suspend fun loadLyrics(candidate: LyricCandidate): LyricCandidate { loads++; return candidate }
    }
    @Test fun constructorOrPreparedQueueOrDisabledPreferenceNeverSendsMetadata() = runTest {
        val fake = Fake(); val loader = AutomaticLyrics(this, fake)
        loader.onPlayback(null, true, null)
        loader.onPlayback(playback(playing = false), true, null)
        advanceUntilIdle(); assertTrue(fake.calls.isEmpty())
        loader.onPlayback(playback(), false, null)
        advanceUntilIdle(); assertTrue(fake.calls.isEmpty()); assertEquals(AutoLyricStatus.DISABLED, loader.state.value.status)
    }
    @Test fun waitsForSQLiteSnapshotAndThenHonorsSavedOriginalAndFrench() = runTest {
        val fake = Fake(); val loader = AutomaticLyrics(this, fake)
        loader.onPlayback(playback(), true, null, localDataReady = false)
        advanceUntilIdle(); assertTrue(fake.calls.isEmpty())
        val saved = SavedLyrics("Ma VO choisie", "Ma VF", "Source VO", "Source VF")
        loader.onPlayback(playback(), true, saved)
        advanceUntilIdle(); assertTrue(fake.calls.isEmpty())
        assertEquals(saved, resolveLyrics("local:one", saved, loader.state.value))
    }
    @Test fun allAdmittedCoreProvidersAreUsedButOnlyTwoAtOnce() = runTest {
        val fake = Fake { _, _ -> delay(100); listOf(candidate()) }
        val loader = AutomaticLyrics(this, fake)
        loader.onPlayback(playback(), true, null)
        repeat(10) { loader.onPlayback(playback(), true, null) }
        advanceUntilIdle()
        assertEquals(setOf(Provider.LRCRED, Provider.LRCLIB, Provider.LIRIQO), fake.calls.map { it.first }.toSet())
        assertEquals(3, fake.calls.size); assertEquals(2, fake.peak)
        assertEquals(AutoLyricStatus.READY, loader.state.value.status)
        assertEquals("[00:01.000]Ligne de test", loader.state.value.lyrics?.original)
    }
    @Test fun aFastSkipCancelsBeforeTheOldTrackLeavesThePhone() = runTest {
        val fake = Fake { _, q -> listOf(candidate(q = q)) }; val loader = AutomaticLyrics(this, fake)
        loader.onPlayback(playback("old"), true, null)
        advanceTimeBy(200)
        val next = query.copy(title = "Un autre morceau")
        loader.onPlayback(playback("new", q = next), true, null)
        advanceUntilIdle()
        assertEquals(3, fake.calls.size); assertTrue(fake.calls.all { it.second.title == next.title })
        assertEquals("new", loader.state.value.trackId)
    }
    @Test fun lateOldResponsesCannotReplaceTheNewCurrentTrack() = runTest {
        val fake = Fake { _, q -> withContext(NonCancellable) { delay(if (q.title == query.title) 2000 else 10) }; listOf(candidate(q = q)) }
        val loader = AutomaticLyrics(this, fake)
        loader.onPlayback(playback("old"), true, null)
        advanceTimeBy(600)
        loader.onPlayback(playback("new", q = query.copy(title = "Nouveau morceau")), true, null)
        advanceUntilIdle()
        assertEquals("new", loader.state.value.trackId)
        assertEquals(3, fake.calls.count { it.second.title == "Nouveau morceau" })
        assertEquals(AutoLyricStatus.READY, loader.state.value.status)
    }
    @Test fun aManualChoiceArrivingDuringLoadingWinsAndIsNeverWrittenOver() = runTest {
        val fake = Fake { _, _ -> delay(2000); listOf(candidate()) }; val loader = AutomaticLyrics(this, fake)
        loader.onPlayback(playback(), true, null)
        advanceTimeBy(600)
        val manual = SavedLyrics("VO corrigée", "VF corrigée")
        loader.onPlayback(playback(), true, manual)
        advanceUntilIdle()
        assertEquals(AutoLyricStatus.SAVED, loader.state.value.status); assertNull(loader.state.value.lyrics)
        assertEquals(manual, resolveLyrics("local:one", manual, loader.state.value))
    }
    @Test fun genuineWordWindowsWinWithoutADowngradeFromALateLineOnlySource() = runTest {
        val native = LyricTiming.line(1000, "Ligne de test", listOf(TimedToken("Ligne", 1000, 1400), TimedToken("de", 1600, 1800), TimedToken("test", 1900, 2500)))
        val fake = Fake { provider, _ ->
            delay(if (provider == Provider.LRCRED) 100 else 10)
            listOf(if (provider == Provider.LIRIQO) candidate().copy(nativeLines = listOf(native)) else candidate())
        }
        val loader = AutomaticLyrics(this, fake)
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        assertEquals(3, loader.state.value.quality)
        val saved = loader.state.value.lyrics!!
        assertEquals(native.words, LyricTiming.decode(saved.original, saved.originalTiming).single().words)
    }
    @Test fun ambiguousMetadataDoesNotTriggerAnAutomaticChoice() = runTest {
        val fake = Fake { _, _ -> listOf(candidate(q = query.copy(artist = "Autre artiste")), candidate(q = query.copy(durationMs = 95000)), candidate(q = query.copy(title = "Lumière originale live"))) }
        val loader = AutomaticLyrics(this, fake)
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        assertEquals(AutoLyricStatus.NO_MATCH, loader.state.value.status); assertEquals(0, fake.loads)
    }
    @Test fun missingTitleArtistOrKnownDurationNeverQueriesProviders() = runTest {
        val fake = Fake(); val loader = AutomaticLyrics(this, fake)
        listOf(query.copy(title = ""), query.copy(artist = "Artiste inconnu"), query.copy(durationMs = 0), query.copy(artist = "---")).forEachIndexed { index, q ->
            loader.onPlayback(playback("local:$index", q = q), true, null); advanceUntilIdle()
        }
        assertTrue(fake.calls.isEmpty())
    }
    @Test fun conservativeMatchNormalizesAccentsButNotRecordingVariants() {
        assertTrue(LyricMatch.accepts(query, candidate(q = query.copy(title = "Lumiere originale", durationMs = 67000))))
        assertFalse(LyricMatch.accepts(query, candidate(q = query.copy(title = "Lumière originale remix"))))
        assertFalse(LyricMatch.accepts(query, candidate(q = query.copy(durationMs = 0))))
    }
    @Test fun aMemoryCacheReusesResultsAcrossPauseAndTrackReturnWithoutDiskMutation() = runTest {
        val fake = Fake { _, q -> listOf(candidate(q = q)) }; val loader = AutomaticLyrics(this, fake) { testScheduler.currentTime }
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        loader.onPlayback(playback(playing = false), true, null); advanceUntilIdle()
        loader.onPlayback(playback("two", q = query.copy(title = "Deuxième essai")), true, null); advanceUntilIdle()
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        assertEquals(6, fake.calls.size); assertEquals(AutoLyricStatus.READY, loader.state.value.status)
        assertNull(resolveLyrics("other", null, loader.state.value).takeIf { it.original.isNotBlank() })
    }
    @Test fun noMatchIsCachedRatherThanPolledAndExpiresOnALaterPlaybackEvent() = runTest {
        val fake = Fake(); val loader = AutomaticLyrics(this, fake) { testScheduler.currentTime }
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        repeat(5) { loader.onPlayback(playback(), true, null) }; advanceUntilIdle()
        assertEquals(3, fake.calls.size)
        advanceTimeBy(600_001)
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        assertEquals(6, fake.calls.size)
    }
    @Test fun offlineFailuresAreNotMistakenForAnEmptySuccessfulSearchOrRetriedImmediately() = runTest {
        val fake = Fake { _, _ -> throw RemoteFailure("Connexion de test absente") }
        val loader = AutomaticLyrics(this, fake) { testScheduler.currentTime }
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        assertEquals(AutoLyricStatus.UNAVAILABLE, loader.state.value.status)
        loader.onPlayback(playback(), true, null); advanceUntilIdle(); assertEquals(3, fake.calls.size)
    }
    @Test fun disablingCancelsWorkAndStopDoesNotLeakATextToTheNextSong() = runTest {
        val fake = Fake { _, _ -> delay(1000); listOf(candidate()) }; val loader = AutomaticLyrics(this, fake)
        loader.onPlayback(playback(), true, null); advanceTimeBy(600)
        loader.onPlayback(playback(), false, null); advanceUntilIdle()
        assertEquals(AutoLyricStatus.DISABLED, loader.state.value.status); assertEquals(0, fake.loads)
        loader.stop(); assertEquals("", loader.state.value.trackId); assertNull(loader.state.value.lyrics)
    }
}
