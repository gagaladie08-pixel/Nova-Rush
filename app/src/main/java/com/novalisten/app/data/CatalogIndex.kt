package com.novalisten.app.data

/** Shared immutable index. UI cells never rebuild the catalogue or cross-scan all albums. */
data class CatalogSnapshot(
    val ready: Boolean = false,
    val tracks: Map<String, Track> = emptyMap(),
    val artists: List<ArtistGroup> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artistsByTrack: Map<String, List<ArtistGroup>> = emptyMap(),
    val albumsByArtist: Map<String, List<AlbumGroup>> = emptyMap(),
    val albumByTrack: Map<String, AlbumGroup> = emptyMap(),
    val sourceChoices: CatalogChoices = CatalogChoices(),
    val sourceHidden: Set<String> = emptySet(),
) {
    fun isCurrent(track: Track, local: LocalData): Boolean = ready && tracks[track.id] == track &&
        sourceChoices == local.catalog && sourceHidden == local.hidden
    companion object {
        fun build(tracks: List<Track>, hidden: Set<String>, choices: CatalogChoices): CatalogSnapshot {
            val playable = tracks.filter { it.playable && it.id !in hidden }.distinctBy { it.id }
            val artists = artistGroups(playable, choices)
            val albums = albumGroups(playable, choices)
            val artistsByTrack = mutableMapOf<String, MutableList<ArtistGroup>>()
            artists.forEach { artist -> artist.tracks.forEach { artistsByTrack.getOrPut(it.id) { mutableListOf() }.add(artist) } }
            val albumByTrack = mutableMapOf<String, AlbumGroup>()
            val albumsByArtist = mutableMapOf<String, MutableList<AlbumGroup>>()
            albums.forEach { album ->
                val participants = linkedSetOf<String>()
                album.editions.forEach { edition -> edition.tracks.forEach { track ->
                    albumByTrack[track.id] = album
                    artistsByTrack[track.id].orEmpty().forEach { participants += it.id }
                } }
                participants.forEach { albumsByArtist.getOrPut(it) { mutableListOf() }.add(album) }
            }
            return CatalogSnapshot(true, playable.associateBy { it.id }, artists, albums, artistsByTrack, albumsByArtist, albumByTrack, choices, hidden)
        }
    }
}
