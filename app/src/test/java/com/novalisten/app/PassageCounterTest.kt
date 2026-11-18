package com.novalisten.app

import com.novalisten.app.playback.PassageCounter
import org.junit.Assert.*
import org.junit.Test

class PassageCounterTest {
    @Test fun exactlySixtySecondsAndOnlyOncePerPassage() {
        val c = PassageCounter(); c.sample(0, 0, true)
        assertFalse(c.sample(59_999, 59_999, true))
        assertTrue(c.sample(60_000, 60_000, true))
        assertFalse(c.sample(120_000, 120_000, true))
    }
    @Test fun pausePreservesCumulativeTimeButAddsNothing() {
        val c = PassageCounter(); c.sample(0, 0, true); c.sample(45_000, 45_000, false)
        assertEquals(45_000L, c.playedMs)
        c.sample(45_000, 100_000, true)
        assertTrue(c.sample(60_000, 115_000, true))
    }
    @Test fun forwardSeekIsNeverPlayedTime() {
        val c = PassageCounter(); c.sample(0, 0, true); c.sample(10_000, 10_000, true)
        c.rebase(140_000, 10_000, true)
        assertFalse(c.sample(170_000, 40_000, true))
        assertEquals(40_000L, c.playedMs)
    }
    @Test fun replayedPortionsWithinSamePassageCountButDoNotStartAnotherEvent() {
        val c = PassageCounter(); c.sample(0, 0, true); c.sample(45_000, 45_000, true)
        c.rebase(10_000, 45_000, true)
        assertTrue(c.sample(25_000, 60_000, true)); assertEquals(60_000L, c.playedMs)
    }
    @Test fun repeatStartsNewPassage() {
        val c = PassageCounter(); c.sample(0, 0, true); assertTrue(c.sample(60_000, 60_000, true))
        c.newPassage(0, 61_000, true)
        assertFalse(c.validated); assertEquals(0L, c.playedMs)
        assertTrue(c.sample(60_000, 121_000, true))
    }
    @Test fun newProcessDoesNotRestorePartialPassage() {
        val old = PassageCounter(); old.sample(0, 0, true); old.sample(45_000, 45_000, false)
        val fresh = PassageCounter(); fresh.sample(45_000, 80_000, true)
        assertFalse(fresh.sample(60_000, 95_000, true)); assertEquals(15_000L, fresh.playedMs)
    }
    @Test fun bufferingDoesNotCountWallTimeWithoutAudioMovement() {
        val c = PassageCounter(); c.sample(0, 0, true); assertFalse(c.sample(0, 120_000, true)); assertEquals(0L, c.playedMs)
    }
    @Test fun fasterPlaybackCountsRealTimeNotDistance() {
        val c = PassageCounter(); c.sample(0, 0, true, 2f)
        assertFalse(c.sample(60_000, 30_000, true, 2f)); assertEquals(30_000L, c.playedMs)
    }
}
