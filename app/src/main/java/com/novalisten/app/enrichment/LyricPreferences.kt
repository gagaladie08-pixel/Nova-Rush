package com.novalisten.app.enrichment

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user requested automatic current-song loading; this preference never enables cloud translation. */
class LyricPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("nova_lyrics", Context.MODE_PRIVATE)
    private val _automatic = MutableStateFlow(preferences.getBoolean("automatic_current_track", true))
    val automatic = _automatic.asStateFlow()
    fun automatic(enabled: Boolean) {
        preferences.edit().putBoolean("automatic_current_track", enabled).apply()
        _automatic.value = enabled
    }
}
