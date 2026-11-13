import { describe, expect, it } from 'vitest';
import { analyzeSong, extractFeatures } from '../src/analysis/analyzer';

const SR = 22050;

function synth(opts: { bpm: number; seconds: number; melody: boolean; seed?: number }) {
  const out = new Float32Array(SR * opts.seconds);
  const beat = 60 / opts.bpm;
  const truth: number[] = [];
  let x = opts.seed ?? 1;
  const rnd = () => ((x = (x * 16807) % 2147483647) / 2147483647);
  // kick sur chaque temps
  for (let t = 0.5; t < opts.seconds - 0.5; t += beat) {
    const s0 = Math.round(t * SR);
    for (let i = 0; i < SR * 0.15 && s0 + i < out.length; i++) {
      const env = Math.exp(-i / (SR * 0.04)) * Math.min(1, (SR * 0.15 - i) / 400);
      out[s0 + i] += 0.6 * env * Math.sin(2 * Math.PI * (55 + 80 * Math.exp(-i / 400)) * (i / SR));
    }
  }
  const scale = [60, 62, 64, 67, 69, 72, 74, 76];
  for (let t = 0.5; t < opts.seconds - 0.5; t += beat / 2) {
    const s0 = Math.round(t * SR);
    truth.push(t);
    if (opts.melody) {
      const m = scale[Math.floor(rnd() * scale.length)];
      const f = 440 * Math.pow(2, (m - 69) / 12);
      const len = SR * beat * 0.45;
      for (let i = 0; i < len && s0 + i < out.length; i++) {
        const env = Math.min(1, i / 60) * Math.exp(-i / (SR * 0.3)) * Math.min(1, (len - i) / 400);
        let v = 0;
        for (let h = 1; h <= 5; h++) v += Math.sin(2 * Math.PI * f * h * (i / SR)) / h;
        out[s0 + i] += 0.3 * env * v;
      }
    } else {
      for (let i = 0; i < SR * 0.05 && s0 + i < out.length; i++) {
        out[s0 + i] += 0.25 * (rnd() * 2 - 1) * Math.exp(-i / (SR * 0.01));
      }
    }
  }
  return { out, truth };
}

describe('analyse musicale', () => {
  it('retrouve le tempo et la mélodie', () => {
    const { out, truth } = synth({ bpm: 120, seconds: 40, melody: true });
    const f = extractFeatures(out, SR);
    expect(Math.abs(f.bpm - 120)).toBeLessThan(3);
    console.log('confiance', f.melodyConfidence);
    expect(f.melodic).toBe(true);
    // précision temporelle des attaques
    const errs = truth.slice(2, -2).map((t) => Math.min(...f.onsets.map((o) => Math.abs(o.t - t))));
    const meanSigned =
      truth.slice(2, -2).map((t) => {
        let best = f.onsets[0];
        for (const o of f.onsets) if (Math.abs(o.t - t) < Math.abs(best.t - t)) best = o;
        return best.t - t;
      }).reduce((a, b) => a + b, 0) / (truth.length - 4);
    console.log('biais moyen (ms)', (meanSigned * 1000).toFixed(1), 'erreur max', (Math.max(...errs) * 1000).toFixed(1));
    expect(Math.abs(meanSigned)).toBeLessThan(0.015);
    const chart = analyzeSong(out, SR, 42);
    console.log('mélodique', chart.level, chart.notes.length, chart.stats);
    const lanes = new Set(chart.notes.map((n) => n.lane));
    expect(lanes.size).toBe(4);
  });

  it('se replie sur le rythme sans mélodie claire', () => {
    const { out } = synth({ bpm: 100, seconds: 40, melody: false });
    const f = extractFeatures(out, SR);
    console.log('confiance', f.melodyConfidence);
    expect(f.melodic).toBe(false);
    expect(Math.abs(f.bpm - 100)).toBeLessThan(3);
    const chart = analyzeSong(out, SR, 7);
    console.log('rythmique', chart.level, chart.notes.length, chart.stats);
    expect(chart.notes.length).toBeGreaterThan(30);
    // aucune collision dans une même voie
    for (let l = 0; l < 4; l++) {
      const ts = chart.notes.filter((n) => n.lane === l).map((n) => n.t);
      for (let i = 1; i < ts.length; i++) expect(ts[i] - ts[i - 1]).toBeGreaterThan(0.1);
    }
  });

  it('refuse un fichier silencieux', () => {
    expect(() => analyzeSong(new Float32Array(SR * 5), SR, 1)).toThrow();
  });
});
