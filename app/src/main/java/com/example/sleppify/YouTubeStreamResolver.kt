package com.example.sleppify

import android.util.Log

/**
 * Clase de utilidad para resolver URLs de streaming directo de YouTube.
 * Ahora utiliza InnertubeResolver para una velocidad óptima, eliminando el scraping de NewPipe.
 */
object YouTubeStreamResolver {

    private const val TAG = "YouTubeStreamResolver"

    /**
     * Resuelve el enlace directo de audio para un video de YouTube.
     * Este método es bloqueante y debe llamarse desde un hilo secundario.
     */
    @JvmStatic
    fun resolveStreamUrl(videoId: String?): String? {
        if (videoId.isNullOrEmpty()) return null

        Log.d(TAG, "resolveStreamUrl: Iniciando resolución vía InnerTube para $videoId")
        
        // Usamos el nuevo motor InnerTube (Kotlin)
        val streamUrl = InnertubeResolver.resolveStreamUrl(videoId)
        
        if (!streamUrl.isNullOrEmpty()) {
            Log.d(TAG, "resolveStreamUrl: URL resuelta exitosamente")
            return streamUrl
        }

        Log.w(TAG, "resolveStreamUrl: No se pudo resolver la URL mediante InnerTube para $videoId")
        return null
    }
}
