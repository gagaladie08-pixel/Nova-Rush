import { db } from '../storage/db';
import type { SongMeta } from '../game/types';
import { renderDemo } from '../demos/render';

interface Playback {
  songId: string;
  title: string;
  source: AudioBufferSourceNode;
  gain: GainNode;
  /** Temps du contexte auquel la position `offset` est jouée. */
  startCtx: number;
  offset: number;
  duration: number;
  snippet: boolean;
}

type Listener = () => void;

/**
 * Moteur audio unique de l'application (Web Audio).
 * Une seule musique joue à la fois : choisir un autre morceau arrête la précédente.
 */
class AudioEngine {
  ctx: AudioContext | null = null;
  current: Playback | null = null;
  private cache = new Map<string, AudioBuffer>();
  private listeners = new Set<Listener>();
  private delta: number | null = null;
  private snippetTimer: number | undefined;

  ensure(): AudioContext {
    if (!this.ctx) {
      const AC = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.ctx = new AC({ latencyHint: 'interactive' });
    }
    if (this.ctx.state === 'suspended') void this.ctx.resume();
    return this.ctx;
  }

  onChange(fn: Listener) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }
  private emit() {
    this.listeners.forEach((l) => l());
  }

  async load(song: SongMeta): Promise<AudioBuffer> {
    const cached = this.cache.get(song.id);
    if (cached) return cached;
    const ctx = this.ensure();
    let buffer: AudioBuffer;
    if (song.demo) {
      buffer = await renderDemo(song.id, ctx.sampleRate);
    } else {
      const blob = await db.getAudio(song.id);
      if (!blob) throw new Error('Le fichier audio de ce morceau a été supprimé.');
      buffer = await ctx.decodeAudioData(await blob.arrayBuffer());
    }
    this.cache.set(song.id, buffer);
    while (this.cache.size > 3) this.cache.delete(this.cache.keys().next().value as string);
    return buffer;
  }

  forget(id: string) {
    this.cache.delete(id);
  }

  /**
   * Lance la lecture à `offset` secondes dans le morceau, après `delay` secondes.
   * Renvoie le temps de contexte correspondant à `offset`.
   */
  play(song: { id: string; title: string }, buffer: AudioBuffer, offset = 0, delay = 0, snippetDur = 0): number {
    const ctx = this.ensure();
    this.stop(false);
    const source = ctx.createBufferSource();
    source.buffer = buffer;
    const gain = ctx.createGain();
    source.connect(gain).connect(ctx.destination);
    const when = ctx.currentTime + delay;
    if (snippetDur > 0) {
      gain.gain.setValueAtTime(0, when);
      gain.gain.linearRampToValueAtTime(1, when + 0.6);
      gain.gain.setValueAtTime(1, when + snippetDur - 1.2);
      gain.gain.linearRampToValueAtTime(0, when + snippetDur);
      source.start(when, offset, snippetDur);
    } else {
      source.start(when, offset);
    }
    const pb: Playback = {
      songId: song.id,
      title: song.title,
      source,
      gain,
      startCtx: when,
      offset,
      duration: buffer.duration,
      snippet: snippetDur > 0,
    };
    source.onended = () => {
      if (this.current === pb) {
        this.current = null;
        this.emit();
      }
    };
    this.current = pb;
    this.emit();
    return when;
  }

  /** Extrait d'écoute (~15 s) pris vers le premier tiers du morceau. */
  playSnippet(song: { id: string; title: string }, buffer: AudioBuffer) {
    const dur = Math.min(15, buffer.duration);
    const start = Math.max(0, Math.min(buffer.duration - dur, buffer.duration * 0.33));
    this.play(song, buffer, start, 0.05, dur);
  }

  stop(emit = true) {
    window.clearTimeout(this.snippetTimer);
    const pb = this.current;
    if (!pb) return;
    this.current = null;
    try {
      pb.source.onended = null;
      pb.source.stop();
    } catch {
      /* déjà arrêtée */
    }
    pb.source.disconnect();
    if (emit) this.emit();
  }

  isPlaying(songId?: string): boolean {
    return !!this.current && (songId === undefined || this.current.songId === songId);
  }

  /** Temps de contexte effectivement entendu à l'instant `perfMs` (performance.now). */
  heardCtxTime(perfMs = performance.now()): number {
    const ctx = this.ensure();
    let measured: number | null = null;
    const ts = ctx.getOutputTimestamp?.();
    if (ts && ts.contextTime && ts.performanceTime && ts.contextTime > 0) {
      measured = ts.contextTime - ts.performanceTime / 1000;
    }
    if (measured === null || Math.abs(measured + performance.now() / 1000 - ctx.currentTime) > 0.5) {
      const lat = (ctx as AudioContext & { outputLatency?: number }).outputLatency || ctx.baseLatency || 0;
      measured = ctx.currentTime - lat - performance.now() / 1000;
    }
    // Lissage pour éviter les à-coups de l'horloge audio, recalage si dérive.
    if (this.delta === null || Math.abs(measured - this.delta) > 0.04) this.delta = measured;
    else this.delta += (measured - this.delta) * 0.05;
    return perfMs / 1000 + this.delta;
  }

  resetClock() {
    this.delta = null;
  }
}

export const audio = new AudioEngine();
