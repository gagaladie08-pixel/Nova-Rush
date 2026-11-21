package com.novalisten.app

import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticLyricPersistenceTest {
    private val query = MusicQuery("Clarté nouvelle", "Artiste synthétique", "Album de test", 65000)
    private val line = LyricTiming.line(1000, "Clarté nouvelle", listOf(TimedToken("Clarté", 1000, 1500), TimedToken("nouvelle", 1800, 2800)))
    private fun candidate(q: MusicQuery = query, native: Boolean = true) = LyricCandidate("fixture", q.title, q.artist, q.album, q.durationMs,
        Credit("Fixture locale", "https://example.org/fixture", at = 1), "[00:01.000]Clarté nouvelle", nativeLines = if (native) listOf(line) else emptyList())
    private fun playback(q: MusicQuery = query, playing: Boolean = true, id: String = "fixture") = LyricPlayback(id, q, playing)
    private fun record() = AutomaticLyricRecord(query.lyricIdentity(), candidate().asSavedLyrics())
    private class Lookup(private val providers: List<Provider> = listOf(Provider.LIRIQO),
                         val action: suspend (Provider, MusicQuery) -> List<LyricCandidate> = { _, _ -> emptyList() }) : LyricLookup {
        var discoveries = 0; var loads = 0
        val calls = mutableListOf<Pair<Provider, MusicQuery>>()
        override suspend fun availableLyricProviders(): List<Provider> { discoveries++; return providers }
        override suspend fun lyrics(provider: Provider, input: MusicQuery): List<LyricCandidate> {
            calls += provider to input
            return action(provider, input)
        }
        override suspend fun loadLyrics(candidate: LyricCandidate): LyricCandidate { loads++; return candidate }
    }

    @Test fun aReadyNativeResultIsPublishedOnlyAfterItsAtomicSaveCompletes() = runTest {
        val lookup = Lookup { _, _ -> listOf(candidate()) }
        var disk: AutomaticLyricRecord? = null
        val loader = AutomaticLyrics(this, lookup, persist = { _, value -> delay(300); disk = value; true })
        loader.onPlayback(playback(), true, null)
        advanceTimeBy(600); runCurrent()
        assertEquals(AutoLyricStatus.LOADING, loader.state.value.status)
        assertNull(disk)
        advanceUntilIdle()
        assertTrue(loader.state.value.durable)
        assertEquals(AutoLyricStatus.READY, loader.state.value.status)
        assertEquals(record(), disk)
        assertEquals(line.words, LyricTiming.decode(disk!!.lyrics.original, disk!!.lyrics.originalTiming).single().words)
    }

    @Test fun savingAFirstLineResultDoesNotCancelTheSlowerNativeWordUpgrade() = runTest {
        val lookup = Lookup(listOf(Provider.LRCRED, Provider.LRCLIB, Provider.LIRIQO)) { provider, _ ->
            delay(when (provider) { Provider.LRCRED -> 10; Provider.LIRIQO -> 200; else -> 1000 })
            listOf(candidate(native = provider == Provider.LIRIQO))
        }
        val qualities = mutableListOf<Int>()
        var disk: AutomaticLyricRecord? = null
        lateinit var loader: AutomaticLyrics
        loader = AutomaticLyrics(this, lookup, persist = { _, value ->
            disk = value; qualities += value.lyrics.timingQuality()
            // Simulates the repository emitting its updated SQLite snapshot after each write.
            loader.onPlayback(playback(), true, null, stored = value)
            true
        })
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        assertEquals(3, lookup.calls.size)
        assertEquals(listOf(2, 3), qualities)
        assertEquals(3, disk!!.lyrics.timingQuality())
        assertEquals(3, loader.state.value.quality)
        assertTrue(loader.state.value.durable)
    }

    @Test fun aFreshEngineRestoresNativeTimingWithoutDiscoveryOrNetworkEvenWhenDisabledOrPaused() = runTest {
        val lookup = Lookup { _, _ -> error("No network while a durable result exists") }
        val loader = AutomaticLyrics(this, lookup) { testScheduler.currentTime }
        for (enabled in listOf(false, true)) for (playing in listOf(false, true)) {
            loader.onPlayback(playback(playing = playing), enabled, null, stored = record())
            advanceUntilIdle()
            assertEquals(AutoLyricStatus.SAVED, loader.state.value.status)
            assertEquals(record().lyrics, loader.state.value.lyrics)
            assertTrue(loader.state.value.durable)
        }
        advanceTimeBy(86_400_001)
        loader.stop()
        val restarted = AutomaticLyrics(this, lookup)
        restarted.onPlayback(playback(), true, null, stored = record()); advanceUntilIdle()
        assertEquals(3, restarted.state.value.quality)
        assertEquals(0, lookup.discoveries); assertTrue(lookup.calls.isEmpty()); assertEquals(0, lookup.loads)
    }

    @Test fun aColdStartWaitsForTheSqliteSnapshotInsteadOfStartingAnUnnecessarySearch() = runTest {
        val lookup = Lookup { _, _ -> error("The restored record must win") }
        val loader = AutomaticLyrics(this, lookup)
        loader.onPlayback(playback(), true, null, localDataReady = false)
        advanceUntilIdle(); assertEquals(0, lookup.discoveries)
        loader.onPlayback(playback(), true, null, localDataReady = true, stored = record())
        advanceUntilIdle(); assertEquals(0, lookup.discoveries)
        assertEquals(record().lyrics, loader.state.value.lyrics)
    }

    @Test fun aReusedMediaStoreIdWithDifferentMetadataCannotInheritTheOldAutomaticText() = runTest {
        val changed = query.copy(title = "Autre enregistrement")
        val lookup = Lookup { _, q -> listOf(candidate(q)) }
        val loader = AutomaticLyrics(this, lookup)
        loader.onPlayback(playback(changed, playing = false), true, null, stored = record())
        advanceUntilIdle(); assertNull(loader.state.value.lyrics); assertTrue(lookup.calls.isEmpty())
        loader.onPlayback(playback(changed), true, null, stored = record())
        advanceUntilIdle(); assertEquals(listOf(Provider.LIRIQO to changed), lookup.calls)
        assertNotEquals(query.lyricIdentity(), changed.lyricIdentity())
    }

    @Test fun anExplicitManualChoiceCancelsAnInFlightSaveAndAlwaysHasPriority() = runTest {
        val lookup = Lookup { _, _ -> listOf(candidate()) }
        var disk: AutomaticLyricRecord? = null
        val loader = AutomaticLyrics(this, lookup, persist = { _, value -> delay(500); disk = value; true })
        loader.onPlayback(playback(), true, null); advanceTimeBy(600)
        val chosen = SavedLyrics("VO corrigée", "VF manuelle", "Source VO choisie", "Source VF choisie")
        loader.onPlayback(playback(), true, chosen, stored = record()); advanceUntilIdle()
        assertNull(disk)
        assertEquals(AutoLyricStatus.SAVED, loader.state.value.status)
        assertEquals(chosen, resolveLyrics("fixture", chosen, loader.state.value, record().lyrics))
    }

    @Test fun aDiskFailureDoesNotDiscardTheDisplayedResultOrPretendItIsDurable() = runTest {
        val lookup = Lookup { _, _ -> listOf(candidate()) }
        val loader = AutomaticLyrics(this, lookup, persist = { _, _ -> throw java.io.IOException("Synthetic storage failure") })
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        assertEquals(AutoLyricStatus.READY, loader.state.value.status)
        assertNotNull(loader.state.value.lyrics); assertFalse(loader.state.value.durable)
        assertTrue(loader.state.value.diagnostics.any { it.contains("Sauvegarde locale indisponible") })
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        assertEquals(1, lookup.calls.size)
    }

    @Test fun storedNativeWordsBeatALineOnlyFallbackAndDoNotEraseAManualFrenchText() {
        val frenchOnly = SavedLyrics(french = "VF choisie", frenchSource = "Crédit VF")
        val live = AutoLyricState("fixture", AutoLyricStatus.READY, candidate(native = false).asSavedLyrics(), quality = 2)
        val resolved = resolveLyrics("fixture", frenchOnly, live, record().lyrics)
        assertEquals(record().lyrics.originalTiming, resolved.originalTiming)
        assertEquals("VF choisie", resolved.french); assertEquals("Crédit VF", resolved.frenchSource)
        assertEquals(record().lyrics.originalSource, resolved.originalSource)
    }

    @Test fun lateResponsesFromAnOldTrackCannotWriteADurableRecord() = runTest {
        val lookup = Lookup { _, q ->
            withContext(NonCancellable) { delay(if (q == query) 2000 else 10) }
            listOf(candidate(q))
        }
        val writes = mutableListOf<String>()
        val loader = AutomaticLyrics(this, lookup, persist = { id, _ -> writes += id; true })
        loader.onPlayback(playback(id = "old"), true, null); advanceTimeBy(600)
        loader.onPlayback(playback(query.copy(title = "Nouveau titre"), id = "new"), true, null)
        advanceUntilIdle()
        assertEquals(listOf("new"), writes)
        assertEquals("new", loader.state.value.trackId)
    }

    @Test fun noMatchOrNetworkFailureIsNeverSavedAsAnEmptyDurableResult() = runTest {
        for (fails in listOf(false, true)) {
            var writes = 0
            val lookup = Lookup { _, _ -> if (fails) throw RemoteFailure("Hors ligne synthétique") else emptyList() }
            val loader = AutomaticLyrics(this, lookup, persist = { _, _ -> writes++; true })
            loader.onPlayback(playback(), true, null); advanceUntilIdle()
            assertEquals(0, writes); assertNull(loader.state.value.lyrics)
            assertEquals(if (fails) AutoLyricStatus.UNAVAILABLE else AutoLyricStatus.NO_MATCH, loader.state.value.status)
        }
    }

    @Test fun clearingADurableRecordDoesNotResurrectAMemoryCopyThatClaimsToBeSaved() = runTest {
        val lookup = Lookup { _, _ -> listOf(candidate()) }
        val loader = AutomaticLyrics(this, lookup, persist = { _, _ -> true })
        loader.onPlayback(playback(), true, null); advanceUntilIdle()
        assertTrue(loader.state.value.durable)
        loader.onPlayback(playback(playing = false), true, SavedLyrics(), stored = null)
        advanceUntilIdle()
        assertNull(loader.state.value.lyrics)
        assertEquals(1, lookup.calls.size)
    }

    @Test fun storageIdentityNormalizesEquivalentMetadataButSeparatesDifferentVersionsAndCorruptTiming() {
        assertEquals(query.lyricIdentity(), query.copy(title = "  Clarte nouvelle  ", artist = "ARTISTE SYNTHETIQUE").lyricIdentity())
        assertNotEquals(query.lyricIdentity(), query.copy(durationMs = 95000).lyricIdentity())
        assertNotEquals(query.lyricIdentity(), query.copy(album = "Édition live").lyricIdentity())
        assertEquals(2, record().lyrics.copy(originalTiming = "corrompu").timingQuality())
        assertEquals(1, SavedLyrics("Texte non synchronisé").timingQuality())
    }
}
