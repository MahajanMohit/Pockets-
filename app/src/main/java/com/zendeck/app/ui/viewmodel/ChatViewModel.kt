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
import com.zendeck.app.service.ImageAnalysisService
import com.zendeck.app.service.LlmSummarizationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LinkRepository.getInstance(application)
    private val dataStore = (application as ZenDeckApplication).dataStore
    private var llmService = LlmSummarizationService(application)

    private val memoryFile: File = application.filesDir.resolve("user_memory.txt")

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelAvailable = MutableStateFlow(false)
    val modelAvailable: StateFlow<Boolean> = _modelAvailable.asStateFlow()

    private val _activeModelName = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    sealed class ImportStatus {
        object Idle                              : ImportStatus()
        data class Copying(val fileName: String) : ImportStatus()
        data class Done(val fileName: String)    : ImportStatus()
        data class Failed(val error: String)     : ImportStatus()
    }
    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    private val _articleContext = MutableStateFlow("")
    val articleContext: StateFlow<String> = _articleContext.asStateFlow()

    private val _userMemory = MutableStateFlow("")
    val userMemory: StateFlow<String> = _userMemory.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val savedPath = prefs[SettingsViewModel.KEY_SELECTED_MODEL_PATH]
            if (savedPath != null) llmService.setPreferredModelPath(savedPath)
            refreshModelState()
            loadArticleContext()
            loadMemory()
        }
    }

    // ── Memory ────────────────────────────────────────────────────────────────

    private fun loadMemory() {
        try {
            _userMemory.value = if (memoryFile.exists()) memoryFile.readText().trim() else ""
        } catch (_: Exception) {}
        rebuildChatSession()
    }

    fun updateMemory(content: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            memoryFile.writeText(content)
            _userMemory.value = content
            rebuildChatSession()
        } catch (_: Exception) {}
    }

    private fun rebuildChatSession() {
        val systemPrompt = buildSystemPrompt(_articleContext.value, _userMemory.value)
        llmService.resetChatSession(systemPrompt)
        // Reset to greeting whenever session rebuilds
        _messages.value = listOf(buildGreeting(_userMemory.value))
    }

    private fun buildSystemPrompt(articleContext: String, memory: String): String = buildString {
        append("You are a personal AI assistant. Be helpful, concise, and direct.\n")
        if (memory.isNotBlank()) {
            append("\nUser profile (remember this across the conversation):\n$memory\n")
        }
        if (articleContext.isNotBlank()) {
            append("\nUser's current saved reading list (refer to these when relevant):\n$articleContext\n")
        }
    }

    private fun buildGreeting(memory: String): ChatMessage {
        val name = extractName(memory)
        val greeting = if (name != null) {
            "Hey $name! I'm your personal assistant. I have access to your reading list and I remember what you've told me about yourself. What's on your mind?"
        } else {
            "Hey! I'm your personal assistant. I have access to your saved reading list and I remember our conversations. How can I help you today?"
        }
        return ChatMessage(greeting, isUser = false)
    }

    private fun extractName(memory: String): String? {
        val patterns = listOf(
            Regex("""(?i)my name is ([A-Z][a-z]+)"""),
            Regex("""(?i)I(?:'m| am) ([A-Z][a-z]+)"""),
            Regex("""(?i)call me ([A-Z][a-z]+)""")
        )
        for (p in patterns) {
            val match = p.find(memory) ?: continue
            val name = match.groupValues[1]
            if (name.length in 2..20) return name
        }
        return null
    }

    // ── Model management ──────────────────────────────────────────────────────

    fun refreshModels() = refreshModelState()

    fun refreshModelState() {
        val name = LlmSummarizationService.getActiveModelName(getApplication())
        _activeModelName.value = name
        _modelAvailable.value = name != null
    }

    fun importModel(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val fileName = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        } ?: "gemma-4-E2B-it.litertlm"

        _importStatus.value = ImportStatus.Copying(fileName)
        try {
            val modelsDir = app.getExternalFilesDir("models") ?: app.filesDir.resolve("models")
            modelsDir.mkdirs()
            val dest = File(modelsDir, fileName)
            app.contentResolver.openInputStream(uri)?.use { i ->
                dest.outputStream().use { o -> i.copyTo(o) }
            }
            _importStatus.value = ImportStatus.Done(fileName)
            dataStore.edit { prefs -> prefs[SettingsViewModel.KEY_SELECTED_MODEL_PATH] = dest.absolutePath }
            llmService.setPreferredModelPath(dest.absolutePath)
            refreshModelState()
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
            rebuildChatSession()
        } catch (e: Exception) {
            Log.w(TAG, "loadArticleContext failed: ${e.message}")
        }
    }

    fun setSelectedImage(uri: Uri?) {
        if (uri != null) {
            try {
                getApplication<Application>().contentResolver
                    .takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
        }
        _selectedImageUri.value = uri
    }

    fun clearMessages() {
        _messages.value = emptyList()
        rebuildChatSession()
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isLoading.value) return

        val imageUri = _selectedImageUri.value
        _selectedImageUri.value = null

        val displayText = if (imageUri != null) "📷 $trimmed" else trimmed
        _messages.value = _messages.value + ChatMessage(displayText, isUser = true)
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            var imageOcrText = ""
            if (imageUri != null) {
                try {
                    val tempFile = ImageAnalysisService.copyAndCompress(getApplication(), imageUri)
                    if (tempFile != null) {
                        imageOcrText = ImageAnalysisService.extractText(tempFile.absolutePath)
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Image OCR failed: ${e.message}")
                }
            }

            val fullContent = if (imageOcrText.isNotBlank()) {
                "Image content (via OCR):\n$imageOcrText\n\nUser: $trimmed"
            } else trimmed

            val reply = llmService.chatTurn(fullContent)

            withContext(Dispatchers.Main) {
                _messages.value = _messages.value + ChatMessage(reply, isUser = false)
                _isLoading.value = false
            }
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
