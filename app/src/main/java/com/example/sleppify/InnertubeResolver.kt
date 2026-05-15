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

        // Fast path: InnerTube API - Try ANDROID first
        var fastUrl = fetchInnerTubeApi(videoId, forceAlternativeClient, targetBitrate, "ANDROID", "19.29.37")
        
        // If Android fails, try IOS (very stable)
        if (fastUrl.isNullOrEmpty()) {
            Log.d(TAG, "ANDROID client failed, trying IOS fallback for $videoId...")
            fastUrl = fetchInnerTubeApi(videoId, forceAlternativeClient, targetBitrate, "IOS", "19.29.1")
        }

        // Final fast fallback: TV client (very permissive)
        if (fastUrl.isNullOrEmpty()) {
            Log.d(TAG, "IOS client failed, trying TVHTML5 fallback for $videoId...")
            fastUrl = fetchInnerTubeApi(videoId, forceAlternativeClient, targetBitrate, "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.20230801.05.00")
        }

        if (!fastUrl.isNullOrEmpty()) {
            urlCache[cacheKey] = CachedUrl(fastUrl, System.currentTimeMillis())
            return fastUrl
        }

        // Slow fallback: NewPipeExtractor
        Log.w(TAG, "All Fast API clients failed for $videoId, falling back to NewPipeExtractor")
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

    private fun fetchInnerTubeApi(videoId: String, forceAlternativeClient: Boolean, targetBitrate: Int, clientName: String, clientVersion: String): String? {
        return try {
            Log.d(TAG, "fetchInnerTubeApi: connecting for $videoId (client: $clientName)...")
            val url = java.net.URL("https://www.youtube.com/youtubei/v1/player")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 2500
            connection.readTimeout = 2500
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "gzip")
            connection.setRequestProperty("X-YouTube-Client-Name", when(clientName) {
                "IOS" -> "5"
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> "30"
                else -> "3" // ANDROID
            })
            connection.setRequestProperty("X-YouTube-Client-Version", clientVersion)

            val userAgent = when (clientName) {
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36"
                "IOS" -> "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 15_6 like Mac OS X; en_US)"
                else -> "com.google.android.youtube/$clientVersion (Linux; U; Android 12; en_US) gzip"
            }

            connection.setRequestProperty("User-Agent", userAgent)
            
            if (authCookieHeader.isNotEmpty()) {
                connection.setRequestProperty("Cookie", authCookieHeader)
            }
            connection.doOutput = true

            // Use a more complete context to avoid 400 FAILED_PRECONDITION
            val body = """
                {
                  "context": {
                    "client": {
                      "clientName": "$clientName",
                      "clientVersion": "$clientVersion",
                      "hl": "es",
                      "gl": "US",
                      "osName": "Android",
                      "osVersion": "12",
                      "androidSdkVersion": 31,
                      "platform": "MOBILE"
                    },
                    "user": {
                      "lockedSafetyMode": false
                    }
                  },
                  "videoId": "$videoId",
                  "playbackContext": {
                      "contentPlaybackContext": {
                          "signatureTimestamp": 20242
                      }
                  }
                }
            """.trimIndent()

            Log.d(TAG, "fetchInnerTubeApi: sending request body for $videoId")
            connection.outputStream.use { os ->
                val input = body.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "fetchInnerTubeApi: HTTP $responseCode for $videoId")
            
            if (responseCode in 200..299) {
                val isGzip = connection.contentEncoding?.contains("gzip", true) == true
                val inputStream = if (isGzip) {
                    java.util.zip.GZIPInputStream(connection.inputStream)
                } else {
                    connection.inputStream
                }
                val response = inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "fetchInnerTubeApi: parse start for $videoId (len: ${response.length})")
                val result = parseInnerTubeResponse(response, targetBitrate)
                Log.d(TAG, "fetchInnerTubeApi: parse end, result found: ${result != null}")
                result
            } else {
                val isGzip = connection.contentEncoding?.contains("gzip", true) == true
                val errorStream = if (isGzip) {
                    connection.errorStream?.let { 
                        try { java.util.zip.GZIPInputStream(it) } catch(e: Exception) { null } 
                    }
                } else {
                    connection.errorStream
                }
                val error = errorStream?.bufferedReader()?.use { it.readText() } ?: "no error stream"
                Log.w(TAG, "InnerTube API HTTP $responseCode for $videoId: $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "InnerTube API Exception for videoId $videoId: ${e.message}")
            null
        }
    }

    private fun parseInnerTubeResponse(jsonString: String, targetBitrate: Int): String? {
        try {
            val root = org.json.JSONObject(jsonString)
            
            // Check for playability errors
            val playabilityStatus = root.optJSONObject("playabilityStatus")
            if (playabilityStatus != null) {
                val status = playabilityStatus.optString("status")
                if (status != "OK") {
                    val reason = playabilityStatus.optString("reason")
                    Log.w(TAG, "Playability Error ($status): $reason")
                    // Don't return null yet, sometimes it still has streamingData (rare)
                }
            }

            val streamingData = root.optJSONObject("streamingData") ?: return null
            
            // Collect all formats (muxed and adaptive)
            val allFormats = org.json.JSONArray()
            streamingData.optJSONArray("formats")?.let { 
                for (i in 0 until it.length()) allFormats.put(it.get(i))
            }
            streamingData.optJSONArray("adaptiveFormats")?.let {
                for (i in 0 until it.length()) allFormats.put(it.get(i))
            }

            var bestUrl: String? = null
            var bestDiff = Int.MAX_VALUE

            for (i in 0 until allFormats.length()) {
                val format = allFormats.optJSONObject(i) ?: continue
                val mimeType = format.optString("mimeType", "")
                if (!mimeType.contains("audio/")) continue

                // Handle both direct 'url' and 'signatureCipher' (though cipher needs JS which we don't have here)
                val url = format.optString("url", "")
                if (url.isEmpty()) continue

                val bitrate = format.optInt("bitrate", 0)
                val diff = Math.abs(bitrate - targetBitrate)
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestUrl = url
                }
            }
            return bestUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing InnerTube JSON: ${e.message}")
            return null
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
