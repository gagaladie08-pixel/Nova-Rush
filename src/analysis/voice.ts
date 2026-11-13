import { fft, percentile } from './dsp';

/**
 * Suivi de la voix sur la piste vocale isolée (séparation de sources).
 *
 * - Hauteur en continu (YIN) : un changement de note devient une note, même
 *   sans attaque nette (chant lié).
 * - Les syllabes ré-articulées sur la même hauteur sont repérées par les
 *   attaques de la piste vocale.
 * - Les notes tenues donnent une durée fiable (tuiles longues).
 */

export interface VoiceNote {
  /** Début (s). */
  t: number;
  /** Durée (s). */
  dur: number;
  /** Hauteur MIDI (médiane de la note). */
  midi: number;
  /** Force relative (0..~1.5). */
  strength: number;
  /** Hauteur stable sur toute la note (tenue franche). */
  stable: boolean;
}

export interface VoiceTrack {
  notes: VoiceNote[];
  /** Plages où la voix chante (s), fusionnées. */
  spans: [number, number][];
  /** Part du morceau chantée (0..1). */
  ratio: number;
}

const HOP = 256;
const WIN = 1024;
const N = 2048;
const FMIN = 75;
const FMAX = 1050;
const VOICED_AP = 0.3;

type Progress = (p: number) => void;

/** Hauteur par trame (MIDI, NaN si non voisé) et apériodicité. */
export function pitchTrack(x: Float32Array, sr: number, progress: Progress = () => {}) {
  const hopT = HOP / sr;
  const tauMax = Math.min(N - WIN, Math.ceil(sr / FMIN));
  const tauMin = Math.max(2, Math.floor(sr / FMAX));
  const frames = Math.max(0, Math.floor((x.length - WIN - tauMax) / HOP) + 1);
  const midi = new Float32Array(frames).fill(NaN);
  const ap = new Float32Array(frames).fill(1);
  const rms = new Float32Array(frames);

  // Sommes cumulées des carrés (énergies de fenêtres en O(1)).
  const cum = new Float64Array(x.length + 1);
  for (let i = 0; i < x.length; i++) cum[i + 1] = cum[i] + x[i] * x[i];
  for (let f = 0; f < frames; f++) {
    const a = f * HOP;
    rms[f] = Math.sqrt((cum[a + WIN] - cum[a]) / WIN);
  }
  const ref = percentile(rms.filter((_, i) => i % 3 === 0), 0.95);
  const gate = Math.max(ref * 0.06, 1e-4);

  const re = new Float32Array(N);
  const im = new Float32Array(N);
  const cr = new Float32Array(N);
  const ci = new Float32Array(N);
  const d = new Float32Array(tauMax + 2);
  for (let f = 0; f < frames; f++) {
    if (rms[f] < gate) continue;
    const a = f * HOP;
    // a = x[a..a+WIN) (réel), b = x[a..a+WIN+tauMax) (imaginaire) : 2 FFT réelles en une.
    for (let i = 0; i < N; i++) {
      re[i] = i < WIN ? x[a + i] : 0;
      im[i] = i < WIN + tauMax ? x[a + i] : 0;
    }
    fft(re, im);
    // C_k = conj(A_k)·B_k
    for (let k = 0; k < N; k++) {
      const j = (N - k) & (N - 1);
      const ar = (re[k] + re[j]) / 2;
      const ai = (im[k] - im[j]) / 2;
      const br = (im[k] + im[j]) / 2;
      const bi = -(re[k] - re[j]) / 2;
      cr[k] = ar * br + ai * bi;
      ci[k] = -(ar * bi - ai * br); // conjugué pour la FFT inverse via FFT directe
    }
    fft(cr, ci);
    const e0 = cum[a + WIN] - cum[a];
    d[0] = 0;
    for (let t = 1; t <= tauMax; t++) {
      const et = cum[a + t + WIN] - cum[a + t];
      d[t] = Math.max(0, e0 + et - (2 * cr[t]) / N);
    }
    // Différence normalisée cumulée (CMNDF).
    let run = 0;
    let best = -1;
    let bestV = 1;
    let firstBelow = -1;
    for (let t = 1; t <= tauMax; t++) {
      run += d[t];
      const v = run > 0 ? (d[t] * t) / run : 1;
      d[t] = v;
    }
    for (let t = tauMin; t <= tauMax; t++) {
      if (d[t] < 0.15) {
        firstBelow = t;
        break;
      }
    }
    if (firstBelow > 0) {
      let t = firstBelow;
      while (t + 1 <= tauMax && d[t + 1] < d[t]) t++;
      best = t;
      bestV = d[t];
    } else {
      for (let t = tauMin; t <= tauMax; t++) {
        if (d[t] < bestV) {
          bestV = d[t];
          best = t;
        }
      }
    }
    if (best < 0 || bestV > VOICED_AP) continue;
    // Interpolation parabolique.
    let tau = best;
    if (best > tauMin && best < tauMax) {
      const y0 = d[best - 1];
      const y1 = d[best];
      const y2 = d[best + 1];
      const den = y0 - 2 * y1 + y2;
      if (den > 0) tau = best + Math.max(-0.5, Math.min(0.5, (0.5 * (y0 - y2)) / den));
    }
    const hz = sr / tau;
    midi[f] = 69 + 12 * Math.log2(hz / 440);
    ap[f] = bestV;
    if ((f & 511) === 0) progress(f / frames);
  }
  progress(1);
  return { midi, ap, rms, hopT, frames, ref };
}

/** Corrige les sauts d'octave isolés et lisse légèrement (médiane 5 trames). */
function cleanPitch(midi: Float32Array): Float32Array {
  const out = new Float32Array(midi);
  const w: number[] = [];
  for (let i = 0; i < midi.length; i++) {
    if (Number.isNaN(midi[i])) continue;
    w.length = 0;
    for (let j = i - 3; j <= i + 3; j++) if (j >= 0 && j < midi.length && !Number.isNaN(midi[j])) w.push(midi[j]);
    if (w.length < 3) continue;
    w.sort((a, b) => a - b);
    const med = w[w.length >> 1];
    let m = midi[i];
    if (Math.abs(m - med - 12) < 1.2) m -= 12;
    else if (Math.abs(m - med + 12) < 1.2) m += 12;
    out[i] = m;
  }
  const sm = new Float32Array(out.length).fill(NaN);
  for (let i = 0; i < out.length; i++) {
    if (Number.isNaN(out[i])) continue;
    w.length = 0;
    for (let j = i - 2; j <= i + 2; j++) if (j >= 0 && j < out.length && !Number.isNaN(out[j])) w.push(out[j]);
    w.sort((a, b) => a - b);
    sm[i] = w[w.length >> 1];
  }
  return sm;
}

/**
 * Découpe la piste vocale en notes.
 * @param onsets attaques détectées sur la piste vocale (s, force normalisée).
 */
export function trackVoice(
  vocals: Float32Array,
  sr: number,
  onsets: { t: number; strength: number }[],
  progress: Progress = () => {},
): VoiceTrack {
  const pt = pitchTrack(vocals, sr, progress);
  const { hopT, frames } = pt;
  const midi = cleanPitch(pt.midi);
  const center = WIN / 2 / sr; // la trame f est centrée sur f·hop + WIN/2
  const tOf = (f: number) => f * hopT + center;

  // Trames voisées, petits trous (≤ 3 trames) comblés.
  const voiced = new Uint8Array(frames);
  for (let f = 0; f < frames; f++) voiced[f] = Number.isNaN(midi[f]) ? 0 : 1;
  for (let f = 1; f < frames; f++) {
    if (voiced[f] || !voiced[f - 1]) continue;
    let g = f;
    while (g < frames && !voiced[g] && g - f < 4) g++;
    if (g < frames && voiced[g] && g - f <= 3) {
      for (let k = f; k < g; k++) {
        voiced[k] = 2; // comblé
        midi[k] = midi[f - 1];
      }
    }
  }

  // Attaques vocales triées, pour les ré-articulations et le calage des débuts.
  const strongOn = onsets.filter((o) => o.strength > 0.35).map((o) => o.t);
  const onsetNear = (t: number, before: number, after: number) => {
    let best: number | undefined;
    for (const o of strongOn) {
      if (o < t - before) continue;
      if (o > t + after) break;
      if (best === undefined || Math.abs(o - t) < Math.abs(best - t)) best = o;
    }
    return best;
  };

  // Creux d'énergie de la voix autour de la trame k (frontière de syllabe).
  const rms = pt.rms;
  const dipAt = (k: number) => {
    let m = Infinity;
    for (let j = k - 2; j <= k + 2; j++) if (j >= 0 && j < frames) m = Math.min(m, rms[j]);
    let pre = 0;
    let post = 0;
    for (let j = k - 10; j <= k - 3; j++) if (j >= 0) pre = Math.max(pre, rms[j]);
    for (let j = k + 3; j <= k + 10; j++) if (j < frames) post = Math.max(post, rms[j]);
    return m < 0.6 * Math.min(pre, post);
  };

  interface Seg {
    a: number;
    b: number; // exclu
    /** Coupure par changement de hauteur seulement (fusionnable si même note). */
    soft?: boolean;
  }
  // Hauteur de référence d'une note qui commence en `a` : médiane un peu après
  // l'attaque (les premières trames sont instables).
  const lookAhead = (a: number, g: number, sm: Float32Array, f: number) => {
    const v: number[] = [];
    for (let j = a + 2; j < Math.min(g, a + 11); j++) v.push(sm[j - f]);
    if (!v.length) return sm[a - f];
    v.sort((x, y) => x - y);
    return v[v.length >> 1];
  };
  const segs: Seg[] = [];
  let f = 0;
  while (f < frames) {
    if (!voiced[f]) {
      f++;
      continue;
    }
    let g = f;
    while (g < frames && voiced[g]) g++;
    // Découpage de la plage voisée [f, g) en notes. La hauteur est lissée sur
    // ~1 période de vibrato (±7 trames) : le vibrato disparaît, un vrai
    // changement de note (même d'un demi-ton) reste net et bien placé.
    const sm = new Float32Array(g - f);
    for (let k = f; k < g; k++) {
      let acc = 0;
      let cnt = 0;
      for (let j = Math.max(f, k - 7); j <= Math.min(g - 1, k + 7); j++) {
        acc += midi[j];
        cnt++;
      }
      sm[k - f] = acc / cnt;
    }
    let start = f;
    let refSum = lookAhead(f, g, sm, f) * 4;
    let refN = 4;
    let off = 0;
    for (let k = f; k < g; k++) {
      const m = sm[k - f];
      if (k < start + 3) continue;
      const ref = refSum / refN;
      if (Math.abs(m - ref) > 0.6) {
        off++;
        if (off >= 3) {
          // Coupure là où la courbe lissée passe à mi-chemin.
          let cut = k;
          while (cut - 1 > start + 2 && Math.abs(sm[cut - 1 - f] - ref) > 0.5) cut--;
          segs.push({ a: start, b: cut, soft: true });
          start = cut;
          refSum = lookAhead(cut, g, sm, f) * 4;
          refN = 4;
          off = 0;
        }
        continue;
      }
      off = 0;
      // Trou comblé avec creux d'énergie : nouvelle syllabe sur la même hauteur.
      if (voiced[k] === 2 && voiced[k - 1] === 1 && (k - start) * hopT >= 0.08) {
        let e = k;
        while (e < g && voiced[e] === 2) e++;
        if (e - k >= 2 && e < g && dipAt(Math.floor((k + e) / 2))) {
          segs.push({ a: start, b: k });
          start = e;
          refSum = lookAhead(e, g, sm, f) * 4;
          refN = 4;
          k = e - 1;
          continue;
        }
      }
      refSum += m;
      refN++;
      // Ré-articulation : attaque vocale nette + creux d'énergie au milieu d'une note.
      const t = tOf(k);
      if ((k - start) * hopT >= 0.12 && g - k > 4) {
        const o = onsetNear(t, hopT / 2, hopT / 2);
        if (o !== undefined && dipAt(k)) {
          segs.push({ a: start, b: k });
          start = k;
          refSum = lookAhead(k, g, sm, f) * 4;
          refN = 4;
        }
      }
    }
    segs.push({ a: start, b: g });
    f = g;
  }

  // Fusion des fragments trop courts (glissements, ornements) avec la note suivante.
  const minFrames = Math.ceil(0.08 / hopT);
  const merged: Seg[] = [];
  for (let i = 0; i < segs.length; i++) {
    const s = { ...segs[i] };
    if (s.b - s.a < minFrames) {
      const next = segs[i + 1];
      if (next && next.a === s.b) {
        next.a = s.a;
        continue;
      }
      const prev = merged[merged.length - 1];
      if (prev && prev.b === s.a) {
        prev.b = s.b;
        continue;
      }
      if (s.b - s.a < minFrames / 2) continue;
    }
    merged.push(s);
  }

  const notes: VoiceNote[] = [];
  const vals: number[] = [];
  let prevSoft = false;
  let prevSeg: Seg | undefined;
  for (const s of merged) {
    vals.length = 0;
    const skip = s.b - s.a > 6 ? 2 : 0; // ignore l'attaque pour la hauteur
    let e = 0;
    for (let k = s.a; k < s.b; k++) {
      if (k >= s.a + skip && voiced[k] === 1) vals.push(midi[k]);
      e += pt.rms[k];
    }
    if (!vals.length) continue;
    vals.sort((a, b) => a - b);
    const med = vals[vals.length >> 1];
    const spread = vals[Math.floor(vals.length * 0.85)] - vals[Math.floor(vals.length * 0.15)];
    const prev = notes[notes.length - 1];
    const contiguous = prev && Math.abs(prev.t + prev.dur - (tOf(s.a) - hopT / 2)) < hopT * 1.5;
    // Début de la partie voisée (biais de détection corrigé, ~12 ms). Le temps
    // musical tombe sur la voyelle : une consonne qui précède n'avance pas la
    // tuile ; une attaque qui coïncide avec le début voisé l'affine.
    const startedSoft = !!prevSeg && prevSeg.soft === true && prevSeg.b === s.a;
    prevSeg = s;
    const tv = tOf(s.a) - hopT / 2 - (startedSoft ? 0.018 : 0.012);
    let t = tv;
    const o = onsetNear(tv, startedSoft ? 0.045 : 0.015, 0.03);
    if (o !== undefined && (!prev || o > prev.t + 0.05)) t = o;
    const end = tOf(s.b - 1) + hopT / 2;
    const dur = end - t;
    if (dur < 0.06) continue;
    if (contiguous && prev && prevSoft && prev.midi === Math.round(med)) {
      // Coupure de hauteur sans changement de note : on prolonge la précédente.
      prev.dur = end - prev.t;
      prev.stable = prev.stable && spread < 1.2;
      prevSoft = !!s.soft;
      continue;
    }
    if (contiguous && prev) prev.dur = Math.max(0.03, t - prev.t);
    prevSoft = !!s.soft;
    notes.push({
      t,
      dur,
      midi: Math.round(med),
      strength: e / (s.b - s.a) / (pt.ref || 1),
      stable: spread < 1.2,
    });
  }

  // Plages chantées (notes fusionnées si l'écart est < 0,35 s).
  const spans: [number, number][] = [];
  for (const n of notes) {
    const last = spans[spans.length - 1];
    if (last && n.t - last[1] < 0.35) last[1] = Math.max(last[1], n.t + n.dur);
    else spans.push([n.t, n.t + n.dur]);
  }
  const dur = vocals.length / sr;
  const sung = spans.reduce((a, [s, e]) => a + (e - s), 0);
  return { notes, spans, ratio: dur > 0 ? sung / dur : 0 };
}

/** Vrai si t tombe dans une plage chantée (avec une marge). */
export function inSpans(spans: [number, number][], t: number, margin = 0): boolean {
  let lo = 0;
  let hi = spans.length - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const [s, e] = spans[mid];
    if (t < s - margin) hi = mid - 1;
    else if (t > e + margin) lo = mid + 1;
    else return true;
  }
  return false;
}
