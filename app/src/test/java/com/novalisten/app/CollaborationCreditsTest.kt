package com.novalisten.app

import com.novalisten.app.data.ArtistCredit
import com.novalisten.app.enrichment.*
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CollaborationCreditsTest {
    private val first = "11111111-1111-1111-1111-111111111111"
    private val second = "22222222-2222-2222-2222-222222222222"
    private val other = "33333333-3333-3333-3333-333333333333"
    private val context = ArtistLocalContext("Song", "Album", 180000)
    private val request = ArtistRequest("anyma & lisa", "Anyma & LISA", context = context)
    private fun credit(name: String, id: String) = JSONObject().put("name", name).put("artist", JSONObject().put("name", name).put("id", id))
    private fun recording(credits: List<JSONObject> = listOf(credit("Anyma", first), credit("LISA", second))) = JSONObject()
        .put("id", other).put("title", context.title).put("length", context.durationMs)
        .put("artist-credit", JSONArray(credits)).put("releases", JSONArray().put(JSONObject().put("title", context.album)))
    private class Fixture(var body: String) : RemoteTransport {
        val paths = mutableListOf<String>()
        val queries = mutableListOf<Map<String, String>>()
        override suspend fun request(provider: Provider, path: String, parameters: Map<String, String>, headers: Map<String, String>, jsonBody: String?): String {
            assertEquals(Provider.MUSICBRAINZ, provider); paths += path; queries += parameters; return body
        }
        override suspend fun image(url: String, maxBytes: Int): ByteArray = error("No image bytes in identity resolution")
    }
    private fun fixture(vararg recordings: JSONObject) = Fixture(JSONObject().put("count", recordings.size).put("recordings", JSONArray(recordings.toList())).toString())

    @Test fun completeUnambiguousRecordingContextProducesSeparateNativeCredits() = runTest {
        val transport = fixture(recording())
        val result = RemoteSources(transport) { null }.collaborationCredits(request)
        assertEquals(listOf(ArtistCredit("Anyma", first), ArtistCredit("LISA", second)), result)
        assertEquals(listOf("/ws/2/recording/"), transport.paths)
        assertFalse(transport.queries.single().toString().contains("content://"))
    }
    @Test fun conflictingIdsForTheSameContextAreAmbiguousNotTheFirstNameMatch() = runTest {
        val transport = fixture(recording(), recording(listOf(credit("Anyma", first), credit("LISA", other))))
        assertTrue(RemoteSources(transport) { null }.collaborationCredits(request).isEmpty())
    }
    @Test fun aTruncatedPageOrIncompleteExtraCreditCannotCertifyACompleteCollaboration() = runTest {
        val transport = fixture(recording())
        transport.body = JSONObject(transport.body).put("count", 99).toString()
        assertTrue(RemoteSources(transport) { null }.collaborationCredits(request).isEmpty())
        val extra = fixture(recording(listOf(credit("Anyma", first), credit("LISA", second), credit("Unidentified guest", "bad-id"))))
        assertTrue(RemoteSources(extra) { null }.collaborationCredits(request).isEmpty())
    }
    @Test fun titlesAlbumsDurationsAndCompleteNamesMustAgree() = runTest {
        for (wrong in listOf(recording().put("title", "Other"), recording().put("length", 130000),
            recording().put("releases", JSONArray().put(JSONObject().put("title", "Other"))), recording(listOf(credit("Anyma", first))))) {
            assertTrue(RemoteSources(fixture(wrong)) { null }.collaborationCredits(request).isEmpty())
        }
        val transport = fixture(recording())
        assertTrue(RemoteSources(transport) { null }.collaborationCredits(request.copy(context = context.copy(durationMs = 0))).isEmpty())
        assertTrue(transport.paths.isEmpty())
    }
    @Test fun aRealGroupCreditIsNotSplitByItsPunctuation() = runTest {
        val group = request.copy(name = "An Unknown & Group")
        val transport = fixture(recording(listOf(credit(group.name, first))))
        assertTrue(RemoteSources(transport) { null }.collaborationCredits(group).isEmpty())
    }
    @Test fun nativeRecordingLookupAlsoValidatesTheReturnedIdentifier() = runTest {
        val transport = Fixture(recording().toString())
        val native = request.copy(context = context.copy(recordingId = other))
        assertEquals(2, RemoteSources(transport) { null }.collaborationCredits(native).size)
        assertEquals(listOf("/ws/2/recording/$other"), transport.paths)
        transport.body = recording().put("id", first).toString()
        assertTrue(RemoteSources(transport) { null }.collaborationCredits(native).isEmpty())
    }
}
