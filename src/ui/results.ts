import { audio } from '../audio/engine';
import type { GameResult } from '../game/game';
import type { SongMeta, ThemeId } from '../game/types';
import { saveResult } from '../storage/db';
import { settings } from '../storage/settings';
import { celebrate } from './celebrate';
import { coverFor } from './covers';
import { esc, formatScore } from './dom';
import { nav } from './nav';
import type { Screen } from './router';

export function resultsScreen(song: SongMeta, r: GameResult, theme: ThemeId): Screen {
  let offAudio: (() => void) | undefined;
  return {
    name: 'results',
    onBack() {
      void nav.library();
      return true;
    },
    dispose() {
      offAudio?.();
    },
    render(root) {
      const stars = r.completed ? 5 : Math.floor(r.progress * 5);
      const heading = r.completed ? 'Chanson terminée' : r.failed ? 'Partie terminée' : 'Partie interrompue';
      root.innerHTML = `
        <div class="results ${r.completed ? 'is-complete' : ''}">
          <p class="results-kicker">${r.failed ? 'Raté — la musique continue' : r.quit ? 'Résultat partiel enregistré' : 'Bravo !'}</p>
          <h1 class="results-title">${heading}</h1>
          <div class="results-song">
            <img src="${coverFor(song)}" alt="" />
            <div><h3>${esc(song.title)}</h3><p class="muted">${esc(song.artist)}</p></div>
          </div>
          <div class="results-stars">${Array.from({ length: 5 }, (_, i) => `<span class="star ${i < stars ? 'on' : ''}">★</span>`).join('')}
            <span class="muted">${Math.round(r.progress * 100)} %</span></div>
          <div class="results-score">
            <span class="stat-label">Score</span>
            <span class="results-score-value">${formatScore(r.score)}</span>
            <span class="record-badge hidden" data-new-score>Nouveau record !</span>
          </div>
          <div class="records">
            <div><span class="stat-value">${r.maxCombo}</span><span class="stat-label">Meilleur combo <span class="record-badge-inline hidden" data-new-combo>record</span></span></div>
            <div><span class="stat-value">${r.counts.perfectPlus}</span><span class="stat-label">Perfect+</span></div>
            <div><span class="stat-value">${r.counts.perfect}</span><span class="stat-label">Perfect</span></div>
            <div><span class="stat-value">${r.counts.great}</span><span class="stat-label">Great</span></div>
          </div>
          <p class="muted results-best" data-best></p>
          <div class="results-actions">
            <button class="btn btn-primary btn-wide" data-replay>Rejouer depuis le début</button>
            <button class="btn btn-secondary btn-wide" data-library>${r.failed ? 'Choisir une autre musique' : 'Bibliothèque'}</button>
            <button class="btn btn-ghost btn-wide hidden" data-stop>Arrêter la musique</button>
          </div>
        </div>`;

      const stopBtn = root.querySelector<HTMLButtonElement>('[data-stop]')!;
      const refresh = () => stopBtn.classList.toggle('hidden', !audio.isPlaying(song.id));
      offAudio = audio.onChange(refresh);
      refresh();
      stopBtn.addEventListener('click', () => audio.stop());
      root.querySelector('[data-replay]')!.addEventListener('click', () => void nav.play(song, theme));
      root.querySelector('[data-library]')!.addEventListener('click', () => void nav.library());

      void saveResult(song.id, r.score, r.maxCombo, r.progress, r.completed).then(({ record, newScore, newCombo }) => {
        if (newScore && r.score > 0) root.querySelector('[data-new-score]')!.classList.remove('hidden');
        if (newCombo && r.maxCombo > 0) root.querySelector('[data-new-combo]')!.classList.remove('hidden');
        root.querySelector('[data-best]')!.textContent = `Records : ${formatScore(record.bestScore)} pts · combo ${record.bestCombo}`;
      });
      if (r.completed) celebrate(root, theme, settings.get().reduceMotion);
    },
  };
}
