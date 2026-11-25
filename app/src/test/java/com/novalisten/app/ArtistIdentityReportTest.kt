package com.novalisten.app

import com.novalisten.app.data.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Le diagnostic doit expliquer un doublon sans jamais le masquer : il montre la clé réelle de
 * chaque carte. Si deux identités distinctes existent pour un nom, rien n'est fusionné —
 * deux artistes homonymes ne doivent pas être confondus.
 */
class ArtistIdentityReportTest {
    private val mbid = "8b3b9f0c-7a1e-4c2d-9f4a-1b2c3d4e5f60"
    private fun song(id: String, artist: String, credits: List<ArtistCredit> = emptyList(), albumId: Long = 1) =
        Track(id, "Titre $id", artist, "Album $albumId", albumId, artist, 200000, 0, 2020, 1, "$id.mp3", 100, "audio/mpeg").copy(credits = credits)

    @Test fun noIssueIsReportedWhenEveryCardHasItsOwnName() {
        val tracks = listOf(song("1", "Rihanna"), song("2", "Drake"))
        assertTrue(splitArtistIdentities(tracks).isEmpty())
    }

    @Test fun aNameBackedOnlyByTextIsReportedNextToTheSameNameWithAnIdentifier() {
        // Une carte porte un identifiant MusicBrainz, l'autre seulement le nom.
        val tracks = listOf(
            song("id", "Beyoncé", credits = listOf(ArtistCredit("Beyoncé", mbid))),
            song("text", "Beyoncé"),
        )
        val issue = splitArtistIdentities(tracks).single()
        assertEquals("Beyoncé", issue.name)
        assertEquals(2, issue.cards.size)
        assertEquals(setOf("identifiant MusicBrainz", "nom seul, aucun identifiant"), issue.cards.map { it.origin }.toSet())
        assertTrue("La clé doit être visible, pas devinée.", issue.cards.any { it.key.startsWith("mbid:") })
        assertTrue(issue.cards.any { it.mbid.isBlank() && it.key.isNotBlank() })
    }

    @Test fun twoDistinctIdentifiersAreNeverSilentlyMerged() {
        val other = "11111111-2222-3333-4444-555555555555"
        val tracks = listOf(
            song("a", "Beyoncé", credits = listOf(ArtistCredit("Beyoncé", mbid))),
            song("b", "Beyoncé", credits = listOf(ArtistCredit("Beyoncé", other))),
        )
        // Le catalogue garde deux identités : c'est voulu, et le diagnostic doit le dire.
        assertEquals(2, artistGroups(tracks).size)
        val issue = splitArtistIdentities(tracks).single()
        assertEquals(2, issue.cards.size)
        assertEquals(2, issue.cards.map { it.mbid }.distinct().size)
    }

    @Test fun theReportShowsCountsAndExamplesSoTheUserCanRecogniseEachCard() {
        val tracks = (1..3).map { song("t$it", "Rihanna") } + song("id", "Rihanna", credits = listOf(ArtistCredit("Rihanna", mbid)))
        val albums = albumGroups(tracks)
        val issue = splitArtistIdentities(tracks, albumsByArtist = emptyMap()).single()
        assertEquals(2, issue.cards.size)
        assertEquals(listOf(3, 1), issue.cards.map { it.tracks }.sortedDescending())
        assertTrue("Au moins une carte doit montrer un titre pour être reconnaissable.", issue.cards.all { it.sample.isNotEmpty() })
        assertTrue(albums.isNotEmpty())
    }

    @Test fun theSameRulesApplyWhenDecidingWhatCountsAsTheSameName() {
        // Casse et accents ne créent pas deux noms : le diagnostic ne doit pas inventer un doublon.
        val tracks = listOf(song("1", "BEYONCE"), song("2", "Beyoncé"))
        assertTrue(splitArtistIdentities(tracks).isEmpty())
    }
}
