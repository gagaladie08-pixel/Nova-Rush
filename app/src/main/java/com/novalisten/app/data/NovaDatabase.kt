package com.novalisten.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.util.UUID

class NovaDatabase(context: Context, name: String = "novalisten.db") : SQLiteOpenHelper(context, name, null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE flags (track TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, tracks TEXT NOT NULL, created INTEGER NOT NULL, pinned INTEGER, cover TEXT)")
        db.execSQL("CREATE TABLE listens (id INTEGER PRIMARY KEY AUTOINCREMENT, track TEXT NOT NULL, at INTEGER NOT NULL, zone TEXT NOT NULL, local_date TEXT NOT NULL, local_hour INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX listens_track_at ON listens(track, at)")
        db.execSQL("CREATE TABLE lyrics (track TEXT PRIMARY KEY, original TEXT NOT NULL, french TEXT NOT NULL, original_source TEXT NOT NULL DEFAULT '', french_source TEXT NOT NULL DEFAULT '', original_timing TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE artists (id TEXT PRIMARY KEY, bio TEXT NOT NULL, source TEXT NOT NULL, portrait TEXT, portrait_source TEXT NOT NULL DEFAULT '')")
        createEnrichmentTables(db)
        createAutomaticLyricsTable(db)
    }
    private fun createAutomaticLyricsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE automatic_lyrics (track TEXT PRIMARY KEY, identity TEXT NOT NULL, original TEXT NOT NULL, original_source TEXT NOT NULL, original_timing TEXT NOT NULL DEFAULT '')")
    }
    private fun createEnrichmentTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE album_covers (id TEXT PRIMARY KEY, uri TEXT NOT NULL, source TEXT NOT NULL)")
        db.execSQL("CREATE TABLE identities (id TEXT PRIMARY KEY, mbid TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE lyrics ADD COLUMN original_source TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE lyrics ADD COLUMN french_source TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE artists ADD COLUMN portrait_source TEXT NOT NULL DEFAULT ''")
            createEnrichmentTables(db)
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE lyrics ADD COLUMN original_timing TEXT NOT NULL DEFAULT ''")
        if (oldVersion < 4) createAutomaticLyricsTable(db)
    }

    fun snapshot(): LocalData {
        val favorites = mutableSetOf<String>()
        val hidden = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT track, favorite, hidden FROM flags", null).use { c ->
            while (c.moveToNext()) {
                if (c.getInt(1) == 1) favorites.add(c.getString(0))
                if (c.getInt(2) == 1) hidden.add(c.getString(0))
            }
        }
        val lists = mutableListOf<LocalPlaylist>()
        readableDatabase.rawQuery("SELECT id,name,tracks,created,pinned,cover FROM playlists ORDER BY name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) {
                val ids = JSONArray(c.getString(2))
                lists += LocalPlaylist(c.getString(0), c.getString(1), List(ids.length()) { ids.getString(it) }, c.getLong(3), if (c.isNull(4)) null else c.getLong(4), c.getString(5))
            }
        }
        val events = mutableListOf<ListenEvent>()
        readableDatabase.rawQuery("SELECT id,track,at,zone,local_date,local_hour FROM listens ORDER BY at DESC,id DESC", null).use { c ->
            while (c.moveToNext()) events += ListenEvent(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getString(4), c.getInt(5))
        }
        val lyrics = mutableMapOf<String, SavedLyrics>()
        readableDatabase.rawQuery("SELECT track,original,french,original_source,french_source,original_timing FROM lyrics", null).use { c ->
            while (c.moveToNext()) lyrics[c.getString(0)] = SavedLyrics(c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))
        }
        val artists = mutableMapOf<String, ArtistNote>()
        readableDatabase.rawQuery("SELECT id,bio,source,portrait,portrait_source FROM artists", null).use { c ->
            while (c.moveToNext()) artists[c.getString(0)] = ArtistNote(c.getString(1), c.getString(2), c.getString(3), c.getString(4))
        }
        val covers = mutableMapOf<String, SavedCover>()
        readableDatabase.rawQuery("SELECT id,uri,source FROM album_covers", null).use { c ->
            while (c.moveToNext()) covers[c.getString(0)] = SavedCover(c.getString(1), c.getString(2))
        }
        val identities = mutableMapOf<String, String>()
        readableDatabase.rawQuery("SELECT id,mbid FROM identities", null).use { c ->
            while (c.moveToNext()) identities[c.getString(0)] = c.getString(1)
        }
        val automaticLyrics = mutableMapOf<String, AutomaticLyricRecord>()
        readableDatabase.rawQuery("SELECT track,identity,original,original_source,original_timing FROM automatic_lyrics", null).use { c ->
            while (c.moveToNext()) automaticLyrics[c.getString(0)] = AutomaticLyricRecord(c.getString(1),
                SavedLyrics(original = c.getString(2), originalSource = c.getString(3), originalTiming = c.getString(4)))
        }
        return LocalData(favorites, hidden, lists, events, lyrics, artists, covers, identities, automaticLyrics)
    }

    fun setFlag(id: String, column: String, value: Boolean) {
        require(column in setOf("favorite", "hidden"))
        writableDatabase.execSQL("INSERT OR IGNORE INTO flags(track) VALUES (?)", arrayOf(id))
        writableDatabase.update("flags", ContentValues().apply { put(column, if (value) 1 else 0) }, "track=?", arrayOf(id))
    }
    fun savePlaylist(playlist: LocalPlaylist) {
        writableDatabase.insertWithOnConflict("playlists", null, ContentValues().apply {
            put("id", playlist.id); put("name", playlist.name.trim().take(100)); put("tracks", JSONArray(playlist.trackIds).toString()); put("created", playlist.createdAt)
            if (playlist.pinnedAt == null) putNull("pinned") else put("pinned", playlist.pinnedAt)
            put("cover", playlist.cover)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun createPlaylist(name: String, ids: List<String>): LocalPlaylist = LocalPlaylist(UUID.randomUUID().toString(), name.trim(), ids.distinct(), System.currentTimeMillis()).also(::savePlaylist)
    fun deletePlaylist(id: String) { writableDatabase.delete("playlists", "id=?", arrayOf(id)) }
    fun addEvent(event: ListenEvent) {
        writableDatabase.insertOrThrow("listens", null, ContentValues().apply {
            put("track", event.trackId); put("at", event.at); put("zone", event.zone); put("local_date", event.localDate); put("local_hour", event.localHour)
        })
    }
    fun saveLyrics(id: String, lyrics: SavedLyrics) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(db.insertWithOnConflict("lyrics", null, ContentValues().apply {
                put("track", id); put("original", lyrics.original); put("french", lyrics.french); put("original_source", lyrics.originalSource); put("french_source", lyrics.frenchSource); put("original_timing", lyrics.originalTiming)
            }, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Échec de sauvegarde lyrics." }
            // An explicit choice/edit is authoritative, including an intentional clearing of text.
            db.delete("automatic_lyrics", "track=?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Commit text, credit and native timing together; never replace a chosen VO or downgrade native timing. */
    fun saveAutomaticLyrics(id: String, record: AutomaticLyricRecord): Boolean {
        require(record.identity.matches(Regex("[a-f0-9]{64}")))
        require(record.lyrics.original.isNotBlank() && record.lyrics.original.length <= 200_000 && record.lyrics.originalTiming.length <= 2_000_000)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val hidden = db.rawQuery("SELECT hidden FROM flags WHERE track=?", arrayOf(id)).use { it.moveToFirst() && it.getInt(0) != 0 }
            val chosen = db.rawQuery("SELECT original FROM lyrics WHERE track=?", arrayOf(id)).use { it.moveToFirst() && it.getString(0).isNotBlank() }
            if (hidden || chosen) return false
            val previous = db.rawQuery("SELECT identity,original,original_source,original_timing FROM automatic_lyrics WHERE track=?", arrayOf(id)).use { c ->
                if (!c.moveToFirst()) null else AutomaticLyricRecord(c.getString(0), SavedLyrics(original = c.getString(1), originalSource = c.getString(2), originalTiming = c.getString(3)))
            }
            if (previous?.identity == record.identity && previous.lyrics.timingQuality() >= record.lyrics.timingQuality()) {
                db.setTransactionSuccessful()
                return true
            }
            check(db.insertWithOnConflict("automatic_lyrics", null, ContentValues().apply {
                put("track", id); put("identity", record.identity); put("original", record.lyrics.original)
                put("original_source", record.lyrics.originalSource); put("original_timing", record.lyrics.originalTiming)
            }, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Échec de sauvegarde automatique des paroles." }
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
    }
    fun saveCover(id: String, cover: SavedCover?) {
        if (cover == null) writableDatabase.delete("album_covers", "id=?", arrayOf(id))
        else check(writableDatabase.insertWithOnConflict("album_covers", null, ContentValues().apply { put("id", id); put("uri", cover.uri); put("source", cover.source) }, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Échec de sauvegarde de la pochette." }
    }
    fun saveIdentity(id: String, mbid: String?) {
        if (mbid == null) writableDatabase.delete("identities", "id=?", arrayOf(id))
        else {
            com.novalisten.app.enrichment.RemoteSafety.mbid(mbid)
            check(writableDatabase.insertWithOnConflict("identities", null, ContentValues().apply { put("id", id); put("mbid", mbid) }, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Échec de sauvegarde de l’identité." }
        }
    }
    fun saveArtist(id: String, note: ArtistNote) {
        check(writableDatabase.insertWithOnConflict("artists", null, ContentValues().apply {
            put("id", id); put("bio", note.biography); put("source", note.source); put("portrait", note.portrait); put("portrait_source", note.portraitSource)
        }, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Échec de sauvegarde artists." }
    }
}
