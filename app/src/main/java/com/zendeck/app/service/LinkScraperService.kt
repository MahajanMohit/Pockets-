package com.zendeck.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

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
    // (YouTube and X have dedicated scrapers above and are not listed here)
    private val KNOWN_JS_DOMAINS = setOf(
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
        val domain = extractDomain(url)

        // YouTube — use oEmbed API (reliable, no auth needed)
        if (domain == "youtube.com" || domain == "youtu.be" || domain.endsWith(".youtube.com")) {
            return@withContext scrapeYouTube(url, domain)
        }

        // X / Twitter — extract username from URL, try og:title via nitter fallback
        if (domain == "x.com" || domain == "twitter.com" || domain.endsWith(".x.com")) {
            return@withContext scrapeXTwitter(url, domain)
        }

        try {
            val doc = Jsoup.connect(url)
                .timeout(TIMEOUT_MS)
                // Pretend to be a real browser so more sites respond
                .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .followRedirects(true)
                .get()

            // Trust the URL we actually landed on: if the shared link was a
            // redirector we didn't recognise, doc.location() is the real site.
            val finalDomain = extractDomain(doc.location().ifBlank { url })
                .ifBlank { domain }

            // Other known JS-heavy domains — Jsoup only gets homepage boilerplate, skip body
            if (KNOWN_JS_DOMAINS.any { finalDomain == it || finalDomain.endsWith(".$it") }) {
                val title = doc.select("meta[property=og:title]").attr("content")
                    .ifBlank { doc.title() }.ifBlank { finalDomain }
                val description = doc.select("meta[property=og:description]").attr("content")
                return@withContext ScrapedContent(
                    title = title,
                    description = description,
                    bodyText = "",
                    domain = finalDomain,
                    faviconUrl = "https://$finalDomain/favicon.ico"
                )
            }

            val title = doc.select("meta[property=og:title]").attr("content").ifBlank {
                doc.title()
            }.ifBlank { finalDomain }
            val description = doc.select("meta[name=description]").attr("content").ifBlank {
                doc.select("meta[property=og:description]").attr("content")
            }

            val bodyText = extractMainContent(doc)

            val faviconUrl = doc.select("link[rel~=(?i)icon]").firstOrNull()
                ?.absUrl("href")
                ?.ifBlank { null }
                ?: "https://$finalDomain/favicon.ico"

            ScrapedContent(
                title = title,
                description = description,
                // Return empty bodyText for JS-gated pages so no garbage summary is generated
                bodyText = if (isJsGated(bodyText)) "" else bodyText,
                domain = finalDomain,
                faviconUrl = faviconUrl
            )
        } catch (e: Exception) {
            ScrapedContent(
                title = domain,
                description = "",
                bodyText = "",
                domain = domain,
                faviconUrl = "https://$domain/favicon.ico"
            )
        }
    }

    /** Uses YouTube's public oEmbed API to get video title and channel name. */
    private fun scrapeYouTube(url: String, domain: String): ScrapedContent {
        return try {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val conn = URL("https://www.youtube.com/oembed?url=$encoded&format=json")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val body = if (conn.responseCode == 200) conn.inputStream.use { it.readBytes().decodeToString() } else ""
            conn.disconnect()
            val title = jsonField(body, "title")
            val author = jsonField(body, "author_name")
            ScrapedContent(
                title = title?.ifBlank { null } ?: "YouTube video",
                description = if (author != null) "by $author" else "",
                bodyText = "",
                domain = domain,
                faviconUrl = "https://www.youtube.com/favicon.ico"
            )
        } catch (e: Exception) {
            ScrapedContent("YouTube video", "", "", domain, "https://www.youtube.com/favicon.ico")
        }
    }

    // Known X system paths that are not usernames
    private val X_SYSTEM_PATHS = setOf(
        "i", "intent", "home", "explore",
        "notifications", "messages", "search", "settings", "compose"
    )

    /** Extracts tweet author from the URL path; tries og:title via a simple HTTP fetch as well. */
    private fun scrapeXTwitter(url: String, domain: String): ScrapedContent {
        // URL form: https://x.com/username/status/12345 or https://twitter.com/username/...
        val username = try {
            URI(url).path.split("/")
                .firstOrNull { it.isNotBlank() && it.lowercase() !in X_SYSTEM_PATHS }
        } catch (_: Exception) { null }

        // Try fetching og:title — X serves it to crawlers (without JS)
        val ogTitle = try {
            val doc = Jsoup.connect(url)
                .timeout(TIMEOUT_MS)
                .userAgent("Twitterbot/1.0")  // X serves og tags to crawler UA
                .followRedirects(true)
                .get()
            doc.select("meta[property=og:title]").attr("content")
                .ifBlank { doc.select("meta[name=twitter:title]").attr("content") }
                .ifBlank { null }
        } catch (_: Exception) { null }

        val title = ogTitle
            ?: if (username != null) "Post by @$username"
            else "Post on X"
        return ScrapedContent(
            title = title,
            description = "",
            bodyText = "",
            domain = domain,
            faviconUrl = "https://x.com/favicon.ico"
        )
    }

    /** Extracts a string field from a flat JSON object using regex (avoids extra deps). */
    private fun jsonField(json: String, key: String): String? =
        Regex(""""${Regex.escape(key)}"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.replace("\\/", "/")
            ?.replace("\\n", " ")
            ?.trim()

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
