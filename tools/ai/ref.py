"""Implémentations de référence (numpy + onnxruntime) des 4 IA de l'analyse.

Elles servent de modèle exact aux portages Java (téléphone) et TypeScript
(appli), et sont vérifiées en CI contre les implémentations d'origine
(PyTorch) par tools/ai/check.py.

- Demucs v4 (htdemucs) : le réseau ONNX ne contient pas la STFT ; spectre,
  masque et iSTFT sont faits ici (cf. sevagh/demucs.onnx).
- Beat This! : spectrogramme log-mel (torchaudio) + réseau ONNX par tranches
  de 1500 images (30 s) + post-traitement « minimal ».
- CREPE (onnxcrepe) : trames de 1024 échantillons à 16 kHz, moyenne pondérée
  autour du maximum.
- Basic Pitch : fenêtres de 2 s avec recouvrement de 30 images.
"""

from __future__ import annotations

import math

import numpy as np

# --------------------------------------------------------------------------
# STFT façon torch (fenêtre de Hann périodique, center + reflect)
# --------------------------------------------------------------------------


def hann(n: int) -> np.ndarray:
    return (0.5 - 0.5 * np.cos(2 * np.pi * np.arange(n) / n)).astype(np.float64)


def reflect_pad(x: np.ndarray, left: int, right: int) -> np.ndarray:
    """Comme torch reflect (sans répéter le bord). Gère les signaux courts par répétition."""
    out = x
    while left > 0 or right > 0:
        n = out.shape[-1]
        lp = min(left, n - 1)
        rp = min(right, n - 1)
        out = np.pad(out, [(0, 0)] * (out.ndim - 1) + [(lp, rp)], mode="reflect")
        left -= lp
        right -= rp
    return out


def stft(x: np.ndarray, n_fft: int, hop: int, normalized: bool) -> np.ndarray:
    """x : (..., n) → (..., n_fft//2+1, frames), center=True, reflect."""
    w = hann(n_fft)
    xp = reflect_pad(x.astype(np.float64), n_fft // 2, n_fft // 2)
    n = xp.shape[-1]
    frames = 1 + (n - n_fft) // hop
    idx = np.arange(n_fft)[None, :] + hop * np.arange(frames)[:, None]
    seg = xp[..., idx] * w  # (..., frames, n_fft)
    z = np.fft.rfft(seg, axis=-1)
    if normalized:
        z = z / math.sqrt(n_fft)
    return np.swapaxes(z, -1, -2)


def istft(z: np.ndarray, n_fft: int, hop: int, length: int, normalized: bool) -> np.ndarray:
    """Inverse de stft (center=True), tronqué/complété à `length`."""
    w = hann(n_fft)
    frames = z.shape[-1]
    zz = np.swapaxes(z, -1, -2)
    if normalized:
        zz = zz * math.sqrt(n_fft)
    seg = np.fft.irfft(zz, n=n_fft, axis=-1) * w  # (..., frames, n_fft)
    n = n_fft + hop * (frames - 1)
    out = np.zeros(z.shape[:-2] + (n,))
    den = np.zeros(n)
    for t in range(frames):
        out[..., t * hop : t * hop + n_fft] += seg[..., t, :]
        den[t * hop : t * hop + n_fft] += w * w
    out = out / np.where(den > 1e-11, den, 1.0)
    out = out[..., n_fft // 2 :]
    if out.shape[-1] < length:
        out = np.pad(out, [(0, 0)] * (out.ndim - 1) + [(0, length - out.shape[-1])])
    return out[..., :length]


# --------------------------------------------------------------------------
# Demucs v4 (htdemucs)
# --------------------------------------------------------------------------

DEMUCS_SR = 44100
DEMUCS_SEGMENT = 343980  # int(7.8 * 44100)
DEMUCS_SOURCES = ("drums", "bass", "other", "vocals")
_NFFT = 4096
_HL = 1024


def demucs_spec(x: np.ndarray) -> np.ndarray:
    """x : (2, L) → (2, 2048, le) complexe (standalone_spec)."""
    L = x.shape[-1]
    le = int(math.ceil(L / _HL))
    pad = _HL // 2 * 3
    xp = reflect_pad(x, pad, pad + le * _HL - L)
    z = stft(xp, _NFFT, _HL, normalized=True)[..., :-1, :]
    assert z.shape[-1] == le + 4, z.shape
    return z[..., 2 : 2 + le]


def demucs_ispec(z: np.ndarray, length: int) -> np.ndarray:
    """z : (..., 2048, le) → (..., length) (standalone_ispec)."""
    z = np.concatenate([z, np.zeros(z.shape[:-2] + (1, z.shape[-1]), z.dtype)], axis=-2)
    z = np.pad(z, [(0, 0)] * (z.ndim - 1) + [(2, 2)])
    pad = _HL // 2 * 3
    le = _HL * int(math.ceil(length / _HL)) + 2 * pad
    x = istft(z, _NFFT, _HL, le, normalized=True)
    return x[..., pad : pad + length]


def demucs_segment(sess, seg: np.ndarray) -> np.ndarray:
    """seg : (2, SEGMENT) → (4 sources, 2, SEGMENT)."""
    z = demucs_spec(seg)  # (2, 2048, 336)
    mag = np.stack([z[0].real, z[0].imag, z[1].real, z[1].imag])[None].astype(np.float32)
    ins = sess.get_inputs()
    out_x, out_xt = sess.run(None, {ins[0].name: seg[None].astype(np.float32), ins[1].name: mag})
    S = out_x.shape[1]
    ox = out_x[0].astype(np.float64).reshape(S, 2, 2, z.shape[-2], z.shape[-1])
    zc = ox[:, :, 0] + 1j * ox[:, :, 1]  # (S, 2, F, T)
    x = demucs_ispec(zc, DEMUCS_SEGMENT)
    return out_xt[0].astype(np.float64) + x


def demucs_separate(sess, mix: np.ndarray, overlap: float = 0.25, progress=None) -> np.ndarray:
    """mix : (2, n) à 44,1 kHz → (4, 2, n). Comme demucs.apply.apply_model(shifts=0, split=True)."""
    n = mix.shape[-1]
    ref = mix.mean(0)
    mu, sd = ref.mean(), ref.std() + 1e-8
    mixn = (mix - mu) / sd
    seg = DEMUCS_SEGMENT
    stride = int((1 - overlap) * seg)
    weight = np.concatenate([np.arange(1, seg // 2 + 1), np.arange(seg - seg // 2, 0, -1)]).astype(np.float64)
    weight /= weight.max()
    out = np.zeros((len(DEMUCS_SOURCES), 2, n))
    sw = np.zeros(n)
    offsets = list(range(0, n, stride))
    for k, off in enumerate(offsets):
        length = min(seg, n - off)
        delta = seg - length
        start = off - delta // 2
        end = start + seg
        cs, ce = max(0, start), min(n, end)
        chunk = np.zeros((2, seg))
        chunk[:, cs - start : cs - start + (ce - cs)] = mixn[:, cs:ce]
        y = demucs_segment(sess, chunk)
        d = seg - length
        y = y[..., d // 2 : seg - (d - d // 2)]  # center_trim
        out[..., off : off + length] += weight[:length] * y
        sw[off : off + length] += weight[:length]
        if progress:
            progress((k + 1) / len(offsets))
    out /= sw
    return out * sd + mu


# --------------------------------------------------------------------------
# Beat This!
# --------------------------------------------------------------------------

BEAT_SR = 22050
BEAT_FPS = 50
BEAT_CHUNK = 1500
BEAT_BORDER = 6


def _hz_to_mel_slaney(f):
    f = np.asarray(f, dtype=np.float64)
    f_sp = 200.0 / 3
    min_log_hz = 1000.0
    min_log_mel = min_log_hz / f_sp
    logstep = math.log(6.4) / 27.0
    return np.where(f >= min_log_hz, min_log_mel + np.log(np.maximum(f, 1e-10) / min_log_hz) / logstep, f / f_sp)


def _mel_to_hz_slaney(m):
    m = np.asarray(m, dtype=np.float64)
    f_sp = 200.0 / 3
    min_log_hz = 1000.0
    min_log_mel = min_log_hz / f_sp
    logstep = math.log(6.4) / 27.0
    return np.where(m >= min_log_mel, min_log_hz * np.exp(logstep * (m - min_log_mel)), f_sp * m)


def mel_filterbank(n_freqs=513, f_min=30.0, f_max=11000.0, n_mels=128, sr=22050) -> np.ndarray:
    """torchaudio.functional.melscale_fbanks(norm=None, mel_scale='slaney') : (n_freqs, n_mels)."""
    all_freqs = np.linspace(0, sr // 2, n_freqs)
    m_pts = np.linspace(_hz_to_mel_slaney(f_min), _hz_to_mel_slaney(f_max), n_mels + 2)
    f_pts = _mel_to_hz_slaney(m_pts)
    f_diff = f_pts[1:] - f_pts[:-1]
    slopes = f_pts[None, :] - all_freqs[:, None]
    down = -slopes[:, :-2] / f_diff[:-1]
    up = slopes[:, 2:] / f_diff[1:]
    return np.maximum(0.0, np.minimum(down, up))


def beat_logmel(x: np.ndarray) -> np.ndarray:
    """x : mono 22 050 Hz → (frames, 128) = log1p(1000 · mel(|STFT|)) (LogMelSpect de Beat This!)."""
    z = stft(x, 1024, 441, normalized=False)  # (513, frames)
    w = hann(1024)
    mag = np.abs(z) / math.sqrt((w * w).sum())  # normalized="frame_length"
    mel = mel_filterbank().T @ mag  # (128, frames)
    return np.log1p(1000.0 * mel).T.astype(np.float32)


def beat_frames(sess, spect: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Logits par image (50 i/s) : tranches de 1500 avec bords de 6, « keep_first »."""
    T = spect.shape[0]
    C, B = BEAT_CHUNK, BEAT_BORDER
    starts = list(range(-B, T - B, C - 2 * B))
    if T > C - 2 * B:
        starts[-1] = T - (C - B)
    beat = np.full(T, -1000.0, np.float32)
    down = np.full(T, -1000.0, np.float32)
    name = sess.get_inputs()[0].name
    # keep_first : on traite à l'envers pour que les premières tranches l'emportent.
    for s in reversed(starts):
        chunk = np.zeros((C, spect.shape[1]), np.float32)
        a, b = max(s, 0), min(s + C, T)
        chunk[a - s : a - s + (b - a)] = spect[a:b]
        ob, od = sess.run(None, {name: chunk[None]})
        ob, od = ob[0], od[0]
        lo, hi = s + B, s + C - B
        ia, ib = max(lo, 0), min(hi, T)
        beat[ia:ib] = ob[ia - s : ib - s]
        down[ia:ib] = od[ia - s : ib - s]
    return beat, down


def beat_postprocess(beat: np.ndarray, down: np.ndarray, fps: int = BEAT_FPS) -> tuple[np.ndarray, np.ndarray]:
    """Post-traitement « minimal » de Beat This!."""

    def peaks(x):
        T = len(x)
        pad = np.concatenate([np.full(3, -np.inf), x, np.full(3, -np.inf)])
        mx = np.max(np.stack([pad[i : i + T] for i in range(7)]), axis=0)
        p = np.where((x == mx) & (x > 0))[0]
        return dedup(p)

    def dedup(p, width=1):
        if len(p) == 0:
            return p.astype(np.float64)
        out = []
        cur = [p[0]]
        for v in p[1:]:
            if v - cur[-1] <= width:
                cur.append(v)
            else:
                out.append(np.mean(cur))
                cur = [v]
        out.append(np.mean(cur))
        return np.array(out)

    bt = peaks(beat) / fps
    dt = peaks(down) / fps
    if len(bt):
        dt = np.array([bt[np.argmin(np.abs(bt - d))] for d in dt])
    return bt, np.unique(dt)


# --------------------------------------------------------------------------
# CREPE
# --------------------------------------------------------------------------

CREPE_SR = 16000
CREPE_HOP = 160  # 10 ms


def crepe_cents(bins: np.ndarray) -> np.ndarray:
    return 20.0 * bins + 1997.3794084376191


def crepe_run(sess, x: np.ndarray, batch: int = 512) -> np.ndarray:
    """x : mono 16 kHz → probabilités (frames, 360). Trames centrées (pad 512)."""
    xp = np.pad(x.astype(np.float32), (512, 512))
    n = 1 + len(x) // CREPE_HOP
    out = np.zeros((n, 360), np.float32)
    name = sess.get_inputs()[0].name
    for a in range(0, n, batch):
        b = min(n, a + batch)
        idx = np.arange(1024)[None, :] + CREPE_HOP * np.arange(a, b)[:, None]
        out[a:b] = sess.run(None, {name: xp[idx]})[0]
    return out


def crepe_decode(prob: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Moyenne pondérée sur ±4 cases autour du maximum → (f0 Hz, confiance)."""
    centre = prob.argmax(axis=1)
    f0 = np.zeros(len(prob))
    for t, c in enumerate(centre):
        a, b = max(0, c - 4), min(360, c + 5)
        p = np.maximum(prob[t, a:b], 0)
        cents = (crepe_cents(np.arange(a, b)) * p).sum() / max(p.sum(), 1e-9)
        f0[t] = 10.0 * 2 ** (cents / 1200.0)
    return f0, prob.max(axis=1)


# --------------------------------------------------------------------------
# Basic Pitch
# --------------------------------------------------------------------------

BP_SR = 22050
BP_HOP = 256
BP_WIN = 43844  # 2 s − 256
BP_OVERLAP_FRAMES = 30


def basic_pitch_run(sess, x: np.ndarray) -> dict[str, np.ndarray]:
    """x : mono 22 050 Hz → {'note': (T, 88), 'onset': (T, 88), 'contour': (T, 264)}."""
    overlap_len = BP_OVERLAP_FRAMES * BP_HOP
    hop = BP_WIN - overlap_len
    xp = np.concatenate([np.zeros(overlap_len // 2, np.float32), x.astype(np.float32)])
    outs: dict[str, list] = {"note": [], "onset": [], "contour": []}
    name = sess.get_inputs()[0].name
    names = [o.name for o in sess.get_outputs()]
    for i in range(0, len(xp), hop):
        w = xp[i : i + BP_WIN]
        if len(w) < BP_WIN:
            w = np.pad(w, (0, BP_WIN - len(w)))
        res = dict(zip(names, sess.run(None, {name: w[None, :, None]})))
        for k, v in res.items():
            v = v[0]
            key = "contour" if v.shape[-1] == 264 else None
            if key is None:
                # Les deux sorties à 88 cases : « StatefulPartitionedCall:1 » = note, « :2 » = onset.
                key = "note" if k.endswith(":1") else "onset"
            outs[key].append(v)
    n_olap = BP_OVERLAP_FRAMES // 2
    n_frames = int(math.floor(len(x) * (22050 // BP_HOP) / BP_SR))
    res = {}
    for k, lst in outs.items():
        a = np.stack(lst)[:, n_olap:-n_olap]
        res[k] = a.reshape(-1, a.shape[-1])[:n_frames]
    return res


def resample(x: np.ndarray, sr_in: int, sr_out: int) -> np.ndarray:
    from scipy.signal import resample_poly

    g = math.gcd(sr_in, sr_out)
    return resample_poly(x, sr_out // g, sr_in // g, axis=-1)
