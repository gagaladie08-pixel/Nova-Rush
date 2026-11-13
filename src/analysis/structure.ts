/**
 * Structure du morceau (intro, couplets, refrains, pont, solo, outro).
 *
 * Pas d'IA ici : les passages qui reviennent se ressemblent (mêmes accords,
 * même timbre). On décrit chaque mesure (chroma = accords, enveloppe
 * spectrale = timbre, énergie), on compare toutes les mesures entre elles
 * (matrice d'auto-similarité), on coupe aux ruptures (nouveauté), puis on
 * regroupe les passages semblables. Le groupe répété le plus fort et chanté
 * devient le refrain.
 */
import { fft, hann, median, percentile } from './dsp';
import type { Section, SectionKind } from '../game/types';

export interface StructureOptions {
  /** Passages chantés (analyse avancée), pour distinguer couplets et solos. */
  voiceSpans?: [number, number][];
}

export interface Structure {
  sections: Section[];
  /** Confiance globale 0..1 (refrain net et bien répété → élevée). */
  confidence: number;
  /** Débuts de mesure utilisés (s). */
  bars: number[];
}

const N_FFT = 4096;
const N_CHROMA = 12;
const N_BANDS = 20;
const N_TIMBRE = 12;

interface FrameFeatures {
  hopT: number;
  chroma: Float32Array[];
  timbre: Float32Array[];
  energy: Float32Array;
}

/** Chroma, timbre (cepstre de bandes) et énergie toutes les ~93 ms. */
function frameFeatures(x: Float32Array, sr: number): FrameFeatures {
  const n = Math.round((N_FFT * sr) / 22050) >= 3000 ? N_FFT : 2048;
  const hop = n / 2;
  const frames = Math.max(1, Math.floor((x.length - n) / hop) + 1);
  const w = hann(n);
  const re = new Float32Array(n);
  const im = new Float32Array(n);
  // Correspondance case FFT → classe de hauteur (55 Hz – 2 kHz).
  const pcOf = new Int8Array(n / 2).fill(-1);
  for (let k = 1; k < n / 2; k++) {
    const f = (k * sr) / n;
    if (f < 55 || f > 2000) continue;
    const midi = 69 + 12 * Math.log2(f / 440);
    pcOf[k] = ((Math.round(midi) % 12) + 12) % 12;
  }
  // Bandes espacées logarithmiquement (60 Hz – 10 kHz) pour le timbre.
  const edges: number[] = [];
  for (let b = 0; b <= N_BANDS; b++) edges.push(Math.round((60 * Math.pow(10000 / 60, b / N_BANDS) * n) / sr));
  const dct: Float32Array[] = [];
  for (let c = 1; c <= N_TIMBRE; c++) {
    const row = new Float32Array(N_BANDS);
    for (let b = 0; b < N_BANDS; b++) row[b] = Math.cos((Math.PI * c * (b + 0.5)) / N_BANDS);
    dct.push(row);
  }
  const chroma: Float32Array[] = [];
  const timbre: Float32Array[] = [];
  const energy = new Float32Array(frames);
  const bands = new Float32Array(N_BANDS);
  for (let f = 0; f < frames; f++) {
    const o = f * hop;
    for (let i = 0; i < n; i++) {
      re[i] = (x[o + i] ?? 0) * w[i];
      im[i] = 0;
    }
    fft(re, im);
    const c = new Float32Array(N_CHROMA);
    let e = 0;
    for (let k = 1; k < n / 2; k++) {
      const p = re[k] * re[k] + im[k] * im[k];
      e += p;
      const pc = pcOf[k];
      if (pc >= 0) c[pc] += Math.sqrt(p);
    }
    for (let b = 0; b < N_BANDS; b++) {
      let s = 0;
      const a = Math.max(1, edges[b]);
      const z = Math.max(a + 1, edges[b + 1]);
      for (let k = a; k < z && k < n / 2; k++) s += re[k] * re[k] + im[k] * im[k];
      bands[b] = Math.log(1e-9 + s / (z - a));
    }
    const t = new Float32Array(N_TIMBRE);
    for (let i = 0; i < N_TIMBRE; i++) {
      let s = 0;
      for (let b = 0; b < N_BANDS; b++) s += dct[i][b] * bands[b];
      t[i] = s;
    }
    chroma.push(c);
    timbre.push(t);
    energy[f] = Math.log10(1e-9 + e / n);
  }
  return { hopT: hop / sr, chroma, timbre, energy };
}

const l2 = (v: Float32Array) => {
  let s = 0;
  for (const x of v) s += x * x;
  const k = s > 1e-12 ? 1 / Math.sqrt(s) : 0;
  for (let i = 0; i < v.length; i++) v[i] *= k;
  return v;
};

const dot = (a: Float32Array, b: Float32Array) => {
  let s = 0;
  for (let i = 0; i < a.length; i++) s += a[i] * b[i];
  return s;
};

/** Débuts de mesure : premiers temps de l'IA, sinon un temps sur quatre. */
export function barStarts(beats: number[], downbeats: number[] | undefined, duration: number): number[] {
  let bars: number[];
  if (downbeats && downbeats.length >= 4) bars = [...downbeats];
  else bars = beats.filter((_, i) => i % 4 === 0);
  // Mesure incomplète au début (anacrouse, silence) rattachée à la première.
  if (bars.length && bars[0] > 0.5) {
    const len = bars.length > 1 ? bars[1] - bars[0] : 2;
    if (bars[0] > len * 0.6) bars.unshift(Math.max(0, bars[0] - len));
  }
  return bars.filter((t) => t < duration - 0.5);
}

function voicedRatio(spans: [number, number][], a: number, b: number): number {
  let s = 0;
  for (const [x, y] of spans) {
    const lo = Math.max(a, x);
    const hi = Math.min(b, y);
    if (hi > lo) s += hi - lo;
  }
  return b > a ? s / (b - a) : 0;
}

export function detectStructure(
  samples: Float32Array,
  sr: number,
  beats: number[],
  downbeats: number[] | undefined,
  opts: StructureOptions = {},
): Structure | null {
  const duration = samples.length / sr;
  const bars = barStarts(beats, downbeats, duration);
  const B = bars.length;
  if (B < 12) return null;
  const ff = frameFeatures(samples, sr);
  const F = ff.energy.length;
  const frameAt = (t: number) => Math.max(0, Math.min(F - 1, Math.round(t / ff.hopT)));

  // Normalisation du timbre sur tout le morceau (z-score par coefficient).
  const tMean = new Float32Array(N_TIMBRE);
  const tStd = new Float32Array(N_TIMBRE);
  for (const t of ff.timbre) for (let i = 0; i < N_TIMBRE; i++) tMean[i] += t[i] / F;
  for (const t of ff.timbre) for (let i = 0; i < N_TIMBRE; i++) tStd[i] += (t[i] - tMean[i]) ** 2 / F;
  for (let i = 0; i < N_TIMBRE; i++) tStd[i] = Math.sqrt(tStd[i]) || 1;

  // Description de chaque mesure : chroma de chaque moitié + timbre + énergie.
  const barChroma: Float32Array[] = [];
  const barTimbre: Float32Array[] = [];
  const barEnergy: number[] = [];
  for (let b = 0; b < B; b++) {
    const t0 = bars[b];
    const t1 = b + 1 < B ? bars[b + 1] : Math.min(duration, t0 + (bars[b] - bars[Math.max(0, b - 1)] || 2));
    const fa = frameAt(t0);
    const fz = Math.max(fa + 1, frameAt(t1));
    const mid = Math.max(fa + 1, Math.round((fa + fz) / 2));
    const ch = new Float32Array(2 * N_CHROMA);
    const tb = new Float32Array(N_TIMBRE);
    let e = 0;
    for (let f = fa; f < fz; f++) {
      const off = f < mid ? 0 : N_CHROMA;
      for (let i = 0; i < N_CHROMA; i++) ch[off + i] += ff.chroma[f][i];
      for (let i = 0; i < N_TIMBRE; i++) tb[i] += (ff.timbre[f][i] - tMean[i]) / tStd[i];
      e += ff.energy[f];
    }
    // Chaque moitié normalisée séparément (accords), puis l'ensemble.
    l2(ch.subarray(0, N_CHROMA));
    l2(ch.subarray(N_CHROMA));
    barChroma.push(l2(ch));
    barTimbre.push(l2(tb));
    barEnergy.push(e / (fz - fa));
  }

  // Matrice d'auto-similarité, recentrée sur le morceau (médiane → 0, p95 → 1).
  const raw: Float32Array[] = [];
  const all: number[] = [];
  for (let i = 0; i < B; i++) {
    const row = new Float32Array(B);
    for (let j = 0; j < B; j++) {
      row[j] = 0.6 * dot(barChroma[i], barChroma[j]) + 0.4 * dot(barTimbre[i], barTimbre[j]);
      if (j > i) all.push(row[j]);
    }
    raw.push(row);
  }
  const med = median(all);
  const hi = Math.max(med + 1e-3, percentile(all, 0.95));
  const S = raw.map((row) => row.map((v) => (v - med) / (hi - med)));

  // Nouveauté (noyau en damier, 4 mesures de chaque côté).
  const L = 4;
  const novelty = new Float32Array(B);
  for (let b = 1; b < B; b++) {
    let s = 0;
    let wsum = 0;
    for (let i = -L; i < L; i++) {
      for (let j = -L; j < L; j++) {
        const x = b + i;
        const y = b + j;
        if (x < 0 || y < 0 || x >= B || y >= B) continue;
        const g = Math.exp(-((i + 0.5) ** 2 + (j + 0.5) ** 2) / (2 * (L * 0.6) ** 2));
        const sign = (i < 0) === (j < 0) ? 1 : -1;
        s += sign * g * S[x][y];
        wsum += g;
      }
    }
    novelty[b] = wsum > 0 ? s / wsum : 0;
  }
  // Les ruptures d'énergie comptent aussi (entrée de la batterie, refrain).
  const eDiff = new Float32Array(B);
  for (let b = 2; b < B - 1; b++) {
    const before = (barEnergy[b - 1] + barEnergy[b - 2]) / 2;
    const after = (barEnergy[b] + barEnergy[b + 1]) / 2;
    eDiff[b] = Math.abs(after - before);
  }
  const eScale = percentile(Array.from(eDiff), 0.9) || 1;
  const nScale = percentile(Array.from(novelty), 0.9) || 1;
  const score = new Float32Array(B);
  for (let b = 0; b < B; b++) score[b] = Math.max(0, novelty[b]) / nScale + 0.5 * (eDiff[b] / eScale);

  // Choix des frontières : pics forts, au moins 4 mesures d'écart.
  const thr = percentile(Array.from(score), 0.7);
  const cands = Array.from({ length: B }, (_, b) => b)
    .filter((b) => b >= 4 && b <= B - 4 && score[b] >= thr && score[b] >= (score[b - 1] ?? 0) && score[b] >= (score[b + 1] ?? 0))
    .sort((a, b) => score[b] - score[a]);
  const cuts: number[] = [];
  for (const b of cands) if (cuts.every((c) => Math.abs(c - b) >= 4)) cuts.push(b);
  cuts.sort((a, b) => a - b);
  let bounds = [0, ...cuts, B];
  // Découpe des passages trop longs (> 24 mesures) au meilleur endroit.
  for (let guard = 0; guard < 20; guard++) {
    const k = bounds.findIndex((v, i) => i + 1 < bounds.length && bounds[i + 1] - v > 24);
    if (k < 0) break;
    let best = bounds[k] + 8;
    for (let b = bounds[k] + 4; b <= bounds[k + 1] - 4; b++) if (score[b] > score[best]) best = b;
    bounds = [...bounds.slice(0, k + 1), best, ...bounds.slice(k + 1)];
  }

  interface Seg {
    a: number;
    z: number;
    group: number;
    energy: number;
    voiced: number;
    sim: number;
  }
  const segs: Seg[] = [];
  for (let i = 0; i + 1 < bounds.length; i++) {
    const a = bounds[i];
    const z = bounds[i + 1];
    let e = 0;
    for (let b = a; b < z; b++) e += barEnergy[b];
    const t0 = bars[a];
    const t1 = z < B ? bars[z] : duration;
    segs.push({ a, z, group: -1, energy: e / (z - a), voiced: opts.voiceSpans ? voicedRatio(opts.voiceSpans, t0, t1) : -1, sim: 0 });
  }

  // Ressemblance de deux passages : moyenne sur la diagonale (décalage ±1 mesure).
  const segSim = (p: Seg, q: Seg) => {
    let best = -Infinity;
    for (let off = -1; off <= 1; off++) {
      const len = Math.min(p.z - p.a, q.z - q.a) - Math.abs(off);
      if (len < 2) continue;
      let s = 0;
      for (let k = 0; k < len; k++) {
        const i = p.a + k + Math.max(0, off);
        const j = q.a + k + Math.max(0, -off);
        s += S[i][j];
      }
      // Une forte différence de longueur pénalise un peu.
      const ratio = Math.min(p.z - p.a, q.z - q.a) / Math.max(p.z - p.a, q.z - q.a);
      best = Math.max(best, (s / len) * (0.75 + 0.25 * ratio));
    }
    return best;
  };

  // Regroupement : chaque passage rejoint le premier groupe assez ressemblant.
  const SIM_THR = 0.55;
  const groups: Seg[][] = [];
  for (const s of segs) {
    let bestG = -1;
    let bestSim = SIM_THR;
    groups.forEach((g, gi) => {
      const sim = Math.min(...g.slice(0, 3).map((o) => segSim(s, o)));
      if (sim > bestSim) {
        bestSim = sim;
        bestG = gi;
      }
    });
    if (bestG < 0) {
      s.group = groups.length;
      s.sim = 1;
      groups.push([s]);
    } else {
      s.group = bestG;
      s.sim = segSim(s, groups[bestG][0]);
      groups[bestG].push(s);
    }
  }

  // Le refrain : groupe répété, énergique, chanté, ni trop rare ni omniprésent.
  const eMed = median(barEnergy);
  const eSpread = Math.max(0.05, percentile(barEnergy, 0.9) - percentile(barEnergy, 0.1));
  const barsTotal = B;
  let refrain = -1;
  let refrainScore = -Infinity;
  groups.forEach((g, gi) => {
    if (g.length < 2) return;
    const nb = g.reduce((a, s) => a + s.z - s.a, 0);
    const cover = nb / barsTotal;
    const e = g.reduce((a, s) => a + s.energy * (s.z - s.a), 0) / nb;
    const voiced = g[0].voiced < 0 ? -1 : g.reduce((a, s) => a + s.voiced, 0) / g.length;
    let sc = (e - eMed) / eSpread + 0.25 * Math.min(3, g.length - 1);
    if (voiced >= 0) sc += voiced > 0.3 ? 0.4 : -0.8;
    if (cover > 0.6) sc -= 1;
    if (g.every((s) => s.z - s.a < 4)) sc -= 0.6;
    if (g[0].a === 0) sc -= 0.3; // rarement en tout début de morceau
    if (sc > refrainScore) {
      refrainScore = sc;
      refrain = gi;
    }
  });

  const kinds: SectionKind[] = segs.map(() => 'couplet');
  const firstRef = segs.findIndex((s) => s.group === refrain);
  let lastRef = -1;
  segs.forEach((s, i) => {
    if (s.group === refrain) lastRef = i;
  });
  // Si le groupe est omniprésent, seuls ses passages les plus forts sont des refrains.
  const refrainGroup = refrain >= 0 ? groups[refrain] : [];
  const refrainCover = refrainGroup.reduce((a, s) => a + s.z - s.a, 0) / barsTotal;
  const refrainEnergyCut = refrainCover > 0.5 ? median(refrainGroup.map((s) => s.energy)) : -Infinity;

  segs.forEach((s, i) => {
    const repeated = groups[s.group].length >= 2;
    const instrumental = s.voiced >= 0 && s.voiced < 0.2;
    if (s.group === refrain && s.energy >= refrainEnergyCut) kinds[i] = 'refrain';
    else if (i === 0 && (firstRef < 0 || i < firstRef) && (instrumental || s.voiced < 0 || !repeated) && s.z - s.a <= 12) kinds[i] = 'intro';
    else if (i === segs.length - 1 && i > lastRef && (instrumental || !repeated || s.voiced < 0)) kinds[i] = 'outro';
    else if (instrumental) kinds[i] = 'solo';
    else if (!repeated && firstRef >= 0 && i > firstRef) kinds[i] = 'pont';
    else kinds[i] = 'couplet';
  });

  const reps = new Map<number, number>();
  const sections: Section[] = segs.map((s, i) => {
    const rep = reps.get(s.group) ?? 0;
    reps.set(s.group, rep + 1);
    return {
      t: i === 0 ? 0 : Math.round(bars[s.a] * 1000) / 1000,
      end: Math.round((s.z < B ? bars[s.z] : duration) * 1000) / 1000,
      kind: kinds[i],
      group: s.group,
      rep,
      sim: Math.round(Math.max(0, Math.min(1, s.sim)) * 100) / 100,
      energy: Math.round(Math.max(0, Math.min(1, 0.5 + (s.energy - eMed) / (2 * eSpread))) * 100) / 100,
    };
  });

  let confidence = 0.25;
  if (refrain >= 0) {
    const sims = refrainGroup.slice(1).map((s) => s.sim);
    const meanSim = sims.length ? sims.reduce((a, b) => a + b, 0) / sims.length : 0;
    confidence = Math.max(0, Math.min(1, 0.3 + 0.12 * Math.min(3, refrainGroup.length) + 0.3 * Math.min(1, meanSim) - (refrainCover > 0.5 ? 0.25 : 0)));
  }
  return { sections, confidence: Math.round(confidence * 100) / 100, bars };
}
