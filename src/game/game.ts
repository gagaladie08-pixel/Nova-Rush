import { audio } from '../audio/engine';
import { LEVEL_PARAMS } from '../analysis/difficulty';
import { JUDGE_LABEL, ScoreKeeper, WINDOWS, judgeOffset, type Judge, type ScoreEvent } from './scoring';
import { THEMES, type GameTheme } from './themes';
import type { Note, SongMeta, ThemeId } from './types';

export interface GameResult {
  score: number;
  maxCombo: number;
  /** Progression atteinte dans le morceau (0..1). */
  progress: number;
  completed: boolean;
  failed: boolean;
  quit: boolean;
  counts: Record<Judge, number>;
}

export interface GameOptions {
  song: SongMeta;
  buffer: AudioBuffer;
  theme: ThemeId;
  reduceMotion: boolean;
  offsetMs: number;
  onEnd: (r: GameResult) => void;
  onPauseChange?: (paused: boolean) => void;
}

interface NoteState extends Note {
  idx: number;
  judged: boolean;
  holdPointer?: number;
  headJudge?: Judge;
  hitAt?: number;
  result?: Judge | 'miss' | 'early';
}

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  life: number;
  color: string;
  size: number;
}

type State = 'countdown' | 'playing' | 'paused' | 'resuming' | 'failed' | 'done';

const COUNTDOWN = 3;

export class Game {
  private root: HTMLElement;
  private canvas: HTMLCanvasElement;
  private g: CanvasRenderingContext2D;
  private theme: GameTheme;
  private notes: NoteState[];
  private laneQueues: NoteState[][] = [[], [], [], []];
  private laneHead = [0, 0, 0, 0];
  private keeper: ScoreKeeper;
  private state: State = 'countdown';
  private startCtx = 0;
  private playOffset = 0;
  private pausedAt = 0;
  private frozenAt = 0;
  private raf = 0;
  private dpr = 1;
  private w = 0;
  private h = 0;
  private fieldX = 0;
  private fieldW = 0;
  private laneW = 0;
  private lineY = 0;
  private pxPerSec = 0;
  private tileH = 0;
  private travel: number;
  private duration: number;
  private bg: HTMLCanvasElement | null = null;
  private lineSprite: HTMLCanvasElement | null = null;
  private sprites = new Map<string, { c: HTMLCanvasElement; pad: number }>();
  private lanePress = [0, 0, 0, 0];
  private laneFlash = [0, 0, 0, 0];
  private pointerLane = new Map<number, number>();
  private particles: Particle[] = [];
  private rings: { x: number; y: number; t: number; color: string }[] = [];
  private maxDur = 0;
  private hud: {
    score: HTMLElement;
    combo: HTMLElement;
    mult: HTMLElement;
    stars: HTMLElement[];
    judge: HTMLElement;
    countdown: HTMLElement;
    pauseBtn: HTMLButtonElement;
    overlay: HTMLElement;
  };
  private lastScore = -1;
  private lastCombo = -1;
  private lastStars = -1;
  private failNote: NoteState | null = null;
  private ended = false;
  private disposers: (() => void)[] = [];
  private judgeTimer = 0;

  constructor(root: HTMLElement, private opts: GameOptions) {
    this.root = root;
    this.theme = THEMES[opts.theme];
    this.travel = LEVEL_PARAMS[opts.song.level].travel;
    this.duration = opts.buffer.duration;
    this.notes = opts.song.chart.notes.map((n, idx) => ({ ...n, idx, judged: false }));
    for (const n of this.notes) {
      this.laneQueues[n.lane].push(n);
      this.maxDur = Math.max(this.maxDur, n.dur ?? 0);
    }
    this.keeper = new ScoreKeeper(this.notes);

    root.innerHTML = `
      <div class="game" data-game-theme="${opts.theme}">
        <canvas class="game-canvas"></canvas>
        <div class="game-hud">
          <div class="hud-top">
            <div class="hud-score"><span class="hud-label">Score</span><span class="hud-score-value">0</span></div>
            <div class="hud-stars" aria-label="Progression">${'<span class="star">★</span>'.repeat(5)}</div>
            <button class="hud-pause" aria-label="Pause">❚❚</button>
          </div>
          <div class="hud-combo"><span class="hud-combo-value"></span><span class="hud-mult"></span></div>
          <div class="hud-judge"></div>
          <div class="hud-countdown"></div>
        </div>
        <div class="game-overlay hidden"></div>
      </div>`;
    this.canvas = root.querySelector('.game-canvas')!;
    this.g = this.canvas.getContext('2d', { alpha: false })!;
    this.hud = {
      score: root.querySelector('.hud-score-value')!,
      combo: root.querySelector('.hud-combo-value')!,
      mult: root.querySelector('.hud-mult')!,
      stars: Array.from(root.querySelectorAll('.star')),
      judge: root.querySelector('.hud-judge')!,
      countdown: root.querySelector('.hud-countdown')!,
      pauseBtn: root.querySelector('.hud-pause')!,
      overlay: root.querySelector('.game-overlay')!,
    };

    this.resize();
    const onResize = () => this.resize();
    window.addEventListener('resize', onResize);
    this.disposers.push(() => window.removeEventListener('resize', onResize));

    const down = (e: PointerEvent) => this.onDown(e);
    const up = (e: PointerEvent) => this.onUp(e);
    this.canvas.addEventListener('pointerdown', down);
    window.addEventListener('pointerup', up);
    window.addEventListener('pointercancel', up);
    this.disposers.push(() => {
      this.canvas.removeEventListener('pointerdown', down);
      window.removeEventListener('pointerup', up);
      window.removeEventListener('pointercancel', up);
    });
    this.hud.pauseBtn.addEventListener('click', () => this.pause());
    const vis = () => {
      if (document.hidden) this.pause();
    };
    document.addEventListener('visibilitychange', vis);
    this.disposers.push(() => document.removeEventListener('visibilitychange', vis));

    if (import.meta.env.DEV) (window as unknown as { __novaGame?: Game }).__novaGame = this;
    this.start();
  }

  /* --------------------------- Horloge --------------------------- */

  songTime(perfMs = performance.now()): number {
    return audio.heardCtxTime(perfMs) - this.startCtx + this.playOffset - this.opts.offsetMs / 1000;
  }

  private start() {
    audio.resetClock();
    this.playOffset = 0;
    this.frozenAt = 0;
    this.startCtx = audio.play(this.opts.song, this.opts.buffer, 0, COUNTDOWN);
    this.state = 'countdown';
    this.loop();
  }

  /* --------------------------- Pause ----------------------------- */

  pause() {
    if (this.state !== 'playing' && this.state !== 'countdown' && this.state !== 'resuming') return;
    const now = this.state === 'playing' ? this.songTime() : this.frozenAt;
    this.pausedAt = Math.max(0, now);
    audio.stop();
    // Une tenue en cours au moment de la pause est validée selon son toucher initial.
    for (const n of this.notes) {
      if (n.holdPointer !== undefined && !n.judged && n.headJudge) {
        this.applyEvent(n, this.keeper.applyLong(n, n.headJudge, n.headJudge));
        n.judged = true;
        n.result = n.headJudge;
        n.holdPointer = undefined;
      }
    }
    this.pointerLane.clear();
    this.state = 'paused';
    this.hud.countdown.textContent = '';
    this.opts.onPauseChange?.(true);
    this.showOverlay(`
      <div class="overlay-card">
        <h2>Pause</h2>
        <button class="btn btn-primary" data-act="resume">Reprendre</button>
        <button class="btn" data-act="restart">Recommencer</button>
        <button class="btn btn-ghost" data-act="quit">Quitter la partie</button>
      </div>`);
  }

  resume() {
    if (this.state !== 'paused') return;
    this.hideOverlay();
    this.opts.onPauseChange?.(false);
    audio.resetClock();
    this.frozenAt = this.pausedAt;
    // Reprise au même instant après un compte à rebours ; les positions des
    // tuiles sont recalculées à partir de l'horloge audio.
    const bufferPos = Math.max(0, this.pausedAt + this.opts.offsetMs / 1000);
    this.startCtx = audio.play(this.opts.song, this.opts.buffer, bufferPos, COUNTDOWN);
    this.playOffset = bufferPos;
    this.state = 'resuming';
  }

  restart() {
    this.hideOverlay();
    this.notes.forEach((n) => {
      n.judged = false;
      n.holdPointer = undefined;
      n.headJudge = undefined;
      n.result = undefined;
      n.hitAt = undefined;
    });
    this.laneHead = [0, 0, 0, 0];
    this.keeper = new ScoreKeeper(this.notes);
    this.particles = [];
    this.rings = [];
    this.failNote = null;
    this.lastScore = this.lastCombo = this.lastStars = -1;
    cancelAnimationFrame(this.raf);
    this.start();
  }

  /** Appui sur le bouton retour d'Android. */
  back() {
    if (this.state === 'paused') this.resume();
    else this.pause();
  }

  private quit() {
    this.hideOverlay();
    this.finish({ failed: false, completed: false, quit: true }, this.pausedAt);
  }

  private showOverlay(html: string) {
    const o = this.hud.overlay;
    o.innerHTML = html;
    o.classList.remove('hidden');
    o.querySelectorAll<HTMLButtonElement>('[data-act]').forEach((b) =>
      b.addEventListener('click', () => {
        const act = b.dataset.act;
        if (act === 'resume') this.resume();
        else if (act === 'restart') this.restart();
        else if (act === 'quit') this.quit();
      }),
    );
  }

  private hideOverlay() {
    this.hud.overlay.classList.add('hidden');
    this.hud.overlay.innerHTML = '';
  }

  /* --------------------------- Entrées --------------------------- */

  private laneAt(clientX: number): number {
    const rect = this.canvas.getBoundingClientRect();
    const x = clientX - rect.left - this.fieldX;
    if (x < 0 || x >= this.fieldW) return -1;
    return Math.min(3, Math.floor(x / this.laneW));
  }

  private onDown(e: PointerEvent) {
    e.preventDefault();
    if (this.state !== 'playing') return;
    const lane = this.laneAt(e.clientX);
    if (lane < 0) return;
    this.pointerLane.set(e.pointerId, lane);
    this.lanePress[lane] = 1;
    const t = this.songTime(e.timeStamp || performance.now());
    const q = this.laneQueues[lane];
    let target: NoteState | undefined;
    for (let i = this.laneHead[lane]; i < q.length; i++) {
      const n = q[i];
      if (n.judged || n.holdPointer !== undefined) continue;
      if (n.t - t > WINDOWS.great) break;
      if (Math.abs(n.t - t) <= WINDOWS.great) {
        target = n;
        break;
      }
    }
    if (!target) return;
    const judge = judgeOffset(t - target.t)!;
    if (target.dur) {
      target.holdPointer = e.pointerId;
      target.headJudge = judge;
      target.hitAt = t;
      this.showJudge(judge, false);
      this.burst(target.lane, judge, 6);
    } else {
      target.judged = true;
      target.result = judge;
      target.hitAt = t;
      this.applyEvent(target, this.keeper.applyShort(target, judge));
    }
  }

  private onUp(e: PointerEvent) {
    const lane = this.pointerLane.get(e.pointerId);
    if (lane !== undefined) this.pointerLane.delete(e.pointerId);
    if (this.state !== 'playing') return;
    const n = this.notes.find((x) => x.holdPointer === e.pointerId && !x.judged);
    if (!n || !n.headJudge || !n.dur) return;
    const t = this.songTime(e.timeStamp || performance.now());
    this.releaseHold(n, t);
  }

  private releaseHold(n: NoteState, t: number) {
    const end = n.t + (n.dur ?? 0);
    n.holdPointer = undefined;
    n.judged = true;
    if (t < end - WINDOWS.great) {
      n.result = 'early';
      this.applyEvent(n, this.keeper.applyEarlyRelease(n));
    } else {
      const rel = judgeOffset(t - end) ?? 'great';
      const ev = this.keeper.applyLong(n, n.headJudge!, rel);
      n.result = ev.judge;
      this.applyEvent(n, ev);
    }
  }

  private applyEvent(n: Note, ev: ScoreEvent) {
    this.showJudge(ev.judge, !!ev.earlyRelease, ev.points);
    this.laneFlash[n.lane] = 1;
    this.burst(n.lane, ev.judge, ev.judge === 'perfectPlus' ? 14 : 8);
    if (navigator.vibrate && ev.judge === 'perfectPlus' && !this.opts.reduceMotion) {
      try {
        navigator.vibrate(8);
      } catch {
        /* non pris en charge */
      }
    }
  }

  private showJudge(j: Judge | 'miss', early: boolean, points?: number) {
    const el = this.hud.judge;
    const label = j === 'miss' ? 'Raté' : early ? 'Relâché trop tôt · Great' : JUDGE_LABEL[j];
    el.innerHTML = `<span class="judge-label">${label}</span>${points ? `<span class="judge-points">+${points}</span>` : ''}`;
    el.style.color = this.theme.judge[j];
    el.classList.remove('pop');
    void el.offsetWidth;
    el.classList.add('pop');
    window.clearTimeout(this.judgeTimer);
    this.judgeTimer = window.setTimeout(() => el.classList.remove('pop'), 650);
  }

  /* --------------------------- Boucle ---------------------------- */

  private loop = () => {
    this.raf = requestAnimationFrame(this.loop);
    const raw = this.state === 'paused' ? this.pausedAt : this.songTime();
    let now = raw;

    if (this.state === 'countdown' || this.state === 'resuming') {
      // Les tuiles restent figées pendant le compte à rebours.
      now = this.frozenAt;
      const remaining = this.frozenAt - raw;
      const n = Math.ceil(remaining);
      this.hud.countdown.textContent = remaining > 0 && n <= COUNTDOWN ? String(n) : '';
      if (raw >= this.frozenAt) {
        this.state = 'playing';
        this.hud.countdown.textContent = '';
        now = raw;
      }
    } else if (this.state === 'failed' || this.state === 'done') {
      now = this.frozenAt;
    }

    if (this.state === 'playing') this.update(now);
    this.render(now, raw);
    this.updateHud(now);
  };

  private update(now: number) {
    // Tenues : relâchement automatique au-delà de la fenêtre de fin.
    for (const n of this.notes) {
      if (n.holdPointer !== undefined && !n.judged && n.dur && now > n.t + n.dur + WINDOWS.great) {
        this.releaseHold(n, now);
      }
    }
    // Ratés : une tuile disparaît sans toucher valide → fin de partie.
    for (let l = 0; l < 4; l++) {
      const q = this.laneQueues[l];
      while (this.laneHead[l] < q.length && q[this.laneHead[l]].judged) this.laneHead[l]++;
      for (let i = this.laneHead[l]; i < q.length; i++) {
        const n = q[i];
        if (n.t > now) break;
        if (!n.judged && n.holdPointer === undefined && now - n.t > WINDOWS.great) {
          this.fail(n, now);
          return;
        }
      }
    }
    if (now >= this.duration - 0.02 || (!audio.isPlaying(this.opts.song.id) && now > this.duration - 0.5)) {
      this.complete();
    }
  }

  private fail(n: NoteState, now: number) {
    this.state = 'failed';
    this.frozenAt = now;
    this.failNote = n;
    n.result = 'miss';
    this.showJudge('miss', false);
    // La musique continue en arrière-plan.
    window.setTimeout(() => this.finish({ failed: true, completed: false, quit: false }, now), 1100);
  }

  private complete() {
    this.state = 'done';
    this.frozenAt = this.duration;
    this.finish({ failed: false, completed: true, quit: false }, this.duration);
  }

  private finish(flags: { failed: boolean; completed: boolean; quit: boolean }, at: number) {
    if (this.ended) return;
    this.ended = true;
    const progress = flags.completed ? 1 : Math.max(0, Math.min(1, at / this.duration));
    this.opts.onEnd({
      score: this.keeper.score,
      maxCombo: this.keeper.maxCombo,
      progress,
      counts: { ...this.keeper.counts },
      ...flags,
    });
  }

  private updateHud(now: number) {
    const k = this.keeper;
    if (k.score !== this.lastScore) {
      this.lastScore = k.score;
      this.hud.score.textContent = k.score.toLocaleString('fr-FR');
    }
    if (k.combo !== this.lastCombo) {
      this.lastCombo = k.combo;
      this.hud.combo.textContent = k.combo > 0 ? `Combo ${k.combo}` : '';
      this.hud.mult.textContent = k.combo > 0 ? `Perfect+ suivant ×${k.nextMultiplier}` : '';
      if (k.combo > 0 && !this.opts.reduceMotion) {
        this.hud.combo.classList.remove('bump');
        void this.hud.combo.offsetWidth;
        this.hud.combo.classList.add('bump');
      }
    }
    // Cinq étoiles : une par cinquième de la durée (temps écoulé, pas performance).
    const stars = this.state === 'done' ? 5 : Math.max(0, Math.min(5, Math.floor((now / this.duration) * 5)));
    if (stars !== this.lastStars) {
      this.lastStars = stars;
      this.hud.stars.forEach((s, i) => s.classList.toggle('on', i < stars));
    }
  }

  /* --------------------------- Rendu ----------------------------- */

  private resize() {
    const rect = this.root.getBoundingClientRect();
    this.dpr = Math.min(2.5, window.devicePixelRatio || 1);
    this.w = Math.max(1, rect.width);
    this.h = Math.max(1, rect.height);
    this.canvas.width = Math.round(this.w * this.dpr);
    this.canvas.height = Math.round(this.h * this.dpr);
    this.canvas.style.width = `${this.w}px`;
    this.canvas.style.height = `${this.h}px`;
    this.fieldW = Math.min(this.w, this.h * 0.62);
    this.fieldX = (this.w - this.fieldW) / 2;
    this.laneW = this.fieldW / 4;
    this.lineY = this.h * 0.8;
    this.pxPerSec = this.lineY / this.travel;
    const minGap = LEVEL_PARAMS[this.opts.song.level].minGap;
    this.tileH = Math.max(22, Math.min(this.laneW * 0.55, this.pxPerSec * minGap * 0.85));
    this.bg = this.makeBackground();
    this.sprites.clear();
    this.lineSprite = this.makeLineSprite();
  }

  /** Ligne de jugement pré-rendue (le flou d'ombre est coûteux à chaque image). */
  private makeLineSprite(): HTMLCanvasElement {
    const pad = 24;
    const c = document.createElement('canvas');
    c.width = Math.ceil((this.fieldW + pad * 2) * this.dpr);
    c.height = Math.ceil((4 + pad * 2) * this.dpr);
    const g = c.getContext('2d')!;
    g.scale(this.dpr, this.dpr);
    if (!this.opts.reduceMotion) {
      g.shadowColor = this.theme.line;
      g.shadowBlur = this.theme.glow * 1.5;
    }
    g.fillStyle = this.theme.line;
    g.fillRect(pad, pad, this.fieldW, 4);
    return c;
  }

  private makeBackground(): HTMLCanvasElement {
    const c = document.createElement('canvas');
    c.width = this.canvas.width;
    c.height = this.canvas.height;
    const g = c.getContext('2d')!;
    g.scale(this.dpr, this.dpr);
    const { w, h } = this;
    const th = this.theme;
    const grad = g.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, th.bg);
    grad.addColorStop(1, th.surface);
    g.fillStyle = grad;
    g.fillRect(0, 0, w, h);

    if (th.id === 'cyber') {
      // Grille en perspective discrète
      g.strokeStyle = 'rgba(189,0,255,0.18)';
      g.lineWidth = 1;
      const horizon = h * 0.35;
      for (let i = -12; i <= 12; i++) {
        g.beginPath();
        g.moveTo(w / 2 + i * 8, horizon);
        g.lineTo(w / 2 + i * w * 0.18, h);
        g.stroke();
      }
      for (let k = 1; k < 14; k++) {
        const y = horizon + Math.pow(k / 14, 2) * (h - horizon);
        g.strokeStyle = `rgba(0,180,255,${0.05 + 0.12 * (k / 14)})`;
        g.beginPath();
        g.moveTo(0, y);
        g.lineTo(w, y);
        g.stroke();
      }
      g.fillStyle = 'rgba(255,255,255,0.025)';
      for (let y = 0; y < h; y += 3) g.fillRect(0, y, w, 1);
    } else if (th.id === 'survivor') {
      // Bandes diagonales inspirées de plusieurs drapeaux
      const flags = [
        ['#E40303', '#FF8C00', '#FFED00', '#008026', '#004DFF', '#750787'],
        ['#5BCEFA', '#F5A9B8', '#FFFFFF', '#F5A9B8', '#5BCEFA'],
        ['#D60270', '#9B4F96', '#0038A8'],
        ['#FF218C', '#FFD800', '#21B1FF'],
      ];
      g.save();
      g.globalAlpha = 0.11;
      g.translate(w / 2, h / 2);
      g.rotate(-Math.PI / 5);
      let y = -h;
      let fi = 0;
      while (y < h) {
        const flag = flags[fi++ % flags.length];
        for (const col of flag) {
          g.fillStyle = col;
          g.fillRect(-w * 1.5, y, w * 3, 9);
          y += 9;
        }
        y += 70;
      }
      g.restore();
    } else {
      // Losanges emboîtés
      const s = 34;
      g.lineWidth = 1;
      for (let y = -s; y < h + s; y += s) {
        for (let x = -s; x < w + s; x += s) {
          const ox = (Math.floor(y / s) % 2) * (s / 2);
          g.strokeStyle = 'rgba(212,160,23,0.09)';
          g.beginPath();
          g.moveTo(x + ox, y - s / 2);
          g.lineTo(x + ox + s / 2, y);
          g.lineTo(x + ox, y + s / 2);
          g.lineTo(x + ox - s / 2, y);
          g.closePath();
          g.stroke();
          g.strokeStyle = 'rgba(196,98,45,0.08)';
          g.beginPath();
          g.moveTo(x + ox, y - s / 4);
          g.lineTo(x + ox + s / 4, y);
          g.lineTo(x + ox, y + s / 4);
          g.lineTo(x + ox - s / 4, y);
          g.closePath();
          g.stroke();
        }
      }
    }
    // Voies
    g.fillStyle = 'rgba(0,0,0,0.45)';
    g.fillRect(this.fieldX, 0, this.fieldW, h);
    for (let l = 0; l <= 4; l++) {
      const x = this.fieldX + l * this.laneW;
      g.strokeStyle = l === 0 || l === 4 ? th.accents[1] + '88' : 'rgba(255,255,255,0.12)';
      g.lineWidth = l === 0 || l === 4 ? 2 : 1;
      g.beginPath();
      g.moveTo(x, 0);
      g.lineTo(x, h);
      g.stroke();
    }
    return c;
  }

  private yOf(t: number, now: number) {
    return this.lineY - (t - now) * this.pxPerSec;
  }

  private render(now: number, raw: number) {
    const g = this.g;
    const th = this.theme;
    const reduce = this.opts.reduceMotion;
    g.setTransform(1, 0, 0, 1, 0, 0);
    if (this.bg) g.drawImage(this.bg, 0, 0);
    g.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    const wall = performance.now() / 1000;

    if (!reduce) this.drawAmbient(g, wall);

    // Appuis sur les voies
    for (let l = 0; l < 4; l++) {
      const held = [...this.pointerLane.values()].includes(l);
      if (held) this.lanePress[l] = Math.max(this.lanePress[l], 0.6);
      const a = Math.max(this.lanePress[l] * 0.35, this.laneFlash[l] * 0.5);
      if (a > 0.01) {
        const x = this.fieldX + l * this.laneW;
        const grad = g.createLinearGradient(0, this.lineY, 0, this.lineY - this.h * 0.45);
        grad.addColorStop(0, this.alpha(th.laneColors[l], a));
        grad.addColorStop(1, this.alpha(th.laneColors[l], 0));
        g.fillStyle = grad;
        g.fillRect(x, 0, this.laneW, this.lineY + 20);
      }
      if (!held) this.lanePress[l] *= 0.85;
      this.laneFlash[l] *= 0.88;
    }

    // Ligne de jugement
    if (this.lineSprite) {
      g.drawImage(this.lineSprite, this.fieldX - 24, this.lineY - 2 - 24, this.fieldW + 48, 4 + 48);
    }
    g.fillStyle = 'rgba(255,255,255,0.06)';
    g.fillRect(this.fieldX, this.lineY + 2, this.fieldW, this.h - this.lineY);

    // Tuiles
    const tMin = now - 0.4 - this.maxDur;
    const tMax = now + this.travel * 1.08;
    const chordsDrawn = new Set<number>();
    for (const n of this.notes) {
      if (n.t < tMin) continue;
      if (n.t > tMax) break;
      this.drawNote(g, n, now, chordsDrawn);
    }

    // Anneaux et particules
    for (let i = this.rings.length - 1; i >= 0; i--) {
      const r = this.rings[i];
      const age = wall - r.t;
      if (age > 0.35) {
        this.rings.splice(i, 1);
        continue;
      }
      g.strokeStyle = this.alpha(r.color, 1 - age / 0.35);
      g.lineWidth = 3;
      g.beginPath();
      g.ellipse(r.x, r.y, this.laneW * (0.3 + age * 1.6), this.laneW * (0.12 + age * 0.6), 0, 0, Math.PI * 2);
      g.stroke();
    }
    for (let i = this.particles.length - 1; i >= 0; i--) {
      const p = this.particles[i];
      p.life -= 1 / 60;
      if (p.life <= 0) {
        this.particles.splice(i, 1);
        continue;
      }
      p.x += p.vx;
      p.y += p.vy;
      p.vy += 0.18;
      g.fillStyle = this.alpha(p.color, Math.min(1, p.life * 2));
      if (th.id === 'african') {
        g.save();
        g.translate(p.x, p.y);
        g.rotate(Math.PI / 4);
        g.fillRect(-p.size / 2, -p.size / 2, p.size, p.size);
        g.restore();
      } else {
        g.fillRect(p.x, p.y, p.size, p.size);
      }
    }

    if (this.state === 'failed') {
      const k = Math.min(1, (raw - this.frozenAt) * 2);
      g.fillStyle = `rgba(0,0,0,${0.45 * Math.max(0, k)})`;
      g.fillRect(0, 0, this.w, this.h);
    }
  }

  private drawAmbient(g: CanvasRenderingContext2D, wall: number) {
    const th = this.theme;
    if (th.id === 'cyber') {
      const y = ((wall * 0.25) % 1) * this.h * 1.2 - this.h * 0.1;
      const grad = g.createLinearGradient(0, y - 40, 0, y + 40);
      grad.addColorStop(0, 'rgba(0,255,240,0)');
      grad.addColorStop(0.5, 'rgba(0,255,240,0.07)');
      grad.addColorStop(1, 'rgba(0,255,240,0)');
      g.fillStyle = grad;
      g.fillRect(0, y - 40, this.w, 80);
    } else if (th.id === 'survivor') {
      const a = 0.08 + 0.05 * Math.sin(wall * 1.6);
      for (const [x, col] of [
        [this.fieldX, '#F77FB4'],
        [this.fieldX + this.fieldW, '#5BCEFA'],
      ] as [number, string][]) {
        const grad = g.createRadialGradient(x, this.lineY, 0, x, this.lineY, this.h * 0.5);
        grad.addColorStop(0, this.alpha(col, a));
        grad.addColorStop(1, this.alpha(col, 0));
        g.fillStyle = grad;
        g.fillRect(0, 0, this.w, this.h);
      }
    } else {
      const a = 0.06 + 0.04 * Math.sin(wall * 1.2);
      g.strokeStyle = `rgba(212,160,23,${a})`;
      g.lineWidth = 2;
      const cx = this.w / 2;
      const cy = this.h * 0.35;
      for (let k = 1; k <= 3; k++) {
        const s = k * 50 + ((wall * 10) % 50);
        g.beginPath();
        g.moveTo(cx, cy - s);
        g.lineTo(cx + s, cy);
        g.lineTo(cx, cy + s);
        g.lineTo(cx - s, cy);
        g.closePath();
        g.stroke();
      }
    }
  }

  private drawNote(g: CanvasRenderingContext2D, n: NoteState, now: number, chordsDrawn: Set<number>) {
    const th = this.theme;
    const x = this.fieldX + n.lane * this.laneW + 4;
    const w = this.laneW - 8;
    const color = th.laneColors[n.lane];
    const isFail = this.failNote === n;

    if (n.dur) {
      const end = n.t + n.dur;
      if (n.judged && n.result !== 'early') return;
      const holding = n.holdPointer !== undefined;
      const headT = holding ? Math.max(now, n.t) : n.t;
      if (n.judged && n.result === 'early' && now > (n.hitAt ?? n.t) + 0.3) return;
      const yHead = this.yOf(headT, now);
      const yTail = this.yOf(end, now);
      if (yHead < -20 || yTail > this.h + 20) return;
      const bodyW = w * 0.62;
      const grad = g.createLinearGradient(0, yTail, 0, yHead);
      const c2 = th.id === 'african' ? '#2D5A27' : th.accents[1];
      grad.addColorStop(0, this.alpha(c2, holding ? 0.95 : 0.7));
      grad.addColorStop(1, this.alpha(color, holding ? 0.95 : 0.7));
      if (holding && !this.opts.reduceMotion) {
        // halo simple (sans shadowBlur)
        g.fillStyle = this.alpha(color, 0.18);
        g.fillRect(x + (w - bodyW) / 2 - 6, yTail, bodyW + 12, Math.max(0, yHead - yTail));
      }
      g.fillStyle = grad;
      this.roundRect(g, x + (w - bodyW) / 2, yTail, bodyW, Math.max(0, yHead - yTail), Math.min(th.tileRadius, bodyW / 2));
      g.fill();
      // tête et queue
      this.drawTile(g, x, yHead - this.tileH / 2, w, this.tileH, isFail ? th.judge.miss : color, holding);
      g.fillStyle = this.alpha('#FFFFFF', 0.6);
      g.fillRect(x + w * 0.2, yTail - 2, w * 0.6, 4);
      if (holding && !this.opts.reduceMotion && Math.random() < 0.5) this.spark(n.lane, color, 1);
      return;
    }

    if (n.judged && !isFail) return;
    const y = this.yOf(n.t, now);
    if (y < -this.tileH || y > this.h + this.tileH) return;
    if (n.chord !== undefined && !chordsDrawn.has(n.chord)) {
      chordsDrawn.add(n.chord);
      const lanes = this.notes.filter((m) => m.chord === n.chord && !m.judged).map((m) => m.lane);
      if (lanes.length > 1) {
        const x1 = this.fieldX + (Math.min(...lanes) + 0.5) * this.laneW;
        const x2 = this.fieldX + (Math.max(...lanes) + 0.5) * this.laneW;
        g.fillStyle = this.alpha(th.text, 0.35);
        g.fillRect(x1, y - 2, x2 - x1, 4);
      }
    }
    this.drawTile(g, x, y - this.tileH / 2, w, this.tileH, isFail ? th.judge.miss : color, false, n.chord !== undefined);
  }

  private drawTile(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, color: string, active: boolean, chord = false) {
    const key = `${color}|${active}|${chord}`;
    let sp = this.sprites.get(key);
    if (!sp) {
      const pad = this.theme.glow * 2 + 4;
      const c = document.createElement('canvas');
      c.width = Math.ceil((w + pad * 2) * this.dpr);
      c.height = Math.ceil((h + pad * 2) * this.dpr);
      const sg = c.getContext('2d')!;
      sg.scale(this.dpr, this.dpr);
      this.paintTile(sg, pad, pad, w, h, color, active, chord);
      sp = { c, pad };
      this.sprites.set(key, sp);
    }
    g.drawImage(sp.c, x - sp.pad, y - sp.pad, w + sp.pad * 2, h + sp.pad * 2);
  }

  private paintTile(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, color: string, active: boolean, chord: boolean) {
    const th = this.theme;
    g.save();
    if (!this.opts.reduceMotion) {
      g.shadowColor = color;
      g.shadowBlur = th.glow * (active ? 2 : 1);
    }
    if (th.id === 'cyber') {
      const c = Math.min(10, h / 3);
      g.beginPath();
      g.moveTo(x + c, y);
      g.lineTo(x + w, y);
      g.lineTo(x + w, y + h - c);
      g.lineTo(x + w - c, y + h);
      g.lineTo(x, y + h);
      g.lineTo(x, y + c);
      g.closePath();
      const grad = g.createLinearGradient(x, y, x + w, y + h);
      grad.addColorStop(0, color);
      grad.addColorStop(1, '#2A0050');
      g.fillStyle = grad;
      g.fill();
      g.shadowBlur = 0;
      g.strokeStyle = '#00FFF0';
      g.lineWidth = 1.5;
      g.stroke();
      g.fillStyle = 'rgba(255,215,120,0.85)'; // petit reflet doré
      g.fillRect(x + c + 3, y + 3, Math.min(14, w * 0.2), 2);
    } else if (th.id === 'survivor') {
      const grad = g.createLinearGradient(x, y, x, y + h);
      grad.addColorStop(0, '#FFFFFF');
      grad.addColorStop(0.25, color);
      grad.addColorStop(1, this.alpha(color, 0.85));
      g.fillStyle = grad;
      this.roundRect(g, x, y, w, h, Math.min(16, h / 2));
      g.fill();
      g.shadowBlur = 0;
      g.strokeStyle = 'rgba(255,255,255,0.7)';
      g.lineWidth = 1.5;
      g.stroke();
    } else {
      g.fillStyle = color;
      this.roundRect(g, x, y, w, h, Math.min(8, h / 2));
      g.fill();
      g.shadowBlur = 0;
      g.strokeStyle = '#D4A017';
      g.lineWidth = 1.5;
      g.stroke();
      // losange central
      const cx = x + w / 2;
      const cy = y + h / 2;
      const s = Math.min(h * 0.32, 10);
      g.fillStyle = '#FFF3E0';
      g.beginPath();
      g.moveTo(cx, cy - s);
      g.lineTo(cx + s, cy);
      g.lineTo(cx, cy + s);
      g.lineTo(cx - s, cy);
      g.closePath();
      g.fill();
    }
    if (chord) {
      g.fillStyle = 'rgba(255,255,255,0.9)';
      g.fillRect(x + w / 2 - 6, y + h - 5, 12, 2);
    }
    g.restore();
  }

  private burst(lane: number, j: Judge, count: number) {
    const x = this.fieldX + (lane + 0.5) * this.laneW;
    const color = this.theme.judge[j];
    this.rings.push({ x, y: this.lineY, t: performance.now() / 1000, color });
    if (this.opts.reduceMotion) return;
    this.spark(lane, color, count);
  }

  private spark(lane: number, color: string, count: number) {
    const x = this.fieldX + (lane + 0.5) * this.laneW;
    const palette = this.theme.id === 'survivor' ? this.theme.accents : [color, this.theme.accents[1]];
    for (let i = 0; i < count; i++) {
      this.particles.push({
        x: x + (Math.random() - 0.5) * this.laneW * 0.6,
        y: this.lineY,
        vx: (Math.random() - 0.5) * 5,
        vy: -2 - Math.random() * 5,
        life: 0.35 + Math.random() * 0.35,
        color: palette[i % palette.length],
        size: 2 + Math.random() * 3,
      });
    }
    if (this.particles.length > 260) this.particles.splice(0, this.particles.length - 260);
  }

  private roundRect(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
    const rr = Math.max(0, Math.min(r, w / 2, h / 2));
    g.beginPath();
    g.moveTo(x + rr, y);
    g.arcTo(x + w, y, x + w, y + h, rr);
    g.arcTo(x + w, y + h, x, y + h, rr);
    g.arcTo(x, y + h, x, y, rr);
    g.arcTo(x, y, x + w, y, rr);
    g.closePath();
  }

  private alpha(hex: string, a: number): string {
    const h = hex.replace('#', '');
    const r = parseInt(h.slice(0, 2), 16);
    const gg = parseInt(h.slice(2, 4), 16);
    const b = parseInt(h.slice(4, 6), 16);
    return `rgba(${r},${gg},${b},${Math.max(0, Math.min(1, a)).toFixed(3)})`;
  }

  dispose() {
    cancelAnimationFrame(this.raf);
    window.clearTimeout(this.judgeTimer);
    this.disposers.forEach((d) => d());
  }
}
