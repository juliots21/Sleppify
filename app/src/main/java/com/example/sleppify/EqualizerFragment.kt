package com.example.sleppify

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class EqualizerFragment : Fragment() {

    companion object {
        private const val KEY_SELECTED_PRESET = "selected_preset"
        private const val KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX = "profile_custom_preset_name_"
        private const val KEY_USER_PRESET_BAND_PREFIX = "user_preset_band_"
        private const val KEY_DEVICE_PROFILE_PREFIX = "device_profile_"
        private const val PRESET_DEFAULT = "default"
        private const val PRESET_CUSTOM = "custom"
        private const val PRESET_USER_PREFIX = "user_profile_"
        private const val PRESET_DARK = "dark"
        private const val PRESET_BRIGHT = "bright"
        private const val PRESET_BASS_BOOST = "bass_boost"
        private const val PRESET_BASS_REDUCTION = "bass_reduction"
        private const val PRESET_TREBLE_BOOST = "treble_boost"
        private const val PRESET_TREBLE_REDUCTION = "treble_reduction"
        private const val PRESET_VOCAL_BOOST = "vocal_boost"
        private const val PRESET_M_SHAPED = "m_shaped"
        private const val PRESET_U_SHAPED = "u_shaped"
        private const val PRESET_V_SHAPE = "v_shape"
        private const val PRESET_W_SHAPED = "w_shaped"
        private const val FORCE_SPEAKER_FALLBACK_WINDOW_MS = 4000L
        private const val OUTPUT_REFRESH_MIN_INTERVAL_MS = 900L
        private const val EQ_BAND_COUNT = AudioEffectsService.EQ_BAND_COUNT
        private const val BASS_GAIN_MAX_DB = AudioEffectsService.BASS_DB_MAX
        private const val BASS_GAIN_MIN_DB = 0f
        private const val BASS_GAIN_TOGGLE_THRESHOLD_DB = BASS_GAIN_MAX_DB * 0.5f

        private const val BASS_DEFAULT_FREQUENCY_HZ = AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ
        private const val BASS_TYPE_DEFAULT = AudioEffectsService.BASS_TYPE_NATURAL
        private const val REVERB_LEVEL_DEFAULT = AudioEffectsService.REVERB_LEVEL_OFF
        
        private val GRAPHIC_EQ_DIALOG_FREQUENCIES_HZ = floatArrayOf(
            62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )

        private val PRESET_CONFIGS = arrayOf(
            PresetConfig(PRESET_DEFAULT, "Plano", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_DARK, "Oscuro", floatArrayOf(4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -2.0f, -3.0f, -4.0f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_BRIGHT, "Brillante", floatArrayOf(-4.0f, -3.0f, -2.0f, -1.0f, 0.0f, 1.0f, 2.0f, 3.0f, 4.0f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_BASS_BOOST, "Refuerzo de graves", floatArrayOf(5.5f, 4.5f, 1.5f, 0.2f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f), 3.0f, AudioEffectsService.bassSliderFromCutoffHz(80f), AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_BASS_REDUCTION, "Reduccion de graves", floatArrayOf(-5.5f, -4.5f, -1.5f, -0.2f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_TREBLE_BOOST, "Refuerzo de agudos", floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.2f, 1.5f, 5.3f, 3.8f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_TREBLE_REDUCTION, "Reduccion de agudos", floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.2f, -1.5f, -5.3f, -3.8f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_VOCAL_BOOST, "Refuerzo vocal", floatArrayOf(-1.0f, -1.1f, -0.6f, 0.7f, 3.1f, 4.6f, 3.3f, 0.6f, 0.0f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_M_SHAPED, "Forma M", floatArrayOf(-3.0f, 0.0f, 2.8f, 0.5f, -2.0f, 0.5f, 2.8f, 0.0f, -3.0f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_U_SHAPED, "Forma U", floatArrayOf(4.0f, 0.0f, -2.0f, -2.6f, -2.7f, -2.6f, -2.0f, 0.0f, 4.0f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_V_SHAPE, "Forma V", floatArrayOf(4.0f, 2.9f, 0.2f, -2.3f, -4.0f, -2.3f, 0.2f, 2.9f, 4.0f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL),
            PresetConfig(PRESET_W_SHAPED, "Forma W", floatArrayOf(3.0f, 0.0f, -2.8f, -0.5f, 2.0f, -0.5f, -2.8f, 0.0f, 3.0f), 0f, BASS_DEFAULT_FREQUENCY_HZ, AudioEffectsService.BASS_TYPE_NATURAL)
        )

        private val BASS_TYPE_OPTIONS = arrayOf(
            BassTypeOption(AudioEffectsService.BASS_TYPE_NATURAL, "Natural"),
            BassTypeOption(AudioEffectsService.BASS_TYPE_TRANSIENT_COMPRESSOR, "Compresor de transitorios"),
            BassTypeOption(AudioEffectsService.BASS_TYPE_SUSTAIN_COMPRESSOR, "Compresor de sustain")
        )

        private val REVERB_OPTIONS = arrayOf(
            ReverbOption(AudioEffectsService.REVERB_LEVEL_OFF, "Desactivada"),
            ReverbOption(AudioEffectsService.REVERB_LEVEL_LIGHT, "Ligera"),
            ReverbOption(AudioEffectsService.REVERB_LEVEL_MEDIUM, "Media"),
            ReverbOption(AudioEffectsService.REVERB_LEVEL_STRONG, "Intensa")
        )
    }

    private var preferences: SharedPreferences? = null
    private var tvOutputRoute: TextView? = null
    private var ivOutputIcon: ImageView? = null
    private var tvSelectedPreset: TextView? = null
    private var cardPresetSelector: View? = null
    private var cardGraphicEq: View? = null
    private var cardBassTuner: View? = null

    private var eqCurveView: EqCurveEditorView? = null
    private var switchEqEnabled: SwitchMaterial? = null
    private var activePopupWindow: PopupWindow? = null
    private var sliderBassGain: Slider? = null
    private var tvBassGainValue: TextView? = null
    private var tvSelectedBassType: TextView? = null
    private var cardBassTypeSelector: View? = null
    private var tvSelectedReverb: TextView? = null
    private var cardReverbSelector: View? = null
    private var sliderBassFrequency: Slider? = null
    private var tvBassFrequencyValue: TextView? = null

    private var restoringUi = false
    private var hasLoadedUi = false
    private var boundProfileId: String? = null
    private var audioManager: AudioManager? = null
    private var forceSpeakerOutputSelection = false
    private var forceSpeakerFallbackUntilMs = 0L
    private var lastOutputRefreshAtMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val outputDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>?) {
            if (addedDevices != null && containsHeadphoneLikeOutput(addedDevices)) {
                forceSpeakerOutputSelection = false
                forceSpeakerFallbackUntilMs = 0L
            }
            refreshOutputDeviceAndProfile(true, true)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>?) {
            if (removedDevices != null && containsHeadphoneLikeOutput(removedDevices)) {
                forceSpeakerOutputSelection = true
                forceSpeakerFallbackUntilMs = System.currentTimeMillis() + FORCE_SPEAKER_FALLBACK_WINDOW_MS
            }
            refreshOutputDeviceAndProfile(true, true)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = requireContext().getSharedPreferences(AudioEffectsService.PREFS_NAME, Context.MODE_PRIVATE)
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        hasLoadedUi = false
        boundProfileId = null

        tvOutputRoute = view.findViewById(R.id.tvOutputRoute)
        ivOutputIcon = view.findViewById(R.id.ivOutputIcon)
        tvSelectedPreset = view.findViewById(R.id.tvSelectedPreset)
        cardPresetSelector = view.findViewById(R.id.cardPresetSelector)
        cardGraphicEq = view.findViewById(R.id.cardGraphicEq)
        cardBassTuner = view.findViewById(R.id.cardBassTuner)

        eqCurveView = view.findViewById(R.id.eqCurveView)
        switchEqEnabled = view.findViewById(R.id.switchEqEnabled)

        sliderBassGain = view.findViewById(R.id.sliderBassGain)
        tvBassGainValue = view.findViewById(R.id.tvBassGainValue)
        tvSelectedBassType = view.findViewById(R.id.tvSelectedBassType)
        cardBassTypeSelector = view.findViewById(R.id.cardBassTypeSelector)
        tvSelectedReverb = view.findViewById(R.id.tvSelectedReverb)
        cardReverbSelector = view.findViewById(R.id.cardReverbSelector)
        sliderBassFrequency = view.findViewById(R.id.sliderBassFrequency)
        tvBassFrequencyValue = view.findViewById(R.id.tvBassFrequencyValue)

        sliderBassFrequency?.valueFrom = AudioEffectsService.BASS_FREQUENCY_MIN_HZ
        sliderBassFrequency?.valueTo = AudioEffectsService.BASS_FREQUENCY_MAX_HZ

        setupListeners()
        refreshOutputDeviceAndProfile(false, true)
        refreshEqModuleState()
        ensureEqServiceActive()
        applyAmoledStyles(view)
    }

    override fun onResume() {
        super.onResume()
        refreshOutputDeviceAndProfile(true, false)
        refreshEqModuleState()
        ensureEqServiceActive()
    }

    fun onCloudEqHydrationCompleted() {
        if (!isAdded || preferences == null) return

        mainHandler.post {
            if (!isAdded) return@post
            hasLoadedUi = false
            refreshOutputDeviceAndProfile(true, true)
            refreshEqModuleState()
            ensureEqServiceActive()
        }
    }

    override fun onStart() {
        super.onStart()
        registerOutputDeviceCallback()
    }

    override fun onStop() {
        dismissActivePopupWindow()
        unregisterOutputDeviceCallback()
        super.onStop()
    }

    private fun dismissActivePopupWindow() {
        try {
            activePopupWindow?.dismiss()
        } catch (ignored: Throwable) {
        }
        activePopupWindow = null
    }

    private fun registerActivePopupWindow(popupWindow: PopupWindow) {
        dismissActivePopupWindow()
        activePopupWindow = popupWindow
        popupWindow.setOnDismissListener {
            if (activePopupWindow === popupWindow) {
                activePopupWindow = null
            }
        }
    }

    private fun loadPreferencesIntoUi() {
        restoringUi = true

        val prefs = preferences ?: return
        val bassDb = quantizeBassGainToggle(prefs.getFloat(AudioEffectsService.KEY_BASS_DB, 0f))
        sliderBassGain?.value = bassDb
        tvBassGainValue?.text = formatDb(bassDb)

        val bassFrequencyHz = normalizeBassFrequencySlider(
            prefs.getFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, BASS_DEFAULT_FREQUENCY_HZ)
        )
        sliderBassFrequency?.value = bassFrequencyHz
        tvBassFrequencyValue?.text = formatHz(bassFrequencyHz)

        val bassType = normalizeBassType(prefs.getInt(AudioEffectsService.KEY_BASS_TYPE, BASS_TYPE_DEFAULT))
        tvSelectedBassType?.text = bassTypeLabelFromValue(bassType)

        val reverbLevel = normalizeReverbLevel(prefs.getInt(AudioEffectsService.KEY_REVERB_LEVEL, REVERB_LEVEL_DEFAULT))
        tvSelectedReverb?.text = reverbLabelFromLevel(reverbLevel)

        val bandValues = FloatArray(EQ_BAND_COUNT)
        for (i in bandValues.indices) {
            bandValues[i] = prefs.getFloat(AudioEffectsService.bandDbKey(i), 0f)
        }
        eqCurveView?.setBandValues(bandValues)

        val selectedPreset = prefs.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT)
        tvSelectedPreset?.text = presetLabelFromId(selectedPreset)

        restoringUi = false
    }

    private fun setupListeners() {
        sliderBassGain?.addOnChangeListener { _, value, _ ->
            tvBassGainValue?.text = formatDb(value)
            if (restoringUi) return@addOnChangeListener
            preferences?.edit()?.putFloat(AudioEffectsService.KEY_BASS_DB, value)?.apply()
            markPresetAsCustom()
            applyIfEnabled()
        }

        sliderBassFrequency?.addOnChangeListener { _, value, _ ->
            tvBassFrequencyValue?.text = formatHz(value)
            if (restoringUi) return@addOnChangeListener
            preferences?.edit()?.putFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, value)?.apply()
            markPresetAsCustom()
            applyIfEnabled()
        }

        cardBassTypeSelector?.setOnClickListener { showBassTypePopup() }
        cardReverbSelector?.setOnClickListener { showReverbPopup() }

        eqCurveView?.apply {
            setEditingEnabled(false)
            setHandlesVisible(false)
            setGridVisible(true)
            setAxisDbLabelsEnabled(true)
            setAreaFillEnabled(true)
            setModalBandGuidesEnabled(false)
        }

        cardGraphicEq?.setOnClickListener { showGraphicEqEditorDialog() }
        cardPresetSelector?.setOnClickListener { showPresetPopup() }

        switchEqEnabled?.setOnCheckedChangeListener { _, isChecked ->
            val prefs = preferences ?: return@setOnCheckedChangeListener
            prefs.edit().putBoolean(AudioEffectsService.KEY_ENABLED, isChecked).apply()
            AudioDeviceProfileStore.persistActiveValuesToCurrentProfile(prefs)
            if (isChecked) {
                startOrUpdateService()
            } else {
                stopEqService()
            }
            refreshEqModuleState()
        }
    }

    private fun applyIfEnabled() {
        if (!isEqEnabled()) {
            refreshEqModuleState()
            return
        }
        preferences?.let { AudioDeviceProfileStore.persistActiveValuesToCurrentProfile(it) }
        startOrUpdateService()
    }

    private fun ensureEqServiceActive() {
        if (isEqEnabled()) {
            startOrUpdateService()
        }
    }

    private fun isEqEnabled(): Boolean {
        return preferences?.getBoolean(AudioEffectsService.KEY_ENABLED, false) ?: false
    }
    private fun refreshEqModuleState() {
        val eqEnabled = isEqEnabled()

        if (switchEqEnabled?.isChecked != eqEnabled) {
            restoringUi = true
            switchEqEnabled?.isChecked = eqEnabled
            restoringUi = false
        }

        cardPresetSelector?.apply {
            isEnabled = eqEnabled
            isClickable = eqEnabled
            isFocusable = eqEnabled
            alpha = if (eqEnabled) 1f else 0.45f
        }

        cardBassTypeSelector?.apply {
            isEnabled = eqEnabled
            isClickable = eqEnabled
            isFocusable = eqEnabled
            alpha = if (eqEnabled) 1f else 0.45f
        }

        cardReverbSelector?.apply {
            isEnabled = eqEnabled
            isClickable = eqEnabled
            isFocusable = eqEnabled
            alpha = if (eqEnabled) 1f else 0.45f
        }

        cardGraphicEq?.apply {
            isEnabled = eqEnabled
            isClickable = eqEnabled
            isFocusable = eqEnabled
            alpha = if (eqEnabled) 1f else 0.45f
        }

        cardBassTuner?.apply {
            isEnabled = eqEnabled
            isClickable = eqEnabled
            isFocusable = eqEnabled
            alpha = if (eqEnabled) 1f else 0.45f
        }

        eqCurveView?.apply {
            isEnabled = eqEnabled
            setEditingEnabled(false)
            setHandlesVisible(false)
            alpha = if (eqEnabled) 1f else 0.45f
        }

        sliderBassGain?.isEnabled = eqEnabled
        sliderBassFrequency?.isEnabled = eqEnabled
        view?.let { applyAmoledStyles(it) }
    }

    private fun registerOutputDeviceCallback() {
        if (audioManager == null) {
            refreshOutputDeviceAndProfile(false, false)
            return
        }
        try {
            audioManager?.registerAudioDeviceCallback(outputDeviceCallback, mainHandler)
        } catch (ignored: Throwable) {
        }
        refreshOutputDeviceAndProfile(true, false)
    }

    private fun unregisterOutputDeviceCallback() {
        try {
            audioManager?.unregisterAudioDeviceCallback(outputDeviceCallback)
        } catch (ignored: Throwable) {
        }
    }

    private fun refreshOutputDeviceAndProfile(applyOnProfileSwitch: Boolean, forceRefresh: Boolean = false) {
        if (tvOutputRoute == null || ivOutputIcon == null) return

        val now = System.currentTimeMillis()
        if (!forceRefresh && (now - lastOutputRefreshAtMs) < OUTPUT_REFRESH_MIN_INTERVAL_MS) return
        lastOutputRefreshAtMs = now

        var selected: AudioDeviceInfo? = null
        if (audioManager == null) {
            setOutputUi("Altavoces", R.drawable.ic_output_speaker, "Salida por altavoz")
        } else {
            val outputDevices = audioManager!!.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            selected = selectPreferredOutput(outputDevices)

            if (selected != null) {
                val selectedType = selected.type
                if (isBluetoothType(selectedType) || isWiredType(selectedType)) {
                    forceSpeakerOutputSelection = false
                    forceSpeakerFallbackUntilMs = 0L
                }
            }

            if (selected == null) {
                setOutputUi("Altavoces", R.drawable.ic_output_speaker, "Salida por altavoz")
            } else {
                val type = selected.type
                when {
                    isBluetoothType(type) -> setOutputUi(deviceName(selected, "Bluetooth"), R.drawable.ic_output_bluetooth, "Salida por Bluetooth")
                    isWiredType(type) -> setOutputUi("Auriculares", R.drawable.ic_output_wired, "Salida por auriculares")
                    type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> setOutputUi("Auriculares", R.drawable.ic_output_earpiece, "Salida por auriculares")
                    else -> setOutputUi("Altavoces", R.drawable.ic_output_speaker, "Salida por altavoz")
                }
            }
        }

        val prefs = preferences ?: return

        val restoredFromProfile = AudioDeviceProfileStore.restoreActiveValuesForOutput(prefs, selected)
        val profileSwitched = AudioDeviceProfileStore.syncActiveProfileForOutput(prefs, selected)
        val currentProfile = AudioDeviceProfileStore.getActiveProfileId(prefs, selected)
        val previousBoundProfile = boundProfileId
        val shouldReloadUi = restoredFromProfile || profileSwitched || !hasLoadedUi || !TextUtils.equals(previousBoundProfile, currentProfile)
        
        boundProfileId = currentProfile
        if (shouldReloadUi) {
            ensureBassDefaults()
            ensurePresetStateConsistency()
            loadPreferencesIntoUi()
            hasLoadedUi = true
        }
        
        refreshEqModuleState()
        if ((profileSwitched || restoredFromProfile) && applyOnProfileSwitch) {
            applyIfEnabled()
        }
    }

    private fun setOutputUi(route: String, iconRes: Int, contentDescription: String) {
        tvOutputRoute?.text = route
        ivOutputIcon?.setImageResource(iconRes)
        ivOutputIcon?.contentDescription = contentDescription
    }

    private fun showPresetPopup() {
        if (!isEqEnabled()) return
        val context = requireContext()
        val selectedPresetId = preferences?.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT) ?: PRESET_DEFAULT
        val userPresets = loadUserPresetEntries()
        val popupWidth = dp(188)

        val popupRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.surface_low))
                cornerRadius = dp(14).toFloat()
            }
        }

        val popupWindow = PopupWindow(popupRoot, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
        }
        registerActivePopupWindow(popupWindow)

        for (preset in PRESET_CONFIGS) {
            val isSelected = preset.id == selectedPresetId
            val row = createPresetRow(preset, isSelected, popupWindow)
            popupRoot.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        if (userPresets.isNotEmpty()) {
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                }
                setBackgroundColor(withAlpha(ContextCompat.getColor(context, R.color.text_secondary), 0.22f))
            }
            popupRoot.addView(divider)

            for (entry in userPresets) {
                val isSelected = entry.id == selectedPresetId
                val row = createUserPresetRow(entry, isSelected, popupWindow)
                popupRoot.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }

        val xOffset = dp(10)
        cardPresetSelector?.let { popupWindow.showAsDropDown(it, xOffset, dp(8)) }
    }

    private fun showBassTypePopup() {
        if (cardBassTypeSelector == null || !isEqEnabled()) return
        val context = requireContext()
        val selectedBassType = normalizeBassType(preferences?.getInt(AudioEffectsService.KEY_BASS_TYPE, BASS_TYPE_DEFAULT) ?: BASS_TYPE_DEFAULT)
        val popupWidth = dp(228)

        val popupRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.surface_low))
                cornerRadius = dp(14).toFloat()
            }
        }

        val popupWindow = PopupWindow(popupRoot, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
        }
        registerActivePopupWindow(popupWindow)

        for (option in BASS_TYPE_OPTIONS) {
            val isSelected = option.value == selectedBassType
            val row = createBassTypeRow(option, isSelected, popupWindow)
            popupRoot.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val xOffset = dp(10)
        cardBassTypeSelector?.let { popupWindow.showAsDropDown(it, xOffset, dp(8)) }
    }

    private fun showReverbPopup() {
        if (cardReverbSelector == null || !isEqEnabled()) return
        val context = requireContext()
        val selectedReverbLevel = normalizeReverbLevel(preferences?.getInt(AudioEffectsService.KEY_REVERB_LEVEL, REVERB_LEVEL_DEFAULT) ?: REVERB_LEVEL_DEFAULT)
        val popupWidth = dp(188)

        val popupRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.surface_low))
                cornerRadius = dp(14).toFloat()
            }
        }

        val popupWindow = PopupWindow(popupRoot, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
        }
        registerActivePopupWindow(popupWindow)

        for (option in REVERB_OPTIONS) {
            val isSelected = option.value == selectedReverbLevel
            val row = createReverbRow(option, isSelected, popupWindow)
            popupRoot.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val xOffset = dp(10)
        cardReverbSelector?.let { popupWindow.showAsDropDown(it, xOffset, dp(8)) }
    }

    private fun createPresetRow(preset: PresetConfig, isSelected: Boolean, popupWindow: PopupWindow): TextView {
        val context = requireContext()
        val row = TextView(context).apply {
            text = preset.label
            maxLines = 1
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextColor(ContextCompat.getColor(context, if (isSelected) R.color.stitch_blue_light else R.color.text_secondary))
        }

        val rowContent = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (isSelected) withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.22f) else Color.TRANSPARENT)
        }
        val rippleColor = withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.32f)
        row.background = RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null)

        row.setOnClickListener {
            applyPreset(preset)
            popupWindow.dismiss()
        }
        return row
    }

    private fun createUserPresetRow(preset: UserPresetEntry, isSelected: Boolean, popupWindow: PopupWindow): TextView {
        val context = requireContext()
        val row = TextView(context).apply {
            text = preset.name
            maxLines = 1
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextColor(ContextCompat.getColor(context, if (isSelected) R.color.stitch_blue_light else R.color.text_secondary))
        }

        val rowContent = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (isSelected) withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.22f) else Color.TRANSPARENT)
        }
        val rippleColor = withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.32f)
        row.background = RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null)

        row.setOnClickListener {
            applyUserPreset(preset)
            popupWindow.dismiss()
        }
        row.setOnLongClickListener {
            showDeleteUserPresetDialog(preset, popupWindow)
            true
        }
        return row
    }
    private fun createBassTypeRow(option: BassTypeOption, isSelected: Boolean, popupWindow: PopupWindow): TextView {
        val context = requireContext()
        val row = TextView(context).apply {
            text = option.label
            maxLines = 1
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextColor(ContextCompat.getColor(context, if (isSelected) R.color.stitch_blue_light else R.color.text_secondary))
        }

        val rowContent = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (isSelected) withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.22f) else Color.TRANSPARENT)
        }
        val rippleColor = withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.32f)
        row.background = RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null)

        row.setOnClickListener {
            preferences?.edit()?.putInt(AudioEffectsService.KEY_BASS_TYPE, option.value)?.apply()
            tvSelectedBassType?.text = option.label
            markPresetAsCustom()
            applyIfEnabled()
            popupWindow.dismiss()
        }
        return row
    }

    private fun createReverbRow(option: ReverbOption, isSelected: Boolean, popupWindow: PopupWindow): TextView {
        val context = requireContext()
        val row = TextView(context).apply {
            text = option.label
            maxLines = 1
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextColor(ContextCompat.getColor(context, if (isSelected) R.color.stitch_blue_light else R.color.text_secondary))
        }

        val rowContent = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (isSelected) withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.22f) else Color.TRANSPARENT)
        }
        val rippleColor = withAlpha(ContextCompat.getColor(context, R.color.stitch_blue_light), 0.32f)
        row.background = RippleDrawable(ColorStateList.valueOf(rippleColor), rowContent, null)

        row.setOnClickListener {
            preferences?.edit()?.putInt(AudioEffectsService.KEY_REVERB_LEVEL, option.value)?.apply()
            tvSelectedReverb?.text = option.label
            applyIfEnabled()
            popupWindow.dismiss()
        }
        return row
    }

    private fun applyPreset(preset: PresetConfig) {
        restoringUi = true
        eqCurveView?.setBandValues(preset.bands)
        eqCurveView?.setHandlesVisible(false)
        sliderBassGain?.value = quantizeBassGainToggle(preset.bassDb)
        tvBassGainValue?.text = formatDb(sliderBassGain?.value ?: 0f)
        val normalizedBassFrequencyHz = normalizeBassFrequencySlider(preset.bassFrequencyHz)
        sliderBassFrequency?.value = normalizedBassFrequencyHz
        tvBassFrequencyValue?.text = formatHz(normalizedBassFrequencyHz)
        val normalizedBassType = normalizeBassType(preset.bassType)
        tvSelectedBassType?.text = bassTypeLabelFromValue(normalizedBassType)
        tvSelectedPreset?.text = preset.label
        restoringUi = false

        savePresetValues(preset)
        applyIfEnabled()
    }

    private fun applyUserPreset(preset: UserPresetEntry) {
        restoringUi = true
        eqCurveView?.apply {
            setBandValues(preset.bands)
            setHandlesVisible(false)
        }
        tvSelectedPreset?.text = preset.name
        restoringUi = false

        preferences?.edit()?.apply {
            for (i in preset.bands.indices) {
                putFloat(AudioEffectsService.bandDbKey(i), preset.bands[i])
            }
            putString(KEY_SELECTED_PRESET, preset.id)
            apply()
        }
        applyIfEnabled()
    }

    private fun showDeleteUserPresetDialog(preset: UserPresetEntry, popupWindow: PopupWindow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar preset")
            .setMessage("Deseas eliminar el preset \"${preset.name}\"?")
            .setNegativeButton("No", null)
            .setPositiveButton("Eliminar") { _, _ ->
                val selectedPresetId = preferences?.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT)

                preferences?.edit()?.apply {
                    remove(KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX + preset.profileId)
                    for (i in 0 until EQ_BAND_COUNT) {
                        remove(userPresetBandKey(preset.profileId, i))
                    }
                    if (TextUtils.equals(selectedPresetId, preset.id)) {
                        putString(KEY_SELECTED_PRESET, PRESET_CUSTOM)
                    }
                    apply()
                }

                if (TextUtils.equals(selectedPresetId, preset.id)) {
                    tvSelectedPreset?.text = presetLabelFromId(PRESET_CUSTOM)
                }
                popupWindow.dismiss()
            }
            .show()
    }

    private fun loadUserPresetEntries(): List<UserPresetEntry> {
        val allValues = preferences?.all ?: return emptyList()
        val result = mutableListOf<UserPresetEntry>()

        for ((key, valueObj) in allValues) {
            if (!key.startsWith(KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX)) continue
            if (valueObj !is String) continue

            val profileId = key.substring(KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX.length)
            if (TextUtils.isEmpty(profileId)) continue

            val name = valueObj.trim()
            if (TextUtils.isEmpty(name)) continue

            val bands = readUserPresetBands(profileId)
            result.add(UserPresetEntry(profileId, userPresetIdForProfile(profileId), name, bands))
        }

        if (result.isEmpty()) return emptyList()
        result.sortBy { it.name.lowercase(Locale.US) }
        return result
    }

    private fun findUserPresetById(presetId: String?): UserPresetEntry? {
        val profileId = profileIdFromUserPresetId(presetId) ?: return null
        val name = preferences?.getString(KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX + profileId, "")?.trim()
        if (name.isNullOrEmpty()) return null

        val bands = readUserPresetBands(profileId)
        return UserPresetEntry(profileId, userPresetIdForProfile(profileId), name, bands)
    }

    private fun readUserPresetBands(profileId: String): FloatArray {
        val bands = FloatArray(EQ_BAND_COUNT)
        var hasStoredUserBands = false

        for (i in bands.indices) {
            val bandKey = userPresetBandKey(profileId, i)
            if (preferences?.contains(bandKey) == true) {
                bands[i] = readNumericPrefAsFloat(bandKey, 0f)
                hasStoredUserBands = true
            }
        }

        if (hasStoredUserBands) return bands

        var hasProfileBands = false
        for (i in bands.indices) {
            val fallbackProfileBandKey = KEY_DEVICE_PROFILE_PREFIX + profileId + "_" + AudioEffectsService.bandDbKey(i)
            if (preferences?.contains(fallbackProfileBandKey) == true) {
                bands[i] = readNumericPrefAsFloat(fallbackProfileBandKey, 0f)
                hasProfileBands = true
            }
        }

        if (!hasProfileBands && TextUtils.equals(profileId, activeProfileIdForCustomPreset())) {
            for (i in bands.indices) {
                bands[i] = preferences?.getFloat(AudioEffectsService.bandDbKey(i), 0f) ?: 0f
            }
        }

        saveUserPresetBandsForProfile(profileId, bands)
        return bands
    }

    private fun readNumericPrefAsFloat(key: String, defaultValue: Float): Float {
        try {
            return preferences?.getFloat(key, defaultValue) ?: defaultValue
        } catch (ignored: ClassCastException) {
        }
        val all = preferences?.all ?: return defaultValue
        val raw = all[key]
        if (raw is Number) return raw.toFloat()
        if (raw is String) {
            try {
                return raw.trim().toFloat()
            } catch (ignored: Exception) {
            }
        }
        return defaultValue
    }

    private fun saveUserPresetBandsForProfile(profileId: String, bands: FloatArray) {
        preferences?.edit()?.apply {
            for (i in 0 until EQ_BAND_COUNT) {
                val value = if (i < bands.size) bands[i] else 0f
                putFloat(userPresetBandKey(profileId, i), value)
            }
            apply()
        }
    }

    private fun userPresetBandKey(profileId: String, bandIndex: Int): String {
        return "${KEY_USER_PRESET_BAND_PREFIX}${profileId}_${bandIndex}"
    }

    private fun userPresetIdForProfile(profileId: String): String {
        return "$PRESET_USER_PREFIX$profileId"
    }

    private fun isUserPresetId(presetId: String?): Boolean {
        return presetId?.startsWith(PRESET_USER_PREFIX) == true
    }

    private fun profileIdFromUserPresetId(presetId: String?): String? {
        if (!isUserPresetId(presetId)) return null
        val profileId = presetId!!.substring(PRESET_USER_PREFIX.length)
        return if (TextUtils.isEmpty(profileId)) null else profileId
    }

    private fun showGraphicEqEditorDialog() {
        if (!isAdded || preferences == null || !isEqEnabled()) return

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_eq_editor, null, false)
        val modalEqView = dialogView.findViewById<EqCurveEditorView>(R.id.eqCurveEditorModal)
        val gainTextsRow = dialogView.findViewById<LinearLayout>(R.id.gain_texts)
        val frequencyTextsRow = dialogView.findViewById<LinearLayout>(R.id.frequency_texts)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnEqEditorClose)
        val btnAccept = dialogView.findViewById<MaterialButton>(R.id.btnEqEditorAccept)

        val originalBands = readCurrentBandsSnapshot()
        val originalPresetId = preferences?.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT)
        val draftBands = originalBands.clone()
        val gainValueViews = populateGraphicEqDialogRows(gainTextsRow, frequencyTextsRow, draftBands)
        val accepted = booleanArrayOf(false)

        modalEqView?.apply {
            setBandValues(draftBands)
            setEditingEnabled(true)
            setHandlesVisible(true)
            setGridVisible(false)
            setAxisDbLabelsEnabled(false)
            setAreaFillEnabled(false)
            setModalBandGuidesEnabled(true)
            setOnBandChangeListener(object : EqCurveEditorView.OnBandChangeListener {
                override fun onBandChanged(bandIndex: Int, value: Float) {
                    if (bandIndex in draftBands.indices) {
                        draftBands[bandIndex] = value
                        refreshGraphicEqDialogGainLabels(gainValueViews, draftBands)
                        applyDraftBandsPreview(draftBands)
                    }
                }
            })
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.setOnDismissListener {
            if (!accepted[0]) {
                restoreGraphicEqSnapshot(originalBands, originalPresetId)
            }
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        btnAccept?.setOnClickListener {
            if (needsCustomPresetNameForCurrentProfile()) {
                promptForCustomPresetName { name ->
                    persistCustomPresetNameForCurrentProfile(name)
                    commitAcceptedGraphicEqChanges(draftBands)
                    accepted[0] = true
                    dialog.dismiss()
                }
                return@setOnClickListener
            }
            commitAcceptedGraphicEqChanges(draftBands)
            accepted[0] = true
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            decorView.setPadding(0, 0, 0, 0)
            setDimAmount(0.88f)
        }
    }

    private fun populateGraphicEqDialogRows(gainTextsRow: LinearLayout?, frequencyTextsRow: LinearLayout?, bandValues: FloatArray): Array<TextView?> {
        val gainValueViews = arrayOfNulls<TextView>(EQ_BAND_COUNT)

        gainTextsRow?.apply {
            removeAllViews()
            for (i in 0 until EQ_BAND_COUNT) {
                val gainView = createGraphicEqDialogLabel(true)
                gainView.text = formatGraphicEqDialogGain(if (i < bandValues.size) bandValues[i] else 0f)
                addView(gainView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                gainValueViews[i] = gainView
            }
        }

        frequencyTextsRow?.apply {
            removeAllViews()
            for (i in 0 until EQ_BAND_COUNT) {
                val frequencyView = createGraphicEqDialogLabel(false)
                val frequencyHz = if (i < GRAPHIC_EQ_DIALOG_FREQUENCIES_HZ.size) GRAPHIC_EQ_DIALOG_FREQUENCIES_HZ[i].toFloat() else AudioEffectsService.WAVELET_9_BAND_FREQUENCIES_HZ[i].toFloat()
                frequencyView.text = formatGraphicEqDialogFrequency(frequencyHz)
                addView(frequencyView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        return gainValueViews
    }

    private fun refreshGraphicEqDialogGainLabels(gainValueViews: Array<TextView?>, bandValues: FloatArray) {
        for (i in gainValueViews.indices) {
            val gainView = gainValueViews[i] ?: continue
            val value = if (i < bandValues.size) bandValues[i] else 0f
            gainView.text = formatGraphicEqDialogGain(value)
        }
    }

    private fun createGraphicEqDialogLabel(gainRow: Boolean): TextView {
        return TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            setSingleLine(true)
            maxLines = 1
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, if (gainRow) dp(2) else dp(6), 0, if (gainRow) dp(2) else dp(4))
            textSize = if (gainRow) 11f else 12f
            setTextColor(ContextCompat.getColor(requireContext(), if (gainRow) R.color.text_secondary else R.color.text_primary))
            typeface = if (gainRow) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        }
    }

    private fun formatGraphicEqDialogGain(value: Float): String {
        return String.format(Locale.US, "%.1f", value)
    }

    private fun formatGraphicEqDialogFrequency(frequencyHz: Float): String {
        if (frequencyHz >= 1000f) {
            val khz = frequencyHz / 1000f
            if (abs(khz - Math.round(khz)) > 0.01f) {
                return String.format(Locale.US, "%.1fk", khz)
            }
            return String.format(Locale.US, "%dk", Math.round(khz).toInt())
        }
        if (abs(frequencyHz - Math.round(frequencyHz)) > 0.01f) {
            return String.format(Locale.US, "%.1f", frequencyHz)
        }
        return String.format(Locale.US, "%d", Math.round(frequencyHz).toInt())
    }

    private fun applyDraftBandsPreview(draftBands: FloatArray) {
        preferences?.edit()?.apply {
            for (i in draftBands.indices) {
                putFloat(AudioEffectsService.bandDbKey(i), draftBands[i])
            }
            apply()
        }

        eqCurveView?.apply {
            setBandValues(draftBands)
            setHandlesVisible(false)
        }
        applyIfEnabled()
    }

    private fun commitAcceptedGraphicEqChanges(draftBands: FloatArray) {
        val activeProfileId = activeProfileIdForCustomPreset()
        val customPresetName = customPresetNameForCurrentProfile()
        val selectedPresetId = if (TextUtils.isEmpty(customPresetName)) PRESET_CUSTOM else userPresetIdForProfile(activeProfileId)

        preferences?.edit()?.apply {
            for (i in draftBands.indices) {
                putFloat(AudioEffectsService.bandDbKey(i), draftBands[i])
            }
            putString(KEY_SELECTED_PRESET, selectedPresetId)
            apply()
        }

        if (!TextUtils.isEmpty(customPresetName)) {
            saveUserPresetBandsForProfile(activeProfileId, draftBands)
        }

        eqCurveView?.apply {
            setBandValues(draftBands)
            setHandlesVisible(false)
        }
        tvSelectedPreset?.text = presetLabelFromId(selectedPresetId)
        applyIfEnabled()
    }

    private fun restoreGraphicEqSnapshot(originalBands: FloatArray, originalPresetId: String?) {
        val safePresetId = if (TextUtils.isEmpty(originalPresetId)) PRESET_DEFAULT else originalPresetId!!

        preferences?.edit()?.apply {
            for (i in originalBands.indices) {
                putFloat(AudioEffectsService.bandDbKey(i), originalBands[i])
            }
            putString(KEY_SELECTED_PRESET, safePresetId)
            apply()
        }

        eqCurveView?.apply {
            setBandValues(originalBands)
            setHandlesVisible(false)
        }
        tvSelectedPreset?.text = presetLabelFromId(safePresetId)
        applyIfEnabled()
    }

    private fun needsCustomPresetNameForCurrentProfile(): Boolean {
        return TextUtils.isEmpty(customPresetNameForCurrentProfile())
    }

    private fun readCurrentBandsSnapshot(): FloatArray {
        if (eqCurveView != null) {
            return eqCurveView!!.getBandValuesSnapshot()
        }
        val snapshot = FloatArray(EQ_BAND_COUNT)
        for (i in snapshot.indices) {
            snapshot[i] = preferences?.getFloat(AudioEffectsService.bandDbKey(i), 0f) ?: 0f
        }
        return snapshot
    }

    private fun resolveMutableCustomPresetId(): String {
        val customName = customPresetNameForCurrentProfile()
        if (!TextUtils.isEmpty(customName)) {
            return userPresetIdForProfile(activeProfileIdForCustomPreset())
        }
        return PRESET_CUSTOM
    }

    private fun activeProfileIdForCustomPreset(): String {
        if (!TextUtils.isEmpty(boundProfileId)) return boundProfileId!!
        val prefs = preferences ?: return "default"
        return AudioDeviceProfileStore.getActiveProfileId(prefs, null)
    }

    private fun profileCustomPresetNameKey(profileId: String): String {
        return "$KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX$profileId"
    }

    private fun customPresetNameForCurrentProfile(): String? {
        val prefs = preferences ?: return null
        val profileId = activeProfileIdForCustomPreset()
        val raw = prefs.getString(profileCustomPresetNameKey(profileId), null) ?: return null
        val value = raw.trim()
        return if (TextUtils.isEmpty(value)) null else value
    }

    private fun persistCustomPresetNameForCurrentProfile(presetName: String) {
        val prefs = preferences ?: return
        var cleanName = presetName.trim()
        if (TextUtils.isEmpty(cleanName)) return
        if (cleanName.length > 32) cleanName = cleanName.substring(0, 32)
        val profileId = activeProfileIdForCustomPreset()
        prefs.edit().putString(profileCustomPresetNameKey(profileId), cleanName).apply()
    }

    private fun promptForCustomPresetName(callback: (String) -> Unit) {
        if (!isAdded) return

        val input = EditText(requireContext()).apply {
            setSingleLine(true)
            hint = "Ejemplo: Mis audifonos"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            val pad = dp(14)
            setPadding(pad, pad, pad, pad)
        }

        val nameDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nombre del preset")
            .setMessage("Ponle un nombre a tu preset personalizado para este dispositivo. Solo se pedira una vez.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()

        nameDialog.setOnShowListener {
            val positiveButton = nameDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val value = input.text?.toString()?.trim() ?: ""
                if (TextUtils.isEmpty(value)) {
                    input.error = "Escribe un nombre para el preset"
                    return@setOnClickListener
                }
                callback(value)
                nameDialog.dismiss()
            }
        }
        nameDialog.show()
    }

    private fun ensureBassDefaults() {
        val prefs = preferences ?: return
        val hasBassDb = prefs.contains(AudioEffectsService.KEY_BASS_DB)
        val hasBassType = prefs.contains(AudioEffectsService.KEY_BASS_TYPE)
        val hasBassFrequency = prefs.contains(AudioEffectsService.KEY_BASS_FREQUENCY_HZ)
        val hasReverbLevel = prefs.contains(AudioEffectsService.KEY_REVERB_LEVEL)

        if (hasBassDb && hasBassType && hasBassFrequency && hasReverbLevel) return

        prefs.edit().apply {
            if (!hasBassDb) putFloat(AudioEffectsService.KEY_BASS_DB, 0f)
            if (!hasBassType) putInt(AudioEffectsService.KEY_BASS_TYPE, BASS_TYPE_DEFAULT)
            if (!hasBassFrequency) putFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, BASS_DEFAULT_FREQUENCY_HZ)
            if (!hasReverbLevel) putInt(AudioEffectsService.KEY_REVERB_LEVEL, REVERB_LEVEL_DEFAULT)
            apply()
        }
    }

    private fun ensurePresetStateConsistency() {
        val prefs = preferences ?: return
        if (!prefs.contains(KEY_SELECTED_PRESET)) {
            val activeUserPresetId = resolveActiveUserPresetIdIfSynced()
            prefs.edit().putString(KEY_SELECTED_PRESET, if (TextUtils.isEmpty(activeUserPresetId)) PRESET_CUSTOM else activeUserPresetId).apply()
            return
        }

        val presetId = prefs.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT)
        if (PRESET_CUSTOM == presetId) {
            val activeUserPresetId = resolveActiveUserPresetIdIfSynced()
            if (!TextUtils.isEmpty(activeUserPresetId)) {
                prefs.edit().putString(KEY_SELECTED_PRESET, activeUserPresetId).apply()
            }
            return
        }

        if (isUserPresetId(presetId)) {
            val userPreset = findUserPresetById(presetId)
            if (userPreset == null || !isUserPresetSynced(userPreset)) {
                val activeUserPresetId = resolveActiveUserPresetIdIfSynced()
                prefs.edit().putString(KEY_SELECTED_PRESET, if (TextUtils.isEmpty(activeUserPresetId)) PRESET_CUSTOM else activeUserPresetId).apply()
            }
            return
        }

        val selectedPreset = findPresetById(presetId)
        if (selectedPreset == null) {
            val activeUserPresetId = resolveActiveUserPresetIdIfSynced()
            prefs.edit().putString(KEY_SELECTED_PRESET, if (TextUtils.isEmpty(activeUserPresetId)) PRESET_CUSTOM else activeUserPresetId).apply()
            return
        }

        if (!isPresetSynced(selectedPreset)) {
            val activeUserPresetId = resolveActiveUserPresetIdIfSynced()
            prefs.edit().putString(KEY_SELECTED_PRESET, if (TextUtils.isEmpty(activeUserPresetId)) PRESET_CUSTOM else activeUserPresetId).apply()
        }
    }

    private fun resolveActiveUserPresetIdIfSynced(): String? {
        val activeProfileId = activeProfileIdForCustomPreset()
        if (TextUtils.isEmpty(activeProfileId)) return null

        val activeUserPresetId = userPresetIdForProfile(activeProfileId)
        val activeUserPreset = findUserPresetById(activeUserPresetId)
        if (activeUserPreset == null || !isUserPresetSynced(activeUserPreset)) return null

        return activeUserPresetId
    }

    private fun isUserPresetSynced(preset: UserPresetEntry): Boolean {
        for (i in preset.bands.indices) {
            val stored = preferences?.getFloat(AudioEffectsService.bandDbKey(i), 0f) ?: 0f
            if (abs(stored - preset.bands[i]) > 0.01f) return false
        }
        return true
    }

    private fun isPresetSynced(preset: PresetConfig): Boolean {
        for (i in preset.bands.indices) {
            val stored = preferences?.getFloat(AudioEffectsService.bandDbKey(i), 0f) ?: 0f
            if (abs(stored - preset.bands[i]) > 0.01f) return false
        }
        return true
    }

    private fun savePresetValues(preset: PresetConfig) {
        preferences?.edit()?.apply {
            putString(KEY_SELECTED_PRESET, preset.id)
            for (i in preset.bands.indices) {
                putFloat(AudioEffectsService.bandDbKey(i), preset.bands[i])
            }
            putFloat(AudioEffectsService.KEY_BASS_DB, quantizeBassGainToggle(preset.bassDb))
            putFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, normalizeBassFrequencySlider(preset.bassFrequencyHz))
            putInt(AudioEffectsService.KEY_BASS_TYPE, normalizeBassType(preset.bassType))
            apply()
        }
    }

    private fun quantizeBassGainToggle(rawValue: Float): Float {
        return max(BASS_GAIN_MIN_DB, min(rawValue, BASS_GAIN_MAX_DB))
    }

    private fun markPresetAsCustom() {
        val targetPresetId = resolveMutableCustomPresetId()
        val currentPreset = preferences?.getString(KEY_SELECTED_PRESET, PRESET_DEFAULT)
        if (TextUtils.equals(currentPreset, targetPresetId)) return
        preferences?.edit()?.putString(KEY_SELECTED_PRESET, targetPresetId)?.apply()
        tvSelectedPreset?.text = presetLabelFromId(targetPresetId)
    }

    private fun findPresetById(presetId: String?): PresetConfig? {
        return PRESET_CONFIGS.find { it.id == presetId }
    }
    private fun presetLabelFromId(presetId: String?): String {
        if (PRESET_CUSTOM == presetId) {
            val customName = customPresetNameForCurrentProfile()
            return if (TextUtils.isEmpty(customName)) "Personalizado" else customName!!
        }

        if (isUserPresetId(presetId)) {
            val userPreset = findUserPresetById(presetId)
            if (userPreset != null && !TextUtils.isEmpty(userPreset.name)) {
                return userPreset.name
            }
            return "Preset usuario"
        }

        val config = findPresetById(presetId)
        return config?.label ?: "Personalizado"
    }

    private fun normalizeBassType(value: Int): Int {
        return BASS_TYPE_OPTIONS.find { it.value == value }?.value ?: AudioEffectsService.BASS_TYPE_NATURAL
    }

    private fun bassTypeLabelFromValue(value: Int): String {
        return BASS_TYPE_OPTIONS.find { it.value == value }?.label ?: "Natural"
    }

    private fun normalizeReverbLevel(level: Int): Int {
        return REVERB_OPTIONS.find { it.value == level }?.value ?: AudioEffectsService.REVERB_LEVEL_OFF
    }

    private fun reverbLabelFromLevel(level: Int): String {
        return REVERB_OPTIONS.find { it.value == level }?.label ?: "Desactivada"
    }

    private fun normalizeBassFrequencySlider(frequencyHz: Float): Float {
        return max(AudioEffectsService.BASS_FREQUENCY_MIN_HZ, min(frequencyHz, AudioEffectsService.BASS_FREQUENCY_MAX_HZ))
    }

    private fun formatDb(value: Float): String {
        return if (value > 0) "+${String.format(Locale.US, "%.1f", value)}dB" else "${String.format(Locale.US, "%.1f", value)}dB"
    }

    private fun formatHz(valueHz: Float): String {
        return "${valueHz.roundToInt()}Hz"
    }

    private fun startOrUpdateService() {
        val context = context ?: return
        AudioEffectsService.sendApply(context)
    }

    private fun stopEqService() {
        val context = context ?: return
        AudioEffectsService.sendStop(context)
    }

    private fun selectPreferredOutput(devices: Array<AudioDeviceInfo>): AudioDeviceInfo? {
        if (devices.isEmpty()) return null

        var bluetooth: AudioDeviceInfo? = null
        var wired: AudioDeviceInfo? = null
        var earpiece: AudioDeviceInfo? = null
        var speaker: AudioDeviceInfo? = null

        for (device in devices) {
            when {
                isBluetoothType(device.type) -> bluetooth = device
                isWiredType(device.type) -> wired = device
                device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> earpiece = device
                device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> speaker = device
            }
        }

        if (forceSpeakerOutputSelection && System.currentTimeMillis() < forceSpeakerFallbackUntilMs) {
            return speaker
        }

        return bluetooth ?: wired ?: earpiece ?: speaker
    }

    private fun containsHeadphoneLikeOutput(devices: Array<AudioDeviceInfo>): Boolean {
        return devices.any { isBluetoothType(it.type) || isWiredType(it.type) }
    }

    private fun isBluetoothType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
               type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
               type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
               type == AudioDeviceInfo.TYPE_HEARING_AID
    }

    private fun isWiredType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
               type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
               type == AudioDeviceInfo.TYPE_USB_HEADSET ||
               type == AudioDeviceInfo.TYPE_USB_DEVICE
    }

    private fun deviceName(device: AudioDeviceInfo, defaultName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val name = device.productName?.toString()
            if (!TextUtils.isEmpty(name)) name!! else defaultName
        } else {
            defaultName
        }
    }

    private fun dp(dps: Int): Int {
        if (!isAdded || context == null) return dps * 3
        return (dps * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun withAlpha(color: Int, alphaMultiplier: Float): Int {
        val a = (Color.alpha(color) * alphaMultiplier).roundToInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun applyAmoledStyles(view: View) {
        // Obsolete UI style adjustments omitted. 
    }

    private class PresetConfig(
        val id: String,
        val label: String,
        val bands: FloatArray,
        val bassDb: Float,
        val bassFrequencyHz: Float,
        val bassType: Int
    )

    private class UserPresetEntry(
        val profileId: String,
        val id: String,
        val name: String,
        val bands: FloatArray
    )

    private class BassTypeOption(
        val value: Int,
        val label: String
    )

    private class ReverbOption(
        val value: Int,
        val label: String
    )
}
