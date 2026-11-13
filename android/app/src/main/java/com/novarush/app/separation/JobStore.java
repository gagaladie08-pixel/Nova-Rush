package com.novarush.app.separation;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Tâches de séparation persistées sur disque (survivent à l'arrêt de l'appli) :
 * files/separation/<id>/job.json + fichiers audio.
 */
final class JobStore {
    static final String UPLOADING = "uploading";
    static final String QUEUED = "queued";
    static final String RUNNING = "running";
    static final String DONE = "done";
    static final String ERROR = "error";

    private JobStore() {}

    static File root(Context ctx) {
        File d = new File(ctx.getFilesDir(), "separation");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File dir(Context ctx, String id) {
        if (!id.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("identifiant invalide");
        return new File(root(ctx), id);
    }

    static synchronized JSONObject read(Context ctx, String id) {
        File f = new File(dir(ctx, id), "job.json");
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) > 0) bos.write(b, 0, r);
            return new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    static synchronized void write(Context ctx, String id, JSONObject job) {
        File d = dir(ctx, id);
        if (!d.exists()) d.mkdirs();
        File tmp = new File(d, "job.json.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(job.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            return;
        }
        // Remplacement atomique.
        tmp.renameTo(new File(d, "job.json"));
    }

    static synchronized void update(Context ctx, String id, String state, double progress, String error, long etaSec) {
        JSONObject j = read(ctx, id);
        if (j == null) j = new JSONObject();
        try {
            j.put("id", id);
            if (state != null) j.put("state", state);
            j.put("progress", progress);
            if (error != null) j.put("error", error);
            else j.remove("error");
            if (etaSec >= 0) j.put("eta", etaSec);
            else j.remove("eta");
            j.put("updatedAt", System.currentTimeMillis());
        } catch (JSONException ignored) {
            // clés simples
        }
        write(ctx, id, j);
    }

    static synchronized List<JSONObject> list(Context ctx) {
        List<JSONObject> out = new ArrayList<>();
        File[] dirs = root(ctx).listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) {
            JSONObject j = read(ctx, d.getName());
            if (j != null) out.add(j);
        }
        return out;
    }

    static synchronized void remove(Context ctx, String id) {
        deleteRecursive(dir(ctx, id));
    }

    static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }
}
