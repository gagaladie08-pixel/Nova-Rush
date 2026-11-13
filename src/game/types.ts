export type Level = 'facile' | 'moyen' | 'difficile' | 'expert';
export const LEVELS: Level[] = ['facile', 'moyen', 'difficile', 'expert'];
export const LEVEL_LABEL: Record<Level, string> = {
  facile: 'Facile',
  moyen: 'Moyen',
  difficile: 'Difficile',
  expert: 'Expert',
};

export type Lane = 0 | 1 | 2 | 3;

export interface Note {
  /** Instant (en secondes) où la tuile atteint la ligne de jugement. */
  t: number;
  lane: Lane;
  /** Durée en secondes pour une tuile longue (absente = tuile courte). */
  dur?: number;
  /** Durée en temps musicaux d'une tuile longue (pour le score). */
  beats?: number;
  /** Identifiant de groupe pour les accords (tuiles simultanées). */
  chord?: number;
}

export interface DifficultyStats {
  /** Gestes par seconde en moyenne sur la partie active. */
  npsAvg: number;
  /** Densité des passages exigeants (90e centile sur fenêtres de 4 s). */
  npsPeak: number;
  /** 10e centile de l'espacement entre gestes (s). */
  gapP10: number;
  chordRatio: number;
  longRatio: number;
  /** 0..1 : variété des changements de rythme. */
  rhythmVariety: number;
  /** Score continu utilisé pour le classement. */
  score: number;
}

export interface Chart {
  notes: Note[];
  bpm: number;
  duration: number;
  /** true si la mélodie a été détectée avec une confiance suffisante. */
  melodic: boolean;
  level: Level;
  stats?: DifficultyStats;
}

export type ThemeId = 'cyber' | 'survivor' | 'african';

export interface SongMeta {
  id: string;
  title: string;
  artist: string;
  cover?: string;
  level: Level;
  chart: Chart;
  duration: number;
  createdAt: number;
  hasAudio: boolean;
  fileName?: string;
  demo?: boolean;
  style?: string;
}

export interface SongRecord {
  id: string;
  bestScore: number;
  bestCombo: number;
  /** Progression maximale atteinte (0..1). */
  bestProgress: number;
  completed: boolean;
}
