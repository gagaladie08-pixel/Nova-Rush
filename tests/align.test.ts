import { describe, expect, it } from 'vitest';
import { alignStems, measureLag } from '../src/analysis/align';

const SR = 22050;

function song(seconds: number): Float32Array {
  const x = new Float32Array(seconds * SR);
  let seed = 7;
  const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff) * 2 - 1;
  for (let b = 0; b < seconds * 2; b++) {
    // Percussions (bruit décroissant) + notes tenues.
    const a = Math.round(b * 0.5 * SR + (b % 3) * 1000);
    for (let i = 0; i < 4000 && a + i < x.length; i++) x[a + i] += 0.6 * rnd() * Math.exp(-i / 600);
    const f = 220 * 2 ** ((b % 7) / 12);
    for (let i = 0; i < 10000 && a + i < x.length; i++) x[a + i] += 0.2 * Math.sin((2 * Math.PI * f * i) / SR);
  }
  return x;
}

describe('recalage des pistes séparées', () => {
  it('retrouve le décalage à l’échantillon près', () => {
    const mix = song(40);
    for (const lag of [0, 576, -300, 1105, 2047]) {
      const stems = new Float32Array(mix.length + 3000);
      for (let i = 0; i < mix.length; i++) {
        const j = i + lag;
        if (j >= 0 && j < stems.length) stems[j] = mix[i] * 0.98;
      }
      expect(measureLag(mix, stems, SR)).toBe(lag);
    }
  });

  it('recale voix et accompagnement ensemble', () => {
    const mix = song(20);
    const lag = 600;
    const voc = new Float32Array(mix.length + lag);
    const acc = new Float32Array(mix.length + lag);
    for (let i = 0; i < mix.length; i++) {
      voc[i + lag] = mix[i] * 0.4;
      acc[i + lag] = mix[i] * 0.6;
    }
    const r = alignStems(mix, voc, acc, SR);
    expect(r.lagSec).toBeCloseTo(lag / SR, 5);
    expect(r.vocals.length).toBe(mix.length);
    let err = 0;
    for (let i = 0; i < mix.length; i++) err = Math.max(err, Math.abs(r.vocals[i] + r.accomp[i] - mix[i]));
    expect(err).toBeLessThan(1e-5);
  });

  it('renvoie 0 sur du silence', () => {
    expect(measureLag(new Float32Array(SR * 10), new Float32Array(SR * 10), SR)).toBe(0);
  });
});
