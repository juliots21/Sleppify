package com.example.sleppify

import android.content.Context
import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Resolver de streams de YouTube usando NewPipeExtractor.
 * Simplificado: solo NewPipeExtractor, sin clientes InnerTube.
 */
object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private const val PREFS_PLAYER_STATE = "player_state"
    private const val PREF_LAST_YOUTUBE_WEB_COOKIE = "stream_last_youtube_web_cookie"
    private const val CACHE_EXPIRY_MS = 6 * 60 * 60 * 1000 // 6 horas

    private val urlCache = mutableMapOf<String, CachedUrl>()
    @Volatile
    private var authCookieHeader: String = ""

    @Volatile
    private var newPipeInitialized: Boolean = false
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
        videoId?.let { urlCache.remove(it) }
    }

    /**
     * Obtiene el bitrate objetivo basado en la preferencia de calidad de streaming.
     */
    private fun getTargetBitrate(context: Context): Int {
        val prefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE)
        val quality = prefs.getString(CloudSyncManager.KEY_STREAMING_QUALITY, CloudSyncManager.STREAMING_QUALITY_MEDIUM)
        return when (quality) {
            CloudSyncManager.STREAMING_QUALITY_LOW -> 96_000      // Baja: 96 kbps
            CloudSyncManager.STREAMING_QUALITY_HIGH -> 256_000    // Alta: 256 kbps
            CloudSyncManager.STREAMING_QUALITY_VERY_HIGH -> 320_000 // Muy alta: 320 kbps
            else -> 128_000                                       // Media: 128 kbps (default)
        }
    }

    /**
     * Resuelve URL de audio usando NewPipeExtractor.
     * (forceAlternativeClient se ignora - NewPipeExtractor maneja todo internamente)
     */
    @JvmStatic
    @JvmOverloads
    fun resolveStreamUrl(context: Context, videoId: String?, forceAlternativeClient: Boolean = false): String? {
        if (videoId.isNullOrEmpty()) return null

        // Cache check - incluir calidad en la key para cache separado por calidad
        val targetBitrate = getTargetBitrate(context)
        val cacheKey = "${videoId}_${targetBitrate}"
        urlCache[cacheKey]?.let {
            if (System.currentTimeMillis() - it.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "Cache hit: $cacheKey")
                return it.url
            }
        }

        return try {
            // Ensure NewPipe is initialized (normally pre-warmed in SleppifyApp.onCreate).
            // The null-check on getDownloader() is cheap and avoids redundant re-init.
            if (NewPipe.getDownloader() == null) {
                synchronized(newPipeInitLock) {
                    if (NewPipe.getDownloader() == null) {
                        Log.d(TAG, "Inicializando NewPipe (lazy fallback)...")
                        NewPipe.init(NewPipeHttpDownloader.getInstance())
                    }
                }
            }

            // Usar /watch evita redirect extra (youtu.be -> youtube.com)
            val info = StreamInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$videoId")
            // Seleccionar stream según la calidad preferida del usuario
            val audioStream = info.audioStreams
                .sortedBy { Math.abs(it.bitrate - targetBitrate) }
                .firstOrNull()
                ?: info.audioStreams.maxByOrNull { it.bitrate }

            val url = audioStream?.content
            if (!url.isNullOrEmpty()) {
                urlCache[cacheKey] = CachedUrl(url, System.currentTimeMillis())
                Log.d(TAG, "Éxito: $videoId - ${audioStream?.format} @ ${audioStream?.bitrate}bps (target: ${targetBitrate}bps)")
                url
            } else {
                Log.w(TAG, "No hay stream de audio: $videoId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolviendo $videoId: ${e.message}")
            null
        }
    }

    /**
     * Resuelve URL de audio usando NewPipeExtractor (versión sin context para compatibilidad).
     * Usa calidad media por defecto.
     */
    @JvmStatic
    @JvmOverloads
    fun resolveStreamUrl(videoId: String?, forceAlternativeClient: Boolean = false): String? {
        // Fallback sin context - usa 128kbps por defecto
        if (videoId.isNullOrEmpty()) return null

        return try {
            if (NewPipe.getDownloader() == null) {
                synchronized(newPipeInitLock) {
                    if (NewPipe.getDownloader() == null) {
                        NewPipe.init(NewPipeHttpDownloader.getInstance())
                    }
                }
            }

            val info = StreamInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$videoId")
            val TARGET_BITRATE = 128_000
            val audioStream = info.audioStreams
                .sortedBy { Math.abs(it.bitrate - TARGET_BITRATE) }
                .firstOrNull()
                ?: info.audioStreams.maxByOrNull { it.bitrate }

            audioStream?.content
        } catch (e: Exception) {
            Log.e(TAG, "Error resolviendo $videoId: ${e.message}")
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
