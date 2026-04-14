package com.example.sleppify

import android.text.TextUtils
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Motor de resolución para el API privado de YouTube (InnerTube).
 * Proporciona una forma ultra-ligera y rápida de obtener URLs de streaming.
 */
object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player"
    
    // Una caché simple en memoria de corto plazo para URLs resueltas (evita re-resolución inmediata)
    private val urlCache = mutableMapOf<String, CachedUrl>()
    private const val CACHE_EXPIRY_MS = 2 * 60 * 60 * 1000 // 2 horas (YouTube expira links rápido)

    data class CachedUrl(val url: String, val timestamp: Long)

    /**
     * Resuelve el enlace directo de audio.
     */
    @JvmStatic
    fun resolveStreamUrl(videoId: String?): String? {
        if (videoId.isNullOrEmpty()) return null

        // Check cache
        val cached = urlCache[videoId]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_EXPIRY_MS) {
            Log.d(TAG, "resolveStreamUrl: Usando cache para $videoId")
            return cached.url
        }

        try {
            val responseBody = performRequest(videoId) ?: return null
            val json = JSONObject(responseBody)
            
            // Buscar en streamingData.adaptiveFormats
            val streamingData = json.optJSONObject("streamingData") ?: return null
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null
            
            var bestUrl: String? = null
            var bestBitrate = -1

            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.getJSONObject(i)
                val mimeType = format.optString("mimeType", "")
                
                // Buscamos flujos de solo audio
                if (mimeType.startsWith("audio/")) {
                    val bitrate = format.optInt("bitrate", 0)
                    val url = format.optString("url")
                    
                    // Priorizamos audio/mp4 o audio/webm con bitrate alto
                    if (!url.isNullOrEmpty() && bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        bestUrl = url
                    }
                }
            }

            if (bestUrl != null) {
                urlCache[videoId] = CachedUrl(bestUrl, System.currentTimeMillis())
            }

            return bestUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error resolviendo InnerTube para $videoId: ${e.message}")
        }
        return null
    }

    private fun performRequest(videoId: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(PLAYER_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "com.google.android.apps.youtube.music/6.42.51 (Linux; U; Android 14; es_US; Pixel 7 Pro; Build/UQ1A.240205.002; Cronet/121.0.6167.71)")
            }

            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_MUSIC")
                        put("clientVersion", "6.42.51")
                        put("androidSdkVersion", 34)
                        put("hl", "es-419")
                        put("gl", "US")
                    })
                })
            }

            val os: OutputStream = connection.outputStream
            os.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            os.flush()
            os.close()

            val statusCode = connection.responseCode
            if (statusCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                response.toString()
            } else {
                Log.w(TAG, "HTTP error $statusCode al llamar a InnerTube")
                null
            }
        } finally {
            connection?.disconnect()
        }
    }
}
