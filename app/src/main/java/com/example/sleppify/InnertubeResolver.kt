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
    private const val CACHE_EXPIRY_MS = 6 * 60 * 60 * 1000 // 6 horas (links de YouTube duran más de lo esperado)
    
    // Retry configuration
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val INITIAL_RETRY_DELAY_MS = 500L

    data class CachedUrl(val url: String, val timestamp: Long)

    /**
     * Resuelve el enlace directo de audio con retry automático.
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

        // Retry loop with exponential backoff
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRY_ATTEMPTS) {
            try {
                val result = resolveStreamUrlInternal(videoId)
                if (result != null) {
                    return result
                }
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    val delay = INITIAL_RETRY_DELAY_MS * attempt
                    Log.d(TAG, "Retry $attempt/$MAX_RETRY_ATTEMPTS for $videoId, delay=${delay}ms")
                    Thread.sleep(delay)
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    val delay = INITIAL_RETRY_DELAY_MS * attempt
                    Log.w(TAG, "Attempt $attempt failed for $videoId: ${e.message}, retrying in ${delay}ms")
                    try {
                        Thread.sleep(delay)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
        
        Log.e(TAG, "All $MAX_RETRY_ATTEMPTS attempts failed for $videoId", lastException)
        return null
    }
    
    private fun resolveStreamUrlInternal(videoId: String): String? {
        val responseBody = performRequest(videoId) ?: return null
        val json = JSONObject(responseBody)
        
        // Buscar en streamingData.adaptiveFormats
        val streamingData = json.optJSONObject("streamingData") ?: return null
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") 
            ?: streamingData.optJSONArray("formats") 
            ?: return null
        
        // Coleccionar todos los formatos de audio disponibles
        val audioFormats = mutableListOf<AudioFormat>()

        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.getJSONObject(i)
            val mimeType = format.optString("mimeType", "")
            
            // Buscamos flujos de solo audio
            if (mimeType.startsWith("audio/")) {
                val bitrate = format.optInt("bitrate", 0)
                val url = format.optString("url", "")
                
                if (url.isNotEmpty()) {
                    audioFormats.add(AudioFormat(url, bitrate, mimeType))
                }
            }
        }

        if (audioFormats.isEmpty()) {
            Log.w(TAG, "No audio formats found for $videoId")
            return null
        }

        // Ordenar por bitrate (mayor calidad primero)
        audioFormats.sortByDescending { it.bitrate }
        
        // Intentar el mejor formato disponible
        val bestFormat = audioFormats.first()
        Log.d(TAG, "Selected audio format: bitrate=${bestFormat.bitrate}, mime=${bestFormat.mimeType} for $videoId")
        
        urlCache[videoId] = CachedUrl(bestFormat.url, System.currentTimeMillis())
        return bestFormat.url
    }
    
    data class AudioFormat(val url: String, val bitrate: Int, val mimeType: String)

    private fun performRequest(videoId: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(PLAYER_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000  // Reduced from 10000 for faster failure
                readTimeout = 12000    // Reduced from 15000 for faster failure
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
