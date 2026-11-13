import { describe, expect, it } from 'vitest';
import { bpFrameTime, notesFromPosteriors, parsePosteriors, skyline, type InstrNote } from '../src/analysis/instruments';

import fixture from './fixtures/basic-pitch-notes.json';

const fx = fixture as unknown as {
  frames: number;
  onset: string;
  note: string;
  events: [number, number, number, number][];
};
const b64 = (s: string) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));

describe('Basic Pitch : notes des instruments', () => {
  it('donne exactement les notes du code officiel (output_to_notes_polyphonic)', () => {
    const onset = b64(fx.onset);
    const note = b64(fx.note);
    const file = new Uint8Array(onset.length + note.length);
    file.set(onset, 0);
    file.set(note, onset.length);
    const p = parsePosteriors(file)!;
    expect(p.frames).toBe(fx.frames);
    const ev = notesFromPosteriors(p);
    expect(ev.length).toBe(fx.events.length);
    ev.forEach((e, i) => {
      const w = fx.events[i];
      expect([e[0], e[1], e[2]]).toEqual([w[0], w[1], w[2]]);
      expect(e[3]).toBeCloseTo(w[3], 5);
    });
  });

  it('recoud les fenêtres de 2 s sans dérive', () => {
    expect(bpFrameTime(0)).toBe(0);
    expect(bpFrameTime(141)).toBeCloseTo((141 * 256) / 22050, 9);
    expect(bpFrameTime(142)).toBeCloseTo(36164 / 22050, 9);
    // 4 min : l'image correspondant à 240 s tombe bien à 240 s (±6 ms).
    const k = Math.round((240 * 22050) / 36164) * 142;
    expect(Math.abs(bpFrameTime(k) - (k / 142) * (36164 / 22050))).toBeLessThan(1e-9);
  });

  it('extrait la ligne mélodique des accords', () => {
    const n = (t: number, midi: number, dur = 0.4, amp = 0.6): InstrNote => ({ t, dur, midi, amp });
    const notes = [n(0, 48, 2), n(0, 55, 2), n(0.01, 67), n(0.5, 69), n(1, 71), n(1.5, 60, 0.4, 0.2), n(2, 52), n(2, 64)];
    const top = skyline(notes);
    expect(top.map((x) => x.midi)).toEqual([67, 69, 71, 64]);
  });
});
