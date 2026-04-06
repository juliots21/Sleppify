package com.example.sleppify;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SmartSuggestionPrefetcher {

    private static final String KEY_SCHEDULE_AI_ACCEPT_COUNT = "schedule_ai_accept_count";
    private static final String KEY_SCHEDULE_AI_DISMISS_COUNT = "schedule_ai_dismiss_count";
    private static final String KEY_SCHEDULE_AI_LAST_MESSAGE = "schedule_ai_last_message";
    private static final String KEY_SCHEDULE_AI_LAST_ACTION = "schedule_ai_last_action";
    private static final String KEY_SCHEDULE_AI_OPEN_COUNT = "schedule_ai_open_count";
    private static final String KEY_SCHEDULE_AI_NEXT_AT = "schedule_ai_next_at";
    private static final String KEY_SCHEDULE_AI_LAST_PROCESS_SESSION = "schedule_ai_last_process_session";
    private static final String KEY_SCHEDULE_AI_HIDDEN_UNTIL_NEXT = "schedule_ai_hidden_until_next";
    private static final String KEY_SCHEDULE_AI_ACTIONS_HIDDEN = "schedule_ai_actions_hidden";
    private static final String KEY_SCHEDULE_AI_FEEDBACK_MESSAGE_HASH = "schedule_ai_feedback_message_hash";

    private static final String KEY_AI_EQ_NEXT_AT_PREFIX = "ai_eq_next_at_";
    private static final String KEY_AI_EQ_PENDING_JSON_PREFIX = "ai_eq_pending_json_";
    private static final String KEY_AI_EQ_LAST_PROCESS_SESSION_PREFIX = "ai_eq_last_process_session_";
    private static final String KEY_AI_EQ_DISMISSED_SESSION_PREFIX = "ai_eq_dismissed_session_";

    public static final String KEY_APPS_AI_LAST_MESSAGE = "apps_ai_last_message";
    public static final String KEY_APPS_AI_NEXT_AT = "apps_ai_next_at";
    public static final String KEY_APPS_AI_LAST_PROCESS_SESSION = "apps_ai_last_process_session";
    public static final String KEY_APPS_AI_ACCEPT_COUNT = "apps_ai_accept_count";
    public static final String KEY_APPS_AI_DISMISS_COUNT = "apps_ai_dismiss_count";
    public static final String KEY_APPS_AI_LAST_ACTION = "apps_ai_last_action";
    public static final String KEY_APPS_AI_HIDDEN_UNTIL_NEXT = "apps_ai_hidden_until_next";
    public static final String KEY_APPS_AI_ACTIONS_HIDDEN = "apps_ai_actions_hidden";

    public static final long SUGGESTION_INTERVAL_MS = 2L * 60L * 60L * 1000L;
    private static final long EQ_RETRY_MS = 15L * 60L * 1000L;
    private static final long APPS_RETRY_MS = 15L * 60L * 1000L;
    private static final long APPS_LOOKBACK_MS = 45L * 60L * 1000L;
    private static final long AGENDA_SNAPSHOT_CACHE_TTL_MS = 30_000L;
    private static final long ACTIVE_APPS_SNAPSHOT_CACHE_TTL_MS = 20_000L;
    private static final int APPS_MAX_LABELS = 4;
    private static volatile boolean appsSuggestionInFlight;
    private static final Object agendaSnapshotCacheLock = new Object();
    private static final Object activeAppsSnapshotCacheLock = new Object();
    @Nullable
    private static String cachedAgendaRaw;
    @Nullable
    private static String cachedAgendaSnapshot;
    private static long cachedAgendaSnapshotAtMs;
    @Nullable
    private static ActiveAppsSnapshot cachedActiveAppsSnapshot;
    @Nullable
    private static String cachedActiveAppsPackageName;
    private static long cachedActiveAppsSnapshotAtMs;

    private SmartSuggestionPrefetcher() {
    }

    public static void prefetchAll(@NonNull Context context, @Nullable String profileName) {
        if (!isSmartSuggestionsEnabled(context)) {
            return;
        }
        // Schedule suggestion is owned by WeeklySchedulerFragment to avoid write races.
        prefetchEqSuggestion(context);
        prefetchAppsSuggestion(context);
    }

    public static void prefetchScheduleSuggestion(@NonNull Context context, @Nullable String profileName) {
        if (!isSmartSuggestionsEnabled(context)) {
            return;
        }
        SharedPreferences settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();

        long lastProcessSession = settingsPrefs.getLong(KEY_SCHEDULE_AI_LAST_PROCESS_SESSION, Long.MIN_VALUE);
        boolean isNewProcessSession = lastProcessSession != AiSuggestionSession.PROCESS_SESSION_ID;
        if (isNewProcessSession) {
            settingsPrefs.edit()
                    .putLong(KEY_SCHEDULE_AI_LAST_PROCESS_SESSION, AiSuggestionSession.PROCESS_SESSION_ID)
                    .apply();
        }

        String lastMessage = settingsPrefs.getString(KEY_SCHEDULE_AI_LAST_MESSAGE, "");
        long nextAt = settingsPrefs.getLong(KEY_SCHEDULE_AI_NEXT_AT, 0L);
        boolean shouldGenerate = TextUtils.isEmpty(lastMessage) || isNewProcessSession || now >= nextAt;
        if (!shouldGenerate) {
            return;
        }

        String safeName = TextUtils.isEmpty(profileName) ? "usuario" : profileName.trim();
        String agendaSnapshot = buildAgendaSnapshotForAi(context);
        String behaviorSignals = buildBehaviorSignalsForAi(settingsPrefs);

        new GeminiIntelligenceService().generateScheduleSuggestion(
                safeName,
                agendaSnapshot,
                behaviorSignals,
                new GeminiIntelligenceService.ScheduleSuggestionCallback() {
                    @Override
                    public void onSuccess(GeminiIntelligenceService.ScheduleSuggestion suggestion) {
                        String rendered = renderScheduleSuggestionMessage(suggestion);
                        settingsPrefs.edit()
                                .putString(KEY_SCHEDULE_AI_LAST_MESSAGE, rendered)
                                .putBoolean(KEY_SCHEDULE_AI_HIDDEN_UNTIL_NEXT, false)
                                .putBoolean(KEY_SCHEDULE_AI_ACTIONS_HIDDEN, false)
                            .remove(KEY_SCHEDULE_AI_FEEDBACK_MESSAGE_HASH)
                                .putLong(KEY_SCHEDULE_AI_NEXT_AT, System.currentTimeMillis() + SUGGESTION_INTERVAL_MS)
                                .apply();
                    }

                    @Override
                    public void onError(String error) {
                        String fallback = buildScheduleFallbackFromAgendaSnapshot(agendaSnapshot);
                        settingsPrefs.edit()
                                .putString(KEY_SCHEDULE_AI_LAST_MESSAGE, fallback)
                                .putBoolean(KEY_SCHEDULE_AI_HIDDEN_UNTIL_NEXT, false)
                                .putBoolean(KEY_SCHEDULE_AI_ACTIONS_HIDDEN, false)
                            .remove(KEY_SCHEDULE_AI_FEEDBACK_MESSAGE_HASH)
                                .putLong(KEY_SCHEDULE_AI_NEXT_AT, System.currentTimeMillis() + SUGGESTION_INTERVAL_MS)
                                .apply();
                    }
                }
        );
    }

    public static void prefetchEqSuggestion(@NonNull Context context) {
        if (!isSmartSuggestionsEnabled(context)) {
            return;
        }
        SharedPreferences eqPrefs = context.getSharedPreferences(AudioEffectsService.PREFS_NAME, Context.MODE_PRIVATE);

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo selectedOutput = null;
        if (audioManager != null) {
            try {
                selectedOutput = selectPreferredOutput(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
            } catch (Throwable ignored) {
                selectedOutput = null;
            }
        }

        AudioDeviceProfileStore.syncActiveProfileForOutput(eqPrefs, selectedOutput);
        String profileId = AudioDeviceProfileStore.getActiveProfileId(eqPrefs, selectedOutput);

        String pendingKey = eqPendingKey(profileId);
        String nextAtKey = eqNextAtKey(profileId);
        String processKey = eqProcessSessionKey(profileId);
        String dismissedSessionKey = eqDismissedSessionKey(profileId);

        long lastProcessSession = eqPrefs.getLong(processKey, Long.MIN_VALUE);
        boolean isNewProcessSession = lastProcessSession != AiSuggestionSession.PROCESS_SESSION_ID;

        SharedPreferences.Editor processEditor = eqPrefs.edit();
        processEditor.putLong(processKey, AiSuggestionSession.PROCESS_SESSION_ID);
        if (isNewProcessSession) {
            processEditor.remove(dismissedSessionKey);
        }
        processEditor.apply();

        long dismissedOnSession = eqPrefs.getLong(dismissedSessionKey, Long.MIN_VALUE);
        if (dismissedOnSession == AiSuggestionSession.PROCESS_SESSION_ID) {
            return;
        }

        long now = System.currentTimeMillis();
        long nextAt = eqPrefs.getLong(nextAtKey, 0L);
        if (nextAt > 0L && now < nextAt) {
            return;
        }

        float[] bands = new float[AudioEffectsService.EQ_BAND_COUNT];
        for (int i = 0; i < bands.length; i++) {
            bands[i] = eqPrefs.getFloat(AudioEffectsService.bandDbKey(i), 0f);
        }

        float bassDb = eqPrefs.getFloat(AudioEffectsService.KEY_BASS_DB, 0f);
        float bassFrequencyHz = eqPrefs.getFloat(AudioEffectsService.KEY_BASS_FREQUENCY_HZ, AudioEffectsService.BASS_FREQUENCY_DEFAULT_HZ);

        String outputDescriptor = buildOutputDescriptorForAi(selectedOutput);
        String phoneModel = buildPhoneModelDescriptor();

        new GeminiIntelligenceService().generateEqSuggestion(
                outputDescriptor,
                phoneModel,
                bands,
                bassDb,
                bassFrequencyHz,
                new GeminiIntelligenceService.EqSuggestionCallback() {
                    @Override
                    public void onSuccess(GeminiIntelligenceService.EqSuggestion suggestion) {
                        try {
                            JSONObject json = new JSONObject();
                            json.put("message", suggestion.message);
                            JSONArray bandArray = new JSONArray();
                            for (float value : suggestion.bands) {
                                bandArray.put(value);
                            }
                            json.put("bands", bandArray);
                            json.put("bassDb", suggestion.bassDb);
                            json.put("bassFrequencyHz", suggestion.bassFrequencyHz);

                            eqPrefs.edit()
                                    .putString(pendingKey, json.toString())
                                    .remove(dismissedSessionKey)
                                    .putLong(nextAtKey, System.currentTimeMillis() + SUGGESTION_INTERVAL_MS)
                                    .apply();
                        } catch (Exception ignored) {
                            // Si falla el serializado no bloqueamos el flujo.
                        }
                    }

                    @Override
                    public void onError(String error) {
                        eqPrefs.edit()
                                .putLong(nextAtKey, System.currentTimeMillis() + EQ_RETRY_MS)
                                .apply();
                    }
                }
        );
    }

    public static void prefetchAppsSuggestion(@NonNull Context context) {
        if (!isSmartSuggestionsEnabled(context)) {
            appsSuggestionInFlight = false;
            return;
        }
        SharedPreferences settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();

        long lastProcessSession = settingsPrefs.getLong(KEY_APPS_AI_LAST_PROCESS_SESSION, Long.MIN_VALUE);
        boolean isNewProcessSession = lastProcessSession != AiSuggestionSession.PROCESS_SESSION_ID;
        if (isNewProcessSession) {
            settingsPrefs.edit()
                    .putLong(KEY_APPS_AI_LAST_PROCESS_SESSION, AiSuggestionSession.PROCESS_SESSION_ID)
                    .apply();
        }

        String lastMessage = settingsPrefs.getString(KEY_APPS_AI_LAST_MESSAGE, "");
        long nextAt = settingsPrefs.getLong(KEY_APPS_AI_NEXT_AT, 0L);

        boolean shouldGenerate = TextUtils.isEmpty(lastMessage) || isNewProcessSession || now >= nextAt;
        if (!shouldGenerate) {
            return;
        }

        if (appsSuggestionInFlight) {
            return;
        }
        appsSuggestionInFlight = true;

        ActiveAppsSnapshot snapshot = readActiveAppsSnapshot(context);
        String appsContext = buildAppsContextForAi(context, snapshot);
        String behaviorSignals = buildAppsBehaviorSignalsForAi(settingsPrefs);

        String profileName = "usuario";
        try {
            AuthManager authManager = AuthManager.getInstance(context);
            if (authManager != null && authManager.isSignedIn() && !TextUtils.isEmpty(authManager.getDisplayName())) {
                profileName = authManager.getDisplayName();
            }
        } catch (Throwable ignored) {
            // Si no podemos resolver perfil, usamos fallback neutro.
        }

        new GeminiIntelligenceService().generateAppsSuggestion(
                profileName,
                appsContext,
                behaviorSignals,
                new GeminiIntelligenceService.AppsSuggestionCallback() {
                    @Override
                    public void onSuccess(String message) {
                        appsSuggestionInFlight = false;
                        settingsPrefs.edit()
                                .putString(KEY_APPS_AI_LAST_MESSAGE, message)
                                .putBoolean(KEY_APPS_AI_HIDDEN_UNTIL_NEXT, false)
                            .putBoolean(KEY_APPS_AI_ACTIONS_HIDDEN, false)
                                .putLong(KEY_APPS_AI_NEXT_AT, System.currentTimeMillis() + SUGGESTION_INTERVAL_MS)
                                .apply();
                    }

                    @Override
                    public void onError(String error) {
                        appsSuggestionInFlight = false;
                        settingsPrefs.edit()
                                .putLong(KEY_APPS_AI_NEXT_AT, System.currentTimeMillis() + APPS_RETRY_MS)
                                .apply();
                    }
                }
        );
    }

    @NonNull
    private static String renderScheduleSuggestionMessage(@NonNull GeminiIntelligenceService.ScheduleSuggestion suggestion) {
        String message = suggestion.message == null ? "" : suggestion.message;
        String microAction = suggestion.microAction == null ? "" : suggestion.microAction.trim();
        if (!TextUtils.isEmpty(microAction)) {
            return message + "\n\nAccion sugerida: " + microAction;
        }
        return message;
    }

    @NonNull
    private static String buildAgendaSnapshotForAi(@NonNull Context context) {
        String rawAgenda = CloudSyncManager.getInstance(context).getLocalAgendaJson();
        long now = System.currentTimeMillis();

        synchronized (agendaSnapshotCacheLock) {
            if (cachedAgendaSnapshot != null
                    && TextUtils.equals(cachedAgendaRaw, rawAgenda)
                    && (now - cachedAgendaSnapshotAtMs) <= AGENDA_SNAPSHOT_CACHE_TTL_MS) {
                return cachedAgendaSnapshot;
            }
        }

        int totalTasks = 0;
        int morningTasks = 0;
        int afternoonTasks = 0;
        int nightTasks = 0;

        try {
            JSONObject root = new JSONObject(rawAgenda);
            JSONArray names = root.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String day = names.optString(i, "");
                    JSONArray tasks = root.optJSONArray(day);
                    if (tasks == null) {
                        continue;
                    }
                    for (int j = 0; j < tasks.length(); j++) {
                        JSONObject task = tasks.optJSONObject(j);
                        if (task == null) {
                            continue;
                        }
                        totalTasks++;
                        int hour = parseHourFromTaskTime(task.optString("time", "12:00 PM"));
                        if (hour >= 5 && hour < 12) {
                            morningTasks++;
                        } else if (hour >= 12 && hour < 19) {
                            afternoonTasks++;
                        } else {
                            nightTasks++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Si falla parseo, devolvemos snapshot minimo.
        }

        String snapshot = "total_tareas=" + totalTasks
                + "; franjas{manana=" + morningTasks + ", tarde=" + afternoonTasks + ", noche=" + nightTasks + "}";

        synchronized (agendaSnapshotCacheLock) {
            cachedAgendaRaw = rawAgenda;
            cachedAgendaSnapshot = snapshot;
            cachedAgendaSnapshotAtMs = now;
        }
        return snapshot;
    }

    @NonNull
    private static String buildBehaviorSignalsForAi(@NonNull SharedPreferences settingsPrefs) {
        int acceptCount = settingsPrefs.getInt(KEY_SCHEDULE_AI_ACCEPT_COUNT, 0);
        int dismissCount = settingsPrefs.getInt(KEY_SCHEDULE_AI_DISMISS_COUNT, 0);
        int openCount = settingsPrefs.getInt(KEY_SCHEDULE_AI_OPEN_COUNT, 0);
        String lastAction = settingsPrefs.getString(KEY_SCHEDULE_AI_LAST_ACTION, "ninguna");
        String lastMessage = settingsPrefs.getString(KEY_SCHEDULE_AI_LAST_MESSAGE, "");

        return "aprobadas=" + acceptCount
                + ", descartadas=" + dismissCount
                + ", aperturas_app=" + openCount
                + ", ultima_accion=" + lastAction
                + ", ultimo_mensaje='" + lastMessage + "'";
    }

    @NonNull
    private static String buildScheduleFallbackFromAgendaSnapshot(@NonNull String agendaSnapshot) {
        if (agendaSnapshot.contains("total_tareas=0")) {
            return "Aun no tengo suficientes datos tuyos. Crea 2 o 3 tareas y al abrir de nuevo te dare una recomendacion mas precisa.";
        }
        if (agendaSnapshot.contains("manana=") && extractCount(agendaSnapshot, "manana=") >= extractCount(agendaSnapshot, "tarde=")) {
            return "Tus tareas suelen concentrarse en la manana. Reserva un bloque de foco de 45 min entre 9:00 AM y 11:00 AM para tu prioridad principal.";
        }
        return "Protege hoy un bloque sin interrupciones para la tarea mas exigente y deja las tareas ligeras para despues.";
    }

    private static int extractCount(@NonNull String raw, @NonNull String token) {
        int index = raw.indexOf(token);
        if (index < 0) {
            return 0;
        }
        int start = index + token.length();
        int end = start;
        while (end < raw.length() && Character.isDigit(raw.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.substring(start, end));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int parseHourFromTaskTime(@Nullable String rawTime) {
        if (TextUtils.isEmpty(rawTime)) {
            return 12;
        }

        String value = rawTime.trim().toUpperCase(Locale.US);
        try {
            boolean isPm = value.contains("PM");
            boolean isAm = value.contains("AM");

            String numeric = value.replace("AM", "").replace("PM", "").trim();
            String[] split = numeric.split(":");
            int hour = Integer.parseInt(split[0].trim());
            if (isPm && hour < 12) {
                hour += 12;
            }
            if (isAm && hour == 12) {
                hour = 0;
            }
            return Math.max(0, Math.min(23, hour));
        } catch (Exception ignored) {
            return 12;
        }
    }

    @NonNull
    private static String buildOutputDescriptorForAi(@Nullable AudioDeviceInfo output) {
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
        return typeLabel + " modelo " + model;
    }

    @NonNull
    private static String buildPhoneModelDescriptor() {
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
                builder.append(" (").append(device).append(")");
            } else {
                builder.append(device);
            }
        }
        if (builder.length() == 0) {
            return "modelo de telefono desconocido";
        }
        return builder.toString();
    }

    @Nullable
    private static AudioDeviceInfo selectPreferredOutput(@Nullable AudioDeviceInfo[] outputs) {
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

    @NonNull
    private static String deviceName(@NonNull AudioDeviceInfo deviceInfo, @NonNull String fallback) {
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

    @NonNull
    private static String eqPendingKey(@NonNull String profileId) {
        return KEY_AI_EQ_PENDING_JSON_PREFIX + profileId;
    }

    @NonNull
    private static String eqNextAtKey(@NonNull String profileId) {
        return KEY_AI_EQ_NEXT_AT_PREFIX + profileId;
    }

    @NonNull
    private static String eqProcessSessionKey(@NonNull String profileId) {
        return KEY_AI_EQ_LAST_PROCESS_SESSION_PREFIX + profileId;
    }

    @NonNull
    private static String eqDismissedSessionKey(@NonNull String profileId) {
        return KEY_AI_EQ_DISMISSED_SESSION_PREFIX + profileId;
    }

    @NonNull
    private static String buildAppsContextForAi(@NonNull Context context, @NonNull ActiveAppsSnapshot snapshot) {
        int ramPercent = readRamPercent(context);
        float tempC = readBatteryTempC(context);
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("ram_percent=").append(ramPercent).append("; ");
        if (tempC > 0f) {
            contextBuilder.append("temperature_c=").append(String.format(Locale.US, "%.1f", tempC)).append("; ");
        } else {
            contextBuilder.append("temperature_c=unavailable; ");
        }

        contextBuilder.append("usage_access=").append(snapshot.hasUsageAccess ? "granted" : "missing").append("; ");
        contextBuilder.append("active_apps_count=").append(snapshot.activeAppCount).append("; ");

        if (snapshot.topAppLabels.isEmpty()) {
            contextBuilder.append("active_apps=[]");
        } else {
            contextBuilder.append("active_apps=[").append(formatAppList(snapshot.topAppLabels)).append("]");
        }

        return contextBuilder.toString();
    }

    @NonNull
    private static String buildAppsBehaviorSignalsForAi(@NonNull SharedPreferences settingsPrefs) {
        int acceptCount = settingsPrefs.getInt(KEY_APPS_AI_ACCEPT_COUNT, 0);
        int dismissCount = settingsPrefs.getInt(KEY_APPS_AI_DISMISS_COUNT, 0);
        String lastAction = settingsPrefs.getString(KEY_APPS_AI_LAST_ACTION, "ninguna");
        String lastMessage = settingsPrefs.getString(KEY_APPS_AI_LAST_MESSAGE, "");

        return "aprobadas=" + acceptCount
                + ", descartadas=" + dismissCount
                + ", ultima_accion=" + lastAction
                + ", ultimo_mensaje='" + lastMessage + "'";
    }

    @NonNull
    private static ActiveAppsSnapshot readActiveAppsSnapshot(@NonNull Context context) {
        long now = System.currentTimeMillis();
        String contextPackageName = context.getPackageName();
        boolean currentHasUsageAccess = hasUsageStatsAccess(context);

        synchronized (activeAppsSnapshotCacheLock) {
            if (cachedActiveAppsSnapshot != null
                    && TextUtils.equals(cachedActiveAppsPackageName, contextPackageName)
                    && cachedActiveAppsSnapshot.hasUsageAccess == currentHasUsageAccess
                    && (now - cachedActiveAppsSnapshotAtMs) <= ACTIVE_APPS_SNAPSHOT_CACHE_TTL_MS) {
                return new ActiveAppsSnapshot(
                        cachedActiveAppsSnapshot.topAppLabels,
                        cachedActiveAppsSnapshot.activeAppCount,
                        cachedActiveAppsSnapshot.hasUsageAccess
                );
            }
        }

        Map<String, Long> scoreByPackage = new HashMap<>();
        collectRunningApps(context, scoreByPackage);

        boolean hasUsageAccess = currentHasUsageAccess;
        if (hasUsageAccess) {
            collectUsageEvents(context, scoreByPackage, APPS_LOOKBACK_MS);
            collectUsageStats(context, scoreByPackage, APPS_LOOKBACK_MS);
            collectAggregatedUsageStats(context, scoreByPackage, APPS_LOOKBACK_MS);
        }

        if (scoreByPackage.isEmpty()) {
            ActiveAppsSnapshot emptySnapshot = new ActiveAppsSnapshot(Collections.emptyList(), 0, hasUsageAccess);
            synchronized (activeAppsSnapshotCacheLock) {
                cachedActiveAppsPackageName = contextPackageName;
                cachedActiveAppsSnapshot = emptySnapshot;
                cachedActiveAppsSnapshotAtMs = now;
            }
            return emptySnapshot;
        }

        List<Map.Entry<String, Long>> orderedEntries = new ArrayList<>(scoreByPackage.entrySet());
        Collections.sort(orderedEntries, (left, right) -> Long.compare(right.getValue(), left.getValue()));

        PackageManager packageManager = context.getPackageManager();
        String ownPackage = context.getPackageName();
        List<String> labels = new ArrayList<>();
        int activeCount = 0;

        for (Map.Entry<String, Long> entry : orderedEntries) {
            String packageName = entry.getKey();
            if (!isAppsSuggestionCandidate(packageManager, packageName, ownPackage)) {
                continue;
            }

            activeCount++;
            if (labels.size() >= APPS_MAX_LABELS) {
                continue;
            }

            String label = resolveAppLabel(packageManager, packageName);
            if (!TextUtils.isEmpty(label)) {
                labels.add(label);
            }
        }

        ActiveAppsSnapshot snapshot = new ActiveAppsSnapshot(labels, activeCount, hasUsageAccess);
        synchronized (activeAppsSnapshotCacheLock) {
            cachedActiveAppsPackageName = contextPackageName;
            cachedActiveAppsSnapshot = snapshot;
            cachedActiveAppsSnapshotAtMs = now;
        }
        return snapshot;
    }

    private static void collectRunningApps(@NonNull Context context, @NonNull Map<String, Long> scoreByPackage) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return;
        }

        List<ActivityManager.RunningAppProcessInfo> running = activityManager.getRunningAppProcesses();
        if (running == null || running.isEmpty()) {
            return;
        }

        for (ActivityManager.RunningAppProcessInfo info : running) {
            if (info == null || info.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY) {
                continue;
            }

            long baseScore = 1_000_000L - Math.min(999, info.importance);
            if (info.pkgList != null) {
                for (String packageName : info.pkgList) {
                    mergeAppScore(scoreByPackage, packageName, baseScore);
                }
            }

            String processName = info.processName;
            if (!TextUtils.isEmpty(processName)) {
                int split = processName.indexOf(':');
                String packageName = split > 0 ? processName.substring(0, split) : processName;
                mergeAppScore(scoreByPackage, packageName, baseScore - 1L);
            }
        }
    }

    private static void collectUsageEvents(
            @NonNull Context context,
            @NonNull Map<String, Long> scoreByPackage,
            long lookbackMs
    ) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long begin = Math.max(0L, now - lookbackMs);
        UsageEvents usageEvents = usageStatsManager.queryEvents(begin, now);
        if (usageEvents == null) {
            return;
        }

        Map<String, Long> lastForeground = new HashMap<>();
        Map<String, Long> lastBackground = new HashMap<>();
        UsageEvents.Event event = new UsageEvents.Event();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            String packageName = event.getPackageName();
            if (TextUtils.isEmpty(packageName)) {
                continue;
            }

            int eventType = event.getEventType();
            long timestamp = event.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    || eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || eventType == UsageEvents.Event.FOREGROUND_SERVICE_START) {
                lastForeground.put(packageName, Math.max(lastForeground.getOrDefault(packageName, 0L), timestamp));
                continue;
            }

            if (eventType == UsageEvents.Event.ACTIVITY_PAUSED
                    || eventType == UsageEvents.Event.ACTIVITY_STOPPED
                    || eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
                    || eventType == UsageEvents.Event.FOREGROUND_SERVICE_STOP) {
                lastBackground.put(packageName, Math.max(lastBackground.getOrDefault(packageName, 0L), timestamp));
            }
        }

        Set<String> packages = new LinkedHashSet<>();
        packages.addAll(lastForeground.keySet());
        packages.addAll(lastBackground.keySet());

        for (String packageName : packages) {
            long foregroundTs = lastForeground.getOrDefault(packageName, 0L);
            long backgroundTs = lastBackground.getOrDefault(packageName, 0L);
            long referenceTs = Math.max(foregroundTs, backgroundTs);
            if (referenceTs <= 0L || (now - referenceTs) > lookbackMs) {
                continue;
            }

            long score = backgroundTs > foregroundTs
                    ? 1_800_000L + backgroundTs
                    : 2_000_000L + foregroundTs;
            mergeAppScore(scoreByPackage, packageName, score);
        }
    }

    private static void collectUsageStats(
            @NonNull Context context,
            @NonNull Map<String, Long> scoreByPackage,
            long lookbackMs
    ) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long begin = Math.max(0L, now - lookbackMs);
        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                begin,
                now
        );
        if (usageStatsList == null || usageStatsList.isEmpty()) {
            return;
        }

        for (UsageStats usageStats : usageStatsList) {
            if (usageStats == null) {
                continue;
            }

            String packageName = usageStats.getPackageName();
            if (TextUtils.isEmpty(packageName)) {
                continue;
            }

            long referenceTs = usageStats.getLastTimeUsed();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                referenceTs = Math.max(referenceTs, usageStats.getLastTimeVisible());
                referenceTs = Math.max(referenceTs, usageStats.getLastTimeForegroundServiceUsed());
            }

            if (referenceTs <= 0L || (now - referenceTs) > lookbackMs) {
                continue;
            }

            mergeAppScore(scoreByPackage, packageName, 1_900_000L + referenceTs);
        }
    }

    private static void collectAggregatedUsageStats(
            @NonNull Context context,
            @NonNull Map<String, Long> scoreByPackage,
            long lookbackMs
    ) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long begin = Math.max(0L, now - lookbackMs);
        Map<String, UsageStats> aggregated = usageStatsManager.queryAndAggregateUsageStats(begin, now);
        if (aggregated == null || aggregated.isEmpty()) {
            return;
        }

        for (Map.Entry<String, UsageStats> entry : aggregated.entrySet()) {
            String packageName = entry.getKey();
            UsageStats usageStats = entry.getValue();
            if (TextUtils.isEmpty(packageName) || usageStats == null) {
                continue;
            }

            long referenceTs = usageStats.getLastTimeUsed();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                referenceTs = Math.max(referenceTs, usageStats.getLastTimeVisible());
                referenceTs = Math.max(referenceTs, usageStats.getLastTimeForegroundServiceUsed());
            }

            if (referenceTs <= 0L || (now - referenceTs) > lookbackMs) {
                continue;
            }

            mergeAppScore(scoreByPackage, packageName, 1_850_000L + referenceTs);
        }
    }

    private static boolean hasUsageStatsAccess(@NonNull Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOpsManager == null) {
            return false;
        }

        int mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private static void mergeAppScore(@NonNull Map<String, Long> scoreByPackage, @Nullable String packageName, long score) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        Long previous = scoreByPackage.get(packageName);
        if (previous == null || score > previous) {
            scoreByPackage.put(packageName, score);
        }
    }

    private static boolean isAppsSuggestionCandidate(
            @NonNull PackageManager packageManager,
            @Nullable String packageName,
            @NonNull String ownPackage
    ) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        if (ownPackage.equals(packageName)) {
            return false;
        }

        if (packageName.equals("android")
                || packageName.startsWith("com.android.settings")
                || packageName.startsWith("com.android.systemui")
                || packageName.startsWith("com.google.android.permissioncontroller")) {
            return false;
        }

        if (packageManager.getLaunchIntentForPackage(packageName) == null) {
            return false;
        }

        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystem = (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            return !isSystem || isUpdatedSystem;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    @NonNull
    private static String resolveAppLabel(@NonNull PackageManager packageManager, @NonNull String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence labelValue = packageManager.getApplicationLabel(appInfo);
            if (labelValue != null) {
                String label = labelValue.toString().trim();
                if (!TextUtils.isEmpty(label)) {
                    return label;
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Sin label, devolvemos package fallback.
        }
        return packageName;
    }

    @NonNull
    private static String formatAppList(@NonNull List<String> appLabels) {
        if (appLabels.isEmpty()) {
            return "";
        }
        if (appLabels.size() == 1) {
            return appLabels.get(0);
        }
        if (appLabels.size() == 2) {
            return appLabels.get(0) + " y " + appLabels.get(1);
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < appLabels.size(); i++) {
            if (i > 0) {
                if (i == appLabels.size() - 1) {
                    builder.append(" y ");
                } else {
                    builder.append(", ");
                }
            }
            builder.append(appLabels.get(i));
        }
        return builder.toString();
    }

    private static final class ActiveAppsSnapshot {
        final List<String> topAppLabels;
        final int activeAppCount;
        final boolean hasUsageAccess;

        ActiveAppsSnapshot(@NonNull List<String> topAppLabels, int activeAppCount, boolean hasUsageAccess) {
            this.topAppLabels = new ArrayList<>(topAppLabels);
            this.activeAppCount = Math.max(0, activeAppCount);
            this.hasUsageAccess = hasUsageAccess;
        }
    }

    private static int readRamPercent(@NonNull Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return 0;
        }

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        long total = Math.max(1L, memoryInfo.totalMem);
        long used = Math.max(0L, total - memoryInfo.availMem);
        int percent = (int) Math.round((used * 100d) / total);
        return Math.max(0, Math.min(100, percent));
    }

    private static float readBatteryTempC(@NonNull Context context) {
        try {
            android.content.Intent batteryIntent = context.registerReceiver(
                    null,
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            );
            if (batteryIntent == null) {
                return 0f;
            }
            int tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (tempTenths == Integer.MIN_VALUE || tempTenths <= 0) {
                return 0f;
            }
            return tempTenths / 10f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private static boolean isSmartSuggestionsEnabled(@NonNull Context context) {
        SharedPreferences settingsPrefs = context.getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE);
        return settingsPrefs.getBoolean(
                CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED,
                settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true)
        );
    }
}
