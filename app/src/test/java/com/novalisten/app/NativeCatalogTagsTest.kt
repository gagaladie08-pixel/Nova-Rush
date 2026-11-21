package com.novalisten.app

import com.novalisten.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeCatalogTagsTest {
    private val artistId = "11111111-1111-1111-1111-111111111111"
    private val groupId = "22222222-2222-2222-2222-222222222222"
    private fun syncSafe(size: Int) = byteArrayOf((size ushr 21 and 127).toByte(), (size ushr 14 and 127).toByte(), (size ushr 7 and 127).toByte(), (size and 127).toByte())
    private fun frame(id: String, text: String): ByteArray {
        val bytes = byteArrayOf(3) + text.toByteArray()
        return id.toByteArray() + syncSafe(bytes.size) + byteArrayOf(0, 0) + bytes
    }
    private fun id3(vararg frames: ByteArray): ByteArray {
        val data = frames.fold(byteArrayOf()) { all, next -> all + next }
        return "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncSafe(data.size) + data
    }
    @Test fun readsId3NativeIdsTypesAndStructuredNamesWithoutChangingAnyBytes() {
        val bytes = id3(frame("TPE1", "Singer"), frame("TXXX", "MusicBrainz Artist Id\u0000$artistId"), frame("TXXX", "MusicBrainz Release Group Id\u0000$groupId"), frame("TXXX", "MusicBrainz Album Type\u0000album;mixtape/street"), frame("TPOS", "2/3"))
        val original = bytes.copyOf()
        val result = NativeCatalogTagReader.read(bytes.inputStream())
        assertEquals(artistId, result.credits.single().mbid); assertEquals(groupId, result.releaseGroupId)
        assertEquals(ReleaseKind.MIXTAPE, result.kind); assertEquals(2, result.disc)
        assertArrayEquals(original, bytes)
    }
    @Test fun repeatedFlacArtistFieldsAreStructuredParticipants() {
        val comments = listOf("ARTIST=One", "ARTIST=Two", "RELEASETYPE=ep", "MUSICBRAINZ_RELEASEGROUPID=$groupId", "ORIGINALDATE=2001-02-03")
        val block = ByteArrayOutputStream()
        fun little(value: Int) = block.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        little(0); little(comments.size)
        comments.forEach { value -> val bytes = value.toByteArray(); little(bytes.size); block.write(bytes) }
        val data = block.toByteArray()
        val raw = "fLaC".toByteArray() + byteArrayOf(0x84.toByte(), (data.size ushr 16).toByte(), (data.size ushr 8).toByte(), data.size.toByte()) + data
        val result = NativeCatalogTagReader.read(raw.inputStream())
        assertEquals(listOf("One", "Two"), result.credits.map { it.name }); assertEquals(ReleaseKind.EP, result.kind)
        assertEquals(2001, result.originalYear)
    }
    @Test fun arbitraryOrTruncatedFilesNeverInventMetadata() {
        listOf(byteArrayOf(), "RIFFwaveaudio".toByteArray(), "ID3".toByteArray(), id3(frame("TPE1", "A")).dropLast(2).toByteArray()).forEach { raw ->
            assertEquals(NativeCatalogTags(), NativeCatalogTagReader.read(raw.inputStream()))
        }
    }
    @Test fun oversizedTagsAreRejectedBeforeReadingThePayload() {
        val raw = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncSafe(2_000_000)
        assertEquals(NativeCatalogTags(), NativeCatalogTagReader.read(raw.inputStream()))
    }
    @Test fun invalidMusicIdsAreNotAcceptedAsIdentityProof() {
        val raw = id3(frame("TXXX", "MusicBrainz Release Group Id\u0000not-an-id"))
        assertEquals("", NativeCatalogTagReader.read(raw.inputStream()).releaseGroupId)
    }
    @Test fun isrcNormalizationDoesNotTreatAMusicBrainzReleaseTrackIdAsARecordingId() {
        val result = NativeCatalogTagReader.read(id3(frame("TSRC", "US-AAA-01-00001"), frame("TXXX", "MusicBrainz Release Track Id\u0000$groupId")).inputStream())
        assertEquals("USAAA0100001", result.isrc); assertEquals("", result.recordingId)
    }
    @Test fun nativeAlbumArtistAndItsOwnMusicBrainzIdAreNotConfusedWithTrackArtistIds() {
        val bytes = id3(frame("TPE1", "One feat. Two"), frame("TPE2", "One"), frame("TXXX", "MusicBrainz Album Artist Id\u0000$groupId"), frame("TXXX", "MusicBrainz Artist Id\u0000$artistId"))
        val tags = NativeCatalogTagReader.read(bytes.inputStream())
        assertEquals("One", tags.albumArtist)
        assertEquals(ArtistCredit("One", groupId), tags.albumCredits.single())
        assertTrue(tags.credits.isEmpty()) // A single artist MBID cannot identify both participants.
    }

}
