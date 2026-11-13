"""Notes des instruments (Basic Pitch ONNX via ref.py) sur la basse et les autres instruments de Demucs.

python notes.py <basic_pitch.onnx> <dossier stems>
<nom>.bass.f32 / <nom>.other.f32 (mono 22 050 Hz) → <nom>.bp_bass.u8 / <nom>.bp_other.u8
Même format que le service Android (BasicPitch.java) : attaques puis notes, images × 88, octets 0..255.
"""

import glob
import os
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import ref  # noqa: E402
import onnxruntime as ort  # noqa: E402


def q(a: np.ndarray) -> np.ndarray:
    # Comme Java Math.round(c * 255) : arrondi au plus proche, moitiés vers le haut.
    return np.floor(np.clip(a, 0, 1).astype(np.float32) * np.float32(255) + np.float32(0.5)).astype(np.uint8)


model, folder = sys.argv[1], sys.argv[2]
so = ort.SessionOptions()
so.intra_op_num_threads = 4
sess = ort.InferenceSession(model, so, providers=["CPUExecutionProvider"])
for stem in ("bass", "other"):
    for path in sorted(glob.glob(os.path.join(folder, f"*.{stem}.f32"))):
        x = np.fromfile(path, dtype="<f4")
        t = time.time()
        r = ref.basic_pitch_run(sess, x)
        out = np.concatenate([q(r["onset"]).ravel(), q(r["note"]).ravel()])
        out.tofile(path.replace(f".{stem}.f32", f".bp_{stem}.u8"))
        print(
            f"::notice title=Basic Pitch {os.path.basename(path)}::{r['note'].shape[0]} images, "
            f"{(r['note'] > 0.3).mean() * 100:.2f} % actives, {len(x) / 22050:.0f} s en {time.time() - t:.1f} s"
        )
