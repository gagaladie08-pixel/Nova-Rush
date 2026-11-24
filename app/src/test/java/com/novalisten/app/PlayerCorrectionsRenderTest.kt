package com.novalisten.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.novalisten.app.data.*
import com.novalisten.app.enrichment.AutoLyricState
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.screens.*
import com.novalisten.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Actual Compose measurement/drawing under Robolectric, no installable APK or signing key. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, qualifiers = "w820dp-h1280dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerCorrectionsRenderTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track("fixture", "Titre unique", "Artiste unique", "Album test", 1, "Artiste unique", 180000, 0, 2025, 1, "test.mp3", 100, "audio/mpeg")
    private data class Viewport(val width: Int, val height: Int, val font: Float = 1f, val bottom: Int = 48, val left: Int = 0)
    private val sizes = listOf(Viewport(320, 568), Viewport(280, 400, 2f), Viewport(240, 480, 1.5f), Viewport(640, 320, 1.8f, 24, 32))

    @Test fun actionsSitAboveTheTimelineAndTransportKeepsFullTouchTargetsAndInsets() {
        val size = mutableStateOf(sizes.first())
        var favorite = 0
        compose.setContent {
            val viewport = size.value
            CompositionLocalProvider(LocalDensity provides Density(.5f, viewport.font)) {
                NovaTheme(themeCatalog.first(), effects = false) {
                    Box(Modifier.size(viewport.width.dp, viewport.height.dp).testTag("fixture")) {
                        PlayerFrame(insets = WindowInsets(left = viewport.left.dp, top = 24.dp, bottom = viewport.bottom.dp),
                            header = { compact -> PlayerHeader(track, false, compact, {}, {}) },
                            footer = { compact -> Column {
                                PlayerActions(false, { favorite++ }, {}, {}, {})
                                PlayerTransport(PlaybackUi(currentId = track.id, duration = track.durationMs), compact, {}, {}, {}, {}, {}, {})
                            } }) { compact -> PlayerCoverPage(track, true, compact, null, false, {}) }
                    }
                }
            }
        }
        for (viewport in sizes) {
            compose.runOnIdle { size.value = viewport }
            val frame = compose.onNodeWithTag("fixture").getUnclippedBoundsInRoot()
            val safe = compose.onNodeWithTag("player_safe_area").getUnclippedBoundsInRoot()
            val actions = compose.onNodeWithTag("player_actions").getUnclippedBoundsInRoot()
            val transport = compose.onNodeWithTag("player_transport").getUnclippedBoundsInRoot()
            assertTrue(actions.bottom <= transport.top + 1.dp)
            assertTrue(transport.top - actions.bottom <= 8.dp)
            compose.onNodeWithContentDescription("Favori").assertIsDisplayed().performClick()
            for (tag in listOf("player_toggle", "player_previous", "player_next", "player_repeat", "player_queue")) {
                val button = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue("$tag horizontal / $viewport", button.left >= safe.left - 2.dp && button.right <= safe.right + 2.dp)
                assertTrue("$tag vertical / $viewport", button.top >= safe.top - 2.dp && button.bottom <= safe.bottom + 2.dp)
                assertTrue("$tag navigation / $viewport", frame.bottom - button.bottom >= (viewport.bottom + 20 - 2).dp)
                assertTrue("$tag target / $viewport", button.right - button.left >= 46.dp && button.bottom - button.top >= 46.dp)
            }
        }
        compose.runOnIdle { assertEquals(sizes.size, favorite) }
    }

    @Test fun immersiveModeKeepsOnlyTheHeaderIdentity() {
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            Column(Modifier.width(320.dp).height(600.dp)) {
                PlayerHeader(track, false, false, {}, {})
                PlayerCoverPage(track, false, false, "Une ligne centrée", false, {})
            }
        } }
        compose.onAllNodesWithText(track.title).assertCountEquals(1)
        compose.onAllNodesWithText(track.artist).assertCountEquals(1)
        compose.onNodeWithText("Touchez pour afficher les commandes").assertDoesNotExist()
        compose.onNodeWithText("Une ligne centrée").assertIsDisplayed()
    }

    @Test fun timedLyricsAreCenteredHorizontallyAndVerticallyAndTappingStillSeeks() {
        val rows = List(8) { LyricLine(it * 5000L, if (it == 3) "Une ligne assez longue pour s’enrouler sur plusieurs lignes au centre" else "Ligne $it") }
        var sought = -1L
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            LyricsBody(track.id, "test", rows, 3, rows[3].timeMs, false, AutoLyricState(), Modifier.width(280.dp).height(420.dp)) { sought = it }
        } }
        val body = compose.onNodeWithTag("lyrics_viewport").fetchSemanticsNode().boundsInRoot
        val line = compose.onNodeWithTag("lyric_line_3").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(body.center.y - line.center.y) <= 3f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("lyric_text_3", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(TextAlign.Center, layouts.single().layoutInput.style.textAlign)
        compose.onNodeWithTag("lyric_line_3").performClick()
        compose.runOnIdle { assertEquals(15000L, sought) }
    }

    @Test fun plainLyricsUseTheSameHorizontalCenterAlignment() {
        val text = "Des paroles non synchronisées\nUne deuxième ligne"
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            LyricsBody(track.id, text, emptyList(), -1, 0, false, AutoLyricState(), Modifier.width(280.dp).height(420.dp), {})
        } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(TextAlign.Center, layouts.single().layoutInput.style.textAlign)
    }

    @Test fun centeredNativeKaraokeStillFillsPixelsAndResetsOnBackwardSeek() {
        val position = mutableLongStateOf(1000)
        val line = LyricTiming.line(1000, "Clarté nouvelle", listOf(TimedToken("Clarté", 1000, 2000), TimedToken("nouvelle", 2500, 3500)))
        val theme = themeCatalog.first()
        var owner: View? = null
        compose.setContent { NovaTheme(theme, effects = false) {
            val view = LocalView.current
            SideEffect { owner = view }
            KaraokeLine(line, true, position.longValue, false, theme.text, theme.accent,
                androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                Modifier.width(280.dp).background(theme.background).testTag("native_fill"))
        } }
        fun coloredPixels(): Int {
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("native_fill").fetchSemanticsNode().boundsInRoot
            val bitmap = Bitmap.createBitmap(kotlin.math.ceil((bounds.right - bounds.left).toDouble()).toInt().coerceAtLeast(1),
                kotlin.math.ceil((bounds.bottom - bounds.top).toDouble()).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            // Robolectric has no real Window surface for PixelCopy. Draw the measured Compose
            // owner into a native software Canvas instead; the instrumented test keeps PixelCopy.
            compose.runOnIdle {
                val canvas = Canvas(bitmap)
                canvas.translate(-bounds.left, -bounds.top)
                owner!!.draw(canvas)
            }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val accent = theme.accent.toArgb()
            return pixels.count { pixel -> abs((pixel ushr 16 and 255) - (accent ushr 16 and 255)) < 35 &&
                abs((pixel ushr 8 and 255) - (accent ushr 8 and 255)) < 35 && abs((pixel and 255) - (accent and 255)) < 35 }
        }
        val start = coloredPixels()
        compose.runOnIdle { position.longValue = 1500 }
        val middle = coloredPixels()
        compose.runOnIdle { position.longValue = 2000 }
        val end = coloredPixels()
        assertTrue(middle > start + 10); assertTrue(end > middle + 10)
        compose.runOnIdle { position.longValue = 1000 }
        assertEquals(start, coloredPixels())
    }

    @Test fun savedTabAndDestinationKeysKeepIndependentSearchStates() {
        val destination = mutableStateOf("artists/index")
        var query: MutableState<String>? = null
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            holder.SaveableStateProvider(destination.value) {
                val state = rememberSaveable { mutableStateOf("") }
                SideEffect { query = state }
            }
        }
        compose.runOnIdle { query!!.value = "LISA" }
        compose.runOnIdle { destination.value = "artists/detail/lisa" }
        compose.runOnIdle { query!!.value = "detail"; destination.value = "albums/index" }
        compose.runOnIdle { assertEquals("", query!!.value); query!!.value = "Destinée" }
        compose.runOnIdle { destination.value = "artists/index" }
        compose.runOnIdle { assertEquals("LISA", query!!.value) }
        compose.runOnIdle { destination.value = "albums/index" }
        compose.runOnIdle { assertEquals("Destinée", query!!.value) }
    }
}
