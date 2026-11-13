import { audio } from '../audio/engine';
import { previewStart } from '../game/sections';
import { cancelVoice, onVoiceChange, voiceLive } from '../analysis/voiceQueue';
import { LEVEL_LABEL, type SongMeta } from '../game/types';
import { db } from '../storage/db';
import { coverFor } from './covers';
import { confirmDialog, esc, formatScore, formatTime, toast } from './dom';
import { startImport } from './importer';
import { nav } from './nav';
import type { Screen } from './router';

export function previewScreen(song: SongMeta): Screen {
  let offAudio: (() => void) | undefined;
  let offVoice: (() => void) | undefined;
  return {
    name: 'preview',
    onBack() {
      if (audio.current?.snippet) audio.stop();
      void nav.library();
      return true;
    },
    dispose() {
      offAudio?.();
      offVoice?.();
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
              : song.chart.source === 'voice'
                ? 'Voix isolée par l’IA de l’appareil : les tuiles suivent la mélodie chantée, l’accompagnement remplit les passages instrumentaux.'
                : song.chart.melodic
                ? 'Mélodie détectée : les voies suivent les hauteurs relatives des notes.'
                : 'Mélodie peu nette : le parcours suit les battements et les accents.'
          }</p>
          <p class="muted preview-voice"></p>
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

      // État de l'analyse avancée.
      const voiceEl = root.querySelector<HTMLElement>('.preview-voice')!;
      const renderVoice = () => {
        const v = voiceLive(song.id);
        if (v) {
          voiceEl.textContent =
            v.state === 'queued'
              ? 'Analyse avancée (voix isolée) en attente — tu peux déjà jouer avec le parcours actuel.'
              : v.state === 'finishing'
                ? 'Analyse avancée : finalisation du parcours…'
                : `Analyse avancée en cours : ${Math.floor(v.progress * 100)} %${v.eta ? ` · environ ${Math.max(1, Math.ceil(v.eta / 60))} min` : ''}. Tu peux déjà jouer avec le parcours actuel.`;
        } else if (song.voice?.state === 'failed') {
          voiceEl.textContent =
            song.voice.reason === 'trop lent'
              ? 'Analyse avancée trop longue sur cet appareil : parcours classique conservé.'
              : 'Analyse avancée impossible : parcours classique conservé.';
        } else voiceEl.textContent = '';
      };
      renderVoice();
      offVoice = onVoiceChange(() => {
        const was = voiceLive(song.id);
        renderVoice();
        // Analyse terminée (ou échouée) : on recharge l'aperçu avec le nouveau parcours.
        if (!was) {
          void db.getSong(song.id).then((fresh) => {
            if (fresh && fresh.voice?.state !== song.voice?.state && fresh.hasAudio) void nav.preview(fresh);
          });
        }
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
          audio.playSnippet(song, buf, previewStart(song.chart));
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
        await cancelVoice(song.id);
        await db.deleteAudio(song.id);
        await db.putSong({ ...(await db.getSong(song.id)) ?? song, hasAudio: false, voice: undefined });
        toast('Audio supprimé — records conservés');
        void nav.library();
      });
    },
  };
}
