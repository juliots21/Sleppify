package com.example.sleppify

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Downloads audio via sleppifydownloader.alwaysdata.net/api/download.
 *
 * The server runs yt-dlp internally and streams the audio bytes directly to the client.
 * The device never touches the YouTube CDN, so there are no 403 errors.
 * Confirmed ~8s for a 3-4MB m4a file from Peru.
 */
object SleppifyDownloaderResolver {

    private const val TAG = "SleppifyDL"
    private const val ENDPOINT = "https://sleppifydownloader.alwaysdata.net/api/download"
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 120000
    private const val MIN_VALID_BYTES = 50_000L

    /**
     * Downloads audio for [videoId] via the server proxy directly into [targetFile].
     * Returns true on success, false on any failure — caller should fall back to NewPipe.
     */
    fun downloadViaProxy(videoId: String, targetFile: File, onProgress: ((Long) -> Unit)? = null): Boolean {
        if (videoId.isBlank()) return false

        val body = JSONObject()
            .apply { put("url", "https://www.youtube.com/watch?v=$videoId") }
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        val startMs = System.currentTimeMillis()
        Log.d(TAG, "proxy_start id=$videoId")

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "audio/mp4, audio/*, */*")
                setRequestProperty("User-Agent", "Sleppify-Android/1.0")
                outputStream.use { it.write(body) }
            }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "proxy_fail id=$videoId http=$code elapsed=${System.currentTimeMillis() - startMs}ms")
                return false
            }

            targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            var totalBytes = 0L
            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buf = ByteArray(16384)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        totalBytes += n
                        onProgress?.invoke(totalBytes)
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startMs
            if (totalBytes < MIN_VALID_BYTES) {
                Log.w(TAG, "proxy_fail id=$videoId reason=too_small bytes=$totalBytes elapsed=${elapsed}ms")
                targetFile.delete()
                return false
            }

            Log.d(TAG, "proxy_ok id=$videoId bytes=$totalBytes elapsed=${elapsed}ms")
            true
        } catch (e: Exception) {
            Log.w(TAG, "proxy_exception id=$videoId reason=${e.javaClass.simpleName} msg=${e.message} elapsed=${System.currentTimeMillis() - startMs}ms")
            targetFile.delete()
            false
        } finally {
            connection?.disconnect()
        }
    }
}
