package com.novalisten.app.data

data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList(), val endMs: Long? = null)

object LrcParser {
    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val wordTag = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")
    private val offsetTag = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private fun time(tag: MatchResult, offset: Long): Long? {
        val sec = tag.groupValues[2].toInt()
        if (sec >= 60) return null
        val fraction = tag.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
        return (tag.groupValues[1].toLong() * 60_000 + sec * 1000 + fraction + offset).coerceAtLeast(0)
    }
    fun withoutWordTags(text: String): String = text.replace(wordTag, "")
    fun parse(text: String): List<LyricLine> {
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(-86_400_000, 86_400_000) ?: 0
        return text.lineSequence().flatMap { line ->
            val tags = timeTag.findAll(line).toList()
            val content = line.replace(timeTag, "").trim()
            val inline = wordTag.findAll(content).toList()
            val plain = withoutWordTags(content).trim()
            val tokens = inline.mapIndexedNotNull { index, tag ->
                val start = time(tag, offset) ?: return@mapIndexedNotNull null
                val segment = content.substring(tag.range.last + 1, inline.getOrNull(index + 1)?.range?.first ?: content.length)
                TimedToken(segment, start) // Enhanced LRC usually supplies starts, not native word ends.
            }
            tags.asSequence().mapIndexedNotNull { index, tag ->
                val start = time(tag, offset) ?: return@mapIndexedNotNull null
                // Multiple repeated line stamps do not supply a second set of native word windows.
                if (index == 0 && tokens.isNotEmpty()) LyricTiming.line(start, plain, tokens) else LyricLine(start, plain)
            }
        }.sortedBy { it.timeMs }.toList()
    }
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int = lines.indexOfLast { it.timeMs <= positionMs }
}
