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
     * Resuelve URL de audio usando NewPipeExtractor.
     * (forceAlternativeClient se ignora - NewPipeExtractor maneja todo internamente)
     */
    @JvmStatic
    @JvmOverloads
    fun resolveStreamUrl(videoId: String?, forceAlternativeClient: Boolean = false): String? {
        if (videoId.isNullOrEmpty()) return null

        // Cache check
        urlCache[videoId]?.let {
            if (System.currentTimeMillis() - it.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "Cache hit: $videoId")
                return it.url
            }
        }

        return try {
            // Inicializar NewPipe una sola vez. Esto evita carreras (init duplicado)
            // y reduce trabajo extra en el primer playback.
            if (!newPipeInitialized || NewPipe.getDownloader() == null) {
                synchronized(newPipeInitLock) {
                    if (!newPipeInitialized || NewPipe.getDownloader() == null) {
                        Log.d(TAG, "Inicializando NewPipe...")
                        NewPipe.init(NewPipeHttpDownloader.getInstance())
                        newPipeInitialized = true
                    }
                }
            }

            // Usar /watch evita redirect extra (youtu.be -> youtube.com)
            val info = StreamInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$videoId")
            val audioStream = info.audioStreams.maxByOrNull { it.bitrate }

            val url = audioStream?.url
            if (!url.isNullOrEmpty()) {
                urlCache[videoId] = CachedUrl(url, System.currentTimeMillis())
                Log.d(TAG, "Éxito: $videoId - ${audioStream?.format} @ ${audioStream?.bitrate}bps")
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
