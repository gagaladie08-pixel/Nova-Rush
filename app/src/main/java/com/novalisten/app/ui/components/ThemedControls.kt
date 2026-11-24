package com.novalisten.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novalisten.app.ui.theme.CurveReveal
import com.novalisten.app.ui.theme.GlyphFamily
import com.novalisten.app.ui.theme.LocalEffects
import com.novalisten.app.ui.theme.LocalUniverse
import com.novalisten.app.ui.theme.ThemeSpec

/** Ornement propre à chaque univers, dessiné autour et derrière le contrôle. */
fun DrawScope.drawControlOrnament(spec: ThemeSpec, phase: Float, width: Float, height: Float, progress: Float = 0f) {
    val accent = spec.accent
    when (spec.motion.curveReveal) {
        CurveReveal.SWEEP -> {
            repeat(3) { i -> drawLine(spec.secondary.copy(alpha = .05f), Offset(0f, height * (i + 1) / 4), Offset(width, height * (i + 1) / 4), .6f) }
        }
        CurveReveal.BLINK -> repeat(9) { i ->
            val lit = ((phase * 9).toInt() + i) % 9 == 0
            drawCircle(if (lit) accent.copy(alpha = .8f) else spec.primary.copy(alpha = .22f), 1.6.dp.toPx(),
                Offset(width * (i + .5f) / 9, height / 2))
        }
        CurveReveal.HARSH -> {
            val p = Path().apply { moveTo(0f, height); lineTo(width * .34f, height * .3f); lineTo(width * .62f, height); lineTo(width, height * .44f) }
            drawPath(p, spec.primary.copy(alpha = .10f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Butt))
        }
        CurveReveal.GLOW -> drawCircle(accent.copy(alpha = .10f), radius = height * 1.4f, center = Offset(width, height / 2))
        CurveReveal.STARS -> repeat(6) { i ->
            val x = width * ((i * .29f + phase * .1f) % 1f)
            drawCircle(accent.copy(alpha = .18f), 1.2.dp.toPx(), Offset(x, height * (.2f + (i % 3) * .3f)))
        }
        CurveReveal.SPOTLIGHT -> {
            val p = Path().apply { moveTo(width * .1f, 0f); lineTo(width * .4f, height); lineTo(width * .6f, height); lineTo(width * .9f, 0f); close() }
            drawPath(p, Brush.verticalGradient(listOf(accent.copy(alpha = .10f), Color.Transparent)))
        }
        CurveReveal.DRIP -> repeat(4) { i ->
            val x = width * (i + .5f) / 4
            drawLine(spec.primary.copy(alpha = .16f), Offset(x, 0f), Offset(x, height * .5f), (1 + i % 2).dp.toPx(), StrokeCap.Round)
        }
        CurveReveal.FADE -> drawCircle(spec.primary.copy(alpha = .07f), radius = height * 1.6f, center = Offset(width * .5f, height / 2))
        CurveReveal.ORBIT -> rotate(phase * 360, Offset(width / 2, height / 2)) {
            drawArc(spec.glow.copy(alpha = .12f), 0f, 240f, false, topLeft = Offset(width * .1f, height * .1f),
                size = Size(width * .8f, height * .8f), style = Stroke(1.2.dp.toPx()))
        }
        CurveReveal.JITTER -> {
            val shift = (((phase * 40).toInt() % 5) - 2) * .8f
            drawLine(spec.primary.copy(alpha = .09f), Offset(0f, height / 2 + shift), Offset(width, height / 2 - shift), 3.dp.toPx())
        }
        CurveReveal.RIBBON -> {
            val colors = listOf(spec.primary, spec.secondary, spec.glow, spec.accent)
            colors.forEachIndexed { i, color ->
                drawLine(color.copy(alpha = .16f), Offset(0f, height * (.2f + i * .2f)), Offset(width, height * (.28f + i * .2f)), 1.6.dp.toPx())
            }
        }
        CurveReveal.SPARKLE -> repeat(8) { i ->
            val x = width * ((i * .17f + phase * .05f) % 1f)
            val y = height * ((i * .31f) % 1f)
            drawCircle(listOf(spec.primary, spec.secondary, spec.accent)[i % 3].copy(alpha = .18f), 1.3.dp.toPx(), Offset(x, y))
        }
        CurveReveal.FLASH -> drawRect(accent.copy(alpha = (if ((phase * 6).toInt() % 6 == 0) .10f else .02f)))
        CurveReveal.DIAGONAL -> {
            val p = Path().apply { moveTo(0f, height); lineTo(width * .5f, 0f); lineTo(width, height); close() }
            drawPath(p, Brush.linearGradient(listOf(spec.primary.copy(alpha = .12f), spec.secondary.copy(alpha = .05f))))
        }
        CurveReveal.ALTERNATE -> {
            val highlighted = ((phase * 2).toInt() % 2) == 0
            drawLine(if (highlighted) accent.copy(alpha = .22f) else spec.secondary.copy(alpha = .10f),
                Offset(0f, height / 2), Offset(width / 2, height / 2), 2.dp.toPx())
        }
    }
}

/**
 * Curseur de lecture du player : trait, halo et dévoilement de l'univers.
 * Le comportement de seek est conservé (geste, plage, valeur) ; seule la peinture change.
 */
@Composable
internal fun UniverseSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "player_seek",
) {
    val spec = LocalUniverse.current
    val effects = LocalEffects.current
    val interaction = remember { MutableInteractionSource() }
    val draggedState = interaction.collectIsDraggedAsState()
    val dragged = draggedState.value
    val transition = if (effects) rememberInfiniteTransition(label = "courbe ${spec.name}") else null
    val phase = transition?.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "phase")?.value ?: 0f
    val fraction = ((value - range.start) / (range.endInclusive - range.start).coerceAtLeast(.001f)).coerceIn(0f, 1f)
    val halo = spec.curveHalo.dp
    val thickness = spec.curveWidth.dp
    val accent = spec.accent
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val trackHeight = (thickness + halo + 10.dp).coerceAtLeast(40.dp)
        Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
            val centerY = size.height / 2
            val haloPx = halo.toPx()
            val thicknessPx = thickness.toPx()
            // Halo de la courbe derrière la partie parcourue.
            drawLine(spec.primary.copy(alpha = if (dragged) .42f else .24f), Offset(0f, centerY),
                Offset(size.width * fraction, centerY), thicknessPx + haloPx, StrokeCap.Round)
            // Piste de fond, teintée par la couleur principale de l'univers.
            drawLine(spec.primary.copy(alpha = .22f), Offset(0f, centerY), Offset(size.width, centerY), thicknessPx, StrokeCap.Round)
            // Remplissage : dégradé pour les univers lumineux, aplat sinon.
            val gradientIds = setOf("cyber", "solara", "african", "rainbow", "disco", "queen", "velvet")
            val fill: androidx.compose.ui.graphics.Brush = if (spec.id in gradientIds)
                Brush.horizontalGradient(listOf(spec.primary, accent, spec.secondary))
            else SolidColor(accent)
            drawLine(fill, Offset(0f, centerY), Offset(size.width * fraction, centerY), thicknessPx, StrokeCap.Round)
            // Dévoilement et ornements propres à l'univers, limités à la partie parcourue.
            clipRect(right = size.width * fraction) { drawControlOrnament(spec, phase, size.width, size.height, fraction) }
            // Curseur : le dessin suit la famille d'icônes, la cible tactile reste celle du Slider.
            val thumbX = size.width * fraction
            when (spec.glyphs) {
                GlyphFamily.HUD -> {
                    drawLine(accent, Offset(thumbX, centerY - 7.dp.toPx()), Offset(thumbX, centerY + 7.dp.toPx()), 2.5.dp.toPx())
                    drawCircle(accent, radius = 2.dp.toPx(), center = Offset(thumbX, centerY))
                }
                GlyphFamily.BUBBLE, GlyphFamily.CLOUD -> drawCircle(accent, radius = 7.dp.toPx(), center = Offset(thumbX, centerY))
                GlyphFamily.DIAMOND, GlyphFamily.GEM -> {
                    val p = Path().apply {
                        moveTo(thumbX, centerY - 7.dp.toPx()); lineTo(thumbX + 7.dp.toPx(), centerY)
                        lineTo(thumbX, centerY + 7.dp.toPx()); lineTo(thumbX - 7.dp.toPx(), centerY); close()
                    }
                    drawPath(p, accent)
                }
                GlyphFamily.WING -> {
                    drawCircle(accent.copy(alpha = .30f), radius = 9.dp.toPx(), center = Offset(thumbX, centerY))
                    drawCircle(accent, radius = 4.5.dp.toPx(), center = Offset(thumbX, centerY))
                }
                else -> drawCircle(accent, radius = 6.dp.toPx(), center = Offset(thumbX, centerY))
            }
        }
        // Comportement inchangé : plage, glissement, clic pour seek (exigences des tests).
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = { onFinished(value) },
            valueRange = range,
            enabled = enabled,
            interactionSource = interaction,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent, disabledActiveTrackColor = Color.Transparent, disabledInactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().height(44.dp).testTag(tag),
        )
    }
}

/** Barre de progression linéaire du thème (mini-player, chargements). */
@Composable
fun UniverseProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val spec = LocalUniverse.current
    Box(modifier.fillMaxWidth().height(height).background(spec.primary.copy(alpha = .22f), CircleShape)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxSize()
            .background(Brush.horizontalGradient(listOf(spec.primary, spec.accent)), CircleShape))
    }
}

/** Chargement circulaire propre à l'univers. */
@Composable
fun UniverseSpinner(modifier: Modifier = Modifier) {
    val spec = LocalUniverse.current
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = spec.accent, trackColor = spec.primary.copy(alpha = .2f), strokeWidth = 3.dp)
    }
}

/** Ancien nom conservé pour compatibilité interne. */
@Composable
fun ThemedLinearProgress(progress: () -> Float, modifier: Modifier = Modifier, testTag: String? = null) {
    val value = progress()
    val spec = LocalUniverse.current
    LinearProgressIndicator(
        progress = { value },
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier,
        color = spec.accent,
        trackColor = spec.primary.copy(alpha = .18f),
    )
}
