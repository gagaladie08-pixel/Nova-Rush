package com.novalisten.app

import com.novalisten.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

class LibraryRulesTest {
    private fun track(id: String = "1", duration: Long = 180_000, title: String = "Élévation") = Track(id, title, "Beyoncé", "Rêves", 1, "Beyoncé", duration, 1, 2024, 1, "été.flac", 1000, "audio/flac")
    @Test fun minimumIsExactlyOneMinute() {
        assertFalse(track(duration = 59_999).playable)
        assertTrue(track(duration = 60_000).playable)
        assertEquals(listOf("exact"), playableTracks(listOf(track("short", 59_999), track("exact", 60_000), track("unknown", 0)), emptySet()).map { it.id })
    }
    @Test fun unknownDurationIsVisibleButNotPlayableOrCounted() {
        val tracks = listOf(track("pending", 0), track("bad", -1), track("short", 1000))
        assertEquals(2, visibleTracks(tracks, emptySet()).size)
        assertTrue(playableTracks(tracks, emptySet()).isEmpty())
    }
    @Test fun hiddenFilterIsGlobalAndIncludesPending() {
        assertTrue(visibleTracks(listOf(track("a"), track("b", 0)), setOf("a", "b")).isEmpty())
        assertTrue(playableTracks(listOf(track("a")), setOf("a")).isEmpty())
    }
    @Test fun searchIgnoresAccentsAndCaseAcrossAllFourFields() {
        listOf("elevation", "BEYONCE", "reves", "ETE.FLAC").forEach { query -> assertEquals(query, 1, searchTracks(listOf(track()), query).size) }
        assertTrue(searchTracks(listOf(track()), "other").isEmpty())
    }
    @Test fun prependPreservesExistingOrderAndSkipsAlreadyPresentWithoutMovingIt() {
        assertEquals(listOf("c", "d", "a", "b"), prependUnique(listOf("a", "b"), listOf("b", "c", "c", "d")))
    }
    @Test fun differentFilesWithSameTitleAreNotDeduplicated() {
        assertEquals(listOf("file2", "file1"), prependUnique(listOf("file1"), listOf("file2")))
    }
    @Test fun readNextPreservesPastAndCurrentAndMovesFutureDuplicates() {
        assertEquals(listOf("past", "active", "new", "b", "c"), insertNext(listOf("past", "active", "b", "c", "b"), 1, listOf("new", "b", "b")))
    }
    @Test fun readNextWithoutCurrentReturnsAnOrderedPreparedBlock() {
        assertEquals(listOf("b", "a"), insertNext(emptyList(), 0, listOf("b", "a", "b")))
    }
    @Test fun newestReadNextComesBeforePriorRequests() {
        assertEquals(listOf("a", "d", "b", "c"), insertNext(insertNext(listOf("a", "c"), 0, listOf("b")), 0, listOf("d")))
    }
    @Test fun dayWindowIncludesTodayAndPreviousDatesNotRollingHours() {
        val today = LocalDate.of(2026, 11, 17)
        val event = ListenEvent(1, "a", 0, "UTC", "2026-11-15", 0)
        assertTrue(inLocalDayWindow(event, 3, today))
        assertFalse(inLocalDayWindow(event.copy(localDate = "2026-11-14"), 3, today))
        assertFalse(inLocalDayWindow(event.copy(localDate = "2026-11-18"), 3, today))
    }
    @Test fun historicalDateIsRecordedInOriginalZone() {
        val timestamp = Instant.parse("2026-11-17T23:30:00Z").toEpochMilli()
        val event = eventAt("a", timestamp, ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-11-18", event.localDate)
        assertEquals(8, event.localHour)
        assertEquals("Asia/Tokyo", event.zone)
    }
    @Test fun durationFormattingNeverShowsNegativeNumbers() {
        assertEquals("0:00", durationLabel(-1)); assertEquals("1:00", durationLabel(60_000)); assertEquals("1:01:01", durationLabel(3_661_000))
    }
}
