package com.novalisten.app

import android.app.Application
import com.novalisten.app.data.MusicRepository
import com.novalisten.app.enrichment.*
import com.novalisten.app.ui.theme.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val translation by lazy { LocalTranslation(this) }
}
