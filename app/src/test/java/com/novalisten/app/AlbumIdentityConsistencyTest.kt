package com.novalisten.app

import com.novalisten.app.data.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Une seule règle doit décider où finit un nom, sinon la fiche artiste et l'album se contredisent.
 * Cas réel : « Rihanna/ Drake » dans l'artiste d'album produisait un troisième nom inexistant,
 * et séparait « ANTI » de « ANTI (Deluxe) ».
 */
class AlbumIdentityConsistencyTest {
    private fun song(id: String, number: Int, albumId: Long = 1, album: String = "ANTI",
                     albumArtist: String = "Rihanna", artist: String = "Rihanna") =
        Track(id, "Titre $number", artist, album, albumId, albumArtist, 200000, 0, 2016, number, "$id.mp3", 100, "audio/mpeg")

    @Test fun aSlashWithoutSpacesIsSplitTheSameWayOnTheAlbumAsOnTheTrack() {
        // Le morceau est crédité « Rihanna » et « Drake »...
        val duet = song("duet", 1, albumArtist = "Rihanna/ Drake", artist = "Rihanna/ Drake")
        assertEquals(listOf("Drake", "Rihanna"), artistGroups(listOf(duet)).map { it.name }.sorted())
        // ...et l'album doit porter le premier d'entre eux, pas la chaîne brute.
        val group = albumGroups(listOf(duet)).single()
        assertEquals("Rihanna", group.artist)
        assertEquals("Rihanna", group.editions.single().artist)
    }

    @Test fun antiAndAntiDeluxeAreNoLongerSeparatedByANonExistentThirdName() {
        val normal = (1..3).map { song("n$it", it, albumId = 10, album = "ANTI") }
        // Le même disque, avec une piste créditée d'un invité : la barre oblique collée ne doit
        // pas inventer un artiste d'album distinct.
        val deluxe = (4..5).map { song("d$it", it, albumId = 11, album = "ANTI (Deluxe)", albumArtist = "Rihanna/ Drake") }
        val groups = albumGroups(normal + deluxe)
        assertTrue("ANTI doit rester une seule carte, pas « Rihanna » + « Rihanna/ Drake ».", groups.none { it.artist.contains("/") })
    }

    @Test fun protectedNamesStillKeepTheWholeNameAsTheAlbumArtist() {
        val acdc = song("acdc", 1, album = "Back in Black", albumArtist = "AC/DC", artist = "AC/DC")
        assertEquals("AC/DC", albumGroups(listOf(acdc)).single().artist)
        // « Earth, Wind & Fire » reste entier, même avec la virgule active par défaut.
        val ewf = song("ewf", 1, album = "September", albumArtist = "Earth, Wind & Fire", artist = "Earth, Wind & Fire")
        assertEquals("Earth, Wind & Fire", albumGroups(listOf(ewf)).single().artist)
    }

    @Test fun aMissingWordInTheAlbumTitleIsNotASuffixAndStaysASeparateAlbum() {
        // Cas réel : l'album de base s'appelle « Destinée », l'édition « Destinée Supremacy (Deluxe) ».
        // Retirer « Deluxe » donne « Destinée Supremacy », qui n'est pas le même titre de base.
        // Aucune règle de suffixe ne peut réunir ces deux-là : c'est le regroupement manuel qui décide.
        val base = song("base", 1, albumId = 20, album = "Destinée")
        val deluxe = song("deluxe", 1, albumId = 21, album = "Destinée Supremacy (Deluxe)")
        assertEquals(2, albumGroups(listOf(base, deluxe)).size)
    }

    @Test fun aDeluxeEditionOfTheSameTitleIsReunited() {
        val normal = song("n", 1, albumId = 22, album = "Destinée Supremacy")
        val deluxe = song("d", 2, albumId = 23, album = "Destinée Supremacy (Deluxe)")
        val group = albumGroups(listOf(normal, deluxe)).single()
        assertEquals("Destinée Supremacy", group.name)
        assertEquals(2, group.tracks.size)
    }

    @Test fun disablingTheSeparatorRestoresTheWholeAlbumArtist() {
        val duet = song("duet", 1, albumArtist = "Rihanna/ Drake", artist = "Rihanna/ Drake")
        val noSlash = CatalogChoices(split = SplitRules(separators = listOf(";")))
        assertEquals("Rihanna/ Drake", albumGroups(listOf(duet), noSlash).single().artist)
        // Une seule règle pour les deux vues : la fiche artiste ne découpe plus non plus.
        assertEquals(listOf("Rihanna/ Drake"), artistGroups(listOf(duet), noSlash).map { it.name })
    }

    @Test fun aFeaturingInTheAlbumArtistStillDoesNotCreateASecondAlbum() {
        val plain = song("plain", 1, albumId = 143, album = "143", albumArtist = "Katy Perry", artist = "Katy Perry")
        val feat = song("feat", 2, albumId = 143, album = "143", albumArtist = "Katy Perry feat. Doechii", artist = "Katy Perry feat. Doechii")
        val group = albumGroups(listOf(plain, feat)).single()
        assertEquals("Katy Perry", group.artist)
        assertEquals(2, group.tracks.size)
    }
}
