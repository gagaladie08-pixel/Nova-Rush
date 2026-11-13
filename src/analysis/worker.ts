/// <reference lib="webworker" />
import { AnalysisError, analyzeSong } from './analyzer';

export interface AnalyzeRequest {
  samples: Float32Array;
  sampleRate: number;
  seed: number;
}

self.onmessage = (e: MessageEvent<AnalyzeRequest>) => {
  const { samples, sampleRate, seed } = e.data;
  try {
    let last = 0;
    const chart = analyzeSong(samples, sampleRate, seed, (p, label) => {
      const now = performance.now();
      if (now - last > 80 || p >= 1) {
        last = now;
        self.postMessage({ type: 'progress', p, label });
      }
    });
    self.postMessage({ type: 'done', chart });
  } catch (err) {
    const message =
      err instanceof AnalysisError ? err.message : 'Une erreur inattendue est survenue pendant l’analyse.';
    self.postMessage({ type: 'error', message });
  }
};
