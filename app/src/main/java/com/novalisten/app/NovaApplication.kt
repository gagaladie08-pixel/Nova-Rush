package com.novalisten.app

import android.app.Application
import com.novalisten.app.data.MusicRepository
import com.novalisten.app.enrichment.*
import com.novalisten.app.ui.theme.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import com.novalisten.app.data.artistNote
import com.novalisten.app.data.ArtistCredits

class NovaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository by lazy { MusicRepository(this, applicationScope) }
    val themes by lazy { ThemePreferences(this) }
    val credentials by lazy { CredentialVault(this) }
    private val http by lazy { RemoteHttp() }
    val remote by lazy { RemoteSources(http, credentials::get) }
    val lyricPreferences by lazy { LyricPreferences(this) }
    val automaticLyrics by lazy { AutomaticLyrics(applicationScope, remote, persist = repository::saveAutomaticLyrics) }
    val remoteImages by lazy { RemoteImages(this, http) }
    val artistPreferences by lazy { ArtistPreferences(this) }
    val artistPortraits by lazy { PortraitCascade(applicationScope, RemotePortraitCatalog(remote), remoteImages::preparePhoto, remoteImages::previewAvailable) }
    val automaticArtists by lazy { AutomaticArtists(applicationScope, AutomaticArtistLookup(remote),
        prepare = { images -> PhotoDiscovery(remoteImages::preparePhoto).discover(images).photos },
        saveImage = remoteImages::save, discardImage = { uri -> kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { java.io.File(java.net.URI(uri)).delete() }; Unit } },
        persist = repository::saveAutomaticArtist,
        resolveCredits = { work ->
            val parts = ArtistCredits.candidates(work.artist.name)
            if (parts.size <= 1) false else {
                val credits = remote.collaborationCredits(work.artist)
                if (credits.isNotEmpty()) repository.saveResolvedArtists(work.trackId, work.artist, credits)
                true // Never assign a collective credit's biography to individual participants.
            }
        }, discoverPortraits = { request, mbid -> artistPortraits.initial(PhotoSubject(request.name, mbid)) }) }
    val artistForeground = MutableStateFlow(false)
    val artistPlaying = MutableStateFlow<String?>(null)
    val visibleArtists = MutableStateFlow<List<String>>(emptyList())
    private var artistsStarted = false
    fun startArtistEnrichment() {
        if (artistsStarted) return
        artistsStarted = true
        applicationScope.launch {
            combine(repository.catalog, repository.data, artistPreferences.state,
                combine(artistForeground, artistPlaying, visibleArtists) { foreground, playing, visible -> Triple(foreground, playing, visible) }) { index, local, settings, priority ->
                val works = kotlinx.coroutines.withContext(Dispatchers.Default) { artistLibraryWorks(index, local, priority.second, priority.third) }
                automaticArtists.onLibrary(works, settings, index.artists.associate { it.id to artistNote(it, local) },
                    index.ready && (priority.first || priority.second != null), index.artistsByTrack[priority.second].orEmpty().map { it.id }.toSet())
            }.collect { }
        }
    }
    val translation by lazy { LocalTranslation(this) }
}
