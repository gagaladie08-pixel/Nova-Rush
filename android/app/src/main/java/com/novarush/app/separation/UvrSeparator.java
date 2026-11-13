package com.novarush.app.separation;

/**
 * Séparation voix / accompagnement avec un modèle UVR-MDX-Net.
 *
 * Portage fidèle de sherpa-onnx (offline-source-separation-uvr-impl.h,
 * Apache 2.0) et de la STFT de kaldi-native-fbank : fenêtre de Hann
 * périodique, remplissage « reflect » (center), iSTFT normalisée par la
 * somme des carrés de la fenêtre. Référence Python : tools/separation/sep.py.
 *
 * Les deux canaux sont transformés ensemble (une FFT complexe pour deux
 * signaux réels). Le modèle est appelé segment par segment (lot de 1) pour
 * limiter la mémoire sur téléphone.
 */
final class UvrSeparator {

    /** Appel du réseau : entrée et sortie de forme (1, 4, dimF, dimT). */
    interface Model {
        float[] run(float[] input) throws Exception;
    }

    /** Progression d'un bloc (0..1) ; renvoie false pour annuler. */
    interface Progress {
        boolean onProgress(double fraction);
    }

    static final class Cancelled extends java.io.IOException {
        Cancelled() {
            super("annulé");
        }
    }

    final int nFft;
    final int hop;
    final int dimF;
    final int dimT;
    final boolean center;
    private final Model model;
    private final Fft fft;
    private final float[] window;
    private final float[] zr;
    private final float[] zi;

    UvrSeparator(Model model, int nFft, int hop, int dimF, int dimT, boolean center) {
        if (dimF > nFft / 2 + 1) throw new IllegalArgumentException("dim_f > n_fft/2+1");
        this.model = model;
        this.nFft = nFft;
        this.hop = hop;
        this.dimF = dimF;
        this.dimT = dimT;
        this.center = center;
        this.fft = new Fft(nFft);
        this.window = new float[nFft];
        for (int i = 0; i < nFft; i++) window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / nFft));
        this.zr = new float[nFft];
        this.zi = new float[nFft];
    }

    /** Taille d'un segment du modèle (échantillons). */
    int chunkSize() {
        return hop * (dimT - 1);
    }

    /** Échantillons utiles produits par segment. */
    int genSize() {
        return chunkSize() - 2 * (nFft / 2);
    }

    /**
     * Sépare un bloc stéréo (≈ 17 s). Renvoie la piste principale du modèle
     * (la voix pour Voc_FT), de même longueur.
     */
    float[][] processBlock(float[] left, float[] right, int len, Progress progress) throws Exception {
        final int trim = nFft / 2;
        final int chunk = chunkSize();
        final int gen = genSize();
        final int pad = gen - len % gen;
        final int total = trim + len + pad + trim;
        float[] pl = new float[total];
        float[] pr = new float[total];
        System.arraycopy(left, 0, pl, trim, len);
        System.arraycopy(right, 0, pr, trim, len);
        float[] outL = new float[len + pad];
        float[] outR = new float[len + pad];
        float[] input = new float[4 * dimF * dimT];
        int segments = (len + pad) / gen;
        int done = 0;
        for (int i = 0; i < len + pad; i += gen) {
            stft(pl, pr, i, chunk, input);
            float[] y = model.run(input);
            if (y.length < 4 * dimF * dimT) throw new IllegalStateException("sortie du modèle trop courte");
            istftInto(y, outL, outR, i, gen, trim);
            done++;
            if (progress != null && !progress.onProgress((double) done / segments)) throw new Cancelled();
        }
        float[] l = new float[len];
        float[] r = new float[len];
        System.arraycopy(outL, 0, l, 0, len);
        System.arraycopy(outR, 0, r, 0, len);
        return new float[][] {l, r};
    }

    /** Échantillon de x[off .. off+n) avec remplissage « reflect » si center. */
    private float sampleAt(float[] x, int off, int n, int idx) {
        if (center) {
            idx -= nFft / 2;
            if (idx < 0) idx = -idx;
            if (idx >= n) idx = 2 * (n - 1) - idx;
        }
        return x[off + idx];
    }

    /** STFT des deux canaux du segment x[off .. off+n) → entrée du modèle. */
    void stft(float[] l, float[] r, int off, int n, float[] input) {
        final int frames = center ? 1 + n / hop : 1 + (n - nFft) / hop;
        if (frames != dimT) throw new IllegalStateException("frames " + frames + " != dim_t " + dimT);
        final int plane = dimF * dimT;
        for (int t = 0; t < frames; t++) {
            int base = t * hop;
            for (int i = 0; i < nFft; i++) {
                zr[i] = sampleAt(l, off, n, base + i) * window[i];
                zi[i] = sampleAt(r, off, n, base + i) * window[i];
            }
            fft.forward(zr, zi);
            for (int k = 0; k < dimF; k++) {
                int j = k == 0 ? 0 : nFft - k;
                float lr = 0.5f * (zr[k] + zr[j]);
                float li = 0.5f * (zi[k] - zi[j]);
                float rr = 0.5f * (zi[k] + zi[j]);
                float ri = -0.5f * (zr[k] - zr[j]);
                int idx = k * dimT + t;
                input[idx] = lr;
                input[plane + idx] = li;
                input[2 * plane + idx] = rr;
                input[3 * plane + idx] = ri;
            }
        }
    }

    /**
     * iSTFT de la sortie du modèle, recadrage (center puis trim), et copie des
     * `gen` échantillons utiles dans out[pos ..].
     */
    void istftInto(float[] y, float[] outL, float[] outR, int pos, int gen, int trim) {
        final int plane = dimF * dimT;
        final int frames = dimT;
        final int n = nFft + (frames - 1) * hop;
        float[] accL = new float[n];
        float[] accR = new float[n];
        float[] den = new float[n];
        final int half = nFft / 2;
        for (int t = 0; t < frames; t++) {
            // Spectre hermitien complet de Z = XL + i·XR (bins ≥ dimF nuls).
            for (int k = 0; k < nFft; k++) {
                zr[k] = 0;
                zi[k] = 0;
            }
            for (int k = 0; k < dimF && k <= half; k++) {
                int idx = k * dimT + t;
                float lr = y[idx];
                float li = y[plane + idx];
                float rr = y[2 * plane + idx];
                float ri = y[3 * plane + idx];
                if (k == 0 || k == half) {
                    li = 0; // comme kaldi-native-fbank (rfft compacte)
                    ri = 0;
                }
                // Z_k = XL_k + i·XR_k
                zr[k] = lr - ri;
                zi[k] = li + rr;
                if (k != 0 && k != half) {
                    // Z_{N-k} = conj(XL_k) + i·conj(XR_k)
                    int j = nFft - k;
                    zr[j] = lr + ri;
                    zi[j] = -li + rr;
                }
            }
            fft.inverse(zr, zi);
            int base = t * hop;
            for (int i = 0; i < nFft; i++) {
                float w = window[i];
                accL[base + i] += zr[i] * w;
                accR[base + i] += zi[i] * w;
                den[base + i] += w * w;
            }
        }
        for (int i = 0; i < n; i++) {
            if (den[i] != 0) {
                accL[i] /= den[i];
                accR[i] /= den[i];
            }
        }
        // center : on retire n_fft/2 de chaque côté (→ chunkSize), puis trim.
        int start = (center ? half : 0) + trim;
        System.arraycopy(accL, start, outL, pos, gen);
        System.arraycopy(accR, start, outR, pos, gen);
    }
}
