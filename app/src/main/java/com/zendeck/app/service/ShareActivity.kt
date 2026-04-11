package com.zendeck.app.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zendeck.app.ZenDeckApplication
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.widget.ZenDeckWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            intent?.type?.startsWith("image/") == true -> handleImageShare()
            else -> handleTextOrLinkShare()
        }
        finish()
    }

    // ── Image share ──────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun handleImageShare() {
        val uri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null) {
            Toast.makeText(this, "No image found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {}

        val app = application as ZenDeckApplication
        val repository = LinkRepository.getInstance(applicationContext)
        val mainHandler = Handler(Looper.getMainLooper())
        val syntheticUrl = "image://${uri.toString().hashCode().toUInt()}"

        app.applicationScope.launch {
            val prefs = app.dataStore.data.first()
            val ttlHours = prefs[longPreferencesKey("ttl_hours")] ?: 72L

            val (id, isNew) = repository.addLink(
                url = syntheticUrl, title = "Screenshot", description = "",
                domain = "image", faviconUrl = "", ttlHours = ttlHours,
                contentType = "image", summaryStatus = "pending"
            )
            if (!isNew) {
                mainHandler.post {
                    Toast.makeText(applicationContext, "Already saved", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            mainHandler.post { Toast.makeText(applicationContext, "Saved ✓", Toast.LENGTH_SHORT).show() }

            // Compress image and update the stored path
            val outFile = ImageAnalysisService.copyAndCompress(applicationContext, uri)
            if (outFile == null) {
                repository.updateSummaryStatus(id, "unavailable")
                return@launch
            }
            repository.updateContentMeta(id, "image", outFile.absolutePath)
            repository.updateSummaryStatus(id, "unavailable")  // images don't have text summary
        }
    }

    // ── Text / link share ────────────────────────────────────────────────────

    private fun handleTextOrLinkShare() {
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val url = extractUrl(sharedText)
        when {
            url != null -> handleLinkShare(url)
            !sharedText.isNullOrBlank() -> handlePlainTextShare(sharedText)
            else -> Toast.makeText(this, "No content found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLinkShare(url: String) {
        val app = application as ZenDeckApplication
        val repository = LinkRepository.getInstance(applicationContext)
        val mainHandler = Handler(Looper.getMainLooper())

        app.applicationScope.launch {
            val prefs = app.dataStore.data.first()
            val ttlHours = prefs[longPreferencesKey("ttl_hours")] ?: 72L
            val customPrompt = prefs[stringPreferencesKey("custom_summary_prompt")] ?: ""
            val modelPath = prefs[stringPreferencesKey("selected_model_path")]
            val domain = extractDomain(url)

            // Save immediately with "pending" so the spinner shows in the card
            val (id, isNew) = repository.addLink(
                url = url, title = domain, description = "", domain = domain,
                faviconUrl = "", ttlHours = ttlHours, summaryStatus = "pending"
            )
            if (!isNew) {
                mainHandler.post {
                    Toast.makeText(applicationContext, "Already saved", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            mainHandler.post { Toast.makeText(applicationContext, "Saved ✓", Toast.LENGTH_SHORT).show() }
            ZenDeckWidget.updateAll(applicationContext)

            // Scrape metadata
            val scraped = LinkScraperService.scrape(url)
            repository.updateLinkMetadata(id, scraped.title, scraped.description, scraped.domain, scraped.faviconUrl)

            if (scraped.bodyText.isNotBlank()) {
                // Summarize: LLM with SummaryEngine fallback
                val llm = LlmSummarizationService(applicationContext)
                modelPath?.let { llm.setPreferredModelPath(it) }
                val summary = llm.summarize(scraped.bodyText, customPrompt)
                if (summary.isNotBlank()) {
                    repository.updateSummary(id, summary)
                    repository.updateSummaryStatus(id, "done")
                } else {
                    repository.updateSummaryStatus(id, "unavailable")
                }
                // Generate AI tags, fall back to title heuristics
                val aiTags = llm.generateTags(scraped.title, scraped.bodyText)
                val finalTags = if (aiTags.isNotEmpty()) {
                    aiTags.map { "llm:$it" }
                } else {
                    extractAutoTags(scraped.title)
                }
                if (finalTags.isNotEmpty()) {
                    val current = repository.getTagsForLink(id)
                    val userTags = current.filter { !it.startsWith("auto:") && !it.startsWith("llm:") }
                    repository.updateTags(id, userTags + finalTags)
                }
                llm.close()
            } else {
                repository.updateSummaryStatus(id, "unavailable")
            }
        }
    }

    private fun handlePlainTextShare(text: String) {
        val app = application as ZenDeckApplication
        val repository = LinkRepository.getInstance(applicationContext)
        val mainHandler = Handler(Looper.getMainLooper())

        val hash = MessageDigest.getInstance("SHA-256")
            .digest(text.take(200).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val syntheticUrl = "text://$hash"
        val title = ImageAnalysisService.heuristicTitle(text)

        app.applicationScope.launch {
            val prefs = app.dataStore.data.first()
            val ttlHours = prefs[longPreferencesKey("ttl_hours")] ?: 72L
            val customPrompt = prefs[stringPreferencesKey("custom_summary_prompt")] ?: ""
            val modelPath = prefs[stringPreferencesKey("selected_model_path")]

            val (id, isNew) = repository.addLink(
                url = syntheticUrl, title = title, description = text.take(1000),
                domain = "note", faviconUrl = "", ttlHours = ttlHours,
                contentType = "text", summaryStatus = "pending"
            )
            if (!isNew) {
                mainHandler.post {
                    Toast.makeText(applicationContext, "Already saved", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            mainHandler.post { Toast.makeText(applicationContext, "Saved ✓", Toast.LENGTH_SHORT).show() }

            if (text.length >= 80) {
                val llm = LlmSummarizationService(applicationContext)
                modelPath?.let { llm.setPreferredModelPath(it) }
                val summary = llm.summarize(text, customPrompt)
                if (summary.isNotBlank()) {
                    repository.updateSummary(id, summary)
                    repository.updateSummaryStatus(id, "done")
                } else {
                    repository.updateSummaryStatus(id, "unavailable")
                }
                llm.close()
            } else {
                // Short text — no summary needed, already stored as description
                repository.updateSummaryStatus(id, "done")
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun extractUrl(text: String?): String? {
        if (text == null) return null
        val urlRegex = Regex("""https?://[^\s]+""")
        return urlRegex.find(text)?.value ?: if (text.startsWith("http")) text else null
    }

    private fun extractDomain(url: String): String {
        return try {
            java.net.URI(url).host?.removePrefix("www.") ?: url
        } catch (_: Exception) { url }
    }

    /** Derives 1-3 auto tags from a page title (lowercase, 3+ chars, no stopwords). */
    private val STOPWORDS = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "is", "are", "was", "were", "how", "why", "what",
        "this", "that", "your", "you", "its", "it", "from", "as", "be"
    )

    private fun extractAutoTags(title: String): List<String> =
        title.split(Regex("[^a-zA-Z0-9]+"))
            .map { it.lowercase().trim() }
            .filter { it.length >= 4 && it !in STOPWORDS }
            .distinct()
            .take(3)
            .map { "auto:$it" }
}
