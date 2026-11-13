import { describe, expect, it } from 'vitest';
import { detectStructure } from '../src/analysis/structure';
import { buildChart, extractFeatures } from '../src/analysis/analyzer';
import type { Section } from '../src/game/types';

const SR = 22050;
const BPM = 120;
const BEAT = 60 / BPM;
const BAR = 4 * BEAT;

/** Accords (fondamentales MIDI) par mesure pour chaque type de section. */
const PROG: Record<string, number[][]> = {
  intro: [[57, 60, 64]],
  couplet: [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]],
  refrain: [[53, 57, 60], [55, 59, 62], [52, 56, 59], [57, 60, 64]],
  pont: [[50, 53, 57], [52, 55, 59]],
  outro: [[57, 60, 64]],
};

const PLAN: [keyof typeof PROG, number, number][] = [
  // [type, mesures, volume]
  ['intro', 4, 0.25],
  ['couplet', 8, 0.45],
  ['refrain', 8, 0.9],
  ['couplet', 8, 0.45],
  ['refrain', 8, 0.9],
  ['pont', 8, 0.55],
  ['refrain', 8, 0.9],
  ['outro', 4, 0.25],
];

function synth(): { x: Float32Array; beats: number[]; downbeats: number[]; truth: { kind: string; t: number }[] } {
  const bars = PLAN.reduce((a, p) => a + p[1], 0);
  const n = Math.ceil(bars * BAR * SR) + SR;
  const x = new Float32Array(n);
  const truth: { kind: string; t: number }[] = [];
  let bar = 0;
  let noise = 12345;
  const rnd = () => {
    noise = (noise * 1103515245 + 12345) & 0x7fffffff;
    return noise / 0x7fffffff - 0.5;
  };
  for (const [kind, len, vol] of PLAN) {
    truth.push({ kind, t: bar * BAR });
    for (let b = 0; b < len; b++, bar++) {
      const chord = PROG[kind][b % PROG[kind].length];
      const t0 = bar * BAR;
      for (let i = Math.floor(t0 * SR); i < Math.floor((t0 + BAR) * SR); i++) {
        const t = i / SR;
        let v = 0;
        for (const m of chord) {
          const f = 440 * Math.pow(2, (m - 69) / 12);
          v += Math.sin(2 * Math.PI * f * t) + 0.3 * Math.sin(4 * Math.PI * f * t);
        }
        // Mélodie différente par section (timbre plus brillant au refrain).
        const beatIn = Math.floor((t - t0) / BEAT);
        const mel = chord[beatIn % 3] + 12;
        const fm = 440 * Math.pow(2, (mel - 69) / 12);
        v += (kind === 'refrain' ? 0.9 : 0.4) * Math.sin(2 * Math.PI * fm * t) * (kind === 'refrain' ? 1 + 0.5 * Math.sin(6 * Math.PI * fm * t) : 1);
        // Batterie : grosse caisse sur chaque temps, bruit plus fort au refrain.
        const tb = (t - t0) % BEAT;
        if (kind !== 'intro' && kind !== 'outro') v += Math.exp(-tb * 30) * Math.sin(2 * Math.PI * 60 * tb) * 2;
        if (kind === 'refrain') v += rnd() * 0.6 * Math.exp(-((t - t0) % (BEAT / 2)) * 20);
        x[i] = (v * vol) / 4;
      }
    }
  }
  const beats: number[] = [];
  const downbeats: number[] = [];
  for (let k = 0; k < bars * 4; k++) {
    beats.push(k * BEAT);
    if (k % 4 === 0) downbeats.push(k * BEAT);
  }
  return { x, beats, downbeats, truth };
}

const kindAt = (sections: Section[], t: number) => sections.find((s) => t >= s.t && t < s.end)?.kind;

describe('structure', () => {
  const song = synth();
  const st = detectStructure(song.x, SR, song.beats, song.downbeats);

  it('repère les refrains comme un groupe répété', () => {
    expect(st).not.toBeNull();
    const sections = st!.sections;
    // Milieu de chaque section réelle → type détecté
    const got = song.truth.map((tr, i) => {
      const end = song.truth[i + 1]?.t ?? tr.t + 4 * BAR;
      return kindAt(sections, (tr.t + end) / 2);
    });
    expect(got.filter((k, i) => song.truth[i].kind === 'refrain' && k === 'refrain').length).toBe(3);
    expect(got.filter((k, i) => song.truth[i].kind !== 'refrain' && k === 'refrain').length).toBe(0);
    expect(got[0]).toBe('intro');
    expect(got[got.length - 1]).toBe('outro');
    expect(st!.confidence).toBeGreaterThanOrEqual(0.5);
    const refr = sections.filter((s) => s.kind === 'refrain');
    expect(new Set(refr.map((s) => s.group)).size).toBe(1);
  });

  it('chaque refrain reprend le motif du premier', () => {
    const f = extractFeatures(song.x, SR, () => {}, { beats: song.beats, downbeats: song.downbeats });
    expect(f.rhythmSource).toBe('ai');
    const features = { ...f, sections: st!.sections, structureConfidence: st!.confidence };
    const notes = buildChart(features, 'moyen', 7);
    const refr = st!.sections.filter((s) => s.kind === 'refrain');
    const rel = (s: Section) =>
      notes
        .filter((n) => n.t >= s.t + 0.01 && n.t < s.end - BEAT)
        .map((n) => `${Math.round(((n.t - s.t) / BEAT) * 4)}:${n.lane}`);
    const first = new Set(rel(refr[0]));
    expect(first.size).toBeGreaterThan(4);
    for (const s of refr.slice(1)) {
      const r = rel(s);
      const same = r.filter((k) => first.has(k)).length;
      expect(same / Math.max(r.length, first.size)).toBeGreaterThan(0.8);
    }
  });
});
