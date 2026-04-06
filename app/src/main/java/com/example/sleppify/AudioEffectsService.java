package com.example.sleppify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.EnvironmentalReverb;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class AudioEffectsService extends Service {
    private static final String TAG = "AudioEffectsService";
    private static final String CHANNEL_ID = "global_eq_channel";
    private static final int NOTIFICATION_ID = 1001;
    public static final float EQ_GAIN_MIN_DB = -10f;
    public static final float EQ_GAIN_MAX_DB = 10f;
    private static final float MIN_EQ_DB = EQ_GAIN_MIN_DB;
    private static final float MAX_EQ_DB = EQ_GAIN_MAX_DB;
    public static final float BASS_DB_MAX = 5f;
    public static final float BASS_FREQUENCY_DEFAULT_HZ = 25f;
    public static final float BASS_FREQUENCY_MIN_HZ = 20f;
    public static final float BASS_FREQUENCY_MAX_HZ = 250f;
    private static final float BASS_TUNER_CUTOFF_MIN_HZ = 20f;
    private static final float BASS_TUNER_CUTOFF_MAX_HZ = 250f;
    public static final int[] WAVELET_9_BAND_FREQUENCIES_HZ = {62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    public static final int EQ_BAND_COUNT = WAVELET_9_BAND_FREQUENCIES_HZ.length;
    private static final int LOW_EDGE_FREQUENCY_HZ = 20;
    private static final int HIGH_EDGE_FREQUENCY_HZ = 20000;
    private static final float EDGE_EXTENSION_FACTOR = 1f;
    private static final float BASS_TUNER_ACTIVE_BELOW_OCTAVES = 0.36f;
    private static final float BASS_TUNER_ACTIVE_ABOVE_OCTAVES = 0.28f;
    private static final float BASS_TUNER_CUTOFF_BASE_WEIGHT = 0.58f;
    private static final float BASS_TUNER_PUNCH_CENTER_RATIO = 0.88f;
    private static final float BASS_TUNER_PUNCH_WIDTH_OCTAVES = 0.36f;
    private static final float BASS_TUNER_PUNCH_INTENSITY = 0.22f;
    private static final float BASS_TUNER_MAX_BOOST_MULTIPLIER = 1.08f;
    private static final float BASS_TUNER_GLOBAL_LIFT_FACTOR = 0.40f;
    private static final float BASS_TUNER_OFF_CURVE_RECOVERY_FACTOR = 0.52f;
    private static final float BASS_TUNER_OUTSIDE_FALLOFF_OCTAVES = 0.72f;
    private static final float BASS_TUNER_MAX_COMBINED_BOOST_DB = 6.9f;
    private static final float BASS_TUNER_MID_HIGH_PRESERVE_FROM_HZ = 250f;
    private static final float BASS_TUNER_MID_HIGH_MIN_LIFT_FACTOR = 0.68f;
    private static final float BASS_TUNER_MID_HIGH_ASSIST_FACTOR = 0.26f;
    private static final float BASS_TUNER_MID_HIGH_ASSIST_FALLOFF = 0.30f;
    private static final float BASS_TUNER_MID_HIGH_ASSIST_SPAN_OCTAVES = 5.2f;
    private static final boolean FORCE_BASS_TUNER_NO_ATTENUATION = true;
    private static final boolean FORCE_LOCALIZED_BAND_SHAPES = true;
    private static final float LOCALIZED_BAND_WIDTH_LOW_OCTAVES = 0.46f;
    private static final float LOCALIZED_BAND_WIDTH_250_OCTAVES = 0.30f;
    private static final float LOCALIZED_BAND_WIDTH_MID_OCTAVES = 0.40f;
    private static final float LOCALIZED_BAND_WIDTH_HIGH_OCTAVES = 0.34f;
    private static final float LOCALIZED_BAND_WIDTH_AIR_OCTAVES = 0.30f;
    private static final float SUB_BASS_PROTECTION_MAX_HZ = 62.5f;
        private static final boolean USE_BIQUAD_GRAPHIC_EQ_ENGINE = true;
        private static final boolean USE_WAVELET_AKIMA_GRAPHIC_EQ = true;
        private static final double[] WAVELET_GRAPHIC_EQ_SPLINE_X = {
            0d,
            62.5d,
            125d,
            250d,
            500d,
            1000d,
            2000d,
            4000d,
            8000d,
            16000d,
            24000d
        };
    private static final boolean FORCE_GRAPHIC_EQ_FULL_SPECTRUM = false;
    private static final float GRAPHIC_EQ_REFERENCE_SAMPLE_RATE_HZ = 48000f;
    private static final float GRAPHIC_EQ_BIQUAD_Q = 2.30f;
    private static final float GRAPHIC_EQ_BIQUAD_Q_MIN = 0.82f;
    private static final float GRAPHIC_EQ_BIQUAD_GAIN_WIDENING = 0.24f;
    private static final float GRAPHIC_EQ_BIQUAD_SPREAD_BLEND = 0.26f;
    private static final float GRAPHIC_EQ_BIQUAD_SPREAD_HIGH_EXTRA = 0.30f;
    private static final float GRAPHIC_EQ_BIQUAD_AIR_SPREAD_CENTER_HZ = 11000f;
    private static final float GRAPHIC_EQ_BIQUAD_AIR_SPREAD_WIDTH_OCTAVES = 1.75f;
    private static final float GRAPHIC_EQ_BIQUAD_AIR_SPREAD_DRIVE_RATIO = 0.34f;
    private static final float GRAPHIC_EQ_BIQUAD_AIR_SPREAD_START_HZ = 3500f;
    private static final float GRAPHIC_EQ_BIQUAD_MIN_MAGNITUDE = 0.00001f;
    private static final float GRAPHIC_EQ_NEIGHBOR_BLEND = 0.14f;
    private static final float GRAPHIC_EQ_NEIGHBOR_WIDTH_OCTAVES = 0.95f;
    private static final float GRAPHIC_EQ_CENTER_BLEND = 0.10f;
    private static final float GRAPHIC_EQ_CENTER_WIDTH_OCTAVES = 0.30f;
    private static final float GRAPHIC_EQ_ANCHOR_LOCK_WIDTH_OCTAVES = 0.18f;
    private static final float GRAPHIC_EQ_ULTRA_LOW_RELIEF_FLOOR = 1.0f;
    private static final float GRAPHIC_EQ_PRESENCE_CENTER_HZ = 3200f;
    private static final float GRAPHIC_EQ_PRESENCE_WIDTH_OCTAVES = 1.1f;
    private static final float GRAPHIC_EQ_AIR_CENTER_HZ = 9200f;
    private static final float GRAPHIC_EQ_AIR_WIDTH_OCTAVES = 1.15f;
    private static final float GRAPHIC_EQ_PRESENCE_LIFT_MAX_DB = 0.30f;
    private static final float GRAPHIC_EQ_AIR_LIFT_MAX_DB = 0.20f;
    private static final boolean FORCE_NO_DUCKING_GAIN_COMPENSATION = true;
    private static final float NO_DUCKING_EQ_PEAK_GAIN_FACTOR = 4.35f;
    private static final float NO_DUCKING_EQ_AVG_GAIN_FACTOR = 2.75f;
    private static final float NO_DUCKING_GAIN_MAX_DB = 18.8f;
    private static final float NO_DUCKING_EQ_MAKEUP_FLOOR_DB = 0.9f;
    private static final float NO_DUCKING_EQ_MAKEUP_CAP_DB = 14.8f;
    private static final float NO_DUCKING_EQ_EXTRA_AUTOGAIN_DB = -2f;
    private static final float BASS_TUNER_DYNAMIC_AUTOGAIN_MAX_DB = 7.0f;
    private static final float BASS_TUNER_LOW_CUTOFF_EXTRA_AUTOGAIN_DB = 3.5f;
    private static final float BASS_TUNER_LOW_CUTOFF_BOOST_MAX_HZ = 75f;
    private static final float BASS_TUNER_LOW_CUTOFF_BOOST_END_HZ = 125f;
    private static final float BASS_TUNER_AUTOGAIN_CUTOFF_1_HZ = 80f;
    private static final float BASS_TUNER_AUTOGAIN_CUTOFF_2_HZ = 60f;
    private static final float BASS_TUNER_AUTOGAIN_CUTOFF_3_HZ = 40f;
    private static final float BASS_TUNER_AUTOGAIN_REDUCTION_1_DB = 2.0f;
    private static final float BASS_TUNER_AUTOGAIN_REDUCTION_2_DB = 4.0f;
    private static final float BASS_TUNER_AUTOGAIN_REDUCTION_3_DB = 6.0f;
    private static final float BASS_TUNER_AUTOGAIN_HIGH_REDUCTION_STEP_1_HZ = 70f;
    private static final float BASS_TUNER_AUTOGAIN_HIGH_REDUCTION_STEP_2_HZ = 120f;
    private static final float BASS_TUNER_AUTOGAIN_HIGH_REDUCTION_STEP_1_DB = 3.0f;
    private static final float BASS_TUNER_AUTOGAIN_HIGH_REDUCTION_STEP_2_DB = 8.0f;
    private static final float BASS_TUNER_MAX_ACTIVE_THRESHOLD_DB = BASS_DB_MAX - 0.01f;
    private static final float BASS_TUNER_MAX_EQ_AUTOGAIN_BAND_FACTOR = 0.32f;
    private static final float BASS_TUNER_MAX_EQ_AUTOGAIN_BASS_FACTOR = 0.28f;
    private static final float BASS_TUNER_MAX_EQ_AUTOGAIN_CAP_DB = 5.8f;
    private static final float BASS_TUNER_LOW_CUTOFF_DIRECT_BOOST_DB = 5.0f;
    private static final float BASS_TUNER_MAX_EQ_AUTOGAIN_LOW_CUTOFF_CAP_DB = 10.8f;
    private static final float BASS_TUNER_HIGH_CUTOFF_GAIN_THRESHOLD_HZ = 70f;
    private static final float BASS_TUNER_HIGH_CUTOFF_GAIN_BONUS_DB = 7.0f;
        private static final float[] NO_DUCKING_BAND_AUTOGAIN_DB = {
                10.0f,   // 62.5 Hz
                6f,   // 125 Hz
                8f,   // 250 Hz
                5f,   // 500 Hz
                9.0f,   // 1 kHz
                4f,   // 2 kHz
                6f,   // 4 kHz
                2f,   // 8 kHz
                10.0f    // 16 kHz
        };
    private static final float NO_DUCKING_ULTRA_SUB_RATIO = 0.42f;
    private static final float NO_DUCKING_ULTRA_SUB_FLOOR = 0.58f;
    private static final float SPATIAL_LOUDNESS_BASE_DB = 0.95f;
    private static final float SPATIAL_LOUDNESS_DYNAMIC_DB = 2.35f;
    private static final float SPATIAL_LOUDNESS_SPEAKER_BONUS_DB = 0.6f;
    private static final float SPATIAL_LOUDNESS_HEADPHONE_TRIM_DB = 0.25f;
    private static final float SPATIAL_WIDE_STRENGTH_BOOST = 1.22f;
    private static final int SPATIAL_WIDE_STRENGTH_FLOOR = 760;
    private static final float SPATIAL_EQ_CONTOUR_BLEND_MULTIPLIER = 1.10f;
    private static final float REVERB_EQ_CONTOUR_BLEND_MULTIPLIER = 0.82f;
    private static final int SPATIAL_WITH_EQ_VIRTUALIZER_BOOST = 140;
    private static final int REVERB_ONLY_STRENGTH_CAP = 640;
    private static final float REVERB_CONTOUR_LOW_CENTER_HZ = 230f;
    private static final float REVERB_CONTOUR_LOW_WIDTH_OCTAVES = 0.95f;
    private static final float REVERB_CONTOUR_MUD_CENTER_HZ = 430f;
    private static final float REVERB_CONTOUR_MUD_WIDTH_OCTAVES = 0.82f;
    private static final float REVERB_CONTOUR_PRESENCE_CENTER_HZ = 3200f;
    private static final float REVERB_CONTOUR_PRESENCE_WIDTH_OCTAVES = 0.98f;
    private static final float REVERB_CONTOUR_AIR_CENTER_HZ = 9800f;
    private static final float REVERB_CONTOUR_AIR_WIDTH_OCTAVES = 1.05f;
    private static final long FORCE_SPEAKER_FALLBACK_WINDOW_MS = 4000L;
    private static final float DUCKING_LOW_BAND_MAX_HZ = 260f;
    private static final float DUCKING_SOFT_KNEE_RATIO = 0.38f;
    private static final float DUCKING_GLOBAL_OVERFLOW_REDUCTION_DB = 0.9f;
    private static final boolean USE_HARDWARE_BASS_BOOST = false;
    private static final int MAX_HARDWARE_BASS_BOOST_STRENGTH = 600;
    private static final float HARDWARE_BASS_BOOST_CURVE_EXPONENT = 1.15f;
    private static final int EQ_NEUTRAL_TOLERANCE_MB = 24;
    private static final boolean USE_WAVELET_DYNAMICS_PIPELINE = true;
    private static final float DYNAMICS_FRAME_DURATION_MS = 10f;
    private static final float DYNAMICS_MBC_KNEE_WIDTH_DB = 0f;
    private static final float DYNAMICS_MBC_PRE_GAIN_DB = 0f;
    private static final int DYNAMICS_LIMITER_LINK_GROUP = 0;
    private static final float DYNAMICS_LIMITER_ATTACK_MS = 1f;
    private static final float DYNAMICS_LIMITER_RELEASE_MS = 60f;
    private static final float DYNAMICS_LIMITER_RATIO = 10f;
    private static final float DYNAMICS_LIMITER_THRESHOLD_DB = -2f;
    private static final float DYNAMICS_LIMITER_POST_GAIN_DB = 0f;
    private static final float DYNAMICS_INPUT_AUTOGAIN_CAP_DB = 14.8f;
    private static final boolean DYNAMICS_BYPASS_LIMITER_FOR_MAX_LOUDNESS = true;
    private static final boolean DYNAMICS_REMOVE_LIMITER_STAGE = true;
    private static final float[] WAVELET_DYNAMICS_EQ_FREQUENCIES_HZ = {
            20f, 21f, 22f, 23f, 24f, 26f, 27f, 29f, 30f, 32f, 34f, 36f, 38f, 40f,
            43f, 45f, 48f, 50f, 53f, 56f, 59f, 63f, 66f, 70f, 74f, 78f, 83f, 87f,
            92f, 97f, 103f, 109f, 115f, 121f, 128f, 136f, 143f, 151f, 160f, 169f,
            178f, 188f, 199f, 210f, 222f, 235f, 248f, 262f, 277f, 292f, 309f, 326f,
            345f, 364f, 385f, 406f, 429f, 453f, 479f, 506f, 534f, 565f, 596f, 630f,
            665f, 703f, 743f, 784f, 829f, 875f, 924f, 977f, 1032f, 1090f, 1151f,
            1216f, 1284f, 1357f, 1433f, 1514f, 1599f, 1689f, 1784f, 1885f, 1991f,
            2103f, 2221f, 2347f, 2479f, 2618f, 2766f, 2921f, 3086f, 3260f, 3443f,
            3637f, 3842f, 4058f, 4287f, 4528f, 4783f, 5052f, 5337f, 5637f, 5955f,
            6290f, 6644f, 7018f, 7414f, 7831f, 8272f, 8738f, 9230f, 9749f, 10298f,
            10878f, 11490f, 12137f, 12821f, 13543f, 14305f, 15110f, 15961f, 16860f,
            17809f, 18812f, 19871f
    };

    public static final String ACTION_APPLY = "com.example.sleppify.action.APPLY_EFFECTS";
    public static final String ACTION_STOP = "com.example.sleppify.action.STOP_EFFECTS";

    public static final String PREFS_NAME = "global_eq_prefs";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_BASS_DB = "bass_db";
    public static final String KEY_BASS_FREQUENCY_HZ = "bass_frequency_hz";
    public static final String KEY_BASS_TYPE = "bass_type";
    public static final String KEY_SPATIAL_ENABLED = "spatial_enabled";
    public static final String KEY_SPATIAL_STRENGTH = "spatial_strength";
    public static final String KEY_REVERB_LEVEL = "reverb_level";
    public static final int SPATIAL_STRENGTH_DEFAULT = 1000;
    private static final int SPATIAL_STRENGTH_MIN = 0;
    private static final int SPATIAL_STRENGTH_MAX = 1000;
    public static final int REVERB_LEVEL_OFF = 0;
    public static final int REVERB_LEVEL_LIGHT = 1;
    public static final int REVERB_LEVEL_MEDIUM = 2;
    public static final int REVERB_LEVEL_STRONG = 3;
    public static final int BASS_TYPE_NATURAL = 0;
    public static final int BASS_TYPE_TRANSIENT_COMPRESSOR = 1;
    public static final int BASS_TYPE_SUSTAIN_COMPRESSOR = 2;
    private static final String KEY_BAND_DB_PREFIX = "band_db_";

    private static final BassOutputTuning BASS_TUNING_HEADPHONES = new BassOutputTuning(
            1.00f,
            1.00f,
            1.12f,
            0.92f,
            1.12f,
            1.08f,
            1.00f,
            5.6f,
            6.2f,
            6.6f,
            52f
    );
    private static final BassOutputTuning BASS_TUNING_SPEAKER = new BassOutputTuning(
            0.84f,
            0.88f,
            0.95f,
            1.20f,
            0.78f,
            0.94f,
            0.86f,
            4.4f,
            5.0f,
            5.3f,
            35f
    );
    private static final BassOutputTuning BASS_TUNING_EARPIECE = new BassOutputTuning(
            0.52f,
            0.60f,
            0.66f,
            1.35f,
            0.50f,
            0.72f,
            0.62f,
            3.2f,
            3.8f,
            4.1f,
            20f
    );
    private static final BassOutputTuning BASS_TUNING_GENERIC = new BassOutputTuning(
            0.92f,
            0.95f,
            1.00f,
            1.05f,
            0.92f,
            1.00f,
            0.94f,
            5.0f,
            5.6f,
            5.9f,
            43f
    );
            private static final BassTunerTypeProfile BASS_TUNER_PROFILE_NATURAL = new BassTunerTypeProfile(3f, 80f, 1f, -45f, -90f, 1f);
            private static final BassTunerTypeProfile BASS_TUNER_PROFILE_TRANSIENT = new BassTunerTypeProfile(0.01f, 15f, 1f, -15f, -15f, 0.95f);
            private static final BassTunerTypeProfile BASS_TUNER_PROFILE_SUSTAIN = new BassTunerTypeProfile(30f, 100f, 0.925f, -60f, -30f, 1f);

    private Equalizer equalizer;
    private DynamicsProcessing dynamicsProcessing;
    private BassBoost bassBoost;
    private LoudnessEnhancer loudnessEnhancer;
    private Virtualizer virtualizer;
    private PresetReverb presetReverb;
    private EnvironmentalReverb environmentalReverb;
    private SharedPreferences preferences;
    private AudioManager audioManager;
    private boolean forceSpeakerOutputSelection;
    private long forceSpeakerFallbackUntilMs;
    private boolean wasSpatialEqStackActive;
    private boolean hasAppliedAudioState;
    private boolean lastAppliedEqEnabled;
    private boolean lastAppliedSpatialEnabled;
    private int lastAppliedReverbLevel = REVERB_LEVEL_OFF;
    private boolean usingDynamicsProcessingPath;

    private static final class BassOutputTuning {
        final float baseScale;
        final float naturalPunchScale;
        final float transientPunchScale;
        final float transientSubTrimScale;
        final float sustainRumbleScale;
        final float maxBoostMultiplier;
        final float dbDrive;
        final float lowBandSoftCeilingDb;
        final float fullBandSoftCeilingDb;
        final float hardCeilingDb;
        final float maxPositiveEnergy;

        BassOutputTuning(
                float baseScale,
                float naturalPunchScale,
                float transientPunchScale,
                float transientSubTrimScale,
                float sustainRumbleScale,
                float maxBoostMultiplier,
                float dbDrive,
                float lowBandSoftCeilingDb,
                float fullBandSoftCeilingDb,
                float hardCeilingDb,
                float maxPositiveEnergy
        ) {
            this.baseScale = baseScale;
            this.naturalPunchScale = naturalPunchScale;
            this.transientPunchScale = transientPunchScale;
            this.transientSubTrimScale = transientSubTrimScale;
            this.sustainRumbleScale = sustainRumbleScale;
            this.maxBoostMultiplier = maxBoostMultiplier;
            this.dbDrive = dbDrive;
            this.lowBandSoftCeilingDb = lowBandSoftCeilingDb;
            this.fullBandSoftCeilingDb = fullBandSoftCeilingDb;
            this.hardCeilingDb = hardCeilingDb;
            this.maxPositiveEnergy = maxPositiveEnergy;
        }
    }

    private static final class BassTunerTypeProfile {
        final float attackMs;
        final float releaseMs;
        final float ratio;
        final float thresholdDb;
        final float noiseGateThresholdDb;
        final float expanderRatio;

        BassTunerTypeProfile(
                float attackMs,
                float releaseMs,
                float ratio,
                float thresholdDb,
                float noiseGateThresholdDb,
                float expanderRatio
        ) {
            this.attackMs = attackMs;
            this.releaseMs = releaseMs;
            this.ratio = ratio;
            this.thresholdDb = thresholdDb;
            this.noiseGateThresholdDb = noiseGateThresholdDb;
            this.expanderRatio = expanderRatio;
        }
    }

    private final AudioDeviceCallback outputDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (containsHeadphoneLikeOutput(addedDevices)) {
                forceSpeakerOutputSelection = false;
                forceSpeakerFallbackUntilMs = 0L;
            }
            synchronizeProfileForCurrentOutput();
            applyEffectsFromPreferences();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            if (containsHeadphoneLikeOutput(removedDevices)) {
                forceSpeakerOutputSelection = true;
                forceSpeakerFallbackUntilMs = System.currentTimeMillis() + FORCE_SPEAKER_FALLBACK_WINDOW_MS;
            }
            synchronizeProfileForCurrentOutput();
            applyEffectsFromPreferences();
        }
    };

    public static String bandDbKey(int bandIndex) {
        return KEY_BAND_DB_PREFIX + bandIndex;
    }

    public static float normalizeBassFrequencySliderValue(float rawValue) {
        if (rawValue < BASS_FREQUENCY_MIN_HZ || rawValue > BASS_FREQUENCY_MAX_HZ) {
            return bassSliderFromCutoffHz(rawValue);
        }
        return clampStatic(rawValue, BASS_FREQUENCY_MIN_HZ, BASS_FREQUENCY_MAX_HZ);
    }

    public static float bassCutoffFromSliderHz(float sliderValue) {
        return clampStatic(sliderValue, BASS_TUNER_CUTOFF_MIN_HZ, BASS_TUNER_CUTOFF_MAX_HZ);
    }

    public static float bassSliderFromCutoffHz(float cutoffHz) {
        return clampStatic(cutoffHz, BASS_FREQUENCY_MIN_HZ, BASS_FREQUENCY_MAX_HZ);
    }

    private static float clampStatic(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        registerOutputDeviceCallback();
        synchronizeProfileForCurrentOutput();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_APPLY;

        if (ACTION_STOP.equals(action)) {
            synchronizeProfileForCurrentOutput();
            SharedPreferences prefs = preferences;
            if (prefs == null) {
                prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                preferences = prefs;
            }
            AudioDeviceProfileStore.persistActiveValuesToCurrentProfile(prefs);
            stopAndRelease();
            return START_NOT_STICKY;
        }

        startAsForeground();
        synchronizeProfileForCurrentOutput();
        applyEffectsFromPreferences();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterOutputDeviceCallback();
        releaseEffects();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        SharedPreferences prefs = preferences;
        if (prefs == null) {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            preferences = prefs;
        }

        boolean eqEnabled = prefs.getBoolean(KEY_ENABLED, false);
        boolean spatialEnabled = prefs.getBoolean(KEY_SPATIAL_ENABLED, false);
        boolean reverbEnabled = isReverbEnabled(prefs);
        if (eqEnabled || spatialEnabled || reverbEnabled) {
            Intent restartIntent = new Intent(getApplicationContext(), AudioEffectsService.class);
            restartIntent.setAction(ACTION_APPLY);
            try {
                ContextCompat.startForegroundService(getApplicationContext(), restartIntent);
            } catch (Throwable throwable) {
                Log.w(TAG, "No se pudo reanudar servicio tras remover task", throwable);
            }
        }

        super.onTaskRemoved(rootIntent);
    }

    private void startAsForeground() {
        createNotificationChannel();
        SharedPreferences prefs = preferences;
        if (prefs == null) {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            preferences = prefs;
        }

        boolean eqEnabled = prefs.getBoolean(KEY_ENABLED, false);
        boolean spatialEnabled = prefs.getBoolean(KEY_SPATIAL_ENABLED, false);
        boolean reverbEnabled = isReverbEnabled(prefs);

        Notification notification = buildForegroundNotification(eqEnabled, spatialEnabled, reverbEnabled);
        startForeground(NOTIFICATION_ID, notification);
    }

    @NonNull
    private Notification buildForegroundNotification(boolean eqEnabled, boolean spatialEnabled, boolean reverbEnabled) {
        String outputLabel = describeOutputForNotification(readCurrentPreferredOutput());
        String title = "Aplicaciones usando Sleppify:";
        String content = "Sistema Android";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setSubText(outputLabel)
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void refreshForegroundNotification(boolean eqEnabled, boolean spatialEnabled, boolean reverbEnabled) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        try {
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildForegroundNotification(eqEnabled, spatialEnabled, reverbEnabled)
            );
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo actualizar notificacion de foreground", throwable);
        }
    }

    @NonNull
    private String describeOutputForNotification(@Nullable AudioDeviceInfo output) {
        if (output == null) {
            return "salida desconocida";
        }

        int type = output.getType();
        CharSequence productName = output.getProductName();
        String normalizedProductName = productName == null ? "" : productName.toString().trim();
        if (!normalizedProductName.isEmpty()
                && !normalizedProductName.equalsIgnoreCase("speaker")
                && !normalizedProductName.equalsIgnoreCase("phone")) {
            return normalizedProductName;
        }

        if (isBluetoothType(type)) {
            return "Bluetooth";
        }
        if (isWiredType(type)) {
            return "audifonos con cable";
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) {
            return "parlante del telefono";
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
            return "auricular del telefono";
        }
        return "salida externa";
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Global EQ",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Efectos de audio globales en modo heredado");
        notificationManager.createNotificationChannel(channel);
    }

    private void applyEffectsFromPreferences() {
        SharedPreferences prefs = preferences;
        if (prefs == null) {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            preferences = prefs;
        }
        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        boolean spatialEnabled = prefs.getBoolean(KEY_SPATIAL_ENABLED, false);
        int reverbLevel = normalizeReverbLevel(
            prefs.getInt(KEY_REVERB_LEVEL, REVERB_LEVEL_OFF)
        );
        boolean reverbEnabled = reverbLevel != REVERB_LEVEL_OFF;
        boolean spatialPathEnabled = spatialEnabled || reverbEnabled;
        boolean spatialEqStackActive = enabled && spatialEnabled;
        boolean shouldForceSpatialRearm = spatialEqStackActive && !wasSpatialEqStackActive;
        wasSpatialEqStackActive = spatialEqStackActive;

        boolean eqTurnedOn = enabled && (!hasAppliedAudioState || !lastAppliedEqEnabled);
        boolean eqTurnedOff = hasAppliedAudioState && lastAppliedEqEnabled && !enabled;

        if (eqTurnedOff) {
            AudioDeviceProfileStore.persistActiveValuesToCurrentProfile(prefs);
        }
        if (eqTurnedOn) {
            AudioDeviceInfo currentOutput = readCurrentPreferredOutput();
            AudioDeviceProfileStore.restoreActiveValuesForOutput(prefs, currentOutput);
        }

        boolean shouldRebuildChain = shouldRebuildEffectChain(enabled, spatialEnabled, reverbLevel);

        if (!enabled && !spatialPathEnabled) {
            wasSpatialEqStackActive = false;
            disableBassTunerPath();
            stopAndRelease();
            return;
        }

        refreshForegroundNotification(enabled, spatialEnabled, reverbEnabled);

        if (shouldRebuildChain) {
            releaseEffects();
            // Fuerza rearmado completo de Spatial en transiciones para evitar estados latentes OEM.
            shouldForceSpatialRearm = true;
            wasSpatialEqStackActive = false;
        }

        updateAppliedAudioState(enabled, spatialEnabled, reverbLevel);

        initEffectsIfNeeded();
        if (equalizer == null && bassBoost == null && loudnessEnhancer == null && virtualizer == null && presetReverb == null) {
            Log.w(TAG, "No fue posible inicializar efectos globales en sesión 0.");
            return;
        }

        if (enabled) {
            if (equalizer != null) {
                applyEqualizer(prefs, reverbLevel, spatialEnabled);
            }

            if (bassBoost != null) {
                applyBassBoost(prefs);
            }

            if (loudnessEnhancer != null) {
                applyLoudnessCompensation(prefs, true, spatialEnabled);
            }
        } else {
            // EQ apagado: bass tuner siempre off (Dynamics + BassBoost).
            disableBassTunerPath();
            if (spatialEnabled) {
                applySpatialContourWithEq(prefs, reverbLevel);
                applyLoudnessCompensation(prefs, false, true);
            } else if (reverbEnabled) {
                disableBassAndLoudnessPath();
                applyReverbOnlyContourWithEq(reverbLevel);
            } else {
                disableEqualizerPath();
            }
        }

        applySpatializer(
            prefs,
            spatialPathEnabled,
            spatialEnabled,
            reverbLevel,
            enabled,
            shouldForceSpatialRearm
        );
    }

    private boolean shouldRebuildEffectChain(boolean eqEnabled, boolean spatialEnabled, int reverbLevel) {
        if (!hasAppliedAudioState) {
            return false;
        }

        return eqEnabled != lastAppliedEqEnabled
                || spatialEnabled != lastAppliedSpatialEnabled
                || reverbLevel != lastAppliedReverbLevel;
    }

    private void updateAppliedAudioState(boolean eqEnabled, boolean spatialEnabled, int reverbLevel) {
        hasAppliedAudioState = true;
        lastAppliedEqEnabled = eqEnabled;
        lastAppliedSpatialEnabled = spatialEnabled;
        lastAppliedReverbLevel = normalizeReverbLevel(reverbLevel);
    }

    private void clearAppliedAudioStateTracking() {
        hasAppliedAudioState = false;
        lastAppliedEqEnabled = false;
        lastAppliedSpatialEnabled = false;
        lastAppliedReverbLevel = REVERB_LEVEL_OFF;
        wasSpatialEqStackActive = false;
        usingDynamicsProcessingPath = false;
    }

    private void initEffectsIfNeeded() {
        if (dynamicsProcessing == null && canUseDynamicsProcessing()) {
            try {
                dynamicsProcessing = createDynamicsProcessingInstance();
            } catch (Throwable throwable) {
                Log.w(TAG, "DynamicsProcessing global no soportado por el dispositivo", throwable);
                dynamicsProcessing = null;
            }
        }

        if (equalizer == null) {
            try {
                equalizer = new Equalizer(0, 0);
            } catch (Throwable throwable) {
                Log.w(TAG, "Equalizer global no soportado por el dispositivo", throwable);
                equalizer = null;
            }
        }

        if (bassBoost == null) {
            try {
                bassBoost = new BassBoost(0, 0);
            } catch (Throwable throwable) {
                Log.w(TAG, "BassBoost global no soportado por el dispositivo", throwable);
                bassBoost = null;
            }
        }

        if (loudnessEnhancer == null) {
            try {
                loudnessEnhancer = new LoudnessEnhancer(0);
            } catch (Throwable throwable) {
                Log.w(TAG, "LoudnessEnhancer global no soportado por el dispositivo", throwable);
                loudnessEnhancer = null;
            }
        }

        if (virtualizer == null) {
            try {
                virtualizer = new Virtualizer(0, 0);
            } catch (Throwable throwable) {
                Log.w(TAG, "Virtualizer global no soportado por el dispositivo", throwable);
                virtualizer = null;
            }
        }

        if (presetReverb == null) {
            try {
                presetReverb = new PresetReverb(0, 0);
            } catch (Throwable throwable) {
                Log.w(TAG, "PresetReverb global no soportado por el dispositivo", throwable);
                presetReverb = null;
            }
        }

        if (environmentalReverb == null) {
            try {
                environmentalReverb = new EnvironmentalReverb(0, 0);
            } catch (Throwable throwable) {
                Log.w(TAG, "EnvironmentalReverb global no soportado por el dispositivo", throwable);
                environmentalReverb = null;
            }
        }
    }

    private boolean canUseDynamicsProcessing() {
        return USE_WAVELET_DYNAMICS_PIPELINE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    @NonNull
    private DynamicsProcessing createDynamicsProcessingInstance() {
        DynamicsProcessing.Config config = buildWaveletDynamicsConfig();
        DynamicsProcessing processing = new DynamicsProcessing(0, 0, config);
        processing.setEnabled(false);
        return processing;
    }

    @NonNull
    private DynamicsProcessing.Config buildWaveletDynamicsConfig() {
        DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                0,
                2,
                true,
                WAVELET_DYNAMICS_EQ_FREQUENCIES_HZ.length,
                true,
                1,
                false,
                0,
                !DYNAMICS_REMOVE_LIMITER_STAGE
        );
        return builder.setPreferredFrameDuration(DYNAMICS_FRAME_DURATION_MS).build();
    }

    private void disableDynamicsPath() {
        usingDynamicsProcessingPath = false;
        if (dynamicsProcessing == null) {
            return;
        }

        try {
            dynamicsProcessing.setInputGainbyChannel(0, 0f);
            dynamicsProcessing.setInputGainbyChannel(1, 0f);
            dynamicsProcessing.setEnabled(false);
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo desactivar DynamicsProcessing", throwable);
        }
    }

    private void disableEqualizerPath() {
        disableDynamicsPath();

        try {
            if (equalizer != null) {
                equalizer.setEnabled(false);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo desactivar equalizer", throwable);
        }

        disableBassAndLoudnessPath();
    }

    private void disableBassAndLoudnessPath() {

        disableBassPath();
        disableLoudnessPath();
    }

    private void disableBassTunerPath() {
        disableDynamicsPath();
        disableBassPath();
    }

    private void disableBassPath() {

        try {
            if (bassBoost != null) {
                bassBoost.setStrength((short) 0);
                bassBoost.setEnabled(false);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo desactivar bass boost", throwable);
        }
    }

    private void disableLoudnessPath() {
        try {
            if (loudnessEnhancer != null) {
                loudnessEnhancer.setTargetGain(0);
                loudnessEnhancer.setEnabled(false);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo desactivar loudness", throwable);
        }
    }

    private void applySpatialContourWithEq(SharedPreferences prefs, int reverbLevel) {
        disableDynamicsPath();

        if (equalizer == null) {
            return;
        }

        try {
            short[] bandRange = equalizer.getBandLevelRange();
            short bandCount = equalizer.getNumberOfBands();
            if (bandCount <= 0) {
                equalizer.setEnabled(false);
                return;
            }

            int rawStrength = prefs.getInt(KEY_SPATIAL_STRENGTH, SPATIAL_STRENGTH_DEFAULT);
            if (rawStrength <= 0) {
                rawStrength = SPATIAL_STRENGTH_DEFAULT;
            }
            float strengthNorm = clamp(rawStrength / 1000f, 0f, 1f);
            boolean hasAudibleChange = false;

            for (short band = 0; band < bandCount; band++) {
                float centerHz = equalizer.getCenterFreq(band) / 1000f;
                float contourDb = computeSpatialContourDb(centerHz, strengthNorm);
                contourDb += computeReverbContourDb(centerHz, reverbLevel, false);
                int levelMb = Math.round(contourDb * 100f);
                if (Math.abs(levelMb) > EQ_NEUTRAL_TOLERANCE_MB) {
                    hasAudibleChange = true;
                }
                equalizer.setBandLevel(band, clampToShort(levelMb, bandRange[0], bandRange[1]));
            }

            equalizer.setEnabled(hasAudibleChange);
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando contorno espacial por EQ", throwable);
        }
    }

    private void applyReverbOnlyContourWithEq(int reverbLevel) {
        disableDynamicsPath();

        if (equalizer == null) {
            return;
        }

        int normalizedReverbLevel = normalizeReverbLevel(reverbLevel);
        if (normalizedReverbLevel == REVERB_LEVEL_OFF) {
            try {
                equalizer.setEnabled(false);
            } catch (Throwable throwable) {
                Log.w(TAG, "No se pudo desactivar contour de reverb", throwable);
            }
            return;
        }

        try {
            short[] bandRange = equalizer.getBandLevelRange();
            short bandCount = equalizer.getNumberOfBands();
            if (bandCount <= 0) {
                equalizer.setEnabled(false);
                return;
            }

            boolean hasAudibleChange = false;
            for (short band = 0; band < bandCount; band++) {
                float centerHz = equalizer.getCenterFreq(band) / 1000f;
                float contourDb = computeReverbContourDb(centerHz, normalizedReverbLevel, true);
                int levelMb = Math.round(contourDb * 100f);
                if (Math.abs(levelMb) > EQ_NEUTRAL_TOLERANCE_MB) {
                    hasAudibleChange = true;
                }
                equalizer.setBandLevel(band, clampToShort(levelMb, bandRange[0], bandRange[1]));
            }

            equalizer.setEnabled(hasAudibleChange);
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando contour de reverb por EQ", throwable);
        }
    }

    private float computeSpatialContourDb(float centerFrequencyHz, float strengthNorm) {
        if (strengthNorm <= 0f) {
            return 0f;
        }

        float safeFrequencyHz = Math.max(LOW_EDGE_FREQUENCY_HZ, centerFrequencyHz);
        float lowLiftDb = 1.45f * strengthNorm * gaussian(Math.abs(log2(safeFrequencyHz / 140f)), 0.85f);
        float presenceDipDb = -1.25f * strengthNorm * gaussian(Math.abs(log2(safeFrequencyHz / 1800f)), 0.68f);
        float airLiftDb = 1.95f * strengthNorm * gaussian(Math.abs(log2(safeFrequencyHz / 9200f)), 1.08f);

        return clamp(lowLiftDb + presenceDipDb + airLiftDb, MIN_EQ_DB, MAX_EQ_DB);
    }

    private float computeReverbContourDb(float centerFrequencyHz, int reverbLevel, boolean reverbOnlyMode) {
        int normalizedReverbLevel = normalizeReverbLevel(reverbLevel);
        if (normalizedReverbLevel == REVERB_LEVEL_OFF) {
            return 0f;
        }

        float levelFactor;
        if (normalizedReverbLevel == REVERB_LEVEL_LIGHT) {
            levelFactor = 0.52f;
        } else if (normalizedReverbLevel == REVERB_LEVEL_STRONG) {
            levelFactor = 1f;
        } else {
            levelFactor = 0.78f;
        }

        float contourGain = reverbOnlyMode ? 1.26f : 0.82f;
        float safeFrequencyHz = Math.max(LOW_EDGE_FREQUENCY_HZ, centerFrequencyHz);

        float warmthLiftDb = 0.58f * levelFactor
                * gaussian(Math.abs(log2(safeFrequencyHz / REVERB_CONTOUR_LOW_CENTER_HZ)), REVERB_CONTOUR_LOW_WIDTH_OCTAVES);
        float mudCutDb = -0.95f * levelFactor
                * gaussian(Math.abs(log2(safeFrequencyHz / REVERB_CONTOUR_MUD_CENTER_HZ)), REVERB_CONTOUR_MUD_WIDTH_OCTAVES);
        float presenceLiftDb = 1.22f * levelFactor
                * gaussian(Math.abs(log2(safeFrequencyHz / REVERB_CONTOUR_PRESENCE_CENTER_HZ)), REVERB_CONTOUR_PRESENCE_WIDTH_OCTAVES);
        float airLiftDb = 1.42f * levelFactor
                * gaussian(Math.abs(log2(safeFrequencyHz / REVERB_CONTOUR_AIR_CENTER_HZ)), REVERB_CONTOUR_AIR_WIDTH_OCTAVES);

        float contourDb = (warmthLiftDb + mudCutDb + presenceLiftDb + airLiftDb) * contourGain;
        return clamp(contourDb, MIN_EQ_DB, MAX_EQ_DB);
    }

    private void applyEqualizer(SharedPreferences prefs, int reverbLevel, boolean spatialEnabled) {
        usingDynamicsProcessingPath = false;

        if (applyWaveletDynamicsProcessing(prefs)) {
            return;
        }

        disableDynamicsPath();

        if (equalizer == null) {
            return;
        }

        try {
            short[] bandRange = equalizer.getBandLevelRange();
            short bandCount = equalizer.getNumberOfBands();
            if (bandCount <= 0) {
                equalizer.setEnabled(false);
                return;
            }

            float[] uiBandDb = readUiBandDbFromPrefs(prefs);
            float lowEdgeDb = uiBandDb.length > 0 ? uiBandDb[0] * EDGE_EXTENSION_FACTOR : 0f;
            float highEdgeDb = uiBandDb.length > 0
                    ? uiBandDb[uiBandDb.length - 1] * EDGE_EXTENSION_FACTOR
                    : 0f;

            float bassDb = clamp(prefs.getFloat(KEY_BASS_DB, 0f), 0f, BASS_DB_MAX);
                float bassFrequencySlider = normalizeBassFrequencySlider(
                    prefs.getFloat(KEY_BASS_FREQUENCY_HZ, BASS_FREQUENCY_DEFAULT_HZ)
                );
                float bassCutoffHz = bassSliderToCutoffHz(bassFrequencySlider);
            int bassType = normalizeBassType(prefs.getInt(KEY_BASS_TYPE, BASS_TYPE_NATURAL));

            AudioDeviceInfo currentOutput = readCurrentPreferredOutput();
            BassOutputTuning outputTuning = resolveBassOutputTuning(currentOutput);

            int safeBandCount = Math.max(0, bandCount);
            float[] bandCentersHz = new float[safeBandCount];
            for (short band = 0; band < bandCount; band++) {
                bandCentersHz[band] = equalizer.getCenterFreq(band) / 1000f;
            }

            float[] computedBandDb = computeCombinedEqCurveDb(
                    bandCentersHz,
                    uiBandDb,
                    lowEdgeDb,
                    highEdgeDb,
                    bassDb,
                    bassCutoffHz,
                    bassType,
                    outputTuning,
                    true,
                        false
            );

            boolean hasAudibleChange = false;
            for (short band = 0; band < bandCount; band++) {
                float totalDb = computedBandDb[band];
                int levelMb = Math.round(totalDb * 100f);
                if (Math.abs(levelMb) > EQ_NEUTRAL_TOLERANCE_MB) {
                    hasAudibleChange = true;
                }

                short clamped = clampToShort(levelMb, bandRange[0], bandRange[1]);
                equalizer.setBandLevel(band, clamped);
            }

            equalizer.setEnabled(hasAudibleChange);
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando bandas de equalizer", throwable);
        }
    }

    @NonNull
    private float[] readUiBandDbFromPrefs(@NonNull SharedPreferences prefs) {
        float[] uiBandDb = new float[WAVELET_9_BAND_FREQUENCIES_HZ.length];
        for (int i = 0; i < uiBandDb.length; i++) {
            float db = prefs.getFloat(bandDbKey(i), 0f);
            uiBandDb[i] = clamp(db, MIN_EQ_DB, MAX_EQ_DB);
        }
        return uiBandDb;
    }

    @NonNull
    private float[] computeCombinedEqCurveDb(
            @NonNull float[] centerFrequenciesHz,
            @NonNull float[] uiBandDb,
            float lowEdgeDb,
            float highEdgeDb,
            float bassDb,
            float bassCutoffHz,
            int bassType,
            @NonNull BassOutputTuning outputTuning,
            boolean includeBassTunerCurve,
            boolean applyNoDuckingSafeguards
    ) {
        int count = centerFrequenciesHz.length;
        float[] computedBandDb = new float[count];
        WaveletAkimaSpline waveletSpline = null;
        if (!FORCE_LOCALIZED_BAND_SHAPES && USE_WAVELET_AKIMA_GRAPHIC_EQ) {
            waveletSpline = buildWaveletGraphicEqSpline(uiBandDb, lowEdgeDb, highEdgeDb);
        }

        for (int i = 0; i < count; i++) {
            float centerHz = centerFrequenciesHz[i];
            float curveDb;
            if (FORCE_LOCALIZED_BAND_SHAPES) {
                curveDb = computeLocalizedBandCurveDb(centerHz, uiBandDb);
            } else if (USE_WAVELET_AKIMA_GRAPHIC_EQ && waveletSpline != null) {
                curveDb = clamp(
                        (float) waveletSpline.evaluate(clamp(centerHz, 0f, 24000f)),
                        MIN_EQ_DB,
                        MAX_EQ_DB
                );
            } else {
                curveDb = computeGraphicEqCurveDb(centerHz, uiBandDb, lowEdgeDb, highEdgeDb);
            }

            // Aisla el subgrave: de 20 a 62.5 Hz solo responde la banda de 62.5.
            if (centerHz <= SUB_BASS_PROTECTION_MAX_HZ) {
                curveDb = computeSubBassProtectedCurveDb(centerHz, uiBandDb);
            }

            float bassTunerDb = includeBassTunerCurve
                    ? computeBassTunerExtraDb(centerHz, bassDb, bassCutoffHz, bassType, outputTuning)
                    : 0f;

            float compensatedCurveDb = curveDb;
            if (bassDb > 0.01f && compensatedCurveDb > 0f) {
                // Evita doble suma entre EQ positivo y Bass Tuner: el bass consume headroom del boost por banda.
                compensatedCurveDb = Math.max(0f, compensatedCurveDb - bassDb);
            }

            computedBandDb[i] = clamp(compensatedCurveDb + bassTunerDb, MIN_EQ_DB, MAX_EQ_DB);
        }

        if (applyNoDuckingSafeguards && !FORCE_LOCALIZED_BAND_SHAPES) {
            applyAntiDuckingSafeguards(computedBandDb, centerFrequenciesHz, outputTuning);
        }
        return computedBandDb;
    }

    private float computeLocalizedBandCurveDb(float frequencyHz, @NonNull float[] uiBandDb) {
        int count = Math.min(WAVELET_9_BAND_FREQUENCIES_HZ.length, uiBandDb.length);
        if (count <= 0) {
            return 0f;
        }

        float safeFrequencyHz = clamp(frequencyHz, LOW_EDGE_FREQUENCY_HZ, HIGH_EDGE_FREQUENCY_HZ);
        float totalDb = 0f;
        for (int i = 0; i < count; i++) {
            float bandDb = clamp(uiBandDb[i], MIN_EQ_DB, MAX_EQ_DB);
            if (Math.abs(bandDb) < 0.0001f) {
                continue;
            }

            float anchorHz = WAVELET_9_BAND_FREQUENCIES_HZ[i];
            float widthOctaves = resolveLocalizedBandWidthOctaves(anchorHz);
            float distanceOctaves = Math.abs(log2(safeFrequencyHz / anchorHz));
            float localWeight = gaussian(distanceOctaves, widthOctaves);
            totalDb += bandDb * localWeight;
        }

        return clamp(totalDb, MIN_EQ_DB, MAX_EQ_DB);
    }

    private float computeSubBassProtectedCurveDb(float frequencyHz, @NonNull float[] uiBandDb) {
        float band62Db = uiBandDb.length > 0 ? clamp(uiBandDb[0], MIN_EQ_DB, MAX_EQ_DB) : 0f;
        float safeFrequencyHz = clamp(frequencyHz, LOW_EDGE_FREQUENCY_HZ, SUB_BASS_PROTECTION_MAX_HZ);
        float distanceOctaves = Math.abs(log2(safeFrequencyHz / SUB_BASS_PROTECTION_MAX_HZ));
        float localWeight = gaussian(distanceOctaves, LOCALIZED_BAND_WIDTH_LOW_OCTAVES);
        return clamp(band62Db * localWeight, MIN_EQ_DB, MAX_EQ_DB);
    }

    private float resolveLocalizedBandWidthOctaves(float anchorHz) {
        if (Math.abs(anchorHz - 250f) <= 0.01f) {
            return LOCALIZED_BAND_WIDTH_250_OCTAVES;
        }
        if (anchorHz >= 12000f) {
            return LOCALIZED_BAND_WIDTH_AIR_OCTAVES;
        }
        if (anchorHz >= 4000f) {
            return LOCALIZED_BAND_WIDTH_HIGH_OCTAVES;
        }
        if (anchorHz >= 500f) {
            return LOCALIZED_BAND_WIDTH_MID_OCTAVES;
        }
        return LOCALIZED_BAND_WIDTH_LOW_OCTAVES;
    }

    @NonNull
    private WaveletAkimaSpline buildWaveletGraphicEqSpline(
            @NonNull float[] uiBandDb,
            float lowEdgeDb,
            float highEdgeDb
    ) {
        double[] y = new double[WAVELET_GRAPHIC_EQ_SPLINE_X.length];
        y[0] = clamp(lowEdgeDb, MIN_EQ_DB, MAX_EQ_DB);
        int count = Math.min(uiBandDb.length, WAVELET_9_BAND_FREQUENCIES_HZ.length);
        for (int i = 0; i < count; i++) {
            y[i + 1] = clamp(uiBandDb[i], MIN_EQ_DB, MAX_EQ_DB);
        }
        y[y.length - 1] = clamp(highEdgeDb, MIN_EQ_DB, MAX_EQ_DB);
        return WaveletAkimaSpline.create(WAVELET_GRAPHIC_EQ_SPLINE_X, y);
    }

    private boolean hasAudibleCurveChange(@NonNull float[] bandDb) {
        for (float gainDb : bandDb) {
            int gainMb = Math.round(gainDb * 100f);
            if (Math.abs(gainMb) > EQ_NEUTRAL_TOLERANCE_MB) {
                return true;
            }
        }
        return false;
    }

    private boolean applyWaveletDynamicsProcessing(@NonNull SharedPreferences prefs) {
        if (!canUseDynamicsProcessing() || dynamicsProcessing == null) {
            return false;
        }

        try {
            float[] uiBandDb = readUiBandDbFromPrefs(prefs);
            float lowEdgeDb = uiBandDb.length > 0 ? uiBandDb[0] * EDGE_EXTENSION_FACTOR : 0f;
            float highEdgeDb = uiBandDb.length > 0
                    ? uiBandDb[uiBandDb.length - 1] * EDGE_EXTENSION_FACTOR
                    : 0f;

            float bassDb = clamp(prefs.getFloat(KEY_BASS_DB, 0f), 0f, BASS_DB_MAX);
                float bassFrequencySlider = normalizeBassFrequencySlider(
                    prefs.getFloat(KEY_BASS_FREQUENCY_HZ, BASS_FREQUENCY_DEFAULT_HZ)
                );
                float bassCutoffHz = bassSliderToCutoffHz(bassFrequencySlider);
            int bassType = normalizeBassType(prefs.getInt(KEY_BASS_TYPE, BASS_TYPE_NATURAL));

            AudioDeviceInfo currentOutput = readCurrentPreferredOutput();
            BassOutputTuning outputTuning = resolveBassOutputTuning(currentOutput);

            float[] bandCentersHz = WAVELET_DYNAMICS_EQ_FREQUENCIES_HZ.clone();
            float[] computedBandDb = computeCombinedEqCurveDb(
                    bandCentersHz,
                    uiBandDb,
                    lowEdgeDb,
                    highEdgeDb,
                    bassDb,
                    bassCutoffHz,
                    bassType,
                    outputTuning,
                    false,
                    false
            );

            // Wavelet aplica pre-EQ activo en la etapa Dynamics; si queda desactivado solo sube volumen global.
            DynamicsProcessing.Eq preEq = new DynamicsProcessing.Eq(true, true, bandCentersHz.length);
            for (int i = 0; i < bandCentersHz.length; i++) {
                preEq.setBand(i, new DynamicsProcessing.EqBand(true, bandCentersHz[i], computedBandDb[i]));
            }

            boolean hasAudibleChange = hasAudibleCurveChange(computedBandDb) || bassDb > 0.01f;
            float dynamicsInputGainDb = 0f;
            DynamicsProcessing.Mbc mbc = buildWaveletDynamicsMbc(bassFrequencySlider, bassDb, bassType);

            dynamicsProcessing.setPreEqAllChannelsTo(preEq);
            dynamicsProcessing.setMbcAllChannelsTo(mbc);
            if (!DYNAMICS_REMOVE_LIMITER_STAGE) {
                DynamicsProcessing.Limiter limiter = buildWaveletDynamicsLimiter(hasAudibleChange);
                dynamicsProcessing.setLimiterAllChannelsTo(limiter);
            }
            dynamicsProcessing.setInputGainbyChannel(0, dynamicsInputGainDb);
            dynamicsProcessing.setInputGainbyChannel(1, dynamicsInputGainDb);
            dynamicsProcessing.setEnabled(hasAudibleChange);

            if (equalizer != null) {
                equalizer.setEnabled(false);
            }
            disableBassPath();
            usingDynamicsProcessingPath = hasAudibleChange;
            return true;
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando DynamicsProcessing estilo Wavelet", throwable);
            disableDynamicsPath();
            return false;
        }
    }

    @NonNull
    private DynamicsProcessing.Mbc buildWaveletDynamicsMbc(float bassFrequencySlider, float bassDb, int bassType) {
        int normalizedBassType = normalizeBassType(bassType);
        // En Natural evitamos compresion para no aplanar; Transient/Sustain mantienen MBC.
        boolean mbcEnabled = bassDb > 0.01f && normalizedBassType != BASS_TYPE_NATURAL;
        DynamicsProcessing.Mbc mbc = new DynamicsProcessing.Mbc(true, mbcEnabled, 1);

        BassTunerTypeProfile profile = resolveBassTunerTypeProfile(bassType);

        float cutoffFrequencyHz = bassSliderToCutoffHz(bassFrequencySlider);

        float postGainDb = mbcEnabled ? clamp(bassDb, 0f, BASS_DB_MAX) : 0f;
        DynamicsProcessing.MbcBand mbcBand = new DynamicsProcessing.MbcBand(
                mbcEnabled,
                cutoffFrequencyHz,
                profile.attackMs,
                profile.releaseMs,
                profile.ratio,
                profile.thresholdDb,
                DYNAMICS_MBC_KNEE_WIDTH_DB,
                profile.noiseGateThresholdDb,
                profile.expanderRatio,
                DYNAMICS_MBC_PRE_GAIN_DB,
                postGainDb
        );
        mbc.setBand(0, mbcBand);
        return mbc;
    }

    private float normalizeBassFrequencySlider(float rawValue) {
        return normalizeBassFrequencySliderValue(rawValue);
    }

    private float bassSliderToCutoffHz(float sliderValue) {
        return bassCutoffFromSliderHz(sliderValue);
    }

    @NonNull
    private BassTunerTypeProfile resolveBassTunerTypeProfile(int bassType) {
        int normalizedType = normalizeBassType(bassType);
        if (normalizedType == BASS_TYPE_TRANSIENT_COMPRESSOR) {
            return BASS_TUNER_PROFILE_TRANSIENT;
        }
        if (normalizedType == BASS_TYPE_SUSTAIN_COMPRESSOR) {
            return BASS_TUNER_PROFILE_SUSTAIN;
        }
        return BASS_TUNER_PROFILE_NATURAL;
    }

    private static final class WaveletAkimaSpline {
        private final double[] x;
        private final double[][] coeffs;

        private WaveletAkimaSpline(@NonNull double[] x, @NonNull double[][] coeffs) {
            this.x = x;
            this.coeffs = coeffs;
        }

        @NonNull
        static WaveletAkimaSpline create(@NonNull double[] x, @NonNull double[] y) {
            int n = Math.min(x.length, y.length);
            if (n < 5) {
                throw new IllegalArgumentException("Akima spline requiere al menos 5 puntos");
            }

            double[] safeX = new double[n];
            double[] safeY = new double[n];
            System.arraycopy(x, 0, safeX, 0, n);
            System.arraycopy(y, 0, safeY, 0, n);

            double[] segmentSlope = new double[n - 1];
            double[] absSlopeDelta = new double[n - 1];
            for (int i = 0; i < n - 1; i++) {
                double dx = safeX[i + 1] - safeX[i];
                segmentSlope[i] = dx == 0d ? 0d : (safeY[i + 1] - safeY[i]) / dx;
            }

            for (int i = 1; i < n - 1; i++) {
                absSlopeDelta[i] = Math.abs(segmentSlope[i] - segmentSlope[i - 1]);
            }

            double[] tangent = new double[n];
            for (int i = 2; i < n - 2; i++) {
                double nextWeight = absSlopeDelta[i + 1];
                double prevWeight = absSlopeDelta[i - 1];

                if (isNearlyZero(nextWeight) && isNearlyZero(prevWeight)) {
                    double dxRight = safeX[i + 1] - safeX[i];
                    double dxLeft = safeX[i] - safeX[i - 1];
                    double dxSpan = safeX[i + 1] - safeX[i - 1];
                    tangent[i] = dxSpan == 0d
                            ? 0d
                            : ((dxRight * segmentSlope[i - 1]) + (dxLeft * segmentSlope[i])) / dxSpan;
                } else {
                    double denominator = nextWeight + prevWeight;
                    tangent[i] = denominator == 0d
                            ? 0d
                            : ((segmentSlope[i - 1] * nextWeight) + (segmentSlope[i] * prevWeight)) / denominator;
                }
            }

            tangent[0] = akimaEndpointTangent(safeX, safeY, 0, 0, 1, 2);
            tangent[1] = akimaEndpointTangent(safeX, safeY, 1, 0, 1, 2);
            tangent[n - 2] = akimaEndpointTangent(safeX, safeY, n - 2, n - 3, n - 2, n - 1);
            tangent[n - 1] = akimaEndpointTangent(safeX, safeY, n - 1, n - 3, n - 2, n - 1);

            double[][] coeffs = new double[n - 1][4];
            for (int i = 0; i < n - 1; i++) {
                double h = safeX[i + 1] - safeX[i];
                if (h == 0d) {
                    coeffs[i][0] = safeY[i];
                    coeffs[i][1] = 0d;
                    coeffs[i][2] = 0d;
                    coeffs[i][3] = 0d;
                    continue;
                }

                double a = safeY[i];
                double b = tangent[i];
                double c = (((3d * (safeY[i + 1] - safeY[i])) / h) - (2d * tangent[i]) - tangent[i + 1]) / h;
                double d = ((((2d * (safeY[i] - safeY[i + 1])) / h) + tangent[i] + tangent[i + 1]) / (h * h));

                coeffs[i][0] = a;
                coeffs[i][1] = b;
                coeffs[i][2] = c;
                coeffs[i][3] = d;
            }

            return new WaveletAkimaSpline(safeX, coeffs);
        }

        double evaluate(double value) {
            double clampedValue = Math.max(x[0], Math.min(x[x.length - 1], value));
            int segment = 0;
            for (int i = 0; i < x.length - 1; i++) {
                if (clampedValue <= x[i + 1]) {
                    segment = i;
                    break;
                }
            }

            double t = clampedValue - x[segment];
            double[] c = coeffs[segment];
            return (((c[3] * t) + c[2]) * t + c[1]) * t + c[0];
        }

        private static double akimaEndpointTangent(
                @NonNull double[] x,
                @NonNull double[] y,
                int point,
                int p3,
                int p4,
                int p5
        ) {
            double y3 = y[p3];
            double y4 = y[p4];
            double y5 = y[p5];

            double dx2 = x[point] - x[p3];
            double dx4 = x[p4] - x[p3];
            double dx5 = x[p5] - x[p3];

            if (dx4 == 0d || dx5 == 0d || (dx5 * dx5 - dx5 * dx4) == 0d) {
                return 0d;
            }

            double c = (y5 - y3 - ((dx5 / dx4) * (y4 - y3))) / ((dx5 * dx5) - (dx5 * dx4));
            double b = ((y4 - y3) - (c * dx4 * dx4)) / dx4;
            return (2d * c * dx2) + b;
        }

        private static boolean isNearlyZero(double value) {
            return Math.abs(value) <= 1.0e-12d;
        }
    }

    @NonNull
    private DynamicsProcessing.Limiter buildWaveletDynamicsLimiter(boolean enabled) {
        boolean limiterEnabled = enabled && !DYNAMICS_BYPASS_LIMITER_FOR_MAX_LOUDNESS;
        return new DynamicsProcessing.Limiter(
                true,
            limiterEnabled,
                DYNAMICS_LIMITER_LINK_GROUP,
                DYNAMICS_LIMITER_ATTACK_MS,
                DYNAMICS_LIMITER_RELEASE_MS,
                DYNAMICS_LIMITER_RATIO,
                DYNAMICS_LIMITER_THRESHOLD_DB,
                DYNAMICS_LIMITER_POST_GAIN_DB
        );
    }

    private float interpolateCurveDb(float frequencyHz, float[] uiBandDb, float lowEdgeDb, float highEdgeDb) {
        float safeFrequency = clamp(frequencyHz, LOW_EDGE_FREQUENCY_HZ, HIGH_EDGE_FREQUENCY_HZ);

        int coreBandCount = Math.min(WAVELET_9_BAND_FREQUENCIES_HZ.length, uiBandDb.length);
        if (coreBandCount <= 0) {
            return 0f;
        }

        int anchorCount = coreBandCount + 2;
        float[] anchorFrequencies = new float[anchorCount];
        float[] anchorDb = new float[anchorCount];

        anchorFrequencies[0] = LOW_EDGE_FREQUENCY_HZ;
        anchorDb[0] = lowEdgeDb;

        for (int i = 0; i < coreBandCount; i++) {
            anchorFrequencies[i + 1] = WAVELET_9_BAND_FREQUENCIES_HZ[i];
            anchorDb[i + 1] = uiBandDb[i];
        }

        anchorFrequencies[anchorCount - 1] = HIGH_EDGE_FREQUENCY_HZ;
        anchorDb[anchorCount - 1] = highEdgeDb;

        if (safeFrequency <= anchorFrequencies[0]) {
            return anchorDb[0];
        }
        if (safeFrequency >= anchorFrequencies[anchorFrequencies.length - 1]) {
            return anchorDb[anchorDb.length - 1];
        }

        float logFrequency = log10(safeFrequency);
        for (int i = 0; i < anchorFrequencies.length - 1; i++) {
            float leftFrequency = anchorFrequencies[i];
            float rightFrequency = anchorFrequencies[i + 1];

            if (safeFrequency > rightFrequency) {
                continue;
            }

            if (Math.abs(rightFrequency - leftFrequency) < 0.001f) {
                return anchorDb[i + 1];
            }

            float logLeft = log10(leftFrequency);
            float logRight = log10(rightFrequency);
            float t = (logFrequency - logLeft) / (logRight - logLeft);
            t = smoothstep(t);
            return anchorDb[i] + ((anchorDb[i + 1] - anchorDb[i]) * t);
        }

        return anchorDb[anchorDb.length - 1];
    }

    private float computeGraphicEqCurveDb(
            float centerFrequencyHz,
            @NonNull float[] uiBandDb,
            float lowEdgeDb,
            float highEdgeDb
    ) {
        if (USE_BIQUAD_GRAPHIC_EQ_ENGINE) {
            return computeBiquadGraphicEqCurveDb(centerFrequencyHz, uiBandDb);
        }

        float interpolatedDb = interpolateCurveDb(centerFrequencyHz, uiBandDb, lowEdgeDb, highEdgeDb);
        if (!FORCE_GRAPHIC_EQ_FULL_SPECTRUM) {
            return clamp(interpolatedDb, MIN_EQ_DB, MAX_EQ_DB);
        }

        float neighborhoodDb = computeNeighborBandInfluenceDb(centerFrequencyHz, uiBandDb);
        float centerDb = computeCenterBandInfluenceDb(centerFrequencyHz, uiBandDb);

        float neighborhoodBlendDb = interpolatedDb
            + ((neighborhoodDb - interpolatedDb) * GRAPHIC_EQ_NEIGHBOR_BLEND);
        float centerBlendDb = interpolatedDb
            + ((centerDb - interpolatedDb) * GRAPHIC_EQ_CENTER_BLEND);
        float blendedDb = (neighborhoodBlendDb * 0.68f) + (centerBlendDb * 0.32f);

        // Keep slider centers faithful: at/near anchor frequencies, preserve the direct curve value.
        float anchorLock = computeAnchorLockFactor(centerFrequencyHz);
        blendedDb += (interpolatedDb - blendedDb) * anchorLock;

        float lowBoostDb = computeLowBoostAmountDb(uiBandDb);
        if (lowBoostDb > 0f) {
            float lowBoostNorm = clamp(lowBoostDb / MAX_EQ_DB, 0f, 1f);

            if (centerFrequencyHz < 50f) {
                float t = clamp((50f - centerFrequencyHz) / 30f, 0f, 1f);
                float relief = 1f - ((1f - GRAPHIC_EQ_ULTRA_LOW_RELIEF_FLOOR) * lowBoostNorm * t);
                blendedDb *= relief;
            }

            float presenceLiftDb = GRAPHIC_EQ_PRESENCE_LIFT_MAX_DB
                    * lowBoostNorm
                    * gaussian(Math.abs(log2(centerFrequencyHz / GRAPHIC_EQ_PRESENCE_CENTER_HZ)), GRAPHIC_EQ_PRESENCE_WIDTH_OCTAVES);

            float airLiftDb = GRAPHIC_EQ_AIR_LIFT_MAX_DB
                    * lowBoostNorm
                    * gaussian(Math.abs(log2(centerFrequencyHz / GRAPHIC_EQ_AIR_CENTER_HZ)), GRAPHIC_EQ_AIR_WIDTH_OCTAVES);

            blendedDb += presenceLiftDb + airLiftDb;
        }

        return clamp(blendedDb, MIN_EQ_DB, MAX_EQ_DB);
    }

    private float computeBiquadGraphicEqCurveDb(float targetFrequencyHz, @NonNull float[] uiBandDb) {
        int count = Math.min(WAVELET_9_BAND_FREQUENCIES_HZ.length, uiBandDb.length);
        if (count <= 0) {
            return 0f;
        }

        float safeTargetFrequencyHz = clamp(targetFrequencyHz, LOW_EDGE_FREQUENCY_HZ, HIGH_EDGE_FREQUENCY_HZ);
        double totalDb = 0d;

        for (int i = 0; i < count; i++) {
            float gainDb = clamp(uiBandDb[i], MIN_EQ_DB, MAX_EQ_DB);
            if (Math.abs(gainDb) < 0.0001f) {
                continue;
            }

            float centerHz = WAVELET_9_BAND_FREQUENCIES_HZ[i];
            float adaptiveQ = resolveAdaptiveBiquadQ(centerHz, gainDb);
            totalDb += computePeakingBiquadMagnitudeDb(
                    safeTargetFrequencyHz,
                    centerHz,
                    adaptiveQ,
                    gainDb,
                    GRAPHIC_EQ_REFERENCE_SAMPLE_RATE_HZ
            );
        }

        float baseDb = (float) totalDb;
        float spreadDb = applyBiquadLateralSpreadDb(safeTargetFrequencyHz, uiBandDb, baseDb);
        return clamp(spreadDb, MIN_EQ_DB, MAX_EQ_DB);
    }

    private float resolveAdaptiveBiquadQ(float bandCenterHz, float gainDb) {
        float baseQ;
        if (bandCenterHz >= 12000f) {
            baseQ = 0.95f;
        } else if (bandCenterHz >= 8000f) {
            baseQ = 1.08f;
        } else if (bandCenterHz >= 4000f) {
            baseQ = 1.35f;
        } else if (bandCenterHz >= 2000f) {
            baseQ = 1.70f;
        } else if (bandCenterHz >= 500f) {
            baseQ = 2.05f;
        } else {
            baseQ = GRAPHIC_EQ_BIQUAD_Q;
        }

        float gainNorm = clamp(Math.abs(gainDb) / Math.max(0.001f, Math.abs(MAX_EQ_DB)), 0f, 1f);
        float adaptedQ = baseQ * (1f - (GRAPHIC_EQ_BIQUAD_GAIN_WIDENING * gainNorm));
        return clamp(adaptedQ, GRAPHIC_EQ_BIQUAD_Q_MIN, GRAPHIC_EQ_BIQUAD_Q);
    }

    private float applyBiquadLateralSpreadDb(
            float targetFrequencyHz,
            @NonNull float[] uiBandDb,
            float baseDb
    ) {
        if (FORCE_LOCALIZED_BAND_SHAPES) {
            return baseDb;
        }

        float neighborDb = computeNeighborBandInfluenceDb(targetFrequencyHz, uiBandDb);

        float highSpreadNorm = 0f;
        float spreadStartHz = 4000f;
        if (targetFrequencyHz > spreadStartHz) {
            float spanOctaves = log2(HIGH_EDGE_FREQUENCY_HZ / spreadStartHz);
            if (spanOctaves > 0.0001f) {
                highSpreadNorm = clamp(log2(targetFrequencyHz / spreadStartHz) / spanOctaves, 0f, 1f);
            }
        }

        float spreadBlend = GRAPHIC_EQ_BIQUAD_SPREAD_BLEND
                + (GRAPHIC_EQ_BIQUAD_SPREAD_HIGH_EXTRA * highSpreadNorm);
        float spreadDb = baseDb + ((neighborDb - baseDb) * spreadBlend);

        float upperTrebleDb = uiBandDb.length > 7 ? clamp(uiBandDb[7], MIN_EQ_DB, MAX_EQ_DB) : 0f;
        float airDb = uiBandDb.length > 8 ? clamp(uiBandDb[8], MIN_EQ_DB, MAX_EQ_DB) : 0f;
        float airDriveDb = (airDb * 0.78f) + (upperTrebleDb * 0.34f);

        if (Math.abs(airDriveDb) > 0.001f && targetFrequencyHz >= GRAPHIC_EQ_BIQUAD_AIR_SPREAD_START_HZ) {
            float distanceOctaves = Math.abs(log2(targetFrequencyHz / GRAPHIC_EQ_BIQUAD_AIR_SPREAD_CENTER_HZ));
            float airShape = gaussian(distanceOctaves, GRAPHIC_EQ_BIQUAD_AIR_SPREAD_WIDTH_OCTAVES);
            spreadDb += airDriveDb * GRAPHIC_EQ_BIQUAD_AIR_SPREAD_DRIVE_RATIO * airShape;
        }

        return spreadDb;
    }

    private float computePeakingBiquadMagnitudeDb(
            float targetFrequencyHz,
            float centerFrequencyHz,
            float q,
            float gainDb,
            float sampleRateHz
    ) {
        double safeSampleRate = Math.max(8000d, sampleRateHz);
        double nyquist = safeSampleRate * 0.5d;
        double safeTarget = clampDouble(targetFrequencyHz, LOW_EDGE_FREQUENCY_HZ, nyquist * 0.999d);
        double safeCenter = clampDouble(centerFrequencyHz, LOW_EDGE_FREQUENCY_HZ, nyquist * 0.999d);
        double safeQ = Math.max(0.1d, q);

        double A = Math.pow(10d, gainDb / 40d);
        double w0 = 2d * Math.PI * safeCenter / safeSampleRate;
        double cosW0 = Math.cos(w0);
        double sinW0 = Math.sin(w0);
        double alpha = sinW0 / (2d * safeQ);

        double b0 = 1d + (alpha * A);
        double b1 = -2d * cosW0;
        double b2 = 1d - (alpha * A);
        double a0 = 1d + (alpha / A);
        double a1 = -2d * cosW0;
        double a2 = 1d - (alpha / A);

        double w = 2d * Math.PI * safeTarget / safeSampleRate;
        double cosW = Math.cos(w);
        double sinW = Math.sin(w);
        double cos2W = Math.cos(2d * w);
        double sin2W = Math.sin(2d * w);

        double numRe = b0 + (b1 * cosW) + (b2 * cos2W);
        double numIm = -((b1 * sinW) + (b2 * sin2W));
        double denRe = a0 + (a1 * cosW) + (a2 * cos2W);
        double denIm = -((a1 * sinW) + (a2 * sin2W));

        double numeratorMag = Math.hypot(numRe, numIm);
        double denominatorMag = Math.max(1.0e-9d, Math.hypot(denRe, denIm));
        double magnitude = numeratorMag / denominatorMag;
        double safeMagnitude = Math.max(GRAPHIC_EQ_BIQUAD_MIN_MAGNITUDE, magnitude);
        return (float) (20d * Math.log10(safeMagnitude));
    }

    private float computeNeighborBandInfluenceDb(float centerFrequencyHz, @NonNull float[] uiBandDb) {
        int count = Math.min(WAVELET_9_BAND_FREQUENCIES_HZ.length, uiBandDb.length);
        if (count <= 0) {
            return 0f;
        }

        float weightedSum = 0f;
        float totalWeight = 0f;
        float maxDistance = GRAPHIC_EQ_NEIGHBOR_WIDTH_OCTAVES * 2.2f;
        for (int i = 0; i < count; i++) {
            float anchorHz = WAVELET_9_BAND_FREQUENCIES_HZ[i];
            float distanceOctaves = Math.abs(log2(centerFrequencyHz / anchorHz));
            if (distanceOctaves > maxDistance) {
                continue;
            }
            float weight = gaussian(distanceOctaves, GRAPHIC_EQ_NEIGHBOR_WIDTH_OCTAVES);
            weightedSum += uiBandDb[i] * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0001f) {
            return 0f;
        }
        return weightedSum / totalWeight;
    }

    private float computeCenterBandInfluenceDb(float centerFrequencyHz, @NonNull float[] uiBandDb) {
        int count = Math.min(WAVELET_9_BAND_FREQUENCIES_HZ.length, uiBandDb.length);
        if (count <= 0) {
            return 0f;
        }

        float weightedSum = 0f;
        float totalWeight = 0f;
        float maxDistance = GRAPHIC_EQ_CENTER_WIDTH_OCTAVES * 2.4f;
        for (int i = 0; i < count; i++) {
            float anchorHz = WAVELET_9_BAND_FREQUENCIES_HZ[i];
            float distanceOctaves = Math.abs(log2(centerFrequencyHz / anchorHz));
            if (distanceOctaves > maxDistance) {
                continue;
            }
            float weight = gaussian(distanceOctaves, GRAPHIC_EQ_CENTER_WIDTH_OCTAVES);
            weightedSum += uiBandDb[i] * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0001f) {
            return 0f;
        }
        return weightedSum / totalWeight;
    }

    private float computeAnchorLockFactor(float centerFrequencyHz) {
        float safeFrequencyHz = Math.max(LOW_EDGE_FREQUENCY_HZ, centerFrequencyHz);
        float nearestDistanceOctaves = Float.MAX_VALUE;

        for (int anchorHz : WAVELET_9_BAND_FREQUENCIES_HZ) {
            float distanceOctaves = Math.abs(log2(safeFrequencyHz / anchorHz));
            if (distanceOctaves < nearestDistanceOctaves) {
                nearestDistanceOctaves = distanceOctaves;
            }
        }

        float t = clamp(nearestDistanceOctaves / GRAPHIC_EQ_ANCHOR_LOCK_WIDTH_OCTAVES, 0f, 1f);
        return 1f - smoothstep(t);
    }

    private float computeLowBoostAmountDb(@NonNull float[] uiBandDb) {
        float sub = uiBandDb.length > 0 ? Math.max(0f, uiBandDb[0]) : 0f;
        float midBass = uiBandDb.length > 1 ? Math.max(0f, uiBandDb[1]) : 0f;
        float upperBass = uiBandDb.length > 2 ? Math.max(0f, uiBandDb[2]) : 0f;
        return (sub * 0.6f) + (midBass * 0.3f) + (upperBass * 0.1f);
    }

    private float computeBassTunerExtraDb(
            float centerFrequencyHz,
            float bassDb,
            float cutoffFrequencyHz,
            int bassType,
            @NonNull BassOutputTuning outputTuning
    ) {
        if (bassDb <= 0f) {
            return 0f;
        }

        float frequency = Math.max(LOW_EDGE_FREQUENCY_HZ, centerFrequencyHz);
        float cutoff = clamp(cutoffFrequencyHz, BASS_TUNER_CUTOFF_MIN_HZ, BASS_TUNER_CUTOFF_MAX_HZ);

        float lowerBoundHz = (float) (cutoff / Math.pow(2d, BASS_TUNER_ACTIVE_BELOW_OCTAVES));
        float upperBoundHz = (float) (cutoff * Math.pow(2d, BASS_TUNER_ACTIVE_ABOVE_OCTAVES));

        float curveGate = 1f;
        if (frequency < lowerBoundHz) {
            float outsideOctaves = log2(lowerBoundHz / frequency);
            curveGate = 1f - smoothstep(outsideOctaves / BASS_TUNER_OUTSIDE_FALLOFF_OCTAVES);
        } else if (frequency > upperBoundHz) {
            float outsideOctaves = log2(frequency / upperBoundHz);
            curveGate = 1f - smoothstep(outsideOctaves / BASS_TUNER_OUTSIDE_FALLOFF_OCTAVES);
        }
        curveGate = clamp(curveGate, 0f, 1f);

        float baseWeight;
        if (frequency <= cutoff) {
            float octavesBelowCutoff = log2(cutoff / frequency);
            float subBassBlend = clamp(
                    octavesBelowCutoff / BASS_TUNER_ACTIVE_BELOW_OCTAVES,
                    0f,
                    1f
            );
            baseWeight = BASS_TUNER_CUTOFF_BASE_WEIGHT
                    + ((1f - BASS_TUNER_CUTOFF_BASE_WEIGHT) * subBassBlend);
        } else {
            float octavesAboveCutoff = log2(frequency / cutoff);
            float transition = 1f - smoothstep(octavesAboveCutoff / BASS_TUNER_ACTIVE_ABOVE_OCTAVES);
            baseWeight = BASS_TUNER_CUTOFF_BASE_WEIGHT * clamp(transition, 0f, 1f);
        }
        float baseScale = FORCE_BASS_TUNER_NO_ATTENUATION
            ? Math.max(1f, outputTuning.baseScale)
            : outputTuning.baseScale;
        baseWeight *= baseScale;

        if (FORCE_BASS_TUNER_NO_ATTENUATION) {
            float ultraSubPivot = clamp(
                cutoff * NO_DUCKING_ULTRA_SUB_RATIO,
                28f,
                85f
            );
            if (frequency < ultraSubPivot) {
            float t = clamp(
                (ultraSubPivot - frequency)
                    / Math.max(0.001f, ultraSubPivot - LOW_EDGE_FREQUENCY_HZ),
                0f,
                1f
            );
            float suppress = 1f - ((1f - NO_DUCKING_ULTRA_SUB_FLOOR) * t);
            baseWeight *= suppress;
            }
        }

        float punchCenterHz = clamp(
                cutoff * BASS_TUNER_PUNCH_CENTER_RATIO,
                LOW_EDGE_FREQUENCY_HZ,
                HIGH_EDGE_FREQUENCY_HZ
        );
        float punchDistanceOctaves = Math.abs(log2(frequency / punchCenterHz));
        float punchWeight = BASS_TUNER_PUNCH_INTENSITY
                * gaussian(punchDistanceOctaves, BASS_TUNER_PUNCH_WIDTH_OCTAVES);

        float totalWeight;
        switch (normalizeBassType(bassType)) {
            case BASS_TYPE_TRANSIENT_COMPRESSOR:
                totalWeight = computeTransientCompressorWeight(frequency, cutoff, baseWeight, outputTuning);
                break;

            case BASS_TYPE_SUSTAIN_COMPRESSOR:
                totalWeight = computeSustainCompressorWeight(frequency, cutoff, baseWeight, outputTuning);
                break;

            case BASS_TYPE_NATURAL:
            default:
                float naturalPunchScale = FORCE_BASS_TUNER_NO_ATTENUATION
                        ? Math.max(1f, outputTuning.naturalPunchScale)
                        : outputTuning.naturalPunchScale;
                totalWeight = baseWeight + (punchWeight * naturalPunchScale);
                break;
        }

        float maxBoost;
        if (FORCE_BASS_TUNER_NO_ATTENUATION) {
            maxBoost = Math.max(BASS_TUNER_MAX_BOOST_MULTIPLIER, 1.24f);
        } else {
            maxBoost = Math.min(BASS_TUNER_MAX_BOOST_MULTIPLIER, outputTuning.maxBoostMultiplier);
        }
        totalWeight = clamp(totalWeight, 0f, maxBoost) * curveGate;

        float drivenBassDb = FORCE_BASS_TUNER_NO_ATTENUATION
                ? bassDb
                : bassDb * outputTuning.dbDrive;

        float focusBoostDb = drivenBassDb * totalWeight;
        float focusWeight = maxBoost <= 0.0001f ? 0f : clamp(totalWeight / maxBoost, 0f, 1f);

        float globalLiftDb = drivenBassDb * BASS_TUNER_GLOBAL_LIFT_FACTOR;
        float compensatedLiftFactor = 1f - (BASS_TUNER_OFF_CURVE_RECOVERY_FACTOR * (1f - focusWeight));
        if (frequency >= BASS_TUNER_MID_HIGH_PRESERVE_FROM_HZ) {
            compensatedLiftFactor = Math.max(BASS_TUNER_MID_HIGH_MIN_LIFT_FACTOR, compensatedLiftFactor);
        }
        float compensatedGlobalLiftDb = globalLiftDb * clamp(compensatedLiftFactor, 0f, 1f);
        float midHighAssistDb = computeBassTunerMidHighAssistDb(frequency, drivenBassDb);

        float combinedDb = focusBoostDb + compensatedGlobalLiftDb + midHighAssistDb;
        float safetyCeilingDb = FORCE_BASS_TUNER_NO_ATTENUATION
            ? Math.max(outputTuning.hardCeilingDb, BASS_TUNER_MAX_COMBINED_BOOST_DB)
            : outputTuning.hardCeilingDb;
        return clamp(combinedDb, 0f, safetyCeilingDb);
    }

    private float computeBassTunerMidHighAssistDb(float frequencyHz, float drivenBassDb) {
        if (drivenBassDb <= 0f || frequencyHz < BASS_TUNER_MID_HIGH_PRESERVE_FROM_HZ) {
            return 0f;
        }

        float octavesAbove = log2(frequencyHz / BASS_TUNER_MID_HIGH_PRESERVE_FROM_HZ);
        float highFalloff = smoothstep(octavesAbove / BASS_TUNER_MID_HIGH_ASSIST_SPAN_OCTAVES);
        float shelfWeight = 1f - (BASS_TUNER_MID_HIGH_ASSIST_FALLOFF * highFalloff);
        shelfWeight = clamp(shelfWeight, 0.58f, 1f);

        return drivenBassDb * BASS_TUNER_MID_HIGH_ASSIST_FACTOR * shelfWeight;
    }

        private float computeNoDuckingPresenceCompensationDb() {
        // Mantiene el Bass Tuner enfocado en su rango sin elevar brillo/aire global.
        return 0f;
        }

    private void applyAntiDuckingSafeguards(
            @NonNull float[] bandDb,
            @NonNull float[] centerFrequenciesHz,
            @NonNull BassOutputTuning outputTuning
    ) {
        if (FORCE_LOCALIZED_BAND_SHAPES) {
            return;
        }

        int count = Math.min(bandDb.length, centerFrequenciesHz.length);
        if (count <= 0) {
            return;
        }

        for (int i = 0; i < count; i++) {
            bandDb[i] = applySoftBoostCeiling(bandDb[i], centerFrequenciesHz[i], outputTuning);
        }

        float positiveEnergy = computePositiveBoostEnergy(bandDb, centerFrequenciesHz, count);
        if (positiveEnergy <= outputTuning.maxPositiveEnergy) {
            return;
        }

        float overflowRatio = clamp(
                (positiveEnergy - outputTuning.maxPositiveEnergy)
                        / Math.max(0.001f, outputTuning.maxPositiveEnergy),
                0f,
                1.6f
        );

        float reductionDb = overflowRatio * DUCKING_GLOBAL_OVERFLOW_REDUCTION_DB;
        if (reductionDb <= 0f) {
            return;
        }

        for (int i = 0; i < count; i++) {
            float gainDb = bandDb[i];
            if (gainDb <= 0f) {
                continue;
            }

            float centerHz = centerFrequenciesHz[i];
            float bandReduction = centerHz <= DUCKING_LOW_BAND_MAX_HZ
                    ? reductionDb
                    : reductionDb * 0.55f;

            float adjusted = Math.max(0f, gainDb - bandReduction);
            bandDb[i] = applySoftBoostCeiling(adjusted, centerHz, outputTuning);
        }
    }

    private float applySoftBoostCeiling(float gainDb, float centerHz, @NonNull BassOutputTuning outputTuning) {
        if (gainDb <= 0f) {
            return gainDb;
        }

        float softCeiling = centerHz <= DUCKING_LOW_BAND_MAX_HZ
                ? outputTuning.lowBandSoftCeilingDb
                : outputTuning.fullBandSoftCeilingDb;

        float adjusted = gainDb;
        if (adjusted > softCeiling) {
            adjusted = softCeiling + ((adjusted - softCeiling) * DUCKING_SOFT_KNEE_RATIO);
        }
        return Math.min(adjusted, outputTuning.hardCeilingDb);
    }

    private float computePositiveBoostEnergy(
            @NonNull float[] bandDb,
            @NonNull float[] centerFrequenciesHz,
            int count
    ) {
        float energy = 0f;
        for (int i = 0; i < count; i++) {
            float gainDb = bandDb[i];
            if (gainDb <= 0f) {
                continue;
            }

            float frequencyHz = centerFrequenciesHz[i];
            float weight;
            if (frequencyHz <= 120f) {
                weight = 1.55f;
            } else if (frequencyHz <= DUCKING_LOW_BAND_MAX_HZ) {
                weight = 1.35f;
            } else if (frequencyHz <= 1200f) {
                weight = 1.0f;
            } else {
                weight = 0.72f;
            }

            energy += gainDb * gainDb * weight;
        }
        return energy;
    }

    private float computeTransientCompressorWeight(
            float frequency,
            float cutoff,
            float naturalBaseWeight,
            @NonNull BassOutputTuning outputTuning
    ) {
        float weight = naturalBaseWeight;

        if (frequency < cutoff) {
            float deepSub = clamp(log2(cutoff / frequency) / 1.8f, 0f, 1f);
            float subTrimScale = FORCE_BASS_TUNER_NO_ATTENUATION
                    ? Math.min(1f, outputTuning.transientSubTrimScale)
                    : outputTuning.transientSubTrimScale;
            weight *= 1f - ((0.34f * subTrimScale) * deepSub);
        }

        float punchCenter = clamp(cutoff * 1.03f, LOW_EDGE_FREQUENCY_HZ, HIGH_EDGE_FREQUENCY_HZ);
        float punchDistance = Math.abs(log2(frequency / punchCenter));
        float transientPunchScale = FORCE_BASS_TUNER_NO_ATTENUATION
                ? Math.max(1f, outputTuning.transientPunchScale)
                : outputTuning.transientPunchScale;
        float punchBoost = 0.42f * transientPunchScale * gaussian(punchDistance, 0.28f);

        float upperTrim = 1f;
        if (frequency > cutoff) {
            float octAbove = log2(frequency / cutoff);
            upperTrim = 1f - smoothstep(octAbove / 1.05f);
        }

        return (weight + punchBoost) * clamp(upperTrim, 0f, 1f);
    }

    private float computeSustainCompressorWeight(
            float frequency,
            float cutoff,
            float naturalBaseWeight,
            @NonNull BassOutputTuning outputTuning
    ) {
        float weight = naturalBaseWeight;

        if (frequency <= cutoff) {
            float deepSub = clamp(log2(cutoff / frequency) / 2.4f, 0f, 1f);
            float sustainRumbleScale = FORCE_BASS_TUNER_NO_ATTENUATION
                    ? Math.max(1f, outputTuning.sustainRumbleScale)
                    : outputTuning.sustainRumbleScale;
            weight += 0.26f * sustainRumbleScale * deepSub;
        }

        float rumbleCenter = clamp(cutoff * 0.75f, LOW_EDGE_FREQUENCY_HZ, HIGH_EDGE_FREQUENCY_HZ);
        float rumbleDistance = Math.abs(log2(frequency / rumbleCenter));
        float sustainRumbleScale = FORCE_BASS_TUNER_NO_ATTENUATION
                ? Math.max(1f, outputTuning.sustainRumbleScale)
                : outputTuning.sustainRumbleScale;
        float rumbleBoost = 0.18f * sustainRumbleScale * gaussian(rumbleDistance, 0.55f);

        return weight + rumbleBoost;
    }

    @Nullable
    private AudioDeviceInfo readCurrentPreferredOutput() {
        if (audioManager == null) {
            return null;
        }

        try {
            return selectPreferredOutput(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo determinar salida de audio actual para calibracion", throwable);
            return null;
        }
    }

    @NonNull
    private BassOutputTuning resolveBassOutputTuning(@Nullable AudioDeviceInfo selectedOutput) {
        if (selectedOutput == null) {
            return BASS_TUNING_SPEAKER;
        }

        int type = selectedOutput.getType();
        if (isBluetoothType(type) || isWiredType(type)) {
            return BASS_TUNING_HEADPHONES;
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) {
            return BASS_TUNING_SPEAKER;
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
            return BASS_TUNING_EARPIECE;
        }
        return BASS_TUNING_GENERIC;
    }

    private void applyBassBoost(SharedPreferences prefs) {
        if (bassBoost == null) {
            return;
        }

        try {
            if (!USE_HARDWARE_BASS_BOOST) {
                bassBoost.setStrength((short) 0);
                bassBoost.setEnabled(false);
                return;
            }

            float bassDb = clamp(prefs.getFloat(KEY_BASS_DB, 0f), 0f, BASS_DB_MAX);
            if (bassDb <= 0.01f) {
                bassBoost.setStrength((short) 0);
                bassBoost.setEnabled(false);
                return;
            }

            float normalizedBass = clamp(bassDb / BASS_DB_MAX, 0f, 1f);
            int strength = Math.round((float) Math.pow(normalizedBass, HARDWARE_BASS_BOOST_CURVE_EXPONENT) * MAX_HARDWARE_BASS_BOOST_STRENGTH);
            strength = (int) clamp(strength, 0f, MAX_HARDWARE_BASS_BOOST_STRENGTH);
            bassBoost.setStrength((short) strength);
            bassBoost.setEnabled(true);
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando bass boost", throwable);
        }
    }

    private void applyLoudnessCompensation(
            SharedPreferences prefs,
            boolean includeEqAndBass,
            boolean spatialEnabled
    ) {
        if (loudnessEnhancer == null) {
            return;
        }

        try {
            if (!FORCE_NO_DUCKING_GAIN_COMPENSATION) {
                disableLoudnessPath();
                return;
            }

            float targetGainDb = 0f;

            if (includeEqAndBass) {
                // Makeup gain controlado para evitar caida de volumen al aumentar bandas/bass.
                targetGainDb += computeEqBassMakeupCompensationDb(prefs);
                targetGainDb += computeBassTuneHighBonusDb(prefs);
            }

            if (spatialEnabled) {
                targetGainDb += computeSpatialLoudnessCompensationDb(prefs, readCurrentPreferredOutput());
            }

            targetGainDb = clamp(targetGainDb, 0f, NO_DUCKING_GAIN_MAX_DB);
            if (targetGainDb <= 0.01f) {
                disableLoudnessPath();
                return;
            }

            int targetGainMb = Math.round(targetGainDb * 100f);
            loudnessEnhancer.setTargetGain(targetGainMb);
            loudnessEnhancer.setEnabled(targetGainMb > 0);
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando compensacion de loudness", throwable);
        }
    }

    private float computeEqBassMakeupCompensationDb(@NonNull SharedPreferences prefs) {
        float[] uiBandDb = readUiBandDbFromPrefs(prefs);
        float weightedBandAutoGainDb = 0f;
        int bandCount = Math.min(uiBandDb.length, NO_DUCKING_BAND_AUTOGAIN_DB.length);
        for (int i = 0; i < bandCount; i++) {
            float bandBoostDb = Math.max(0f, uiBandDb[i]);
            if (bandBoostDb <= 0.01f) {
                continue;
            }

            float bandNorm = clamp(bandBoostDb / Math.max(0.001f, MAX_EQ_DB), 0f, 1f);
            weightedBandAutoGainDb += bandNorm * NO_DUCKING_BAND_AUTOGAIN_DB[i];
        }

        float bassDb = clamp(prefs.getFloat(KEY_BASS_DB, 0f), 0f, BASS_DB_MAX);
        float bassAutoGainDb = computeDynamicBassTunerAutoGainDb(prefs, bassDb);
        boolean bassAtMax = bassDb >= BASS_TUNER_MAX_ACTIVE_THRESHOLD_DB;
    float bassFrequencySlider = normalizeBassFrequencySlider(
        prefs.getFloat(KEY_BASS_FREQUENCY_HZ, BASS_FREQUENCY_DEFAULT_HZ)
    );
    float cutoffHz = bassSliderToCutoffHz(bassFrequencySlider);
    boolean lowCutoffDirectBoostActive = bassAtMax && cutoffHz >= 25f && cutoffHz <= 69f;

        if (bassAtMax) {
            weightedBandAutoGainDb *= BASS_TUNER_MAX_EQ_AUTOGAIN_BAND_FACTOR;
            bassAutoGainDb *= BASS_TUNER_MAX_EQ_AUTOGAIN_BASS_FACTOR;
        }

        if (weightedBandAutoGainDb <= 0.01f && bassAutoGainDb <= 0.01f) {
            return 0f;
        }

        float makeupFloorDb = bassAtMax ? 0f : NO_DUCKING_EQ_MAKEUP_FLOOR_DB;

        float makeupDb = makeupFloorDb
                + weightedBandAutoGainDb
                + bassAutoGainDb
                + NO_DUCKING_EQ_EXTRA_AUTOGAIN_DB;

        if (lowCutoffDirectBoostActive) {
            // Este boost se aplica post-factor para que el realce de subgrave sea realmente audible.
            makeupDb += BASS_TUNER_LOW_CUTOFF_DIRECT_BOOST_DB;
        }

        if (bassAtMax) {
            float bassAtMaxCapDb = lowCutoffDirectBoostActive
                    ? BASS_TUNER_MAX_EQ_AUTOGAIN_LOW_CUTOFF_CAP_DB
                    : BASS_TUNER_MAX_EQ_AUTOGAIN_CAP_DB;
            makeupDb = Math.min(makeupDb, bassAtMaxCapDb);
        }

        return clamp(makeupDb, 0f, NO_DUCKING_EQ_MAKEUP_CAP_DB);
    }

    private float computeBassTuneHighBonusDb(@NonNull SharedPreferences prefs) {
        float bassDb = clamp(prefs.getFloat(KEY_BASS_DB, 0f), 0f, BASS_DB_MAX);
        if (bassDb <= 0.01f) {
            return 0f;
        }

        float bassFrequencySlider = normalizeBassFrequencySlider(
                prefs.getFloat(KEY_BASS_FREQUENCY_HZ, BASS_FREQUENCY_DEFAULT_HZ)
        );
        float cutoffHz = bassSliderToCutoffHz(bassFrequencySlider);
        if (cutoffHz < BASS_TUNER_HIGH_CUTOFF_GAIN_THRESHOLD_HZ) {
            return 0f;
        }

        return BASS_TUNER_HIGH_CUTOFF_GAIN_BONUS_DB;
    }

    private float computeDynamicBassTunerAutoGainDb(@NonNull SharedPreferences prefs, float bassDb) {
        float bassNorm = clamp(bassDb / Math.max(0.001f, BASS_DB_MAX), 0f, 1f);
        if (bassNorm <= 0.01f) {
            return 0f;
        }

        float bassFrequencySlider = normalizeBassFrequencySlider(
                prefs.getFloat(KEY_BASS_FREQUENCY_HZ, BASS_FREQUENCY_DEFAULT_HZ)
        );
        float cutoffHz = bassSliderToCutoffHz(bassFrequencySlider);
        if (cutoffHz < BASS_TUNER_CUTOFF_MIN_HZ) {
            return 0f;
        }

        float lowCutoffBoostNorm;
        if (cutoffHz <= BASS_TUNER_LOW_CUTOFF_BOOST_MAX_HZ) {
            lowCutoffBoostNorm = 1f;
        } else if (cutoffHz >= BASS_TUNER_LOW_CUTOFF_BOOST_END_HZ) {
            lowCutoffBoostNorm = 0f;
        } else {
            lowCutoffBoostNorm = 1f - ((cutoffHz - BASS_TUNER_LOW_CUTOFF_BOOST_MAX_HZ)
                    / Math.max(0.001f, BASS_TUNER_LOW_CUTOFF_BOOST_END_HZ - BASS_TUNER_LOW_CUTOFF_BOOST_MAX_HZ));
        }

        float baseAutoGainDb = (BASS_TUNER_DYNAMIC_AUTOGAIN_MAX_DB * bassNorm)
                + (BASS_TUNER_LOW_CUTOFF_EXTRA_AUTOGAIN_DB * bassNorm * lowCutoffBoostNorm);

        float cutoffReductionDb;
        if (cutoffHz <= BASS_TUNER_AUTOGAIN_CUTOFF_3_HZ) {
            cutoffReductionDb = BASS_TUNER_AUTOGAIN_REDUCTION_3_DB;
        } else if (cutoffHz <= BASS_TUNER_AUTOGAIN_CUTOFF_2_HZ) {
            cutoffReductionDb = BASS_TUNER_AUTOGAIN_REDUCTION_2_DB;
        } else if (cutoffHz <= BASS_TUNER_AUTOGAIN_CUTOFF_1_HZ) {
            cutoffReductionDb = BASS_TUNER_AUTOGAIN_REDUCTION_1_DB;
        } else {
            cutoffReductionDb = 0f;
        }

        float highCutoffReductionDb = 0f;
        if (cutoffHz >= BASS_TUNER_AUTOGAIN_HIGH_REDUCTION_STEP_2_HZ) {
            highCutoffReductionDb = BASS_TUNER_AUTOGAIN_HIGH_REDUCTION_STEP_2_DB;
        } else if (cutoffHz > BASS_TUNER_AUTOGAIN_HIGH_REDUCTION_STEP_1_HZ) {
            highCutoffReductionDb = BASS_TUNER_AUTOGAIN_HIGH_REDUCTION_STEP_1_DB;
        }

        return Math.max(0f, baseAutoGainDb - ((cutoffReductionDb + highCutoffReductionDb) * bassNorm));
    }

    private float computeSpatialLoudnessCompensationDb(
            @NonNull SharedPreferences prefs,
            @Nullable AudioDeviceInfo output
    ) {
        int rawStrength = prefs.getInt(KEY_SPATIAL_STRENGTH, SPATIAL_STRENGTH_DEFAULT);
        if (rawStrength <= 0) {
            rawStrength = SPATIAL_STRENGTH_DEFAULT;
        }
        float strengthNorm = clamp(rawStrength / 1000f, 0f, 1f);
        if (strengthNorm <= 0f) {
            return 0f;
        }

        float gainDb = SPATIAL_LOUDNESS_BASE_DB + (SPATIAL_LOUDNESS_DYNAMIC_DB * strengthNorm);
        if (isHeadphoneLikeOutput(output)) {
            gainDb -= SPATIAL_LOUDNESS_HEADPHONE_TRIM_DB;
        } else {
            gainDb += SPATIAL_LOUDNESS_SPEAKER_BONUS_DB;
        }

        return Math.max(0f, gainDb);
    }

    private void applySpatializer(
            SharedPreferences prefs,
            boolean spatialPathEnabled,
            boolean spatialEnabled,
            int reverbLevel,
            boolean eqEnabled,
            boolean forceSpatialRearm
    ) {
        if (virtualizer == null && presetReverb == null && environmentalReverb == null) {
            return;
        }

        if (!spatialPathEnabled) {
            disableSpatialPath();
            return;
        }

        if (forceSpatialRearm) {
            disableSpatialPath();
        }

        try {
            int configuredStrength = prefs.getInt(KEY_SPATIAL_STRENGTH, SPATIAL_STRENGTH_DEFAULT);
            if (spatialEnabled && configuredStrength <= 0) {
                configuredStrength = SPATIAL_STRENGTH_DEFAULT;
            }
            configuredStrength = Math.max(SPATIAL_STRENGTH_MIN, Math.min(SPATIAL_STRENGTH_MAX, configuredStrength));

            int strength = configuredStrength;
            if (!spatialEnabled) {
                // Reverb sin Spatial: fuerza una intensidad moderada para que el cambio sea perceptible.
                strength = Math.max(360, Math.min(620, configuredStrength));
            }

            int shapedStrength = Math.round((float) Math.pow(strength / 1000f, 0.56f) * 1000f);
            shapedStrength = Math.max(SPATIAL_STRENGTH_MIN, Math.min(SPATIAL_STRENGTH_MAX, shapedStrength));
            if (strength > 0) {
                shapedStrength = Math.max(spatialEnabled ? SPATIAL_WIDE_STRENGTH_FLOOR : 320, shapedStrength);
            }

            AudioDeviceInfo selectedOutput = readCurrentPreferredOutput();

            int virtualizerStrength = shapedStrength + computeReverbVirtualizerBoost(reverbLevel, spatialEnabled);
            if (spatialEnabled && eqEnabled) {
                virtualizerStrength += SPATIAL_WITH_EQ_VIRTUALIZER_BOOST;
            }
            virtualizerStrength = Math.max(SPATIAL_STRENGTH_MIN, Math.min(SPATIAL_STRENGTH_MAX, virtualizerStrength));

            applyVirtualizerStrength(virtualizerStrength, selectedOutput, spatialEnabled);
            applySpatialReverbPreset(strength, selectedOutput, reverbLevel);
            applyEnvironmentalReverb(strength, selectedOutput, reverbLevel, spatialEnabled);
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando spatial audio", throwable);
        }
    }

    private int computeReverbVirtualizerBoost(int reverbLevel, boolean spatialEnabled) {
        int normalizedReverbLevel = normalizeReverbLevel(reverbLevel);
        if (normalizedReverbLevel == REVERB_LEVEL_OFF) {
            return 0;
        }

        int boost;
        if (normalizedReverbLevel == REVERB_LEVEL_LIGHT) {
            boost = 70;
        } else if (normalizedReverbLevel == REVERB_LEVEL_STRONG) {
            boost = 210;
        } else {
            boost = 130;
        }

        if (!spatialEnabled) {
            boost = Math.max(boost, 150);
        }
        return boost;
    }

    private void applyVirtualizerStrength(int strength, @Nullable AudioDeviceInfo output, boolean spatialEnabled) {
        if (virtualizer == null) {
            return;
        }

        try {
            int tunedStrength = strength;
            if (spatialEnabled) {
                tunedStrength = Math.round(tunedStrength * SPATIAL_WIDE_STRENGTH_BOOST);
                tunedStrength = Math.max(SPATIAL_WIDE_STRENGTH_FLOOR, tunedStrength);
            } else {
                tunedStrength = Math.min(REVERB_ONLY_STRENGTH_CAP, tunedStrength);
            }
            tunedStrength = Math.max(SPATIAL_STRENGTH_MIN, Math.min(SPATIAL_STRENGTH_MAX, tunedStrength));

            if (virtualizer.getStrengthSupported()) {
                virtualizer.setStrength((short) tunedStrength);
                virtualizer.setEnabled(tunedStrength > 0);
            } else {
                virtualizer.setEnabled(true);
            }

            if (spatialEnabled) {
                int preferredMode = isHeadphoneLikeOutput(output)
                        ? Virtualizer.VIRTUALIZATION_MODE_BINAURAL
                        : Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL;

                boolean supportsPreferredMode = virtualizer.canVirtualize(
                        AudioFormat.CHANNEL_OUT_STEREO,
                        preferredMode
                );

                if (supportsPreferredMode) {
                    virtualizer.forceVirtualizationMode(preferredMode);
                } else {
                    virtualizer.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_AUTO);
                }
            } else {
                virtualizer.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_AUTO);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando virtualizer", throwable);
        }
    }

    private void applySpatialReverbPreset(int strength, @Nullable AudioDeviceInfo output, int reverbLevel) {
        if (presetReverb == null) {
            return;
        }

        try {
            short preset = resolveSpatialReverbPreset(strength, output, reverbLevel);
            presetReverb.setPreset(preset);
            presetReverb.setEnabled(preset != PresetReverb.PRESET_NONE);
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando reverb espacial", throwable);
        }
    }

    private void applyEnvironmentalReverb(
            int strength,
            @Nullable AudioDeviceInfo output,
            int reverbLevel,
            boolean spatialEnabled
    ) {
        if (environmentalReverb == null) {
            return;
        }

        int normalizedReverbLevel = normalizeReverbLevel(reverbLevel);

        try {
            if (normalizedReverbLevel == REVERB_LEVEL_OFF || strength <= 0) {
                environmentalReverb.setEnabled(false);
                return;
            }

            int roomLevel;
            int roomHfLevel;
            int decayTime;
            int decayHfRatio;
            int reflectionsLevel;
            int reflectionsDelay;
            int reverbLevelMb;
            int reverbDelay;
            int diffusion;
            int density;

            if (normalizedReverbLevel == REVERB_LEVEL_LIGHT) {
                roomLevel = -1000;
                roomHfLevel = -1700;
                decayTime = 920;
                decayHfRatio = 1120;
                reflectionsLevel = -2200;
                reflectionsDelay = 20;
                reverbLevelMb = -1800;
                reverbDelay = 48;
                diffusion = 760;
                density = 780;
            } else if (normalizedReverbLevel == REVERB_LEVEL_STRONG) {
                roomLevel = -620;
                roomHfLevel = -920;
                decayTime = 2180;
                decayHfRatio = 1360;
                reflectionsLevel = -1220;
                reflectionsDelay = 34;
                reverbLevelMb = -760;
                reverbDelay = 74;
                diffusion = 980;
                density = 980;
            } else {
                roomLevel = -820;
                roomHfLevel = -1320;
                decayTime = 1460;
                decayHfRatio = 1220;
                reflectionsLevel = -1720;
                reflectionsDelay = 26;
                reverbLevelMb = -1220;
                reverbDelay = 60;
                diffusion = 900;
                density = 900;
            }

            float strengthNorm = clamp(strength / 1000f, 0f, 1f);
            int dynamicDecay = Math.round((spatialEnabled ? 650f : 420f) * strengthNorm);
            int dynamicWet = Math.round(260f * strengthNorm);

            decayTime += dynamicDecay;
            reverbLevelMb += dynamicWet;

            if (isHeadphoneLikeOutput(output)) {
                roomHfLevel -= 260;
                reflectionsLevel += 120;
                reverbLevelMb += 120;
            } else {
                roomLevel += 120;
            }

            environmentalReverb.setRoomLevel(clampToShortRange(roomLevel, -9000, 0));
            environmentalReverb.setRoomHFLevel(clampToShortRange(roomHfLevel, -9000, 0));
            environmentalReverb.setDecayTime(clampInt(decayTime, 100, 7000));
            environmentalReverb.setDecayHFRatio(clampToShortRange(decayHfRatio, 100, 2000));
            environmentalReverb.setReflectionsLevel(clampToShortRange(reflectionsLevel, -9000, 1000));
            environmentalReverb.setReflectionsDelay(clampInt(reflectionsDelay, 0, 300));
            environmentalReverb.setReverbLevel(clampToShortRange(reverbLevelMb, -9000, 2000));
            environmentalReverb.setReverbDelay(clampInt(reverbDelay, 0, 100));
            environmentalReverb.setDiffusion(clampToShortRange(diffusion, 0, 1000));
            environmentalReverb.setDensity(clampToShortRange(density, 0, 1000));
            environmentalReverb.setEnabled(true);
        } catch (Throwable throwable) {
            Log.w(TAG, "Error aplicando environmental reverb", throwable);
        }
    }

    private short resolveSpatialReverbPreset(int strength, @Nullable AudioDeviceInfo output, int reverbLevel) {
        int normalizedReverbLevel = normalizeReverbLevel(reverbLevel);
        if (normalizedReverbLevel == REVERB_LEVEL_OFF || strength <= 0) {
            return PresetReverb.PRESET_NONE;
        }

        int tunedStrength = strength;
        if (normalizedReverbLevel == REVERB_LEVEL_LIGHT) {
            tunedStrength -= 260;
        } else if (normalizedReverbLevel == REVERB_LEVEL_STRONG) {
            tunedStrength += 180;
        }
        tunedStrength = Math.max(SPATIAL_STRENGTH_MIN, Math.min(SPATIAL_STRENGTH_MAX, tunedStrength));

        boolean headphoneLike = isHeadphoneLikeOutput(output);

        if (tunedStrength >= 780) {
            return PresetReverb.PRESET_LARGEHALL;
        }
        if (tunedStrength >= 560) {
            return headphoneLike ? PresetReverb.PRESET_MEDIUMHALL : PresetReverb.PRESET_LARGEROOM;
        }
        if (tunedStrength >= 320) {
            return headphoneLike ? PresetReverb.PRESET_LARGEROOM : PresetReverb.PRESET_MEDIUMROOM;
        }
        return PresetReverb.PRESET_SMALLROOM;
    }

    private void disableSpatialPath() {
        try {
            if (virtualizer != null) {
                virtualizer.setStrength((short) 0);
                virtualizer.setEnabled(false);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo desactivar virtualizer", throwable);
        }

        try {
            if (presetReverb != null) {
                presetReverb.setPreset(PresetReverb.PRESET_NONE);
                presetReverb.setEnabled(false);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo desactivar preset reverb", throwable);
        }

        try {
            if (environmentalReverb != null) {
                environmentalReverb.setEnabled(false);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo desactivar environmental reverb", throwable);
        }
    }

    private void stopAndRelease() {
        clearAppliedAudioStateTracking();
        releaseEffects();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private void releaseEffects() {
        if (dynamicsProcessing != null) {
            dynamicsProcessing.release();
            dynamicsProcessing = null;
        }
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        if (bassBoost != null) {
            bassBoost.release();
            bassBoost = null;
        }
        if (loudnessEnhancer != null) {
            loudnessEnhancer.release();
            loudnessEnhancer = null;
        }
        if (virtualizer != null) {
            virtualizer.release();
            virtualizer = null;
        }
        if (presetReverb != null) {
            presetReverb.release();
            presetReverb = null;
        }
        if (environmentalReverb != null) {
            environmentalReverb.release();
            environmentalReverb = null;
        }

        wasSpatialEqStackActive = false;
        usingDynamicsProcessingPath = false;
    }

    private void registerOutputDeviceCallback() {
        if (audioManager == null) {
            return;
        }

        try {
            audioManager.registerAudioDeviceCallback(outputDeviceCallback, null);
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo registrar callback de dispositivos de audio", throwable);
        }
    }

    private void unregisterOutputDeviceCallback() {
        if (audioManager == null) {
            return;
        }

        try {
            audioManager.unregisterAudioDeviceCallback(outputDeviceCallback);
        } catch (Throwable throwable) {
            Log.w(TAG, "No se pudo remover callback de dispositivos de audio", throwable);
        }
    }

    private void synchronizeProfileForCurrentOutput() {
        if (preferences == null) {
            preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        }

        AudioDeviceInfo selectedOutput = null;
        if (audioManager != null) {
            try {
                selectedOutput = selectPreferredOutput(
                        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                );
                if (selectedOutput != null) {
                    int selectedType = selectedOutput.getType();
                    if (isBluetoothType(selectedType) || isWiredType(selectedType)) {
                        forceSpeakerOutputSelection = false;
                        forceSpeakerFallbackUntilMs = 0L;
                    }
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "No se pudo leer salida de audio actual", throwable);
            }
        }

        AudioDeviceProfileStore.syncActiveProfileForOutput(preferences, selectedOutput);
    }

    @Nullable
    private AudioDeviceInfo selectPreferredOutput(AudioDeviceInfo[] outputs) {
        if (outputs == null || outputs.length == 0) {
            return null;
        }

        AudioDeviceInfo bluetooth = null;
        AudioDeviceInfo wired = null;
        AudioDeviceInfo speaker = null;
        AudioDeviceInfo earpiece = null;
        AudioDeviceInfo fallback = null;

        for (AudioDeviceInfo output : outputs) {
            if (output == null || !output.isSink()) {
                continue;
            }
            if (fallback == null) {
                fallback = output;
            }

            int type = output.getType();
            if (isBluetoothType(type) && bluetooth == null) {
                bluetooth = output;
            } else if (isWiredType(type) && wired == null) {
                wired = output;
            } else if ((type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) && speaker == null) {
                speaker = output;
            } else if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE && earpiece == null) {
                earpiece = output;
            }
        }

        if (forceSpeakerOutputSelection) {
            if (System.currentTimeMillis() <= forceSpeakerFallbackUntilMs) {
                if (speaker != null) {
                    return speaker;
                }
                if (earpiece != null) {
                    return earpiece;
                }
            }
            forceSpeakerOutputSelection = false;
            forceSpeakerFallbackUntilMs = 0L;
        }

        if (bluetooth != null) {
            return bluetooth;
        }
        if (wired != null) {
            return wired;
        }
        if (speaker != null) {
            return speaker;
        }
        if (earpiece != null) {
            return earpiece;
        }
        return fallback;
    }

    private boolean containsHeadphoneLikeOutput(@Nullable AudioDeviceInfo[] devices) {
        if (devices == null || devices.length == 0) {
            return false;
        }
        for (AudioDeviceInfo info : devices) {
            if (info == null || !info.isSink()) {
                continue;
            }
            int type = info.getType();
            if (isBluetoothType(type) || isWiredType(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeadphoneLikeOutput(@Nullable AudioDeviceInfo output) {
        if (output == null || !output.isSink()) {
            return false;
        }
        int type = output.getType();
        return isBluetoothType(type)
                || isWiredType(type)
                || type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
    }

    private boolean isBluetoothType(int type) {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_HEARING_AID
                || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                || type == AudioDeviceInfo.TYPE_BLE_BROADCAST;
    }

    private boolean isWiredType(int type) {
        return type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_DEVICE
                || type == AudioDeviceInfo.TYPE_LINE_ANALOG
                || type == AudioDeviceInfo.TYPE_LINE_DIGITAL
                || type == AudioDeviceInfo.TYPE_AUX_LINE;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int normalizeBassType(int rawType) {
        if (rawType == BASS_TYPE_TRANSIENT_COMPRESSOR) {
            return BASS_TYPE_TRANSIENT_COMPRESSOR;
        }
        if (rawType == BASS_TYPE_SUSTAIN_COMPRESSOR) {
            return BASS_TYPE_SUSTAIN_COMPRESSOR;
        }
        return BASS_TYPE_NATURAL;
    }

    private int normalizeReverbLevel(int rawLevel) {
        if (rawLevel == REVERB_LEVEL_OFF) {
            return REVERB_LEVEL_OFF;
        }
        if (rawLevel == REVERB_LEVEL_LIGHT) {
            return REVERB_LEVEL_LIGHT;
        }
        if (rawLevel == REVERB_LEVEL_MEDIUM) {
            return REVERB_LEVEL_MEDIUM;
        }
        if (rawLevel == REVERB_LEVEL_STRONG) {
            return REVERB_LEVEL_STRONG;
        }
        return REVERB_LEVEL_OFF;
    }

    private boolean isReverbEnabled(@NonNull SharedPreferences prefs) {
        int reverbLevel = normalizeReverbLevel(
                prefs.getInt(KEY_REVERB_LEVEL, REVERB_LEVEL_OFF)
        );
        return reverbLevel != REVERB_LEVEL_OFF;
    }

    private short clampToShort(int value, short min, short max) {
        int clamped = Math.max(min, Math.min(max, value));
        return (short) clamped;
    }

    private short clampToShortRange(int value, int min, int max) {
        int clamped = Math.max(min, Math.min(max, value));
        return (short) clamped;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float smoothstep(float t) {
        float clamped = clamp(t, 0f, 1f);
        return clamped * clamped * (3f - (2f * clamped));
    }

    private float log10(float value) {
        return (float) (Math.log(value) / Math.log(10.0));
    }

    private float log2(float value) {
        return (float) (Math.log(value) / Math.log(2.0));
    }

    private float gaussian(float x, float sigma) {
        float safeSigma = Math.max(0.05f, sigma);
        float exponent = -((x * x) / (2f * safeSigma * safeSigma));
        return (float) Math.exp(exponent);
    }
}