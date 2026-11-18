package com.novalisten.app

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novalisten.app.data.LyricLine
import com.novalisten.app.enrichment.AutoLyricState
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.screens.PlayerCoverPage
import com.novalisten.app.ui.screens.PlayerHeader
import com.novalisten.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PlayerLayoutTest {
    @get:Rule val compose = createComposeRule()
    private data class Viewport(val width: Int = 320, val height: Int = 568, val fontScale: Float = 1f,
                                val top: Int = 24, val bottom: Int = 48, val left: Int = 0, val right: Int = 0,
                                val lyrics: Boolean = false)
    private val sizes = listOf(
        Viewport(), // small phone with three-button navigation
        Viewport(height = 480),
        Viewport(width = 640, height = 320, bottom = 24, left = 32), // landscape / cutout / gestures
        Viewport(width = 280, height = 400, fontScale = 2f),
        Viewport(width = 240, height = 480, fontScale = 1.5f), // wraps secondary playback buttons
        Viewport(width = 640, height = 360, bottom = 0, right = 48), // navigation at the side
    )
    private val rows = List(10) { LyricLine(it * 5000L, "Ligne synthétique $it") }

    @Test fun everyPlaybackButtonStaysFullyInsideTheSafeAreaOnBothPages() {
        val viewport = mutableStateOf(sizes.first())
        compose.setContent { Fixture(viewport.value) }
        for (size in sizes) for (lyrics in listOf(false, true)) {
            val current = size.copy(lyrics = lyrics)
            compose.runOnIdle { viewport.value = current }
            assertControlsInside(current)
        }
    }

    @Test fun smallLandscapeControlsAndSeekRemainOperational() {
        val viewport = Viewport(width = 640, height = 320, fontScale = 1.8f, bottom = 24)
        val calls = IntArray(5)
        var sought = -1L
        compose.setContent { Fixture(viewport, onSeek = { sought = it }, onToggle = { calls[0]++ },
            onPrevious = { calls[1]++ }, onNext = { calls[2]++ }, onRepeat = { calls[3]++ }, onQueue = { calls[4]++ }) }
        assertControlsInside(viewport)
        for (tag in listOf("player_toggle", "player_previous", "player_next", "player_repeat", "player_queue")) {
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
        }
        compose.onNodeWithTag("player_seek").performTouchInput { click(center.copy(x = width * .75f)) }
        compose.runOnIdle {
            assertTrue(calls.all { it == 1 })
            assertTrue("Seek must still reach the playback callback", sought in 1..180_000)
        }
    }

    @Test fun artworkYieldsHeightAndOptionalActionsScrollWithoutMovingPlaybackControls() {
        val viewport = Viewport(width = 640, height = 360, fontScale = 1.8f, bottom = 24)
        var edited = false
        compose.setContent { Fixture(viewport, onEdit = { edited = true }) }
        assertControlsInside(viewport)
        val middle = compose.onNodeWithTag("player_content").getUnclippedBoundsInRoot()
        val artwork = compose.onNodeWithTag("player_artwork", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val artWidth = artwork.right - artwork.left
        val artHeight = artwork.bottom - artwork.top
        assertTrue("Artwork is square", abs(artWidth.value - artHeight.value) <= 2f)
        assertTrue("Artwork must yield to available height", artHeight <= (middle.bottom - middle.top) * .65f + 2.dp)
        val before = compose.onNodeWithTag("player_transport").getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription("Modifier les paroles").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(edited) }
        assertEquals(before, compose.onNodeWithTag("player_transport").getUnclippedBoundsInRoot())
        assertControlsInside(viewport)
    }

    @Test fun immersiveCoverStillLeavesVerticalPreviousNextGesturesToItsParent() {
        var dy = 0f; var completed = 0f
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            Box(Modifier.width(280.dp).height(350.dp).pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { completed = dy; dy = 0f }, onDragCancel = { dy = 0f }) { change, amount ->
                    change.consume(); dy += amount
                }
            }) {
                PlayerCoverPage(playerTestTrack(), false, false, null, false, false, {}, {}, {}, {}, {})
            }
        } }
        compose.onNodeWithTag("player_cover_page").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue("Up remains available for the previous-track gesture", completed < -75f) }
        compose.onNodeWithTag("player_cover_page").performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue("Down remains available for the next-track gesture", completed > 75f) }
    }

    @Test fun activeLyricsStayCenteredWhenWindowAndNavigationInsetsChange() {
        val viewport = mutableStateOf(Viewport(height = 640, lyrics = true))
        compose.setContent { Fixture(viewport.value) }
        for (size in listOf(Viewport(height = 640), Viewport(height = 480), Viewport(width = 640, height = 320, bottom = 24))) {
            val current = size.copy(lyrics = true)
            compose.runOnIdle { viewport.value = current }
            compose.waitForIdle()
            val body = compose.onNodeWithTag("lyrics_viewport").fetchSemanticsNode().boundsInRoot
            val line = compose.onNodeWithTag("lyric_line_2").fetchSemanticsNode().boundsInRoot
            assertTrue("Active lyric must remain centered after resize", abs(body.center.y - line.center.y) <= 3f)
            assertControlsInside(current)
        }
    }

    @androidx.compose.runtime.Composable
    private fun Fixture(viewport: Viewport, onSeek: (Long) -> Unit = {}, onToggle: () -> Unit = {},
                        onPrevious: () -> Unit = {}, onNext: () -> Unit = {}, onRepeat: () -> Unit = {},
                        onQueue: () -> Unit = {}, onEdit: () -> Unit = {}) {
        // Fit portrait and landscape DP constraints inside the same small emulator; this is a
        // layout/font/inset simulation, not a claim that a physical device was rotated or tested.
        CompositionLocalProvider(LocalDensity provides Density(.5f, viewport.fontScale)) {
            NovaTheme(themeCatalog.first(), effects = false) {
                Box(Modifier.size(viewport.width.dp, viewport.height.dp).testTag("player_fixture")) {
                    PlayerFrame(insets = WindowInsets(left = viewport.left.dp, top = viewport.top.dp,
                        right = viewport.right.dp, bottom = viewport.bottom.dp),
                        header = { compact -> PlayerHeader(playerTestTrack(), viewport.lyrics, compact, {}, {}) },
                        footer = { compact -> PlayerTransport(PlaybackUi(currentId = "fixture", duration = 180_000), compact,
                            onSeek, onToggle, onPrevious, onNext, onRepeat, onQueue) }) { compact ->
                        if (viewport.lyrics) LyricsBody("fixture", "synthetic", rows, 2, 10_000, false, AutoLyricState(), Modifier.fillMaxSize(), {})
                        else PlayerCoverPage(playerTestTrack(), true, compact, "Une ligne synthétique assez longue pour s’enrouler", false, false,
                            onReveal = {}, onFavorite = {}, onAdd = {}, onLyrics = {}, onEditLyrics = onEdit)
                    }
                }
            }
        }
    }

    private fun assertControlsInside(viewport: Viewport) {
        compose.waitForIdle()
        val frame = compose.onNodeWithTag("player_fixture").getUnclippedBoundsInRoot()
        val safe = compose.onNodeWithTag("player_safe_area").getUnclippedBoundsInRoot()
        val transport = compose.onNodeWithTag("player_transport").getUnclippedBoundsInRoot()
        val middle = compose.onNodeWithTag("player_content").getUnclippedBoundsInRoot()
        assertTrue("Middle cannot overlap playback controls: $viewport", middle.bottom.value <= transport.top.value + 2f)
        for (tag in listOf("player_toggle", "player_previous", "player_next", "player_repeat", "player_queue")) {
            val node = compose.onNodeWithTag(tag).assertIsDisplayed()
            // Use UNCLIPPED bounds: a partly off-screen button must not pass this regression test.
            val button = node.getUnclippedBoundsInRoot()
            assertTrue("Full $tag must stay inside horizontal insets: $viewport", button.left.value >= safe.left.value - 2f && button.right.value <= safe.right.value + 2f)
            assertTrue("Full $tag must stay inside vertical insets: $viewport", button.top.value >= safe.top.value - 2f && button.bottom.value <= safe.bottom.value + 2f)
            assertTrue("$tag must stay clear of Android navigation: $viewport", frame.bottom.value - button.bottom.value >= viewport.bottom + 20f - 2f)
            assertTrue("$tag keeps its 48dp touch target: $viewport", (button.right - button.left).value >= 48f - 2f && (button.bottom - button.top).value >= 48f - 2f)
        }
        assertTrue("Left safe inset plus content margin", transport.left.value - frame.left.value >= viewport.left + 16f - 2f)
        assertTrue("Right safe inset plus content margin", frame.right.value - transport.right.value >= viewport.right + 16f - 2f)
    }
}
