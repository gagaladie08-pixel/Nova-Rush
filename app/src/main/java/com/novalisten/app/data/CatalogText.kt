package com.novalisten.app.data

import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Display/index aliases only. Never writes metadata back to the music files. */
object CatalogText {
    private val legacy = listOf("windows-1252", "ISO-8859-1", "windows-1250").map(Charset::forName)
    private fun damage(value: String): Int = value.count { it in "ÃÂĂÄÅâð\uFFFD" || (it.isISOControl() && !it.isWhitespace()) }
    fun display(raw: String): String {
        var value = raw.trim()
        repeat(2) {
            if (damage(value) == 0) return value
            val repaired = legacy.mapNotNull { charset -> runCatching {
                val encoded = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(encoded).toString()
            }.getOrNull() }.filter { it.isNotBlank() && damage(it) < damage(value) && it.none { c -> c.isISOControl() && !c.isWhitespace() } }
                .minByOrNull(::damage) ?: return value
            value = repaired
        }
        return value
    }
    fun key(raw: String): String = display(raw).normalized().replace(Regex("\\s+"), " ")
}

fun itemCount(count: Int, singular: String, plural: String = singular + "s"): String = "$count ${if (count == 1) singular else plural}"
