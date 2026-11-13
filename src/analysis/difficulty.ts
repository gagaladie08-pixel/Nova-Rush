import { percentile } from './dsp';
import type { DifficultyStats, Level, Note } from '../game/types';

/**
 * Seuils PROVISOIRES séparant les niveaux. Le document de conception prévoit
 * de les calibrer lors des essais : ils sont regroupés ici pour être ajustés.
 */
export const DIFFICULTY_THRESHOLDS = {
  moyen: 2.6,
  difficile: 4.0,
  expert: 5.6,
};

interface Gesture {
  t: number;
  size: number;
  long: boolean;
}

function toGestures(notes: Note[]): Gesture[] {
  const out: Gesture[] = [];
  const byChord = new Map<number, Gesture>();
  for (const n of notes) {
    if (n.chord !== undefined) {
      const g = byChord.get(n.chord);
      if (g) {
        g.size++;
        g.long ||= !!n.dur;
        continue;
      }
      const ng = { t: n.t, size: 1, long: !!n.dur };
      byChord.set(n.chord, ng);
      out.push(ng);
    } else {
      out.push({ t: n.t, size: 1, long: !!n.dur });
    }
  }
  return out.sort((a, b) => a.t - b.t);
}

/**
 * Mesure le parcours réellement joué : densité des gestes, espacement,
 * accords, changements de rythme et tuiles longues. Le tempo seul ne suffit pas.
 */
export function computeStats(notes: Note[]): DifficultyStats {
  const g = toGestures(notes);
  if (g.length < 2) {
    return { npsAvg: 0, npsPeak: 0, gapP10: 1, chordRatio: 0, longRatio: 0, rhythmVariety: 0, score: 0 };
  }
  const first = g[0].t;
  const last = g[g.length - 1].t;
  const active = Math.max(4, last - first + 1);
  const npsAvg = g.length / active;

  // Densité sur fenêtres glissantes de 4 s : le 90e centile ne retient un
  // passage exigeant que s'il est assez long ou revient souvent.
  const win = 4;
  const counts: number[] = [];
  let j = 0;
  let k = 0;
  for (let start = first; start <= Math.max(first, last - win + 0.001); start += 1) {
    while (j < g.length && g[j].t < start) j++;
    while (k < g.length && g[k].t < start + win) k++;
    counts.push((k - j) / win);
  }
  const npsPeak = percentile(counts, 0.9);

  const iois: number[] = [];
  for (let i = 1; i < g.length; i++) iois.push(g[i].t - g[i - 1].t);
  const gapP10 = percentile(iois, 0.1);

  let changes = 0;
  for (let i = 1; i < iois.length; i++) {
    const r = iois[i] / Math.max(1e-3, iois[i - 1]);
    if (r > 1.3 || r < 1 / 1.3) changes++;
  }
  const rhythmVariety = iois.length > 1 ? changes / (iois.length - 1) : 0;
  const chordRatio = g.filter((x) => x.size > 1).length / g.length;
  const longRatio = g.filter((x) => x.long).length / g.length;

  const tightness = gapP10 < 0.16 ? (0.16 - gapP10) * 12 : 0;
  const score =
    0.55 * npsAvg + 0.45 * npsPeak + tightness + 3 * chordRatio + 1.5 * longRatio + 1.2 * rhythmVariety;

  return { npsAvg, npsPeak, gapP10, chordRatio, longRatio, rhythmVariety, score };
}

export function levelFromScore(score: number): Level {
  if (score >= DIFFICULTY_THRESHOLDS.expert) return 'expert';
  if (score >= DIFFICULTY_THRESHOLDS.difficile) return 'difficile';
  if (score >= DIFFICULTY_THRESHOLDS.moyen) return 'moyen';
  return 'facile';
}

/** Paramètres de génération et de défilement associés à chaque niveau. */
export const LEVEL_PARAMS: Record<
  Level,
  { minGap: number; laneGap: number; travel: number; chordRate: number; maxChord: number; minLongBeats: number }
> = {
  facile: { minGap: 0.3, laneGap: 0.42, travel: 1.9, chordRate: 0, maxChord: 1, minLongBeats: 1.5 },
  moyen: { minGap: 0.2, laneGap: 0.3, travel: 1.55, chordRate: 0.05, maxChord: 2, minLongBeats: 1 },
  difficile: { minGap: 0.14, laneGap: 0.22, travel: 1.25, chordRate: 0.08, maxChord: 2, minLongBeats: 1 },
  expert: { minGap: 0.095, laneGap: 0.16, travel: 1.0, chordRate: 0.12, maxChord: 3, minLongBeats: 1 },
};
