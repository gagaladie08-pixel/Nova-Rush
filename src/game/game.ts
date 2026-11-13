import { audio } from '../audio/engine';
import { LEVEL_PARAMS } from '../analysis/difficulty';
import { AutoCalibrator } from './calibration';
import { JUDGE_LABEL, ScoreKeeper, WINDOWS, judgeOffset, type Judge, type ScoreEvent } from './scoring';
import { THEMES, type GameTheme } from './themes';
import { sectionAt, sectionIntensity } from './sections';
import type { Note, Section, SongMeta, ThemeId } from './types';

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
  /** Calibration automatique du décalage audio pendant la partie. */
  autoCalibrate: boolean;
  onOffsetChange?: (ms: number) => void;
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

interface Fade {
  lane: number;
  top: number;
  bottom: number;
  t0: number;
  color: string;
}

type State = 'countdown' | 'playing' | 'paused' | 'resuming' | 'failed' | 'done';

const COUNTDOWN = 3;
/** Position de la ligne de jugement (fraction de la hauteur d'écran). */
const LINE_RATIO = 0.7;

/** Jugement d'un toucher : toute tuile visible est touchable, la ligne ne sert qu'à la précision. */
export function judgeTap(dt: number): Judge {
  return judgeOffset(dt) ?? 'great';
}

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
  /** Délai après l'instant de la tuile au-delà duquel elle a quitté l'écran (s). */
  private missDelay = 1;
  private travel: number;
  private duration: number;
  private minLaneGap: number;
  private bg: HTMLCanvasElement | null = null;
  private lineSprite: HTMLCanvasElement | null = null;
  private laneFlash = [0, 0, 0, 0];
  /** Effets liés à la musique : premiers temps et sections (refrains). */
  private sections: Section[] | undefined;
  private downbeats: number[] = [];
  /** Batterie isolée (Demucs) : grosse caisse = petite pulsation, cymbale = éclair. */
  private kicks: number[] = [];
  private crashes: number[] = [];
  private kickIdx = 0;
  private crashIdx = 0;
  private kickPulse = 0;
  private flash = 0;
  private dbIdx = 0;
  private pulse = 0;
  private intensity = 0.3;
  private lastSection: Section | undefined;
  private sweepT0 = -9;
  private pointerLane = new Map<number, number>();
  private particles: Particle[] = [];
  private fades: Fade[] = [];
  private maxDur = 0;
  private offsetMs: number;
  private calibrator: AutoCalibrator | null;
  private hud: {
    score: HTMLElement;
    combo: HTMLElement;
    progress: HTMLElement;
    stars: HTMLElement[];
    judge: HTMLElement;
    countdown: HTMLElement;
    pauseBtn: HTMLButtonElement;
    overlay: HTMLElement;
  };
  private lastScore = -1;
  private lastCombo = -1;
  private lastStars = -1;
  private lastProgress = -1;
  private failNote: NoteState | null = null;
  private ended = false;
  private disposers: (() => void)[] = [];
  private judgeTimer = 0;

  constructor(root: HTMLElement, private opts: GameOptions) {
    this.root = root;
    this.theme = THEMES[opts.theme];
    this.travel = LEVEL_PARAMS[opts.song.level].travel;
    this.duration = opts.buffer.duration;
    this.offsetMs = opts.offsetMs;
    this.calibrator = opts.autoCalibrate ? new AutoCalibrator(opts.offsetMs) : null;
    this.notes = opts.song.chart.notes.map((n, idx) => ({ ...n, idx, judged: false }));
    for (const n of this.notes) {
      this.laneQueues[n.lane].push(n);
      this.maxDur = Math.max(this.maxDur, n.dur ?? 0);
    }
    this.minLaneGap = this.computeMinLaneGap();
    const chart = opts.song.chart;
    this.sections = (chart.structureConfidence ?? 0) >= 0.3 ? chart.sections : undefined;
    this.downbeats = chart.downbeats ?? [];
    this.kicks = chart.kicks ?? [];
    this.crashes = chart.crashes ?? [];
    this.keeper = new ScoreKeeper(this.notes);

    const stars = [1, 2, 3, 4, 5]
      .map((i) => `<span class="star" style="left:${i * 20}%">★</span>`)
      .join('');
    root.innerHTML = `
      <div class="game" data-game-theme="${opts.theme}">
        <canvas class="game-canvas"></canvas>
        <div class="game-hud">
          <div class="hud-top">
            <div class="hud-progress" aria-label="Progression"><div class="hud-progress-fill"></div>${stars}</div>
            <button class="hud-pause" aria-label="Pause">❚❚</button>
          </div>
          <div class="hud-center">
            <div class="hud-score-value">0</div>
            <div class="hud-judge"></div>
            <div class="hud-combo-value"></div>
          </div>
          <div class="hud-countdown"></div>
        </div>
        <div class="game-overlay hidden"></div>
      </div>`;
    this.canvas = root.querySelector('.game-canvas')!;
    this.g = this.canvas.getContext('2d', { alpha: false })!;
    this.hud = {
      score: root.querySelector('.hud-score-value')!,
      combo: root.querySelector('.hud-combo-value')!,
      progress: root.querySelector('.hud-progress-fill')!,
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

  /** Plus petit écart entre la fin d'une tuile et la suivante dans une même voie (s). */
  private computeMinLaneGap(): number {
    const gaps: number[] = [];
    for (const q of this.laneQueues) {
      for (let i = 1; i < q.length; i++) gaps.push(q[i].t - (q[i - 1].t + (q[i - 1].dur ?? 0)));
    }
    if (!gaps.length) return 0.5;
    gaps.sort((a, b) => a - b);
    return Math.max(0.08, gaps[Math.floor(gaps.length * 0.03)]);
  }

  /* --------------------------- Horloge --------------------------- */

  songTime(perfMs = performance.now()): number {
    return audio.heardCtxTime(perfMs) - this.startCtx + this.playOffset - this.offsetMs / 1000;
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
        n.judged = true;
        n.result = n.headJudge;
        n.holdPointer = undefined;
        this.applyEvent(n, this.keeper.applyLong(n, n.headJudge, n.headJudge));
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
    const bufferPos = Math.max(0, this.pausedAt + this.offsetMs / 1000);
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
    this.fades = [];
    this.failNote = null;
    this.lastScore = this.lastCombo = this.lastStars = this.lastProgress = -1;
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

  /* --------------------------- Géométrie ------------------------- */

  /** Ordonnée du bas d'une tuile dont l'instant est t. */
  private yOf(t: number, now: number) {
    return this.lineY - (t - now) * this.pxPerSec;
  }

  /** Rectangle vertical (haut, bas) d'une tuile à l'instant `now`. */
  private rectOf(n: Note, now: number): { top: number; bottom: number } {
    const bottom = this.yOf(n.t, now);
    const top = n.dur ? Math.min(this.yOf(n.t + n.dur, now), bottom - this.tileH) : bottom - this.tileH;
    return { top, bottom };
  }

  /* --------------------------- Entrées --------------------------- */

  private laneAt(clientX: number): number {
    const rect = this.canvas.getBoundingClientRect();
    const x = clientX - rect.left - this.fieldX;
    if (x < 0 || x >= this.fieldW) return -1;
    return Math.min(3, Math.floor(x / this.laneW));
  }

  /**
   * Toute tuile visible peut être touchée, dans n'importe quel ordre.
   * Toucher une zone vide ne pénalise pas.
   */
  private onDown(e: PointerEvent) {
    e.preventDefault();
    if (this.state !== 'playing') return;
    const lane = this.laneAt(e.clientX);
    if (lane < 0) return;
    this.pointerLane.set(e.pointerId, lane);
    const t = this.songTime(e.timeStamp || performance.now());
    const y = e.clientY - this.canvas.getBoundingClientRect().top;
    const tolAbove = 20;
    const tolBelow = Math.max(24, this.pxPerSec * 0.1);
    let target: NoteState | undefined;
    let bestDist = Infinity;
    const q = this.laneQueues[lane];
    for (let i = this.laneHead[lane]; i < q.length; i++) {
      const n = q[i];
      if (n.t - t > this.travel * 1.2) break;
      if (n.judged || n.holdPointer !== undefined) continue;
      const r = this.rectOf(n, t);
      if (r.bottom < 0 || r.top > this.h) continue;
      if (y < r.top - tolAbove || y > r.bottom + tolBelow) continue;
      const d = y < r.top ? r.top - y : y > r.bottom ? y - r.bottom : 0;
      if (d < bestDist) {
        bestDist = d;
        target = n;
      }
    }
    if (!target) return; // zone vide : aucune pénalité
    const dt = t - target.t;
    const judge = judgeTap(dt);
    this.calibrate(dt);
    if (target.dur) {
      target.holdPointer = e.pointerId;
      target.headJudge = judge;
      target.hitAt = t;
      this.showJudge(judge, false, dt);
      this.laneFlash[target.lane] = 0.6;
    } else {
      target.judged = true;
      target.result = judge;
      target.hitAt = t;
      const r = this.rectOf(target, t);
      this.fades.push({ lane: target.lane, top: r.top, bottom: r.bottom, t0: performance.now() / 1000, color: this.theme.fill });
      this.applyEvent(target, this.keeper.applyShort(target, judge), dt);
    }
  }

  private onUp(e: PointerEvent) {
    this.pointerLane.delete(e.pointerId);
    if (this.state !== 'playing') return;
    const n = this.notes.find((x) => x.holdPointer === e.pointerId && !x.judged);
    if (!n || !n.headJudge || !n.dur) return;
    this.releaseHold(n, this.songTime(e.timeStamp || performance.now()));
  }

  /** Option A : le relâchement est jugé quand le haut de la tuile passe la ligne. */
  private releaseHold(n: NoteState, t: number) {
    const end = n.t + (n.dur ?? 0);
    n.holdPointer = undefined;
    n.judged = true;
    if (t < end - WINDOWS.great) {
      n.result = 'early';
      this.applyEvent(n, this.keeper.applyEarlyRelease(n));
    } else {
      const dt = t - end;
      const ev = this.keeper.applyLong(n, n.headJudge!, judgeTap(dt));
      n.result = ev.judge;
      this.applyEvent(n, ev, dt);
    }
  }

  private calibrate(dt: number) {
    if (!this.calibrator) return;
    const next = this.calibrator.add(dt);
    if (next !== null) {
      this.offsetMs = next;
      this.opts.onOffsetChange?.(Math.round(next));
    }
  }

  private applyEvent(n: Note, ev: ScoreEvent, dt?: number) {
    this.showJudge(ev.judge, !!ev.earlyRelease, dt);
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

  private showJudge(j: Judge | 'miss', early: boolean, dt?: number) {
    const el = this.hud.judge;
    const label = j === 'miss' ? 'Raté' : early ? 'Relâché trop tôt' : JUDGE_LABEL[j];
    // Petit repère : en avance ou en retard sur la ligne.
    const hint =
      j === 'perfect' || j === 'great'
        ? dt !== undefined && Math.abs(dt) > WINDOWS.perfectPlus
          ? dt < 0
            ? 'trop tôt'
            : 'trop tard'
          : ''
        : early
          ? 'Great'
          : '';
    el.innerHTML = `<span class="judge-label">${label}</span>${hint ? `<span class="judge-hint">${hint}</span>` : ''}`;
    el.style.color = this.theme.judge[j];
    el.classList.remove('pop');
    void el.offsetWidth;
    el.classList.add('pop');
    window.clearTimeout(this.judgeTimer);
    this.judgeTimer = window.setTimeout(() => el.classList.remove('pop'), 700);
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
    // Ratés : une tuile sortie de l'écran par le bas sans avoir été touchée.
    for (let l = 0; l < 4; l++) {
      const q = this.laneQueues[l];
      while (this.laneHead[l] < q.length && q[this.laneHead[l]].judged) this.laneHead[l]++;
      for (let i = this.laneHead[l]; i < q.length; i++) {
        const n = q[i];
        if (n.t > now - this.missDelay) break;
        if (!n.judged && n.holdPointer === undefined) {
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
      this.hud.combo.textContent = k.combo > 0 ? `×${k.combo}` : '';
      if (k.combo > 0 && !this.opts.reduceMotion) {
        this.hud.combo.classList.remove('bump');
        void this.hud.combo.offsetWidth;
        this.hud.combo.classList.add('bump');
      }
    }
    // Progression et cinq étoiles : une par cinquième de la durée (temps écoulé, pas performance).
    const p = this.state === 'done' ? 1 : Math.max(0, Math.min(1, now / this.duration));
    if (Math.abs(p - this.lastProgress) > 0.002) {
      this.lastProgress = p;
      this.hud.progress.style.transform = `scaleX(${p})`;
    }
    const stars = this.state === 'done' ? 5 : Math.floor(p * 5);
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
    this.lineY = this.h * LINE_RATIO;
    this.pxPerSec = this.lineY / this.travel;
    // Tuiles hautes (style touches de piano), sans chevauchement dans une voie.
    this.tileH = Math.max(46, Math.min(this.h * 0.2, this.laneW * 1.6, this.pxPerSec * this.minLaneGap * 0.9));
    this.missDelay = (this.h - this.lineY + this.tileH) / this.pxPerSec;
    this.bg = this.makeBackground();
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
      g.shadowBlur = this.theme.glow * 1.8;
    }
    g.fillStyle = this.alpha(this.theme.line, 0.85);
    g.fillRect(pad, pad + 1, this.fieldW, 2);
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
    g.fillStyle = th.bg;
    g.fillRect(0, 0, w, h);

    // Voies en dégradé coloré
    const grad = g.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, th.laneGrad[0]);
    grad.addColorStop(1, th.laneGrad[1]);
    g.fillStyle = grad;
    g.fillRect(this.fieldX, 0, this.fieldW, h);

    g.save();
    g.beginPath();
    g.rect(this.fieldX, 0, this.fieldW, h);
    g.clip();
    // Grands losanges translucides
    const s = this.fieldW / 2;
    g.lineWidth = 1.5;
    for (let y = -s; y < h + s; y += s) {
      for (let x = this.fieldX; x <= this.fieldX + this.fieldW; x += s) {
        const ox = (Math.round(y / s) % 2) * (s / 2);
        g.fillStyle = 'rgba(255,255,255,0.035)';
        g.strokeStyle = 'rgba(255,255,255,0.06)';
        g.beginPath();
        g.moveTo(x + ox, y - s / 2);
        g.lineTo(x + ox + s / 2, y);
        g.lineTo(x + ox, y + s / 2);
        g.lineTo(x + ox - s / 2, y);
        g.closePath();
        g.fill();
        g.stroke();
      }
    }
    if (th.id === 'cyber') {
      g.fillStyle = 'rgba(255,255,255,0.03)';
      for (let y = 0; y < h; y += 3) g.fillRect(this.fieldX, y, this.fieldW, 1);
    } else if (th.id === 'survivor') {
      const flags = [
        ['#E40303', '#FF8C00', '#FFED00', '#008026', '#004DFF', '#750787'],
        ['#5BCEFA', '#F5A9B8', '#FFFFFF', '#F5A9B8', '#5BCEFA'],
        ['#D60270', '#9B4F96', '#0038A8'],
      ];
      g.globalAlpha = 0.09;
      g.translate(w / 2, h / 2);
      g.rotate(-Math.PI / 5);
      let yy = -h;
      let fi = 0;
      while (yy < h) {
        for (const col of flags[fi++ % flags.length]) {
          g.fillStyle = col;
          g.fillRect(-w * 1.5, yy, w * 3, 7);
          yy += 7;
        }
        yy += 110;
      }
    } else {
      const d = 22;
      g.strokeStyle = 'rgba(212,160,23,0.10)';
      g.lineWidth = 1;
      for (let y = 0; y < h + d; y += d) {
        for (let x = this.fieldX; x < this.fieldX + this.fieldW + d; x += d) {
          const ox = (Math.round(y / d) % 2) * (d / 2);
          g.beginPath();
          g.moveTo(x + ox, y - d / 3);
          g.lineTo(x + ox + d / 3, y);
          g.lineTo(x + ox, y + d / 3);
          g.lineTo(x + ox - d / 3, y);
          g.closePath();
          g.stroke();
        }
      }
    }
    g.restore();

    // Séparations fines entre les voies
    g.fillStyle = 'rgba(255,255,255,0.28)';
    for (let l = 1; l < 4; l++) g.fillRect(Math.round(this.fieldX + l * this.laneW), 0, 1, h);
    // Zone sous la ligne légèrement assombrie
    g.fillStyle = 'rgba(0,0,0,0.18)';
    g.fillRect(this.fieldX, this.lineY, this.fieldW, h - this.lineY);
    return c;
  }

  private render(now: number, raw: number) {
    const g = this.g;
    const th = this.theme;
    const reduce = this.opts.reduceMotion;
    g.setTransform(1, 0, 0, 1, 0, 0);
    if (this.bg) g.drawImage(this.bg, 0, 0);
    g.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    const wall = performance.now() / 1000;

    this.drawMusic(g, now, wall, reduce);
    if (!reduce) this.drawAmbient(g, wall);

    // Éclairs des voies après un toucher
    for (let l = 0; l < 4; l++) {
      const a = this.laneFlash[l] * 0.35;
      if (a > 0.01) {
        const x = this.fieldX + l * this.laneW;
        const grad = g.createLinearGradient(0, this.h, 0, 0);
        grad.addColorStop(0, this.alpha(th.fill, a));
        grad.addColorStop(1, this.alpha(th.fill, 0));
        g.fillStyle = grad;
        g.fillRect(x, 0, this.laneW, this.h);
      }
      this.laneFlash[l] *= 0.86;
    }

    if (this.lineSprite) {
      g.drawImage(this.lineSprite, this.fieldX - 24, this.lineY - 2 - 24, this.fieldW + 48, 4 + 48);
    }

    // Tuiles
    const tMin = now - this.missDelay - 0.2 - this.maxDur;
    const tMax = now + this.travel * 1.15;
    for (const n of this.notes) {
      if (n.t < tMin) continue;
      if (n.t > tMax) break;
      this.drawNote(g, n, now);
    }

    // Tuiles touchées qui s'effacent
    for (let i = this.fades.length - 1; i >= 0; i--) {
      const f = this.fades[i];
      const age = wall - f.t0;
      if (age > 0.25) {
        this.fades.splice(i, 1);
        continue;
      }
      const k = age / 0.25;
      const x = this.fieldX + f.lane * this.laneW;
      const grow = reduce ? 0 : k * 6;
      g.fillStyle = this.alpha(f.color, 0.55 * (1 - k));
      this.roundRect(g, x + 1 - grow, f.top - grow, this.laneW - 2 + grow * 2, f.bottom - f.top + grow * 2, th.tileRadius);
      g.fill();
    }

    // Particules
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

  /**
   * Pulsation sur chaque premier temps de mesure, ambiance plus intense
   * pendant les refrains et balayage lumineux à l'entrée d'un refrain.
   */
  private drawMusic(g: CanvasRenderingContext2D, now: number, wall: number, reduce: boolean) {
    const th = this.theme;
    const live = this.state === 'playing' || this.state === 'countdown' || this.state === 'resuming';
    // Premiers temps
    const db = this.downbeats;
    while (this.dbIdx > 0 && db[this.dbIdx - 1] > now + 0.1) this.dbIdx--;
    let hit = false;
    while (this.dbIdx < db.length && db[this.dbIdx] <= now) {
      if (now - db[this.dbIdx] < 0.1) hit = true;
      this.dbIdx++;
    }
    if (hit && live && this.state !== 'countdown') this.pulse = 1;
    // Batterie : chaque grosse caisse fait battre le terrain, chaque cymbale l'illumine.
    const passed = (list: number[], idx: number): [number, boolean] => {
      while (idx > 0 && list[idx - 1] > now + 0.1) idx--;
      let on = false;
      while (idx < list.length && list[idx] <= now) {
        if (now - list[idx] < 0.1) on = true;
        idx++;
      }
      return [idx, on];
    };
    const playing = live && this.state !== 'countdown';
    let on: boolean;
    [this.kickIdx, on] = passed(this.kicks, this.kickIdx);
    if (on && playing) this.kickPulse = 1;
    [this.crashIdx, on] = passed(this.crashes, this.crashIdx);
    if (on && playing) this.flash = 1;
    // Section en cours
    const sec = sectionAt(this.sections, now);
    if (sec !== this.lastSection) {
      if (sec?.kind === 'refrain' && this.lastSection?.kind !== 'refrain' && now > 0.5) this.sweepT0 = wall;
      this.lastSection = sec;
    }
    if (live) this.intensity += (sectionIntensity(sec) - this.intensity) * 0.04;
    const k = this.intensity;
    const refrain = sec?.kind === 'refrain' ? 1 : 0;

    // Teinte des voies (plus vive aux refrains)
    const tint = reduce ? 0.04 * refrain : 0.03 + 0.09 * k;
    if (tint > 0.005) {
      const grad = g.createLinearGradient(0, 0, 0, this.lineY);
      grad.addColorStop(0, this.alpha(th.accents[0], 0));
      grad.addColorStop(1, this.alpha(th.accents[0], tint));
      g.fillStyle = grad;
      g.fillRect(this.fieldX, 0, this.fieldW, this.lineY);
    }
    if (reduce) return;

    // Lueurs sur les bords du terrain, qui battent avec la mesure (et la grosse caisse)
    const beat = Math.max(this.pulse, 0.6 * this.kickPulse);
    const edge = (0.08 + 0.3 * k) * (0.55 + 0.45 * beat);
    if (edge > 0.02) {
      const w = Math.max(10, this.laneW * 0.22);
      for (const [x0, x1, col] of [
        [this.fieldX, this.fieldX + w, th.accents[1]],
        [this.fieldX + this.fieldW, this.fieldX + this.fieldW - w, th.accents[3]],
      ] as [number, number, string][]) {
        const grad = g.createLinearGradient(x0, 0, x1, 0);
        grad.addColorStop(0, this.alpha(col, edge));
        grad.addColorStop(1, this.alpha(col, 0));
        g.fillStyle = grad;
        g.fillRect(Math.min(x0, x1), 0, w, this.h);
      }
    }

    // Pulsation de la ligne de jugement
    if (this.pulse > 0.02 && this.lineSprite) {
      g.save();
      g.globalAlpha = Math.min(1, this.pulse * (0.35 + 0.65 * k));
      const grow = 10 + 18 * this.pulse;
      g.drawImage(this.lineSprite, this.fieldX - 24 - grow, this.lineY - 26 - grow, this.fieldW + 48 + grow * 2, 52 + grow * 2);
      g.restore();
    }
    this.pulse *= 0.88;
    this.kickPulse *= 0.8;

    // Éclair sur un coup de cymbale
    if (this.flash > 0.02) {
      const grad = g.createRadialGradient(this.w / 2, this.lineY, 0, this.w / 2, this.lineY, this.h * 0.8);
      grad.addColorStop(0, this.alpha(th.fill, 0.22 * this.flash * (0.5 + 0.5 * k)));
      grad.addColorStop(1, this.alpha(th.fill, 0));
      g.fillStyle = grad;
      g.fillRect(this.fieldX, 0, this.fieldW, this.h);
    }
    this.flash *= 0.9;

    // Balayage à l'entrée d'un refrain
    const age = wall - this.sweepT0;
    if (age >= 0 && age < 0.7) {
      const y = (age / 0.7) * this.lineY;
      const a = 0.28 * (1 - age / 0.7);
      const grad = g.createLinearGradient(0, y - 90, 0, y + 10);
      grad.addColorStop(0, this.alpha(th.fill, 0));
      grad.addColorStop(0.85, this.alpha(th.fill, a));
      grad.addColorStop(1, this.alpha(th.fill, 0));
      g.fillStyle = grad;
      g.fillRect(this.fieldX, y - 90, this.fieldW, 100);
    }
  }

  private drawAmbient(g: CanvasRenderingContext2D, wall: number) {
    const th = this.theme;
    if (th.id === 'cyber') {
      const y = ((wall * 0.25) % 1) * this.h * 1.2 - this.h * 0.1;
      const grad = g.createLinearGradient(0, y - 40, 0, y + 40);
      grad.addColorStop(0, 'rgba(0,255,240,0)');
      grad.addColorStop(0.5, 'rgba(0,255,240,0.06)');
      grad.addColorStop(1, 'rgba(0,255,240,0)');
      g.fillStyle = grad;
      g.fillRect(this.fieldX, y - 40, this.fieldW, 80);
    } else if (th.id === 'survivor') {
      const a = 0.07 + 0.05 * Math.sin(wall * 1.6);
      const grad = g.createLinearGradient(0, this.lineY - this.h * 0.3, 0, this.lineY);
      grad.addColorStop(0, this.alpha('#F77FB4', 0));
      grad.addColorStop(1, this.alpha('#F77FB4', a));
      g.fillStyle = grad;
      g.fillRect(this.fieldX, this.lineY - this.h * 0.3, this.fieldW, this.h * 0.3);
    } else {
      const a = 0.05 + 0.035 * Math.sin(wall * 1.2);
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

  private drawNote(g: CanvasRenderingContext2D, n: NoteState, now: number) {
    const th = this.theme;
    const isFail = this.failNote === n;
    if (n.judged && !isFail) return;
    const { top, bottom } = this.rectOf(n, now);
    if (bottom < -4 || top > this.h + 4) return;
    const x = this.fieldX + n.lane * this.laneW + 1;
    const w = this.laneW - 2;
    const r = th.tileRadius;

    // Corps sombre
    const grad = g.createLinearGradient(0, top, 0, bottom);
    grad.addColorStop(0, isFail ? '#5A0A14' : th.tile[0]);
    grad.addColorStop(1, isFail ? '#C0283D' : th.tile[1]);
    g.fillStyle = grad;
    this.roundRect(g, x, top, w, bottom - top, r);
    g.fill();
    // Reflet en haut et liseré coloré en bas
    g.fillStyle = 'rgba(255,255,255,0.07)';
    g.fillRect(x + r / 2, top + 1, w - r, 2);
    g.fillStyle = this.alpha(th.laneColors[n.lane], 0.8);
    g.fillRect(x + r / 2, bottom - 3, w - r, 2);

    if (n.dur) {
      const holding = n.holdPointer !== undefined;
      const cx = x + w / 2;
      // Remplissage de la partie déjà passée sous la ligne
      if (holding) {
        const fTop = Math.max(top, Math.min(this.lineY, bottom));
        const fBottom = Math.min(bottom, this.h);
        if (fBottom > fTop) {
          const fg = g.createLinearGradient(0, fTop, 0, fBottom);
          fg.addColorStop(0, this.alpha(th.fill, 0.95));
          fg.addColorStop(1, this.alpha(th.laneColors[n.lane], 0.85));
          g.fillStyle = fg;
          this.roundRect(g, x, fTop, w, fBottom - fTop, r);
          g.fill();
        }
        g.strokeStyle = this.alpha(th.fill, 0.9);
        g.lineWidth = 2;
        this.roundRect(g, x + 1, top + 1, w - 2, bottom - top - 2, r);
        g.stroke();
        if (!this.opts.reduceMotion && Math.random() < 0.5) this.spark(n.lane, th.fill, 1);
      }
      // Trait central et point
      const lineTop = top + 14;
      const lineBottom = holding ? Math.min(bottom, this.lineY) : bottom - 10;
      if (lineBottom > lineTop) {
        g.fillStyle = 'rgba(255,255,255,0.75)';
        g.fillRect(cx - 1, lineTop, 2, lineBottom - lineTop);
      }
      g.fillStyle = '#FFFFFF';
      g.beginPath();
      g.arc(cx, lineTop, 4.5, 0, Math.PI * 2);
      g.fill();
      if (holding) {
        g.beginPath();
        g.arc(cx, Math.min(bottom, this.lineY), 6, 0, Math.PI * 2);
        g.fill();
      }
    } else if (th.id === 'african') {
      // petit losange doré
      const cx = x + w / 2;
      const cy = (top + bottom) / 2;
      const s = Math.min(9, (bottom - top) / 4);
      g.strokeStyle = 'rgba(212,160,23,0.7)';
      g.lineWidth = 1.5;
      g.beginPath();
      g.moveTo(cx, cy - s);
      g.lineTo(cx + s, cy);
      g.lineTo(cx, cy + s);
      g.lineTo(cx - s, cy);
      g.closePath();
      g.stroke();
    }
  }

  private burst(lane: number, j: Judge, count: number) {
    if (this.opts.reduceMotion) return;
    this.spark(lane, this.theme.judge[j], count);
  }

  private spark(lane: number, color: string, count: number) {
    const x = this.fieldX + (lane + 0.5) * this.laneW;
    const palette = this.theme.id === 'survivor' ? this.theme.accents : [color, this.theme.fill];
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
