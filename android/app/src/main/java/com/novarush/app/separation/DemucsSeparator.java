package com.novarush.app.separation;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Demucs v4 (htdemucs, MIT) : batterie, basse et autres instruments de
 * l'accompagnement (voix déjà retirée par UVR).
 *
 * Portage de tools/ai/ref.py (demucs_spec / demucs_ispec, vérifiés en CI
 * contre PyTorch) et de tools/ai/demucs_stream.py (segments de 7,8 s,
 * recouvrement 0,25, poids triangulaires, écrits au fil de l'eau : le morceau
 * n'est jamais entièrement en mémoire). Sorties mono.
 */
final class DemucsSeparator {
    static final int SR = 44100;
    static final int SEGMENT = 343980;
    static final int NFFT = 4096;
    static final int HL = 1024;
    static final int BINS = NFFT / 2;
    static final int LE = (SEGMENT + HL - 1) / HL; // 336 images
    static final int PAD = HL / 2 * 3;
    static final int SOURCES = 4; // drums, bass, other, vocals
    /** Sources gardées : batterie, basse, autres. */
    static final int KEEP = 3;
    static final int STRIDE = (int) (0.75 * SEGMENT);
    private static final float NORM = (float) Math.sqrt(NFFT);

    /** Réponse du réseau : spectre (1,S,4,BINS,LE) et onde (1,S,2,SEGMENT), en lecture absolue. */
    interface Sink {
        void accept(FloatBuffer spec, FloatBuffer wave) throws Exception;
    }

    interface Net {
        /** mix (1,2,SEGMENT) et spectre CaC (1,4,BINS,LE) → appelle `out` avec les sorties. */
        void run(float[] mix, float[] spec, Sink out) throws Exception;
    }

    interface Progress {
        boolean onProgress(double fraction);

        /** true : attendre avant le segment suivant (une partie est en cours). */
        default boolean paused() {
            return false;
        }
    }

    private final Net net;
    private final Fft fft = new Fft(NFFT);
    private final float[] window = new float[NFFT];
    private final float[] zr = new float[NFFT];
    private final float[] zi = new float[NFFT];
    /** Somme des carrés de la fenêtre sur toutes les images de l'iSTFT. */
    private final float[] den;
    private final float[] xpL = new float[LE * HL + 2 * PAD];
    private final float[] xpR = new float[LE * HL + 2 * PAD];
    private final float[] specIn = new float[4 * BINS * LE];
    private final float[] mixIn = new float[2 * SEGMENT];
    private final float[] accL;
    private final float[] accR;

    DemucsSeparator(Net net) {
        this.net = net;
        for (int i = 0; i < NFFT; i++) window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / NFFT));
        int frames = LE + 4;
        int n = NFFT + HL * (frames - 1);
        den = new float[n];
        for (int f = 0; f < frames; f++) for (int i = 0; i < NFFT; i++) den[f * HL + i] += window[i] * window[i];
        accL = new float[n];
        accR = new float[n];
    }

    private static int reflect(int i, int n) {
        if (i < 0) i = -i;
        if (i >= n) i = 2 * (n - 1) - i;
        return i;
    }

    /** Spectre CaC [reL, imL, reR, imR] × (BINS, LE) d'un segment stéréo (standalone_spec). */
    void spec(float[] l, float[] r, float[] out) {
        final int L = SEGMENT;
        for (int j = 0; j < xpL.length; j++) {
            int i = reflect(j - PAD, L);
            xpL[j] = l[i];
            xpR[j] = r[i];
        }
        final int plane = BINS * LE;
        for (int t = 0; t < LE; t++) {
            int base = t * HL;
            for (int i = 0; i < NFFT; i++) {
                zr[i] = xpL[base + i] * window[i];
                zi[i] = xpR[base + i] * window[i];
            }
            fft.forward(zr, zi);
            for (int k = 0; k < BINS; k++) {
                int j = k == 0 ? 0 : NFFT - k;
                int idx = k * LE + t;
                out[idx] = 0.5f * (zr[k] + zr[j]) / NORM;
                out[plane + idx] = 0.5f * (zi[k] - zi[j]) / NORM;
                out[2 * plane + idx] = 0.5f * (zi[k] + zi[j]) / NORM;
                out[3 * plane + idx] = -0.5f * (zr[k] - zr[j]) / NORM;
            }
        }
    }

    /**
     * iSTFT (standalone_ispec) de la source `s` lue dans `spec` (1,S,4,BINS,LE) :
     * ajoute 0,5·(gauche + droite) × `gain` à out[0..SEGMENT).
     */
    void ispecMonoAdd(FloatBuffer spec, int s, float[] out) {
        final int frames = LE + 4;
        final int n = den.length;
        java.util.Arrays.fill(accL, 0f);
        java.util.Arrays.fill(accR, 0f);
        final int sBase = s * 4 * BINS * LE;
        final int plane = BINS * LE;
        for (int f = 2; f < frames - 2; f++) {
            int t = f - 2;
            java.util.Arrays.fill(zr, 0f);
            java.util.Arrays.fill(zi, 0f);
            for (int k = 0; k < BINS; k++) {
                int idx = sBase + k * LE + t;
                float lr = spec.get(idx);
                float li = spec.get(idx + plane);
                float rr = spec.get(idx + 2 * plane);
                float ri = spec.get(idx + 3 * plane);
                if (k == 0) {
                    li = 0; // irfft ignore la partie imaginaire du continu
                    ri = 0;
                }
                zr[k] = lr - ri;
                zi[k] = li + rr;
                if (k != 0) {
                    int j = NFFT - k;
                    zr[j] = lr + ri;
                    zi[j] = -li + rr;
                }
            }
            fft.inverse(zr, zi);
            int base = f * HL;
            for (int i = 0; i < NFFT; i++) {
                float w = window[i] * NORM;
                accL[base + i] += zr[i] * w;
                accR[base + i] += zi[i] * w;
            }
        }
        final int start = NFFT / 2 + PAD;
        for (int i = 0; i < SEGMENT; i++) {
            int j = start + i;
            float d = j < n && den[j] > 1e-11f ? den[j] : 1f;
            out[i] += 0.5f * (accL[j] + accR[j]) / d;
        }
    }

    /** Un segment normalisé (gauche, droite) → batterie, basse, autres en mono. */
    float[][] segment(float[] l, float[] r) throws Exception {
        spec(l, r, specIn);
        System.arraycopy(l, 0, mixIn, 0, SEGMENT);
        System.arraycopy(r, 0, mixIn, SEGMENT, SEGMENT);
        final float[][] y = new float[KEEP][SEGMENT];
        net.run(mixIn, specIn, (spec, wave) -> {
            for (int s = 0; s < KEEP; s++) {
                int a = (s * 2) * SEGMENT;
                int b = (s * 2 + 1) * SEGMENT;
                float[] ys = y[s];
                for (int i = 0; i < SEGMENT; i++) ys[i] = 0.5f * (wave.get(a + i) + wave.get(b + i));
                ispecMonoAdd(spec, s, ys);
            }
        });
        return y;
    }

    static float[] weights() {
        float[] w = new float[SEGMENT];
        int half = SEGMENT / 2;
        for (int i = 0; i < half; i++) w[i] = i + 1;
        for (int i = half; i < SEGMENT; i++) w[i] = SEGMENT - i;
        float max = 0;
        for (float v : w) max = Math.max(max, v);
        for (int i = 0; i < SEGMENT; i++) w[i] /= max;
        return w;
    }

    /**
     * Sépare `src` (stéréo 44,1 kHz) et écrit batterie, basse, autres (mono)
     * dans outs[0..2]. Les segments presque muets (RMS < silentRms) ne passent
     * pas par le réseau.
     */
    void separate(Resampler.Source src, RawAudio.Writer[] outs, float silentRms, Progress progress) throws Exception {
        final long n = src.frames();
        if (n <= 0) return;
        // Normalisation globale (moyenne et écart type du mono), comme Demucs.
        double sum = 0, sum2 = 0;
        final int block = 1 << 16;
        float[] buf = new float[block * 2];
        for (long a = 0; a < n; a += block) {
            int len = (int) Math.min(block, n - a);
            src.read(a, len, buf);
            for (int i = 0; i < len; i++) {
                double m = 0.5 * (buf[2 * i] + buf[2 * i + 1]);
                sum += m;
                sum2 += m * m;
            }
        }
        final double mu = sum / n;
        final double sd = Math.sqrt(Math.max(0, sum2 / n - mu * mu)) + 1e-8;
        final float[] w = weights();
        final double[][] acc = new double[KEEP][SEGMENT];
        final double[] sw = new double[SEGMENT];
        final float[] inter = new float[2 * SEGMENT];
        final float[] l = new float[SEGMENT];
        final float[] r = new float[SEGMENT];
        final long segments = (n + STRIDE - 1) / STRIDE;
        long index = 0;
        for (long off = 0; off < n; off += STRIDE) {
            while (progress != null && progress.paused()) {
                if (!progress.onProgress((double) index / segments)) throw new UvrSeparator.Cancelled();
                Thread.sleep(1000);
            }
            final int length = (int) Math.min(SEGMENT, n - off);
            final int delta = SEGMENT - length;
            final long start = off - delta / 2;
            src.read(start, SEGMENT, inter);
            double e = 0;
            int valid = 0;
            for (int i = 0; i < SEGMENT; i++) {
                long p = start + i;
                if (p < 0 || p >= n) {
                    l[i] = 0;
                    r[i] = 0;
                    continue;
                }
                float a = inter[2 * i], b = inter[2 * i + 1];
                e += a * a + b * b;
                valid += 2;
                l[i] = (float) ((a - mu) / sd);
                r[i] = (float) ((b - mu) / sd);
            }
            boolean silent = valid == 0 || Math.sqrt(e / valid) < silentRms;
            float[][] y = silent ? null : segment(l, r);
            final int d0 = delta / 2;
            for (int s = 0; s < KEEP; s++) {
                double[] as = acc[s];
                if (y != null) {
                    float[] ys = y[s];
                    for (int i = 0; i < length; i++) as[i] += w[i] * ys[d0 + i];
                }
            }
            for (int i = 0; i < length; i++) sw[i] += w[i];
            boolean last = off + STRIDE >= n;
            int emit = last ? length : STRIDE;
            for (int s = 0; s < KEEP; s++) {
                double[] as = acc[s];
                RawAudio.Writer o = outs[s];
                for (int i = 0; i < emit; i++) o.put((float) (as[i] / sw[i] * sd + mu));
                o.addFrames(emit);
                System.arraycopy(as, STRIDE, as, 0, SEGMENT - STRIDE);
                java.util.Arrays.fill(as, SEGMENT - STRIDE, SEGMENT, 0);
            }
            System.arraycopy(sw, STRIDE, sw, 0, SEGMENT - STRIDE);
            java.util.Arrays.fill(sw, SEGMENT - STRIDE, SEGMENT, 0);
            index++;
            if (progress != null && !progress.onProgress((double) index / segments)) throw new UvrSeparator.Cancelled();
            if (last) break;
        }
    }

    /** Réseau ONNX ; sorties écrites directement dans des tampons natifs réutilisés (pas de copie). */
    static final class OrtNet implements Net, AutoCloseable {
        private final OrtEnvironment env = OrtEnvironment.getEnvironment();
        private final OrtSession session;
        private final String mixName;
        private final String specName;
        private final String outSpecName;
        private final String outWaveName;
        private final FloatBuffer specOut;
        private final FloatBuffer waveOut;
        private final OnnxTensor specOutT;
        private final OnnxTensor waveOutT;

        OrtNet(String path, int threads) throws Exception {
            OrtSession.SessionOptions so = new OrtSession.SessionOptions();
            so.setIntraOpNumThreads(threads);
            so.setInterOpNumThreads(1);
            so.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            so.addConfigEntry("session.intra_op.allow_spinning", "0");
            session = env.createSession(path, so);
            Iterator<String> in = session.getInputNames().iterator();
            String a = in.next(), b = in.next();
            mixName = session.getInputNames().contains("mix") ? "mix" : a;
            specName = session.getInputNames().contains("spec") ? "spec" : b;
            Iterator<String> out = session.getOutputNames().iterator();
            String c = out.next(), d = out.next();
            outSpecName = session.getOutputNames().contains("out_spec") ? "out_spec" : c;
            outWaveName = session.getOutputNames().contains("out_wave") ? "out_wave" : d;
            specOut = direct(SOURCES * 4 * BINS * LE);
            waveOut = direct(SOURCES * 2 * SEGMENT);
            specOutT = OnnxTensor.createTensor(env, specOut, new long[] {1, SOURCES, 4, BINS, LE});
            waveOutT = OnnxTensor.createTensor(env, waveOut, new long[] {1, SOURCES, 2, SEGMENT});
        }

        private static FloatBuffer direct(int floats) {
            return ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        }

        @Override
        public void run(float[] mix, float[] spec, Sink sink) throws Exception {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            Map<String, OnnxValue> pinned = new HashMap<>();
            pinned.put(outSpecName, specOutT);
            pinned.put(outWaveName, waveOutT);
            try (OnnxTensor m = OnnxTensor.createTensor(env, FloatBuffer.wrap(mix), new long[] {1, 2, SEGMENT});
                    OnnxTensor s = OnnxTensor.createTensor(env, FloatBuffer.wrap(spec), new long[] {1, 4, BINS, LE})) {
                inputs.put(mixName, m);
                inputs.put(specName, s);
                try (OrtSession.Result r = session.run(inputs, pinned)) {
                    sink.accept(specOut, waveOut);
                }
            }
        }

        @Override
        public void close() {
            try {
                specOutT.close();
                waveOutT.close();
                session.close();
            } catch (Exception ignored) {
                // rien de plus
            }
        }
    }
}
