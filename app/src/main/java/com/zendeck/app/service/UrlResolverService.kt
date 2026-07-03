package com.zendeck.app.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder

/**
 * Resolves the URL a user *meant* to share into the canonical article URL.
 *
 * Shares from the Google app, search results, and most social apps arrive as
 * wrappers — `share.google/xxxx`, `search.app/xxxx`, `google.com/url?q=…`,
 * `t.co/…` — which break scraping (we'd fetch the redirector page), dedup
 * (two shares of the same article get different wrapper URLs), and display
 * (domain shows "share.google" instead of the real site).
 *
 * Resolution steps, repeated up to [MAX_HOPS] times:
 *  1. Unwrap known parameter-wrappers locally (no network): google.com/url?q=,
 *     facebook l.php?u=, youtube.com/redirect?q=
 *  2. For known shortener/redirector domains, follow one HTTP redirect hop
 *  3. Otherwise stop
 * Finally strips tracking parameters (utm_*, fbclid, gclid, …) so the stored
 * URL is canonical.
 */
object UrlResolverService {

    private const val TAG = "UrlResolverService"
    private const val MAX_HOPS = 6
    private const val HOP_TIMEOUT_MS = 6_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /** Domains that exist only to redirect somewhere else. */
    private val REDIRECT_DOMAINS = setOf(
        // Google share/shortener family
        "share.google", "search.app", "g.co", "goo.gl", "goo.gle",
        // Google News article wrappers
        "news.google.com",
        // generic shorteners
        "t.co", "bit.ly", "tinyurl.com", "ow.ly", "buff.ly", "rb.gy",
        "is.gd", "cutt.ly", "rebrand.ly", "shorturl.at", "tiny.cc",
        // app-specific shorteners
        "lnkd.in", "fb.me", "amzn.to", "amzn.eu", "amzn.in", "a.co",
        "spoti.fi", "redd.it", "pin.it", "flip.it", "apple.news",
        // outbound-click wrappers
        "l.facebook.com", "lm.facebook.com", "l.instagram.com",
        "out.reddit.com", "t.umblr.com", "away.vk.com", "slack-redir.net"
    )

    /** Query params that are pure tracking noise — stripped for canonical URLs. */
    private val TRACKING_PARAM_PREFIXES = listOf("utm_", "vero_", "_hs", "mc_", "pk_", "piwik_")
    private val TRACKING_PARAMS = setOf(
        "fbclid", "gclid", "gbraid", "wbraid", "dclid", "msclkid", "twclid",
        "ttclid", "igsh", "igshid", "si", "mkt_tok", "ref_src", "ref_url",
        "cmpid", "ncid", "sr_share", "ss_source", "rtid", "share_id", "xtor"
    )

    suspend fun resolve(rawUrl: String): String = withContext(Dispatchers.IO) {
        var url = rawUrl.trim()
        try {
            repeat(MAX_HOPS) {
                // 1. Local unwrap of parameter-wrapper URLs (no network round-trip)
                val unwrapped = unwrapParameterWrapper(url)
                if (unwrapped != null && unwrapped != url) {
                    url = unwrapped
                    return@repeat
                }
                // 2. Network hop only for known redirector domains
                val domain = domainOf(url) ?: return@withContext stripTracking(url)
                if (domain !in REDIRECT_DOMAINS) return@withContext stripTracking(url)
                val next = followOneRedirect(url) ?: return@withContext stripTracking(url)
                url = next
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolve failed for $rawUrl: ${e.message}")
        }
        stripTracking(url)
    }

    /** Unwraps redirect URLs whose real target is carried in a query parameter. */
    private fun unwrapParameterWrapper(url: String): String? {
        val domain = domainOf(url) ?: return null
        val path = try { URI(url).path ?: "" } catch (_: Exception) { "" }

        val paramName = when {
            // https://www.google.com/url?q=<target>  (search results, Gmail, Docs)
            (domain == "google.com" || domain.endsWith(".google.com")) && path == "/url" ->
                listOf("q", "url")
            // https://l.facebook.com/l.php?u=<target>
            domain.endsWith("facebook.com") && path.startsWith("/l.php") -> listOf("u")
            // https://www.youtube.com/redirect?q=<target>  (video description links)
            domain.endsWith("youtube.com") && path == "/redirect" -> listOf("q")
            else -> return null
        }
        for (p in paramName) {
            val target = queryParam(url, p) ?: continue
            val decoded = URLDecoder.decode(target, "UTF-8")
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) return decoded
        }
        return null
    }

    /** Follows a single 3xx redirect. Returns null if the response isn't a redirect. */
    private fun followOneRedirect(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = HOP_TIMEOUT_MS
            conn.readTimeout = HOP_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: return null
                // Location may be relative — resolve against the current URL
                URI(url).resolve(location).toString()
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "redirect hop failed for $url: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Removes tracking query params and Google's #:~:text= highlight fragment. */
    internal fun stripTracking(url: String): String {
        return try {
            val uri = URI(url)
            val cleanQuery = uri.rawQuery
                ?.split("&")
                ?.filter { pair ->
                    val key = pair.substringBefore("=").lowercase()
                    key.isNotBlank() &&
                        key !in TRACKING_PARAMS &&
                        TRACKING_PARAM_PREFIXES.none { key.startsWith(it) }
                }
                ?.joinToString("&")
                ?.ifBlank { null }
            val fragment = uri.rawFragment?.takeUnless { it.startsWith(":~:") }
            buildString {
                append(uri.scheme).append("://").append(uri.rawAuthority).append(uri.rawPath ?: "")
                if (cleanQuery != null) append("?").append(cleanQuery)
                if (fragment != null) append("#").append(fragment)
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun queryParam(url: String, name: String): String? = try {
        URI(url).rawQuery
            ?.split("&")
            ?.firstOrNull { it.substringBefore("=") == name }
            ?.substringAfter("=", "")
            ?.ifBlank { null }
    } catch (_: Exception) { null }

    private fun domainOf(url: String): String? = try {
        URI(url).host?.removePrefix("www.")?.lowercase()
    } catch (_: Exception) { null }
}
