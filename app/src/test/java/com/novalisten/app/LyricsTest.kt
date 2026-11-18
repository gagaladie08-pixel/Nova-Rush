package com.novalisten.app

import com.novalisten.app.data.LrcParser
import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test fun plainTextNeverBecomesFakeSynchronization() { assertTrue(LrcParser.parse("Bonjour\nLe monde").isEmpty()) }
    @Test fun parsesFractionsAndMultipleTimestamps() {
        val lines = LrcParser.parse("[ar:Artiste]\n[00:01.5][00:10.25]Une ligne\n[01:02.003]Fin")
        assertEquals(listOf(1500L, 10250L, 62003L), lines.map { it.timeMs })
        assertEquals(listOf("Une ligne", "Une ligne", "Fin"), lines.map { it.text })
    }
    @Test fun offsetIsAppliedAndClampedAtZero() { assertEquals(listOf(0L, 500L), LrcParser.parse("[offset:-500]\n[00:00.10]A\n[00:01]B").map { it.timeMs }) }
    @Test fun activeLineIsAbsentBeforeItsFirstTimestamp() {
        val lines = LrcParser.parse("[00:05]A\n[00:10]B")
        assertEquals(-1, LrcParser.activeIndex(lines, 0)); assertEquals(0, LrcParser.activeIndex(lines, 5000)); assertEquals(1, LrcParser.activeIndex(lines, 11000))
    }
    @Test fun malformedSecondsAreRejectedAndRowsAreSorted() { assertEquals(listOf(1000L, 2000L), LrcParser.parse("[00:02]B\n[00:75]Bad\n[00:01]A").map { it.timeMs }) }
}
