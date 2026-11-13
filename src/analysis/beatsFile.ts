/** Décode beats.f32 : [nb, nd, temps…, premiers temps…]. */
export function parseBeats(data: Float32Array): { beats: number[]; downbeats: number[] } | undefined {
  if (data.length < 2) return undefined;
  const nb = data[0];
  const nd = data[1];
  if (!Number.isInteger(nb) || !Number.isInteger(nd) || nb < 0 || nd < 0 || 2 + nb + nd !== data.length) return undefined;
  return { beats: Array.from(data.subarray(2, 2 + nb)), downbeats: Array.from(data.subarray(2 + nb)) };
}

/** Hauteur de la voix par l'IA (CREPE) : une valeur toutes les `hopT` s, à partir de `t0`. */
export interface ExternalPitch {
  t0: number;
  hopT: number;
  /** Hz (0 = inconnu). */
  f0: Float32Array;
  /** Confiance 0..1. */
  conf: Float32Array;
}

/** Décode pitch.f32 : [n, f0…, confiance…] (10 ms). */
export function parsePitch(data: Float32Array): ExternalPitch | undefined {
  if (data.length < 1) return undefined;
  const n = data[0];
  if (!Number.isInteger(n) || n < 1 || data.length !== 1 + 2 * n) return undefined;
  return { t0: 0, hopT: 0.01, f0: data.slice(1, 1 + n), conf: data.slice(1 + n) };
}
