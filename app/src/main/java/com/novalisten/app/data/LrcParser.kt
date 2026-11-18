package com.novalisten.app.data

data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {
    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offsetTag = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    fun parse(text: String): List<LyricLine> {
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return text.lineSequence().flatMap { line ->
            val tags = timeTag.findAll(line).toList()
            val content = line.replace(timeTag, "").trim()
            tags.asSequence().mapNotNull { tag ->
                val sec = tag.groupValues[2].toInt()
                if (sec >= 60) return@mapNotNull null
                val fraction = tag.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                LyricLine((tag.groupValues[1].toLong() * 60_000 + sec * 1000 + fraction + offset).coerceAtLeast(0), content)
            }
        }.sortedBy { it.timeMs }.toList()
    }
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int = lines.indexOfLast { it.timeMs <= positionMs }
}
