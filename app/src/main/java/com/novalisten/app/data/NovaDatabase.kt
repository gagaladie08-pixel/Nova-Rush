package com.novalisten.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

class NovaDatabase(context: Context, name: String = "novalisten.db") : SQLiteOpenHelper(context, name, null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE flags (track TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, tracks TEXT NOT NULL, created INTEGER NOT NULL, pinned INTEGER, cover TEXT)")
        db.execSQL("CREATE TABLE listens (id INTEGER PRIMARY KEY AUTOINCREMENT, track TEXT NOT NULL, at INTEGER NOT NULL, zone TEXT NOT NULL, local_date TEXT NOT NULL, local_hour INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX listens_track_at ON listens(track, at)")
        db.execSQL("CREATE TABLE lyrics (track TEXT PRIMARY KEY, original TEXT NOT NULL, french TEXT NOT NULL, original_source TEXT NOT NULL DEFAULT '', french_source TEXT NOT NULL DEFAULT '', original_timing TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE artists (id TEXT PRIMARY KEY, bio TEXT NOT NULL, source TEXT NOT NULL, portrait TEXT, portrait_source TEXT NOT NULL DEFAULT '', bio_locked INTEGER NOT NULL DEFAULT 0, portrait_locked INTEGER NOT NULL DEFAULT 0)")
        createEnrichmentTables(db)
        createAutomaticLyricsTable(db)
        createCatalogTable(db)
    }
    private fun createCatalogTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE catalog_choices (kind TEXT NOT NULL, id TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(kind,id))")
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
        if (oldVersion < 5) {
            createCatalogTable(db)
            db.execSQL("ALTER TABLE artists ADD COLUMN bio_locked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE artists ADD COLUMN portrait_locked INTEGER NOT NULL DEFAULT 0")
            // Before v5 every stored artist row came from a manual action. An empty field
            // may have been intentionally cleared; never infer consent to replace it.
            db.execSQL("UPDATE artists SET bio_locked = 1, portrait_locked = 1")
        }
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
        readableDatabase.rawQuery("SELECT id,bio,source,portrait,portrait_source,bio_locked,portrait_locked FROM artists", null).use { c ->
            while (c.moveToNext()) artists[c.getString(0)] = ArtistNote(c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5) != 0, c.getInt(6) != 0)
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
        val artistChoices = mutableMapOf<String, List<ArtistCredit>>()
        val families = mutableMapOf<String, AlbumFamily>()
        val kinds = mutableMapOf<String, ReleaseKind>()
        val recordings = mutableMapOf<String, String>()
        val separated = mutableSetOf<String>()
        val resolved = mutableMapOf<String, ResolvedArtistCredits>()
        readableDatabase.rawQuery("SELECT kind,id,value FROM catalog_choices", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(1); val raw = c.getString(2)
                when (c.getString(0)) {
                    "artists" -> { val list = JSONArray(raw); artistChoices[id] = (0 until list.length()).map { index -> list.getJSONObject(index).let { ArtistCredit(it.getString("name"), it.optString("mbid")) } } }
                    "resolved_artists" -> {
                        val item = JSONObject(raw); val list = item.getJSONArray("credits")
                        resolved[id] = ResolvedArtistCredits(item.getString("artist"), item.getString("title"), item.getString("album"), item.getLong("duration"),
                            (0 until list.length()).map { index -> list.getJSONObject(index).let { ArtistCredit(it.getString("name"), it.getString("mbid")) } })
                    }
                    "family" -> { val item = JSONObject(raw); val list = item.getJSONArray("editions"); families[id] = AlbumFamily(id, (0 until list.length()).map { list.getString(it) }.toSet(), item.getString("original"), item.getString("representative")) }
                    "kind" -> kinds[id] = runCatching { ReleaseKind.valueOf(raw) }.getOrDefault(ReleaseKind.UNKNOWN)
                    "recording" -> recordings[id] = raw
                    "separate" -> separated += id
                }
            }
        }
        return LocalData(favorites, hidden, lists, events, lyrics, artists, covers, identities, automaticLyrics,
            CatalogChoices(artistChoices, families, kinds, recordings, separated, resolved))
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
    private fun catalogChoice(kind: String, id: String, value: String?) {
        require(id.isNotBlank() && id.length <= 2000)
        if (value == null) writableDatabase.delete("catalog_choices", "kind=? AND id=?", arrayOf(kind, id))
        else check(writableDatabase.insertWithOnConflict("catalog_choices", null, ContentValues().apply {
            put("kind", kind); put("id", id); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    fun saveArtistCredits(track: String, credits: List<ArtistCredit>?) {
        require(credits == null || (credits.size in 1..16 && credits.all { it.name.isNotBlank() && it.name.length <= 300 && (it.mbid.isBlank() || validMusicId(it.mbid)) }))
        catalogChoice("artists", track, credits?.let { list -> JSONArray().apply { list.distinctBy { it.id }.forEach { put(JSONObject().put("name", it.name.trim()).put("mbid", it.mbid)) } }.toString() })
    }
    fun saveResolvedArtists(track: Track, credits: List<ArtistCredit>) {
        require(credits.size in 2..16 && credits.all { it.name.isNotBlank() && validMusicId(it.mbid) })
        catalogChoice("resolved_artists", track.id, JSONObject().put("artist", track.artist).put("title", track.title).put("album", track.album).put("duration", track.durationMs)
            .put("credits", JSONArray().apply { credits.forEach { put(JSONObject().put("name", it.name).put("mbid", it.mbid)) } }).toString())
    }
    fun saveAlbumFamily(family: AlbumFamily) {
        validateFamily(family, snapshot().catalog)
        catalogChoice("family", family.id, JSONObject().put("editions", JSONArray(family.editions.sorted())).put("original", family.original).put("representative", family.representative).toString())
    }
    fun separateEdition(id: String) = catalogChoice("separate", id, "1")
    fun removeAlbumFamily(id: String) = catalogChoice("family", id, null)
    fun saveReleaseKind(edition: String, kind: ReleaseKind?) = catalogChoice("kind", edition, kind?.name)
    fun saveRecordingAssociation(tracks: Set<String>, recording: String?) {
        require(tracks.size in 1..100)
        val db = writableDatabase
        db.beginTransaction()
        try { tracks.forEach { catalogChoice("recording", it, recording) }; db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    private fun storedArtist(id: String): ArtistNote? = readableDatabase.rawQuery("SELECT bio,source,portrait,portrait_source,bio_locked,portrait_locked FROM artists WHERE id=?", arrayOf(id)).use { c ->
        if (!c.moveToFirst()) null else ArtistNote(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) != 0, c.getInt(5) != 0)
    }
    private inline fun <T> artistTransaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try { return block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
    fun saveArtistBiography(id: String, biography: String, source: String, seed: ArtistNote = ArtistNote()) = artistTransaction {
        val current = storedArtist(id) ?: seed
        saveArtist(id, current.copy(biography = biography, source = source, biographyLocked = true))
    }
    fun saveArtistPortrait(id: String, portrait: String?, source: String, seed: ArtistNote = ArtistNote()) = artistTransaction {
        val current = storedArtist(id) ?: seed
        saveArtist(id, current.copy(portrait = portrait, portraitSource = source, portraitLocked = true))
    }
    fun allowMissingArtistFields(id: String, seed: ArtistNote = ArtistNote()) = artistTransaction {
        val current = storedArtist(id) ?: seed
        saveArtist(id, current.copy(biographyLocked = current.biographyLocked && current.biography.isNotBlank(), portraitLocked = current.portraitLocked && current.portrait != null))
    }
    /** Re-read and merge in one SQLite transaction as well as under the repository mutex. */
    fun saveAutomaticArtist(id: String, incoming: ArtistNote): Boolean = artistTransaction {
        val current = storedArtist(id) ?: ArtistNote()
        val updated = current.copy(
            biography = if (!current.biographyLocked && current.biography.isBlank()) incoming.biography else current.biography,
            source = if (!current.biographyLocked && current.biography.isBlank()) incoming.source else current.source,
            portrait = if (!current.portraitLocked && current.portrait == null) incoming.portrait else current.portrait,
            portraitSource = if (!current.portraitLocked && current.portrait == null) incoming.portraitSource else current.portraitSource,
        )
        saveArtist(id, updated)
        incoming.portrait != null && updated.portrait == incoming.portrait
    }
    fun saveArtist(id: String, note: ArtistNote) {
        check(writableDatabase.insertWithOnConflict("artists", null, ContentValues().apply {
            put("id", id); put("bio", note.biography); put("source", note.source); put("portrait", note.portrait); put("portrait_source", note.portraitSource); put("bio_locked", if (note.biographyLocked) 1 else 0); put("portrait_locked", if (note.portraitLocked) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Échec de sauvegarde artists." }
    }
}
