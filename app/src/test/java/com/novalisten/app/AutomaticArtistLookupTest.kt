package com.novalisten.app

import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AutomaticArtistLookupTest {
    private val id = "11111111-1111-1111-1111-111111111111"
    private val other = "22222222-2222-2222-2222-222222222222"
    private val recordingId = "33333333-3333-3333-3333-333333333333"
    private val context = ArtistLocalContext("Synthetic song", "Synthetic album", 65000)
    private val request get() = ArtistRequest("singer", "Singer", context = context)
    private fun obj(vararg fields: Pair<String, Any>) = JSONObject().apply { fields.forEach { put(it.first, it.second) } }
    private fun array(vararg values: Any) = JSONArray().apply { values.forEach { put(it) } }
    private inner class Transport : RemoteTransport {
        val calls = mutableListOf<Pair<Provider, String>>()
        var ambiguous = false
        var truncated = false
        var matchingBacklink = true
        var matchingPage = true
        var matchingLocalMetadata = true
        var matchingRecordingId = true
        var frenchPage = true
        var image = true
        var licensed = true
        var author = true
        var failBio = false
        var failPhoto = false
        override suspend fun request(provider: Provider, path: String, parameters: Map<String, String>, headers: Map<String, String>, jsonBody: String?): String {
            calls += provider to path
            fun artistCredit(mbid: String) = obj("name" to "Singer", "artist" to obj("id" to mbid, "name" to "Singer"))
            fun recording(mbid: String) = obj("id" to if (matchingRecordingId) recordingId else other,
                "title" to if (matchingLocalMetadata) context.title else "Another song", "length" to context.durationMs,
                "artist-credit" to array(artistCredit(mbid)), "releases" to array(obj("title" to context.album)))
            return when {
                provider == Provider.MUSICBRAINZ && path == "/ws/2/recording/" -> obj("count" to if (truncated) 99 else if (ambiguous) 2 else 1,
                    "recordings" to if (ambiguous) array(recording(id), recording(other)) else array(recording(id))).toString()
                provider == Provider.MUSICBRAINZ && path.startsWith("/ws/2/recording/") -> recording(id).toString()
                provider == Provider.MUSICBRAINZ && path == "/ws/2/artist/" -> error("Automatic name-only searches must not be used as local identity proof")
                provider == Provider.MUSICBRAINZ -> obj("id" to id, "name" to "Singer", "type" to "Person", "relations" to array(obj("type" to "wikidata", "url" to obj("resource" to "https://www.wikidata.org/wiki/Q123")))).toString()
                provider == Provider.WIKIDATA -> {
                    val claims = obj("P434" to array(obj("mainsnak" to obj("datavalue" to obj("value" to if (matchingBacklink) id else other)))))
                    if (image) claims.put("P18", array(obj("mainsnak" to obj("datavalue" to obj("value" to "Singer.jpg")))))
                    val sitelinks = JSONObject(); if (frenchPage) sitelinks.put("frwiki", obj("title" to "Singer"))
                    obj("entities" to obj("Q123" to obj("claims" to claims, "sitelinks" to sitelinks))).toString()
                }
                provider == Provider.WIKIPEDIA_FR -> {
                    if (failBio) throw RemoteFailure("Synthetic biography outage")
                    obj("query" to obj("pages" to array(obj("pageid" to 42, "title" to "Singer", "extract" to "Texte synthétique de test.", "fullurl" to "https://fr.wikipedia.org/wiki/Singer", "pageprops" to obj("wikibase_item" to if (matchingPage) "Q123" else "Q456"))))).toString()
                }
                provider == Provider.COMMONS && parameters.containsKey("titles") -> {
                    if (failPhoto) throw RemoteFailure("Synthetic image outage")
                    val meta = obj("LicenseUrl" to obj("value" to if (licensed) "https://creativecommons.org/licenses/by/4.0/" else "https://example.com/no-rights"))
                    if (author) meta.put("Artist", obj("value" to "Fixture"))
                    obj("query" to obj("pages" to array(obj("pageid" to 1, "title" to "File:Singer.jpg", "imageinfo" to array(obj("url" to "https://upload.wikimedia.org/Singer.jpg", "thumburl" to "https://upload.wikimedia.org/320px-Singer.jpg", "descriptionurl" to "https://commons.wikimedia.org/wiki/File:Singer.jpg", "extmetadata" to meta)))))).toString()
                }
                else -> error("Unexpected provider: $provider")
            }
        }
        override suspend fun image(url: String, maxBytes: Int): ByteArray = error("Metadata lookup must not download image bytes")
    }
    private suspend fun lookup(transport: Transport, value: ArtistRequest = request, biography: Boolean = true, portrait: Boolean = true) =
        AutomaticArtistLookup(RemoteSources(transport) { null }).resolve(value, biography, portrait)

    @Test fun evenOneExactCatalogueNameWithoutLocalEvidenceDoesNotProveTheFileArtist() = runTest {
        val transport = Transport()
        val result = lookup(transport, ArtistRequest("singer", "Singer"))
        assertFalse(result.identityVerified); assertTrue(transport.calls.isEmpty())
    }
    @Test fun localSongAlbumDurationAndArtistCreditPlusIndependentBacklinksPermitAutomaticAttribution() = runTest {
        val transport = Transport(); val result = lookup(transport)
        assertTrue(result.identityVerified); assertEquals(id, result.verifiedMbid); assertNotNull(result.biography); assertNotNull(result.portrait)
        assertEquals(1, transport.calls.count { it.first == Provider.WIKIPEDIA_FR })
        assertFalse(transport.calls.any { it.first == Provider.WIKIPEDIA_EN || it.first == Provider.GENIUS || it.first == Provider.AZURE })
    }
    @Test fun homonymousCreditsForTheSameLocalContextStopBeforeBioOrPhoto() = runTest {
        val transport = Transport().apply { ambiguous = true }
        assertFalse(lookup(transport).identityVerified); assertEquals(1, transport.calls.size)
    }
    @Test fun aTruncatedResultPageCannotHideAnotherHomonym() = runTest {
        val transport = Transport().apply { truncated = true }
        assertFalse(lookup(transport).identityVerified); assertEquals(1, transport.calls.size)
    }
    @Test fun theContextMustReallyAgreeNotJustTheArtistName() = runTest {
        val transport = Transport().apply { matchingLocalMetadata = false }
        assertFalse(lookup(transport).identityVerified); assertEquals(1, transport.calls.size)
    }
    @Test fun nativeOrChosenArtistIdAvoidsRepeatingNameOrRecordingSearch() = runTest {
        val transport = Transport()
        assertTrue(lookup(transport, ArtistRequest("singer", "Singer", id)).identityVerified)
        assertFalse(transport.calls.any { it.second.startsWith("/ws/2/recording/") || it.second == "/ws/2/artist/" })
    }
    @Test fun nativeRecordingIdIsCheckedBeforeItsArtistCreditCanBeUsed() = runTest {
        val transport = Transport()
        val value = request.copy(context = context.copy(recordingId = recordingId))
        assertTrue(lookup(transport, value).identityVerified)
        val wrong = Transport().apply { matchingRecordingId = false }
        assertFalse(lookup(wrong, value).identityVerified)
    }
    @Test fun unknownAlbumAndNoNativeIdentifiersRemainProvisionalWithoutANetworkProbe() = runTest {
        val transport = Transport()
        assertFalse(lookup(transport, request.copy(context = context.copy(album = "Album inconnu"))).identityVerified)
        assertTrue(transport.calls.isEmpty())
    }
    @Test fun mismatchedWikidataBacklinkCannotApplyAnything() = runTest {
        val result = lookup(Transport().apply { matchingBacklink = false })
        assertFalse(result.identityVerified); assertNull(result.biography); assertNull(result.portrait)
    }
    @Test fun aWikipediaArticleOfADifferentEntityCannotBeSaved() = runTest {
        val result = lookup(Transport().apply { matchingPage = false }, portrait = false)
        assertTrue(result.identityVerified); assertNull(result.biography)
    }
    @Test fun missingFrenchNeverTriggersEnglishOrTranslationAutomatically() = runTest {
        val transport = Transport().apply { frenchPage = false }; val result = lookup(transport, portrait = false)
        assertNull(result.biography); assertTrue(result.note.contains("anglaise"))
        assertFalse(transport.calls.any { it.first in setOf(Provider.WIKIPEDIA_EN, Provider.AZURE) })
    }
    @Test fun missingP18DoesNotTurnTheFirstBroadSearchResultIntoAnAutomaticPortrait() = runTest {
        val transport = Transport().apply { image = false }; val result = lookup(transport, biography = false)
        assertTrue(result.identityVerified); assertNull(result.portrait); assertTrue(result.proposals.isEmpty())
        assertFalse(transport.calls.any { it.first == Provider.COMMONS || it.first == Provider.OPENVERSE })
    }
    @Test fun rightsAndAttributionCannotBeInferredFromAnImageUrlOrArtistId() = runTest {
        assertNull(lookup(Transport().apply { licensed = false }, biography = false).portrait)
        assertNull(lookup(Transport().apply { author = false }, biography = false).portrait)
    }
    @Test fun biographyAndPhotoMetadataFailuresAreIndependent() = runTest {
        val photo = lookup(Transport().apply { failBio = true })
        assertNull(photo.biography); assertNotNull(photo.portrait)
        val bio = lookup(Transport().apply { failPhoto = true })
        assertNotNull(bio.biography); assertNull(bio.portrait)
    }
    @Test fun anIdBorrowedFromAnotherLocalFileIsNotTreatedAsNativeProofForThePlayingFile() {
        val one = Track("1", "First", "Same", "Album", 1, "Same", 65000, 1, 2020, 1, "one.flac", 10, "audio/flac", credits = listOf(ArtistCredit("Same", id)))
        val two = one.copy(id = "2", credits = emptyList())
        val groups = artistGroups(listOf(one, two))
        assertEquals(id, groups.single().mbid)
        assertEquals("", artistRequestsForTrack(two, LocalData(), groups).single().mbid)
        assertEquals(id, artistRequestsForTrack(one, LocalData(), groups).single().mbid)
        val manual = LocalData(identities = mapOf("artist:${groups.single().id}" to other))
        assertEquals(other, artistRequestsForTrack(two, manual, groups).single().mbid)
        assertNotEquals(artistRequestsForTrack(two, LocalData(), groups), artistRequestsForTrack(two, manual, groups))
    }
}
