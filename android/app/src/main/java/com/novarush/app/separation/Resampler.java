package com.novarush.app.separation;

import java.io.IOException;

/**
 * Rééchantillonnage par sinus cardinal fenêtré (Blackman), table sur-échantillonnée.
 * Fonctionne par blocs sur une source à accès aléatoire (fichier), sans état.
 */
final class Resampler {

    /** Source de trames entrelacées (zéros hors limites). */
    interface Source {
        long frames();

        int channels();

        /** Lit les trames [start, start+count) dans dst (entrelacé), zéros hors du signal. */
        void read(long start, int count, float[] dst) throws IOException;
    }

    /** Reçoit des trames entrelacées. */
    interface Sink {
        void write(float[] data, int frames) throws IOException;
    }

    private static final int ZEROS = 24; // passages par zéro de chaque côté
    private static final int OVERSAMPLE = 512;

    private final double ratio; // trames d'entrée par trame de sortie
    private final double cutoff; // fréquence de coupure relative (Nyquist d'entrée = 1)
    private final int half; // demi-largeur du noyau (trames d'entrée)
    private final float[] table;

    Resampler(int inRate, int outRate) {
        ratio = (double) inRate / outRate;
        cutoff = 0.97 * Math.min(1.0, (double) outRate / inRate);
        half = (int) Math.ceil(ZEROS / cutoff);
        int n = half * OVERSAMPLE + 2;
        table = new float[n];
        for (int i = 0; i < n; i++) {
            double x = (double) i / OVERSAMPLE; // en trames d'entrée
            double u = x * cutoff;
            double sinc = u == 0 ? 1 : Math.sin(Math.PI * u) / (Math.PI * u);
            double w = x >= half ? 0 : 0.42 + 0.5 * Math.cos(Math.PI * x / half) + 0.08 * Math.cos(2 * Math.PI * x / half);
            table[i] = (float) (cutoff * sinc * w);
        }
    }

    long outFrames(long inFrames) {
        return (long) Math.floor(inFrames / ratio);
    }

    private float kernel(double x) {
        x = Math.abs(x);
        if (x >= half) return 0;
        double p = x * OVERSAMPLE;
        int i = (int) p;
        float f = (float) (p - i);
        return table[i] + (table[i + 1] - table[i]) * f;
    }

    /** Rééchantillonne toute la source vers sink, par blocs. */
    void run(Source src, Sink sink, java.util.function.DoublePredicate progress) throws IOException {
        final int ch = src.channels();
        final long total = outFrames(src.frames());
        final int block = 32768;
        float[] out = new float[block * ch];
        float[] in = new float[0];
        float[] weights = new float[2 * half + 2];
        for (long o0 = 0; o0 < total; o0 += block) {
            int count = (int) Math.min(block, total - o0);
            long i0 = (long) Math.floor(o0 * ratio) - half;
            long i1 = (long) Math.ceil((o0 + count) * ratio) + half + 1;
            int inCount = (int) (i1 - i0);
            if (in.length < inCount * ch) in = new float[inCount * ch];
            src.read(i0, inCount, in);
            for (int o = 0; o < count; o++) {
                double pos = (o0 + o) * ratio;
                long first = (long) Math.ceil(pos - half);
                long last = (long) Math.floor(pos + half);
                int taps = (int) (last - first + 1);
                if (weights.length < taps) weights = new float[taps];
                for (int k = 0; k < taps; k++) weights[k] = kernel(pos - (first + k));
                int base = (int) (first - i0) * ch;
                for (int c = 0; c < ch; c++) {
                    float acc = 0;
                    for (int k = 0, idx = base + c; k < taps; k++, idx += ch) acc += in[idx] * weights[k];
                    out[o * ch + c] = acc;
                }
            }
            sink.write(out, count);
            if (progress != null && !progress.test((double) (o0 + count) / total)) throw new UvrSeparator.Cancelled();
        }
    }
}
