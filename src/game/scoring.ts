import type { Note } from './types';

/**
 * Règles de jugement et de score de Nova Rush (document de conception §4).
 * Les tolérances sont des valeurs de départ à vérifier lors des essais.
 */
export type Judge = 'perfectPlus' | 'perfect' | 'great';

export const WINDOWS = {
  perfectPlus: 0.025,
  perfect: 0.07,
  /** Au-delà, la tuile est considérée comme disparue sans toucher valide. */
  great: 0.15,
};

export const BASE_POINTS: Record<Judge, number> = {
  perfectPlus: 150,
  perfect: 100,
  great: 50,
};

export const JUDGE_LABEL: Record<Judge, string> = {
  perfectPlus: 'Perfect+',
  perfect: 'Perfect',
  great: 'Great',
};

const RANK: Record<Judge, number> = { great: 1, perfect: 2, perfectPlus: 3 };
const FROM_RANK: Judge[] = ['great', 'great', 'perfect', 'perfectPlus'];

/** Renvoie le jugement pour un écart (s) ou null si hors fenêtre. */
export function judgeOffset(dt: number): Judge | null {
  const a = Math.abs(dt);
  if (a <= WINDOWS.perfectPlus) return 'perfectPlus';
  if (a <= WINDOWS.perfect) return 'perfect';
  if (a <= WINDOWS.great) return 'great';
  return null;
}

/** Rang final d'une tuile longue : moyenne du toucher et du relâchement, arrondie vers le bas. */
export function combineLong(head: Judge, release: Judge): Judge {
  return FROM_RANK[Math.floor((RANK[head] + RANK[release]) / 2)];
}

export interface ScoreEvent {
  judge: Judge;
  points: number;
  combo: number;
  multiplier: number;
  earlyRelease?: boolean;
}

interface ChordState {
  remaining: number;
  reset: boolean;
}

export class ScoreKeeper {
  score = 0;
  combo = 0;
  maxCombo = 0;
  counts: Record<Judge, number> = { perfectPlus: 0, perfect: 0, great: 0 };
  private chords = new Map<number, ChordState>();

  constructor(notes: Note[] = []) {
    for (const n of notes) {
      if (n.chord === undefined) continue;
      const st = this.chords.get(n.chord) ?? { remaining: 0, reset: false };
      st.remaining++;
      this.chords.set(n.chord, st);
    }
  }

  /** Multiplicateur que recevrait le prochain Perfect+. */
  get nextMultiplier(): number {
    return this.combo + 1;
  }

  /** Tuile courte (ou tuile d'accord) jugée. */
  applyShort(note: Note, judge: Judge): ScoreEvent {
    let points: number;
    let multiplier = 1;
    if (judge === 'perfectPlus') {
      this.combo++;
      multiplier = this.combo;
      points = BASE_POINTS.perfectPlus * multiplier;
      this.maxCombo = Math.max(this.maxCombo, this.combo);
    } else {
      points = BASE_POINTS[judge];
      this.breakCombo(note);
    }
    this.counts[judge]++;
    this.score += points;
    this.finishChordMember(note);
    return { judge, points, combo: this.combo, multiplier };
  }

  /** Tuile longue tenue correctement jusqu'au relâchement. */
  applyLong(note: Note, head: Judge, release: Judge): ScoreEvent {
    const judge = combineLong(head, release);
    const beats = note.beats ?? 1;
    let multiplier = 1;
    if (judge === 'perfectPlus') {
      this.combo++; // le combo n'augmente que d'un rang pour toute la tuile
      multiplier = this.combo;
      this.maxCombo = Math.max(this.maxCombo, this.combo);
    } else {
      this.breakCombo(note);
    }
    const points = Math.round(BASE_POINTS[judge] * beats * multiplier);
    this.counts[judge]++;
    this.score += points;
    this.finishChordMember(note);
    return { judge, points, combo: this.combo, multiplier };
  }

  /** Tuile longue relâchée trop tôt : compte comme une tuile courte Great. */
  applyEarlyRelease(note: Note): ScoreEvent {
    const points = BASE_POINTS.great;
    this.breakCombo(note);
    this.counts.great++;
    this.score += points;
    this.finishChordMember(note);
    return { judge: 'great', points, combo: this.combo, multiplier: 1, earlyRelease: true };
  }

  private breakCombo(note: Note) {
    const st = note.chord !== undefined ? this.chords.get(note.chord) : undefined;
    if (st && st.remaining > 1) {
      // Dans un accord, la remise à zéro attend la fin du groupe pour ne pas
      // dépendre de l'ordre technique des doigts.
      st.reset = true;
    } else {
      this.combo = 0;
    }
  }

  private finishChordMember(note: Note) {
    if (note.chord === undefined) return;
    const st = this.chords.get(note.chord);
    if (!st) return;
    st.remaining--;
    if (st.remaining <= 0) {
      if (st.reset) this.combo = 0;
      this.chords.delete(note.chord);
    }
  }
}
