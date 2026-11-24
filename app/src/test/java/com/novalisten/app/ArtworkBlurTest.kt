package com.novalisten.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Size
import com.novalisten.app.ui.components.ArtworkBlur
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkBlurTest {
    @Test fun blurIsBoundedWorksBeforeAndroid31AndNeverMutatesTheForegroundCover() = runBlocking {
        val source = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(512 * 256) { if (it % 512 < 256) Color.BLACK else Color.WHITE }
        source.setPixels(pixels, 0, 512, 0, 0, 512, 256)
        val result = ArtworkBlur.transform(source, Size.ORIGINAL)
        assertEquals(480, result.width); assertEquals(240, result.height)
        assertNotSame(source, result); assertFalse(source.isRecycled)
        assertEquals(Color.WHITE, source.getPixel(256, 128))
        val boundary = result.getPixel(result.width / 2, result.height / 2)
        assertTrue(Color.red(boundary) in 1..254)
        assertEquals(255, Color.alpha(boundary))
    }
    /** La pochette de fond doit rester reconnaissable : assez de matière, un flou mesuré. */
    @Test fun backgroundKeepsEnoughDetailToRecogniseTheCover() = runBlocking {
        val source = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(480 * 480) { index ->
            val x = index % 480; val y = index / 480
            if ((x / 60 + y / 60) % 2 == 0) Color.rgb(220, 40, 90) else Color.rgb(20, 20, 60)
        }
        source.setPixels(pixels, 0, 480, 0, 0, 480, 480)
        val result = ArtworkBlur.transform(source, Size.ORIGINAL)
        assertEquals(480, result.width)
        // Un damier de 60 px ne doit pas devenir un aplat : le centre et un bord restent distincts.
        val center = result.getPixel(240, 240)
        val corner = result.getPixel(30, 30)
        assertTrue("Le fond ne doit pas être uniforme", abs(Color.red(center) - Color.red(corner)) > 12)
        assertEquals(255, Color.alpha(center))
    }

    @Test fun evenOnePixelArtworkIsHandledWithoutScalingOrDivisionErrors() = runBlocking {
        val source = Bitmap.createBitmap(intArrayOf(Color.RED), 1, 1, Bitmap.Config.ARGB_8888)
        val result = ArtworkBlur.transform(source, Size.ORIGINAL)
        assertEquals(1, result.width); assertEquals(1, result.height)
        assertEquals(Color.RED, result.getPixel(0, 0)); assertEquals(Color.RED, source.getPixel(0, 0))
    }
}
