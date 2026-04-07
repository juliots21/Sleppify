package com.example.sleppify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EqualizerFragment extends Fragment {
    private static final String KEY_SELECTED_PRESET = "selected_preset";
    private static final String KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX = "profile_custom_preset_name_";
    private static final String KEY_USER_PRESET_BAND_PREFIX = "user_preset_band_";
    private static final String KEY_DEVICE_PROFILE_PREFIX = "device_profile_";
    private static final String PRESET_DEFAULT = "default";
    private static final String PRESET_CUSTOM = "custom";
    private static final String PRESET_USER_PREFIX = "user_profile_";
    private static final String PRESET_DARK = "dark";
    private static final String PRESET_BRIGHT = "bright";
    private static final String PRESET_BASS_BOOST = "bass_boost";
    private static final String PRESET_BASS_REDUCTION = "bass_reduction";
    private static final String PRESET_TREBLE_BOOST = "treble_boost";
    private static final String PRESET_TREBLE_REDUCTION = "treble_reduction";
    private static final String PRESET_VOCAL_BOOST = "vocal_boost";
    private static final String PRESET_M_SHAPED = "m_shaped";
    private static final String PRESET_U_SHAPED = "u_shaped";
    private static final String PRESET_V_SHAPE = "v_shape";
    private static final String PRESET_W_SHAPED = "w_shaped";
    private static final String KEY_AI_EQ_NEXT_AT_PREFIX = "ai_eq_next_at_";
    private static final String KEY_AI_EQ_PENDING_JSON_PREFIX = "ai_eq_pending_json_";
    private static final String KEY_AI_EQ_LAST_PROCESS_SESSION_PREFIX = "ai_eq_last_process_session_";
    private static final String KEY_AI_EQ_DISMISSED_SESSION_PREFIX = "ai_eq_dismissed_session_";
    private static final long AI_EQ_SUGGESTION_INTERVAL_MS = 2L * 60L * 60L * 1000L;
    private static final long AI_EQ_SUGGESTION_RETRY_MS = 15L * 60L * 1000L;
    private static final long AI_EQ_LOADING_FRAME_MS = 420L;
    private static final long FORCE_SPEAKER_FALLBACK_WINDOW_MS = 4000L;
    private static final long OUTPUT_REFRESH_MIN_INTERVAL_MS = 900L;
    private static final long PROCESS_SESSION_ID = AiSuggestionSession.PROCESS_SESSION_ID;
    private static final int EQ_BAND_COUNT = AudioEffectsService.EQ_BAND_COUNT;
    private static final float BASS_GAIN_MAX_DB = AudioEffectsService.BASS_DB_MAX;
    private static final float BASS_GAIN_MIN_DB = 0f;
    private static final float BASS_GAIN_TOGGLE_THRESHOLD_DB = BASS_GAIN_MAX_DB * 0.5f;
    private static final float BASS_DEFAULT_FREQUENCY_HZ = AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ;
    private static final int BASS_TYPE_DEFAULT = AudioEffectsService.BASS_TYPE_NATURAL;
    private static final int REVERB_LEVEL_DEFAULT = AudioEffectsService.REVERB_LEVEL_OFF;
        private static final float[] GRAPHIC_EQ_DIALOG_FREQUENCIES_HZ = {
            62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        };

    private static final PresetConfig[] PRESET_CONFIGS = new PresetConfig[]{
            new PresetConfig(PRESET_DEFAULT, "Plano", new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_DARK, "Oscuro", new float[]{4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f, -4.0f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_BRIGHT, "Brillante", new float[]{-4.0f, -3.0f, -2.0f, -1.0f, 0.0f, 1.0f, 2.0f, 3.0f, 4.0f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_BASS_BOOST, "Refuerzo de graves", new float[]{5.5f, 4.5f, 1.5f, 0.2f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 3.0f, AudioEffectsService.bassSliderFromCutoffHz(80f), AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_BASS_REDUCTION, "Reduccion de graves", new float[]{-5.5f, -4.5f, -1.5f, -0.2f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_TREBLE_BOOST, "Refuerzo de agudos", new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.2f, 1.5f, 5.3f, 3.8f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_TREBLE_REDUCTION, "Reduccion de agudos", new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.2f, -1.5f, -5.3f, -3.8f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_VOCAL_BOOST, "Refuerzo vocal", new float[]{-1.0f, -1.1f, -0.6f, 0.7f, 3.1f, 4.6f, 3.3f, 0.6f, 0.0f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_M_SHAPED, "Forma M", new float[]{-3.0f, 0.0f, 2.8f, 0.5f, -2.0f, 0.5f, 2.8f, 0.0f, -3.0f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_U_SHAPED, "Forma U", new float[]{4.0f, 0.0f, -2.0f, -2.6f, -2.7f, -2.6f, -2.0f, 0.0f, 4.0f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_V_SHAPE, "Forma V", new float[]{4.0f, 2.9f, 0.2f, -2.3f, -4.0f, -2.3f, 0.2f, 2.9f, 4.0f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            new PresetConfig(PRESET_W_SHAPED, "Forma W", new float[]{3.0f, 0.0f, -2.8f, -0.5f, 2.0f, -0.5f, -2.8f, 0.0f, 3.0f}, 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL)
    };

    private static final BassTypeOption[] BASS_TYPE_OPTIONS = new BassTypeOption[]{
            new BassTypeOption(AudioEffectsService.BASS_TYPE_NATURAL, "Natural"),
            new BassTypeOption(AudioEffectsService.BASS_TYPE_TRANSIENT_COMPRESSOR, "Compresor de transitorios"),
            new BassTypeOption(AudioEffectsService.BASS_TYPE_SUSTAIN_COMPRESSOR, "Compresor de sustain")
    };

    private static final ReverbOption[] REVERB_OPTIONS = new ReverbOption[]{
            new ReverbOption(AudioEffectsService.REVERB_LEVEL_OFF, "Desactivada"),
            new ReverbOption(AudioEffectsService.REVERB_LEVEL_LIGHT, "Ligera"),
            new ReverbOption(AudioEffectsService.REVERB_LEVEL_MEDIUM, "Media"),
            new ReverbOption(AudioEffectsService.REVERB_LEVEL_STRONG, "Intensa")
    };

    private SharedPreferences preferences;
    private TextView tvOutputRoute;
    private ImageView ivOutputIcon;
    private View cardEqAiSuggestion;
    private TextView tvEqAiSuggestionText;
    private MaterialButton btnEqAiApply;
    private MaterialButton btnEqAiDismiss;
    private TextView tvSelectedPreset;
    private View cardPresetSelector;
    private View cardGraphicEq;
    private View cardBassTuner;
    private EqCurveEditorView eqCurveView;
    private SwitchMaterial switchEqEnabled;
    @Nullable
    private PopupWindow activePopupWindow;
    private Slider sliderBassGain;
    private TextView tvBassGainValue;
    private TextView tvSelectedBassType;
    private View cardBassTypeSelector;
    private TextView tvSelectedReverb;
    private View cardReverbSelector;
    private Slider sliderBassFrequency;
    private TextView tvBassFrequencyValue;
    private boolean restoringUi;
    private boolean hasLoadedUi;
    @Nullable
    private String boundProfileId;
    @NonNull
    private String currentOutputDescriptorForAi = "parlante integrado del telefono";
    @Nullable
    private GeminiIntelligenceService.EqSuggestion pendingEqAiSuggestion;
    @Nullable
    private EqAiUndoSnapshot eqAiUndoSnapshot;
    @Nullable
    private String eqAiSuggestionSignature;
    private boolean eqAiSuggestionApplied;
    private boolean eqAiSuggestionInFlight;
    @Nullable
    private String eqAiInFlightProfileId;
    private boolean eqAiLoading;
    private int eqAiLoadingFrame;
    private AudioManager audioManager;
    private boolean forceSpeakerOutputSelection;
    private long forceSpeakerFallbackUntilMs;
    private long lastOutputRefreshAtMs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable eqAiLoadingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!eqAiLoading || tvEqAiSuggestionText == null) {
                return;
            }

            String[] frames = {
                    "Generando sugerencia experta",
                    "Generando sugerencia experta.",
                    "Generando sugerencia experta..",
                    "Generando sugerencia experta..."
            };
            tvEqAiSuggestionText.setText(frames[eqAiLoadingFrame % frames.length]);
            eqAiLoadingFrame++;
            mainHandler.postDelayed(this, AI_EQ_LOADING_FRAME_MS);
        }
    };

    private final AudioDeviceCallback outputDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (containsHeadphoneLikeOutput(addedDevices)) {
                forceSpeakerOutputSelection = false;
                forceSpeakerFallbackUntilMs = 0L;
            }
            refreshOutputDeviceAndProfile(true, true);
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            if (containsHeadphoneLikeOutput(removedDevices)) {
                forceSpeakerOutputSelection = true;
                forceSpeakerFallbackUntilMs = System.currentTimeMillis() + FORCE_SPEAKER_FALLBACK_WINDOW_MS;
            }
            refreshOutputDeviceAndProfile(true, true);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_equalizer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = requireContext().getSharedPreferences(AudioEffectsService.PREFS_NAME, Context.MODE_PRIVATE);
        audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);

        // La vista puede recrearse al entrar/salir de Ajustes; forzamos recarga de preset/curva.
        hasLoadedUi = false;
        boundProfileId = null;

        tvOutputRoute = view.findViewById(R.id.tvOutputRoute);
        ivOutputIcon = view.findViewById(R.id.ivOutputIcon);
        cardEqAiSuggestion = view.findViewById(R.id.cardEqAiSuggestion);
        tvEqAiSuggestionText = view.findViewById(R.id.tvEqAiSuggestionText);
        btnEqAiApply = view.findViewById(R.id.btnEqAiApply);
        btnEqAiDismiss = view.findViewById(R.id.btnEqAiDismiss);
        tvSelectedPreset = view.findViewById(R.id.tvSelectedPreset);
        cardPresetSelector = view.findViewById(R.id.cardPresetSelector);
        cardGraphicEq = view.findViewById(R.id.cardGraphicEq);
        cardBassTuner = view.findViewById(R.id.cardBassTuner);
        eqCurveView = view.findViewById(R.id.eqCurveView);
        switchEqEnabled = view.findViewById(R.id.switchEqEnabled);

        sliderBassGain = view.findViewById(R.id.sliderBassGain);
        tvBassGainValue = view.findViewById(R.id.tvBassGainValue);
        tvSelectedBassType = view.findViewById(R.id.tvSelectedBassType);
        cardBassTypeSelector = view.findViewById(R.id.cardBassTypeSelector);
        tvSelectedReverb = view.findViewById(R.id.tvSelectedReverb);
        cardReverbSelector = view.findViewById(R.id.cardReverbSelector);
        sliderBassFrequency = view.findViewById(R.id.sliderBassFrequency);
        tvBassFrequencyValue = view.findViewById(R.id.tvBassFrequencyValue);

        sliderBassFrequency.setValueFrom(AudioEffectsService.BASS_FREQUENCY_MIN_HZ);
        sliderBassFrequency.setValueTo(AudioEffectsService.BASS_FREQUENCY_MAX_HZ);

        setupListeners();
        refreshOutputDeviceAndProfile(false, true);
        refreshEqModuleState();
        ensureEqServiceActive();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshOutputDeviceAndProfile(true, false);
        refreshEqModuleState();
        ensureEqServiceActive();
    }

    public void onCloudEqHydrationCompleted() {
        if (!isAdded() || preferences == null) {
            return;
        }

        mainHandler.post(() -> {
            if (!isAdded()) {
                return;
            }
            hasLoadedUi = false;
            refreshOutputDeviceAndProfile(true, true);
            refreshEqModuleState();
            ensureEqServiceActive();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        registerOutputDeviceCallback();
    }

    @Override
    public void onStop() {
        dismissActivePopupWindow();
        unregisterOutputDeviceCallback();
        stopEqAiLoadingAnimation();
        super.onStop();
    }

    private void dismissActivePopupWindow() {
        if (activePopupWindow == null) {
            return;
        }
        try {
            activePopupWindow.dismiss();
        } catch (Throwable ignored) {
            // Evita crash por ventanas desacopladas al cambiar de modulo.
        }
        activePopupWindow = null;
    }

    private void registerActivePopupWindow(@NonNull PopupWindow popupWindow) {
        dismissActivePopupWindow();
        activePopupWindow = popupWindow;
        popupWindow.setOnDismissListener(() -> {
            if (activePopupWindow == popupWindow) {
                activePopupWindow = null;
            }
        });
    }

    private void loadPreferencesIntoUi() {
        restoringUi = true;

        float bassDb = quantizeBassGainToggle(preferences.getFloat(AudioEffectsService.KEY_BASS_DB, 0f));
        sliderBassGain.setValue(bassDb);
        tvBassGainValue.setText(formatDb(bassDb));

        float bassFrequencyHz = normalizeBassFrequencySlider(
            preferences.getFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, BASS_DEFAULT_FREQUENCY_HZ)
        );
        sliderBassFrequency.setValue(bassFrequencyHz);
        tvBassFrequencyValue.setText(formatHz(bassFrequencyHz));

        int bassType = normalizeBassType(preferences.getInt(AudioEffectsService.KEY_BASS_TYPE, BASS_TYPE_DEFAULT));
        if (tvSelectedBassType != null) {
            tvSelectedBassType.setText(bassTypeLabelFromValue(bassType));
        }

        int reverbLevel = normalizeReverbLevel(
                preferences.getInt(AudioEffectsService.KEY_REVERB_LEVEL, REVERB_LEVEL_DEFAULT)
        );
        if (tvSelectedReverb != null) {
            tvSelectedReverb.setText(reverbLabelFromLevel(reverbLevel));
        }

        float[] bandValues = new float[EQ_BAND_COUNT];
        for (int i = 0; i < bandValues.length; i++) {
            bandValues[i] = preferences.getFloat(AudioEffectsService.bandDbKey(i), 0f);
        }
        eqCurveView.setBandValues(bandValues);

        String selectedPreset = preferences.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT);
        tvSelectedPreset.setText(presetLabelFromId(selectedPreset));

        restoringUi = false;
    }

    private void setupListeners() {
        sliderBassGain.addOnChangeListener((slider, value, fromUser) -> {
            float snappedValue = quantizeBassGainToggle(value);
            if (fromUser && Math.abs(slider.getValue() - snappedValue) > 0.001f) {
                restoringUi = true;
                slider.setValue(snappedValue);
                restoringUi = false;
            }

            tvBassGainValue.setText(formatDb(snappedValue));
            if (restoringUi) {
                return;
            }
            preferences.edit().putFloat(AudioEffectsService.KEY_BASS_DB, snappedValue).apply();
            markPresetAsCustom();
            applyIfEnabled();
        });

        sliderBassFrequency.addOnChangeListener((slider, value, fromUser) -> {
            tvBassFrequencyValue.setText(formatHz(value));
            if (restoringUi) {
                return;
            }
            preferences.edit().putFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, value).apply();
            markPresetAsCustom();
            applyIfEnabled();
        });

        if (cardBassTypeSelector != null) {
            cardBassTypeSelector.setOnClickListener(v -> showBassTypePopup());
        }
        if (cardReverbSelector != null) {
            cardReverbSelector.setOnClickListener(v -> showReverbPopup());
        }

        if (eqCurveView != null) {
            eqCurveView.setEditingEnabled(false);
            eqCurveView.setHandlesVisible(false);
            eqCurveView.setGridVisible(true);
            eqCurveView.setAxisDbLabelsEnabled(true);
            eqCurveView.setAreaFillEnabled(true);
            eqCurveView.setModalBandGuidesEnabled(false);
        }

        if (cardGraphicEq != null) {
            cardGraphicEq.setOnClickListener(v -> showGraphicEqEditorDialog());
        }

        cardPresetSelector.setOnClickListener(v -> showPresetPopup());

        if (switchEqEnabled != null) {
            switchEqEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (restoringUi || preferences == null) {
                    return;
                }
                preferences.edit().putBoolean(AudioEffectsService.KEY_ENABLED, isChecked).apply();
                if (isChecked) {
                    startOrUpdateService();
                } else {
                    stopEqService();
                }
                refreshEqModuleState();
            });
        }

        if (btnEqAiApply != null) {
            btnEqAiApply.setOnClickListener(v -> applyAiSuggestion());
        }
        if (btnEqAiDismiss != null) {
            btnEqAiDismiss.setOnClickListener(v -> undoAiSuggestion());
        }
    }

    private void applyIfEnabled() {
        boolean eqEnabled = isEqEnabled();
        if (!eqEnabled) {
            refreshEqModuleState();
            return;
        }
        if (preferences != null) {
            AudioDeviceProfileStore.persistActiveValuesToCurrentProfile(preferences);
        }
        startOrUpdateService();
    }

    private void ensureEqServiceActive() {
        if (!isEqEnabled()) {
            return;
        }
        startOrUpdateService();
    }

    private boolean isEqEnabled() {
        return preferences != null && preferences.getBoolean(AudioEffectsService.KEY_ENABLED, false);
    }

    private void refreshEqModuleState() {
        boolean eqEnabled = isEqEnabled();

        if (switchEqEnabled != null && switchEqEnabled.isChecked() != eqEnabled) {
            restoringUi = true;
            switchEqEnabled.setChecked(eqEnabled);
            restoringUi = false;
        }

        if (cardPresetSelector != null) {
            cardPresetSelector.setEnabled(eqEnabled);
            cardPresetSelector.setClickable(eqEnabled);
            cardPresetSelector.setFocusable(eqEnabled);
            cardPresetSelector.setAlpha(eqEnabled ? 1f : 0.45f);
        }

        if (cardBassTypeSelector != null) {
            cardBassTypeSelector.setEnabled(eqEnabled);
            cardBassTypeSelector.setClickable(eqEnabled);
            cardBassTypeSelector.setFocusable(eqEnabled);
            cardBassTypeSelector.setAlpha(eqEnabled ? 1f : 0.45f);
        }

        if (cardReverbSelector != null) {
            cardReverbSelector.setEnabled(eqEnabled);
            cardReverbSelector.setClickable(eqEnabled);
            cardReverbSelector.setFocusable(eqEnabled);
            cardReverbSelector.setAlpha(eqEnabled ? 1f : 0.45f);
        }

        if (cardGraphicEq != null) {
            cardGraphicEq.setEnabled(eqEnabled);
            cardGraphicEq.setClickable(eqEnabled);
            cardGraphicEq.setFocusable(eqEnabled);
            cardGraphicEq.setAlpha(eqEnabled ? 1f : 0.45f);
        }

        if (cardBassTuner != null) {
            cardBassTuner.setEnabled(eqEnabled);
            cardBassTuner.setClickable(eqEnabled);
            cardBassTuner.setFocusable(eqEnabled);
            cardBassTuner.setAlpha(eqEnabled ? 1f : 0.45f);
        }

        if (eqCurveView != null) {
            eqCurveView.setEnabled(eqEnabled);
            eqCurveView.setEditingEnabled(false);
            eqCurveView.setHandlesVisible(false);
            eqCurveView.setAlpha(eqEnabled ? 1f : 0.45f);
        }
        if (sliderBassGain != null) {
            sliderBassGain.setEnabled(eqEnabled);
        }
        if (sliderBassFrequency != null) {
            sliderBassFrequency.setEnabled(eqEnabled);
        }
        boolean hasSuggestion = pendingEqAiSuggestion != null;
        boolean canApplySuggestion = hasSuggestion && !eqAiLoading && !eqAiSuggestionApplied;
        boolean canUndoSuggestion = hasSuggestion && !eqAiLoading && eqAiSuggestionApplied && eqAiUndoSnapshot != null;
        if (btnEqAiApply != null) {
            btnEqAiApply.setEnabled(eqEnabled && canApplySuggestion);
        }
        if (btnEqAiDismiss != null) {
            btnEqAiDismiss.setEnabled(eqEnabled && canUndoSuggestion);
        }
    }

    private void registerOutputDeviceCallback() {
        if (audioManager == null) {
            refreshOutputDeviceAndProfile(false, false);
            return;
        }
        try {
            audioManager.registerAudioDeviceCallback(outputDeviceCallback, mainHandler);
        } catch (Throwable ignored) {
            // Si el callback falla en algun OEM, seguimos con lectura puntual.
        }
        refreshOutputDeviceAndProfile(true, false);
    }

    private void unregisterOutputDeviceCallback() {
        if (audioManager == null) {
            return;
        }
        try {
            audioManager.unregisterAudioDeviceCallback(outputDeviceCallback);
        } catch (Throwable ignored) {
            // Evita crash en stacks con comportamiento inconsistente.
        }
    }

    private void refreshOutputDeviceAndProfile(boolean applyOnProfileSwitch) {
        refreshOutputDeviceAndProfile(applyOnProfileSwitch, false);
    }

    private void refreshOutputDeviceAndProfile(boolean applyOnProfileSwitch, boolean forceRefresh) {
        if (tvOutputRoute == null || ivOutputIcon == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!forceRefresh && (now - lastOutputRefreshAtMs) < OUTPUT_REFRESH_MIN_INTERVAL_MS) {
            return;
        }
        lastOutputRefreshAtMs = now;

        AudioDeviceInfo selected = null;
        if (audioManager == null) {
            setOutputUi(
                "Altavoces",
                    R.drawable.ic_output_speaker,
                    "Salida por altavoz"
            );
        } else {
            AudioDeviceInfo[] outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            selected = selectPreferredOutput(outputDevices);

            if (selected != null) {
                int selectedType = selected.getType();
                if (isBluetoothType(selectedType) || isWiredType(selectedType)) {
                    forceSpeakerOutputSelection = false;
                    forceSpeakerFallbackUntilMs = 0L;
                }
            }

            if (selected == null) {
                setOutputUi(
                    "Altavoces",
                        R.drawable.ic_output_speaker,
                        "Salida por altavoz"
                );
            } else {
                int type = selected.getType();
                if (isBluetoothType(type)) {
                    setOutputUi(
                            deviceName(selected, "Bluetooth"),
                            R.drawable.ic_output_bluetooth,
                            "Salida por Bluetooth"
                    );
                } else if (isWiredType(type)) {
                    setOutputUi(
                            "Auriculares",
                            R.drawable.ic_output_wired,
                            "Salida por auriculares"
                    );
                } else if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                    setOutputUi(
                            "Auriculares",
                            R.drawable.ic_output_earpiece,
                            "Salida por auriculares"
                    );
                } else {
                    setOutputUi(
                            "Altavoces",
                            R.drawable.ic_output_speaker,
                            "Salida por altavoz"
                    );
                }
            }
        }

        boolean restoredFromProfile = AudioDeviceProfileStore.restoreActiveValuesForOutput(preferences, selected);
        boolean profileSwitched = AudioDeviceProfileStore.syncActiveProfileForOutput(preferences, selected);
        String currentProfile = AudioDeviceProfileStore.getActiveProfileId(preferences, selected);
        currentOutputDescriptorForAi = buildOutputDescriptorForAi(selected);
        String previousBoundProfile = boundProfileId;
        boolean shouldReloadUi = restoredFromProfile || profileSwitched || !hasLoadedUi || !TextUtils.equals(previousBoundProfile, currentProfile);
        boundProfileId = currentProfile;
        if (shouldReloadUi) {
            ensureBassDefaults();
            ensurePresetStateConsistency();
            loadPreferencesIntoUi();
            hasLoadedUi = true;
        }
        refreshEqAiSuggestionState();
        refreshEqModuleState();
        if ((profileSwitched || restoredFromProfile) && applyOnProfileSwitch) {
            applyIfEnabled();
        }
    }

    private void refreshEqAiSuggestionState() {
        if (!areSmartSuggestionsEnabled()) {
            pendingEqAiSuggestion = null;
            eqAiSuggestionInFlight = false;
            eqAiInFlightProfileId = null;
            stopEqAiLoadingAnimation();
            renderEqAiSuggestion(null);
            return;
        }

        if (TextUtils.isEmpty(boundProfileId)) {
            pendingEqAiSuggestion = null;
            stopEqAiLoadingAnimation();
            renderEqAiSuggestion(null);
            return;
        }

        String profileId = boundProfileId;
        boolean isNewProcessSession = registerAndDetectNewProcessSession(profileId);
        long now = System.currentTimeMillis();
        if (isNewProcessSession) {
            preferences.edit().remove(eqAiDismissedSessionKey(profileId)).apply();
        }

        if (isEqSuggestionDismissedInCurrentSession(profileId)) {
            pendingEqAiSuggestion = null;
            stopEqAiLoadingAnimation();
            renderEqAiSuggestion(null);
            return;
        }

        long nextAt = preferences.getLong(eqAiNextAtKey(profileId), 0L);

        pendingEqAiSuggestion = readPendingAiSuggestion(profileId);
        if (pendingEqAiSuggestion != null && now >= nextAt && nextAt > 0L) {
            preferences.edit().remove(eqAiPendingKey(profileId)).apply();
            pendingEqAiSuggestion = null;
        }

        if (pendingEqAiSuggestion != null) {
            stopEqAiLoadingAnimation();
            renderEqAiSuggestion(pendingEqAiSuggestion);
            return;
        }

        if (eqAiSuggestionInFlight && TextUtils.equals(eqAiInFlightProfileId, profileId)) {
            renderEqAiLoadingState();
            return;
        }

        renderEqAiSuggestion(null);
        boolean forceNow = isNewProcessSession || nextAt <= 0L || now >= nextAt;
        maybeGenerateEqAiSuggestion(profileId, forceNow);
    }

    private void maybeGenerateEqAiSuggestion(@NonNull String profileId, boolean forceNow) {
        if (eqAiSuggestionInFlight) {
            return;
        }

        long now = System.currentTimeMillis();
        long nextAt = preferences.getLong(eqAiNextAtKey(profileId), 0L);
        if (!forceNow && now < nextAt) {
            return;
        }

        float[] currentBands = new float[EQ_BAND_COUNT];
        for (int i = 0; i < currentBands.length; i++) {
            currentBands[i] = preferences.getFloat(AudioEffectsService.bandDbKey(i), 0f);
        }

        float bassDb = quantizeBassGainToggle(preferences.getFloat(AudioEffectsService.KEY_BASS_DB, 0f));
        float bassFrequencyHz = normalizeBassFrequencySlider(
            preferences.getFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, BASS_DEFAULT_FREQUENCY_HZ)
        );
        String currentOutput = TextUtils.isEmpty(currentOutputDescriptorForAi)
                ? "parlante integrado del telefono"
                : currentOutputDescriptorForAi;
        String phoneModel = buildPhoneModelDescriptor();

        eqAiSuggestionInFlight = true;
        eqAiInFlightProfileId = profileId;
        renderEqAiLoadingState();
        new GeminiIntelligenceService().generateEqSuggestion(
                currentOutput,
            phoneModel,
                currentBands,
                bassDb,
                bassFrequencyHz,
                new GeminiIntelligenceService.EqSuggestionCallback() {
                    @Override
                    public void onSuccess(GeminiIntelligenceService.EqSuggestion suggestion) {
                        eqAiSuggestionInFlight = false;
                        eqAiInFlightProfileId = null;
                        stopEqAiLoadingAnimation();
                        savePendingAiSuggestion(profileId, suggestion);
                        if (TextUtils.equals(boundProfileId, profileId)) {
                            pendingEqAiSuggestion = suggestion;
                            renderEqAiSuggestion(suggestion);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        eqAiSuggestionInFlight = false;
                        eqAiInFlightProfileId = null;
                        stopEqAiLoadingAnimation();
                        preferences.edit()
                                .putLong(eqAiNextAtKey(profileId), System.currentTimeMillis() + AI_EQ_SUGGESTION_RETRY_MS)
                                .apply();
                        if (TextUtils.equals(boundProfileId, profileId)) {
                            renderEqAiSuggestion(null);
                        }
                    }
                }
        );
    }

    private void savePendingAiSuggestion(
            @NonNull String profileId,
            @NonNull GeminiIntelligenceService.EqSuggestion suggestion
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("message", suggestion.message);
            JSONArray bands = new JSONArray();
            for (float value : suggestion.bands) {
                bands.put(value);
            }
            json.put("bands", bands);
            json.put("bassDb", suggestion.bassDb);
            json.put("bassFrequencyHz", suggestion.bassFrequencyHz);

            preferences.edit()
                    .putString(eqAiPendingKey(profileId), json.toString())
                    .remove(eqAiDismissedSessionKey(profileId))
                    .putLong(eqAiNextAtKey(profileId), System.currentTimeMillis() + AI_EQ_SUGGESTION_INTERVAL_MS)
                    .apply();
        } catch (Exception ignored) {
            // Si el serializado falla, no bloqueamos el modulo de EQ.
        }
    }

    @Nullable
    private GeminiIntelligenceService.EqSuggestion readPendingAiSuggestion(@NonNull String profileId) {
        String raw = preferences.getString(eqAiPendingKey(profileId), null);
        if (TextUtils.isEmpty(raw)) {
            return null;
        }

        try {
            JSONObject json = new JSONObject(raw);
            String message = json.optString("message", "").trim();
            if (TextUtils.isEmpty(message)) {
                return null;
            }

            JSONArray bandsArray = json.optJSONArray("bands");
            if (bandsArray == null || bandsArray.length() < EQ_BAND_COUNT) {
                return null;
            }

            float[] bands = new float[EQ_BAND_COUNT];
            for (int i = 0; i < bands.length; i++) {
                bands[i] = clamp(
                        (float) bandsArray.optDouble(i, 0d),
                        AudioEffectsService.EQ_GAIN_MIN_DB,
                        AudioEffectsService.EQ_GAIN_MAX_DB
                );
            }

                float bassDb = quantizeBassGainToggle((float) json.optDouble("bassDb", 0d));
            float bassFrequencyHz = normalizeBassFrequencySlider(
                    (float) json.optDouble("bassFrequencyHz", BASS_DEFAULT_FREQUENCY_HZ)
            );
            return new GeminiIntelligenceService.EqSuggestion(message, bands, bassDb, bassFrequencyHz);
        } catch (Exception ignored) {
            preferences.edit().remove(eqAiPendingKey(profileId)).apply();
            return null;
        }
    }

    private void applyAiSuggestion() {
        if (pendingEqAiSuggestion == null || TextUtils.isEmpty(boundProfileId)) {
            return;
        }

        if (eqAiSuggestionApplied) {
            renderEqAiSuggestion(pendingEqAiSuggestion);
            return;
        }

        GeminiIntelligenceService.EqSuggestion suggestion = pendingEqAiSuggestion;
        String profileId = boundProfileId;
        eqAiUndoSnapshot = captureEqAiUndoSnapshot();
        float normalizedBassDb = quantizeBassGainToggle(suggestion.bassDb);
        float normalizedBassFrequencyHz = normalizeBassFrequencySlider(suggestion.bassFrequencyHz);

        restoringUi = true;
        eqCurveView.setBandValues(suggestion.bands);
        sliderBassGain.setValue(normalizedBassDb);
        tvBassGainValue.setText(formatDb(normalizedBassDb));
        sliderBassFrequency.setValue(normalizedBassFrequencyHz);
        tvBassFrequencyValue.setText(formatHz(normalizedBassFrequencyHz));
        String selectedPresetId = resolveMutableCustomPresetId();
        tvSelectedPreset.setText(presetLabelFromId(selectedPresetId));
        restoringUi = false;

        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < suggestion.bands.length; i++) {
            editor.putFloat(AudioEffectsService.bandDbKey(i), suggestion.bands[i]);
        }
        editor.putFloat(AudioEffectsService.KEY_BASS_DB, normalizedBassDb);
        editor.putFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, normalizedBassFrequencyHz);
        editor.putString(KEY_SELECTED_PRESET, selectedPresetId);
        editor.remove(eqAiDismissedSessionKey(profileId));
        editor.putLong(eqAiNextAtKey(profileId), System.currentTimeMillis() + AI_EQ_SUGGESTION_INTERVAL_MS);
        editor.apply();

        if (isUserPresetId(selectedPresetId)) {
            String userProfileId = profileIdFromUserPresetId(selectedPresetId);
            if (!TextUtils.isEmpty(userProfileId)) {
                saveUserPresetBandsForProfile(userProfileId, suggestion.bands);
            }
        }

        eqAiSuggestionApplied = true;
        stopEqAiLoadingAnimation();
        renderEqAiSuggestion(suggestion);
        applyIfEnabled();
    }

    private void undoAiSuggestion() {
        if (eqAiUndoSnapshot == null) {
            if (pendingEqAiSuggestion != null) {
                renderEqAiSuggestion(pendingEqAiSuggestion);
            }
            return;
        }

        EqAiUndoSnapshot snapshot = eqAiUndoSnapshot;

        restoringUi = true;
        if (eqCurveView != null) {
            eqCurveView.setBandValues(snapshot.bands);
        }
        if (sliderBassGain != null) {
            sliderBassGain.setValue(snapshot.bassDb);
            tvBassGainValue.setText(formatDb(snapshot.bassDb));
        }
        if (sliderBassFrequency != null) {
            sliderBassFrequency.setValue(snapshot.bassFrequencyHz);
            tvBassFrequencyValue.setText(formatHz(snapshot.bassFrequencyHz));
        }
        if (tvSelectedPreset != null) {
            tvSelectedPreset.setText(presetLabelFromId(snapshot.presetId));
        }
        restoringUi = false;

        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < snapshot.bands.length; i++) {
            editor.putFloat(AudioEffectsService.bandDbKey(i), snapshot.bands[i]);
        }
        editor.putFloat(AudioEffectsService.KEY_BASS_DB, snapshot.bassDb);
        editor.putFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, snapshot.bassFrequencyHz);
        editor.putString(KEY_SELECTED_PRESET, snapshot.presetId);
        editor.apply();

        eqAiSuggestionApplied = false;
        stopEqAiLoadingAnimation();
        if (pendingEqAiSuggestion != null) {
            renderEqAiSuggestion(pendingEqAiSuggestion);
        }
        applyIfEnabled();
    }

    private void renderEqAiSuggestion(@Nullable GeminiIntelligenceService.EqSuggestion suggestion) {
        if (cardEqAiSuggestion == null || tvEqAiSuggestionText == null) {
            return;
        }

        if (suggestion == null) {
            resetEqAiSuggestionActionState();
            if (eqAiLoading) {
                cardEqAiSuggestion.setVisibility(View.VISIBLE);
                refreshEqModuleState();
                return;
            }
            cardEqAiSuggestion.setVisibility(View.GONE);
            refreshEqModuleState();
            return;
        }

        syncEqAiSuggestionActionState(suggestion);

        if (btnEqAiApply != null) {
            btnEqAiApply.setEnabled(!eqAiSuggestionApplied);
            btnEqAiApply.setVisibility(View.VISIBLE);
            btnEqAiApply.setText(eqAiSuggestionApplied ? "Aplicado" : "Aplicar cambios");
        }
        if (btnEqAiDismiss != null) {
            btnEqAiDismiss.setEnabled(eqAiSuggestionApplied && eqAiUndoSnapshot != null);
            btnEqAiDismiss.setVisibility(View.VISIBLE);
            btnEqAiDismiss.setText("Deshacer");
        }
        tvEqAiSuggestionText.setText(suggestion.message);
        cardEqAiSuggestion.setVisibility(View.VISIBLE);
        refreshEqModuleState();
    }

    @NonNull
    private EqAiUndoSnapshot captureEqAiUndoSnapshot() {
        float[] bands = new float[EQ_BAND_COUNT];
        for (int i = 0; i < bands.length; i++) {
            bands[i] = preferences.getFloat(AudioEffectsService.bandDbKey(i), 0f);
        }

        float bassDb = quantizeBassGainToggle(preferences.getFloat(AudioEffectsService.KEY_BASS_DB, 0f));
        float bassFrequencyHz = normalizeBassFrequencySlider(
            preferences.getFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, BASS_DEFAULT_FREQUENCY_HZ)
        );
        String presetId = preferences.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT);
        if (TextUtils.isEmpty(presetId)) {
            presetId = PRESET_DEFAULT;
        }

        return new EqAiUndoSnapshot(bands, bassDb, bassFrequencyHz, presetId);
    }

    private void syncEqAiSuggestionActionState(@NonNull GeminiIntelligenceService.EqSuggestion suggestion) {
        String signature = buildEqAiSuggestionSignature(suggestion);
        if (TextUtils.equals(signature, eqAiSuggestionSignature)) {
            return;
        }

        eqAiSuggestionSignature = signature;
        eqAiSuggestionApplied = false;
        eqAiUndoSnapshot = null;
    }

    private void resetEqAiSuggestionActionState() {
        eqAiSuggestionSignature = null;
        eqAiSuggestionApplied = false;
        eqAiUndoSnapshot = null;
    }

    @NonNull
    private String buildEqAiSuggestionSignature(@NonNull GeminiIntelligenceService.EqSuggestion suggestion) {
        return suggestion.message + "|" + Arrays.toString(suggestion.bands)
                + "|" + suggestion.bassDb + "|" + suggestion.bassFrequencyHz;
    }

    private boolean areSmartSuggestionsEnabled() {
        SharedPreferences settingsPrefs = requireContext().getSharedPreferences(
                CloudSyncManager.PREFS_SETTINGS,
                Context.MODE_PRIVATE
        );
        return settingsPrefs.getBoolean(
                CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
                settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        );
    }

    private void renderEqAiLoadingState() {
        if (cardEqAiSuggestion == null || tvEqAiSuggestionText == null) {
            return;
        }

        cardEqAiSuggestion.setVisibility(View.VISIBLE);
        if (btnEqAiApply != null) {
            btnEqAiApply.setEnabled(false);
            btnEqAiApply.setVisibility(View.INVISIBLE);
        }
        if (btnEqAiDismiss != null) {
            btnEqAiDismiss.setEnabled(false);
            btnEqAiDismiss.setVisibility(View.INVISIBLE);
        }

        if (!eqAiLoading) {
            eqAiLoading = true;
            eqAiLoadingFrame = 0;
            mainHandler.removeCallbacks(eqAiLoadingRunnable);
            mainHandler.post(eqAiLoadingRunnable);
        }
        refreshEqModuleState();
    }

    private void stopEqAiLoadingAnimation() {
        if (!eqAiLoading) {
            return;
        }
        eqAiLoading = false;
        mainHandler.removeCallbacks(eqAiLoadingRunnable);
        eqAiLoadingFrame = 0;
    }

    private boolean registerAndDetectNewProcessSession(@NonNull String profileId) {
        String key = eqAiProcessSessionKey(profileId);
        long lastSessionId = preferences.getLong(key, Long.MIN_VALUE);
        if (lastSessionId == PROCESS_SESSION_ID) {
            return false;
        }
        preferences.edit().putLong(key, PROCESS_SESSION_ID).apply();
        return true;
    }

    private boolean isEqSuggestionDismissedInCurrentSession(@NonNull String profileId) {
        long dismissedOnSession = preferences.getLong(eqAiDismissedSessionKey(profileId), Long.MIN_VALUE);
        return dismissedOnSession == PROCESS_SESSION_ID;
    }

    @NonNull
    private String eqAiPendingKey(@NonNull String profileId) {
        return KEY_AI_EQ_PENDING_JSON_PREFIX + profileId;
    }

    @NonNull
    private String eqAiNextAtKey(@NonNull String profileId) {
        return KEY_AI_EQ_NEXT_AT_PREFIX + profileId;
    }

    @NonNull
    private String eqAiProcessSessionKey(@NonNull String profileId) {
        return KEY_AI_EQ_LAST_PROCESS_SESSION_PREFIX + profileId;
    }

    @NonNull
    private String eqAiDismissedSessionKey(@NonNull String profileId) {
        return KEY_AI_EQ_DISMISSED_SESSION_PREFIX + profileId;
    }

    private void setOutputUi(
            @NonNull String route,
            int iconRes,
            @NonNull String contentDescription
    ) {
        tvOutputRoute.setText(route);
        ivOutputIcon.setImageResource(iconRes);
        ivOutputIcon.setContentDescription(contentDescription);
    }

    @NonNull
    private String buildOutputDescriptorForAi(@Nullable AudioDeviceInfo output) {
        if (output == null) {
            return "parlante integrado del telefono";
        }

        int type = output.getType();
        String typeLabel;
        if (isBluetoothType(type)) {
            typeLabel = "audifonos bluetooth";
        } else if (isWiredType(type)) {
            typeLabel = "audifonos cableados";
        } else if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
            typeLabel = "auricular interno";
        } else if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) {
            typeLabel = "parlante integrado";
        } else {
            typeLabel = "salida de audio";
        }

        String model = deviceName(output, "desconocido");
        String address = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                String raw = output.getAddress();
                if (raw != null) {
                    address = raw.trim();
                }
            } catch (Throwable ignored) {
                // Algunos OEM no exponen direccion de forma consistente.
            }
        }

        if (!TextUtils.isEmpty(address) && !"00:00:00:00:00:00".equals(address)) {
            return typeLabel + " modelo " + model + " (" + address + ")";
        }
        return typeLabel + " modelo " + model;
    }

    @NonNull
    private String buildPhoneModelDescriptor() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String device = Build.DEVICE == null ? "" : Build.DEVICE.trim();

        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(manufacturer)) {
            builder.append(manufacturer);
        }
        if (!TextUtils.isEmpty(model)) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(model);
        }
        if (!TextUtils.isEmpty(device)) {
            if (builder.length() > 0) {
                builder.append(" (");
                builder.append(device);
                builder.append(")");
            } else {
                builder.append(device);
            }
        }

        if (builder.length() == 0) {
            return "modelo de telefono desconocido";
        }
        return builder.toString();
    }

    private void showPresetPopup() {
        if (!isEqEnabled()) {
            return;
        }

        Context context = requireContext();
        String selectedPresetId = preferences.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT);
        List<UserPresetEntry> userPresets = loadUserPresetEntries();
        int popupWidth = dp(188);

        LinearLayout popupRoot = new LinearLayout(context);
        popupRoot.setOrientation(LinearLayout.VERTICAL);
        popupRoot.setPadding(dp(6), dp(6), dp(6), dp(6));

        GradientDrawable popupBackground = new GradientDrawable();
        popupBackground.setColor(ContextCompat.getColor(context, R.color.surface_low));
        popupBackground.setCornerRadius(dp(14));
        popupRoot.setBackground(popupBackground);

        PopupWindow popupWindow = new PopupWindow(
                popupRoot,
            popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(dp(8));
        registerActivePopupWindow(popupWindow);

        for (int i = 0; i < PRESET_CONFIGS.length; i++) {
            PresetConfig preset = PRESET_CONFIGS[i];
            boolean isSelected = preset.id.equals(selectedPresetId);
            TextView row = createPresetRow(preset, isSelected, popupWindow);
            popupRoot.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        if (!userPresets.isEmpty()) {
            View divider = new View(context);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(1)
            );
            dividerParams.topMargin = dp(6);
            dividerParams.bottomMargin = dp(6);
            divider.setLayoutParams(dividerParams);
            divider.setBackgroundColor(withAlpha(ContextCompat.getColor(context, R.color.text_secondary), 0.22f));
            popupRoot.addView(divider);

            for (UserPresetEntry entry : userPresets) {
                boolean isSelected = entry.id.equals(selectedPresetId);
                TextView row = createUserPresetRow(entry, isSelected, popupWindow);
                popupRoot.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
        }

        int xOffset = dp(10);
        popupWindow.showAsDropDown(cardPresetSelector, xOffset, dp(8));
    }

    private void showBassTypePopup() {
        if (cardBassTypeSelector == null || !isEqEnabled()) {
            return;
        }

        Context context = requireContext();
        int selectedBassType = normalizeBassType(
                preferences.getInt(AudioEffectsService.KEY_BASS_TYPE, BASS_TYPE_DEFAULT)
        );
        int popupWidth = dp(228);

        LinearLayout popupRoot = new LinearLayout(context);
        popupRoot.setOrientation(LinearLayout.VERTICAL);
        popupRoot.setPadding(dp(6), dp(6), dp(6), dp(6));

        GradientDrawable popupBackground = new GradientDrawable();
        popupBackground.setColor(ContextCompat.getColor(context, R.color.surface_low));
        popupBackground.setCornerRadius(dp(14));
        popupRoot.setBackground(popupBackground);

        PopupWindow popupWindow = new PopupWindow(
                popupRoot,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(dp(8));
        registerActivePopupWindow(popupWindow);

        for (BassTypeOption option : BASS_TYPE_OPTIONS) {
            boolean isSelected = option.value == selectedBassType;
            TextView row = createBassTypeRow(option, isSelected, popupWindow);
            popupRoot.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        int xOffset = dp(10);
        popupWindow.showAsDropDown(cardBassTypeSelector, xOffset, dp(8));
    }

    private void showReverbPopup() {
        if (cardReverbSelector == null || !isEqEnabled()) {
            return;
        }

        Context context = requireContext();
        int selectedReverbLevel = normalizeReverbLevel(
                preferences.getInt(AudioEffectsService.KEY_REVERB_LEVEL, REVERB_LEVEL_DEFAULT)
        );
        int popupWidth = dp(188);

        LinearLayout popupRoot = new LinearLayout(context);
        popupRoot.setOrientation(LinearLayout.VERTICAL);
        popupRoot.setPadding(dp(6), dp(6), dp(6), dp(6));

        GradientDrawable popupBackground = new GradientDrawable();
        popupBackground.setColor(ContextCompat.getColor(context, R.color.surface_low));
        popupBackground.setCornerRadius(dp(14));
        popupRoot.setBackground(popupBackground);

        PopupWindow popupWindow = new PopupWindow(
                popupRoot,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(dp(8));
        registerActivePopupWindow(popupWindow);

        for (ReverbOption option : REVERB_OPTIONS) {
            boolean isSelected = option.value == selectedReverbLevel;
            TextView row = createReverbRow(option, isSelected, popupWindow);
            popupRoot.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        int xOffset = dp(10);
        popupWindow.showAsDropDown(cardReverbSelector, xOffset, dp(8));
    }

    @NonNull
    private TextView createPresetRow(
            @NonNull PresetConfig preset,
            boolean isSelected,
            @NonNull PopupWindow popupWindow
    ) {
        Context context = requireContext();

        TextView row = new TextView(context);
        row.setText(preset.label);
        row.setSingleLine(true);
        row.setTextSize(13f);
        row.setTypeface(Typeface.DEFAULT_BOLD);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setTextColor(ContextCompat.getColor(context, isSelected ? R.color.stitch_blue_light : R.color.text_secondary));

        GradientDrawable rowContent = new GradientDrawable();
        rowContent.setCornerRadius(dp(10));
        rowContent.setColor(isSelected
                ? withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.22f)
                : Color.TRANSPARENT);

        int rippleColor = withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.32f);
        RippleDrawable rowRipple = new RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null);
        row.setBackground(rowRipple);

        row.setOnClickListener(v -> {
            applyPreset(preset);
            popupWindow.dismiss();
        });

        return row;
    }

    @NonNull
    private TextView createUserPresetRow(
            @NonNull UserPresetEntry preset,
            boolean isSelected,
            @NonNull PopupWindow popupWindow
    ) {
        Context context = requireContext();

        TextView row = new TextView(context);
        row.setText(preset.name);
        row.setSingleLine(true);
        row.setTextSize(13f);
        row.setTypeface(Typeface.DEFAULT_BOLD);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setTextColor(ContextCompat.getColor(context, isSelected ? R.color.stitch_blue_light : R.color.text_secondary));

        GradientDrawable rowContent = new GradientDrawable();
        rowContent.setCornerRadius(dp(10));
        rowContent.setColor(isSelected
                ? withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.22f)
                : Color.TRANSPARENT);

        int rippleColor = withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.32f);
        RippleDrawable rowRipple = new RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null);
        row.setBackground(rowRipple);

        row.setOnClickListener(v -> {
            applyUserPreset(preset);
            popupWindow.dismiss();
        });

        row.setOnLongClickListener(v -> {
            showDeleteUserPresetDialog(preset, popupWindow);
            return true;
        });

        return row;
    }

    @NonNull
    private TextView createBassTypeRow(
            @NonNull BassTypeOption option,
            boolean isSelected,
            @NonNull PopupWindow popupWindow
    ) {
        Context context = requireContext();

        TextView row = new TextView(context);
        row.setText(option.label);
        row.setSingleLine(true);
        row.setTextSize(13f);
        row.setTypeface(Typeface.DEFAULT_BOLD);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setTextColor(ContextCompat.getColor(context, isSelected ? R.color.stitch_blue_light : R.color.text_secondary));

        GradientDrawable rowContent = new GradientDrawable();
        rowContent.setCornerRadius(dp(10));
        rowContent.setColor(isSelected
                ? withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.22f)
                : Color.TRANSPARENT);

        int rippleColor = withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.32f);
        RippleDrawable rowRipple = new RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null);
        row.setBackground(rowRipple);

        row.setOnClickListener(v -> {
            preferences.edit().putInt(AudioEffectsService.KEY_BASS_TYPE, option.value).apply();
            if (tvSelectedBassType != null) {
                tvSelectedBassType.setText(option.label);
            }
            markPresetAsCustom();
            applyIfEnabled();
            popupWindow.dismiss();
        });

        return row;
    }

    @NonNull
    private TextView createReverbRow(
            @NonNull ReverbOption option,
            boolean isSelected,
            @NonNull PopupWindow popupWindow
    ) {
        Context context = requireContext();

        TextView row = new TextView(context);
        row.setText(option.label);
        row.setSingleLine(true);
        row.setTextSize(13f);
        row.setTypeface(Typeface.DEFAULT_BOLD);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setTextColor(ContextCompat.getColor(context, isSelected ? R.color.stitch_blue_light : R.color.text_secondary));

        GradientDrawable rowContent = new GradientDrawable();
        rowContent.setCornerRadius(dp(10));
        rowContent.setColor(isSelected
                ? withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.22f)
                : Color.TRANSPARENT);

        int rippleColor = withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.32f);
        RippleDrawable rowRipple = new RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null);
        row.setBackground(rowRipple);

        row.setOnClickListener(v -> {
            preferences.edit().putInt(AudioEffectsService.KEY_REVERB_LEVEL, option.value).apply();
            if (tvSelectedReverb != null) {
                tvSelectedReverb.setText(option.label);
            }
            applyIfEnabled();
            popupWindow.dismiss();
        });

        return row;
    }

    private void applyPreset(@NonNull PresetConfig preset) {
        restoringUi = true;
        eqCurveView.setBandValues(preset.bands);
        eqCurveView.setHandlesVisible(false);
        sliderBassGain.setValue(quantizeBassGainToggle(preset.bassDb));
        tvBassGainValue.setText(formatDb(sliderBassGain.getValue()));
        float normalizedBassFrequencyHz = normalizeBassFrequencySlider(preset.bassFrequencyHz);
        sliderBassFrequency.setValue(normalizedBassFrequencyHz);
        tvBassFrequencyValue.setText(formatHz(normalizedBassFrequencyHz));
        int normalizedBassType = normalizeBassType(preset.bassType);
        if (tvSelectedBassType != null) {
            tvSelectedBassType.setText(bassTypeLabelFromValue(normalizedBassType));
        }
        tvSelectedPreset.setText(preset.label);
        restoringUi = false;

        savePresetValues(preset);
        applyIfEnabled();
    }

    private void applyUserPreset(@NonNull UserPresetEntry preset) {
        restoringUi = true;
        if (eqCurveView != null) {
            eqCurveView.setBandValues(preset.bands);
            eqCurveView.setHandlesVisible(false);
        }
        if (tvSelectedPreset != null) {
            tvSelectedPreset.setText(preset.name);
        }
        restoringUi = false;

        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < preset.bands.length; i++) {
            editor.putFloat(AudioEffectsService.bandDbKey(i), preset.bands[i]);
        }
        editor.putString(KEY_SELECTED_PRESET, preset.id);
        editor.apply();

        applyIfEnabled();
    }

    private void showDeleteUserPresetDialog(@NonNull UserPresetEntry preset, @NonNull PopupWindow popupWindow) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar preset")
                .setMessage("Deseas eliminar el preset \"" + preset.name + "\"?")
                .setNegativeButton("No", null)
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    String selectedPresetId = preferences.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT);

                    SharedPreferences.Editor editor = preferences.edit();
                    editor.remove(KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX + preset.profileId);
                    for (int i = 0; i < EQ_BAND_COUNT; i++) {
                        editor.remove(userPresetBandKey(preset.profileId, i));
                    }

                    if (TextUtils.equals(selectedPresetId, preset.id)) {
                        editor.putString(KEY_SELECTED_PRESET, PRESET_CUSTOM);
                    }
                    editor.apply();

                    if (TextUtils.equals(selectedPresetId, preset.id) && tvSelectedPreset != null) {
                        tvSelectedPreset.setText(presetLabelFromId(PRESET_CUSTOM));
                    }

                    popupWindow.dismiss();
                })
                .show();
    }

    @NonNull
    private List<UserPresetEntry> loadUserPresetEntries() {
        Map<String, ?> allValues = preferences.getAll();
        if (allValues == null || allValues.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserPresetEntry> result = new ArrayList<>();
        for (Map.Entry<String, ?> entry : allValues.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX)) {
                continue;
            }

            Object valueObj = entry.getValue();
            if (!(valueObj instanceof String)) {
                continue;
            }

            String profileId = key.substring(KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX.length());
            if (TextUtils.isEmpty(profileId)) {
                continue;
            }

            String name = ((String) valueObj).trim();
            if (TextUtils.isEmpty(name)) {
                continue;
            }

            float[] bands = readUserPresetBands(profileId);
            result.add(new UserPresetEntry(profileId, userPresetIdForProfile(profileId), name, bands));
        }

        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        Collections.sort(result, Comparator.comparing(item -> item.name.toLowerCase(Locale.US)));
        return result;
    }

    @Nullable
    private UserPresetEntry findUserPresetById(@Nullable String presetId) {
        String profileId = profileIdFromUserPresetId(presetId);
        if (TextUtils.isEmpty(profileId)) {
            return null;
        }

        String name = preferences.getString(KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX + profileId, "");
        if (name == null) {
            return null;
        }
        String cleanedName = name.trim();
        if (TextUtils.isEmpty(cleanedName)) {
            return null;
        }

        float[] bands = readUserPresetBands(profileId);
        return new UserPresetEntry(profileId, userPresetIdForProfile(profileId), cleanedName, bands);
    }

    private float[] readUserPresetBands(@NonNull String profileId) {
        float[] bands = new float[EQ_BAND_COUNT];
        boolean hasStoredUserBands = false;

        for (int i = 0; i < bands.length; i++) {
            String bandKey = userPresetBandKey(profileId, i);
            if (preferences.contains(bandKey)) {
                bands[i] = readNumericPrefAsFloat(bandKey, 0f);
                hasStoredUserBands = true;
            }
        }

        if (hasStoredUserBands) {
            return bands;
        }

        // Migration fallback: si no hay snapshot de usuario, intentamos tomar valores del bucket de perfil.
        boolean hasProfileBands = false;
        for (int i = 0; i < bands.length; i++) {
            String fallbackProfileBandKey = KEY_DEVICE_PROFILE_PREFIX + profileId + "_" + AudioEffectsService.bandDbKey(i);
            if (preferences.contains(fallbackProfileBandKey)) {
                bands[i] = readNumericPrefAsFloat(fallbackProfileBandKey, 0f);
                hasProfileBands = true;
            }
        }

        if (!hasProfileBands && TextUtils.equals(profileId, activeProfileIdForCustomPreset())) {
            for (int i = 0; i < bands.length; i++) {
                bands[i] = preferences.getFloat(AudioEffectsService.bandDbKey(i), 0f);
            }
        }

        saveUserPresetBandsForProfile(profileId, bands);
        return bands;
    }

    private float readNumericPrefAsFloat(@NonNull String key, float defaultValue) {
        try {
            return preferences.getFloat(key, defaultValue);
        } catch (ClassCastException ignored) {
            // Fallback para datos legacy tipados como int/long/double.
        }

        Map<String, ?> all = preferences.getAll();
        if (all == null || !all.containsKey(key)) {
            return defaultValue;
        }

        Object raw = all.get(key);
        if (raw instanceof Number) {
            return ((Number) raw).floatValue();
        }
        if (raw instanceof String) {
            try {
                return Float.parseFloat(((String) raw).trim());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private void saveUserPresetBandsForProfile(@NonNull String profileId, @NonNull float[] bands) {
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < EQ_BAND_COUNT; i++) {
            float value = i < bands.length ? bands[i] : 0f;
            editor.putFloat(userPresetBandKey(profileId, i), value);
        }
        editor.apply();
    }

    @NonNull
    private String userPresetBandKey(@NonNull String profileId, int bandIndex) {
        return KEY_USER_PRESET_BAND_PREFIX + profileId + "_" + bandIndex;
    }

    @NonNull
    private String userPresetIdForProfile(@NonNull String profileId) {
        return PRESET_USER_PREFIX + profileId;
    }

    private boolean isUserPresetId(@Nullable String presetId) {
        return presetId != null && presetId.startsWith(PRESET_USER_PREFIX);
    }

    @Nullable
    private String profileIdFromUserPresetId(@Nullable String presetId) {
        if (!isUserPresetId(presetId)) {
            return null;
        }
        String profileId = presetId.substring(PRESET_USER_PREFIX.length());
        return TextUtils.isEmpty(profileId) ? null : profileId;
    }

    private void showGraphicEqEditorDialog() {
        if (!isAdded() || preferences == null || !isEqEnabled()) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_eq_editor, null, false);
        EqCurveEditorView modalEqView = dialogView.findViewById(R.id.eqCurveEditorModal);
        LinearLayout gainTextsRow = dialogView.findViewById(R.id.gain_texts);
        LinearLayout frequencyTextsRow = dialogView.findViewById(R.id.frequency_texts);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnEqEditorClose);
        MaterialButton btnAccept = dialogView.findViewById(R.id.btnEqEditorAccept);

        float[] originalBands = readCurrentBandsSnapshot();
        String originalPresetId = preferences.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT);

        float[] draftBands = new float[EQ_BAND_COUNT];
        System.arraycopy(originalBands, 0, draftBands, 0, originalBands.length);
        TextView[] gainValueViews = populateGraphicEqDialogRows(gainTextsRow, frequencyTextsRow, draftBands);

        final boolean[] accepted = {false};

        if (modalEqView != null) {
            modalEqView.setBandValues(draftBands);
            modalEqView.setEditingEnabled(true);
            modalEqView.setHandlesVisible(true);
            modalEqView.setGridVisible(false);
            modalEqView.setAxisDbLabelsEnabled(false);
            modalEqView.setAreaFillEnabled(false);
            modalEqView.setModalBandGuidesEnabled(true);
            modalEqView.setOnBandChangeListener((bandIndex, value) -> {
                if (bandIndex < 0 || bandIndex >= draftBands.length) {
                    return;
                }
                draftBands[bandIndex] = value;
                refreshGraphicEqDialogGainLabels(gainValueViews, draftBands);
                applyDraftBandsPreview(draftBands);
            });
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialog.setOnDismissListener(d -> {
            if (!accepted[0]) {
                restoreGraphicEqSnapshot(originalBands, originalPresetId);
            }
        });

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                if (needsCustomPresetNameForCurrentProfile()) {
                    promptForCustomPresetName(name -> {
                        persistCustomPresetNameForCurrentProfile(name);
                        commitAcceptedGraphicEqChanges(draftBands);
                        accepted[0] = true;
                        dialog.dismiss();
                    });
                    return;
                }

                commitAcceptedGraphicEqChanges(draftBands);
                accepted[0] = true;
                dialog.dismiss();
            });
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
            dialog.getWindow().setDimAmount(0.88f);
        }
    }

    @NonNull
    private TextView[] populateGraphicEqDialogRows(
            @Nullable LinearLayout gainTextsRow,
            @Nullable LinearLayout frequencyTextsRow,
            @NonNull float[] bandValues
    ) {
        TextView[] gainValueViews = new TextView[EQ_BAND_COUNT];

        if (gainTextsRow != null) {
            gainTextsRow.removeAllViews();
            for (int i = 0; i < EQ_BAND_COUNT; i++) {
                TextView gainView = createGraphicEqDialogLabel(true);
                gainView.setText(formatGraphicEqDialogGain(i < bandValues.length ? bandValues[i] : 0f));
                gainTextsRow.addView(gainView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                gainValueViews[i] = gainView;
            }
        }

        if (frequencyTextsRow != null) {
            frequencyTextsRow.removeAllViews();
            for (int i = 0; i < EQ_BAND_COUNT; i++) {
                TextView frequencyView = createGraphicEqDialogLabel(false);
                float frequencyHz = i < GRAPHIC_EQ_DIALOG_FREQUENCIES_HZ.length
                        ? GRAPHIC_EQ_DIALOG_FREQUENCIES_HZ[i]
                        : AudioEffectsService.WAVELET_9_BAND_FREQUENCIES_HZ[i];
                frequencyView.setText(formatGraphicEqDialogFrequency(frequencyHz));
                frequencyTextsRow.addView(frequencyView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
        }

        return gainValueViews;
    }

    private void refreshGraphicEqDialogGainLabels(@NonNull TextView[] gainValueViews, @NonNull float[] bandValues) {
        for (int i = 0; i < gainValueViews.length; i++) {
            TextView gainView = gainValueViews[i];
            if (gainView == null) {
                continue;
            }
            float value = i < bandValues.length ? bandValues[i] : 0f;
            gainView.setText(formatGraphicEqDialogGain(value));
        }
    }

    @NonNull
    private TextView createGraphicEqDialogLabel(boolean gainRow) {
        TextView textView = new TextView(requireContext());
        textView.setGravity(Gravity.CENTER);
        textView.setSingleLine(true);
        textView.setMaxLines(1);
        textView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setPadding(0, gainRow ? dp(2) : dp(6), 0, gainRow ? dp(2) : dp(4));
        textView.setTextSize(gainRow ? 11f : 12f);
        textView.setTextColor(ContextCompat.getColor(requireContext(), gainRow ? R.color.text_secondary : R.color.text_primary));
        textView.setTypeface(gainRow ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
        return textView;
    }

    @NonNull
    private String formatGraphicEqDialogGain(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    @NonNull
    private String formatGraphicEqDialogFrequency(float frequencyHz) {
        if (frequencyHz >= 1000f) {
            float khz = frequencyHz / 1000f;
            if (Math.abs(khz - Math.round(khz)) > 0.01f) {
                return String.format(Locale.US, "%.1fk", khz);
            }
            return String.format(Locale.US, "%dk", Math.round(khz));
        }

        if (Math.abs(frequencyHz - Math.round(frequencyHz)) > 0.01f) {
            return String.format(Locale.US, "%.1f", frequencyHz);
        }
        return String.format(Locale.US, "%d", Math.round(frequencyHz));
    }

    private void applyDraftBandsPreview(@NonNull float[] draftBands) {
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < draftBands.length; i++) {
            editor.putFloat(AudioEffectsService.bandDbKey(i), draftBands[i]);
        }
        editor.apply();

        if (eqCurveView != null) {
            eqCurveView.setBandValues(draftBands);
            eqCurveView.setHandlesVisible(false);
        }

        applyIfEnabled();
    }

    private void commitAcceptedGraphicEqChanges(@NonNull float[] draftBands) {
        String activeProfileId = activeProfileIdForCustomPreset();
        String customPresetName = customPresetNameForCurrentProfile();
        String selectedPresetId = TextUtils.isEmpty(customPresetName)
                ? PRESET_CUSTOM
                : userPresetIdForProfile(activeProfileId);

        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < draftBands.length; i++) {
            editor.putFloat(AudioEffectsService.bandDbKey(i), draftBands[i]);
        }
        editor.putString(KEY_SELECTED_PRESET, selectedPresetId);
        editor.apply();

        if (!TextUtils.isEmpty(customPresetName)) {
            saveUserPresetBandsForProfile(activeProfileId, draftBands);
        }

        if (eqCurveView != null) {
            eqCurveView.setBandValues(draftBands);
            eqCurveView.setHandlesVisible(false);
        }
        if (tvSelectedPreset != null) {
            tvSelectedPreset.setText(presetLabelFromId(selectedPresetId));
        }

        applyIfEnabled();
    }

    private void restoreGraphicEqSnapshot(@NonNull float[] originalBands, @Nullable String originalPresetId) {
        String safePresetId = TextUtils.isEmpty(originalPresetId) ? PRESET_DEFAULT : originalPresetId;

        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < originalBands.length; i++) {
            editor.putFloat(AudioEffectsService.bandDbKey(i), originalBands[i]);
        }
        editor.putString(KEY_SELECTED_PRESET, safePresetId);
        editor.apply();

        if (eqCurveView != null) {
            eqCurveView.setBandValues(originalBands);
            eqCurveView.setHandlesVisible(false);
        }
        if (tvSelectedPreset != null) {
            tvSelectedPreset.setText(presetLabelFromId(safePresetId));
        }

        applyIfEnabled();
    }

    private boolean needsCustomPresetNameForCurrentProfile() {
        String currentName = customPresetNameForCurrentProfile();
        return TextUtils.isEmpty(currentName);
    }

    @NonNull
    private float[] readCurrentBandsSnapshot() {
        if (eqCurveView != null) {
            return eqCurveView.getBandValuesSnapshot();
        }

        float[] snapshot = new float[EQ_BAND_COUNT];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = preferences.getFloat(AudioEffectsService.bandDbKey(i), 0f);
        }
        return snapshot;
    }

    @NonNull
    private String resolveMutableCustomPresetId() {
        String customName = customPresetNameForCurrentProfile();
        if (!TextUtils.isEmpty(customName)) {
            return userPresetIdForProfile(activeProfileIdForCustomPreset());
        }
        return PRESET_CUSTOM;
    }

    @NonNull
    private String activeProfileIdForCustomPreset() {
        if (!TextUtils.isEmpty(boundProfileId)) {
            return boundProfileId;
        }
        return AudioDeviceProfileStore.getActiveProfileId(preferences, null);
    }

    @NonNull
    private String profileCustomPresetNameKey(@NonNull String profileId) {
        return KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX + profileId;
    }

    @Nullable
    private String customPresetNameForCurrentProfile() {
        if (preferences == null) {
            return null;
        }
        String profileId = activeProfileIdForCustomPreset();
        String raw = preferences.getString(profileCustomPresetNameKey(profileId), null);
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return TextUtils.isEmpty(value) ? null : value;
    }

    private void persistCustomPresetNameForCurrentProfile(@NonNull String presetName) {
        if (preferences == null) {
            return;
        }
        String cleanName = presetName.trim();
        if (TextUtils.isEmpty(cleanName)) {
            return;
        }
        if (cleanName.length() > 32) {
            cleanName = cleanName.substring(0, 32);
        }
        String profileId = activeProfileIdForCustomPreset();
        preferences.edit().putString(profileCustomPresetNameKey(profileId), cleanName).apply();
    }

    private void promptForCustomPresetName(@NonNull PresetNameCallback callback) {
        if (!isAdded()) {
            return;
        }

        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint("Ejemplo: Mis audifonos");
        input.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        input.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        int padding = dp(14);
        input.setPadding(padding, padding, padding, padding);

        AlertDialog nameDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Nombre del preset")
                .setMessage("Ponle un nombre a tu preset personalizado para este dispositivo. Solo se pedira una vez.")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();

        nameDialog.setOnShowListener(dialogInterface -> {
            android.widget.Button positiveButton = nameDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String value = input.getText() == null ? "" : input.getText().toString().trim();
                if (TextUtils.isEmpty(value)) {
                    input.setError("Escribe un nombre para el preset");
                    return;
                }
                callback.onNameConfirmed(value);
                nameDialog.dismiss();
            });
        });

        nameDialog.show();
    }

    private void ensureBassDefaults() {
        boolean hasBassDb = preferences.contains(AudioEffectsService.KEY_BASS_DB);
        boolean hasBassType = preferences.contains(AudioEffectsService.KEY_BASS_TYPE);
        boolean hasBassFrequency = preferences.contains(AudioEffectsService.KEY_BASS_FREQUENCY_HZ);
        boolean hasReverbLevel = preferences.contains(AudioEffectsService.KEY_REVERB_LEVEL);
        if (hasBassDb && hasBassType && hasBassFrequency && hasReverbLevel) {
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        if (!hasBassDb) {
            editor.putFloat(AudioEffectsService.KEY_BASS_DB, 0f);
        }
        if (!hasBassType) {
            editor.putInt(AudioEffectsService.KEY_BASS_TYPE, BASS_TYPE_DEFAULT);
        }
        if (!hasBassFrequency) {
            editor.putFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, BASS_DEFAULT_FREQUENCY_HZ);
        }
        if (!hasReverbLevel) {
            editor.putInt(AudioEffectsService.KEY_REVERB_LEVEL, REVERB_LEVEL_DEFAULT);
        }
        editor.apply();
    }

    private void ensurePresetStateConsistency() {
        if (!preferences.contains(KEY_SELECTED_PRESET)) {
            // Si viene una curva restaurada sin metadata de preset, la respetamos como personalizada.
            String activeUserPresetId = resolveActiveUserPresetIdIfSynced();
            preferences.edit()
                    .putString(KEY_SELECTED_PRESET, TextUtils.isEmpty(activeUserPresetId) ? PRESET_CUSTOM : activeUserPresetId)
                    .apply();
            return;
        }

        String presetId = preferences.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT);
        if (PRESET_CUSTOM.equals(presetId)) {
            String activeUserPresetId = resolveActiveUserPresetIdIfSynced();
            if (!TextUtils.isEmpty(activeUserPresetId)) {
                preferences.edit().putString(KEY_SELECTED_PRESET, activeUserPresetId).apply();
            }
            return;
        }

        if (isUserPresetId(presetId)) {
            UserPresetEntry userPreset = findUserPresetById(presetId);
            if (userPreset == null || !isUserPresetSynced(userPreset)) {
                String activeUserPresetId = resolveActiveUserPresetIdIfSynced();
                preferences.edit()
                        .putString(KEY_SELECTED_PRESET, TextUtils.isEmpty(activeUserPresetId) ? PRESET_CUSTOM : activeUserPresetId)
                        .apply();
            }
            return;
        }

        PresetConfig selectedPreset = findPresetById(presetId);
        if (selectedPreset == null) {
            String activeUserPresetId = resolveActiveUserPresetIdIfSynced();
            preferences.edit()
                    .putString(KEY_SELECTED_PRESET, TextUtils.isEmpty(activeUserPresetId) ? PRESET_CUSTOM : activeUserPresetId)
                    .apply();
            return;
        }

        if (!isPresetSynced(selectedPreset)) {
            // Evita pisar una configuracion real del usuario cuando no coincide exactamente con un preset fijo.
            String activeUserPresetId = resolveActiveUserPresetIdIfSynced();
            preferences.edit()
                    .putString(KEY_SELECTED_PRESET, TextUtils.isEmpty(activeUserPresetId) ? PRESET_CUSTOM : activeUserPresetId)
                    .apply();
        }
    }

    @Nullable
    private String resolveActiveUserPresetIdIfSynced() {
        String activeProfileId = activeProfileIdForCustomPreset();
        if (TextUtils.isEmpty(activeProfileId)) {
            return null;
        }

        String activeUserPresetId = userPresetIdForProfile(activeProfileId);
        UserPresetEntry activeUserPreset = findUserPresetById(activeUserPresetId);
        if (activeUserPreset == null || !isUserPresetSynced(activeUserPreset)) {
            return null;
        }

        return activeUserPresetId;
    }

    private boolean isUserPresetSynced(@NonNull UserPresetEntry preset) {
        for (int i = 0; i < preset.bands.length; i++) {
            float stored = preferences.getFloat(AudioEffectsService.bandDbKey(i), 0f);
            if (Math.abs(stored - preset.bands[i]) > 0.01f) {
                return false;
            }
        }
        return true;
    }

    private boolean isPresetSynced(@NonNull PresetConfig preset) {
        for (int i = 0; i < preset.bands.length; i++) {
            float stored = preferences.getFloat(AudioEffectsService.bandDbKey(i), 0f);
            if (Math.abs(stored - preset.bands[i]) > 0.01f) {
                return false;
            }
        }

        return true;
    }

    private void savePresetValues(@NonNull PresetConfig preset) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_SELECTED_PRESET, preset.id);
        for (int i = 0; i < preset.bands.length; i++) {
            editor.putFloat(AudioEffectsService.bandDbKey(i), preset.bands[i]);
        }
        editor.putFloat(AudioEffectsService.KEY_BASS_DB, quantizeBassGainToggle(preset.bassDb));
        editor.putFloat(
                AudioEffectsService.KEY_BASS_FREQUENCY_HZ,
                normalizeBassFrequencySlider(preset.bassFrequencyHz)
        );
        editor.putInt(AudioEffectsService.KEY_BASS_TYPE, normalizeBassType(preset.bassType));
        editor.apply();
    }

    private float quantizeBassGainToggle(float rawValue) {
        float clamped = clamp(rawValue, BASS_GAIN_MIN_DB, BASS_GAIN_MAX_DB);
        return clamped > BASS_GAIN_TOGGLE_THRESHOLD_DB ? BASS_GAIN_MAX_DB : BASS_GAIN_MIN_DB;
    }

    private void markPresetAsCustom() {
        String targetPresetId = resolveMutableCustomPresetId();
        String currentPreset = preferences.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT);
        if (TextUtils.equals(currentPreset, targetPresetId)) {
            return;
        }
        preferences.edit().putString(KEY_SELECTED_PRESET, targetPresetId).apply();
        tvSelectedPreset.setText(presetLabelFromId(targetPresetId));
    }

    @Nullable
    private PresetConfig findPresetById(@Nullable String presetId) {
        for (PresetConfig presetConfig : PRESET_CONFIGS) {
            if (presetConfig.id.equals(presetId)) {
                return presetConfig;
            }
        }
        return null;
    }

    @NonNull
    private String presetLabelFromId(@Nullable String presetId) {
        if (PRESET_CUSTOM.equals(presetId)) {
            String customName = customPresetNameForCurrentProfile();
            return TextUtils.isEmpty(customName) ? "Personalizado" : customName;
        }

        if (isUserPresetId(presetId)) {
            UserPresetEntry userPreset = findUserPresetById(presetId);
            if (userPreset != null && !TextUtils.isEmpty(userPreset.name)) {
                return userPreset.name;
            }
            return "Preset usuario";
        }

        PresetConfig preset = findPresetById(presetId);
        return preset != null ? preset.label : "Predeterminado";
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

    private String deviceName(@NonNull AudioDeviceInfo deviceInfo, @NonNull String fallback) {
        CharSequence productName = deviceInfo.getProductName();
        if (productName == null) {
            return fallback;
        }
        String value = productName.toString().trim();
        if (TextUtils.isEmpty(value) || "null".equalsIgnoreCase(value)) {
            return fallback;
        }
        return value;
    }

    private void startOrUpdateService() {
        Context context = requireContext().getApplicationContext();
        Intent serviceIntent = new Intent(context, AudioEffectsService.class);
        serviceIntent.setAction(AudioEffectsService.ACTION_APPLY);
        try {
            ContextCompat.startForegroundService(context, serviceIntent);
        } catch (Throwable ignored) {
            // Fallback para OEMs que restringen foreground service de forma transitoria.
            context.startService(serviceIntent);
        }
    }

    private void stopEqService() {
        Context context = requireContext().getApplicationContext();
        Intent serviceIntent = new Intent(context, AudioEffectsService.class);
        serviceIntent.setAction(AudioEffectsService.ACTION_STOP);
        try {
            context.startService(serviceIntent);
        } catch (Throwable ignored) {
            // Evita crash por restricciones transitorias de OEM.
        }
    }

    private String formatDb(float value) {
        return String.format(Locale.US, "%.1f dB", value);
    }

    private String formatHz(float value) {
        return String.format(Locale.US, "%d Hz", Math.round(value));
    }

    private float normalizeBassFrequencySlider(float rawValue) {
        return AudioEffectsService.normalizeBassFrequencySliderValue(rawValue);
    }

    private int withAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int normalizeBassType(int rawType) {
        if (rawType == AudioEffectsService.BASS_TYPE_TRANSIENT_COMPRESSOR) {
            return AudioEffectsService.BASS_TYPE_TRANSIENT_COMPRESSOR;
        }
        if (rawType == AudioEffectsService.BASS_TYPE_SUSTAIN_COMPRESSOR) {
            return AudioEffectsService.BASS_TYPE_SUSTAIN_COMPRESSOR;
        }
        return AudioEffectsService.BASS_TYPE_NATURAL;
    }

    private int normalizeReverbLevel(int rawLevel) {
        if (rawLevel == AudioEffectsService.REVERB_LEVEL_OFF) {
            return AudioEffectsService.REVERB_LEVEL_OFF;
        }
        if (rawLevel == AudioEffectsService.REVERB_LEVEL_LIGHT) {
            return AudioEffectsService.REVERB_LEVEL_LIGHT;
        }
        if (rawLevel == AudioEffectsService.REVERB_LEVEL_MEDIUM) {
            return AudioEffectsService.REVERB_LEVEL_MEDIUM;
        }
        if (rawLevel == AudioEffectsService.REVERB_LEVEL_STRONG) {
            return AudioEffectsService.REVERB_LEVEL_STRONG;
        }
        return AudioEffectsService.REVERB_LEVEL_OFF;
    }

    @Nullable
    private BassTypeOption findBassTypeOption(int bassType) {
        int normalized = normalizeBassType(bassType);
        for (BassTypeOption option : BASS_TYPE_OPTIONS) {
            if (option.value == normalized) {
                return option;
            }
        }
        return null;
    }

    @NonNull
    private String bassTypeLabelFromValue(int bassType) {
        BassTypeOption option = findBassTypeOption(bassType);
        if (option != null) {
            return option.label;
        }
        return BASS_TYPE_OPTIONS[0].label;
    }

    @Nullable
    private ReverbOption findReverbOption(int reverbLevel) {
        int normalized = normalizeReverbLevel(reverbLevel);
        for (ReverbOption option : REVERB_OPTIONS) {
            if (option.value == normalized) {
                return option;
            }
        }
        return null;
    }

    @NonNull
    private String reverbLabelFromLevel(int reverbLevel) {
        ReverbOption option = findReverbOption(reverbLevel);
        if (option != null) {
            return option.label;
        }
        return REVERB_OPTIONS[2].label;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class PresetConfig {
        final String id;
        final String label;
        final float[] bands;
        final float bassDb;
        final float bassFrequencyHz;
        final int bassType;

        PresetConfig(String id, String label, float[] bands) {
            this(id, label, bands, 0f, BASS_DEFAULT_FREQUENCY_HZ, BASS_TYPE_DEFAULT);
        }

        PresetConfig(String id, String label, float[] bands, float bassDb, float bassFrequencyHz, int bassType) {
            this.id = id;
            this.label = label;
            this.bands = bands;
            this.bassDb = bassDb;
            this.bassFrequencyHz = bassFrequencyHz;
            this.bassType = bassType;
        }
    }

    private static class BassTypeOption {
        final int value;
        final String label;

        BassTypeOption(int value, @NonNull String label) {
            this.value = value;
            this.label = label;
        }
    }

    private static class ReverbOption {
        final int value;
        final String label;

        ReverbOption(int value, @NonNull String label) {
            this.value = value;
            this.label = label;
        }
    }

    private static class UserPresetEntry {
        final String profileId;
        final String id;
        final String name;
        final float[] bands;

        UserPresetEntry(
                @NonNull String profileId,
                @NonNull String id,
                @NonNull String name,
                @NonNull float[] bands
        ) {
            this.profileId = profileId;
            this.id = id;
            this.name = name;
            this.bands = bands;
        }
    }

    private static class EqAiUndoSnapshot {
        final float[] bands;
        final float bassDb;
        final float bassFrequencyHz;
        final String presetId;

        EqAiUndoSnapshot(float[] bands, float bassDb, float bassFrequencyHz, @NonNull String presetId) {
            this.bands = bands;
            this.bassDb = bassDb;
            this.bassFrequencyHz = bassFrequencyHz;
            this.presetId = presetId;
        }
    }

    private interface PresetNameCallback {
        void onNameConfirmed(@NonNull String name);
    }
}
