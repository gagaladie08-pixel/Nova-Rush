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
  /** Ce que suivent les tuiles : la voix (séparation), la mélodie ou le rythme. */
  source?: 'voice' | 'melody' | 'rhythm';
  /** Part du morceau chantée (analyse avancée). */
  voiceRatio?: number;
  /** Temps et premiers temps de mesure (s) ; « ai » si donnés par Beat This!. */
  beats?: number[];
  downbeats?: number[];
  rhythmSource?: 'ai' | 'classic';
  /** Hauteur de la voix : IA CREPE ou suivi classique (YIN). */
  pitchSource?: 'ai' | 'classic';
  /** Structure du morceau (intro, couplets, refrains…). */
  sections?: Section[];
  structureConfidence?: number;
  /** Chef d'orchestre : confiance de chaque IA et source suivie par section. */
  conductor?: ConductorReport;
  /** Coups de grosse caisse et de cymbale (effets visuels). */
  kicks?: number[];
  crashes?: number[];
}

/** Source qui mène une section : voix, instrument mélodique, basse + batterie, batterie, accompagnement. */
export type Lead = 'voice' | 'instrument' | 'groove' | 'drums' | 'accomp';

export interface ConductorReport {
  conf: { voice: number; drums: number; instrument: number; bass: number };
  leads: { t: number; end: number; lead: Lead }[];
}

export type SectionKind = 'intro' | 'couplet' | 'refrain' | 'pont' | 'solo' | 'outro';

export interface Section {
  t: number;
  end: number;
  kind: SectionKind;
  /** Passages semblables = même groupe ; rep = numéro de la répétition (0 = première). */
  group: number;
  rep: number;
  /** Ressemblance avec la première occurrence du groupe (0..1). */
  sim: number;
  /** Énergie relative 0..1. */
  energy: number;
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
  /** Analyse avancée (voix isolée sur l'appareil). */
  voice?: VoiceAnalysis;
}

export interface VoiceAnalysis {
  state: 'queued' | 'running' | 'done' | 'failed';
  /** Raison de l'échec (le parcours classique est conservé). */
  reason?: string;
  /** Durée de la séparation sur l'appareil (s). */
  seconds?: number;
  /** Décalage mesuré entre décodeurs (s). */
  lagSec?: number;
}

export interface SongRecord {
  id: string;
  bestScore: number;
  bestCombo: number;
  /** Progression maximale atteinte (0..1). */
  bestProgress: number;
  completed: boolean;
}
