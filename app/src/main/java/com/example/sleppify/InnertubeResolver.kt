package com.example.sleppify

import android.os.Build
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
 *  1. [Client.ANDROID] — cliente nativo, no requiere JS player ni PO token
 *     para el request de player. URLs directas sin signature cipher.
 *  2. [Client.IOS] — cliente nativo, misma ventaja que ANDROID.
 *  3. [Client.ANDROID_VR] — cliente nativo alternativo.
 *  4. [Client.TVHTML5_SIMPLY] — bypass para contenido restringido (thirdParty embed).
 *  5. [Client.WEB_REMIX] — YouTube Music web, último recurso.
 *
 * Perfiles verificados contra yt-dlp INNERTUBE_CLIENTS (_base.py).
 *
 * NOTA: PO Token (bgutil) y signature/n-param decipher NO están implementados
 * aquí. Para videos que requieren esos tokens las URLs pueden cortarse a los
 * pocos segundos; el [SongPlayerFragment] detectará el fallo, invalidará la
 * caché y reintentará con otro cliente o saltará al siguiente track.
 */
object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    // Public InnerTube key used by web/TV clients. This is not a secret.
    private const val INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    private fun playerUrlFor(client: Client): String {
        val host = if (client.useMusicHost) "music.youtube.com" else "www.youtube.com"
        return "https://$host/youtubei/v1/player?prettyPrint=false&key=$INNERTUBE_KEY"
    }

    // Caché en memoria de corto plazo para URLs resueltas
    private val urlCache = mutableMapOf<String, CachedUrl>()
    private const val CACHE_EXPIRY_MS = 6 * 60 * 60 * 1000 // 6 horas

    // Visitor ID persistente entre requests (anti-bot heuristic)
    @Volatile
    private var visitorId: String? = null

    // Perfiles de cliente InnerTube — verificados contra yt-dlp INNERTUBE_CLIENTS
    private data class Client(
        val name: String,
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val extraFields: Map<String, Any> = emptyMap(),
        val contextExtraFields: Map<String, Any> = emptyMap(),
        val useMusicHost: Boolean = false,
        val isNativeClient: Boolean = false
    ) {
        companion object {
            // ── Native clients (no JS player → direct URLs, no signature cipher) ──

            val ANDROID = Client(
                name = "android",
                clientName = "ANDROID",
                clientVersion = "21.02.35",
                userAgent = "com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip",
                extraFields = mapOf(
                    "androidSdkVersion" to 30,
                    "osName" to "Android",
                    "osVersion" to "11"
                ),
                isNativeClient = true
            )

            val IOS = Client(
                name = "ios",
                clientName = "IOS",
                clientVersion = "21.02.3",
                userAgent = "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                extraFields = mapOf(
                    "deviceMake" to "Apple",
                    "deviceModel" to "iPhone16,2",
                    "osName" to "iPhone",
                    "osVersion" to "18.3.2.22D82"
                ),
                isNativeClient = true
            )

            val ANDROID_VR = Client(
                name = "android_vr",
                clientName = "ANDROID_VR",
                clientVersion = "1.65.10",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                extraFields = mapOf(
                    "deviceMake" to "Oculus",
                    "deviceModel" to "Quest 3",
                    "androidSdkVersion" to 32,
                    "osName" to "Android",
                    "osVersion" to "12L"
                ),
                isNativeClient = true
            )

            // ── Web / TV clients ──

            val TVHTML5_SIMPLY = Client(
                name = "tvhtml5_simply",
                clientName = "TVHTML5_SIMPLY",
                clientVersion = "1.0",
                userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
                contextExtraFields = mapOf(
                    "thirdParty" to mapOf("embedUrl" to "https://www.youtube.com/")
                )
            )

            val WEB_REMIX = Client(
                name = "web_remix",
                clientName = "WEB_REMIX",
                clientVersion = "1.20260114.03.00",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
                useMusicHost = true
            )

            // ANDROID_MUSIC ahora requiere login — último recurso
            val ANDROID_MUSIC = Client(
                name = "android_music",
                clientName = "ANDROID_MUSIC",
                clientVersion = "7.02.52",
                userAgent = "com.google.android.apps.youtube.music/7.02.52 (Linux; U; Android 14; es_US; Pixel 7 Pro; Build/UQ1A.240205.002; Cronet/121.0.6167.71)",
                extraFields = mapOf(
                    "androidSdkVersion" to Build.VERSION.SDK_INT,
                    "osName" to "Android",
                    "osVersion" to (Build.VERSION.RELEASE ?: "13"),
                    "deviceMake" to (Build.MANUFACTURER ?: "Android"),
                    "deviceModel" to (Build.MODEL ?: "Android")
                ),
                useMusicHost = true,
                isNativeClient = true
            )
        }
    }

    private val PRIMARY_CLIENT_CHAIN = listOf(
        Client.ANDROID,
        Client.IOS,
        Client.ANDROID_VR,
        Client.TVHTML5_SIMPLY,
        Client.WEB_REMIX
    )
    private val ALTERNATIVE_CLIENT_CHAIN = listOf(
        Client.TVHTML5_SIMPLY,
        Client.ANDROID_VR,
        Client.IOS,
        Client.WEB_REMIX,
        Client.ANDROID_MUSIC
    )

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
            val url = URL(playerUrlFor(client))
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", client.userAgent)
                // Web/TV clients need browser-like headers; native clients don't
                if (!client.isNativeClient) {
                    val originHost = if (client.useMusicHost) "https://music.youtube.com" else "https://www.youtube.com"
                    setRequestProperty("Accept-Language", "es-US,es;q=0.9,en;q=0.8")
                    setRequestProperty("Origin", originHost)
                    setRequestProperty("Referer", "$originHost/")
                    visitorId?.let { setRequestProperty("X-Goog-Visitor-Id", it) }
                }
            }

            val locale = java.util.Locale.getDefault()
            val hl = buildString {
                val lang = locale.language
                if (!lang.isNullOrEmpty()) {
                    append(lang)
                    val country = locale.country
                    if (!country.isNullOrEmpty()) {
                        append("-")
                        append(country)
                    }
                } else {
                    append("en")
                }
            }
            val gl = locale.country?.takeIf { it.isNotEmpty() } ?: "US"

            val clientJson = JSONObject().apply {
                put("clientName", client.clientName)
                put("clientVersion", client.clientVersion)
                put("hl", hl)
                put("gl", gl)
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
                // context-level fields (e.g. thirdParty for TVHTML5_SIMPLY)
                for ((key, value) in client.contextExtraFields) {
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

            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", contextJson)
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
                val errorBody = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }
                    .getOrNull()
                    ?.take(1200)
                if (!errorBody.isNullOrEmpty()) {
                    Log.w(TAG, "performRequest: HTTP $statusCode with client=${client.name} videoId=$videoId error=$errorBody")
                } else {
                    Log.w(TAG, "performRequest: HTTP $statusCode with client=${client.name} videoId=$videoId")
                }
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
