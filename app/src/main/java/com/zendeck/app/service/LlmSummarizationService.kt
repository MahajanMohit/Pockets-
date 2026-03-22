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

    companion object {
        private const val TAG = "LlmSummarizationService"
        private const val MAX_TOKENS = 512

        // Prefer higher-quality models if present; fall back to smaller ones
        private val MODEL_FILENAMES = listOf(
            "gemma-3-4b-it-cpu-int4.bin",   // Gemma 3 4B – best quality for 12GB RAM
            "gemma-3-1b-it-cpu-int4.bin",   // Gemma 3 1B – fast, good quality
            "gemma-2b-it-gpu-int4.bin",     // Gemma 2B – GPU quantized
            "gemma-2b-it-cpu-int4.bin",     // Gemma 2B – CPU fallback
        )

        private val SEARCH_DIRS = listOf(
            "/storage/emulated/0/Download/gemma",  // actual path from device
            "/sdcard/Download/gemma",               // symlink variant
            "/sdcard/Download",
            "/data/local/tmp",
        )
    }

    private fun initialize() {
        if (isInitialized) return
        val modelPath = findModelPath() ?: return

        // Try GPU first (faster for GPU-quantized models), fall back to CPU
        if (tryInitialize(modelPath, useGpu = true)) return
        tryInitialize(modelPath, useGpu = false)
    }

    private fun tryInitialize(modelPath: String, useGpu: Boolean): Boolean {
        return try {
            val backend = if (useGpu)
                LlmInference.LlmInferenceOptions.Backend.GPU
            else
                LlmInference.LlmInferenceOptions.Backend.CPU
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setPreferredBackend(backend)
                .build()
            llm = LlmInference.createFromOptions(context, options)
            isInitialized = true
            Log.i(TAG, "LLM initialized on ${if (useGpu) "GPU" else "CPU"} from: $modelPath")
            true
        } catch (e: Exception) {
            Log.w(TAG, "LLM init failed (${if (useGpu) "GPU" else "CPU"}): ${e.message}")
            false
        }
    }

    private fun findModelPath(): String? {
        val searchDirs = listOf(context.filesDir) +
            SEARCH_DIRS.map { File(it) }
        for (name in MODEL_FILENAMES) {
            for (dir in searchDirs) {
                val f = File(dir, name)
                if (f.exists()) {
                    Log.i(TAG, "Found model: ${f.absolutePath}")
                    return f.absolutePath
                }
            }
        }
        return null
    }

    /**
     * @param text  Extracted article body text
     * @param memoryContext  Short string from ReadingMemoryStore describing user interests
     */
    suspend fun summarize(text: String, memoryContext: String = ""): String {
        if (text.isBlank()) return ""
        return withContext(Dispatchers.Default) {
            try {
                initialize()
                val inference = llm ?: return@withContext fallbackSummarize(text)
                val prompt = buildPrompt(text, memoryContext)
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

    private fun buildPrompt(text: String, memoryContext: String): String {
        val contextLine = if (memoryContext.isNotBlank())
            "Reader profile: $memoryContext\n\n"
        else ""

        // Truncate body to fit model context window (keep first 3000 chars)
        val truncated = text.take(3000)

        return """
            <start_of_turn>user
            ${contextLine}Summarize this article in exactly 3 concise bullet points.
            Rules:
            - Each bullet starts with "• "
            - Maximum 20 words per bullet
            - Focus on key facts, not filler or opinions
            - Only output the 3 bullets, nothing else

            Article:
            $truncated
            <end_of_turn>
            <start_of_turn>model
            •
        """.trimIndent()
    }

    private fun cleanSummary(raw: String): String {
        return raw.lines()
            .filter { it.trimStart().startsWith("•") || it.trimStart().startsWith("-") || it.trimStart().startsWith("*") }
            .take(3)
            .joinToString("\n") { line ->
                "• " + line.trimStart().removePrefix("•").removePrefix("-").removePrefix("*").trim()
            }
    }

    fun close() {
        llm?.close()
        llm = null
        isInitialized = false
    }
}
