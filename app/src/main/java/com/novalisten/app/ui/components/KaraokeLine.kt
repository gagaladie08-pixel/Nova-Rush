@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package com.novalisten.app.ui.components

import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import com.novalisten.app.data.LyricLine
import kotlinx.coroutines.isActive

/** The fill uses native segment windows. Start-only segments switch color without a fabricated end. */
@Composable
internal fun KaraokeLine(line: LyricLine, active: Boolean, positionMs: Long, playing: Boolean,
                         color: Color, accent: Color, style: TextStyle, modifier: Modifier = Modifier) {
    var layout by remember(line.text, style) { mutableStateOf<TextLayoutResult?>(null) }
    // Read in the draw phase only: the entire player does not recompose on every animation frame.
    val projected = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, playing, active, line) {
        projected.longValue = positionMs
        if (playing && active && line.words.any { it.endMs != null }) {
            val received = SystemClock.uptimeMillis()
            while (isActive) withFrameNanos {
                // Media3 is sampled every 250 ms; never run a stale sample indefinitely.
                projected.longValue = positionMs + (SystemClock.uptimeMillis() - received).coerceIn(0, 500)
            }
        }
    }
    Text(line.text.ifBlank { "♪" }, modifier.drawWithContent {
        drawContent()
        val result = layout
        if (active && result != null && line.words.isNotEmpty()) {
            val completed = Path()
            line.words.forEach { word ->
                val progress = word.progress(projected.longValue)
                if (progress >= 1f) completed.addPath(result.getPathForRange(word.startChar, word.endChar))
                else if (progress > 0f) {
                    // Keep a wrapped segment's fill in reading order, including RTL lines.
                    val first = result.getLineForOffset(word.startChar)
                    val last = result.getLineForOffset(word.endChar - 1)
                    val pieces = (first..last).mapNotNull { row ->
                        val from = maxOf(word.startChar, result.getLineStart(row))
                        val to = minOf(word.endChar, result.getLineEnd(row, visibleEnd = true))
                        if (from >= to) null else result.getPathForRange(from, to) to (result.getBidiRunDirection(from) == ResolvedTextDirection.Rtl)
                    }
                    var remaining = pieces.sumOf { it.first.getBounds().width.toDouble() }.toFloat() * progress
                    pieces.forEach { (path, rtl) ->
                        val bounds = path.getBounds()
                        val width = remaining.coerceIn(0f, bounds.width)
                        if (width > 0f) clipPath(path) {
                            clipRect(left = if (rtl) bounds.right - width else bounds.left, top = bounds.top,
                                right = if (rtl) bounds.right else bounds.left + width, bottom = bounds.bottom) { drawText(result, color = accent) }
                        }
                        remaining -= bounds.width
                    }
                }
            }
            clipPath(completed) { drawText(result, color = accent) }
        }
    }, style = style, color = color, onTextLayout = { layout = it })
}
