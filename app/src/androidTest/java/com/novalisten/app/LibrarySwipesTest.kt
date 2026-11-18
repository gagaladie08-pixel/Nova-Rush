package com.novalisten.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySwipesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trackRowShowsAndFiresLeftNextAndRightInformation() {
        var next = 0; var info = 0; var clicks = 0
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            Box(Modifier.width(300.dp)) {
                TrackRow(playerTestTrack(), false, false, { clicks++ }, { clicks++ }, { clicks++ }, { next++ }, { info++ })
            }
        } }
        dragAndCheckHint("track_fixture", left = true, "Lire ensuite")
        compose.runOnIdle { assertEquals(1, next); assertEquals(0, info); assertEquals(0, clicks) }
        dragAndCheckHint("track_fixture", left = false, "Informations")
        compose.runOnIdle { assertEquals(1, next); assertEquals(1, info); assertEquals(0, clicks) }
    }

    @Test fun entityCardsUseTheSamePhysicalDirectionsEvenInRtl() {
        var next = 0; var info = 0; var clicks = 0
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.width(280.dp)) {
                    EntityCard("Carte synthétique", "Test", null, Modifier.testTag("entity"),
                        onClick = { clicks++ }, onNext = { next++ }, onInfo = { info++ })
                }
            }
        } }
        dragAndCheckHint("entity", left = true, "Lire ensuite")
        dragAndCheckHint("entity", left = false, "Informations")
        compose.runOnIdle { assertEquals(1, next); assertEquals(1, info); assertEquals(0, clicks) }
    }

    @Test fun selectionAndPendingDurationKeepRowSwipesDisabled() {
        val selecting = mutableStateOf(true)
        var actions = 0
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            Box(Modifier.width(300.dp)) {
                TrackRow(playerTestTrack().let { if (selecting.value) it else it.copy(durationMs = 0) }, false, false,
                    {}, {}, {}, { actions++ }, { actions++ }, selecting = selecting.value)
            }
        } }
        for (selected in listOf(true, false)) {
            compose.runOnIdle { selecting.value = selected }
            for (from in listOf(.9f, .1f)) {
                compose.onNodeWithTag("track_fixture").performTouchInput {
                    down(Offset(width * from, height / 2f))
                    moveTo(Offset(width * (1f - from), height / 2f), delayMillis = 300)
                }
                compose.onNodeWithTag("library_swipe_hint").assertDoesNotExist()
                // With no drag recognizer, a release inside a clickable row may be a tap.
                // Cancel to test swipe admission separately from the pending row's Info click.
                compose.onNodeWithTag("track_fixture").performTouchInput { cancel() }
            }
        }
        compose.runOnIdle { assertEquals(0, actions) }
        compose.onNodeWithTag("track_fixture").performClick()
        compose.runOnIdle { assertEquals("A deliberate click on a pending track still opens information", 1, actions) }
    }

    @Test fun smallVerticalAndCancelledGesturesDoNothingAndCallbacksStayFresh() {
        val generation = mutableStateOf(0)
        var next = 0; var info = 0
        compose.setContent { NovaTheme(themeCatalog.first(), effects = false) {
            val value = generation.value
            Box(Modifier.width(280.dp)) {
                SwipeActions("fixture", true, { next += value }, { info += value }) {
                    Box(Modifier.fillMaxWidth().height(160.dp).background(LocalUniverse.current.surface).testTag("swipe_target"))
                }
            }
        } }
        compose.onNodeWithTag("swipe_target").performTouchInput { swipeUp() }
        compose.onNodeWithTag("swipe_target").performTouchInput {
            swipe(start = center, end = center + Offset(20f, 0f))
        }
        compose.onNodeWithTag("swipe_target").performTouchInput {
            down(Offset(width * .9f, height / 2f)); moveTo(Offset(width * .1f, height / 2f)); cancel()
        }
        compose.onNodeWithTag("library_swipe_hint").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, next); assertEquals(0, info); generation.value = 2 }
        compose.onNodeWithTag("swipe_target").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("swipe_target").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(2, next); assertEquals(2, info) }
    }

    private fun dragAndCheckHint(tag: String, left: Boolean, label: String) {
        compose.onNodeWithTag(tag).performTouchInput {
            val from = if (left) .9f else .1f
            down(Offset(width * from, height / 2f))
            moveTo(Offset(width * (1f - from), height / 2f), delayMillis = 300)
        }
        // Check the preview while the finger is still down, not merely the eventual callback.
        compose.onNodeWithTag("library_swipe_hint").assertIsDisplayed().assertTextEquals(label)
        compose.onNodeWithTag(tag).performTouchInput { up() }
        compose.onNodeWithTag("library_swipe_hint").assertDoesNotExist()
    }
}
