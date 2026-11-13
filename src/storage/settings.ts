import type { ThemeId } from '../game/types';

export interface Settings {
  menuTheme: ThemeId;
  /** Thème choisi pour la prochaine partie seulement (null = identique aux menus). */
  nextGameTheme: ThemeId | null;
  reduceMotion: boolean;
  /** Décalage audio/tactile en millisecondes (positif = le son arrive plus tard). */
  audioOffsetMs: number;
  /** Corrige doucement le décalage selon la façon de taper du joueur. */
  autoCalibrate: boolean;
}

const KEY = 'nova-rush.settings';
const DEFAULTS: Settings = { menuTheme: 'cyber', nextGameTheme: null, reduceMotion: false, audioOffsetMs: 0, autoCalibrate: true };

let current: Settings = load();
const listeners = new Set<(s: Settings) => void>();

function load(): Settings {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...DEFAULTS, ...JSON.parse(raw) };
  } catch {
    /* valeurs par défaut */
  }
  return { ...DEFAULTS };
}

export const settings = {
  get: (): Settings => current,
  update(patch: Partial<Settings>) {
    current = { ...current, ...patch };
    try {
      localStorage.setItem(KEY, JSON.stringify(current));
    } catch {
      /* stockage indisponible */
    }
    listeners.forEach((l) => l(current));
  },
  subscribe(fn: (s: Settings) => void) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  /** Thème de la partie qui démarre ; le choix ponctuel est ensuite consommé. */
  consumeGameTheme(): ThemeId {
    const t = current.nextGameTheme ?? current.menuTheme;
    if (current.nextGameTheme) this.update({ nextGameTheme: null });
    return t;
  },
};
