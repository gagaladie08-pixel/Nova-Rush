package com.novalisten.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novalisten.app.MainViewModel
import com.novalisten.app.enrichment.*
import com.novalisten.app.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ApiSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val credentials by vm.credentials.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SecretSlot?>(null) }
    var deletingAll by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize().testTag("api_settings"), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { PageTitle("API et clés", "Aucune recherche au démarrage ni à l’enregistrement d’une clé") { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Retour") } } }
        item { Notice("La musique reste locale. Les commandes Rechercher en ligne transmettent uniquement les termes affichés (titre, artiste, album, durée ou identifiant), jamais l’audio, l’historique ou toute la bibliothèque. Aucun résultat n’est appliqué sans votre choix.") }
        item { SectionHeader("Sources sans clé") }
        item { Notice("Paroles : LRCLIB, lrc.red et LiriQo (noyau), Better Lyrics limité au cache public, Textyl et lyrics.ovh sur demande. lrc.red exige un courriel de contact, à renseigner plus bas. Lyrica reste en réserve ; aucun connecteur privé de plateforme n’est activé.") }
        item { Notice("Métadonnées : MusicBrainz, puis iTunes Search sur comparaison volontaire avec choix de boutique. Pochettes : Cover Art Archive par édition choisie. Artistes : Wikipédia FR, anglais proposé séparément, Wikidata, Commons et Openverse anonyme avec quotas. L’écriture des tags dans les fichiers reste distincte et non intégrée.") }
        item { Notice("Photos : Genius/TheAudioDB/Discogs non activés comme collecteurs tant que leurs usages photo ne sont pas établis. Commons/Openverse sont donc les premières branches admissibles ; fanart.tv reste l’ultime complément. Licence, source et choix manuel sont préservés ; aucune garantie de six résultats.") }
        item { SectionHeader("Traduction locale facultative") }
        item { Notice("ML Kit : pas de clé. Packs téléchargés seulement par le bouton dédié. La traduction est calculée sur l’appareil ; le SDK Google déclare néanmoins des diagnostics d’appareil, d’application et d’usage après activation. Aucun SDK publicitaire ni analyse de votre collection. L’initialisation automatique de ML Kit au lancement est retirée.") }
        item { TextButton({ runCatching { uri.openUri("https://developers.google.com/ml-kit/android-data-disclosure") } }) { Text("Confidentialité et diagnostics ML Kit") } }
        item { Notice("La commande Translate with Google utilise Google Translate sur l’appareil via ML Kit. Les traductions automatiques ne garantissent ni exactitude ni fidélité poétique ; l’attribution Google est affichée à côté des résultats.") }
        item { TextButton({ runCatching { uri.openUri("https://cloud.google.com/translate") } }) { Text("À propos de Google Translate / Cloud Translation") } }
        item { SectionHeader("Vos identifiants sur ce téléphone") }
        item { Notice("Chiffrés par AES-GCM avec une clé Android Keystore. Jamais intégrés au code, à l’APK ou aux sauvegardes Android. La saisie est masquée, non restaurée après fermeture, et protégée contre les captures. Une clé enregistrée n’est pas une clé vérifiée. Un appareil compromis ne peut pas être rendu inviolable.") }
        if (feedback.isNotBlank()) item { Notice(feedback) }
        items(SecretSlot.entries, key = { it.name }) { slot ->
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).testTag("credential_${slot.name}")) {
                Column(Modifier.padding(14.dp)) {
                    Text(slot.label, style = MaterialTheme.typography.titleMedium)
                    Text(slot.help, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    val status = credentials[slot] ?: CredentialStatus.ABSENT
                    Text(when (status) { CredentialStatus.STORED -> "Enregistré sur cet appareil · non testé"; CredentialStatus.UNREADABLE -> "Stockage illisible · ressaisir ou supprimer"; else -> "Non renseigné" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("credential_status_${slot.name}"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ editing = slot }, Modifier.testTag("credential_edit_${slot.name}")) { Text(if (status == CredentialStatus.ABSENT) "Renseigner" else "Remplacer") }
                        if (status != CredentialStatus.ABSENT) TextButton({ scope.launch {
                            try { vm.credentials.remove(slot); feedback = "Identifiant supprimé de ce téléphone." } catch (e: Exception) { feedback = e.publicMessage() }
                        } }, Modifier.testTag("credential_delete_${slot.name}")) { Text("Supprimer") }
                        TextButton({ runCatching { uri.openUri(slot.website) }.onFailure { feedback = "Aucun navigateur disponible." } }) { Text("Site officiel") }
                    }
                }
            }
        }
        item { Notice("Azure : ressource standard F0 uniquement. NovaListen ne peut pas connaître le niveau tarifaire d’une clé ; vous devrez le confirmer avant tout envoi. Aucun passage automatique au payant ni repli depuis ML Kit. TheAudioDB : clé stockable mais appels désactivés faute d’accès gratuit confirmé. Deezer : accès non confirmé. Spotify, Flickr direct et Bandsintown ne sont pas ajoutés.") }
        item { OutlinedButton({ deletingAll = true }, Modifier.fillMaxWidth().padding(20.dp).testTag("delete_all_credentials")) { Text("Effacer tous mes identifiants") } }
    }
    editing?.let { slot -> CredentialEditor(slot, vm, { editing = null }) { feedback = "Identifiant chiffré et enregistré uniquement sur ce téléphone. Aucun test réseau effectué."; editing = null } }
    if (deletingAll) AlertDialog(onDismissRequest = { deletingAll = false }, title = { Text("Effacer tous les identifiants ?") }, text = { Text("Les clés, la région Azure et le courriel lrc.red seront retirés de ce téléphone. Vos musiques et contenus sauvegardés seront conservés. Cela ne révoque pas les clés chez les fournisseurs.") }, confirmButton = { TextButton({ scope.launch {
        try { vm.credentials.clear(); feedback = "Tous les identifiants locaux ont été effacés." } catch (e: Exception) { feedback = e.publicMessage() }
        deletingAll = false
    } }) { Text("Tout effacer") } }, dismissButton = { TextButton({ deletingAll = false }) { Text("Annuler") } })
}

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@Composable
private fun CredentialEditor(slot: SecretSlot, vm: MainViewModel, onDismiss: () -> Unit, onSaved: () -> Unit) {
    // Not rememberSaveable: credentials must never enter a Bundle or saved-instance state.
    var input by remember(slot) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalContext.current.activity()?.window
    DisposableEffect(lifecycle, window) {
        val alreadySecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { input = ""; visible = false } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); if (!alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    AlertDialog(onDismissRequest = { if (!busy) { input = ""; onDismiss() } }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(slot.label) }, text = {
            Column {
                Text(slot.help, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(input, { input = it.take(4096); error = "" }, Modifier.fillMaxWidth().testTag("credential_input"), singleLine = true,
                    label = { Text("Nouvelle valeur · jamais affichée ensuite") }, enabled = !busy,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Masquer la saisie" else "Voir la saisie") } })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("Aucun appel de vérification. Aucun mot de passe de compte à saisir ici.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton({
            scope.launch {
                busy = true
                try { vm.credentials.save(slot, input); input = ""; onSaved() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { error = e.publicMessage() }
                finally { busy = false }
            }
        }, enabled = !busy && (input.isNotBlank() || slot == SecretSlot.AZURE_REGION), modifier = Modifier.testTag("credential_save")) { Text(if (busy) "Chiffrement…" else "Enregistrer sur ce téléphone") } },
        dismissButton = { TextButton({ input = ""; onDismiss() }, enabled = !busy) { Text("Annuler") } })
}
