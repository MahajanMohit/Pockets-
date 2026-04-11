package com.zendeck.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val imageUri: Uri? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LinkRepository.getInstance(application)
    private val dataStore = (application as ZenDeckApplication).dataStore
    private val llmService = LlmSummarizationService(application)
    private val memoryFile: File = application.filesDir.resolve("user_memory.txt")

    // ── State ─────────────────────────────────────────────────────────────────

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelAvailable = MutableStateFlow(false)
    val modelAvailable: StateFlow<Boolean> = _modelAvailable.asStateFlow()

    private val _activeModelName = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    private val _pendingImageUri = MutableStateFlow<Uri?>(null)
    val pendingImageUri: StateFlow<Uri?> = _pendingImageUri.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            prefs[KEY_SELECTED_MODEL_PATH]?.let { llmService.setPreferredModelPath(it) }
            refreshModelState()
            rebuildChatSession()
        }
    }

    companion object {
        val KEY_SELECTED_MODEL_PATH = stringPreferencesKey("selected_model_path")
        private const val TAG = "ChatViewModel"
    }

    // ── Session management ────────────────────────────────────────────────────

    private fun rebuildChatSession() {
        val memory = try {
            if (memoryFile.exists()) memoryFile.readText().trim() else ""
        } catch (_: Exception) { "" }

        val systemPrompt = buildString {
            appendLine("You are a helpful personal reading assistant. Be direct, concise, and conversational.")
            if (memory.isNotBlank()) {
                appendLine()
                appendLine("About the user:")
                appendLine(memory)
            }
        }
        llmService.resetChatSession(systemPrompt)
        if (_messages.value.isEmpty()) {
            _messages.value = listOf(ChatMessage(text = buildGreeting(memory), isUser = false))
        }
    }

    private fun buildGreeting(memory: String): String {
        val nameRegex = listOf(
            Regex("""(?i)my name is ([A-Z][a-z]+)"""),
            Regex("""(?i)I(?:'m| am) ([A-Z][a-z]+)"""),
            Regex("""(?i)call me ([A-Z][a-z]+)""")
        )
        val name = nameRegex.firstNotNullOfOrNull { it.find(memory)?.groupValues?.get(1) }
        return if (name != null) {
            "Hey $name! Ask me anything, or ask about your saved articles — I have access to them."
        } else {
            "Hey! Ask me anything. I can also answer questions about your saved articles."
        }
    }

    fun refreshModelState() {
        val name = LlmSummarizationService.getActiveModelName(getApplication())
        _activeModelName.value = name
        _modelAvailable.value = name != null
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    fun setPendingImage(uri: Uri?) {
        if (uri != null) {
            try {
                getApplication<Application>().contentResolver
                    .takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
        }
        _pendingImageUri.value = uri
    }

    fun sendMessage(text: String, imageUri: Uri? = null) {
        val hasText = text.isNotBlank()
        val hasImage = imageUri != null
        if (!hasText && !hasImage) return

        val userMsg = ChatMessage(text = text.trim(), isUser = true, imageUri = imageUri)
        _messages.value = _messages.value + userMsg
        _pendingImageUri.value = null
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Build RAG context: search saved articles for relevant content
                val ragContext = if (hasText) buildRagContext(text) else ""

                // Compose what we send to the LLM
                val llmPrompt = buildString {
                    if (ragContext.isNotBlank()) {
                        appendLine("[Relevant articles from your reading list:]")
                        appendLine(ragContext)
                    }
                    if (hasImage) {
                        appendLine("[User shared an image]")
                    }
                    if (hasText) {
                        append(text.trim())
                    } else if (hasImage) {
                        append("What do you see in this image?")
                    }
                }

                val reply = llmService.chatTurn(llmPrompt)
                _messages.value = _messages.value + ChatMessage(text = reply, isUser = false)
            } catch (e: Exception) {
                Log.w(TAG, "sendMessage failed: ${e.message}")
                _messages.value = _messages.value +
                    ChatMessage(text = "Error: ${e.message}", isUser = false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun buildRagContext(query: String): String {
        return try {
            val queryWords = query.lowercase()
                .split(Regex("\\s+"))
                .filter { it.length > 3 }
                .toSet()
            if (queryWords.isEmpty()) return ""

            val allLinks = repo.getAllLinks()
            val relevant = allLinks
                .mapNotNull { link ->
                    val searchText = buildString {
                        append(link.title.lowercase())
                        append(" ")
                        append(link.summary.lowercase())
                        append(" ")
                        append(link.tags.joinToString(" ").lowercase())
                    }
                    val score = queryWords.count { searchText.contains(it) }
                    if (score > 0) link to score else null
                }
                .sortedByDescending { it.second }
                .take(3)

            if (relevant.isEmpty()) return ""

            buildString {
                relevant.forEach { (link, _) ->
                    appendLine("• \"${link.title}\" (${link.domain})")
                    if (link.summary.isNotBlank()) {
                        appendLine("  ${link.summary.take(300)}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "buildRagContext failed: ${e.message}")
            ""
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        rebuildChatSession()
    }

    // ── Model import ──────────────────────────────────────────────────────────

    sealed class ImportStatus {
        object Idle : ImportStatus()
        data class Copying(val fileName: String) : ImportStatus()
        data class Done(val fileName: String) : ImportStatus()
        data class Failed(val error: String) : ImportStatus()
    }

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    fun importModelFile(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val fileName = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        } ?: "gemma-4-E2B-it.litertlm"

        _importStatus.value = ImportStatus.Copying(fileName)
        try {
            val modelsDir = app.getExternalFilesDir("models")
                ?: app.filesDir.resolve("models").also { it.mkdirs() }
            modelsDir.mkdirs()
            val dest = File(modelsDir, fileName)
            app.contentResolver.openInputStream(uri)?.use { i ->
                dest.outputStream().use { o -> i.copyTo(o) }
            }
            Log.i(TAG, "Model imported → ${dest.absolutePath}")
            _importStatus.value = ImportStatus.Done(fileName)
            llmService.setPreferredModelPath(dest.absolutePath)
            // Persist preferred model path
            dataStore.edit { prefs ->
                prefs[KEY_SELECTED_MODEL_PATH] = dest.absolutePath
            }
            refreshModelState()
        } catch (e: Exception) {
            Log.e(TAG, "Model import failed: ${e.message}")
            _importStatus.value = ImportStatus.Failed(e.message ?: "Unknown error")
        }
    }

    fun clearImportStatus() { _importStatus.value = ImportStatus.Idle }

    override fun onCleared() {
        super.onCleared()
        llmService.close()
    }
}
