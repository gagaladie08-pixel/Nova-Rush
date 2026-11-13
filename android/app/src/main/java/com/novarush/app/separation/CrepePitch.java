package com.novarush.app.separation;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.util.Collections;

/**
 * CREPE « tiny » (onnxcrepe, MIT) : hauteur de la voix isolée.
 *
 * Port de tools/ai/ref.py (vérifié en CI) : 16 kHz, fenêtres de 1024
 * échantillons toutes les 10 ms (centrées : 512 zéros de chaque côté),
 * 360 cases de 20 cents ; hauteur = moyenne pondérée sur ±4 cases autour du
 * maximum, confiance = probabilité de la case retenue.
 */
final class CrepePitch {
    static final int SR = 16000;
    static final int HOP = 160;
    static final int WIN = 1024;
    static final int BINS = 360;
    static final int BATCH = 256;
    /** En dessous (≈ −66 dB), le lot est considéré muet : hauteur 0, confiance 0. */
    static final double SILENT_RMS = 5e-4;

    private CrepePitch() {}

    interface Net {
        /** frames [n × 1024] aplati → probabilités [n × 360]. */
        float[] run(float[] frames, int n) throws Exception;
    }

    static int frameCount(int samples) {
        return 1 + samples / HOP;
    }

    static double cents(double bin) {
        return 20.0 * bin + 1997.3794084376191;
    }

    /** Une trame de probabilités → { f0 (Hz), confiance }. */
    static float[] decode(float[] prob, int off) {
        int c = 0;
        for (int i = 1; i < BINS; i++) if (prob[off + i] > prob[off + c]) c = i;
        int a = Math.max(0, c - 4), b = Math.min(BINS, c + 5);
        double num = 0, den = 0;
        for (int i = a; i < b; i++) {
            double p = Math.max(0, prob[off + i]);
            num += cents(i) * p;
            den += p;
        }
        double ct = num / Math.max(den, 1e-9);
        return new float[] {(float) (10.0 * Math.pow(2, ct / 1200.0)), prob[off + c]};
    }

    /** x mono 16 kHz → { f0[n], confiance[n] }, une valeur toutes les 10 ms. */
    static float[][] track(Net net, float[] x, SeparationJob.Listener.Fraction progress) throws Exception {
        int n = frameCount(x.length);
        float[] f0 = new float[n], conf = new float[n];
        float[] batch = new float[BATCH * WIN];
        for (int a = 0; a < n; a += BATCH) {
            int m = Math.min(BATCH, n - a);
            // Passage muet (voix absente) : inutile d'appeler le réseau.
            int s0 = Math.max(0, a * HOP - WIN / 2), s1 = Math.min(x.length, (a + m - 1) * HOP + WIN / 2);
            double e = 0;
            for (int i = s0; i < s1; i++) e += x[i] * x[i];
            if (s1 <= s0 || Math.sqrt(e / (s1 - s0)) < SILENT_RMS) {
                if (progress != null && !progress.onProgress((a + m) / (double) n)) throw new UvrSeparator.Cancelled();
                continue;
            }
            for (int k = 0; k < m; k++) {
                int start = (a + k) * HOP - WIN / 2;
                int o = k * WIN;
                for (int i = 0; i < WIN; i++) {
                    int j = start + i;
                    batch[o + i] = j >= 0 && j < x.length ? x[j] : 0f;
                }
            }
            float[] prob = net.run(batch, m);
            for (int k = 0; k < m; k++) {
                float[] d = decode(prob, k * BINS);
                f0[a + k] = d[0];
                conf[a + k] = d[1];
            }
            if (progress != null && !progress.onProgress((a + m) / (double) n)) throw new UvrSeparator.Cancelled();
        }
        return new float[][] {f0, conf};
    }

    static final class OrtNet implements Net, AutoCloseable {
        private final OrtEnvironment env = OrtEnvironment.getEnvironment();
        private final OrtSession session;
        private final String input;

        OrtNet(String path, int threads) throws Exception {
            OrtSession.SessionOptions so = new OrtSession.SessionOptions();
            so.setIntraOpNumThreads(threads);
            so.setInterOpNumThreads(1);
            so.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            // Attente active entre les couches (étape courte) : plus rapide sur téléphone.
            session = env.createSession(path, so);
            input = session.getInputNames().iterator().next();
        }

        @Override
        public float[] run(float[] frames, int n) throws Exception {
            FloatBuffer in = FloatBuffer.wrap(frames, 0, n * WIN);
            try (OnnxTensor t = OnnxTensor.createTensor(env, in, new long[] {n, WIN});
                    OrtSession.Result r = session.run(Collections.singletonMap(input, t))) {
                FloatBuffer fb = ((OnnxTensor) r.get(0)).getFloatBuffer();
                float[] y = new float[fb.remaining()];
                fb.get(y);
                return y;
            }
        }

        @Override
        public void close() {
            try {
                session.close();
            } catch (Exception ignored) {
                // rien de plus
            }
        }
    }
}
