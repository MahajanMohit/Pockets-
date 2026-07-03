package com.zendeck.app.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    private const val TAG = "LinkScraperService"
    private const val BODY_TEXT_LIMIT = 4000
    private const val TIMEOUT_MS = 10_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    // Sites that render content client-side — a plain Jsoup fetch only sees a
    // shell, so we render them in a headless WebView instead (when a Context is
    // available). YouTube and X have dedicated handlers and aren't listed here.
    private val KNOWN_JS_DOMAINS = setOf(
        "instagram.com", "threads.net",
        "facebook.com", "fb.com",
        "tiktok.com",
        "reddit.com", "redd.it",
        "linkedin.com",
        "msn.com",
        "medium.com",
        "bloomberg.com",
    )

    // Phrases that indicate the page needs JavaScript to render its content.
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

    /**
     * Scrapes [url] into a [ScrapedContent].
     *
     * When [context] is provided, JS-heavy pages that a plain fetch can't read
     * are rendered in a headless WebView as a fallback — so sites like MSN,
     * Reddit and Medium still produce a real summary.
     */
    suspend fun scrape(url: String, context: Context? = null): ScrapedContent = withContext(Dispatchers.IO) {
        val domain = extractDomain(url)

        // YouTube — use oEmbed API (reliable, no auth needed)
        if (domain == "youtube.com" || domain == "youtu.be" || domain.endsWith(".youtube.com")) {
            return@withContext scrapeYouTube(url, domain)
        }

        // X / Twitter — real tweet text via the syndication API, og fallback
        if (domain == "x.com" || domain == "twitter.com" || domain.endsWith(".x.com")) {
            return@withContext scrapeXTwitter(url, domain)
        }

        // Known client-side-rendered domains: render first (Jsoup would only get boilerplate)
        if (context != null && KNOWN_JS_DOMAINS.any { domain == it || domain.endsWith(".$it") }) {
            renderAndExtract(context, url, domain)?.let { return@withContext it }
        }

        try {
            val doc = Jsoup.connect(url)
                .timeout(TIMEOUT_MS)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .followRedirects(true)
                .get()

            // Trust the URL we actually landed on: if the shared link was a
            // redirector we didn't recognise, doc.location() is the real site.
            val finalDomain = extractDomain(doc.location().ifBlank { url }).ifBlank { domain }
            val content = extractFromDocument(doc, finalDomain)

            // If the fetch couldn't produce real body text, try rendering with JS
            if (content.bodyText.isBlank() && context != null) {
                renderAndExtract(context, url, finalDomain)?.let { rendered ->
                    if (rendered.bodyText.isNotBlank()) return@withContext rendered
                    // Keep whichever has the better title/description
                    return@withContext if (rendered.title.length > content.title.length) rendered else content
                }
            }
            content
        } catch (e: Exception) {
            Log.w(TAG, "Jsoup fetch failed for $url: ${e.message}")
            // Blocked or network error — a WebView render may still succeed
            if (context != null) {
                renderAndExtract(context, url, domain)?.let { return@withContext it }
            }
            ScrapedContent(
                title = domain,
                description = "",
                bodyText = "",
                domain = domain,
                faviconUrl = "https://$domain/favicon.ico"
            )
        }
    }

    /** Renders [url] in a headless WebView and extracts content from the result. */
    private suspend fun renderAndExtract(context: Context, url: String, domain: String): ScrapedContent? {
        val html = WebViewScraper.renderHtml(context, url) ?: return null
        return try {
            val doc = Jsoup.parse(html, url)
            val content = extractFromDocument(doc, domain)
            if (content.bodyText.isNotBlank() || content.description.isNotBlank()) content else null
        } catch (e: Exception) {
            Log.w(TAG, "parse of rendered HTML failed: ${e.message}")
            null
        }
    }

    /** Pulls title, description, body text and favicon out of a parsed document. */
    private fun extractFromDocument(doc: Document, finalDomain: String): ScrapedContent {
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

        return ScrapedContent(
            title = title,
            description = description,
            bodyText = if (isJsGated(bodyText)) "" else bodyText,
            domain = finalDomain,
            faviconUrl = faviconUrl
        )
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

    /**
     * Gets the real tweet via X's public syndication endpoint (the same one the
     * embeddable-tweet widget uses — no login, no API key). Falls back to the
     * Twitterbot og: tags, then to a username-only card.
     */
    private fun scrapeXTwitter(url: String, domain: String): ScrapedContent {
        val tweetId = extractTweetId(url)
        val username = extractXUsername(url)

        // 1. Syndication API — full tweet text + author
        if (tweetId != null) {
            fetchTweetSyndication(tweetId)?.let { return it }
        }

        // 2. Twitterbot og: fallback — X still serves these to crawler UAs
        val (ogTitle, ogDescription) = try {
            val doc = Jsoup.connect(url)
                .timeout(TIMEOUT_MS)
                .userAgent("Twitterbot/1.0")
                .followRedirects(true)
                .get()
            val t = doc.select("meta[property=og:title]").attr("content")
                .ifBlank { doc.select("meta[name=twitter:title]").attr("content") }
                .ifBlank { null }
            val d = doc.select("meta[property=og:description]").attr("content")
                .ifBlank { doc.select("meta[name=twitter:description]").attr("content") }
                .ifBlank { null }
            t to d
        } catch (_: Exception) { null to null }

        val title = ogTitle
            ?: if (username != null) "Post by @$username" else "Post on X"
        return ScrapedContent(
            title = title,
            description = ogDescription ?: "",
            bodyText = "",
            domain = domain,
            faviconUrl = "https://x.com/favicon.ico"
        )
    }

    /** Calls cdn.syndication.twimg.com for a single tweet's text + author. */
    private fun fetchTweetSyndication(id: String): ScrapedContent? {
        return try {
            val token = syndicationToken(id)
            val endpoint = "https://cdn.syndication.twimg.com/tweet-result?id=$id&token=$token&lang=en"
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val body = if (conn.responseCode == 200) {
                conn.inputStream.use { it.readBytes().decodeToString() }
            } else ""
            conn.disconnect()
            if (body.isBlank()) return null

            val json = JSONObject(body)
            val text = json.optString("text").trim()
            if (text.isBlank()) return null
            val user = json.optJSONObject("user")
            val name = user?.optString("name")?.ifBlank { null }
            val screen = user?.optString("screen_name")?.ifBlank { null }

            val title = when {
                name != null && screen != null -> "$name (@$screen) on X"
                screen != null -> "@$screen on X"
                else -> "Post on X"
            }
            ScrapedContent(
                title = title,
                description = text,
                bodyText = "",
                domain = "x.com",
                faviconUrl = "https://x.com/favicon.ico"
            )
        } catch (e: Exception) {
            Log.w(TAG, "tweet syndication failed for $id: ${e.message}")
            null
        }
    }

    /**
     * Reproduces the token the syndication endpoint expects, derived from the
     * tweet id (same algorithm the official embed widget uses):
     * base-36 of `(id / 1e15) * PI`, with zeros and the dot stripped.
     */
    private fun syndicationToken(id: String): String {
        return try {
            val value = (id.toDouble() / 1e15) * Math.PI
            val sb = StringBuilder()
            val intPart = value.toLong()
            sb.append(intPart.toString(36))
            var frac = value - intPart
            sb.append('.')
            var guard = 0
            while (frac > 0.0 && guard < 24) {
                frac *= 36
                val digit = frac.toInt()
                sb.append(digit.toString(36))
                frac -= digit
                guard++
            }
            sb.toString().replace(Regex("(0+|\\.)"), "").ifBlank { "a" }
        } catch (_: Exception) {
            "a"
        }
    }

    private fun extractTweetId(url: String): String? =
        Regex("""/status(?:es)?/(\d+)""").find(url)?.groupValues?.get(1)

    private fun extractXUsername(url: String): String? = try {
        URI(url).path.split("/")
            .firstOrNull { it.isNotBlank() && it.lowercase() !in X_SYSTEM_PATHS }
    } catch (_: Exception) { null }

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
        if (text.length > 400) return false   // real content present; ignore stray mentions
        val lower = text.lowercase()
        return JS_GATE_PHRASES.any { lower.contains(it) }
    }

    private fun extractDomain(url: String): String = try {
        URI(url).host?.removePrefix("www.") ?: url
    } catch (e: Exception) {
        url
    }
}
