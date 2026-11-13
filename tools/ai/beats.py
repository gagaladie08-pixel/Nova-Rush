"""Temps et mesures (Beat This! ONNX via ref.py) pour chaque mix de la CI.

python beats.py <modèle.onnx> <dossier stems>  →  <nom>.beats.f32
Même format que le service Android : [nb, nd, temps…, premiers temps…].
"""

import glob
import os
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import ref  # noqa: E402
import onnxruntime as ort  # noqa: E402

model, folder = sys.argv[1], sys.argv[2]
sess = ort.InferenceSession(model, providers=["CPUExecutionProvider"])
for path in sorted(glob.glob(os.path.join(folder, "*.mix.f32"))):
    x = np.fromfile(path, dtype="<f4")
    t = time.time()
    b, d = ref.beat_postprocess(*ref.beat_frames(sess, ref.beat_logmel(x)))
    out = np.concatenate([[len(b), len(d)], b, d]).astype("<f4")
    out.tofile(path.replace(".mix.f32", ".beats.f32"))
    bpm = 60 / np.median(np.diff(b)) if len(b) > 1 else 0
    print(f"::notice title=Beat This! {os.path.basename(path)}::{len(b)} temps, {len(d)} mesures, {bpm:.1f} bpm, {time.time() - t:.1f} s")
