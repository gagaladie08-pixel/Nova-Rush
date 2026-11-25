package com.novalisten.app

import com.novalisten.app.data.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Verrouille le contrat du découpage réglable : ce n'est plus le code qui décide si « Rihanna, Drake »
 * est un duo, c'est la liste de séparateurs de l'utilisateur, et la liste protégée qui l'en empêche.
 */
class SplitRulesTest {
    private fun track(id: String, artist: String) =
        Track(id, "Titre $id", artist, "Album", 1, "Album Artist", 65000, 1, 2020, 1, "$id.flac", 100, "audio/flac")

    @Test fun theOneSeparatorMostLikelyToBelongToANameStaysOffByDefault() {
        val rules = SplitRules()
        // « & » est le seul séparateur éteint au départ : c'est celui qui ressemble le plus à un nom.
        assertFalse(SplitRules.DEFAULT_SEPARATORS.contains("&"))
        assertFalse("&" in rules.active)
        assertEquals(listOf("Anyma & LISA"), ArtistSplitter.split("Anyma & LISA", rules))
        // Les noms sensibles qui contiennent « , » ou « / » tiennent parce qu'ils sont protégés,
        // pas parce que le séparateur serait désactivé : c'est bien la liste de l'utilisateur qui agit.
        assertEquals(listOf("Earth, Wind & Fire"), ArtistSplitter.split("Earth, Wind & Fire", rules))
        assertEquals(listOf("AC/DC"), ArtistSplitter.split("AC/DC", rules))
        assertTrue("," in rules.active && "/" in rules.active)
    }

    @Test fun userChoiceOfSeparatorIsWhatSplitsACollaboration() {
        val chosen = SplitRules(separators = listOf(";", ","))
        assertEquals(listOf("Rihanna", "Drake"), ArtistSplitter.split("Rihanna, Drake", chosen))
        // Le même nom n'est plus découpé dès qu'il est protégé, sans redémarrage.
        val guarded = chosen.copy(protectedNames = SplitRules.DEFAULT_PROTECTED + "Rihanna, Drake")
        assertEquals(listOf("Rihanna, Drake"), ArtistSplitter.split("Rihanna, Drake", guarded))
    }

    @Test fun everyProtectedNameResistsTheStrongestSeparatorSet() {
        // Avec TOUS les séparateurs actifs, y compris les plus agressifs, les douze noms protégés
        // doivent traverser intacts. C'est le garde-fou qui empêche une régression silencieuse.
        val everything = SplitRules(separators = SplitRules.AVAILABLE)
        SplitRules.DEFAULT_PROTECTED.forEach { name ->
            assertEquals("« $name » a été découpé alors qu'il est protégé", listOf(name), ArtistSplitter.split(name, everything))
            assertFalse("« $name » devrait être signalé protégé", ArtistSplitter.wouldSplit(name, everything))
        }
    }

    @Test fun collectiveAlbumsAreNeverSplitEvenWithEverySeparatorActive() {
        val everything = SplitRules(separators = SplitRules.AVAILABLE, protectedNames = emptyList())
        SplitRules.NEVER_SPLIT.forEach { name -> assertEquals(listOf(name), ArtistSplitter.split(name, everything)) }
    }

    @Test fun userTypedSeparatorIsEscapedInsteadOfInterpretedAsRegex() {
        // Un séparateur libre doit agir sur les vraies occurrences du texte saisi...
        // « C » fait un caractère : la coupe serait annulée, d'où « C.D » ici.
        assertEquals(listOf("A.B", "C.D"), ArtistSplitter.split("A.B puis C.D", SplitRules(separators = listOf("puis"))))
        // ...mais « . » ne doit pas devenir « n'importe quel caractère » : « AxB » n'est pas coupé.
        val dot = SplitRules(separators = listOf("."))
        assertEquals(listOf("A.B"), ArtistSplitter.split("A.B", dot))
        assertEquals(listOf("AxB"), ArtistSplitter.split("AxB", dot))
    }

    @Test fun aChunkToShortAfterSplittingCancelsTheSplitting() {
        // « A/BC » ne casse pas en « A » + « BC » : un morceau d'un caractère annule la coupure.
        val rules = SplitRules(separators = listOf("/"))
        assertEquals(listOf("A/BC"), ArtistSplitter.split("A/BC", rules))
    }

    @Test fun rulesReachTheCatalogWithoutEverRewritingTheFile() {
        val song = track("1", "Rihanna, Drake")
        assertEquals(listOf("Drake", "Rihanna"), artistGroups(listOf(song)).map { it.name }.sorted())
        // Une seule ligne de réglage suffit à revenir en arrière : le nom redevient entier.
        val guarded = CatalogChoices(split = SplitRules(protectedNames = SplitRules.DEFAULT_PROTECTED + "Rihanna, Drake"))
        assertEquals(listOf("Rihanna, Drake"), artistGroups(listOf(song), guarded).map { it.name })
        // Le fichier n'est jamais réécrit : le tag d'origine est intact.
        assertEquals("Rihanna, Drake", song.artist)
    }

    @Test fun albumArtistFollowsTheSameRulesAsTheTrackArtist() {
        val duet = track("1", "Rihanna").copy(albumArtist = "Rihanna, Drake")
        val solo = track("2", "Rihanna").copy(albumArtist = "Rihanna", album = "Autre album")
        // Découpé, l'album de « Rihanna, Drake » est l'album de Rihanna : un seul disque, pas deux.
        assertEquals(1, albumGroups(listOf(duet, solo)).size)
        // Protégé, il redevient une identité distincte : c'est la même liste qui décide.
        val guarded = CatalogChoices(split = SplitRules(protectedNames = SplitRules.DEFAULT_PROTECTED + "Rihanna, Drake"))
        assertEquals(2, albumGroups(listOf(duet, solo), guarded).size)
    }

    @Test fun structuredCreditsStillWinOverAguessOnTheWholeText() {
        // Un vrai duo annoncé par deux crédits MusicBrainz n'est pas écrasé par les règles de texte.
        val song = track("1", "Ambiguous & Name").copy(credits = listOf(ArtistCredit("A"), ArtistCredit("B")))
        assertEquals(listOf("A", "B"), artistGroups(listOf(song)).map { it.name }.sorted())
    }
}
