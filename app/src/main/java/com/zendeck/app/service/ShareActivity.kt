package com.zendeck.app.service

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.zendeck.app.data.repository.LinkRepository
import com.zendeck.app.server.ZenDeckNanoServer
import com.zendeck.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

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

        Toast.makeText(this, "Saving to ZenDeck…", Toast.LENGTH_SHORT).show()

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
                    Toast.makeText(this@ShareActivity, "Already in ZenDeck", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                // Summarize only when AI summaries are enabled in Settings
                if (scraped.bodyText.isNotBlank() && getAiSummariesEnabled()) {
                    val customPrompt = getCustomSummaryPrompt()
                    val summary = llmService.summarize(scraped.bodyText, customPrompt = customPrompt)
                    if (summary.isNotBlank()) {
                        repository.updateSummary(id, summary)
                    }
                }

                Toast.makeText(this@ShareActivity, "Saved to ZenDeck ✓", Toast.LENGTH_SHORT).show()

                // Push to peer device if one is configured
                val peerIp = getSyncPeerIp()
                if (peerIp.isNotBlank()) {
                    val savedLink = repository.getInboxLinksSnapshot().firstOrNull { it.url == url }
                    if (savedLink != null) {
                        launch(Dispatchers.IO) { pushLinkToPeer(savedLink, peerIp) }
                    }
                }
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

    private suspend fun getSyncPeerIp(): String {
        return try {
            val dataStore = (application as? com.zendeck.app.ZenDeckApplication)?.dataStore
            dataStore?.data?.first()?.get(SettingsViewModel.KEY_SYNC_PEER_IP) ?: ""
        } catch (e: Exception) { "" }
    }

    private fun pushLinkToPeer(link: com.zendeck.app.domain.model.LinkItem, peerIp: String) {
        try {
            val url = URL("http://$peerIp:${ZenDeckNanoServer.PORT}/api/sync/push")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            val body = Json.encodeToString(listOf(link))
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            Log.i("ShareActivity", "Pushed link to peer $peerIp → HTTP $code")
        } catch (e: Exception) {
            Log.w("ShareActivity", "Push to peer $peerIp failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        llmService.close()
    }
}
