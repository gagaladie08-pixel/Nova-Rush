package com.novarush.app.separation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Port Java de CREPE comparé à tools/ai/ref.py. */
public class CrepePitchTest {
    @Test
    public void decodeMatchesReference() {
        float[] p = new float[360];
        for (int i = 0; i < 360; i++) p[i] = (float) (Math.exp(-0.5 * Math.pow((i - 100.3) / 1.5, 2)) * 0.9);
        p[250] = 0.3f;
        float[] d = CrepePitch.decode(p, 0);
        assertEquals(100.9819610215947, d[0], 1e-3);
        assertEquals(0.882178783416748, d[1], 1e-6);
    }

    @Test
    public void framesAreCentredEvery10ms() throws Exception {
        float[] x = new float[16000];
        for (int i = 0; i < x.length; i++) x[i] = i + 1; // 0 = remplissage
        final int[] seen = {0};
        CrepePitch.Net net = (frames, n) -> {
            for (int r = 0; r < n; r++) {
                int k = seen[0] + r;
                // Le centre de la trame k est l'échantillon k·160.
                float centre = frames[r * 1024 + 512];
                float want = k * 160 < x.length ? k * 160 + 1 : 0;
                assertEquals("trame " + k, want, centre, 0);
                float first = frames[r * 1024];
                float wantFirst = k * 160 - 512 >= 0 ? k * 160 - 512 + 1 : 0;
                assertEquals("début trame " + k, wantFirst, first, 0);
            }
            seen[0] += n;
            float[] out = new float[n * 360];
            for (int r = 0; r < n; r++) out[r * 360 + 120] = 1f;
            return out;
        };
        float[][] fc = CrepePitch.track(net, x, null);
        assertEquals(101, fc[0].length);
        assertEquals(101, seen[0]);
        // Case 120 seule → 20·120 + 1997,38 cents.
        assertEquals(10 * Math.pow(2, (20 * 120 + 1997.3794084376191) / 1200), fc[0][50], 1e-2);
        assertEquals(1f, fc[1][50], 0);
    }

    @Test
    public void silentPassagesSkipTheNetwork() throws Exception {
        float[] x = new float[16000 * 6];
        // Voix seulement entre 4 et 5 s.
        for (int i = 64000; i < 80000; i++) x[i] = (float) (0.3 * Math.sin(2 * Math.PI * 220 * i / 16000.0));
        final int[] frames = {0};
        CrepePitch.Net net = (f, n) -> {
            frames[0] += n;
            float[] p = new float[n * CrepePitch.BINS];
            for (int k = 0; k < n; k++) p[k * CrepePitch.BINS + 100] = 0.9f;
            return p;
        };
        float[][] fc = CrepePitch.track(net, x, null);
        int n = CrepePitch.frameCount(x.length);
        org.junit.Assert.assertEquals(n, fc[0].length);
        org.junit.Assert.assertTrue("lots muets sautés : " + frames[0] + " / " + n, frames[0] < n);
        org.junit.Assert.assertEquals(0f, fc[1][50], 0f);
        org.junit.Assert.assertEquals(0.9f, fc[1][450], 1e-6f);
    }
}

