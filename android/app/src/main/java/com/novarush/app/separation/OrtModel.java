package com.novarush.app.separation;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

/**
 * Modèle ONNX exécuté par ONNX Runtime (processeur du téléphone).
 *
 * Deux sessions : « pleine » (tous les cœurs rapides) et « légère »
 * (2 fils, sans attente active) utilisée pendant qu'on joue, pour que la
 * partie reste fluide.
 */
final class OrtModel implements UvrSeparator.Model, AutoCloseable {
    private final OrtEnvironment env;
    private final String path;
    private final int threads;
    private final OrtSession full;
    private OrtSession light;
    private final String inputName;
    final Map<String, String> meta;
    private long[] shape;

    OrtModel(String path, int threads) throws OrtException {
        env = OrtEnvironment.getEnvironment();
        this.path = path;
        this.threads = threads;
        full = env.createSession(path, options(threads, true));
        inputName = full.getInputNames().iterator().next();
        meta = full.getMetadata().getCustomMetadata();
    }

    private static OrtSession.SessionOptions options(int threads, boolean spinning) throws OrtException {
        OrtSession.SessionOptions so = new OrtSession.SessionOptions();
        so.setIntraOpNumThreads(threads);
        so.setInterOpNumThreads(1);
        so.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        if (!spinning) so.addConfigEntry("session.intra_op.allow_spinning", "0");
        return so;
    }

    int metaInt(String key) {
        String v = meta.get(key);
        if (v == null) throw new IllegalStateException("métadonnée manquante : " + key);
        return Integer.parseInt(v.trim());
    }

    void setShape(long[] shape) {
        this.shape = shape;
    }

    private OrtSession session() throws OrtException {
        if (!SeparationService.gameActive || threads <= 2) return full;
        if (light == null) light = env.createSession(path, options(2, false));
        return light;
    }

    @Override
    public float[] run(float[] input) throws OrtException {
        try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape);
                OrtSession.Result r = session().run(Collections.singletonMap(inputName, t))) {
            OnnxTensor out = (OnnxTensor) r.get(0);
            FloatBuffer fb = out.getFloatBuffer();
            float[] y = new float[fb.remaining()];
            fb.get(y);
            return y;
        }
    }

    @Override
    public void close() throws OrtException {
        full.close();
        if (light != null) light.close();
    }
}
