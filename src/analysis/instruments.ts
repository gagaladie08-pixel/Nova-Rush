/**
 * Notes des instruments : décodage des sorties de Basic Pitch (Spotify,
 * Apache 2.0) calculées par le téléphone sur les pistes « basse » et
 * « autres » de Demucs.
 *
 * `notesFromPosteriors` est un portage exact de
 * basic_pitch.note_creation.output_to_notes_polyphonic (seuils par défaut de
 * basic_pitch.predict : attaque 0,5, note 0,3, durée minimale 11 images,
 * attaques déduites des variations, « astuce melodia »). Vérifié contre le
 * code officiel (tests/fixtures/basic-pitch-notes.json).
 */

export const BP_KEYS = 88;
const MIDI_OFFSET = 21;
const BP_SR = 22050;
const BP_HOP = 256;
/** Images utiles par fenêtre (172 − 2 × 15) et pas d'une fenêtre (échantillons). */
const BP_WIN_FRAMES = 142;
const BP_STEP = 43844 - 30 * BP_HOP;

export interface Posteriors {
  frames: number;
  /** Attaques (images × 88), 0..255. */
  onset: Uint8Array;
  /** Notes (images × 88), 0..255. */
  note: Uint8Array;
}

/** Note d'instrument (s, MIDI, amplitude 0..1). */
export interface InstrNote {
  t: number;
  dur: number;
  midi: number;
  amp: number;
}

/** Lit un fichier bp_*.u8 : [attaques] puis [notes], images × 88 octets. */
export function parsePosteriors(bytes: Uint8Array): Posteriors | undefined {
  const per = 2 * BP_KEYS;
  if (bytes.length < per || bytes.length % per !== 0) return undefined;
  const frames = bytes.length / per;
  return { frames, onset: bytes.subarray(0, frames * BP_KEYS), note: bytes.subarray(frames * BP_KEYS) };
}

/** Instant (s) de l'image k (fenêtres de 2 s recousues, sans dérive). */
export function bpFrameTime(k: number): number {
  return (Math.floor(k / BP_WIN_FRAMES) * BP_STEP + (k % BP_WIN_FRAMES) * BP_HOP) / BP_SR;
}

export interface NoteOptions {
  onsetThresh?: number;
  frameThresh?: number;
  minNoteLen?: number;
  energyTol?: number;
  melodia?: boolean;
}

/** Événements (début, fin) en images, MIDI, amplitude — comme Basic Pitch. */
export function notesFromPosteriors(p: Posteriors, o: NoteOptions = {}): [number, number, number, number][] {
  const onsetThresh = o.onsetThresh ?? 0.5;
  const frameThresh = o.frameThresh ?? 0.3;
  const minLen = o.minNoteLen ?? 11;
  const tol = o.energyTol ?? 11;
  const T = p.frames;
  const K = BP_KEYS;
  const frames = new Float32Array(T * K);
  const onsets = new Float32Array(T * K);
  for (let i = 0; i < T * K; i++) {
    frames[i] = p.note[i] / 255;
    onsets[i] = p.onset[i] / 255;
  }
  // Attaques déduites des montées d'énergie (get_infered_onsets, n_diff = 2).
  const diff = new Float32Array(T * K);
  let maxDiff = 0;
  let maxOnset = 0;
  for (let i = 0; i < T * K; i++) if (onsets[i] > maxOnset) maxOnset = onsets[i];
  for (let t = 2; t < T; t++) {
    for (let k = 0; k < K; k++) {
      const v = frames[t * K + k];
      const d1 = v - frames[(t - 1) * K + k];
      const d2 = v - frames[(t - 2) * K + k];
      let d = Math.min(d1, d2);
      if (d < 0) d = 0;
      diff[t * K + k] = d;
      if (d > maxDiff) maxDiff = d;
    }
  }
  const on = new Float32Array(T * K);
  for (let i = 0; i < T * K; i++) {
    const d = maxDiff > 0 ? (maxOnset * diff[i]) / maxDiff : 0;
    on[i] = Math.max(onsets[i], d);
  }
  // Pics (maxima stricts dans le temps) au-dessus du seuil, du plus tard au plus tôt.
  const peaks: number[] = [];
  for (let t = 1; t < T - 1; t++) {
    for (let k = 0; k < K; k++) {
      const v = on[t * K + k];
      if (v >= onsetThresh && v > on[(t - 1) * K + k] && v > on[(t + 1) * K + k]) peaks.push(t * K + k);
    }
  }
  const remaining = frames.slice();
  const events: [number, number, number, number][] = [];
  const mean = (a: number, b: number, k: number) => {
    let s = 0;
    for (let t = a; t < b; t++) s += frames[t * K + k];
    return b > a ? s / (b - a) : NaN;
  };
  const clear = (t: number, k: number) => {
    remaining[t * K + k] = 0;
    if (k < K - 1) remaining[t * K + k + 1] = 0;
    if (k > 0) remaining[t * K + k - 1] = 0;
  };
  for (let n = peaks.length - 1; n >= 0; n--) {
    const start = Math.floor(peaks[n] / K);
    const k = peaks[n] % K;
    if (start >= T - 1) continue;
    let i = start + 1;
    let c = 0;
    while (i < T - 1 && c < tol) {
      if (remaining[i * K + k] < frameThresh) c++;
      else c = 0;
      i++;
    }
    i -= c;
    if (i - start <= minLen) continue;
    for (let t = start; t < i; t++) clear(t, k);
    events.push([start, i, k + MIDI_OFFSET, mean(start, i, k)]);
  }
  if (o.melodia ?? true) {
    // Notes sans attaque : de la plus forte énergie restante à la plus faible
    // (même ordre que argmax répété : valeur décroissante, puis indice croissant).
    const cells: number[] = [];
    for (let i = 0; i < T * K; i++) if (remaining[i] > frameThresh) cells.push(i);
    cells.sort((a, b) => remaining[b] - remaining[a] || a - b);
    for (const cell of cells) {
      if (!(remaining[cell] > frameThresh)) continue;
      const mid = Math.floor(cell / K);
      const k = cell % K;
      remaining[cell] = 0;
      let i = mid + 1;
      let c = 0;
      while (i < T - 1 && c < tol) {
        if (remaining[i * K + k] < frameThresh) c++;
        else c = 0;
        clear(i, k);
        i++;
      }
      const end = i - 1 - c;
      i = mid - 1;
      c = 0;
      while (i > 0 && c < tol) {
        if (remaining[i * K + k] < frameThresh) c++;
        else c = 0;
        clear(i, k);
        i--;
      }
      const start = i + 1 + c;
      if (end - start <= minLen) continue;
      events.push([start, end, k + MIDI_OFFSET, mean(start, end, k)]);
    }
  }
  return events;
}

/** Notes en secondes, triées par début. */
export function instrumentNotes(p: Posteriors, o: NoteOptions = {}): InstrNote[] {
  return notesFromPosteriors(p, o)
    .map(([a, b, midi, amp]) => {
      const t = bpFrameTime(a);
      return { t, dur: bpFrameTime(b) - t, midi, amp };
    })
    .sort((x, y) => x.t - y.t || y.midi - x.midi);
}

/**
 * Ligne mélodique (« ligne de crête ») : à chaque attaque, la note la plus
 * aiguë et assez forte parmi celles qui sonnent ; les notes d'accompagnement
 * plus graves qui démarrent en même temps sont ignorées.
 */
export function skyline(notes: InstrNote[], minAmp = 0.32, together = 0.05): InstrNote[] {
  const strong = notes.filter((n) => n.amp >= minAmp);
  const out: InstrNote[] = [];
  for (let i = 0; i < strong.length; ) {
    let j = i;
    let best = strong[i];
    while (j < strong.length && strong[j].t - strong[i].t <= together) {
      if (strong[j].midi > best.midi) best = strong[j];
      j++;
    }
    // Ignorée si une note plus aiguë et plus forte sonne déjà par-dessus.
    const covered = out.some((p) => p.t + p.dur > best.t + 0.08 && p.midi > best.midi + 4 && p.amp >= best.amp);
    if (!covered) {
      const last = out[out.length - 1];
      if (last && last.t + last.dur > best.t) last.dur = Math.max(0.05, best.t - last.t);
      out.push({ ...best });
    }
    i = j;
  }
  return out;
}
