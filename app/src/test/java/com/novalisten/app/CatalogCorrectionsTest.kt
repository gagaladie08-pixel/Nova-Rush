package com.novalisten.app

import com.novalisten.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CatalogCorrectionsTest {
    private fun song(id: String, albumId: Long = 1, number: Int = 1, album: String = "Destinée Supremacy") =
        Track(id, "Song $number", "Aya Nakamura", album, albumId, "Aya Nakamura", 180000, 0, 2025, number, "$id.mp3", 100, "audio/mpeg")
    @Test fun commonMojibakeIsRepairedOnlyInDisplayAliasesIncludingDoubleEncoding() {
        assertEquals("Destinée Supremacy", CatalogText.display("DestinÃ©e Supremacy"))
        assertEquals("Destinée Supremacy", CatalogText.display("DestinÃƒÂ©e Supremacy"))
        for (valid in listOf("Ärzte", "Åsa", "Âme", "Beyoncé", "宇多田ヒカル", "LISA")) assertEquals(valid, CatalogText.display(valid))
    }
    @Test fun fourCompatibleFragmentsWithScreenshotLikeCountsBecomeOneCardWithoutRewritingTracks() {
        val original = (1..15).map { song("original$it", number = it) }
        val additions = (16..19).map { song("extra$it", 2, it) }
        val broken = (20..21).map { song("broken$it", 3, it, "DestinÃ©e Supremacy") }
        val last = song("last", 4, 22, "DestinÃƒÂ©e Supremacy")
        val all = original + additions + broken + last
        val group = albumGroups(all).single()
        assertEquals("Destinée Supremacy", group.name)
        assertEquals(22, group.tracks.size); assertEquals(1, group.editionCount)
        assertEquals(4, group.editions.size) // source fragments remain available for corrections/cover references
        assertEquals("DestinÃ©e Supremacy", broken.first().album)
        assertEquals(4, albumGroups(all, CatalogChoices(separated = all.map { it.editionKey }.toSet())).size)
    }
    @Test fun differentArtistsAndContradictoryNativeReleasesStillSeparateAlbums() {
        val one = song("one")
        // Un autre artiste d'album reste un autre album : c'est la condition « même artiste ».
        assertEquals(2, albumGroups(listOf(one, song("other", 2, 2).copy(albumArtist = "Another artist"))).size)
        // Deux identifiants de sortie natifs distincts ne séparent plus à eux seuls : une édition
        // Deluxe porte elle aussi son propre identifiant. C'est « Séparer les éditions » qui décide,
        // et cette séparation est mémorisée.
        val oneId = one.copy(releaseId = "11111111-1111-1111-1111-111111111111")
        val twoId = song("other", 2, 2).copy(releaseId = "22222222-2222-2222-2222-222222222222")
        assertEquals(1, albumGroups(listOf(oneId, twoId)).size)
        assertEquals(2, albumGroups(listOf(oneId, twoId), CatalogChoices(separated = setOf(oneId.albumKey, twoId.albumKey))).size)
        // En revanche, un contenu ou une année différents ne suffisent plus à séparer deux
        // versions du même titre : la réunion se fait sur le titre de base et l'artiste.
        assertEquals(1, albumGroups(listOf(one, one.copy(id = "different", albumId = 2, title = "Different song"))).size)
        assertEquals(1, albumGroups(listOf(one, song("later", 2, 2).copy(year = 2026))).size)
    }
    @Test fun groupingFragmentsDoesNotEraseUnprovenCopiesOrLiveVersions() {
        val first = song("first")
        val copy = first.copy(id = "copy", albumId = 2)
        val live = song("live", 2, 2).copy(title = "Song 1 (Live)")
        val group = albumGroups(listOf(first, copy, live)).single()
        assertEquals(setOf("first", "copy", "live"), group.tracks.map { it.id }.toSet())
    }
    @Test fun commaAndAmpersandCreditsUseIndependentParticipantsWithoutBlindlySplittingUnknownGroups() {
        val lisa = song("lisa").copy(artist = "LISA", albumArtist = "LISA")
        val guest = song("guest").copy(artist = "Anyma & LISA", albumArtist = "Anyma & LISA")
        val anyma = song("anyma").copy(artist = "Anyma", albumArtist = "Anyma")
        assertEquals(setOf("LISA", "Anyma"), artistGroups(listOf(lisa, anyma, guest)).map { it.name }.toSet())
        assertEquals(listOf("Anyma & LISA"), ArtistCredits.parse(guest.artist, setOf("lisa")).map { it.name })
        val duet = song("duet").copy(artist = "Anitta, Shakira", albumArtist = "Anitta")
        assertEquals(setOf("Anitta", "Shakira"), artistGroups(listOf(duet, song("shakira").copy(artist = "Shakira", albumArtist = "Shakira"))).map { it.name }.toSet())
        assertEquals(listOf("Simon & Garfunkel"), ArtistCredits.parse("Simon & Garfunkel", setOf("simon")).map { it.name })
        assertEquals(listOf("Tyler, the Creator"), ArtistCredits.parse("Tyler, the Creator", setOf("tyler")).map { it.name })
    }
    @Test fun verifiedAutomaticCreditsAreContextBoundAndManualChoicesAlwaysWin() {
        // Texte sans séparateur actif : le seul chemin vers deux artistes est le crédit résolu.
        // Cela isole bien ce que ce test protège, sans dépendre du réglage des séparateurs.
        val track = song("duet").copy(artist = "Anitta Shakira", albumArtist = "Anitta Shakira")
        val resolved = ResolvedArtistCredits(track.artist, track.title, track.album, track.durationMs, listOf(ArtistCredit("Anitta"), ArtistCredit("Shakira")))
        val choices = CatalogChoices(resolvedArtists = mapOf(track.id to resolved))
        assertEquals(2, artistGroups(listOf(track), choices).size)
        assertEquals(1, artistGroups(listOf(track.copy(title = "Changed")), choices).size)
        assertEquals(listOf("Manual"), artistGroups(listOf(track), choices.copy(artists = mapOf(track.id to listOf(ArtistCredit("Manual"))))).map { it.name })
    }
    @Test(timeout = 15000) fun aLargeLocalIndexPrecomputesAssociationsAndExcludesHiddenAndUnplayableFiles() {
        val tracks = (0 until 10000).map { n -> song("$n", n / 10L + 1, n % 10 + 1, "Album ${n / 10}").copy(artist = "Artist ${n / 100}", albumArtist = "Artist ${n / 100}") }
        val started = System.nanoTime()
        val index = CatalogSnapshot.build(tracks + song("unknown").copy(durationMs = 0), setOf("0"), CatalogChoices())
        println("catalog-index fixture=10000 files elapsed_ms=${(System.nanoTime() - started) / 1_000_000}")
        assertTrue(index.ready); assertEquals(9999, index.tracks.size)
        assertEquals(100, index.artists.size); assertEquals(1000, index.albums.size)
        assertEquals(10, index.albumsByArtist.getValue("artist 0").size)
        assertEquals("Artist 0", index.artistsByTrack.getValue("1").single().name)
        assertFalse(index.albumByTrack.containsKey("0")); assertFalse(index.artistsByTrack.containsKey("unknown"))
    }
    @Test fun unknownMetadataCannotBridgeContradictoryNativeReleaseIdentities() {
        // Sans identifiant natif, les trois morceaux partagent le même titre : une seule carte.
        val one = song("one", 1, 1)
        val unknown = song("bridge", 2, 2).copy(year = 0)
        val other = song("other", 3, 3).copy(year = 2026)
        val merged = albumGroups(listOf(one, unknown, other))
        assertEquals(1, merged.size)
        assertEquals(3, merged.single().tracks.size)
        // L'ordre d'entrée ne doit pas changer l'identifiant de la carte produite.
        assertEquals(merged.single().id, albumGroups(listOf(other, unknown, one)).single().id)
        // Des identifiants natifs distincts ne bloquent plus la réunion par le titre de base.
        // Le seul moyen de les séparer est explicite, et il reste respecté.
        val id1 = "11111111-1111-1111-1111-111111111111"
        val id2 = "22222222-2222-2222-2222-222222222222"
        val native = listOf(one.copy(releaseId = id1), unknown, other.copy(year = one.year, releaseId = id2))
        assertEquals(1, albumGroups(native).size)
        val separated = CatalogChoices(separated = native.map { it.albumKey }.toSet())
        assertEquals(3, albumGroups(native, separated).size)
    }
    @Test fun aMatchingTitleIsEnoughOnceTheSuffixIsGone() {
        // Garde-fou conservé sous une autre forme : le titre de base doit réellement correspondre.
        val one = song("one").copy(number = 0, displayCover = "file:///same.jpg")
        val two = song("two", 2).copy(number = 0, displayCover = one.displayCover)
        assertEquals(1, albumGroups(listOf(one, two)).size)
        // Un titre différent reste un album différent.
        assertEquals(2, albumGroups(listOf(one, two.copy(album = "Une autre sortie", albumId = 3))).size)
    }
    @Test fun numberedFragmentsUseOneGlobalDiscAndTrackOrderEvenWhenASmallerIdContainsTheLastSong() {
        val first = song("last", 1, 3)
        val rest = listOf(song("one", 2, 1), song("two", 2, 2))
        val group = albumGroups(listOf(first) + rest).single()
        assertEquals(listOf("one", "two", "last"), group.tracks.map { it.id })
        assertEquals(rest.first().editionKey, group.originalId)
        assertEquals(1, group.editionCount)
    }
    @Test fun aSavedSourceChoiceCanAdoptANewCompatibleFragmentWithoutResettingTheChoice() {
        val songs = listOf(song("one", 1, 1), song("two", 2, 2))
        val old = albumGroups(songs).single()
        val family = AlbumFamily(old.id, songs.map { it.editionKey }.toSet(), songs.first().editionKey, songs.last().editionKey)
        val third = song("three", 3, 3)
        val after = albumGroups(songs + third, CatalogChoices(families = mapOf(family.id to family))).single()
        assertEquals(old.id, after.id); assertEquals(songs.last().editionKey, after.representativeId)
        assertEquals(3, after.editions.size); assertEquals(1, after.editionCount)
        assertEquals(2, albumGroups(songs + third, CatalogChoices(families = mapOf(family.id to family), separated = setOf(third.editionKey))).size)
    }
    @Test fun staleCreditOrHiddenSnapshotsCannotValidateAnAutomaticCommit() {
        val track = song("one")
        val index = CatalogSnapshot.build(listOf(track), emptySet(), CatalogChoices())
        assertTrue(index.isCurrent(track, LocalData()))
        assertFalse(index.isCurrent(track, LocalData(hidden = setOf("one"))))
        assertFalse(index.isCurrent(track, LocalData(catalog = CatalogChoices(artists = mapOf(track.id to listOf(ArtistCredit("Chosen")))))))
        assertFalse(index.isCurrent(track.copy(title = "Changed"), LocalData()))
    }

    @Test fun repairedArtistNamesKeepManualLegacyFieldsIncludingClearingAndNeverBorrowCollectiveProfiles() {
        val raw = song("one").copy(artist = "BeyoncÃ©", albumArtist = "BeyoncÃ©")
        val group = artistGroups(listOf(raw)).single()
        val manual = ArtistNote("", "Deliberate clearing", "file:///chosen.jpg", "Chosen", biographyLocked = true, portraitLocked = true)
        val local = LocalData(artists = mapOf(raw.artistKey to manual))
        assertEquals(manual, artistNote(group, local))
        val unlocked = manual.copy(biographyLocked = false, portraitLocked = false)
        assertEquals(unlocked, artistNote(group, local.copy(artists = local.artists + (group.id to unlocked))))
        assertEquals("BeyoncÃ©", raw.artist)
        val collective = raw.copy(id = "duet", artist = "BeyoncÃ© feat. Guest")
        val participants = artistGroups(listOf(collective))
        assertTrue(participants.all { artistNote(it, LocalData(artists = mapOf(collective.artistKey to manual))) == ArtistNote() })
    }

    @Test fun oneVisualCardDoesNotDependOnWhetherTwentyTwoFilesRepresentFifteenOrTwentyTwoRecordings() {
        val files = (1..15).map { song("original$it", 1, it) } + (1..4).map { song("copy$it", 2, it) } +
            (3..4).map { song("broken$it", 3, it, "DestinÃ©e Supremacy") } + song("last", 4, 7)
        val unproven = albumGroups(files).single()
        assertEquals(1, unproven.editionCount); assertEquals(22, unproven.tracks.size)
        val identified = files.map { it.copy(recordingId = "%08d-1111-1111-1111-111111111111".format(it.number)) }
        val proven = albumGroups(identified).single()
        assertEquals(1, proven.editionCount); assertEquals(15, proven.tracks.size)
        assertEquals(22, proven.editions.sumOf { it.tracks.size })
    }

}
