package com.novalisten.app

import com.novalisten.app.enrichment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoDiscoveryTest {
    private fun candidate(index: Int, licence: Boolean = true) = ImageCandidate("fixture:$index", "Image synthétique $index", "https://example.org/preview/$index.jpg", "https://example.org/image/$index.jpg", Credit("Fixture locale", "https://example.org/source/$index", "CC0 fictive de test"), licence)
    private fun prepared(image: ImageCandidate, index: Int = image.id.substringAfter(':').toInt()) = PreparedPhoto(image, "file:///test/$index.jpg", PhotoFingerprint(index * 0x0f0f0f0f0f0f0f0fL, index * 18 % 255, index * 97 % 255, index * 43 % 255, 1000))

    @Test fun initialDiscoveryStopsAtSixActualDistinctPreviewsNotSixApiReplies() = runTest {
        var attempts = 0
        val result = PhotoDiscovery { image -> attempts++; prepared(image) }.discover((1..12).map { candidate(it) })
        assertEquals(6, result.photos.size); assertEquals(6, attempts); assertEquals(6, result.attempted)
    }
    @Test fun sixAlreadyPreparedChoicesAreReusedWithoutNetwork() = runTest {
        val existing = (1..6).map { prepared(candidate(it)) }
        val result = PhotoDiscovery { error("No request after target") }.discover((7..12).map { candidate(it) }, existing)
        assertEquals(existing, result.photos); assertEquals(0, result.attempted)
    }
    @Test fun p18ReuseCountsTowardTargetWithoutDownloadingItAgain() = runTest {
        val existing = listOf(prepared(candidate(1)))
        var attempts = 0
        val result = PhotoDiscovery { image -> attempts++; prepared(image) }.discover((1..12).map { candidate(it) }, existing)
        assertEquals(6, result.photos.size); assertEquals(5, attempts)
    }
    @Test fun samePhotoWithTwoUrlsDoesNotCountAsTwoUsefulThumbnails() = runTest {
        val result = PhotoDiscovery { image -> prepared(image, if (image.id == "fixture:2") 1 else image.id.substringAfter(':').toInt()) }.discover((1..7).map { candidate(it) })
        assertEquals(6, result.photos.size); assertEquals(1, result.probableDuplicates); assertEquals(7, result.attempted)
    }
    @Test fun duplicateUrlsAreNotRequestedTwiceEvenWithDifferentSourceIds() = runTest {
        val one = candidate(1)
        val result = PhotoDiscovery { prepared(it) }.discover(listOf(one, one.copy(id = "other:1"), candidate(2)))
        assertEquals(2, result.photos.size); assertEquals(2, result.attempted); assertEquals(1, result.probableDuplicates)
    }
    @Test fun cannotAutoPreviewImagesWhoseRightsAreUnresolved() = runTest {
        val result = PhotoDiscovery { error("No rights, no automatic thumbnail") }.discover((1..8).map { candidate(it, false) })
        assertTrue(result.photos.isEmpty()); assertEquals(8, result.deferredRights); assertEquals(0, result.attempted)
    }
    @Test fun forbiddenProviderBehindAnOpenverseRecordIsNotDownloaded() = runTest {
        val blocked = candidate(1).copy(original = "https://cdn.dzcdn.net/image.jpg")
        val result = PhotoDiscovery { error("Blocked original source") }.discover(listOf(blocked))
        assertTrue(result.photos.isEmpty()); assertEquals(1, result.unavailable); assertEquals(0, result.attempted)
    }
    @Test fun initialPreviewAttemptsHaveAHardBudgetAndDoNotInventSixResults() = runTest {
        var attempts = 0
        val result = PhotoDiscovery { attempts++; throw RemoteFailure("Image indisponible") }.discover((1..40).map { candidate(it) })
        assertTrue(result.photos.isEmpty()); assertEquals(12, attempts); assertEquals(12, result.unavailable)
    }
    @Test fun failedOrTooSmallPreviewsDoNotFillTheSixUsefulSlots() = runTest {
        val result = PhotoDiscovery { image -> if (image.id == "fixture:1") throw IllegalArgumentException("Aperçu trop petit") else prepared(image) }.discover((1..7).map { candidate(it) })
        assertEquals(6, result.photos.size); assertEquals(1, result.unavailable); assertEquals(listOf("Aperçu trop petit"), result.warnings)
    }
    @Test fun quotaFailureIsShownAsAFailureNotAsNoCandidate() = runTest {
        val result = PhotoDiscovery { throw RemoteFailure("Quota : réessayez plus tard") }.discover(listOf(candidate(1)))
        assertTrue(result.photos.isEmpty()); assertEquals(listOf("Quota : réessayez plus tard"), result.warnings)
    }
    @Test fun previewPreparationIsNeverMoreThanTwoAtATime() = runTest {
        var active = 0; var peak = 0
        val result = PhotoDiscovery { image -> active++; peak = maxOf(active, peak); delay(100); active--; prepared(image) }.discover((1..12).map { candidate(it) })
        assertEquals(2, peak); assertEquals(6, result.photos.size)
    }
    @Test fun cancellingDiscoveryCancelsChildrenAndDoesNotAutoContinue() = runTest {
        var started = 0; var completed = 0
        val task = launch { PhotoDiscovery { image -> started++; delay(10000); completed++; prepared(image) }.discover((1..12).map { candidate(it) }) }
        testScheduler.runCurrent(); task.cancelAndJoin(); testScheduler.advanceUntilIdle()
        assertEquals(2, started); assertEquals(0, completed)
    }
    @Test fun localFingerprintIsResolutionIndependentButNotFaceRecognition() {
        val pixels = IntArray(72) { index -> (index * 3 shl 16) or (index * 2 shl 8) or index }
        val first = PhotoFingerprint.fromPixels(pixels, 600, 400)
        val same = PhotoFingerprint.fromPixels(pixels, 300, 200)
        assertEquals(first, same); assertTrue(first.resembles(same))
        assertFalse(first.resembles(first.copy(gradient = first.gradient.inv())))
        assertFalse(first.resembles(first.copy(aspectMilli = 700)))
    }
    @Test fun fingerprintDoesNotMergeCompletelyDifferentSolidColors() {
        val red = PhotoFingerprint.fromPixels(IntArray(72) { 0xff0000 }, 320, 320)
        val blue = PhotoFingerprint.fromPixels(IntArray(72) { 0x0000ff }, 320, 320)
        assertFalse(red.resembles(blue))
    }
    @Test fun urlIdentityPreservesMeaningfulQueriesButDropsFragments() {
        assertEquals(imageIdentity("https://EXAMPLE.org:443/a.jpg#section"), imageIdentity("https://example.org/a.jpg"))
        assertNotEquals(imageIdentity("https://example.org/a.jpg?id=1"), imageIdentity("https://example.org/a.jpg?id=2"))
    }
}
