package com.novalisten.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context, private val scope: CoroutineScope) {
    private val database = NovaDatabase(context)
    private val _library = MutableStateFlow(LibraryState())
    val library = _library.asStateFlow()
    private val _data = MutableStateFlow(LocalData())
    val data = _data.asStateFlow()
    private var refreshJob: Job? = null
    private var observerRegistered = false
    private val dataMutex = Mutex()
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh(debounce = true) }
    }
    init {
        scope.launch(Dispatchers.IO) { _data.value = database.snapshot() }
    }
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED
    fun audioPermission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun refresh(debounce: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (debounce) delay(450)
            if (!hasPermission()) {
                _library.value = LibraryState(permission = false)
                return@launch
            }
            if (!observerRegistered) {
                runCatching { context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer) }
                    .onSuccess { observerRegistered = true }
            }
            _library.value = _library.value.copy(permission = true, loading = true, error = null)
            try {
                val tracks = withContext(Dispatchers.IO) { scan() }
                _library.value = LibraryState(permission = true, tracks = tracks)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                _library.value = LibraryState(permission = false, error = "L’accès à la musique a été retiré. Accordez-le dans les paramètres Android.")
            } catch (_: Exception) {
                _library.value = _library.value.copy(loading = false, error = "La bibliothèque n’a pas pu être lue. Réessayez sans modifier vos fichiers.")
            }
        }
    }
    private fun scan(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf("_id", "title", "artist", "album", "album_id", if (Build.VERSION.SDK_INT >= 30) "album_artist" else "artist", "duration", "date_added", "year", "track", "_display_name", "_size", "mime_type")
        val result = ArrayList<Track>()
        context.contentResolver.query(collection, columns, "is_music != 0", null, "date_added DESC, _id DESC")?.use { c ->
            while (c.moveToNext()) {
                fun text(i: Int) = if (c.isNull(i)) "" else c.getString(i).orEmpty()
                fun known(value: String, fallback: String) = if (value.isBlank() || value == "<unknown>") fallback else value
                val duration = c.getLong(6)
                if (duration in 1 until MIN_TRACK_DURATION_MS) continue
                result += Track(
                    ContentUris.withAppendedId(collection, c.getLong(0)).toString(),
                    known(text(1), text(10).substringBeforeLast('.')), known(text(2), "Artiste inconnu"),
                    known(text(3), "Album inconnu"), c.getLong(4), known(text(5), ""), duration,
                    c.getLong(7) * 1000, c.getInt(8), c.getInt(9), text(10), c.getLong(11), text(12),
                )
            }
        }
        return result
    }
    suspend fun mutate(block: NovaDatabase.() -> Unit) = withContext(Dispatchers.IO) {
        dataMutex.withLock { database.block(); _data.value = database.snapshot() }
    }
    suspend fun createPlaylist(name: String, ids: List<String>): LocalPlaylist = withContext(Dispatchers.IO) {
        dataMutex.withLock {
            database.createPlaylist(name, ids).also { _data.value = database.snapshot() }
        }
    }
    suspend fun addToPlaylists(destinations: Set<String>, ids: List<String>): Int = withContext(Dispatchers.IO) {
        dataMutex.withLock {
            var ignored = 0
            val current = database.snapshot()
            current.playlists.filter { it.id in destinations }.forEach { playlist ->
                ignored += ids.distinct().count { it in playlist.trackIds }
                database.savePlaylist(playlist.copy(trackIds = prependUnique(playlist.trackIds, ids)))
            }
            _data.value = database.snapshot()
            ignored
        }
    }
    fun recordListen(id: String) { scope.launch { mutate { addEvent(eventAt(id)) } } }
}
