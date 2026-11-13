import { audio } from '../audio/engine';
import { Game } from '../game/game';
import type { SongMeta, ThemeId } from '../game/types';
import { settings } from '../storage/settings';
import { esc } from './dom';
import { nav } from './nav';
import type { Screen } from './router';

/** Écran de partie. `theme` est fourni lors d'un « Rejouer » pour garder l'habillage. */
export function playScreen(song: SongMeta, replayTheme?: ThemeId): Screen {
  let game: Game | null = null;
  let disposed = false;
  const theme = replayTheme ?? settings.consumeGameTheme();
  return {
    name: 'play',
    onBack() {
      if (game) game.back();
      else void nav.library();
      return true;
    },
    dispose() {
      disposed = true;
      game?.dispose();
      document.documentElement.classList.remove('in-game');
    },
    render(root) {
      document.documentElement.classList.add('in-game');
      root.dataset.gameTheme = theme;
      root.innerHTML = `<div class="loading"><div class="spinner"></div><p>Préparation de « ${esc(song.title)} »…</p></div>`;
      // Choisir un morceau arrête la musique précédente.
      audio.stop();
      audio
        .load(song)
        .then((buffer) => {
          if (disposed) return;
          const s = settings.get();
          game = new Game(root, {
            song,
            buffer,
            theme,
            reduceMotion: s.reduceMotion,
            offsetMs: s.audioOffsetMs,
            onEnd: (result) => void nav.results(song, result, theme),
          });
        })
        .catch((e) => {
          if (disposed) return;
          root.innerHTML = `<div class="loading"><p>${esc(e instanceof Error ? e.message : 'Lecture impossible.')}</p>
            <button class="btn btn-primary">Retour à la bibliothèque</button></div>`;
          root.querySelector('button')!.addEventListener('click', () => void nav.library());
        });
    },
  };
}
