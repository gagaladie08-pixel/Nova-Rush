package com.novalisten.app.ui.components

import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/**
 * Protect the player against the actual window, not only the insets dispatched to a child view.
 * On Android 15, a recreated activity can dispatch a smaller bottom gesture inset to Compose
 * than the root window reports. Union this measurement with Compose's normal safe insets.
 * No listener is replaced and no system inset is consumed here.
 */
@Composable
internal fun rememberPlayerWindowInsets(): WindowInsets {
    val view = LocalView.current
    var protection by remember(view) { mutableStateOf(Insets.NONE) }
    DisposableEffect(view) {
        val location = IntArray(2)
        val rootLocation = IntArray(2)
        fun update() {
            val root = view.rootView
            val insets = ViewCompat.getRootWindowInsets(view) ?: return
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemGestures() or
                WindowInsetsCompat.Type.mandatorySystemGestures() or WindowInsetsCompat.Type.tappableElement() or
                WindowInsetsCompat.Type.ime())
            view.getLocationInWindow(location)
            root.getLocationInWindow(rootLocation)
            val x = location[0] - rootLocation[0]
            val y = location[1] - rootLocation[1]
            // Insets are window-relative. Subtract any space already outside this Compose view
            // (for example in a resized window), rather than adding the status/navigation twice.
            protection = Insets.of(max(0, safe.left - x), max(0, safe.top - y),
                max(0, safe.right - (root.width - x - view.width)),
                max(0, safe.bottom - (root.height - y - view.height)))
        }
        // A pre-draw observation also covers rotation, activity recreation and a navigation-mode
        // change without polling, overriding Compose's listener or forcing another inset dispatch.
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener { update(); true }
        observer.addOnPreDrawListener(listener)
        update()
        onDispose {
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            else view.viewTreeObserver.removeOnPreDrawListener(listener)
        }
    }
    return WindowInsets(protection.left, protection.top, protection.right, protection.bottom)
}
