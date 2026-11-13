/**
 * Recalage des pistes séparées sur le mix décodé par le navigateur.
 *
 * Le décodeur Android (MediaCodec) et celui du WebView peuvent différer de
 * quelques dizaines de millisecondes (délai d'encodeur MP3/AAC). Les tuiles
 * étant jugées à ±25 ms, on mesure ce décalage (voix + accompagnement ≈ mix)
 * et on le compense.
 */

/** Énergie par paquets de `hop` échantillons (dérivée positive : attaques). */
function envelope(x: Float32Array, start: number, len: number, hop: number): Float32Array {
  const n = Math.floor(len / hop);
  const e = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    let s = 0;
    const a = start + i * hop;
    for (let j = 0; j < hop; j++) {
      const v = x[a + j] ?? 0;
      s += v * v;
    }
    e[i] = Math.sqrt(s / hop);
  }
  const d = new Float32Array(n);
  for (let i = 1; i < n; i++) d[i] = Math.max(0, e[i] - e[i - 1]);
  return d;
}

function corr(a: ArrayLike<number>, b: ArrayLike<number>, lag: number, from: number, to: number): number {
  let s = 0;
  for (let i = from; i < to; i++) {
    const j = i + lag;
    if (j >= 0 && j < b.length) s += a[i] * b[j];
  }
  return s;
}

/**
 * Décalage (en échantillons) tel que stems[i + lag] ≈ mix[i].
 * Renvoie 0 si la mesure n'est pas fiable.
 */
export function measureLag(mix: Float32Array, stems: Float32Array, sr: number, maxLagSec = 0.2): number {
  const n = Math.min(mix.length, stems.length);
  // Extrait de 30 s le plus énergique parmi quelques candidats.
  const win = Math.min(n, Math.round(30 * sr));
  let best = 0;
  let bestE = -1;
  for (let k = 0; k < 5; k++) {
    const a = Math.round(((n - win) * k) / 4);
    let e = 0;
    for (let i = a; i < a + win; i += 64) e += mix[i] * mix[i];
    if (e > bestE) {
      bestE = e;
      best = a;
    }
  }
  const maxLag = Math.round(maxLagSec * sr);
  // 1) Grossier : enveloppes d'attaque (pas de 32 échantillons ≈ 1,5 ms).
  const hop = 32;
  const em = envelope(mix, best, win, hop);
  const es = envelope(stems, best - maxLag - hop, win + 2 * (maxLag + hop), hop);
  const off = Math.round((maxLag + hop) / hop);
  const L = Math.ceil(maxLag / hop);
  let coarse = 0;
  let bestC = -Infinity;
  let sumC = 0;
  let cnt = 0;
  for (let l = -L; l <= L; l++) {
    const c = corr(em, es, l + off, 0, em.length);
    sumC += c;
    cnt++;
    if (c > bestC) {
      bestC = c;
      coarse = l * hop;
    }
  }
  if (!(bestC > 0) || bestC < 1.5 * (sumC / cnt)) return 0;
  // 2) Fin : échantillons, ±2 paquets autour, sur 3 s.
  const fa = best + Math.round(win / 2 - 1.5 * sr);
  const fb = Math.min(n - maxLag - hop * 3, fa + Math.round(3 * sr));
  let fine = coarse;
  let bestF = -Infinity;
  for (let l = coarse - 2 * hop; l <= coarse + 2 * hop; l++) {
    let s = 0;
    for (let i = Math.max(fa, -l); i < fb; i++) s += mix[i] * stems[i + l];
    if (s > bestF) {
      bestF = s;
      fine = l;
    }
  }
  return fine;
}

/** Décale une piste : out[i] = x[i + lag] (zéros hors limites), longueur `len`. */
export function shift(x: Float32Array, lag: number, len: number): Float32Array {
  const out = new Float32Array(len);
  for (let i = 0; i < len; i++) {
    const j = i + lag;
    if (j >= 0 && j < x.length) out[i] = x[j];
  }
  return out;
}

/** Recale voix et accompagnement sur le mix ; renvoie aussi le décalage mesuré (s). */
export function alignStems(
  mix: Float32Array,
  vocals: Float32Array,
  accomp: Float32Array,
  sr: number,
): { vocals: Float32Array; accomp: Float32Array; lagSec: number } {
  const n = Math.min(vocals.length, accomp.length);
  const sum = new Float32Array(n);
  for (let i = 0; i < n; i++) sum[i] = vocals[i] + accomp[i];
  const lag = measureLag(mix, sum, sr);
  return { vocals: shift(vocals, lag, mix.length), accomp: shift(accomp, lag, mix.length), lagSec: lag / sr };
}
