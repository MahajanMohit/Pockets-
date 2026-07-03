package com.zendeck.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * Headless WebView renderer.
 *
 * Loads a URL with JavaScript enabled, waits for the page to settle, then hands
 * back the fully-rendered HTML so [LinkScraperService] can extract text from
 * sites that build their content client-side (MSN, Reddit, many news apps) —
 * pages a plain Jsoup fetch only sees as an empty shell.
 *
 * Images and media are blocked: we only need text, and skipping them keeps the
 * render fast and light.
 */
object WebViewScraper {

    private const val TAG = "WebViewScraper"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Renders [url] and returns its rendered HTML, or null on timeout/failure.
     * Must be safe to call from any thread — internally hops to the main thread
     * (WebView is a UI component).
     *
     * @param settleMs how long to wait after page load for client-side JS to
     *   populate the DOM before snapshotting.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun renderHtml(
        context: Context,
        url: String,
        timeoutMs: Long = 15_000L,
        settleMs: Long = 2_200L
    ): String? = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val main = Handler(Looper.getMainLooper())
                var webView: WebView? = WebView(context.applicationContext)
                var done = false

                fun finish(result: String?) {
                    if (done) return
                    done = true
                    val wv = webView
                    webView = null
                    main.post {
                        try {
                            wv?.stopLoading()
                            wv?.destroy()
                        } catch (_: Exception) {}
                    }
                    if (cont.isActive) cont.resume(result)
                }

                webView?.settings?.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = UA
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }

                webView?.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        // Give client-side frameworks time to populate the DOM,
                        // then snapshot the rendered HTML.
                        main.postDelayed({
                            val v = webView ?: return@postDelayed
                            try {
                                v.evaluateJavascript(
                                    "(function(){return document.documentElement.outerHTML;})();"
                                ) { encoded -> finish(decodeJsString(encoded)) }
                            } catch (e: Exception) {
                                Log.w(TAG, "evaluateJavascript failed: ${e.message}")
                                finish(null)
                            }
                        }, settleMs)
                    }
                }

                cont.invokeOnCancellation { finish(null) }

                try {
                    webView?.loadUrl(url)
                } catch (e: Exception) {
                    Log.w(TAG, "loadUrl failed: ${e.message}")
                    finish(null)
                }
            }
        }
    }

    /** evaluateJavascript hands back a JSON-encoded string literal — decode it. */
    private fun decodeJsString(encoded: String?): String? {
        if (encoded == null || encoded == "null") return null
        return try {
            // Wrap in an array so org.json unescapes \uXXXX, \", \n, … correctly
            JSONArray("[$encoded]").getString(0).ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "decode failed: ${e.message}")
            null
        }
    }
}
