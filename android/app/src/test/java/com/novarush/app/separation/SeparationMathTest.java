package com.novarush.app.separation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Mathématiques de la séparation (FFT, STFT/iSTFT, découpage, rééchantillonnage). */
public class SeparationMathTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static double snrDb(float[] ref, float[] got, int from, int to) {
        double s = 0, e = 0;
        for (int i = from; i < to; i++) {
            s += (double) ref[i] * ref[i];
            double d = ref[i] - got[i];
            e += d * d;
        }
        return 10 * Math.log10(s / Math.max(e, 1e-30));
    }

    @Test
    public void fftMatchesNaiveDft() {
        int[] sizes = {1, 2, 3, 4, 5, 6, 8, 12, 15, 16, 30, 49, 60, 64, 7, 96, 100};
        for (int n : sizes) {
            float[] re = new float[n], im = new float[n];
            for (int i = 0; i < n; i++) {
                re[i] = (float) (Math.sin(i * 1.3) + (i % 7) * 0.1);
                im[i] = (float) (Math.cos(i * 0.7) - 0.05 * i);
            }
            float[] r0 = re.clone(), i0 = im.clone();
            new Fft(n).forward(re, im);
            for (int k = 0; k < n; k++) {
                double sr = 0, si = 0;
                for (int j = 0; j < n; j++) {
                    double a = -2 * Math.PI * j * k / n;
                    sr += r0[j] * Math.cos(a) - i0[j] * Math.sin(a);
                    si += r0[j] * Math.sin(a) + i0[j] * Math.cos(a);
                }
                assertEquals("n=" + n + " k=" + k, sr, re[k], 1e-3 * Math.max(1, n));
                assertEquals("n=" + n + " k=" + k, si, im[k], 1e-3 * Math.max(1, n));
            }
        }
    }

    @Test
    public void fftLargeSizesRoundTrip() {
        for (int n : new int[] {4096, 6144, 7680}) {
            Fft f = new Fft(n);
            float[] re = new float[n], im = new float[n];
            for (int i = 0; i < n; i++) {
                re[i] = (float) Math.sin(i * 0.01 + Math.sin(i * 0.3));
                im[i] = (float) Math.cos(i * 0.02);
            }
            float[] r0 = re.clone(), i0 = im.clone();
            // Bin unique : un cosinus pur de fréquence 17 donne n/2 aux bins 17 et n-17.
            float[] cr = new float[n], ci = new float[n];
            for (int i = 0; i < n; i++) cr[i] = (float) Math.cos(2 * Math.PI * 17 * i / n);
            f.forward(cr, ci);
            assertEquals(n / 2.0, cr[17], 1e-2 * n / 2);
            assertEquals(n / 2.0, cr[n - 17], 1e-2 * n / 2);
            f.forward(re, im);
            f.inverse(re, im);
            assertTrue("aller-retour n=" + n, snrDb(r0, re, 0, n) > 80);
            assertTrue("aller-retour n=" + n, snrDb(i0, im, 0, n) > 80);
        }
    }

    /** Avec un « modèle » identité, séparer = reconstruire le signal (canaux distincts). */
    @Test
    public void identityModelReconstructsBothChannels() throws Exception {
        int nFft = 7680, hop = 1024, dimT = 64, dimF = 3072;
        UvrSeparator.Model identity = (x) -> x.clone();
        UvrSeparator sep = new UvrSeparator(identity, nFft, hop, dimF, dimT, true);
        int len = 44100 * 4 + 123;
        float[] l = new float[len], r = new float[len];
        for (int i = 0; i < len; i++) {
            double t = i / 44100.0;
            l[i] = (float) (0.5 * Math.sin(2 * Math.PI * 220 * t) + 0.2 * Math.sin(2 * Math.PI * 3100 * t + 1));
            r[i] = (float) (0.4 * Math.sin(2 * Math.PI * 330 * t) * Math.sin(2 * Math.PI * 0.5 * t)
                    + 0.1 * Math.cos(2 * Math.PI * 9000 * t));
        }
        float[][] out = sep.processBlock(l, r, len, null);
        double sl = snrDb(l, out[0], 0, len), sr = snrDb(r, out[1], 0, len);
        assertTrue("gauche " + sl, sl > 60);
        assertTrue("droite " + sr, sr > 60);
    }

    /** Découpage en blocs avec marges : raccords invisibles, longueur conservée. */
    @Test
    public void blockStreamingIsSeamless() throws Exception {
        int nFft = 4096, hop = 1024, dimT = 32, dimF = 2048;
        // « Modèle » : garde la moitié du spectre → voix = mix / 2.
        UvrSeparator.Model half = (x) -> {
            float[] y = x.clone();
            for (int i = 0; i < y.length; i++) y[i] *= 0.5f;
            return y;
        };
        UvrSeparator sep = new UvrSeparator(half, nFft, hop, dimF, dimT, true);
        int n = 44100 * 7 + 777;
        File src = tmp.newFile("src.raw");
        float[] mono = new float[n];
        try (RawAudio.Writer w = new RawAudio.Writer(src, 2)) {
            float[] fr = new float[2];
            for (int i = 0; i < n; i++) {
                double t = i / 44100.0;
                fr[0] = (float) (0.6 * Math.sin(2 * Math.PI * 440 * t) + 0.1 * Math.sin(2 * Math.PI * 5000 * t));
                fr[1] = (float) (0.3 * Math.sin(2 * Math.PI * 660 * t));
                mono[i] = 0.5f * (fr[0] + fr[1]);
                w.write(fr, 1);
            }
        }
        File voc = tmp.newFile("v.raw"), acc = tmp.newFile("a.raw");
        try (RawAudio.Reader r = new RawAudio.Reader(src, 2);
                RawAudio.Writer wv = new RawAudio.Writer(voc, 1);
                RawAudio.Writer wa = new RawAudio.Writer(acc, 1)) {
            assertEquals(n, r.frames());
            // Petits blocs (2 s + 0,5 s de marge) pour tester plusieurs raccords.
            SeparationJob.separateStream(r, sep, 88200, 22050, wv, wa, (f) -> true);
            assertEquals(n, wv.frames);
        }
        float[] v = new float[n], a = new float[n];
        try (RawAudio.Reader rv = new RawAudio.Reader(voc, 1); RawAudio.Reader ra = new RawAudio.Reader(acc, 1)) {
            assertEquals(n, rv.frames());
            rv.read(0, n, v);
            ra.read(0, n, a);
        }
        float[] halfMix = new float[n];
        for (int i = 0; i < n; i++) halfMix[i] = mono[i] * 0.5f;
        assertTrue("voix", snrDb(halfMix, v, 0, n) > 55);
        assertTrue("accompagnement", snrDb(halfMix, a, 0, n) > 55);
    }

    @Test
    public void resamplerKeepsTonesClean() throws Exception {
        int[][] cases = {{48000, 44100}, {44100, 22050}, {22050, 44100}, {32000, 44100}};
        for (int[] c : cases) {
            int in = c[0], out = c[1];
            int n = in * 2;
            final float[] x = new float[n * 2];
            for (int i = 0; i < n; i++) {
                double t = (double) i / in;
                x[2 * i] = (float) Math.sin(2 * Math.PI * 1000 * t);
                x[2 * i + 1] = (float) (0.5 * Math.sin(2 * Math.PI * 3000 * t));
            }
            Resampler.Source src = new Resampler.Source() {
                public long frames() {
                    return x.length / 2;
                }

                public int channels() {
                    return 2;
                }

                public void read(long start, int count, float[] dst) {
                    for (int i = 0; i < count; i++) {
                        long j = start + i;
                        boolean ok = j >= 0 && j < x.length / 2;
                        dst[2 * i] = ok ? x[(int) (2 * j)] : 0;
                        dst[2 * i + 1] = ok ? x[(int) (2 * j + 1)] : 0;
                    }
                }
            };
            Resampler rs = new Resampler(in, out);
            final int m = (int) rs.outFrames(n);
            final float[] y = new float[m * 2];
            final int[] pos = {0};
            rs.run(src, (data, frames) -> {
                System.arraycopy(data, 0, y, pos[0] * 2, frames * 2);
                pos[0] += frames;
            }, null);
            assertEquals(m, pos[0]);
            float[] refL = new float[m], gotL = new float[m], refR = new float[m], gotR = new float[m];
            for (int i = 0; i < m; i++) {
                double t = (double) i / out;
                refL[i] = (float) Math.sin(2 * Math.PI * 1000 * t);
                refR[i] = (float) (0.5 * Math.sin(2 * Math.PI * 3000 * t));
                gotL[i] = y[2 * i];
                gotR[i] = y[2 * i + 1];
            }
            int edge = out / 20; // on ignore les bords (signal coupé)
            double sl = snrDb(refL, gotL, edge, m - edge), sr = snrDb(refR, gotR, edge, m - edge);
            assertTrue(in + "→" + out + " gauche " + sl, sl > 60);
            assertTrue(in + "→" + out + " droite " + sr, sr > 60);
        }
    }
}
