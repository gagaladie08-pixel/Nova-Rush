package com.novalisten.app

import com.novalisten.app.enrichment.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AutomaticArtistLookupTest {
    private val id = "11111111-1111-1111-1111-111111111111"
    private val other = "22222222-2222-2222-2222-222222222222"
    private inner class Transport : RemoteTransport {
        val calls = mutableListOf<Pair<Provider, String>>()
        var ambiguous = false
        var matchingBacklink = true
        var matchingPage = true
        var frenchPage = true
        var image = true
        var licensed = true
        override suspend fun request(provider: Provider, path: String, parameters: Map<String, String>, headers: Map<String, String>, jsonBody: String?): String {
            calls += provider to path
            return when {
                provider == Provider.MUSICBRAINZ && path == "/ws/2/artist/" -> """{"artists":[{"id":"$id","name":"Singer"}${if (ambiguous) ",{\"id\":\"$other\",\"name\":\"Singer\"}" else ""}]}"""
                provider == Provider.MUSICBRAINZ -> """{"id":"$id","type":"Person","relations":[{"type":"wikidata","url":{"resource":"https://www.wikidata.org/wiki/Q123"}}]}"""
                provider == Provider.WIKIDATA -> """{"entities":{"Q123":{"claims":{"P434":[{"mainsnak":{"datavalue":{"value":"${if (matchingBacklink) id else other}"}}}]${if (image) ",\"P18\":[{\"mainsnak\":{\"datavalue\":{\"value\":\"Singer.jpg\"}}}]" else ""}},"sitelinks":{${if (frenchPage) "\"frwiki\":{\"title\":\"Singer\"}" else ""}}}}}"""
                provider == Provider.WIKIPEDIA_FR -> """{"query":{"pages":[{"pageid":42,"title":"Singer","extract":"Texte synthétique de test.","fullurl":"https://fr.wikipedia.org/wiki/Singer","pageprops":{"wikibase_item":"${if (matchingPage) "Q123" else "Q456"}"}}]}}"""
                provider == Provider.COMMONS && parameters.containsKey("titles") -> """{"query":{"pages":[{"pageid":1,"title":"File:Singer.jpg","imageinfo":[{"url":"https://upload.wikimedia.org/Singer.jpg","thumburl":"https://upload.wikimedia.org/320px-Singer.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:Singer.jpg","extmetadata":{"Artist":{"value":"Fixture"},"LicenseUrl":{"value":"${if (licensed) "https://creativecommons.org/licenses/by/4.0/" else "https://example.com/no-rights"}"}}}]}]}}"""
                provider == Provider.COMMONS -> """{"query":{"pages":[]}}"""
                provider == Provider.OPENVERSE -> """{"results":[]}"""
                else -> error("Unexpected provider: $provider")
            }
        }
        override suspend fun image(url: String, maxBytes: Int): ByteArray = error("Metadata lookup must not download image bytes")
    }
    @Test fun exactArtistNameStillRequiresTheIndependentIdentifierBacklinkAndArticleIdentity() = runTest {
        val transport = Transport(); val lookup = AutomaticArtistLookup(RemoteSources(transport) { null })
        val result = lookup.resolve(ArtistRequest("singer", "Singer"), true, true)
        assertTrue(result.identityVerified); assertNotNull(result.biography); assertNotNull(result.portrait)
        assertEquals(1, transport.calls.count { it.first == Provider.WIKIPEDIA_FR })
        assertFalse(transport.calls.any { it.first == Provider.WIKIPEDIA_EN || it.first == Provider.GENIUS || it.first == Provider.AZURE })
    }
    @Test fun homonymsStopBeforeFetchingABioOrPhoto() = runTest {
        val transport = Transport().apply { ambiguous = true }
        val result = AutomaticArtistLookup(RemoteSources(transport) { null }).resolve(ArtistRequest("singer", "Singer"), true, true)
        assertFalse(result.identityVerified); assertEquals(1, transport.calls.size)
    }
    @Test fun chosenOrNativeMbidAvoidsRepeatingTheNameSearch() = runTest {
        val transport = Transport()
        val result = AutomaticArtistLookup(RemoteSources(transport) { null }).resolve(ArtistRequest("singer", "Singer", id), true, true)
        assertTrue(result.identityVerified); assertFalse(transport.calls.any { it.second == "/ws/2/artist/" })
    }
    @Test fun mismatchedWikidataBacklinkCannotApplyAnything() = runTest {
        val transport = Transport().apply { matchingBacklink = false }
        val result = AutomaticArtistLookup(RemoteSources(transport) { null }).resolve(ArtistRequest("singer", "Singer", id), true, true)
        assertFalse(result.identityVerified); assertNull(result.biography); assertNull(result.portrait)
    }
    @Test fun aWikipediaArticleOfADifferentEntityCannotBeSaved() = runTest {
        val transport = Transport().apply { matchingPage = false }
        val result = AutomaticArtistLookup(RemoteSources(transport) { null }).resolve(ArtistRequest("singer", "Singer", id), true, false)
        assertTrue(result.identityVerified); assertNull(result.biography)
    }
    @Test fun missingFrenchNeverTriggersEnglishOrTranslationAutomatically() = runTest {
        val transport = Transport().apply { frenchPage = false }
        val result = AutomaticArtistLookup(RemoteSources(transport) { null }).resolve(ArtistRequest("singer", "Singer", id), true, false)
        assertNull(result.biography); assertTrue(result.note.contains("anglaise"))
        assertFalse(transport.calls.any { it.first in setOf(Provider.WIKIPEDIA_EN, Provider.AZURE) })
    }
    @Test fun missingP18OnlyDiscoversABoundedManualGallery() = runTest {
        val transport = Transport().apply { image = false }
        val result = AutomaticArtistLookup(RemoteSources(transport) { null }).resolve(ArtistRequest("singer", "Singer", id), false, true)
        assertNull(result.portrait); assertEquals(1, transport.calls.count { it.first == Provider.COMMONS })
        assertEquals(1, transport.calls.count { it.first == Provider.OPENVERSE })
    }
    @Test fun rightsCannotBeInferredFromAPublicImageUrlOrAnArtistId() = runTest {
        val transport = Transport().apply { licensed = false }
        val result = AutomaticArtistLookup(RemoteSources(transport) { null }).resolve(ArtistRequest("singer", "Singer", id), false, true)
        assertTrue(result.identityVerified); assertNull(result.portrait)
    }
}
