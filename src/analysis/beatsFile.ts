/** Décode beats.f32 : [nb, nd, temps…, premiers temps…]. */
export function parseBeats(data: Float32Array): { beats: number[]; downbeats: number[] } | undefined {
  if (data.length < 2) return undefined;
  const nb = data[0];
  const nd = data[1];
  if (!Number.isInteger(nb) || !Number.isInteger(nd) || nb < 0 || nd < 0 || 2 + nb + nd !== data.length) return undefined;
  return { beats: Array.from(data.subarray(2, 2 + nb)), downbeats: Array.from(data.subarray(2 + nb)) };
}
