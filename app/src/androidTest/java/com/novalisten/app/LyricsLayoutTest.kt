package com.novalisten.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novalisten.app.data.*
import com.novalisten.app.enrichment.AutoLyricState
import com.novalisten.app.ui.components.KaraokeLine
import com.novalisten.app.ui.components.LyricsBody
import com.novalisten.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LyricsLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val rows = List(12) { index -> LyricLine(index * 5000L, if (index == 5) "Une ligne de test volontairement assez longue pour occuper plusieurs lignes à l’écran" else "Ligne synthétique $index") }
    @Test fun firstWrappedLastAndSeekedRowsAreCenteredEvenAfterAViewportResize() {
        val active = mutableIntStateOf(0)
        val height = mutableStateOf(420.dp)
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            LyricsBody("fixture", "synthetic", rows, active.intValue, rows[active.intValue].timeMs, false, AutoLyricState(),
                Modifier.width(280.dp).height(height.value), onSeek = {})
        } }
        for (index in listOf(0, 5, 11, 2)) {
            compose.runOnIdle { active.intValue = index }
            centered(index)
        }
        compose.runOnIdle { height.value = 290.dp }
        centered(2)
    }
    @Test fun manualScrollingStopsFollowingUntilRecenterAndTappingALineSeeks() {
        val active = mutableIntStateOf(3)
        var sought = -1L
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            LyricsBody("fixture", "synthetic", rows, active.intValue, rows[active.intValue].timeMs, false, AutoLyricState(),
                Modifier.width(280.dp).height(420.dp), onSeek = { sought = it; active.intValue = LrcParser.activeIndex(rows, it) })
        } }
        centered(3)
        compose.onNodeWithTag("lyrics_list").performTouchInput { swipeUp() }
        compose.onNodeWithTag("lyrics_recenter").assertIsDisplayed()
        compose.runOnIdle { active.intValue = 6 }
        compose.onNodeWithTag("lyrics_recenter").assertIsDisplayed().performClick()
        centered(6)
        compose.onNodeWithTag("lyric_line_7").performClick()
        compose.runOnIdle { assertEquals(35000L, sought) }
        centered(7)
    }
    @Test fun wrappedAndPlainLyricsUseHorizontalCenterAlignment() {
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            LyricsBody("fixture", "synthetic", rows, 5, rows[5].timeMs, false, AutoLyricState(), Modifier.width(280.dp).height(420.dp), {})
        } }
        centered(5)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("lyric_text_5").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(androidx.compose.ui.text.style.TextAlign.Center, layouts.single().layoutInput.style.textAlign)
    }
    @Test fun nativeWordFillActuallyChangesPixelsAtItsRealStartMiddleAndEnd() {
        val position = mutableLongStateOf(1000)
        val line = LyricTiming.line(1000, "Clarté nouvelle", listOf(TimedToken("Clarté", 1000, 2000), TimedToken("nouvelle", 2500, 3500)))
        val theme = themeCatalog.first()
        compose.setContent { NovaTheme(theme, effects = false) {
            KaraokeLine(line, true, position.longValue, false, theme.text, theme.accent,
                androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                Modifier.width(280.dp).background(theme.background).testTag("native_fill"))
        } }
        fun coloredPixels(): Int {
            val bitmap = compose.onNodeWithTag("native_fill").captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val target = theme.accent.toArgb()
            return pixels.count { pixel -> listOf(0, 8, 16).all { shift -> abs((pixel ushr shift and 255) - (target ushr shift and 255)) < 35 } }
        }
        val start = coloredPixels()
        compose.runOnIdle { position.longValue = 1500 }; compose.waitForIdle()
        val middle = coloredPixels()
        compose.runOnIdle { position.longValue = 2000 }; compose.waitForIdle()
        val end = coloredPixels()
        assertTrue("The native word must begin unfilled", middle > start + 10)
        assertTrue("The fill must advance within the real native window", end > middle + 10)
        compose.runOnIdle { position.longValue = 1000 }; compose.waitForIdle()
        assertEquals("Seeking backwards resets the fill", start, coloredPixels())
    }
    private fun centered(index: Int) {
        compose.waitForIdle()
        val viewport = compose.onNodeWithTag("lyrics_viewport").fetchSemanticsNode().boundsInRoot
        val line = compose.onNodeWithTag("lyric_line_$index").fetchSemanticsNode().boundsInRoot
        assertTrue("Line $index center ${line.center.y} must match viewport ${viewport.center.y}", abs(viewport.center.y - line.center.y) <= 3f)
    }
}
