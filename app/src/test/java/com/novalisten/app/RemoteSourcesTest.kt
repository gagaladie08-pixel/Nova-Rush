package com.novalisten.app

import com.novalisten.app.enrichment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RemoteSourcesTest {
    private val query = MusicQuery("Lumière d’essai", "Artiste de test", "Album original de test", 65000)
    private val mbid = "12345678-1234-1234-1234-123456789abc"
    private class Fake(var body: String = "[]") : RemoteTransport {
        data class Request(val provider: Provider, val path: String, val params: Map<String, String>, val headers: Map<String, String>, val body: String?)
        val calls = mutableListOf<Request>()
        override suspend fun request(provider: Provider, path: String, parameters: Map<String, String>, headers: Map<String, String>, jsonBody: String?): String {
            calls += Request(provider, path, parameters, headers, jsonBody); return body
        }
        override suspend fun image(url: String, maxBytes: Int): ByteArray = error("No image download expected")
    }
    @Test fun constructingClientsMakesNoRequests() { val fake = Fake(); RemoteSources(fake) { null }; assertTrue(fake.calls.isEmpty()) }
    @Test fun lrclibAcceptsActualLrcAndNullPlain() {
        val result = RemoteParsers.lrclib("""[{"id":7,"trackName":"Test","artistName":"Local","albumName":"Tests","duration":65,"plainLyrics":null,"syncedLyrics":"[00:01.230]Lumière de test"}]""").single()
        assertEquals(65000, result.durationMs); assertEquals("[00:01.230]Lumière de test", result.text); assertEquals("Synchro · Ligne", result.timing)
    }
    @Test fun noLyricsOrInstrumentalIsNotInvented() { assertTrue(RemoteParsers.lrclib("""[{"id":1,"instrumental":true,"plainLyrics":null,"syncedLyrics":null}]""").isEmpty()) }
    @Test fun liriqoUsesMillisecondsNotSecondsAndKeepsUpstream() {
        val result = RemoteParsers.liriqo("""{"metadata":{"title":"Test","artist":"Essai"},"tracks":[{"provider":"test","upstreamProvider":"Source de test","syncLevel":"word","timed":[{"start":1234,"text":"Clarté de test","words":[{"start":1234,"end":1500,"text":"Clarté"}]}]}]}""", query).single()
        assertEquals("[00:01.234]Clarté de test", result.text); assertTrue(result.credit.provider.contains("Source de test")); assertFalse(result.timing.contains("Mot"))
    }
    @Test fun liriqoErrorAtHttp200IsNotAnEmptySuccess() {
        assertThrows(RemoteFailure::class.java) { RemoteParsers.liriqo("""{"error":"quota test"}""", query) }
    }
    @Test fun liriqoNoPrimaryAndEmptyTracksIsEmpty() { assertTrue(RemoteParsers.liriqo("""{"tracks":[],"primary":null}""", query).isEmpty()) }
    @Test fun ttmlUsesRealLineStartsOnly() {
        val result = LyricFormats.ttmlToText("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:00:01.250"><span begin="1.250s">Un </span><span begin="1.700s">essai</span></p></div></body></tt>""")
        assertEquals("[00:01.250]Un essai", result)
    }
    @Test fun ttmlRejectsEntitiesBeforeAnyXmlParsing() { assertThrows(IllegalArgumentException::class.java) { LyricFormats.ttmlToText("""<!DOCTYPE tt [<!ENTITY x SYSTEM "file:///private">]><tt><p>&x;</p></tt>""") } }
    @Test fun ttmlWithNoLineTimesRemainsPlain() { assertEquals("Texte de test", LyricFormats.ttmlToText("<tt><p>Texte de test</p></tt>")) }
    @Test fun textylSecondsAreConvertedExactlyOnce() { assertEquals("[00:02.500]Un autre essai", RemoteParsers.textyl("""[{"seconds":2.5,"lyrics":"Un autre essai"}]""", query).single().text) }
    @Test fun textylMalformedTimesAreNotInvented() { assertThrows(RemoteFailure::class.java) { RemoteParsers.textyl("""[{"lyrics":"Essai"}]""", query) } }
    @Test fun lyricSourcesNeverNeedOrReceiveApiKeysForCore() = runTest {
        val fake = Fake()
        val sources = RemoteSources(fake) { if (it == SecretSlot.LRC_CONTACT) "dev@example.org" else "test-only-not-a-real-key" }
        sources.lyrics(Provider.LRCLIB, query)
        assertTrue(fake.calls.single().headers.isEmpty()); assertFalse(fake.calls.single().params.containsKey("api_key"))
    }
    @Test fun lrcRedNeedsARealConfiguredContactBeforeSendingAnything() = runTest {
        val fake = Fake(); val sources = RemoteSources(fake) { null }
        try { sources.lyrics(Provider.LRCRED, query); fail("Expected missing contact") } catch (_: RemoteFailure) { }
        assertTrue(fake.calls.isEmpty())
    }
    @Test fun lrcRedIdentifiesClientAndDoesNotUseLyricsFullTextSearch() = runTest {
        val fake = Fake("""{"hits":[]}"""); val sources = RemoteSources(fake) { "dev@example.org" }
        sources.lyrics(Provider.LRCRED, query)
        val call = fake.calls.single(); assertTrue(call.headers.getValue("User-Agent").contains("dev@example.org")); assertFalse(call.params.containsKey("in"))
    }
    @Test fun lrcRedInvalidIsrcIsNotUsedAsAPath() { assertTrue(RemoteParsers.lrcRed("""{"hits":[{"isrc":"../private","title":"Test"}]}""").isEmpty()) }
    @Test fun betterCacheRequestsIncludeAllFourFieldsAndNoKey() = runTest {
        val fake = Fake("""{"ttml":"<tt><p>Essai original</p></tt>"}""")
        RemoteSources(fake) { "test-only-not-a-real-key" }.lyrics(Provider.BETTER, query)
        val call = fake.calls.single(); assertEquals(setOf("s", "a", "al", "d"), call.params.keys); assertTrue(call.headers.isEmpty())
    }
    @Test fun publicMetadataSearchDoesNotSendAudioFileNamesOrHistory() = runTest {
        val fake = Fake("""{"recordings":[]}""")
        RemoteSources(fake) { null }.recordings(query)
        assertEquals(Provider.MUSICBRAINZ, fake.calls.single().provider)
        assertEquals(setOf("query", "fmt", "limit"), fake.calls.single().params.keys)
    }
    @Test fun itunesCountryMustBeChosenNotInferred() = runTest {
        val fake = Fake(); val sources = RemoteSources(fake) { null }
        try { sources.itunes(query, ""); fail("No implicit country") } catch (_: IllegalArgumentException) { }
        assertTrue(fake.calls.isEmpty())
    }
    @Test fun lastfmMissingKeyIsSkippedWithoutNetwork() = runTest {
        val fake = Fake(); val sources = RemoteSources(fake) { null }
        try { sources.lastFmTags(query); fail("Missing key") } catch (_: RemoteFailure) { }
        assertTrue(fake.calls.isEmpty())
    }
    @Test fun lastfmReadsOnlyTagsNoScrobblingOrImages() = runTest {
        val fake = Fake("""{"toptags":{"tag":[{"name":"genre de test"}]}}""")
        val result = RemoteSources(fake) { if (it == SecretSlot.LASTFM) "test-only" else null }.lastFmTags(query)
        assertEquals(listOf("genre de test"), result); assertEquals("track.getTopTags", fake.calls.single().params["method"])
    }
    @Test fun biographyDisambiguationIsExcludedAndAttributionIsKept() {
        val body = """{"query":{"pages":[{"pageid":1,"title":"Ambigu","extract":"Test","pageprops":{"disambiguation":""}},{"pageid":2,"title":"Artiste de test","extract":"Biographie de test originale.","lastrevid":42,"fullurl":"https://fr.wikipedia.org/wiki/Test"}]}}"""
        val result = RemoteParsers.biography(body, "fr").single()
        assertEquals("2", result.id); assertTrue(result.credit.licence.contains("BY-SA")); assertTrue(result.credit.details.contains("42")); assertFalse(result.full)
    }
    @Test fun commonsRequiresAnActualKnownLicence() {
        val body = """{"query":{"pages":[{"pageid":1,"title":"File:Test.jpg","imageinfo":[{"url":"https://upload.wikimedia.org/test.jpg","thumburl":"https://upload.wikimedia.org/thumb.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:Test.jpg","extmetadata":{"Artist":{"value":"<b>Créateur de test</b>"},"LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0/"},"LicenseShortName":{"value":"CC BY-SA 4.0"}}}]}]}}"""
        val image = RemoteParsers.commons(body).single(); assertTrue(image.licenceKnown); assertEquals("Créateur de test", image.credit.author); assertTrue(image.date.contains("inconnue"))
    }
    @Test fun openverseDoesNotTurnIndexedDateIntoPhotoDate() {
        val body = """{"results":[{"id":"test","title":"Photo test","url":"https://example.org/a.jpg","thumbnail":"https://example.org/t.jpg","license_url":"https://creativecommons.org/licenses/by/4.0/","indexed_on":"2026-11-17","foreign_landing_url":"https://example.org/source"}]}"""
        val image = RemoteParsers.openverse(body).single(); assertTrue(image.needsOriginCheck); assertTrue(image.date.contains("inconnue"))
    }
    @Test fun caaApprovalIsNotALicenceAndHttpIsUpgradedOnlyOnKnownHosts() {
        val body = """{"images":[{"id":"1","front":true,"approved":true,"image":"http://coverartarchive.org/release/test/1.jpg","thumbnails":{"250":"http://coverartarchive.org/release/test/1-250.jpg"}}]}"""
        val image = RemoteParsers.covers(body, mbid, false).single(); assertFalse(image.licenceKnown); assertTrue(image.original.startsWith("https://"))
    }
    @Test fun httpsRejectsPrivateLiteralAddressesAndCredentials() {
        listOf("http://example.org/a", "https://127.0.0.1/a", "https://10.0.0.1/a", "https://[::1]/a", "https://100.64.0.1/a", "https://user:password@example.org/a", "https://localhost/a", "https://example.org:8080/a").forEach { url -> assertThrows(IllegalArgumentException::class.java) { RemoteSafety.https(url) } }
        RemoteSafety.https("https://musicbrainz.org/ws/2/")
    }
    @Test fun addressFilterRejectsPrivateAndAcceptsGlobal() {
        assertFalse(RemoteSafety.publicAddress(InetAddress.getByName("192.168.1.1")))
        assertFalse(RemoteSafety.publicAddress(InetAddress.getByName("169.254.169.254")))
        assertFalse(RemoteSafety.publicAddress(InetAddress.getByName("198.18.0.1")))
        assertTrue(RemoteSafety.publicAddress(InetAddress.getByName("8.8.8.8")))
    }
    @Test fun apiCredentialsCannotGoToAnotherProviderHost() { assertThrows(IllegalArgumentException::class.java) { RemoteSafety.api(Provider.GENIUS, "https://example.org/") } }
    @Test fun caaRedirectMustBeDocumentedArchiveIndexNotAnArbitraryUrl() {
        assertEquals("https://archive.org/download/mbid-$mbid/index.json", RemoteSafety.caaRedirect("http://archive.org/download/mbid-$mbid/index.json"))
        assertThrows(IllegalArgumentException::class.java) { RemoteSafety.caaRedirect("https://archive.org.evil.example/index.json") }
        assertThrows(IllegalArgumentException::class.java) { RemoteSafety.caaRedirect("https://archive.org/other.json") }
    }
    @Test fun imagePolicyDoesNotLaunderBlockedProvidersThroughImport() { listOf("https://cdn.dzcdn.net/a.jpg", "https://images.genius.com/a.jpg", "https://lastfm.freetls.fastly.net/a.png").forEach { assertThrows(IllegalArgumentException::class.java) { RemoteSafety.image(it) } } }
    @Test fun retryAfterSupportsSecondsAndHttpDatesWithoutInfiniteWait() {
        val now = 1_000_000L
        assertEquals(now + 20_000, RemoteSafety.retryAfter("20", now))
        assertEquals(0L, RemoteSafety.retryAfter("Thu, 01 Jan 1970 00:00:00 GMT", 0))
        assertNull(RemoteSafety.retryAfter("not-a-date", now))
    }
    @Test fun credentialsRejectHeaderInjectionButAllowPrivateAppTokens() {
        assertThrows(IllegalArgumentException::class.java) { SecretSlot.GENIUS.validate("token\nX-Evil:1") }
        assertThrows(IllegalArgumentException::class.java) { SecretSlot.AZURE_REGION.validate("https://example.org") }
        assertEquals("test-only-123", SecretSlot.DISCOGS.validate(" test-only-123 "))
    }
    @Test fun fanartV32UsesDocumentedArrayAndProjectHeaders() = runTest {
        val fake = Fake("""{"albums":[{"release_group_id":"$mbid","albumcover":[{"id":"1","url":"https://assets.fanart.tv/fanart/music/test/albumcover/test.jpg","copyright":"Créateur de test"}]}]}""")
        val sources = RemoteSources(fake) { when (it) { SecretSlot.FANART -> "test-only-project"; SecretSlot.FANART_PERSONAL -> "test-only-personal"; else -> null } }
        val image = sources.fanart(mbid, false).single()
        val call = fake.calls.single()
        assertEquals("/v3.2/music/albums/$mbid", call.path)
        assertEquals(mapOf("api-key" to "test-only-project", "client-key" to "test-only-personal"), call.headers)
        assertTrue(call.params.isEmpty()); assertTrue(image.preview.contains("/preview/")); assertFalse(image.licenceKnown)
    }
    @Test fun fanartArtistOnlyUsesPortraitsNotLogosOrBackgrounds() {
        val image = RemoteParsers.fanart("""{"artistthumb":[{"id":"portrait","url":"https://assets.fanart.tv/fanart/music/test/artistthumb/a.jpg"}],"hdmusiclogo":[{"id":"logo"}],"artistbackground":[{"id":"background"}]}""", mbid, true).single()
        assertEquals("fanart:portrait", image.id)
    }
    @Test fun known404NoLyricResultsAreEmptyNotProtectedAccess() {
        assertTrue(RemoteParsers.better("null", query).isEmpty())
        assertTrue(RemoteParsers.textyl("null", query).isEmpty())
        assertTrue(RemoteParsers.ovh("null", query).isEmpty())
    }
    @Test fun openLicenceDoesNotConfuseAllCreativeCommonsWithReusable() {
        assertTrue(RemoteSafety.openLicence("https://creativecommons.org/licenses/by-sa/4.0/"))
        assertTrue(RemoteSafety.openLicence("http://creativecommons.org/publicdomain/zero/1.0/"))
        assertFalse(RemoteSafety.openLicence("https://creativecommons.org/licenses/by-nc/4.0/"))
        assertFalse(RemoteSafety.openLicence("https://creativecommons.org.evil.example/licenses/by/4.0/"))
        assertFalse(RemoteSafety.openLicence("https://creativecommons.org/licenses/by/../by-nc/4.0/"))
    }
    @Test fun translationKeepsTimestampsBlankLinesAndMetadata() {
        val plan = TranslationPlan("[ar:Test local]\n[00:01.234]Test line\n\n[00:04.000][00:08.000]Again")
        assertEquals("[ar:Test local]\n[00:01.234]Ligne de test\n\n[00:04.000][00:08.000]Encore", plan.render(mapOf("Test line" to "Ligne de test", "Again" to "Encore")))
    }
    @Test fun translationCannotInjectNewTimedControlData() { assertEquals("[00:01.000]Texte suite", TranslationPlan("[00:01.000]Test").render(mapOf("Test" to "[99:00.000]Texte\nsuite"))) }
    @Test fun translationRejectsOversizeOrMissingSegmentsWithoutTruncating() {
        assertThrows(IllegalArgumentException::class.java) { TranslationPlan("x".repeat(20_001)) }
        assertThrows(RemoteFailure::class.java) { TranslationPlan("One\nTwo").render(mapOf("One" to "Un")) }
    }
    @Test fun limiterNeverExceedsTwoRequestsEvenAcrossProviders() = runTest {
        val limiter = RequestLimiter({ testScheduler.currentTime }, { testScheduler.currentTime })
        var active = 0; var peak = 0
        coroutineScope { (1..8).forEach { index -> launch { limiter.execute("source$index", 0) { active++; peak = maxOf(active, peak); delay(100); active-- } } } }
        assertEquals(2, peak)
    }
    @Test fun limiterSerializesSupplierAndRespectsMinimumInterval() = runTest {
        val limiter = RequestLimiter({ testScheduler.currentTime }, { testScheduler.currentTime })
        val starts = mutableListOf<Long>()
        coroutineScope { repeat(3) { launch { limiter.execute("musicbrainz", 1100) { starts += testScheduler.currentTime; delay(100) } } } }
        assertEquals(listOf(0L, 1200L, 2400L), starts)
    }
    @Test fun limiterDoesNotTreatQuotaAsNoResultOrAutoRetryIt() = runTest {
        val limiter = RequestLimiter({ testScheduler.currentTime }, { testScheduler.currentTime })
        limiter.cooldown("test", 60_000)
        var sent = false
        try { limiter.execute("test", 0) { sent = true }; fail("Expected quota") } catch (_: RemoteFailure) { }
        assertFalse(sent)
    }
    @Test fun cancelledWorkDoesNotConsumeAnExtraSupplierSlot() = runTest {
        val limiter = RequestLimiter({ testScheduler.currentTime }, { testScheduler.currentTime })
        val job = launch { limiter.execute("test", 500) { delay(10_000) } }
        testScheduler.runCurrent(); job.cancelAndJoin()
        var reached = false; limiter.execute("test", 0) { reached = true }; assertTrue(reached)
    }
    @Test fun azureUsesOnlyTheChosenTextAndExplicitSourceLanguage() = runTest {
        val fake = Fake("""[{"translations":[{"to":"fr","text":"Texte de test"}]}]""")
        val sources = RemoteSources(fake) { when (it) { SecretSlot.AZURE -> "test-only"; else -> null } }
        assertEquals("[00:01.000]Texte de test", sources.azure(TranslationPlan("[00:01.000]Test text"), "en"))
        val call = fake.calls.single(); assertEquals(Provider.AZURE, call.provider); assertFalse(call.body!!.contains("Lumière")); assertEquals("en", call.params["from"]); assertEquals("test-only", call.headers["Ocp-Apim-Subscription-Key"])
    }
}
