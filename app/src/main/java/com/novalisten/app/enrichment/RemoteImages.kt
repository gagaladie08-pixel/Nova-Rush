package com.novalisten.app.enrichment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

class RemoteImages(private val context: Context, private val transport: RemoteTransport) {
    private val folder get() = File(context.cacheDir, "api-previews").apply { mkdirs() }
    suspend fun preview(image: ImageCandidate): String = withContext(Dispatchers.IO) {
        RemoteSafety.image(image.preview)
        RemoteSafety.image(image.original)
        val name = MessageDigest.getInstance("SHA-256").digest((imageIdentity(image.original) + "\n" + image.preview).toByteArray()).joinToString("") { "%02x".format(it) }
        val cached = File(folder, "$name.jpg")
        if (cached.isFile && cached.length() > 0 && System.currentTimeMillis() - cached.lastModified() in 0..86_400_000) return@withContext Uri.fromFile(cached).toString()
        val bytes = transport.image(image.preview, 2_000_000)
        coroutineContext.ensureActive()
        writeImage(bytes, cached, 640)
    }
    suspend fun preparePhoto(image: ImageCandidate): PreparedPhoto = withContext(Dispatchers.IO) {
        val uri = preview(image)
        coroutineContext.ensureActive()
        val bitmap = BitmapFactory.decodeFile(File(java.net.URI(uri)).path) ?: throw RemoteFailure("Aperçu illisible.")
        try {
            require(bitmap.width >= 96 && bitmap.height >= 96) { "Aperçu trop petit pour une vignette utile." }
            val sample = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            val pixels = IntArray(72)
            try { sample.getPixels(pixels, 0, 9, 0, 0, 9, 8) } finally { if (sample !== bitmap) sample.recycle() }
            PreparedPhoto(image, uri, PhotoFingerprint.fromPixels(pixels, bitmap.width, bitmap.height))
        } finally { bitmap.recycle() }
    }
    suspend fun save(image: ImageCandidate): String = withContext(Dispatchers.IO) {
        RemoteSafety.image(image.original)
        val bytes = transport.image(image.original)
        coroutineContext.ensureActive()
        val destination = File(context.filesDir, "images/remote-${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
        try {
            val uri = writeImage(bytes, destination, 1600)
            coroutineContext.ensureActive()
            uri
        } catch (e: Exception) { destination.delete(); throw e }
    }
    private fun writeImage(bytes: ByteArray, destination: File, maxDimension: Int): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..40000 && bounds.outHeight in 1..40000) { "Dimensions d’image invalides." }
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw RemoteFailure("Image illisible.")
        val temporary = File(destination.path + ".part-${UUID.randomUUID()}")
        try {
            temporary.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) throw RemoteFailure("Encodage d’image impossible.") }
            check(temporary.renameTo(destination))
        } finally { bitmap.recycle(); temporary.delete() }
        return Uri.fromFile(destination).toString()
    }
    suspend fun clearPreviews() = withContext(Dispatchers.IO) { folder.listFiles()?.forEach { it.delete() }; Unit }
}
