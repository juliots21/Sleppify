package com.example.sleppify

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Resolver de streams de YouTube via InnerTube directo.
 * Cadena: ANDROID_TESTSUITE → WEB_REMIX (auth) → WEB (auth) → ANDROID_MUSIC → IOS_MUSIC → IOS → ANDROID_VR.
 * Web clients use SAPISIDHASH auth with cookies. Mobile clients run unauthenticated.
 * WEB_REMIX is the primary client for Music tracks (needs web session cookies).
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
    private var cookieMap: Map<String, String> = emptyMap()
    

    data class CachedUrl(
        val url: String,
        val timestamp: Long
    )

    @JvmStatic
    fun loadAuthCookiesFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_PLAYER_STATE, Context.MODE_PRIVATE)
        authCookieHeader = prefs.getString(PREF_LAST_YOUTUBE_WEB_COOKIE, "")?.trim() ?: ""
        cookieMap = parseCookies(authCookieHeader)
        Log.d(TAG, "Cookies cargadas: ${authCookieHeader.isNotEmpty()} SAPISID=${cookieMap.containsKey("SAPISID")} VISITOR=${cookieMap.containsKey("VISITOR_INFO1_LIVE")} keys=${cookieMap.keys.take(10)}")
    }

    @JvmStatic
    fun setAuthCookies(cookieHeader: String?) {
        authCookieHeader = cookieHeader?.trim() ?: ""
        cookieMap = parseCookies(authCookieHeader)
        Log.d(TAG, "Cookies set: ${authCookieHeader.isNotEmpty()} SAPISID=${cookieMap.containsKey("SAPISID")}")
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

    // ----- InnerTube client definitions -----

    private data class InnerTubeClient(
        val name: String,
        val clientName: String,
        val clientVersion: String,
        val apiKey: String,
        val userAgent: String,
        val clientId: String? = null,
        val androidSdkVersion: Int? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceModel: String? = null,
        val deviceMake: String? = null,
        val platform: String? = null,
        val isEmbedded: Boolean = false,
        val useMusicEndpoint: Boolean = false,
        val isWebClient: Boolean = false
    )

    /**
     * Resolution chain:
     * 1. ANDROID_TESTSUITE — sin auth, URLs directas (YouTube regular, no Music)
     * 2. WEB_REMIX         — web client con SAPISIDHASH auth (Music tracks)
     * 3. WEB               — web client con SAPISIDHASH auth (fallback general)
     * 4. ANDROID_MUSIC     — mobile music client (sin cookies web, necesita OAuth)
     * 5. IOS_MUSIC         — fallback mobile music
     * 6. ANDROID_VR        — último recurso
     *
     * NOTE: Mobile clients (ANDROID_MUSIC, IOS_MUSIC, IOS, ANDROID_VR) do NOT receive
     * web cookies or SAPISIDHASH — those are only valid for web clients. Mobile clients
     * would need OAuth2 tokens which we don't have.
     */
    private val INNERTUBE_CLIENTS = listOf(
        InnerTubeClient(
            name = "ANDROID_TESTSUITE",
            clientName = "ANDROID_TESTSUITE",
            clientVersion = "1.9",
            clientId = "30",
            apiKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            userAgent = "com.google.android.youtube/19.29.37 (Linux; U; Android 11; en_US) gzip",
            androidSdkVersion = 30
        ),
        InnerTubeClient(
            name = "WEB_REMIX",
            clientName = "WEB_REMIX",
            clientVersion = "1.20250514.01.00",
            clientId = "67",
            apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            platform = "DESKTOP",
            useMusicEndpoint = true,
            isWebClient = true
        ),
        InnerTubeClient(
            name = "WEB",
            clientName = "WEB",
            clientVersion = "2.20250514.00.00",
            clientId = "1",
            apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            platform = "DESKTOP",
            isWebClient = true
        ),
        InnerTubeClient(
            name = "ANDROID_MUSIC",
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.29.53",
            clientId = "21",
            apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            userAgent = "com.google.android.apps.youtube.music/7.29.53 (Linux; U; Android 14; es_US) gzip",
            androidSdkVersion = 34,
            useMusicEndpoint = true
        ),
        InnerTubeClient(
            name = "IOS_MUSIC",
            clientName = "IOS_MUSIC",
            clientVersion = "7.28.0",
            clientId = "26",
            apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            userAgent = "com.google.ios.youtubemusic/7.28.0 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X; es_US)",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
            deviceModel = "iPhone16,2",
            deviceMake = "Apple",
            useMusicEndpoint = true
        ),
        InnerTubeClient(
            name = "ANDROID_VR",
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            androidSdkVersion = 32
        )
    )

    /**
     * Resuelve URL de audio directamente via InnerTube.
     */
    @JvmStatic
    @JvmOverloads
    fun resolveStreamUrl(context: Context, videoId: String?, forceAlternativeClient: Boolean = false): String? {
        if (videoId.isNullOrEmpty()) return null

        Log.d(TAG, "resolveStreamUrl: videoId=$videoId alt=$forceAlternativeClient cookies=${authCookieHeader.length} SAPISID=${cookieMap.containsKey("SAPISID")} VISITOR=${cookieMap.containsKey("VISITOR_INFO1_LIVE")}")

        val targetBitrate = getTargetBitrate(context)
        val cacheKey = "${videoId}_${targetBitrate}"
        urlCache[cacheKey]?.let {
            if (System.currentTimeMillis() - it.timestamp < CACHE_EXPIRY_MS) {
                Log.d(TAG, "Cache hit: $cacheKey")
                return it.url
            }
        }

        // Try WebView-based resolver first (has full session + PoToken context)
        if (WebViewStreamResolver.isAvailable()) {
            if (Thread.currentThread().isInterrupted) return null
            try {
                val webViewUrl = WebViewStreamResolver.resolveStreamUrl(videoId, targetBitrate)
                if (!webViewUrl.isNullOrEmpty()) {
                    urlCache[cacheKey] = CachedUrl(webViewUrl, System.currentTimeMillis())
                    Log.d(TAG, "WebView resolver éxito: $videoId")
                    return webViewUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "WebView resolver error for $videoId: ${e.message}")
            }
        } else {
            Log.d(TAG, "WebView resolver not available, falling back to InnerTube clients")
        }

        // Fallback: try InnerTube HTTP clients
        val clients = if (forceAlternativeClient) INNERTUBE_CLIENTS.drop(1) else INNERTUBE_CLIENTS
        for (client in clients) {
            if (Thread.currentThread().isInterrupted) {
                Log.w(TAG, "Thread interrupted, abortando cadena de clientes para $videoId")
                return null
            }
            try {
                val url = resolveViaInnerTube(videoId, targetBitrate, client)
                if (!url.isNullOrEmpty()) {
                    urlCache[cacheKey] = CachedUrl(url, System.currentTimeMillis())
                    Log.d(TAG, "InnerTube ${client.name} éxito: $videoId")
                    return url
                }
            } catch (e: InterruptedException) {
                Log.w(TAG, "Cadena interrumpida en ${client.name} para $videoId")
                Thread.currentThread().interrupt()
                return null
            }
        }

        Log.e(TAG, "Todos los resolvers fallaron para $videoId")
        return null
    }

    /**
     * Resuelve URL de audio (versión sin context para compatibilidad).
     * Usa calidad media por defecto.
     */
    @JvmStatic
    @JvmOverloads
    fun resolveStreamUrl(videoId: String?, forceAlternativeClient: Boolean = false): String? {
        if (videoId.isNullOrEmpty()) return null

        val targetBitrate = 128_000
        val cacheKey = "${videoId}_${targetBitrate}"
        urlCache[cacheKey]?.let {
            if (System.currentTimeMillis() - it.timestamp < CACHE_EXPIRY_MS) {
                return it.url
            }
        }

        // Try WebView-based resolver first
        if (WebViewStreamResolver.isAvailable()) {
            if (Thread.currentThread().isInterrupted) return null
            try {
                val webViewUrl = WebViewStreamResolver.resolveStreamUrl(videoId, targetBitrate)
                if (!webViewUrl.isNullOrEmpty()) {
                    urlCache[cacheKey] = CachedUrl(webViewUrl, System.currentTimeMillis())
                    return webViewUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "WebView resolver error for $videoId (sin context): ${e.message}")
            }
        }

        val clients = if (forceAlternativeClient) INNERTUBE_CLIENTS.drop(1) else INNERTUBE_CLIENTS
        for (client in clients) {
            if (Thread.currentThread().isInterrupted) {
                Log.w(TAG, "Thread interrupted, abortando cadena para $videoId (sin context)")
                return null
            }
            try {
                val url = resolveViaInnerTube(videoId, targetBitrate, client)
                if (!url.isNullOrEmpty()) {
                    urlCache[cacheKey] = CachedUrl(url, System.currentTimeMillis())
                    return url
                }
            } catch (e: InterruptedException) {
                Log.w(TAG, "Cadena interrumpida en ${client.name} para $videoId (sin context)")
                Thread.currentThread().interrupt()
                return null
            }
        }

        Log.e(TAG, "Todos los resolvers fallaron para $videoId (sin context)")
        return null
    }

    private fun resolveViaInnerTube(videoId: String, targetBitrate: Int, client: InnerTubeClient): String? {
        return try {
            val visitorData = cookieMap["VISITOR_INFO1_LIVE"] ?: ""
            val clientJson = JSONObject().apply {
                put("clientName", client.clientName)
                put("clientVersion", client.clientVersion)
                put("hl", "es")
                put("gl", "US")
                if (visitorData.isNotEmpty()) {
                    put("visitorData", visitorData)
                }
                client.androidSdkVersion?.let { put("androidSdkVersion", it) }
                client.osName?.let { put("osName", it) }
                client.osVersion?.let { put("osVersion", it) }
                client.deviceModel?.let { put("deviceModel", it) }
                client.deviceMake?.let { put("deviceMake", it) }
                client.platform?.let { put("platform", it) }
            }

            val contextJson = JSONObject().apply {
                put("client", clientJson)
                if (client.isEmbedded) {
                    put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/watch?v=$videoId"))
                }
            }

            val body = JSONObject().apply {
                put("context", contextJson)
                put("videoId", videoId)
                put("racyCheckOk", true)
                put("contentCheckOk", true)
            }.toString().toByteArray(StandardCharsets.UTF_8)

            val origin = if (client.useMusicEndpoint) "https://music.youtube.com" else "https://www.youtube.com"
            if (client.name == "WEB_REMIX") {
                Log.d(TAG, "InnerTube WEB_REMIX body=${String(body, StandardCharsets.UTF_8).take(500)}")
            }
            val endpoint = "$origin/youtubei/v1/player?key=${client.apiKey}&prettyPrint=false"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 6_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", client.userAgent)
                setRequestProperty("Origin", origin)
                setRequestProperty("Referer", "$origin/")
                setRequestProperty("X-YouTube-Client-Name", client.clientId ?: client.clientName)
                setRequestProperty("X-YouTube-Client-Version", client.clientVersion)
                if (authCookieHeader.isNotEmpty() && client.isWebClient) {
                    setRequestProperty("Cookie", authCookieHeader)
                    val sapisid = cookieMap["SAPISID"] ?: cookieMap["__Secure-3PAPISID"]
                    if (!sapisid.isNullOrEmpty()) {
                        val ts = System.currentTimeMillis() / 1000
                        val hash = sha1("$ts $sapisid $origin")
                        setRequestProperty("Authorization", "SAPISIDHASH ${ts}_${hash}")
                        setRequestProperty("X-Origin", origin)
                        Log.d(TAG, "InnerTube ${client.name}: auth SAPISIDHASH origin=$origin sapisidKey=${if (cookieMap.containsKey("SAPISID")) "SAPISID" else "__Secure-3PAPISID"}")
                    } else {
                        Log.w(TAG, "InnerTube ${client.name}: cookies present but no SAPISID found, keys=${cookieMap.keys.take(5)}")
                    }
                }
            }

            try {
                conn.outputStream.use { it.write(body) }
                val statusCode = conn.responseCode
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    val errBody = try { readResponseStream(conn).take(300) } catch (_: Exception) { "" }
                    Log.w(TAG, "InnerTube ${client.name}: HTTP $statusCode para $videoId err=$errBody")
                    return null
                }

                val responseBody = readResponseStream(conn)
                val json = JSONObject(responseBody)

                val playability = json.optJSONObject("playabilityStatus")
                val status = playability?.optString("status", "")
                if (status != "OK") {
                    val reason = playability?.optString("reason", "unknown")
                    val subreason = playability?.optJSONObject("errorScreen")
                        ?.optJSONObject("playerErrorMessageRenderer")
                        ?.optJSONObject("subreason")
                        ?.optJSONObject("runs")
                        ?.optString("text", "") ?: ""
                    val loginReq = playability?.optBoolean("loginRequired", false)
                    Log.w(TAG, "InnerTube ${client.name}: status=$status reason=$reason subreason=$subreason loginReq=$loginReq videoId=$videoId")
                    if (client.isWebClient) {
                        Log.w(TAG, "InnerTube ${client.name}: playabilityStatus=${playability.toString().take(500)}")
                    }
                    return null
                }

                val streamingData = json.optJSONObject("streamingData")
                    ?: return null.also { Log.w(TAG, "InnerTube ${client.name}: no streamingData para $videoId") }

                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                    ?: return null.also { Log.w(TAG, "InnerTube ${client.name}: no adaptiveFormats para $videoId") }

                data class AudioCandidate(val url: String, val bitrate: Int, val mimeType: String, val fromCipher: Boolean)
                val candidates = mutableListOf<AudioCandidate>()

                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                    val mimeType = fmt.optString("mimeType", "")
                    if (!mimeType.startsWith("audio/")) continue
                    var url = fmt.optString("url", "")
                    var fromCipher = false
                    if (url.isEmpty()) {
                        val cipher = fmt.optString("signatureCipher", "")
                        if (cipher.isNotEmpty()) {
                            url = extractUrlFromCipher(cipher)
                            fromCipher = true
                        }
                    }
                    if (url.isEmpty()) continue
                    val bitrate = fmt.optInt("bitrate", 0)
                    candidates.add(AudioCandidate(url, bitrate, mimeType, fromCipher))
                }
                
                // Prefer direct URLs over cipher URLs
                val directCandidates = candidates.filter { !it.fromCipher }
                val effectiveCandidates = if (directCandidates.isNotEmpty()) directCandidates else candidates

                if (candidates.isEmpty()) {
                    // Diagnostic: log what the first audio format looks like
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType", "")
                        if (!mime.startsWith("audio/")) continue
                        val hasUrl = fmt.has("url")
                        val hasCipher = fmt.has("signatureCipher")
                        val cipherSnippet = fmt.optString("signatureCipher", "").take(120)
                        val urlSnippet = fmt.optString("url", "").take(80)
                        val itag = fmt.optInt("itag", -1)
                        Log.w(TAG, "InnerTube ${client.name}: audio fmt[$i] itag=$itag hasUrl=$hasUrl hasCipher=$hasCipher mime=$mime url=$urlSnippet cipher=$cipherSnippet")
                        break
                    }
                    Log.w(TAG, "InnerTube ${client.name}: no audio streams con URL directa para $videoId")
                    return null
                }

                val best = effectiveCandidates.sortedBy { Math.abs(it.bitrate - targetBitrate) }.first()
                val srcType = if (best.fromCipher) "cipher" else "direct"
                Log.d(TAG, "InnerTube ${client.name}: $videoId - ${best.mimeType} @ ${best.bitrate}bps ($srcType, ${directCandidates.size} direct / ${candidates.size - directCandidates.size} cipher)")
                best.url
            } finally {
                conn.disconnect()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "InnerTube ${client.name} interrupted para $videoId")
            throw e
        } catch (e: java.io.InterruptedIOException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "InnerTube ${client.name} IO-interrupted para $videoId")
            throw InterruptedException("IO interrupted: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube ${client.name} error para $videoId: ${e.message}")
            null
        }
    }

    /**
     * Extracts a playable URL from a signatureCipher string.
     */
    private fun extractUrlFromCipher(cipher: String): String {
        return YouTubeSignatureDescrambler.extractUrlFromCipher(cipher)
    }

    private fun parseCookies(cookieHeader: String): Map<String, String> {
        if (cookieHeader.isEmpty()) return emptyMap()
        return cookieHeader.split(";").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq < 0) null
            else part.substring(0, eq).trim() to part.substring(eq + 1).trim()
        }.toMap()
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun readResponseStream(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
            ?: return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
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
