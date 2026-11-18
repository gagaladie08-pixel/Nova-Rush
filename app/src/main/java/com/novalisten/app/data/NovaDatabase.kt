package com.novalisten.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.util.UUID

class NovaDatabase(context: Context) : SQLiteOpenHelper(context, "novalisten.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE flags (track TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, tracks TEXT NOT NULL, created INTEGER NOT NULL, pinned INTEGER, cover TEXT)")
        db.execSQL("CREATE TABLE listens (id INTEGER PRIMARY KEY AUTOINCREMENT, track TEXT NOT NULL, at INTEGER NOT NULL, zone TEXT NOT NULL, local_date TEXT NOT NULL, local_hour INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX listens_track_at ON listens(track, at)")
        db.execSQL("CREATE TABLE lyrics (track TEXT PRIMARY KEY, original TEXT NOT NULL, french TEXT NOT NULL)")
        db.execSQL("CREATE TABLE artists (id TEXT PRIMARY KEY, bio TEXT NOT NULL, source TEXT NOT NULL, portrait TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
        readableDatabase.rawQuery("SELECT track,original,french FROM lyrics", null).use { c ->
            while (c.moveToNext()) lyrics[c.getString(0)] = SavedLyrics(c.getString(1), c.getString(2))
        }
        val artists = mutableMapOf<String, ArtistNote>()
        readableDatabase.rawQuery("SELECT id,bio,source,portrait FROM artists", null).use { c ->
            while (c.moveToNext()) artists[c.getString(0)] = ArtistNote(c.getString(1), c.getString(2), c.getString(3))
        }
        return LocalData(favorites, hidden, lists, events, lyrics, artists)
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
        writableDatabase.insertWithOnConflict("lyrics", null, ContentValues().apply {
            put("track", id); put("original", lyrics.original); put("french", lyrics.french)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun saveArtist(id: String, note: ArtistNote) {
        writableDatabase.insertWithOnConflict("artists", null, ContentValues().apply {
            put("id", id); put("bio", note.biography); put("source", note.source); put("portrait", note.portrait)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
