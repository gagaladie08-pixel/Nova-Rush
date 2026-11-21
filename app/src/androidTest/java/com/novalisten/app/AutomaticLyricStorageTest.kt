package com.novalisten.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticLyricStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val query = MusicQuery("Clarté nouvelle", "Artiste synthétique", "Album synthétique", 65000)
    private val line = LyricTiming.line(1000, "Clarté nouvelle", listOf(TimedToken("Clarté", 1000, 1500), TimedToken("nouvelle", 1800, 2800)))
    private fun candidate(native: Boolean = true) = LyricCandidate("fixture", query.title, query.artist, query.album, query.durationMs,
        Credit("Fixture locale", "https://example.org/fixture", at = 1), "[00:01.000]Clarté nouvelle", nativeLines = if (native) listOf(line) else emptyList())
    private fun record(native: Boolean = true) = AutomaticLyricRecord(query.lyricIdentity(), candidate(native).asSavedLyrics())

    @Test fun automaticNativeWordsSurviveClosingSqliteAndRestartingTheEngineWithoutAnyLookup() = runBlocking {
        val name = "automatic_lyrics_restart_test.db"
        context.deleteDatabase(name)
        val playback = LyricPlayback("fixture", query, true)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            NovaDatabase(context, name).use { db ->
                db.saveLyrics("manual", SavedLyrics("VO choisie", "VF choisie", "Crédit VO", "Crédit VF"))
                db.setFlag("manual", "favorite", true)
                val lookup = object : LyricLookup {
                    override suspend fun availableLyricProviders() = listOf(Provider.LRCRED, Provider.LRCLIB, Provider.LIRIQO)
                    override suspend fun lyrics(provider: Provider, input: MusicQuery): List<LyricCandidate> {
                        delay(if (provider == Provider.LIRIQO) 100 else 10)
                        return listOf(candidate(provider == Provider.LIRIQO))
                    }
                    override suspend fun loadLyrics(candidate: LyricCandidate) = candidate
                }
                lateinit var loader: AutomaticLyrics
                loader = AutomaticLyrics(firstScope, lookup, persist = { id, value ->
                    val result = withContext(Dispatchers.IO) { db.saveAutomaticLyrics(id, value) }
                    val stored = withContext(Dispatchers.IO) { db.snapshot().automaticLyrics[id] }
                    loader.onPlayback(playback, true, null, stored = stored)
                    result
                })
                withContext(Dispatchers.Main) { loader.onPlayback(playback, true, null) }
                val result = withTimeout(10_000) { loader.state.first { it.status == AutoLyricStatus.READY && it.quality == 3 } }
                assertTrue(result.durable)
                withContext(Dispatchers.Main) { loader.stop() }
            }
            firstScope.cancel() // no in-memory engine or open SQLite helper is reused below
            NovaDatabase(context, name).use { reopened ->
                val data = reopened.snapshot()
                assertEquals(record(), data.automaticLyrics.getValue("fixture"))
                assertEquals("VF choisie", data.lyrics.getValue("manual").french)
                assertTrue("manual" in data.favorites)
                var requests = 0
                val noNetwork = object : LyricLookup {
                    override suspend fun availableLyricProviders(): List<Provider> { requests++; error("Unexpected discovery after restart") }
                    override suspend fun lyrics(provider: Provider, input: MusicQuery): List<LyricCandidate> { requests++; error("Unexpected network after restart") }
                    override suspend fun loadLyrics(candidate: LyricCandidate): LyricCandidate { requests++; error("Unexpected reload after restart") }
                }
                val restarted = AutomaticLyrics(secondScope, noNetwork)
                withContext(Dispatchers.Main) {
                    restarted.onPlayback(playback, true, data.lyrics["fixture"], stored = data.automaticLyrics["fixture"])
                }
                delay(800) // would expose an accidentally scheduled 500ms automatic lookup
                assertEquals(0, requests)
                assertEquals(AutoLyricStatus.SAVED, restarted.state.value.status)
                val restored = restarted.state.value.lyrics!!
                assertEquals(record().lyrics, restored)
                assertEquals(line.words, LyricTiming.decode(restored.original, restored.originalTiming).single().words)
                withContext(Dispatchers.Main) {
                    restarted.onPlayback(playback.copy(playing = false), false, null, stored = data.automaticLyrics["fixture"])
                    restarted.stop()
                }
                assertEquals(0, requests)
            }
        } finally { firstScope.cancel(); secondScope.cancel(); context.deleteDatabase(name) }
    }

    @Test fun automaticWritesNeverDowngradeWordsOverwriteAChoiceOrLoseAnExistingFrenchText() {
        val name = "automatic_lyrics_priority_test.db"
        context.deleteDatabase(name)
        try { NovaDatabase(context, name).use { db ->
            assertTrue(db.saveAutomaticLyrics("fixture", record()))
            assertTrue(db.saveAutomaticLyrics("fixture", record(native = false)))
            assertEquals(record(), db.snapshot().automaticLyrics.getValue("fixture"))
            val manual = SavedLyrics("VO corrigée", "VF corrigée", "Ma source VO", "Ma source VF")
            db.saveLyrics("fixture", manual)
            assertFalse(db.snapshot().automaticLyrics.containsKey("fixture"))
            assertFalse(db.saveAutomaticLyrics("fixture", record()))
            assertEquals(manual, db.snapshot().lyrics.getValue("fixture"))
            db.saveLyrics("french-only", SavedLyrics(french = "VF manuelle", frenchSource = "Crédit français"))
            assertTrue(db.saveAutomaticLyrics("french-only", record()))
            val data = db.snapshot()
            val resolved = resolveLyrics("french-only", data.lyrics["french-only"], AutoLyricState(), data.automaticLyrics["french-only"]!!.lyrics)
            assertEquals("VF manuelle", resolved.french); assertEquals("Crédit français", resolved.frenchSource)
            assertEquals(3, resolved.timingQuality())
            db.setFlag("hidden", "hidden", true)
            assertFalse(db.saveAutomaticLyrics("hidden", record()))
            assertFalse(db.snapshot().automaticLyrics.containsKey("hidden"))
            val different = record(native = false).copy(identity = query.copy(title = "Autre titre").lyricIdentity())
            assertTrue(db.saveAutomaticLyrics("reused-id", record()))
            assertTrue(db.saveAutomaticLyrics("reused-id", different))
            assertNull(db.snapshot().automaticLyrics.getValue("reused-id").forIdentity(query.lyricIdentity()))
        } } finally { context.deleteDatabase(name) }
    }

    @Test fun anInterruptedUpgradeKeepsThePreviousTextCreditAndTimingAtomic() {
        val name = "automatic_lyrics_atomic_test.db"
        context.deleteDatabase(name)
        try { NovaDatabase(context, name).use { db ->
            val previous = record(native = false)
            assertTrue(db.saveAutomaticLyrics("fixture", previous))
            db.writableDatabase.execSQL("CREATE TEMP TRIGGER fail_automatic BEFORE INSERT ON automatic_lyrics BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
            try { db.saveAutomaticLyrics("fixture", record()); fail("A failed write must be reported") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(previous, db.snapshot().automaticLyrics.getValue("fixture"))
            db.writableDatabase.execSQL("DROP TRIGGER fail_automatic")
            assertTrue(db.saveAutomaticLyrics("fixture", record()))
            assertEquals(record(), db.snapshot().automaticLyrics.getValue("fixture"))
        } } finally { context.deleteDatabase(name) }
    }

    @Test fun databaseV3AddsAutomaticStorageWithoutChangingTheExistingLibraryOrNativeVoVf() {
        val name = "automatic_lyrics_v3_migration_test.db"
        context.deleteDatabase(name)
        val saved = record().lyrics.copy(french = "VF de test", frenchSource = "Crédit VF de test")
        val legacy = object : SQLiteOpenHelper(context, name, null, 3) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE flags (track TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, tracks TEXT NOT NULL, created INTEGER NOT NULL, pinned INTEGER, cover TEXT)")
                db.execSQL("CREATE TABLE listens (id INTEGER PRIMARY KEY AUTOINCREMENT, track TEXT NOT NULL, at INTEGER NOT NULL, zone TEXT NOT NULL, local_date TEXT NOT NULL, local_hour INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE lyrics (track TEXT PRIMARY KEY, original TEXT NOT NULL, french TEXT NOT NULL, original_source TEXT NOT NULL DEFAULT '', french_source TEXT NOT NULL DEFAULT '', original_timing TEXT NOT NULL DEFAULT '')")
                db.execSQL("CREATE TABLE artists (id TEXT PRIMARY KEY, bio TEXT NOT NULL, source TEXT NOT NULL, portrait TEXT, portrait_source TEXT NOT NULL DEFAULT '')")
                db.execSQL("CREATE TABLE album_covers (id TEXT PRIMARY KEY, uri TEXT NOT NULL, source TEXT NOT NULL)")
                db.execSQL("CREATE TABLE identities (id TEXT PRIMARY KEY, mbid TEXT NOT NULL)")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Not used")
        }
        try {
            legacy.use { helper -> helper.writableDatabase.apply {
                execSQL("INSERT INTO flags VALUES ('fixture',1,0)")
                execSQL("INSERT INTO lyrics VALUES (?,?,?,?,?,?)", arrayOf("fixture", saved.original, saved.french, saved.originalSource, saved.frenchSource, saved.originalTiming))
                execSQL("INSERT INTO artists VALUES ('artist','Biographie locale','Source bio',NULL,'')")
                execSQL("INSERT INTO playlists VALUES ('list','Liste locale','[\"fixture\"]',1,NULL,NULL)")
                execSQL("INSERT INTO listens VALUES (1,'fixture',1,'UTC','1970-01-01',0)")
                execSQL("INSERT INTO album_covers VALUES ('album','file:///local-cover','Crédit image')")
                execSQL("INSERT INTO identities VALUES ('artist','local-test-identity')")
            } }
            NovaDatabase(context, name).use { migrated ->
                val data = migrated.snapshot()
                assertEquals(5, migrated.readableDatabase.version)
                assertEquals(saved, data.lyrics.getValue("fixture"))
                assertEquals(line.words, LyricTiming.decode(saved.original, saved.originalTiming).single().words)
                assertTrue("fixture" in data.favorites); assertTrue(data.automaticLyrics.isEmpty())
                assertEquals("Biographie locale", data.artists.getValue("artist").biography)
                assertEquals(listOf("fixture"), data.playlists.single().trackIds)
                assertEquals("fixture", data.events.single().trackId)
                assertEquals("Crédit image", data.covers.getValue("album").source)
                assertEquals("local-test-identity", data.identities.getValue("artist"))
            }
        } finally { legacy.close(); context.deleteDatabase(name) }
    }
}
