/** Lecture minimale des métadonnées ID3v2 (MP3) et iTunes (M4A). */

export interface AudioMetadata {
  title?: string;
  artist?: string;
  picture?: { mime: string; data: Uint8Array };
}

const latin1 = new TextDecoder('latin1');
const utf8 = new TextDecoder('utf-8');
const utf16 = new TextDecoder('utf-16le');
const utf16be = (() => {
  try {
    return new TextDecoder('utf-16be');
  } catch {
    return null;
  }
})();

function clean(s: string | undefined): string | undefined {
  const v = s?.replace(/\0/g, ' ').trim();
  return v ? v : undefined;
}

function decodeText(enc: number, bytes: Uint8Array): string {
  if (enc === 0) return latin1.decode(bytes);
  if (enc === 3) return utf8.decode(bytes);
  if (enc === 1) {
    if (bytes[0] === 0xfe && bytes[1] === 0xff && utf16be) return utf16be.decode(bytes.subarray(2));
    if (bytes[0] === 0xff && bytes[1] === 0xfe) return utf16.decode(bytes.subarray(2));
    return utf16.decode(bytes);
  }
  if (enc === 2 && utf16be) return utf16be.decode(bytes);
  return latin1.decode(bytes);
}

/** Position de fin d'une chaîne terminée par zéro selon l'encodage. */
function findTerminator(bytes: Uint8Array, start: number, enc: number): number {
  if (enc === 1 || enc === 2) {
    for (let i = start; i + 1 < bytes.length; i += 2) if (bytes[i] === 0 && bytes[i + 1] === 0) return i;
    return bytes.length;
  }
  for (let i = start; i < bytes.length; i++) if (bytes[i] === 0) return i;
  return bytes.length;
}

function synchsafe(b: Uint8Array, o: number): number {
  return ((b[o] & 0x7f) << 21) | ((b[o + 1] & 0x7f) << 14) | ((b[o + 2] & 0x7f) << 7) | (b[o + 3] & 0x7f);
}

function removeUnsync(b: Uint8Array): Uint8Array {
  const out: number[] = [];
  for (let i = 0; i < b.length; i++) {
    out.push(b[i]);
    if (b[i] === 0xff && b[i + 1] === 0x00) i++;
  }
  return Uint8Array.from(out);
}

function parseId3(buf: Uint8Array): AudioMetadata {
  const meta: AudioMetadata = {};
  if (buf.length < 10 || buf[0] !== 0x49 || buf[1] !== 0x44 || buf[2] !== 0x33) return meta;
  const ver = buf[3];
  const flags = buf[5];
  const size = synchsafe(buf, 6);
  let tag = buf.subarray(10, Math.min(buf.length, 10 + size));
  if (flags & 0x80 && ver < 4) tag = removeUnsync(tag);
  let p = 0;
  if (flags & 0x40 && ver >= 3) {
    const extSize = ver === 4 ? synchsafe(tag, 0) : ((tag[0] << 24) | (tag[1] << 16) | (tag[2] << 8) | tag[3]) + 4;
    p += extSize;
  }
  const idLen = ver === 2 ? 3 : 4;
  const headLen = ver === 2 ? 6 : 10;
  while (p + headLen <= tag.length) {
    const id = latin1.decode(tag.subarray(p, p + idLen));
    if (!/^[A-Z0-9]+$/.test(id)) break;
    let fsize: number;
    if (ver === 2) fsize = (tag[p + 3] << 16) | (tag[p + 4] << 8) | tag[p + 5];
    else if (ver === 4) fsize = synchsafe(tag, p + 4);
    else fsize = ((tag[p + 4] << 24) | (tag[p + 5] << 16) | (tag[p + 6] << 8) | tag[p + 7]) >>> 0;
    const body = tag.subarray(p + headLen, p + headLen + fsize);
    p += headLen + fsize;
    if (fsize <= 0) continue;
    if (id === 'TIT2' || id === 'TT2') meta.title ??= clean(decodeText(body[0], body.subarray(1)));
    else if (id === 'TPE1' || id === 'TP1') meta.artist ??= clean(decodeText(body[0], body.subarray(1)));
    else if ((id === 'APIC' || id === 'PIC') && !meta.picture) {
      const enc = body[0];
      let q = 1;
      let mime: string;
      if (id === 'PIC') {
        const fmt = latin1.decode(body.subarray(1, 4)).toUpperCase();
        mime = fmt === 'PNG' ? 'image/png' : 'image/jpeg';
        q = 4;
      } else {
        const end = findTerminator(body, 1, 0);
        mime = latin1.decode(body.subarray(1, end)) || 'image/jpeg';
        if (!mime.includes('/')) mime = `image/${mime.toLowerCase()}`;
        q = end + 1;
      }
      q += 1; // type d'image
      const dEnd = findTerminator(body, q, enc);
      q = dEnd + (enc === 1 || enc === 2 ? 2 : 1);
      if (q < body.length) meta.picture = { mime, data: body.slice(q) };
    }
  }
  return meta;
}

function parseMp4(buf: Uint8Array): AudioMetadata {
  const meta: AudioMetadata = {};
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const type = (o: number) => latin1.decode(buf.subarray(o + 4, o + 8));

  const walk = (start: number, end: number, path: string[]) => {
    let o = start;
    while (o + 8 <= end) {
      let size = view.getUint32(o);
      let header = 8;
      if (size === 1 && o + 16 <= end) {
        size = Number(view.getBigUint64(o + 8));
        header = 16;
      } else if (size === 0) size = end - o;
      if (size < header || o + size > end) break;
      const t = type(o);
      const bodyStart = o + header;
      const bodyEnd = o + size;
      if (t === 'moov' || t === 'udta' || t === 'ilst' || t === 'trak' || t === 'mdia') {
        walk(bodyStart, bodyEnd, [...path, t]);
      } else if (t === 'meta') {
        walk(bodyStart + 4, bodyEnd, [...path, t]);
      } else if (path[path.length - 1] === 'ilst') {
        // Les éléments de la liste contiennent un atome « data ».
        const dataOff = bodyStart;
        if (dataOff + 16 <= bodyEnd && type(dataOff) === 'data') {
          const dsize = view.getUint32(dataOff);
          const kind = view.getUint32(dataOff + 8) & 0xffffff;
          const payload = buf.subarray(dataOff + 16, Math.min(bodyEnd, dataOff + dsize));
          if (t === '\u00a9nam') meta.title ??= clean(utf8.decode(payload));
          else if (t === '\u00a9ART' || t === 'aART') meta.artist ??= clean(utf8.decode(payload));
          else if (t === 'covr' && !meta.picture) {
            meta.picture = { mime: kind === 14 ? 'image/png' : 'image/jpeg', data: payload.slice() };
          }
        }
      }
      o += size;
    }
  };
  try {
    walk(0, buf.length, []);
  } catch {
    /* métadonnées ignorées */
  }
  return meta;
}

export function readMetadata(data: ArrayBuffer): AudioMetadata {
  const buf = new Uint8Array(data);
  try {
    if (buf[0] === 0x49 && buf[1] === 0x44 && buf[2] === 0x33) return parseId3(buf);
    if (latin1.decode(buf.subarray(4, 8)) === 'ftyp') return parseMp4(buf);
  } catch {
    /* fichier sans métadonnées lisibles */
  }
  return {};
}

/** Réduit une pochette à 320 px (JPEG) pour un stockage léger. */
export async function pictureToDataUrl(pic: { mime: string; data: Uint8Array }): Promise<string | undefined> {
  try {
    const blob = new Blob([pic.data as BlobPart], { type: pic.mime });
    const bmp = await createImageBitmap(blob);
    const size = 320;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;
    const s = Math.min(bmp.width, bmp.height);
    ctx.drawImage(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s, 0, 0, size, size);
    bmp.close();
    return canvas.toDataURL('image/jpeg', 0.85);
  } catch {
    return undefined;
  }
}
