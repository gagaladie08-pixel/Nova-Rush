package com.novalisten.app.ui.components

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Small, cached software blur: also works on Android 26–30, where Modifier.blur is ignored. */
internal object ArtworkBlur : Transformation {
    override val cacheKey = "nova-cover-background-v1-96px-r4x3"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val factor = minOf(1f, 96f / input.width, 96f / input.height)
        val width = (input.width * factor).roundToInt().coerceAtLeast(1)
        val height = (input.height * factor).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, width, height, true)
        var pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        // Never mutate/recycle Coil's source bitmap (it may also be the foreground cover).
        repeat(3) {
            pixels = pass(pixels, width, height, horizontal = true)
            pixels = pass(pixels, width, height, horizontal = false)
        }
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
    private fun pass(source: IntArray, width: Int, height: Int, horizontal: Boolean): IntArray {
        val output = IntArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var alpha = 0; var red = 0; var green = 0; var blue = 0
            for (offset in -4..4) {
                val xx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                val yy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                val color = source[yy * width + xx]
                alpha += color ushr 24; red += color ushr 16 and 255
                green += color ushr 8 and 255; blue += color and 255
            }
            output[y * width + x] = ((alpha / 9) shl 24) or ((red / 9) shl 16) or ((green / 9) shl 8) or (blue / 9)
        }
        return output
    }
}
