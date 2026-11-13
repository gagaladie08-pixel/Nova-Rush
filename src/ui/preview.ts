import { audio } from '../audio/engine';
import { LEVEL_LABEL, type SongMeta } from '../game/types';
import { db } from '../storage/db';
import { coverFor } from './covers';
import { confirmDialog, esc, formatScore, formatTime, toast } from './dom';
import { startImport } from './importer';
import { nav } from './nav';
import type { Screen } from './router';

export function previewScreen(song: SongMeta): Screen {
  let offAudio: (() => void) | undefined;
  return {
    name: 'preview',
    onBack() {
      if (audio.current?.snippet) audio.stop();
      void nav.library();
      return true;
    },
    dispose() {
      offAudio?.();
      if (audio.current?.snippet) audio.stop();
    },
    render(root) {
      const notes = song.chart.notes;
      const longs = notes.filter((n) => n.dur).length;
      const chords = new Set(notes.filter((n) => n.chord !== undefined).map((n) => n.chord)).size;
      root.innerHTML = `
        <header class="topbar">
          <button class="icon-btn" data-back aria-label="Retour">←</button>
          <h2 class="topbar-title">Aperçu</h2>
          <span></span>
        </header>
        <div class="preview">
          <img class="preview-cover" src="${coverFor(song)}" alt="Pochette" />
          <h1 class="preview-title">${esc(song.title)}</h1>
          <p class="preview-artist">${esc(song.artist)}</p>
          <p><span class="badge badge-lg badge-${song.level}">${LEVEL_LABEL[song.level]}</span>
            ${song.demo ? `<span class="badge badge-ghost">Démo · ${esc(song.style ?? '')}</span>` : ''}</p>
          <div class="preview-stats">
            <div><span class="stat-value">${formatTime(song.duration)}</span><span class="stat-label">Durée</span></div>
            <div><span class="stat-value">${Math.round(song.chart.bpm)}</span><span class="stat-label">BPM</span></div>
            <div><span class="stat-value">${notes.length}</span><span class="stat-label">Tuiles</span></div>
            <div><span class="stat-value">${longs}/${chords}</span><span class="stat-label">Longues/Accords</span></div>
          </div>
          <p class="muted preview-mode">${
            song.demo
              ? 'Parcours préparé à la main.'
              : song.chart.melodic
                ? 'Mélodie détectée : les voies suivent les hauteurs relatives des notes.'
                : 'Mélodie peu nette : le parcours suit les battements et les accents.'
          }</p>
          <div class="preview-records"></div>
          ${
            song.hasAudio
              ? `<button class="btn btn-secondary btn-wide" data-snippet>▶ Écouter un extrait</button>
                 <button class="btn btn-primary btn-wide btn-big" data-play>JOUER</button>`
              : `<p class="warning">Le fichier audio a été supprimé. Le titre et les records sont conservés.</p>
                 <button class="btn btn-primary btn-wide" data-reimport>Réimporter le fichier</button>`
          }
          ${!song.demo && song.hasAudio ? '<button class="btn btn-ghost btn-wide btn-danger-text" data-delete>Supprimer le morceau</button>' : ''}
        </div>`;

      root.querySelector('[data-back]')!.addEventListener('click', () => this.onBack!());
      const recEl = root.querySelector<HTMLElement>('.preview-records')!;
      void db.getRecord(song.id).then((r) => {
        recEl.innerHTML = r
          ? `<div class="records"><div><span class="stat-value">${formatScore(r.bestScore)}</span><span class="stat-label">Meilleur score</span></div>
             <div><span class="stat-value">${r.bestCombo}</span><span class="stat-label">Meilleur combo</span></div>
             <div><span class="stat-value">${Math.round(r.bestProgress * 100)} %</span><span class="stat-label">Progression</span></div></div>`
          : '<p class="muted">Pas encore joué.</p>';
      });

      const snippetBtn = root.querySelector<HTMLButtonElement>('[data-snippet]');
      const refresh = () => {
        if (!snippetBtn) return;
        const playing = audio.current?.snippet && audio.current.songId === song.id;
        snippetBtn.textContent = playing ? '■ Arrêter l’extrait' : '▶ Écouter un extrait';
      };
      offAudio = audio.onChange(refresh);
      snippetBtn?.addEventListener('click', async () => {
        if (audio.current?.snippet && audio.current.songId === song.id) {
          audio.stop();
          return;
        }
        snippetBtn.disabled = true;
        snippetBtn.textContent = 'Préparation…';
        try {
          const buf = await audio.load(song);
          audio.playSnippet(song, buf);
        } catch (e) {
          toast(e instanceof Error ? e.message : 'Lecture impossible');
        } finally {
          snippetBtn.disabled = false;
          refresh();
        }
      });
      root.querySelector('[data-play]')?.addEventListener('click', () => void nav.play(song));
      root.querySelector('[data-reimport]')?.addEventListener('click', () => void startImport());
      root.querySelector('[data-delete]')?.addEventListener('click', async () => {
        const ok = await confirmDialog(
          'Supprimer ce morceau ?',
          `Le fichier audio de « ${esc(song.title)} » sera supprimé de l’appareil. Le titre et tes records seront conservés.`,
          'Supprimer',
          true,
        );
        if (!ok) return;
        if (audio.isPlaying(song.id)) audio.stop();
        audio.forget(song.id);
        await db.deleteAudio(song.id);
        await db.putSong({ ...song, hasAudio: false });
        toast('Audio supprimé — records conservés');
        void nav.library();
      });
    },
  };
}
