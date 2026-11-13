package com.novarush.app.separation;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.FloatBuffer;
import org.junit.Test;

/** Portage Java de Demucs comparé à tools/ai/ref.py et tools/ai/demucs_stream.py. */
public class DemucsSeparatorTest {
    private static float[][] signal() {
        int n = DemucsSeparator.SEGMENT;
        float[] l = new float[n], r = new float[n];
        for (int i = 0; i < n; i++) {
            l[i] = (float) Math.sin(0.001 * Math.pow(i, 1.3) % 6.28);
            r[i] = (float) Math.cos(0.0003 * i);
        }
        return new float[][] {l, r};
    }

    @Test
    public void specMatchesReference() {
        float[][] x = signal();
        DemucsSeparator d = new DemucsSeparator(null);
        float[] z = new float[4 * DemucsSeparator.BINS * DemucsSeparator.LE];
        d.spec(x[0], x[1], z);
        int plane = DemucsSeparator.BINS * DemucsSeparator.LE;
        // (canal, case, image) → (réel, imaginaire), valeurs de ref.demucs_spec
        int[][] at = {{0, 0, 0}, {0, 10, 5}, {1, 100, 200}, {0, 2047, 335}, {1, 7, 335}};
        double[][] want = {
            {4.726577065641639, 0.0},
            {-2.59871839614243, -4.926866010365351},
            {1.1248391809570412e-08, -5.5508392654150155e-06},
            {0.0001172984088361334, 7.553216386774197e-05},
            {-0.010039042547453938, 0.037302137694236126},
        };
        for (int k = 0; k < at.length; k++) {
            int c = at[k][0], idx = at[k][1] * DemucsSeparator.LE + at[k][2];
            double tol = 2e-4 + 1e-3 * Math.hypot(want[k][0], want[k][1]);
            assertEquals("re " + k, want[k][0], z[(2 * c) * plane + idx], tol);
            assertEquals("im " + k, want[k][1], z[(2 * c + 1) * plane + idx], tol);
        }
    }

    @Test
    public void ispecMatchesReference() {
        float[][] x = signal();
        DemucsSeparator d = new DemucsSeparator(null);
        float[] z = new float[4 * DemucsSeparator.BINS * DemucsSeparator.LE];
        d.spec(x[0], x[1], z);
        float[] y = new float[DemucsSeparator.SEGMENT];
        d.ispecMonoAdd(FloatBuffer.wrap(z), 0, y);
        int[] at = {0, 5, 1000, 171990, 343979};
        double[] want = {0.25000022916767795, 0.2538477297995752, 0.9589451711872714, 0.2660393642186609, 0.004993161412037622};
        for (int k = 0; k < at.length; k++) assertEquals("y[" + at[k] + "]", want[k], y[at[k]], 2e-4);
    }

    @Test
    public void weightsAreTriangular() {
        float[] w = DemucsSeparator.weights();
        int half = DemucsSeparator.SEGMENT / 2;
        assertEquals(1.0 / half, w[0], 1e-9);
        assertEquals(1.0, w[half - 1], 1e-6);
        assertEquals(1.0, w[half], 1e-6);
        assertEquals(1.0 / half, w[DemucsSeparator.SEGMENT - 1], 1e-9);
    }

    /** Source stéréo en mémoire. */
    static final class Mem implements Resampler.Source {
        final float[] inter;

        Mem(float[] inter) {
            this.inter = inter;
        }

        @Override
        public long frames() {
            return inter.length / 2;
        }

        @Override
        public int channels() {
            return 2;
        }

        @Override
        public void read(long start, int count, float[] dst) {
            for (int i = 0; i < count; i++) {
                long p = start + i;
                boolean in = p >= 0 && p < frames();
                dst[2 * i] = in ? inter[(int) (2 * p)] : 0;
                dst[2 * i + 1] = in ? inter[(int) (2 * p + 1)] : 0;
            }
        }
    }

    /**
     * Réseau factice : chaque source rend le segment tel quel (spectre nul).
     * Le recouvrement pondéré et la normalisation doivent alors redonner
     * exactement le mono de l'entrée, y compris au dernier segment (centré).
     */
    @Test
    public void streamingOverlapAddIsExact() throws Exception {
        final int seg = DemucsSeparator.SEGMENT;
        final float[] zeroSpec = new float[DemucsSeparator.SOURCES * 4 * DemucsSeparator.BINS * DemucsSeparator.LE];
        final float[] wave = new float[DemucsSeparator.SOURCES * 2 * seg];
        final int[] calls = {0};
        DemucsSeparator.Net net = (mix, spec, sink) -> {
            calls[0]++;
            for (int s = 0; s < DemucsSeparator.SOURCES; s++) {
                System.arraycopy(mix, 0, wave, (2 * s) * seg, seg);
                System.arraycopy(mix, seg, wave, (2 * s + 1) * seg, seg);
            }
            sink.accept(FloatBuffer.wrap(zeroSpec), FloatBuffer.wrap(wave));
        };
        int n = 700001;
        float[] inter = new float[2 * n];
        for (int i = 0; i < n; i++) {
            inter[2 * i] = (float) (0.3 * Math.sin(i * 0.01) + 0.05);
            inter[2 * i + 1] = (float) (0.2 * Math.cos(i * 0.003) - 0.02);
        }
        File[] f = new File[3];
        RawAudio.Writer[] w = new RawAudio.Writer[3];
        for (int k = 0; k < 3; k++) {
            f[k] = File.createTempFile("demucs" + k, ".raw");
            w[k] = new RawAudio.Writer(f[k], 1);
        }
        new DemucsSeparator(net).separate(new Mem(inter), w, 0f, null);
        for (RawAudio.Writer x : w) x.close();
        assertEquals((n + DemucsSeparator.STRIDE - 1) / DemucsSeparator.STRIDE, calls[0]);
        for (int k = 0; k < 3; k++) {
            try (RawAudio.Reader r = new RawAudio.Reader(f[k], 1)) {
                assertEquals(n, r.frames());
                float[] y = new float[n];
                r.read(0, n, y);
                for (int i = 0; i < n; i += 997) {
                    assertEquals("source " + k + " [" + i + "]", 0.5 * (inter[2 * i] + inter[2 * i + 1]), y[i], 2e-5);
                }
                assertEquals(0.5 * (inter[2 * (n - 1)] + inter[2 * n - 1]), y[n - 1], 2e-5);
            }
            f[k].delete();
        }
    }

    /** Segments muets : pas d'appel au réseau, sortie = moyenne (silence). */
    @Test
    public void silentSegmentsSkipTheNetwork() throws Exception {
        DemucsSeparator.Net net = (mix, spec, sink) -> {
            throw new AssertionError("réseau appelé sur du silence");
        };
        int n = 400000;
        File f = File.createTempFile("demucs-silent", ".raw");
        RawAudio.Writer[] w = new RawAudio.Writer[3];
        File[] fs = new File[3];
        for (int k = 0; k < 3; k++) {
            fs[k] = k == 0 ? f : File.createTempFile("demucs-silent" + k, ".raw");
            w[k] = new RawAudio.Writer(fs[k], 1);
        }
        new DemucsSeparator(net).separate(new Mem(new float[2 * n]), w, 1e-4f, null);
        for (RawAudio.Writer x : w) x.close();
        try (RawAudio.Reader r = new RawAudio.Reader(f, 1)) {
            assertEquals(n, r.frames());
            float[] y = new float[10];
            r.read(1000, 10, y);
            assertEquals(0, y[0], 1e-9);
        }
        for (File x : fs) x.delete();
    }
}
