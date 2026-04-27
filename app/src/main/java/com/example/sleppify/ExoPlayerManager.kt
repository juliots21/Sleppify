package com.example.sleppify

import android.content.Context
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Singleton manager para una instancia compartida de ExoPlayer que reduce la latencia de inicio.
 * Pre-inicializa ExoPlayer al iniciar la app y lo proporciona a las instancias de ExoMediaPlayer.
 */
@UnstableApi
object ExoPlayerManager {

    private const val TAG = "ExoPlayerManager"

    @Volatile
    private var initialized = false

    @Volatile
    private var sharedExoPlayer: ExoPlayer? = null

    private val initLock = Any()

    /**
     * Inicializa la instancia compartida de ExoPlayer. Debe llamarse temprano al iniciar la app.
     */
    fun initialize(@NonNull context: Context) {
        if (!initialized) {
            synchronized(initLock) {
                if (!initialized) {
                    try {
                        val appContext = context.applicationContext
                        sharedExoPlayer = ExoPlayer.Builder(appContext)
                            .setLooper(android.os.Looper.getMainLooper())
                            .build()
                        initialized = true
                        Log.d(TAG, "ExoPlayer compartido inicializado exitosamente con main thread handler")
                    } catch (e: Exception) {
                        Log.e(TAG, "Falló la inicialización del ExoPlayer compartido", e)
                    }
                }
            }
        }
    }

    /**
     * Obtiene la instancia compartida de ExoPlayer.
     * Retorna null si la inicialización falló o no ha sido llamada aún.
     */
    @Nullable
    fun getSharedExoPlayer(): ExoPlayer? {
        return sharedExoPlayer
    }

    /**
     * Reinicializa el ExoPlayer compartido si está en estado inválido.
     * Útil cuando hay problemas con handlers de threads muertos.
     */
    fun reinitialize(@NonNull context: Context) {
        synchronized(initLock) {
            try {
                sharedExoPlayer?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error liberando ExoPlayer durante reinitialización", e)
            }
            sharedExoPlayer = null
            initialized = false
            initialize(context)
            Log.d(TAG, "ExoPlayer compartido reinicializado")
        }
    }

    /**
     * Verifica si el ExoPlayer compartido está inicializado.
     */
    fun isInitialized(): Boolean = initialized && sharedExoPlayer != null

    /**
     * Libera la instancia compartida de ExoPlayer. Llamar esto cuando la app se está cerrando.
     */
    fun release() {
        synchronized(initLock) {
            sharedExoPlayer?.release()
            sharedExoPlayer = null
            initialized = false
            Log.d(TAG, "ExoPlayer compartido liberado")
        }
    }
}
