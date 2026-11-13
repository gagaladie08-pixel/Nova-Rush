import type { SongMeta, SongRecord } from '../game/types';

/**
 * Stockage local (IndexedDB) : métadonnées et parcours, copie audio, records.
 * Rien ne quitte l'appareil.
 */
const DB_NAME = 'nova-rush';
const VERSION = 1;
let dbPromise: Promise<IDBDatabase> | null = null;

function open(): Promise<IDBDatabase> {
  dbPromise ??= new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('songs')) db.createObjectStore('songs', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('audio')) db.createObjectStore('audio');
      if (!db.objectStoreNames.contains('records')) db.createObjectStore('records', { keyPath: 'id' });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

function tx<T>(store: string, mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T> | void): Promise<T> {
  return open().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const t = db.transaction(store, mode);
        const s = t.objectStore(store);
        const req = fn(s);
        let result: T;
        if (req) req.onsuccess = () => (result = req.result);
        t.oncomplete = () => resolve(result);
        t.onerror = () => reject(t.error);
        t.onabort = () => reject(t.error ?? new Error('Transaction annulée'));
      }),
  );
}

export const db = {
  async requestPersistence() {
    try {
      await navigator.storage?.persist?.();
    } catch {
      /* facultatif */
    }
  },
  listSongs: () => tx<SongMeta[]>('songs', 'readonly', (s) => s.getAll()),
  getSong: (id: string) => tx<SongMeta | undefined>('songs', 'readonly', (s) => s.get(id)),
  putSong: (song: SongMeta) => tx('songs', 'readwrite', (s) => s.put(song)),
  getAudio: (id: string) => tx<Blob | undefined>('audio', 'readonly', (s) => s.get(id)),
  putAudio: (id: string, blob: Blob) => tx('audio', 'readwrite', (s) => s.put(blob, id)),
  deleteAudio: (id: string) => tx('audio', 'readwrite', (s) => s.delete(id)),
  listRecords: () => tx<SongRecord[]>('records', 'readonly', (s) => s.getAll()),
  getRecord: (id: string) => tx<SongRecord | undefined>('records', 'readonly', (s) => s.get(id)),
  putRecord: (r: SongRecord) => tx('records', 'readwrite', (s) => s.put(r)),
};

/** Met à jour le meilleur score / combo ; renvoie ce qui a été battu. */
export async function saveResult(
  id: string,
  score: number,
  combo: number,
  progress: number,
  completed: boolean,
): Promise<{ record: SongRecord; newScore: boolean; newCombo: boolean }> {
  const prev = (await db.getRecord(id)) ?? { id, bestScore: 0, bestCombo: 0, bestProgress: 0, completed: false };
  const newScore = score > prev.bestScore;
  const newCombo = combo > prev.bestCombo;
  const record: SongRecord = {
    id,
    bestScore: Math.max(prev.bestScore, score),
    bestCombo: Math.max(prev.bestCombo, combo),
    bestProgress: Math.max(prev.bestProgress, progress),
    completed: prev.completed || completed,
  };
  await db.putRecord(record);
  return { record, newScore, newCombo };
}
