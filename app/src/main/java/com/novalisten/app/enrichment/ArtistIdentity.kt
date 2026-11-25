package com.novalisten.app.enrichment

import com.novalisten.app.data.*

/** Public catalogue context only: no file URI, audio bytes or listening history. */
data class ArtistLocalContext(val title: String, val album: String, val durationMs: Long, val recordingId: String = "")

fun artistRequestsForTrack(track: Track, local: LocalData, groups: List<ArtistGroup>): List<ArtistRequest> {
    val native = ArtistCredits.forTrack(track, local.catalog)
    return groups.filter { group -> group.tracks.any { it.id == track.id } }.map { group ->
        // A MBID inferred from a different file with the same name is not local evidence for THIS file.
        val chosen = local.identities["artist:${group.id}"]
            ?: if (group.inheritLegacy) group.legacyIds.mapNotNull { local.identities["artist:$it"] }.distinct().singleOrNull() else null
        val fromThisFile = native.filter { it.name.artistNameKey() == group.name.artistNameKey() || (validMusicId(it.mbid) && it.mbid.equals(group.mbid, true)) }
            .map { it.mbid }.filter(::validMusicId).distinct().singleOrNull()
        ArtistRequest(group.id, group.name, chosen?.takeIf(::validMusicId) ?: fromThisFile.orEmpty(),
            ArtistLocalContext(track.title, track.album, track.durationMs, track.recordingId))
    }
}

/**
 * Stable context per artist; prefer a native identity, then rotate only when the index changes.
 *
 * Le calcul complet parcourt tous les artistes et tous leurs morceaux : il ne doit donc pas
 * dépendre de la liste des cartes visibles, qui change à chaque geste de défilement. La visibilité
 * ne sert plus qu'au **tri** des travaux déjà calculés — une liste ne peut pas modifier un contexte
 * d'artiste. C'est ce qui faisait relancer tout le travail à chaque mouvement de l'onglet Artistes.
 */
fun artistLibraryWorks(index: CatalogSnapshot, local: LocalData, playing: String?, visible: List<String>, settings: Any? = null): List<ArtistWork> =
    artistWorksCache.get(index, local, settings)?.let { order(it, index, playing, visible) }
        ?: buildWorks(index, local).also { artistWorksCache.put(index, local, settings, it) }
            .let { order(it, index, playing, visible) }

/** Réordonne des travaux déjà calculés : moins cher que de les recalculer. */
private fun order(works: List<ArtistWork>, index: CatalogSnapshot, playing: String?, visible: List<String>): List<ArtistWork> {
    val playingArtists = index.artistsByTrack[playing].orEmpty().map { it.id }.toSet()
    val ranks = visible.withIndex().associate { it.value to it.index }
    fun rank(work: ArtistWork): Int {
        val id = work.artist.id
        return if (id in playingArtists) 0 else if (id in ranks) 1 else 2
    }
    return works.sortedWith(compareBy<ArtistWork> { rank(it) }
        .thenBy { ranks[it.artist.id] ?: Int.MAX_VALUE }
        .thenBy { it.artist.name.artistNameKey() })
}

/**
 * Cache à une entrée : il ne sert que pendant qu'un même index et un même état local sont valides.
 * Toute modification du catalogue ou des réglages invalide l'entrée, donc rien ne peut devenir
 * périmé silencieusement.
 */
private object artistWorksCache {
    private var cachedIndex: CatalogSnapshot? = null
    private var cachedLocal: LocalData? = null
    private var value: List<ArtistWork> = emptyList()

    /** Les réglages d'enrichissement changent quels travaux sont utiles : ils font partie de la clé. */
    private var settingsKey: Any? = null

    fun get(index: CatalogSnapshot, local: LocalData, settings: Any?): List<ArtistWork>? =
        if (index === cachedIndex && local === cachedLocal && settings == settingsKey) value else null

    fun put(index: CatalogSnapshot, local: LocalData, settings: Any?, works: List<ArtistWork>) {
        cachedIndex = index; cachedLocal = local; settingsKey = settings; value = works
    }
}

private fun buildWorks(index: CatalogSnapshot, local: LocalData): List<ArtistWork> =
    index.artists.mapNotNull { group ->
        val track = group.tracks.firstOrNull { song ->
            ArtistCredits.forTrack(song, local.catalog).any { validMusicId(it.mbid) && (it.name.artistNameKey() == group.name.artistNameKey() || it.mbid.equals(group.mbid, true)) }
        } ?: group.tracks.firstOrNull { validMusicId(it.recordingId) }
            ?: group.tracks.firstOrNull { song -> song.title.isNotBlank() && song.title.length <= 300 && song.album.isNotBlank() && song.album.length <= 300 &&
                song.album.artistNameKey() !in setOf("album inconnu", "unknown album") }
            ?: group.tracks.firstOrNull() ?: return@mapNotNull null
        artistRequestsForTrack(track, local, listOf(group)).singleOrNull()?.let { ArtistWork(track.id, it) }
    }
