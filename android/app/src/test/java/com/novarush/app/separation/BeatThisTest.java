package com.novarush.app.separation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Port Java de Beat This! comparé à tools/ai/ref.py (valeurs de référence calculées par ref.py). */
public class BeatThisTest {
    @Test
    public void melFilterbankMatchesReference() {
        float[][] fb = BeatThis.melFilterbank(513, 30, 11000, 128, 22050);
        double sum = 0;
        for (float[] row : fb) for (float v : row) sum += v;
        assertEquals(502.2056887466289, sum, 1e-3);
        assertEquals(0.6454879861669842, fb[3][0], 1e-6);
        assertEquals(0.0, fb[4][0], 1e-6);
        assertEquals(0.7431873684876553, fb[26][20], 1e-6);
        assertEquals(0.4138267465273607, fb[27][20], 1e-6);
        assertEquals(0.9315140343208799, fb[144][80], 1e-6);
        assertEquals(0.807008265707696, fb[145][80], 1e-6);
        assertEquals(0.9666993659935434, fb[498][127], 1e-6);
        assertEquals(0.8914054026584763, fb[499][127], 1e-6);
    }

    @Test
    public void logMelMatchesReference() {
        int sr = 22050;
        float[] x = new float[sr * 3];
        for (int i = 0; i < x.length; i++) {
            double t = (double) i / sr;
            x[i] = (float) (0.5 * Math.sin(2 * Math.PI * 440 * t) + (t > 1 ? 0.2 * Math.sin(2 * Math.PI * 1234 * t) : 0));
        }
        float[][] m = BeatThis.logMel(x);
        assertEquals(151, m.length);
        assertEquals(128, m[0].length);
        int[][] at = {{0, 10}, {10, 40}, {75, 40}, {100, 64}, {150, 90}, {150, 10}};
        double[] want = {6.378122329711914, 0.061185747385025024, 1.0038111209869385, 0.010556838475167751, 3.586463212966919, 6.438086032867432};
        for (int k = 0; k < at.length; k++) {
            assertEquals("mel[" + at[k][0] + "][" + at[k][1] + "]", want[k], m[at[k][0]][at[k][1]], 2e-3 + 1e-3 * Math.abs(want[k]));
        }
    }

    @Test
    public void postprocessMatchesReference() {
        float[] b = new float[300], d = new float[300];
        java.util.Arrays.fill(b, -5f);
        java.util.Arrays.fill(d, -5f);
        for (int k : new int[] {10, 60, 61, 110, 160, 210, 211, 212}) b[k] = 2f;
        d[11] = 1f;
        d[208] = 0.5f;
        double[][] r = BeatThis.postprocess(b, d);
        assertArrayEquals(new double[] {0.2, 1.21, 2.2, 3.2, 4.21, 4.24}, r[0], 1e-9);
        assertArrayEquals(new double[] {0.2, 4.21}, r[1], 1e-9);
    }

    @Test
    public void chunkingCoversEveryFrameOnce() throws Exception {
        int T = 4000;
        // Réseau factice : logit = indice absolu de l'image (vérifie le recollage).
        final int[] start = {0};
        int[] starts = BeatThis.chunkStarts(T);
        assertEquals(-6, starts[0]);
        assertEquals(T - (1500 - 6), starts[starts.length - 1]);
        final int[] call = {starts.length - 1};
        BeatThis.Net net = (spect) -> {
            int s = starts[call[0]--];
            float[] o = new float[1500];
            for (int i = 0; i < 1500; i++) o[i] = s + i;
            return new float[][] {o, o.clone()};
        };
        float[][] spect = new float[T][128];
        float[][] out = BeatThis.frames(net, spect, null);
        for (int t = 0; t < T; t++) assertEquals(t, out[0][t], 0);
        assertEquals(0, start[0]);
    }
}
