package com.novalisten.app.enrichment

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArtistPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("nova_artists", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(ArtistAutomation(preferences.getBoolean("biography", true), preferences.getBoolean("portrait", true)))
    val state = _state.asStateFlow()
    fun biography(enabled: Boolean) { preferences.edit().putBoolean("biography", enabled).apply(); _state.value = _state.value.copy(biography = enabled) }
    fun portrait(enabled: Boolean) { preferences.edit().putBoolean("portrait", enabled).apply(); _state.value = _state.value.copy(portrait = enabled) }
}
