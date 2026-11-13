import { audio } from '../audio/engine';
import { median } from '../analysis/dsp';
import { settings } from '../storage/settings';
import { nav } from './nav';
import type { Screen } from './router';

const BPM = 100;
const CLICKS = 20;
const WARMUP = 4;

export function syncScreen(): Screen {
  let nodes: AudioScheduledSourceNode[] = [];
  let timer = 0;
  const stopAll = () => {
    window.clearInterval(timer);
    nodes.forEach((n) => {
      try {
        n.stop();
      } catch {
        /* déjà arrêté */
      }
    });
    nodes = [];
  };
  return {
    name: 'sync',
    onBack() {
      void nav.settings();
      return true;
    },
    dispose: stopAll,
    render(root) {
      root.innerHTML = `
        <header class="topbar">
          <button class="icon-btn" data-back aria-label="Retour">←</button>
          <h2 class="topbar-title">Test de synchronisation</h2>
          <span></span>
        </header>
        <div class="sync">
          <p class="muted sync-help">Écoute les clics et touche le grand bouton <strong>exactement en même temps</strong> que chaque clic.
          Utilise si possible le casque ou les écouteurs avec lesquels tu joues.</p>
          <button class="sync-pad" data-pad disabled>Touche ici</button>
          <p class="sync-status">Prêt ?</p>
          <div class="row-actions">
            <button class="btn btn-primary" data-start>Démarrer</button>
            <button class="btn btn-secondary hidden" data-apply>Appliquer</button>
          </div>
          <p class="muted">Décalage actuel : ${settings.get().audioOffsetMs} ms</p>
        </div>`;
      const pad = root.querySelector<HTMLButtonElement>('[data-pad]')!;
      const status = root.querySelector<HTMLElement>('.sync-status')!;
      const startBtn = root.querySelector<HTMLButtonElement>('[data-start]')!;
      const applyBtn = root.querySelector<HTMLButtonElement>('[data-apply]')!;
      let clicks: number[] = [];
      let diffs: number[] = [];
      let measured = 0;
      root.querySelector('[data-back]')!.addEventListener('click', () => this.onBack!());

      startBtn.addEventListener('click', () => {
        stopAll();
        audio.stop();
        audio.resetClock();
        const ctx = audio.ensure();
        const beat = 60 / BPM;
        const t0 = ctx.currentTime + 1;
        clicks = [];
        diffs = [];
        for (let i = 0; i < CLICKS; i++) {
          const t = t0 + i * beat;
          clicks.push(t);
          const o = ctx.createOscillator();
          const g = ctx.createGain();
          o.frequency.value = i % 4 === 0 ? 1320 : 990;
          g.gain.setValueAtTime(0.0001, t);
          g.gain.exponentialRampToValueAtTime(0.6, t + 0.002);
          g.gain.exponentialRampToValueAtTime(0.0001, t + 0.07);
          o.connect(g).connect(ctx.destination);
          o.start(t);
          o.stop(t + 0.1);
          nodes.push(o);
        }
        pad.disabled = false;
        startBtn.disabled = true;
        applyBtn.classList.add('hidden');
        status.textContent = 'Écoute… puis touche en rythme';
        const end = clicks[clicks.length - 1] + 0.6;
        timer = window.setInterval(() => {
          const now = ctx.currentTime;
          const passed = clicks.filter((c) => c < now).length;
          if (now < end) {
            status.textContent = passed <= WARMUP ? `Écoute… (${WARMUP - passed + 1})` : `Touche en rythme · ${passed}/${CLICKS}`;
            return;
          }
          window.clearInterval(timer);
          pad.disabled = true;
          startBtn.disabled = false;
          startBtn.textContent = 'Recommencer';
          if (diffs.length < 8) {
            status.textContent = 'Pas assez de touches valides. Recommence en suivant les clics.';
            return;
          }
          measured = Math.round((median(diffs) * 1000) / 5) * 5;
          status.textContent = `Décalage mesuré : ${measured > 0 ? '+' : ''}${measured} ms`;
          applyBtn.classList.remove('hidden');
        }, 100);
      });

      pad.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        pad.classList.remove('hit');
        void pad.offsetWidth;
        pad.classList.add('hit');
        const h = audio.heardCtxTime(e.timeStamp || performance.now());
        let best = Infinity;
        let idx = -1;
        clicks.forEach((c, i) => {
          if (Math.abs(h - c) < Math.abs(best)) {
            best = h - c;
            idx = i;
          }
        });
        if (idx >= WARMUP && Math.abs(best) < 0.3) diffs.push(best);
      });
      applyBtn.addEventListener('click', () => {
        settings.update({ audioOffsetMs: Math.max(-200, Math.min(300, measured)) });
        void nav.settings();
      });
    },
  };
}
