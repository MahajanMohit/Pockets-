package com.zendeck.app.ui.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendeck.app.ZenDeckApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = (application as ZenDeckApplication).dataStore

    companion object {
        val KEY_TTL_HOURS = longPreferencesKey("ttl_hours")
        val KEY_SHOW_SUMMARY = booleanPreferencesKey("show_summary")
        val TTL_OPTIONS = listOf(24L, 48L, 72L, 168L)
    }

    val ttlHours: StateFlow<Long> = dataStore.data
        .map { prefs -> prefs[KEY_TTL_HOURS] ?: 72L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 72L)

    val showSummary: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_SHOW_SUMMARY] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setTtlHours(hours: Long) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_TTL_HOURS] = hours }
    }

    fun setShowSummary(show: Boolean) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_SHOW_SUMMARY] = show }
    }
}
