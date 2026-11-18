package com.novalisten.app.ui.gestures

import kotlin.math.abs

/** Physical directions (not layout-direction dependent): left opens lyrics, right returns to artwork. */
fun lyricsPageAfterSwipe(showingLyrics: Boolean, dx: Float, dy: Float, threshold: Float): Boolean {
    if (!dx.isFinite() || !dy.isFinite() || threshold <= 0 || !threshold.isFinite() ||
        abs(dx) <= threshold || abs(dx) <= abs(dy)) return showingLyrics
    return dx < 0
}

enum class LibrarySwipeAction { PLAY_NEXT, INFORMATION }

/** Inversion requested for library rows/cards: left queues next; right opens information. */
fun librarySwipeAction(dx: Float, threshold: Float): LibrarySwipeAction? = when {
    !dx.isFinite() || !threshold.isFinite() || threshold <= 0 -> null
    dx < -threshold -> LibrarySwipeAction.PLAY_NEXT
    dx > threshold -> LibrarySwipeAction.INFORMATION
    else -> null
}
