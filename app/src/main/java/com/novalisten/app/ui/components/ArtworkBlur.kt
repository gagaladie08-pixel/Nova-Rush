package com.novalisten.app.ui.components

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Flou logiciel borné : fonctionne aussi sur Android 26–30, où Modifier.blur est ignoré.
 *
 * La base est nettement plus grande qu'avant (480 px, une seule passe de rayon 5) : à 96 px
 * floutés trois fois puis étirés sur 1080 px, il ne restait rien de la pochette.
 */
internal object ArtworkBlur : Transformation {
    override val cacheKey = "nova-cover-background-v2-480px-r5"
    private const val BASE_WIDTH = 480f
    private const val RADIUS = 5

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val factor = minOf(1f, BASE_WIDTH / input.width, BASE_WIDTH / input.height)
        val width = (input.width * factor).roundToInt().coerceAtLeast(1)
        val height = (input.height * factor).roundToInt().coerceAtLeast(1)
        val small = if (width == input.width && height == input.height) input
        else Bitmap.createScaledBitmap(input, width, height, true)
        var pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        // Une passe horizontale puis une verticale : un flou séparable, pas un empilement de passes.
        pixels = pass(pixels, width, height, horizontal = true)
        pixels = pass(pixels, width, height, horizontal = false)
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun pass(source: IntArray, width: Int, height: Int, horizontal: Boolean): IntArray {
        val output = IntArray(source.size)
        val span = RADIUS * 2 + 1
        for (y in 0 until height) for (x in 0 until width) {
            var alpha = 0; var red = 0; var green = 0; var blue = 0
            for (offset in -RADIUS..RADIUS) {
                val xx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                val yy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                val color = source[yy * width + xx]
                alpha += color ushr 24; red += color ushr 16 and 255
                green += color ushr 8 and 255; blue += color and 255
            }
            output[y * width + x] = ((alpha / span) shl 24) or ((red / span) shl 16) or ((green / span) shl 8) or (blue / span)
        }
        return output
    }
}
