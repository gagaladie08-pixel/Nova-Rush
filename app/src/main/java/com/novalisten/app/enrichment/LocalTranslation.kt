package com.novalisten.app.enrichment

import android.content.Context
import com.novalisten.app.data.LrcParser
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKit
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Keeps the original LRC prefixes and never manufactures French word timings. */
data class TranslationSegment(val prefix: String, val content: String, val translate: Boolean)
class TranslationPlan(original: String) {
    val segments: List<TranslationSegment>
    val contents: List<String> get() = segments.filter { it.translate }.map { it.content }
    init {
        require(original.isNotBlank() && original.length <= 20_000) { "Cette version traduit des textes de 20 000 caractères maximum, sans les tronquer." }
        val prefix = Regex("^((?:\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?])+)(.*)$")
        val tag = Regex("^\\[[A-Za-z]+:.*]$")
        segments = original.replace("\r\n", "\n").split('\n').map { raw ->
            val line = LrcParser.withoutWordTags(raw)
            val match = prefix.matchEntire(line)
            when {
                match != null -> TranslationSegment(match.groupValues[1], match.groupValues[2], match.groupValues[2].isNotBlank())
                tag.matches(line) || line.isBlank() -> TranslationSegment("", line, false)
                else -> TranslationSegment("", line, true)
            }
        }
        require(contents.isNotEmpty() && contents.size <= 500) { "Aucun texte traduisible, ou plus de 500 lignes." }
    }
    fun render(translations: Map<String, String>): String = segments.joinToString("\n") { segment ->
        if (!segment.translate) segment.prefix + segment.content
        else {
            val text = translations[segment.content] ?: throw RemoteFailure("Traduction incomplète. L’original reste intact.")
            // A translated line is one timed segment, never new LRC control data.
            val plain = LrcParser.withoutWordTags(text.replace('\n', ' ').replace('\r', ' ').replace(Regex("\\[\\d{1,3}:\\d{2}[^]]*]"), "")).trim()
            require(plain.isNotBlank()) { "Segment de traduction vide." }
            segment.prefix + plain
        }
    }
}

/** ML Kit is explicitly initialized here, not by an Android startup provider. */
class LocalTranslation(private val context: Context) {
    @Volatile private var initialized = false
    val activated: Boolean get() = initialized
    private fun initialize() {
        if (!initialized) synchronized(this) { if (!initialized) { MlKit.initialize(context); initialized = true } }
    }
    private fun languages(source: String): List<String> {
        require(source in setOf("en", "ko", "es")) { "Choisissez la langue source." }
        return listOf(source, TranslateLanguage.FRENCH).filter { it != TranslateLanguage.ENGLISH }.distinct()
    }
    suspend fun downloaded(source: String): Boolean {
        val required = languages(source)
        initialize()
        val models = RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java).waitForTask()
        return required.all { language -> models.any { it.language == language } }
    }
    /** Called only by the dedicated, informed download button. No automatic remote fallback. */
    suspend fun download(source: String, wifiOnly: Boolean) {
        val required = languages(source)
        initialize()
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        for (language in required) {
            coroutineContext.ensureActive()
            RemoteModelManager.getInstance().download(TranslateRemoteModel.Builder(language).build(), conditions).waitForTask()
        }
    }
    suspend fun translate(plan: TranslationPlan, source: String, progress: (Int, Int) -> Unit): String {
        if (!downloaded(source)) throw RemoteFailure("Packs absents. Utilisez le bouton de téléchargement volontaire ; aucune donnée n’a été envoyée à Azure.")
        val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.FRENCH).build())
        try {
            val translated = linkedMapOf<String, String>()
            val distinct = plan.contents.distinct()
            distinct.forEachIndexed { index, text ->
                coroutineContext.ensureActive()
                translated[text] = translator.translate(text).waitForTask()
                progress(index + 1, distinct.size)
            }
            return plan.render(translated)
        } finally { translator.close() }
    }
    suspend fun deletePacks() {
        initialize()
        val manager = RemoteModelManager.getInstance()
        for (model in manager.getDownloadedModels(TranslateRemoteModel::class.java).waitForTask()) manager.deleteDownloadedModel(model).waitForTask()
    }
}

private suspend fun <T> Task<T>.waitForTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(RemoteFailure("ML Kit : opération impossible sur cet appareil ou pack indisponible. Aucun repli distant.")) }
    addOnCanceledListener { continuation.cancel() }
}
