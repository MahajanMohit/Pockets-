package com.zendeck.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

    // Sites that require JavaScript or serve only boilerplate via Jsoup
    private val KNOWN_JS_DOMAINS = setOf(
        "youtube.com", "youtu.be",
        "twitter.com", "x.com",
        "instagram.com", "threads.net",
        "facebook.com", "fb.com",
        "tiktok.com",
        "reddit.com", "redd.it",
        "linkedin.com"
    )

    // Phrases that indicate the site requires JavaScript to render — Jsoup can't scrape these.
    private val JS_GATE_PHRASES = listOf(
        "javascript is disabled",
        "javascript is not enabled",
        "enable javascript",
        "javascript required",
        "please enable javascript",
        "requires javascript to run",
        "this site requires javascript",
        "you need to enable javascript"
    )

    suspend fun scrape(url: String): ScrapedContent = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .timeout(TIMEOUT_MS)
                // Pretend to be a real browser so more sites respond
                .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .followRedirects(true)
                .get()

            val domain = extractDomain(url)

            // Known JS-heavy domains — Jsoup only gets homepage boilerplate, skip body
            if (KNOWN_JS_DOMAINS.any { domain == it || domain.endsWith(".$it") }) {
                val title = doc.select("meta[property=og:title]").attr("content")
                    .ifBlank { doc.title() }.ifBlank { domain }
                val description = doc.select("meta[property=og:description]").attr("content")
                return@withContext ScrapedContent(
                    title = title,
                    description = description,
                    bodyText = "",
                    domain = domain,
                    faviconUrl = "https://$domain/favicon.ico"
                )
            }

            val title = doc.title().ifBlank {
                doc.select("meta[property=og:title]").attr("content").ifBlank { domain }
            }
            val description = doc.select("meta[name=description]").attr("content").ifBlank {
                doc.select("meta[property=og:description]").attr("content")
            }

            val bodyText = extractMainContent(doc)

            val faviconUrl = doc.select("link[rel~=(?i)icon]").firstOrNull()
                ?.absUrl("href")
                ?.ifBlank { null }
                ?: "https://$domain/favicon.ico"

            ScrapedContent(
                title = title,
                description = description,
                // Return empty bodyText for JS-gated pages so no garbage summary is generated
                bodyText = if (isJsGated(bodyText)) "" else bodyText,
                domain = domain,
                faviconUrl = faviconUrl
            )
        } catch (e: Exception) {
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

    /**
     * Extract the article body text, preferring semantic content containers
     * over the full page body to avoid nav/header/ad noise.
     *
     * Priority: <article> → <main> → <p> paragraphs → cleaned body
     */
    private fun extractMainContent(doc: Document): String {
        // Strip universal noise before any extraction
        doc.select("nav, header, footer, script, style, noscript, aside, " +
                ".ad, .ads, .advertisement, .sidebar, .widget, .promo, " +
                ".related, .comments, .share, .social, #sidebar, #comments").remove()

        // 1. <article> — most authoritative semantic container
        val article = doc.select("article").firstOrNull()
        if (article != null) {
            val text = article.text().trim()
            if (text.length > 200) return text.take(BODY_TEXT_LIMIT)
        }

        // 2. <main> — second-best semantic container
        val main = doc.select("main, [role=main]").firstOrNull()
        if (main != null) {
            val text = main.text().trim()
            if (text.length > 200) return text.take(BODY_TEXT_LIMIT)
        }

        // 3. Collect <p> tags with real content (avoids one-word UI labels)
        val paragraphs = doc.select("p")
            .map { it.text().trim() }
            .filter { it.length > 60 }
            .joinToString(" ")
        if (paragraphs.length > 200) return paragraphs.take(BODY_TEXT_LIMIT)

        // 4. Last resort: cleaned body text
        return doc.body().text().take(BODY_TEXT_LIMIT)
    }

    /**
     * Returns true if the scraped text is just a JavaScript-required error page.
     * In that case we skip summary generation entirely.
     */
    private fun isJsGated(text: String): Boolean {
        val lower = text.lowercase()
        return JS_GATE_PHRASES.any { lower.contains(it) }
    }

    private fun extractDomain(url: String): String = try {
        URI(url).host?.removePrefix("www.") ?: url
    } catch (e: Exception) {
        url
    }
}
