package com.novalisten.app.data

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val MIN_TRACK_DURATION_MS = 60_000L

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val durationMs: Long,
    val dateAdded: Long,
    val year: Int,
    val number: Int,
    val fileName: String,
    val size: Long,
    val mimeType: String,
    val displayCover: String? = null,
) {
    val playable: Boolean get() = durationMs >= MIN_TRACK_DURATION_MS
    val pending: Boolean get() = durationMs <= 0
    val artwork: String get() = displayCover ?: "content://media/external/audio/albumart/$albumId"
    val artistKey: String get() = artist.normalized()
    val albumKey: String get() = "$albumId:${(albumArtist.ifBlank { artist }).normalized()}"
}

data class LocalPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    val createdAt: Long,
    val pinnedAt: Long? = null,
    val cover: String? = null,
)

data class ListenEvent(
    val id: Long,
    val trackId: String,
    val at: Long,
    val zone: String,
    val localDate: String,
    val localHour: Int,
)

data class SavedLyrics(val original: String = "", val french: String = "", val originalSource: String = "", val frenchSource: String = "", val originalTiming: String = "")
data class ArtistNote(val biography: String = "", val source: String = "", val portrait: String? = null, val portraitSource: String = "")
data class SavedCover(val uri: String, val source: String)

data class LocalData(
    val favorites: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val events: List<ListenEvent> = emptyList(),
    val lyrics: Map<String, SavedLyrics> = emptyMap(),
    val artists: Map<String, ArtistNote> = emptyMap(),
    val covers: Map<String, SavedCover> = emptyMap(),
    val identities: Map<String, String> = emptyMap(),
    val automaticLyrics: Map<String, AutomaticLyricRecord> = emptyMap(),
)

data class LibraryState(
    val permission: Boolean = false,
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
)

enum class TrackSort(val label: String) {
    ADDED("Date d’ajout"), TITLE("Titre"), ARTIST("Artiste"), ALBUM("Album"), DURATION("Durée"), YEAR("Année")
}

fun String.normalized(): String = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").lowercase(java.util.Locale.ROOT)

fun visibleTracks(tracks: List<Track>, hidden: Set<String>): List<Track> =
    tracks.filter { (it.playable || it.pending) && it.id !in hidden }

fun playableTracks(tracks: List<Track>, hidden: Set<String>): List<Track> =
    tracks.filter { it.playable && it.id !in hidden }

fun searchTracks(tracks: List<Track>, query: String): List<Track> {
    val q = query.normalized()
    if (q.isEmpty()) return tracks
    return tracks.filter { listOf(it.title, it.artist, it.album, it.fileName).any { field -> q in field.normalized() } }
}

fun sortTracks(tracks: List<Track>, sort: TrackSort, descending: Boolean): List<Track> {
    val comparator = when (sort) {
        TrackSort.ADDED -> compareBy<Track> { it.dateAdded }
        TrackSort.TITLE -> compareBy { it.title.normalized() }
        TrackSort.ARTIST -> compareBy { it.artist.normalized() }
        TrackSort.ALBUM -> compareBy { it.album.normalized() }
        TrackSort.DURATION -> compareBy { if (it.pending) Long.MAX_VALUE else it.durationMs }
        TrackSort.YEAR -> compareBy { it.year }
    }.thenBy { it.title.normalized() }.thenBy { it.id }
    return tracks.sortedWith(if (descending) comparator.reversed() else comparator)
}

/** New references are prepended without moving any existing reference. */
fun prependUnique(existing: List<String>, incoming: List<String>): List<String> {
    val existingIds = existing.toHashSet()
    return incoming.distinct().filterNot { it in existingIds } + existing
}

/** Retain the current item and all the past; move future duplicates to the new block. */
fun insertNext(existing: List<String>, current: Int, incoming: List<String>): List<String> {
    val block = incoming.distinct()
    if (existing.isEmpty() || current !in existing.indices) return block
    val blockIds = block.toHashSet()
    return existing.take(current + 1) + block + existing.drop(current + 1).filterNot { it in blockIds }
}

fun inLocalDayWindow(event: ListenEvent, days: Long, today: LocalDate = LocalDate.now()): Boolean =
    runCatching { LocalDate.parse(event.localDate) in today.minusDays(days - 1)..today }.getOrDefault(false)

fun eventAt(trackId: String, at: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): ListenEvent {
    val local = Instant.ofEpochMilli(at).atZone(zone)
    return ListenEvent(0, trackId, at, zone.id, local.toLocalDate().toString(), local.hour)
}

fun durationLabel(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) / 1000)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

fun totalDurationLabel(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
