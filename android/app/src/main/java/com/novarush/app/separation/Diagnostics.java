package com.novarush.app.separation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * Mesures d'une analyse, montrées dans l'aperçu du morceau : durée de chaque
 * étape et part du calcul faite sur les cœurs rapides du processeur (Android
 * peut placer un service en arrière-plan sur les cœurs lents).
 */
final class Diagnostics {
    private final Map<String, Double> stages = new LinkedHashMap<>();
    private String stage;
    private long stageT0;
    private long lastSample;
    private int bigSamples;
    private int allSamples;
    private long[] maxFreq;
    private long topFreq;

    /** Appelé à chaque progression avec l'étape en cours. */
    synchronized void tick(String now) {
        long t = System.nanoTime();
        if (!now.equals(stage)) {
            if (stage != null) add(stage, (t - stageT0) / 1e9);
            stage = now;
            stageT0 = t;
        }
        if (t - lastSample > 5_000_000_000L) {
            lastSample = t;
            sampleCores();
        }
    }

    private void add(String s, double sec) {
        Double prev = stages.get(s);
        stages.put(s, (prev == null ? 0 : prev) + sec);
    }

    private static long readLong(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return Long.parseLong(r.readLine().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** Sur quels cœurs tournent les fils de calcul (créés par « nova-separation », dont ils héritent le nom) ? */
    private void sampleCores() {
        try {
            if (maxFreq == null) {
                int n = Runtime.getRuntime().availableProcessors();
                maxFreq = new long[Math.max(n, 16)];
                for (int c = 0; c < maxFreq.length; c++) {
                    maxFreq[c] = readLong("/sys/devices/system/cpu/cpu" + c + "/cpufreq/cpuinfo_max_freq");
                    topFreq = Math.max(topFreq, maxFreq[c]);
                }
            }
            if (topFreq <= 0) return;
            File[] tasks = new File("/proc/self/task").listFiles();
            if (tasks == null) return;
            for (File task : tasks) {
                String stat;
                try (BufferedReader r = new BufferedReader(new FileReader(new File(task, "stat")))) {
                    stat = r.readLine();
                }
                if (stat == null) continue;
                int open = stat.indexOf('('), close = stat.lastIndexOf(')');
                if (open < 0 || close < 0 || !stat.substring(open + 1, close).startsWith("nova-separ")) continue;
                String[] f = stat.substring(close + 2).split(" ");
                if (f.length < 37 || !"R".equals(f[0])) continue; // fil en train de calculer
                int cpu = Integer.parseInt(f[36]);
                if (cpu < 0 || cpu >= maxFreq.length || maxFreq[cpu] <= 0) continue;
                allSamples++;
                if (maxFreq[cpu] >= topFreq * 0.9) bigSamples++;
            }
        } catch (Throwable ignored) {
            // mesure facultative (fichiers système illisibles sur certains appareils)
        }
    }

    synchronized JSONObject toJson() {
        JSONObject j = new JSONObject();
        try {
            Map<String, Double> all = new LinkedHashMap<>(stages);
            if (stage != null) {
                Double prev = all.get(stage);
                all.put(stage, (prev == null ? 0 : prev) + (System.nanoTime() - stageT0) / 1e9);
            }
            JSONObject s = new JSONObject();
            for (Map.Entry<String, Double> e : all.entrySet()) s.put(e.getKey(), Math.round(e.getValue()));
            j.put("stages", s);
            if (allSamples > 0) j.put("big", Math.round(100.0 * bigSamples / allSamples) / 100.0);
            j.put("cores", Runtime.getRuntime().availableProcessors());
        } catch (Exception ignored) {
            // clés simples
        }
        return j;
    }
}
