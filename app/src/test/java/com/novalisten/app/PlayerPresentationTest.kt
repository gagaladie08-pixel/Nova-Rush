package com.novalisten.app

import org.junit.Assert.*
import org.junit.Test

class PlayerPresentationTest {
    @Test fun libraryIsTheFreshStartupTabAndHomeStillHasItsOwnIndex() {
        assertEquals(1, INITIAL_TAB)
        assertNotEquals(0, INITIAL_TAB)
    }
    @Test fun everyExplicitPlayRequestOpensIncludingTheSameTrackPlayedAgain() {
        val first = PlayerPresentation().accept(PlayerOpenRequest("one", "track"))
        assertTrue(first.open)
        val dismissed = first.dismiss()
        assertTrue(dismissed.accept(PlayerOpenRequest("two", "track")).open)
    }
    @Test fun dismissedRequestCannotReplayAfterRestoringBothSaveableFields() {
        val closed = PlayerPresentation().accept(PlayerOpenRequest("one", "track")).dismiss()
        val restored = PlayerPresentation(closed.open, closed.consumedToken)
        assertFalse(restored.accept(PlayerOpenRequest("one", "track")).open)
    }
    @Test fun naturalAdvancePreparedQueueAndMetadataChangesDoNotOpenADismissedPlayer() {
        val closed = PlayerPresentation().accept(PlayerOpenRequest("one", "track")).dismiss()
        assertFalse(closed.onPlayback("next").open)
        assertFalse(closed.onPlayback("same").open)
        assertFalse(PlayerPresentation().onPlayback("prepared-by-read-next").open)
        assertTrue(closed.openManually().open)
    }
    @Test fun naturalAdvanceDoesNotCloseAPlayerTheUserKeptOpenButStoppingDoes() {
        val open = PlayerPresentation().accept(PlayerOpenRequest("one", "track"))
        assertTrue(open.onPlayback("next").open)
        assertFalse(open.onPlayback(null).open)
    }
    @Test fun emptyOrRepeatedEventsCannotInventAPlaybackLaunch() {
        assertFalse(PlayerPresentation().accept(PlayerOpenRequest()).open)
        assertFalse(PlayerPresentation().accept(PlayerOpenRequest("one", "")).open)
        val closed = PlayerPresentation(false, "one")
        assertEquals(closed, closed.accept(PlayerOpenRequest("one", "track")))
    }
}
