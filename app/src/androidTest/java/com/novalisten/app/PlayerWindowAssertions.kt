package com.novalisten.app

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import java.io.File

/** Compare UNCLIPPED Compose controls to Android's physical display/insets, not only to their own parent. */
internal fun assertPlayerInsidePhysicalWindow(compose: ComposeContentTestRule, activity: MainActivity) {
    compose.waitForIdle()
    compose.onNodeWithTag("full_player").assert(!(isDialog() or hasAnyAncestor(isDialog())))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val controls = listOf("player_repeat", "player_previous", "player_toggle", "player_next", "player_queue", "player_seek")
    val tags = listOf("app_window", "full_player", "player_safe_area", "player_transport") + controls
    val nodes = tags.associateWith { compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode() }
    val bounds = mutableMapOf<String, Rect>()
    val insetDetails = mutableMapOf<String, String>()
    var safe = Rect.Zero
    var density = 1f
    val width = device.displayWidth
    val height = device.displayHeight
    // Take the platform insets and ALL coordinates in one UI-thread snapshot: a moving system bar
    // must not be compared to button coordinates taken several frames later.
    compose.runOnIdle {
        val decor = activity.window.decorView
        val screen = IntArray(2)
        val window = IntArray(2)
        decor.getLocationOnScreen(screen)
        decor.getLocationInWindow(window)
        val raw = requireNotNull(ViewCompat.getRootWindowInsets(decor))
        val insets = raw.getInsets(WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemGestures())
        safe = Rect(insets.left.toFloat(), insets.top.toFloat(), (width - insets.right).toFloat(), (height - insets.bottom).toFloat())
        density = activity.resources.displayMetrics.density
        insetDetails["systemBars"] = raw.getInsets(WindowInsetsCompat.Type.systemBars()).toString()
        insetDetails["systemGestures"] = raw.getInsets(WindowInsetsCompat.Type.systemGestures()).toString()
        insetDetails["mandatoryGestures"] = raw.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).toString()
        nodes.forEach { (tag, node) ->
            val p = node.positionInWindow
            val x = p.x + screen[0] - window[0]
            val y = p.y + screen[1] - window[1]
            bounds[tag] = Rect(x, y, x + node.size.width, y + node.size.height)
        }
    }
    val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
        ?: activity.getExternalFilesDir("evidence")!!
    output.mkdirs()
    val evidence = "window-bounds-${System.nanoTime()}"
    File(output, "$evidence.txt").writeText("Display: ${width}x${height}, density=$density\nSafe: $safe\n$insetDetails\n" +
        bounds.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    try {
        val app = bounds.getValue("app_window")
        val player = bounds.getValue("full_player")
        assertTrue("Activity viewport must not exceed the physical display: $app", app.bottom <= height + 2f && app.right <= width + 2f)
        assertTrue("Full player stays in the actual activity viewport: $player / $app", player.top >= app.top - 2f && player.bottom <= app.bottom + 2f && player.left >= app.left - 2f && player.right <= app.right + 2f)
        for (tag in controls) {
            val button = bounds.getValue(tag)
            assertTrue("Full $tag must clear the real Android bottom navigation, not merely a synthetic parent: $button / $safe; $evidence",
                button.bottom <= safe.bottom - 20f * density + 2f)
            assertTrue("Full $tag must clear status/cutout and side navigation: $button / $safe; $evidence",
                button.top >= safe.top - 2f && button.left >= safe.left - 2f && button.right <= safe.right + 2f)
        }
    } catch (error: AssertionError) {
        device.takeScreenshot(File(output, "$evidence.png"))
        throw error
    }
}
