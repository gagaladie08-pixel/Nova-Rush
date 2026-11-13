import type { ThemeId } from './types';

export interface GameTheme {
  id: ThemeId;
  name: string;
  tagline: string;
  bg: string;
  surface: string;
  text: string;
  accents: [string, string, string, string];
  laneColors: [string, string, string, string];
  line: string;
  /** Dégradé des voies (haut, bas). */
  laneGrad: [string, string];
  /** Dégradé des tuiles sombres (haut, bas). */
  tile: [string, string];
  /** Couleur de remplissage d'une tuile touchée / tenue. */
  fill: string;
  tileRadius: number;
  glow: number;
  fontTitle: string;
  judge: { perfectPlus: string; perfect: string; great: string; miss: string };
}

export const THEMES: Record<ThemeId, GameTheme> = {
  cyber: {
    id: 'cyber',
    name: 'Cyber Nova',
    tagline: 'Futuriste, électrique, néon.',
    bg: '#050510',
    surface: '#0D0D2B',
    text: '#F0F0FF',
    accents: ['#FF006E', '#00B4FF', '#BD00FF', '#00FFF0'],
    laneColors: ['#FF006E', '#BD00FF', '#00B4FF', '#00FFF0'],
    line: '#00FFF0',
    laneGrad: ['#3B0F7A', '#16308F'],
    tile: ['#14092A', '#05030C'],
    fill: '#00FFF0',
    tileRadius: 6,
    glow: 8,
    fontTitle: 'Orbitron',
    judge: { perfectPlus: '#00FFF0', perfect: '#FF006E', great: '#BD00FF', miss: '#FF3355' },
  },
  survivor: {
    id: 'survivor',
    name: 'Survivor',
    tagline: 'Fier, vivant, inclusif — Ballroom.',
    bg: '#080808',
    surface: '#121018',
    text: '#FFFFFF',
    accents: ['#F77FB4', '#5BCEFA', '#9B4F96', '#26CEAA'],
    laneColors: ['#5BCEFA', '#F77FB4', '#9B4F96', '#26CEAA'],
    line: '#FFFFFF',
    laneGrad: ['#7A2E6E', '#2A5F9E'],
    tile: ['#1A1020', '#070508'],
    fill: '#F77FB4',
    tileRadius: 16,
    glow: 10,
    fontTitle: 'Righteous',
    judge: { perfectPlus: '#FFD84D', perfect: '#F77FB4', great: '#5BCEFA', miss: '#FF5A5A' },
  },
  african: {
    id: 'african',
    name: 'African Confessions',
    tagline: 'Chaleureux, géométrique, royal.',
    bg: '#120800',
    surface: '#1E0E00',
    text: '#FFF3E0',
    accents: ['#C4622D', '#D4A017', '#2D5A27', '#FFF3E0'],
    laneColors: ['#C4622D', '#D4A017', '#3E7A35', '#E07B3C'],
    line: '#D4A017',
    laneGrad: ['#7A3512', '#2E1606'],
    tile: ['#1E0D02', '#0A0400'],
    fill: '#D4A017',
    tileRadius: 8,
    glow: 5,
    fontTitle: 'Fraunces',
    judge: { perfectPlus: '#FFD24A', perfect: '#E07B3C', great: '#7DBA6E', miss: '#FF5A3C' },
  },
};

export const THEME_IDS: ThemeId[] = ['cyber', 'survivor', 'african'];
