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
 * On-device LLM service backed exclusively by LiteRT-LM (Gemma 4 2B .litertlm format).
 *
 * GPU is tried automatically on every init; if the runtime rejects the GPU backend at any
 * stage the engine is closed and the model is reloaded on CPU.  No user toggle is needed.
 */
class LlmSummarizationService(private val context: Context) {

    data class ModelInfo(
        val name: String,
        val path: String
    )

    private var engine: Engine? = null
    private var isInitialized = false
    private var isGpuActive = false
    private var initAttempted = false
    private var initFailReason = ""
    private var lastCandidatePaths: List<String> = emptyList()
    private var lastLoadErrors: List<String> = emptyList()
    private var preferredModelPath: String? = null
    private var loadedModelPath: String? = null

    // ── Multi-turn chat session ───────────────────────────────────────────────
    private var chatConversation: Conversation? = null
    private var chatSystemPrompt = ""

    companion object {
        private const val TAG = "LlmSummarizationService"
        private const val MAX_TOKENS = 1024

        private val SEARCH_DIRS = listOf(
            "/storage/emulated/0/Download/gemma",
            "/sdcard/Download/gemma",
            "/sdcard/Download",
        )

        fun discoverModels(context: Context): List<ModelInfo> {
            val dirs = buildList {
                add(context.filesDir)
                context.getExternalFilesDir("models")?.let { add(it) }
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

    /** Returns "GPU" or "CPU" — only meaningful after the first inference call. */
    fun getActiveBackend(): String = if (isGpuActive) "GPU" else "CPU"

    private fun resetEngine() {
        engine?.close()
        engine = null
        isInitialized = false
        isGpuActive = false
        initAttempted = false
        lastCandidatePaths = emptyList()
        resetChatSession("")
    }

    fun resetChatSession(systemPrompt: String) {
        chatConversation?.close()
        chatConversation = null
        chatSystemPrompt = systemPrompt
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private fun initialize() {
        if (isInitialized) return
        val candidates = findModelPaths()
        if (initAttempted && candidates == lastCandidatePaths) return
        initAttempted = true
        lastCandidatePaths = candidates
        initFailReason = ""

        if (candidates.isEmpty()) {
            initFailReason = "no_file"
            return
        }

        val errors = mutableListOf<String>()
        for (path in candidates) {
            // ── Try GPU first ─────────────────────────────────────────────────
            val gpuLoaded = tryLoadWithBackend(path, gpu = true)
            if (gpuLoaded) {
                isGpuActive = true
                isInitialized = true
                loadedModelPath = path
                Log.i(TAG, "Model loaded on GPU: $path")
                return
            }
            // ── Fall back to CPU ──────────────────────────────────────────────
            val cpuLoaded = tryLoadWithBackend(path, gpu = false)
            if (cpuLoaded) {
                isGpuActive = false
                isInitialized = true
                loadedModelPath = path
                Log.i(TAG, "Model loaded on CPU: $path")
                return
            }
            errors += "${File(path).name}: failed on both GPU and CPU"
        }

        initFailReason = "load_failed"
        lastLoadErrors = errors
        Log.w(TAG, "All model candidates failed: $errors")
    }

    /**
     * Attempts to create and initialise the Engine with the given backend.
     * Returns true on success, false on any error (engine is closed on failure).
     */
    private fun tryLoadWithBackend(path: String, gpu: Boolean): Boolean {
        var eng: Engine? = null
        return try {
            val backend = if (gpu) Backend.GPU() else Backend.CPU()
            eng = Engine(EngineConfig(modelPath = path, backend = backend))
            eng.initialize()
            engine = eng
            true
        } catch (e: Exception) {
            eng?.close()
            if (engine === eng) engine = null
            Log.w(TAG, "${if (gpu) "GPU" else "CPU"} load failed ($path): ${e.message}")
            false
        } catch (e: OutOfMemoryError) {
            eng?.close()
            if (engine === eng) engine = null
            Log.e(TAG, "OOM during ${if (gpu) "GPU" else "CPU"} load: ${e.message}")
            false
        }
    }

    private fun findModelPaths(): List<String> {
        val preferred = preferredModelPath?.let { p ->
            if (File(p).exists()) listOf(p) else emptyList()
        } ?: emptyList()
        val discovered = discoverModels(context)
            .map { it.path }
            .filter { it !in preferred }
        return preferred + discovered
    }

    // ── Core generation ───────────────────────────────────────────────────────

    private suspend fun generateResponse(userContent: String, systemInstruction: String = ""): String {
        val eng = engine ?: return ""
        val config = if (systemInstruction.isNotBlank()) {
            ConversationConfig(systemInstruction = Contents.of(systemInstruction))
        } else ConversationConfig()

        return eng.createConversation(config).use { conv ->
            val sb = StringBuilder()
            conv.sendMessageAsync(userContent).collect { sb.append(it) }
            sb.toString().trim()
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun summarize(text: String, customPrompt: String = ""): String {
        if (text.isBlank()) return ""
        return withContext(Dispatchers.IO) {
            try {
                initialize()
                if (!isInitialized) return@withContext fallbackSummarize(text)

                val userMsg = if (customPrompt.isNotBlank()) {
                    "${customPrompt.trim()}\n\nArticle:\n${text.take(3000)}"
                } else {
                    "Summarise the following article in 5 to 6 sentences as a single coherent " +
                    "paragraph. Use precise vocabulary and complete sentences. Cover the main " +
                    "point, key supporting details, and any important conclusion. Do not use " +
                    "bullet points or headers.\n\nArticle:\n${text.take(3000)}"
                }
                val result = generateResponse(userMsg, "You are a reading assistant. Be accurate and concise.")
                if (result.isBlank()) fallbackSummarize(text) else result
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM during summarize")
                fallbackSummarize(text)
            } catch (e: Exception) {
                Log.w(TAG, "Summarize failed: ${e.message}")
                fallbackSummarize(text)
            }
        }
    }

    suspend fun generateTags(title: String, text: String): List<String> {
        if (title.isBlank() && text.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                initialize()
                if (!isInitialized) return@withContext emptyList()

                val userMsg = "Generate 2 to 4 short tags for this content.\n" +
                    "Rules: each tag is 1–3 words, lowercase, no spaces (use hyphens), no hashtags.\n" +
                    "Output ONLY the tags, one per line, nothing else.\n\n" +
                    "Title: $title\nContent: ${text.take(600)}"
                val raw = generateResponse(userMsg, "You are a content tagger. Output only tags.")
                raw.lines()
                    .map { it.trim().lowercase().replace(Regex("[^a-z0-9\\-]"), "").take(30) }
                    .filter { it.isNotBlank() && it.length >= 2 }
                    .take(4)
            } catch (e: Exception) {
                Log.w(TAG, "generateTags failed: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Sends one turn in a persistent multi-turn chat session.
     * Call [resetChatSession] with a system prompt before the first turn.
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
                chatConversation!!.sendMessageAsync(userContent).collect { sb.append(it) }
                sb.toString().trim()
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM during chat")
                resetEngine()
                "Out of memory — the model was unloaded. Please try again."
            } catch (e: Exception) {
                Log.w(TAG, "Chat inference failed: ${e.message}")
                "Something went wrong: ${e.message}"
            }
        }
    }

    // ── Status accessors ──────────────────────────────────────────────────────

    fun isLlmLoaded(): Boolean = isInitialized && engine != null
    fun getLoadedModelName(): String? = loadedModelPath?.let { File(it).name }

    fun close() {
        chatConversation?.close()
        chatConversation = null
        engine?.close()
        engine = null
        isInitialized = false
        isGpuActive = false
        initAttempted = false
        initFailReason = ""
        lastCandidatePaths = emptyList()
        preferredModelPath = null
        loadedModelPath = null
    }

    // ── Fallback (no model available) ─────────────────────────────────────────

    private fun buildNotFoundMessage(): String {
        return if (initFailReason == "load_failed") buildString {
            append("Model file found but failed to load.\n\nErrors:\n")
            lastLoadErrors.forEach { append("• $it\n") }
            append("\nCommon causes:\n")
            append("• Not enough RAM — close other apps and try again\n")
            append("• Model file corrupted — re-download from Hugging Face\n")
            append("• Storage permission denied in Android Settings → Apps → Permissions")
        } else {
            "No model found. Download gemma-4-E2B-it.litertlm from Hugging Face " +
            "and place it in /storage/emulated/0/Download/gemma/\n\n" +
            "Or use Settings → AI Model → Import to pick the file directly."
        }
    }

    private fun fallbackSummarize(text: String): String {
        val boilerplate = listOf(
            "subscribe", "newsletter", "sign up", "sign in", "log in",
            "privacy policy", "terms of service", "cookie", "advertisement",
            "sponsored", "click here", "read more", "share this", "follow us"
        )
        return text
            .split(Regex("""(?<=[.!?])\s+"""))
            .map { it.trim() }
            .filter { sentence ->
                val lower = sentence.lowercase()
                val wordCount = sentence.split(Regex("\\s+")).size
                sentence.length in 60..280 &&
                wordCount >= 10 &&
                boilerplate.none { lower.contains(it) } &&
                !sentence.startsWith("©") && !sentence.startsWith("@")
            }
            .take(3)
            .joinToString("\n") { "• $it" }
    }
}
