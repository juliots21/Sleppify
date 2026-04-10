package com.example.sleppify

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class NewPipeHttpDownloader private constructor() : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        var connection: HttpURLConnection? = null
        return try {
            connection = openConnection(request)
            val statusCode = connection.responseCode
            if (statusCode == 429) {
                throw ReCaptchaException("Too many requests", request.url())
            }

            val body = readBody(connection, statusCode)
            val statusMessage = connection.responseMessage ?: ""
            val latestUrl = connection.url?.toString() ?: request.url()

            Response(
                statusCode,
                statusMessage,
                sanitizeHeaders(connection.headerFields),
                body,
                latestUrl
            )
        } finally {
            connection?.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun openConnection(request: Request): HttpURLConnection {
        val url = URL(request.url())
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }

        for ((name, values) in request.headers()) {
            if (name == null || values.isNullOrEmpty()) {
                continue
            }

            var first = true
            for (value in values) {
                if (value == null) {
                    continue
                }
                if (first) {
                    connection.setRequestProperty(name, value)
                    first = false
                } else {
                    connection.addRequestProperty(name, value)
                }
            }
        }

        val dataToSend = request.dataToSend()
        if (dataToSend != null && dataToSend.isNotEmpty()) {
            connection.doOutput = true
            connection.outputStream.use { outputStream: OutputStream ->
                outputStream.write(dataToSend)
                outputStream.flush()
            }
        } else if (requiresRequestBody(request.httpMethod())) {
            connection.doOutput = true
            connection.outputStream.use { outputStream: OutputStream ->
                outputStream.write(byteArrayOf())
                outputStream.flush()
            }
        }

        return connection
    }

    private fun requiresRequestBody(method: String?): Boolean {
        if (method == null) {
            return false
        }

        return method.equals("POST", ignoreCase = true)
            || method.equals("PUT", ignoreCase = true)
            || method.equals("PATCH", ignoreCase = true)
    }

    @Throws(IOException::class)
    private fun readBody(connection: HttpURLConnection, statusCode: Int): String {
        val raw = if (statusCode >= 400) connection.errorStream else connection.inputStream
        if (raw == null) {
            return ""
        }

        BufferedInputStream(raw).use { input ->
            ByteArrayOutputStream().use { out ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) {
                        break
                    }
                    out.write(buffer, 0, read)
                }
                return out.toString(StandardCharsets.UTF_8.name())
            }
        }
    }

    private fun sanitizeHeaders(headers: Map<String?, List<String>?>?): Map<String, List<String>> {
        if (headers.isNullOrEmpty()) {
            return emptyMap()
        }

        val result = LinkedHashMap<String, List<String>>()
        for ((key, value) in headers) {
            if (key == null || value == null) {
                continue
            }
            result[key] = value
        }
        return result
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 30000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        @JvmField
        val INSTANCE: NewPipeHttpDownloader = NewPipeHttpDownloader()

        @JvmStatic
        fun getInstance(): NewPipeHttpDownloader = INSTANCE
    }
}
