import { hashString, rng } from '../analysis/dsp';
import { demoDef } from '../demos/songs';
import type { SongMeta } from '../game/types';

const cache = new Map<string, string>();

/** Pochette : celle du fichier, sinon une pochette générée (démos et pochette par défaut). */
export function coverFor(song: Pick<SongMeta, 'id' | 'cover' | 'title' | 'demo'>): string {
  if (song.cover) return song.cover;
  const key = song.demo ? song.id : 'default';
  let url = cache.get(key);
  if (!url) {
    url = song.demo ? demoCover(song.id) : defaultCover();
    cache.set(key, url);
  }
  return url;
}

function canvas(size = 256) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  return { c, g: c.getContext('2d')! };
}

function defaultCover(): string {
  const { c, g } = canvas();
  const grad = g.createLinearGradient(0, 0, 256, 256);
  grad.addColorStop(0, '#1a1a3a');
  grad.addColorStop(1, '#050510');
  g.fillStyle = grad;
  g.fillRect(0, 0, 256, 256);
  g.strokeStyle = 'rgba(0,255,240,0.5)';
  g.lineWidth = 3;
  for (let r = 30; r < 120; r += 18) {
    g.beginPath();
    g.arc(128, 128, r, 0, Math.PI * 2);
    g.stroke();
  }
  g.fillStyle = '#FF006E';
  g.beginPath();
  g.arc(128, 128, 22, 0, Math.PI * 2);
  g.fill();
  g.fillStyle = '#F0F0FF';
  g.font = 'bold 40px sans-serif';
  g.textAlign = 'center';
  g.textBaseline = 'middle';
  g.fillText('♪', 128, 130);
  return c.toDataURL('image/png');
}

function demoCover(id: string): string {
  const def = demoDef(id)!;
  const [a, b, bg] = def.palette;
  const { c, g } = canvas();
  const rand = rng(hashString(id));
  g.fillStyle = bg;
  g.fillRect(0, 0, 256, 256);
  const grad = g.createRadialGradient(80, 70, 10, 128, 128, 220);
  grad.addColorStop(0, a + 'cc');
  grad.addColorStop(0.55, b + '55');
  grad.addColorStop(1, bg);
  g.fillStyle = grad;
  g.fillRect(0, 0, 256, 256);
  g.globalAlpha = 0.75;
  switch (def.drumStyle) {
    case 'synthwave': {
      g.fillStyle = a;
      g.beginPath();
      g.arc(128, 150, 70, Math.PI, 0);
      g.fill();
      g.fillStyle = bg;
      for (let i = 0; i < 6; i++) g.fillRect(50, 100 + i * 9 + i * i, 160, 3 + i);
      g.strokeStyle = b;
      g.lineWidth = 2;
      for (let i = 0; i < 9; i++) {
        g.beginPath();
        g.moveTo(128, 160);
        g.lineTo(-100 + i * 57, 256);
        g.stroke();
      }
      break;
    }
    case 'afro':
    case 'ethio': {
      for (let i = 0; i < 7; i++) {
        const s = 20 + i * 18;
        g.strokeStyle = i % 2 ? a : b;
        g.lineWidth = 5;
        g.beginPath();
        g.moveTo(128, 128 - s);
        g.lineTo(128 + s, 128);
        g.lineTo(128, 128 + s);
        g.lineTo(128 - s, 128);
        g.closePath();
        g.stroke();
      }
      break;
    }
    case 'vogue': {
      const cols = ['#E40303', '#FF8C00', '#FFED00', '#008026', '#004DFF', '#750787', '#5BCEFA', '#F5A9B8'];
      g.save();
      g.translate(128, 128);
      g.rotate(-0.6);
      cols.forEach((col, i) => {
        g.fillStyle = col;
        g.fillRect(-200, -80 + i * 20, 400, 14);
      });
      g.restore();
      break;
    }
    case 'dnb': {
      for (let i = 0; i < 40; i++) {
        g.fillStyle = i % 2 ? a : b;
        const x = rand() * 256;
        const hgt = 20 + rand() * 120;
        g.fillRect(x, 256 - hgt, 4, hgt);
      }
      g.strokeStyle = a;
      g.lineWidth = 3;
      g.strokeRect(40, 40, 176, 176);
      break;
    }
  }
  g.globalAlpha = 1;
  g.fillStyle = 'rgba(0,0,0,0.45)';
  g.fillRect(0, 196, 256, 60);
  g.fillStyle = '#FFFFFF';
  g.font = 'bold 22px sans-serif';
  g.textAlign = 'left';
  g.fillText(def.title, 14, 226);
  g.font = '14px sans-serif';
  g.fillStyle = 'rgba(255,255,255,0.75)';
  g.fillText(def.style, 14, 246);
  return c.toDataURL('image/png');
}
