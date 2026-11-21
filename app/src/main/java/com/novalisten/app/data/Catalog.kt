package com.novalisten.app.data

import java.util.Locale

/** Original credits remain in Track.artist. These associations only affect NovaListen's index. */
data class ArtistCredit(val name: String, val mbid: String = "") {
    val id: String get() = if (validMusicId(mbid)) "mbid:${mbid.lowercase(Locale.ROOT)}" else name.artistNameKey()
}
fun validMusicId(value: String): Boolean = value.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))
fun String.artistNameKey(): String = normalized().replace(Regex("\\s+"), " ")

object ArtistCredits {
    private val featuring = Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring)\\s+")
    private val protected = setOf("ac/dc", "+/-", "earth, wind & fire", "tyler, the creator", "and/or")
    fun parse(raw: String, knownNames: Set<String> = emptySet()): List<ArtistCredit> {
        val value = raw.trim().take(1000)
        if (value.isBlank()) return emptyList()
        if (value.artistNameKey() in protected) return listOf(ArtistCredit(value))
        val explicit = value.replace(featuring, ";").split(';', '\u0000').map { it.trim() }.filter { it.isNotBlank() }
        return explicit.flatMap { part ->
            val slash = part.split('/').map(String::trim)
            val splitSlash = slash.size in 2..8 && slash.all { it.length > 1 } &&
                (part.contains(" / ") || slash.any { it.artistNameKey() in knownNames }) &&
                part.artistNameKey() !in protected
            val amp = part.split(Regex("\\s+&\\s+")).map(String::trim)
            when {
                splitSlash -> slash
                amp.size in 2..8 && amp.all { it.artistNameKey() in knownNames } && part.artistNameKey() !in protected -> amp
                else -> listOf(part)
            }.map { ArtistCredit(it) }
        }.distinctBy { it.id }.take(16)
    }
    fun mayBeMultiple(raw: String): Boolean = raw.artistNameKey() !in protected &&
        (featuring.containsMatchIn(raw) || raw.any { it == ';' || it == '\u0000' || it == '/' } || Regex("\\s+&\\s+").containsMatchIn(raw))
    fun forTrack(track: Track, choices: CatalogChoices = CatalogChoices(), knownNames: Set<String> = emptySet()): List<ArtistCredit> =
        choices.artists[track.id] ?: track.credits.takeIf { it.isNotEmpty() } ?: parse(track.artist, knownNames)
}

enum class ReleaseKind(val label: String) {
    ALBUM("Album"), EP("EP"), MIXTAPE("Mixtape"), SINGLE("Single"), COMPILATION("Compilation"), UNKNOWN("Type à préciser");
    companion object {
        fun fromTag(raw: String): ReleaseKind {
            val tokens = raw.lowercase(Locale.ROOT).split(Regex("[;/,\\u0000]+| / ")).map(String::trim)
            return when {
                tokens.any { it in setOf("mixtape", "mixtape/street", "street") } || raw.contains("mixtape/street", true) -> MIXTAPE
                "compilation" in tokens -> COMPILATION
                "ep" in tokens -> EP
                "single" in tokens -> SINGLE
                "album" in tokens -> ALBUM
                else -> UNKNOWN
            }
        }
    }
}

data class AlbumFamily(val id: String, val editions: Set<String>, val original: String, val representative: String = original)
data class CatalogChoices(
    val artists: Map<String, List<ArtistCredit>> = emptyMap(),
    val families: Map<String, AlbumFamily> = emptyMap(),
    val kinds: Map<String, ReleaseKind> = emptyMap(),
    val recordings: Map<String, String> = emptyMap(),
    val separated: Set<String> = emptySet(),
)
data class ArtistGroup(val id: String, val name: String, val tracks: List<Track>, val mbid: String = "", val legacyId: String = name.artistNameKey(), val inheritLegacy: Boolean = true)
data class AlbumEdition(val id: String, val name: String, val artist: String, val tracks: List<Track>, val kind: ReleaseKind,
                        val artistCredits: List<ArtistCredit> = emptyList()) {
    val artwork: String? get() = (tracks.firstOrNull { it.displayCover != null } ?: tracks.firstOrNull())?.artwork
    val releaseGroupId: String get() = tracks.map { it.releaseGroupId.lowercase(Locale.ROOT) }.distinct().singleOrNull()?.takeIf(::validMusicId).orEmpty()
    val year: Int get() = tracks.map { it.originalYear.takeIf { year -> year in 1000..2999 } ?: it.year.takeIf { year -> year in 1000..2999 } ?: 0 }.filter { it > 0 }.minOrNull() ?: 0
}
data class AlbumGroup(val id: String, val name: String, val artist: String, val tracks: List<Track>,
                      val editions: List<AlbumEdition>, val originalId: String, val representativeId: String,
                      val kind: ReleaseKind, val familyId: String? = null, val automaticFamily: Boolean = false, val groupingReason: String = "") {
    val representative: AlbumEdition get() = editions.first { it.id == representativeId }
    val artwork: String? get() = representative.artwork
    fun containsEdition(id: String): Boolean = editions.any { it.id == id }
    fun containsTrack(id: String): Boolean = editions.any { edition -> edition.tracks.any { it.id == id } }
    val year: Int get() = editions.first { it.id == originalId }.year
}
enum class CatalogSort(val label: String) { NAME_ASC("Nom A → Z"), NAME_DESC("Nom Z → A"), MOST_TRACKS("Plus de titres → moins de titres"), FEWEST_TRACKS("Moins de titres → plus de titres") }
fun <T> sortCatalog(values: List<T>, sort: CatalogSort, name: (T) -> String, count: (T) -> Int): List<T> {
    val alpha = compareBy<T> { name(it).normalized() }.thenBy { name(it) }
    val order = when (sort) {
        CatalogSort.NAME_ASC -> alpha
        CatalogSort.NAME_DESC -> alpha.reversed()
        CatalogSort.MOST_TRACKS -> compareByDescending(count).then(alpha)
        CatalogSort.FEWEST_TRACKS -> compareBy(count).then(alpha)
    }
    return values.sortedWith(order)
}

fun artistGroups(tracks: List<Track>, choices: CatalogChoices = CatalogChoices()): List<ArtistGroup> {
    val eligible = tracks.filter { it.playable }.distinctBy { it.id }
    val known = eligible.map { it.artist.artistNameKey() }.toSet()
    val parsed = eligible.associateWith { ArtistCredits.forTrack(it, choices, known) }
    val idsByName = parsed.values.flatten().filter { validMusicId(it.mbid) }.groupBy { it.name.artistNameKey() }
        .mapValues { (_, values) -> values.map { it.mbid.lowercase(Locale.ROOT) }.distinct() }
    val credits = linkedMapOf<String, ArtistCredit>()
    val groups = linkedMapOf<String, MutableList<Track>>()
    parsed.forEach { (track, artists) -> artists.forEach { value ->
        val knownId = idsByName[value.name.artistNameKey()]?.singleOrNull()
        val artist = if (value.mbid.isBlank() && knownId != null) value.copy(mbid = knownId) else value
        credits.putIfAbsent(artist.id, artist)
        groups.getOrPut(artist.id) { mutableListOf() }.add(track)
    } }
    return groups.map { (id, songs) ->
        val credit = credits.getValue(id)
        ArtistGroup(id, credit.name, songs.distinctBy { it.id }, credit.mbid, inheritLegacy = (idsByName[credit.name.artistNameKey()]?.size ?: 0) <= 1)
    }.sortedBy { it.name.normalized() }
}
fun artistNote(group: ArtistGroup, data: LocalData): ArtistNote = data.artists[group.id]
    ?: if (group.inheritLegacy) data.artists[group.legacyId] ?: ArtistNote() else ArtistNote()

/** Edition relationships are local; recording identity remains separate from album title matching. */
fun albumGroups(tracks: List<Track>, choices: CatalogChoices = CatalogChoices()): List<AlbumGroup> {
    val eligible = tracks.filter { it.playable }.distinctBy { it.id }
    fun nativeAlbumArtists(track: Track): String? = track.albumCredits.map { it.mbid.lowercase(Locale.ROOT) }
        .takeIf { ids -> ids.isNotEmpty() && ids.all(::validMusicId) }?.sorted()?.joinToString("+")
    val sources = eligible.groupBy { it.editionKey }.flatMap { (id, songs) ->
        val identities = songs.mapNotNull(::nativeAlbumArtists).distinct()
        if (identities.size <= 1) listOf(id to songs)
        else songs.groupBy { nativeAlbumArtists(it) ?: "unidentified:${it.id}" }.map { (artist, files) -> "$id:album-artists:$artist" to files }
    }
    val editions = sources.map { (id, songs) ->
        val tagged = songs.map { it.releaseKind }.filter { it != ReleaseKind.UNKNOWN }.distinct().singleOrNull() ?: ReleaseKind.UNKNOWN
        val artist = songs.first().catalogAlbumArtist
        val nativeCredits = songs.map { it.albumCredits }.filter { it.isNotEmpty() }.distinct().singleOrNull()
        AlbumEdition(id, songs.first().album, artist,
            songs.sortedWith(compareBy<Track> { it.discNumber }.thenBy { it.number }.thenBy { it.title.normalized() }.thenBy { it.id }),
            choices.kinds[id] ?: choices.kinds[songs.first().albumKey] ?: tagged,
            nativeCredits ?: ArtistCredits.parse(artist))
    }
    return AlbumFamilies.group(editions, choices).map { bucket ->
        val versions = bucket.editions
        val family = bucket.family
        val ordered = versions.sortedWith(compareBy<AlbumEdition> { if (AlbumFamilies.isRequestedOriginal(it)) 0 else 1 }
            .thenBy { it.year.takeIf { year -> year > 0 } ?: Int.MAX_VALUE }
            .thenBy { if (AlbumFamilies.baseTitle(it.name) != AlbumFamilies.titleKey(it.name)) 1 else 0 }
            .thenBy { it.name.normalized() }.thenBy { it.id })
        val original = ordered.firstOrNull { it.id == family?.original } ?: ordered.first()
        val representative = ordered.firstOrNull { it.id == family?.representative } ?: original
        val sourceOrder = listOf(original) + ordered.filterNot { it.id == original.id }
        val unique = mutableListOf<Track>()
        sourceOrder.flatMap { it.tracks }.forEach { track -> if (unique.none { sameRecording(it, track, choices) }) unique += track }
        val kinds = ordered.map { it.kind }.filter { it != ReleaseKind.UNKNOWN }.distinct()
        AlbumGroup(bucket.id, representative.name, representative.artist, unique, ordered, original.id, representative.id,
            choices.kinds[bucket.id] ?: AlbumFamilies.requestedKind(ordered)
                ?: if (kinds.size == 1) kinds.first() else if (ordered.size == 1) representative.kind else ReleaseKind.UNKNOWN,
            family?.id, family == null && (ordered.size > 1 || bucket.id != representative.id), bucket.reason)
    }.sortedBy { it.name.normalized() }
}
fun sameRecording(first: Track, second: Track, choices: CatalogChoices): Boolean {
    if (first.id == second.id) return true
    val explicit = choices.recordings[first.id]
    if (first.id in choices.recordings || second.id in choices.recordings) return explicit != null && explicit == choices.recordings[second.id] && !explicit.startsWith("distinct:")
    // Native recording identity is useful only when title/credits/version and duration also agree.
    val id = first.recordingId.takeIf(::validMusicId)
    val sameId = id != null && id.equals(second.recordingId, true)
    val sameIsrc = first.isrc.matches(Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")) && first.isrc == second.isrc
    if (!sameId && !sameIsrc) return false
    val structuredNames = (first.credits + second.credits + choices.artists[first.id].orEmpty() + choices.artists[second.id].orEmpty()).map { it.name.artistNameKey() }.toSet()
    val firstArtists = ArtistCredits.forTrack(first, choices, structuredNames)
    val secondArtists = ArtistCredits.forTrack(second, choices, structuredNames)
    val idsByName = (firstArtists + secondArtists).filter { validMusicId(it.mbid) }.groupBy { it.name.artistNameKey() }
        .mapValues { (_, credits) -> credits.map { it.mbid.lowercase(Locale.ROOT) }.distinct().singleOrNull() }
    fun identities(artists: List<ArtistCredit>): List<String> = artists.map { artist ->
        if (artist.mbid.isBlank()) idsByName[artist.name.artistNameKey()]?.let { artist.copy(mbid = it).id } ?: artist.id else artist.id
    }.sorted()
    return (sameId || sameIsrc) && first.title.normalized() == second.title.normalized() &&
        identities(firstArtists) == identities(secondArtists) &&
        kotlin.math.abs(first.durationMs - second.durationMs) <= 2000
}
fun validateFamily(family: AlbumFamily, choices: CatalogChoices) {
    require(family.id.isNotBlank() && family.editions.size in 2..100 && family.original in family.editions && family.representative in family.editions)
    require(choices.families.values.none { it.id != family.id && it.editions.any(family.editions::contains) }) { "Une édition appartient déjà à un autre regroupement. Séparez-le d’abord." }
}
