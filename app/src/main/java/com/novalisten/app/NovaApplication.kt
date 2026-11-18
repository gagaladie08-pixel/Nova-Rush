package com.novalisten.app

import android.app.Application
import com.novalisten.app.data.MusicRepository
import com.novalisten.app.ui.theme.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NovaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository by lazy { MusicRepository(this, applicationScope) }
    val themes by lazy { ThemePreferences(this) }
}
