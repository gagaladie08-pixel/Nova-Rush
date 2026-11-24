package com.novalisten.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith

/**
 * Transitions d'écran propres à chaque univers : durée, direction et courbe viennent du catalogue.
 * Chaque univers garde son caractère (coupe sèche, rebond, rideau, glitch) sans changer la navigation.
 */
fun universeTransitionSpec(spec: ThemeSpec, effects: Boolean): androidx.compose.animation.ContentTransform {
    if (!effects) return EnterTransition.None togetherWith ExitTransition.None
    val duration = spec.transitionMs
    return when (spec.effect) {
        "villain" -> slideInHorizontally(tween(duration ?: 150)) { 14 } togetherWith ExitTransition.None
        "venom" -> (fadeIn(tween((duration ?: 150) / 3)) + slideInHorizontally(tween(duration ?: 150)) { 10 }) togetherWith ExitTransition.None
        "survivor" -> slideInVertically(tween(duration ?: 360), initialOffsetY = { it / 3 }) togetherWith ExitTransition.None
        "y2k", "queen" -> (scaleIn(tween(duration ?: 330), initialScale = .94f) + fadeIn(tween(duration ?: 330))) togetherWith fadeOut(tween(120))
        "velvet" -> slideInHorizontally(tween(duration ?: 500)) { it / 3 } togetherWith fadeOut(tween((duration ?: 500) / 2))
        "chaos" -> slideInHorizontally(tween(duration ?: (100..400).random())) { 16 } togetherWith ExitTransition.None
        "cyber" -> (fadeIn(tween(duration ?: 280)) + scaleIn(tween(duration ?: 280), initialScale = .985f)) togetherWith fadeOut(tween((duration ?: 280) / 2))
        "cloud" -> fadeIn(tween(duration ?: 450)) togetherWith fadeOut(tween(duration ?: 450))
        "revolution" -> (fadeIn(tween(duration ?: 180)) + scaleIn(tween(duration ?: 180), initialScale = 1.02f)) togetherWith ExitTransition.None
        "african" -> slideInHorizontally(tween(duration ?: 320)) { it / 4 } togetherWith fadeOut(tween(duration ?: 320))
        else -> fadeIn(if (duration != null) tween(duration) else spring()) togetherWith fadeOut(if (duration != null) tween(duration) else spring())
    }
}
