package com.novalisten.app.playback

import kotlin.math.min

/** Counts actual playback, never the seek distance, and at most once per passage.
 * A pause preserves the counter. A new instance/newPassage resets it.
 * All calls come from the player application looper.
 */
class PassageCounter(private val thresholdMs: Long = 60_000) {
    var playedMs = 0L
        private set
    var validated = false
        private set
    private var position: Long? = null
    private var clock: Long? = null
    private var wasPlaying = false
    private var previousSpeed = 1f

    fun sample(positionMs: Long, elapsedMs: Long, playing: Boolean, speed: Float = 1f): Boolean {
        val oldPosition = position
        val oldClock = clock
        if (wasPlaying && oldPosition != null && oldClock != null) {
            val wall = (elapsedMs - oldClock).coerceAtLeast(0)
            val movement = ((positionMs - oldPosition).coerceAtLeast(0) / previousSpeed).toLong()
            playedMs += min(wall, movement)
        }
        rebase(positionMs, elapsedMs, playing, speed)
        if (!validated && playedMs >= thresholdMs) {
            validated = true
            return true
        }
        return false
    }

    /** A seek is a rebase, not a new passage or a source of played time. */
    fun rebase(positionMs: Long, elapsedMs: Long, playing: Boolean, speed: Float = 1f) {
        position = positionMs
        clock = elapsedMs
        wasPlaying = playing
        previousSpeed = speed.coerceAtLeast(0.1f)
    }

    fun newPassage(positionMs: Long, elapsedMs: Long, playing: Boolean, speed: Float = 1f) {
        playedMs = 0
        validated = false
        rebase(positionMs, elapsedMs, playing, speed)
    }
}
