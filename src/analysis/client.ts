import type { Chart } from '../game/types';
import { hashString } from './dsp';
import type { ExternalPitch } from './beatsFile';
import type { MusicSummary } from './conductor';

/** Sorties des IA sur les instruments (fichiers du service Android). */
export interface InstrumentFiles {
  drums?: Float32Array;
  bpBass?: Uint8Array;
  bpOther?: Uint8Array;
}

export class ImportError extends Error {}

export const ANALYSIS_RATE = 22050;

/** Décode l'audio en mono 22 050 Hz pour l'analyse. */
export async function decodeForAnalysis(data: ArrayBuffer): Promise<{ samples: Float32Array; duration: number }> {
  let buffer: AudioBuffer;
  try {
    const ctx = new OfflineAudioContext(1, 1, ANALYSIS_RATE);
    buffer = await ctx.decodeAudioData(data.slice(0));
  } catch {
    throw new ImportError(
      'Ce fichier ne peut pas être lu. Il est peut-être endommagé ou encodé dans un format non pris en charge.',
    );
  }
  if (buffer.duration < 5) throw new ImportError('Le morceau est trop court (5 secondes minimum).');
  const len = buffer.length;
  const samples = new Float32Array(len);
  for (let c = 0; c < buffer.numberOfChannels; c++) {
    const ch = buffer.getChannelData(c);
    for (let i = 0; i < len; i++) samples[i] += ch[i] / buffer.numberOfChannels;
  }
  return { samples, duration: buffer.duration };
}

export function runAnalysis(
  samples: Float32Array,
  seedKey: string,
  onProgress: (p: number, label: string) => void,
  signal?: AbortSignal,
  stems?: { vocals: Float32Array; accomp: Float32Array },
  rhythm?: { beats: number[]; downbeats: number[] },
  pitch?: ExternalPitch,
): Promise<Chart> {
  return runAnalysisFull(samples, seedKey, onProgress, signal, stems, rhythm, pitch).then((r) => r.chart);
}

/** Comme runAnalysis, avec le résumé des IA (analyse avancée) pour reconstruire le parcours plus tard. */
export function runAnalysisFull(
  samples: Float32Array,
  seedKey: string,
  onProgress: (p: number, label: string) => void,
  signal?: AbortSignal,
  stems?: { vocals: Float32Array; accomp: Float32Array },
  rhythm?: { beats: number[]; downbeats: number[] },
  pitch?: ExternalPitch,
  inst: InstrumentFiles = {},
): Promise<{ chart: Chart; summary?: MusicSummary; lagSec?: number }> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' });
    signal?.addEventListener('abort', () => {
      worker.terminate();
      reject(new ImportError('Import annulé.'));
    });
    worker.onmessage = (e) => {
      const msg = e.data;
      if (msg.type === 'progress') onProgress(msg.p, msg.label);
      else if (msg.type === 'done') {
        worker.terminate();
        resolve({ chart: msg.chart as Chart, summary: msg.summary as MusicSummary | undefined, lagSec: msg.lagSec });
      } else if (msg.type === 'error') {
        worker.terminate();
        reject(new ImportError(msg.message));
      }
    };
    worker.onerror = () => {
      worker.terminate();
      reject(new ImportError('L’analyse n’a pas pu démarrer sur cet appareil.'));
    };
    const transfer: Transferable[] = [samples.buffer];
    if (stems) transfer.push(stems.vocals.buffer, stems.accomp.buffer);
    if (pitch) transfer.push(pitch.f0.buffer, pitch.conf.buffer);
    for (const a of [inst.drums, inst.bpBass, inst.bpOther]) if (a) transfer.push(a.buffer);
    worker.postMessage(
      { samples, sampleRate: ANALYSIS_RATE, seed: hashString(seedKey), ...stems, rhythm, pitch, ...inst },
      transfer,
    );
  });
}
