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

    @Test fun aNameWithoutIdentifierIsAlreadyReunitedWithTheSingleIdentifierItMatches() {
        // Un fichier porte l'identifiant, l'autre seulement le nom : c'est déjà fusionné, donc
        // ce n'est pas un doublon. Le diagnostic ne doit rien signaler.
        val tracks = listOf(
            song("id", "Beyoncé", credits = listOf(ArtistCredit("Beyoncé", mbid))),
            song("text", "Beyoncé"),
        )
        assertEquals(1, artistGroups(tracks).size)
        assertTrue(splitArtistIdentities(tracks).isEmpty())
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
        // Deux identifiants distincts pour un même nom : le seul cas où le catalogue refuse de
        // fusionner, et donc le seul cas réellement dédoublé.
        val other = "11111111-2222-3333-4444-555555555555"
        val tracks = (1..3).map { song("t$it", "Rihanna") } +
            song("id", "Rihanna", credits = listOf(ArtistCredit("Rihanna", mbid))) +
            song("other", "Rihanna", credits = listOf(ArtistCredit("Rihanna", other)))
        val issue = splitArtistIdentities(tracks).single()
        // Trois cartes : deux identifiants distincts, et le nom seul qui ne peut être rattaché
        // à aucun des deux avec certitude. C'est le cas le plus fréquent chez un utilisateur réel.
        assertEquals(3, issue.cards.size)
        // La carte la plus fournie vient en premier : on reconnaît tout de suite l'ancienne.
        assertEquals(3, issue.cards.first().tracks)
        assertTrue("Au moins une carte doit montrer un titre pour être reconnaissable.", issue.cards.all { it.sample.isNotEmpty() })
        assertEquals(2, issue.cards.count { it.origin.startsWith("identifiant MusicBrainz") })
        assertEquals(1, issue.cards.count { it.origin == "nom seul, aucun identifiant" })
        // Sans appelant fournissant les albums, les compteurs restent honnêtes : zéro, pas deviné.
        assertTrue(issue.cards.all { it.albums == 0 })
    }

    @Test fun albumCountsAreReportedWhenTheAlbumIndexIsProvided() {
        val other = "11111111-2222-3333-4444-555555555555"
        val tracks = listOf(
            song("a", "Rihanna", credits = listOf(ArtistCredit("Rihanna", mbid)), albumId = 1),
            song("b", "Rihanna", credits = listOf(ArtistCredit("Rihanna", other)), albumId = 2),
        )
        val groups = artistGroups(tracks)
        val byArtist = groups.associate { group -> group.id to albumGroups(tracks).filter { album -> album.tracks.any { it.id in group.tracks.map { t -> t.id } } } }
        val issue = splitArtistIdentities(tracks, albumsByArtist = byArtist).single()
        assertEquals(2, issue.cards.size)
        assertTrue("Chaque carte doit annoncer ses sorties.", issue.cards.all { it.albums >= 1 })
    }

    @Test fun theSameRulesApplyWhenDecidingWhatCountsAsTheSameName() {
        // Casse et accents ne créent pas deux noms : le diagnostic ne doit pas inventer un doublon.
        val tracks = listOf(song("1", "BEYONCE"), song("2", "Beyoncé"))
        assertTrue(splitArtistIdentities(tracks).isEmpty())
    }
}
