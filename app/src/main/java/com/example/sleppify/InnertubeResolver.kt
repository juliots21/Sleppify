package com.example.sleppify

import android.content.Context
import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolver de streams de YouTube usando NewPipeExtractor.
 * Simplificado: solo NewPipeExtractor, sin clientes InnerTube manuales.
 */
object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private const val PREFS_PLAYER_STATE = "player_state"
    private const val PREF_LAST_YOUTUBE_WEB_COOKIE = "stream_last_youtube_web_cookie"
    private const val CACHE_EXPIRY_MS = 6 * 60 * 60 * 1000 // 6 horas

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()
    @Volatile
    private var authCookieHeader: String = ""

    private val newPipeInitLock = Any()

    data class CachedUrl(
        val url: String,
        val timestamp: Long
    )

    @JvmStatic
    fun loadAuthCookiesFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_PLAYER_STATE, Context.MODE_PRIVATE)
        authCookieHeader = prefs.getString(PREF_LAST_YOUTUBE_WEB_COOKIE, "")?.trim() ?: ""
        Log.d(TAG, "Cookies cargadas: ${authCookieHeader.isNotEmpty()}")
    }

    @JvmStatic
    fun setAuthCookies(cookieHeader: String?) {
        authCookieHeader = cookieHeader?.trim() ?: ""
        Log.d(TAG, "Cookies set: ${authCookieHeader.isNotEmpty()}")
    }

    @JvmStatic
    fun getAuthCookieHeader(): String = authCookieHeader

    @JvmStatic
    fun invalidate(videoId: String?) {
        if (videoId.isNullOrEmpty()) return
        val keys = urlCache.keys.filter { it.startsWith("${videoId}_") }
        keys.forEach { urlCache.remove(it) }
    }

    /**
     * Obtiene el bitrate objetivo basado en la preferencia de calidad de streaming.
     */
    private fun getTargetBitrate(context: Context): Int {
        val prefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
        val quality = prefs.getString(CloudSyncManager.KEY_STREAMING_QUALITY, CloudSyncManager.STREAMING_QUALITY_MEDIUM)
        return when (quality) {
            CloudSyncManager.STREAMING_QUALITY_LOW -> 96_000
            CloudSyncManager.STREAMING_QUALITY_HIGH -> 256_000
            CloudSyncManager.STREAMING_QUALITY_VERY_HIGH -> 320_000
            else -> 128_000
        }
    }

    private fun ensureNewPipe() {
        if (NewPipe.getDownloader() == null) {
            synchronized(newPipeInitLock) {
                if (NewPipe.getDownloader() == null) {
                    Log.d(TAG, "Inicializando NewPipe (lazy fallback)...")
                    NewPipe.init(NewPipeHttpDownloader.getInstance())
                }
            }
        }
    }

    /**
     * Resuelve URL de audio usando NewPipeExtractor.
     */
    @JvmStatic
    @JvmOverloads
    fun resolveStreamUrl(context: Context, videoId: String?, forceAlternativeClient: Boolean = false): String? {
        if (videoId.isNullOrEmpty()) return null

        val targetBitrate = getTargetBitrate(context)
        val cacheKey = "${videoId}_${targetBitrate}"
        urlCache[cacheKey]?.let {
            if (System.currentTimeMillis() - it.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "Cache hit: $cacheKey")
                return it.url
            }
        }

        Log.d(TAG, "resolving via NewPipe: $videoId")
        return try {
            ensureNewPipe()
            val info = StreamInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$videoId")
            val audioStream = info.audioStreams
                .sortedBy { Math.abs(it.bitrate - targetBitrate) }
                .firstOrNull()
                ?: info.audioStreams.maxByOrNull { it.bitrate }

            val url = audioStream?.content
            if (!url.isNullOrEmpty()) {
                urlCache[cacheKey] = CachedUrl(url, System.currentTimeMillis())
                Log.d(TAG, "NewPipe fallback success: $videoId - ${audioStream?.format} @ ${audioStream?.bitrate}bps")
                url
            } else {
                Log.w(TAG, "No audio stream via NewPipe for: $videoId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving via NewPipe $videoId: ${e.message}")
            null
        }
    }

    /**
     * Headers mínimos para reproducir streams.
     */
    @JvmStatic
    fun getHeadersFor(videoId: String?): Map<String, String> {
        return if (authCookieHeader.isNotEmpty()) {
            mapOf("Cookie" to authCookieHeader)
        } else {
            emptyMap()
        }
    }
}
