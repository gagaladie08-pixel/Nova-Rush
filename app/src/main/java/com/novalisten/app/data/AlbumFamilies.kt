package com.novalisten.app.data

import java.util.Locale

/** Local presentation rules, not a remote discography or a title-only audio matcher. */
internal object AlbumFamilies {
    private data class Requested(val key: String, val artist: String, val original: String, val names: Set<String>)
    // These two relationships were explicitly requested, including the cross-release-group case.
    // Artist and release-kind guards still apply; a cover/single by a namesake is not this family.
    private val requested = listOf(
        Requested("the-fame", "lady gaga", "the fame", setOf("the fame", "the fame monster")),
        Requested("future-nostalgia", "dua lipa", "future nostalgia", setOf("future nostalgia", "future nostalgia moonlight edition", "future nostalgia the moonlight edition")),
    )
    private val editionWords = Regex("(?i)(?:deluxe(?: edition)?|expanded(?: edition)?|special edition|bonus(?: tracks| edition)?|(?:[0-9]+(?:st|nd|rd|th|e|ème)? )?anniversary(?: edition)?|reissue|réédition|(?:[0-9]{4} )?remaster(?:ed)?(?: edition)?)")
    private val trailingGroup = Regex("\\s*(?:\\(|\\[)([^()\\[\\]]+)(?:\\)|\\])\\s*$")
    private val trailingDash = Regex("\\s+[—–-]\\s+([^—–]+)$")

    fun titleKey(raw: String): String = CatalogText.key(raw).replace(Regex("[\\p{P}\\p{S}]+"), " ").replace(Regex("\\s+"), " ").trim()
    fun baseTitle(raw: String): String {
        var result = raw.trim()
        repeat(3) {
            val ending = trailingGroup.find(result) ?: trailingDash.find(result)
            if (ending != null && editionWords.matches(ending.groupValues[1].trim())) result = result.take(ending.range.first).trim()
        }
        return titleKey(result)
    }
    private fun requested(edition: AlbumEdition): Requested? {
        if (edition.kind in setOf(ReleaseKind.SINGLE, ReleaseKind.COMPILATION)) return null
        return requested.firstOrNull { rule ->
            edition.artist.artistNameKey() == rule.artist && baseTitle(edition.name) in rule.names &&
                edition.artistCredits.all { it.name.artistNameKey() == rule.artist }
        }
    }
    fun isRequestedOriginal(edition: AlbumEdition): Boolean = requested(edition)?.let { baseTitle(edition.name) == it.original } == true
    fun requestedKind(editions: List<AlbumEdition>): ReleaseKind? {
        val original = editions.firstOrNull(::isRequestedOriginal) ?: return null
        return if (editions.mapNotNull { requested(it)?.key }.distinct().size == 1 && editions.all { requested(it) != null })
            original.kind.takeIf { it != ReleaseKind.UNKNOWN } ?: ReleaseKind.ALBUM else null
    }

    /** Same-edition fragments need compatible contents, not just a matching album title. */
    fun fragments(one: AlbumEdition, two: AlbumEdition): Boolean {
        if (titleKey(one.name) != titleKey(two.name) || titleKey(one.name) in setOf("", "album inconnu", "unknown album")) return false
        val releasesOne = one.tracks.map { it.releaseId }.filter(::validMusicId).toSet()
        val releasesTwo = two.tracks.map { it.releaseId }.filter(::validMusicId).toSet()
        if (releasesOne.isNotEmpty() && releasesTwo.isNotEmpty() && releasesOne != releasesTwo) return false
        if (one.releaseGroupId.isNotEmpty() && two.releaseGroupId.isNotEmpty() && one.releaseGroupId != two.releaseGroupId) return false
        if (one.kind != ReleaseKind.UNKNOWN && two.kind != ReleaseKind.UNKNOWN && one.kind != two.kind) return false
        if (one.year > 0 && two.year > 0 && one.year != two.year) return false
        fun slots(edition: AlbumEdition) = edition.tracks.filter { it.number > 0 }.groupBy { (it.discNumber.coerceAtLeast(1) to it.number) }
        val a = slots(one); val b = slots(two)
        val common = a.keys.intersect(b.keys)
        if (common.any { slot -> a.getValue(slot).any { first -> b.getValue(slot).any { second ->
            CatalogText.key(first.title) != CatalogText.key(second.title) || kotlin.math.abs(first.durationMs - second.durationMs) > 2000
        } } }) return false
        return (a.keys + b.keys).size >= 2 || common.isNotEmpty() ||
            (a.isEmpty() && b.isEmpty() && one.year > 0 && one.year == two.year)
    }
    private fun markFragments(editions: List<AlbumEdition>, separated: Set<String>): List<AlbumEdition> {
        val parent = editions.indices.toMutableList()
        fun root(index: Int): Int { var at = index; while (parent[at] != at) at = parent[at]; return at }
        editions.indices.forEach { a -> (a + 1 until editions.size).forEach { b ->
            if (editions[a].id !in separated && editions[b].id !in separated && fragments(editions[a], editions[b])) parent[root(b)] = root(a)
        } }
        val keys = editions.indices.groupBy(::root).mapValues { (_, indices) -> indices.map { editions[it].id }.sorted().joinToString("|") }
        return editions.mapIndexed { index, edition -> edition.copy(fragmentKey = keys.getValue(root(index))) }
    }

    data class Bucket(val id: String, val editions: List<AlbumEdition>, val family: AlbumFamily? = null, val reason: String = "")

    fun group(editions: List<AlbumEdition>, choices: CatalogChoices): List<Bucket> {
        val manual = choices.families.values.flatMap { family -> family.editions.map { it to family } }.toMap()
        val result = editions.filter { it.id in manual }.groupBy { manual.getValue(it.id).id }.map { (id, versions) ->
            Bucket(id, versions, manual.getValue(versions.first().id), "Regroupement confirmé localement.")
        }.toMutableList()
        val remaining = editions.filterNot { it.id in manual }
        val idsByName = editions.groupBy { it.artist.artistNameKey() }.mapValues { (_, versions) ->
            versions.mapNotNull { edition -> edition.artistCredits.map { it.mbid.lowercase(Locale.ROOT) }.takeIf { ids -> ids.isNotEmpty() && ids.all(::validMusicId) }?.sorted() }.distinct()
        }
        fun artistKey(edition: AlbumEdition): String {
            val name = edition.artist.artistNameKey()
            if (name.isBlank() || name in setOf("artiste inconnu", "unknown artist", "artistes divers", "various artists")) return "unidentified:${edition.id}"
            val native = edition.artistCredits.map { it.mbid.lowercase(Locale.ROOT) }.takeIf { ids -> ids.isNotEmpty() && ids.all(::validMusicId) }?.sorted()
            val ids = native ?: idsByName[name]?.singleOrNull()
            if (ids == null && idsByName[name].orEmpty().size > 1) return "unidentified:${edition.id}"
            return if (ids == null) "name:$name" else "artists:${ids.joinToString("+")}"
        }
        remaining.groupBy(::artistKey).forEach { (artist, versions) ->
            val parent = versions.indices.toMutableList()
            fun root(index: Int): Int { var at = index; while (parent[at] != at) at = parent[at]; return at }
            fun join(a: Int, b: Int) { parent[root(b)] = root(a) }
            fun allowed(index: Int) = versions[index].id !in choices.separated && !artist.startsWith("unidentified:")
            val requestedByIndex = versions.map(::requested)
            // Strong local release-group links and the user's explicitly accepted families.
            versions.indices.filter(::allowed).groupBy { versions[it].releaseGroupId }.filterKeys(::validMusicId).values.forEach { indices -> indices.drop(1).forEach { join(indices.first(), it) } }
            versions.indices.filter(::allowed).filter { requestedByIndex[it] != null }.groupBy { requestedByIndex[it]!!.key }.values.forEach { indices -> indices.drop(1).forEach { join(indices.first(), it) } }
            // Android album IDs and text encoding differences are not editions by themselves.
            versions.indices.filter(::allowed).groupBy { titleKey(versions[it].name) }.values.forEach { indices ->
                indices.forEachIndexed { at, a -> indices.drop(at + 1).forEach { b ->
                    if (fragments(versions[a], versions[b])) join(a, b)
                } }
            }
            // An edition suffix is only a hint. Require an actual shared recording identity as well.
            versions.indices.filter(::allowed).groupBy { baseTitle(versions[it].name) }.filterKeys { it.isNotBlank() }.values.forEach { indices ->
                indices.forEachIndexed { at, a -> indices.drop(at + 1).forEach { b ->
                    val one = versions[a]; val two = versions[b]
                    val marked = baseTitle(one.name) != titleKey(one.name) || baseTitle(two.name) != titleKey(two.name)
                    val suitable = one.kind !in setOf(ReleaseKind.SINGLE, ReleaseKind.COMPILATION) && two.kind !in setOf(ReleaseKind.SINGLE, ReleaseKind.COMPILATION)
                    if (marked && suitable && one.tracks.any { first -> two.tracks.any { second -> sameRecording(first, second, choices) } }) join(a, b)
                } }
            }
            versions.indices.groupBy(::root).values.forEach { indices ->
                val members = indices.map(versions::get)
                val accepted = indices.mapNotNull { requestedByIndex[it] }.distinctBy { it.key }.singleOrNull()?.takeIf { indices.all(::allowed) }
                val native = members.map { it.releaseGroupId }.distinct().singleOrNull()?.takeIf(::validMusicId)?.takeIf { indices.all(::allowed) }
                val id: String; val reason: String
                when {
                    accepted != null -> { id = "requested-family:${accepted.key}:$artist"; reason = "Relation d’éditions explicitement demandée, limitée à cet artiste d’album local. Les identifiants différents n’interdisent pas ce regroupement." }
                    native != null -> { id = "release-group:$native:$artist"; reason = "Même release-group MusicBrainz local et même identité d’artiste d’album, sans requête réseau." }
                    members.size > 1 -> { id = "local-editions:$artist:${baseTitle(members.first().name)}"; reason = "Même artiste d’album, indication d’édition et enregistrement commun identifié. Le titre d’album seul n’a pas déclenché ce regroupement." }
                    else -> { id = members.single().id; reason = "Édition locale isolée, ou séparation explicitement conservée." }
                }
                result += Bucket(id, members, reason = reason)
            }
        }
        // A representative choice on an automatic family keeps that family's stable ID.
        // Newly scanned editions with the same proven family must not create duplicate UI keys.
        return result.groupBy { it.id }.map { (id, buckets) ->
            val selected = buckets.firstOrNull { it.family != null }
            Bucket(id, markFragments(buckets.flatMap { it.editions }.distinctBy { it.id }, choices.separated), selected?.family,
                selected?.reason ?: buckets.first().reason)
        }
    }
}
