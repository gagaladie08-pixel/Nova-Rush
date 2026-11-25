package com.novalisten.app.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal interne, borné et conservé sur disque.
 *
 * Il ne prétend pas tout voir : il enregistre ce que le code sait déjà — les erreurs avalées,
 * les étapes du démarrage et les changements de page. Un défaut de logique qui ne lève aucune
 * exception (un bouton sans effet, un retour inopérant) n'y apparaîtra jamais.
 *
 * Deux fichiers de 200 Ko au maximum, alternés : l'application ne peut pas remplir le téléphone.
 */
object NovaLog {
    const val INFO = "info"
    const val WARN = "avert."
    const val ERROR = "erreur"

    data class Entry(
        val at: Long,
        val level: String,
        val source: String,
        val message: String,
        val detail: String = "",
    ) {
        fun line(): String = "${stamp(at)} [$level] $source — $message" + if (detail.isBlank()) "" else " :: $detail"
    }

    private const val MAX_BYTES = 200_000
    private const val MEMORY = 800

    private val entries = ArrayDeque<Entry>()
    private val lock = Any()
    private var file: File? = null
    private var installed = false
    private var crashed = false

    /** Installé au démarrage : branche le fichier, la rotation et le rapport de plantage. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val dir = File(context.filesDir, "journal")
        dir.mkdirs()
        file = File(dir, "nova-journal.log")
        rotateIfNeeded()
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            crashed = true
            write(Entry(System.currentTimeMillis(), ERROR, "plantage",
                "L'application s'est arrêtée : ${error.javaClass.simpleName}",
                stack(error).take(4000) + "\nfil : ${thread.name}"))
            flush()
            previous?.uncaughtException(thread, error)
        }
    }

    private var previous: Thread.UncaughtExceptionHandler? = null

    fun info(source: String, message: String) = write(Entry(System.currentTimeMillis(), INFO, source, message))

    fun warn(source: String, message: String, error: Throwable? = null) =
        write(Entry(System.currentTimeMillis(), WARN, source, message, error?.let { short(it) } ?: ""))

    fun error(source: String, message: String, error: Throwable? = null) =
        write(Entry(System.currentTimeMillis(), ERROR, source, message, error?.let { short(it) } ?: ""))

    fun entries(): List<Entry> = synchronized(lock) { entries.toList() }

    fun counts(): Map<String, Int> = entries().groupingBy { it.level }.eachCount()

    /** Vrai si un arrêt brutal a été enregistré depuis le dernier démarrage. */
    fun crashedSinceStart(): Boolean = crashed

    fun clear() {
        synchronized(lock) { entries.clear() }
        file?.let { runCatching { it.writeText("") } }
    }

    /** Résumé copiable, pour transmission sans outil externe. */
    fun report(app: String): String = buildString {
        appendLine("NovaListen — journal interne")
        appendLine("version : $app")
        appendLine("généré le : ${stamp(System.currentTimeMillis())}")
        val counts = counts()
        appendLine("entrées en mémoire : ${entries().size}" + if (counts.isEmpty()) "" else " (" + counts.entries.joinToString(", ") { "${it.key} ${it.value}" } + ")")
        appendLine("fichier : ${file?.let { "${it.length()} octets" } ?: "non installé"}")
        appendLine("arrêt brutal depuis le démarrage : " + if (crashed) "oui" else "non")
        appendLine()
        // Les erreurs d'abord : c'est ce qu'on vient chercher.
        entries().sortedBy { if (it.level == ERROR) 0 else if (it.level == WARN) 1 else 2 }
            .takeLast(400).forEach { appendLine(it.line()) }
    }

    private fun write(entry: Entry) {
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MEMORY) entries.removeFirst()
        }
        // Une écriture immédiate par entrée serait trop coûteuse pendant un scan. Les erreurs,
        // elles, sont écrites tout de suite : c'est précisément ce qu'un arrêt brutal effacerait.
        if (entry.level != INFO) flush()
    }

    fun flush() {
        val target = file ?: return
        val snapshot = synchronized(lock) { entries.toList() }
        if (snapshot.isEmpty()) return
        runCatching {
            // Une seule rotation avant écriture : si le fichier dépasse la borne, on repart propre.
            // On n'alterne pas après coup, sinon la ligne qu'on vient d'écrire serait déplacée.
            rotateIfNeeded()
            target.appendText(snapshot.joinToString("\n", postfix = "\n") { it.line() })
        }
        synchronized(lock) { entries.clear() }
    }

    private fun rotateIfNeeded() {
        val target = file ?: return
        if (target.exists() && target.length() > MAX_BYTES) {
            val old = File(target.parentFile, target.name + ".1")
            runCatching { old.delete(); target.renameTo(old) }
        }
    }

    private fun short(error: Throwable): String {
        val head = error.javaClass.simpleName + if (error.message.isNullOrBlank()) "" else ": ${error.message}"
        val frame = error.stackTrace.firstOrNull()?.let { " (${it.fileName}:${it.lineNumber})" } ?: ""
        return (head + frame).take(300)
    }

    private fun stack(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun stamp(at: Long) = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(at))
}
