/**
 * Chef d'orchestre : combine les IA (voix UVR + CREPE, temps Beat This!,
 * batterie / basse / autres de Demucs, notes Basic Pitch, structure) en un
 * seul parcours.
 *
 * - Par section, une source « mène » : la voix quand on chante, l'instrument
 *   mélodique pour les intros et les solos, la batterie pour les breaks, la
 *   basse + la batterie pour les passages rythmiques ; sinon l'accompagnement
 *   (analyse classique).
 * - Les IA se contrôlent entre elles (score de confiance) ; une source peu
 *   fiable est écartée, jusqu'au parcours d'avant si plus rien n'est sûr.
 * - Tuiles longues seulement si deux sources confirment la note tenue.
 * - Accords appuyés sur les coups de cymbale, intensité qui monte jusqu'au
 *   dernier refrain.
 *
 * Tout part d'un résumé (`MusicSummary`, quelques centaines de Ko) : le
 * parcours peut être reconstruit sans relancer les IA.
 */
import {
  type AnalysisFeatures,
  type Candidate,
  type ProgressFn,
  type Rhythm,
  type VoiceFeatures,
  extractFeaturesVoice,
  finishChart,
  makeSnap,
  withStructure,
} from './analyzer';
import type { ExternalPitch } from './beatsFile';
import { CRASH, HAT, KICK, SNARE, beatAgreement, type DrumHit } from './drums';
import { skyline, type InstrNote } from './instruments';
import type { Chart, ConductorReport, Lead, Section } from '../game/types';

export type { Lead } from '../game/types';

/** Sorties des IA sur la batterie, la basse et les autres instruments (horloge du mix). */
export interface Instruments {
  drums?: DrumHit[];
  bass?: InstrNote[];
  other?: InstrNote[];
}

export interface MusicSummary {
  v: 1;
  duration: number;
  bpm: number;
  beats: number[];
  /** Indices des premiers temps de mesure dans `beats`. */
  downs: number[];
  beatEnergy: number[];
  rhythmSource: 'ai' | 'classic';
  melodic: boolean;
  melodyConfidence: number;
  sections?: Section[];
  structureConfidence?: number;
  voice?: {
    ratio: number;
    spans: [number, number][];
    noteSpans: [number, number][];
    cands: Candidate[];
    pitchSource: 'ai' | 'classic';
  };
  /** Attaques de l'accompagnement (analyse classique). */
  accomp: Candidate[];
  drums?: DrumHit[];
  bass?: InstrNote[];
  other?: InstrNote[];
}

type Confidence = ConductorReport['conf'];

/** À augmenter quand le chef d'orchestre change : les parcours sont reconstruits depuis leur résumé. */
export const CONDUCTOR_VERSION = 1;

const r3 = (t: number) => Math.round(t * 1000) / 1000;
const r2 = (t: number) => Math.round(t * 100) / 100;

/* ------------------------------------------------------------------ */
/* Résumé                                                              */
/* ------------------------------------------------------------------ */

const packCand = (c: Candidate): Candidate => {
  const o: Candidate = {
    t: r3(c.t),
    strength: r3(c.strength),
    onBeat: c.onBeat,
    downBeat: c.downBeat,
    beatIndex: c.beatIndex,
    priority: r3(c.priority),
  };
  if (c.pitch !== undefined) o.pitch = r2(c.pitch);
  if (c.sustain !== undefined) o.sustain = r3(c.sustain);
  return o;
};

export function makeSummary(f: VoiceFeatures, inst: Instruments, pitch?: ExternalPitch): MusicSummary {
  const s: MusicSummary = {
    v: 1,
    duration: r3(f.duration),
    bpm: f.bpm,
    beats: f.beats.map(r3),
    downs: f.isDown.flatMap((d, i) => (d ? [i] : [])),
    beatEnergy: f.beatEnergy.map((e) => Number(e.toPrecision(4))),
    rhythmSource: f.rhythmSource,
    melodic: f.melodic,
    melodyConfidence: r3(f.melodyConfidence),
    accomp: (f.accompCands ?? f.candidates).map(packCand),
  };
  if (f.sections) {
    s.sections = f.sections;
    s.structureConfidence = f.structureConfidence;
  }
  if (f.followsVoice && f.voiceCands && f.voiceSpans) {
    s.voice = {
      ratio: r3(f.voiceRatio),
      spans: f.voiceSpans.map(([a, b]) => [r3(a), r3(b)] as [number, number]),
      noteSpans: (f.noteSpans ?? []).map(([a, b]) => [r3(a), r3(b)] as [number, number]),
      cands: f.voiceCands.map(packCand),
      pitchSource: pitch ? 'ai' : 'classic',
    };
  }
  if (inst.drums?.length) s.drums = inst.drums.map((h) => ({ t: r3(h.t), k: h.k, s: r2(h.s) }));
  const packNotes = (n: InstrNote[]) => n.map((x) => ({ t: r3(x.t), dur: r3(x.dur), midi: x.midi, amp: r2(x.amp) }));
  if (inst.bass?.length) s.bass = packNotes(inst.bass);
  if (inst.other?.length) s.other = packNotes(inst.other);
  return s;
}

/* ------------------------------------------------------------------ */
/* Confiance                                                           */
/* ------------------------------------------------------------------ */

/** Part des débuts de notes posés sur la grille (temps ou demi-temps, ±70 ms). */
function gridShare(times: number[], beats: number[]): number {
  if (times.length < 8 || beats.length < 4) return 0;
  const grid: number[] = [];
  for (let i = 0; i < beats.length; i++) {
    grid.push(beats[i]);
    if (i + 1 < beats.length) grid.push((beats[i] + beats[i + 1]) / 2);
  }
  let ok = 0;
  let j = 0;
  for (const t of times) {
    while (j + 1 < grid.length && grid[j + 1] <= t) j++;
    const d = Math.min(Math.abs(t - grid[j]), j + 1 < grid.length ? Math.abs(grid[j + 1] - t) : Infinity);
    if (d <= 0.07) ok++;
  }
  return ok / times.length;
}

export function confidences(s: MusicSummary, sky: InstrNote[], bassLine: InstrNote[]): Confidence {
  return {
    voice: s.voice ? Math.min(1, s.voice.ratio * 2) * (s.voice.cands.length >= 12 ? 1 : 0) : 0,
    drums: s.drums ? beatAgreement(s.drums, s.beats) : 0,
    instrument: sky.length >= 16 ? gridShare(sky.map((n) => n.t), s.beats) : 0,
    bass: bassLine.length >= 16 ? gridShare(bassLine.map((n) => n.t), s.beats) : 0,
  };
}

/** Une note par instant sur la basse : la plus grave. */
function bassLineOf(notes: InstrNote[]): InstrNote[] {
  const out: InstrNote[] = [];
  for (const n of notes) {
    if (n.amp < 0.3) continue;
    const last = out[out.length - 1];
    if (last && n.t - last.t < 0.05) {
      if (n.midi < last.midi) out[out.length - 1] = n;
      continue;
    }
    out.push(n);
  }
  return out;
}

/* ------------------------------------------------------------------ */
/* Chef d'orchestre                                                    */
/* ------------------------------------------------------------------ */

const DRUMS_MIN = 0.5;
const INSTR_MIN = 0.45;

interface Block {
  t: number;
  end: number;
  sec?: Section;
  si: number;
}

function overlap(spans: [number, number][], a: number, b: number): number {
  let s = 0;
  for (const [x, y] of spans) {
    const lo = Math.max(a, x);
    const hi = Math.min(b, y);
    if (hi > lo) s += hi - lo;
  }
  return s;
}

/** Durée pendant laquelle des notes d'instrument couvrent [a, b] (avec, éventuellement, la même note). */
function heldBy(notes: InstrNote[], a: number, b: number, pc?: number): number {
  const spans: [number, number][] = [];
  for (const n of notes) {
    if (n.t >= b) break;
    if (n.t + n.dur <= a) continue;
    if (pc !== undefined && ((n.midi % 12) + 12) % 12 !== pc) continue;
    spans.push([n.t, n.t + n.dur]);
  }
  spans.sort((x, y) => x[0] - y[0]);
  let covered = 0;
  let cur = a;
  for (const [x, y] of spans) {
    const lo = Math.max(cur, x);
    const hi = Math.min(b, y);
    if (hi > lo) {
      covered += hi - lo;
      cur = hi;
    }
  }
  return b > a ? covered / (b - a) : 0;
}

export function conduct(s: MusicSummary): { features: AnalysisFeatures; report: ConductorReport } {
  const beats = s.beats;
  const isDown = beats.map(() => false);
  for (const i of s.downs) if (i < isDown.length) isDown[i] = true;
  const beatLen = s.bpm > 0 ? 60 / s.bpm : 0.5;
  const snap = makeSnap(beats, beatLen, 0.04, 0.13);

  const other = [...(s.other ?? [])].sort((a, b) => a.t - b.t);
  const bass = [...(s.bass ?? [])].sort((a, b) => a.t - b.t);
  const sky = skyline(other);
  const bassLine = bassLineOf(bass);
  const conf = confidences(s, sky, bassLine);
  const drumsOk = conf.drums >= DRUMS_MIN;
  const instrOk = conf.instrument >= INSTR_MIN;
  const bassOk = conf.bass >= INSTR_MIN;
  const drums = drumsOk ? s.drums ?? [] : [];
  const anyInstr = !!(s.other || s.bass || s.drums);

  // Blocs : les sections (si la structure est sûre) ou des phrases de 4 mesures.
  const secs = s.sections && (s.structureConfidence ?? 0) >= 0.3 ? s.sections : undefined;
  const blocks: Block[] = [];
  if (secs) secs.forEach((sec, si) => blocks.push({ t: sec.t, end: sec.end, sec, si }));
  else {
    for (let b = 0; b < beats.length; b += 16) blocks.push({ t: b === 0 ? 0 : beats[b], end: beats[b + 16] ?? s.duration, si: -1 });
    if (!blocks.length) blocks.push({ t: 0, end: s.duration, si: -1 });
    blocks[blocks.length - 1].end = s.duration;
  }

  // Intensité : énergie de la section, refrains de plus en plus forts, le dernier au maximum.
  const refrains = secs ? secs.filter((x) => x.kind === 'refrain') : [];
  const lastRefrain = refrains[refrains.length - 1];
  const liftOf = (b: Block) => {
    if (!b.sec) return 0.5;
    if (b.sec === lastRefrain) return 1;
    const e = b.sec.energy ?? 0.5;
    if (b.sec.kind === 'refrain') {
      const k = refrains.indexOf(b.sec);
      return Math.min(0.94, 0.6 + 0.3 * (refrains.length > 1 ? k / (refrains.length - 1) : 0) + 0.1 * e);
    }
    return Math.min(0.9, 0.2 + 0.5 * e);
  };

  // Source qui mène chaque bloc.
  const countIn = <T extends { t: number }>(list: T[], a: number, b: number, keep: (x: T) => boolean = () => true) => {
    let n = 0;
    for (const x of list) if (x.t >= a && x.t < b && keep(x)) n++;
    return n;
  };
  const beatsIn = (a: number, b: number) => Math.max(1, countIn(beats.map((t) => ({ t })), a, b));
  const leadOf = (b: Block): Lead => {
    const len = Math.max(0.5, b.end - b.t);
    if (s.voice && conf.voice > 0 && overlap(s.voice.spans, b.t, b.end) / len >= 0.3) return 'voice';
    const nb = beatsIn(b.t, b.end);
    const instDens = instrOk ? countIn(sky, b.t, b.end) / len : 0;
    const drumDens = drumsOk ? countIn(drums, b.t, b.end, (h) => h.k === KICK || h.k === SNARE) / nb : 0;
    const bassDens = bassOk ? countIn(bassLine, b.t, b.end) / nb : 0;
    if (instDens >= 0.7) return 'instrument';
    if (drumDens >= 0.5 && bassDens >= 0.5) return 'groove';
    if (drumDens >= 0.5) return 'drums';
    // Repli : comme avant le chef d'orchestre (voix là où l'on chante, accompagnement ailleurs).
    return s.voice && conf.voice > 0 && overlap(s.voice.spans, b.t, b.end) > 0 ? 'voice' : 'accomp';
  };
  const leads = blocks.map(leadOf);
  // Même motif à chaque refrain : toutes les sections d'un groupe suivent la source de la première.
  if (secs) {
    const firstOfGroup = new Map<number, Lead>();
    blocks.forEach((b, i) => {
      if (!b.sec) return;
      const g = b.sec.group;
      if (!firstOfGroup.has(g)) firstOfGroup.set(g, leads[i]);
      else if (b.sec.kind === 'refrain') leads[i] = firstOfGroup.get(g)!;
    });
  }

  const mk = (t: number, strength: number, pitch: number | undefined, sustain: number | undefined, bonus: number): Candidate => {
    const sn = snap(t);
    const c: Candidate = {
      t: sn.t,
      strength,
      pitch,
      sustain,
      onBeat: sn.onBeat,
      downBeat: sn.onBeat && !!isDown[sn.beatIndex],
      beatIndex: sn.beatIndex,
      priority: 0,
    };
    c.priority = strength + bonus + (c.onBeat ? 0.6 : 0) + (c.downBeat ? 0.3 : 0);
    return c;
  };
  const accomp = s.accomp;
  const inBlock = <T extends { t: number }>(list: T[], b: Block) => list.filter((x) => x.t >= b.t && x.t < b.end);
  const nearVoice = (t: number) => (s.voice ? s.voice.cands.some((c) => Math.abs(c.t - t) <= beatLen * 0.6) : false);
  const inSpans = (spans: [number, number][], t: number, pad: number) => spans.some(([a, z]) => t >= a - pad && t <= z + pad);

  const raw: Candidate[] = [];
  blocks.forEach((b, i) => {
    const lead = leads[i];
    const lift = liftOf(b);
    const out: Candidate[] = [];
    if (lead === 'voice' && s.voice) {
      for (const c of inBlock(s.voice.cands, b)) out.push(c);
      // Respirations du chant : accents de l'accompagnement (ou grosse caisse / caisse claire).
      for (const c of inBlock(accomp, b)) {
        if (inSpans(s.voice.spans, c.t, 0.2)) {
          if (c.onBeat && !inSpans(s.voice.noteSpans, c.t, 0.1) && !nearVoice(c.t)) out.push({ ...c, sustain: undefined, priority: c.priority - 0.4 });
        } else out.push(c);
      }
    } else if (lead === 'instrument') {
      for (const n of inBlock(sky, b)) out.push(mk(n.t, 0.5 + n.amp, n.midi, n.dur >= 0.4 ? n.dur : undefined, 0.8));
      for (const c of inBlock(accomp, b)) out.push({ ...c, sustain: undefined, priority: c.priority - 0.5 });
    } else if (lead === 'groove') {
      for (const n of inBlock(bassLine, b)) out.push(mk(n.t, 0.5 + n.amp, n.midi, n.dur >= 0.4 ? n.dur : undefined, 0.7));
      for (const h of inBlock(drums, b)) {
        if (h.k === KICK || h.k === SNARE) out.push(mk(h.t, 0.4 + 0.3 * Math.min(2, h.s), undefined, undefined, 0.2));
      }
    } else if (lead === 'drums') {
      // Colonnes stables : grosse caisse à gauche, caisse claire à droite, charleston au milieu.
      let side = 0;
      for (const h of inBlock(drums, b)) {
        if (h.k === CRASH) continue;
        const c = mk(h.t, 0.4 + 0.3 * Math.min(2, h.s), undefined, undefined, h.k === HAT ? -0.6 : 0.5);
        c.lane = h.k === KICK ? 0 : h.k === SNARE ? 3 : ((1 + (side++ % 2)) as 1 | 2);
        out.push(c);
      }
    } else {
      out.push(...inBlock(accomp, b));
    }
    for (const c of out) raw.push({ ...c, lift });
  });

  // Grosse caisse sur un premier temps : un peu plus prioritaire, quelle que soit la source.
  if (drumsOk) {
    const kicks = drums.filter((h) => h.k === KICK).map((h) => h.t);
    for (const c of raw) if (c.downBeat && kicks.some((t) => Math.abs(t - c.t) < 0.06)) c.priority += 0.2;
  }

  raw.sort((a, b) => a.t - b.t);
  const candidates: Candidate[] = [];
  for (const c of raw) {
    const last = candidates[candidates.length - 1];
    if (last && c.t - last.t < 0.07) {
      if (c.priority > last.priority) candidates[candidates.length - 1] = c;
      continue;
    }
    candidates.push(c);
  }

  // Tuiles longues : deux sources doivent confirmer la tenue.
  if (anyInstr) {
    const pitched = [...other, ...bass].sort((a, b) => a.t - b.t);
    const accompHeld = accomp.filter((c) => c.sustain !== undefined);
    for (const c of candidates) {
      if (c.sustain === undefined) continue;
      const a = c.t + 0.05;
      const z = c.t + c.sustain;
      const pc = c.pitch !== undefined ? ((Math.round(c.pitch) % 12) + 12) % 12 : undefined;
      const sameNote = pc !== undefined ? heldBy(pitched, a, z, pc) >= 0.5 : false;
      const chordUnder = heldBy(pitched, a, z) >= 0.8;
      const classic = accompHeld.some((q) => Math.abs(q.t - c.t) < 0.1 && (q.sustain ?? 0) >= c.sustain! * 0.6);
      const fromVoice = s.voice?.cands.some((q) => q.t === c.t && q.sustain !== undefined) ?? false;
      // Voix tenue + instrument tenu dessous ; note d'instrument + suivi classique ou basse tenue.
      const ok = fromVoice ? sameNote || chordUnder : classic || sameNote || (chordUnder && heldBy(bass, a, z) >= 0.8);
      if (!ok) c.sustain = undefined;
    }
  }

  // Cymbales : accord appuyé sur le temps (ajouté s'il n'y avait pas de tuile).
  if (drumsOk) {
    for (const h of drums) {
      if (h.k !== CRASH) continue;
      const sn = snap(h.t);
      if (!sn.onBeat) continue;
      let c = candidates.find((x) => Math.abs(x.t - sn.t) < 0.07);
      if (!c) {
        const b = blocks.find((x) => sn.t >= x.t && sn.t < x.end);
        c = { ...mk(h.t, 1.2, undefined, undefined, 0.6), lift: b ? liftOf(b) : 0.5 };
        const at = candidates.findIndex((x) => x.t > c!.t);
        candidates.splice(at < 0 ? candidates.length : at, 0, c);
      }
      c.accent = true;
      c.priority += 0.8;
    }
  }

  const melodic = candidates.some((c) => c.pitch !== undefined) && (s.melodic || !!s.voice || instrOk);
  const features: AnalysisFeatures = {
    bpm: s.bpm,
    beats,
    onsets: [],
    melodic,
    melodyConfidence: s.melodyConfidence,
    candidates: candidates.filter((c) => c.t >= 0.6 && c.t <= s.duration - 0.15),
    duration: s.duration,
    beatEnergy: s.beatEnergy,
    isDown,
    rhythmSource: s.rhythmSource,
    ...(s.sections ? { sections: s.sections, structureConfidence: s.structureConfidence } : {}),
  };
  const report: ConductorReport = {
    conf: { voice: r2(conf.voice), drums: r2(conf.drums), instrument: r2(conf.instrument), bass: r2(conf.bass) },
    leads: blocks.map((b, i) => ({ t: r2(b.t), end: r2(b.end), lead: leads[i] })),
  };
  return { features, report };
}

/** Parcours à partir du résumé (sans relancer les IA). */
export function chartFromSummary(s: MusicSummary, seed: number, progress: ProgressFn = () => {}): Chart {
  const { features, report } = conduct(s);
  const drums = s.drums && report.conf.drums >= DRUMS_MIN ? s.drums : undefined;
  return finishChart(features, seed, progress, {
    ...(s.voice ? { pitchSource: s.voice.pitchSource, source: 'voice' as const } : {}),
    voiceRatio: s.voice ? r2(s.voice.ratio) : 0,
    conductor: report,
    conductorVersion: CONDUCTOR_VERSION,
    ...(drums
      ? {
          kicks: drums.filter((h) => h.k === KICK).map((h) => h.t),
          crashes: drums.filter((h) => h.k === CRASH).map((h) => h.t),
        }
      : {}),
  });
}

/** Analyse avancée complète : sources séparées → résumé → parcours. */
export function analyzeSongAdvanced(
  mix: Float32Array,
  vocals: Float32Array,
  accomp: Float32Array,
  sr: number,
  seed: number,
  progress: ProgressFn = () => {},
  rhythm?: Rhythm,
  pitch?: ExternalPitch,
  inst: Instruments = {},
): { chart: Chart; summary: MusicSummary } {
  const fv = extractFeaturesVoice(mix, vocals, accomp, sr, progress, rhythm, pitch);
  const f = withStructure(fv, mix, sr, progress, fv.followsVoice ? fv.voiceSpans : undefined);
  const summary = makeSummary(f, inst, pitch);
  return { chart: chartFromSummary(summary, seed, progress), summary };
}
