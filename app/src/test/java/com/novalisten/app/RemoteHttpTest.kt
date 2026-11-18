package com.novalisten.app

import com.novalisten.app.enrichment.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

/** In-memory OkHttp interceptors only: no test contacts a real provider or requires a real key. */
class RemoteHttpTest {
    private fun client(handler: (Request) -> Response): OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).addInterceptor { chain -> handler(chain.request()) }.build()
    private fun response(request: Request, code: Int, body: String = "{}", headers: Map<String, String> = emptyMap(), type: String = "application/json"): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test fixture")
        .apply { headers.forEach { (key, value) -> addHeader(key, value) } }.body(body.toResponseBody(type.toMediaType())).build()

    @Test fun credentialBearingRedirectIsRejectedWithoutSendingKeyElsewhere() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<Request>())
        val http = RemoteHttp(client { request -> calls += request; response(request, 302, headers = mapOf("Location" to "https://example.org/leak")) })
        try { http.request(Provider.GENIUS, "/search", headers = mapOf("Authorization" to "Bearer test-only")); fail("Redirect must fail") } catch (e: RemoteFailure) { assertFalse(e.publicMessage().contains("test-only")) }
        assertEquals(1, calls.size); assertEquals("api.genius.com", calls.single().url.host)
    }
    @Test fun documentedCaaRedirectIsFollowedWithNoOriginalHeaders() = runBlocking {
        val id = "12345678-1234-1234-1234-123456789abc"
        val calls = Collections.synchronizedList(mutableListOf<Request>())
        val http = RemoteHttp(client { request ->
            calls += request
            if (request.url.host == "coverartarchive.org") response(request, 307, headers = mapOf("Location" to "http://archive.org/download/mbid-$id/index.json"))
            else response(request, 200, "{\"images\":[]}")
        })
        assertEquals("{\"images\":[]}", http.request(Provider.CAA, "/release/$id/", headers = mapOf("X-Test-Private" to "test-only")))
        assertEquals(2, calls.size); assertNull(calls[1].header("X-Test-Private")); assertEquals("https", calls[1].url.scheme)
    }
    @Test fun caaCannotRedirectToAnUnrelatedJsonHost() = runBlocking {
        var calls = 0
        val http = RemoteHttp(client { request -> calls++; response(request, 307, headers = mapOf("Location" to "https://example.org/index.json")) })
        try { http.request(Provider.CAA, "/release/test/"); fail("Unexpected redirect") } catch (_: IllegalArgumentException) { }
        assertEquals(1, calls)
    }
    @Test fun quotaAndRetryAfterSuspendTheProviderNotTheLocalLibrary() = runBlocking {
        var calls = 0
        val http = RemoteHttp(client { request -> calls++; response(request, 429, headers = mapOf("Retry-After" to "60")) })
        repeat(2) { try { http.request(Provider.LRCLIB, "/api/search"); fail("Quota is an error, not no lyrics") } catch (_: RemoteFailure) { } }
        assertEquals(1, calls)
    }
    @Test fun better404IsAMissingResultWhile401RemainsAnAccessFailure() = runBlocking {
        val noLyrics = RemoteHttp(client { request -> response(request, 404) })
        assertTrue(RemoteParsers.better(noLyrics.request(Provider.BETTER, "/getLyrics"), MusicQuery("Test", "Essai")).isEmpty())
        val protected = RemoteHttp(client { request -> response(request, 401) })
        try { protected.request(Provider.BETTER, "/getLyrics"); fail("Protected cache must not look like a no-result") } catch (e: RemoteFailure) { assertTrue(e.safeMessage.contains("protégé")) }
    }
    @Test fun oversizedResponseIsStoppedAndNeverReturnedAsPartialLyrics() = runBlocking {
        val http = RemoteHttp(client { request -> response(request, 200, "x".repeat(2_000_001)) })
        try { http.request(Provider.LRCLIB, "/api/search"); fail("Must reject oversized body") } catch (e: RemoteFailure) { assertTrue(e.safeMessage.contains("volumineuse")) }
    }
    @Test fun imageRedirectToPrivateAddressIsBlockedBeforeSecondRequest() = runBlocking {
        var calls = 0
        val http = RemoteHttp(client { request -> calls++; response(request, 302, headers = mapOf("Location" to "https://127.0.0.1/private.jpg")) })
        try { http.image("https://upload.wikimedia.org/test.jpg"); fail("Private image request") } catch (_: IllegalArgumentException) { }
        assertEquals(1, calls)
    }
    @Test fun imageResponsesDoNotAcceptHtmlOrJsonAsPictures() = runBlocking {
        val http = RemoteHttp(client { request -> response(request, 200, "<html>not a picture</html>", type = "text/html") })
        try { http.image("https://upload.wikimedia.org/test.jpg"); fail("Not an image") } catch (_: RemoteFailure) { }
    }
    @Test fun forbiddenImageSourceCannotBeLaunderedByAThumbnailRedirect() = runBlocking {
        var calls = 0
        val http = RemoteHttp(client { request -> calls++; response(request, 302, headers = mapOf("Location" to "https://cdn.dzcdn.net/a.jpg")) })
        try { http.image("https://api.openverse.org/thumb/test"); fail("Blocked original provider") } catch (_: IllegalArgumentException) { }
        assertEquals(1, calls)
    }
}
