package com.zendeck.app.service

import android.content.Context
import android.net.Uri
import android.util.Log
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

    private var llm: LlmInference? = null
    private var isInitialized = false
    private var initAttempted = false
    private var initFailReason = ""
    private var lastCandidatePaths: List<String> = emptyList()
    private var lastLoadErrors: List<String> = emptyList()
    private var preferredModelPath: String? = null
    private var loadedModelPath: String? = null

    companion object {
        private const val TAG = "LlmSummarizationService"
        private const val MAX_TOKENS = 1024

        private val SEARCH_DIRS = listOf(
            "/storage/emulated/0/Download/gemma",
            "/sdcard/Download/gemma",
            "/sdcard/Download",
            "/data/local/tmp",
        )

        /** Scans all known directories and returns every model file found. */
        fun discoverModels(context: Context): List<ModelInfo> {
            val dirs = buildList {
                add(context.filesDir)
                context.getExternalFilesDir("models")?.let { add(it) }
                addAll(SEARCH_DIRS.map { File(it) })
            }
            return dirs.flatMap { dir ->
                dir.listFiles()?.filter { f ->
                    f.isFile && (f.name.endsWith(".bin") || f.name.endsWith(".task") || f.name.endsWith(".gguf"))
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

        /** Returns true if any supported model file exists on disk. */
        fun hasModel(context: Context): Boolean = getActiveModelName(context) != null

        /**
         * Returns the filename of the highest-priority model file that currently exists
         * on disk, or null if none found.
         */
        fun getActiveModelName(context: Context): String? =
            discoverModels(context).firstOrNull()?.name
    }

    /**
     * Sets a specific model path to prefer. Resets the service so the next
     * inference call loads the preferred model.
     */
    fun setPreferredModelPath(path: String?) {
        if (path != preferredModelPath) {
            preferredModelPath = path
            llm?.close()
            llm = null
            isInitialized = false
            initAttempted = false
            lastCandidatePaths = emptyList()
        }
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
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(path)
                    .setMaxTokens(MAX_TOKENS)
                    .build()
                llm = LlmInference.createFromOptions(context, options)
                isInitialized = true
                loadedModelPath = path
                Log.i(TAG, "LLM initialized from: $path")
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

    /** Returns every existing model file across all search dirs, preferred path first. */
    private fun findAllModelPaths(): List<String> {
        val preferred = preferredModelPath?.let { p ->
            if (File(p).exists()) listOf(p) else emptyList()
        } ?: emptyList()
        val discovered = discoverModels(context)
            .map { it.path }
            .filter { it !in preferred }
        return preferred + discovered
    }

    private fun findModelPath(): String? = findAllModelPaths().firstOrNull()

    /**
     * @param text          Extracted article body text
     * @param memoryContext Short string from ReadingMemoryStore describing user interests
     * @param customPrompt  Optional user-defined prompt; overrides the built-in one when non-blank
     */
    suspend fun summarize(text: String, memoryContext: String = "", customPrompt: String = ""): String {
        if (text.isBlank()) return ""
        return withContext(Dispatchers.Default) {
            try {
                initialize()
                val inference = llm ?: return@withContext fallbackSummarize(text)
                val prompt = buildPrompt(text, memoryContext, customPrompt)
                val result = inference.generateResponse(prompt)
                val cleaned = cleanSummary(result)
                if (cleaned.isBlank()) fallbackSummarize(text) else cleaned
            } catch (e: Exception) {
                Log.w(TAG, "Summarization failed, using fallback: ${e.message}")
                fallbackSummarize(text)
            }
        }
    }

    /**
     * Rates an article as "must", "worth", or "skip" based on title and summary.
     * Returns null if no model is loaded or inference fails.
     */
    suspend fun rateArticle(title: String, summary: String): String? {
        if (title.isBlank() && summary.isBlank()) return null
        return withContext(Dispatchers.Default) {
            try {
                initialize()
                val inference = llm ?: return@withContext null
                val prompt = """
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
                val response = inference.generateResponse(prompt).trim().lowercase()
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
     * Sentence-extraction fallback when no LLM model is installed.
     */
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

    private fun buildPrompt(text: String, memoryContext: String, customPrompt: String = ""): String {
        val truncated = text.take(3000)

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

        val contextLine = if (memoryContext.isNotBlank())
            "Reader profile: $memoryContext\n\n"
        else ""

        return """
            <start_of_turn>user
            ${contextLine}Read the following article carefully and write a clear, fluent summary in 5 to 6 sentences. Use precise vocabulary and complete sentences. Cover the main point, the key supporting details, and any important conclusion or implication. Do not use bullet points, headers, or lists — write it as a single coherent paragraph. Do not begin with "The article" or "This article" — start directly with the substance.

            Article:
            $truncated
            <end_of_turn>
            <start_of_turn>model
        """.trimIndent()
    }

    private fun cleanSummary(raw: String): String {
        return raw.trim()
    }

    /** Returns true if the model loaded successfully. */
    fun isLlmLoaded(): Boolean = isInitialized && llm != null

    /** Returns the filename of the currently loaded model, or null if none loaded. */
    fun getLoadedModelName(): String? = loadedModelPath?.let { File(it).name }

    /**
     * Sends a message to the model with optional article context and image URI.
     * Article context feeds the user's saved reading list to the model.
     * Image URI is included as a path reference (text-based for CPU-only models).
     */
    suspend fun chat(
        message: String,
        articleContext: String = "",
        imageUri: Uri? = null
    ): String {
        if (message.isBlank()) return ""
        return withContext(Dispatchers.Default) {
            try {
                initialize()
                val inference = llm ?: return@withContext when (initFailReason) {
                    "load_failed" -> buildString {
                        append("⚠️ Model file(s) found but failed to load.\n\n")
                        append("Errors:\n")
                        lastLoadErrors.forEach { append("• $it\n") }
                        append("\nCommon causes:\n")
                        append("• Storage permission denied (grant 'All files access' in Android Settings > Apps > AI Link Triage > Permissions)\n")
                        append("• GPU model on unsupported device — use gemma3n-E2B-it-int4.task\n")
                        append("• Not enough RAM to load the model\n")
                        append("• Model file corrupted (re-download it)")
                    }
                    else ->
                        "⚠️ No AI model found.\n\nUse Settings → AI Model to import one, or place a model file in:\n" +
                        "/storage/emulated/0/Download/gemma/\n\n" +
                        "Supported filenames:\n• gemma3n-E2B-it-int4.task  (~1.5 GB, recommended)\n" +
                        "• gemma3n-E4B-it-int4.task  (~2.5 GB, best quality)\n" +
                        "• gemma-3-1b-it-cpu-int4.bin  (~0.8 GB, lightweight)"
                }

                val systemPreamble = buildChatSystemPrompt(articleContext)
                val userContent = buildString {
                    if (imageUri != null) {
                        append("[Image attached: ${imageUri.lastPathSegment}]\n")
                    }
                    append(message.trim())
                }

                val prompt = """
                    <start_of_turn>user
                    $systemPreamble

                    $userContent
                    <end_of_turn>
                    <start_of_turn>model
                """.trimIndent()

                inference.generateResponse(prompt).trim()
            } catch (e: Exception) {
                Log.w(TAG, "Chat inference failed: ${e.message}")
                "Error during inference: ${e.message}"
            }
        }
    }

    private fun buildChatSystemPrompt(articleContext: String): String {
        return if (articleContext.isNotBlank()) {
            """
You are the user's personal reading assistant with access to their saved reading list.
Their current saved articles:
$articleContext

Answer their questions about these articles, help them prioritise what to read,
identify themes, or answer any other question. Be concise and direct.
            """.trimIndent()
        } else {
            "You are a helpful AI assistant. Be concise and direct in your answers."
        }
    }

    fun close() {
        llm?.close()
        llm = null
        isInitialized = false
        initAttempted = false
        initFailReason = ""
        lastCandidatePaths = emptyList()
        preferredModelPath = null
        loadedModelPath = null
    }
}
