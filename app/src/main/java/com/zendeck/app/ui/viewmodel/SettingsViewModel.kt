package com.zendeck.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendeck.app.ZenDeckApplication
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.server.LanServerService
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
        val KEY_TTL_HOURS  = longPreferencesKey("ttl_hours")
        val KEY_DARK_MODE  = booleanPreferencesKey("dark_mode")
        val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        val TTL_OPTIONS    = listOf(24L, 48L, 72L, 168L)
        private const val TAG = "SettingsViewModel"
    }

    // ── LAN Server ────────────────────────────────────────────────────────────

    val lanServerRunning: StateFlow<Boolean> = LanServerService.isRunning

    fun getLanIpAddress(): String? = LanServerService.getLanIpAddress()

    fun toggleLanServer(enable: Boolean) {
        val app = getApplication<Application>()
        val intent = Intent(app, LanServerService::class.java)
        if (enable) ContextCompat.startForegroundService(app, intent)
        else app.stopService(intent)
    }

    // ── Appearance ────────────────────────────────────────────────────────────

    val ttlHours: StateFlow<Long> = dataStore.data
        .map { prefs -> prefs[KEY_TTL_HOURS] ?: 72L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 72L)

    val darkMode: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_DARK_MODE] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setTtlHours(hours: Long) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_TTL_HOURS] = hours }
    }

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_DARK_MODE] = enabled }
    }

    /** Font scale multiplier. 0.85 = Small, 1.0 = Normal, 1.15 = Large, 1.3 = XL. */
    val fontScale: StateFlow<Float> = dataStore.data
        .map { prefs -> prefs[KEY_FONT_SCALE] ?: 1.0f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    fun setFontScale(scale: Float) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_FONT_SCALE] = scale }
    }

    // ── Backup & Restore ──────────────────────────────────────────────────────

    fun exportBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val links = repo.getAllLinks()
            val json = Json { prettyPrint = true }.encodeToString<List<LinkItem>>(links)
            getApplication<Application>().contentResolver.openOutputStream(uri)
                ?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            Log.i(TAG, "Exported ${links.size} links to $uri")
        } catch (e: Exception) { Log.e(TAG, "Export failed: ${e.message}") }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val json = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.use { it.readBytes().decodeToString() } ?: return@launch
            val links = Json.decodeFromString<List<LinkItem>>(json)
            repo.importLinks(links)
            Log.i(TAG, "Imported ${links.size} links from $uri")
        } catch (e: Exception) { Log.e(TAG, "Import failed: ${e.message}") }
    }
}
