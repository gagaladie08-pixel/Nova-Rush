import { LEVEL_PARAMS, computeStats } from '../analysis/difficulty';
import type { Chart, Lane, Level, Note, Section as ChartSection, SectionKind, SongMeta } from '../game/types';

/**
 * Morceaux de démonstration originaux, composés pour Nova Rush et synthétisés
 * sur l'appareil (aucun droit tiers). Les parcours sont écrits à la main à
 * partir de la mélodie ; le niveau de chaque démo est fixé manuellement.
 */

/** [temps dans la mesure, note MIDI, durée en temps, accord ?] */
export type MelodyNote = [number, number, number, boolean?];
export type Bar = MelodyNote[];

export type DrumStyle = 'synthwave' | 'afro' | 'ethio' | 'vogue' | 'dnb';

export interface Section {
  bars: Bar[];
  /** Accords (notes MIDI) par mesure pour la nappe et la basse. */
  chords: number[][];
  drums: 'none' | 'light' | 'full' | 'build';
  /** Répéter la section n fois. */
  repeat?: number;
}

export interface DemoDef {
  id: string;
  title: string;
  artist: string;
  style: string;
  drumStyle: DrumStyle;
  level: Level;
  bpm: number;
  lead: 'saw' | 'pluck' | 'vibes' | 'stab' | 'square';
  sections: Section[];
  palette: [string, string, string];
}

const R = (n: number, bars: Bar[]) => Array.from({ length: n }, () => bars).flat();

// Accords utiles
const Am = [57, 60, 64], F = [53, 57, 60], C = [48, 52, 55], G = [55, 59, 62];
const Gmaj = [55, 59, 62], Em = [52, 55, 59], Cmaj = [48, 52, 55], D = [50, 54, 57];
const Eamb = [52, 59, 64], Famb = [53, 57, 60], Aamb = [57, 60, 64];
const Cm = [48, 51, 55], Ab = [56, 60, 63], Bb = [58, 62, 65], Gm = [55, 59, 62];
const EmD = [52, 55, 59], CD = [48, 52, 55], GD = [55, 59, 62], DD = [50, 54, 57];

export const DEMO_DEFS: DemoDef[] = [
  {
    id: 'demo-aube-neon',
    title: 'Aube Néon',
    artist: 'Nova Rush Démo',
    style: 'Synthwave',
    drumStyle: 'synthwave',
    level: 'facile',
    bpm: 96,
    lead: 'saw',
    palette: ['#FF006E', '#00B4FF', '#120030'],
    sections: [
      { bars: [[], []], chords: [Am, F], drums: 'light' },
      {
        bars: [
          [[0, 76, 1], [1, 74, 1], [2, 72, 2]],
          [[0, 72, 1], [1, 74, 1], [2, 69, 2]],
          [[0, 67, 1], [1, 69, 1], [2, 72, 1], [3, 74, 1]],
          [[0, 71, 2], [2, 74, 2]],
          [[0, 69, 1], [1, 72, 1], [2, 76, 1], [3, 74, 1]],
          [[0, 72, 2], [2, 77, 2]],
          [[0, 76, 1], [1, 74, 1], [2, 72, 1], [3, 67, 1]],
          [[0, 71, 1], [1, 74, 1], [2, 79, 2]],
        ],
        chords: [Am, F, C, G, Am, F, C, G],
        drums: 'full',
        repeat: 2,
      },
      { bars: [[[0, 69, 4]], []], chords: [Am, Am], drums: 'light' },
    ],
  },
  {
    id: 'demo-lumiere-marche',
    title: 'Lumière du Marché',
    artist: 'Nova Rush Démo',
    style: 'Afropop',
    drumStyle: 'afro',
    level: 'moyen',
    bpm: 104,
    lead: 'pluck',
    palette: ['#FFB000', '#26CEAA', '#2A0F00'],
    sections: [
      { bars: [[], []], chords: [Gmaj, Em], drums: 'light' },
      {
        bars: [
          [[0, 74, 0.5], [0.5, 71, 0.5], [1, 67, 0.5], [1.5, 71, 0.5], [2.5, 74, 0.5], [3, 76, 1]],
          [[0, 79, 0.5], [0.5, 76, 0.5], [1, 71, 0.5], [2, 74, 0.5], [2.5, 76, 0.5], [3, 74, 1]],
          [[0, 72, 0.5], [0.5, 76, 0.5], [1, 79, 0.5], [1.5, 76, 0.5], [2.5, 72, 0.5], [3, 74, 0.5], [3.5, 76, 0.5]],
          [[0, 78, 1.5], [2, 74, 0.5], [2.5, 72, 0.5], [3, 71, 1]],
        ],
        chords: [Gmaj, Em, Cmaj, D],
        drums: 'full',
        repeat: 2,
      },
      {
        bars: [
          [[0, 79, 0.5, true], [1, 79, 0.5], [1.5, 76, 0.5], [2, 74, 1], [3, 71, 0.5], [3.5, 74, 0.5]],
          [[0, 76, 1.5], [2, 74, 0.5], [2.5, 71, 0.5], [3, 67, 1]],
          [[0, 72, 0.5, true], [0.5, 74, 0.5], [1, 76, 0.5], [2, 79, 0.5], [2.5, 81, 0.5], [3, 79, 1]],
          [[0, 78, 2], [2, 81, 2]],
        ],
        chords: [Gmaj, Em, Cmaj, D],
        drums: 'full',
      },
      {
        bars: [
          [[0, 74, 0.5], [0.5, 71, 0.5], [1, 67, 0.5], [1.5, 71, 0.5], [2.5, 74, 0.5], [3, 76, 1]],
          [[0, 79, 0.5], [0.5, 76, 0.5], [1, 71, 0.5], [2, 74, 0.5], [2.5, 76, 0.5], [3, 74, 1]],
          [[0, 72, 0.5], [0.5, 76, 0.5], [1, 79, 0.5], [1.5, 76, 0.5], [2.5, 72, 0.5], [3, 74, 0.5], [3.5, 76, 0.5]],
          [[0, 78, 1.5], [2, 74, 0.5], [2.5, 72, 0.5], [3, 71, 1]],
          [[0, 79, 0.5, true], [1, 79, 0.5], [1.5, 76, 0.5], [2, 74, 1], [3, 71, 0.5], [3.5, 74, 0.5]],
          [[0, 76, 1.5], [2, 74, 0.5], [2.5, 71, 0.5], [3, 67, 1]],
          [[0, 72, 0.5, true], [0.5, 74, 0.5], [1, 76, 0.5], [2, 79, 0.5], [2.5, 81, 0.5], [3, 79, 1]],
          [[0, 79, 4]],
        ],
        chords: [Gmaj, Em, Cmaj, D, Gmaj, Em, Cmaj, Gmaj],
        drums: 'full',
      },
      { bars: [[], []], chords: [Gmaj, Gmaj], drums: 'light' },
    ],
  },
  {
    id: 'demo-reine-saba',
    title: 'Reine de Saba',
    artist: 'Nova Rush Démo',
    style: 'Éthio-jazz',
    drumStyle: 'ethio',
    level: 'moyen',
    bpm: 112,
    lead: 'vibes',
    palette: ['#D4A017', '#C4622D', '#120800'],
    sections: [
      { bars: [[], []], chords: [Eamb, Eamb], drums: 'light' },
      {
        bars: [
          [[0, 76, 1], [1, 77, 0.5], [1.5, 76, 0.5], [2, 72, 1], [3, 71, 0.5], [3.5, 72, 0.5]],
          [[0, 71, 0.5], [0.5, 69, 0.5], [1, 65, 1], [2, 64, 2]],
          [[0, 69, 0.5], [0.5, 71, 0.5], [1, 72, 0.5], [1.5, 76, 0.5], [2, 77, 1], [3, 76, 1]],
          [[0, 72, 0.5], [0.5, 71, 0.5], [1, 69, 0.5], [1.5, 65, 0.5], [2, 64, 2]],
        ],
        chords: [Eamb, Famb, Aamb, Eamb],
        drums: 'full',
        repeat: 2,
      },
      {
        bars: [
          [[0, 81, 1.5, true], [1.5, 77, 0.5], [2, 76, 1], [3, 77, 1]],
          [[0, 76, 0.5], [0.5, 72, 0.5], [1, 71, 1], [2, 72, 0.5], [2.5, 71, 0.5], [3, 69, 1]],
          [[0, 65, 0.5], [0.5, 69, 0.5], [1, 71, 0.5], [1.5, 72, 0.5], [2, 76, 0.5], [2.5, 77, 0.5], [3, 81, 1]],
          [[0, 76, 3]],
        ],
        chords: [Aamb, Famb, Famb, Eamb],
        drums: 'full',
        repeat: 2,
      },
      {
        bars: [
          [[0, 76, 1, true], [1, 77, 0.5], [1.5, 76, 0.5], [2, 72, 1], [3, 71, 0.5], [3.5, 72, 0.5]],
          [[0, 71, 0.5], [0.5, 69, 0.5], [1, 65, 1], [2, 64, 2]],
          [[0, 69, 0.5], [0.5, 71, 0.5], [1, 72, 0.5], [1.5, 76, 0.5], [2, 77, 1], [3, 76, 1]],
          [[0, 72, 0.5], [0.5, 71, 0.5], [1, 69, 0.5], [1.5, 65, 0.5], [2, 64, 4]],
        ],
        chords: [Eamb, Famb, Aamb, Eamb],
        drums: 'full',
      },
      { bars: [[]], chords: [Eamb], drums: 'light' },
    ],
  },
  {
    id: 'demo-prisme',
    title: 'Prisme',
    artist: 'Nova Rush Démo',
    style: 'Vogue house',
    drumStyle: 'vogue',
    level: 'difficile',
    bpm: 124,
    lead: 'stab',
    palette: ['#F77FB4', '#5BCEFA', '#100818'],
    sections: [
      { bars: [[], []], chords: [Cm, Cm], drums: 'build' },
      {
        bars: [
          [[0, 72, 0.5, true], [0.75, 75, 0.5], [1.5, 79, 0.5], [2, 77, 0.5], [2.5, 75, 0.5], [3, 72, 0.5], [3.5, 75, 0.5]],
          [[0, 80, 0.5, true], [0.75, 79, 0.5], [1.5, 75, 0.5], [2, 77, 1], [3, 75, 0.5], [3.5, 77, 0.5]],
          [[0, 82, 0.5, true], [0.5, 79, 0.5], [1, 77, 0.5], [1.5, 79, 0.5], [2, 82, 0.5], [2.5, 84, 0.5], [3, 82, 0.5], [3.5, 79, 0.5]],
          [[0, 79, 2], [2.5, 74, 0.5], [3, 77, 0.5], [3.5, 74, 0.5]],
        ],
        chords: [Cm, Ab, Bb, Gm],
        drums: 'full',
        repeat: 2,
      },
      {
        bars: [
          [[0, 84, 0.5, true], [0.5, 84, 0.25], [0.75, 82, 0.25], [1, 79, 0.5], [1.5, 82, 0.5], [2, 84, 0.5, true], [3, 82, 0.5], [3.5, 79, 0.5]],
          [[0, 80, 0.5, true], [0.5, 79, 0.5], [1, 77, 0.5], [1.5, 75, 0.5], [2, 77, 0.5], [2.5, 79, 0.5], [3, 80, 1]],
          [[0, 82, 0.5, true], [0.5, 82, 0.25], [0.75, 80, 0.25], [1, 79, 0.5], [1.5, 77, 0.5], [2, 79, 0.5, true], [3, 77, 0.5], [3.5, 75, 0.5]],
          [[0, 74, 0.5], [0.5, 77, 0.5], [1, 79, 0.5], [1.5, 83, 0.5], [2, 86, 2]],
        ],
        chords: [Cm, Ab, Bb, Gm],
        drums: 'full',
        repeat: 2,
      },
      {
        bars: [
          [[0, 72, 0.5, true], [0.75, 75, 0.5], [1.5, 79, 0.5], [2, 77, 0.5], [2.5, 75, 0.5], [3, 72, 0.5], [3.5, 75, 0.5]],
          [[0, 80, 0.5, true], [0.75, 79, 0.5], [1.5, 75, 0.5], [2, 77, 1], [3, 75, 0.5], [3.5, 77, 0.5]],
          [[0, 82, 0.5, true], [0.5, 79, 0.5], [1, 77, 0.5], [1.5, 79, 0.5], [2, 82, 0.5], [2.5, 84, 0.5], [3, 82, 0.5], [3.5, 79, 0.5]],
          [[0, 79, 2], [2.5, 74, 0.5], [3, 77, 0.5], [3.5, 74, 0.5]],
          [[0, 84, 0.5, true], [0.5, 84, 0.25], [0.75, 82, 0.25], [1, 79, 0.5], [1.5, 82, 0.5], [2, 84, 0.5, true], [3, 82, 0.5], [3.5, 79, 0.5]],
          [[0, 80, 0.5, true], [0.5, 79, 0.5], [1, 77, 0.5], [1.5, 75, 0.5], [2, 77, 0.5], [2.5, 79, 0.5], [3, 80, 1]],
          [[0, 82, 0.5, true], [0.5, 82, 0.25], [0.75, 80, 0.25], [1, 79, 0.5], [1.5, 77, 0.5], [2, 79, 0.5, true], [3, 77, 0.5], [3.5, 75, 0.5]],
          [[0, 72, 4, true]],
        ],
        chords: [Cm, Ab, Bb, Gm, Cm, Ab, Bb, Cm],
        drums: 'full',
      },
      { bars: [[]], chords: [Cm], drums: 'light' },
    ],
  },
  {
    id: 'demo-overdrive-nova',
    title: 'Overdrive Nova',
    artist: 'Nova Rush Démo',
    style: 'Drum & bass',
    drumStyle: 'dnb',
    level: 'expert',
    bpm: 172,
    lead: 'square',
    palette: ['#00FFF0', '#BD00FF', '#050510'],
    sections: [
      {
        bars: R(2, [[[0, 76, 2], [2, 79, 2]], [[0, 83, 2], [2, 79, 2]]]),
        chords: [EmD, EmD, CD, CD],
        drums: 'build',
      },
      {
        bars: [
          [[0, 76, 0.5, true], [0.5, 79, 0.5], [1, 83, 0.5], [1.5, 79, 0.5], [2, 76, 0.5], [2.5, 79, 0.5], [3, 83, 0.5], [3.5, 86, 0.5]],
          [[0, 88, 1], [1, 86, 0.5], [1.5, 83, 0.5], [2, 79, 0.5], [2.5, 83, 0.25], [2.75, 86, 0.25], [3, 83, 1]],
          [[0, 76, 0.5, true], [0.5, 79, 0.5], [1, 84, 0.5], [1.5, 79, 0.5], [2, 76, 0.5], [2.5, 79, 0.5], [3, 84, 0.5], [3.5, 88, 0.5]],
          [[0, 86, 2], [2, 84, 0.5], [2.5, 83, 0.5], [3, 79, 1]],
          [[0, 74, 0.5, true], [0.5, 79, 0.5], [1, 83, 0.5], [1.5, 86, 0.5], [2, 83, 0.5], [2.5, 79, 0.5], [3, 83, 0.25], [3.25, 86, 0.25], [3.5, 91, 0.5]],
          [[0, 88, 0.5], [0.5, 86, 0.5], [1, 83, 0.5], [1.5, 79, 0.5], [2, 81, 0.5], [2.5, 83, 0.5], [3, 86, 1]],
          [[0, 78, 0.5, true], [0.5, 81, 0.5], [1, 86, 0.5], [1.5, 81, 0.5], [2, 78, 0.5], [2.5, 81, 0.5], [3, 86, 0.5], [3.5, 90, 0.5]],
          [[0, 88, 1], [1, 86, 0.5], [1.5, 83, 0.5], [2, 86, 2]],
        ],
        chords: [EmD, EmD, CD, CD, GD, GD, DD, DD],
        drums: 'full',
        repeat: 2,
      },
      {
        bars: [[[0, 83, 4]], [[0, 79, 4]], [[0, 84, 4]], [[0, 86, 2], [2, 90, 2]]],
        chords: [EmD, EmD, CD, DD],
        drums: 'light',
      },
      {
        bars: [
          [[0, 76, 0.5, true], [0.5, 79, 0.5], [1, 83, 0.5], [1.5, 79, 0.5], [2, 76, 0.5], [2.5, 79, 0.5], [3, 83, 0.5], [3.5, 86, 0.5]],
          [[0, 88, 1, true], [1, 86, 0.5], [1.5, 83, 0.5], [2, 79, 0.5], [2.5, 83, 0.25], [2.75, 86, 0.25], [3, 83, 1]],
          [[0, 76, 0.5, true], [0.5, 79, 0.5], [1, 84, 0.5], [1.5, 79, 0.5], [2, 76, 0.5], [2.5, 79, 0.5], [3, 84, 0.5], [3.5, 88, 0.5]],
          [[0, 86, 2, true], [2, 84, 0.5], [2.5, 83, 0.5], [3, 79, 1]],
          [[0, 74, 0.5, true], [0.5, 79, 0.5], [1, 83, 0.5], [1.5, 86, 0.5], [2, 83, 0.5], [2.5, 79, 0.5], [3, 83, 0.25], [3.25, 86, 0.25], [3.5, 91, 0.5]],
          [[0, 88, 0.5, true], [0.5, 86, 0.5], [1, 83, 0.5], [1.5, 79, 0.5], [2, 81, 0.5], [2.5, 83, 0.5], [3, 86, 1]],
          [[0, 78, 0.5, true], [0.5, 81, 0.5], [1, 86, 0.5], [1.5, 81, 0.5], [2, 78, 0.5], [2.5, 81, 0.5], [3, 86, 0.5], [3.5, 90, 0.5]],
          [[0, 88, 1, true], [1, 86, 0.5], [1.5, 83, 0.5], [2, 88, 2]],
        ],
        chords: [EmD, EmD, CD, CD, GD, GD, DD, EmD],
        drums: 'full',
        repeat: 2,
      },
      { bars: [[[0, 76, 4, true]], []], chords: [EmD, EmD], drums: 'light' },
    ],
  },
];

/** Déroule les sections en une liste de mesures. */
export function flattenDemo(def: DemoDef) {
  const bars: { notes: Bar; chord: number[]; drums: Section['drums'] }[] = [];
  for (const s of def.sections) {
    for (let r = 0; r < (s.repeat ?? 1); r++) {
      s.bars.forEach((b, i) => bars.push({ notes: b, chord: s.chords[i % s.chords.length], drums: s.drums }));
    }
  }
  return bars;
}

/** Temps de silence initial avant la première mesure. */
export const DEMO_LEAD_IN = 0.3;

export function demoDuration(def: DemoDef): number {
  return DEMO_LEAD_IN + flattenDemo(def).length * 4 * (60 / def.bpm) + 2;
}

const PAIRS: Lane[][] = [[0, 2], [1, 3], [0, 3], [1, 2]];
const TRIPLES: Lane[][] = [[0, 1, 3], [0, 2, 3]];

/** Parcours écrit à partir de la mélodie de la démo. */
export function demoChart(def: DemoDef): Chart {
  const beat = 60 / def.bpm;
  const params = LEVEL_PARAMS[def.level];
  const flat = flattenDemo(def);
  const all: { t: number; midi: number; len: number; chord: boolean; bar: number }[] = [];
  flat.forEach((b, bi) => {
    for (const [pos, midi, len, chord] of b.notes) {
      all.push({ t: DEMO_LEAD_IN + (bi * 4 + pos) * beat, midi, len, chord: !!chord, bar: bi });
    }
  });
  const notes: Note[] = [];
  const lastUse = [-9, -9, -9, -9];
  let chordId = 0;
  let prev: { lane: Lane; midi: number } | null = null;
  let chordCount = 0;
  for (const n of all) {
    const win = all.filter((m) => Math.abs(m.bar - n.bar) <= 1).map((m) => m.midi);
    const uniq = [...new Set(win)].sort((a, b) => a - b);
    const idx = uniq.indexOf(n.midi);
    let desired = (uniq.length > 1 ? Math.round((idx / (uniq.length - 1)) * 3) : 1) as Lane;
    if (prev && n.midi === prev.midi) desired = prev.lane;
    else if (prev && desired === prev.lane) {
      desired = (n.midi > prev.midi ? Math.min(3, desired + 1) : Math.max(0, desired - 1)) as Lane;
      if (desired === prev.lane) desired = (prev.lane === 0 ? 1 : prev.lane - 1) as Lane;
    }
    const isLong = n.len >= Math.max(params.minLongBeats, 1) && n.len * beat >= 0.4;
    const free = (l: number) => n.t - lastUse[l] >= Math.min(params.laneGap, 0.2);
    if (n.chord && params.maxChord >= 2 && !isLong) {
      const size = params.maxChord >= 3 && chordCount % 3 === 2 ? 3 : 2;
      const sets = size === 3 ? TRIPLES : PAIRS;
      const set = sets.find((s) => s.includes(desired) && s.every(free)) ?? sets.find((s) => s.every(free));
      chordCount++;
      if (set) {
        const id = chordId++;
        for (const l of set) {
          notes.push({ t: n.t, lane: l, chord: id });
          lastUse[l] = n.t;
        }
        prev = { lane: desired, midi: n.midi };
        continue;
      }
    }
    let lane = desired;
    if (!free(lane)) {
      const alt = ([0, 1, 2, 3] as Lane[]).filter(free).sort((a, b) => Math.abs(a - desired) - Math.abs(b - desired))[0];
      if (alt === undefined) continue;
      lane = alt;
    }
    const note: Note = { t: n.t, lane };
    if (isLong) {
      note.beats = n.len;
      note.dur = n.len * beat;
    }
    lastUse[lane] = n.t + (note.dur ?? 0);
    notes.push(note);
    prev = { lane, midi: n.midi };
  }
  notes.sort((a, b) => a.t - b.t || a.lane - b.lane);
  return {
    notes,
    bpm: def.bpm,
    duration: demoDuration(def),
    melodic: true,
    level: def.level,
    stats: computeStats(notes),
    downbeats: flat.map((_, bi) => Math.round((DEMO_LEAD_IN + bi * 4 * beat) * 1000) / 1000),
    sections: demoSections(def),
    structureConfidence: 1,
  };
}

/** Structure connue de la démo : batterie complète = refrain, début et fin calmes = intro et outro. */
function demoSections(def: DemoDef): ChartSection[] {
  const beat = 60 / def.bpm;
  const out: ChartSection[] = [];
  const groups: Section[] = [];
  const reps = new Map<number, number>();
  let bar = 0;
  def.sections.forEach((s, i) => {
    const len = s.bars.length * (s.repeat ?? 1);
    let g = groups.findIndex((o) => o.bars === s.bars || (o.chords.join() === s.chords.join() && o.drums === s.drums));
    if (g < 0) g = groups.push(s) - 1;
    const calm = s.drums === 'none' || s.drums === 'light';
    const kind: SectionKind =
      s.drums === 'full' ? 'refrain' : i === 0 && calm ? 'intro' : i === def.sections.length - 1 && calm ? 'outro' : s.drums === 'build' ? 'pont' : 'couplet';
    const rep = reps.get(g) ?? 0;
    reps.set(g, rep + 1);
    out.push({
      t: i === 0 ? 0 : DEMO_LEAD_IN + bar * 4 * beat,
      end: DEMO_LEAD_IN + (bar + len) * 4 * beat,
      kind,
      group: g,
      rep,
      sim: 1,
      energy: s.drums === 'full' ? 0.85 : s.drums === 'build' ? 0.6 : s.drums === 'light' ? 0.4 : 0.2,
    });
    bar += len;
  });
  if (out.length) out[out.length - 1].end = demoDuration(def);
  return out;
}

let cached: SongMeta[] | null = null;
export function demoSongs(): SongMeta[] {
  cached ??= DEMO_DEFS.map((d) => {
    const chart = demoChart(d);
    return {
      id: d.id,
      title: d.title,
      artist: d.artist,
      level: d.level,
      chart,
      duration: chart.duration,
      createdAt: 0,
      hasAudio: true,
      demo: true,
      style: d.style,
    };
  });
  return cached;
}

export function demoDef(id: string): DemoDef | undefined {
  return DEMO_DEFS.find((d) => d.id === id);
}
