package com.example.sleppify;

import androidx.annotation.NonNull;

/**
 * Clean-room DSP approximation for a Wavelet-style 9-band graphic EQ plus bass tuner.
 * This class does not use or copy third-party source code.
 */
public final class WaveletStyleEqEngine {
    private static final float MIN_FREQUENCY_HZ = 20f;
    private static final float MAX_FREQUENCY_HZ = 20000f;
    private static final float[] WAVELET_GRAPHIC_EQ_ANCHORS_HZ = new float[]{
            0f,
            62.5f,
            125f,
            250f,
            500f,
            1000f,
            2000f,
            4000f,
            8000f,
            16000f,
            24000f
    };

    // Valores de presets del bass tuner tomados de la apk decompilada.
    private static final float[] BASS_TYPE_NATURAL_PARAMS = new float[]{3f, 80f, 1f, -45f, -90f, 1f};
    private static final float[] BASS_TYPE_TRANSIENT_PARAMS = new float[]{0.01f, 15f, 1f, -15f, -15f, 0.95f};
    private static final float[] BASS_TYPE_SUSTAIN_PARAMS = new float[]{30f, 100f, 0.925f, -60f, -30f, 1f};

    private WaveletStyleEqEngine() {
    }

    public static float computeGraphicEqDb(float targetFrequencyHz, @NonNull float[] uiBandDb) {
        if (uiBandDb.length <= 0) {
            return 0f;
        }

        int bandCount = Math.min(9, uiBandDb.length);
        float[] anchorsDb = new float[WAVELET_GRAPHIC_EQ_ANCHORS_HZ.length];
        anchorsDb[0] = 0f;
        for (int i = 0; i < bandCount; i++) {
            anchorsDb[i + 1] = clamp(uiBandDb[i], AudioEffectsService.EQ_GAIN_MIN_DB, AudioEffectsService.EQ_GAIN_MAX_DB);
        }
        for (int i = bandCount + 1; i <= 9; i++) {
            anchorsDb[i] = 0f;
        }
        anchorsDb[10] = anchorsDb[9];

        float safeFrequency = clamp(targetFrequencyHz, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ);
        float interpolated = interpolateLogFrequency(anchorsDb, safeFrequency);
        return clamp(interpolated, AudioEffectsService.EQ_GAIN_MIN_DB, AudioEffectsService.EQ_GAIN_MAX_DB);
    }

    public static float computeBassTunerDb(
            float targetFrequencyHz,
            float bassDb,
            float cutoffFrequencyHz,
            int bassType,
            float bassDbMax,
            float minCutoffHz,
            float maxCutoffHz
    ) {
        if (bassDb <= 0.0001f) {
            return 0f;
        }
        float frequency = clamp(targetFrequencyHz, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ);
        float cutoff = clamp(cutoffFrequencyHz, minCutoffHz, maxCutoffHz);
        float safeBassDb = clamp(bassDb, 0f, bassDbMax);

        float[] profile = resolveBassTypeProfile(bassType);

        float drivenDb = safeBassDb;
        float normalized = clamp(safeBassDb / Math.max(0.0001f, bassDbMax), 0f, 1f);

        float shoulderHz = cutoff * 2f + 10f;
        float focusWidth = clamp(1.05f - (0.35f * normalized), 0.45f, 1.10f);
        float focus = gaussian(Math.abs(log2(frequency / Math.max(20f, shoulderHz))), focusWidth);

        float subCenter = clamp(cutoff * 1.20f, 20f, 280f);
        float subShape = gaussian(Math.abs(log2(frequency / subCenter)), 0.95f);

        float highRollOff = 1f - smoothstep(log2(Math.max(frequency, cutoff) / cutoff) / 1.3f);
        highRollOff = clamp(highRollOff, 0f, 1f);

        float drive = profile[5];
        float level = drivenDb * drive;

        float thresholdShape = clamp((Math.abs(profile[3]) + Math.abs(profile[4])) / 180f, 0f, 1f);
        float compressorShape = clamp(profile[2], 0.6f, 1.2f);

        float bassLift = level * (0.28f + (0.72f * focus)) * highRollOff;
        float bodyLift = level * (0.18f + (0.40f * subShape)) * (0.72f + 0.28f * compressorShape);
        float contourTrim = level * thresholdShape * gaussian(Math.abs(log2(frequency / (cutoff * 2.8f))), 0.75f) * 0.30f;

        float result = bassLift + bodyLift - contourTrim;
        float maxBoostDb = Math.max(safeBassDb * 1.35f, safeBassDb);
        return clamp(result, 0f, maxBoostDb);
    }

    private static float[] resolveBassTypeProfile(int bassType) {
        if (bassType == AudioEffectsService.BASS_TYPE_TRANSIENT_COMPRESSOR) {
            return BASS_TYPE_TRANSIENT_PARAMS;
        }
        if (bassType == AudioEffectsService.BASS_TYPE_SUSTAIN_COMPRESSOR) {
            return BASS_TYPE_SUSTAIN_PARAMS;
        }
        return BASS_TYPE_NATURAL_PARAMS;
    }

    private static float interpolateLogFrequency(@NonNull float[] anchorsDb, float frequencyHz) {
        float safeHz = clamp(frequencyHz, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ);
        float[] f = WAVELET_GRAPHIC_EQ_ANCHORS_HZ;

        if (safeHz <= f[1]) {
            return anchorsDb[1];
        }

        for (int i = 1; i < f.length - 1; i++) {
            float leftHz = f[i];
            float rightHz = f[i + 1];
            if (safeHz > rightHz) {
                continue;
            }
            float t = (log10(safeHz) - log10(leftHz)) / (log10(rightHz) - log10(leftHz));
            t = smoothstep(clamp(t, 0f, 1f));
            return anchorsDb[i] + ((anchorsDb[i + 1] - anchorsDb[i]) * t);
        }

        return anchorsDb[anchorsDb.length - 1];
    }

    private static float smoothstep(float t) {
        return t * t * (3f - (2f * t));
    }

    private static float log10(float value) {
        return (float) (Math.log(value) / Math.log(10d));
    }

    private static float log2(float value) {
        return (float) (Math.log(value) / Math.log(2d));
    }

    private static float gaussian(float x, float sigma) {
        float safeSigma = Math.max(0.05f, sigma);
        float exponent = -((x * x) / (2f * safeSigma * safeSigma));
        return (float) Math.exp(exponent);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
