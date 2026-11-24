package com.novalisten.app

import com.novalisten.app.data.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Le featuring ne doit jamais créer un second album : cas « 143 » de Katy Perry, où les pistes
 * portent tantôt « Katy Perry », tantôt « Katy Perry, Doechii ».
 */
class FeaturingAlbumIdentityTest {
    private fun song(id: String, number: Int, albumId: Long = 143, albumArtist: String = "Katy Perry",
                     artist: String = "Katy Perry", album: String = "143", title: String = "Chanson $number") =
        Track(id, title, artist, album, albumId, albumArtist, 180000, 0, 2024, number, "$id.mp3", 100, "audio/mpeg")

    @Test fun aGuestInTheAlbumArtistFieldDoesNotCreateASecondAlbum() {
        val solo = (1..3).map { song("solo$it", it) }
        val duet = song("duet", 4, albumArtist = "Katy Perry, Doechii", artist = "Katy Perry, Doechii", title = "Chanson 4")
        val group = albumGroups(solo + duet).single()
        assertEquals("143", group.name)
        assertEquals("Katy Perry", group.artist)
        assertEquals(4, group.tracks.size)
        assertEquals(4, group.tracks.map { it.id }.distinct().size)
    }

    @Test fun theGuestStaysVisibleOnItsOwnTrack() {
        val solo = song("solo", 1)
        val duet = song("duet", 2, albumArtist = "Katy Perry, Doechii", artist = "Katy Perry, Doechii")
        val group = albumGroups(listOf(solo, duet)).single()
        // Les crédits d'origine ne sont jamais réécrits : seul le regroupement change.
        assertEquals("Katy Perry, Doechii", group.tracks.first { it.id == "duet" }.artist)
        assertEquals("Katy Perry, Doechii", group.tracks.first { it.id == "duet" }.albumArtist)
    }

    @Test fun featWordingIsHandledTheSameWay() {
        val plain = song("plain", 1)
        val feat = song("feat", 2, albumArtist = "Katy Perry feat. Doechii")
        val ft = song("ft", 3, albumArtist = "Katy Perry ft. 21 Savage")
        assertEquals(1, albumGroups(listOf(plain, feat, ft)).size)
    }

    @Test fun aGenuinelyDifferentAlbumArtistStillSeparatesTheAlbums() {
        val katy = song("katy", 1)
        val other = song("other", 1, albumId = 900, albumArtist = "Autre Artiste", album = "Autre album")
        assertEquals(2, albumGroups(listOf(katy, other)).size)
    }

    @Test fun variousArtistsAndProtectedNamesAreNeverCollapsed() {
        assertEquals("Earth, Wind & Fire", ArtistCredits.albumHead("Earth, Wind & Fire"))
        assertEquals("AC/DC", ArtistCredits.albumHead("AC/DC"))
        assertEquals("Tyler, the Creator", ArtistCredits.albumHead("Tyler, the Creator"))
        val collection = (1..3).map { song("various$it", it, albumId = 500, albumArtist = "Artistes divers", album = "Compilation", title = "Titre $it") }
        val group = albumGroups(collection).single()
        assertEquals("Artistes divers", group.artist)
        assertEquals(3, group.tracks.size)
    }

    @Test fun twoMediaStoreIdsOfTheSameAlbumAreReunitedWhenTheCoverAndYearAgree() {
        val first = (1..3).map { song("a$it", it, albumId = 143) }
        val second = song("b4", 4, albumId = 144, albumArtist = "Katy Perry, Doechii", title = "Chanson 4")
        val group = albumGroups(first + second).single()
        assertEquals("143", group.name)
        assertEquals(4, group.tracks.size)
    }
}
