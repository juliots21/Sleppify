package com.example.sleppify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Motor DSP avanzado basado en DynamicsProcessing (API 28+).
 *
 * Proporciona un ecualizador parametrico de 9 bandas con filtros biquad IIR,
 * ajuste de graves independiente (low-shelf), y cero limitador/compresor/auto-ganancia.
 *
 * Al subir las bandas el volumen general NO se reduce.
 * Se aplica globalmente (session 0) para afectar todo el audio del sistema.
 */
class AudioEffectsService : Service() {

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var currentAudioSessionId: Int = GLOBAL_SESSION_ID
    private var isEngineActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action")

        when (action) {
            ACTION_APPLY -> {
                val sessionId = intent.getIntExtra(EXTRA_AUDIO_SESSION_ID, GLOBAL_SESSION_ID)
                applyEffects(sessionId)
            }
            ACTION_START_FOREGROUND -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_NOTIFICATION, android.app.Notification::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_NOTIFICATION)
                }
                if (notification != null && notificationId != 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(notificationId, notification)
                    }
                }
            }
            ACTION_STOP_FOREGROUND -> {
                stopForeground(intent.getBooleanExtra(EXTRA_REMOVE_NOTIFICATION, true))
            }
            ACTION_STOP -> {
                releaseEngine()
                stopSelf()
            }
            else -> {
                applyEffects(GLOBAL_SESSION_ID)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        releaseEngine()
        super.onDestroy()
    }

    // ───────────────────────────────────────────────────────────────────
    // Core DSP Engine
    // ───────────────────────────────────────────────────────────────────

    private fun applyEffects(sessionId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "DynamicsProcessing requires API 28+, current=${Build.VERSION.SDK_INT}")
            stopSelfResult(0)
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)

        if (!enabled) {
            releaseEngine()
            stopSelf()
            return
        }

        val targetSession = if (sessionId != GLOBAL_SESSION_ID) sessionId else GLOBAL_SESSION_ID

        try {
            // Rebuild engine if session changed or not active
            if (dynamicsProcessing == null || currentAudioSessionId != targetSession) {
                releaseEngine()
                dynamicsProcessing = createDynamicsProcessingEngine(targetSession)
                currentAudioSessionId = targetSession
                isEngineActive = true
                Log.d(TAG, "engine:created session=$targetSession")
            }

            applyParametersFromPrefs(prefs)
            dynamicsProcessing?.enabled = true
            Log.d(TAG, "engine:applied session=$targetSession enabled=true")

        } catch (e: Exception) {
            Log.e(TAG, "engine:error session=$targetSession", e)

            // If global session failed, try with session 0 as absolute fallback
            if (targetSession != GLOBAL_SESSION_ID) {
                try {
                    releaseEngine()
                    dynamicsProcessing = createDynamicsProcessingEngine(GLOBAL_SESSION_ID)
                    currentAudioSessionId = GLOBAL_SESSION_ID
                    isEngineActive = true
                    applyParametersFromPrefs(prefs)
                    dynamicsProcessing?.enabled = true
                    Log.d(TAG, "engine:fallback_to_global success")
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "engine:fallback_failed", fallbackError)
                    releaseEngine()
                }
            }
        }
    }

    /**
     * Creates a DynamicsProcessing engine with:
     * - 9-band pre-EQ (biquad parametric)
     * - NO multi-band compressor
     * - NO post-EQ
     * - NO limiter
     *
     * This ensures ZERO volume reduction when boosting bands.
     */
    private fun createDynamicsProcessingEngine(sessionId: Int): DynamicsProcessing {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw UnsupportedOperationException("DynamicsProcessing requires API 28+")
        }

        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount */ 2,
            /* preEqInUse */ true,
            /* preEqBandCount */ EQ_BAND_COUNT,
            /* mbcInUse */ false,       // NO compressor
            /* mbcBandCount */ 0,
            /* postEqInUse */ false,     // NO post-EQ
            /* postEqBandCount */ 0,
            /* limiterInUse */ false     // NO limiter
        )

        // Configure pre-EQ channel 0
        val preEqChannel0 = DynamicsProcessing.EqBand(true, 1000f, 0f)
        val eqConfig = DynamicsProcessing.Eq(
            true,  // inUse
            true,  // enabled
            EQ_BAND_COUNT
        )
        for (i in 0 until EQ_BAND_COUNT) {
            val freq = WAVELET_9_BAND_FREQUENCIES_HZ[i].toFloat()
            eqConfig.setBand(i, DynamicsProcessing.EqBand(true, freq, 0f))
        }
        config.setPreEqByChannelIndex(0, eqConfig)
        config.setPreEqByChannelIndex(1, eqConfig)

        // Build and configure
        val dp = DynamicsProcessing(0, sessionId, config.build())

        // Explicitly disable limiter on each channel
        for (ch in 0..1) {
            try {
                val limiter = dp.getLimiterByChannelIndex(ch)
                limiter.isEnabled = false
                dp.setLimiterByChannelIndex(ch, limiter)
            } catch (ignored: Exception) {
                // Some devices may not support limiter configuration
            }
        }

        // Set input gain to 0 dB (no pre-attenuation)
        try {
            dp.setInputGainAllChannelsTo(0f)
        } catch (ignored: Exception) {
        }


        return dp
    }

    /**
     * Reads all EQ band values, bass settings from SharedPreferences
     * and applies them to the DynamicsProcessing engine.
     *
     * NO auto-gain compensation is applied — bands boost freely.
     */
    private fun applyParametersFromPrefs(prefs: SharedPreferences) {
        val dp = dynamicsProcessing ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        // Apply 9-band EQ values
        for (bandIndex in 0 until EQ_BAND_COUNT) {
            val gainDb = prefs.getFloat(bandDbKey(bandIndex), 0f)
                .coerceIn(EQ_GAIN_MIN_DB, EQ_GAIN_MAX_DB)
            val freq = WAVELET_9_BAND_FREQUENCIES_HZ[bandIndex].toFloat()

            for (ch in 0..1) {
                try {
                    val band = dp.getPreEqBandByChannelIndex(ch, bandIndex)
                    band.cutoffFrequency = freq
                    band.gain = gainDb
                    band.isEnabled = true
                    dp.setPreEqBandByChannelIndex(ch, bandIndex, band)
                } catch (e: Exception) {
                    Log.w(TAG, "eq:set_band_failed ch=$ch band=$bandIndex", e)
                }
            }
        }

        // Apply bass boost via input gain (low-shelf simulation)
        // DynamicsProcessing doesn't have a dedicated bass shelf, so we use the
        // lowest EQ band (62.5 Hz) with additional gain from the bass tuner
        val bassDb = prefs.getFloat(KEY_BASS_DB, 0f).coerceIn(0f, BASS_DB_MAX)
        if (bassDb > 0.1f) {
            // Add bass gain to the lowest frequency bands
            val bassCutoffHz = prefs.getFloat(KEY_BASS_FREQUENCY_HZ, BASS_FREQUENCY_DEFAULT_HZ)
                .coerceIn(BASS_FREQUENCY_MIN_HZ, BASS_FREQUENCY_MAX_HZ)

            for (bandIndex in 0 until EQ_BAND_COUNT) {
                val bandFreq = WAVELET_9_BAND_FREQUENCIES_HZ[bandIndex].toFloat()
                if (bandFreq > bassCutoffHz * 2f) break

                // Scale bass contribution by how far the band is below cutoff
                val ratio = if (bandFreq <= bassCutoffHz) {
                    1.0f
                } else {
                    val logRatio = Math.log10((bassCutoffHz * 2f / bandFreq).toDouble()).toFloat()
                    logRatio.coerceIn(0f, 1f)
                }

                val additionalBassGain = bassDb * ratio
                val currentBandGain = prefs.getFloat(bandDbKey(bandIndex), 0f)
                    .coerceIn(EQ_GAIN_MIN_DB, EQ_GAIN_MAX_DB)
                val totalGain = (currentBandGain + additionalBassGain)
                    .coerceIn(EQ_GAIN_MIN_DB * 2f, EQ_GAIN_MAX_DB * 2f) // Extended range for bass

                for (ch in 0..1) {
                    try {
                        val band = dp.getPreEqBandByChannelIndex(ch, bandIndex)
                        band.gain = totalGain
                        dp.setPreEqBandByChannelIndex(ch, bandIndex, band)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }

        // Ensure NO input gain reduction — keep at 0 dB
        try {
            dp.setInputGainAllChannelsTo(0f)
        } catch (ignored: Exception) {
        }


        Log.d(TAG, "params:applied bands=9 bassDb=$bassDb")
    }

    private fun releaseEngine() {
        try {
            dynamicsProcessing?.enabled = false
            dynamicsProcessing?.release()
        } catch (e: Exception) {
            Log.w(TAG, "engine:release_error", e)
        }
        dynamicsProcessing = null
        isEngineActive = false
        currentAudioSessionId = GLOBAL_SESSION_ID
        Log.d(TAG, "engine:released")
    }

    // ───────────────────────────────────────────────────────────────────
    // Companion: Constants, keys, and utilities
    // ───────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "AudioEffectsService"
        private const val GLOBAL_SESSION_ID = 0

        const val EQ_GAIN_MIN_DB = -10f
        const val EQ_GAIN_MAX_DB = 10f
        const val BASS_DB_MAX = 5f

        const val BASS_FREQUENCY_DEFAULT_HZ = 25f
        const val BASS_FREQUENCY_MIN_HZ = 20f
        const val BASS_FREQUENCY_MAX_HZ = 250f

        @JvmField
        val WAVELET_9_BAND_FREQUENCIES_HZ = intArrayOf(62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        const val EQ_BAND_COUNT = 9

        const val ACTION_APPLY = "com.example.sleppify.action.APPLY_EFFECTS"
        const val ACTION_STOP = "com.example.sleppify.action.STOP_EFFECTS"
        const val ACTION_START_FOREGROUND = "com.example.sleppify.action.START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "com.example.sleppify.action.STOP_FOREGROUND"

        const val EXTRA_AUDIO_SESSION_ID = "extra_audio_session_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_NOTIFICATION = "extra_notification"
        const val EXTRA_REMOVE_NOTIFICATION = "extra_remove_notification"

        const val PREFS_NAME = "global_eq_prefs"

        const val KEY_ENABLED = "enabled"
        const val KEY_BASS_DB = "bass_db"
        const val KEY_BASS_FREQUENCY_HZ = "bass_frequency_hz"
        const val KEY_BASS_TYPE = "bass_type"
        const val KEY_SPATIAL_ENABLED = "spatial_enabled"
        const val KEY_SPATIAL_STRENGTH = "spatial_strength"
        const val KEY_REVERB_LEVEL = "reverb_level"

        const val SPATIAL_STRENGTH_DEFAULT = 1000

        const val REVERB_LEVEL_OFF = 0
        const val REVERB_LEVEL_LIGHT = 1
        const val REVERB_LEVEL_MEDIUM = 2
        const val REVERB_LEVEL_STRONG = 3

        const val BASS_TYPE_NATURAL = 0
        const val BASS_TYPE_TRANSIENT_COMPRESSOR = 1
        const val BASS_TYPE_SUSTAIN_COMPRESSOR = 2

        private const val KEY_BAND_DB_PREFIX = "band_db_"

        @JvmStatic
        fun bandDbKey(bandIndex: Int): String = KEY_BAND_DB_PREFIX + bandIndex

        @JvmStatic
        fun normalizeBassFrequencySliderValue(rawValue: Float): Float {
            return rawValue.coerceIn(BASS_FREQUENCY_MIN_HZ, BASS_FREQUENCY_MAX_HZ)
        }

        @JvmStatic
        fun bassSliderFromCutoffHz(cutoffHz: Float): Float {
            return cutoffHz.coerceIn(BASS_FREQUENCY_MIN_HZ, BASS_FREQUENCY_MAX_HZ)
        }

        @JvmStatic
        fun startForeground(context: Context, notificationId: Int, notification: android.app.Notification) {
            val intent = Intent(context, AudioEffectsService::class.java).apply {
                action = ACTION_START_FOREGROUND
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_NOTIFICATION, notification)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startForeground:failed", e)
            }
        }

        @JvmStatic
        fun stopForeground(context: Context, removeNotification: Boolean) {
            val intent = Intent(context, AudioEffectsService::class.java).apply {
                action = ACTION_STOP_FOREGROUND
                putExtra(EXTRA_REMOVE_NOTIFICATION, removeNotification)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "stopForeground:failed", e)
            }
        }

        /**
         * Sends an apply intent to the service, optionally with a specific audio session.
         */
        @JvmStatic
        @JvmOverloads
        fun sendApply(context: Context, audioSessionId: Int = GLOBAL_SESSION_ID) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_ENABLED, false)

            if (!enabled) {
                // Do not attempt to start foreground service if EQ is disabled.
                // Just send a stop signal to clean up if it was running.
                sendStop(context)
                return
            }

            val intent = Intent(context, AudioEffectsService::class.java).apply {
                action = ACTION_APPLY
                putExtra(EXTRA_AUDIO_SESSION_ID, audioSessionId)
            }
            try {
                // Use startService (NOT startForegroundService) because ACTION_APPLY
                // does not call startForeground(). Using startForegroundService would
                // trigger ForegroundServiceDidNotStartInTimeException after 5s,
                // crashing the entire app process.
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "sendApply:failed", e)
            }
        }

        /**
         * Sends a stop intent to the service.
         */
        @JvmStatic
        fun sendStop(context: Context) {
            val intent = Intent(context, AudioEffectsService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "sendStop:failed", e)
            }
        }
    }
}
