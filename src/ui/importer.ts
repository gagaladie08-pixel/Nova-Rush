import { ImportError, decodeForAnalysis, runAnalysis } from '../analysis/client';
import { pictureToDataUrl, readMetadata } from '../audio/metadata';
import { audio } from '../audio/engine';
import { LEVEL_LABEL, type SongMeta } from '../game/types';
import { db } from '../storage/db';
import { alertDialog, esc, modal } from './dom';
import { nav } from './nav';
import { uiState } from './state';

const ACCEPT = '.mp3,.m4a,.wav,audio/mpeg,audio/mp4,audio/x-m4a,audio/m4a,audio/wav,audio/x-wav,audio/wave';
const EXT = /\.(mp3|m4a|wav)$/i;
const MIME = /^audio\/(mpeg|mp3|mp4|x-m4a|m4a|aac|wav|x-wav|wave|vnd\.wave)$/i;

function pickFile(): Promise<File | null> {
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = ACCEPT;
    input.style.display = 'none';
    document.body.appendChild(input);
    input.addEventListener('change', () => {
      resolve(input.files?.[0] ?? null);
      input.remove();
    });
    input.addEventListener('cancel', () => {
      resolve(null);
      input.remove();
    });
    input.click();
  });
}

function titleFromFile(name: string): string {
  return name.replace(/\.[^.]+$/, '').replace(/[_]+/g, ' ').trim() || 'Sans titre';
}

export async function startImport(): Promise<void> {
  const file = await pickFile();
  if (!file) return;
  if (!EXT.test(file.name) && !MIME.test(file.type)) {
    await alertDialog(
      'Format non pris en charge',
      `« ${esc(file.name)} » n’est pas un fichier MP3, M4A ou WAV. Choisis un fichier audio dans l’un de ces formats.`,
    );
    return;
  }

  let cancelled = false;
  const controller = new AbortController();
  const m = modal(`
    <h2>Import en cours</h2>
    <p class="import-file">${esc(file.name)}</p>
    <div class="progress"><div class="progress-bar"></div></div>
    <p class="progress-label muted">Copie du fichier…</p>
    <div class="modal-actions"><button class="btn btn-ghost" data-cancel>Annuler</button></div>`);
  const bar = m.el.querySelector<HTMLElement>('.progress-bar')!;
  const label = m.el.querySelector<HTMLElement>('.progress-label')!;
  const set = (p: number, text: string) => {
    bar.style.width = `${Math.round(p * 100)}%`;
    label.textContent = `${text} ${Math.round(p * 100)} %`;
  };
  m.el.querySelector('[data-cancel]')!.addEventListener('click', () => {
    cancelled = true;
    controller.abort();
    m.close();
  });

  try {
    const data = await file.arrayBuffer();
    if (cancelled) return;
    set(0.03, 'Lecture des informations…');
    const meta = readMetadata(data);
    const cover = meta.picture ? await pictureToDataUrl(meta.picture) : undefined;
    set(0.06, 'Décodage audio…');
    const { samples, duration } = await decodeForAnalysis(data);
    if (cancelled) return;
    const title = meta.title ?? titleFromFile(file.name);
    const artist = meta.artist ?? 'Artiste inconnu';
    const chart = await runAnalysis(
      samples,
      `${title}|${artist}|${Math.round(duration)}`,
      (p, text) => set(0.08 + p * 0.9, text),
      controller.signal,
    );
    if (cancelled) return;
    set(0.99, 'Enregistrement…');

    // Si ce morceau avait été supprimé (audio retiré), on garde son identifiant et ses records.
    const existing = (await db.listSongs()).find(
      (s) => !s.hasAudio && s.title.toLowerCase() === title.toLowerCase() && s.artist.toLowerCase() === artist.toLowerCase(),
    );
    const id = existing?.id ?? `song-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
    const song: SongMeta = {
      id,
      title,
      artist,
      cover,
      level: chart.level,
      chart,
      duration,
      createdAt: existing?.createdAt ?? Date.now(),
      hasAudio: true,
      fileName: file.name,
    };
    // L'application conserve sa propre copie du morceau.
    await db.putAudio(id, new Blob([data], { type: file.type || 'audio/mpeg' }));
    await db.putSong(song);
    void db.requestPersistence();
    audio.forget(id);
    m.close();
    uiState.activeTab = song.level;
    await nav.preview(song);
    const toastMsg = `Analyse terminée : ${LEVEL_LABEL[chart.level]}${chart.melodic ? '' : ' (parcours rythmique)'}`;
    const { toast } = await import('./dom');
    toast(toastMsg);
  } catch (err) {
    if (cancelled) return;
    m.close();
    const msg =
      err instanceof ImportError
        ? err.message
        : err instanceof DOMException && err.name === 'QuotaExceededError'
          ? 'Il n’y a plus assez d’espace de stockage sur l’appareil.'
          : 'Une erreur inattendue est survenue.';
    await alertDialog('Import impossible', `${esc(msg)}<br><br>Le fichier n’a pas été ajouté à la bibliothèque.`);
  }
}
