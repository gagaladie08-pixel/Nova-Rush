package com.novalisten.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.data.CatalogChoices
import com.novalisten.app.data.SplitRules
import com.novalisten.app.data.Track
import com.novalisten.app.data.ArtistSplitter
import com.novalisten.app.ui.components.Notice
import com.novalisten.app.ui.components.PageTitle
import com.novalisten.app.ui.theme.LocalUniverse
import com.novalisten.app.ui.theme.NovaIcon

/**
 * Découpage des artistes : ce que l'application ne peut pas deviner, vous le décidez ici.
 * Deux listes vérifiables (noms protégés, séparateurs) et un audit sur vos morceaux réels.
 */
@Composable
fun SplitSettingsScreen(tracks: List<Track>, choices: CatalogChoices, vm: MainViewModel, onBack: () -> Unit) {
    val universe = LocalUniverse.current
    var rules by remember(choices.split) { mutableStateOf(choices.split) }
    var newName by remember { mutableStateOf("") }
    var newSeparator by remember { mutableStateOf("") }
    var showAudit by remember { mutableStateOf(false) }
    val dirty = rules != choices.split

    // Aperçu vivant : trois exemples, dont un nom protégé, pour vérifier la règle d'un coup d'œil.
    val preview = remember(rules) {
        listOf("Rihanna, Drake", "Earth, Wind & Fire", "Anyma & LISA").map { it to ArtistSplitter.split(it, rules) }
    }
    // Audit réel : uniquement les noms qui seraient effectivement découpés dans la bibliothèque.
    val affected = remember(tracks, rules) {
        tracks.map { it.artist }.distinct().filter { ArtistSplitter.wouldSplit(it, rules) }.sortedBy { it.lowercase() }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("split_settings"), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { PageTitle("Découpage des artistes", "${rules.protectedNames.size} noms protégés · ${rules.separators.size} séparateurs") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        item { Notice("L’application ne peut pas savoir qu’un nom contient une virgule qui fait partie du nom. Cette liste le lui dit. Vos fichiers audio ne sont jamais modifiés : seul l’affichage change.") }

        item { SplitSection("Aperçu") }
        items(preview) { (raw, parts) -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("« $raw »", Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (parts.size > 1) parts.joinToString(" · ") else "entier", Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = if (parts.size > 1) universe.accent else universe.text, maxLines = 1)
        } }

        item { SplitSection("Séparateurs") }
        items(SplitRules.AVAILABLE) { separator ->
            val active = separator in rules.separators
            Row(Modifier.fillMaxWidth().clickable {
                rules = rules.copy(separators = if (active) rules.separators - separator else rules.separators + separator)
            }.padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = active, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(label(separator), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (separator == "&") Text("désactivé par défaut", style = MaterialTheme.typography.labelSmall, color = universe.readableSecondaryText())
            }
        }
        item { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newSeparator, { newSeparator = it.take(2) }, Modifier.weight(1f).testTag("new_separator"),
                label = { Text("Ajouter un séparateur (2 caractères)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val value = newSeparator.trim()
                    if (value.isNotEmpty() && value !in rules.separators) rules = rules.copy(separators = rules.separators + value)
                    newSeparator = ""
                }))
            IconButton({ val value = newSeparator.trim()
                if (value.isNotEmpty() && value !in rules.separators) rules = rules.copy(separators = rules.separators + value)
                newSeparator = "" }, enabled = newSeparator.isNotBlank()) { NovaIcon(Icons.Outlined.Add, "Ajouter") }
        } }
        // Un séparateur ajouté à la main doit pouvoir être retiré, même s'il n'est pas dans la liste standard.
        val custom = rules.separators.filter { it !in SplitRules.AVAILABLE }
        if (custom.isNotEmpty()) item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ajoutés : " + custom.joinToString("  "), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton({ rules = rules.copy(separators = rules.separators - custom.toSet()) }) { Text("Retirer") }
        } }

        item { SplitSection("Noms à ne jamais découper") }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newName, { newName = it }, Modifier.weight(1f).testTag("new_protected_name"),
                label = { Text("Ajouter un nom complet") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val value = newName.trim()
                    if (value.isNotEmpty() && rules.protectedNames.none { it.equals(value, true) }) rules = rules.copy(protectedNames = rules.protectedNames + value)
                    newName = ""
                }))
            IconButton({ val value = newName.trim()
                if (value.isNotEmpty() && rules.protectedNames.none { it.equals(value, true) }) rules = rules.copy(protectedNames = rules.protectedNames + value)
                newName = "" }, enabled = newName.isNotBlank()) { NovaIcon(Icons.Outlined.Add, "Protéger ce nom") }
        } }
        items(rules.protectedNames) { name ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton({ rules = rules.copy(protectedNames = rules.protectedNames - name) }) { NovaIcon(Icons.Outlined.Close, "Ne plus protéger $name") }
            }
        }

        item { SplitSection("Ce que cela change dans votre bibliothèque") }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (affected.isEmpty()) "Aucun nom ne sera découpé." else "${affected.size} nom(s) seront découpés en plusieurs artistes.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (affected.isNotEmpty()) TextButton({ showAudit = !showAudit }) { Text(if (showAudit) "Masquer" else "Voir") }
        } }
        if (showAudit) items(affected) { raw ->
            val parts = ArtistSplitter.split(raw, rules)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(raw, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("→ " + parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = universe.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Annuler n'efface pas la séparation : cela protège le nom entier, ce qui est réversible.
                TextButton({ rules = rules.copy(protectedNames = rules.protectedNames + raw) }) { Text("Garder entier") }
            }
        }
        if (affected.isNotEmpty() && showAudit) item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ rules = rules.copy(protectedNames = rules.protectedNames + affected) }) { Text("Tout garder entier") }
        } }

        item {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ rules = choices.split }, Modifier.weight(1f), enabled = dirty) { Text("Annuler") }
                Button({ vm.saveSplitRules(rules) }, Modifier.weight(1f), enabled = dirty) { Text("Enregistrer") }
            }
        }
        if (dirty) item { Text("Modifications non enregistrées", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelSmall, color = universe.accent) }
    }
}

@Composable
private fun SplitSection(title: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalUniverse.current.accent)
    }
}

private fun label(separator: String): String = when (separator) {
    ";" -> "Point-virgule  ;"
    "feat." -> "feat."
    "featuring" -> "featuring"
    "ft." -> "ft."
    "/" -> "Barre oblique  /"
    "," -> "Virgule  ,"
    "&" -> "Esperluette  &"
    else -> separator
}
