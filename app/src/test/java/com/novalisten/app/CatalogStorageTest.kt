package com.novalisten.app

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.novalisten.app.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/** Database tests on Android's SQLite API under Robolectric; no APK signing and no phone/network music API. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class CatalogStorageTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun database(block: (String) -> Unit) { val name = "catalog-${UUID.randomUUID()}.db"; try { block(name) } finally { context.deleteDatabase(name) } }
    // SQLiteOpenHelper only implements AutoCloseable in newer Android SDKs. Calling the
    // Kotlin AutoCloseable.use extension compiled against SDK 35 casts on Android 28.
    private inline fun <T : SQLiteOpenHelper, R> T.withHelper(block: (T) -> R): R =
        try { block(this) } finally { close() }

    @Test fun catalogChoicesSurviveCloseAndReopenWithoutChangingExistingMusicData() = database { name ->
        val family = AlbumFamily("family:fixture", setOf("edition-one", "edition-two"), "edition-one", "edition-two")
        val lyrics = SavedLyrics("[00:01.000]Test", "Version française", "Source VO", "Source VF")
        NovaDatabase(context, name).withHelper { db ->
            db.setFlag("track", "favorite", true); db.saveLyrics("track", lyrics)
            db.createPlaylist("Existing list", listOf("track")); db.addEvent(eventAt("track"))
            db.saveArtistCredits("track", listOf(ArtistCredit("One"), ArtistCredit("Two")))
            db.saveAlbumFamily(family); db.saveReleaseKind(family.id, ReleaseKind.MIXTAPE)
            db.saveRecordingAssociation(setOf("track", "copy"), "confirmed-recording")
            db.separateEdition("other-edition")
        }
        NovaDatabase(context, name).withHelper { db ->
            val state = db.snapshot()
            assertEquals(family, state.catalog.families.getValue(family.id))
            assertEquals(ReleaseKind.MIXTAPE, state.catalog.kinds[family.id])
            assertEquals(listOf("One", "Two"), state.catalog.artists.getValue("track").map { it.name })
            assertEquals("confirmed-recording", state.catalog.recordings["copy"])
            assertTrue("other-edition" in state.catalog.separated)
            assertEquals(lyrics, state.lyrics["track"]); assertTrue("track" in state.favorites)
            assertEquals(listOf("track"), state.playlists.single().trackIds); assertEquals("track", state.events.single().trackId)
        }
    }
    @Test fun manualPhotoOrBiographyAlwaysWinsOverAnInFlightAutomaticResultIncludingIntentionalClearing() = database { name ->
        NovaDatabase(context, name).withHelper { db ->
            val manual = ArtistNote("", "User cleared it", "file:///chosen.jpg", "Manual credit", biographyLocked = true, portraitLocked = true)
            db.saveArtist("artist", manual)
            assertFalse(db.saveAutomaticArtist("artist", ArtistNote("Remote bio", "Remote credit", "file:///automatic.jpg", "Auto credit")))
            assertEquals(manual, db.snapshot().artists.getValue("artist"))
        }
    }
    @Test fun automaticFieldsFillOnlyTheirMissingUnlockedCounterpartAndRetainCredits() = database { name ->
        NovaDatabase(context, name).withHelper { db ->
            db.saveArtist("artist", ArtistNote("User bio", "User credit", biographyLocked = true))
            assertTrue(db.saveAutomaticArtist("artist", ArtistNote("Ignored text", "Ignored credit", "file:///portrait.jpg", "Portrait credit")))
            val saved = db.snapshot().artists.getValue("artist")
            assertEquals("User bio", saved.biography); assertEquals("User credit", saved.source)
            assertEquals("file:///portrait.jpg", saved.portrait); assertEquals("Portrait credit", saved.portraitSource)
            assertFalse(saved.portraitLocked)
        }
    }
    @Test fun separatingEditionPresentationDoesNotDeleteLyricsFlagsPlaylistsOrRecordingDecisions() = database { name ->
        NovaDatabase(context, name).withHelper { db ->
            val family = AlbumFamily("family:test", setOf("one", "two"), "one")
            db.setFlag("track", "favorite", true); db.saveLyrics("track", SavedLyrics("Text")); db.createPlaylist("Keep", listOf("track"))
            db.saveAlbumFamily(family); db.saveRecordingAssociation(setOf("track", "copy"), "same")
            db.removeAlbumFamily(family.id); family.editions.forEach(db::separateEdition)
            val state = db.snapshot()
            assertTrue(state.catalog.families.isEmpty()); assertEquals(setOf("one", "two"), state.catalog.separated)
            assertEquals("same", state.catalog.recordings["track"]); assertEquals("Text", state.lyrics.getValue("track").original)
            assertTrue("track" in state.favorites); assertEquals(listOf("track"), state.playlists.single().trackIds)
        }
    }
    @Test fun migrationFromV4IsAdditiveAndProtectsOldManualProfilesWithoutAssigningCollectiveProfilesToIndividuals() = database { name ->
        val legacy = object : SQLiteOpenHelper(context, name, null, 4) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE flags(track TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE TABLE playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL,tracks TEXT NOT NULL,created INTEGER NOT NULL,pinned INTEGER,cover TEXT)")
                db.execSQL("CREATE TABLE listens(id INTEGER PRIMARY KEY AUTOINCREMENT,track TEXT NOT NULL,at INTEGER NOT NULL,zone TEXT NOT NULL,local_date TEXT NOT NULL,local_hour INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE lyrics(track TEXT PRIMARY KEY,original TEXT NOT NULL,french TEXT NOT NULL,original_source TEXT NOT NULL DEFAULT '',french_source TEXT NOT NULL DEFAULT '',original_timing TEXT NOT NULL DEFAULT '')")
                db.execSQL("CREATE TABLE automatic_lyrics(track TEXT PRIMARY KEY,identity TEXT NOT NULL,original TEXT NOT NULL,original_source TEXT NOT NULL,original_timing TEXT NOT NULL DEFAULT '')")
                db.execSQL("CREATE TABLE artists(id TEXT PRIMARY KEY,bio TEXT NOT NULL,source TEXT NOT NULL,portrait TEXT,portrait_source TEXT NOT NULL DEFAULT '')")
                db.execSQL("CREATE TABLE album_covers(id TEXT PRIMARY KEY,uri TEXT NOT NULL,source TEXT NOT NULL)")
                db.execSQL("CREATE TABLE identities(id TEXT PRIMARY KEY,mbid TEXT NOT NULL)")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unused")
        }
        legacy.withHelper { helper -> helper.writableDatabase.apply {
            execSQL("INSERT INTO flags VALUES ('track',1,0)")
            execSQL("INSERT INTO lyrics VALUES ('track','Original','Français','VO','VF','')")
            execSQL("INSERT INTO automatic_lyrics VALUES ('auto','identity','Automatic','Credit','native')")
            execSQL("INSERT INTO artists VALUES ('one/two','Collective profile','Source','file:///old.jpg','Photo credit')")
            execSQL("INSERT INTO album_covers VALUES ('edition','file:///cover.jpg','Cover credit')")
        } }
        NovaDatabase(context, name).withHelper { db ->
            val state = db.snapshot()
            assertEquals(5, db.readableDatabase.version); assertTrue("track" in state.favorites)
            assertEquals("Français", state.lyrics.getValue("track").french)
            assertEquals("native", state.automaticLyrics.getValue("auto").lyrics.originalTiming)
            assertEquals("Cover credit", state.covers.getValue("edition").source)
            assertTrue(state.artists.getValue("one/two").biographyLocked); assertTrue(state.artists.getValue("one/two").portraitLocked)
            assertFalse(state.artists.containsKey("one")); assertTrue(state.catalog.families.isEmpty())
        }
    }
}
