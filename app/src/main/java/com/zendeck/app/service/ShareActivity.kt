package com.zendeck.app.service

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.longPreferencesKey
import com.zendeck.app.ZenDeckApplication
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = extractUrl(intent?.getStringExtra(Intent.EXTRA_TEXT))
        if (url == null) {
            Toast.makeText(this, "No valid URL found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val app = application as ZenDeckApplication
        val repository = LinkRepository.getInstance(applicationContext)
        val mainHandler = Handler(Looper.getMainLooper())

        app.applicationScope.launch {
            val prefs = app.dataStore.data.first()
            val ttlHours = prefs[longPreferencesKey("ttl_hours")] ?: 72L
            val domain = extractDomain(url)

            // Insert placeholder immediately so the link appears in the inbox right away
            val (id, isNew) = repository.addLink(
                url = url,
                title = domain,
                description = "",
                domain = domain,
                faviconUrl = "",
                ttlHours = ttlHours
            )

            if (!isNew) {
                mainHandler.post { Toast.makeText(applicationContext, "Already saved", Toast.LENGTH_SHORT).show() }
                return@launch
            }

            mainHandler.post { Toast.makeText(applicationContext, "Saved ✓", Toast.LENGTH_SHORT).show() }

            // Background enrichment — Activity has already finished
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

                        if (llmService.isLlmLoaded()) {
                            val modelName = llmService.getLoadedModelName()
                            val rating = llmService.rateArticle(scraped.title, summary)
                            val current = repository.getTagsForLink(id)
                            val newTags = current.filter { !it.startsWith("ai:") && !it.startsWith("llm:") } +
                                listOfNotNull(rating?.let { "ai:$it" }, modelName?.let { "llm:$it" })
                            repository.updateTags(id, newTags)
                        }
                    }
                } finally {
                    llmService.close()
                }
            }
        }

        finish()
    }

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
