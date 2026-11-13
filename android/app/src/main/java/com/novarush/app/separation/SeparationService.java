package com.novarush.app.separation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.novarush.app.MainActivity;
import com.novarush.app.R;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.json.JSONObject;

/**
 * Service de premier plan : sépare la voix des morceaux importés, un par un,
 * même appli quittée ou écran éteint. Notification « Analyse de Titre… 42 % ».
 */
public class SeparationService extends Service {
    private static final String TAG = "NovaSeparation";
    static final String CHANNEL = "analyse";
    static final String CHANNEL_DONE = "analyse_fin";
    private static final int NOTIF_ID = 4201;
    /** Modèle embarqué (assets/separation/). */
    static final String MODEL_ASSET = "separation/model.onnx";
    /** Beat This! (temps et mesures), facultatif. */
    static final String BEAT_ASSET = "ai/beat_this.onnx";
    /** CREPE (hauteur de la voix), facultatif. */
    static final String CREPE_ASSET = "ai/crepe.onnx";
    /** Au-delà, le téléphone est jugé trop lent : on garde l'analyse classique. */
    private static final double MAX_MINUTES = 25;

    /** Écouteur (le plugin) pour prévenir l'appli. */
    interface Events {
        void onJobUpdate(JSONObject job);
    }

    static volatile Events events;
    /** Une partie est en cours : la séparation se fait plus discrète. */
    static volatile boolean gameActive;
    private static volatile String cancelId;
    private static Thread worker;

    static void start(Context ctx) {
        Intent i = new Intent(ctx, SeparationService.class);
        ContextCompat.startForegroundService(ctx, i);
    }

    static void cancel(String id) {
        cancelId = id;
    }

    static synchronized boolean isRunning() {
        return worker != null && worker.isAlive();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannels(this);
        goForeground(buildProgress("Préparation de l’analyse…", "", 0, true));
        synchronized (SeparationService.class) {
            if (worker == null || !worker.isAlive()) {
                worker = new Thread(this::loop, "nova-separation");
                worker.setPriority(Thread.NORM_PRIORITY - 1);
                worker.start();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        // Android 15 : durée maximale atteinte pour ce type de service.
        cancelId = "*";
        stopSelf();
    }

    private void goForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel c = new NotificationChannel(CHANNEL, "Analyse des morceaux", NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Progression de l’analyse avancée (voix isolée)");
        c.setShowBadge(false);
        nm.createNotificationChannel(c);
        NotificationChannel d = new NotificationChannel(CHANNEL_DONE, "Analyses terminées", NotificationManager.IMPORTANCE_DEFAULT);
        d.setDescription("Prévient quand un morceau est prêt");
        nm.createNotificationChannel(d);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildProgress(String title, String text, int percent, boolean indeterminate) {
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_nova)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(100, percent, indeterminate)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openApp())
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void notifyDone(String title, boolean ok) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_nova)
                .setContentTitle(ok ? "Analyse terminée" : "Analyse avancée impossible")
                .setContentText(ok ? title + " suit maintenant la voix. Ouvre Nova Rush pour finaliser."
                        : title + " garde son parcours classique.")
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build();
        try {
            nm.notify(Math.abs(title.hashCode()) % 100000 + 5000, n);
        } catch (SecurityException ignored) {
            // notifications refusées
        }
    }

    private void loop() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovaRush:separation");
        wl.setReferenceCounted(false);
        try {
            while (true) {
                JSONObject job = nextQueued();
                if (job == null) break;
                wl.acquire(45 * 60 * 1000L);
                runJob(job);
            }
        } finally {
            if (wl.isHeld()) wl.release();
            stopForeground(true);
            stopSelf();
        }
    }

    private JSONObject nextQueued() {
        JSONObject best = null;
        for (JSONObject j : JobStore.list(this)) {
            if (!JobStore.QUEUED.equals(j.optString("state"))) continue;
            if (best == null || j.optLong("createdAt") < best.optLong("createdAt")) best = j;
        }
        return best;
    }

    private void emit(String id) {
        Events e = events;
        JSONObject j = JobStore.read(this, id);
        if (e != null && j != null) e.onJobUpdate(j);
    }

    /** Copie le modèle des assets vers le stockage interne (une fois par version de l'appli). */
    private File modelFile() throws Exception {
        return modelFile(MODEL_ASSET, "model");
    }

    /** Modèle facultatif : null s'il n'est pas embarqué. */
    private File optionalModel(String asset, String name) {
        try {
            return modelFile(asset, name);
        } catch (Exception e) {
            Log.w(TAG, "modèle absent : " + asset);
            return null;
        }
    }

    private File modelFile(String asset, String name) throws Exception {
        File dir = new File(getFilesDir(), "models");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, name + ".onnx");
        File stamp = new File(dir, name + ".stamp");
        long version = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
        String want = Long.toString(version);
        if (f.exists() && stamp.exists()) {
            byte[] b = new byte[64];
            int r;
            try (InputStream in = new java.io.FileInputStream(stamp)) {
                r = in.read(b);
            }
            if (r > 0 && new String(b, 0, r, "UTF-8").equals(want)) return f;
        }
        File tmp = new File(dir, name + ".onnx.tmp");
        try (InputStream in = getAssets().open(asset); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
        if (f.exists()) f.delete();
        if (!tmp.renameTo(f)) throw new IllegalStateException("copie du modèle impossible");
        try (OutputStream out = new FileOutputStream(stamp)) {
            out.write(want.getBytes("UTF-8"));
        }
        return f;
    }

    private void runJob(JSONObject job) {
        final String id = job.optString("id");
        final String title = job.optString("title", "morceau");
        final File dir = JobStore.dir(this, id);
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int attempts = job.optInt("attempts", 0) + 1;
        try {
            job.put("attempts", attempts);
            job.put("state", JobStore.RUNNING);
            job.put("startedAt", System.currentTimeMillis());
            JobStore.write(this, id, job);
        } catch (Exception ignored) {
            // clés simples
        }
        emit(id);
        cancelId = null;
        final long t0 = SystemClock.elapsedRealtime();
        final long[] lastUi = {0};
        final double[] sepStart = {-1, 0};
        final boolean[] slowChecked = {false};
        final boolean[] playedDuring = {false};
        final int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        try (OrtModel model = new OrtModel(modelFile().getAbsolutePath(), threads)) {
            int nFft = model.metaInt("n_fft");
            int hop = model.metaInt("hop_length");
            int dimF = model.metaInt("dim_f");
            int dimT = model.metaInt("dim_t");
            String c = model.meta.get("center");
            boolean center = c == null || !(c.trim().equals("0") || c.trim().equalsIgnoreCase("false"));
            model.setShape(new long[] {1, 4, dimF, dimT});
            UvrSeparator sep = new UvrSeparator(model, nFft, hop, dimF, dimT, center);
            File beatFile = optionalModel(BEAT_ASSET, "beat_this");
            BeatThis.OrtNet beatNet = null;
            if (beatFile != null) {
                try {
                    beatNet = new BeatThis.OrtNet(beatFile.getAbsolutePath(), threads);
                } catch (Throwable t) {
                    Log.w(TAG, "Beat This! indisponible : " + t);
                }
            }
            final BeatThis.OrtNet beats = beatNet;
            File crepeFile = optionalModel(CREPE_ASSET, "crepe");
            CrepePitch.OrtNet crepeNet = null;
            if (crepeFile != null) {
                try {
                    crepeNet = new CrepePitch.OrtNet(crepeFile.getAbsolutePath(), threads);
                } catch (Throwable t) {
                    Log.w(TAG, "CREPE indisponible : " + t);
                }
            }
            final CrepePitch.OrtNet crepe = crepeNet;
            try {
            SeparationJob.run(dir, sep, beats, crepe, (fraction, stage) -> {
                String cid = cancelId;
                if (cid != null && (cid.equals(id) || cid.equals("*"))) return false;
                long now = SystemClock.elapsedRealtime();
                double elapsed = (now - t0) / 1000.0;
                long eta = -1;
                if (gameActive) playedDuring[0] = true;
                if ("separate".equals(stage)) {
                    if (sepStart[0] < 0) {
                        sepStart[0] = elapsed;
                        sepStart[1] = fraction;
                    } else if (fraction - sepStart[1] > 0.05) {
                        double rate = (elapsed - sepStart[0]) / (fraction - sepStart[1]);
                        double total = sepStart[0] + rate * (1 - sepStart[1]);
                        eta = Math.round(total - elapsed);
                        // Vérifié une seule fois, hors partie (le mode léger fausserait la mesure).
                        if (!slowChecked[0] && fraction - sepStart[1] > 0.1 && !gameActive && !playedDuring[0]) {
                            slowChecked[0] = true;
                            if (total > MAX_MINUTES * 60) throw new TooSlowException(total);
                        }
                    }
                }
                if (now - lastUi[0] > 1000 || fraction >= 1) {
                    lastUi[0] = now;
                    int pct = (int) Math.floor(fraction * 100);
                    String text = "beats".equals(stage)
                            ? "Rythme et mesures analysés par l’IA"
                            : "pitch".equals(stage)
                            ? "Mélodie chantée suivie par l’IA"
                            : eta >= 0 ? "Voix isolée · environ " + formatEta(eta) + " restantes" : "Voix isolée par l’IA de l’appareil";
                    nm.notify(NOTIF_ID, buildProgress("Analyse de " + title + "… " + pct + " %", text, pct, false));
                    JobStore.update(this, id, JobStore.RUNNING, fraction, null, eta);
                    emit(id);
                }
                return true;
            });
            } finally {
                if (beats != null) beats.close();
                if (crepe != null) crepe.close();
            }
            JSONObject j = JobStore.read(this, id);
            if (j == null) j = new JSONObject();
            j.put("state", JobStore.DONE);
            j.put("progress", 1);
            j.put("seconds", (SystemClock.elapsedRealtime() - t0) / 1000.0);
            j.put("vocals", new File(dir, "vocals.f32").getAbsolutePath());
            j.put("accomp", new File(dir, "accomp.f32").getAbsolutePath());
            j.put("sampleRate", SeparationJob.OUT_SR);
            File beatsOut = new File(dir, "beats.f32");
            if (beatsOut.exists()) j.put("beats", beatsOut.getAbsolutePath());
            else j.remove("beats");
            File pitchOut = new File(dir, "pitch.f32");
            if (pitchOut.exists()) j.put("pitch", pitchOut.getAbsolutePath());
            else j.remove("pitch");
            j.put("threads", threads);
            JobStore.write(this, id, j);
            emit(id);
            notifyDone(title, true);
        } catch (Throwable t) {
            boolean cancelled = t instanceof UvrSeparator.Cancelled || (t.getCause() instanceof UvrSeparator.Cancelled);
            Log.w(TAG, "séparation " + id + " : " + t, t);
            if (JobStore.read(this, id) == null) return; // supprimé pendant l'analyse
            if (cancelled && "*".equals(cancelId)) {
                // Arrêt par le système : on reprendra plus tard.
                JobStore.update(this, id, JobStore.QUEUED, 0, null, -1);
            } else {
                String msg = t instanceof TooSlowException ? "trop lent" : cancelled ? "annulé" : String.valueOf(t.getMessage());
                JobStore.update(this, id, JobStore.ERROR, 0, msg, -1);
                if (!cancelled) notifyDone(title, false);
            }
            emit(id);
            new File(dir, "input.bin").delete();
        }
    }

    static String formatEta(long sec) {
        if (sec < 60) return "1 min";
        return (sec + 59) / 60 + " min";
    }

    /** Projection de durée trop longue pour ce téléphone. */
    static final class TooSlowException extends RuntimeException {
        TooSlowException(double total) {
            super("durée estimée " + Math.round(total / 60) + " min");
        }
    }

    /** Remet en file les tâches interrompues (appli tuée pendant l'analyse). */
    static void recover(Context ctx) {
        if (isRunning()) return;
        List<JSONObject> jobs = JobStore.list(ctx);
        boolean any = false;
        for (JSONObject j : jobs) {
            String s = j.optString("state");
            String id = j.optString("id");
            if (JobStore.RUNNING.equals(s)) {
                if (j.optInt("attempts", 0) >= 3) {
                    JobStore.update(ctx, id, JobStore.ERROR, 0, "interrompu trop souvent", -1);
                } else {
                    JobStore.update(ctx, id, JobStore.QUEUED, 0, null, -1);
                    any = true;
                }
            } else if (JobStore.QUEUED.equals(s)) {
                any = true;
            } else if (JobStore.UPLOADING.equals(s) && System.currentTimeMillis() - j.optLong("createdAt") > 3600_000L) {
                JobStore.remove(ctx, id);
            }
        }
        if (any) start(ctx);
    }
}
