"""Hauteur de la voix (CREPE tiny ONNX via ref.py) pour chaque piste vocale de la CI.

python pitch.py <crepe.onnx> <dossier stems>  →  <nom>.pitch.f32
Même format que le service Android : [n, f0…, confiance…] (10 ms).
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
so = ort.SessionOptions()
so.intra_op_num_threads = 4
sess = ort.InferenceSession(model, so, providers=["CPUExecutionProvider"])
for path in sorted(glob.glob(os.path.join(folder, "*.voc.f32"))):
    x = np.fromfile(path, dtype="<f4").astype(np.float64)
    x16 = ref.resample(x, 22050, ref.CREPE_SR).astype(np.float32)
    t = time.time()
    f0, conf = ref.crepe_decode(ref.crepe_run(sess, x16))
    out = np.concatenate([[len(f0)], f0, conf]).astype("<f4")
    out.tofile(path.replace(".voc.f32", ".pitch.f32"))
    voiced = conf >= 0.5
    print(
        f"::notice title=CREPE {os.path.basename(path)}::{len(f0)} trames, {voiced.mean() * 100:.0f} % sûres, "
        f"{len(x16) / 16000:.0f} s en {time.time() - t:.1f} s"
    )
