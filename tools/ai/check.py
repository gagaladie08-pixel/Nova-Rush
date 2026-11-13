"""Vérifications de la phase 0 (CI) : fidélité des exports et vitesse.

python check.py demucs-ref <audio> <out.npy>   (environnement « demucs officiel »)
python check.py demucs <models> <audio> <ref.npy>
python check.py beat <models> <audio>
python check.py crepe <models> <audio>
python check.py basicpitch <models> <audio>

Chaque résultat est émis en annotation GitHub (::notice).
"""

from __future__ import annotations

import os
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import ref  # noqa: E402

EXCERPT_S = float(os.environ.get("EXCERPT_S", "40"))


def notice(title: str, msg: str) -> None:
    print(f"::notice title={title}::{msg}", flush=True)


def load_stereo44(path: str, seconds: float = EXCERPT_S) -> np.ndarray:
    import soundfile as sf

    x, sr = sf.read(path, dtype="float64", always_2d=True)
    x = x.T
    if x.shape[0] == 1:
        x = np.repeat(x, 2, axis=0)
    x = x[:2]
    if sr != 44100:
        x = ref.resample(x, sr, 44100)
    return x[:, : int(seconds * 44100)]


def sdr(ref_sig: np.ndarray, est: np.ndarray) -> float:
    n = min(ref_sig.shape[-1], est.shape[-1])
    r, e = ref_sig[..., :n], est[..., :n]
    return float(10 * np.log10(np.sum(r**2) / max(np.sum((r - e) ** 2), 1e-20)))


def session(path: str, threads: int = 4):
    import onnxruntime as ort

    so = ort.SessionOptions()
    so.intra_op_num_threads = threads
    so.inter_op_num_threads = 1
    return ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])


def size_mb(p: str) -> float:
    return os.path.getsize(p) / 1e6


def demucs_ref(audio: str, out: str) -> None:
    """Séparation par le Demucs officiel (PyTorch), shifts=0."""
    import torch
    from demucs.apply import apply_model
    from demucs.pretrained import get_model

    torch.set_num_threads(4)
    model = get_model("htdemucs").eval()
    x = load_stereo44(audio)
    ref_mono = x.mean(0)
    mu, sd = ref_mono.mean(), ref_mono.std() + 1e-8
    t = time.time()
    with torch.no_grad():
        y = apply_model(model, torch.tensor((x - mu) / sd, dtype=torch.float32)[None], shifts=0, split=True, overlap=0.25)[0]
    dt = time.time() - t
    y = y.numpy().astype(np.float64) * sd + mu
    np.save(out, y)
    notice("demucs pytorch", f"{x.shape[1] / 44100:.0f} s en {dt:.1f} s (x{dt / (x.shape[1] / 44100):.3f}), sources {model.sources}")


def check_demucs(models: str, audio: str, ref_npy: str) -> None:
    x = load_stereo44(audio)
    yref = np.load(ref_npy)
    dur = x.shape[1] / 44100
    for name in ("htdemucs.fp32.onnx", "htdemucs.onnx"):
        p = os.path.join(models, name)
        s = session(p)
        t = time.time()
        y = ref.demucs_separate(s, x)
        dt = time.time() - t
        parts = ", ".join(f"{src} {sdr(yref[i], y[i]):.1f} dB" for i, src in enumerate(ref.DEMUCS_SOURCES))
        notice(
            f"demucs {name}",
            f"{size_mb(p):.1f} Mo | fidélité au PyTorch : {parts} | {dur:.0f} s en {dt:.1f} s (x{dt / dur:.3f})",
        )


def check_beat(models: str, audio: str) -> None:
    import torch
    from beat_this.inference import Audio2Frames
    from beat_this.model.postprocessor import Postprocessor

    torch.set_num_threads(4)
    x = load_stereo44(audio, seconds=float(os.environ.get("BEAT_S", "75"))).mean(0)
    x22 = ref.resample(x, 44100, 22050).astype(np.float32)
    dur = len(x22) / 22050
    spect_np = ref.beat_logmel(x22)
    post = Postprocessor("minimal")
    for name in ("small0", "final0"):
        a2f = Audio2Frames(checkpoint_path=name, device="cpu")
        spect_t = a2f.spect(torch.tensor(x22)).numpy()
        mel_err = float(np.abs(spect_t - spect_np).max())
        tb, td = a2f.spect2frames(torch.tensor(spect_t))
        bt_ref, dt_ref = post(tb, td)
        for suffix in ("fp32.onnx", "onnx"):
            p = os.path.join(models, f"beat_this_{name}.{suffix}")
            s = session(p)
            t = time.time()
            ob, od = ref.beat_frames(s, spect_np)
            dt = time.time() - t
            bt, dtt = ref.beat_postprocess(ob, od)
            nb, nd = ref.beat_frames(s, spect_t.astype(np.float32))
            net_err = float(max(np.abs(nb - tb.numpy()).max(), np.abs(nd - td.numpy()).max()))
            logit_err = float(max(np.abs(ob - tb.numpy()).max(), np.abs(od - td.numpy()).max()))
            same_b = len(bt) == len(bt_ref) and np.allclose(bt, bt_ref, atol=0.021)
            same_d = len(dtt) == len(dt_ref) and np.allclose(dtt, dt_ref, atol=0.021)
            ibi = np.diff(bt)
            bpm = 60 / np.median(ibi) if len(ibi) else 0
            notice(
                f"beat {name}.{suffix}",
                f"{size_mb(p):.1f} Mo | mel écart max {mel_err:.2e} | logits écart max {logit_err:.3f} (réseau seul {net_err:.3f}) | "
                f"temps {len(bt)}/{len(bt_ref)} {'identiques' if same_b else 'DIFFÉRENTS'}, "
                f"1ers temps {len(dtt)}/{len(dt_ref)} {'identiques' if same_d else 'DIFFÉRENTS'} | "
                f"tempo {bpm:.1f} bpm | {dur:.0f} s en {dt:.2f} s (x{dt / dur:.4f})",
            )


def check_crepe(models: str, audio: str) -> None:
    # Voyelle chantée synthétique (f0 connue, vibrato, glissandos) + bruit.
    sr = ref.CREPE_SR
    t = np.arange(int(20 * sr)) / sr
    f0 = 220 * 2 ** ((np.floor(t / 0.5) % 7) * 2 / 12) * (1 + 0.006 * np.sin(2 * np.pi * 5.5 * t))
    phase = 2 * np.pi * np.cumsum(f0) / sr
    sig = sum((0.6 / k) * np.sin(k * phase) * (1.0 if k < 6 else 0.4) for k in range(1, 12))
    sig = sig * 0.3 + 0.01 * np.random.default_rng(0).standard_normal(len(t))
    for name in ("tiny", "small"):
        p = os.path.join(models, f"crepe_{name}.onnx")
        s = session(p)
        tt = time.time()
        prob = ref.crepe_run(s, sig)
        dt = time.time() - tt
        f, conf = ref.crepe_decode(prob)
        truth = np.interp(np.arange(len(f)) * ref.CREPE_HOP / sr, t, f0)
        cents = 1200 * np.abs(np.log2(f / truth))
        ok = float(np.mean(cents[5:-5] < 50))
        med = float(np.median(cents[5:-5]))
        notice(
            f"crepe {name}",
            f"{size_mb(p):.1f} Mo | justesse (±50 cents) {ok * 100:.1f} %, écart médian {med:.1f} cents, confiance médiane {np.median(conf):.2f} | "
            f"20 s en {dt:.2f} s (x{dt / 20:.4f})",
        )


def check_basic_pitch(models: str, audio: str) -> None:
    x = load_stereo44(audio).mean(0)
    x22 = ref.resample(x, 44100, 22050).astype(np.float32)
    dur = len(x22) / 22050
    p = os.path.join(models, "basic_pitch.onnx")
    s = session(p)
    t = time.time()
    out = ref.basic_pitch_run(s, x22)
    dt = time.time() - t
    notice(
        "basic pitch",
        f"{size_mb(p):.2f} Mo | {dur:.0f} s en {dt:.2f} s (x{dt / dur:.4f}) | images {out['note'].shape[0]}, notes actives {(out['note'] > 0.5).mean() * 100:.1f} %",
    )


def check_uvr16(models: str, audio: str) -> None:
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "separation"))
    from sep import Uvr

    x = load_stereo44(audio, seconds=30).astype(np.float32)
    a = Uvr(os.path.join(models, "uvr.fp32.onnx"), threads=4, batch=1).separate(x)
    t = time.time()
    b = Uvr(os.path.join(models, "uvr_1_9703.onnx"), threads=4, batch=1).separate(x)
    dt = time.time() - t
    notice(
        "uvr float16",
        f"{size_mb(os.path.join(models, 'uvr.fp32.onnx')):.1f} Mo → {size_mb(os.path.join(models, 'uvr_1_9703.onnx')):.1f} Mo | "
        f"fidélité voix {sdr(a, b):.1f} dB | 30 s en {dt:.1f} s",
    )


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "demucs-ref":
        demucs_ref(sys.argv[2], sys.argv[3])
    elif cmd == "demucs":
        check_demucs(*sys.argv[2:5])
    elif cmd == "beat":
        check_beat(*sys.argv[2:4])
    elif cmd == "crepe":
        check_crepe(*sys.argv[2:4])
    elif cmd == "uvr16":
        check_uvr16(*sys.argv[2:4])
    elif cmd == "basicpitch":
        check_basic_pitch(*sys.argv[2:4])
    else:
        raise SystemExit(__doc__)
