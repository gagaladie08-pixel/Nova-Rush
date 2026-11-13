import { Capacitor, registerPlugin, type PluginListenerHandle } from '@capacitor/core';

/** Tâche de séparation côté Android (voir SeparationPlugin.java). */
export interface NativeJob {
  id: string;
  title?: string;
  state: 'uploading' | 'queued' | 'running' | 'done' | 'error';
  progress: number;
  /** Secondes restantes estimées. */
  eta?: number;
  error?: string;
  vocals?: string;
  accomp?: string;
  /** Temps et mesures de l'IA Beat This! (float32 : nb, nd, temps…, premiers temps…). */
  beats?: string;
  /** Hauteur de la voix par l'IA CREPE (float32 : n, f0…, confiance…). */
  pitch?: string;
  /** Batterie isolée par Demucs (float32 mono, même fréquence que la voix). */
  drums?: string;
  /** Basic Pitch sur la basse et les autres instruments (octets : attaques puis notes, images × 88). */
  bpBass?: string;
  bpOther?: string;
  sampleRate?: number;
  seconds?: number;
  /** Voix, accompagnement, temps et hauteur prêts ; instruments (Demucs) encore en cours. */
  partial?: boolean;
  /** Analyse complète (batterie, basse, instruments). */
  full?: boolean;
  /** Mesures : secondes par étape, part du calcul sur les cœurs rapides. */
  diag?: AnalysisDiag;
}

export interface AnalysisDiag {
  stages?: Record<string, number>;
  big?: number;
  cores?: number;
}

interface VoiceSeparationPlugin {
  isAvailable(): Promise<{ available: boolean; cores: number }>;
  requestNotifications(): Promise<{ granted: boolean }>;
  createJob(o: { id: string; title: string; full?: boolean }): Promise<void>;
  appendInput(o: { id: string; data: string }): Promise<void>;
  enqueue(o: { id: string }): Promise<void>;
  list(): Promise<{ jobs: NativeJob[] }>;
  resume(): Promise<void>;
  cancel(o: { id: string }): Promise<void>;
  remove(o: { id: string }): Promise<void>;
  setGameActive(o: { active: boolean }): Promise<void>;
  addListener(event: 'job', fn: (job: NativeJob) => void): Promise<PluginListenerHandle>;
}

export const VoiceSeparation = registerPlugin<VoiceSeparationPlugin>('VoiceSeparation');

export const nativeSeparationPossible = () =>
  Capacitor.isNativePlatform() && Capacitor.isPluginAvailable('VoiceSeparation');

async function readBytes(path: string): Promise<ArrayBuffer> {
  const url = Capacitor.convertFileSrc(path.startsWith('file://') ? path : `file://${path}`);
  const res = await fetch(url);
  if (!res.ok) throw new Error(`lecture impossible (${res.status})`);
  return res.arrayBuffer();
}

/** Lit un fichier float32 produit par le service (servi par le WebView, sans base64). */
export async function readFloatFile(path: string): Promise<Float32Array> {
  return new Float32Array(await readBytes(path));
}

/** Lit un fichier d'octets produit par le service. */
export async function readByteFile(path: string): Promise<Uint8Array> {
  return new Uint8Array(await readBytes(path));
}

function toBase64(bytes: Uint8Array): string {
  let s = '';
  const step = 0x8000;
  for (let i = 0; i < bytes.length; i += step) s += String.fromCharCode(...bytes.subarray(i, i + step));
  return btoa(s);
}

/** Envoie le fichier audio au service par morceaux (le pont Capacitor ne transporte que du texte). */
export async function uploadInput(id: string, blob: Blob): Promise<void> {
  const CHUNK = 768 * 1024;
  for (let a = 0; a < blob.size; a += CHUNK) {
    const part = new Uint8Array(await blob.slice(a, a + CHUNK).arrayBuffer());
    await VoiceSeparation.appendInput({ id, data: toBase64(part) });
  }
}
