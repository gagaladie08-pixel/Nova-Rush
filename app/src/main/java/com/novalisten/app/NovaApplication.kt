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
    val repository by lazy { MusicRepository(this, applicationScope) {
        if (artistForeground.value || artistPlaying.value != null) artistPreferences.state.value else ArtistAutomation(false, false)
    } }
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
            val local = repository.data.value
            val track = repository.catalog.value.tracks[work.trackId]
            // Native/manual credits may intentionally name a band containing punctuation.
            val unresolved = track != null && track.credits.isEmpty() && track.id !in local.catalog.artists &&
                local.catalog.resolvedArtists[track.id]?.matches(track) != true && !com.novalisten.app.data.validMusicId(work.artist.mbid) &&
                work.artist.name == com.novalisten.app.data.CatalogText.display(track.artist)
            if (!unresolved || ArtistCredits.candidates(work.artist.name).size <= 1) false else {
                val credits = remote.collaborationCredits(work.artist)
                if (credits.isNotEmpty()) repository.saveResolvedArtists(work.trackId, work.artist, credits)
                // A genuine group can still be verified as a whole if no collaboration matched.
                credits.isNotEmpty()
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
                val current = index.ready && index.sourceChoices == local.catalog && index.sourceHidden == local.hidden
                val (works, notes) = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    artistLibraryWorks(index, local, priority.second, priority.third) to index.artists.associate { it.id to artistNote(it, local) }
                }
                automaticArtists.onLibrary(works, settings, notes,
                    current && (priority.first || priority.second != null), index.artistsByTrack[priority.second].orEmpty().map { it.id }.toSet(), priority.third.toSet())
            }.collect { }
        }
    }
    val translation by lazy { LocalTranslation(this) }
}
