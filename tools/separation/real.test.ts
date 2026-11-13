/**
 * Analyse de vraies chansons séparées par la CI (tools/separation/stems.py).
 * Ignoré hors CI (pas de pistes). Compare aussi le rythme de Beat This! et la structure.
 */
import { appendFileSync, existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { analyzeSong, analyzeSongWithVoice, onsetEnvelope, pickOnsets } from '../../src/analysis/analyzer';
import { percentile } from '../../src/analysis/dsp';
import { trackVoice } from '../../src/analysis/voice';
import { parseBeats } from '../../src/analysis/beatsFile';
import { parsePitch } from '../../src/analysis/beatsFile';
import { analyzeSongAdvanced, type Instruments } from '../../src/analysis/conductor';
import { beatAgreement, drumHits } from '../../src/analysis/drums';
import { instrumentNotes, parsePosteriors, skyline } from '../../src/analysis/instruments';

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
  // Meilleur alignement (± 8 temps), comme le générateur.
  return refr
    .slice(1)
    .map((s) => {
      let best = '';
      let bestN = -1;
      for (let sh = -32; sh <= 32; sh += 4) {
        const k = keys(s).map((x) => {
          const [q, l] = x.split(':');
          return `${Number(q) + sh}:${l}`;
        });
        const n = k.filter((x) => first.has(x)).length;
        const firstPos = new Set([...first].map((x) => x.split(':')[0]));
        const rhythm = k.filter((x) => firstPos.has(x.split(':')[0])).length;
        if (n > bestN) {
          bestN = n;
          best = `${n}/${k.length} même place et colonne, ${rhythm}/${k.length} même place (1er refrain ${first.size} tuiles, décalage ${sh / 4} temps)`;
        }
      }
      return best;
    })
    .join(', ');
}

function refrainDump(c: ReturnType<typeof analyzeSong>, vnotes: { t: number; midi: number }[]): string {
  const refr = (c.sections ?? []).filter((s) => s.kind === 'refrain').slice(0, 2);
  if (refr.length < 2 || !c.beats?.length) return '-';
  const beats = c.beats;
  const pos = (t: number) => {
    let i = 0;
    while (i + 2 < beats.length && beats[i + 1] <= t) i++;
    return i + (t - beats[i]) / (beats[i + 1] - beats[i]);
  };
  return refr
    .map((s) =>
      c.notes
        .filter((n) => n.t >= s.t && n.t < s.end)
        .slice(0, 22)
        .map((n) => {
          const v = vnotes.find((x) => Math.abs(x.t - n.t) < 0.08);
          return `${((pos(n.t) - pos(s.t))).toFixed(2)}:${n.lane}${v ? ':' + v.midi : ''}`;
        })
        .join(' '),
    )
    .join(' ||| ');
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
      const pitchFile = join(DIR, `${name}.pitch.f32`);
      const ext = existsSync(pitchFile) ? parsePitch(read(name, 'pitch')) : undefined;
      const ai2 = ext ? analyzeSongWithVoice(mix, voc, acc, SR, 1, () => {}, rhythm, ext) : undefined;

      const env = onsetEnvelope(voc, SR, () => {});
      const odf = env.flux.slice();
      const p95 = percentile(odf, 0.95) || 1;
      for (let i = 0; i < odf.length; i++) odf[i] /= p95;
      const vOn = pickOnsets(odf, env.hopT, env.t0);
      const vt = trackVoice(voc, SR, vOn);
      const vc = ext ? trackVoice(voc, SR, vOn, () => {}, ext) : undefined;
      /** Sauts d'au moins une octave entre notes voisines (souvent des erreurs). */
      const jumps = (ns: { midi: number; t: number; dur: number }[]) => {
        let j = 0;
        for (let i = 1; i < ns.length; i++) if (ns[i].t - (ns[i - 1].t + ns[i - 1].dur) < 0.3 && Math.abs(ns[i].midi - ns[i - 1].midi) >= 11) j++;
        return `${j}/${Math.max(1, ns.length - 1)}`;
      };
      const range = (ns: { midi: number }[]) => {
        const m = ns.map((n) => n.midi).sort((a, b) => a - b);
        return `${m[Math.floor(m.length * 0.05)]}–${m[Math.floor(m.length * 0.95)]}`;
      };
      const durs = vt.notes.map((n) => n.dur).sort((a, b) => a - b);
      const midis = vt.notes.map((n) => n.midi).sort((a, b) => a - b);
      // Part des tuiles posées sur une note chantée (±70 ms), pendant le chant.
      const near = (notes: { t: number }[], ref = vt) => {
        const sung = notes.filter((n) => ref.spans.some(([a, b]) => n.t >= a - 0.05 && n.t <= b));
        const on = sung.filter((n) => ref.notes.some((v) => Math.abs(v.t - n.t) < 0.07));
        return `${on.length}/${sung.length}`;
      };
      const fmt = (c: typeof classic, ref = vt) =>
        `${c.source} ${c.level} ${c.bpm} bpm, ${c.notes.length} tuiles (${c.notes.filter((n) => n.dur).length} longues), sur la voix ${near(c.notes, ref)}`;
      // Phase 3 : Demucs + Basic Pitch + chef d'orchestre.
      const has = (s: string) => existsSync(join(DIR, `${name}.${s}`));
      let conductorMsg = 'chef d’orchestre : pas de pistes Demucs\n';
      if (has('drums.f32') && ai2) {
        const inst: Instruments = {};
        let t3 = performance.now();
        inst.drums = drumHits(read(name, 'drums'), SR);
        const tDrums = performance.now() - t3;
        for (const st of ['bass', 'other'] as const) {
          if (!has(`bp_${st}.u8`)) continue;
          const b = readFileSync(join(DIR, `${name}.bp_${st}.u8`));
          const p = parsePosteriors(new Uint8Array(b.buffer, b.byteOffset, b.byteLength));
          if (p) inst[st] = instrumentNotes(p);
        }
        t3 = performance.now();
        const full = analyzeSongAdvanced(mix, voc, acc, SR, 1, () => {}, rhythm, ext, inst);
        const tFull = performance.now() - t3;
        const c = full.chart;
        const kinds = [0, 1, 2, 3].map((k) => inst.drums!.filter((h) => h.k === k).length);
        const sky = inst.other ? skyline(inst.other) : [];
        const leadAt = (s: { t: number; end: number }) => c.conductor?.leads.find((l) => Math.abs(l.t - s.t) < 0.5)?.lead ?? '?';
        conductorMsg =
          `batterie : ${kinds[0]} grosses caisses, ${kinds[1]} caisses claires, ${kinds[2]} charleston, ${kinds[3]} crash ` +
          `(${tDrums.toFixed(0)} ms), accord avec Beat This! ${(beatAgreement(inst.drums, rhythm?.beats ?? c.beats ?? []) * 100).toFixed(0)} %\n` +
          `Basic Pitch : basse ${inst.bass?.length ?? '-'} notes, autres ${inst.other?.length ?? '-'} notes (ligne mélodique ${sky.length}) ; ` +
          `hauteurs autres ${inst.other?.length ? range(inst.other) : '-'}\n` +
          `confiance : ${JSON.stringify(c.conductor?.conf)}\n` +
          `sources : ${(c.sections ?? []).map((s) => `${s.kind} ${s.t.toFixed(0)}–${s.end.toFixed(0)} → ${leadAt(s)}`).join(' · ') || c.conductor?.leads.map((l) => `${l.t}–${l.end} ${l.lead}`).join(' · ')}\n` +
          `chef d’orchestre : ${fmt(c, vc)}, ${c.notes.filter((n) => n.chord !== undefined).length} tuiles en accord ` +
          `(avant : ${ai2.notes.filter((n) => n.chord !== undefined).length}) (${tFull.toFixed(0)} ms), résumé ${(JSON.stringify(full.summary).length / 1024).toFixed(0)} Ko\n` +
          `refrains (chef d’orchestre) : ${refrainMotif(c)}\n`;
        expect(c.notes.length).toBeGreaterThan(10);
      }

      notice(
        `analyse ${name}`,
        `durée ${(mix.length / SR).toFixed(0)} s | voix : ${vt.notes.length} notes, part chantée ${(vt.ratio * 100).toFixed(0)} %, ` +
          `durée médiane ${(durs[durs.length >> 1] ?? 0).toFixed(2)} s, hauteurs ${midis[Math.floor(midis.length * 0.05)]}–${midis[Math.floor(midis.length * 0.95)]}\n` +
          `classique : ${fmt(classic)} (${tClassic.toFixed(0)} ms)\n` +
          `voix : ${fmt(voice)} (${tVoice.toFixed(0)} ms)\n` +
          (ai
            ? `voix + Beat This! : ${fmt(ai)}, rythme ${ai.rhythmSource}, ${ai.downbeats?.length} mesures\n` +
              `structure (confiance ${ai.structureConfidence}) : ${(ai.sections ?? [])
                .map((s) => `${s.kind}[${s.group}] ${s.t.toFixed(0)}–${s.end.toFixed(0)} (sim ${s.sim}, énergie ${s.energy})`)
                .join(' · ')}\n` +
              `refrains : ${refrainMotif(ai)}\n` +
              `détail : ${refrainDump(ai, vt.notes)}\n`
            : 'Beat This! : pas de temps\n') +
          (vc && ai2
            ? `CREPE : ${vc.notes.length} notes (YIN ${vt.notes.length}), hauteurs ${range(vc.notes)} (YIN ${range(vt.notes)}), ` +
              `sauts d'octave ${jumps(vc.notes)} (YIN ${jumps(vt.notes)}), part chantée ${(vc.ratio * 100).toFixed(0)} %\n` +
              `voix + Beat This! + CREPE : ${fmt(ai2, vc)} (sur les notes YIN : ${near(ai2.notes)})\nrefrains (CREPE) : ${refrainMotif(ai2)}\n`
            : 'CREPE : pas de hauteur\n') +
          conductorMsg +
          `premières notes : ${vt.notes
            .slice(0, 16)
            .map((n) => `${n.t.toFixed(2)}/${n.midi}/${n.dur.toFixed(2)}`)
            .join(' ')}`,
      );
      expect(voice.notes.length).toBeGreaterThan(10);
    }, 180_000);
  }
});
