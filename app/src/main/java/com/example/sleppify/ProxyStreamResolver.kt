package com.example.sleppify

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Resolves YouTube stream URLs via Sleppify proxy servers using round-robin with failover.
 * Returns proxy streaming URLs (the proxy handles Range/seeking server-side).
 *
 * Architecture: ExoPlayer → proxy /api/stream/<videoId> → googlevideo.com
 * The proxy resolves yt-dlp once, caches the CDN URL, and proxies bytes with Range support.
 */
object ProxyStreamResolver {

    private const val TAG = "ProxyStreamResolver"
    private const val CACHE_EXPIRY_MS = 4 * 60 * 60 * 1000L // 4 hours
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 4000

    private val STREAM_SERVERS = arrayOf(
        "https://sleppifydownload.alwaysdata.net",
        "https://sleppifydownload2.alwaysdata.net",
        "https://sleppifydownloader2.alwaysdata.net"
    )

    private data class CachedUrl(val url: String, val timestamp: Long)

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()
    private val roundRobinIndex = AtomicInteger(0)

    /**
     * Returns the streaming proxy URL for the given videoId.
     * Uses round-robin server selection with failover (health check via HEAD).
     * The returned URL supports Range requests (seeking) via the proxy server.
     */
    @JvmStatic
    fun resolveStreamUrl(videoId: String?): String? {
        if (videoId.isNullOrBlank()) return null

        // Check cache first
        urlCache[videoId]?.let {
            if (System.currentTimeMillis() - it.timestamp < CACHE_EXPIRY_MS) {
                return it.url
            } else {
                urlCache.remove(videoId)
            }
        }

        val startIndex = roundRobinIndex.getAndUpdate { (it + 1) % STREAM_SERVERS.size }

        for (attempt in 0 until STREAM_SERVERS.size) {
            val serverIndex = (startIndex + attempt) % STREAM_SERVERS.size
            val server = STREAM_SERVERS[serverIndex]
            val streamUrl = "$server/api/stream/$videoId"

            try {
                // Verify the server is reachable with a quick health check
                val healthUrl = URL("$server/api/health")
                val conn = healthUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                val code = conn.responseCode
                conn.disconnect()

                if (code != 200) {
                    Log.w(TAG, "Server $serverIndex health check failed: $code")
                    continue
                }

                // Server is alive — use this proxy URL
                urlCache[videoId] = CachedUrl(streamUrl, System.currentTimeMillis())
                Log.d(TAG, "Resolved $videoId via server $serverIndex")
                return streamUrl

            } catch (e: Exception) {
                Log.w(TAG, "Server $serverIndex unreachable for $videoId: ${e.message}")
                continue
            }
        }

        Log.e(TAG, "All servers failed for $videoId")
        return null
    }

    /**
     * Invalidates cached URL for the given videoId.
     */
    @JvmStatic
    fun invalidate(videoId: String?) {
        if (videoId.isNullOrBlank()) return
        urlCache.remove(videoId)
    }

    /**
     * Clears entire URL cache.
     */
    @JvmStatic
    fun clearCache() {
        urlCache.clear()
    }
}
