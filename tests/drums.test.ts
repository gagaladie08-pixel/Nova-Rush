import { describe, expect, it } from 'vitest';
import { CRASH, HAT, KICK, SNARE, beatAgreement, drumHits, type DrumKind } from '../src/analysis/drums';

const SR = 22050;

/** Batterie synthétique : grosse caisse, caisse claire, charleston, crash. */
export function drumTrack(seconds: number, bpm = 120, seed = 5) {
  let s = seed;
  const rnd = () => ((s = (s * 16807) % 2147483647) / 2147483647) * 2 - 1;
  const x = new Float32Array(SR * seconds);
  const truth: { t: number; k: DrumKind }[] = [];
  const beat = 60 / bpm;
  const add = (t: number, k: DrumKind) => {
    truth.push({ t, k });
    const a = Math.round(t * SR);
    if (k === KICK) {
      for (let i = 0; i < SR * 0.25 && a + i < x.length; i++) {
        const f = 50 + 90 * Math.exp(-i / 400);
        x[a + i] += 0.8 * Math.exp(-i / (SR * 0.07)) * Math.sin((2 * Math.PI * f * i) / SR);
      }
    } else if (k === SNARE) {
      // Bruit passe-bande (≈ 200 Hz – 5 kHz) + corps à 190 Hz.
      let fast = 0;
      let slow = 0;
      for (let i = 0; i < SR * 0.18 && a + i < x.length; i++) {
        const r = rnd();
        fast += 0.6 * (r - fast);
        slow += 0.05 * (r - slow);
        x[a + i] += Math.exp(-i / (SR * 0.05)) * (0.6 * (fast - slow) + 0.25 * Math.sin((2 * Math.PI * 190 * i) / SR));
      }
    } else {
      // Aigu : bruit passe-haut (différence seconde) ; crash = longue décroissance.
      const len = k === CRASH ? 1.6 : 0.05;
      let p = 0;
      let pp = 0;
      for (let i = 0; i < SR * len && a + i < x.length; i++) {
        const r = rnd();
        x[a + i] += (k === CRASH ? 0.2 : 0.1) * Math.exp(-i / (SR * (k === CRASH ? 0.6 : 0.012))) * (r - 2 * p + pp);
        pp = p;
        p = r;
      }
    }
  };
  for (let b = 0; b * beat < seconds - 2; b++) {
    const t = 0.5 + b * beat;
    if (b % 2 === 0) add(t, KICK);
    else add(t, SNARE);
    if (b % 16 === 0) add(t, CRASH);
    else add(t + 0.0, HAT);
    if (b % 4 === 3) add(t + beat / 2, KICK);
    add(t + beat / 2, HAT);
  }
  return { x, truth: truth.sort((a, b) => a.t - b.t), beat };
}

describe('coups de batterie', () => {
  const { x, truth, beat } = drumTrack(24);
  const hits = drumHits(x, SR);

  it('reconnaît grosse caisse, caisse claire, charleston et crash', () => {
    const rows: string[] = [];
    for (const k of [KICK, SNARE, HAT, CRASH] as DrumKind[]) {
      // Un charleston joué avec la caisse claire est couvert par son bruit : non exigé.
      const want = truth.filter((h) => h.k === k && !(k === HAT && truth.some((o) => o.k === SNARE && Math.abs(o.t - h.t) < 0.04)));
      const got = hits.filter((h) => h.k === k);
      const found = want.filter((w) => got.some((g) => Math.abs(g.t - w.t) < 0.04)).length;
      const right = got.filter((g) => truth.some((w) => w.k === k && Math.abs(g.t - w.t) < 0.04)).length;
      rows.push(`${['grosse caisse', 'caisse claire', 'charleston', 'crash'][k]} ${found}/${want.length} trouvés, ${right}/${got.length} justes`);
      expect(found / want.length, `rappel ${k}`).toBeGreaterThan(k === HAT ? 0.75 : 0.9);
      expect(right / Math.max(1, got.length), `précision ${k}`).toBeGreaterThan(0.85);
    }
    console.log(rows.join(' | '));
  });

  it('mesure l’accord avec la grille des temps', () => {
    const beats: number[] = [];
    for (let t = 0.5; t < 22; t += beat) beats.push(t);
    expect(beatAgreement(hits, beats)).toBeGreaterThan(0.9);
    // Grille décalée d'un quart de temps : désaccord.
    expect(beatAgreement(hits, beats.map((t) => t + beat / 4))).toBeLessThan(0.3);
  });
});

describe('batterie absente', () => {
  it('ne trouve aucun coup dans le bruit d’une piste batterie vide', () => {
    let s = 9;
    const x = new Float32Array(SR * 20);
    // Résidu de séparation à −65 dB, avec de petites bouffées.
    for (let i = 0; i < x.length; i++) x[i] = ((s = (s * 16807) % 2147483647) / 2147483647 - 0.5) * 0.001 * (i % 11025 < 800 ? 3 : 1);
    expect(drumHits(x, SR)).toEqual([]);
  });

  it('ignore les coups très faibles entre les vrais coups', () => {
    const { x, truth } = drumTrack(12);
    // Petites bouffées à −40 dB entre les temps.
    for (let t = 0.62; t < 11; t += 0.5) for (let i = 0; i < 200; i++) x[Math.round(t * SR) + i] += 0.004 * Math.sin(i * 0.4);
    const hits = drumHits(x, SR);
    const stray = hits.filter((h) => !truth.some((w) => Math.abs(w.t - h.t) < 0.04));
    expect(stray.length).toBeLessThanOrEqual(1);
  });
});
