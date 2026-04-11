package com.zendeck.app.service

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LlmSummarizationService(private val context: Context) {

    data class ModelInfo(
        val name: String,
        val path: String,
        val isVision: Boolean = false
    )

    // LiteRT-LM engine (Gemma 4 E2B / E4B — .litertlm files)
    private var engine: Engine? = null
    // MediaPipe inference (legacy Gemma 2/3 — .bin / .task files)
    private var llm: LlmInference? = null

    private var isInitialized = false
    private var isLiteRtLmMode = false
    private var initAttempted = false
    private var initFailReason = ""
    private var lastCandidatePaths: List<String> = emptyList()
    private var lastLoadErrors: List<String> = emptyList()
    private var preferredModelPath: String? = null
    private var loadedModelPath: String? = null

    // ── Multi-turn chat session state ─────────────────────────────────────────
    // LiteRT-LM keeps a Conversation alive across turns; MediaPipe rebuilds from history.
    private var chatConversation: Conversation? = null
    private val chatHistory = mutableListOf<Pair<String, String>>() // (user, ai) for MediaPipe
    private var chatSystemPrompt = ""

    companion object {
        private const val TAG = "LlmSummarizationService"
        private const val MAX_TOKENS = 1024

        private val SEARCH_DIRS = listOf(
            "/storage/emulated/0/Download/gemma",
            "/sdcard/Download/gemma",
            "/sdcard/Download",
            "/data/local/tmp",
        )

        /** Scans all known directories and returns every supported model file found. */
        fun discoverModels(context: Context): List<ModelInfo> {
            val dirs = buildList {
                add(context.filesDir)
                context.getExternalFilesDir("models")?.let { add(it) }
                addAll(SEARCH_DIRS.map { File(it) })
            }
            return dirs.flatMap { dir ->
                dir.listFiles()?.filter { f ->
                    f.isFile && (f.name.endsWith(".bin") || f.name.endsWith(".task") ||
                                 f.name.endsWith(".gguf") || f.name.endsWith(".litertlm"))
                }?.map { f ->
                    ModelInfo(
                        name = f.name,
                        path = f.absolutePath,
                        isVision = f.name.contains("vision", ignoreCase = true) ||
                                   f.name.contains("vit", ignoreCase = true) ||
                                   f.name.contains("multimodal", ignoreCase = true)
                    )
                } ?: emptyList()
            }.distinctBy { it.path }
        }

        fun hasModel(context: Context): Boolean = getActiveModelName(context) != null

        fun getActiveModelName(context: Context): String? =
            discoverModels(context).firstOrNull()?.name

        private fun isLiteRtLmFile(path: String) = path.endsWith(".litertlm")
    }

    fun setPreferredModelPath(path: String?) {
        if (path != preferredModelPath) {
            preferredModelPath = path
            resetBackends()
        }
    }

    private fun resetBackends() {
        llm?.close()
        llm = null
        engine?.close()
        engine = null
        isInitialized = false
        isLiteRtLmMode = false
        initAttempted = false
        lastCandidatePaths = emptyList()
        resetChatSession("")
    }

    /** Starts a fresh multi-turn chat session with the given system prompt. */
    fun resetChatSession(systemPrompt: String) {
        chatConversation?.close()
        chatConversation = null
        chatHistory.clear()
        chatSystemPrompt = systemPrompt
    }

    private fun initialize() {
        if (isInitialized) return
        val candidates = findAllModelPaths()
        if (initAttempted && candidates == lastCandidatePaths) return
        initAttempted = true
        lastCandidatePaths = candidates
        initFailReason = ""
        if (candidates.isEmpty()) {
            initFailReason = "no_file"
            Log.w(TAG, "No model file found in any search directory")
            return
        }
        val errors = mutableListOf<String>()
        for (path in candidates) {
            try {
                if (isLiteRtLmFile(path)) {
                    // ── LiteRT-LM path (Gemma 4 E2B / E4B) ──────────────────
                    val eng = Engine(EngineConfig(modelPath = path, backend = Backend.CPU()))
                    eng.initialize()
                    engine = eng
                    isLiteRtLmMode = true
                } else {
                    // ── MediaPipe path (legacy Gemma 2 / 3) ──────────────────
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(path)
                        .setMaxTokens(MAX_TOKENS)
                        .build()
                    llm = LlmInference.createFromOptions(context, options)
                    isLiteRtLmMode = false
                }
                isInitialized = true
                loadedModelPath = path
                Log.i(TAG, "LLM initialised from: $path (LiteRT-LM=$isLiteRtLmMode)")
                return
            } catch (e: Exception) {
                val msg = "${File(path).name}: ${e::class.simpleName}: ${e.message}"
                Log.w(TAG, "Failed to load $path: $msg")
                errors += msg
            }
        }
        initFailReason = "load_failed"
        lastLoadErrors = errors
        Log.w(TAG, "All model candidates failed: $errors")
    }

    private fun findAllModelPaths(): List<String> {
        val preferred = preferredModelPath?.let { p ->
            if (File(p).exists()) listOf(p) else emptyList()
        } ?: emptyList()
        // Prefer .litertlm (Gemma 4) over older formats when auto-discovering
        val discovered = discoverModels(context)
            .sortedByDescending { it.path.endsWith(".litertlm") }
            .map { it.path }
            .filter { it !in preferred }
        return preferred + discovered
    }

    /**
     * Generates a response using whichever backend is active.
     * For LiteRT-LM: creates a fresh Conversation per call (stateless for summarisation).
     * For MediaPipe: calls generateResponse directly.
     */
    private suspend fun generateResponse(userContent: String, systemInstruction: String = ""): String {
        if (isLiteRtLmMode) {
            val eng = engine ?: return ""
            val convConfig = if (systemInstruction.isNotBlank()) {
                ConversationConfig(systemInstruction = Contents.of(systemInstruction))
            } else {
                ConversationConfig()
            }
            return eng.createConversation(convConfig).use { conv ->
                val sb = StringBuilder()
                conv.sendMessageAsync(userContent).collect { token -> sb.append(token) }
                sb.toString().trim()
            }
        } else {
            return llm?.generateResponse(userContent)?.trim() ?: ""
        }
    }

    suspend fun summarize(text: String, memoryContext: String = "", customPrompt: String = ""): String {
        if (text.isBlank()) return ""
        return withContext(Dispatchers.Default) {
            try {
                initialize()
                if (!isInitialized) return@withContext fallbackSummarize(text)

                val (userContent, systemInstruction) = buildSummarizePrompt(
                    text, memoryContext, customPrompt
                )
                val result = generateResponse(userContent, systemInstruction)
                val cleaned = cleanSummary(result)
                if (cleaned.isBlank()) fallbackSummarize(text) else cleaned
            } catch (e: Exception) {
                Log.w(TAG, "Summarization failed, using fallback: ${e.message}")
                fallbackSummarize(text)
            }
        }
    }

    suspend fun rateArticle(title: String, summary: String): String? {
        if (title.isBlank() && summary.isBlank()) return null
        return withContext(Dispatchers.Default) {
            try {
                initialize()
                if (!isInitialized) return@withContext null

                val userContent = if (isLiteRtLmMode) {
                    // LiteRT-LM: plain instruction to the model
                    "Rate this article as exactly one of: must / worth / skip\n" +
                    "Rules:\n" +
                    "- \"must\": genuinely new information, timely insight, or practical value\n" +
                    "- \"worth\": interesting but not urgent\n" +
                    "- \"skip\": generic, clickbait, low-information, or promotional\n" +
                    "Output only one word.\n\n" +
                    "Title: $title\nSummary: ${summary.take(500)}"
                } else {
                    // MediaPipe: raw prompt with turn markers
                    """
                    <start_of_turn>user
                    Based on this article's title and summary, rate it as exactly one of: must / worth / skip
                    Rules:
                    - "must": contains genuinely new information, timely insight, or practical value
                    - "worth": interesting but not urgent, safe to read when convenient
                    - "skip": generic, clickbait, low-information, or promotional
                    Only output one word, nothing else.

                    Title: $title
                    Summary: ${summary.take(500)}
                    <end_of_turn>
                    <start_of_turn>model
                    """.trimIndent()
                }

                val response = generateResponse(userContent).trim().lowercase()
                val firstWord = response.split(Regex("\\s+")).firstOrNull() ?: ""
                when {
                    firstWord.startsWith("must")  -> "must"
                    firstWord.startsWith("skip")  -> "skip"
                    firstWord.startsWith("worth") -> "worth"
                    else -> null
                }
            } catch (e: Exception) {
                Log.w(TAG, "rateArticle failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Sends one turn in an ongoing multi-turn chat session.
     * Call [resetChatSession] once before the first turn to set the system prompt.
     * The conversation context is automatically maintained across calls.
     *
     * @param userContent  The user's message (may include prepended OCR text from images).
     */
    suspend fun chatTurn(userContent: String): String {
        if (userContent.isBlank()) return ""
        return withContext(Dispatchers.Default) {
            try {
                initialize()
                if (!isInitialized) return@withContext buildNotFoundMessage()

                if (isLiteRtLmMode) {
                    val eng = engine ?: return@withContext buildNotFoundMessage()
                    // Create or reuse the conversation object for this session
                    if (chatConversation == null) {
                        val config = ConversationConfig(
                            systemInstruction = Contents.of(chatSystemPrompt)
                        )
                        chatConversation = eng.createConversation(config)
                    }
                    val sb = StringBuilder()
                    chatConversation!!.sendMessageAsync(userContent).collect { sb.append(it) }
                    sb.toString().trim()
                } else {
                    // MediaPipe: manually reconstruct context from history each turn
                    val sb = StringBuilder()
                    if (chatSystemPrompt.isNotBlank()) {
                        sb.append("<start_of_turn>user\n${chatSystemPrompt}<end_of_turn>\n<start_of_turn>model\nUnderstood. I will act as your reading assistant.<end_of_turn>\n")
                    }
                    chatHistory.takeLast(8).forEach { (u, a) ->
                        sb.append("<start_of_turn>user\n$u<end_of_turn>\n<start_of_turn>model\n$a<end_of_turn>\n")
                    }
                    sb.append("<start_of_turn>user\n$userContent<end_of_turn>\n<start_of_turn>model\n")
                    val response = llm?.generateResponse(sb.toString())?.trim() ?: ""
                    chatHistory.add(Pair(userContent, response))
                    response
                }
            } catch (e: Exception) {
                Log.w(TAG, "Chat inference failed: ${e.message}")
                "Error during inference: ${e.message}"
            }
        }
    }

    /**
     * Generates 2–4 short descriptive tags for the given content.
     * Returns an empty list if no model is available.
     */
    suspend fun generateTags(title: String, text: String): List<String> {
        if (title.isBlank() && text.isBlank()) return emptyList()
        return withContext(Dispatchers.Default) {
            try {
                initialize()
                if (!isInitialized) return@withContext emptyList()

                val snippet = text.take(600)
                val userContent = if (isLiteRtLmMode) {
                    "Generate 2 to 4 short tags for this content.\n" +
                    "Rules: each tag is 1–3 words, lowercase, no spaces (use hyphens), no hashtags.\n" +
                    "Output ONLY the tags, one per line, nothing else.\n\n" +
                    "Title: $title\nContent: $snippet"
                } else {
                    """
                    <start_of_turn>user
                    Generate 2 to 4 short tags for this content.
                    Rules: each tag is 1-3 words, lowercase, no spaces (use hyphens), no hashtags.
                    Output ONLY the tags, one per line, nothing else.

                    Title: $title
                    Content: $snippet
                    <end_of_turn>
                    <start_of_turn>model
                    """.trimIndent()
                }
                val sysInstruction = if (isLiteRtLmMode) "You are a content tagger. Output only tags." else ""
                val raw = generateResponse(userContent, sysInstruction)

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

    // ── Prompt builders ───────────────────────────────────────────────────────

    /**
     * Returns (userContent, systemInstruction).
     * For LiteRT-LM the system instruction is separated out; for MediaPipe it's
     * embedded in the raw turn-marker prompt.
     */
    private fun buildSummarizePrompt(
        text: String,
        memoryContext: String,
        customPrompt: String
    ): Pair<String, String> {
        val truncated = text.take(3000)
        return if (isLiteRtLmMode) {
            val contextLine = if (memoryContext.isNotBlank()) "Reader profile: $memoryContext\n\n" else ""
            val userMsg = if (customPrompt.isNotBlank()) {
                "${customPrompt.trim()}\n\nArticle:\n$truncated"
            } else {
                "${contextLine}Summarise the following article in 5 to 6 sentences as a single " +
                "coherent paragraph. Use precise vocabulary and complete sentences. Cover the main " +
                "point, key supporting details, and any important conclusion. Do not use bullet " +
                "points or headers. Do not begin with 'The article' or 'This article'.\n\nArticle:\n$truncated"
            }
            Pair(userMsg, "You are a reading assistant. Be accurate and concise.")
        } else {
            // MediaPipe: return the full formatted prompt as userContent, empty system
            Pair(buildMediaPipePrompt(truncated, memoryContext, customPrompt), "")
        }
    }

    private fun buildMediaPipePrompt(truncated: String, memoryContext: String, customPrompt: String): String {
        if (customPrompt.isNotBlank()) {
            return """
                <start_of_turn>user
                ${customPrompt.trim()}

                Article:
                $truncated
                <end_of_turn>
                <start_of_turn>model
            """.trimIndent()
        }
        val contextLine = if (memoryContext.isNotBlank()) "Reader profile: $memoryContext\n\n" else ""
        return """
            <start_of_turn>user
            ${contextLine}Read the following article carefully and write a clear, fluent summary in 5 to 6 sentences. Use precise vocabulary and complete sentences. Cover the main point, the key supporting details, and any important conclusion or implication. Do not use bullet points, headers, or lists — write it as a single coherent paragraph. Do not begin with "The article" or "This article" — start directly with the substance.

            Article:
            $truncated
            <end_of_turn>
            <start_of_turn>model
        """.trimIndent()
    }

    private fun buildChatSystemPrompt(articleContext: String): String {
        return if (articleContext.isNotBlank()) {
            "You are the user's personal reading assistant with access to their saved reading list.\n" +
            "Their current saved articles:\n$articleContext\n\n" +
            "Answer their questions about these articles, help them prioritise what to read, " +
            "identify themes, or answer any other question. Be concise and direct."
        } else {
            "You are a helpful AI assistant. Be concise and direct in your answers."
        }
    }

    private fun buildNotFoundMessage(): String {
        return if (initFailReason == "load_failed") buildString {
            append("⚠️ Model file(s) found but failed to load.\n\nErrors:\n")
            lastLoadErrors.forEach { append("• $it\n") }
            append("\nCommon causes:\n")
            append("• Storage permission denied (grant 'All files access' in Android Settings → Apps → AI Link Triage → Permissions)\n")
            append("• Not enough RAM to load the model\n")
            append("• Model file corrupted (re-download it)\n")
            append("• GPU model on unsupported device — use gemma-4-E2B-it.litertlm")
        } else {
            "⚠️ No AI model found.\n\nUse Settings → AI Model to import one, or place a model file in:\n" +
            "/storage/emulated/0/Download/gemma/\n\n" +
            "Supported filenames:\n• gemma-4-E2B-it.litertlm  (~2.6 GB, recommended)\n" +
            "• gemma-4-E4B-it.litertlm  (~4.3 GB, best quality)\n" +
            "• gemma-3-1b-it-cpu-int4.bin  (~0.8 GB, lightweight legacy)"
        }
    }

    // ── Fallback (no model) ───────────────────────────────────────────────────

    private fun fallbackSummarize(text: String): String {
        val boilerplate = listOf(
            "subscribe", "newsletter", "sign up", "sign in", "log in", "login",
            "register", "create account", "forgot password",
            "privacy policy", "terms of service", "terms and conditions",
            "all rights reserved", "copyright ©", "cookie", "advertisement",
            "sponsored", "click here", "read more", "learn more", "find out more",
            "by continuing",
            "share your videos", "share with friends", "share with family",
            "friends, family", "share this", "follow us", "follow me",
            "like and subscribe", "hit the bell", "comment below",
            "check out my", "for more videos", "don't miss", "watch now",
            "stream now", "listen now", "watch later", "add to queue",
            "tweet", "facebook", "instagram", "patreon", "merch",
            "affiliate", "discount code", "use code", "sponsored by",
            "this post contains", "as an amazon associate",
            "get it on google play", "download the app", "get the app",
            "available on", "play store", "app store",
            "you might also like", "related articles", "recommended for you",
            "view all", "see all", "load more", "show more",
            "add a comment", "report abuse", "flag as",
            "javascript", "enable javascript", "browser", "reload", "refresh",
            "noscript"
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
                !sentence.startsWith("©") &&
                !sentence.startsWith("@")
            }
            .take(3)
            .joinToString("\n") { "• $it" }
    }

    private fun cleanSummary(raw: String) = raw.trim()

    // ── Status accessors ──────────────────────────────────────────────────────

    fun isLlmLoaded(): Boolean = isInitialized && (llm != null || engine != null)
    fun getLoadedModelName(): String? = loadedModelPath?.let { File(it).name }

    fun close() {
        chatConversation?.close()
        chatConversation = null
        chatHistory.clear()
        llm?.close()
        llm = null
        engine?.close()
        engine = null
        isInitialized = false
        isLiteRtLmMode = false
        initAttempted = false
        initFailReason = ""
        lastCandidatePaths = emptyList()
        preferredModelPath = null
        loadedModelPath = null
    }
}
