/**
 * Navigation centrale (évite les dépendances circulaires entre écrans).
 * Les écrans sont chargés à la demande par cette table.
 */
import type { SongMeta, ThemeId } from '../game/types';
import type { GameResult } from '../game/game';
import { show } from './router';

export const nav = {
  async library() {
    const { libraryScreen } = await import('./library');
    show(libraryScreen());
  },
  async preview(song: SongMeta) {
    const { previewScreen } = await import('./preview');
    show(previewScreen(song));
  },
  async play(song: SongMeta, theme?: ThemeId) {
    const { playScreen } = await import('./play');
    show(playScreen(song, theme));
  },
  async results(song: SongMeta, result: GameResult, theme: ThemeId) {
    const { resultsScreen } = await import('./results');
    show(resultsScreen(song, result, theme));
  },
  async settings() {
    const { settingsScreen } = await import('./settings');
    show(settingsScreen());
  },
  async help() {
    const { helpScreen } = await import('./help');
    show(helpScreen());
  },
  async sync() {
    const { syncScreen } = await import('./sync');
    show(syncScreen());
  },
};
