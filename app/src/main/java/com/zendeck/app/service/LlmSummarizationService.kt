package com.zendeck.app.service

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LlmSummarizationService(private val context: Context) {

    private var llm: LlmInference? = null
    private var isInitialized = false
    private var initAttempted = false
    private var initFailReason = ""
    private var lastCandidatePaths: List<String> = emptyList()
    private var lastLoadErrors: List<String> = emptyList() // actual exception messages per candidate

    companion object {
        private const val TAG = "LlmSummarizationService"
        private const val MAX_TOKENS = 1024

        // CPU models listed first — GPU models fail on most devices and are kept only as a fallback
        private val MODEL_FILENAMES = listOf(
            "gemma-3-4b-it-cpu-int4.bin",   // Gemma 3 4B CPU – best quality
            "gemma-3-1b-it-cpu-int4.bin",   // Gemma 3 1B CPU – fast, good quality
            "gemma-2b-it-cpu-int4.bin",     // Gemma 2B CPU – reliable on all devices
            "gemma-2b-it-gpu-int4.bin",     // Gemma 2B GPU – fallback, may fail on some devices
        )

        private val SEARCH_DIRS = listOf(
            "/storage/emulated/0/Download/gemma",  // actual path from device
            "/sdcard/Download/gemma",               // symlink variant
            "/sdcard/Download",
            "/data/local/tmp",
        )

        /** Returns true if any supported model file exists on disk. */
        fun hasModel(context: Context): Boolean = getActiveModelName(context) != null

        /**
         * Returns the filename of the highest-priority model file that currently exists
         * on disk (same search order as initialization), or null if none found.
         * Used by the UI to display which model is active.
         */
        fun getActiveModelName(context: Context): String? {
            val dirs = buildList {
                add(context.filesDir)
                context.getExternalFilesDir("models")?.let { add(it) }
                addAll(SEARCH_DIRS.map { File(it) })
            }
            for (name in MODEL_FILENAMES) {
                for (dir in dirs) {
                    if (File(dir, name).exists()) return name
                }
            }
            return null
        }
    }

    private fun initialize() {
        if (isInitialized) return
        val candidates = findAllModelPaths()
        // Skip only if we already tried with these exact files — re-run if new files appeared
        if (initAttempted && candidates == lastCandidatePaths) return
        initAttempted = true
        lastCandidatePaths = candidates
        initFailReason = ""
        if (candidates.isEmpty()) {
            initFailReason = "no_file"
            Log.w(TAG, "No model file found in any search directory")
            return
        }
        // Try each candidate in priority order; GPU models may fail on unsupported devices
        val errors = mutableListOf<String>()
        for (path in candidates) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(path)
                    .setMaxTokens(MAX_TOKENS)
                    .setTopK(40)
                    .setTemperature(0.8f)
                    .setRandomSeed(101)
                    .build()
                llm = LlmInference.createFromOptions(context, options)
                isInitialized = true
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

    /** Returns every existing model file across all search dirs, in priority order. */
    private fun findAllModelPaths(): List<String> {
        val searchDirs = buildList {
            add(context.filesDir)
            context.getExternalFilesDir("models")?.let { add(it) }
            addAll(SEARCH_DIRS.map { File(it) })
        }
        return MODEL_FILENAMES.flatMap { name ->
            searchDirs.mapNotNull { dir ->
                val f = File(dir, name)
                if (f.exists()) { Log.i(TAG, "Found model candidate: ${f.absolutePath}"); f.absolutePath }
                else null
            }
        }
    }

    // Kept for callers that still reference findModelPath() conceptually; returns first candidate.
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
     * Sentence-extraction fallback when no LLM model is installed.
     *
     * Scores each sentence and picks up to 3 best:
     * - 60–280 chars (avoids labels and run-ons)
     * - At least 10 words
     * - Not matching any boilerplate phrase
     */
    private fun fallbackSummarize(text: String): String {
        val boilerplate = listOf(
            // Generic web boilerplate
            "subscribe", "newsletter", "sign up", "sign in", "log in", "login",
            "register", "create account", "forgot password",
            "privacy policy", "terms of service", "terms and conditions",
            "all rights reserved", "copyright ©", "cookie", "advertisement",
            "sponsored", "click here", "read more", "learn more", "find out more",
            "by continuing",
            // Social sharing / engagement
            "share your videos", "share with friends", "share with family",
            "friends, family", "share this", "follow us", "follow me",
            "like and subscribe", "hit the bell", "comment below",
            "check out my", "for more videos", "don't miss", "watch now",
            "stream now", "listen now", "watch later", "add to queue",
            "tweet", "facebook", "instagram", "patreon", "merch",
            // Ads / affiliate
            "affiliate", "discount code", "use code", "sponsored by",
            "this post contains", "as an amazon associate",
            // App install prompts
            "get it on google play", "download the app", "get the app",
            "available on", "play store", "app store",
            // Discovery / pagination
            "you might also like", "related articles", "recommended for you",
            "view all", "see all", "load more", "show more",
            // Comments section
            "add a comment", "report abuse", "flag as",
            // Tech boilerplate
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

        // If user provided a custom prompt, use it verbatim with the article appended
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

    /** Returns true if the model loaded successfully (i.e. LLM inference is active). */
    fun isLlmLoaded(): Boolean = isInitialized && llm != null

    /**
     * Sends an arbitrary [message] to the model and returns the raw response.
     * Useful for verifying inference works or for the Chat screen.
     */
    suspend fun chat(message: String): String {
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
                        append("• Storage permission denied (grant 'All files access' in Android Settings > Apps > ZenDeck > Permissions)\n")
                        append("• GPU model on unsupported device — use gemma-2b-it-cpu-int4.bin\n")
                        append("• Not enough RAM to load the model\n")
                        append("• Model file corrupted (re-download it)")
                    }
                    else ->
                        "⚠️ No AI model found.\n\nPlace a model file in:\n" +
                        "/storage/emulated/0/Download/gemma/\n\n" +
                        "Supported filenames:\n• gemma-2b-it-cpu-int4.bin\n• gemma-2b-it-gpu-int4.bin\n" +
                        "• gemma-3-1b-it-cpu-int4.bin\n• gemma-3-4b-it-cpu-int4.bin"
                }
                val prompt = """
                    <start_of_turn>user
                    You are a helpful reading assistant. When the user pastes or describes any text or article, summarise it in 5 to 6 concise bullet points, each capturing one key idea. Use clear, precise language. If the user asks a direct question instead, answer it directly without bullet points.

                    ${message.trim()}
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

    fun close() {
        llm?.close()
        llm = null
        isInitialized = false
        initAttempted = false
        initFailReason = ""
        lastCandidatePaths = emptyList()
    }
}
