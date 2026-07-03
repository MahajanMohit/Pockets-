package com.zendeck.app.service

/**
 * Pure-Kotlin extractive summarizer.
 *
 * Scores each sentence by:
 *   - keyword density (TF-like: avg frequency of content words)
 *   - position bonus  (first 20 % of article → 1.5×; last 10 % → 0.8×)
 *   - length penalty  (prefer 60–250 chars)
 *
 * Returns the top N sentences (in original order) as "• " bullet lines.
 */
object SummaryEngine {

    private val STOPWORDS = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "up", "about", "into", "through", "during",
        "is", "was", "are", "were", "be", "been", "being", "have", "has", "had",
        "do", "does", "did", "will", "would", "could", "should", "may", "might",
        "it", "its", "this", "that", "these", "those", "i", "you", "he", "she",
        "we", "they", "what", "which", "who", "whom", "how", "when", "where", "why",
        "not", "no", "so", "if", "as", "also", "just", "then", "than", "can",
        "more", "some", "said", "says", "has", "had", "now", "new", "one", "two"
    )

    private val BOILERPLATE = listOf(
        Regex("cookie", RegexOption.IGNORE_CASE),
        Regex("subscribe", RegexOption.IGNORE_CASE),
        Regex("newsletter", RegexOption.IGNORE_CASE),
        Regex("click here", RegexOption.IGNORE_CASE),
        Regex("sign up", RegexOption.IGNORE_CASE),
        Regex("terms of service", RegexOption.IGNORE_CASE),
        Regex("privacy policy", RegexOption.IGNORE_CASE),
        Regex("all rights reserved", RegexOption.IGNORE_CASE),
        Regex("read more", RegexOption.IGNORE_CASE),
        Regex("advertisement", RegexOption.IGNORE_CASE),
        Regex("sponsored", RegexOption.IGNORE_CASE),
        Regex("©"),
        Regex("follow us", RegexOption.IGNORE_CASE),
        Regex("share this", RegexOption.IGNORE_CASE),
        Regex("loading\\.\\.\\.", RegexOption.IGNORE_CASE),
        Regex("javascript", RegexOption.IGNORE_CASE),
    )

    /**
     * Summarize [text] into at most [maxBullets] bullet points.
     * Returns a newline-separated string of "• sentence" lines,
     * or "" if no meaningful sentences are found.
     *
     * [focusHint] — optional user hint ("technical details", "pricing", …);
     * sentences containing hint words get a scoring boost so the summary
     * leans toward what the user cares about.
     */
    fun summarize(text: String, maxBullets: Int = 4, focusHint: String = ""): String {
        if (text.isBlank()) return ""

        val sentences = splitSentences(text)
        if (sentences.isEmpty()) return ""

        if (sentences.size <= maxBullets) {
            return sentences.joinToString("\n") { "• ${it.trim()}" }
        }

        // Build word-frequency map over the whole text (content words only)
        val allWords = sentences
            .flatMap { it.split(Regex("\\s+")) }
            .map { it.lowercase().filter { c -> c.isLetter() } }
            .filter { it.length > 2 && it !in STOPWORDS }
        val wordFreq = allWords.groupingBy { it }.eachCount()

        val focusWords = focusHint.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in STOPWORDS }
            .toSet()

        // Score each sentence and pick the best
        val scored = sentences.mapIndexed { idx, sentence ->
            var score = score(sentence, idx, sentences.size, wordFreq)
            if (focusWords.isNotEmpty()) {
                val lower = sentence.lowercase()
                val hits = focusWords.count { lower.contains(it) }
                if (hits > 0) score *= 1.0 + 0.5 * hits   // +50 % per focus word hit
            }
            idx to score
        }

        val topIndices = scored
            .sortedByDescending { it.second }
            .take(maxBullets)
            .map { it.first }
            .sorted()   // restore reading order

        return topIndices.joinToString("\n") { "• ${sentences[it].trim()}" }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { s -> s.length in 40..300 && !isBoilerplate(s) }

    private fun isBoilerplate(s: String) = BOILERPLATE.any { it.containsMatchIn(s) }

    private fun score(
        sentence: String,
        position: Int,
        total: Int,
        wordFreq: Map<String, Int>
    ): Double {
        val words = sentence.split(Regex("\\s+"))
            .map { it.lowercase().filter { c -> c.isLetter() } }
            .filter { it.length > 2 && it !in STOPWORDS }

        if (words.isEmpty()) return 0.0

        val keywordScore = words.sumOf { wordFreq[it] ?: 0 }.toDouble() / words.size

        val positionBonus = when {
            position.toFloat() / total < 0.20f -> 1.5
            position.toFloat() / total > 0.90f -> 0.8
            else -> 1.0
        }

        val lengthPenalty = when {
            sentence.length < 60  -> 0.8
            sentence.length > 250 -> 0.9
            else -> 1.0
        }

        return keywordScore * positionBonus * lengthPenalty
    }
}
