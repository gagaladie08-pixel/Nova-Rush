package com.novarush.app.separation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Découpage en fenêtres de Basic Pitch, comme tools/ai/ref.py (basic_pitch_run). */
public class BasicPitchTest {
    @Test
    public void windowsAreStitchedLikeTheReference() throws Exception {
        int len = 22050 * 5 + 123;
        float[] x = new float[len];
        for (int i = 0; i < len; i++) x[i] = (i % 1000) / 1000f;
        // Faux réseau : image f de la fenêtre = échantillon f·256 de la fenêtre (172 images).
        BasicPitch.Net net = (w) -> {
            float[] note = new float[172 * 88], onset = new float[172 * 88];
            for (int f = 0; f < 172; f++) {
                for (int k = 0; k < 88; k++) {
                    note[f * 88 + k] = w[f * 256];
                    onset[f * 88 + k] = k == 3 ? 1f : 0f;
                }
            }
            return new float[][] {note, onset};
        };
        byte[][] out = BasicPitch.run(net, x, null);
        int n = BasicPitch.frameCount(len);
        assertEquals((int) Math.floor(len * 86.0 / 22050), n);
        assertEquals(n * 88, out[1].length);
        for (int k = 0; k < n; k++) {
            int w = k / 142, j = k % 142;
            int sample = w * BasicPitch.STEP + j * 256; // instant de l'image k
            int want = Math.round(x[sample] * 255);
            assertEquals("image " + k, want, out[1][k * 88 + 5] & 0xff);
            assertEquals(255, out[0][k * 88 + 3] & 0xff);
            assertEquals(0, out[0][k * 88 + 4] & 0xff);
        }
    }

    @Test
    public void quantizationClamps() {
        assertEquals(0, BasicPitch.q(-0.2f) & 0xff);
        assertEquals(255, BasicPitch.q(1.4f) & 0xff);
        assertEquals(128, BasicPitch.q(0.5f) & 0xff);
    }
}
