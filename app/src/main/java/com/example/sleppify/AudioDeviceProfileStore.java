package com.example.sleppify;

import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AudioDeviceProfileStore {
    private static final String KEY_ACTIVE_PROFILE_ID = "active_profile_id";
    private static final String KEY_PROFILE_PREFIX = "device_profile_";
    private static final String KEY_SELECTED_PRESET = "selected_preset";
    private static final String KEY_USER_PRESET_BAND_PREFIX = "user_preset_band_";
    private static final String KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX = "profile_custom_preset_name_";
    private static final String PRESET_CUSTOM = "custom";
    private static final String USER_PRESET_ID_PREFIX = "user_profile_";
    private static final int BAND_COUNT = AudioEffectsService.EQ_BAND_COUNT;
    private static final float FLOAT_TOLERANCE = 0.01f;

    private AudioDeviceProfileStore() {
    }

    public static boolean syncActiveProfileForOutput(
            @NonNull SharedPreferences prefs,
            @Nullable AudioDeviceInfo selectedOutput
    ) {
        String targetProfileId = buildProfileId(selectedOutput);
        String currentProfileId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null);
        String aliasProfileId = findExistingAliasProfileId(prefs, targetProfileId, selectedOutput);

        if (TextUtils.equals(currentProfileId, targetProfileId)) {
            // Bootstrap/migration for installs where the current bucket is missing.
            if (!hasProfileData(prefs, targetProfileId)) {
                SharedPreferences.Editor editor = prefs.edit();
                if (!TextUtils.isEmpty(aliasProfileId)) {
                    copyProfileValuesToActive(editor, prefs, aliasProfileId);
                    copyProfileValuesToProfile(editor, prefs, aliasProfileId, targetProfileId);
                } else {
                    copyActiveValuesToProfile(editor, prefs, targetProfileId);
                }
                editor.apply();
                return !TextUtils.isEmpty(aliasProfileId);
            }
            if (!areActiveValuesAlignedWithProfile(prefs, targetProfileId)) {
                SharedPreferences.Editor editor = prefs.edit();
                copyProfileValuesToActive(editor, prefs, targetProfileId);
                editor.apply();
                return true;
            }
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit();

        if (!TextUtils.isEmpty(currentProfileId)) {
            copyActiveValuesToProfile(editor, prefs, currentProfileId);
        }

        if (hasProfileData(prefs, targetProfileId)) {
            copyProfileValuesToActive(editor, prefs, targetProfileId);
        } else if (!TextUtils.isEmpty(aliasProfileId)) {
            copyProfileValuesToActive(editor, prefs, aliasProfileId);
            copyProfileValuesToProfile(editor, prefs, aliasProfileId, targetProfileId);
        } else {
            // First time on this output: clone current active config as base profile.
            copyActiveValuesToProfile(editor, prefs, targetProfileId);
        }

        editor.putString(KEY_ACTIVE_PROFILE_ID, targetProfileId);
        editor.apply();
        return true;
    }

    public static boolean persistActiveValuesToCurrentProfile(@NonNull SharedPreferences prefs) {
        String currentProfileId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null);
        if (TextUtils.isEmpty(currentProfileId)) {
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit();
        copyActiveValuesToProfile(editor, prefs, currentProfileId);
        editor.apply();
        return true;
    }

    public static boolean restoreActiveValuesForOutput(
            @NonNull SharedPreferences prefs,
            @Nullable AudioDeviceInfo selectedOutput
    ) {
        String targetProfileId = buildProfileId(selectedOutput);
        String aliasProfileId = findExistingAliasProfileId(prefs, targetProfileId, selectedOutput);

        String sourceProfileId;
        if (hasProfileData(prefs, targetProfileId)) {
            sourceProfileId = targetProfileId;
        } else if (!TextUtils.isEmpty(aliasProfileId) && hasProfileData(prefs, aliasProfileId)) {
            sourceProfileId = aliasProfileId;
        } else {
            return false;
        }

        String currentProfileId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null);
        boolean sourceIsTarget = TextUtils.equals(sourceProfileId, targetProfileId);
        if (sourceIsTarget
                && TextUtils.equals(currentProfileId, targetProfileId)
                && areActiveValuesAlignedWithProfile(prefs, targetProfileId)) {
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit();
        copyProfileValuesToActive(editor, prefs, sourceProfileId);
        if (!TextUtils.equals(sourceProfileId, targetProfileId)) {
            copyProfileValuesToProfile(editor, prefs, sourceProfileId, targetProfileId);
        }
        editor.putString(KEY_ACTIVE_PROFILE_ID, targetProfileId);
        editor.apply();
        return true;
    }

    @NonNull
    public static String getActiveProfileId(
            @NonNull SharedPreferences prefs,
            @Nullable AudioDeviceInfo selectedOutput
    ) {
        String active = prefs.getString(KEY_ACTIVE_PROFILE_ID, null);
        if (!TextUtils.isEmpty(active)) {
            return active;
        }
        return buildProfileId(selectedOutput);
    }

    @NonNull
    public static String buildProfileId(@Nullable AudioDeviceInfo selectedOutput) {
        if (selectedOutput == null) {
            return "builtin_speaker";
        }

        int type = selectedOutput.getType();
        String uniquePart = sanitize(uniqueOutputName(selectedOutput));

        if (isBluetoothType(type)) {
            return TextUtils.isEmpty(uniquePart) ? "bluetooth" : "bluetooth_" + uniquePart;
        }
        if (isWiredType(type)) {
            return TextUtils.isEmpty(uniquePart) ? "wired" : "wired_" + uniquePart;
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) {
            return "builtin_speaker";
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
            return "builtin_earpiece";
        }

        String typeKey = "type_" + type;
        return TextUtils.isEmpty(uniquePart) ? typeKey : typeKey + "_" + uniquePart;
    }

    private static boolean hasProfileData(@NonNull SharedPreferences prefs, @NonNull String profileId) {
        if (prefs.contains(profileKey(profileId, AudioEffectsService.KEY_BASS_DB))) {
            return true;
        }
        if (prefs.contains(profileKey(profileId, AudioEffectsService.KEY_BASS_TYPE))) {
            return true;
        }
        if (prefs.contains(profileKey(profileId, AudioEffectsService.KEY_BASS_FREQUENCY_HZ))) {
            return true;
        }
        if (prefs.contains(profileKey(profileId, KEY_SELECTED_PRESET))) {
            return true;
        }
        if (prefs.contains(profileCustomPresetNameKey(profileId))) {
            return true;
        }
        if (hasAnyUserPresetBands(prefs, profileId)) {
            return true;
        }
        return prefs.contains(profileBandKey(profileId, 0));
    }

    private static boolean areActiveValuesAlignedWithProfile(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId
    ) {
        if (!hasProfileData(prefs, profileId)) {
            return false;
        }

        if (!sameFloat(
                prefs.getFloat(AudioEffectsService.KEY_BASS_DB, 0f),
                prefs.getFloat(profileKey(profileId, AudioEffectsService.KEY_BASS_DB), 0f)
        )) {
            return false;
        }

        if (prefs.getInt(AudioEffectsService.KEY_BASS_TYPE, AudioEffectsService.BASS_TYPE_NATURAL)
                != prefs.getInt(
                profileKey(profileId, AudioEffectsService.KEY_BASS_TYPE),
                AudioEffectsService.BASS_TYPE_NATURAL
        )) {
            return false;
        }

        if (!sameFloat(
                prefs.getFloat(
                        AudioEffectsService.KEY_BASS_FREQUENCY_HZ,
                        AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ
                ),
                prefs.getFloat(
                        profileKey(profileId, AudioEffectsService.KEY_BASS_FREQUENCY_HZ),
                        AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ
                )
        )) {
            return false;
        }

        String activePreset = prefs.getString(KEY_SELECTED_PRESET, PRESET_CUSTOM);
        String profilePreset = resolveProfilePresetId(prefs, profileId);
        if (!TextUtils.equals(activePreset, profilePreset)) {
            return false;
        }

        for (int i = 0; i < BAND_COUNT; i++) {
            if (!sameFloat(
                    prefs.getFloat(AudioEffectsService.bandDbKey(i), 0f),
                    resolveProfileBandValue(prefs, profileId, i, profilePreset)
            )) {
                return false;
            }
        }

        return true;
    }

    private static boolean sameFloat(float left, float right) {
        return Math.abs(left - right) <= FLOAT_TOLERANCE;
    }

    private static void copyActiveValuesToProfile(
            @NonNull SharedPreferences.Editor editor,
            @NonNull SharedPreferences prefs,
            @NonNull String profileId
    ) {
        editor.putFloat(
                profileKey(profileId, AudioEffectsService.KEY_BASS_DB),
            prefs.getFloat(AudioEffectsService.KEY_BASS_DB, 0f)
        );
        editor.putInt(
            profileKey(profileId, AudioEffectsService.KEY_BASS_TYPE),
            prefs.getInt(AudioEffectsService.KEY_BASS_TYPE, AudioEffectsService.BASS_TYPE_NATURAL)
        );
        editor.putFloat(
                profileKey(profileId, AudioEffectsService.KEY_BASS_FREQUENCY_HZ),
            prefs.getFloat(
                AudioEffectsService.KEY_BASS_FREQUENCY_HZ,
                AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ
            )
        );
        String selectedPreset = prefs.getString(KEY_SELECTED_PRESET, null);
        if (TextUtils.isEmpty(selectedPreset)) {
            selectedPreset = "custom";
        }
        editor.putString(
                profileKey(profileId, KEY_SELECTED_PRESET),
                selectedPreset
        );

        for (int i = 0; i < BAND_COUNT; i++) {
            editor.putFloat(
                    profileBandKey(profileId, i),
                    prefs.getFloat(AudioEffectsService.bandDbKey(i), 0f)
            );
        }
    }

    private static void copyProfileValuesToActive(
            @NonNull SharedPreferences.Editor editor,
            @NonNull SharedPreferences prefs,
            @NonNull String profileId
    ) {
        String profilePresetId = resolveProfilePresetId(prefs, profileId);

        editor.putFloat(
                AudioEffectsService.KEY_BASS_DB,
            prefs.getFloat(profileKey(profileId, AudioEffectsService.KEY_BASS_DB), 0f)
        );
        editor.putInt(
            AudioEffectsService.KEY_BASS_TYPE,
            prefs.getInt(
                profileKey(profileId, AudioEffectsService.KEY_BASS_TYPE),
                AudioEffectsService.BASS_TYPE_NATURAL
            )
        );
        editor.putFloat(
                AudioEffectsService.KEY_BASS_FREQUENCY_HZ,
            prefs.getFloat(
                profileKey(profileId, AudioEffectsService.KEY_BASS_FREQUENCY_HZ),
                AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ
            )
        );
        editor.putString(
                KEY_SELECTED_PRESET,
            profilePresetId
        );

        for (int i = 0; i < BAND_COUNT; i++) {
            editor.putFloat(
                    AudioEffectsService.bandDbKey(i),
                    resolveProfileBandValue(prefs, profileId, i, profilePresetId)
            );
        }
    }

    private static void copyProfileValuesToProfile(
            @NonNull SharedPreferences.Editor editor,
            @NonNull SharedPreferences prefs,
            @NonNull String sourceProfileId,
            @NonNull String targetProfileId
    ) {
        editor.putFloat(
                profileKey(targetProfileId, AudioEffectsService.KEY_BASS_DB),
                prefs.getFloat(profileKey(sourceProfileId, AudioEffectsService.KEY_BASS_DB), 0f)
        );
        editor.putInt(
                profileKey(targetProfileId, AudioEffectsService.KEY_BASS_TYPE),
                prefs.getInt(
                        profileKey(sourceProfileId, AudioEffectsService.KEY_BASS_TYPE),
                        AudioEffectsService.BASS_TYPE_NATURAL
                )
        );
        editor.putFloat(
                profileKey(targetProfileId, AudioEffectsService.KEY_BASS_FREQUENCY_HZ),
                prefs.getFloat(
                        profileKey(sourceProfileId, AudioEffectsService.KEY_BASS_FREQUENCY_HZ),
                        AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ
                )
        );

        String selectedPreset = resolveProfilePresetId(prefs, sourceProfileId);
        editor.putString(profileKey(targetProfileId, KEY_SELECTED_PRESET), selectedPreset);

        for (int i = 0; i < BAND_COUNT; i++) {
            editor.putFloat(
                    profileBandKey(targetProfileId, i),
                    resolveProfileBandValue(prefs, sourceProfileId, i, selectedPreset)
            );
        }
    }

    private static float resolveProfileBandValue(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId,
            int bandIndex
    ) {
        return resolveProfileBandValue(prefs, profileId, bandIndex, null);
    }

    private static float resolveProfileBandValue(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId,
            int bandIndex,
            @Nullable String profilePresetId
    ) {
        boolean fallbackEligible = shouldUseUserPresetBandFallback(prefs, profileId, profilePresetId);
        boolean preferUserSnapshot = fallbackEligible && shouldPreferUserPresetSnapshot(prefs, profileId);

        if (preferUserSnapshot) {
            String userPresetBandKey = userPresetBandKey(profileId, bandIndex);
            if (prefs.contains(userPresetBandKey)) {
                return prefs.getFloat(userPresetBandKey, 0f);
            }
        }

        String directBandKey = profileBandKey(profileId, bandIndex);
        if (prefs.contains(directBandKey)) {
            return prefs.getFloat(directBandKey, 0f);
        }

        if (fallbackEligible) {
            String userPresetBandKey = userPresetBandKey(profileId, bandIndex);
            if (prefs.contains(userPresetBandKey)) {
                return prefs.getFloat(userPresetBandKey, 0f);
            }
        }

        return 0f;
    }

    private static boolean shouldUseUserPresetBandFallback(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId,
            @Nullable String profilePresetId
    ) {
        String resolvedPresetId = TextUtils.isEmpty(profilePresetId)
                ? resolveProfilePresetId(prefs, profileId)
                : profilePresetId;

        if (!TextUtils.isEmpty(resolvedPresetId)) {
            if (resolvedPresetId.startsWith(USER_PRESET_ID_PREFIX)) {
                return true;
            }
            if (PRESET_CUSTOM.equals(resolvedPresetId)) {
                return prefs.contains(profileCustomPresetNameKey(profileId)) || hasAnyUserPresetBands(prefs, profileId);
            }
            // Presets fijos (ej. flat/bass/treble) deben respetar su curva de perfil
            // y no heredar snapshots de presets de usuario.
            return false;
        }

        return hasAnyUserPresetBands(prefs, profileId);
    }

    @NonNull
    private static String resolveProfilePresetId(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId
    ) {
        String storedPresetId = prefs.getString(profileKey(profileId, KEY_SELECTED_PRESET), null);
        if (!TextUtils.isEmpty(storedPresetId)) {
            return storedPresetId;
        }

        if (hasAnyUserPresetBands(prefs, profileId)) {
            if (prefs.contains(profileCustomPresetNameKey(profileId))) {
                return userPresetIdForProfile(profileId);
            }
            return PRESET_CUSTOM;
        }

        return PRESET_CUSTOM;
    }

    private static boolean hasAnyUserPresetBands(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId
    ) {
        for (int i = 0; i < BAND_COUNT; i++) {
            if (prefs.contains(userPresetBandKey(profileId, i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldPreferUserPresetSnapshot(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId
    ) {
        if (!hasAnyUserPresetBands(prefs, profileId)) {
            return false;
        }
        if (!hasAnyDirectProfileBands(prefs, profileId)) {
            return false;
        }
        if (!isProfileBandsEffectivelyFlat(prefs, profileId)) {
            return false;
        }
        return hasNonFlatUserPresetBands(prefs, profileId);
    }

    private static boolean hasAnyDirectProfileBands(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId
    ) {
        for (int i = 0; i < BAND_COUNT; i++) {
            if (prefs.contains(profileBandKey(profileId, i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProfileBandsEffectivelyFlat(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId
    ) {
        for (int i = 0; i < BAND_COUNT; i++) {
            String key = profileBandKey(profileId, i);
            if (!prefs.contains(key)) {
                continue;
            }
            if (Math.abs(prefs.getFloat(key, 0f)) > FLOAT_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNonFlatUserPresetBands(
            @NonNull SharedPreferences prefs,
            @NonNull String profileId
    ) {
        for (int i = 0; i < BAND_COUNT; i++) {
            String key = userPresetBandKey(profileId, i);
            if (!prefs.contains(key)) {
                continue;
            }
            if (Math.abs(prefs.getFloat(key, 0f)) > FLOAT_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String findExistingAliasProfileId(
            @NonNull SharedPreferences prefs,
            @NonNull String targetProfileId,
            @Nullable AudioDeviceInfo selectedOutput
    ) {
        if (selectedOutput == null) {
            return null;
        }

        int type = selectedOutput.getType();
        String productNamePart = sanitizeProductName(selectedOutput);

        if (isBluetoothType(type)) {
            if (!TextUtils.isEmpty(productNamePart)) {
                String bluetoothByName = "bluetooth_" + productNamePart;
                if (!TextUtils.equals(bluetoothByName, targetProfileId) && hasProfileData(prefs, bluetoothByName)) {
                    return bluetoothByName;
                }
            }
            if (!TextUtils.equals("bluetooth", targetProfileId) && hasProfileData(prefs, "bluetooth")) {
                return "bluetooth";
            }
        }

        if (isWiredType(type)) {
            if (!TextUtils.isEmpty(productNamePart)) {
                String wiredByName = "wired_" + productNamePart;
                if (!TextUtils.equals(wiredByName, targetProfileId) && hasProfileData(prefs, wiredByName)) {
                    return wiredByName;
                }
            }
            if (!TextUtils.equals("wired", targetProfileId) && hasProfileData(prefs, "wired")) {
                return "wired";
            }
        }

        String typeOnly = "type_" + type;
        if (!TextUtils.equals(typeOnly, targetProfileId) && hasProfileData(prefs, typeOnly)) {
            return typeOnly;
        }

        return null;
    }

    @NonNull
    private static String sanitizeProductName(@NonNull AudioDeviceInfo deviceInfo) {
        CharSequence productName = deviceInfo.getProductName();
        if (productName == null) {
            return "";
        }
        return sanitize(productName.toString().trim());
    }

    @NonNull
    private static String uniqueOutputName(@NonNull AudioDeviceInfo deviceInfo) {
        String address = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                String value = deviceInfo.getAddress();
                if (value != null) {
                    address = value.trim();
                }
            } catch (Throwable ignored) {
                // Some API levels/vendors may not expose address reliably.
            }
        }
        if (!TextUtils.isEmpty(address) && !"00:00:00:00:00:00".equals(address)) {
            return address;
        }

        CharSequence name = deviceInfo.getProductName();
        if (name == null) {
            return "";
        }
        return name.toString().trim();
    }

    @NonNull
    private static String sanitize(@Nullable String rawValue) {
        if (TextUtils.isEmpty(rawValue)) {
            return "";
        }
        String lower = rawValue.toLowerCase();
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }

        String normalized = builder.toString().replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String profileBandKey(@NonNull String profileId, int bandIndex) {
        return profileKey(profileId, AudioEffectsService.bandDbKey(bandIndex));
    }

    private static String userPresetBandKey(@NonNull String profileId, int bandIndex) {
        return KEY_USER_PRESET_BAND_PREFIX + profileId + "_" + bandIndex;
    }

    private static String profileCustomPresetNameKey(@NonNull String profileId) {
        return KEY_PROFILE_CUSTOM_PRESET_NAME_PREFIX + profileId;
    }

    @NonNull
    private static String userPresetIdForProfile(@NonNull String profileId) {
        return USER_PRESET_ID_PREFIX + profileId;
    }

    private static String profileKey(@NonNull String profileId, @NonNull String baseKey) {
        return KEY_PROFILE_PREFIX + profileId + "_" + baseKey;
    }

    private static boolean isBluetoothType(int type) {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_HEARING_AID
                || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                || type == AudioDeviceInfo.TYPE_BLE_BROADCAST;
    }

    private static boolean isWiredType(int type) {
        return type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_DEVICE
                || type == AudioDeviceInfo.TYPE_LINE_ANALOG
                || type == AudioDeviceInfo.TYPE_LINE_DIGITAL
                || type == AudioDeviceInfo.TYPE_AUX_LINE;
    }
}
