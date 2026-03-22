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
        private const val MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"
        private const val MAX_TOKENS = 512
    }

    private fun initialize() {
        if (isInitialized) return
        try {
            val modelPath = findModelPath() ?: return
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .build()
            llm = LlmInference.createFromOptions(context, options)
            isInitialized = true
            Log.i(TAG, "LLM initialized from: $modelPath")
        } catch (e: Exception) {
            Log.w(TAG, "LLM initialization failed, summaries disabled: ${e.message}")
        }
    }

    private fun findModelPath(): String? {
        // Check common locations for the model file
        val locations = listOf(
            File(context.filesDir, MODEL_FILENAME),
            File("/sdcard/Download/$MODEL_FILENAME"),
            File("/data/local/tmp/$MODEL_FILENAME")
        )
        return locations.firstOrNull { it.exists() }?.absolutePath
    }

    suspend fun summarize(text: String): String {
        if (text.isBlank()) return ""
        return withContext(Dispatchers.Default) {
            try {
                initialize()
                val inference = llm ?: return@withContext fallbackSummarize(text)
                val prompt = buildPrompt(text)
                val result = inference.generateResponse(prompt)
                val cleaned = cleanSummary(result)
                // If LLM returned garbage (empty after cleaning), fall back
                if (cleaned.isBlank()) fallbackSummarize(text) else cleaned
            } catch (e: Exception) {
                Log.w(TAG, "Summarization failed, using fallback: ${e.message}")
                fallbackSummarize(text)
            }
        }
    }

    /**
     * Sentence-extraction fallback used when no LLM model is installed.
     *
     * Scores each sentence and picks the 3 best:
     * - Must be 50–250 chars (filters single-word labels and run-on strings)
     * - Must contain at least 8 words
     * - Must not match common boilerplate patterns (subscribe, cookie notice, ads, etc.)
     */
    private fun fallbackSummarize(text: String): String {
        val boilerplate = listOf(
            "subscribe", "newsletter", "sign up", "sign in", "log in", "login",
            "register", "privacy policy", "terms of service", "terms and conditions",
            "all rights reserved", "copyright ©", "cookie", "advertisement",
            "sponsored", "click here", "read more", "learn more", "find out more",
            "follow us", "share this", "tweet", "facebook", "instagram",
            "javascript", "enable javascript", "browser", "reload", "refresh"
        )

        return text
            .split(Regex("""(?<=[.!?])\s+"""))
            .map { it.trim() }
            .filter { sentence ->
                val lower = sentence.lowercase()
                val wordCount = sentence.split(Regex("\\s+")).size
                sentence.length in 50..250 &&
                wordCount >= 8 &&
                boilerplate.none { lower.contains(it) } &&
                !sentence.startsWith("©")
            }
            .take(3)
            .joinToString("\n") { "• $it" }
    }

    private fun buildPrompt(text: String): String = """
        <start_of_turn>user
        Summarize the following article in exactly 3 concise bullet points.
        Each bullet must start with "• ".
        Keep each bullet under 20 words.
        Only output the 3 bullets, nothing else.

        Article: $text
        <end_of_turn>
        <start_of_turn>model
    """.trimIndent()

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
