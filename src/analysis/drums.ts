import { fft, hann, movingAverage, percentile } from './dsp';

/**
 * Coups de batterie sur la piste « batterie » isolée par Demucs.
 *
 * Flux spectral par bandes : grave (grosse caisse), médium (caisse claire),
 * aigu (charleston, cymbales). Une cymbale « crash » se distingue d'un coup
 * de charleston par la tenue de son énergie aiguë.
 */

export const KICK = 0;
export const SNARE = 1;
export const HAT = 2;
export const CRASH = 3;
export type DrumKind = 0 | 1 | 2 | 3;

export interface DrumHit {
  t: number;
  k: DrumKind;
  /** Force relative (≈ 0..2). */
  s: number;
}

const BANDS: [number, number][] = [
  [30, 150],
  [180, 3500],
  [5500, 11000],
];

/** Batterie absente : piste Demucs sous −50 dB (bruit de séparation seulement). */
const DRUMS_GATE = 0.00316;

export function drumHits(x: Float32Array, sr: number): DrumHit[] {
  let e2 = 0;
  for (let i = 0; i < x.length; i += 4) e2 += x[i] * x[i];
  if (Math.sqrt(e2 / Math.max(1, Math.ceil(x.length / 4))) < DRUMS_GATE) return [];
  const n = 1024;
  const hop = 256;
  const hopT = hop / sr;
  const frames = Math.floor((x.length - n) / hop) + 1;
  if (frames < 20) return [];
  const w = hann(n);
  const re = new Float32Array(n);
  const im = new Float32Array(n);
  const bin = sr / n;
  const ranges = BANDS.map(([a, b]) => [Math.max(1, Math.floor(a / bin)), Math.min(n / 2 - 1, Math.ceil(b / bin))]);
  const prev = ranges.map(([a, b]) => new Float32Array(b - a + 1));
  const flux = ranges.map(() => new Float32Array(frames));
  const energy = ranges.map(() => new Float32Array(frames));
  for (let f = 0; f < frames; f++) {
    const s0 = f * hop;
    for (let i = 0; i < n; i++) {
      re[i] = x[s0 + i] * w[i];
      im[i] = 0;
    }
    fft(re, im);
    for (let b = 0; b < ranges.length; b++) {
      const [a, z] = ranges[b];
      let fl = 0;
      let e = 0;
      for (let k = a; k <= z; k++) {
        const m = Math.hypot(re[k], im[k]);
        e += m * m;
        // Flux linéaire : une vraie grosse caisse domine nettement les
        // débords d'autres instruments (la compression log les rapprocherait).
        const d = m - prev[b][k - a];
        if (d > 0) fl += d;
        prev[b][k - a] = m;
      }
      flux[b][f] = f === 0 ? 0 : fl / (z - a + 1);
      energy[b][f] = e;
    }
  }
  const t0 = (n * 0.5) / sr + 0.012;
  // Pics de chaque bande (seuil adaptatif, p95 = 1).
  const peaksOf = (odf: Float32Array) => {
    const p95 = percentile(odf.filter((_, i) => i % 3 === 0), 0.95);
    if (!(p95 > 1e-9)) return { idx: [] as number[], v: new Float32Array(odf.length) };
    const v = new Float32Array(odf.length);
    for (let i = 0; i < odf.length; i++) v[i] = odf[i] / p95;
    const avg = movingAverage(v, Math.round(0.2 / hopT));
    const half = Math.max(1, Math.round(0.03 / hopT));
    const idx: number[] = [];
    for (let i = half; i < v.length - half; i++) {
      if (v[i] < 0.3 || v[i] <= avg[i] * 1.4 + 0.08) continue;
      let max = true;
      for (let j = i - half; j <= i + half; j++) {
        if (v[j] > v[i] || (v[j] === v[i] && j < i)) {
          max = false;
          break;
        }
      }
      if (max && (!idx.length || i - idx[idx.length - 1] >= Math.round(0.05 / hopT))) idx.push(i);
    }
    return { idx, v };
  };
  const bands = flux.map(peaksOf);
  const lag = Math.round(0.03 / hopT);
  // Attaques : réunion des pics des trois bandes (fusionnés à ±30 ms).
  const all = [...new Set(bands.flatMap((b) => b.idx))].sort((a, b) => a - b);
  const onsets: number[] = [];
  for (const i of all) {
    const last = onsets[onsets.length - 1];
    if (last !== undefined && i - last <= lag) continue;
    onsets.push(i);
  }
  /** Saut normalisé d'une bande autour de l'attaque (max sur ±30 ms). */
  const at = (b: number, i: number) => {
    let m = 0;
    for (let j = Math.max(0, i - lag); j <= Math.min(frames - 1, i + lag); j++) m = Math.max(m, bands[b].v[j]);
    return m;
  };
  const eHigh = energy[2];
  // Coups trop faibles (queues, fuites d'autres instruments) : 26 dB sous les coups forts.
  const total = new Float32Array(frames);
  for (let f = 0; f < frames; f++) total[f] = energy[0][f] + energy[1][f] + energy[2][f];
  const loud = percentile(total.filter((_, i) => i % 2 === 0), 0.99);
  const hits: DrumHit[] = [];
  for (const i of onsets) {
    let peakE = 0;
    for (let j = i; j <= Math.min(frames - 1, i + lag); j++) peakE = Math.max(peakE, total[j]);
    if (peakE < loud * 0.0025) continue;
    const lo = at(0, i);
    const md = at(1, i);
    const hi = at(2, i);
    const t = t0 + i * hopT;
    // Grosse caisse : le grave domine (son clic déborde un peu dans le médium).
    const kick = lo >= 0.5 && lo >= 0.9 * md;
    // Caisse claire : médium fort, pas seulement le débord d'une grosse caisse ni le souffle d'une cymbale.
    const snare = md >= 0.5 && md >= 0.75 * hi && md >= 0.8 * lo;
    if (kick) hits.push({ t, k: KICK, s: lo });
    if (snare) hits.push({ t, k: SNARE, s: md });
    if (hi < 0.4) continue;
    // Cymbale crash : nettement plus forte que les cymbales des 2 s précédentes,
    // et son aigu reste présent 0,15–0,45 s après le coup.
    let peak = 0;
    for (let j = i; j <= Math.min(frames - 1, i + lag); j++) peak = Math.max(peak, eHigh[j]);
    const c0 = Math.max(0, i - Math.round(2 / hopT));
    const c1 = Math.max(c0, i - Math.round(0.1 / hopT));
    const before = c1 - c0 >= 8 ? percentile(eHigh.subarray(c0, c1), 0.9) : 0;
    const a = i + Math.round(0.15 / hopT);
    const b = Math.min(frames, i + Math.round(0.45 / hopT));
    let sus = 0;
    for (let j = a; j < b; j++) sus += eHigh[j];
    sus /= Math.max(1, b - a);
    if (hi > 0.8 && peak > before * 2.5 && sus > peak * 0.15) hits.push({ t, k: CRASH, s: hi });
    else if (!snare) hits.push({ t, k: HAT, s: hi });
  }
  // Période réfractaire : un même instrument ne frappe pas deux fois en 90 ms
  // (on garde le coup le plus fort ; l'autre est un rebond de la décroissance).
  hits.sort((p, q) => p.t - q.t || p.k - q.k);
  const kept: DrumHit[] = [];
  const lastOf: (DrumHit | undefined)[] = [undefined, undefined, undefined, undefined];
  for (const h of hits) {
    const last = lastOf[h.k];
    if (last && h.t - last.t < 0.09) {
      if (h.s > last.s) {
        kept[kept.indexOf(last)] = h;
        lastOf[h.k] = h;
      }
      continue;
    }
    kept.push(h);
    lastOf[h.k] = h;
  }
  return kept;
}

/**
 * Accord entre la grille de Beat This! et la batterie : part des grosses
 * caisses et caisses claires tombant sur un temps ou un demi-temps (±60 ms).
 */
export function beatAgreement(hits: DrumHit[], beats: number[]): number {
  const main = hits.filter((h) => h.k === KICK || h.k === SNARE);
  if (main.length < 8 || beats.length < 4) return 0;
  const grid: number[] = [];
  for (let i = 0; i < beats.length; i++) {
    grid.push(beats[i]);
    if (i + 1 < beats.length) grid.push((beats[i] + beats[i + 1]) / 2);
  }
  let ok = 0;
  let j = 0;
  for (const h of main) {
    while (j + 1 < grid.length && grid[j + 1] <= h.t) j++;
    const d = Math.min(Math.abs(h.t - grid[j]), j + 1 < grid.length ? Math.abs(grid[j + 1] - h.t) : Infinity);
    if (d <= 0.06) ok++;
  }
  return ok / main.length;
}
