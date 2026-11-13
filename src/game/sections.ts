import type { Chart, Section } from './types';

/** Section contenant l'instant t (ou undefined). */
export function sectionAt(sections: Section[] | undefined, t: number): Section | undefined {
  if (!sections) return undefined;
  for (const s of sections) if (t >= s.t && t < s.end) return s;
  return undefined;
}

/** Début de l'aperçu : le premier refrain (le plus énergique s'il y en a plusieurs semblables). */
export function previewStart(chart: Chart | undefined): number | undefined {
  const secs = chart?.sections;
  if (!secs || (chart?.structureConfidence ?? 0) < 0.4) return undefined;
  const refrains = secs.filter((s) => s.kind === 'refrain' && s.end - s.t >= 6);
  if (!refrains.length) return undefined;
  const best = refrains.reduce((a, b) => (b.energy > a.energy + 0.08 ? b : a));
  return Math.max(0, best.t - 0.3);
}

/** Intensité visuelle cible d'une section (0..1). */
export function sectionIntensity(s: Section | undefined): number {
  if (!s) return 0.35;
  const base = { refrain: 1, couplet: 0.35, pont: 0.5, solo: 0.6, intro: 0.15, outro: 0.15 }[s.kind];
  return base * (0.65 + 0.35 * s.energy);
}
