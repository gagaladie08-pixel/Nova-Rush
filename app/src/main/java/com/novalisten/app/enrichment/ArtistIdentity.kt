package com.novalisten.app.enrichment

import com.novalisten.app.data.*

/** Public catalogue context only: no file URI, audio bytes or listening history. */
data class ArtistLocalContext(val title: String, val album: String, val durationMs: Long, val recordingId: String = "")

fun artistRequestsForTrack(track: Track, local: LocalData, groups: List<ArtistGroup>): List<ArtistRequest> {
    val native = ArtistCredits.forTrack(track, local.catalog)
    return groups.filter { group -> group.tracks.any { it.id == track.id } }.map { group ->
        // A MBID inferred from a different file with the same name is not local evidence for THIS file.
        val chosen = local.identities["artist:${group.id}"]
            ?: if (group.inheritLegacy) local.identities["artist:${group.legacyId}"] else null
        val fromThisFile = native.filter { it.name.artistNameKey() == group.name.artistNameKey() || (validMusicId(it.mbid) && it.mbid.equals(group.mbid, true)) }
            .map { it.mbid }.filter(::validMusicId).distinct().singleOrNull()
        ArtistRequest(group.id, group.name, chosen?.takeIf(::validMusicId) ?: fromThisFile.orEmpty(),
            ArtistLocalContext(track.title, track.album, track.durationMs, track.recordingId))
    }
}

/** Stable context per artist; prefer a native identity, then rotate only when the index changes. */
fun artistLibraryWorks(index: CatalogSnapshot, local: LocalData, playing: String?, visible: List<String>): List<ArtistWork> {
    val playingArtists = index.artistsByTrack[playing].orEmpty().map { it.id }.toSet()
    val ranks = visible.withIndex().associate { it.value to it.index }
    return index.artists.sortedWith(compareBy<ArtistGroup> { if (it.id in playingArtists) 0 else if (it.id in ranks) 1 else 2 }
        .thenBy { ranks[it.id] ?: Int.MAX_VALUE }.thenBy { it.name.artistNameKey() }).mapNotNull { group ->
        val track = group.tracks.firstOrNull { it.id == playing } ?: group.tracks.firstOrNull { song ->
            ArtistCredits.forTrack(song, local.catalog).any { validMusicId(it.mbid) && (it.name.artistNameKey() == group.name.artistNameKey() || it.mbid.equals(group.mbid, true)) }
        } ?: group.tracks.firstOrNull() ?: return@mapNotNull null
        artistRequestsForTrack(track, local, listOf(group)).singleOrNull()?.let { ArtistWork(track.id, it) }
    }
}
