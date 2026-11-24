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
import com.novalisten.app.ui.theme.NovaIcon

@Composable
fun ApiSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val credentials by vm.credentials.status.collectAsStateWithLifecycle()
    val artistAutomation by vm.artistPreferences.state.collectAsStateWithLifecycle()
    val automaticLyrics by vm.lyricPreferences.automatic.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SecretSlot?>(null) }
    var deletingAll by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize().testTag("api_settings"), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { PageTitle("API et clés", "Recherches et confidentialité") { IconButton(onBack) { NovaIcon(Icons.Outlined.ArrowBack, "Retour") } } }
        item { Notice("La musique reste locale. Le chargement automatique des paroles transmet seulement titre, artiste, album et durée du morceau réellement écouté aux sources admissibles du noyau. Aucun audio, historique ou envoi de la collection. Vos paroles sauvegardées ont priorité et ne sont jamais remplacées automatiquement. Pas de recherche au simple lancement ni à la saisie d’une clé, pas de traduction automatique.") }
        item { ListItem(headlineContent = { Text("Charger les paroles pendant l’écoute") }, supportingContent = { Text("Seulement le morceau en cours. Les paroles retenues, leur source et leur synchronisation sont conservées sur cet appareil pour la réouverture hors ligne, sans nouvelle recherche. Rechercher une autre version et Traduire restent dans les trois points.") }, trailingContent = { Switch(automaticLyrics, { vm.lyricPreferences.automatic(it) }, Modifier.testTag("automatic_lyrics")) }) }
        item { Notice("Les artistes sont complétés progressivement dans la bibliothèque, même sans écoute : priorité à la lecture et aux cartes visibles, puis aux autres. Selon les options ci-dessous, leurs noms/identifiants et si nécessaire titre, album et durée d’un morceau de contexte sont transmis aux sources admissibles. Ces informations peuvent donc décrire progressivement une partie de votre collection. Aucun audio, fichier, URI locale ou historique envoyé. Désactivez ces options pour arrêter ces recherches ; les contenus déjà sauvegardés restent locaux.") }
        item { ListItem(headlineContent = { Text("Biographies automatiques de la bibliothèque") }, supportingContent = { Text("Identité croisée MusicBrainz/Wikidata et introduction Wikipédia FR liée. Le texte est enregistré sans attendre la photo. Anglais sur demande, aucune traduction automatique. Vos textes et effacements manuels restent prioritaires.") }, trailingContent = { Switch(artistAutomation.biography, { vm.artistPreferences.biography(it) }, Modifier.testTag("automatic_artist_biography")) }) }
        item { ListItem(headlineContent = { Text("Portraits automatiques de la bibliothèque") }, supportingContent = { Text("Choix automatique seulement pour un portrait Commons lié à l’identité vérifiée et sous licence ouverte. Sinon propositions à vérifier, sans attribution au hasard. Vos choix manuels ne sont pas écrasés ; images et crédits retenus restent hors ligne. Deux requêtes au total partagées avec les paroles.") }, trailingContent = { Switch(artistAutomation.portrait, { vm.artistPreferences.portrait(it) }, Modifier.testTag("automatic_artist_portrait")) }) }
        item { SectionHeader("Sources sans clé") }
        item { Notice("Paroles : LRCLIB, lrc.red et LiriQo (noyau), Better Lyrics limité au cache public, Textyl et lyrics.ovh sur demande. lrc.red exige un courriel de contact, à renseigner plus bas. Lyrica reste en réserve ; aucun connecteur privé de plateforme n’est activé.") }
        item { Notice("Métadonnées : MusicBrainz, puis iTunes Search sur comparaison volontaire avec choix de boutique. Pochettes : Cover Art Archive par édition choisie. Artistes : Wikipédia FR, anglais proposé séparément, Wikidata, Commons et Openverse anonyme avec quotas. L’écriture des tags dans les fichiers reste distincte et non intégrée.") }
        item { Notice("Photos : Genius/TheAudioDB/Discogs non activés comme collecteurs tant que leurs usages photo ne sont pas établis. Commons/Openverse sont donc les premières branches admissibles ; fanart.tv reste la dernière route conditionnelle, sautée sans droits établis, avec consultation externe volontaire. Licence, source et choix manuel restent prioritaires ; un portrait peut être choisi automatiquement seulement sur le lien d’identité vérifié. Aucune garantie de six résultats.") }
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
                    trailingIcon = { IconButton({ visible = !visible }) { NovaIcon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Masquer la saisie" else "Voir la saisie") } })
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
