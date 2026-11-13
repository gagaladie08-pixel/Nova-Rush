/// <reference lib="webworker" />
import { alignStems } from './align';
import { AnalysisError, analyzeSong, analyzeSongWithVoice } from './analyzer';

export interface AnalyzeRequest {
  samples: Float32Array;
  sampleRate: number;
  seed: number;
  /** Analyse avancée : pistes séparées (voix, accompagnement) à la même fréquence. */
  vocals?: Float32Array;
  accomp?: Float32Array;
}

self.onmessage = (e: MessageEvent<AnalyzeRequest>) => {
  const { samples, sampleRate, seed, vocals, accomp } = e.data;
  try {
    let last = 0;
    const progress = (p: number, label: string) => {
      const now = performance.now();
      if (now - last > 80 || p >= 1) {
        last = now;
        self.postMessage({ type: 'progress', p, label });
      }
    };
    if (vocals && accomp) {
      const a = alignStems(samples, vocals, accomp, sampleRate);
      const chart = analyzeSongWithVoice(samples, a.vocals, a.accomp, sampleRate, seed, progress);
      self.postMessage({ type: 'done', chart, lagSec: a.lagSec });
    } else {
      self.postMessage({ type: 'done', chart: analyzeSong(samples, sampleRate, seed, progress) });
    }
  } catch (err) {
    const message =
      err instanceof AnalysisError ? err.message : 'Une erreur inattendue est survenue pendant l’analyse.';
    self.postMessage({ type: 'error', message });
  }
};
