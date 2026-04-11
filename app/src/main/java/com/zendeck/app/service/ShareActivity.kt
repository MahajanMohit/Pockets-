package com.zendeck.app.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.longPreferencesKey
import com.zendeck.app.ZenDeckApplication
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.ui.viewmodel.SettingsViewModel
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
                mainHandler.post { Toast.makeText(applicationContext, "Already saved", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            mainHandler.post { Toast.makeText(applicationContext, "Saved ✓", Toast.LENGTH_SHORT).show() }

            val outFile = ImageAnalysisService.copyAndCompress(applicationContext, uri)
            if (outFile == null) {
                repository.updateSummaryStatus(id, "unavailable")
                return@launch
            }
            repository.updateContentMeta(id, "image", outFile.absolutePath)

            val ocrText = ImageAnalysisService.extractText(outFile.absolutePath)
            val aiEnabled = prefs[SettingsViewModel.KEY_AI_SUMMARIES] ?: true
            if (ocrText.length >= 80 && aiEnabled) {
                val title = ImageAnalysisService.heuristicTitle(ocrText)
                repository.updateLinkMetadata(id, title, "", "image", "")
                val llmService = LlmSummarizationService(applicationContext)
                try {
                    val selectedPath = prefs[SettingsViewModel.KEY_SELECTED_MODEL_PATH]
                    if (selectedPath != null) llmService.setPreferredModelPath(selectedPath)
                    val customPrompt = prefs[SettingsViewModel.KEY_CUSTOM_PROMPT] ?: ""
                    val summary = llmService.summarize(ocrText, customPrompt = customPrompt)
                    if (summary.isNotBlank()) {
                        repository.updateSummary(id, summary)
                        repository.updateSummaryStatus(id, "done")
                    } else {
                        repository.updateSummaryStatus(id, "unavailable")
                    }
                } catch (_: Exception) {
                    repository.updateSummaryStatus(id, "unavailable")
                } finally {
                    llmService.close()
                }
            } else {
                repository.updateSummaryStatus(id, "unavailable")
            }
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
            val domain = extractDomain(url)

            // Save immediately with "pending" so the spinner shows in the card
            val (id, isNew) = repository.addLink(
                url = url, title = domain, description = "", domain = domain,
                faviconUrl = "", ttlHours = ttlHours, summaryStatus = "pending"
            )
            if (!isNew) {
                mainHandler.post { Toast.makeText(applicationContext, "Already saved", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            mainHandler.post { Toast.makeText(applicationContext, "Saved ✓", Toast.LENGTH_SHORT).show() }
            ZenDeckWidget.updateAll(applicationContext)

            // Scrape metadata
            val scraped = LinkScraperService.scrape(url)
            repository.updateLinkMetadata(id, scraped.title, scraped.description, scraped.domain, scraped.faviconUrl)

            val aiEnabled = prefs[SettingsViewModel.KEY_AI_SUMMARIES] ?: true
            if (scraped.bodyText.isNotBlank() && aiEnabled) {
                val llmService = LlmSummarizationService(applicationContext)
                try {
                    val selectedPath = prefs[SettingsViewModel.KEY_SELECTED_MODEL_PATH]
                    if (selectedPath != null) llmService.setPreferredModelPath(selectedPath)
                    val customPrompt = prefs[SettingsViewModel.KEY_CUSTOM_PROMPT] ?: ""
                    val summary = llmService.summarize(scraped.bodyText, customPrompt = customPrompt)
                    if (summary.isNotBlank()) {
                        repository.updateSummary(id, summary)
                        repository.updateSummaryStatus(id, "done")
                        if (llmService.isLlmLoaded()) {
                            val autoTags = llmService.generateTags(scraped.title, summary)
                            val modelName = llmService.getLoadedModelName()
                            val current = repository.getTagsForLink(id)
                            val userTags = current.filter { !it.startsWith("llm:") }
                            val newTags = userTags + autoTags + listOfNotNull(modelName?.let { "llm:$it" })
                            repository.updateTags(id, newTags)
                        }
                    } else {
                        repository.updateSummaryStatus(id, "unavailable")
                    }
                } catch (_: Exception) {
                    repository.updateSummaryStatus(id, "unavailable")
                } finally {
                    llmService.close()
                }
            } else {
                repository.updateSummaryStatus(id, if (aiEnabled) "unavailable" else "done")
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

            val (id, isNew) = repository.addLink(
                url = syntheticUrl, title = title, description = "", domain = "note",
                faviconUrl = "", ttlHours = ttlHours, contentType = "text", summaryStatus = "pending"
            )
            if (!isNew) {
                mainHandler.post { Toast.makeText(applicationContext, "Already saved", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            mainHandler.post { Toast.makeText(applicationContext, "Saved ✓", Toast.LENGTH_SHORT).show() }

            val aiEnabled = prefs[SettingsViewModel.KEY_AI_SUMMARIES] ?: true
            if (text.length >= 80 && aiEnabled) {
                val llmService = LlmSummarizationService(applicationContext)
                try {
                    val selectedPath = prefs[SettingsViewModel.KEY_SELECTED_MODEL_PATH]
                    if (selectedPath != null) llmService.setPreferredModelPath(selectedPath)
                    val customPrompt = prefs[SettingsViewModel.KEY_CUSTOM_PROMPT] ?: ""
                    val summary = llmService.summarize(text, customPrompt = customPrompt)
                    if (summary.isNotBlank()) {
                        repository.updateSummary(id, summary)
                        repository.updateSummaryStatus(id, "done")
                    } else {
                        repository.updateSummaryStatus(id, "unavailable")
                    }
                } catch (_: Exception) {
                    repository.updateSummaryStatus(id, "unavailable")
                } finally {
                    llmService.close()
                }
            } else {
                repository.updateSummaryStatus(id, "unavailable")
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
        } catch (e: Exception) { url }
    }
}
