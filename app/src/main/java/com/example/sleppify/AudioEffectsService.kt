package com.example.sleppify

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Stub sin procesamiento DSP real.
 * Mantiene claves y utilidades para que frontend/presets/dispositivos sigan funcionando.
 */
class AudioEffectsService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // El backend de ecualizacion fue desactivado; no ejecutamos audio processing.
        stopSelfResult(startId)
        return START_NOT_STICKY
    }

    companion object {
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
        private const val KEY_DEBUG_EQ_BAND_AUTO_GAIN_PREFIX = "debug_eq_band_auto_gain_"
        private const val KEY_DEBUG_BASS_RANGE_AUTO_GAIN_PREFIX = "debug_bass_range_auto_gain_"

        @JvmField
        val EQ_AUTO_GAIN_DEFAULT_DB = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

        @JvmField
        val BASS_AUTO_GAIN_RANGE_MIN_HZ = floatArrayOf(20f, 40f, 63f, 100f, 160f)

        @JvmField
        val BASS_AUTO_GAIN_RANGE_MAX_HZ = floatArrayOf(40f, 63f, 100f, 160f, 250f)

        @JvmField
        val BASS_AUTO_GAIN_RANGE_DEFAULT_DB = floatArrayOf(0f, 0f, 0f, 0f, 0f)

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
        fun defaultEqBandAutoGainDb(bandIndex: Int): Float {
            return if (bandIndex in EQ_AUTO_GAIN_DEFAULT_DB.indices) {
                EQ_AUTO_GAIN_DEFAULT_DB[bandIndex]
            } else {
                0f
            }
        }

        @JvmStatic
        fun debugEqBandAutoGainKey(bandIndex: Int): String {
            return KEY_DEBUG_EQ_BAND_AUTO_GAIN_PREFIX + bandIndex
        }

        @JvmStatic
        fun bassAutoGainRangeCount(): Int = BASS_AUTO_GAIN_RANGE_MIN_HZ.size

        @JvmStatic
        fun bassAutoGainRangeMinHz(rangeIndex: Int): Float {
            return BASS_AUTO_GAIN_RANGE_MIN_HZ[rangeIndex.coerceIn(0, BASS_AUTO_GAIN_RANGE_MIN_HZ.lastIndex)]
        }

        @JvmStatic
        fun bassAutoGainRangeMaxHz(rangeIndex: Int): Float {
            return BASS_AUTO_GAIN_RANGE_MAX_HZ[rangeIndex.coerceIn(0, BASS_AUTO_GAIN_RANGE_MAX_HZ.lastIndex)]
        }

        @JvmStatic
        fun defaultBassAutoGainRangeDb(rangeIndex: Int): Float {
            return BASS_AUTO_GAIN_RANGE_DEFAULT_DB[rangeIndex.coerceIn(0, BASS_AUTO_GAIN_RANGE_DEFAULT_DB.lastIndex)]
        }

        @JvmStatic
        fun debugBassRangeAutoGainKey(rangeIndex: Int): String {
            return KEY_DEBUG_BASS_RANGE_AUTO_GAIN_PREFIX + rangeIndex
        }
    }
}
