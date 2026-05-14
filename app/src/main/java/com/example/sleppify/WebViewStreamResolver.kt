package com.example.sleppify

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Resolves YouTube stream URLs by leveraging a persistent WebView loaded with
 * music.youtube.com. The WebView has the full authenticated session context
 * including BotGuard/PoToken, so fetch() calls from within the page succeed
 * where raw HTTP calls do not.
 *
 * Usage:
 *   1. Call initialize(context) once on app start (posts to main thread).
 *   2. Call resolveStreamUrl(videoId) from a background thread.
 *      Returns the best audio stream URL or null.
 */
object WebViewStreamResolver {
    private const val TAG = "WebViewStreamResolver"
    private const val RESOLVE_TIMEOUT_SECONDS = 12L

    @Volatile
    private var isReady = false
    @Volatile
    private var pageLoaded = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webViewRef: WebView? = null

    // Cache resolved URLs for 5h (streams expire after 6h)
    private val cache = mutableMapOf<String, CachedStream>()
    private const val CACHE_TTL_MS = 5 * 60 * 60 * 1000L

    private data class CachedStream(val url: String, val ts: Long)

    @JvmStatic
    fun initialize(context: Context) {
        if (isReady) return
        val appContext = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) {
            createWebView(appContext)
        } else {
            mainHandler.post { createWebView(appContext) }
        }
    }

    @JvmStatic
    fun isAvailable(): Boolean = isReady && pageLoaded

    /**
     * Resolve a stream URL for the given videoId.
     * Blocking call — do NOT call from main thread.
     * Returns the audio stream URL or null on failure.
     */
    @JvmStatic
    fun resolveStreamUrl(videoId: String, targetBitrate: Int = 128000): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e(TAG, "resolveStreamUrl must not be called from main thread")
            return null
        }

        if (!isReady || !pageLoaded) {
            Log.w(TAG, "WebView not ready, skipping")
            return null
        }

        // Check cache
        cache[videoId]?.let {
            if (System.currentTimeMillis() - it.ts < CACHE_TTL_MS) {
                Log.d(TAG, "Cache hit for $videoId")
                return it.url
            }
            cache.remove(videoId)
        }

        val resultRef = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)

        // Set up the pending listener BEFORE posting JS to avoid race condition
        StreamBridge.setPending(videoId, resultRef, latch)

        // JavaScript that calls YouTube's internal player API from within the page context.
        // The page already has cookies, SAPISIDHASH, and PoToken available.
        val js = buildPlayerFetchScript(videoId, targetBitrate)

        mainHandler.post {
            try {
                webViewRef?.evaluateJavascript(js, null)
            } catch (e: Exception) {
                Log.e(TAG, "JS eval error: ${e.message}")
                latch.countDown()
            }
        }

        // Wait for the bridge callback
        val resolved = latch.await(RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val url = resultRef.get()
        if (url != null && url.startsWith("http")) {
            cache[videoId] = CachedStream(url, System.currentTimeMillis())
            Log.d(TAG, "Resolved $videoId via WebView: ${url.take(80)}")
        } else {
            val err = resultRef.get()
            Log.w(TAG, "WebView resolve failed for $videoId: ${err?.take(200) ?: "timeout"}")
        }
        return if (url != null && url.startsWith("http")) url else null
    }

    private fun buildPlayerFetchScript(videoId: String, targetBitrate: Int): String {
        // This script runs inside the music.youtube.com page context.
        // It uses the page's own ytcfg to get API key and INNERTUBE_CONTEXT,
        // then makes a fetch to /youtubei/v1/player with the page's full auth.
        return """
            (function() {
                try {
                    var cfg = window.ytcfg || {};
                    var getData = cfg.get ? cfg.get.bind(cfg) : function(k) { return cfg.data_ ? cfg.data_[k] : undefined; };
                    var apiKey = getData('INNERTUBE_API_KEY') || 'AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30';
                    var ctx = getData('INNERTUBE_CONTEXT');
                    if (!ctx) {
                        ctx = {client: {clientName: 'WEB_REMIX', clientVersion: '1.20250514.01.00', hl: 'es', gl: 'US'}};
                    }
                    var body = JSON.stringify({
                        context: ctx,
                        videoId: '$videoId',
                        racyCheckOk: true,
                        contentCheckOk: true,
                        playbackContext: {
                            contentPlaybackContext: {
                                signatureTimestamp: getData('STS') || 0
                            }
                        }
                    });
                    fetch('/youtubei/v1/player?key=' + apiKey + '&prettyPrint=false', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/json'},
                        body: body,
                        credentials: 'include'
                    })
                    .then(function(r) { return r.json(); })
                    .then(function(json) {
                        var status = json.playabilityStatus ? json.playabilityStatus.status : 'NONE';
                        if (status !== 'OK') {
                            var reason = json.playabilityStatus ? json.playabilityStatus.reason : 'unknown';
                            StreamBridge.onError('$videoId', status + ': ' + reason);
                            return;
                        }
                        var sd = json.streamingData;
                        if (!sd) { StreamBridge.onError('$videoId', 'no streamingData'); return; }
                        var formats = sd.adaptiveFormats || [];
                        var best = null;
                        var bestDiff = 999999999;
                        for (var i = 0; i < formats.length; i++) {
                            var f = formats[i];
                            if (!f.mimeType || f.mimeType.indexOf('audio/') !== 0) continue;
                            var url = f.url || '';
                            if (!url) continue;
                            var diff = Math.abs((f.bitrate || 0) - $targetBitrate);
                            if (diff < bestDiff) {
                                bestDiff = diff;
                                best = url;
                            }
                        }
                        if (best) {
                            StreamBridge.onResult('$videoId', best);
                        } else {
                            // Check if there are cipher-only formats
                            var hasCipher = false;
                            for (var i = 0; i < formats.length; i++) {
                                if (formats[i].signatureCipher && formats[i].mimeType && formats[i].mimeType.indexOf('audio/') === 0) {
                                    hasCipher = true;
                                    break;
                                }
                            }
                            StreamBridge.onError('$videoId', 'no direct audio URL (hasCipher=' + hasCipher + ', total=' + formats.length + ')');
                        }
                    })
                    .catch(function(e) {
                        StreamBridge.onError('$videoId', 'fetch error: ' + e.message);
                    });
                } catch(e) {
                    StreamBridge.onError('$videoId', 'script error: ' + e.message);
                }
            })();
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun createWebView(context: Context) {
        if (isReady) return
        try {
            val wv = WebView(context)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(wv, true)

            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.cacheMode = WebSettings.LOAD_DEFAULT
            wv.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

            wv.addJavascriptInterface(StreamBridge, "StreamBridge")

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.contains("music.youtube.com") == true) {
                        pageLoaded = true
                        Log.d(TAG, "YouTube Music page loaded: $url")
                    }
                }
            }

            // Load YouTube Music — this will use any existing cookies from CookieManager
            wv.loadUrl("https://music.youtube.com/")
            webViewRef = wv
            isReady = true
            Log.d(TAG, "WebView created, loading music.youtube.com")
        } catch (e: Exception) {
            Log.e(TAG, "WebView creation failed: ${e.message}", e)
        }
    }

    /**
     * Call this when the user re-authenticates to reload the page with new cookies.
     */
    @JvmStatic
    fun reload() {
        mainHandler.post {
            pageLoaded = false
            webViewRef?.loadUrl("https://music.youtube.com/")
        }
    }

    @JvmStatic
    fun destroy() {
        mainHandler.post {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
            isReady = false
            pageLoaded = false
        }
    }

    /**
     * JavaScript interface bridge for receiving results from the WebView.
     */
    object StreamBridge {
        private var pendingVideoId: String? = null
        private var pendingResult: AtomicReference<String?>? = null
        private var pendingLatch: CountDownLatch? = null

        @Synchronized
        fun setPending(videoId: String, result: AtomicReference<String?>, latch: CountDownLatch) {
            pendingVideoId = videoId
            pendingResult = result
            pendingLatch = latch
        }

        @Synchronized
        @JavascriptInterface
        fun onResult(videoId: String, url: String) {
            Log.d(TAG, "StreamBridge.onResult: $videoId url=${url.take(60)}")
            if (videoId == pendingVideoId) {
                pendingResult?.set(url)
                pendingLatch?.countDown()
            }
        }

        @Synchronized
        @JavascriptInterface
        fun onError(videoId: String, error: String) {
            Log.w(TAG, "StreamBridge.onError: $videoId err=$error")
            if (videoId == pendingVideoId) {
                pendingResult?.set("ERROR:$error")
                pendingLatch?.countDown()
            }
        }
    }
}
