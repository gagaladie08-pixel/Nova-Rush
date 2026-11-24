package com.novalisten.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.novalisten.app.ui.theme.NovaIcon

/**
 * Icône de l'univers : le sens et l'action ne changent jamais, seul le dessin change.
 * Chaque famille a une géométrie propre, dessinée à la main, sans dépendre d'un jeu externe.
 */
@Composable
fun NovaIcon(
    image: ImageVector,
    description: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalUniverse.current.text,
) {
    val spec = LocalUniverse.current
    Box(modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) { drawGlyphOrnament(spec, spec.glyphs) }
        androidx.compose.material3.Icon(image, description, Modifier.matchParentSize().padding(3.dp), tint)
    }
}

private fun DrawScope.drawGlyphOrnament(spec: ThemeSpec, family: GlyphFamily) {
    val accent = spec.accent.copy(alpha = .55f)
    val soft = spec.primary.copy(alpha = .35f)
    val w = size.width
    val h = size.height
    when (family) {
        GlyphFamily.HUD -> {
            val d = w * .22f
            listOf(Offset.Zero, Offset(w, 0f), Offset(0f, h), Offset(w, h)).forEach { p ->
                val sx = if (p.x == 0f) 1 else -1
                val sy = if (p.y == 0f) 1 else -1
                drawLine(accent, p, p + Offset(sx * d, 0f), 1.2.dp.toPx())
                drawLine(accent, p, p + Offset(0f, sy * d), 1.2.dp.toPx())
            }
            drawCircle(soft, radius = w * .34f, style = Stroke(1.dp.toPx()))
        }
        GlyphFamily.CHROME -> {
            drawCircle(soft, radius = w * .46f, style = Stroke(1.dp.toPx()))
            drawArc(accent, 210f, 120f, false, topLeft = Offset(w * .12f, h * .12f), size = Size(w * .76f, h * .76f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        }
        GlyphFamily.SHARD -> {
            val p = Path().apply { moveTo(w * .08f, h * .92f); lineTo(w * .34f, h * .12f); lineTo(w * .52f, h * .92f); close() }
            drawPath(p, accent, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
            drawLine(soft, Offset(w * .58f, h * .2f), Offset(w * .96f, h * .2f), 2.dp.toPx())
        }
        GlyphFamily.CROWN -> {
            val p = Path().apply {
                moveTo(w * .12f, h * .74f); lineTo(w * .2f, h * .28f); lineTo(w * .38f, h * .52f)
                lineTo(w * .5f, h * .2f); lineTo(w * .62f, h * .52f); lineTo(w * .8f, h * .28f); lineTo(w * .88f, h * .74f); close()
            }
            drawPath(p, accent, style = Stroke(1.3.dp.toPx()))
            drawCircle(soft, 1.4.dp.toPx(), Offset(w * .5f, h * .18f))
        }
        GlyphFamily.BUBBLE -> {
            drawCircle(soft.copy(alpha = .30f), radius = w * .40f)
            drawCircle(accent, radius = w * .46f, style = Stroke(1.2.dp.toPx()))
            drawCircle(accent.copy(alpha = .8f), radius = w * .07f, center = Offset(w * .74f, h * .24f))
        }
        GlyphFamily.DRAPE -> {
            repeat(3) { i ->
                val x = w * (.16f + i * .34f)
                val p = Path().apply { moveTo(x, h * .04f); cubicTo(x + w * .12f, h * .34f, x - w * .10f, h * .62f, x + w * .05f, h * .96f) }
                drawPath(p, accent, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        GlyphFamily.FANG -> {
            val p = Path().apply { moveTo(w * .14f, h * .10f); lineTo(w * .30f, h * .92f); lineTo(w * .5f, h * .30f); lineTo(w * .70f, h * .92f); lineTo(w * .86f, h * .10f) }
            drawPath(p, accent, style = Stroke(1.8.dp.toPx()))
        }
        GlyphFamily.CLOUD -> {
            drawCircle(accent, radius = w * .22f, center = Offset(w * .36f, h * .56f))
            drawCircle(accent, radius = w * .27f, center = Offset(w * .58f, h * .48f))
            drawCircle(soft, radius = w * .18f, center = Offset(w * .74f, h * .62f))
        }
        GlyphFamily.RAY -> {
            rotate(0f, Offset(w / 2, h / 2)) {
                drawCircle(accent, radius = w * .17f, style = Stroke(1.3.dp.toPx()))
                repeat(8) { i ->
                    val a = i * PI / 4
                    drawLine(soft, Offset(w / 2 + cos(a).toFloat() * w * .28f, h / 2 + sin(a).toFloat() * h * .28f),
                        Offset(w / 2 + cos(a).toFloat() * w * .46f, h / 2 + sin(a).toFloat() * h * .46f), 1.4.dp.toPx())
                }
            }
        }
        GlyphFamily.RIVET -> {
            drawRoundRect(accent, Offset(w * .1f, h * .1f), Size(w * .8f, h * .8f), CornerRadius(1.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            listOf(Offset(w * .1f, h * .1f), Offset(w * .9f, h * .1f), Offset(w * .1f, h * .9f), Offset(w * .9f, h * .9f)).forEach {
                drawCircle(soft, 1.6.dp.toPx(), it)
            }
        }
        GlyphFamily.PRIDE -> {
            val colors = listOf(spec.primary, spec.secondary, spec.glow, spec.accent)
            colors.forEachIndexed { i, color ->
                drawArc(color.copy(alpha = .65f), 180f + i * 6f, 14f, false,
                    topLeft = Offset(w * .04f, h * .16f), size = Size(w * .92f, h * .92f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        GlyphFamily.GEM -> {
            val p = Path().apply {
                moveTo(w * .5f, h * .06f); lineTo(w * .92f, h * .38f); lineTo(w * .74f, h * .92f)
                lineTo(w * .26f, h * .92f); lineTo(w * .08f, h * .38f); close()
            }
            drawPath(p, accent, style = Stroke(1.3.dp.toPx()))
            drawLine(soft, Offset(w * .5f, h * .06f), Offset(w * .5f, h * .92f), 1.dp.toPx())
        }
        GlyphFamily.POLAROID -> {
            drawRoundRect(accent, Offset(w * .1f, h * .08f), Size(w * .8f, h * .84f), CornerRadius(2.dp.toPx()), style = Stroke(1.3.dp.toPx()))
            drawRect(soft, Offset(w * .18f, h * .2f), Size(w * .64f, h * .44f))
            drawLine(accent, Offset(w * .18f, h * .82f), Offset(w * .58f, h * .82f), 1.4.dp.toPx())
        }
        GlyphFamily.DIAMOND -> {
            val p = Path().apply { moveTo(w * .5f, h * .06f); lineTo(w * .94f, h * .5f); lineTo(w * .5f, h * .94f); lineTo(w * .06f, h * .5f); close() }
            drawPath(p, accent, style = Stroke(1.5.dp.toPx()))
            drawPath(Path().apply { moveTo(w * .5f, h * .26f); lineTo(w * .74f, h * .5f); lineTo(w * .5f, h * .74f); lineTo(w * .26f, h * .5f); close() },
                soft, style = Stroke(1.dp.toPx()))
        }
        GlyphFamily.WING -> {
            val left = Path().apply { moveTo(w * .46f, h * .28f); cubicTo(w * .2f, h * .18f, w * .06f, h * .48f, w * .12f, h * .78f); cubicTo(w * .28f, h * .62f, w * .38f, h * .5f, w * .46f, h * .4f) }
            val right = Path().apply { moveTo(w * .54f, h * .28f); cubicTo(w * .8f, h * .18f, w * .94f, h * .48f, w * .88f, h * .78f); cubicTo(w * .72f, h * .62f, w * .62f, h * .5f, w * .54f, h * .4f) }
            drawPath(left, accent, style = Stroke(1.2.dp.toPx()))
            drawPath(right, accent, style = Stroke(1.2.dp.toPx()))
            drawCircle(soft, 1.6.dp.toPx(), Offset(w * .5f, h * .18f))
        }
    }
}
