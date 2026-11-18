package com.novalisten.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UniverseBackdrop(modifier: Modifier = Modifier) {
    val spec = LocalUniverse.current
    val enabled = LocalEffects.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val phase = if (enabled && resumed) {
        val infinite = rememberInfiniteTransition(label = "Décor ${spec.name}")
        infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart), label = "ambiance").value
    } else 0f
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(spec.background)
        drawRect(Brush.radialGradient(listOf(spec.glow.copy(alpha = .14f), Color.Transparent), center = Offset(w * .9f, h * .12f), radius = w * 1.1f))
        fun diamond(x: Float, y: Float, r: Float, color: Color, filled: Boolean = false) {
            val p = Path().apply { moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close() }
            if (filled) drawPath(p, color) else drawPath(p, color, style = Stroke(1.dp.toPx()))
        }
        when (spec.effect) {
            "cyber" -> {
                var y = 0f
                while (y < h) { drawLine(spec.secondary.copy(alpha = .035f), Offset(0f, y), Offset(w, y), .65f); y += 5.dp.toPx() }
                val horizon = h * .57f
                for (i in -8..8) drawLine(spec.secondary.copy(alpha = .09f), Offset(w / 2 + i * 14, horizon), Offset(w / 2 + i * w / 4, h), 1.dp.toPx())
                for (i in 0..11) {
                    val ratio = ((i + phase) / 12f)
                    val py = horizon + (h - horizon) * ratio * ratio
                    drawLine(spec.accent.copy(alpha = .08f), Offset(0f, py), Offset(w, py), 1f)
                }
                val scan = h * phase
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, spec.secondary.copy(alpha = .06f), Color.Transparent), startY = scan - 70, endY = scan + 70))
            }
            "disco" -> {
                rotate(phase * 360, Offset(w * .5f, h * .2f)) {
                    repeat(16) { i ->
                        val angle = i * PI * 2 / 16
                        drawCircle((if (i % 2 == 0) spec.secondary else spec.glow).copy(alpha = .09f), 15.dp.toPx(), Offset(w / 2 + cos(angle).toFloat() * w * .55f, h / 3 + sin(angle).toFloat() * h * .3f))
                    }
                }
                val x = w * (phase * 3 % 1f)
                drawLine(spec.accent.copy(alpha = .09f), Offset(x - 100, 0f), Offset(x + 180, h), 20.dp.toPx())
            }
            "villain", "queen" -> repeat(22) { i ->
                val x = (i * .163f % 1f) * w
                val t = (phase + i * .073f) % 1f
                val y = if (spec.effect == "villain") h * (1 - t) else h * t
                drawCircle(spec.primary.copy(alpha = .08f + .10f * sin(i + phase * 15).toFloat().let { kotlin.math.abs(it) }), (1 + i % 3).dp.toPx(), Offset(x, y))
            }
            "y2k", "cloud" -> repeat(10) { i ->
                val x = ((i * .213f + phase * .08f) % 1f) * w
                val y = if (spec.effect == "y2k") (1 - (phase + i * .127f) % 1f) * h else (i * .173f % 1f) * h
                val r = (12 + i % 4 * 12).dp.toPx()
                drawCircle((if (i % 2 == 0) spec.primary else spec.secondary).copy(alpha = .12f), r, Offset(x, y))
                if (spec.effect == "cloud") drawCircle(spec.secondary.copy(alpha = .12f), r * .8f, Offset(x + r, y + r / 4))
                if (spec.effect == "y2k" && i % 3 == 0) diamond(x, y, r / 4, spec.accent.copy(alpha = .4f))
            }
            "velvet" -> {
                repeat(2) { i ->
                    val x = if (i == 0) 0f else w
                    val reach = w * (.3f + .25f * sin(phase * PI * 2 + i).toFloat())
                    val p = Path().apply { moveTo(x, 0f); lineTo(reach, h); lineTo(reach + w * .5f, h); close() }
                    drawPath(p, Brush.verticalGradient(listOf(spec.accent.copy(alpha = .02f), spec.glow.copy(alpha = .1f))))
                }
                for (i in 0..12) drawLine(spec.primary.copy(alpha = .04f), Offset(i * w / 12, 0f), Offset(i * w / 12, h), 8.dp.toPx())
            }
            "venom" -> repeat(9) { i ->
                val x = i * w / 8
                val y = (phase + i * .17f) % 1 * h
                drawLine(spec.primary.copy(alpha = .13f), Offset(x, y - 90), Offset(x, y), (2 + i % 3).dp.toPx(), StrokeCap.Round)
            }
            "solara" -> {
                val center = Offset(w * .8f, h * .3f)
                drawCircle(Brush.radialGradient(listOf(spec.glow.copy(alpha = .14f), Color.Transparent), center, w * (.65f + sin(phase * PI * 4).toFloat() * .08f)), w * .7f, center)
                rotate(phase * 360, center) { repeat(16) { i ->
                    val a = i * PI / 8
                    drawLine(spec.secondary.copy(alpha = .08f), center + Offset(cos(a).toFloat() * w * .3f, sin(a).toFloat() * w * .3f), center + Offset(cos(a).toFloat() * w, sin(a).toFloat() * w), 2.dp.toPx())
                } }
            }
            "chaos" -> repeat(16) { i ->
                val j = (phase * 55).toInt() % 5
                val x = ((i * .231f) % 1f) * w + (i % 3 - 1) * j
                val y = (i * .163f % 1) * h
                val p = Path().apply { moveTo(x, y); lineTo(x + 35, y - 60); lineTo(x + 16, y + 30); close() }
                drawPath(p, spec.primary.copy(alpha = .025f + (i % 3) * .012f))
            }
            "survivor", "rainbow" -> {
                val colors = listOf(spec.primary, spec.secondary, spec.glow, spec.accent)
                repeat(7) { i ->
                    val x = w * ((i * .23f + phase * .16f) % 1)
                    val y = h * ((i * .17f + phase * .07f) % 1)
                    drawCircle(Brush.radialGradient(listOf(colors[i % colors.size].copy(alpha = .09f), Color.Transparent), Offset(x, y), w * .65f), w * .65f, Offset(x, y))
                }
                if (spec.effect == "rainbow") repeat(25) { i -> diamond((i * .127f % 1) * w, (i * .263f % 1) * h, 2.dp.toPx(), colors[i % 4].copy(alpha = .15f)) }
            }
            "revolution" -> repeat(28) { i ->
                val bar = (12 + 22 * (sin(i * .7 + phase * PI * 14) + 1)).dp.toPx()
                drawRoundRect((if (i % 2 == 0) spec.primary else spec.secondary).copy(alpha = .1f), Offset(w * i / 28, h - bar), Size(w / 40, bar))
            }
            "african" -> repeat(24) { i -> diamond(((i % 4 + phase * .3f) / 4) * w, (i / 4f) * h / 6, 20.dp.toPx(), (if (i % 2 == 0) spec.primary else spec.secondary).copy(alpha = .1f)) }
            "angel" -> {
                drawOval(spec.secondary.copy(alpha = .07f), Offset(w * .2f, h * .1f), Size(w * .6f, w * .13f), style = Stroke(2.dp.toPx()))
                repeat(14) { i ->
                    val x = (i * .233f % 1) * w
                    val y = ((phase + i * .173f) % 1) * h
                    rotate(i * 29f, Offset(x, y)) { drawOval(spec.secondary.copy(alpha = .07f), Offset(x, y), Size(5.dp.toPx(), 22.dp.toPx())) }
                }
            }
        }
    }
}

/** An explicitly decorative preview: these points are not listening statistics. */
@Composable
fun CurvePreview(spec: ThemeSpec, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val points = listOf(.7f, .48f, .56f, .25f, .4f, .19f, .32f)
        val path = Path()
        points.forEachIndexed { index, y ->
            val x = size.width * index / (points.size - 1)
            if (index == 0) path.moveTo(x, size.height * y) else if (spec.curveStyle in listOf("villain", "venom", "chaos")) path.lineTo(x, size.height * y)
            else {
                val lastX = size.width * (index - 1) / (points.size - 1)
                path.cubicTo((lastX + x) / 2, size.height * points[index - 1], (lastX + x) / 2, size.height * y, x, size.height * y)
            }
        }
        val strokeColor = when(spec.curveStyle) { "cyber" -> spec.accent; "disco", "queen", "velvet" -> spec.secondary; "chaos" -> spec.primary; else -> spec.primary }
        drawPath(path, strokeColor.copy(alpha = .08f), style = Stroke((spec.curveWidth + spec.curveHalo).dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, strokeColor, style = Stroke(spec.curveWidth.dp.toPx(), cap = StrokeCap.Round))
        val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(spec.primary.copy(alpha = .2f), Color.Transparent)))
    }
}
