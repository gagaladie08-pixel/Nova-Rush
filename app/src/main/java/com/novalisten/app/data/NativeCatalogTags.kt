package com.novalisten.app.data

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Read-only, bounded ID3v2.3/2.4 and FLAC Vorbis comments. No audio hashes or remote scan. */
data class NativeCatalogTags(val credits: List<ArtistCredit> = emptyList(), val releaseGroupId: String = "", val releaseId: String = "",
                             val kind: ReleaseKind = ReleaseKind.UNKNOWN, val recordingId: String = "", val isrc: String = "", val disc: Int = 0, val originalYear: Int = 0,
                             val albumArtist: String = "", val albumCredits: List<ArtistCredit> = emptyList())
object NativeCatalogTagReader {
    private const val LIMIT = 262_144
    fun read(input: InputStream): NativeCatalogTags = runCatching {
        val stream = DataInputStream(input)
        val head = ByteArray(4); stream.readFully(head)
        val values = linkedMapOf<String, MutableList<String>>()
        if (head.copyOfRange(0, 3).toString(Charsets.ISO_8859_1) == "ID3") {
            val tail = ByteArray(6); stream.readFully(tail)
            val version = head[3].toInt() and 255
            if (version !in 3..4 || tail[1].toInt() and 0xc0 != 0) return@runCatching NativeCatalogTags()
            val size = syncSafe(tail.copyOfRange(2, 6))
            if (size !in 1..(LIMIT - 10)) return@runCatching NativeCatalogTags()
            val bytes = ByteArray(size); stream.readFully(bytes)
            var offset = 0
            while (offset + 10 <= bytes.size) {
                val id = bytes.copyOfRange(offset, offset + 4).toString(Charsets.ISO_8859_1)
                if (!id.matches(Regex("[A-Z0-9]{4}"))) break
                val length = if (version == 4) syncSafe(bytes.copyOfRange(offset + 4, offset + 8)) else ByteBuffer.wrap(bytes, offset + 4, 4).int
                if (length <= 0 || length > bytes.size - offset - 10) break
                val flags = bytes[offset + 9].toInt() and 255
                if (flags == 0) {
                    val body = bytes.copyOfRange(offset + 10, offset + 10 + length)
                    when {
                        id == "TXXX" -> { val text = text(body); val key = text.substringBefore('\u0000'); add(values, key, text.substringAfter('\u0000', "")) }
                        id == "TPE1" -> add(values, "ARTIST", text(body))
                        id == "TPE2" -> add(values, "ALBUMARTIST", text(body))
                        id == "TSRC" -> add(values, "ISRC", text(body))
                        id == "TPOS" -> add(values, "DISCNUMBER", text(body))
                        id in setOf("TORY", "TDOR") -> add(values, "ORIGINALDATE", text(body))
                        id == "UFID" -> { val zero = body.indexOf(0.toByte()); if (zero > 0 && body.copyOfRange(0, zero).toString(Charsets.ISO_8859_1) == "http://musicbrainz.org") add(values, "MUSICBRAINZTRACKID", body.copyOfRange(zero + 1, body.size).toString(Charsets.ISO_8859_1)) }
                    }
                }
                offset += 10 + length
            }
        } else if (head.toString(Charsets.ISO_8859_1) == "fLaC") {
            var consumed = 4
            while (consumed + 4 <= LIMIT) {
                val flags = stream.readUnsignedByte()
                val length = (stream.readUnsignedByte() shl 16) or (stream.readUnsignedByte() shl 8) or stream.readUnsignedByte()
                consumed += 4
                if (length > LIMIT - consumed) break
                val bytes = ByteArray(length); stream.readFully(bytes); consumed += length
                if (flags and 0x7f == 4) {
                    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    fun string(): String { val count = b.int; require(count in 0..b.remaining()); val raw = ByteArray(count); b.get(raw); return raw.toString(Charsets.UTF_8) }
                    string()
                    val count = b.int; require(count in 0..2048)
                    repeat(count) { val field = string(); add(values, field.substringBefore('='), field.substringAfter('=', "")) }
                }
                if (flags and 0x80 != 0) break
            }
        }
        fun all(key: String): List<String> = values[key].orEmpty().flatMap { it.split('\u0000') }.map(String::trim).filter { it.isNotEmpty() }
        fun first(vararg keys: String) = keys.firstNotNullOfOrNull { all(it).firstOrNull() }.orEmpty()
        val names = all("ARTISTS").flatMap { it.split(';') }.map(String::trim).filter(String::isNotBlank).ifEmpty { all("ARTIST").takeIf { it.size > 1 }.orEmpty() }
        val ids = all("MUSICBRAINZARTISTID").flatMap { it.split(';', '/') }.map(String::trim).filter(::validMusicId)
        val credits = if (names.isNotEmpty()) names.mapIndexed { index, name -> ArtistCredit(name, ids.getOrNull(index).orEmpty().takeIf { ids.size == names.size }.orEmpty()) }
            else if (all("ARTIST").size == 1 && ids.size == 1 && !ArtistCredits.mayBeMultiple(all("ARTIST").single())) listOf(ArtistCredit(all("ARTIST").single(), ids.single())) else emptyList()
        val albumNames = all("ALBUMARTISTS").flatMap { it.split(';') }.map(String::trim).filter(String::isNotBlank).ifEmpty { all("ALBUMARTIST").takeIf { it.size > 1 }.orEmpty() }
        val albumIds = all("MUSICBRAINZALBUMARTISTID").flatMap { it.split(';', '/') }.map(String::trim).filter(::validMusicId)
        val albumArtist = all("ALBUMARTIST").joinToString("; ")
        val albumCredits = if (albumNames.isNotEmpty()) albumNames.mapIndexed { index, name -> ArtistCredit(name, albumIds.getOrNull(index).orEmpty().takeIf { albumNames.size == albumIds.size }.orEmpty()) }
            else if (albumArtist.isNotBlank() && albumIds.size == 1 && !ArtistCredits.mayBeMultiple(albumArtist)) listOf(ArtistCredit(albumArtist, albumIds.single())) else emptyList()
        NativeCatalogTags(credits, first("MUSICBRAINZRELEASEGROUPID").takeIf(::validMusicId).orEmpty(), first("MUSICBRAINZALBUMID", "MUSICBRAINZRELEASEID").takeIf(::validMusicId).orEmpty(),
            ReleaseKind.fromTag(first("RELEASETYPE", "MUSICBRAINZALBUMTYPE", "MUSICBRAINZRELEASETYPE")), first("MUSICBRAINZTRACKID", "MUSICBRAINZRECORDINGID").takeIf(::validMusicId).orEmpty(),
            first("ISRC").replace("-", "").uppercase(java.util.Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")) }.orEmpty(),
            first("DISCNUMBER").substringBefore('/').toIntOrNull()?.coerceIn(0, 999) ?: 0, first("ORIGINALDATE", "ORIGINALYEAR").take(4).toIntOrNull()?.takeIf { it in 1000..2999 } ?: 0, albumArtist, albumCredits)
    }.getOrDefault(NativeCatalogTags())
    private fun add(values: MutableMap<String, MutableList<String>>, key: String, value: String) {
        val normalized = key.uppercase(java.util.Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
        if (value.length <= 20_000) values.getOrPut(normalized) { mutableListOf() }.add(value)
    }
    private fun syncSafe(raw: ByteArray): Int { require(raw.all { it.toInt() and 0x80 == 0 }); return raw.fold(0) { value, byte -> (value shl 7) or (byte.toInt() and 127) } }
    private fun text(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val charset = when (bytes[0].toInt()) { 0 -> Charsets.ISO_8859_1; 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> return "" }
        return bytes.copyOfRange(1, bytes.size).toString(charset).trimEnd('\u0000')
    }
}
