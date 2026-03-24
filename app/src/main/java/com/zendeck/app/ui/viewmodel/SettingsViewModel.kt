package com.zendeck.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendeck.app.ZenDeckApplication
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.domain.model.LinkItem
import com.zendeck.app.server.LanServerService
import com.zendeck.app.server.ZenDeckNanoServer
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
import java.net.HttpURLConnection
import java.net.URL

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = (application as ZenDeckApplication).dataStore
    private val repo = LinkRepository.getInstance(application)

    companion object {
        val KEY_TTL_HOURS     = longPreferencesKey("ttl_hours")
        val KEY_DARK_MODE     = booleanPreferencesKey("dark_mode")
        val KEY_AI_SUMMARIES  = booleanPreferencesKey("ai_summaries_enabled")
        val KEY_CUSTOM_PROMPT = stringPreferencesKey("custom_summary_prompt")
        val KEY_SYNC_PEER_IP  = stringPreferencesKey("sync_peer_ip")
        val TTL_OPTIONS       = listOf(24L, 48L, 72L, 168L)
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

    // ── AI Settings ───────────────────────────────────────────────────────────

    /** Whether to run AI summarisation when links are saved (default on). */
    val aiSummariesEnabled: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_AI_SUMMARIES] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** User-provided summarisation prompt; empty = use the built-in prompt. */
    val customSummaryPrompt: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[KEY_CUSTOM_PROMPT] ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setAiSummariesEnabled(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_AI_SUMMARIES] = enabled }
    }

    fun setCustomSummaryPrompt(prompt: String) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_CUSTOM_PROMPT] = prompt }
    }

    private val _activeModelName = MutableStateFlow(LlmSummarizationService.getActiveModelName(getApplication()))
    /** Reactive filename of the highest-priority model on disk; updates after successful import. */
    val activeModelNameState: StateFlow<String?> = _activeModelName.asStateFlow()

    // Keep the simple accessor for callers that just need a one-shot read
    fun getActiveModelName(): String? = _activeModelName.value

    /** Creates /storage/emulated/0/Download/gemma/ for new users to drop model files into. */
    fun createModelFolder() = viewModelScope.launch(Dispatchers.IO) {
        try { File("/storage/emulated/0/Download/gemma").mkdirs() }
        catch (e: Exception) { Log.w(TAG, "Could not create model folder: ${e.message}") }
    }

    // ── Model import via SAF ──────────────────────────────────────────────────

    sealed class ImportStatus {
        object Idle                              : ImportStatus()
        data class Copying(val fileName: String) : ImportStatus()
        data class Done(val fileName: String)    : ImportStatus()
        data class Failed(val error: String)     : ImportStatus()
    }

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    /**
     * Copies a SAF-picked .bin file to the app's private external models dir.
     * That path is always accessible without MANAGE_EXTERNAL_STORAGE, so the
     * model loads on all Android versions after the one-time copy.
     */
    fun importModelFile(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val fileName = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        } ?: "gemma_model.bin"

        _importStatus.value = ImportStatus.Copying(fileName)
        try {
            val modelsDir = app.getExternalFilesDir("models") ?: app.filesDir.resolve("models")
            modelsDir.mkdirs()
            val dest = File(modelsDir, fileName)
            app.contentResolver.openInputStream(uri)?.use { i ->
                dest.outputStream().use { o -> i.copyTo(o) }
            }
            Log.i(TAG, "Model imported → ${dest.absolutePath}")
            _importStatus.value = ImportStatus.Done(fileName)
            _activeModelName.value = LlmSummarizationService.getActiveModelName(getApplication())
        } catch (e: Exception) {
            Log.e(TAG, "Model import failed: ${e.message}")
            _importStatus.value = ImportStatus.Failed(e.message ?: "Unknown error")
        }
    }

    fun clearImportStatus() { _importStatus.value = ImportStatus.Idle }

    // ── LAN Sync ──────────────────────────────────────────────────────────────

    /** IP address (no port) of the peer device to sync with, e.g. "192.168.1.42". */
    val syncPeerIp: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[KEY_SYNC_PEER_IP] ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setSyncPeerIp(ip: String) = viewModelScope.launch {
        dataStore.edit { prefs -> prefs[KEY_SYNC_PEER_IP] = ip.trim() }
    }

    sealed class SyncStatus {
        object Idle                              : SyncStatus()
        object Syncing                           : SyncStatus()
        data class Done(val count: Int)          : SyncStatus()
        data class Failed(val error: String)     : SyncStatus()
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    /** Pulls all links from the peer and merges them into local DB. */
    fun syncFromPeer() = viewModelScope.launch(Dispatchers.IO) {
        val ip = syncPeerIp.value.trim()
        if (ip.isBlank()) {
            _syncStatus.value = SyncStatus.Failed("No peer IP set")
            return@launch
        }
        _syncStatus.value = SyncStatus.Syncing
        try {
            val url = URL("http://$ip:${ZenDeckNanoServer.PORT}/api/links/all")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            val body = conn.inputStream.use { it.readBytes().decodeToString() }
            conn.disconnect()
            val links = Json.decodeFromString<List<LinkItem>>(body)
            repo.mergeLinksFromPeer(links)
            Log.i(TAG, "Sync pull: merged ${links.size} links from $ip")
            _syncStatus.value = SyncStatus.Done(links.size)
        } catch (e: Exception) {
            Log.e(TAG, "Sync pull failed: ${e.message}")
            _syncStatus.value = SyncStatus.Failed(e.message ?: "Connection failed")
        }
    }

    /** Pushes a single link to the peer (called after saving a new link). */
    fun pushLinkToPeer(link: LinkItem, peerIp: String) = viewModelScope.launch(Dispatchers.IO) {
        if (peerIp.isBlank()) return@launch
        try {
            val url = URL("http://$peerIp:${ZenDeckNanoServer.PORT}/api/sync/push")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            val body = Json.encodeToString<List<LinkItem>>(listOf(link))
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            Log.i(TAG, "Pushed link to peer $peerIp → HTTP $code")
        } catch (e: Exception) {
            Log.w(TAG, "Push to peer $peerIp failed: ${e.message}")
        }
    }

    fun clearSyncStatus() { _syncStatus.value = SyncStatus.Idle }

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
