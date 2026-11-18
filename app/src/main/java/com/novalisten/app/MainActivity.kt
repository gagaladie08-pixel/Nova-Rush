package com.novalisten.app

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novalisten.app.data.ArtistNote
import com.novalisten.app.data.Track
import com.novalisten.app.ui.NovaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var imageKind: String? = null
    private var imageId: String? = null
    private var deleting = arrayListOf<String>()
    private var legacyDeleted = 0
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.repository.refresh()
        if (!granted) vm.message("Accès refusé. Vos fichiers restent privés ; vous pouvez autoriser la musique depuis les réglages Android.")
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.message(if (granted) "Notifications de lecture autorisées" else "Notifications refusées · la lecture locale reste accessible")
    }
    private val legacyWrite = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) deleteLegacy() else { deleting.clear(); vm.message("Autorisation d’écriture refusée. Aucun fichier supprimé.") }
    }
    private val systemDelete = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            if (Build.VERSION.SDK_INT >= 30) {
                val ids = deleting.toList(); deleting.clear()
                lifecycleScope.launch {
                    val removed = withContext(Dispatchers.IO) { ids.count { id -> runCatching { contentResolver.query(Uri.parse(id), arrayOf("_id"), null, null, null)?.use { !it.moveToFirst() } ?: true }.getOrDefault(false) } }
                    finishDeletion(removed, ids.size - removed)
                }
            } else deleteLegacy()
        } else { deleting.clear(); vm.repository.refresh(); vm.message("Demande de suppression annulée. Aucune réussite globale annoncée.") }
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val kind = imageKind; val id = imageId
        imageKind = null; imageId = null
        if (uri != null && kind != null && id != null) lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { copyImage(uri, "$kind:$id") } }
            result.onSuccess { localUri ->
                if (kind == "artist") vm.saveArtist(id, (vm.repository.data.value.artists[id] ?: ArtistNote()).copy(portrait = localUri, portraitSource = "Image importée volontairement depuis le téléphone ; droits à conserver par l’utilisateur."))
                else vm.repository.data.value.playlists.find { it.id == id }?.let { vm.savePlaylist(it.copy(cover = localUri)) }
                vm.message("Image copiée dans NovaListen pour rester disponible hors ligne")
            }.onFailure { vm.message("Image impossible à importer. Choisissez une image locale lisible.") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageKind = savedInstanceState?.getString("image_kind")
        imageId = savedInstanceState?.getString("image_id")
        deleting = savedInstanceState?.getStringArrayList("deleting") ?: arrayListOf()
        legacyDeleted = savedInstanceState?.getInt("legacy_deleted") ?: 0
        enableEdgeToEdge()
        setContent {
            val theme by vm.themes.state.collectAsStateWithLifecycle()
            // Read during composition, not only inside SideEffect, so a theme
            // change also updates system bar contrast without recreating the activity.
            val lightBars = theme.universe.background.luminance() > .5f
            SideEffect { applySystemBars(lightBars) }
            NovaApp(vm, { audioPermission.launch(vm.repository.audioPermission()) }, ::requestNotifications,
                { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }, ::requestDeletion,
                { kind, id -> imageKind = kind; imageId = id; imagePicker.launch(arrayOf("image/*")) })
        }
    }
    override fun onResume() { super.onResume(); vm.repository.refresh(); applySystemBars(vm.themes.state.value.universe.background.luminance() > .5f) }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars(vm.themes.state.value.universe.background.luminance() > .5f)
    }
    private fun applySystemBars(light: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("image_kind", imageKind); outState.putString("image_id", imageId); outState.putStringArrayList("deleting", deleting); outState.putInt("legacy_deleted", legacyDeleted)
        super.onSaveInstanceState(outState)
    }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.message("Les notifications de lecture sont gérées dans les paramètres Android de NovaListen.")
    }
    private fun requestDeletion(tracks: List<Track>) {
        if (deleting.isNotEmpty()) return vm.message("Une demande de suppression est déjà en cours.")
        deleting = ArrayList(tracks.map { it.id }); legacyDeleted = 0
        if (deleting.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { val request = MediaStore.createDeleteRequest(contentResolver, deleting.map(Uri::parse)); systemDelete.launch(IntentSenderRequest.Builder(request.intentSender).build()) }
                .onFailure { deleting.clear(); vm.message("Android ne permet pas cette suppression. Essayez une sélection plus petite ou utilisez votre gestionnaire de fichiers.") }
        } else if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) legacyWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else deleteLegacy()
    }
    private fun deleteLegacy() {
        lifecycleScope.launch {
            var failed = 0
            while (deleting.isNotEmpty()) {
                val id = deleting.first()
                val result = withContext(Dispatchers.IO) { runCatching { contentResolver.delete(Uri.parse(id), null, null) } }
                val error = result.exceptionOrNull()
                if (Build.VERSION.SDK_INT == 29 && error is RecoverableSecurityException) {
                    runCatching { systemDelete.launch(IntentSenderRequest.Builder(error.userAction.actionIntent.intentSender).build()) }.onFailure { deleting.clear(); vm.message("Autorisation de suppression indisponible.") }
                    return@launch
                }
                if ((result.getOrNull() ?: 0) > 0) legacyDeleted++ else failed++
                deleting.removeAt(0)
            }
            finishDeletion(legacyDeleted, failed)
        }
    }
    private fun finishDeletion(removed: Int, failed: Int) {
        vm.repository.refresh()
        vm.message("$removed fichier(s) supprimé(s)${if (failed > 0) " · $failed non supprimé(s) ou non vérifiable(s)" else ""}. Les références de playlists et événements historiques sont conservés.")
    }
    private fun copyImage(uri: Uri, key: String): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0)
        var sample = 1
        while (options.outWidth / sample > 1600 || options.outHeight / sample > 1600) sample *= 2
        val bitmap = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Image illisible")
        val folder = File(filesDir, "images").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val destination = File(folder, "$name-${System.currentTimeMillis()}.jpg")
        try { destination.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } } finally { bitmap.recycle() }
        return Uri.fromFile(destination).toString()
    }
}
