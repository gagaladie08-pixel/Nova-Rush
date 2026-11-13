package com.novarush.app.separation;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Beat This! (CPJKU, MIT) : temps et premiers temps de mesure.
 *
 * Port fidèle de tools/ai/ref.py (lui-même vérifié contre l'original en CI) :
 * spectrogramme log-mel (22 050 Hz, fenêtre 1024, pas 441 → 50 images/s,
 * 128 bandes « slaney » de 30 à 11 000 Hz, log1p(1000·x)), découpage en
 * tranches de 1500 images avec 6 images de bord (« keep_first »), puis
 * post-traitement « minimal » (maximums locaux sur ±3 images, logit > 0).
 */
final class BeatThis {
    static final int SR = 22050;
    static final int N_FFT = 1024;
    static final int HOP = 441;
    static final int FPS = 50;
    static final int N_MELS = 128;
    static final int CHUNK = 1500;
    static final int BORDER = 6;

    private BeatThis() {}

    /* ---------------------------- log-mel ---------------------------- */

    static double hzToMel(double f) {
        double fSp = 200.0 / 3;
        if (f < 1000) return f / fSp;
        return 1000 / fSp + Math.log(f / 1000) / (Math.log(6.4) / 27);
    }

    static double melToHz(double m) {
        double fSp = 200.0 / 3;
        double minLogMel = 1000 / fSp;
        if (m < minLogMel) return m * fSp;
        return 1000 * Math.exp((Math.log(6.4) / 27) * (m - minLogMel));
    }

    /** Banc de filtres mel (torchaudio.functional.melscale_fbanks, norm=None) : [freq][mel]. */
    static float[][] melFilterbank(int nFreqs, double fMin, double fMax, int nMels, int sr) {
        double[] all = new double[nFreqs];
        for (int i = 0; i < nFreqs; i++) all[i] = (sr / 2.0) * i / (nFreqs - 1);
        double mMin = hzToMel(fMin), mMax = hzToMel(fMax);
        double[] fPts = new double[nMels + 2];
        for (int i = 0; i < nMels + 2; i++) fPts[i] = melToHz(mMin + (mMax - mMin) * i / (nMels + 1));
        float[][] fb = new float[nFreqs][nMels];
        for (int k = 0; k < nFreqs; k++) {
            for (int m = 0; m < nMels; m++) {
                double down = (all[k] - fPts[m]) / (fPts[m + 1] - fPts[m]);
                double up = (fPts[m + 2] - all[k]) / (fPts[m + 2] - fPts[m + 1]);
                fb[k][m] = (float) Math.max(0, Math.min(down, up));
            }
        }
        return fb;
    }

    /** x mono 22 050 Hz → spectrogramme [images][128] (comme LogMelSpect de Beat This!). */
    static float[][] logMel(float[] x) {
        int n = x.length;
        int pad = N_FFT / 2;
        int frames = 1 + n / HOP;
        float[] w = new float[N_FFT];
        for (int i = 0; i < N_FFT; i++) w[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / N_FFT));
        // normalized="frame_length" de torchaudio = torch.stft(normalized=True) : ÷ √n_fft.
        float norm = (float) (1 / Math.sqrt(N_FFT));
        float[][] fb = melFilterbank(N_FFT / 2 + 1, 30, 11000, N_MELS, SR);
        // Bornes non nulles de chaque filtre, pour un produit rapide.
        int[] lo = new int[N_MELS], hi = new int[N_MELS];
        for (int m = 0; m < N_MELS; m++) {
            lo[m] = -1;
            for (int k = 0; k <= N_FFT / 2; k++) {
                if (fb[k][m] > 0) {
                    if (lo[m] < 0) lo[m] = k;
                    hi[m] = k;
                }
            }
        }
        Fft fft = new Fft(N_FFT);
        float[] re = new float[N_FFT], im = new float[N_FFT], mag = new float[N_FFT / 2 + 1];
        float[][] out = new float[frames][N_MELS];
        for (int f = 0; f < frames; f++) {
            int start = f * HOP - pad;
            for (int i = 0; i < N_FFT; i++) {
                re[i] = sample(x, start + i) * w[i];
                im[i] = 0;
            }
            fft.forward(re, im);
            for (int k = 0; k <= N_FFT / 2; k++) mag[k] = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]) * norm;
            for (int m = 0; m < N_MELS; m++) {
                double s = 0;
                if (lo[m] >= 0) for (int k = lo[m]; k <= hi[m]; k++) s += fb[k][m] * mag[k];
                out[f][m] = (float) Math.log1p(1000 * s);
            }
        }
        return out;
    }

    /** Réflexion aux bords (torch « reflect », sans répéter l'échantillon du bord). */
    static float sample(float[] x, int i) {
        int n = x.length;
        if (n == 1) return x[0];
        while (i < 0 || i >= n) {
            if (i < 0) i = -i;
            if (i >= n) i = 2 * (n - 1) - i;
        }
        return x[i];
    }

    /* ----------------------------- réseau ---------------------------- */

    interface Net {
        /** spect [1500×128] aplati → { beat[1500], downbeat[1500] } (logits). */
        float[][] run(float[] spect) throws Exception;
    }

    /** Débuts des tranches (split_piece, avoid_short_end). */
    static int[] chunkStarts(int frames) {
        List<Integer> s = new ArrayList<>();
        for (int v = -BORDER; v < frames - BORDER; v += CHUNK - 2 * BORDER) s.add(v);
        if (frames > CHUNK - 2 * BORDER) s.set(s.size() - 1, frames - (CHUNK - BORDER));
        int[] r = new int[s.size()];
        for (int i = 0; i < r.length; i++) r[i] = s.get(i);
        return r;
    }

    /** Logits par image pour tout le morceau : { beat[T], downbeat[T] }. */
    static float[][] frames(Net net, float[][] spect, SeparationJob.Listener.Fraction progress) throws Exception {
        int T = spect.length;
        float[] beat = new float[T], down = new float[T];
        java.util.Arrays.fill(beat, -1000f);
        java.util.Arrays.fill(down, -1000f);
        int[] starts = chunkStarts(T);
        float[] chunk = new float[CHUNK * N_MELS];
        // keep_first : à l'envers, pour que les premières tranches l'emportent.
        for (int c = starts.length - 1; c >= 0; c--) {
            int s = starts[c];
            java.util.Arrays.fill(chunk, 0f);
            int a = Math.max(s, 0), b = Math.min(s + CHUNK, T);
            for (int t = a; t < b; t++) System.arraycopy(spect[t], 0, chunk, (t - s) * N_MELS, N_MELS);
            float[][] o = net.run(chunk);
            int ia = Math.max(s + BORDER, 0), ib = Math.min(s + CHUNK - BORDER, T);
            for (int t = ia; t < ib; t++) {
                beat[t] = o[0][t - s];
                down[t] = o[1][t - s];
            }
            if (progress != null && !progress.onProgress((starts.length - c) / (double) starts.length)) {
                throw new UvrSeparator.Cancelled();
            }
        }
        return new float[][] {beat, down};
    }

    /* ------------------------ post-traitement ------------------------ */

    /** Maximums locaux (fenêtre 7) au logit > 0, voisins fusionnés → secondes. */
    static double[] peaks(float[] x) {
        int T = x.length;
        List<Integer> p = new ArrayList<>();
        for (int t = 0; t < T; t++) {
            if (!(x[t] > 0)) continue;
            boolean isMax = true;
            for (int d = -3; d <= 3 && isMax; d++) {
                int u = t + d;
                if (u >= 0 && u < T && x[u] > x[t]) isMax = false;
            }
            if (isMax) p.add(t);
        }
        // Comme deduplicate_peaks : comparaison avec la moyenne courante du groupe.
        List<Double> out = new ArrayList<>();
        if (!p.isEmpty()) {
            double cur = p.get(0);
            int c = 1;
            for (int k = 1; k < p.size(); k++) {
                int v = p.get(k);
                if (v - cur <= 1) {
                    c++;
                    cur += (v - cur) / c;
                } else {
                    out.add(cur / FPS);
                    cur = v;
                    c = 1;
                }
            }
            out.add(cur / FPS);
        }
        double[] r = new double[out.size()];
        for (int k = 0; k < r.length; k++) r[k] = out.get(k);
        return r;
    }

    /** { temps, premiers temps } ; chaque premier temps est recalé sur le temps le plus proche. */
    static double[][] postprocess(float[] beat, float[] down) {
        double[] bt = peaks(beat);
        double[] dt = peaks(down);
        if (bt.length == 0) return new double[][] {bt, new double[0]};
        List<Double> snapped = new ArrayList<>();
        for (double d : dt) {
            double best = bt[0];
            for (double b : bt) if (Math.abs(b - d) < Math.abs(best - d)) best = b;
            if (!snapped.contains(best)) snapped.add(best);
        }
        Collections.sort(snapped);
        double[] r = new double[snapped.size()];
        for (int k = 0; k < r.length; k++) r[k] = snapped.get(k);
        return new double[][] {bt, r};
    }

    /** Réseau Beat This! exécuté par ONNX Runtime. */
    static final class OrtNet implements Net, AutoCloseable {
        private final OrtEnvironment env = OrtEnvironment.getEnvironment();
        private final OrtSession session;
        private final String input;

        OrtNet(String path, int threads) throws Exception {
            OrtSession.SessionOptions so = new OrtSession.SessionOptions();
            so.setIntraOpNumThreads(threads);
            so.setInterOpNumThreads(1);
            so.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            so.addConfigEntry("session.intra_op.allow_spinning", "0");
            session = env.createSession(path, so);
            input = session.getInputNames().iterator().next();
        }

        @Override
        public float[][] run(float[] spect) throws Exception {
            try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(spect), new long[] {1, CHUNK, N_MELS});
                    OrtSession.Result r = session.run(Collections.singletonMap(input, t))) {
                float[][] out = new float[2][];
                for (int k = 0; k < 2; k++) {
                    FloatBuffer fb = ((OnnxTensor) r.get(k)).getFloatBuffer();
                    out[k] = new float[fb.remaining()];
                    fb.get(out[k]);
                }
                return out;
            }
        }

        @Override
        public void close() {
            try {
                session.close();
            } catch (Exception ignored) {
                // rien à libérer de plus
            }
        }
    }
}
