package com.example.sleppify

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
 *
 * Implementa rotación de identidad de cliente para maximizar la tasa de éxito:
 *  1. [Client.ANDROID_MUSIC] — cliente primario, mejor acceso a flujos Opus/AAC.
 *  2. [Client.TVHTML5_SIMPLYEMBEDDED] — bypass para videos que devuelven
 *     LOGIN_REQUIRED / CONTENT_CHECK_REQUIRED / age-restricted.
 *  3. [Client.ANDROID] — cliente de respaldo (sin throttling) para cuando los
 *     otros clientes fallan con FAILED_PRECONDITION.
 *
 * NOTA: PO Token (bgutil) y signature/n-param decipher NO están implementados
 * aquí. Para videos que requieren esos tokens las URLs pueden cortarse a los
 * pocos segundos; el [SongPlayerFragment] detectará el fallo, invalidará la
 * caché y reintentará con otro cliente o saltará al siguiente track.
 */
object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

    // Caché en memoria de corto plazo para URLs resueltas
    private val urlCache = mutableMapOf<String, CachedUrl>()
    private const val CACHE_EXPIRY_MS = 6 * 60 * 60 * 1000 // 6 horas

    // Visitor ID persistente entre requests (anti-bot heuristic)
    @Volatile
    private var visitorId: String? = null

    // Perfiles de cliente InnerTube
    private data class Client(
        val name: String,
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val extraFields: Map<String, Any> = emptyMap()
    ) {
        companion object {
            val ANDROID_MUSIC = Client(
                name = "android_music",
                clientName = "ANDROID_MUSIC",
                clientVersion = "7.02.52",
                userAgent = "com.google.android.apps.youtube.music/7.02.52 (Linux; U; Android 14; es_US; Pixel 7 Pro; Build/UQ1A.240205.002; Cronet/121.0.6167.71)",
                extraFields = mapOf("androidSdkVersion" to 34)
            )
            val TVHTML5_SIMPLYEMBEDDED = Client(
                name = "tvhtml5_simplyembedded",
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15",
                extraFields = mapOf(
                    "clientScreen" to "EMBED",
                    "thirdParty" to mapOf("embedUrl" to "https://www.youtube.com/")
                )
            )
            val ANDROID = Client(
                name = "android",
                clientName = "ANDROID",
                clientVersion = "19.44.38",
                userAgent = "com.google.android.youtube/19.44.38 (Linux; U; Android 14; es_US; Pixel 7 Pro; Build/UQ1A.240205.002) gzip",
                extraFields = mapOf("androidSdkVersion" to 34)
            )
        }
    }

    private val PRIMARY_CLIENT_CHAIN = listOf(Client.ANDROID_MUSIC, Client.TVHTML5_SIMPLYEMBEDDED, Client.ANDROID)
    private val ALTERNATIVE_CLIENT_CHAIN = listOf(Client.TVHTML5_SIMPLYEMBEDDED, Client.ANDROID, Client.ANDROID_MUSIC)

    data class CachedUrl(val url: String, val timestamp: Long)
    data class AudioFormat(val url: String, val bitrate: Int, val mimeType: String)

    /**
     * Invalida la caché para un videoId específico.
     */
    @JvmStatic
    fun invalidate(videoId: String?) {
        if (videoId.isNullOrEmpty()) return
        urlCache.remove(videoId)
        Log.d(TAG, "invalidate: limpiada caché para $videoId")
    }

    /**
     * Resuelve el enlace directo de audio probando la cadena primaria de clientes.
     */
    @JvmStatic
    fun resolveStreamUrl(videoId: String?): String? = resolveStreamUrl(videoId, false)

    /**
     * Resuelve el enlace directo de audio.
     *
     * @param forceAlternativeClient si true, usa la cadena que empieza con
     *   TVHTML5_SIMPLYEMBEDDED (útil tras un error del cliente primario).
     */
    @JvmStatic
    fun resolveStreamUrl(videoId: String?, forceAlternativeClient: Boolean): String? {
        if (videoId.isNullOrEmpty()) return null

        val cached = urlCache[videoId]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_EXPIRY_MS) {
            Log.d(TAG, "resolveStreamUrl: Usando cache para $videoId")
            return cached.url
        }

        val clients = if (forceAlternativeClient) ALTERNATIVE_CLIENT_CHAIN else PRIMARY_CLIENT_CHAIN
        for (client in clients) {
            val url = tryResolveWithClient(videoId, client)
            if (!url.isNullOrEmpty()) {
                urlCache[videoId] = CachedUrl(url, System.currentTimeMillis())
                Log.d(TAG, "resolveStreamUrl: success videoId=$videoId client=${client.name}")
                return url
            }
            Log.w(TAG, "resolveStreamUrl: client=${client.name} failed for $videoId, trying next")
        }

        Log.e(TAG, "resolveStreamUrl: all clients failed for $videoId")
        return null
    }

    private fun tryResolveWithClient(videoId: String, client: Client): String? {
        val responseBody = performRequest(videoId, client) ?: return null
        val json = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            Log.w(TAG, "tryResolveWithClient: invalid JSON from client=${client.name}", e)
            return null
        }

        // Actualizar visitor ID desde el responseContext si está disponible
        captureVisitorIdFromResponse(json)

        val playabilityStatus = json.optJSONObject("playabilityStatus")?.optString("status", "")
        if (!playabilityStatus.isNullOrEmpty() && playabilityStatus != "OK") {
            Log.w(TAG, "tryResolveWithClient: client=${client.name} playabilityStatus=$playabilityStatus videoId=$videoId")
            // Seguir adelante - a veces hay URLs aunque el status no sea OK
        }

        val streamingData = json.optJSONObject("streamingData") ?: return null
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            ?: streamingData.optJSONArray("formats")
            ?: return null

        val audioFormats = mutableListOf<AudioFormat>()
        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.getJSONObject(i)
            val mimeType = format.optString("mimeType", "")
            if (!mimeType.startsWith("audio/")) continue

            val bitrate = format.optInt("bitrate", 0)
            val url = format.optString("url", "")
            if (url.isNotEmpty()) {
                audioFormats.add(AudioFormat(url, bitrate, mimeType))
            }
            // Nota: si format tiene signatureCipher en vez de url, no lo podemos usar
            // sin un decipher de base.js. Lo omitimos por ahora.
        }

        if (audioFormats.isEmpty()) {
            Log.w(TAG, "tryResolveWithClient: no direct-url audio formats for $videoId via client=${client.name}")
            return null
        }

        audioFormats.sortByDescending { it.bitrate }
        val best = audioFormats.first()
        Log.d(TAG, "tryResolveWithClient: selected bitrate=${best.bitrate} mime=${best.mimeType} client=${client.name}")
        return best.url
    }

    private fun captureVisitorIdFromResponse(json: JSONObject) {
        val visitorData = json.optJSONObject("responseContext")?.optString("visitorData", "")
        if (!visitorData.isNullOrEmpty() && visitorData != visitorId) {
            visitorId = visitorData
            Log.d(TAG, "captureVisitorIdFromResponse: updated visitorId")
        }
    }

    private fun performRequest(videoId: String, client: Client): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(PLAYER_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", client.userAgent)
                setRequestProperty("Accept-Language", "es-US,es;q=0.9,en;q=0.8")
                visitorId?.let { setRequestProperty("X-Goog-Visitor-Id", it) }
            }

            val clientJson = JSONObject().apply {
                put("clientName", client.clientName)
                put("clientVersion", client.clientVersion)
                put("hl", "es-419")
                put("gl", "US")
                for ((key, value) in client.extraFields) {
                    when (value) {
                        is Map<*, *> -> {
                            val sub = JSONObject()
                            for ((k, v) in value) {
                                if (k is String) sub.put(k, v)
                            }
                            put(key, sub)
                        }
                        else -> put(key, value)
                    }
                }
            }

            val contextJson = JSONObject().apply {
                put("client", clientJson)
            }

            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", contextJson)
                // Heurística de bypass: solicitar explícitamente checks OK.
                put("racyCheckOk", true)
                put("contentCheckOk", true)
            }

            val os: OutputStream = connection.outputStream
            os.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            os.flush()
            os.close()

            val statusCode = connection.responseCode
            if (statusCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    response.toString()
                }
            } else {
                Log.w(TAG, "performRequest: HTTP $statusCode with client=${client.name} videoId=$videoId")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "performRequest: exception client=${client.name} videoId=$videoId: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
