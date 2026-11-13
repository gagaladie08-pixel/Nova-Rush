import { median } from '../analysis/dsp';

/**
 * Calibration automatique : si le joueur touche régulièrement un peu en avance
 * ou en retard, le décalage audio est corrigé doucement (par petites étapes,
 * calculées sur plusieurs touches précises et bornées).
 */
export const AUTO_CAL = {
  /** Seules les touches proches de la ligne servent à la mesure (s). */
  window: 0.1,
  /** Nombre de touches par étape de correction. */
  batch: 8,
  /** Part de l'écart médian corrigée à chaque étape. */
  gain: 0.35,
  /** Correction maximale par étape (ms). */
  maxStepMs: 6,
  minMs: -200,
  maxMs: 300,
};

export class AutoCalibrator {
  private samples: number[] = [];
  constructor(public offsetMs: number) {}

  /**
   * Ajoute l'écart d'une touche (temps du toucher − instant de la tuile, en s).
   * Renvoie le nouveau décalage (ms) quand une correction est appliquée.
   */
  add(dt: number): number | null {
    if (Math.abs(dt) > AUTO_CAL.window) return null;
    this.samples.push(dt);
    if (this.samples.length < AUTO_CAL.batch) return null;
    const m = median(this.samples) * 1000;
    this.samples = [];
    const step = Math.max(-AUTO_CAL.maxStepMs, Math.min(AUTO_CAL.maxStepMs, m * AUTO_CAL.gain));
    if (Math.abs(step) < 0.5) return null;
    this.offsetMs = Math.max(AUTO_CAL.minMs, Math.min(AUTO_CAL.maxMs, this.offsetMs + step));
    return this.offsetMs;
  }
}
