package com.novalisten.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Offsets are UTF-16 indices in LyricLine.text. No word windows are inferred from a line duration. */
data class LyricWord(val startMs: Long, val endMs: Long?, val startChar: Int, val endChar: Int) {
    fun progress(positionMs: Long): Float = when {
        positionMs < startMs -> 0f
        endMs == null || endMs <= startMs -> 1f // real start only: a color step, never an invented duration
        else -> ((positionMs - startMs).toDouble() / (endMs - startMs)).toFloat().coerceIn(0f, 1f)
    }
}
data class TimedToken(val text: String, val startMs: Long, val endMs: Long? = null)

object LyricTiming {
    private const val MAX_TIME = 86_400_000L
    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Map the provider's exact text segments to the displayed line, without fuzzy guessing. */
    fun line(startMs: Long, text: String, tokens: List<TimedToken>, endMs: Long? = null): LyricLine {
        val content = text.trim()
        val lineEnd = endMs?.takeIf { it in (startMs + 1)..MAX_TIME }
        val base = LyricLine(startMs, content, endMs = lineEnd)
        if (tokens.size > 1000) return base
        var cursor = 0
        var previousStart = startMs
        val words = mutableListOf<LyricWord>()
        for (token in tokens) {
            val segment = token.text.trim()
            if (segment.isEmpty()) continue
            val offset = content.indexOf(segment, cursor)
            if (offset < 0 || token.startMs !in startMs..MAX_TIME || token.startMs < previousStart ||
                (token.endMs != null && token.endMs !in (token.startMs + 1)..MAX_TIME) ||
                (lineEnd != null && (token.startMs >= lineEnd || (token.endMs != null && token.endMs > lineEnd)))) return base
            words += LyricWord(token.startMs, token.endMs, offset, offset + segment.length)
            cursor = offset + segment.length
            previousStart = token.startMs
        }
        return LyricLine(startMs, content, words, lineEnd)
    }
    fun encode(original: String, lines: List<LyricLine>): String {
        if (lines.none { it.words.isNotEmpty() }) return ""
        require(lines.size <= 3000 && lines.sumOf { it.words.size } <= 20_000)
        val payload = JSONObject().put("v", 1).put("text", digest(original)).put("lines", JSONArray().apply {
            lines.forEach { line -> put(JSONObject().put("at", line.timeMs).put("text", line.text).put("end", line.endMs ?: JSONObject.NULL).put("words", JSONArray().apply {
                line.words.forEach { word -> put(JSONArray().put(word.startMs).put(word.endMs ?: JSONObject.NULL).put(word.startChar).put(word.endChar)) }
            })) }
        }).toString()
        require(payload.length <= 2_000_000)
        return payload
    }
    /** A text edit, a corrupt payload or mismatched lines invalidates only the extra timings. */
    fun decode(original: String, payload: String): List<LyricLine> {
        val fallback = LrcParser.parse(original)
        if (payload.isBlank() || payload.length > 2_000_000) return fallback
        return runCatching {
            val root = JSONObject(payload)
            require(root.getInt("v") == 1 && root.getString("text") == digest(original))
            val rows = root.getJSONArray("lines")
            require(rows.length() == fallback.size && rows.length() <= 3000)
            var wordCount = 0
            List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                val base = fallback[index]
                require(row.getLong("at") == base.timeMs && row.getString("text") == base.text)
                val entries = row.getJSONArray("words")
                wordCount += entries.length(); require(wordCount <= 20_000)
                var previousChar = 0; var previousTime = base.timeMs
                val words = List(entries.length()) { w ->
                    val item = entries.getJSONArray(w)
                    val start = item.getLong(0); val end = if (item.isNull(1)) null else item.getLong(1)
                    val from = item.getInt(2); val to = item.getInt(3)
                    require(start in previousTime..MAX_TIME && from >= previousChar && to in (from + 1)..base.text.length)
                    require(end == null || end in (start + 1)..MAX_TIME)
                    previousChar = to; previousTime = start
                    LyricWord(start, end, from, to)
                }
                base.copy(words = words, endMs = if (row.isNull("end")) null else row.getLong("end").takeIf { it in (base.timeMs + 1)..MAX_TIME })
            }
        }.getOrDefault(fallback)
    }
}
