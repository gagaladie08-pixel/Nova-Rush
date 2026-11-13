package com.novarush.app.separation;

import android.util.Log;
import java.io.File;

/**
 * Une séparation complète : décodage → 44,1 kHz stéréo → séparation par blocs
 * (15 s + 1 s de marge, comme sherpa-onnx) → voix et accompagnement mono
 * 22 050 Hz (float32) lus ensuite par l'analyse de l'appli.
 */
final class SeparationJob {
    static final int SR = 44100;
    static final int OUT_SR = 22050;

    interface Listener {
        /** Progression globale 0..1 ; renvoie false pour annuler. */
        boolean onProgress(double fraction, String stage);

        /** Progression simple 0..1 ; renvoie false pour annuler. */
        interface Fraction {
            boolean onProgress(double fraction);
        }
    }

    private SeparationJob() {}

    /**
     * Sépare un flux stéréo par blocs de `chunk` échantillons (+ `margin` de
     * chaque côté, retirée ensuite), comme sherpa-onnx. Écrit la voix et
     * l'accompagnement (mix − voix) en mono.
     */
    static void separateStream(Resampler.Source r, UvrSeparator sep, int chunk, int margin,
            RawAudio.Writer wv, RawAudio.Writer wa, Listener.Fraction progress) throws Exception {
        final long n = r.frames();
        if (n < chunk) chunk = (int) n;
        margin = Math.min(margin, chunk);
        final long blocks = (n + chunk - 1) / chunk;
        long skip = 0;
        int index = 0;
        while (skip < n) {
            long a = Math.max(0, skip - margin);
            long b = Math.min(skip + chunk + margin, n);
            int len = (int) (b - a);
            float[] inter = new float[len * 2];
            r.read(a, len, inter);
            float[] left = new float[len];
            float[] right = new float[len];
            for (int i = 0; i < len; i++) {
                left[i] = inter[2 * i];
                right[i] = inter[2 * i + 1];
            }
            final int blockIndex = index;
            float[][] v = sep.processBlock(left, right, len, (f) -> progress.onProgress((blockIndex + f) / blocks));
            boolean last = b == n;
            int lo = a == 0 ? 0 : margin;
            int hi = last ? len : len - margin;
            for (int i = lo; i < hi; i++) {
                float vm = 0.5f * (v[0][i] + v[1][i]);
                float mm = 0.5f * (left[i] + right[i]);
                wv.put(vm);
                wa.put(mm - vm);
            }
            wv.addFrames(hi - lo);
            wa.addFrames(hi - lo);
            index++;
            if (last) break;
            skip += chunk;
        }
    }

    /** Durée maximale analysée par Beat This! (au-delà : rythme classique). */
    static final int MAX_BEAT_SECONDS = 30 * 60;

    /**
     * Temps et premiers temps (Beat This!) sur le mix mono 22 050 Hz.
     * Écrit beats.f32 = [nb, nd, temps…, premiers temps…] (secondes, float32).
     */
    static void computeBeats(File pcm44, File dir, BeatThis.Net net, Listener.Fraction progress) throws Exception {
        File mono44 = new File(dir, "mix44.raw");
        File mono22 = new File(dir, "mix22.raw");
        try {
            try (RawAudio.Reader r = new RawAudio.Reader(pcm44, 2); RawAudio.Writer w = new RawAudio.Writer(mono44, 1)) {
                long n = r.frames();
                if (n > (long) MAX_BEAT_SECONDS * SR) return;
                int block = 1 << 16;
                float[] inter = new float[block * 2];
                float[] mono = new float[block];
                for (long a = 0; a < n; a += block) {
                    int len = (int) Math.min(block, n - a);
                    r.read(a, len, inter);
                    for (int i = 0; i < len; i++) mono[i] = 0.5f * (inter[2 * i] + inter[2 * i + 1]);
                    w.write(mono, len);
                }
            }
            try (RawAudio.Reader r = new RawAudio.Reader(mono44, 1); RawAudio.Writer w = new RawAudio.Writer(mono22, 1)) {
                new Resampler(SR, BeatThis.SR).run(r, w, (f) -> progress.onProgress(0.15 * f));
            }
            mono44.delete();
            float[] x;
            try (RawAudio.Reader r = new RawAudio.Reader(mono22, 1)) {
                x = new float[(int) r.frames()];
                r.read(0, x.length, x);
            }
            mono22.delete();
            if (!progress.onProgress(0.2)) throw new UvrSeparator.Cancelled();
            float[][] spect = BeatThis.logMel(x);
            x = null;
            if (!progress.onProgress(0.35)) throw new UvrSeparator.Cancelled();
            float[][] logits = BeatThis.frames(net, spect, (f) -> progress.onProgress(0.35 + 0.65 * f));
            double[][] bd = BeatThis.postprocess(logits[0], logits[1]);
            File tmp = new File(dir, "beats.f32.tmp");
            try (RawAudio.Writer w = new RawAudio.Writer(tmp, 1)) {
                w.put(bd[0].length);
                w.put(bd[1].length);
                for (double t : bd[0]) w.put((float) t);
                for (double t : bd[1]) w.put((float) t);
            }
            if (!tmp.renameTo(new File(dir, "beats.f32"))) throw new IllegalStateException("renommage impossible");
        } finally {
            mono44.delete();
            mono22.delete();
        }
    }

    static void run(File dir, UvrSeparator sep, Listener listener) throws Exception {
        run(dir, sep, null, listener);
    }

    static void run(File dir, UvrSeparator sep, BeatThis.Net beatNet, Listener listener) throws Exception {
        File input = new File(dir, "input.bin");
        File src = new File(dir, "decoded.raw");
        File pcm = new File(dir, "pcm44.raw");
        File voc44 = new File(dir, "voc44.raw");
        File acc44 = new File(dir, "acc44.raw");
        try {
            // 1. Décodage (0 → 6 %)
            AudioDecoder.Result dec = AudioDecoder.decode(input, src, (f) -> listener.onProgress(0.06 * f, "decode"));
            // 2. 44,1 kHz (6 → 9 %)
            if (dec.sampleRate != SR) {
                try (RawAudio.Reader r = new RawAudio.Reader(src, 2); RawAudio.Writer w = new RawAudio.Writer(pcm, 2)) {
                    new Resampler(dec.sampleRate, SR).run(r, w, (f) -> listener.onProgress(0.06 + 0.03 * f, "decode"));
                }
                src.delete();
            } else if (!src.renameTo(pcm)) {
                throw new IllegalStateException("renommage impossible");
            }
            // 3. Rythme par l'IA (9 → 12 %) — facultatif : en cas d'échec, rythme classique.
            double sepFrom = 0.09;
            if (beatNet != null) {
                try {
                    computeBeats(pcm, dir, beatNet, (f) -> listener.onProgress(0.09 + 0.03 * f, "beats"));
                } catch (UvrSeparator.Cancelled c) {
                    throw c;
                } catch (Throwable t) {
                    Log.w("NovaRushSep", "Beat This! : " + t, t);
                    new File(dir, "beats.f32").delete();
                }
                sepFrom = 0.12;
            }
            // 4. Séparation (→ 96 %)
            final double from = sepFrom;
            try (RawAudio.Reader r = new RawAudio.Reader(pcm, 2);
                    RawAudio.Writer wv = new RawAudio.Writer(voc44, 1);
                    RawAudio.Writer wa = new RawAudio.Writer(acc44, 1)) {
                separateStream(r, sep, 15 * SR, SR, wv, wa, (f) -> listener.onProgress(from + (0.96 - from) * f, "separate"));
            }
            pcm.delete();
            // 5. 22 050 Hz mono (96 → 100 %)
            Resampler down = new Resampler(SR, OUT_SR);
            File vocOut = new File(dir, "vocals.f32.tmp");
            File accOut = new File(dir, "accomp.f32.tmp");
            try (RawAudio.Reader r = new RawAudio.Reader(voc44, 1); RawAudio.Writer w = new RawAudio.Writer(vocOut, 1)) {
                down.run(r, w, (f) -> listener.onProgress(0.96 + 0.02 * f, "finish"));
            }
            try (RawAudio.Reader r = new RawAudio.Reader(acc44, 1); RawAudio.Writer w = new RawAudio.Writer(accOut, 1)) {
                down.run(r, w, (f) -> listener.onProgress(0.98 + 0.02 * f, "finish"));
            }
            if (!vocOut.renameTo(new File(dir, "vocals.f32")) || !accOut.renameTo(new File(dir, "accomp.f32"))) {
                throw new IllegalStateException("renommage impossible");
            }
        } finally {
            src.delete();
            pcm.delete();
            voc44.delete();
            acc44.delete();
        }
        input.delete();
    }
}
