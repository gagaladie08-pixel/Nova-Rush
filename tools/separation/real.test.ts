/**
 * Analyse de vraies chansons séparées par la CI (tools/separation/stems.py).
 * Ignoré hors CI (pas de pistes).
 */
import { appendFileSync, existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { analyzeSong, analyzeSongWithVoice, onsetEnvelope, pickOnsets } from '../../src/analysis/analyzer';
import { percentile } from '../../src/analysis/dsp';
import { trackVoice } from '../../src/analysis/voice';
import { parseBeats } from '../../src/analysis/beatsFile';

const DIR = join(process.env.SEP_DATA ?? '/nonexistent', 'stems');
const SR = 22050;
const songs = existsSync(DIR)
  ? [...new Set(readdirSync(DIR).filter((f) => f.endsWith('.mix.f32')).map((f) => f.replace('.mix.f32', '')))]
  : [];

const read = (name: string, s: string) => {
  const b = readFileSync(join(DIR, `${name}.${s}.f32`));
  return new Float32Array(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength));
};
const notice = (title: string, msg: string) =>
  appendFileSync(join(DIR, 'notices.txt'), `::notice title=${title}::${msg.replace(/\n/g, '%0A')}\n`);

/** Part des tuiles de chaque refrain répété qui reprennent la place et la colonne du premier. */
function refrainMotif(c: ReturnType<typeof analyzeSong>): string {
  const refr = (c.sections ?? []).filter((s) => s.kind === 'refrain');
  if (refr.length < 2 || !c.beats?.length) return 'aucun refrain répété';
  const beats = c.beats;
  const pos = (t: number) => {
    let i = 0;
    while (i + 2 < beats.length && beats[i + 1] <= t) i++;
    return i + (t - beats[i]) / (beats[i + 1] - beats[i]);
  };
  const keys = (s: (typeof refr)[number]) =>
    c.notes.filter((n) => n.t >= s.t && n.t < s.end).map((n) => `${Math.round((pos(n.t) - pos(s.t)) * 4)}:${n.lane}`);
  const first = new Set(keys(refr[0]));
  return refr
    .slice(1)
    .map((s) => {
      const k = keys(s);
      return `${k.filter((x) => first.has(x)).length}/${k.length}`;
    })
    .join(', ');
}

describe.skipIf(songs.length === 0)('vraies chansons séparées', () => {
  for (const name of songs) {
    it(name, () => {
      const mix = read(name, 'mix');
      const voc = read(name, 'voc');
      const acc = read(name, 'acc');
      let t = performance.now();
      const classic = analyzeSong(mix, SR, 1);
      const tClassic = performance.now() - t;
      t = performance.now();
      const voice = analyzeSongWithVoice(mix, voc, acc, SR, 1);
      const tVoice = performance.now() - t;
      const beatsFile = join(DIR, `${name}.beats.f32`);
      const rhythm = existsSync(beatsFile) ? parseBeats(read(name, 'beats')) : undefined;
      const ai = rhythm ? analyzeSongWithVoice(mix, voc, acc, SR, 1, () => {}, rhythm) : undefined;

      const env = onsetEnvelope(voc, SR, () => {});
      const odf = env.flux.slice();
      const p95 = percentile(odf, 0.95) || 1;
      for (let i = 0; i < odf.length; i++) odf[i] /= p95;
      const vt = trackVoice(voc, SR, pickOnsets(odf, env.hopT, env.t0));
      const durs = vt.notes.map((n) => n.dur).sort((a, b) => a - b);
      const midis = vt.notes.map((n) => n.midi).sort((a, b) => a - b);
      // Part des tuiles posées sur une note chantée (±70 ms), pendant le chant.
      const near = (notes: { t: number }[]) => {
        const sung = notes.filter((n) => vt.spans.some(([a, b]) => n.t >= a - 0.05 && n.t <= b));
        const on = sung.filter((n) => vt.notes.some((v) => Math.abs(v.t - n.t) < 0.07));
        return `${on.length}/${sung.length}`;
      };
      const fmt = (c: typeof classic) =>
        `${c.source} ${c.level} ${c.bpm} bpm, ${c.notes.length} tuiles (${c.notes.filter((n) => n.dur).length} longues), sur la voix ${near(c.notes)}`;
      notice(
        `analyse ${name}`,
        `durée ${(mix.length / SR).toFixed(0)} s | voix : ${vt.notes.length} notes, part chantée ${(vt.ratio * 100).toFixed(0)} %, ` +
          `durée médiane ${(durs[durs.length >> 1] ?? 0).toFixed(2)} s, hauteurs ${midis[Math.floor(midis.length * 0.05)]}–${midis[Math.floor(midis.length * 0.95)]}\n` +
          `classique : ${fmt(classic)} (${tClassic.toFixed(0)} ms)\n` +
          `voix : ${fmt(voice)} (${tVoice.toFixed(0)} ms)\n` +
          (ai
            ? `voix + Beat This! : ${fmt(ai)}, rythme ${ai.rhythmSource}, ${ai.downbeats?.length} mesures\n` +
              `structure (confiance ${ai.structureConfidence}) : ${(ai.sections ?? [])
                .map((s) => `${s.kind}[${s.group}] ${s.t.toFixed(0)}–${s.end.toFixed(0)}`)
                .join(' · ')}\n` +
              `refrains : ${refrainMotif(ai)}\n`
            : 'Beat This! : pas de temps\n') +
          `premières notes : ${vt.notes
            .slice(0, 16)
            .map((n) => `${n.t.toFixed(2)}/${n.midi}/${n.dur.toFixed(2)}`)
            .join(' ')}`,
      );
      expect(voice.notes.length).toBeGreaterThan(10);
    }, 180_000);
  }
});
