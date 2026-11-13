import { describe, expect, it } from 'vitest';
import { AutoCalibrator } from '../src/game/calibration';

describe('calibration automatique', () => {
  it('corrige doucement un retard régulier', () => {
    const c = new AutoCalibrator(0);
    let last: number | null = null;
    for (let i = 0; i < 8; i++) last = c.add(0.04);
    expect(last).not.toBeNull();
    expect(c.offsetMs).toBeGreaterThan(0);
    expect(c.offsetMs).toBeLessThanOrEqual(6);
    for (let i = 0; i < 80; i++) c.add(0.04);
    expect(c.offsetMs).toBeGreaterThan(30);
  });
  it('ignore les touches éloignées de la ligne', () => {
    const c = new AutoCalibrator(10);
    for (let i = 0; i < 50; i++) c.add(0.4);
    expect(c.offsetMs).toBe(10);
  });
  it('ne bouge pas si le joueur est déjà juste', () => {
    const c = new AutoCalibrator(0);
    for (let i = 0; i < 40; i++) c.add(i % 2 ? 0.001 : -0.001);
    expect(c.offsetMs).toBe(0);
  });
});
