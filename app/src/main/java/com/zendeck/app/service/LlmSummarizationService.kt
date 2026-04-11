package com.zendeck.app.service

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device LLM service using LiteRT-LM (Gemma 4 E2B .litertlm format).
 * Runs on CPU only — no GPU acceleration to ensure stability across all devices.
 *
 * Falls back to [SummaryEngine] (pure-Kotlin extractive summarizer) when no
 * model is installed, so the app remains functional without a model file.
 */
class LlmSummarizationService(private val context: Context) {

    data class ModelInfo(val name: String, val path: String)

    private var engine: Engine? = null
    private var isInitialized = false
    private var initAttempted = false
    private var preferredModelPath: String? = null
    private var loadedModelPath: String? = null

    // Persistent conversation for multi-turn chat
    private var chatConversation: Conversation? = null
    private var chatSystemPrompt = ""

    companion object {
        private const val TAG = "LlmSummarizationService"

        private val SEARCH_DIRS = listOf(
            "/storage/emulated/0/Download/gemma",
            "/sdcard/Download/gemma",
            "/sdcard/Download",
        )

        fun discoverModels(context: Context): List<ModelInfo> {
            val dirs = buildList {
                add(context.filesDir)
                context.getExternalFilesDir("models")?.let { add(it) }
                context.filesDir.resolve("models").let { if (it.exists()) add(it) }
                addAll(SEARCH_DIRS.map { File(it) })
            }
            return dirs.flatMap { dir ->
                dir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".litertlm") }
                    ?.map { ModelInfo(name = it.name, path = it.absolutePath) }
                    ?: emptyList()
            }.distinctBy { it.path }
        }

        fun hasModel(context: Context): Boolean = discoverModels(context).isNotEmpty()

        fun getActiveModelName(context: Context): String? =
            discoverModels(context).firstOrNull()?.name
    }

    fun setPreferredModelPath(path: String?) {
        if (path != preferredModelPath) {
            preferredModelPath = path
            resetEngine()
        }
    }

    fun resetChatSession(systemPrompt: String) {
        chatConversation?.close()
        chatConversation = null
        chatSystemPrompt = systemPrompt
    }

    private fun resetEngine() {
        chatConversation?.close()
        chatConversation = null
        engine?.close()
        engine = null
        isInitialized = false
        initAttempted = false
    }

    /** Tries to load the best available model on CPU. No-op if already loaded. */
    private fun initialize() {
        if (isInitialized || initAttempted) return
        initAttempted = true

        val candidates = buildList {
            preferredModelPath?.takeIf { File(it).exists() }?.let { add(it) }
            discoverModels(context).map { it.path }.forEach {
                if (it != preferredModelPath) add(it)
            }
        }

        if (candidates.isEmpty()) {
            Log.i(TAG, "No .litertlm model files found")
            return
        }

        for (path in candidates) {
            var eng: Engine? = null
            try {
                eng = Engine(EngineConfig(modelPath = path, backend = Backend.CPU()))
                eng.initialize()
                engine = eng
                loadedModelPath = path
                isInitialized = true
                Log.i(TAG, "Model loaded on CPU: ${File(path).name}")
                return
            } catch (e: Exception) {
                eng?.runCatching { close() }
                Log.w(TAG, "CPU load failed (${File(path).name}): ${e.message}")
            } catch (e: OutOfMemoryError) {
                eng?.runCatching { close() }
                Log.e(TAG, "OOM during CPU load: ${e.message}")
            }
        }
        Log.w(TAG, "No model could be loaded from ${candidates.size} candidates")
    }

    // ── Core generation ───────────────────────────────────────────────────────

    private suspend fun generateResponse(userContent: String, systemInstruction: String = ""): String {
        val eng = engine ?: return ""
        val config = if (systemInstruction.isNotBlank()) {
            ConversationConfig(systemInstruction = Contents.of(systemInstruction))
        } else {
            ConversationConfig()
        }
        val sb = StringBuilder()
        eng.createConversation(config).use { conv ->
            conv.sendMessageAsync(userContent).collect { token -> sb.append(token) }
        }
        return sb.toString().trim()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Summarizes [text] into 3-4 bullet points using the on-device model.
     * Falls back to [SummaryEngine] if no model is available or if inference fails.
     */
    suspend fun summarize(text: String, customPrompt: String = ""): String {
        if (text.isBlank()) return ""
        return withContext(Dispatchers.IO) {
            try {
                initialize()
                if (!isInitialized) return@withContext SummaryEngine.summarize(text)

                val prompt = if (customPrompt.isNotBlank()) {
                    "${customPrompt.trim()}\n\nArticle:\n${text.take(4000)}"
                } else {
                    "Summarize this article in 3-4 bullet points. Start each with '• '. " +
                    "Be concise and cover the key ideas.\n\nArticle:\n${text.take(4000)}\n\nSummary:"
                }

                val result = generateResponse(
                    userContent = prompt,
                    systemInstruction = "You are a concise reading assistant. Respond with bullet points only."
                )
                result.ifBlank { SummaryEngine.summarize(text) }
            } catch (_: OutOfMemoryError) {
                SummaryEngine.summarize(text)
            } catch (e: Exception) {
                Log.w(TAG, "summarize failed: ${e.message}")
                SummaryEngine.summarize(text)
            }
        }
    }

    /**
     * Generates 2-4 topic tags for a saved article.
     * Returns an empty list when no model is available (tags are optional).
     */
    suspend fun generateTags(title: String, text: String): List<String> {
        if (title.isBlank() && text.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                initialize()
                if (!isInitialized) return@withContext emptyList()

                val prompt = "Generate 2-4 topic tags.\n" +
                    "Rules: 1-3 words each, lowercase, use-hyphens-not-spaces, no # symbol.\n" +
                    "Output ONLY the tags, one per line, nothing else.\n\n" +
                    "Title: $title\nContent excerpt: ${text.take(600)}"

                val raw = generateResponse(
                    userContent = prompt,
                    systemInstruction = "You are a content tagger. Output tags only, one per line."
                )
                raw.lines()
                    .map { it.trim().lowercase().replace(Regex("[^a-z0-9\\-]"), "").take(30) }
                    .filter { it.isNotBlank() && it.length >= 2 }
                    .distinct()
                    .take(4)
            } catch (e: Exception) {
                Log.w(TAG, "generateTags failed: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Sends one turn in a persistent multi-turn chat session.
     * Call [resetChatSession] with a system prompt before the first turn or
     * whenever you want to reset the conversation context.
     */
    suspend fun chatTurn(userContent: String): String {
        if (userContent.isBlank()) return ""
        return withContext(Dispatchers.IO) {
            try {
                initialize()
                if (!isInitialized) return@withContext buildNotFoundMessage()

                val eng = engine ?: return@withContext buildNotFoundMessage()

                if (chatConversation == null) {
                    val config = ConversationConfig(
                        systemInstruction = Contents.of(chatSystemPrompt)
                    )
                    chatConversation = eng.createConversation(config)
                }

                val sb = StringBuilder()
                chatConversation!!.sendMessageAsync(userContent).collect { token -> sb.append(token) }
                sb.toString().trim().ifBlank { "I couldn't generate a response. Please try again." }
            } catch (_: OutOfMemoryError) {
                Log.e(TAG, "OOM during chat — resetting engine")
                resetEngine()
                "Out of memory — please close other apps and try again."
            } catch (e: Exception) {
                Log.w(TAG, "chatTurn failed: ${e.message}")
                "Something went wrong: ${e.message}"
            }
        }
    }

    fun isLlmLoaded(): Boolean = isInitialized && engine != null
    fun getLoadedModelName(): String? = loadedModelPath?.let { File(it).name }

    fun close() {
        chatConversation?.runCatching { close() }
        chatConversation = null
        engine?.runCatching { close() }
        engine = null
        isInitialized = false
        initAttempted = false
        preferredModelPath = null
        loadedModelPath = null
    }

    private fun buildNotFoundMessage(): String =
        "No AI model found.\n\nTo enable Chat AI:\n" +
        "1. Download gemma-4-E2B-it.litertlm from Hugging Face (google/gemma-4-on-device)\n" +
        "2. Go to Settings → Import model file\n" +
        "3. Select the downloaded .litertlm file"
}
