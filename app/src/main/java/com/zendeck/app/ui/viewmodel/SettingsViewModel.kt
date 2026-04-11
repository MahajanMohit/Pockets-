package com.zendeck.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendeck.app.ZenDeckApplication
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.server.LanServerService
import com.zendeck.app.service.LlmSummarizationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = (application as ZenDeckApplication).dataStore
    private val repo = LinkRepository.getInstance(application)

    companion object {
        val KEY_TTL_HOURS          = longPreferencesKey("ttl_hours")
        val KEY_DARK_MODE          = booleanPreferencesKey("dark_mode")
        val KEY_CUSTOM_PROMPT      = stringPreferencesKey("custom_summary_prompt")
        val KEY_FONT_SCALE         = floatPreferencesKey("font_scale")
        val KEY_SELECTED_MODEL_PATH = stringPreferencesKey("selected_model_path")
        val TTL_OPTIONS            = listOf(24L, 48L, 72L, 168L)
        private const val TAG      = "SettingsViewModel"
    }

    // ── AI Model ──────────────────────────────────────────────────────────────

    sealed class ModelImportStatus {
        object Idle : ModelImportStatus()
        data class Copying(val fileName: String) : ModelImportStatus()
        data class Done(val fileName: String) : ModelImportStatus()
        data class Failed(val error: String) : ModelImportStatus()
    }

    private val _activeModelName = MutableStateFlow<String?>(
        LlmSummarizationService.getActiveModelName(application)
    )
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    private val _modelImportStatus = MutableStateFlow<ModelImportStatus>(ModelImportStatus.Idle)
    val modelImportStatus: StateFlow<ModelImportStatus> = _modelImportStatus.asStateFlow()

    fun refreshModelState() {
        _activeModelName.value = LlmSummarizationService.getActiveModelName(getApplication())
    }

    fun importModelFile(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val fileName = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        } ?: "gemma-4-E2B-it.litertlm"

        _modelImportStatus.value = ModelImportStatus.Copying(fileName)
        try {
            val modelsDir = app.getExternalFilesDir("models")
                ?: app.filesDir.resolve("models").also { it.mkdirs() }
            modelsDir.mkdirs()
            val dest = File(modelsDir, fileName)
            app.contentResolver.openInputStream(uri)?.use { i ->
                dest.outputStream().use { o -> i.copyTo(o) }
            }
            Log.i(TAG, "Model imported → ${dest.absolutePath}")
            dataStore.edit { prefs ->
                prefs[KEY_SELECTED_MODEL_PATH] = dest.absolutePath
            }
            _modelImportStatus.value = ModelImportStatus.Done(fileName)
            refreshModelState()
        } catch (e: Exception) {
            Log.e(TAG, "Model import failed: ${e.message}")
            _modelImportStatus.value = ModelImportStatus.Failed(e.message ?: "Unknown error")
        }
    }

    fun clearModelImportStatus() { _modelImportStatus.value = ModelImportStatus.Idle }

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

    // ── Summary prompt ────────────────────────────────────────────────────────

    /** Optional custom prompt hint for the summariser (not used by SummaryEngine, reserved for future). */
    val customSummaryPrompt: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[KEY_CUSTOM_PROMPT] ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setCustomSummaryPrompt(prompt: String) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_CUSTOM_PROMPT] = prompt }
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
