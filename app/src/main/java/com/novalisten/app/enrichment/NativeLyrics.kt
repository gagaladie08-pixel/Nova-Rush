package com.novalisten.app.enrichment

import com.novalisten.app.data.*
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class NativeLyricResult(val text: String, val lines: List<LyricLine>)

/** Retains actual absolute TTML span windows instead of flattening everything to line-only LRC. */
object NativeLyrics {
    private fun compact(value: String): String = value.replace(Regex("\\s+"), " ").trim()
    fun ttml(xml: String): NativeLyricResult {
        safeText(xml)
        require(!xml.contains(Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE))) { "Déclarations XML externes interdites." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true; isExpandEntityReferences = false
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
        val document = builder.parse(InputSource(StringReader(xml)))
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        require(paragraphs.length <= 3000)
        val content = mutableListOf<String>()
        val lines = mutableListOf<LyricLine>()
        for (index in 0 until paragraphs.length) {
            val p = paragraphs.item(index) as Element
            val text = compact(p.textContent)
            if (text.isBlank()) continue
            content += text
            val start = LyricFormats.clock(p.getAttribute("begin")) ?: continue
            // The common music TTML absolute-clock subset only. Do not guess nested syncbases.
            var parent = p.parentNode
            var unsupportedContainer = false
            while (parent is Element) {
                if (parent.getAttribute("timeContainer") == "seq" ||
                    (parent.hasAttribute("begin") && LyricFormats.clock(parent.getAttribute("begin")) != 0L)) unsupportedContainer = true
                parent = parent.parentNode
            }
            if (unsupportedContainer) continue
            val end = LyricFormats.clock(p.getAttribute("end")) ?: LyricFormats.clock(p.getAttribute("dur"))?.let { start + it }
            val spans = p.getElementsByTagNameNS("*", "span")
            val tokens = mutableListOf<TimedToken>()
            for (s in 0 until spans.length) {
                val span = spans.item(s) as Element
                if (!span.hasAttribute("begin")) continue
                val descendants = span.getElementsByTagNameNS("*", "span")
                if ((0 until descendants.length).any { (descendants.item(it) as Element).hasAttribute("begin") }) continue
                val at = LyricFormats.clock(span.getAttribute("begin")) ?: continue
                tokens += TimedToken(compact(span.textContent), at, LyricFormats.clock(span.getAttribute("end")) ?: LyricFormats.clock(span.getAttribute("dur"))?.let { at + it })
            }
            // Relative/sequential containers are not silently interpreted as absolute karaoke windows.
            var ancestor: org.w3c.dom.Node? = p
            var sequential = false
            while (ancestor is Element) {
                if (ancestor.getAttribute("timeContainer") == "seq") sequential = true
                ancestor = ancestor.parentNode
            }
            lines += LyricTiming.line(start, text, if (sequential) emptyList() else tokens, end)
        }
        if (lines.size != content.size) return NativeLyricResult(content.joinToString("\n"), emptyList())
        val ordered = lines.sortedBy { it.timeMs }
        return NativeLyricResult(asLrc(ordered.map { it.timeMs to it.text }), ordered)
    }
}
