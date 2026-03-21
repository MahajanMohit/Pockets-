package com.zendeck.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URI

data class ScrapedContent(
    val title: String,
    val description: String,
    val bodyText: String,
    val domain: String,
    val faviconUrl: String
)

object LinkScraperService {
    private const val BODY_TEXT_LIMIT = 4000
    private const val TIMEOUT_MS = 10_000

    suspend fun scrape(url: String): ScrapedContent = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .timeout(TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36 ZenDeck/1.0")
                .followRedirects(true)
                .get()

            val domain = extractDomain(url)
            val title = doc.title().ifBlank {
                doc.select("meta[property=og:title]").attr("content").ifBlank { domain }
            }
            val description = doc.select("meta[name=description]").attr("content").ifBlank {
                doc.select("meta[property=og:description]").attr("content")
            }

            // Remove nav, footer, ads before extracting text
            doc.select("nav, footer, script, style, .ad, .ads, #sidebar").remove()
            val bodyText = doc.body().text().take(BODY_TEXT_LIMIT)

            val faviconUrl = doc.select("link[rel~=(?i)icon]").firstOrNull()
                ?.absUrl("href")
                ?.ifBlank { null }
                ?: "https://$domain/favicon.ico"

            ScrapedContent(
                title = title,
                description = description,
                bodyText = bodyText,
                domain = domain,
                faviconUrl = faviconUrl
            )
        } catch (e: Exception) {
            // Return minimal data if scraping fails
            val domain = extractDomain(url)
            ScrapedContent(
                title = domain,
                description = "",
                bodyText = "",
                domain = domain,
                faviconUrl = "https://$domain/favicon.ico"
            )
        }
    }

    private fun extractDomain(url: String): String = try {
        URI(url).host?.removePrefix("www.") ?: url
    } catch (e: Exception) {
        url
    }
}
