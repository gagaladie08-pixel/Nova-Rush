package com.novalisten.app

import com.novalisten.app.ui.gestures.*
import org.junit.Assert.*
import org.junit.Test

class HorizontalSwipesTest {
    @Test fun leftOpensLyricsAndRightReturnsToArtwork() {
        assertTrue(lyricsPageAfterSwipe(false, -140f, 2f, 75f))
        assertFalse(lyricsPageAfterSwipe(true, 140f, -2f, 75f))
    }
    @Test fun swipingPastAnOuterPageDoesNotToggleIt() {
        assertFalse(lyricsPageAfterSwipe(false, 140f, 0f, 75f))
        assertTrue(lyricsPageAfterSwipe(true, -140f, 0f, 75f))
    }
    @Test fun verticalAndDiagonalDragsDoNotChangeLyricsPage() {
        for (page in listOf(false, true)) for ((dx, dy) in listOf(-120f to 180f, 120f to -180f, -120f to 120f, 0f to 200f)) {
            assertEquals(page, lyricsPageAfterSwipe(page, dx, dy, 75f))
        }
    }
    @Test fun smallOrInvalidDragsDoNotChangePage() {
        for (page in listOf(false, true)) for (dx in listOf(0f, 20f, -20f, 75f, -75f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(page, lyricsPageAfterSwipe(page, dx, 0f, 75f))
        }
        assertFalse(lyricsPageAfterSwipe(false, -140f, Float.NaN, 75f))
        assertFalse(lyricsPageAfterSwipe(false, -140f, 0f, 0f))
    }
    @Test fun libraryDirectionsAreInvertedTogether() {
        assertEquals(LibrarySwipeAction.PLAY_NEXT, librarySwipeAction(-140f, 78f))
        assertEquals(LibrarySwipeAction.INFORMATION, librarySwipeAction(140f, 78f))
    }
    @Test fun libraryThresholdMustBeCrossedAndInvalidInputIsIgnored() {
        for (dx in listOf(0f, 30f, -30f, 78f, -78f, Float.NaN, Float.NEGATIVE_INFINITY)) assertNull(librarySwipeAction(dx, 78f))
        assertNull(librarySwipeAction(140f, 0f))
        assertNull(librarySwipeAction(140f, Float.NaN))
    }
}
