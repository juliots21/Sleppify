package com.example.sleppify

import android.content.Context
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer

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

    @Volatile
    private var monoProcessor: MonoAudioProcessor? = null

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
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build()

                        // Aggressive LoadControl: start playback after only 500ms of
                        // buffered audio instead of the default 2500ms.  Keep a
                        // reasonable back-buffer so seeks within recently-played
                        // sections are instant.
                        val loadControl = DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                /* minBufferMs */            15_000,
                                /* maxBufferMs */            50_000,
                                /* bufferForPlaybackMs */       500,
                                /* bufferForPlaybackAfterRebufferMs */ 1_000
                            )
                            .setBackBuffer(
                                /* backBufferDurationMs */ 30_000,
                                /* retainBackBufferFromKeyframe */ true
                            )
                            .setPrioritizeTimeOverSizeThresholds(true)
                            .build()

                        monoProcessor = MonoAudioProcessor()

                        val audioSink = DefaultAudioSink.Builder(appContext)
                            .setAudioProcessors(arrayOf(monoProcessor!!))
                            .build()

                        val renderersFactory = object : DefaultRenderersFactory(appContext) {
                            override fun buildAudioSink(
                                context: Context,
                                enableFloatOutput: Boolean,
                                enableAudioTrackPlaybackParams: Boolean
                            ) = audioSink
                        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

                        sharedExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
                            .setLoadControl(loadControl)
                            .setLooper(android.os.Looper.getMainLooper())
                            .setAudioAttributes(audioAttributes, true)
                            .setHandleAudioBecomingNoisy(true)
                            .build()
                        initialized = true
                        Log.d(TAG, "ExoPlayer compartido inicializado con AudioFocus y Noisy handling")
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
                sharedExoPlayer?.let { player ->
                    // Stop + clear before release to ensure AudioTrack resources are freed
                    // immediately. On low-memory devices, release() alone may leave AudioFlinger
                    // resources in a stale state causing -12 (ENOMEM) on the next AudioTrack init.
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                }
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
