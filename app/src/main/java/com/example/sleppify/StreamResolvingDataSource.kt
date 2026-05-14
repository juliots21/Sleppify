package com.example.sleppify

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import java.io.IOException
import java.io.InterruptedIOException

/**
 * DataSource factory that lazily resolves InnerTube stream URLs inside ExoPlayer's
 * buffering pipeline. This eliminates the latency of resolving the URL in a separate
 * step before preparing the player.
 *
 * URI scheme: "innertube://{videoId}"
 *
 * Resolution chain:
 * 1. Check player cache (disk) — if bytes already cached, skip resolution entirely
 * 2. Resolve URL via InnertubeResolver (same InnerTube clients as before)
 * 3. Stream first CHUNK_LENGTH bytes, ExoPlayer starts playback after 500ms buffered
 *
 * Inspired by InnerTune/OuterTune open-source YouTube Music clients.
 */
@UnstableApi
object StreamResolvingDataSource {

    private const val TAG = "StreamResolving"
    const val SCHEME = "innertube"
    const val CHUNK_LENGTH = 512L * 1024L // 512KB per request chunk

    // In-memory URL cache: videoId → (url, expiresAtMs)
    private val urlCache = HashMap<String, Pair<String, Long>>()
    private const val URL_CACHE_DURATION_MS = 6 * 60 * 60 * 1000L // 6 hours

    /**
     * Creates the resolving DataSource factory.
     *
     * @param context Application context
     * @param playerCache The streaming cache (same 100MB cache used by ExoMediaPlayer)
     * @param headers Default HTTP headers (User-Agent, cookies, etc.)
     */
    @JvmStatic
    fun createFactory(
        context: Context,
        playerCache: SimpleCache,
        headers: Map<String, String>? = null
    ): DataSource.Factory {
        val appContext = context.applicationContext

        // Upstream HTTP factory
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
            .setAllowCrossProtocolRedirects(true)

        if (!headers.isNullOrEmpty()) {
            httpFactory.setDefaultRequestProperties(headers)
        }

        // Cache layer wrapping HTTP
        val cacheFactory = CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Resolving layer: intercepts innertube:// URIs and resolves to real stream URLs
        return ResolvingDataSource.Factory(cacheFactory) { dataSpec ->
            resolveDataSpec(appContext, dataSpec, playerCache)
        }
    }

    private fun resolveDataSpec(
        context: Context,
        dataSpec: DataSpec,
        playerCache: SimpleCache
    ): DataSpec {
        val uri = dataSpec.uri
        val videoId = extractVideoId(uri, dataSpec.key)
            ?: return dataSpec // Not an innertube URI, pass through

        // If data is already cached at this position, no need to resolve URL
        val requestLength = if (dataSpec.length >= 0) dataSpec.length else CHUNK_LENGTH
        if (playerCache.isCached(videoId, dataSpec.position, requestLength)) {
            Log.d(TAG, "Cache hit for $videoId at pos=${dataSpec.position}")
            return dataSpec
        }

        // Check in-memory URL cache
        val cached = urlCache[videoId]
        if (cached != null && cached.second > System.currentTimeMillis()) {
            Log.d(TAG, "URL cache hit for $videoId")
            return dataSpec
                .withUri(Uri.parse(cached.first))
                .subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
        }

        // Check if alternative client should be forced (encoded as ?alt=1 in URI)
        val forceAlt = uri.getQueryParameter("alt") == "1"

        // Resolve via InnerTube (blocks on IO thread managed by ExoPlayer)
        Log.d(TAG, "Resolving stream URL for $videoId (alt=$forceAlt)")
        val resolvedUrl = if (forceAlt) {
            InnertubeResolver.resolveStreamUrl(context, videoId, true)
        } else {
            InnertubeResolver.resolveStreamUrl(context, videoId)
        }
        if (resolvedUrl.isNullOrEmpty()) {
            Log.e(TAG, "Failed to resolve URL for $videoId")
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedIOException("Resolve interrupted for $videoId")
            }
            throw IOException("No se pudo resolver stream para $videoId")
        }

        // Cache the resolved URL
        urlCache[videoId] = resolvedUrl to (System.currentTimeMillis() + URL_CACHE_DURATION_MS)
        Log.d(TAG, "Resolved $videoId → ${resolvedUrl.take(80)}...")

        // Apply auth headers for this request
        val authHeaders = InnertubeResolver.getHeadersFor(videoId)
        val newDataSpec = dataSpec
            .withUri(Uri.parse(resolvedUrl))
            .subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)

        return if (authHeaders.isNotEmpty()) {
            newDataSpec.withAdditionalHeaders(authHeaders)
        } else {
            newDataSpec
        }
    }

    /**
     * Extracts videoId from an innertube:// URI or from the DataSpec key.
     * Returns null if this is not an innertube URI (e.g., a local file or regular HTTP URL).
     */
    private fun extractVideoId(uri: Uri, key: String?): String? {
        // Check scheme first
        if (SCHEME.equals(uri.scheme, ignoreCase = true)) {
            // URI format: innertube://videoId or innertube://videoId?alt=1
            return uri.host ?: uri.schemeSpecificPart?.substringBefore('?')
        }
        // Fall back to key if provided
        return null
    }

    /**
     * Creates an innertube URI for a given videoId.
     */
    @JvmStatic
    fun buildUri(videoId: String): Uri {
        return Uri.parse("$SCHEME://$videoId")
    }

    /**
     * Invalidates the URL cache for a videoId (used when playback fails and re-resolve needed).
     */
    @JvmStatic
    fun invalidateUrl(videoId: String) {
        urlCache.remove(videoId)
        InnertubeResolver.invalidate(videoId)
    }

    /**
     * Pre-warm the URL cache for a videoId without actually streaming data.
     * Call this for the next track in queue.
     */
    @JvmStatic
    fun prefetchUrl(context: Context, videoId: String) {
        if (urlCache[videoId]?.let { it.second > System.currentTimeMillis() } == true) return
        val url = InnertubeResolver.resolveStreamUrl(context, videoId)
        if (!url.isNullOrEmpty()) {
            urlCache[videoId] = url to (System.currentTimeMillis() + URL_CACHE_DURATION_MS)
            Log.d(TAG, "Prefetched URL for $videoId")
        }
    }
}
