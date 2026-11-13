import { describe, expect, it } from 'vitest';
import { ScoreKeeper, combineLong, judgeOffset } from '../src/game/scoring';
import type { Note } from '../src/game/types';

const short = (t: number, chord?: number): Note => ({ t, lane: 0, chord });

describe('jugement', () => {
  it('applique les tolérances initiales', () => {
    expect(judgeOffset(0.02)).toBe('perfectPlus');
    expect(judgeOffset(-0.025)).toBe('perfectPlus');
    expect(judgeOffset(0.05)).toBe('perfect');
    expect(judgeOffset(-0.07)).toBe('perfect');
    expect(judgeOffset(0.12)).toBe('great');
    expect(judgeOffset(0.3)).toBeNull();
  });
});

describe('combo Perfect+', () => {
  it('multiplie 150 par le rang du combo', () => {
    const s = new ScoreKeeper();
    expect(s.applyShort(short(1), 'perfectPlus').points).toBe(150);
    expect(s.applyShort(short(2), 'perfectPlus').points).toBe(300);
    expect(s.applyShort(short(3), 'perfectPlus').points).toBe(450);
    expect(s.score).toBe(900);
  });
  it('un Perfect ou un Great remet le combo à zéro', () => {
    const s = new ScoreKeeper();
    s.applyShort(short(1), 'perfectPlus');
    s.applyShort(short(2), 'perfectPlus');
    expect(s.applyShort(short(3), 'perfect').points).toBe(100);
    expect(s.combo).toBe(0);
    expect(s.applyShort(short(4), 'perfectPlus').points).toBe(150);
    expect(s.applyShort(short(5), 'great').points).toBe(50);
    expect(s.combo).toBe(0);
    expect(s.maxCombo).toBe(2);
  });
});

describe('tuiles longues', () => {
  it('moyenne des rangs arrondie vers le bas', () => {
    expect(combineLong('perfectPlus', 'perfectPlus')).toBe('perfectPlus');
    expect(combineLong('perfectPlus', 'perfect')).toBe('perfect');
    expect(combineLong('perfectPlus', 'great')).toBe('perfect');
    expect(combineLong('perfect', 'great')).toBe('great');
  });
  it('exemple du document : Perfect+ de deux temps à ×3 = 900', () => {
    const s = new ScoreKeeper();
    s.applyShort(short(1), 'perfectPlus');
    s.applyShort(short(2), 'perfectPlus');
    const ev = s.applyLong({ t: 3, lane: 1, dur: 1, beats: 2 }, 'perfectPlus', 'perfectPlus');
    expect(ev.points).toBe(900);
    expect(s.combo).toBe(3);
  });
  it('relâchement trop tôt = Great court, combo remis à zéro', () => {
    const s = new ScoreKeeper();
    s.applyShort(short(1), 'perfectPlus');
    const ev = s.applyEarlyRelease({ t: 2, lane: 1, dur: 1, beats: 2 });
    expect(ev.points).toBe(50);
    expect(s.combo).toBe(0);
  });
  it('arrondit au point entier le plus proche', () => {
    const s = new ScoreKeeper();
    const ev = s.applyLong({ t: 1, lane: 0, dur: 0.5, beats: 1.5 }, 'perfect', 'perfect');
    expect(ev.points).toBe(150);
  });
});

describe('accords', () => {
  it('exemple du document : combo ×3, deux Perfect+ et un Great = 1400', () => {
    const notes = [short(1), short(2), short(3), short(4, 7), short(4, 7), short(4, 7)];
    const s = new ScoreKeeper(notes);
    s.applyShort(notes[0], 'perfectPlus');
    s.applyShort(notes[1], 'perfectPlus');
    s.applyShort(notes[2], 'perfectPlus');
    const before = s.score;
    // l'ordre des doigts ne doit pas compter : Great en premier
    s.applyShort(notes[5], 'great');
    expect(s.combo).toBe(3);
    s.applyShort(notes[3], 'perfectPlus');
    s.applyShort(notes[4], 'perfectPlus');
    expect(s.score - before).toBe(1400);
    expect(s.combo).toBe(0);
    expect(s.maxCombo).toBe(5);
  });
});
