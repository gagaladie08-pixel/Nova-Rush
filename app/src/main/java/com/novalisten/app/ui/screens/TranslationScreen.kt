package com.novalisten.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novalisten.app.MainViewModel
import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import com.novalisten.app.ui.components.*

@Composable
internal fun TranslationScreen(track: Track, vm: MainViewModel, onDismiss: () -> Unit) {
    val original = remember(track.id) { vm.repository.data.value.lyrics[track.id]?.original.orEmpty() }
    var language by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
    var wifiOnly by remember { mutableStateOf(true) }
    var cloudF0 by remember { mutableStateOf(false) }
    var cloudConsent by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var savingConfirmed by remember { mutableStateOf(false) }
    val work = rememberRemoteWork()
    val saveWork = rememberRemoteWork()
    fun sourceChosen(): Boolean = language.isNotBlank() && acknowledged && original.isNotBlank() && !work.busy
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Notice("${track.title} — ${track.artist}\nTraduction de la VO sauvegardée (${original.length} caractères), pas du fichier audio. Langue source à choisir ; aucun transfert après une erreur locale. L’ancienne VF reste en place jusqu’à votre choix.") }
        if (original.isBlank()) item { Notice("Ajoutez ou choisissez d’abord des paroles originales. Aucun service n’est appelé.") }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("en" to "Anglais", "es" to "Espagnol", "ko" to "Coréen").forEach { (code, label) ->
                FilterChip(language == code, { language = code; result = ""; progress = ""; cloudConsent = false }, { Text(label) }, enabled = !work.busy)
            }
        } }
        item { Notice("ML Kit calcule sur l’appareil après téléchargement des packs. Le SDK Google déclare des diagnostics d’appareil, d’application et de performances après activation. En dehors de la traduction elle-même, ce n’est donc pas une promesse de zéro communication réseau. EN/ES/KO → FR ; les traductions entre langues autres que l’anglais passent par l’anglais.") }
        item { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(acknowledged, { acknowledged = it }); Text("J’ai les droits de traduire ce texte et j’accepte l’activation de ML Kit avec ces diagnostics.", style = MaterialTheme.typography.bodySmall) } }
        item { Button({ work.run {
            result = ""; savingConfirmed = false
            val plan = TranslationPlan(original)
            progress = "Traduction locale en cours…"
            result = vm.translation.translate(plan, language) { completed, total -> progress = "$completed / $total lignes distinctes traduites localement" }
            engine = "Google Translate · ML Kit sur l’appareil"
            progress = "Traduction locale proposée, pas encore sauvegardée."
        } }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = sourceChosen()) { Text("Translate with Google · sur l’appareil") } }
        item { WorkStatus(work) }
        if (progress.isNotBlank()) item { Notice(progress) }
        item { SectionHeader("Packs de langues · téléchargement volontaire") }
        item { Notice("Si les packs manquent, la traduction s’arrête sans les télécharger. Comptez environ 30 Mo par modèle (volume réel variable). Le lecteur et la VO restent disponibles sans packs. Annuler l’attente peut laisser Android terminer un téléchargement déjà demandé.") }
        item { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(wifiOnly, { wifiOnly = it }); Text("Télécharger les packs uniquement en Wi-Fi", style = MaterialTheme.typography.bodySmall) } }
        item { OutlinedButton({ work.run {
            progress = "Téléchargement volontaire des packs requis…"
            vm.translation.download(language, wifiOnly)
            progress = "Packs disponibles. Vous pouvez lancer la traduction locale."
        } }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = sourceChosen()) { Text("Télécharger les packs pour cette langue → FR") } }
        item { TextButton({ deleting = true }, Modifier.padding(horizontal = 20.dp), enabled = !work.busy) { Text("Supprimer les packs de traduction de NovaListen") } }
        item { SectionHeader("Autre traduction · Azure F0, uniquement sur demande") }
        item { Notice("Cette option envoie les lignes de la VO à Microsoft avec leur langue, sans audio, titre ni historique. La clé doit appartenir à une ressource Text Translation standard F0. NovaListen ne peut pas vérifier son niveau tarifaire ; n’utilisez pas une clé S1 ou une offre facturable. Aucun fallback ni changement de forfait automatique.") }
        item { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(cloudF0, { cloudF0 = it }); Text("J’ai vérifié dans Azure que cette ressource est bien au niveau gratuit F0.", style = MaterialTheme.typography.bodySmall) } }
        item { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(cloudConsent, { cloudConsent = it }); Text("Je demande explicitement l’envoi de cette VO à Azure pour cette traduction et j’en ai les droits.", style = MaterialTheme.typography.bodySmall) } }
        item { OutlinedButton({ work.run {
            result = ""; savingConfirmed = false
            val plan = TranslationPlan(original)
            progress = "Traduction distante demandée à Azure F0…"
            // Consume the confirmation, even on error. No automatic repeat or provider switch.
            cloudConsent = false
            result = vm.remote.azure(plan, language)
            engine = "Azure Translator · traduction automatique demandée en ligne"
            progress = "Traduction Azure proposée, pas encore sauvegardée."
        } }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = !work.busy && original.isNotBlank() && language.isNotBlank() && cloudF0 && cloudConsent) { Text("Envoyer cette VO et traduire avec Azure F0") } }
        if (result.isNotBlank()) {
            item { SectionHeader("Proposition française", "Ligne par ligne ; qualité poétique non garantie") }
            item { Column(Modifier.padding(horizontal = 20.dp)) {
                if (engine.startsWith("Google")) GoogleTranslationAttribution()
                else Text(engine, style = MaterialTheme.typography.labelLarge)
                Text(result, style = MaterialTheme.typography.bodyLarge)
            } }
            item { Notice("Les instants de ligne existants sont conservés, pas réinventés. Ce ne sont pas des temps phonétiques des mots français. Les répétitions identiques sont traduites une seule fois ; le contexte entre lignes peut manquer.") }
            item { Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(savingConfirmed, { savingConfirmed = it }); Text("Remplacer ma VF sauvegardée par cette proposition ; garder la VO.", style = MaterialTheme.typography.bodySmall) } }
            item { WorkStatus(saveWork) }
            item { Button({ saveWork.run {
                val current = vm.repository.data.value.lyrics[track.id] ?: SavedLyrics()
                if (current.original != original) throw RemoteFailure("La VO a changé pendant la traduction. Rien n’a été sauvegardé ; recommencez avec le texte actuel.")
                val source = Credit(engine, if (engine.startsWith("Google")) "https://translate.google.com/" else "https://learn.microsoft.com/azure/ai-services/translator/", details = "$language → fr · traduction automatique par ligne ; instants repris de la VO, pas de minutage des mots français.").text()
                vm.repository.mutate { saveLyrics(track.id, current.copy(french = result, frenchSource = source)) }
                vm.message("VF choisie sauvegardée hors ligne ; VO conservée")
                onDismiss()
            } }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), enabled = savingConfirmed && !work.busy && !saveWork.busy) { Text("Choisir et sauvegarder cette VF") } }
        }
        item { Notice("Cette première intégration accepte jusqu’à 20 000 caractères et 500 lignes, sans troncature. Pour corriger une VF, utilisez l’éditeur local. Google Translate ne garantit ni exactitude, ni fidélité poétique ; référez-vous à la VO.") }
        item { Text("THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall) }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Supprimer les packs ?") }, text = { Text("Supprime les modèles de traduction téléchargés par NovaListen, pas vos textes, VF ou musiques. L’activation du gestionnaire ML Kit peut émettre ses diagnostics déclarés.") }, confirmButton = { TextButton({ deleting = false; work.run { vm.translation.deletePacks(); progress = "Packs supprimés, textes conservés." } }) { Text("Supprimer les packs") } }, dismissButton = { TextButton({ deleting = false }) { Text("Annuler") } })
}
