import { fft, hann, magnitudeAt, median, midiToHz, movingAverage, percentile, rng } from './dsp';
import { LEVEL_PARAMS, computeStats, levelFromScore } from './difficulty';
import { inSpans, trackVoice } from './voice';
import { detectStructure } from './structure';
import type { Chart, Lane, Level, Note, Section } from '../game/types';

export type ProgressFn = (p: number, label: string) => void;

export class AnalysisError extends Error {}

/** Temps et premiers temps fournis par l'IA Beat This! (en secondes, sur le mix). */
export interface Rhythm {
  beats: number[];
  downbeats: number[];
}

interface Onset {
  t: number;
  frame: number;
  strength: number;
}

interface Candidate {
  t: number;
  strength: number;
  pitch?: number;
  /** Durée tenue mesurée (s), pour une éventuelle tuile longue. */
  sustain?: number;
  onBeat: boolean;
  downBeat: boolean;
  beatIndex: number;
  priority: number;
}

export interface AnalysisFeatures {
  bpm: number;
  beats: number[];
  onsets: Onset[];
  melodic: boolean;
  melodyConfidence: number;
  candidates: Candidate[];
  duration: number;
  /** Énergie moyenne par temps (pour choisir des motifs cohérents par section). */
  beatEnergy: number[];
  /** isDown[b] : le temps b est un premier temps de mesure. */
  isDown: boolean[];
  rhythmSource: 'ai' | 'classic';
  sections?: Section[];
  structureConfidence?: number;
}

const nextPow2 = (x: number) => 1 << Math.ceil(Math.log2(Math.max(2, x)));
const MIDI_MIN = 45;
const MIDI_MAX = 88;

/* ------------------------------------------------------------------ */
/* 1. Fonction de détection d'attaques (flux spectral)                 */
/* ------------------------------------------------------------------ */

export function onsetEnvelope(samples: Float32Array, sr: number, progress: ProgressFn) {
  const n = nextPow2((sr * 1024) / 22050);
  const hop = Math.max(64, Math.round((sr * 256) / 22050));
  const hopT = hop / sr;
  const frames = Math.floor((samples.length - n) / hop) + 1;
  if (frames < 100) throw new AnalysisError('Le morceau est trop court pour être analysé.');

  const flux = new Float32Array(frames);
  const rms = new Float32Array(frames);
  const re = new Float32Array(n);
  const im = new Float32Array(n);
  const prev = new Float32Array(n / 2);
  const w = hann(n);
  const kMax = Math.min(n / 2, Math.floor(8000 / (sr / n)));

  for (let f = 0; f < frames; f++) {
    const start = f * hop;
    let e = 0;
    for (let i = 0; i < n; i++) {
      const s = samples[start + i];
      re[i] = s * w[i];
      im[i] = 0;
      e += s * s;
    }
    rms[f] = Math.sqrt(e / n);
    fft(re, im);
    let fl = 0;
    for (let k = 1; k < kMax; k++) {
      const l = Math.log1p(100 * Math.hypot(re[k], im[k]));
      const d = l - prev[k];
      if (d > 0) fl += d;
      prev[k] = l;
    }
    flux[f] = fl;
    if ((f & 1023) === 0) progress(0.05 + 0.4 * (f / frames), 'Analyse du rythme…');
  }
  flux[0] = 0;
  // Centre de la fenêtre, corrigé du biais mesuré sur des attaques nettes
  // (la compression logarithmique détecte l'attaque dès son entrée dans la fenêtre).
  const t0 = (n * 0.5) / sr + 0.012;
  return { flux, rms, hopT, t0, frames };
}

export function pickOnsets(odf: Float32Array, hopT: number, t0: number): Onset[] {
  const avg = movingAverage(odf, Math.round(0.25 / hopT));
  const half = Math.max(1, Math.round(0.035 / hopT));
  const minDist = 0.06;
  const out: Onset[] = [];
  for (let i = half; i < odf.length - half; i++) {
    const v = odf[i];
    if (v <= avg[i] * 1.3 + 0.07) continue;
    let isMax = true;
    for (let j = i - half; j <= i + half; j++) {
      if (odf[j] > v || (odf[j] === v && j < i)) {
        isMax = false;
        break;
      }
    }
    if (!isMax) continue;
    const t = t0 + i * hopT;
    const last = out[out.length - 1];
    if (last && t - last.t < minDist) {
      if (v > last.strength) out[out.length - 1] = { t, frame: i, strength: v };
      continue;
    }
    out.push({ t, frame: i, strength: v });
  }
  return out;
}

/* ------------------------------------------------------------------ */
/* 2. Tempo et suivi des battements (programmation dynamique)          */
/* ------------------------------------------------------------------ */

function estimatePeriod(odf: Float32Array, hopT: number): number {
  const base = movingAverage(odf, Math.round(1 / hopT));
  const x = new Float32Array(odf.length);
  for (let i = 0; i < x.length; i++) x[i] = odf[i] - base[i];
  const minLag = Math.max(2, Math.floor(60 / 200 / hopT));
  const maxLag = Math.ceil(60 / 55 / hopT);
  const ac = new Float32Array(maxLag + 2);
  for (let l = minLag - 1; l <= maxLag + 1; l++) {
    let s = 0;
    for (let i = 0; i + l < x.length; i++) s += x[i] * x[i + l];
    ac[l] = s / (x.length - l);
  }
  let best = minLag;
  let bestV = -Infinity;
  for (let l = minLag; l <= maxLag; l++) {
    const bpm = 60 / (l * hopT);
    const wgt = Math.exp(-0.5 * Math.pow(Math.log2(bpm / 120) / 0.9, 2));
    const v = ac[l] * wgt;
    if (v > bestV) {
      bestV = v;
      best = l;
    }
  }
  const a = ac[best - 1];
  const b = ac[best];
  const c = ac[best + 1];
  const den = a - 2 * b + c;
  const delta = den !== 0 ? Math.max(-0.5, Math.min(0.5, (0.5 * (a - c)) / den)) : 0;
  return best + delta;
}

function trackBeats(odf: Float32Array, period: number): number[] {
  const n = odf.length;
  let mean = 0;
  for (let i = 0; i < n; i++) mean += odf[i];
  mean /= n;
  let sd = 0;
  for (let i = 0; i < n; i++) sd += (odf[i] - mean) ** 2;
  sd = Math.sqrt(sd / n) || 1;
  const local = new Float32Array(n);
  for (let i = 0; i < n; i++) local[i] = odf[i] / sd;

  const score = new Float32Array(n);
  const back = new Int32Array(n).fill(-1);
  const tight = 100;
  const lo = Math.round(2 * period);
  const hi = Math.max(1, Math.round(period / 2));
  for (let i = 0; i < n; i++) {
    let best = -Infinity;
    let bi = -1;
    for (let j = Math.max(0, i - lo); j <= i - hi; j++) {
      const r = Math.log((i - j) / period);
      const v = score[j] - tight * r * r;
      if (v > best) {
        best = v;
        bi = j;
      }
    }
    score[i] = local[i] + (bi >= 0 && best > 0 ? best : 0);
    back[i] = bi >= 0 && best > 0 ? bi : -1;
  }
  let end = n - 1;
  let endV = -Infinity;
  for (let i = Math.max(0, n - Math.ceil(period)); i < n; i++) {
    if (score[i] > endV) {
      endV = score[i];
      end = i;
    }
  }
  const beats: number[] = [];
  for (let i = end; i >= 0; i = back[i]) {
    beats.push(i);
    if (back[i] < 0) break;
  }
  return beats.reverse();
}

/* ------------------------------------------------------------------ */
/* 3. Hauteur (saillance harmonique)                                   */
/* ------------------------------------------------------------------ */

class PitchProbe {
  private n: number;
  private re: Float32Array;
  private im: Float32Array;
  private mag: Float32Array;
  private bin: number;
  constructor(private samples: Float32Array, private sr: number) {
    this.n = nextPow2((sr * 2048) / 22050);
    this.re = new Float32Array(this.n);
    this.im = new Float32Array(this.n);
    this.mag = new Float32Array(this.n / 2);
    this.bin = sr / this.n;
  }
  private spectrum(t: number) {
    return magnitudeAt(this.samples, Math.round(t * this.sr), this.n, this.re, this.im, this.mag);
  }
  private sal(mag: Float32Array, m: number): number {
    const f0 = midiToHz(m);
    let s = 0;
    for (let h = 1; h <= 6; h++) {
      const f = f0 * h;
      if (f > this.sr / 2 - this.bin * 2) break;
      const a = Math.floor((f * 0.985) / this.bin);
      const b = Math.ceil((f * 1.015) / this.bin);
      let v = 0;
      for (let k = a; k <= b; k++) if (mag[k] > v) v = mag[k];
      s += Math.pow(0.84, h - 1) * v;
    }
    return s;
  }
  /** Hauteur dominante (MIDI) et clarté autour de l'instant t. */
  analyze(t: number): { midi: number; clarity: number; salience: number } {
    const mag = this.spectrum(t);
    let best = MIDI_MIN;
    let bestV = 0;
    let sum = 0;
    for (let m = MIDI_MIN; m <= MIDI_MAX; m++) {
      const v = this.sal(mag, m);
      sum += v;
      if (v > bestV) {
        bestV = v;
        best = m;
      }
    }
    const mean = sum / (MIDI_MAX - MIDI_MIN + 1);
    return { midi: best, clarity: mean > 0 ? bestV / mean : 0, salience: bestV };
  }
  salienceAt(t: number, m: number): number {
    return this.sal(this.spectrum(t), m);
  }
}

/** Cale un instant sur la grille (quarts de temps) s'il en est assez proche. */
function makeSnap(beats: number[], beatLen: number, maxTol: number, relTol: number) {
  return (t: number) => {
    if (beats.length < 2) return { t, beatIndex: 0, onBeat: false };
    let lo = 0;
    let hi = beats.length - 1;
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1;
      if (beats[mid] <= t) lo = mid;
      else hi = mid;
    }
    const b = t < beats[0] ? 0 : lo;
    const len = (beats[b + 1] ?? beats[b] + beatLen) - beats[b];
    const q = Math.round(((t - beats[b]) / len) * 4) / 4;
    const snapped = beats[b] + q * len;
    const tt = Math.abs(snapped - t) < Math.min(maxTol, len * relTol) ? snapped : t;
    const beatIndex = b + Math.floor(q + 1e-6);
    const onBeat = Math.abs(q - Math.round(q)) < 1e-6 && Math.abs(snapped - t) < 0.05;
    return { t: tt, beatIndex, onBeat };
  };
}

/** Fonction de détection d'attaques normalisée (p95 = 1) et légèrement lissée. */
function normalizedOdf(flux: Float32Array): Float32Array | null {
  const odf = new Float32Array(flux.length);
  for (let i = 1; i < odf.length - 1; i++) odf[i] = (flux[i - 1] + 2 * flux[i] + flux[i + 1]) / 4;
  const p95 = percentile(odf.filter((_, i) => i % 4 === 0), 0.95);
  if (!(p95 > 1e-6)) return null;
  for (let i = 0; i < odf.length; i++) odf[i] /= p95;
  return odf;
}

/* ------------------------------------------------------------------ */
/* 4. Extraction des caractéristiques                                  */
/* ------------------------------------------------------------------ */

/** Temps de l'IA exploitables ? (assez nombreux, tempo plausible, régulier). */
export function usableRhythm(r: Rhythm | undefined, duration: number): Rhythm | undefined {
  if (!r) return undefined;
  const beats = r.beats.filter((t) => t >= 0 && t <= duration);
  if (beats.length < 8) return undefined;
  const ibi: number[] = [];
  for (let i = 1; i < beats.length; i++) ibi.push(beats[i] - beats[i - 1]);
  const m = median(ibi);
  if (!(m > 0.24 && m < 1.6)) return undefined;
  const regular = ibi.filter((d) => Math.abs(d - m) < m * 0.3).length / ibi.length;
  if (regular < 0.5) return undefined;
  return { beats, downbeats: r.downbeats.filter((t) => t >= 0 && t <= duration) };
}

/** Indique, pour chaque temps, s'il ouvre une mesure. */
function downbeatFlags(beats: number[], downbeats: number[] | undefined): boolean[] {
  if (!downbeats || downbeats.length < 2) return beats.map((_, i) => i % 4 === 0);
  const flags = beats.map(() => false);
  let j = 0;
  for (let i = 0; i < beats.length; i++) {
    while (j < downbeats.length && downbeats[j] < beats[i] - 0.03) j++;
    if (j < downbeats.length && Math.abs(downbeats[j] - beats[i]) <= 0.03) flags[i] = true;
  }
  return flags;
}

export function extractFeatures(
  samples: Float32Array,
  sr: number,
  progress: ProgressFn = () => {},
  rhythm?: Rhythm,
): AnalysisFeatures {
  const duration = samples.length / sr;
  progress(0.03, 'Analyse du rythme…');
  const env = onsetEnvelope(samples, sr, progress);
  const { hopT, t0 } = env;

  // Fonction de détection normalisée et légèrement lissée.
  const odf = normalizedOdf(env.flux);
  if (!odf) throw new AnalysisError('Aucun son exploitable n’a été détecté dans ce fichier.');

  progress(0.47, 'Détection du tempo…');
  const onsets = pickOnsets(odf, hopT, t0);
  const ai = usableRhythm(rhythm, duration);
  const period = estimatePeriod(odf, hopT);
  let beats: number[];
  if (ai) {
    beats = ai.beats;
  } else {
    progress(0.52, 'Suivi des battements…');
    const beatFrames = trackBeats(odf, period);
    const loud = percentile(env.rms, 0.9);
    beats = beatFrames.filter((f) => env.rms[Math.min(env.frames - 1, f)] > loud * 0.05).map((f) => t0 + f * hopT);
  }
  const isDown = downbeatFlags(beats, ai?.downbeats);
  if (beats.length < 4 && onsets.length < 8) {
    throw new AnalysisError('Analyse impossible : aucun rythme exploitable n’a été trouvé.');
  }
  const intervals: number[] = [];
  for (let i = 1; i < beats.length; i++) intervals.push(beats[i] - beats[i - 1]);
  const beatLen = intervals.length ? median(intervals) : period * hopT;
  const bpm = Math.round((60 / beatLen) * 10) / 10;

  // Énergie par temps
  const beatEnergy: number[] = [];
  for (let b = 0; b < beats.length; b++) {
    const a = Math.max(0, Math.round((beats[b] - t0) / hopT));
    const z = Math.min(env.frames, Math.round(((beats[b + 1] ?? beats[b] + beatLen) - t0) / hopT));
    let s = 0;
    for (let i = a; i < z; i++) s += env.rms[i];
    beatEnergy.push(z > a ? s / (z - a) : 0);
  }

  // Hauteur des attaques
  progress(0.56, 'Recherche de la mélodie…');
  const probe = new PitchProbe(samples, sr);
  const strengths = onsets.map((o) => o.strength);
  const sMin = percentile(strengths, 0.2);
  const used = onsets.filter((o) => o.strength >= sMin);
  const pitched: (number | undefined)[] = [];
  let clear = 0;
  for (let i = 0; i < used.length; i++) {
    const o = used[i];
    const a = probe.analyze(o.t + 0.015);
    const b = probe.analyze(o.t + 0.075);
    const stable = Math.abs(a.midi - b.midi) <= 1 || Math.abs(Math.abs(a.midi - b.midi) - 12) <= 1;
    const ok = a.clarity > 2.6 && b.clarity > 2.3 && stable;
    pitched.push(ok ? a.midi : undefined);
    if (ok) clear++;
    if ((i & 63) === 0) progress(0.56 + 0.22 * (i / used.length), 'Recherche de la mélodie…');
  }
  const melodyConfidence = used.length ? clear / used.length : 0;
  const melodic = melodyConfidence >= 0.45 && clear >= 16;

  // Sons tenus : uniquement en mode mélodique et lorsque la tenue est nette.
  progress(0.8, 'Repérage des notes tenues…');
  const sustains: (number | undefined)[] = new Array(used.length);
  if (melodic) {
    for (let i = 0; i < used.length; i++) {
      const p = pitched[i];
      if (p === undefined) continue;
      let next = duration;
      for (let j = i + 1; j < used.length; j++) {
        if (pitched[j] !== undefined) {
          next = used[j].t;
          break;
        }
      }
      if (next - used[i].t < 0.45) continue;
      const s0 = probe.salienceAt(used[i].t + 0.04, p);
      if (s0 <= 0) continue;
      let t = used[i].t + 0.1;
      const limit = Math.min(next - 0.05, used[i].t + 4);
      while (t < limit) {
        if (probe.salienceAt(t, p) < s0 * 0.45) break;
        t += 0.06;
      }
      const sustain = t - used[i].t;
      if (sustain >= 0.4) sustains[i] = sustain;
    }
  }

  // Candidats calés sur la grille rythmique.
  progress(0.88, 'Construction du parcours…');
  const snap = makeSnap(beats, beatLen, 0.03, 0.12);

  const strongThreshold = melodic ? percentile(strengths, 0.25) : percentile(strengths, 0.45);
  const raw: Candidate[] = [];
  used.forEach((o, i) => {
    if (o.strength < strongThreshold && pitched[i] === undefined) return;
    const s = snap(o.t);
    raw.push({
      t: s.t,
      strength: o.strength,
      pitch: pitched[i],
      sustain: sustains[i],
      onBeat: s.onBeat,
      downBeat: s.onBeat && !!isDown[s.beatIndex],
      beatIndex: s.beatIndex,
      priority: 0,
    });
  });
  if (!melodic || raw.length < 8) {
    // Repli rythmique : on suit principalement les battements et accents.
    beats.forEach((t, b) => {
      const f = Math.max(0, Math.min(env.frames - 1, Math.round((t - t0) / hopT)));
      raw.push({ t, strength: odf[f] * 0.8 + 0.2, onBeat: true, downBeat: !!isDown[b], beatIndex: b, priority: 0 });
    });
  }
  raw.sort((a, b) => a.t - b.t);
  const candidates: Candidate[] = [];
  for (const c of raw) {
    const last = candidates[candidates.length - 1];
    if (last && c.t - last.t < 0.07) {
      last.strength = Math.max(last.strength, c.strength);
      last.onBeat ||= c.onBeat;
      last.downBeat ||= c.downBeat;
      last.pitch ??= c.pitch;
      last.sustain ??= c.sustain;
      continue;
    }
    candidates.push({ ...c });
  }
  for (const c of candidates) {
    c.priority = c.strength + (c.onBeat ? 0.6 : 0) + (c.downBeat ? 0.3 : 0) + (c.pitch !== undefined ? 0.3 : 0);
  }
  return {
    bpm,
    beats,
    onsets,
    melodic,
    melodyConfidence,
    candidates: candidates.filter((c) => c.t >= 0.6 && c.t <= duration - 0.15),
    duration,
    beatEnergy,
    isDown,
    rhythmSource: ai ? 'ai' : 'classic',
  };
}

/* ------------------------------------------------------------------ */
/* 5. Génération du parcours                                           */
/* ------------------------------------------------------------------ */

const PATTERNS: Lane[][] = [
  [0, 1, 2, 3, 0, 1, 2, 3],
  [3, 2, 1, 0, 3, 2, 1, 0],
  [0, 2, 1, 3, 0, 2, 1, 3],
  [1, 2, 0, 3, 1, 2, 0, 3],
  [0, 1, 2, 1, 3, 2, 1, 2],
  [3, 1, 2, 0, 3, 1, 2, 0],
  [1, 0, 2, 3, 2, 1, 3, 0],
];
const PAIRS: Lane[][] = [
  [0, 2],
  [1, 3],
  [0, 3],
  [1, 2],
];
const TRIPLES: Lane[][] = [
  [0, 1, 3],
  [0, 2, 3],
];

function thin(cands: Candidate[], minGap: number): Candidate[] {
  const order = [...cands].sort((a, b) => b.priority - a.priority);
  const acceptedTimes: number[] = [];
  const accepted: Candidate[] = [];
  for (const c of order) {
    let lo = 0;
    let hi = acceptedTimes.length;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (acceptedTimes[mid] < c.t) lo = mid + 1;
      else hi = mid;
    }
    const before = acceptedTimes[lo - 1];
    const after = acceptedTimes[lo];
    if ((before !== undefined && c.t - before < minGap) || (after !== undefined && after - c.t < minGap)) continue;
    acceptedTimes.splice(lo, 0, c.t);
    accepted.push(c);
  }
  return accepted.sort((a, b) => a.t - b.t);
}

/** Position en temps (fractionnaire) d'un instant, d'après la grille des battements. */
function beatPosition(beats: number[], fallbackLen: number) {
  return (t: number): number => {
    const n = beats.length;
    if (n < 2) return t / fallbackLen;
    if (t <= beats[0]) return (t - beats[0]) / (beats[1] - beats[0]);
    if (t >= beats[n - 1]) return n - 1 + (t - beats[n - 1]) / (beats[n - 1] - beats[n - 2]);
    let lo = 0;
    let hi = n - 1;
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1;
      if (beats[mid] <= t) lo = mid;
      else hi = mid;
    }
    return lo + (t - beats[lo]) / (beats[lo + 1] - beats[lo]);
  };
}

/** Tolérance (en temps) pour reconnaître « la même place » dans deux refrains. */
const SAME_POS = 0.13;

interface TemplateNote {
  rel: number;
  lanes: Lane[];
  pitch?: number;
  size: number;
}

/**
 * Même note dans deux refrains ? On tolère un demi-ton (petit flottement du
 * suivi de hauteur) et les erreurs d'octave ; un vrai changement de mélodie
 * (au-delà) garde ses propres tuiles.
 */
const pitchCompatible = (a?: number, b?: number) => {
  if (a === undefined || b === undefined) return true;
  const d = Math.abs(a - b);
  return d < 1.5 || Math.abs(d - 12) < 1.5 || Math.abs(d - 24) < 1.5;
};

export function buildChart(features: AnalysisFeatures, level: Level, seed: number, withChords = true): Note[] {
  const params = LEVEL_PARAMS[level];
  const rand = rng(seed ^ 0x9e3779b9);
  const { beats, melodic } = features;
  const beatLenAt = (bi: number) => {
    if (beats.length < 2) return 60 / features.bpm;
    const b = Math.max(0, Math.min(beats.length - 2, bi));
    return beats[b + 1] - beats[b];
  };

  // Structure : chaque refrain reprend le motif du premier (mêmes colonnes,
  // même rythme) là où la musique se répète vraiment.
  const secs = features.sections && features.sections.length ? features.sections : undefined;
  const posOf = beatPosition(beats, 60 / Math.max(1, features.bpm));
  const secIndexOf = (t: number) => {
    if (!secs) return -1;
    for (let i = 0; i < secs.length; i++) if (t >= secs[i].t && t < secs[i].end) return i;
    return -1;
  };
  const copyOk = !!secs && (features.structureConfidence ?? 0) >= 0.4;
  const templateOf = new Map<number, number>();
  secs?.forEach((sec, i) => {
    if (sec.kind === 'refrain' && !templateOf.has(sec.group)) templateOf.set(sec.group, i);
  });
  const isTemplate = (si: number) => copyOk && si >= 0 && templateOf.get(secs![si].group) === si;
  const isRepeat = (si: number) => {
    if (!copyOk || si < 0) return false;
    const sec = secs![si];
    const tpl = templateOf.get(sec.group);
    return sec.kind === 'refrain' && tpl !== undefined && tpl !== si && sec.sim >= 0.35;
  };
  // Décalage (en temps) qui aligne au mieux chaque refrain répété sur le
  // premier : une frontière détectée une mesure trop tôt ou trop tard ne
  // doit pas empêcher la reprise du motif.
  const shiftOf = new Map<number, number>();
  const relOf = (t: number, si: number) => posOf(t) - posOf(secs![si].t) + (shiftOf.get(si) ?? 0);

  let cands = thin(features.candidates, params.minGap);
  if (copyOk) {
    const inSec = (si: number) => cands.filter((c) => c.t >= secs![si].t && c.t < secs![si].end);
    secs!.forEach((sec, si) => {
      if (!isRepeat(si)) return;
      const tpl = templateOf.get(sec.group)!;
      const a = inSec(tpl).map((c) => ({ r: relOf(c.t, tpl), p: c.pitch }));
      const b = inSec(si).map((c) => ({ r: posOf(c.t) - posOf(sec.t), p: c.pitch }));
      let best = 0;
      let bestScore = 0;
      for (let k = -8; k <= 8; k++) {
        let score = 0;
        for (const x of b) {
          const m = a.find((y) => Math.abs(x.r + k - y.r) < SAME_POS && pitchCompatible(x.p, y.p));
          if (m) score += m.p !== undefined && x.p !== undefined ? 1 : 0.5;
        }
        score -= Math.abs(k) * 0.01;
        if (score > bestScore) {
          bestScore = score;
          best = k;
        }
      }
      shiftOf.set(si, best);
    });
  }
  if (copyOk && secs!.some((_, i) => isRepeat(i))) {
    // 2e passage : on favorise, dans les refrains répétés, les attaques
    // placées comme celles retenues dans le premier refrain.
    const tplPos = new Map<number, number[]>();
    const chosen = new Set(cands.map((c) => c.t));
    for (const c of cands) {
      const si = secIndexOf(c.t);
      if (!isTemplate(si)) continue;
      const g = secs![si].group;
      if (!tplPos.has(g)) tplPos.set(g, []);
      tplPos.get(g)!.push(relOf(c.t, si));
    }
    const boosted = features.candidates.map((c) => {
      const si = secIndexOf(c.t);
      let bonus = 0;
      if (isTemplate(si) && chosen.has(c.t)) bonus = 2;
      else if (isRepeat(si)) {
        const r = relOf(c.t, si);
        if ((tplPos.get(secs![si].group) ?? []).some((q) => Math.abs(q - r) < SAME_POS)) bonus = 2;
      }
      return bonus ? { ...c, priority: c.priority + bonus } : c;
    });
    cands = thin(boosted, params.minGap);
  }

  // Tuiles longues
  type Planned = Candidate & { dur?: number; beats?: number; size: number };
  const planned: Planned[] = cands.map((c) => ({ ...c, size: 1 }));
  for (const p of planned) {
    if (p.sustain === undefined) continue;
    const bl = beatLenAt(p.beatIndex);
    const b = Math.floor((p.sustain / bl) * 2) / 2;
    if (b >= params.minLongBeats && p.sustain >= 0.4) {
      p.beats = Math.min(b, 8);
      p.dur = p.beats * bl;
    }
  }
  // Aux niveaux Facile et Moyen, rien d'autre pendant une tenue.
  let filtered: Planned[] = planned;
  if (level === 'facile' || level === 'moyen') {
    filtered = [];
    let holdEnd = -1;
    for (const p of planned) {
      if (p.t < holdEnd + 0.08) continue;
      filtered.push(p);
      if (p.dur) holdEnd = p.t + p.dur;
    }
  }

  // Accords : accents forts sur les temps.
  if (withChords && params.chordRate > 0) {
    const eligible = filtered.filter((p) => p.onBeat && !p.dur);
    const sorted = [...eligible].sort((a, b) => b.strength - a.strength);
    const quota = Math.round(filtered.length * params.chordRate);
    const tripleQuota = params.maxChord >= 3 ? Math.round(quota * 0.2) : 0;
    let lastChordBeat = -999;
    let made = 0;
    const chosen = new Set<Planned>();
    for (const p of sorted) {
      if (made >= quota) break;
      if (Math.abs(p.beatIndex - lastChordBeat) < 2) continue;
      chosen.add(p);
      lastChordBeat = p.beatIndex;
      made++;
    }
    let triples = 0;
    for (const p of sorted) {
      if (!chosen.has(p)) continue;
      p.size = triples < tripleQuota ? 3 : 2;
      if (p.size === 3) triples++;
    }
  }

  // Même taille d'accord qu'au premier refrain, à la même place.
  const tplSizes = new Map<number, { rel: number; size: number; pitch?: number }[]>();
  if (copyOk) {
    for (const p of filtered) {
      const si = secIndexOf(p.t);
      if (!isTemplate(si)) continue;
      const g = secs![si].group;
      if (!tplSizes.has(g)) tplSizes.set(g, []);
      tplSizes.get(g)!.push({ rel: relOf(p.t, si), size: p.size, pitch: p.pitch });
    }
    for (const p of filtered) {
      const si = secIndexOf(p.t);
      if (!isRepeat(si)) continue;
      const r = relOf(p.t, si);
      const m = (tplSizes.get(secs![si].group) ?? []).find((q) => Math.abs(q.rel - r) < SAME_POS && pitchCompatible(q.pitch, p.pitch));
      if (m) p.size = p.dur || !withChords ? 1 : m.size;
    }
  }

  // Attribution des voies
  const notes: Note[] = [];
  const lastUse = [-9, -9, -9, -9];
  const holdUntil = [-9, -9, -9, -9];
  let chordId = 0;
  let prevLane: Lane = 1;
  let prevPitch: number | undefined;
  const energyRef = percentile(features.beatEnergy, 0.5) || 1;
  const pitchedTimes = filtered.filter((p) => p.pitch !== undefined);
  let phrase = -1;
  let step = 0;
  let pattern = PATTERNS[seed % PATTERNS.length];
  const tplNotes = new Map<number, TemplateNote[]>();

  const free = (l: number, t: number) => holdUntil[l] < t - 0.05 && t - lastUse[l] >= params.laneGap;

  for (const p of filtered) {
    const si = secIndexOf(p.t);
    if (si >= 0) {
      // Un motif par section, le même pour toutes les sections d'un même groupe.
      if (si !== phrase) {
        phrase = si;
        step = 0;
        const sec = secs![si];
        const cls = sec.energy > 0.65 ? 2 : sec.energy < 0.35 ? 0 : 1;
        pattern = PATTERNS[(seed + cls * 3 + sec.group) % PATTERNS.length];
      }
    } else {
      // Motif par phrase de 16 temps, choisi selon l'énergie de la section
      const ph = 100000 + Math.floor(p.beatIndex / 16);
      if (ph !== phrase) {
        phrase = ph;
        step = 0;
        const k = ph - 100000;
        const e = features.beatEnergy.slice(k * 16, k * 16 + 16);
        const avg = e.length ? e.reduce((a, b) => a + b, 0) / e.length : energyRef;
        const cls = avg > energyRef * 1.25 ? 2 : avg < energyRef * 0.75 ? 0 : 1;
        pattern = PATTERNS[(seed + cls * 3 + (k % 2)) % PATTERNS.length];
      }
    }

    // Place équivalente dans le premier refrain ?
    let match: TemplateNote | undefined;
    const rel = si >= 0 && copyOk ? relOf(p.t, si) : 0;
    if (isRepeat(si)) {
      match = (tplNotes.get(secs![si].group) ?? []).find((q) => Math.abs(q.rel - rel) < SAME_POS && pitchCompatible(q.pitch, p.pitch));
    }
    const record = (lanes: Lane[]) => {
      if (!isTemplate(si)) return;
      const g = secs![si].group;
      if (!tplNotes.has(g)) tplNotes.set(g, []);
      tplNotes.get(g)!.push({ rel, lanes, pitch: p.pitch, size: lanes.length });
    };

    let desired: Lane;
    if (match) {
      desired = match.lanes[0];
    } else if (melodic && p.pitch !== undefined) {
      const win = pitchedTimes.filter((q) => Math.abs(q.t - p.t) <= 3).map((q) => q.pitch as number);
      const uniq = [...new Set(win)].sort((a, b) => a - b);
      const idx = uniq.indexOf(p.pitch);
      const u = uniq.length;
      if (u <= 1) desired = prevLane;
      else if (u === 2) desired = (idx === 0 ? 1 : 2) as Lane;
      else if (u === 3) desired = ([0, 2, 3] as Lane[])[idx];
      else desired = Math.round((idx / (u - 1)) * 3) as Lane;
    } else {
      desired = pattern[step % pattern.length];
    }
    step++;

    if (p.size > 1) {
      const sets = p.size === 3 ? TRIPLES : PAIRS;
      const startIdx = Math.floor(rand() * sets.length);
      const tries: Lane[][] = [];
      if (match && match.lanes.length === p.size) tries.push(match.lanes);
      for (let k = 0; k < sets.length; k++) tries.push(sets[(startIdx + k) % sets.length]);
      let placed = false;
      for (const set of tries) {
        if (!set.every((l) => free(l, p.t))) continue;
        const id = chordId++;
        for (const l of set) {
          notes.push({ t: p.t, lane: l, chord: id });
          lastUse[l] = p.t;
        }
        prevLane = set[0];
        record(set);
        placed = true;
        break;
      }
      if (placed) continue;
    }

    // Recherche d'une voie libre, de préférence dans le sens de la mélodie.
    const dir = prevPitch !== undefined && p.pitch !== undefined ? Math.sign(p.pitch - prevPitch) : 0;
    const order: Lane[] = ([0, 1, 2, 3] as Lane[]).sort((a, b) => {
      const da = Math.abs(a - desired) + (dir !== 0 && !match && Math.sign(a - desired) === -dir ? 0.5 : 0);
      const db = Math.abs(b - desired) + (dir !== 0 && !match && Math.sign(b - desired) === -dir ? 0.5 : 0);
      return da - db;
    });
    const lane = order.find((l) => free(l, p.t));
    if (lane === undefined) continue;
    const note: Note = { t: p.t, lane };
    if (p.dur) {
      note.dur = p.dur;
      note.beats = p.beats;
      holdUntil[lane] = p.t + p.dur;
    }
    lastUse[lane] = p.dur ? p.t + p.dur : p.t;
    notes.push(note);
    record([lane]);
    prevLane = lane;
    if (p.pitch !== undefined) prevPitch = p.pitch;
  }
  return notes.sort((a, b) => a.t - b.t || a.lane - b.lane);
}

/* ------------------------------------------------------------------ */
/* 6. Analyse avec la voix isolée (séparation de sources)              */
/* ------------------------------------------------------------------ */

export interface VoiceFeatures extends AnalysisFeatures {
  /** Part du morceau chantée (0..1) ; 0 si aucune voix exploitable. */
  voiceRatio: number;
  /** true si le parcours suit la voix. */
  followsVoice: boolean;
  /** Passages chantés (s). */
  voiceSpans?: [number, number][];
}

const rmsOf = (x: Float32Array) => {
  let s = 0;
  for (let i = 0; i < x.length; i += 4) s += x[i] * x[i];
  return Math.sqrt(s / Math.max(1, Math.ceil(x.length / 4)));
};

/**
 * Pendant le chant, les tuiles suivent les notes de la voix (hauteur suivie
 * en continu, syllabes, notes tenues). Pendant les passages instrumentaux,
 * elles suivent l'accompagnement (rythme, accents, mélodie instrumentale).
 */
export function extractFeaturesVoice(
  mix: Float32Array,
  vocals: Float32Array,
  accomp: Float32Array,
  sr: number,
  progress: ProgressFn = () => {},
  rhythm?: Rhythm,
): VoiceFeatures {
  // Rythme : l'accompagnement seul donne une pulsation plus propre (sauf a cappella).
  const rhythmSrc = rmsOf(accomp) > rmsOf(mix) * 0.12 ? accomp : mix;
  const base = extractFeatures(rhythmSrc, sr, (p, l) => progress(p * 0.55, l), rhythm);
  const none: VoiceFeatures = { ...base, voiceRatio: 0, followsVoice: false };
  if (rmsOf(vocals) < rmsOf(mix) * 0.03) return none;

  progress(0.56, 'Écoute de la voix…');
  let vOnsets: Onset[] = [];
  try {
    const venv = onsetEnvelope(vocals, sr, () => {});
    const vodf = normalizedOdf(venv.flux);
    if (vodf) vOnsets = pickOnsets(vodf, venv.hopT, venv.t0);
  } catch {
    return none;
  }
  progress(0.62, 'Suivi de la mélodie chantée…');
  const voice = trackVoice(vocals, sr, vOnsets, (p) => progress(0.62 + 0.26 * p, 'Suivi de la mélodie chantée…'));
  if (voice.notes.length < 12 || voice.ratio < 0.04) return { ...none, voiceRatio: voice.ratio };

  progress(0.89, 'Construction du parcours…');
  const { beats } = base;
  const beatLen = base.bpm > 0 ? 60 / base.bpm : 0.5;
  // Les chanteurs sont plus libres que la batterie : calage un peu plus large.
  const snap = makeSnap(beats, beatLen, 0.05, 0.15);
  const voiceCands: Candidate[] = voice.notes.map((n) => {
    const s = snap(n.t);
    return {
      t: s.t,
      strength: 0.6 + Math.min(1, n.strength),
      pitch: n.midi,
      sustain: n.dur >= 0.4 && n.stable ? n.dur : undefined,
      onBeat: s.onBeat,
      downBeat: s.onBeat && !!base.isDown[s.beatIndex],
      beatIndex: s.beatIndex,
      priority: 0,
    };
  });
  for (const c of voiceCands) {
    c.priority = c.strength + 0.9 + (c.onBeat ? 0.6 : 0) + (c.downBeat ? 0.3 : 0);
  }
  const vt = voiceCands.map((c) => c.t);
  const nearestVoice = (t: number) => {
    let lo = 0;
    let hi = vt.length;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (vt[mid] < t) lo = mid + 1;
      else hi = mid;
    }
    let d = Infinity;
    if (lo < vt.length) d = Math.min(d, Math.abs(vt[lo] - t));
    if (lo > 0) d = Math.min(d, Math.abs(vt[lo - 1] - t));
    return d;
  };
  // Instants où la voix tient une note (pas de tuile de remplissage par-dessus).
  const noteSpans: [number, number][] = voice.notes.map((n) => [n.t, n.t + n.dur]);
  const other: Candidate[] = [];
  for (const c of base.candidates) {
    if (!inSpans(voice.spans, c.t, 0.2)) {
      other.push(c); // passage instrumental
    } else if (c.onBeat && !inSpans(noteSpans, c.t, 0.1) && nearestVoice(c.t) > beatLen * 0.6) {
      other.push({ ...c, sustain: undefined, priority: c.priority - 0.4 }); // respiration du chant
    }
  }
  const all = [...voiceCands, ...other].sort((a, b) => a.t - b.t);
  const candidates: Candidate[] = [];
  for (const c of all) {
    const last = candidates[candidates.length - 1];
    if (last && c.t - last.t < 0.07) {
      if (c.priority > last.priority) candidates[candidates.length - 1] = c;
      continue;
    }
    candidates.push(c);
  }
  return {
    ...base,
    melodic: true,
    melodyConfidence: Math.max(base.melodyConfidence, voice.ratio),
    candidates: candidates.filter((c) => c.t >= 0.6 && c.t <= base.duration - 0.15),
    voiceRatio: voice.ratio,
    followsVoice: true,
    voiceSpans: voice.spans,
  };
}

const r3 = (t: number) => Math.round(t * 1000) / 1000;

/** Ajoute la structure (sur le mix complet) aux caractéristiques. */
function withStructure<T extends AnalysisFeatures>(
  f: T,
  mix: Float32Array,
  sr: number,
  progress: ProgressFn,
  voiceSpans?: [number, number][],
): T {
  progress(0.9, 'Repérage des refrains…');
  try {
    const downs = f.beats.filter((_, i) => f.isDown[i]);
    const st = detectStructure(mix, sr, f.beats, f.rhythmSource === 'ai' ? downs : undefined, { voiceSpans });
    if (st) return { ...f, sections: st.sections, structureConfidence: st.confidence };
  } catch {
    /* structure facultative */
  }
  return f;
}

function finishChart(
  features: AnalysisFeatures,
  seed: number,
  progress: ProgressFn,
  extra: Partial<Chart> = {},
): Chart {
  progress(0.92, 'Estimation de la difficulté…');
  // Le niveau est mesuré sur le parcours le plus complet jouable du morceau
  // (sans les accords, qui dépendent eux-mêmes du niveau).
  const measure = buildChart(features, 'expert', seed, false);
  if (measure.length < 4) throw new AnalysisError('Analyse impossible : trop peu de notes exploitables.');
  const level = levelFromScore(computeStats(measure).score);
  const notes = buildChart(features, level, seed);
  progress(1, 'Terminé');
  return {
    notes,
    bpm: features.bpm,
    duration: features.duration,
    melodic: features.melodic,
    level,
    stats: computeStats(notes),
    source: features.melodic ? 'melody' : 'rhythm',
    beats: features.beats.map(r3),
    downbeats: features.beats.filter((_, i) => features.isDown[i]).map(r3),
    rhythmSource: features.rhythmSource,
    ...(features.sections ? { sections: features.sections, structureConfidence: features.structureConfidence } : {}),
    ...extra,
  };
}

/** Analyse complète : caractéristiques → niveau → parcours final. */
export function analyzeSong(
  samples: Float32Array,
  sr: number,
  seed: number,
  progress: ProgressFn = () => {},
  rhythm?: Rhythm,
): Chart {
  const f = withStructure(extractFeatures(samples, sr, progress, rhythm), samples, sr, progress);
  return finishChart(f, seed, progress);
}

/** Analyse avancée à partir des pistes séparées (voix / accompagnement). */
export function analyzeSongWithVoice(
  mix: Float32Array,
  vocals: Float32Array,
  accomp: Float32Array,
  sr: number,
  seed: number,
  progress: ProgressFn = () => {},
  rhythm?: Rhythm,
): Chart {
  const fv = extractFeaturesVoice(mix, vocals, accomp, sr, progress, rhythm);
  const f = withStructure(fv, mix, sr, progress, fv.followsVoice ? fv.voiceSpans : undefined);
  return finishChart(f, seed, progress, {
    voiceRatio: Math.round(f.voiceRatio * 100) / 100,
    ...(f.followsVoice ? { source: 'voice' as const } : {}),
  });
}
