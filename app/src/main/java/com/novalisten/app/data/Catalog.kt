package com.novalisten.app.data

import java.util.Locale

/** Original credits remain in Track.artist. These associations only affect NovaListen's index. */
data class ArtistCredit(val name: String, val mbid: String = "") {
    val id: String get() = if (validMusicId(mbid)) "mbid:${mbid.lowercase(Locale.ROOT)}" else name.artistNameKey()
}
fun validMusicId(value: String): Boolean = value.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))
fun String.artistNameKey(): String = CatalogText.key(this)

object ArtistCredits {
    private val featuring = Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring)\\s+")
    private val protected = setOf("ac/dc", "+/-", "earth, wind & fire", "tyler, the creator", "and/or", "simon & garfunkel", "kool & the gang", "florence & the machine", "of monsters and men", "angus & julia stone", "belle & sebastian", "years & years")
    /**
     * Découpe un champ multi-artistes.
     *
     * « feat. » et « ; » sont des séparateurs explicites : ils sont toujours découpés.
     * « & », « , » et « / » ne le sont que si les participants sont connus ou si l'un d'eux
     * est déjà un artiste de la bibliothèque — sinon « Rihanna, Drake » resterait une seule
     * carte au lieu de deux, et « Simon & Garfunkel » serait cassé.
     */
    fun parse(raw: String, knownNames: Set<String> = emptySet()): List<ArtistCredit> {
        val value = CatalogText.display(raw).take(1000)
        if (value.isBlank()) return emptyList()
        if (value.artistNameKey() in protected) return listOf(ArtistCredit(value))
        // Deux niveaux de preuve. « / » est un séparateur fort : un seul participant connu suffit.
        // « & » et « , » peuvent appartenir à un nom de groupe : il faut que tous soient connus,
        // sinon « Simon & Garfunkel » ou « Anyma & LISA » seraient cassés sans preuve.
        fun anyKnown(names: List<String>) = knownNames.isNotEmpty() && names.any { it.artistNameKey() in knownNames }
        fun allKnown(names: List<String>) = knownNames.isNotEmpty() && names.all { it.artistNameKey() in knownNames }
        val explicit = value.replace(featuring, ";").split(';', '\u0000').map { it.trim() }.filter { it.isNotBlank() }
        return explicit.flatMap { part ->
            val slash = part.split('/').map(String::trim)
            val splitSlash = slash.size in 2..8 && slash.all { it.length > 1 } &&
                (part.contains(" / ") || anyKnown(slash)) &&
                part.artistNameKey() !in protected
            val amp = part.split(Regex("\\s+&\\s+|,\\s+")).map(String::trim)
            when {
                splitSlash -> slash
                amp.size in 2..8 && amp.all { it.isNotBlank() } && allKnown(amp) && part.artistNameKey() !in protected -> amp
                else -> listOf(part)
            }.map { ArtistCredit(it) }
        }.distinctBy { it.id }.take(16)
    }
    /**
     * Premier artiste d'un champ « album artist » : « Katy Perry, Doechii » -> « Katy Perry ».
     *
     * Quand c'est possible, préférer [ArtistSplitter.split], qui applique exactement les mêmes
     * règles que la fiche artiste. Cette fonction reste le filet de sécurité pour les appelants
     * qui n'ont pas de règles sous la main.
     */
    fun albumHead(raw: String): String = albumHead(raw, SplitRules())

    /**
     * Identité d'album alignée sur la fiche artiste.
     *
     * L'ancienne découpe ne coupait « / » que s'il était entouré d'espaces : « Rihanna/ Drake »
     * restait donc entier, l'album recevait un troisième nom inexistant, et « ANTI » se séparait
     * de « ANTI (Deluxe) ». Une seule règle pour les deux vues supprime cette contradiction.
     */
    fun albumHead(raw: String, rules: SplitRules): String {
        val value = CatalogText.display(raw).take(1000)
        if (value.isBlank()) return value
        val parts = ArtistSplitter.split(value, rules)
        if (parts.size > 1) return parts.first()
        // Protégé, collectif ou non découpable : c'est le nom entier qui identifie l'album.
        // Reste le featuring, que les séparateurs de l'utilisateur ne couvrent pas toujours.
        if (value.artistNameKey() in protected) return value
        val beforeFeature = value.replace(featuring, ";").substringBefore(';').trim()
        return beforeFeature.ifBlank { value }
    }

    fun mayBeMultiple(raw: String): Boolean = raw.artistNameKey() !in protected &&
        (featuring.containsMatchIn(raw) || raw.any { it == ';' || it == '\u0000' || it == '/' || it == ',' } || Regex("\\s+&\\s+").containsMatchIn(raw))
    /** Candidates are not assignments: unknown punctuation credits need recording evidence. */
    fun candidates(raw: String): List<String> {
        if (raw.artistNameKey() in protected) return listOf(raw.trim())
        return CatalogText.display(raw).replace(featuring, ";").split(Regex(";|\\u0000|\\s+&\\s+|,\\s+|\\s+/\\s+"))
            .map(String::trim).filter(String::isNotBlank).distinct().take(16)
    }
    fun forTrack(track: Track, choices: CatalogChoices = CatalogChoices(), knownNames: Set<String> = emptySet()): List<ArtistCredit> {
        // 1. Choix manuel : toujours prioritaire.
        choices.artists[track.id]?.let { return it }
        // 2. Crédits structurés qui contiennent déjà plusieurs artistes : ils font foi.
        val native = track.credits.takeIf { it.isNotEmpty() }
        val resolved = choices.resolvedArtists[track.id]?.takeIf { it.matches(track) }?.credits
        native?.takeIf { it.size > 1 }?.let { return it }
        resolved?.takeIf { it.size > 1 }?.let { return it }
        // 3. Un crédit unique n'est qu'un texte de plus : ce sont vos règles qui décident.
        //    C'est exactement le cas de « Rihanna, Drake » écrit dans un seul tag.
        val single = native?.firstOrNull()?.name ?: resolved?.firstOrNull()?.name ?: track.artist
        val parts = ArtistSplitter.split(single, choices.split)
        if (parts.size > 1) return parts.map { ArtistCredit(CatalogText.display(it)) }
        // 4. Sinon on conserve le crédit d'origine, identifiant MusicBrainz compris.
        return native ?: resolved ?: parse(track.artist, knownNames)
    }
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
data class ResolvedArtistCredits(val artist: String, val title: String, val album: String, val duration: Long, val credits: List<ArtistCredit>, val recordingId: String = "") {
    fun matches(track: Track): Boolean = artist == track.artist && title == track.title && album == track.album && duration == track.durationMs && recordingId == track.recordingId
}
data class CatalogChoices(
    val artists: Map<String, List<ArtistCredit>> = emptyMap(),
    val families: Map<String, AlbumFamily> = emptyMap(),
    val kinds: Map<String, ReleaseKind> = emptyMap(),
    val recordings: Map<String, String> = emptyMap(),
    val separated: Set<String> = emptySet(),
    val resolvedArtists: Map<String, ResolvedArtistCredits> = emptyMap(),
    /** Règles de découpage choisies dans les Paramètres ; elles font partie de l'identité de l'index. */
    val split: SplitRules = SplitRules(),
)
data class ArtistGroup(val id: String, val name: String, val tracks: List<Track>, val mbid: String = "", val legacyId: String = name.artistNameKey(), val inheritLegacy: Boolean = true,
                       val legacyIds: Set<String> = setOf(legacyId))
data class AlbumEdition(val id: String, val name: String, val artist: String, val tracks: List<Track>, val kind: ReleaseKind,
                        val artistCredits: List<ArtistCredit> = emptyList(), val fragmentKey: String = id) {
    val artwork: String? get() = (tracks.firstOrNull { it.displayCover != null } ?: tracks.firstOrNull())?.artwork
    val releaseGroupId: String get() = tracks.map { it.releaseGroupId.lowercase(Locale.ROOT) }.distinct().singleOrNull()?.takeIf(::validMusicId).orEmpty()
    val year: Int get() = tracks.map { it.originalYear.takeIf { year -> year in 1000..2999 } ?: it.year.takeIf { year -> year in 1000..2999 } ?: 0 }.filter { it > 0 }.minOrNull() ?: 0
}
data class AlbumGroup(val id: String, val name: String, val artist: String, val tracks: List<Track>,
                      val editions: List<AlbumEdition>, val originalId: String, val representativeId: String,
                      val kind: ReleaseKind, val familyId: String? = null, val automaticFamily: Boolean = false, val groupingReason: String = "",
                      val editionCount: Int = editions.map { it.fragmentKey }.distinct().size) {
    val representative: AlbumEdition get() = editions.first { it.id == representativeId }
    val artwork: String? get() = representative.artwork
    fun containsEdition(id: String): Boolean = editions.any { it.id == id }
    fun containsTrack(id: String): Boolean = editions.any { edition -> edition.tracks.any { it.id == id } }
    val year: Int get() = editions.first { it.id == originalId }.year
}
enum class CatalogSort(val label: String) { NAME_ASC("Nom A → Z"), NAME_DESC("Nom Z → A"), MOST_TRACKS("Plus de titres → moins de titres"), FEWEST_TRACKS("Moins de titres → plus de titres") }
fun <T> sortCatalog(values: List<T>, sort: CatalogSort, name: (T) -> String, count: (T) -> Int): List<T> {
    // Compute normalization once, not on every comparison (or every visible card).
    val entries = values.map { Triple(it, name(it).normalized(), count(it)) }
    val alpha = compareBy<Triple<T, String, Int>> { it.second }
    val order = when (sort) {
        CatalogSort.NAME_ASC -> alpha
        CatalogSort.NAME_DESC -> alpha.reversed()
        CatalogSort.MOST_TRACKS -> compareByDescending<Triple<T, String, Int>> { it.third }.then(alpha)
        CatalogSort.FEWEST_TRACKS -> compareBy<Triple<T, String, Int>> { it.third }.then(alpha)
    }
    return entries.sortedWith(order).map { it.first }
}

fun artistGroups(tracks: List<Track>, choices: CatalogChoices = CatalogChoices()): List<ArtistGroup> {
    val eligible = tracks.filter { it.playable }.distinctBy { it.id }
    val known = buildSet {
        eligible.forEach { track ->
            if (!ArtistCredits.mayBeMultiple(track.artist)) add(track.artist.artistNameKey())
            (track.credits + track.albumCredits + choices.artists[track.id].orEmpty() + choices.resolvedArtists[track.id]?.takeIf { it.matches(track) }?.credits.orEmpty()).forEach { add(it.name.artistNameKey()) }
            track.catalogAlbumArtist.takeIf { !ArtistCredits.mayBeMultiple(it) }?.let { add(it.artistNameKey()) }
        }
    }
    val parsed = eligible.associateWith { ArtistCredits.forTrack(it, choices, known) }
    val idsByName = parsed.values.flatten().filter { validMusicId(it.mbid) }.groupBy { it.name.artistNameKey() }
        .mapValues { (_, values) -> values.map { it.mbid.lowercase(Locale.ROOT) }.distinct() }
    val credits = linkedMapOf<String, ArtistCredit>()
    val groups = linkedMapOf<String, MutableList<Track>>()
    parsed.forEach { (track, artists) -> artists.forEach { value ->
        val knownId = idsByName[value.name.artistNameKey()]?.singleOrNull()
        val artist = value.copy(name = CatalogText.display(value.name), mbid = if (value.mbid.isBlank() && knownId != null) knownId else value.mbid)
        credits.putIfAbsent(artist.id, artist)
        groups.getOrPut(artist.id) { mutableListOf() }.add(track)
    } }
    return groups.map { (id, songs) ->
        val credit = credits.getValue(id)
        val aliases = buildSet {
            add(credit.name.artistNameKey())
            songs.forEach { song ->
                val participants = parsed.getValue(song)
                participants.filter { it.name.artistNameKey() == credit.name.artistNameKey() }.forEach { add(it.name.normalized()) }
                // A repaired SINGLE artist may inherit its old spelling. Never let a former
                // collective profile become the biography or photo of an individual guest.
                if (participants.size == 1 && song.artist.artistNameKey() == credit.name.artistNameKey()) add(song.artist.normalized())
            }
        }
        ArtistGroup(id, credit.name, songs.distinctBy { it.id }, credit.mbid,
            inheritLegacy = (idsByName[credit.name.artistNameKey()]?.size ?: 0) <= 1, legacyIds = aliases)
    }.sortedBy { it.name.normalized() }
}
/** Existing/manual fields outrank automatic aliases independently, including intentional empties. */
fun protectArtistNote(current: ArtistNote?, seed: ArtistNote): ArtistNote {
    val base = current ?: seed
    return base.copy(
        biography = if (!base.biographyLocked && seed.biographyLocked) seed.biography else base.biography,
        source = if (!base.biographyLocked && seed.biographyLocked) seed.source else base.source,
        portrait = if (!base.portraitLocked && seed.portraitLocked) seed.portrait else base.portrait,
        portraitSource = if (!base.portraitLocked && seed.portraitLocked) seed.portraitSource else base.portraitSource,
        biographyLocked = base.biographyLocked || seed.biographyLocked,
        portraitLocked = base.portraitLocked || seed.portraitLocked,
    )
}
fun artistNote(group: ArtistGroup, data: LocalData): ArtistNote {
    val current = data.artists[group.id]
    // Once this identity has its own row, its latest explicit edits/unlocks are authoritative.
    // Legacy aliases are a migration seed, never a reason to re-lock a field the user allowed.
    if (current != null || !group.inheritLegacy) return current ?: ArtistNote()
    val aliases = group.legacyIds.mapNotNull { data.artists[it] }
    return aliases.fold(current ?: aliases.firstOrNull() ?: ArtistNote()) { note, alias -> protectArtistNote(note, alias) }
}

/** Edition relationships are local; recording identity remains separate from album title matching. */
fun albumGroups(tracks: List<Track>, choices: CatalogChoices = CatalogChoices()): List<AlbumGroup> {
    val eligible = tracks.filter { it.playable }.distinctBy { it.id }
    // Réparation A : un invité crédité ne crée pas un second album. « Rihanna / JAY-Z » sur
    // « Talk That Talk » ne doit pas faire de ce disque l'album de JAY-Z : l'identité d'album
    // est celle de l'artiste principal, et l'invité reste visible sur la piste.
    // L'identité est construite dès le départ plutôt que par remplacement de chaîne : un nom
    // court présent dans un nom long ne peut pas produire une clé erronée.
    fun albumIdentity(track: Track, rules: SplitRules = SplitRules()): String {
        val raw = track.albumArtist.ifBlank { track.artist }
        // Un featuring explicite dans l'artiste d'album est un crédit d'invité, pas une identité :
        // « Katy Perry feat. Doechii » reste l'album de Katy Perry, sans second disque.
        // Les règles de l'utilisateur décident. Sans découpage possible (nom protégé, groupe),
        // c'est le nom entier qui identifie l'album ; sinon c'est son premier participant.
        val identity = ArtistCredits.albumHead(raw, rules)
        // Même logique que editionKey, mais sur l'artiste d'album nettoyé : c'est cette clé qui
        // décide de l'édition, pas la chaîne brute qui contenait l'invité.
        val album = if (!validMusicId(track.releaseId) && (track.albumId <= 0 || track.album.normalized() in setOf("", "album inconnu", "unknown album"))) "unidentified-album:${track.id}"
            else track.albumId.toString()
        val release = if (validMusicId(track.releaseId)) ":release:" + track.releaseId.lowercase(Locale.ROOT) else ""
        return "$album:${identity.normalized()}$release"
    }
    fun nativeAlbumArtists(track: Track): String? = track.albumCredits.map { it.mbid.lowercase(Locale.ROOT) }
        .takeIf { ids -> ids.isNotEmpty() && ids.all(::validMusicId) }?.sorted()?.joinToString("+")
    /**
     * Deux identifiants natifs contradictoires restent deux disques (garde-fou conservé).
     * Un ensemble qui en contient un autre est le même disque avec un invité ajouté :
     * « Rihanna » et « Rihanna + JAY-Z » ne font pas deux albums.
     */
    val sources: List<Pair<String, List<Track>>> = eligible.groupBy { albumIdentity(it, choices.split) }
        .map { (identity, songs) -> identity to songs }
        .flatMap { (id, songs) ->
            val keys = songs.map { track ->
                track.albumCredits.map { it.mbid.lowercase(Locale.ROOT) }
                    .takeIf { ids -> ids.isNotEmpty() && ids.all(::validMusicId) }?.toSet().orEmpty()
            }
            if (keys.map { it }.distinct().size <= 1) return@flatMap listOf(id to songs)
            val parent = songs.indices.toMutableList()
            fun root(i: Int): Int { var at = i; while (parent[at] != at) at = parent[at]; return at }
            for (a in songs.indices) for (b in a + 1 until songs.size) {
                val one = keys[a]; val two = keys[b]
                // Invité ajouté : l'un est strictement inclus dans l'autre ET plus petit.
                val compatible = one.isEmpty() || two.isEmpty() ||
                    (one.size != two.size && (one.containsAll(two) || two.containsAll(one)))
                if (compatible && root(a) != root(b)) parent[root(b)] = root(a)
            }
            // Un fichier sans identifiant rejoint le groupe majoritaire, jamais un groupe au hasard.
            var groups: List<List<Int>> = songs.indices.groupBy(::root).values.toList()
            val unkeyed = groups.filter { indices -> indices.all { keys[it].isEmpty() } }
            if (unkeyed.isNotEmpty() && unkeyed.size < groups.size) {
                val kept = groups.filterNot { it in unkeyed }
                val biggest = kept.maxBy { it.size }
                val merged = kept.map { if (it === biggest) it + unkeyed.flatten() else it }
                groups = merged
            }
            groups.mapIndexed { position, indices ->
                // Sans suffixe, deux sous-groupes porteraient le même identifiant et seraient
                // refusionnés plus loin : le suffixe dépend du nombre de sous-groupes, pas de leur taille.
                val key = if (groups.size > 1) "$id:invites:$position" else id
                key to indices.map { songs[it] }
            }
        }
    val editions = sources.map { (id, songs) ->
        val tagged = songs.map { it.releaseKind }.filter { it != ReleaseKind.UNKNOWN }.distinct().singleOrNull() ?: ReleaseKind.UNKNOWN
        val artist = ArtistCredits.albumHead(songs.first().catalogAlbumArtist, choices.split)
        val nativeCredits = songs.map { it.albumCredits }.filter { it.isNotEmpty() }.distinct().singleOrNull()
        AlbumEdition(id, CatalogText.display(songs.first().album), CatalogText.display(artist),
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
        val initial = ordered.first()
        val original = ordered.firstOrNull { it.id == family?.original }
            ?: ordered.filter { it.fragmentKey == initial.fragmentKey }.maxByOrNull { it.tracks.size } ?: initial
        val representative = ordered.firstOrNull { it.id == family?.representative } ?: original
        val sourceOrder = listOf(original) + ordered.filterNot { it.id == original.id }
        val unique = mutableListOf<Track>()
        val recordingIndex = mutableMapOf<String, MutableList<Track>>()
        // Android fragments of ONE edition share disc/track order. True editions still append
        // their additions after the chosen original. Unproven file copies are never discarded.
        val logicalEditions = sourceOrder.groupBy { it.fragmentKey }.values
        logicalEditions.forEach { fragments -> fragments.flatMap { it.tracks }
            .sortedWith(compareBy<Track> { it.discNumber.coerceAtLeast(1) }.thenBy { it.number.takeIf { n -> n > 0 } ?: Int.MAX_VALUE })
            .forEach { track ->
            val keys = buildList {
                add("file:${track.id}")
                if (validMusicId(track.recordingId)) add("mb:${track.recordingId.lowercase(Locale.ROOT)}")
                if (track.isrc.isNotBlank()) add("isrc:${track.isrc}")
                choices.recordings[track.id]?.let { add("chosen:$it") }
            }
            if (keys.none { key -> recordingIndex[key].orEmpty().any { sameRecording(it, track, choices) } }) {
                unique += track
                keys.forEach { recordingIndex.getOrPut(it) { mutableListOf() }.add(track) }
            }
        } }
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
    return (sameId || sameIsrc) && CatalogText.key(first.title) == CatalogText.key(second.title) &&
        identities(firstArtists) == identities(secondArtists) &&
        kotlin.math.abs(first.durationMs - second.durationMs) <= 2000
}
fun validateFamily(family: AlbumFamily, choices: CatalogChoices) {
    require(family.id.isNotBlank() && family.editions.size in 2..100 && family.original in family.editions && family.representative in family.editions)
    require(choices.families.values.none { it.id != family.id && it.editions.any(family.editions::contains) }) { "Une édition appartient déjà à un autre regroupement. Séparez-le d’abord." }
}
