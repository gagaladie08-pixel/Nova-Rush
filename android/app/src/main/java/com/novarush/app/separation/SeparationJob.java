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
        separateStream(r, sep, chunk, margin, wv, wa, null, progress);
    }

    /** Idem, et écrit aussi l'accompagnement stéréo (gauche − voix, droite − voix) si `was` est donné. */
    static void separateStream(Resampler.Source r, UvrSeparator sep, int chunk, int margin,
            RawAudio.Writer wv, RawAudio.Writer wa, RawAudio.Writer was, Listener.Fraction progress) throws Exception {
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
                if (was != null) {
                    was.put(left[i] - v[0][i]);
                    was.put(right[i] - v[1][i]);
                }
            }
            wv.addFrames(hi - lo);
            wa.addFrames(hi - lo);
            if (was != null) was.addFrames(hi - lo);
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

    /**
     * Hauteur de la voix isolée (CREPE) : écrit pitch.f32 = [n, f0…, confiance…]
     * (une valeur toutes les 10 ms, f0 en Hz).
     */
    static void computePitch(File voc44, File dir, CrepePitch.Net net, Listener.Fraction progress) throws Exception {
        File v16 = new File(dir, "voc16.raw");
        try {
            try (RawAudio.Reader r = new RawAudio.Reader(voc44, 1); RawAudio.Writer w = new RawAudio.Writer(v16, 1)) {
                if (r.frames() > (long) MAX_BEAT_SECONDS * SR) return;
                new Resampler(SR, CrepePitch.SR).run(r, w, (f) -> progress.onProgress(0.1 * f));
            }
            float[] x;
            try (RawAudio.Reader r = new RawAudio.Reader(v16, 1)) {
                x = new float[(int) r.frames()];
                r.read(0, x.length, x);
            }
            v16.delete();
            float[][] fc = CrepePitch.track(net, x, (f) -> progress.onProgress(0.1 + 0.9 * f));
            File tmp = new File(dir, "pitch.f32.tmp");
            try (RawAudio.Writer w = new RawAudio.Writer(tmp, 1)) {
                w.put(fc[0].length);
                for (float v : fc[0]) w.put(v);
                for (float v : fc[1]) w.put(v);
            }
            if (!tmp.renameTo(new File(dir, "pitch.f32"))) throw new IllegalStateException("renommage impossible");
        } finally {
            v16.delete();
        }
    }

    /** Les IA d'une analyse ; seule la séparation de la voix est obligatoire. */
    static final class Models {
        UvrSeparator sep;
        BeatThis.Net beat;
        CrepePitch.Net crepe;
        /** Demucs est gros : ouvert seulement au moment de son étape. */
        Opener<DemucsSeparator.OrtNet> demucs;
        BasicPitch.Net basicPitch;
    }

    interface Opener<T> {
        T open() throws Exception;
    }

    /** Durée maximale de la séparation de la voix (au-delà : téléphone jugé trop lent). */
    static final double MAX_SEP_MINUTES = 22;
    /** Demucs est sauté si son temps estimé dépasse cette durée. */
    static final double MAX_DEMUCS_MINUTES = 45;
    /** Demucs ≈ 2,2 × le temps de la séparation de la voix (mesuré en CI : ×0,27 contre ×0,13 du temps réel). */
    static final double DEMUCS_VS_UVR = 2.2;

    /** Répartition de la progression selon le coût de chaque étape. */
    static final class Plan {
        private final String[] names;
        private final double[] from;
        private final double[] to;

        Plan(String[] names, double[] weights) {
            this.names = names;
            from = new double[names.length];
            to = new double[names.length];
            double total = 0;
            for (double w : weights) total += w;
            double acc = 0;
            for (int i = 0; i < names.length; i++) {
                from[i] = acc / total;
                acc += weights[i];
                to[i] = acc / total;
            }
        }

        static Plan of(Models m) {
            java.util.List<String> n = new java.util.ArrayList<>();
            java.util.List<Double> w = new java.util.ArrayList<>();
            // Poids mesurés en CI (temps relatifs : voix 25, Demucs ≈ 2,2 × voix, CREPE ≈ 0,37 ×, Beat This! ≈ 0,2 ×).
            n.add("decode"); w.add(3.0);
            if (m.beat != null) { n.add("beats"); w.add(5.0); }
            n.add("separate"); w.add(25.0);
            if (m.crepe != null) { n.add("pitch"); w.add(9.0); }
            n.add("finish"); w.add(2.0);
            if (m.demucs != null) { n.add("demucs"); w.add(55.0); n.add("tracks"); w.add(2.0); }
            if (m.demucs != null && m.basicPitch != null) { n.add("notes"); w.add(2.0); }
            double[] ws = new double[w.size()];
            for (int i = 0; i < ws.length; i++) ws[i] = w.get(i);
            return new Plan(n.toArray(new String[0]), ws);
        }

        private int idx(String stage) {
            for (int i = 0; i < names.length; i++) if (names[i].equals(stage)) return i;
            throw new IllegalArgumentException(stage);
        }

        /** Progression globale d'une étape avancée à `f` (0..1). */
        double at(String stage, double f) {
            int i = idx(stage);
            return from[i] + (to[i] - from[i]) * Math.max(0, Math.min(1, f));
        }

        Listener.Fraction stage(Listener l, String stage) {
            return (f) -> l.onProgress(at(stage, f), stage);
        }
    }

    static void run(File dir, UvrSeparator sep, Listener listener) throws Exception {
        Models m = new Models();
        m.sep = sep;
        run(dir, m, listener);
    }

    static void run(File dir, Models models, Listener listener) throws Exception {
        run(dir, models, listener, null);
    }

    /**
     * @param voiceReady appelé dès que voix, accompagnement, temps et hauteur
     *     sont prêts (avant Demucs) : l'appli construit alors un premier
     *     parcours, amélioré à la fin par les instruments.
     */
    static void run(File dir, Models models, Listener listener, Runnable voiceReady) throws Exception {
        final Plan plan = Plan.of(models);
        File input = new File(dir, "input.bin");
        File src = new File(dir, "decoded.raw");
        File pcm = new File(dir, "pcm44.raw");
        File voc44 = new File(dir, "voc44.raw");
        File acc44 = new File(dir, "acc44.raw");
        File accSt = new File(dir, "accst44.raw");
        File[] inst44 = {new File(dir, "drums44.raw"), new File(dir, "bass44.raw"), new File(dir, "other44.raw")};
        File bass22 = new File(dir, "bass22.raw");
        File other22 = new File(dir, "other22.raw");
        try {
            // 1. Décodage et 44,1 kHz stéréo
            Listener.Fraction dec = plan.stage(listener, "decode");
            AudioDecoder.Result d = AudioDecoder.decode(input, src, (f) -> dec.onProgress(0.6 * f));
            if (d.sampleRate != SR) {
                try (RawAudio.Reader r = new RawAudio.Reader(src, 2); RawAudio.Writer w = new RawAudio.Writer(pcm, 2)) {
                    new Resampler(d.sampleRate, SR).run(r, w, (f) -> dec.onProgress(0.6 + 0.4 * f));
                }
                src.delete();
            } else if (!src.renameTo(pcm)) {
                throw new IllegalStateException("renommage impossible");
            }
            // 2. Rythme par l'IA — facultatif : en cas d'échec, rythme classique.
            if (models.beat != null) {
                try {
                    computeBeats(pcm, dir, models.beat, plan.stage(listener, "beats"));
                } catch (UvrSeparator.Cancelled c) {
                    throw c;
                } catch (Throwable t) {
                    Log.w("NovaRushSep", "Beat This! : " + t, t);
                    new File(dir, "beats.f32").delete();
                }
            }
            // 3. Voix / accompagnement (+ accompagnement stéréo pour Demucs)
            final Listener.Fraction sp = plan.stage(listener, "separate");
            final long sepT0 = System.nanoTime();
            final boolean[] played = {SeparationService.gameActive};
            final boolean[] checked = {false};
            try (RawAudio.Reader r = new RawAudio.Reader(pcm, 2);
                    RawAudio.Writer wv = new RawAudio.Writer(voc44, 1);
                    RawAudio.Writer wa = new RawAudio.Writer(acc44, 1);
                    RawAudio.Writer ws = models.demucs != null ? new RawAudio.Writer(accSt, 2) : null) {
                separateStream(r, models.sep, 15 * SR, SR, wv, wa, ws, (f) -> {
                    if (SeparationService.gameActive) played[0] = true;
                    if (!checked[0] && f > 0.1 && !played[0]) {
                        // Vérifié une seule fois, hors partie (le mode léger fausserait la mesure).
                        checked[0] = true;
                        double total = (System.nanoTime() - sepT0) / 1e9 / f;
                        if (total > MAX_SEP_MINUTES * 60) throw new SeparationService.TooSlowException(total);
                    }
                    return sp.onProgress(f);
                });
            }
            final double sepSeconds = (System.nanoTime() - sepT0) / 1e9;
            pcm.delete();
            // 4. Hauteur de la voix par l'IA — facultatif : sinon suivi classique (YIN).
            if (models.crepe != null) {
                try {
                    computePitch(voc44, dir, models.crepe, plan.stage(listener, "pitch"));
                } catch (UvrSeparator.Cancelled c) {
                    throw c;
                } catch (Throwable t) {
                    Log.w("NovaRushSep", "CREPE : " + t, t);
                    new File(dir, "pitch.f32").delete();
                }
            }
            // 5. Voix et accompagnement 22 050 Hz mono pour l'appli : premier parcours sans attendre Demucs.
            Listener.Fraction fin = plan.stage(listener, "finish");
            Resampler down = new Resampler(SR, OUT_SR);
            File vocOut = new File(dir, "vocals.f32.tmp");
            File accOut = new File(dir, "accomp.f32.tmp");
            try (RawAudio.Reader r = new RawAudio.Reader(voc44, 1); RawAudio.Writer w = new RawAudio.Writer(vocOut, 1)) {
                down.run(r, w, (f) -> fin.onProgress(0.5 * f));
            }
            try (RawAudio.Reader r = new RawAudio.Reader(acc44, 1); RawAudio.Writer w = new RawAudio.Writer(accOut, 1)) {
                down.run(r, w, (f) -> fin.onProgress(0.5 + 0.5 * f));
            }
            if (!vocOut.renameTo(new File(dir, "vocals.f32")) || !accOut.renameTo(new File(dir, "accomp.f32"))) {
                throw new IllegalStateException("renommage impossible");
            }
            voc44.delete();
            acc44.delete();
            if (voiceReady != null && models.demucs != null) voiceReady.run();
            // 6. Batterie / basse / autres (Demucs sur l'accompagnement) — facultatif.
            boolean instruments = false;
            if (models.demucs != null) {
                boolean tooSlow = !played[0] && sepSeconds * DEMUCS_VS_UVR > MAX_DEMUCS_MINUTES * 60;
                if (tooSlow) {
                    Log.w("NovaRushSep", "Demucs sauté : environ " + Math.round(sepSeconds * DEMUCS_VS_UVR / 60) + " min estimées");
                } else {
                    try {
                        computeInstruments(accSt, inst44, models.demucs, plan.stage(listener, "demucs"));
                        instruments = true;
                    } catch (UvrSeparator.Cancelled c) {
                        throw c;
                    } catch (Throwable t) {
                        Log.w("NovaRushSep", "Demucs : " + t, t);
                    }
                }
                accSt.delete();
            }
            // 7. Pistes des instruments en 22 050 Hz mono
            if (instruments) {
                Listener.Fraction tr = plan.stage(listener, "tracks");
                try {
                    File drumsOut = new File(dir, "drums.f32.tmp");
                    try (RawAudio.Reader r = new RawAudio.Reader(inst44[0], 1); RawAudio.Writer w = new RawAudio.Writer(drumsOut, 1)) {
                        down.run(r, w, (f) -> tr.onProgress(0.4 * f));
                    }
                    if (!drumsOut.renameTo(new File(dir, "drums.f32"))) throw new IllegalStateException("renommage impossible");
                    try (RawAudio.Reader r = new RawAudio.Reader(inst44[1], 1); RawAudio.Writer w = new RawAudio.Writer(bass22, 1)) {
                        down.run(r, w, (f) -> tr.onProgress(0.4 + 0.3 * f));
                    }
                    try (RawAudio.Reader r = new RawAudio.Reader(inst44[2], 1); RawAudio.Writer w = new RawAudio.Writer(other22, 1)) {
                        down.run(r, w, (f) -> tr.onProgress(0.7 + 0.3 * f));
                    }
                } catch (UvrSeparator.Cancelled c) {
                    throw c;
                } catch (Throwable t) {
                    Log.w("NovaRushSep", "pistes instruments : " + t, t);
                    instruments = false;
                    new File(dir, "drums.f32").delete();
                }
                for (File f : inst44) f.delete();
            }
            // 8. Notes des instruments (Basic Pitch sur la basse et les autres) — facultatif.
            if (instruments && models.basicPitch != null) {
                Listener.Fraction nt = plan.stage(listener, "notes");
                try {
                    computeNotes(bass22, new File(dir, "bp_bass.u8"), models.basicPitch, (f) -> nt.onProgress(0.5 * f));
                    computeNotes(other22, new File(dir, "bp_other.u8"), models.basicPitch, (f) -> nt.onProgress(0.5 + 0.5 * f));
                } catch (UvrSeparator.Cancelled c) {
                    throw c;
                } catch (Throwable t) {
                    Log.w("NovaRushSep", "Basic Pitch : " + t, t);
                    new File(dir, "bp_bass.u8").delete();
                    new File(dir, "bp_other.u8").delete();
                }
            }
        } finally {
            src.delete();
            pcm.delete();
            voc44.delete();
            acc44.delete();
            accSt.delete();
            for (File f : inst44) f.delete();
            bass22.delete();
            other22.delete();
        }
        input.delete();
    }

    /** Demucs sur l'accompagnement stéréo → batterie, basse, autres (mono 44,1 kHz). */
    static void computeInstruments(File accSt, File[] out44, Opener<DemucsSeparator.OrtNet> opener, Listener.Fraction progress)
            throws Exception {
        try (DemucsSeparator.OrtNet net = opener.open();
                RawAudio.Reader r = new RawAudio.Reader(accSt, 2);
                RawAudio.Writer d = new RawAudio.Writer(out44[0], 1);
                RawAudio.Writer b = new RawAudio.Writer(out44[1], 1);
                RawAudio.Writer o = new RawAudio.Writer(out44[2], 1)) {
            if (r.frames() > (long) MAX_BEAT_SECONDS * SR) throw new IllegalStateException("morceau trop long pour Demucs");
            new DemucsSeparator(net).separate(r, new RawAudio.Writer[] {d, b, o}, 1e-4f, new DemucsSeparator.Progress() {
                @Override
                public boolean onProgress(double f) {
                    return progress.onProgress(f);
                }

                @Override
                public boolean paused() {
                    return SeparationService.gameActive;
                }
            });
        }
    }

    /** Basic Pitch sur une piste mono 22 050 Hz → matrices attaques / notes. */
    static void computeNotes(File in22, File out, BasicPitch.Net net, Listener.Fraction progress) throws Exception {
        float[] x;
        try (RawAudio.Reader r = new RawAudio.Reader(in22, 1)) {
            x = new float[(int) r.frames()];
            r.read(0, x.length, x);
        }
        BasicPitch.write(out, BasicPitch.run(net, x, progress));
    }
}
