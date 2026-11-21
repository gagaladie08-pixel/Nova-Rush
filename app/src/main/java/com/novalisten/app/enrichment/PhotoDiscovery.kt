package com.novalisten.app.enrichment

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.URI
import kotlin.math.abs

/** Local perceptual heuristic, not face recognition and not a claim of subject/date correctness. */
data class PhotoFingerprint(val gradient: Long, val red: Int, val green: Int, val blue: Int, val aspectMilli: Int) {
    fun resembles(other: PhotoFingerprint): Boolean = java.lang.Long.bitCount(gradient xor other.gradient) <= 3 &&
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) <= 36 && abs(aspectMilli - other.aspectMilli) <= 60

    companion object {
        /** 9×8 RGB sample, without EXIF, filenames, identity embeddings or a remote analysis service. */
        fun fromPixels(pixels: IntArray, originalWidth: Int, originalHeight: Int): PhotoFingerprint {
            require(pixels.size == 72 && originalWidth > 0 && originalHeight > 0)
            fun r(pixel: Int) = pixel ushr 16 and 255
            fun g(pixel: Int) = pixel ushr 8 and 255
            fun b(pixel: Int) = pixel and 255
            fun luma(pixel: Int) = r(pixel) * 299 + g(pixel) * 587 + b(pixel) * 114
            var gradient = 0L
            for (y in 0..7) for (x in 0..7) {
                if (luma(pixels[y * 9 + x]) > luma(pixels[y * 9 + x + 1])) gradient = gradient or (1L shl (y * 8 + x))
            }
            return PhotoFingerprint(gradient, pixels.sumOf(::r) / 72, pixels.sumOf(::g) / 72, pixels.sumOf(::b) / 72,
                (originalWidth.toLong() * 1000 / originalHeight).toInt())
        }
    }
}

data class PreparedPhoto(val image: ImageCandidate, val preview: String, val fingerprint: PhotoFingerprint)
data class PhotoDiscoveryResult(val photos: List<PreparedPhoto>, val attempted: Int, val probableDuplicates: Int, val unavailable: Int, val deferredRights: Int, val warnings: List<String>)

/** URL normalization never drops meaningful provider parameters or rewrites the destination host. */
fun imageIdentity(url: String): String = runCatching {
    val uri = RemoteSafety.https(url).normalize()
    URI("https", null, uri.host.lowercase(java.util.Locale.ROOT), -1, uri.path, uri.query, null).toASCIIString()
}.getOrDefault(url)

/** One bounded, user-requested batch. Reuse prepared results; stop at the target, never auto-page. */
class PhotoDiscovery(private val prepare: suspend (ImageCandidate) -> PreparedPhoto) {
    suspend fun discover(candidates: List<ImageCandidate>, existing: List<PreparedPhoto> = emptyList(), target: Int = 6, maxPreviews: Int = 12,
                         onPhotos: (List<PreparedPhoto>) -> Unit = {}, onProgress: (Int, Int) -> Unit = { _, _ -> }): PhotoDiscoveryResult {
        require(target in 1..60 && maxPreviews in 1..12)
        val accepted = existing.distinctBy { imageIdentity(it.image.original) }.take(target).toMutableList()
        val seen = accepted.mapTo(mutableSetOf()) { imageIdentity(it.image.original) }
        val pending = ArrayDeque<ImageCandidate>()
        var duplicates = 0; var unavailable = 0; var deferredRights = 0; var attempted = 0
        val warnings = mutableListOf<String>()
        candidates.take(24).forEach { image ->
            if (!seen.add(imageIdentity(image.original))) duplicates++
            else if (!image.licenceKnown) deferredRights++
            else if (runCatching { RemoteSafety.image(image.original); RemoteSafety.image(image.preview) }.isFailure) unavailable++
            else pending.addLast(image)
        }
        while (accepted.size < target && pending.isNotEmpty() && attempted < maxPreviews) {
            val count = minOf(2, target - accepted.size, maxPreviews - attempted, pending.size)
            val batch = List(count) { pending.removeFirst() }
            attempted += count
            val replies = coroutineScope {
                batch.map { image -> async {
                    try { prepare(image) to null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) { null to e.publicMessage() }
                } }.map { it.await() }
            }
            replies.forEach { (photo, warning) ->
                if (warning != null) warnings += warning
                when {
                    photo == null -> unavailable++
                    accepted.any { it.fingerprint.resembles(photo.fingerprint) } -> duplicates++
                    else -> accepted += photo
                }
            }
            onPhotos(accepted.toList())
            onProgress(accepted.size, attempted)
        }
        return PhotoDiscoveryResult(accepted.toList(), attempted, duplicates, unavailable, deferredRights, warnings.distinct())
    }
}
