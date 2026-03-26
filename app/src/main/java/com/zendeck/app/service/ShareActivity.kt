package com.zendeck.app.service

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShareActivity : ComponentActivity() {

    private lateinit var repository: LinkRepository
    private lateinit var scraperService: LinkScraperService
    private lateinit var llmService: LlmSummarizationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = LinkRepository.getInstance(this)
        scraperService = LinkScraperService
        llmService = LlmSummarizationService(this)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val url = extractUrl(sharedText)

        if (url == null) {
            Toast.makeText(this, "No valid URL found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Toast.makeText(this, "Saving…", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val ttlHours = getTtlHours()
                val scraped = scraperService.scrape(url)
                val (id, isNew) = repository.addLink(
                    url = url,
                    title = scraped.title,
                    description = scraped.description,
                    domain = scraped.domain,
                    faviconUrl = scraped.faviconUrl,
                    ttlHours = ttlHours
                )

                if (!isNew) {
                    Toast.makeText(this@ShareActivity, "Already saved", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                // Summarize only when AI summaries are enabled in Settings
                if (scraped.bodyText.isNotBlank() && getAiSummariesEnabled()) {
                    val customPrompt = getCustomSummaryPrompt()
                    val summary = llmService.summarize(scraped.bodyText, customPrompt = customPrompt)
                    if (summary.isNotBlank()) {
                        repository.updateSummary(id, summary)
                        // AI quality rating stored as tag for triage
                        val rating = llmService.rateArticle(scraped.title, summary)
                        if (rating != null) {
                            val current = repository.getTagsForLink(id)
                            val filtered = current.filter { !it.startsWith("ai:") }
                            repository.updateTags(id, filtered + "ai:$rating")
                        }
                    }
                }

                Toast.makeText(this@ShareActivity, "Saved ✓", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ShareActivity, "Error saving link", Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }

    private fun extractUrl(text: String?): String? {
        if (text == null) return null
        // Try to extract a URL from shared text
        val urlRegex = Regex("""https?://[^\s]+""")
        return urlRegex.find(text)?.value ?: if (text.startsWith("http")) text else null
    }

    private suspend fun getTtlHours(): Long {
        return try {
            val dataStore = (application as? com.zendeck.app.ZenDeckApplication)?.dataStore
            dataStore?.data?.first()?.get(longPreferencesKey("ttl_hours")) ?: 72L
        } catch (e: Exception) { 72L }
    }

    private suspend fun getAiSummariesEnabled(): Boolean {
        return try {
            val dataStore = (application as? com.zendeck.app.ZenDeckApplication)?.dataStore
            dataStore?.data?.first()?.get(SettingsViewModel.KEY_AI_SUMMARIES) ?: true
        } catch (e: Exception) { true }
    }

    private suspend fun getCustomSummaryPrompt(): String {
        return try {
            val dataStore = (application as? com.zendeck.app.ZenDeckApplication)?.dataStore
            dataStore?.data?.first()?.get(SettingsViewModel.KEY_CUSTOM_PROMPT) ?: ""
        } catch (e: Exception) { "" }
    }

    override fun onDestroy() {
        super.onDestroy()
        llmService.close()
    }
}
