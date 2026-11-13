import { audio } from '../audio/engine';
import { onVoiceChange, voiceLive } from '../analysis/voiceQueue';
import { demoSongs } from '../demos/songs';
import { LEVELS, LEVEL_LABEL, type Level, type SongMeta, type SongRecord } from '../game/types';
import { db } from '../storage/db';
import { coverFor } from './covers';
import { esc, formatScore, formatTime } from './dom';
import { startImport } from './importer';
import { nav } from './nav';
import type { Screen } from './router';
import { uiState } from './state';


/** Pastille de l'analyse avancée (voix isolée). */
export function voiceBadgeText(song: SongMeta): string {
  const v = voiceLive(song.id);
  if (v) {
    if (v.state === 'queued') return '🎤 en attente';
    if (v.state === 'finishing') return '🎤 finalisation';
    return `🎤 ${Math.floor(v.progress * 100)} %`;
  }
  if (song.voice?.state === 'done' && song.chart.source === 'voice') return '🎤 voix';
  return '';
}

function voiceBadge(song: SongMeta): string {
  const t = voiceBadgeText(song);
  return t ? `<span class="badge badge-voice" data-voice="${esc(song.id)}">${t}</span>` : '';
}

export function songCard(song: SongMeta, rec: SongRecord | undefined): string {
  const best = rec?.bestScore ?? 0;
  const disabled = !song.hasAudio;
  return `
    <article class="song-card ${disabled ? 'is-disabled' : ''}" data-id="${esc(song.id)}">
      <img class="song-cover" src="${coverFor(song)}" alt="" loading="lazy" />
      <div class="song-info">
        <h3 class="song-title">${esc(song.title)}</h3>
        <p class="song-artist">${esc(song.artist)}</p>
        <p class="song-meta">
          <span class="badge badge-${song.level}">${LEVEL_LABEL[song.level]}</span>
          ${voiceBadge(song)}
          ${disabled ? '<span class="muted">Audio supprimé</span>' : `<span class="muted">${best ? `Record ${formatScore(best)}` : formatTime(song.duration)}</span>`}
        </p>
      </div>
      <button class="btn btn-play" data-play="${esc(song.id)}" ${disabled ? 'disabled' : ''} aria-label="Jouer ${esc(song.title)}">▶</button>
    </article>`;
}

export function libraryScreen(): Screen {
  let offAudio: (() => void) | undefined;
  let offVoice: (() => void) | undefined;
  let songs: SongMeta[] = [];
  let records = new Map<string, SongRecord>();
  const demos = demoSongs();

  return {
    name: 'library',
    dispose() {
      offAudio?.();
      offVoice?.();
    },
    render(root) {
      root.innerHTML = `
        <header class="topbar">
          <h1 class="logo">NOVA<span>RUSH</span></h1>
          <div class="topbar-actions">
            <button class="btn btn-primary btn-import">+ Importer</button>
            <button class="icon-btn" data-go="help" aria-label="Aide">?</button>
            <button class="icon-btn" data-go="settings" aria-label="Paramètres">⚙</button>
          </div>
        </header>
        <div class="now-playing hidden"></div>
        <section class="lib-section">
          <nav class="tabs" role="tablist">
            ${LEVELS.map((l) => `<button class="tab" role="tab" data-tab="${l}">${LEVEL_LABEL[l]}<span class="tab-count"></span></button>`).join('')}
          </nav>
          <div class="tab-panel"></div>
        </section>
        <section class="lib-section">
          <h2 class="section-title">Démos <span class="muted">· styles variés, parcours préparés à la main</span></h2>
          <div class="demo-list">
            ${demos.map((d) => songCard(d, undefined)).join('')}
          </div>
        </section>
        <footer class="lib-footer muted">Ta musique, ses analyses et tes scores restent sur cet appareil.</footer>`;

      const panel = root.querySelector<HTMLElement>('.tab-panel')!;
      const nowPlaying = root.querySelector<HTMLElement>('.now-playing')!;

      const renderNowPlaying = () => {
        const cur = audio.current;
        if (cur && !cur.snippet) {
          nowPlaying.classList.remove('hidden');
          nowPlaying.innerHTML = `<span class="np-icon">♪</span><span class="np-title">${esc(cur.title)}</span>
            <button class="btn btn-small" data-stop>Arrêter la musique</button>`;
          nowPlaying.querySelector('[data-stop]')!.addEventListener('click', () => audio.stop());
        } else {
          nowPlaying.classList.add('hidden');
          nowPlaying.innerHTML = '';
        }
      };
      offAudio = audio.onChange(renderNowPlaying);
      renderNowPlaying();

      const renderTabs = (animate = false) => {
        root.querySelectorAll<HTMLButtonElement>('.tab').forEach((b) => {
          const l = b.dataset.tab as Level;
          b.classList.toggle('active', l === uiState.activeTab);
          b.setAttribute('aria-selected', String(l === uiState.activeTab));
          const n = songs.filter((s) => s.level === l).length;
          b.querySelector('.tab-count')!.textContent = n ? ` ${n}` : '';
        });
        const list = songs
          .filter((s) => s.level === uiState.activeTab)
          .sort((a, b) => (records.get(b.id)?.bestScore ?? 0) - (records.get(a.id)?.bestScore ?? 0) || b.createdAt - a.createdAt);
        if (songs.length === 0) {
          panel.innerHTML = `
            <div class="empty">
              <div class="empty-icon">♫</div>
              <p><strong>Ta bibliothèque est vide.</strong></p>
              <p class="muted">Importe une musique (MP3, M4A ou WAV) ou essaie les démos ci-dessous.</p>
              <button class="btn btn-primary btn-import">+ Importer une musique</button>
            </div>`;
        } else if (list.length === 0) {
          panel.innerHTML = `<p class="empty-small muted">Aucun morceau classé « ${LEVEL_LABEL[uiState.activeTab]} » pour l’instant.</p>`;
        } else {
          panel.innerHTML = list.map((s) => songCard(s, records.get(s.id))).join('');
        }
        panel.querySelectorAll('.btn-import').forEach((b) => b.addEventListener('click', () => startImport()));
        if (animate) {
          panel.classList.remove('tab-switch');
          void panel.offsetWidth;
          panel.classList.add('tab-switch');
        }
      };

      root.querySelectorAll<HTMLButtonElement>('.tab').forEach((b) =>
        b.addEventListener('click', () => {
          uiState.activeTab = b.dataset.tab as Level;
          renderTabs(true);
        }),
      );
      root.querySelectorAll('.btn-import').forEach((b) => b.addEventListener('click', () => startImport()));
      root.querySelector('[data-go="settings"]')!.addEventListener('click', () => nav.settings());
      root.querySelector('[data-go="help"]')!.addEventListener('click', () => nav.help());

      root.addEventListener('click', (e) => {
        const target = e.target as HTMLElement;
        const playBtn = target.closest<HTMLElement>('[data-play]');
        const card = target.closest<HTMLElement>('.song-card');
        if (!card) return;
        const song = songs.find((s) => s.id === card.dataset.id) ?? demos.find((s) => s.id === card.dataset.id);
        if (!song) return;
        if (playBtn) {
          e.stopPropagation();
          if (song.hasAudio) void nav.play(song);
        } else {
          void nav.preview(song);
        }
      });

      const demoList = root.querySelector<HTMLElement>('.demo-list')!;
      // Analyse avancée : progression mise à jour en place, rechargement quand l'état change.
      let voiceKey = '';
      const keyOf = () => songs.map((x) => `${x.id}:${voiceLive(x.id)?.state ?? x.voice?.state ?? ''}`).join('|');
      offVoice = onVoiceChange(() => {
        if (keyOf() !== voiceKey) {
          void db.listSongs().then((s) => {
            songs = s;
            voiceKey = keyOf();
            renderTabs();
          });
          return;
        }
        for (const song of songs) {
          const el = panel.querySelector<HTMLElement>(`[data-voice="${CSS.escape(song.id)}"]`);
          if (el) el.textContent = voiceBadgeText(song);
        }
      });
      void Promise.all([db.listSongs(), db.listRecords()])
        .then(([s, r]) => {
          voiceKey = '';
          songs = s;
          voiceKey = keyOf();
          records = new Map(r.map((x) => [x.id, x]));
          renderTabs();
          demoList.innerHTML = demos.map((d) => songCard(d, records.get(d.id))).join('');
        })
        .catch(() => {
          panel.innerHTML = '<p class="muted">Le stockage local est indisponible sur cet appareil.</p>';
        });
      renderTabs();
    },
  };
}
