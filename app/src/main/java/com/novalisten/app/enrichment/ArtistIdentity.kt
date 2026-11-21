package com.novalisten.app.enrichment

import com.novalisten.app.data.*

/** Only the currently listened-to file's public metadata, never its URI, bytes or history. */
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
