package com.novalisten.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** One application of insets, plus a real gap above the system navigation/gesture area. */
internal val PlayerBottomGap = 20.dp

/**
 * Measure transport first, not after artwork, text and optional actions. Only the middle region
 * yields space on a short window. Clip its drawing so a square artwork/large lyric cannot paint
 * over the controls. No onSizeChanged feedback loop or fixed screen-height assumption.
 */
@Composable
internal fun PlayerFrame(modifier: Modifier = Modifier,
                         insets: WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.safeGestures),
                         header: @Composable (compact: Boolean) -> Unit,
                         footer: @Composable (compact: Boolean) -> Unit,
                         content: @Composable (compact: Boolean) -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(insets)
        .padding(start = 16.dp, end = 16.dp, bottom = PlayerBottomGap).testTag("player_safe_area")) {
        val compact = maxHeight < 420.dp
        Layout(modifier = Modifier.fillMaxSize(), content = {
            Box(Modifier.fillMaxWidth().clipToBounds()) { footer(compact) }
            Box(Modifier.fillMaxWidth().clipToBounds()) { header(compact) }
            Box(Modifier.fillMaxSize().clipToBounds().testTag("player_content")) { content(compact) }
        }) { children, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val loose = Constraints(maxWidth = width, maxHeight = height)
            val transport = children[0].measure(loose)
            val heading = children[1].measure(loose.copy(maxHeight = (height - transport.height).coerceAtLeast(0)))
            val middleHeight = (height - transport.height - heading.height).coerceAtLeast(0)
            val middle = children[2].measure(Constraints.fixed(width, middleHeight))
            layout(width, height) {
                heading.placeRelative(0, 0)
                middle.placeRelative(0, heading.height)
                transport.placeRelative(0, height - transport.height)
            }
        }
    }
}
