package com.novalisten.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.novalisten.app.ui.components.*
import com.novalisten.app.ui.theme.*

@Composable
fun AppearanceScreen(settings: ThemeSettings, preferences: ThemePreferences, onBack: () -> Unit, onCustom: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("theme_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Votre univers", "15 thèmes complets + votre création") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        item {
            NovaCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Décors en mouvement", style = MaterialTheme.typography.titleMedium); Text("Désactivez pour un rendu plus sobre. Les formes et les polices sont conservées.", style = MaterialTheme.typography.bodySmall) }
                    Switch(settings.effects, preferences::effects)
                }
            }
        }
        item { OutlinedButton(onCustom, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 54.dp).testTag("custom_theme")) { NovaIcon(Icons.Outlined.Tune, null); Spacer(Modifier.width(8.dp)); Text("Créer mon univers personnalisé") } }
        items(themeCatalog, key = { it.id }) { spec ->
            NovaTheme(spec, effects = false) {
                NovaCard(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).clickable { preferences.select(spec.id) }.testTag("theme_${spec.id}"), selected = settings.selected == spec.id) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NovaIcon(Icons.Outlined.AutoAwesome, modifier = Modifier.size(32.dp), tint = spec.accent)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(spec.name, style = MaterialTheme.typography.titleLarge); Text(if (spec.id == "cyber") "L’univers par défaut" else "${fontLabels[spec.titleFont]} · ${fontLabels[spec.bodyFont]}", style = MaterialTheme.typography.bodySmall) }
                            RadioButton(settings.selected == spec.id, onClick = { preferences.select(spec.id) })
                        }
                        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            spec.palette.take(3).plus(spec.palette.last()).forEach { color -> Box(Modifier.size(19.dp).clip(buttonShape(spec)).background(Color(color.toColorInt()))) }
                            Spacer(Modifier.weight(1f)); Text(if (spec.radius == null) "Angles coupés" else "${spec.radius} dp", style = MaterialTheme.typography.bodySmall)
                        }
                        // Aperçu vivant : formes, curseur et ornement de l'univers, pas seulement des pastilles.
                        CurvePreview(spec, Modifier.fillMaxWidth().height(45.dp).padding(top = 8.dp))
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(width = 84.dp, height = 30.dp).clip(buttonShape(spec)).background(spec.primary), contentAlignment = Alignment.Center) {
                                Text("Lire", style = MaterialTheme.typography.labelMedium, color = onColor(spec.primary))
                            }
                            Box(Modifier.size(width = 84.dp, height = 30.dp).clip(panelShape(spec)).background(spec.accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                                Text("Accent", style = MaterialTheme.typography.labelMedium, color = spec.text)
                            }
                            NovaIcon(Icons.Outlined.PlayArrow, null, Modifier.size(26.dp), tint = spec.text)
                            NovaIcon(Icons.Outlined.FavoriteBorder, null, Modifier.size(26.dp), tint = spec.text)
                            Spacer(Modifier.weight(1f))
                            Text("${spec.glyphs.name.lowercase()}", style = MaterialTheme.typography.labelSmall, color = spec.readableSecondaryText())
                        }
                    }
                }
            }
        }
        if (settings.custom != null) item {
            NovaTheme(settings.custom) {
                NovaCard(Modifier.padding(20.dp).clickable { preferences.select("custom") }, selected = settings.selected == "custom") {
                    Text("Mon univers", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
                    TextButton(onCustom, Modifier.padding(horizontal = 12.dp)) { Text("Modifier mes six catégories") }
                }
            }
        }
        item { Notice("Chaque univers transforme les couleurs, les deux polices, trois niveaux de formes (cartes, boutons, panneaux), les icônes dessinées, les décors et la barre de progression du lecteur. Palettes fournies conservées ; seuls les textes secondaires trop peu contrastés (Pink Y2K, Cloud Nine) sont rapprochés du texte principal.") }
    }
}

@Composable
fun CustomThemeScreen(settings: ThemeSettings, preferences: ThemePreferences, onBack: () -> Unit) {
    var draft by remember { mutableStateOf(settings.custom ?: settings.universe.copy(id = "custom", name = "Mon univers")) }
    var hex by remember { mutableStateOf(draft.palette.map { it.removePrefix("#") }) }
    val roles = listOf("Principale", "Secondaire", "Halo", "Fond", "Surface", "Texte", "Texte secondaire", "Accent")
    val allValid = hex.all { Regex("[A-Fa-f0-9]{6}").matches(it) }
    LazyColumn(Modifier.fillMaxSize().testTag("custom_editor"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitle("Mon univers", "Six catégories, une identité à vous") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        item {
            NovaTheme(draft, effects = false) {
                NovaCard(Modifier.padding(horizontal = 20.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { NovaIcon(Icons.Outlined.Headphones, tint = draft.accent); Spacer(Modifier.width(12.dp)); Text("NovaListen", style = MaterialTheme.typography.headlineSmall) }
                        Text("L’habillage change. Votre musique reste.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
                        CurvePreview(draft, Modifier.fillMaxWidth().height(64.dp))
                        Text("Aperçu décoratif · aucune donnée d’écoute", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { SectionHeader("01 / Couleurs", "Huit rôles, codes hexadécimaux exacts") }
        items(4) { row ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (column in 0..1) {
                    val index = row * 2 + column
                    OutlinedTextField(hex[index], { input ->
                        val value = input.removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hex = hex.toMutableList().apply { this[index] = value }
                        if (value.length == 6) draft = draft.copy(palette = draft.palette.toMutableList().apply { this[index] = "#$value" })
                    }, modifier = Modifier.weight(1f), label = { Text(roles[index], style = MaterialTheme.typography.labelSmall) }, prefix = { Text("#") }, singleLine = true, isError = hex[index].length != 6)
                }
            }
        }
        item { SectionHeader("02 / Polices", "25 familles incluses, aucun téléchargement") }
        item { ChoiceMenu("Titres", draft.titleFont, fontLabels) { draft = draft.copy(titleFont = it) } }
        item { ChoiceMenu("Texte", draft.bodyFont, fontLabels) { draft = draft.copy(bodyFont = it) } }
        item { SectionHeader("03 / Icônes", "Le dessin change, pas la fonction") }
        item { ChoiceMenu("Style", draft.icons.name, IconStyle.entries.associate { it.name to it.name.lowercase().replaceFirstChar(Char::uppercase) }) { draft = draft.copy(icons = IconStyle.valueOf(it)) } }
        item { SectionHeader("04 / Formes", "Les portraits restent ronds, les pochettes carrées") }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Angles coupés", Modifier.weight(1f)); Switch(draft.radius == null, { draft = draft.copy(radius = if (it) null else 6) }) }
                if (draft.radius != null) {
                    Text("Arrondi des cartes : ${draft.radius} dp")
                    Slider(draft.radius!!.toFloat(), { draft = draft.copy(radius = it.toInt()) }, valueRange = 0f..32f, steps = 31)
                } else Text("Coupe relative d’aperçu : 12 % · premier réglage technique", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SectionHeader("05 / Animations", "Décor et transition") }
        item { ChoiceMenu("Ambiance", draft.effect, themeCatalog.associate { it.effect to it.name } + ("none" to "Sans décor animé")) { draft = draft.copy(effect = it) } }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Durée de transition fixe", Modifier.weight(1f)); Switch(draft.transitionMs != null, { draft = draft.copy(transitionMs = if (it) 280 else null) }) }
                if (draft.transitionMs != null) {
                    Text("Transition : ${draft.transitionMs} ms")
                    Slider(draft.transitionMs!!.toFloat(), { draft = draft.copy(transitionMs = it.toInt()) }, valueRange = 100f..700f)
                } else Text("Transition à ressort, sans durée fixe.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SectionHeader("06 / Styles de courbes", "Trait, remplissage et halo, jamais les valeurs") }
        item { ChoiceMenu("Style", draft.curveStyle, themeCatalog.associate { it.id to it.name }) { draft = draft.copy(curveStyle = it) } }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Épaisseur : %.1f dp".format(draft.curveWidth)); Slider(draft.curveWidth, { draft = draft.copy(curveWidth = it) }, valueRange = 1f..6f)
                Text("Halo : ${draft.curveHalo.toInt()} dp"); Slider(draft.curveHalo, { draft = draft.copy(curveHalo = it) }, valueRange = 0f..20f)
            }
        }
        item { Notice("Le profil personnalisé est sauvegardé dans NovaListen sans modifier les 15 préréglages. Pas d’import de police, script ou pack externe dans cette version.") }
        item { Button({ preferences.saveCustom(draft); onBack() }, Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 52.dp), enabled = allValid) { Text("Enregistrer et appliquer") } }
    }
}

@Composable
private fun ChoiceMenu(label: String, selected: String, options: Map<String, String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) {
            Text("$label : ${options[selected] ?: selected}", Modifier.weight(1f)); NovaIcon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 320.dp)) {
            options.forEach { (key, name) -> DropdownMenuItem({ Text(name) }, { onSelect(key); expanded = false }) }
        }
    }
}
