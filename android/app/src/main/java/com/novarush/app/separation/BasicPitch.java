package com.novarush.app.separation;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

/**
 * Basic Pitch (Spotify, Apache 2.0) : notes jouées par les instruments.
 *
 * Portage de tools/ai/ref.py (basic_pitch_run) : mono 22 050 Hz, fenêtres de
 * 43 844 échantillons qui se recouvrent de 30 images (3 840 zéros ajoutés au
 * début), 15 images retirées de chaque côté. Images toutes les 256
 * échantillons ; l'image k vaut l'instant (k/142·36 164 + k%142·256) / 22 050.
 *
 * Le décodage des notes (comme basic_pitch.note_creation) est fait par
 * l'appli (src/analysis/instruments.ts) : on écrit ici les deux matrices
 * « attaques » et « notes » (88 touches), quantifiées sur un octet.
 */
final class BasicPitch {
    static final int SR = 22050;
    static final int HOP = 256;
    static final int WIN = 43844;
    static final int OVERLAP = 30 * HOP;
    static final int STEP = WIN - OVERLAP;
    static final int KEYS = 88;
    static final int TRIM = 15;

    private BasicPitch() {}

    interface Net {
        /** Une fenêtre [WIN] → { notes [images × 88], attaques [images × 88] }. */
        float[][] run(float[] window) throws Exception;
    }

    static int frameCount(int samples) {
        return (int) Math.floor(samples * 86.0 / SR);
    }

    static byte q(float v) {
        float c = v < 0 ? 0 : v > 1 ? 1 : v;
        return (byte) Math.round(c * 255);
    }

    /** x mono 22 050 Hz → { attaques, notes } (images × 88, octets 0..255). */
    static byte[][] run(Net net, float[] x, SeparationJob.Listener.Fraction progress) throws Exception {
        final int n = frameCount(x.length);
        byte[] onset = new byte[n * KEYS];
        byte[] note = new byte[n * KEYS];
        final int padded = OVERLAP / 2 + x.length;
        final int windows = (padded + STEP - 1) / STEP;
        float[] w = new float[WIN];
        int frame = 0;
        for (int wi = 0; wi < windows && frame < n; wi++) {
            int a = wi * STEP - OVERLAP / 2;
            for (int i = 0; i < WIN; i++) {
                int j = a + i;
                w[i] = j >= 0 && j < x.length ? x[j] : 0f;
            }
            float[][] o = net.run(w);
            int frames = o[0].length / KEYS;
            for (int f = TRIM; f < frames - TRIM && frame < n; f++, frame++) {
                for (int k = 0; k < KEYS; k++) {
                    note[frame * KEYS + k] = q(o[0][f * KEYS + k]);
                    onset[frame * KEYS + k] = q(o[1][f * KEYS + k]);
                }
            }
            if (progress != null && !progress.onProgress((wi + 1) / (double) windows)) throw new UvrSeparator.Cancelled();
        }
        return new byte[][] {onset, note};
    }

    /** Fichier : [attaques (images × 88)] puis [notes (images × 88)], un octet par valeur. */
    static void write(File f, byte[][] on) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(on[0]);
            out.write(on[1]);
        }
        if (!tmp.renameTo(f)) throw new IOException("renommage impossible");
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
            so.addConfigEntry("session.intra_op.allow_spinning", "0");
            session = env.createSession(path, so);
            input = session.getInputNames().iterator().next();
        }

        @Override
        public float[][] run(float[] window) throws Exception {
            try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(window), new long[] {1, WIN, 1});
                    OrtSession.Result r = session.run(Collections.singletonMap(input, t))) {
                float[] note = null, onset = null;
                for (Map.Entry<String, OnnxValue> e : r) {
                    OnnxTensor v = (OnnxTensor) e.getValue();
                    long[] shape = v.getInfo().getShape();
                    if (shape[shape.length - 1] != KEYS) continue; // contour (264)
                    FloatBuffer fb = v.getFloatBuffer();
                    float[] a = new float[fb.remaining()];
                    fb.get(a);
                    // « StatefulPartitionedCall:1 » = notes, « :2 » = attaques (cf. ref.py).
                    if (e.getKey().endsWith(":1")) note = a;
                    else onset = a;
                }
                if (note == null || onset == null) throw new IllegalStateException("sorties Basic Pitch inattendues");
                return new float[][] {note, onset};
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
