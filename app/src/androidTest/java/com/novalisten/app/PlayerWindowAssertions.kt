package com.novalisten.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*

/** Compare UNCLIPPED Compose controls to Android's physical display/insets, not only to their own parent. */
internal fun assertPlayerInsidePhysicalWindow(compose: ComposeContentTestRule, activity: MainActivity) {
    compose.waitForIdle()
    compose.onNodeWithTag("full_player").assert(!(isDialog() or hasAnyAncestor(isDialog())))
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val decor = activity.window.decorView
    val screen = IntArray(2)
    val window = IntArray(2)
    var safe = Rect.Zero
    var density = 1f
    compose.runOnIdle {
        decor.getLocationOnScreen(screen)
        decor.getLocationInWindow(window)
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor)).getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemGestures())
        safe = Rect(insets.left.toFloat(), insets.top.toFloat(), (device.displayWidth - insets.right).toFloat(), (device.displayHeight - insets.bottom).toFloat())
        density = activity.resources.displayMetrics.density
    }
    fun physical(tag: String): Rect {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        var bounds = Rect.Zero
        compose.runOnIdle {
            val p = node.positionInWindow
            val x = p.x + screen[0] - window[0]
            val y = p.y + screen[1] - window[1]
            bounds = Rect(x, y, x + node.size.width, y + node.size.height)
        }
        return bounds
    }
    val app = physical("app_window")
    val player = physical("full_player")
    assertTrue("Activity viewport must not exceed the physical display: $app", app.bottom <= device.displayHeight + 2f && app.right <= device.displayWidth + 2f)
    assertTrue("Full player stays in the actual activity viewport: $player / $app", player.top >= app.top - 2f && player.bottom <= app.bottom + 2f && player.left >= app.left - 2f && player.right <= app.right + 2f)
    for (tag in listOf("player_repeat", "player_previous", "player_toggle", "player_next", "player_queue", "player_seek")) {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val button = physical(tag)
        assertTrue("Full $tag must clear the real Android bottom navigation, not merely a synthetic parent: $button / $safe",
            button.bottom <= safe.bottom - 20f * density + 2f)
        assertTrue("Full $tag must clear status/cutout and side navigation: $button / $safe",
            button.top >= safe.top - 2f && button.left >= safe.left - 2f && button.right <= safe.right + 2f)
    }
}
