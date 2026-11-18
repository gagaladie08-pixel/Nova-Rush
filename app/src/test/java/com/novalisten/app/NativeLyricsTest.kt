package com.novalisten.app

import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeLyricsTest {
    private val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:00:01.000" end="00:00:03.000"><span begin="1.000s" end="1.500s">Clarté </span><span begin="1.800s" end="2.800s">nouvelle</span></p></div></body></tt>"""
    @Test fun ttmlRetainsRealWordWindowsAndExactCharacterRanges() {
        val result = NativeLyrics.ttml(xml)
        assertEquals("[00:01.000]Clarté nouvelle", result.text)
        val line = result.lines.single()
        assertEquals(1000L, line.timeMs); assertEquals(3000L, line.endMs)
        assertEquals(listOf(LyricWord(1000, 1500, 0, 6), LyricWord(1800, 2800, 7, 15)), line.words)
        assertEquals(.5f, line.words[0].progress(1250), .001f)
        assertEquals(0f, line.words[1].progress(1500), .001f)
    }
    @Test fun nativeTimingsSurviveTheSavedLyricsRoundTrip() {
        val result = NativeLyrics.ttml(xml)
        val payload = LyricTiming.encode(result.text, result.lines)
        assertTrue(payload.isNotBlank())
        assertEquals(result.lines, LyricTiming.decode(result.text, payload))
    }
    @Test fun editedTextInvalidatesOnlyTheExtraTimingsNotLrcLines() {
        val result = NativeLyrics.ttml(xml)
        val changed = result.text.replace("Clarté", "Lumière")
        val lines = LyricTiming.decode(changed, LyricTiming.encode(result.text, result.lines))
        assertEquals("Lumière nouvelle", lines.single().text); assertTrue(lines.single().words.isEmpty())
        assertEquals(1000L, lines.single().timeMs)
    }
    @Test fun corruptOrOutOfBoundsTimingPayloadCannotCrashTheRenderer() {
        val result = NativeLyrics.ttml(xml)
        val payload = JSONObject(LyricTiming.encode(result.text, result.lines))
        payload.getJSONArray("lines").getJSONObject(0).getJSONArray("words").getJSONArray(0).put(3, 99999)
        assertTrue(LyricTiming.decode(result.text, payload.toString()).single().words.isEmpty())
        assertTrue(LyricTiming.decode(result.text, "not json").single().words.isEmpty())
    }
    @Test fun startOnlyTimingsStepInsteadOfInventingDurations() {
        val word = LyricWord(1000, null, 0, 4)
        assertEquals(0f, word.progress(999), 0f); assertEquals(1f, word.progress(1000), 0f)
        assertEquals(1f, word.progress(1100), 0f)
    }
    @Test fun lineOnlyLrcNeverGetsManufacturedWords() {
        val lines = LrcParser.parse("[00:01.000]Clarté nouvelle\n[00:04.000]Un essai")
        assertTrue(lines.all { it.words.isEmpty() }); assertEquals("", LyricTiming.encode("[00:01.000]Clarté nouvelle", lines))
    }
    @Test fun enhancedLrcStripsTagsAndKeepsOnlyNativeStartsWithOffset() {
        val line = LrcParser.parse("[offset:100]\n[00:01.000]<00:01.000>Hello <00:01.500>world<00:02.000>").single()
        assertEquals("Hello world", line.text); assertEquals(1100L, line.timeMs)
        assertEquals(listOf(LyricWord(1100, null, 0, 5), LyricWord(1600, null, 6, 11)), line.words)
    }
    @Test fun repeatedLineStampsDoNotInventShiftedWordWindows() {
        val lines = LrcParser.parse("[00:01.000][00:10.000]<00:01.000>Hello <00:01.500>world")
        assertTrue(lines.first().words.isNotEmpty()); assertTrue(lines.last().words.isEmpty())
    }
    @Test fun invalidSegmentTextOrTimeFallsBackToTheIntactLine() {
        val text = "Clarté nouvelle"
        assertTrue(LyricTiming.line(1000, text, listOf(TimedToken("Absent", 1000, 1500))).words.isEmpty())
        assertTrue(LyricTiming.line(1000, text, listOf(TimedToken("Clarté", 900, 1500))).words.isEmpty())
        assertTrue(LyricTiming.line(1000, text, listOf(TimedToken("Clarté", 1000, 900))).words.isEmpty())
        assertEquals(text, LyricTiming.line(1000, text, listOf(TimedToken("Clarté", 1000, 5000)), 3000).text)
        assertTrue(LyricTiming.line(1000, text, listOf(TimedToken("Clarté", 1000, 5000)), 3000).words.isEmpty())
    }
    @Test fun ttmlNestedTimedSpanIsNotDuplicatedByItsTimedParent() {
        val result = NativeLyrics.ttml("""<tt><p begin="1s" end="3s"><span begin="1s"><span begin="1s" end="2s">Essai</span></span></p></tt>""")
        assertEquals(1, result.lines.single().words.size)
    }
    @Test fun unsupportedTimeContainersDoNotBecomeFakeAbsoluteClockLyrics() {
        assertTrue(NativeLyrics.ttml("""<tt><body><div begin="5s"><p begin="1s">Essai</p></div></body></tt>""").lines.isEmpty())
        assertTrue(NativeLyrics.ttml("""<tt><body timeContainer="seq"><p begin="1s">Essai</p></body></tt>""").lines.isEmpty())
        val relative = NativeLyrics.ttml("""<tt><p begin="5s"><span begin="1s" end="2s">Essai</span></p></tt>""")
        assertTrue(relative.lines.single().words.isEmpty())
    }
    @Test fun clocksSupportMillisecondsAndSecondsButNeverGuessFrames() {
        listOf("1234ms", "1.234s", "00:01.234", "00:00:01.234").forEach { assertEquals(1234L, LyricFormats.clock(it)) }
        listOf("-1s", "00:75.000", "12f", "00:00:01:12", "NaNs").forEach { assertNull(LyricFormats.clock(it)) }
    }
    @Test fun liriqoWordMillisecondsSurviveConversionAndSaving() {
        val candidate = RemoteParsers.liriqo("""{"metadata":{"title":"Test","artist":"Essai","duration":65},"tracks":[{"provider":"fixture","nativeWordTiming":true,"timed":[{"start":1234,"end":3000,"text":"Clarté de test","words":[{"start":1234,"end":1500,"text":"Clarté"},{"start":1800,"end":2000,"text":"de"},{"start":2200,"end":2800,"text":"test"}]}]}]}""", MusicQuery("Test", "Essai", durationMs = 65000)).single()
        val saved = candidate.asSavedLyrics()
        val line = LyricTiming.decode(saved.original, saved.originalTiming).single()
        assertEquals(1234L, line.words.first().startMs); assertEquals(1500L, line.words.first().endMs)
        assertEquals("Karaoké · minutage natif", candidate.timing); assertEquals(3, line.words.size)
    }
    @Test fun liriqoRejectsProportionalOrExplicitlyNonNativeWordsAndDoesNotAnimateDerivedEnds() {
        fun parse(fields: String) = RemoteParsers.liriqo("""{"metadata":{"title":"Essai","artist":"Test","duration":"01:05"},"tracks":[{$fields,"timed":[{"start":1000,"end":3000,"text":"Essai","words":[{"start":1000,"end":2000,"text":"Essai","derivedEnd":true}]}]}]}""", MusicQuery("Essai", "Test")).single()
        assertTrue(parse("\"provider\":\"lrclib_wordsync\",\"syncLevel\":\"word\"").nativeLines.single().words.isEmpty())
        assertTrue(parse("\"provider\":\"fixture\",\"syncLevel\":\"word\",\"nativeWordTiming\":false").nativeLines.single().words.isEmpty())
        val realStart = parse("\"provider\":\"fixture\",\"nativeRichsync\":true")
        assertEquals(65000L, realStart.durationMs)
        val word = realStart.nativeLines.single().words.single()
        assertNull(word.endMs); assertEquals(1f, word.progress(1000), 0f)
    }
    @Test fun liriqoCanKeepTopLevelWordArraysAddressedByLineId() {
        val result = RemoteParsers.liriqo("""{"tracks":[{"provider":"fixture","syncLevel":"word","timed":[{"id":0,"start":1000,"text":"Essai"}],"words":[[{"text":"Essai","start":1000,"end":2000}]]}]}""", MusicQuery("Essai", "Test")).single()
        assertEquals(2000L, result.nativeLines.single().words.single().endMs)
    }
    @Test fun nativeTtmlCanEnrichLineOnlyNormalizedDataWithoutLosingItsWordWindows() {
        val item = JSONObject().put("provider", "fixture").put("syncLevel", "word").put("ttml", xml)
            .put("timed", org.json.JSONArray("""[{"start":1000,"text":"Clarté nouvelle"}]"""))
        val body = JSONObject().put("tracks", org.json.JSONArray().put(item)).toString()
        assertEquals(2, RemoteParsers.liriqo(body, MusicQuery("Essai", "Test")).single().nativeLines.single().words.size)
    }
    @Test fun metadataFilledForAManualPreviewIsNotProofForAutomaticMatching() {
        val query = MusicQuery("Essai", "Test", durationMs = 65000)
        val result = RemoteParsers.liriqo("""{"metadata":{"duration":65},"tracks":[{"provider":"fixture","plain":"Texte"}]}""", query).single()
        assertEquals(query.title, result.title)
        assertFalse(result.hasMatchMetadata); assertFalse(LyricMatch.accepts(query, result))
    }
    @Test fun translationNeverSendsOrReusesOriginalWordTimingsAsFrenchTimings() {
        val original = "[00:01.000]<00:01.000>Hello <00:01.500>world"
        val plan = TranslationPlan(original)
        assertEquals(listOf("Hello world"), plan.contents)
        val french = plan.render(mapOf("Hello world" to "<00:01.000>Bonjour le monde"))
        assertEquals("[00:01.000]Bonjour le monde", french)
        assertTrue(LrcParser.parse(french).single().words.isEmpty())
    }
}
