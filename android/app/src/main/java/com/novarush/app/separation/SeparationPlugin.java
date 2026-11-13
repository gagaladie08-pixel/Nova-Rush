package com.novarush.app.separation;

import android.Manifest;
import android.os.Build;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pont JavaScript ↔ séparation de la voix (service Android).
 *
 * createJob → appendInput (morceaux base64) → enqueue → événements « job » →
 * l'appli lit vocals.f32 / accomp.f32 puis appelle remove.
 */
@CapacitorPlugin(
        name = "VoiceSeparation",
        permissions = {@Permission(strings = {Manifest.permission.POST_NOTIFICATIONS}, alias = "notifications")})
public class SeparationPlugin extends Plugin {

    @Override
    public void load() {
        SeparationService.events = (job) -> {
            try {
                notifyListeners("job", JSObject.fromJSONObject(job));
            } catch (JSONException ignored) {
                // objet simple
            }
        };
    }

    @Override
    protected void handleOnDestroy() {
        SeparationService.events = null;
    }

    private boolean hasModel() {
        try {
            getContext().getAssets().open(SeparationService.MODEL_ASSET).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject r = new JSObject();
        r.put("available", hasModel() && Build.SUPPORTED_64_BIT_ABIS.length > 0);
        r.put("cores", Runtime.getRuntime().availableProcessors());
        call.resolve(r);
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED) {
            JSObject r = new JSObject();
            r.put("granted", true);
            call.resolve(r);
            return;
        }
        requestPermissionForAlias("notifications", call, "notificationsCallback");
    }

    @PermissionCallback
    private void notificationsCallback(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", getPermissionState("notifications") == PermissionState.GRANTED);
        call.resolve(r);
    }

    @PluginMethod
    public void createJob(PluginCall call) {
        String id = call.getString("id");
        String title = call.getString("title", "morceau");
        try {
            File dir = JobStore.dir(getContext(), id);
            JobStore.deleteRecursive(dir);
            dir.mkdirs();
            JSONObject j = new JSONObject();
            j.put("id", id);
            j.put("title", title);
            // Analyse complète : batterie, basse et instruments (Demucs + Basic Pitch), environ deux fois plus longue.
            j.put("full", Boolean.TRUE.equals(call.getBoolean("full", false)));
            j.put("state", JobStore.UPLOADING);
            j.put("progress", 0);
            j.put("createdAt", System.currentTimeMillis());
            JobStore.write(getContext(), id, j);
            call.resolve();
        } catch (Exception e) {
            call.reject("création impossible : " + e.getMessage());
        }
    }

    @PluginMethod
    public void appendInput(PluginCall call) {
        String id = call.getString("id");
        String data = call.getString("data");
        if (id == null || data == null) {
            call.reject("paramètres manquants");
            return;
        }
        try (FileOutputStream out = new FileOutputStream(new File(JobStore.dir(getContext(), id), "input.bin"), true)) {
            out.write(Base64.decode(data, Base64.DEFAULT));
            call.resolve();
        } catch (Exception e) {
            call.reject("écriture impossible : " + e.getMessage());
        }
    }

    @PluginMethod
    public void enqueue(PluginCall call) {
        String id = call.getString("id");
        JSONObject j = JobStore.read(getContext(), id);
        if (j == null) {
            call.reject("tâche inconnue");
            return;
        }
        try {
            j.put("state", JobStore.QUEUED);
            j.put("progress", 0);
            j.put("attempts", 0);
            j.remove("error");
            JobStore.write(getContext(), id, j);
            SeparationService.start(getContext());
            call.resolve();
        } catch (Exception e) {
            call.reject("démarrage impossible : " + e.getMessage());
        }
    }

    @PluginMethod
    public void list(PluginCall call) {
        JSArray arr = new JSArray();
        for (JSONObject j : JobStore.list(getContext())) arr.put(j);
        JSObject r = new JSObject();
        r.put("jobs", arr);
        call.resolve(r);
    }

    /** Relance les tâches en attente ou interrompues (au démarrage de l'appli). */
    @PluginMethod
    public void resume(PluginCall call) {
        try {
            SeparationService.recover(getContext());
            call.resolve();
        } catch (Exception e) {
            call.reject("reprise impossible : " + e.getMessage());
        }
    }

    @PluginMethod
    public void setGameActive(PluginCall call) {
        SeparationService.gameActive = Boolean.TRUE.equals(call.getBoolean("active", false));
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        SeparationService.cancel(call.getString("id"));
        call.resolve();
    }

    @PluginMethod
    public void remove(PluginCall call) {
        String id = call.getString("id");
        SeparationService.cancel(id);
        JobStore.remove(getContext(), id);
        call.resolve();
    }
}
