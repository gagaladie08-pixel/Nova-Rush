import { describe, expect, it } from 'vitest';
import { buildChart, type Candidate } from '../src/analysis/analyzer';
import { CONDUCTOR_VERSION, chartFromSummary, conduct, type MusicSummary } from '../src/analysis/conductor';
import { CRASH, HAT, KICK, SNARE, type DrumHit, type DrumKind } from '../src/analysis/drums';
import type { InstrNote } from '../src/analysis/instruments';
import type { Section } from '../src/game/types';

const BEAT = 0.5;
const DUR = 64;
const beats = Array.from({ length: DUR / BEAT }, (_, i) => i * BEAT);
const bi = (t: number) => Math.round(t / BEAT);
const cand = (t: number, strength: number, pitch?: number, sustain?: number): Candidate => ({
  t,
  strength,
  pitch,
  sustain,
  onBeat: Math.abs(t / BEAT - bi(t)) < 0.01,
  downBeat: Math.abs(t / BEAT - bi(t)) < 0.01 && bi(t) % 4 === 0,
  beatIndex: bi(t),
  priority: strength + 1,
});
const sec = (t: number, end: number, kind: Section['kind'], group: number, rep: number, energy: number): Section => ({
  t,
  end,
  kind,
  group,
  rep,
  sim: rep ? 0.8 : 1,
  energy,
});
const SECTIONS = [
  sec(0, 8, 'intro', 0, 0, 0.3),
  sec(8, 24, 'couplet', 1, 0, 0.5),
  sec(24, 40, 'refrain', 2, 0, 0.8),
  sec(40, 48, 'pont', 3, 0, 0.6),
  sec(48, 64, 'refrain', 2, 1, 0.85),
];
/** Mélodie chantée : la même dans les deux refrains, une note tenue (2 temps) à 30 s et 54 s. */
const MELODY = [60, 62, 64, 62, 67, 65, 64, 62];
function voiceCands(): Candidate[] {
  const out: Candidate[] = [];
  for (const [a, z] of [
    [8, 24],
    [24, 40],
    [48, 64],
  ]) {
    for (let t = a + 0.5; t < z - 0.5; t += BEAT) {
      const rel = (t - (a >= 48 ? 48 : a >= 24 ? 24 : a)) / BEAT;
      if (rel % 1) continue;
      const held = a >= 24 && rel === 12;
      if (a >= 24 && rel === 13) continue; // tenue
      out.push(cand(t, 1.2, MELODY[rel % 8], held ? 1.0 : undefined));
    }
  }
  return out;
}

function summary(o: { drums?: DrumHit[] | null; other?: InstrNote[] | null; padUnderHold?: boolean } = {}): MusicSummary {
  const hits: DrumHit[] = [];
  const hit = (t: number, k: DrumKind, s = 1) => hits.push({ t, k, s });
  for (let b = 0; b < beats.length; b++) {
    const t = beats[b];
    if (t < 8) continue; // pas de batterie dans l'intro
    hit(t, b % 2 ? SNARE : KICK);
    hit(t, HAT, 0.5);
    hit(t + BEAT / 2, HAT, 0.5);
  }
  hit(24, CRASH, 1.5);
  hit(48, CRASH, 1.5);
  hits.sort((a, b) => a.t - b.t || a.k - b.k);
  const other: InstrNote[] = [];
  // Intro : mélodie de guitare en croches.
  for (let t = 0.5; t < 8; t += BEAT / 2) other.push({ t, dur: 0.2, midi: 72 + ((t * 4) % 5), amp: 0.6 });
  if (o.padUnderHold !== false) {
    // Nappe sous la note tenue des refrains (même note, Do).
    other.push({ t: 29.9, dur: 1.4, midi: 48, amp: 0.5 }, { t: 53.9, dur: 1.4, midi: 48, amp: 0.5 });
  }
  other.sort((a, b) => a.t - b.t);
  const voice = voiceCands();
  return {
    v: 1,
    duration: DUR,
    bpm: 120,
    beats,
    downs: beats.flatMap((_, i) => (i % 4 === 0 ? [i] : [])),
    beatEnergy: beats.map(() => 0.1),
    rhythmSource: 'ai',
    melodic: true,
    melodyConfidence: 0.7,
    sections: SECTIONS,
    structureConfidence: 0.8,
    voice: {
      ratio: 0.7,
      spans: [
        [8, 24],
        [24, 40],
        [48, 64],
      ],
      noteSpans: voice.map((c) => [c.t, c.t + (c.sustain ?? 0.4)] as [number, number]),
      cands: voice,
      pitchSource: 'ai',
    },
    accomp: beats.filter((t) => t >= 0.5).map((t) => cand(t, 0.5)),
    ...(o.drums === null ? {} : { drums: o.drums ?? hits }),
    ...(o.other === null ? {} : { other: o.other ?? other }),
  };
}

describe("chef d'orchestre", () => {
  it('choisit la source qui mène chaque section', () => {
    const { report } = conduct(summary());
    expect(report.leads.map((l) => l.lead)).toEqual(['instrument', 'voice', 'voice', 'drums', 'voice']);
    expect(report.conf.drums).toBeGreaterThan(0.9);
    expect(report.conf.instrument).toBeGreaterThan(0.9);
  });

  it('suit la mélodie de l’instrument dans l’intro et la batterie dans le break', () => {
    const { features } = conduct(summary());
    const intro = features.candidates.filter((c) => c.t < 8);
    expect(intro.filter((c) => c.pitch !== undefined && c.pitch >= 72).length).toBeGreaterThan(20);
    const brk = features.candidates.filter((c) => c.t >= 40 && c.t < 48);
    // Grosse caisse et caisse claire : colonnes stables.
    const kick = brk.filter((c) => c.lane === 0);
    const snare = brk.filter((c) => c.lane === 3);
    expect(kick.length).toBeGreaterThanOrEqual(7);
    expect(snare.length).toBeGreaterThanOrEqual(7);
    const chart = buildChart(features, 'moyen', 3);
    const lanesOf = (ts: Candidate[]) => new Set(chart.filter((n) => ts.some((c) => Math.abs(c.t - n.t) < 0.01)).map((n) => n.lane));
    const kl = lanesOf(kick);
    const sl = lanesOf(snare);
    expect([...kl]).toEqual([0]);
    expect([...sl]).toEqual([3]);
  });

  it('tuile longue seulement si deux sources confirment la tenue', () => {
    const held = (s: MusicSummary) => conduct(s).features.candidates.filter((c) => c.sustain !== undefined).map((c) => c.t);
    expect(held(summary())).toEqual([30, 54]);
    expect(held(summary({ padUnderHold: false }))).toEqual([]);
    // Sans les IA des instruments : comportement d'avant (la voix seule suffit).
    expect(held(summary({ drums: null, other: null }))).toEqual([30, 54]);
  });

  it('appuie les coups de cymbale par un accord, plus fort au dernier refrain', () => {
    const { features } = conduct(summary());
    const accents = features.candidates.filter((c) => c.accent).map((c) => c.t);
    expect(accents).toEqual([24, 48]);
    const lift = (t: number) => features.candidates.find((c) => Math.abs(c.t - t) < 0.01)?.lift ?? -1;
    expect(lift(48)).toBe(1);
    expect(lift(24)).toBeLessThan(1);
    expect(lift(24)).toBeGreaterThan(lift(10));
    const chart = buildChart(features, 'difficile', 3);
    for (const t of [24, 48]) expect(chart.filter((n) => Math.abs(n.t - t) < 0.01).length).toBeGreaterThanOrEqual(2);
  });

  it('même motif à chaque refrain', () => {
    const chart = buildChart(conduct(summary()).features, 'moyen', 3);
    const lanes = (a: number) =>
      chart
        .filter((n) => n.t >= a && n.t < a + 16)
        .map((n) => `${(n.t - a).toFixed(2)}:${n.lane}`)
        .filter((x) => !x.startsWith('0.00'));
    const r1 = lanes(24);
    const r2 = lanes(48);
    const same = r2.filter((x) => r1.includes(x)).length;
    expect(same / r1.length).toBeGreaterThan(0.85);
  });

  it('écarte une batterie mal calée (confiance faible) et revient au parcours d’avant', () => {
    const off = summary().drums!.map((h) => ({ ...h, t: h.t + 0.13 }));
    const { report, features } = conduct(summary({ drums: off }));
    expect(report.conf.drums).toBeLessThan(0.5);
    expect(report.leads.map((l) => l.lead)).not.toContain('drums');
    expect(features.candidates.some((c) => c.accent)).toBe(false);
  });

  it('reconstruit le même parcours à partir du résumé enregistré', () => {
    const s = summary();
    const json = JSON.stringify(s);
    const a = chartFromSummary(s, 11);
    const b = chartFromSummary(JSON.parse(json) as MusicSummary, 11);
    expect(b.notes).toEqual(a.notes);
    expect(a.conductor?.leads.length).toBe(5);
    expect(a.conductorVersion).toBe(CONDUCTOR_VERSION);
    expect(a.kicks?.length).toBeGreaterThan(50);
    expect(a.crashes).toEqual([24, 48]);
    expect(json.length).toBeLessThan(200_000);
  });
});
