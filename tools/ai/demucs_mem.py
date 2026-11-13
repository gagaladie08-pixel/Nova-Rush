"""Pic mémoire de Demucs selon les options d'onnxruntime (comme le téléphone).

python demucs_mem.py <htdemucs.onnx> <arena 0|1> <motifs mémoire 0|1>
Deux segments de 7,8 s ; affiche la mémoire max du processus et le temps par segment.
"""

import os
import resource
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import ref  # noqa: E402
import onnxruntime as ort  # noqa: E402

model, arena, pattern = sys.argv[1], sys.argv[2] == "1", sys.argv[3] == "1"
so = ort.SessionOptions()
so.intra_op_num_threads = 4
so.enable_cpu_mem_arena = arena
so.enable_mem_pattern = pattern
sess = ort.InferenceSession(model, so, providers=["CPUExecutionProvider"])
rss0 = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
rng = np.random.default_rng(1)
times = []
for _ in range(2):
    seg = rng.standard_normal((2, ref.DEMUCS_SEGMENT)) * 0.3
    t = time.time()
    ref.demucs_segment(sess, seg)
    times.append(time.time() - t)
rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
print(
    f"::notice title=Demucs mémoire arena={int(arena)} motifs={int(pattern)}::max {rss:.0f} Mo "
    f"(après chargement {rss0:.0f} Mo), {times[0]:.1f} s puis {times[1]:.1f} s par segment de 7,8 s"
)
