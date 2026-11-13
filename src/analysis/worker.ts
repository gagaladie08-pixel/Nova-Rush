/// <reference lib="webworker" />
import { alignStems } from './align';
import { AnalysisError, analyzeSong, type Rhythm } from './analyzer';
import type { ExternalPitch } from './beatsFile';
import { analyzeSongAdvanced, chartFromSummary, type Instruments, type MusicSummary } from './conductor';
import { drumHits } from './drums';
import { instrumentNotes, parsePosteriors, type InstrNote } from './instruments';

export interface AnalyzeRequest {
  samples: Float32Array;
  sampleRate: number;
  seed: number;
  /** Analyse avancée : pistes séparées (voix, accompagnement) à la même fréquence. */
  vocals?: Float32Array;
  accomp?: Float32Array;
  /** Temps de l'IA Beat This!, sur l'horloge des pistes séparées. */
  rhythm?: Rhythm;
  /** Hauteur de la voix par l'IA CREPE, sur l'horloge des pistes séparées. */
  pitch?: ExternalPitch;
  /** Batterie isolée (Demucs), même horloge et fréquence que les pistes séparées. */
  drums?: Float32Array;
  /** Sorties de Basic Pitch (basse, autres instruments). */
  bpBass?: Uint8Array;
  bpOther?: Uint8Array;
  /** Reconstruction : parcours à partir du résumé enregistré (sans audio). */
  summary?: MusicSummary;
}

/** Notes d'un fichier Basic Pitch, recalées sur le mix ; rien si illisible. */
function notesOf(bytes: Uint8Array | undefined, lag: number): InstrNote[] | undefined {
  if (!bytes) return undefined;
  try {
    const p = parsePosteriors(bytes);
    return p ? instrumentNotes(p).map((n) => ({ ...n, t: n.t - lag })) : undefined;
  } catch {
    return undefined;
  }
}

self.onmessage = (e: MessageEvent<AnalyzeRequest>) => {
  const { samples, sampleRate, seed, vocals, accomp, rhythm, pitch, drums, bpBass, bpOther, summary } = e.data;
  try {
    let last = 0;
    const progress = (p: number, label: string) => {
      const now = performance.now();
      if (now - last > 80 || p >= 1) {
        last = now;
        self.postMessage({ type: 'progress', p, label });
      }
    };
    if (summary) {
      self.postMessage({ type: 'done', chart: chartFromSummary(summary, seed, progress), summary });
    } else if (vocals && accomp) {
      const a = alignStems(samples, vocals, accomp, sampleRate);
      // stems[i + lag] ≈ mix[i] : un instant t des pistes vaut t − lag sur le mix.
      const r = rhythm && {
        beats: rhythm.beats.map((t) => t - a.lagSec),
        downbeats: rhythm.downbeats.map((t) => t - a.lagSec),
      };
      const p = pitch && { ...pitch, t0: pitch.t0 - a.lagSec };
      progress(0.01, 'Écoute des instruments…');
      const inst: Instruments = {};
      try {
        if (drums) inst.drums = drumHits(drums, sampleRate).map((h) => ({ ...h, t: h.t - a.lagSec }));
      } catch {
        /* batterie facultative */
      }
      inst.bass = notesOf(bpBass, a.lagSec);
      inst.other = notesOf(bpOther, a.lagSec);
      const { chart, summary } = analyzeSongAdvanced(samples, a.vocals, a.accomp, sampleRate, seed, progress, r, p, inst);
      self.postMessage({ type: 'done', chart, summary, lagSec: a.lagSec });
    } else {
      self.postMessage({ type: 'done', chart: analyzeSong(samples, sampleRate, seed, progress, rhythm) });
    }
  } catch (err) {
    const message =
      err instanceof AnalysisError ? err.message : 'Une erreur inattendue est survenue pendant l’analyse.';
    self.postMessage({ type: 'error', message });
  }
};
