package com.novalisten.app

import com.novalisten.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    private val mb1 = "11111111-1111-1111-1111-111111111111"
    private val mb2 = "22222222-2222-2222-2222-222222222222"
    private fun track(id: String, artist: String = "Artiste synthétique", album: String = "Édition originale", albumId: Long = 1, number: Int = 1) =
        Track(id, "Titre $id", artist, album, albumId, "Artiste synthétique", 65000, 1, 2020, number, "$id.flac", 100, "audio/flac")

    @Test fun collaborationsProduceOneArtistCardPerParticipantAndKeepTheOriginalCredit() {
        val songs = listOf(track("1", "Ariana Grande/Iggy Azalea"), track("2", "Ariana Grande/Nicki Minaj"), track("3", "Ariana Grande"))
        val groups = artistGroups(songs)
        assertEquals(3, groups.size)
        assertEquals(3, groups.first { it.name == "Ariana Grande" }.tracks.size)
        assertEquals("Ariana Grande/Iggy Azalea", songs.first().artist)
    }
    @Test fun groupsAndPunctuationAreNotBlindlySplit() {
        listOf("AC/DC", "Earth, Wind & Fire", "Tyler, the Creator", "Simon & Garfunkel", "A/B", "An Unknown/Group With Spaces").forEach { name ->
            assertEquals(name, listOf(name), ArtistCredits.parse(name).map { it.name })
        }
    }
    @Test fun featuringAndExplicitMultiValueCreditsAreRecognized() {
        assertEquals(listOf("A", "B"), ArtistCredits.parse("A feat. B").map { it.name })
        assertEquals(listOf("A", "B"), ArtistCredits.parse("A\u0000B;A").map { it.name })
        assertEquals(listOf("A", "B"), ArtistCredits.parse("A & B", setOf("a", "b")).map { it.name })
    }
    @Test fun structuredAndManualCreditsOverrideInferencesWithoutChangingTags() {
        val song = track("1", "Ambiguous & Name").copy(credits = listOf(ArtistCredit("A"), ArtistCredit("B")))
        assertEquals(2, artistGroups(listOf(song)).size)
        val choices = CatalogChoices(artists = mapOf("1" to listOf(ArtistCredit("Actual Band"))))
        assertEquals(listOf("Actual Band"), artistGroups(listOf(song), choices).map { it.name })
        assertEquals("Ambiguous & Name", song.artist)
    }
    @Test fun homonymousArtistsWithDistinctNativeIdsStaySeparateAndDoNotInheritAnAmbiguousOldProfile() {
        val one = track("1").copy(credits = listOf(ArtistCredit("Same", mb1)))
        val two = track("2").copy(credits = listOf(ArtistCredit("Same", mb2)))
        val groups = artistGroups(listOf(one, two))
        assertEquals(2, groups.size); assertTrue(groups.none { it.inheritLegacy })
        assertEquals(ArtistNote(), artistNote(groups.first(), LocalData(artists = mapOf("same" to ArtistNote("Old ambiguous bio")))))
    }
    @Test fun aUniqueNativeArtistIdReusesTheSingleNameProfileButNeverACollectiveProfile() {
        val song = track("1").copy(credits = listOf(ArtistCredit("Singer", mb1)))
        val group = artistGroups(listOf(song)).single()
        val old = ArtistNote("User text", portrait = "file:///chosen.jpg")
        assertEquals(old, artistNote(group, LocalData(artists = mapOf("singer" to old))))
        assertEquals(ArtistNote(), artistNote(group, LocalData(artists = mapOf("singer/guest" to old))))
    }
    @Test fun oneMissingIdCanJoinAnUnambiguousKnownArtist() {
        val songs = listOf(track("1", "Singer").copy(credits = listOf(ArtistCredit("Singer", mb1))), track("2", "Singer"))
        assertEquals(2, artistGroups(songs).single().tracks.size)
    }
    @Test fun countsUseUniqueAdmissibleFilesAndBothNumericSortDirectionsHaveStableNameTies() {
        val a = ArtistGroup("a", "Alpha", listOf(track("1")))
        val b = ArtistGroup("b", "Beta", listOf(track("2"), track("3")))
        val c = ArtistGroup("c", "Charlie", listOf(track("4")))
        assertEquals(listOf("b", "a", "c"), sortCatalog(listOf(c, b, a), CatalogSort.MOST_TRACKS, { it.name }, { it.tracks.size }).map { it.id })
        assertEquals(listOf("a", "c", "b"), sortCatalog(listOf(c, b, a), CatalogSort.FEWEST_TRACKS, { it.name }, { it.tracks.size }).map { it.id })
        assertEquals(1, artistGroups(listOf(track("1"), track("1"), track("unknown").copy(durationMs = 0), track("short").copy(durationMs = 59999))).single().tracks.size)
    }
    @Test fun anIncompleteAlbumIsNotGuessedToBeASingleOrAnEp() {
        assertEquals(ReleaseKind.UNKNOWN, albumGroups(listOf(track("1"))).single().kind)
        assertEquals(ReleaseKind.EP, albumGroups(listOf(track("1").copy(releaseKind = ReleaseKind.EP))).single().kind)
        assertEquals(ReleaseKind.MIXTAPE, ReleaseKind.fromTag("album;mixtape/street"))
    }
    @Test fun albumNamesAndDeluxeSuffixesAloneNeverCauseAnUnrequestedMerge() {
        assertEquals(2, albumGroups(listOf(track("1"), track("2", album = "Édition originale (Deluxe)", albumId = 2))).size)
        assertEquals(2, albumGroups(listOf(track("1"), track("2", albumId = 2))).size)
    }
    @Test fun nativeReleaseGroupIdsIdentifyEditionsWithoutNetworkAndManualSeparationIsRespected() {
        val songs = listOf(track("1").copy(releaseGroupId = mb1), track("2", albumId = 2).copy(releaseGroupId = mb1))
        val group = albumGroups(songs).single()
        assertEquals(2, group.editions.size); assertTrue(group.automaticFamily)
        assertEquals(2, albumGroups(songs, CatalogChoices(separated = setOf(songs.first().albumKey))).size)
    }
    @Test fun explicitEditionFamiliesCanUniteDifferentNamesWithoutLosingFilesOrVariants() {
        val songs = listOf(track("a", album = "Original", number = 2), track("b", album = "Original", number = 1), track("c", album = "Completely different edition", albumId = 2))
        val family = AlbumFamily("family:test", songs.map { it.albumKey }.toSet(), songs.first().albumKey)
        val group = albumGroups(songs, CatalogChoices(families = mapOf(family.id to family))).single()
        assertEquals(listOf("b", "a", "c"), group.tracks.map { it.id })
        assertEquals(2, group.editions.size); assertEquals(3, group.editions.sumOf { it.tracks.size })
    }
    @Test fun nativeRecordingIdentityDeduplicatesOnlyMatchingPerformances() {
        val one = track("1").copy(recordingId = mb1)
        val copy = track("2", albumId = 2).copy(title = one.title, recordingId = mb1)
        val live = copy.copy(id = "3", title = "${one.title} (Live)")
        val songs = listOf(one, copy, live).map { it.copy(releaseGroupId = mb2) }
        val group = albumGroups(songs).single()
        assertEquals(listOf("1", "3"), group.tracks.map { it.id })
        assertEquals(3, group.editions.sumOf { it.tracks.size })
    }
    @Test fun sameTitleAndDurationWithoutRecordingEvidenceDoNotEraseDifferentFiles() {
        val one = track("1")
        val other = track("2", albumId = 2).copy(title = one.title)
        assertFalse(sameRecording(one, other, CatalogChoices()))
        assertTrue(sameRecording(one, other, CatalogChoices(recordings = mapOf("1" to "chosen", "2" to "chosen"))))
    }
    @Test fun aManualDistinctChoiceOverridesIncorrectNativeRecordingTags() {
        val one = track("1").copy(recordingId = mb1)
        val other = one.copy(id = "2")
        assertTrue(sameRecording(one, other, CatalogChoices()))
        assertFalse(sameRecording(one, other, CatalogChoices(recordings = mapOf("2" to "distinct:chosen"))))
    }
    @Test fun representativeEditionChangesNameAndCoverButNotTheOriginalTrackOrder() {
        val one = track("1")
        val two = track("2", album = "Deluxe", albumId = 2)
        val family = AlbumFamily("family:test", setOf(one.albumKey, two.albumKey), one.albumKey, two.albumKey)
        val group = albumGroups(listOf(one, two), CatalogChoices(families = mapOf(family.id to family))).single()
        assertEquals("Deluxe", group.name); assertEquals(two.artwork, group.artwork)
        assertEquals(listOf("1", "2"), group.tracks.map { it.id })
    }
    @Test fun overlappingManualFamiliesAreRejectedWithoutDiscardingTheOldOne() {
        val first = AlbumFamily("a", setOf("1", "2"), "1")
        try { validateFamily(AlbumFamily("b", setOf("2", "3"), "2"), CatalogChoices(families = mapOf("a" to first))); fail("overlap") } catch (_: IllegalArgumentException) { }
    }
    @Test fun incompatibleEditionTypesRemainUnknownUntilAnExplicitChoice() {
        val songs = listOf(track("1").copy(releaseGroupId = mb1, releaseKind = ReleaseKind.ALBUM), track("2", albumId = 2).copy(releaseGroupId = mb1, releaseKind = ReleaseKind.EP))
        val group = albumGroups(songs).single(); assertEquals(ReleaseKind.UNKNOWN, group.kind)
        assertEquals(ReleaseKind.ALBUM, albumGroups(songs, CatalogChoices(kinds = mapOf(group.id to ReleaseKind.ALBUM))).single().kind)
    }
    @Test fun theFameAndMonsterUseTheRequestedFamilyEvenWithDifferentNativeReleaseGroupsAndTypes() {
        val one = track("original", artist = "Lady Gaga", album = "The Fame", number = 1).copy(albumArtist = "Lady Gaga", releaseGroupId = mb1, releaseKind = ReleaseKind.ALBUM, recordingId = mb1, year = 2008)
        val second = one.copy(id = "second", title = "Second song", number = 2, recordingId = mb2)
        val copy = one.copy(id = "copy", album = "The Fame Monster", albumId = 2, releaseGroupId = mb2, releaseKind = ReleaseKind.EP, year = 2009)
        val extra = copy.copy(id = "extra", title = "New song", number = 2, recordingId = "")
        val live = copy.copy(id = "live", title = "${one.title} (Live)", number = 3)
        val source = listOf(copy, live, extra, second, one)
        val group = albumGroups(source).single()
        assertEquals("The Fame", group.name); assertEquals(one.editionKey, group.originalId); assertEquals(2008, group.year)
        assertEquals(listOf("original", "second", "extra", "live"), group.tracks.map { it.id })
        assertEquals(5, group.editions.sumOf { it.tracks.size }); assertEquals(ReleaseKind.ALBUM, group.kind)
        assertTrue(group.automaticFamily); assertTrue(group.groupingReason.contains("explicitement demandée"))
        assertEquals(2, albumGroups(source, CatalogChoices(separated = setOf(one.editionKey))).size)
    }
    @Test fun futureNostalgiaAndBothCommonMoonlightNameFormsUseOneRequestedCardWithoutNeedingAnApi() {
        for (name in listOf("Future Nostalgia — Moonlight Edition", "Future Nostalgia (The Moonlight Edition)")) {
            val original = track("one", artist = "Dua Lipa", album = "Future Nostalgia").copy(albumArtist = "Dua Lipa")
            val extra = original.copy(id = "extra", album = name, albumId = 2)
            val group = albumGroups(listOf(extra, original)).single()
            assertEquals("Future Nostalgia", group.name); assertEquals(2, group.tracks.size)
            assertEquals(ReleaseKind.ALBUM, group.kind)
        }
    }
    @Test fun requestedNamesDoNotMergeCoversOtherArtistsSinglesOrConflictingAlbumArtistIdentities() {
        val original = track("1", album = "The Fame").copy(albumArtist = "Lady Gaga")
        val cover = original.copy(id = "2", album = "The Fame Monster", albumId = 2, albumArtist = "Another artist")
        assertEquals(2, albumGroups(listOf(original, cover)).size)
        val single = cover.copy(albumArtist = "Lady Gaga", releaseKind = ReleaseKind.SINGLE)
        assertEquals(2, albumGroups(listOf(original, single)).size)
        val namesakeOne = original.copy(albumCredits = listOf(ArtistCredit("Lady Gaga", mb1)))
        val namesakeTwo = cover.copy(albumArtist = "Lady Gaga", albumCredits = listOf(ArtistCredit("Lady Gaga", mb2)))
        assertEquals(2, albumGroups(listOf(namesakeOne, namesakeTwo)).size)
    }
    @Test fun identicalReleaseGroupTagsDoNotOverrideAContradictoryAlbumArtist() {
        val one = track("1").copy(releaseGroupId = mb1, albumArtist = "First")
        val two = one.copy(id = "2", albumId = 2, albumArtist = "Second")
        assertEquals(2, albumGroups(listOf(one, two)).size)
    }
    @Test fun genericEditionMarkersNeedSharedRecordingEvidenceAndDoNotStripLiveOrRemixAlbumNames() {
        val one = track("1").copy(recordingId = mb1)
        val copy = one.copy(id = "2", albumId = 2, album = "${one.album} (Deluxe Edition)")
        val group = albumGroups(listOf(copy, one)).single()
        assertEquals(one.album, group.name); assertEquals(1, group.tracks.size)
        assertEquals(2, albumGroups(listOf(one, copy.copy(recordingId = ""))).size)
        assertEquals(2, albumGroups(listOf(one, copy.copy(album = "${one.album} (Live)"))).size)
        assertEquals(2, albumGroups(listOf(one, copy.copy(album = "${one.album} (Remixes)"))).size)
    }
    @Test fun twoNativeReleasesSharingOneAndroidAlbumIdAreStillIdentifiableEditions() {
        val one = track("1").copy(releaseId = mb1, releaseGroupId = mb1)
        val two = one.copy(id = "2", releaseId = mb2)
        val group = albumGroups(listOf(one, two)).single()
        assertEquals(2, group.editions.size); assertNotEquals(one.editionKey, two.editionKey)
        assertEquals(one.albumKey, two.albumKey) // Existing v4 cover associations have not been renamed.
        assertTrue(group.containsTrack("2"))
    }
    @Test fun aNativeAlbumArtistGroupsCollaborationsWithoutRewritingTheOriginalCreditsOrCoverKeys() {
        val one = track("1", artist = "One feat. Two").copy(albumArtist = "One feat. Two", nativeAlbumArtist = "One")
        val two = one.copy(id = "2", artist = "One", albumArtist = "One", displayCover = "file:///old-cover.jpg")
        val group = albumGroups(listOf(one, two)).single()
        assertEquals(1, group.editions.size); assertEquals("One", group.artist)
        assertEquals("file:///old-cover.jpg", group.artwork)
        assertEquals("1:one feat. two", one.albumKey); assertEquals("One feat. Two", one.artist)
    }
    @Test fun unknownAlbumsAreNotAllCombinedIntoOneInventedRelease() {
        val one = track("1", album = "Album inconnu")
        assertEquals(2, albumGroups(listOf(one, one.copy(id = "2"))).size)
    }

    @Test fun aSavedRepresentativeChoiceAndANewNativeEditionKeepOneStableGroupId() {
        val one = track("1").copy(releaseGroupId = mb1, albumCredits = listOf(ArtistCredit("Artiste synthétique", mb2)))
        val two = one.copy(id = "2", albumId = 2)
        val before = albumGroups(listOf(one, two)).single()
        val family = AlbumFamily(before.id, setOf(one.editionKey, two.editionKey), one.editionKey, two.editionKey)
        val third = one.copy(id = "3", albumId = 3, albumCredits = emptyList())
        val after = albumGroups(listOf(one, two, third), CatalogChoices(families = mapOf(family.id to family))).single()
        assertEquals(before.id, after.id); assertEquals(3, after.editions.size)
        assertEquals(two.editionKey, after.representativeId)
    }

}
