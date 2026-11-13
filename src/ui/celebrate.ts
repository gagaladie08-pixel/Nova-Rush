import type { ThemeId } from '../game/types';
import { THEMES } from '../game/themes';

/** Brève célébration adaptée au thème (confettis, étincelles ou losanges dorés). */
export function celebrate(host: HTMLElement, theme: ThemeId, reduce: boolean) {
  if (reduce) return;
  const c = document.createElement('canvas');
  c.className = 'celebration';
  host.appendChild(c);
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const w = host.clientWidth;
  const h = host.clientHeight || window.innerHeight;
  c.width = w * dpr;
  c.height = h * dpr;
  const g = c.getContext('2d')!;
  g.scale(dpr, dpr);
  const th = THEMES[theme];
  const colors =
    theme === 'survivor'
      ? ['#E40303', '#FF8C00', '#FFED00', '#008026', '#004DFF', '#750787', '#5BCEFA', '#F5A9B8', '#FFFFFF']
      : th.accents;
  const parts = Array.from({ length: 140 }, () => ({
    x: w / 2 + (Math.random() - 0.5) * 60,
    y: h * 0.35,
    vx: (Math.random() - 0.5) * 12,
    vy: -6 - Math.random() * 9,
    r: Math.random() * Math.PI,
    vr: (Math.random() - 0.5) * 0.3,
    s: 4 + Math.random() * 6,
    c: colors[Math.floor(Math.random() * colors.length)],
  }));
  const start = performance.now();
  const frame = (now: number) => {
    const t = (now - start) / 1000;
    g.clearRect(0, 0, w, h);
    if (theme === 'cyber') {
      // bandes colorées de type glitch
      for (let i = 0; i < 3; i++) {
        g.fillStyle = `${th.accents[i]}${Math.round(Math.max(0, 0.25 - t * 0.1) * 255).toString(16).padStart(2, '0')}`;
        g.fillRect(0, ((t * 300 + i * 140) % (h + 40)) - 20, w, 6);
      }
    }
    for (const p of parts) {
      p.x += p.vx;
      p.y += p.vy;
      p.vy += 0.32;
      p.vx *= 0.99;
      p.r += p.vr;
      g.save();
      g.globalAlpha = Math.max(0, 1 - t / 2.6);
      g.translate(p.x, p.y);
      g.rotate(theme === 'african' ? Math.PI / 4 : p.r);
      g.fillStyle = p.c;
      if (theme === 'cyber') g.fillRect(-p.s / 2, -1, p.s * 1.6, 2);
      else g.fillRect(-p.s / 2, -p.s / 2, p.s, theme === 'survivor' ? p.s * 0.5 : p.s);
      g.restore();
    }
    if (t < 2.8) requestAnimationFrame(frame);
    else c.remove();
  };
  requestAnimationFrame(frame);
}
