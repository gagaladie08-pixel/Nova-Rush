import { THEMES, THEME_IDS } from '../game/themes';
import type { ThemeId } from '../game/types';
import { db } from '../storage/db';
import { settings } from '../storage/settings';
import { nav } from './nav';
import type { Screen } from './router';

function themeCard(id: ThemeId, selected: boolean, group: string) {
  const t = THEMES[id];
  return `<button class="theme-card ${selected ? 'selected' : ''}" data-group="${group}" data-theme-id="${id}" style="--c1:${t.accents[0]};--c2:${t.accents[1]};--c3:${t.accents[2]};--bgc:${t.bg}">
      <span class="theme-swatch"></span>
      <span class="theme-name" style="font-family:'${t.fontTitle}'">${t.name}</span>
      <span class="theme-tag">${t.tagline}</span>
    </button>`;
}

export function settingsScreen(): Screen {
  return {
    name: 'settings',
    onBack() {
      void nav.library();
      return true;
    },
    render(root) {
      const draw = () => {
        const s = settings.get();
        root.innerHTML = `
          <header class="topbar">
            <button class="icon-btn" data-back aria-label="Retour">←</button>
            <h2 class="topbar-title">Paramètres</h2>
            <span></span>
          </header>
          <div class="settings">
            <section class="card">
              <h3>Thème des menus</h3>
              <p class="muted">Habillage global de l’application. Les thèmes ne changent ni les règles ni la difficulté.</p>
              <div class="theme-grid">${THEME_IDS.map((id) => themeCard(id, s.menuTheme === id, 'menu')).join('')}</div>
            </section>
            <section class="card">
              <h3>Thème de la prochaine partie</h3>
              <p class="muted">S’applique à la prochaine partie seulement, puis revient au thème des menus.</p>
              <div class="theme-grid">
                <button class="theme-card theme-same ${s.nextGameTheme === null ? 'selected' : ''}" data-group="game" data-theme-id="">
                  <span class="theme-name">Identique aux menus</span>
                </button>
                ${THEME_IDS.map((id) => themeCard(id, s.nextGameTheme === id, 'game')).join('')}
              </div>
            </section>
            <section class="card">
              <label class="switch-row">
                <span><strong>Réduire les animations</strong><br><span class="muted">Fonds et effets décoratifs immobiles ; tuiles et jugements restent lisibles.</span></span>
                <input type="checkbox" data-reduce ${s.reduceMotion ? 'checked' : ''} />
              </label>
            </section>
            <section class="card">
              <h3>Synchronisation audio</h3>
              <p class="muted">Compense le délai du son et de l’écran tactile de ton appareil.</p>
              <div class="offset-row">
                <input type="range" min="-200" max="300" step="5" value="${s.audioOffsetMs}" data-offset />
                <span class="offset-value">${s.audioOffsetMs > 0 ? '+' : ''}${s.audioOffsetMs} ms</span>
              </div>
              <label class="switch-row auto-cal">
                <span><strong>Calibration automatique</strong><br><span class="muted">Si tu touches régulièrement un peu en avance ou en retard, le décalage se corrige doucement pendant les parties.</span></span>
                <input type="checkbox" data-autocal ${s.autoCalibrate ? 'checked' : ''} />
              </label>
              <div class="row-actions">
                <button class="btn btn-secondary" data-sync>Lancer le test de synchronisation</button>
                <button class="btn btn-ghost" data-reset-offset>Réinitialiser</button>
              </div>
            </section>
            <section class="card">
              <h3>Aide et confidentialité</h3>
              <p class="muted">Aucun compte n’est nécessaire. Ta musique, ses analyses et tes scores restent sur cet appareil.</p>
              <p class="muted" data-storage></p>
              <button class="btn btn-secondary" data-help>Voir les règles du jeu</button>
            </section>
            <p class="muted version">Nova Rush · version ${__APP_VERSION__} · Français</p>
          </div>`;

        root.querySelector('[data-back]')!.addEventListener('click', () => void nav.library());
        root.querySelectorAll<HTMLButtonElement>('.theme-card').forEach((b) =>
          b.addEventListener('click', () => {
            const id = (b.dataset.themeId || null) as ThemeId | null;
            if (b.dataset.group === 'menu' && id) settings.update({ menuTheme: id });
            else if (b.dataset.group === 'game') settings.update({ nextGameTheme: id });
            draw();
          }),
        );
        root.querySelector<HTMLInputElement>('[data-reduce]')!.addEventListener('change', (e) =>
          settings.update({ reduceMotion: (e.target as HTMLInputElement).checked }),
        );
        root.querySelector<HTMLInputElement>('[data-autocal]')!.addEventListener('change', (e) =>
          settings.update({ autoCalibrate: (e.target as HTMLInputElement).checked }),
        );
        const range = root.querySelector<HTMLInputElement>('[data-offset]')!;
        const out = root.querySelector<HTMLElement>('.offset-value')!;
        range.addEventListener('input', () => {
          const v = Number(range.value);
          out.textContent = `${v > 0 ? '+' : ''}${v} ms`;
          settings.update({ audioOffsetMs: v });
        });
        root.querySelector('[data-reset-offset]')!.addEventListener('click', () => {
          settings.update({ audioOffsetMs: 0 });
          draw();
        });
        root.querySelector('[data-sync]')!.addEventListener('click', () => void nav.sync());
        root.querySelector('[data-help]')!.addEventListener('click', () => void nav.help());
        void db.listSongs().then((songs) => {
          const n = songs.filter((x) => x.hasAudio).length;
          const el = root.querySelector('[data-storage]');
          if (el) el.textContent = `${n} morceau${n > 1 ? 'x' : ''} importé${n > 1 ? 's' : ''} sur l’appareil.`;
        });
      };
      draw();
    },
  };
}
