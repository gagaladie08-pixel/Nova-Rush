import { describe, expect, it } from 'vitest';
import { analyzeSongWithVoice, onsetEnvelope, pickOnsets } from '../src/analysis/analyzer';
import { percentile } from '../src/analysis/dsp';
import { inSpans, trackVoice } from '../src/analysis/voice';

const SR = 22050;

interface TruthNote {
  t: number;
  dur: number;
  midi: number;
  legato: boolean;
}

/** Voix chantée synthétique (voyelle « a », vibrato, glissés, consonnes) + accompagnement. */
function song(seed = 3) {
  let x = seed;
  const rnd = () => ((x = (x * 16807) % 2147483647) / 2147483647);
  const bpm = 100;
  const beat = 60 / bpm;
  const seconds = 62;
  const n = SR * seconds;
  const voice = new Float32Array(n);
  const acc = new Float32Array(n);
  const truth: TruthNote[] = [];
  const scale = [57, 59, 60, 62, 64, 65, 67, 69, 71, 72];
  const pattern = [1, 0.5, 0.5, 1, 1, 0.5, 0.5, 2.5];
  // Phrases chantées : 4 phrases, pont instrumental, 3 phrases.
  let t = 4 * beat * 2; // intro instrumentale (8 temps)
  let idx = 4;
  const phrases: number[] = [];
  for (let p = 0; p < 7; p++) {
    if (p === 4) t += 16 * beat; // pont instrumental
    phrases.push(t);
    for (let k = 0; k < pattern.length; k++) {
      const step = Math.round((rnd() - 0.5) * 4);
      const prevIdx = idx;
      idx = Math.max(0, Math.min(scale.length - 1, idx + step));
      const same = idx === prevIdx;
      truth.push({ t, dur: pattern[k] * beat, midi: scale[idx], legato: k > 0 && !same && rnd() < 0.45 });
      t += pattern[k] * beat;
    }
    t += 2 * beat; // respiration
  }
  // Rendu de la voix.
  const formants = [
    [800, 80],
    [1200, 90],
    [2500, 120],
  ];
  const envAt = (f: number) => {
    let a = 0;
    for (const [fc, bw] of formants) a += 1 / (1 + ((f - fc) / bw) ** 2);
    return a + 0.02;
  };
  let phase = 0;
  for (let i = 0; i < truth.length; i++) {
    const nt = truth[i];
    const prev = truth[i - 1];
    const next = truth[i + 1];
    const tiedNext = next && next.legato && Math.abs(next.t - (nt.t + nt.dur)) < 1e-6;
    const s0 = Math.round(nt.t * SR);
    const s1 = Math.round((nt.t + nt.dur) * SR);
    const end = tiedNext ? s1 : s1 - Math.round(0.06 * SR);
    // Consonne (bruit) juste avant les notes attaquées.
    if (!nt.legato) {
      for (let j = Math.round(-0.03 * SR); j < 0; j++) {
        if (s0 + j >= 0) voice[s0 + j] += 0.08 * (rnd() * 2 - 1);
      }
    }
    for (let s = s0; s < end && s < n; s++) {
      const u = (s - s0) / SR;
      let m = nt.midi;
      if (nt.legato && prev && u < 0.05) m = prev.midi + (nt.midi - prev.midi) * (u / 0.05);
      if (u > 0.25) m += 0.25 * Math.sin(2 * Math.PI * 5.5 * (u - 0.25));
      const f0 = 440 * Math.pow(2, (m - 69) / 12);
      phase += (2 * Math.PI * f0) / SR;
      let v = 0;
      for (let h = 1; h * f0 < 5000; h++) v += envAt(h * f0) * Math.sin(h * phase) * (1 / Math.sqrt(h));
      const att = nt.legato ? 1 : Math.min(1, u / 0.03);
      const rel = Math.min(1, (end - s) / (0.04 * SR));
      voice[s] += 0.12 * v * att * (tiedNext ? 1 : rel);
    }
  }
  // Accompagnement : kick, charleston, basse.
  for (let b = 0; b * beat < seconds - 1; b++) {
    const s0 = Math.round((0.3 + b * beat) * SR);
    for (let i = 0; i < SR * 0.15 && s0 + i < n; i++) {
      acc[s0 + i] += 0.5 * Math.exp(-i / (SR * 0.04)) * Math.sin(2 * Math.PI * (50 + 70 * Math.exp(-i / 300)) * (i / SR));
    }
    for (const off of [0, 0.5]) {
      const h0 = Math.round((0.3 + (b + off) * beat) * SR);
      for (let i = 0; i < SR * 0.03 && h0 + i < n; i++) acc[h0 + i] += 0.12 * (rnd() * 2 - 1) * Math.exp(-i / (SR * 0.006));
    }
    const root = 45 + [0, 5, 7, 3][Math.floor(b / 4) % 4];
    const f = 440 * Math.pow(2, (root - 69) / 12);
    for (let i = 0; i < SR * beat * 0.9 && s0 + i < n; i++) {
      acc[s0 + i] += 0.15 * Math.exp(-i / (SR * 0.4)) * (Math.sin(2 * Math.PI * f * (i / SR)) + 0.3 * Math.sin(4 * Math.PI * f * (i / SR)));
    }
  }
  const mix = new Float32Array(n);
  const vocals = new Float32Array(n);
  const accomp = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    mix[i] = voice[i] + acc[i];
    vocals[i] = voice[i] + 0.04 * acc[i]; // fuite de la séparation
    accomp[i] = acc[i] + 0.03 * voice[i];
  }
  return { mix, vocals, accomp, truth, phrases, beat };
}

describe('suivi de la voix', () => {
  const s = song();

  it('retrouve les notes chantées (liées, attaquées, tenues)', () => {
    const env = onsetEnvelope(s.vocals, SR, () => {});
    const odf = new Float32Array(env.flux.length);
    for (let i = 1; i < odf.length - 1; i++) odf[i] = (env.flux[i - 1] + 2 * env.flux[i] + env.flux[i + 1]) / 4;
    const p95 = percentile(odf, 0.95);
    for (let i = 0; i < odf.length; i++) odf[i] /= p95;
    const onsets = pickOnsets(odf, env.hopT, env.t0);
    const v = trackVoice(s.vocals, SR, onsets);

    let matched = 0;
    let pitchOk = 0;
    let legatoFound = 0;
    let legatoTotal = 0;
    const errs: number[] = [];
    for (const tn of s.truth) {
      let best = v.notes[0];
      for (const n of v.notes) if (Math.abs(n.t - tn.t) < Math.abs(best.t - tn.t)) best = n;
      const ok = Math.abs(best.t - tn.t) < 0.06;
      if (tn.legato) legatoTotal++;
      if (!ok) continue;
      matched++;
      errs.push(best.t - tn.t);
      if (tn.legato) legatoFound++;
      if (best.midi === tn.midi) pitchOk++;
    }
    const recall = matched / s.truth.length;
    const precision = matched / v.notes.length;
    const bias = errs.reduce((a, b) => a + b, 0) / errs.length;
    console.log(
      `notes vraies ${s.truth.length}, détectées ${v.notes.length}, rappel ${recall.toFixed(3)}, précision ${precision.toFixed(3)}, ` +
        `hauteur juste ${(pitchOk / matched).toFixed(3)}, liées ${legatoFound}/${legatoTotal}, biais ${(bias * 1000).toFixed(1)} ms, part chantée ${v.ratio.toFixed(2)}`,
    );
    expect(recall).toBeGreaterThan(0.85);
    expect(precision).toBeGreaterThan(0.8);
    expect(pitchOk / matched).toBeGreaterThan(0.9);
    expect(legatoFound / legatoTotal).toBeGreaterThan(0.75);
    expect(Math.abs(bias)).toBeLessThan(0.025);

    // Notes tenues : la durée détectée suit la vraie durée.
    const longs = s.truth.filter((t) => t.dur > 1.2);
    for (const tn of longs) {
      const n = v.notes.find((x) => Math.abs(x.t - tn.t) < 0.06);
      expect(n, `note longue à ${tn.t.toFixed(2)} s`).toBeTruthy();
      expect(n!.dur).toBeGreaterThan(tn.dur * 0.75);
    }
    // Le pont instrumental n'est pas pris pour du chant.
    const bridgeMid = s.phrases[4] - 8 * s.beat;
    expect(inSpans(v.spans, bridgeMid)).toBe(false);
  });

  it('construit un parcours qui suit la voix, et le rythme quand elle se tait', () => {
    const chart = analyzeSongWithVoice(s.mix, s.vocals, s.accomp, SR, 7);
    expect(chart.source).toBe('voice');
    expect(Math.abs(chart.bpm - 100)).toBeLessThan(3);
    // Plages réellement chantées (8 notes par phrase).
    const phr: [number, number][] = [];
    for (let p = 0; p < 7; p++) {
      const ns = s.truth.slice(p * 8, p * 8 + 8);
      phr.push([ns[0].t, ns[7].t + ns[7].dur]);
    }
    const bridge0 = phr[3][1];
    const bridge1 = phr[4][0];
    const inBridge = chart.notes.filter((n) => n.t > bridge0 + 0.3 && n.t < bridge1 - 0.3);
    const sung = chart.notes.filter((n) => phr.some(([a, b]) => n.t >= a - 0.05 && n.t <= b));
    const nearTruth = sung.filter((n) => s.truth.some((t) => Math.abs(t.t - n.t) < 0.07));
    const longs = chart.notes.filter((n) => n.dur);
    console.log(
      `niveau ${chart.level}, tuiles ${chart.notes.length}, longues ${longs.length}, pendant le chant ${sung.length} ` +
        `(${((nearTruth.length / sung.length) * 100).toFixed(0)} % sur une note chantée), pont ${inBridge.length}, part chantée ${chart.voiceRatio}`,
    );
    expect(nearTruth.length / sung.length).toBeGreaterThan(0.9);
    expect(sung.length).toBeGreaterThan(30);
    expect(inBridge.length).toBeGreaterThan(3);
    expect(longs.length).toBeGreaterThan(0);
  });
});
