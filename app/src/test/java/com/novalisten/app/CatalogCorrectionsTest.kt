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
    @Test fun theFourScreenshotFragmentsBecomeOneCardAndOneEditionWithoutRewritingTracks() {
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
    @Test fun conflictingContentsYearsArtistsAndNativeReleaseIdsAreNotPretendedToBeFragments() {
        val one = song("one")
        assertEquals(2, albumGroups(listOf(one, one.copy(id = "different", albumId = 2, title = "Different song"))).size)
        assertEquals(2, albumGroups(listOf(one, song("later", 2, 2).copy(year = 2026))).size)
        assertEquals(2, albumGroups(listOf(one, song("other", 2, 2).copy(albumArtist = "Another artist"))).size)
        assertEquals(2, albumGroups(listOf(one.copy(releaseId = "11111111-1111-1111-1111-111111111111"), song("other", 2, 2).copy(releaseId = "22222222-2222-2222-2222-222222222222"))).size)
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
        val track = song("duet").copy(artist = "Anitta, Shakira", albumArtist = "Anitta, Shakira")
        val resolved = ResolvedArtistCredits(track.artist, track.title, track.album, track.durationMs, listOf(ArtistCredit("Anitta"), ArtistCredit("Shakira")))
        val choices = CatalogChoices(resolvedArtists = mapOf(track.id to resolved))
        assertEquals(2, artistGroups(listOf(track), choices).size)
        assertEquals(1, artistGroups(listOf(track.copy(title = "Changed")), choices).size)
        assertEquals(listOf("Manual"), artistGroups(listOf(track), choices.copy(artists = mapOf(track.id to listOf(ArtistCredit("Manual"))))).map { it.name })
    }
    @Test(timeout = 15000) fun aLargeLocalIndexPrecomputesAssociationsAndExcludesHiddenAndUnplayableFiles() {
        val tracks = (0 until 10000).map { n -> song("$n", n / 10L + 1, n % 10 + 1, "Album ${n / 10}").copy(artist = "Artist ${n / 100}", albumArtist = "Artist ${n / 100}") }
        val index = CatalogSnapshot.build(tracks + song("unknown").copy(durationMs = 0), setOf("0"), CatalogChoices())
        assertTrue(index.ready); assertEquals(9999, index.tracks.size)
        assertEquals(100, index.artists.size); assertEquals(1000, index.albums.size)
        assertEquals(10, index.albumsByArtist.getValue("artist 0").size)
        assertEquals("Artist 0", index.artistsByTrack.getValue("1").single().name)
        assertFalse(index.albumByTrack.containsKey("0")); assertFalse(index.artistsByTrack.containsKey("unknown"))
    }
}
