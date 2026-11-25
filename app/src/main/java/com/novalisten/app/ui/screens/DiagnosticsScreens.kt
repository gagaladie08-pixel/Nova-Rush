package com.novalisten.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novalisten.app.BuildConfig
import com.novalisten.app.data.*
import com.novalisten.app.diagnostics.NovaLog
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.theme.NovaIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal interne : ce que l'application a rencontré, y compris ce qu'elle taisait auparavant.
 * Un défaut de logique qui ne lève aucune exception n'apparaîtra pas ici — c'est une limite
 * assumée, rappelée à l'écran pour ne pas donner une fausse impression d'exhaustivité.
 */
@Composable
fun JournalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(NovaLog.entries()) }
    var onlyErrors by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val counts = remember(entries) { entries.groupingBy { it.level }.eachCount() }
    val shown = remember(entries, onlyErrors) { entries.filter { !onlyErrors || it.level == NovaLog.ERROR } }
    LazyColumn(Modifier.fillMaxSize().testTag("journal_screen"), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            PageTitle("Journal", "${entries.size} entrée(s) · ${counts[NovaLog.ERROR] ?: 0} erreur(s)") {
                IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") }
            }
        }
        item {
            Notice("Ce journal enregistre les erreurs rencontrées, les étapes du démarrage et les arrêts brutaux. Il ne peut pas voir un défaut qui ne lève aucune erreur — un bouton sans effet, par exemple — ni juger qu'un résultat est faux.")
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onlyErrors = !onlyErrors }, Modifier.testTag("journal_filter")) {
                    Text(if (onlyErrors) "Tout afficher" else "Erreurs seulement")
                }
                OutlinedButton({ NovaLog.clear(); entries = NovaLog.entries() }) { Text("Vider") }
            }
        }
        item {
            TextButton({ copy(context, NovaLog.report("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")); copied = true }) {
                Text(if (copied) "Rapport copié" else "Copier le rapport")
            }
        }
        if (shown.isEmpty()) item { EmptyState("Aucune entrée", "Rien à signaler pour l'instant.", Icons.Outlined.CheckCircle) }
        items(shown.reversed(), key = { "${it.at}-${it.source}-${it.message}" }) { entry ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text("${levelLabel(entry.level)} · ${stamp(entry.at)} · ${entry.source}", style = MaterialTheme.typography.labelSmall)
                Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                if (entry.detail.isNotBlank()) Text(entry.detail, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp)); HorizontalDivider()
            }
        }
    }
}

/**
 * Santé : l'état au moment présent, utile quand la question est « pourquoi ça met longtemps »
 * plutôt que « qu'est-ce qui a échoué ».
 */
@Composable
fun HealthScreen(
    index: CatalogSnapshot,
    library: LibraryState,
    choicesSplit: SplitRules,
    tagCacheEntries: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().testTag("health_screen"), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { PageTitle("Santé", "État au moment présent") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HealthRow("Version", "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
                HealthRow("Catalogue", if (index.ready) "prêt" else "en construction")
                HealthRow("Morceaux indexés", index.sourceTracks.size.toString())
                HealthRow("Artistes", index.artists.size.toString())
                HealthRow("Albums", index.albums.size.toString())
                HealthRow("Bibliothèque", when {
                    !library.permission -> "accès refusé"
                    library.loading -> "lecture en cours"
                    library.error != null -> "erreur : ${library.error}"
                    else -> "${library.tracks.size} morceaux"
                })
                HealthRow("Tags en cache", "$tagCacheEntries fichier(s)")
                HealthRow("Règles de découpage", "${choicesSplit.protectedNames.size} noms protégés · ${choicesSplit.separators.size} séparateurs")
                HealthRow("Arrêt brutal", if (NovaLog.crashedSinceStart()) "oui, depuis ce démarrage" else "non")
                HealthRow("Journal", "${NovaLog.entries().size} entrée(s) en mémoire")
                TextButton({ copy(context, healthReport(index, library, tagCacheEntries)) }) { Text("Copier cet état") }
            }
        }
        item { Notice("Les durées du démarrage figurent dans le journal, entrée après entrée ; cet écran montre l'état, pas l'historique.") }
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun healthReport(index: CatalogSnapshot, library: LibraryState, cache: Int): String = buildString {
    appendLine("NovaListen — état")
    appendLine("version : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("catalogue : ${if (index.ready) "prêt" else "en construction"}")
    appendLine("morceaux indexés : ${index.sourceTracks.size}")
    appendLine("artistes : ${index.artists.size}")
    appendLine("albums : ${index.albums.size}")
    appendLine("bibliothèque : ${library.tracks.size} morceaux" + if (library.error != null) " (erreur : ${library.error})" else "")
    appendLine("tags en cache : $cache")
    append("arrêt brutal : ").append(if (NovaLog.crashedSinceStart()) "oui" else "non")
}

private fun copy(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("NovaListen", text))
}

private fun levelLabel(level: String) = when (level) {
    NovaLog.ERROR -> "ERREUR"; NovaLog.WARN -> "AVERTISSEMENT"; else -> "info"
}

private fun stamp(at: Long) = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(at))
