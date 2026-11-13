/** Outils de traitement du signal sans dépendance. */

const hannCache = new Map<number, Float32Array>();
export function hann(n: number): Float32Array {
  let w = hannCache.get(n);
  if (!w) {
    w = new Float32Array(n);
    for (let i = 0; i < n; i++) w[i] = 0.5 - 0.5 * Math.cos((2 * Math.PI * i) / (n - 1));
    hannCache.set(n, w);
  }
  return w;
}

interface FftTables {
  rev: Uint32Array;
  cos: Float32Array;
  sin: Float32Array;
}
const tables = new Map<number, FftTables>();
function getTables(n: number): FftTables {
  let t = tables.get(n);
  if (t) return t;
  const bits = Math.log2(n) | 0;
  const rev = new Uint32Array(n);
  for (let i = 0; i < n; i++) {
    let r = 0;
    for (let b = 0; b < bits; b++) r |= ((i >> b) & 1) << (bits - 1 - b);
    rev[i] = r;
  }
  const cos = new Float32Array(n / 2);
  const sin = new Float32Array(n / 2);
  for (let i = 0; i < n / 2; i++) {
    cos[i] = Math.cos((2 * Math.PI * i) / n);
    sin[i] = -Math.sin((2 * Math.PI * i) / n);
  }
  t = { rev, cos, sin };
  tables.set(n, t);
  return t;
}

/** FFT complexe en place (radix 2). n doit être une puissance de 2. */
export function fft(re: Float32Array, im: Float32Array): void {
  const n = re.length;
  const { rev, cos, sin } = getTables(n);
  for (let i = 0; i < n; i++) {
    const j = rev[i];
    if (j > i) {
      let tmp = re[i]; re[i] = re[j]; re[j] = tmp;
      tmp = im[i]; im[i] = im[j]; im[j] = tmp;
    }
  }
  for (let size = 2; size <= n; size <<= 1) {
    const half = size >> 1;
    const step = n / size;
    for (let start = 0; start < n; start += size) {
      for (let k = 0; k < half; k++) {
        const wr = cos[k * step];
        const wi = sin[k * step];
        const a = start + k;
        const b = a + half;
        const xr = re[b] * wr - im[b] * wi;
        const xi = re[b] * wi + im[b] * wr;
        re[b] = re[a] - xr;
        im[b] = im[a] - xi;
        re[a] += xr;
        im[a] += xi;
      }
    }
  }
}

/** Spectre d'amplitude d'une fenêtre de `n` échantillons commençant à `start`. */
export function magnitudeAt(
  samples: Float32Array,
  start: number,
  n: number,
  re: Float32Array = new Float32Array(n),
  im: Float32Array = new Float32Array(n),
  out: Float32Array = new Float32Array(n / 2),
): Float32Array {
  const w = hann(n);
  for (let i = 0; i < n; i++) {
    const idx = start + i;
    re[i] = idx >= 0 && idx < samples.length ? samples[idx] * w[i] : 0;
    im[i] = 0;
  }
  fft(re, im);
  for (let k = 0; k < n / 2; k++) out[k] = Math.hypot(re[k], im[k]);
  return out;
}

export function percentile(values: ArrayLike<number>, p: number): number {
  if (values.length === 0) return 0;
  const arr = Array.from(values).sort((a, b) => a - b);
  const idx = Math.min(arr.length - 1, Math.max(0, Math.round((arr.length - 1) * p)));
  return arr[idx];
}

export function median(values: ArrayLike<number>): number {
  return percentile(values, 0.5);
}

/** Moyenne glissante centrée (fenêtre ±w) via sommes préfixes. */
export function movingAverage(x: Float32Array, w: number): Float32Array {
  const n = x.length;
  const prefix = new Float64Array(n + 1);
  for (let i = 0; i < n; i++) prefix[i + 1] = prefix[i] + x[i];
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const a = Math.max(0, i - w);
    const b = Math.min(n, i + w + 1);
    out[i] = (prefix[b] - prefix[a]) / (b - a);
  }
  return out;
}

export function midiToHz(m: number): number {
  return 440 * Math.pow(2, (m - 69) / 12);
}

/** Petit générateur pseudo-aléatoire déterministe (mulberry32). */
export function rng(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function hashString(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}
