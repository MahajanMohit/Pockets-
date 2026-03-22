package com.zendeck.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendeck.app.ZenDeckApplication
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.domain.model.LinkItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = (application as ZenDeckApplication).dataStore
    private val repo = LinkRepository.getInstance(application)

    companion object {
        val KEY_TTL_HOURS = longPreferencesKey("ttl_hours")
        val TTL_OPTIONS = listOf(24L, 48L, 72L, 168L)
        private const val TAG = "SettingsViewModel"
    }

    val ttlHours: StateFlow<Long> = dataStore.data
        .map { prefs -> prefs[KEY_TTL_HOURS] ?: 72L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 72L)

    fun setTtlHours(hours: Long) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_TTL_HOURS] = hours }
    }

    /** Export all saved links as JSON to a URI chosen by the user (SAF). */
    fun exportBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val links = repo.getAllLinks()
            val json = Json { prettyPrint = true }.encodeToString<List<LinkItem>>(links)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
            Log.i(TAG, "Exported ${links.size} links to $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}")
        }
    }

    /** Import links from a JSON backup file chosen by the user (SAF). */
    fun importBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val json = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.use { it.readBytes().decodeToString() } ?: return@launch
            val links = Json.decodeFromString<List<LinkItem>>(json)
            repo.importLinks(links)
            Log.i(TAG, "Imported ${links.size} links from $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}")
        }
    }
}
