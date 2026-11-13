"""Sépare de vraies chansons avec le modèle retenu (segment par segment, comme
sur le téléphone) : temps, mémoire, et pistes mono 22 050 Hz (float32) pour
tester l'analyse de la voix (tools/separation/real.test.ts)."""

from __future__ import annotations

import os
import resource
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from eval import DATA, annotate, load, mono22  # noqa: E402
from sep import SR, Uvr  # noqa: E402

MODEL = os.environ.get("SEP_MODEL", "UVR-MDX-NET-Voc_FT.onnx")


def main() -> None:
    out = os.path.join(DATA, "stems")
    os.makedirs(out, exist_ok=True)
    m = Uvr(os.path.join(DATA, "models", MODEL), threads=4, batch=1)
    print(m.describe(), flush=True)
    for f in ("fishin.ogg", "qi-feng-le-zh.wav", "piao-yang-guo-hai-lai-kan-ni.wav"):
        p = os.path.join(DATA, f)
        if not os.path.exists(p):
            continue
        x = load(p)
        t = time.time()
        v = m.separate(x)
        dt = time.time() - t
        rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
        dur = x.shape[1] / SR
        name = os.path.splitext(f)[0]
        mix = mono22(x)
        voc = mono22(v)
        acc = mix - voc
        for suffix, arr in (("mix", mix), ("voc", voc), ("acc", acc)):
            arr.astype("<f4").tofile(os.path.join(out, f"{name}.{suffix}.f32"))
        e_v = float(np.sqrt(np.mean(voc**2)))
        e_a = float(np.sqrt(np.mean(acc**2)))
        annotate(
            f"separation {name}",
            f"{MODEL} lot=1 : {dur:.1f} s de musique en {dt:.1f} s (x{dt / dur:.3f}), mémoire max {rss:.0f} Mo, "
            f"niveau voix/accomp {20 * np.log10(e_v / max(e_a, 1e-9)):.1f} dB",
        )


if __name__ == "__main__":
    main()
