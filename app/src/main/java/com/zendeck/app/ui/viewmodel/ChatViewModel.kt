package com.zendeck.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendeck.app.ZenDeckApplication
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.service.LlmSummarizationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LinkRepository.getInstance(application)
    private val dataStore = (application as ZenDeckApplication).dataStore
    private var llmService = LlmSummarizationService(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelAvailable = MutableStateFlow(false)
    val modelAvailable: StateFlow<Boolean> = _modelAvailable.asStateFlow()

    private val _activeModelName = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    // ── Model discovery & selection ───────────────────────────────────────────

    private val _discoveredModels = MutableStateFlow<List<LlmSummarizationService.ModelInfo>>(emptyList())
    val discoveredModels: StateFlow<List<LlmSummarizationService.ModelInfo>> = _discoveredModels.asStateFlow()

    private val _selectedModel = MutableStateFlow<LlmSummarizationService.ModelInfo?>(null)
    val selectedModel: StateFlow<LlmSummarizationService.ModelInfo?> = _selectedModel.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    // ── Import status ─────────────────────────────────────────────────────────

    sealed class ImportStatus {
        object Idle                              : ImportStatus()
        data class Copying(val fileName: String) : ImportStatus()
        data class Done(val fileName: String)    : ImportStatus()
        data class Failed(val error: String)     : ImportStatus()
    }

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    // ── Article context ───────────────────────────────────────────────────────

    private val _articleContext = MutableStateFlow("")
    val articleContext: StateFlow<String> = _articleContext.asStateFlow()

    init {
        viewModelScope.launch {
            // Restore persisted model selection, then refresh
            val savedPath = dataStore.data.first()[SettingsViewModel.KEY_SELECTED_MODEL_PATH]
            if (savedPath != null) llmService.setPreferredModelPath(savedPath)
            refreshModels()
            loadArticleContext()
        }
    }

    fun refreshModels() {
        val models = LlmSummarizationService.discoverModels(getApplication())
        _discoveredModels.value = models

        // Keep current selection if still valid, otherwise pick first
        val currentPath = _selectedModel.value?.path
        val stillValid = models.any { it.path == currentPath }
        if (!stillValid) {
            val first = models.firstOrNull()
            _selectedModel.value = first
            first?.let { llmService.setPreferredModelPath(it.path) }
        }
        refreshModelState()
    }

    fun selectModel(model: LlmSummarizationService.ModelInfo) {
        if (_selectedModel.value?.path == model.path) return
        _selectedModel.value = model
        llmService.setPreferredModelPath(model.path)
        refreshModelState()
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[SettingsViewModel.KEY_SELECTED_MODEL_PATH] = model.path }
        }
    }

    /** Re-reads model presence from disk — updates automatically after import. */
    fun refreshModelState() {
        val name = LlmSummarizationService.getActiveModelName(getApplication())
        _activeModelName.value = _selectedModel.value?.name ?: name
        _modelAvailable.value = _selectedModel.value != null || name != null
    }

    fun importModel(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
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
            Log.i(TAG, "Model imported via Chat → ${dest.absolutePath}")
            _importStatus.value = ImportStatus.Done(fileName)
            dataStore.edit { prefs -> prefs[SettingsViewModel.KEY_SELECTED_MODEL_PATH] = dest.absolutePath }
            refreshModels()
        } catch (e: Exception) {
            Log.e(TAG, "Model import failed: ${e.message}")
            _importStatus.value = ImportStatus.Failed(e.message ?: "Unknown error")
        }
    }

    fun clearImportStatus() { _importStatus.value = ImportStatus.Idle }

    fun loadArticleContext() = viewModelScope.launch {
        try {
            val links = repo.getInboxLinksSnapshot().take(15)
            _articleContext.value = if (links.isEmpty()) "" else
                links.joinToString("\n") { link ->
                    "• ${link.title} (${link.domain})" +
                    when {
                        link.summary.isNotBlank() -> " — ${link.summary.take(80)}"
                        link.description.isNotBlank() -> " — ${link.description.take(80)}"
                        else -> ""
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "loadArticleContext failed: ${e.message}")
        }
    }

    fun setSelectedImage(uri: Uri?) { _selectedImageUri.value = uri }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isLoading.value) return

        val imageUri = _selectedImageUri.value
        _selectedImageUri.value = null  // clear attachment after send

        val displayText = if (imageUri != null)
            "[Image: ${imageUri.lastPathSegment}]\n$trimmed"
        else trimmed

        _messages.value = _messages.value + ChatMessage(displayText, isUser = true)
        _isLoading.value = true

        viewModelScope.launch {
            val reply = llmService.chat(
                message = trimmed,
                articleContext = _articleContext.value,
                imageUri = imageUri
            )
            _messages.value = _messages.value + ChatMessage(reply, isUser = false)
            _isLoading.value = false
            refreshModelState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmService.close()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
