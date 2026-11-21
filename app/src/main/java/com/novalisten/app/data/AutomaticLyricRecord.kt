package com.novalisten.app.data

import java.security.MessageDigest

/** Kept separately from the user's chosen/edited VO/VF so an automatic improvement cannot overwrite it. */
data class AutomaticLyricRecord(val identity: String, val lyrics: SavedLyrics) {
    fun forTrack(track: Track?): SavedLyrics? = track?.let { forIdentity(it.lyricIdentity()) }
    fun forIdentity(key: String): SavedLyrics? = lyrics.takeIf { identity == key && it.original.isNotBlank() }
}

/** Local identity only; a MediaStore ID reused for different metadata must not inherit old lyrics. */
fun lyricIdentity(title: String, artist: String, album: String, durationMs: Long): String {
    val parts = listOf(title, artist, album).map { it.normalized().replace(Regex("\\s+"), " ") } + durationMs.toString()
    val canonical = parts.joinToString("") { "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

fun Track.lyricIdentity(): String = lyricIdentity(title, artist, album, durationMs)

fun SavedLyrics.timingQuality(): Int = when {
    original.isBlank() -> 0
    else -> LyricTiming.decode(original, originalTiming).let { lines ->
        when { lines.any { it.words.isNotEmpty() } -> 3; lines.isNotEmpty() -> 2; else -> 1 }
    }
}
