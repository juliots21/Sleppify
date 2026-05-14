package com.example.sleppify

import android.util.Log

/**
 * YouTube signature cipher handler.
 *
 * YouTube's modern player (2025+) uses WASM-based obfuscation for signature
 * descrambling, which requires a full JS runtime (like Deno/Node.js) to execute.
 * This is not feasible in a lightweight Android app.
 *
 * Instead, we rely on InnerTube clients that return direct URLs (no cipher needed)
 * or authenticated sessions where the raw signature works. This class simply
 * extracts and assembles the URL from signatureCipher fields using the raw sig.
 */
object YouTubeSignatureDescrambler {
    private const val TAG = "YTSigDescrambler"

    /**
     * Extracts a playable URL from a signatureCipher string.
     * Format: s=<scrambled_sig>&sp=sig&url=<encoded_url>
     * Returns the URL with the raw signature appended.
     */
    fun extractUrlFromCipher(cipher: String): String {
        try {
            val params = mutableMapOf<String, String>()
            for (part in cipher.split("&")) {
                val eq = part.indexOf('=')
                if (eq > 0) {
                    val key = java.net.URLDecoder.decode(part.substring(0, eq), "UTF-8")
                    val value = java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8")
                    params[key] = value
                }
            }
            val url = params["url"] ?: return ""
            val sig = params["s"] ?: return url
            val sp = params["sp"] ?: "signature"
            val separator = if (url.contains("?")) "&" else "?"
            val fullUrl = "$url${separator}${sp}=${java.net.URLEncoder.encode(sig, "UTF-8")}"
            Log.d(TAG, "extractUrlFromCipher: built URL length=${fullUrl.length}")
            return fullUrl
        } catch (e: Exception) {
            Log.w(TAG, "extractUrlFromCipher: failed: ${e.message}")
            return ""
        }
    }
}
