"""Compare les modèles de séparation (qualité, vitesse) — lancé par la CI GitHub.

Mélanges de test : de vraies voix (librosa) posées sur des morceaux
instrumentaux, à niveau connu. On mesure le SDR de la voix extraite,
l'accord de hauteur (YIN) entre voix extraite et voix réelle, et la vitesse.
Les résultats sont imprimés et transmis sous forme d'annotations GitHub.
"""

from __future__ import annotations

import json
import os
import sys
import time
from math import gcd

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

sys.path.insert(0, os.path.dirname(__file__))
from sep import SR, Spleeter, Uvr  # noqa: E402

DATA = os.environ.get("SEP_DATA", "/tmp/sepdata")
THREADS = int(os.environ.get("SEP_THREADS", "4"))


def load(path: str, sr: int = SR) -> np.ndarray:
    x, r = sf.read(path, dtype="float32", always_2d=True)
    x = x.T
    if x.shape[0] == 1:
        x = np.vstack([x, x])
    x = x[:2]
    if r != sr:
        g = gcd(r, sr)
        x = resample_poly(x, sr // g, r // g, axis=1).astype(np.float32)
    return x


def mono22(x: np.ndarray) -> np.ndarray:
    return resample_poly(x.mean(axis=0), 1, 2).astype(np.float32)


def sdr(ref: np.ndarray, est: np.ndarray) -> float:
    n = min(len(ref), len(est))
    ref, est = ref[:n].astype(np.float64), est[:n].astype(np.float64)
    return float(10 * np.log10(np.sum(ref**2) / (np.sum((ref - est) ** 2) + 1e-12) + 1e-12))


def yin(x: np.ndarray, sr: int = 22050, fmin=80.0, fmax=1000.0, hop=256, win=1024, thr=0.15):
    """Hauteur par trame (Hz, 0 = non voisé) — YIN simplifié, vectorisé."""
    tmax = int(sr / fmin)
    tmin = int(sr / fmax)
    nfr = 1 + (len(x) - win - tmax) // hop
    f0 = np.zeros(max(0, nfr))
    rms_all = np.sqrt(np.mean(x**2) + 1e-12)
    for i in range(nfr):
        a = i * hop
        frame = x[a : a + win + tmax].astype(np.float64)
        if np.sqrt(np.mean(frame[:win] ** 2)) < rms_all * 0.15:
            continue
        # différence carrée par FFT
        n = win + tmax
        F = np.fft.rfft(frame, 2 * n)
        ac = np.fft.irfft(F * np.conj(F))[: tmax + 1]
        e = np.cumsum(frame**2)
        e0 = e[win - 1]
        et = np.array([e[t + win - 1] - (e[t - 1] if t > 0 else 0) for t in range(tmax + 1)])
        d = e0 + et - 2 * ac
        d[0] = 0
        cm = np.ones_like(d)
        cs = np.cumsum(d[1:])
        cm[1:] = d[1:] * np.arange(1, len(d)) / np.maximum(cs, 1e-12)
        cand = np.where(cm[tmin:] < thr)[0]
        if len(cand) == 0:
            continue
        t = cand[0] + tmin
        while t + 1 < len(cm) and cm[t + 1] < cm[t]:
            t += 1
        f0[i] = sr / t
    return f0


def pitch_agreement(ref: np.ndarray, est: np.ndarray) -> dict:
    fr, fe = yin(ref), yin(est)
    n = min(len(fr), len(fe))
    fr, fe = fr[:n], fe[:n]
    v = fr > 0
    if v.sum() == 0:
        return {"voiced": 0}
    both = v & (fe > 0)
    cents = np.abs(1200 * np.log2(np.maximum(fe[both], 1) / fr[both]))
    ok = (cents < 50).sum()
    return {
        "recall": round(float(both.sum() / v.sum()), 3),  # trames voisées retrouvées
        "acc50": round(float(ok / v.sum()), 3),  # bonne hauteur (±50 cents)
        "false": round(float(((~v) & (fe > 0)).sum() / max(1, (~v).sum())), 3),
    }


def build_mixes() -> list[dict]:
    voices = [load(os.path.join(DATA, f"sp{i}.ogg")) for i in (1, 2, 3)]
    mixes = []
    for inst_name in ("vibeace", "choice"):
        inst = load(os.path.join(DATA, f"{inst_name}.ogg"))
        dur = min(inst.shape[1], 30 * SR)
        inst = inst[:, :dur]
        voc = np.zeros_like(inst)
        pos = int(1.5 * SR)
        k = 0
        while pos < dur - SR:
            v = voices[k % 3]
            L = min(v.shape[1], dur - pos)
            pan = 0.85 + 0.15 * (k % 2)  # voix presque au centre
            voc[0, pos : pos + L] += v[0, :L] * pan
            voc[1, pos : pos + L] += v[1, :L] * (1.85 - pan)
            pos += L + int(1.2 * SR)
            k += 1
        for snr in (0, -6):
            pv = np.sqrt(np.mean(voc[:, np.abs(voc).max(axis=0) > 1e-4] ** 2))
            pi = np.sqrt(np.mean(inst**2))
            g = pi / pv * 10 ** (snr / 20)
            v2 = voc * g
            mix = v2 + inst
            peak = np.abs(mix).max()
            if peak > 0.99:
                mix, v2 = mix / peak * 0.99, v2 / peak * 0.99
            mixes.append({"name": f"{inst_name}@{snr}dB", "mix": mix.astype(np.float32), "voc": v2.astype(np.float32)})
    return mixes


def models() -> list[tuple[str, callable]]:
    out = []
    md = os.path.join(DATA, "models")
    for f in sorted(os.listdir(md)):
        p = os.path.join(md, f)
        if f.endswith(".onnx"):
            out.append((f, lambda p=p: Uvr(p, THREADS)))
        elif os.path.isdir(p):
            fs = os.listdir(p)
            v = [x for x in fs if x.startswith("vocals") and x.endswith(".onnx")]
            a = [x for x in fs if x.startswith("accompaniment") and x.endswith(".onnx")]
            if v and a:
                vp, ap = os.path.join(p, v[0]), os.path.join(p, a[0])
                out.append((f, lambda vp=vp, ap=ap: Spleeter(vp, ap, THREADS)))
    return out


def annotate(title: str, msg: str) -> None:
    msg = msg.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    print(f"::notice title={title}::{msg}", flush=True)


def main() -> None:
    mixes = build_mixes()
    songs = []
    for f in ("fishin.ogg", "qi-feng-le-zh.wav", "piao-yang-guo-hai-lai-kan-ni.wav"):
        p = os.path.join(DATA, f)
        if os.path.exists(p):
            songs.append((f, load(p)[:, : 60 * SR]))
    results = {}
    for name, make in models():
        t0 = time.time()
        try:
            m = make()
        except Exception as e:  # noqa: BLE001
            results[name] = {"error": f"load: {e}"}
            continue
        load_t = time.time() - t0
        print(f"== {name}: {m.describe()}", flush=True)
        mp = os.path.join(DATA, "models", name)
        size = os.path.getsize(mp) if os.path.isfile(mp) else sum(
            os.path.getsize(os.path.join(mp, x)) for x in os.listdir(mp) if x.endswith(".onnx")
        )
        r = {"size_mb": round(size / 1e6, 1)}
        r["load_s"] = round(load_t, 1)
        q = []
        for mx in mixes:
            est = m.separate(mx["mix"])
            other = mx["mix"] - est
            ref_v, ref_i = mono22(mx["voc"]), mono22(mx["mix"] - mx["voc"])
            e1, e2 = mono22(est), mono22(other)
            s_direct = sdr(ref_v, e1)
            s_swap = sdr(ref_v, e2)  # modèle « instrumental » : la voix est le reste
            swapped = s_swap > s_direct
            ev = e2 if swapped else e1
            base = sdr(ref_v, mono22(mx["mix"]))
            pa = pitch_agreement(ref_v, ev)
            pm = pitch_agreement(ref_v, mono22(mx["mix"]))
            q.append(
                {
                    "mix": mx["name"],
                    "sdr": round(max(s_direct, s_swap), 2),
                    "gain": round(max(s_direct, s_swap) - base, 2),
                    "inst_sdr": round(sdr(ref_i, e1 if swapped else e2), 2),
                    "swapped": swapped,
                    "pitch": pa,
                    "pitch_mix": pm,
                }
            )
            print(name, q[-1], flush=True)
        r["quality"] = q
        sp = []
        for f, x in songs:
            t = time.time()
            est = m.separate(x)
            dt = time.time() - t
            sp.append({"song": f, "sec": round(x.shape[1] / SR, 1), "time": round(dt, 1), "rt": round(dt / (x.shape[1] / SR), 3)})
            print(name, sp[-1], flush=True)
        r["speed"] = sp
        results[name] = r
        avg_sdr = np.mean([z["sdr"] for z in q])
        avg_acc = np.mean([z["pitch"].get("acc50", 0) for z in q])
        avg_rt = np.mean([z["rt"] for z in sp]) if sp else -1
        annotate(
            f"modele {name}",
            f"SDR voix moy {avg_sdr:.2f} dB | hauteur juste {avg_acc:.3f} | temps/durée {avg_rt:.3f} | "
            f"taille {r['size_mb']} Mo | {json.dumps(q, separators=(',', ':'))}",
        )
    with open(os.path.join(DATA, "results.json"), "w") as fh:
        json.dump(results, fh, indent=1)
    print(json.dumps(results, indent=1))
    import platform

    annotate("machine", f"{platform.processor()} cpus={os.cpu_count()} threads={THREADS}")


if __name__ == "__main__":
    main()
