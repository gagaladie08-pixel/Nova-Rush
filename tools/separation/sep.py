"""Implémentation de référence de la séparation voix / accompagnement.

Fidèle à sherpa-onnx (Apache 2.0) : STFT façon kaldi-native-fbank (fenêtre de
Hann périodique, remplissage « reflect » si center), modèles UVR-MDX et
Spleeter 2 pistes. Sert à évaluer les modèles et de modèle pour le portage Java.
"""

from __future__ import annotations

import numpy as np
import onnxruntime as ort

SR = 44100


def hann(n: int) -> np.ndarray:
    return (0.5 - 0.5 * np.cos(2 * np.pi * np.arange(n) / n)).astype(np.float32)


def stft(x: np.ndarray, n_fft: int, hop: int, center: bool) -> np.ndarray:
    """x : (n,) → (frames, n_fft//2+1) complexe."""
    if center:
        x = np.pad(x, n_fft // 2, mode="reflect")
    nf = 1 + (len(x) - n_fft) // hop
    w = hann(n_fft)
    out = np.empty((nf, n_fft // 2 + 1), dtype=np.complex64)
    step = 256
    for a in range(0, nf, step):
        b = min(nf, a + step)
        idx = np.arange(n_fft)[None, :] + hop * np.arange(a, b)[:, None]
        out[a:b] = np.fft.rfft(x[idx] * w, axis=1)
    return out


def istft(spec: np.ndarray, n_fft: int, hop: int, center: bool) -> np.ndarray:
    nf = spec.shape[0]
    n = n_fft + (nf - 1) * hop
    w = hann(n_fft)
    out = np.zeros(n, dtype=np.float64)
    den = np.zeros(n, dtype=np.float64)
    frames = np.fft.irfft(spec, n=n_fft, axis=1) * w
    w2 = w.astype(np.float64) ** 2
    for i in range(nf):
        out[i * hop : i * hop + n_fft] += frames[i]
        den[i * hop : i * hop + n_fft] += w2
    nz = den > 0
    out[nz] /= den[nz]
    if center:
        out = out[n_fft // 2 : n - n_fft // 2]
    return out.astype(np.float32)


def _session(path: str, threads: int) -> ort.InferenceSession:
    so = ort.SessionOptions()
    so.intra_op_num_threads = threads
    so.inter_op_num_threads = 1
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    return ort.InferenceSession(path, sess_options=so, providers=["CPUExecutionProvider"])


class Uvr:
    """Modèles UVR-MDX-Net (métadonnées ajoutées par sherpa-onnx)."""

    def __init__(self, path: str, threads: int = 4, batch: int = 0):
        self.s = _session(path, threads)
        m = self.s.get_modelmeta().custom_metadata_map
        self.meta = dict(m)
        self.n_fft = int(m["n_fft"])
        self.hop = int(m["hop_length"])
        self.dim_f = int(m["dim_f"])
        self.dim_t = int(m["dim_t"])
        self.dim_c = int(m["dim_c"])
        self.center = bool(int(m.get("center", 1)))
        self.batch = batch  # 0 = tous les segments d'un bloc en une fois
        self.inp = self.s.get_inputs()[0]
        self.out = self.s.get_outputs()[0]

    def describe(self) -> str:
        return (
            f"n_fft={self.n_fft} hop={self.hop} dim_f={self.dim_f} dim_t={self.dim_t} "
            f"dim_c={self.dim_c} in={self.inp.shape} meta={self.meta}"
        )

    def _run(self, x: np.ndarray) -> np.ndarray:
        if self.batch <= 0 or x.shape[0] <= self.batch:
            return self.s.run([self.out.name], {self.inp.name: x})[0]
        parts = [
            self.s.run([self.out.name], {self.inp.name: x[i : i + self.batch]})[0]
            for i in range(0, x.shape[0], self.batch)
        ]
        return np.concatenate(parts, axis=0)

    def _chunk(self, seg: np.ndarray) -> np.ndarray:
        n_fft, hop, dim_f, dim_t = self.n_fft, self.hop, self.dim_f, self.dim_t
        trim = n_fft // 2
        chunk_size = hop * (dim_t - 1)
        gen = chunk_size - 2 * trim
        L = seg.shape[1]
        pad = gen - L % gen
        padded = np.zeros((2, trim + L + pad + trim), dtype=np.float32)
        padded[:, trim : trim + L] = seg
        starts = list(range(0, L + pad, gen))
        specs = [[stft(padded[c, i : i + chunk_size], n_fft, hop, self.center) for i in starts] for c in range(2)]
        x = np.empty((len(starts), 4, dim_f, dim_t), dtype=np.float32)
        for k in range(len(starts)):
            for c in range(2):
                s = specs[c][k][:, :dim_f].T  # (dim_f, dim_t)
                x[k, 2 * c] = s.real
                x[k, 2 * c + 1] = s.imag
        x = x.reshape(len(starts) * 4 // self.dim_c, self.dim_c, dim_f, dim_t)
        y = self._run(x).reshape(len(starts), 4, dim_f, dim_t)
        out = np.empty((2, len(starts) * gen), dtype=np.float32)
        nb = n_fft // 2 + 1
        for c in range(2):
            pieces = []
            for k in range(len(starts)):
                spec = np.zeros((dim_t, nb), dtype=np.complex64)
                spec[:, :dim_f] = (y[k, 2 * c] + 1j * y[k, 2 * c + 1]).T
                w = istft(spec, n_fft, hop, self.center)
                pieces.append(w[trim : len(w) - trim])
            out[c] = np.concatenate(pieces)
        return out[:, :L]

    def separate(self, x: np.ndarray, progress=None) -> np.ndarray:
        """x : (2, n) à 44,1 kHz → piste principale du modèle (2, n)."""
        n = x.shape[1]
        chunk = 15 * SR
        margin = SR
        if n < chunk:
            chunk = n
        margin = min(margin, chunk)
        segs = []
        skip = 0
        while skip < n:
            a = max(0, skip - margin)
            b = min(skip + chunk + margin, n)
            segs.append((a, b))
            if b == n:
                break
            skip += chunk
        out = []
        for k, (a, b) in enumerate(segs):
            v = self._chunk(x[:, a:b])
            lo = 0 if k == 0 else margin
            hi = v.shape[1] if k == len(segs) - 1 else v.shape[1] - margin
            out.append(v[:, lo:hi])
            if progress:
                progress((k + 1) / len(segs))
        return np.concatenate(out, axis=1)[:, :n]


class Spleeter:
    """Spleeter 2 pistes (export sherpa-onnx) : masque doux voix / accompagnement."""

    N_FFT = 4096
    HOP = 1024

    def __init__(self, vocals: str, accompaniment: str, threads: int = 4):
        self.v = _session(vocals, threads)
        self.a = _session(accompaniment, threads)
        self.meta = dict(self.v.get_modelmeta().custom_metadata_map)

    def describe(self) -> str:
        return f"in={self.v.get_inputs()[0].shape} meta={self.meta}"

    def separate(self, x: np.ndarray, progress=None) -> np.ndarray:
        n = x.shape[1]
        S = [stft(x[c], self.N_FFT, self.HOP, False) for c in range(2)]
        nf = S[0].shape[0]
        NF = ((nf + 511) // 512) * 512
        mag = np.zeros((2, NF, 1024), dtype=np.float32)
        for c in range(2):
            mag[c, :nf] = np.abs(S[c][:, :1024])
        mag = mag.reshape(2, NF // 512, 512, 1024)
        vi, ai = self.v.get_inputs()[0].name, self.a.get_inputs()[0].name
        vs, acs = [], []
        for i in range(NF // 512):  # bloc par bloc (mémoire)
            xi = np.ascontiguousarray(mag[:, i : i + 1])
            vs.append(self.v.run(None, {vi: xi})[0])
            acs.append(self.a.run(None, {ai: xi})[0])
            if progress:
                progress((i + 1) / (NF // 512))
        v = np.concatenate(vs, axis=1).reshape(2, NF, 1024)
        a = np.concatenate(acs, axis=1).reshape(2, NF, 1024)
        den = v * v + a * a + 1e-10
        mask = (v * v + 1e-10 / 2) / den
        out = np.zeros((2, n), dtype=np.float32)
        for c in range(2):
            m = np.zeros((nf, self.N_FFT // 2 + 1), dtype=np.float32)
            m[:, :1024] = mask[c, :nf]
            w = istft(S[c] * m, self.N_FFT, self.HOP, False)
            out[c, : min(n, len(w))] = w[:n]
        return out
