/**
 * Analyse avancée : voix isolée par l'IA, sur le téléphone.
 *
 * 1. À l'import, le morceau est confié au service Android (file d'attente,
 *    un morceau à la fois, notification, continue appli fermée).
 * 2. Quand la séparation est finie, l'appli lit voix et accompagnement,
 *    refait le parcours (suivi de la voix) et remplace l'ancien.
 * En cas d'échec, le parcours classique reste en place.
 */
import type { SongMeta, VoiceAnalysis } from '../game/types';
import { db } from '../storage/db';
import { ANALYSIS_RATE, decodeForAnalysis, runAnalysisFull } from './client';
import { parseBeats, parsePitch } from './beatsFile';
import {
  VoiceSeparation,
  nativeSeparationPossible,
  readByteFile,
  readFloatFile,
  uploadInput,
  type NativeJob,
} from '../native/separation';

export interface VoiceLive {
  state: VoiceAnalysis['state'] | 'finishing';
  progress: number;
  eta?: number;
}

const live = new Map<string, VoiceLive>();
const listeners = new Set<() => void>();
let available: Promise<boolean> | null = null;
let notifAsked = false;
let finishing: Promise<void> = Promise.resolve();
let busy: () => boolean = () => false;

function emit() {
  for (const fn of listeners) fn();
}

/** État en direct (progression) d'un morceau, s'il est en cours d'analyse avancée. */
export function voiceLive(id: string): VoiceLive | undefined {
  return live.get(id);
}

export function onVoiceChange(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function isVoiceAvailable(): Promise<boolean> {
  available ??= nativeSeparationPossible()
    ? VoiceSeparation.isAvailable()
        .then((r) => r.available)
        .catch(() => false)
    : Promise.resolve(false);
  return available;
}

/** Signale qu'une partie est en cours (la séparation ralentit pour garder le jeu fluide). */
export function setGameActive(active: boolean) {
  void isVoiceAvailable().then((ok) => {
    if (ok) void VoiceSeparation.setGameActive({ active }).catch(() => {});
  });
}

async function setSongVoice(id: string, voice: VoiceAnalysis | undefined, patch?: Partial<SongMeta>) {
  const song = await db.getSong(id);
  if (!song) return;
  await db.putSong({ ...song, ...patch, voice });
}

/** À appeler au démarrage : reprend les analyses interrompues et finalise celles terminées. */
export async function initVoiceQueue(isBusy: () => boolean): Promise<void> {
  busy = isBusy;
  if (!(await isVoiceAvailable())) return;
  await VoiceSeparation.addListener('job', (job) => void handle(job));
  try {
    await VoiceSeparation.resume();
    const { jobs } = await VoiceSeparation.list();
    for (const job of jobs) await handle(job);
  } catch {
    /* le service se relancera au prochain import */
  }
}

/** Confie un morceau importé au service (ne bloque pas l'import). */
export async function enqueueVoice(song: SongMeta, blob: Blob): Promise<boolean> {
  if (!(await isVoiceAvailable())) return false;
  try {
    if (!notifAsked) {
      notifAsked = true;
      await VoiceSeparation.requestNotifications().catch(() => {});
    }
    await VoiceSeparation.createJob({ id: song.id, title: song.title });
    await uploadInput(song.id, blob);
    live.set(song.id, { state: 'queued', progress: 0 });
    await setSongVoice(song.id, { state: 'queued' });
    await VoiceSeparation.enqueue({ id: song.id });
    emit();
    return true;
  } catch {
    live.delete(song.id);
    await VoiceSeparation.remove({ id: song.id }).catch(() => {});
    await setSongVoice(song.id, { state: 'failed', reason: 'envoi impossible' });
    emit();
    return false;
  }
}

/** Annule l'analyse avancée d'un morceau (suppression de l'audio). */
export async function cancelVoice(id: string): Promise<void> {
  live.delete(id);
  if (await isVoiceAvailable()) await VoiceSeparation.remove({ id }).catch(() => {});
  emit();
}

async function handle(job: NativeJob): Promise<void> {
  const id = job.id;
  if (job.state === 'uploading') return;
  const song = await db.getSong(id);
  if (!song || !song.hasAudio) {
    // Morceau supprimé entre-temps.
    live.delete(id);
    await VoiceSeparation.remove({ id }).catch(() => {});
    emit();
    return;
  }
  if (job.state === 'queued' || job.state === 'running') {
    const prev = live.get(id);
    live.set(id, { state: job.state, progress: job.progress ?? 0, eta: job.eta });
    if (song.voice?.state !== job.state && (!prev || prev.state !== job.state)) await setSongVoice(id, { state: job.state });
    emit();
    return;
  }
  if (job.state === 'error') {
    live.delete(id);
    await setSongVoice(id, { state: 'failed', reason: job.error ?? 'erreur' });
    await VoiceSeparation.remove({ id }).catch(() => {});
    emit();
    return;
  }
  if (job.state === 'done') {
    if (live.get(id)?.state === 'finishing') return;
    live.set(id, { state: 'finishing', progress: 1 });
    emit();
    finishing = finishing.then(() => finish(job)).catch(() => {});
  }
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function readPitch(path: string | undefined) {
  if (!path) return undefined;
  try {
    return parsePitch(await readFloatFile(path));
  } catch {
    return undefined; // suivi classique de la voix
  }
}

async function readBeats(path: string | undefined) {
  if (!path) return undefined;
  try {
    return parseBeats(await readFloatFile(path));
  } catch {
    return undefined; // rythme classique
  }
}

/** Fichier facultatif d'une IA : absent ou illisible → la source est simplement ignorée. */
async function optional<T>(path: string | undefined, read: (p: string) => Promise<T>): Promise<T | undefined> {
  if (!path) return undefined;
  try {
    return await read(path);
  } catch {
    return undefined;
  }
}

/** Refait le parcours avec les pistes séparées (une seule finalisation à la fois). */
async function finish(job: NativeJob): Promise<void> {
  const id = job.id;
  // Pas de calcul lourd pendant une partie.
  while (busy()) await sleep(3000);
  try {
    const song = await db.getSong(id);
    const blob = await db.getAudio(id);
    if (!song || !blob || !job.vocals || !job.accomp) throw new Error('données manquantes');
    if ((job.sampleRate ?? ANALYSIS_RATE) !== ANALYSIS_RATE) throw new Error('fréquence inattendue');
    const [{ samples }, vocals, accomp, rhythm, pitch, drums, bpBass, bpOther] = await Promise.all([
      decodeForAnalysis(await blob.arrayBuffer()),
      readFloatFile(job.vocals),
      readFloatFile(job.accomp),
      readBeats(job.beats),
      readPitch(job.pitch),
      optional(job.drums, readFloatFile),
      optional(job.bpBass, readByteFile),
      optional(job.bpOther, readByteFile),
    ]);
    const { chart, lagSec } = await runAnalysisFull(
      samples,
      `${song.title}|${song.artist}|${Math.round(song.duration)}`,
      () => {},
      undefined,
      { vocals, accomp },
      rhythm,
      pitch,
      { drums, bpBass, bpOther },
    );
    const fresh = await db.getSong(id);
    if (!fresh || !fresh.hasAudio) throw new Error('morceau supprimé');
    await db.putSong({
      ...fresh,
      chart,
      level: chart.level,
      voice: {
        state: 'done',
        seconds: job.seconds ? Math.round(job.seconds) : undefined,
        ...(lagSec !== undefined ? { lagSec: Math.round(lagSec * 1000) / 1000 } : {}),
      },
    });
    live.delete(id);
    emit();
    const { toast } = await import('../ui/dom');
    toast(`« ${fresh.title} » : parcours mis à jour${chart.source === 'voice' ? ' (suit la voix)' : ''}`);
  } catch (e) {
    live.delete(id);
    await setSongVoice(id, { state: 'failed', reason: e instanceof Error ? e.message : 'erreur' });
    emit();
  } finally {
    await VoiceSeparation.remove({ id }).catch(() => {});
  }
}
