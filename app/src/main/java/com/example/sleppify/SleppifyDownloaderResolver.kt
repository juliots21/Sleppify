package com.example.sleppify

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Downloads audio via two parallel Sleppify proxy servers.
 *
 * Server 0: sleppifydownloader.alwaysdata.net
 * Server 1: sleppifydownload2.alwaysdata.net
 *
 * Supports resumable downloads via HTTP Range header — partial files
 * are continued from where they left off instead of restarting.
 */
object SleppifyDownloaderResolver {

    private const val TAG = "SleppifyDL"

    private val ENDPOINTS = arrayOf(
        "https://sleppifydownloader.alwaysdata.net/api/download",
        "https://sleppifydownload2.alwaysdata.net/api/download"
    )

    const val SERVER_COUNT = 2

    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 20000
    private const val MIN_VALID_BYTES = 50_000L

    /**
     * Downloads audio for [videoId] via server [serverIndex] directly into [targetFile].
     * If [targetFile] already has partial content, attempts to resume via Range header.
     * Returns true on success, false on any failure.
     */
    fun downloadViaProxy(
        videoId: String,
        targetFile: File,
        serverIndex: Int = 0,
        onProgress: ((Long) -> Unit)? = null
    ): Boolean {
        if (videoId.isBlank()) return false
        val endpoint = ENDPOINTS[serverIndex.coerceIn(0, ENDPOINTS.size - 1)]
        val serverLabel = "s$serverIndex"

        val body = JSONObject()
            .apply { put("url", "https://www.youtube.com/watch?v=$videoId") }
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        val existingBytes = if (targetFile.isFile) targetFile.length() else 0L
        val isResume = existingBytes >= MIN_VALID_BYTES / 2

        val startMs = System.currentTimeMillis()

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "audio/mp4, audio/*, */*")
                setRequestProperty("User-Agent", "Sleppify-Android/1.0")
                if (isResume) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
                outputStream.use { it.write(body) }
            }

            val code = connection.responseCode
            val resuming = isResume && code == HttpURLConnection.HTTP_PARTIAL
            val freshStart = code == HttpURLConnection.HTTP_OK

            if (!resuming && !freshStart) {
                Log.w(TAG, "proxy_fail id=$videoId $serverLabel http=$code elapsed=${System.currentTimeMillis() - startMs}ms")
                return false
            }

            targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            var totalBytes = if (resuming) existingBytes else 0L
            if (!resuming && targetFile.isFile) {
                targetFile.delete()
            }

            val appendMode = resuming
            connection.inputStream.use { input ->
                FileOutputStream(targetFile, appendMode).use { output ->
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
                Log.w(TAG, "proxy_fail id=$videoId $serverLabel reason=too_small bytes=$totalBytes elapsed=${elapsed}ms")
                targetFile.delete()
                return false
            }

            true
        } catch (e: Exception) {
            Log.w(TAG, "proxy_exception id=$videoId $serverLabel reason=${e.javaClass.simpleName} msg=${e.message} elapsed=${System.currentTimeMillis() - startMs}ms")
            // Do NOT delete the file on exception — partial content can be resumed on next attempt
            false
        } finally {
            connection?.disconnect()
        }
    }

}
