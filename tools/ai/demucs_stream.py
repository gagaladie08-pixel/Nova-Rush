"""Demucs par segments, en flux (modèle exact du portage Java DemucsSeparator).

Même calcul que ref.demucs_separate (recouvrement 0,25, poids triangulaires),
mais sans garder tout le morceau en mémoire : après chaque segment, la partie
que plus aucun segment ne touchera est écrite. Ne garde que batterie, basse et
autres instruments, en mono.

python demucs_stream.py <htdemucs.onnx> <dossier stems>   (CI : <nom>.acc44.f32
stéréo entrelacé 44,1 kHz → <nom>.drums.f32 mono 22 050 Hz + <nom>.bass22 /
<nom>.other22 pour Basic Pitch)
"""

from __future__ import annotations

import glob
import os
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import ref  # noqa: E402

KEEP = (0, 1, 2)  # drums, bass, other


def weights(seg: int) -> np.ndarray:
    w = np.concatenate([np.arange(1, seg // 2 + 1), np.arange(seg - seg // 2, 0, -1)]).astype(np.float64)
    return w / w.max()


def separate_stream(segment_fn, mix: np.ndarray, seg: int = ref.DEMUCS_SEGMENT, overlap: float = 0.25, silent: float = 0.0):
    """mix (2, n) → (3, n) mono : batterie, basse, autres (comme Java)."""
    n = mix.shape[-1]
    mono = mix.mean(0)
    mu = mono.mean()
    sd = mono.std() + 1e-8
    stride = int((1 - overlap) * seg)
    w = weights(seg)
    acc = np.zeros((len(KEEP), seg))
    sw = np.zeros(seg)
    out = np.zeros((len(KEEP), n))
    written = 0
    off = 0
    while off < n:
        length = min(seg, n - off)
        delta = seg - length
        start = off - delta // 2
        chunk = np.zeros((2, seg))
        cs, ce = max(0, start), min(n, start + seg)
        chunk[:, cs - start : ce - start] = (mix[:, cs:ce] - mu) / sd
        if silent > 0 and np.sqrt(np.mean(mix[:, cs:ce] ** 2)) < silent:
            y = np.zeros((4, 2, seg))
        else:
            y = segment_fn(chunk)
        y = y[..., delta // 2 : delta // 2 + length]
        for j, s in enumerate(KEEP):
            acc[j, :length] += w[:length] * y[s].mean(0)
        sw[:length] += w[:length]
        last = off + stride >= n
        emit = length if last else stride
        out[:, written : written + emit] = acc[:, :emit] / sw[:emit] * sd + mu
        written += emit
        # Décalage des tampons : ce qui suit `stride` sera complété au segment suivant.
        acc = np.concatenate([acc[:, stride:], np.zeros((len(KEEP), stride))], axis=1)
        sw = np.concatenate([sw[stride:], np.zeros(stride)])
        off += stride
    return out


def main() -> None:
    import onnxruntime as ort

    model, folder = sys.argv[1], sys.argv[2]
    so = ort.SessionOptions()
    so.intra_op_num_threads = 4
    sess = ort.InferenceSession(model, so, providers=["CPUExecutionProvider"])
    for path in sorted(glob.glob(os.path.join(folder, "*.acc44.f32"))):
        name = os.path.basename(path).replace(".acc44.f32", "")
        x = np.fromfile(path, dtype="<f4").astype(np.float64).reshape(-1, 2).T
        t = time.time()
        y = separate_stream(lambda c: ref.demucs_segment(sess, c), x, silent=1e-4)
        dt = time.time() - t
        dur = x.shape[1] / 44100
        for j, stem in enumerate(("drums", "bass", "other")):
            ref.resample(y[j], 44100, 22050).astype("<f4").tofile(os.path.join(folder, f"{name}.{stem}.f32"))
        lv = [20 * np.log10(np.sqrt(np.mean(y[j] ** 2)) + 1e-9) for j in range(3)]
        import resource

        rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
        print(
            f"::notice title=Demucs {name}::{dur:.0f} s en {dt:.1f} s (x{dt / dur:.3f}), mémoire max {rss:.0f} Mo | niveaux batterie {lv[0]:.1f} dB, "
            f"basse {lv[1]:.1f} dB, autres {lv[2]:.1f} dB"
        )


if __name__ == "__main__":
    main()
