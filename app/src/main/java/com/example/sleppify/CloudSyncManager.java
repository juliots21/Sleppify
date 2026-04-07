package com.example.sleppify;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.WorkManager;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CloudSyncManager {

    private static final String TAG = "CloudSyncManager";

    public interface SyncCallback {
        void onComplete(boolean success, @Nullable String message);
    }

    public interface SyncStateListener {
        void onSyncStateChanged(boolean syncing);
    }

    public static final String PREFS_SETTINGS = "sleppify_settings";
    public static final String PREFS_AGENDA = "sleppify_agenda";
    public static final String KEY_AI_SHIFT_ENABLED = "ai_shift_enabled";
    public static final String KEY_SMART_SUGGESTIONS_ENABLED = "smart_suggestions_enabled";
    public static final String KEY_DEFAULT_DURATION_MINUTES = "default_duration_minutes";
    public static final String KEY_NOTIFICATION_LEAD_MINUTES = "notification_lead_minutes";
    public static final String KEY_DAILY_SUMMARY_INTERVAL_HOURS = "daily_summary_interval_hours";
    public static final String KEY_APPS_TURBO_MODE = "apps_stop_turbo_mode";
    public static final String KEY_APPS_SHOW_SYSTEM_INFO = "apps_show_system_info";
    public static final String KEY_APPS_WHITELIST_PACKAGES = "apps_whitelist_packages";
    public static final String KEY_PLAYER_VIDEO_MODE_ENABLED = "player_video_mode_enabled";
    public static final String KEY_AGENDA_JSON = "agenda_json";

    private static final String USERS_COLLECTION = "users";
    private static final String APP_SCOPE_COLLECTION = "sleppify";
    private static final String DOC_EQ = "eq";
    private static final String DOC_SETTINGS = "settings";
    private static final String DOC_AGENDA = "agenda";

    private static final String FIELD_PREFS = "prefs";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_AGENDA_JSON = "agendaJson";
    private static final String OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME = "offline_playlist_queue";
    private static final String LEGACY_SELECTED_PRESET = "selected_preset";
    private static final String LEGACY_ACTIVE_PROFILE_ID = "active_profile_id";
    private static final String LEGACY_DEVICE_PROFILE_PREFIX = "device_profile_";
    private static final String LEGACY_BAND_PREFIX = "band_db_";
    private static final String LEGACY_USER_PRESET_BAND_PREFIX = "user_preset_band_";
    private static final String LEGACY_AI_EQ_NEXT_AT_PREFIX = "ai_eq_next_at_";
    private static final String LEGACY_AI_EQ_LAST_PROCESS_SESSION_PREFIX = "ai_eq_last_process_session_";
    private static final String LEGACY_AI_EQ_DISMISSED_SESSION_PREFIX = "ai_eq_dismissed_session_";
    private static final String LEGACY_SCHEDULE_AI_NEXT_AT = "schedule_ai_next_at";
    private static final String LEGACY_SCHEDULE_AI_LAST_PROCESS_SESSION = "schedule_ai_last_process_session";
    private static final String LEGACY_APPS_AI_NEXT_AT = "apps_ai_next_at";
    private static final String LEGACY_APPS_AI_LAST_PROCESS_SESSION = "apps_ai_last_process_session";
    private static final String LEGACY_SCHEDULE_AI_ACCEPT_COUNT = "schedule_ai_accept_count";
    private static final String LEGACY_SCHEDULE_AI_DISMISS_COUNT = "schedule_ai_dismiss_count";
    private static final String LEGACY_SCHEDULE_AI_OPEN_COUNT = "schedule_ai_open_count";
    private static final String LEGACY_APPS_AI_ACCEPT_COUNT = "apps_ai_accept_count";
    private static final String LEGACY_APPS_AI_DISMISS_COUNT = "apps_ai_dismiss_count";

    private static final long DEBOUNCE_MS = 650L;
    private static final long SYNC_STUCK_TIMEOUT_MS = 16000L;
    private static final long INITIAL_HYDRATION_RETRY_BASE_MS = 3000L;
    private static final long INITIAL_HYDRATION_RETRY_MAX_MS = 15000L;
    private static final long UPLOAD_RETRY_BASE_MS = 2500L;
    private static final long UPLOAD_RETRY_MAX_MS = 30000L;
    private static final int UPLOAD_RETRY_MAX_ATTEMPTS = 80;

    private static volatile CloudSyncManager instance;

    private final Context appContext;
    private final FirebaseFirestore firestore;
    private final SharedPreferences eqPrefs;
    private final SharedPreferences settingsPrefs;
    private final SharedPreferences agendaPrefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    private String activeUserId;

    private boolean listenersRegistered;
    private boolean suppressEqSync;
    private boolean suppressSettingsSync;
    private boolean suppressAgendaSync;
    private boolean initialHydrationInProgress;

    private final Object syncStateLock = new Object();
    private int activeNetworkSyncCount;
    @Nullable
    private SyncStateListener syncStateListener;
    private final Runnable syncStuckWatchdogRunnable = this::forceClearSyncStateIfStuck;

    @Nullable
    private SharedPreferences.OnSharedPreferenceChangeListener eqListener;
    @Nullable
    private SharedPreferences.OnSharedPreferenceChangeListener settingsListener;
    @Nullable
    private SharedPreferences.OnSharedPreferenceChangeListener agendaListener;

    private final Runnable eqSyncRunnable = this::uploadEqPreferences;
    private final Runnable settingsSyncRunnable = this::uploadSettingsPreferences;
    private final Runnable agendaSyncRunnable = this::uploadAgendaJson;
    private final Runnable eqUploadRetryRunnable = this::uploadEqPreferences;
    private final Runnable settingsUploadRetryRunnable = this::uploadSettingsPreferences;
    private final Runnable agendaUploadRetryRunnable = this::uploadAgendaJson;

    private int eqUploadRetryAttempt;
    private int settingsUploadRetryAttempt;
    private int agendaUploadRetryAttempt;

    private CloudSyncManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        firestore = FirebaseFirestore.getInstance();
        eqPrefs = appContext.getSharedPreferences(AudioEffectsService.PREFS_NAME, Context.MODE_PRIVATE);
        settingsPrefs = appContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        agendaPrefs = appContext.getSharedPreferences(PREFS_AGENDA, Context.MODE_PRIVATE);
        ensureEqDefaults();
        ensureSettingsDefaults();
        ensureAgendaDefaults();
        registerPreferenceListenersIfNeeded();
    }

    @NonNull
    public static CloudSyncManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (CloudSyncManager.class) {
                if (instance == null) {
                    instance = new CloudSyncManager(context);
                }
            }
        }
        return instance;
    }

    private void ensureEqDefaults() {
        SharedPreferences.Editor editor = null;
        if (!eqPrefs.contains(AudioEffectsService.KEY_ENABLED)) {
            if (editor == null) {
                editor = eqPrefs.edit();
            }
            editor.putBoolean(AudioEffectsService.KEY_ENABLED, false);
        }
        if (!eqPrefs.contains(AudioEffectsService.KEY_SPATIAL_ENABLED)) {
            if (editor == null) {
                editor = eqPrefs.edit();
            }
            editor.putBoolean(AudioEffectsService.KEY_SPATIAL_ENABLED, false);
        }
        if (!eqPrefs.contains(AudioEffectsService.KEY_SPATIAL_STRENGTH)) {
            if (editor == null) {
                editor = eqPrefs.edit();
            }
            editor.putInt(AudioEffectsService.KEY_SPATIAL_STRENGTH, AudioEffectsService.SPATIAL_STRENGTH_DEFAULT);
        }
        if (!eqPrefs.contains(AudioEffectsService.KEY_REVERB_LEVEL)) {
            if (editor == null) {
                editor = eqPrefs.edit();
            }
            editor.putInt(AudioEffectsService.KEY_REVERB_LEVEL, AudioEffectsService.REVERB_LEVEL_OFF);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private void ensureSettingsDefaults() {
        SharedPreferences.Editor editor = null;
        if (!settingsPrefs.contains(KEY_AI_SHIFT_ENABLED)) {
            if (editor == null) {
                editor = settingsPrefs.edit();
            }
            editor.putBoolean(KEY_AI_SHIFT_ENABLED, true);
        }
        if (!settingsPrefs.contains(KEY_SMART_SUGGESTIONS_ENABLED)) {
            if (editor == null) {
                editor = settingsPrefs.edit();
            }
            editor.putBoolean(KEY_SMART_SUGGESTIONS_ENABLED, true);
        }
        if (!settingsPrefs.contains(KEY_DEFAULT_DURATION_MINUTES)) {
            if (editor == null) {
                editor = settingsPrefs.edit();
            }
            editor.putInt(KEY_DEFAULT_DURATION_MINUTES, 8 * 60 + 15);
        }
        if (!settingsPrefs.contains(KEY_NOTIFICATION_LEAD_MINUTES)) {
            if (editor == null) {
                editor = settingsPrefs.edit();
            }
            editor.putInt(KEY_NOTIFICATION_LEAD_MINUTES, 1);
        }
        if (!settingsPrefs.contains(KEY_DAILY_SUMMARY_INTERVAL_HOURS)) {
            if (editor == null) {
                editor = settingsPrefs.edit();
            }
            editor.putInt(KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2);
        }
        if (!settingsPrefs.contains(KEY_APPS_TURBO_MODE)) {
            if (editor == null) {
                editor = settingsPrefs.edit();
            }
            editor.putBoolean(KEY_APPS_TURBO_MODE, false);
        }
        if (!settingsPrefs.contains(KEY_APPS_SHOW_SYSTEM_INFO)) {
            if (editor == null) {
                editor = settingsPrefs.edit();
            }
            editor.putBoolean(KEY_APPS_SHOW_SYSTEM_INFO, true);
        }
        if (!settingsPrefs.contains(KEY_APPS_WHITELIST_PACKAGES)) {
            if (editor == null) {
                editor = settingsPrefs.edit();
            }
            editor.putStringSet(KEY_APPS_WHITELIST_PACKAGES, new java.util.HashSet<>());
        }
        if (!settingsPrefs.contains(KEY_PLAYER_VIDEO_MODE_ENABLED)) {
            if (editor == null) {
                editor = settingsPrefs.edit();
            }
            editor.putBoolean(KEY_PLAYER_VIDEO_MODE_ENABLED, false);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private void ensureAgendaDefaults() {
        if (!agendaPrefs.contains(KEY_AGENDA_JSON)) {
            agendaPrefs.edit().putString(KEY_AGENDA_JSON, "{}").apply();
        }
    }

    private void registerPreferenceListenersIfNeeded() {
        if (listenersRegistered) {
            return;
        }

        eqListener = (sharedPreferences, key) -> {
            if (suppressEqSync || initialHydrationInProgress || TextUtils.isEmpty(activeUserId)) {
                return;
            }
            handler.removeCallbacks(eqSyncRunnable);
            handler.postDelayed(eqSyncRunnable, DEBOUNCE_MS);
        };

        settingsListener = (sharedPreferences, key) -> {
            if (suppressSettingsSync || initialHydrationInProgress || TextUtils.isEmpty(activeUserId)) {
                return;
            }
            handler.removeCallbacks(settingsSyncRunnable);
            handler.postDelayed(settingsSyncRunnable, DEBOUNCE_MS);
        };

        agendaListener = (sharedPreferences, key) -> {
            if (suppressAgendaSync || initialHydrationInProgress || TextUtils.isEmpty(activeUserId)) {
                return;
            }
            handler.removeCallbacks(agendaSyncRunnable);
            handler.postDelayed(agendaSyncRunnable, DEBOUNCE_MS);
        };

        eqPrefs.registerOnSharedPreferenceChangeListener(eqListener);
        settingsPrefs.registerOnSharedPreferenceChangeListener(settingsListener);
        agendaPrefs.registerOnSharedPreferenceChangeListener(agendaListener);
        listenersRegistered = true;
    }

    public boolean isCloudEnabledForCurrentUser() {
        return !TextUtils.isEmpty(activeUserId);
    }

    public void setSyncStateListener(@Nullable SyncStateListener listener) {
        boolean syncing;
        synchronized (syncStateLock) {
            syncStateListener = listener;
            syncing = activeNetworkSyncCount > 0;
        }
        dispatchSyncState(syncing);
    }

    public void onUserSignedIn(@NonNull String userId, @NonNull SyncCallback callback) {
        activeUserId = userId;
        handler.removeCallbacks(eqSyncRunnable);
        handler.removeCallbacks(settingsSyncRunnable);
        handler.removeCallbacks(agendaSyncRunnable);
        clearUploadRetryCallbacks();
        resetUploadRetryAttempts();
        initialHydrationInProgress = true;
        ensureEqDefaults();
        ensureSettingsDefaults();
        ensureAgendaDefaults();

        startInitialHydrationAttempt(userId, 0, callback);
    }

    public void syncNow(@NonNull SyncCallback callback) {
        String uid = activeUserId;
        if (TextUtils.isEmpty(uid)) {
            callback.onComplete(false, "No hay sesion activa para sincronizar.");
            return;
        }
        syncNow(uid, callback);
    }

    public void syncNow(@NonNull String userId, @NonNull SyncCallback callback) {
        if (TextUtils.isEmpty(userId)) {
            callback.onComplete(false, "No se pudo resolver el usuario para sincronizar.");
            return;
        }

        activeUserId = userId;
        handler.removeCallbacks(eqSyncRunnable);
        handler.removeCallbacks(settingsSyncRunnable);
        handler.removeCallbacks(agendaSyncRunnable);
        clearUploadRetryCallbacks();
        resetUploadRetryAttempts();
        initialHydrationInProgress = true;
        ensureEqDefaults();
        ensureSettingsDefaults();
        ensureAgendaDefaults();

        startInitialHydrationAttempt(userId, 0, callback);
    }

    public void syncSettingsNowIfSignedIn() {
        if (TextUtils.isEmpty(activeUserId) || initialHydrationInProgress) {
            return;
        }
        handler.removeCallbacks(settingsSyncRunnable);
        uploadSettingsPreferences();
    }

    private void startInitialHydrationAttempt(
            @NonNull String userId,
            int attempt,
            @NonNull SyncCallback callback
    ) {
        if (!TextUtils.equals(activeUserId, userId)) {
            return;
        }

        PendingSync pending = new PendingSync(3, (success, message) -> {
            if (!TextUtils.equals(activeUserId, userId)) {
                return;
            }

            if (!success && isRecoverableSyncMessage(message)) {
                int nextAttempt = attempt + 1;
                long retryStep = Math.max(1, Math.min(nextAttempt, 6));
                long delayMs = Math.min(
                        INITIAL_HYDRATION_RETRY_MAX_MS,
                        INITIAL_HYDRATION_RETRY_BASE_MS * retryStep
                );
                handler.postDelayed(
                        () -> startInitialHydrationAttempt(userId, nextAttempt, callback),
                        delayMs
                );
                return;
            }

            initialHydrationInProgress = false;
            callback.onComplete(success, message);
        });
        syncEqFromCloudThenLocal(pending);
        syncSettingsFromCloudThenLocal(pending);
        syncAgendaFromCloudThenLocal(pending);
    }

    public void onUserSignedOut() {
        activeUserId = null;
        initialHydrationInProgress = false;
        handler.removeCallbacks(eqSyncRunnable);
        handler.removeCallbacks(settingsSyncRunnable);
        handler.removeCallbacks(agendaSyncRunnable);
        clearUploadRetryCallbacks();
        resetUploadRetryAttempts();
        pauseUserScopedBackgroundWork();
        forceClearSyncState();
    }

    public void pauseUserScopedBackgroundWork() {
        try {
            WorkManager manager = WorkManager.getInstance(appContext);
            manager.cancelUniqueWork(OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME);
            manager.cancelAllWorkByTag(OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME);
        } catch (Throwable ignored) {
        }
    }

    public void deleteUserDataFromCloud(@NonNull String userId, @NonNull SyncCallback callback) {
        if (TextUtils.isEmpty(userId)) {
            callback.onComplete(false, "No se encontro usuario para eliminar datos.");
            return;
        }

        beginNetworkSync();

        DocumentReference userDoc = firestore.collection(USERS_COLLECTION).document(userId);
        userDoc.collection(APP_SCOPE_COLLECTION)
                .get(Source.SERVER)
                .addOnSuccessListener(scopeDocs -> deleteScopeDocumentsThenParent(userId, scopeDocs, callback))
                .addOnFailureListener(listError -> {
                    // Fallback: borra docs conocidos aunque no sea posible listar la coleccion completa.
                    WriteBatch fallbackBatch = firestore.batch();
                    fallbackBatch.delete(eqDoc(userId));
                    fallbackBatch.delete(settingsDoc(userId));
                    fallbackBatch.delete(agendaDoc(userId));
                    fallbackBatch.delete(userScope(userId));
                    fallbackBatch.commit()
                            .addOnSuccessListener(unused -> deleteUserDocBestEffort(userId, callback))
                            .addOnFailureListener(e -> {
                                endNetworkSync();
                                callback.onComplete(false, toUserFacingSyncError(e));
                            });
                });
    }

    private void deleteScopeDocumentsThenParent(
            @NonNull String userId,
            @NonNull QuerySnapshot scopeDocs,
            @NonNull SyncCallback callback
    ) {
        WriteBatch batch = firestore.batch();
        int deleteCount = 0;

        for (DocumentSnapshot doc : scopeDocs.getDocuments()) {
            batch.delete(doc.getReference());
            deleteCount++;
        }

        if (deleteCount == 0) {
            batch.delete(eqDoc(userId));
            batch.delete(settingsDoc(userId));
            batch.delete(agendaDoc(userId));
            batch.delete(userScope(userId));
        }

        batch.commit()
                .addOnSuccessListener(unused -> deleteUserDocBestEffort(userId, callback))
                .addOnFailureListener(e -> {
                    endNetworkSync();
                    callback.onComplete(false, toUserFacingSyncError(e));
                });
    }

    private void deleteUserDocBestEffort(@NonNull String userId, @NonNull SyncCallback callback) {
        DocumentReference userDoc = firestore.collection(USERS_COLLECTION).document(userId);
        userDoc.delete()
                .addOnSuccessListener(ignore -> {
                    endNetworkSync();
                    callback.onComplete(true, null);
                })
                .addOnFailureListener(parentDeleteError -> {
                    if (parentDeleteError instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) parentDeleteError).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        endNetworkSync();
                        callback.onComplete(true, null);
                        return;
                    }

                    endNetworkSync();
                    callback.onComplete(false, toUserFacingSyncError(parentDeleteError));
                });
    }

    public void clearLocalUserData() {
        suppressEqSync = true;
        suppressSettingsSync = true;
        suppressAgendaSync = true;

        try {
            eqPrefs.edit().clear().apply();
            settingsPrefs.edit().clear().apply();
            agendaPrefs.edit().clear().apply();
        } finally {
            suppressEqSync = false;
            suppressSettingsSync = false;
            suppressAgendaSync = false;
        }

        ensureEqDefaults();
        ensureSettingsDefaults();
        ensureAgendaDefaults();
    }

    public void clearLocalUserDataCompletely() {
        onUserSignedOut();

        try {
            WorkManager.getInstance(appContext).cancelAllWork();
        } catch (Throwable ignored) {
        }

        try {
            NotificationManagerCompat.from(appContext).cancelAll();
        } catch (Throwable ignored) {
        }

        clearAllSharedPreferencesFiles();
        clearAllDatabases();

        clearDirectoryContents(appContext.getFilesDir());
        clearDirectoryContents(appContext.getCacheDir());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            clearDirectoryContents(appContext.getNoBackupFilesDir());
            clearDirectoryContents(appContext.getCodeCacheDir());
        }

        File[] externalFiles = appContext.getExternalFilesDirs(null);
        if (externalFiles != null) {
            for (File dir : externalFiles) {
                clearDirectoryContents(dir);
            }
        }

        File[] externalCaches = appContext.getExternalCacheDirs();
        if (externalCaches != null) {
            for (File dir : externalCaches) {
                clearDirectoryContents(dir);
            }
        }
    }

    private void clearAllSharedPreferencesFiles() {
        try {
            File sharedPrefsDir = new File(appContext.getApplicationInfo().dataDir, "shared_prefs");
            File[] prefFiles = sharedPrefsDir.listFiles();
            if (prefFiles == null) {
                return;
            }

            for (File prefFile : prefFiles) {
                if (prefFile == null) {
                    continue;
                }
                String fileName = prefFile.getName();
                if (TextUtils.isEmpty(fileName) || !fileName.endsWith(".xml")) {
                    continue;
                }

                String prefName = fileName.substring(0, fileName.length() - 4);
                try {
                    appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .commit();
                } catch (Throwable ignored) {
                }
                // Best-effort physical cleanup after clear.
                try {
                    //noinspection ResultOfMethodCallIgnored
                    prefFile.delete();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void clearAllDatabases() {
        try {
            String[] databaseNames = appContext.databaseList();
            if (databaseNames == null) {
                return;
            }

            for (String dbName : databaseNames) {
                if (TextUtils.isEmpty(dbName)) {
                    continue;
                }
                try {
                    appContext.deleteDatabase(dbName);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void clearDirectoryContents(@Nullable File rootDir) {
        if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory()) {
            return;
        }

        File[] children = rootDir.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            deleteRecursively(child);
        }
    }

    private void deleteRecursively(@Nullable File node) {
        if (node == null || !node.exists()) {
            return;
        }

        if (node.isDirectory()) {
            File[] children = node.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        try {
            //noinspection ResultOfMethodCallIgnored
            node.delete();
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    public String getLocalAgendaJson() {
        String json = agendaPrefs.getString(KEY_AGENDA_JSON, "{}");
        return TextUtils.isEmpty(json) ? "{}" : json;
    }

    public void saveAgendaJsonLocally(@NonNull String agendaJson) {
        agendaPrefs.edit().putString(KEY_AGENDA_JSON, agendaJson).apply();
    }

    public void syncAgendaJson(@NonNull String agendaJson) {
        saveAgendaJsonLocally(agendaJson);
        uploadAgendaJson();
    }

    public void refreshAgendaFromCloud(@NonNull SyncCallback callback) {
        String uid = activeUserId;
        if (TextUtils.isEmpty(uid)) {
            callback.onComplete(false, "No hay sesion activa para agenda en la nube.");
            return;
        }

        beginNetworkSync();

        agendaDoc(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        endNetworkSync();
                        callback.onComplete(true, null);
                        return;
                    }

                    if (applyEncodedPrefsFromSnapshot(agendaPrefs, snapshot)) {
                        ensureAgendaDefaults();
                        endNetworkSync();
                        callback.onComplete(true, null);
                        return;
                    }

                    String json = snapshot.getString(FIELD_AGENDA_JSON);
                    if (!TextUtils.isEmpty(json)) {
                        agendaPrefs.edit().putString(KEY_AGENDA_JSON, json).apply();
                    }
                    ensureAgendaDefaults();
                    endNetworkSync();
                    callback.onComplete(true, null);
                })
                .addOnFailureListener(e -> {
                    endNetworkSync();
                    callback.onComplete(false, e.getMessage());
                });
    }

    private void syncEqFromCloudThenLocal(@NonNull PendingSync pending) {
        String uid = activeUserId;
        if (TextUtils.isEmpty(uid)) {
            pending.finish(true, null);
            return;
        }

        beginNetworkSync();

        eqDoc(uid).get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    handleEqSyncSnapshot(snapshot, true, null, pending);
                })
                .addOnFailureListener(serverError -> {
                    eqDoc(uid).get(Source.CACHE)
                            .addOnSuccessListener(snapshot ->
                                    handleEqSyncSnapshot(snapshot, false, serverError, pending)
                            )
                            .addOnFailureListener(cacheError -> {
                                endNetworkSync();
                                pending.finish(false, toUserFacingSyncError(serverError));
                            });
                });
    }

    private void syncSettingsFromCloudThenLocal(@NonNull PendingSync pending) {
        String uid = activeUserId;
        if (TextUtils.isEmpty(uid)) {
            pending.finish(true, null);
            return;
        }

        beginNetworkSync();

        settingsDoc(uid).get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    handleSettingsSyncSnapshot(snapshot, true, null, pending);
                })
                .addOnFailureListener(serverError -> {
                    settingsDoc(uid).get(Source.CACHE)
                            .addOnSuccessListener(snapshot ->
                                    handleSettingsSyncSnapshot(snapshot, false, serverError, pending)
                            )
                            .addOnFailureListener(cacheError -> {
                                endNetworkSync();
                                pending.finish(false, toUserFacingSyncError(serverError));
                            });
                });
    }

    private void syncAgendaFromCloudThenLocal(@NonNull PendingSync pending) {
        String uid = activeUserId;
        if (TextUtils.isEmpty(uid)) {
            pending.finish(true, null);
            return;
        }

        beginNetworkSync();

        agendaDoc(uid).get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    handleAgendaSyncSnapshot(snapshot, true, null, pending);
                })
                .addOnFailureListener(serverError -> {
                    agendaDoc(uid).get(Source.CACHE)
                            .addOnSuccessListener(snapshot ->
                                    handleAgendaSyncSnapshot(snapshot, false, serverError, pending)
                            )
                            .addOnFailureListener(cacheError -> {
                                endNetworkSync();
                                pending.finish(false, toUserFacingSyncError(serverError));
                            });
                });
    }

    private void handleEqSyncSnapshot(
            @NonNull DocumentSnapshot snapshot,
            boolean fromServer,
            @Nullable Throwable serverError,
            @NonNull PendingSync pending
    ) {
        if (!snapshot.exists()) {
            if (fromServer) {
                uploadEqPreferences();
                endNetworkSync();
                pending.finish(true, null);
            } else {
                endNetworkSync();
                pending.finish(false, toUserFacingSyncError(serverError));
            }
            return;
        }

        if (applyEncodedPrefsFromSnapshot(eqPrefs, snapshot)) {
            ensureEqDefaults();
            endNetworkSync();
            pending.finish(true, null);
            return;
        }

        endNetworkSync();
        if (fromServer) {
            pending.finish(false, "No se pudo leer la configuracion EQ en la nube.");
        } else {
            pending.finish(false, toUserFacingSyncError(serverError));
        }
    }

    private void handleSettingsSyncSnapshot(
            @NonNull DocumentSnapshot snapshot,
            boolean fromServer,
            @Nullable Throwable serverError,
            @NonNull PendingSync pending
    ) {
        if (!snapshot.exists()) {
            if (fromServer) {
                uploadSettingsPreferences();
                endNetworkSync();
                pending.finish(true, null);
            } else {
                endNetworkSync();
                pending.finish(false, toUserFacingSyncError(serverError));
            }
            return;
        }

        if (applyEncodedPrefsFromSnapshot(settingsPrefs, snapshot)) {
            ensureSettingsDefaults();
            endNetworkSync();
            pending.finish(true, null);
            return;
        }

        endNetworkSync();
        if (fromServer) {
            pending.finish(false, "No se pudo leer los ajustes en la nube.");
        } else {
            pending.finish(false, toUserFacingSyncError(serverError));
        }
    }

    private void handleAgendaSyncSnapshot(
            @NonNull DocumentSnapshot snapshot,
            boolean fromServer,
            @Nullable Throwable serverError,
            @NonNull PendingSync pending
    ) {
        if (!snapshot.exists()) {
            if (fromServer) {
                uploadAgendaJson();
                endNetworkSync();
                pending.finish(true, null);
            } else {
                endNetworkSync();
                pending.finish(false, toUserFacingSyncError(serverError));
            }
            return;
        }

        if (applyEncodedPrefsFromSnapshot(agendaPrefs, snapshot)) {
            ensureAgendaDefaults();
            endNetworkSync();
            pending.finish(true, null);
            return;
        }

        String cloudJson = snapshot.getString(FIELD_AGENDA_JSON);
        if (!TextUtils.isEmpty(cloudJson)) {
            agendaPrefs.edit().putString(KEY_AGENDA_JSON, cloudJson).apply();
            ensureAgendaDefaults();
            endNetworkSync();
            pending.finish(true, null);
            return;
        }

        if (fromServer) {
            uploadAgendaJson();
            endNetworkSync();
            pending.finish(true, null);
            return;
        }

        endNetworkSync();
        pending.finish(false, toUserFacingSyncError(serverError));
    }

    @NonNull
    private String toUserFacingSyncError(@Nullable Throwable throwable) {
        if (throwable instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) throwable).getCode();
            if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                return "Cloud Firestore no esta inicializado en Firebase. Crea la base de datos Firestore en modo nativo.";
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Reglas de Firestore bloquean la sincronizacion. Verifica permisos para users/{uid}/sleppify.";
            }
            if (code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                return "Sesion Firebase invalida. Cierra sesion e inicia nuevamente.";
            }
        }

        if (isRecoverableSyncThrowable(throwable)) {
            return "Sin conexion con la nube por ahora. Reintentando automaticamente.";
        }
        if (throwable == null || TextUtils.isEmpty(throwable.getMessage())) {
            return "No se pudo sincronizar en la nube.";
        }
        return throwable.getMessage();
    }

    private boolean isRecoverableSyncMessage(@Nullable String message) {
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("offline")
                || normalized.contains("sin conexion")
                || normalized.contains("network")
                || normalized.contains("unavailable")
                || normalized.contains("tempor")
                || normalized.contains("timeout")
                || normalized.contains("timed out");
    }

    private boolean isRecoverableSyncThrowable(@Nullable Throwable throwable) {
        if (throwable instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) throwable).getCode();
            return code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
                    || code == FirebaseFirestoreException.Code.ABORTED
                    || code == FirebaseFirestoreException.Code.CANCELLED;
        }

        if (throwable == null || TextUtils.isEmpty(throwable.getMessage())) {
            return false;
        }
        return isRecoverableSyncMessage(throwable.getMessage());
    }

    private void uploadAllBucketsNow() {
        if (TextUtils.isEmpty(activeUserId)) {
            return;
        }

        handler.removeCallbacks(eqSyncRunnable);
        handler.removeCallbacks(settingsSyncRunnable);
        handler.removeCallbacks(agendaSyncRunnable);
        uploadEqPreferences();
        uploadSettingsPreferences();
        uploadAgendaJson();
    }

    private void uploadEqPreferences() {
        String uid = activeUserId;
        if (TextUtils.isEmpty(uid)) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put(FIELD_PREFS, encodePreferences(eqPrefs));
        payload.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        beginNetworkSync();
        eqDoc(uid).set(payload)
            .addOnSuccessListener(unused -> {
                eqUploadRetryAttempt = 0;
                handler.removeCallbacks(eqUploadRetryRunnable);
                endNetworkSync();
            })
            .addOnFailureListener(e -> {
                endNetworkSync();
                Log.w(TAG, "Fallo subiendo preferencias de EQ", e);
                scheduleEqUploadRetryIfNeeded(e);
            });
    }

    private void uploadSettingsPreferences() {
        String uid = activeUserId;
        if (TextUtils.isEmpty(uid)) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put(FIELD_PREFS, encodePreferences(settingsPrefs));
        payload.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        beginNetworkSync();
        settingsDoc(uid).set(payload)
            .addOnSuccessListener(unused -> {
                settingsUploadRetryAttempt = 0;
                handler.removeCallbacks(settingsUploadRetryRunnable);
                endNetworkSync();
            })
            .addOnFailureListener(e -> {
                endNetworkSync();
                Log.w(TAG, "Fallo subiendo preferencias de ajustes", e);
                scheduleSettingsUploadRetryIfNeeded(e);
            });
    }

    private void uploadAgendaJson() {
        String uid = activeUserId;
        if (TextUtils.isEmpty(uid)) {
            return;
        }

        String agendaJson = agendaPrefs.getString(KEY_AGENDA_JSON, "{}");
        Map<String, Object> payload = new HashMap<>();
        payload.put(FIELD_PREFS, encodePreferences(agendaPrefs));
        payload.put(FIELD_AGENDA_JSON, TextUtils.isEmpty(agendaJson) ? "{}" : agendaJson);
        payload.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        beginNetworkSync();
        agendaDoc(uid).set(payload)
                .addOnSuccessListener(unused -> {
                    agendaUploadRetryAttempt = 0;
                    handler.removeCallbacks(agendaUploadRetryRunnable);
                    endNetworkSync();
                })
                .addOnFailureListener(e -> {
                    endNetworkSync();
                    Log.w(TAG, "Fallo subiendo agenda", e);
                    scheduleAgendaUploadRetryIfNeeded(e);
                });
    }

    private void clearUploadRetryCallbacks() {
        handler.removeCallbacks(eqUploadRetryRunnable);
        handler.removeCallbacks(settingsUploadRetryRunnable);
        handler.removeCallbacks(agendaUploadRetryRunnable);
    }

    private void resetUploadRetryAttempts() {
        eqUploadRetryAttempt = 0;
        settingsUploadRetryAttempt = 0;
        agendaUploadRetryAttempt = 0;
    }

    private void scheduleEqUploadRetryIfNeeded(@Nullable Throwable throwable) {
        if (!shouldRetryUpload(throwable) || TextUtils.isEmpty(activeUserId)) {
            return;
        }
        eqUploadRetryAttempt = Math.min(eqUploadRetryAttempt + 1, UPLOAD_RETRY_MAX_ATTEMPTS);
        long delayMs = computeUploadRetryDelayMs(eqUploadRetryAttempt);
        handler.removeCallbacks(eqUploadRetryRunnable);
        handler.postDelayed(eqUploadRetryRunnable, delayMs);
    }

    private void scheduleSettingsUploadRetryIfNeeded(@Nullable Throwable throwable) {
        if (!shouldRetryUpload(throwable) || TextUtils.isEmpty(activeUserId)) {
            return;
        }
        settingsUploadRetryAttempt = Math.min(settingsUploadRetryAttempt + 1, UPLOAD_RETRY_MAX_ATTEMPTS);
        long delayMs = computeUploadRetryDelayMs(settingsUploadRetryAttempt);
        handler.removeCallbacks(settingsUploadRetryRunnable);
        handler.postDelayed(settingsUploadRetryRunnable, delayMs);
    }

    private void scheduleAgendaUploadRetryIfNeeded(@Nullable Throwable throwable) {
        if (!shouldRetryUpload(throwable) || TextUtils.isEmpty(activeUserId)) {
            return;
        }
        agendaUploadRetryAttempt = Math.min(agendaUploadRetryAttempt + 1, UPLOAD_RETRY_MAX_ATTEMPTS);
        long delayMs = computeUploadRetryDelayMs(agendaUploadRetryAttempt);
        handler.removeCallbacks(agendaUploadRetryRunnable);
        handler.postDelayed(agendaUploadRetryRunnable, delayMs);
    }

    private long computeUploadRetryDelayMs(int attempt) {
        long boundedAttempt = Math.max(1, attempt);
        return Math.min(UPLOAD_RETRY_MAX_MS, UPLOAD_RETRY_BASE_MS * boundedAttempt);
    }

    private boolean shouldRetryUpload(@Nullable Throwable throwable) {
        if (throwable instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) throwable).getCode();
            return code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
                    || code == FirebaseFirestoreException.Code.ABORTED
                    || code == FirebaseFirestoreException.Code.CANCELLED
                    || code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED;
        }

        if (throwable == null || TextUtils.isEmpty(throwable.getMessage())) {
            return false;
        }

        String normalized = throwable.getMessage().toLowerCase();
        return normalized.contains("offline")
                || normalized.contains("network")
                || normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("unavailable");
    }

    private boolean applyEncodedPrefsFromSnapshot(
            @NonNull SharedPreferences targetPrefs,
            @NonNull DocumentSnapshot snapshot
    ) {
        Map<String, Object> encodedPrefs = extractPrefsMap(snapshot);
        if (encodedPrefs == null || encodedPrefs.isEmpty()) {
            return false;
        }

        boolean eqBucket = targetPrefs == eqPrefs;
        boolean settingsBucket = targetPrefs == settingsPrefs;
        boolean agendaBucket = targetPrefs == agendaPrefs;

        if (eqBucket) {
            suppressEqSync = true;
        } else if (settingsBucket) {
            suppressSettingsSync = true;
        } else if (agendaBucket) {
            suppressAgendaSync = true;
        }

        try {
            SharedPreferences.Editor editor = targetPrefs.edit();
            editor.clear();
            int decodedCount = decodePreferences(encodedPrefs, editor);
            if (decodedCount <= 0) {
                return false;
            }
            editor.apply();
            return true;
        } finally {
            if (eqBucket) {
                suppressEqSync = false;
            } else if (settingsBucket) {
                suppressSettingsSync = false;
            } else if (agendaBucket) {
                suppressAgendaSync = false;
            }
        }
    }

    @Nullable
    private Map<String, Object> extractPrefsMap(@NonNull DocumentSnapshot snapshot) {
        Object raw = snapshot.get(FIELD_PREFS);
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> encodedPrefs = (Map<String, Object>) raw;
            return encodedPrefs;
        }

        Map<String, Object> rootData = snapshot.getData();
        if (rootData == null || rootData.isEmpty()) {
            return null;
        }

        Map<String, Object> fallback = new HashMap<>(rootData);
        fallback.remove(FIELD_PREFS);
        fallback.remove(FIELD_UPDATED_AT);
        fallback.remove(FIELD_AGENDA_JSON);
        return fallback.isEmpty() ? null : fallback;
    }

    private void beginNetworkSync() {
        boolean shouldNotify;
        synchronized (syncStateLock) {
            activeNetworkSyncCount++;
            shouldNotify = activeNetworkSyncCount == 1;
        }
        handler.removeCallbacks(syncStuckWatchdogRunnable);
        handler.postDelayed(syncStuckWatchdogRunnable, SYNC_STUCK_TIMEOUT_MS);
        if (shouldNotify) {
            dispatchSyncState(true);
        }
    }

    private void endNetworkSync() {
        boolean shouldNotify;
        synchronized (syncStateLock) {
            activeNetworkSyncCount = Math.max(0, activeNetworkSyncCount - 1);
            shouldNotify = activeNetworkSyncCount == 0;
        }
        if (shouldNotify) {
            handler.removeCallbacks(syncStuckWatchdogRunnable);
        } else {
            handler.removeCallbacks(syncStuckWatchdogRunnable);
            handler.postDelayed(syncStuckWatchdogRunnable, SYNC_STUCK_TIMEOUT_MS);
        }
        if (shouldNotify) {
            dispatchSyncState(false);
        }
    }

    private void forceClearSyncStateIfStuck() {
        boolean shouldNotify;
        synchronized (syncStateLock) {
            shouldNotify = activeNetworkSyncCount > 0;
            activeNetworkSyncCount = 0;
        }
        if (shouldNotify) {
            dispatchSyncState(false);
        }
    }

    private void forceClearSyncState() {
        handler.removeCallbacks(syncStuckWatchdogRunnable);
        forceClearSyncStateIfStuck();
    }

    private void dispatchSyncState(boolean syncing) {
        SyncStateListener listenerSnapshot;
        synchronized (syncStateLock) {
            listenerSnapshot = syncStateListener;
        }
        if (listenerSnapshot == null) {
            return;
        }
        handler.post(() -> listenerSnapshot.onSyncStateChanged(syncing));
    }

    @NonNull
    private Map<String, Object> encodePreferences(@NonNull SharedPreferences prefs) {
        Map<String, ?> all = prefs.getAll();
        Map<String, Object> encoded = new HashMap<>();

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            Map<String, Object> wrapper = new HashMap<>();
            if (value instanceof Boolean) {
                wrapper.put("type", "bool");
                wrapper.put("value", value);
            } else if (value instanceof Integer) {
                wrapper.put("type", "int");
                wrapper.put("value", ((Integer) value).longValue());
            } else if (value instanceof Long) {
                wrapper.put("type", "long");
                wrapper.put("value", value);
            } else if (value instanceof Float) {
                wrapper.put("type", "float");
                wrapper.put("value", ((Float) value).doubleValue());
            } else if (value instanceof String) {
                wrapper.put("type", "string");
                wrapper.put("value", value);
            } else if (value instanceof java.util.Set) {
                @SuppressWarnings("unchecked")
                java.util.Set<String> set = (java.util.Set<String>) value;
                wrapper.put("type", "string_set");
                wrapper.put("value", new ArrayList<>(set));
            } else {
                continue;
            }
            encoded.put(key, wrapper);
        }

        return encoded;
    }

    private int decodePreferences(
            @NonNull Map<String, Object> encodedPrefs,
            @NonNull SharedPreferences.Editor editor
    ) {
        int decodedCount = 0;
        for (Map.Entry<String, Object> entry : encodedPrefs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (decodePreferenceEntry(key, value, editor)) {
                decodedCount++;
            }
        }
        return decodedCount;
    }

    private boolean decodePreferenceEntry(
            @NonNull String key,
            @Nullable Object value,
            @NonNull SharedPreferences.Editor editor
    ) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> wrapper = (Map<String, Object>) value;
            if (decodeWrappedPreference(key, wrapper, editor)) {
                return true;
            }
        }
        return decodeLegacyPreference(key, value, editor);
    }

    private boolean decodeWrappedPreference(
            @NonNull String key,
            @NonNull Map<String, Object> wrapper,
            @NonNull SharedPreferences.Editor editor
    ) {
        Object typeObj = wrapper.get("type");
        Object raw = wrapper.get("value");
        if (typeObj == null) {
            return decodeLegacyPreference(key, raw, editor);
        }

        String type = String.valueOf(typeObj);
        switch (type) {
            case "bool":
                if (raw instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) raw);
                    return true;
                }
                return false;
            case "int":
                if (raw instanceof Number) {
                    editor.putInt(key, ((Number) raw).intValue());
                    return true;
                }
                return false;
            case "long":
                if (raw instanceof Number) {
                    editor.putLong(key, ((Number) raw).longValue());
                    return true;
                }
                return false;
            case "float":
                if (raw instanceof Number) {
                    editor.putFloat(key, ((Number) raw).floatValue());
                    return true;
                }
                return false;
            case "string":
                if (raw != null) {
                    editor.putString(key, String.valueOf(raw));
                    return true;
                }
                return false;
            case "string_set":
                return decodeStringSetPreference(key, raw, editor);
            default:
                return decodeLegacyPreference(key, raw, editor);
        }
    }

    private boolean decodeLegacyPreference(
            @NonNull String key,
            @Nullable Object raw,
            @NonNull SharedPreferences.Editor editor
    ) {
        if (raw == null) {
            return false;
        }

        if (raw instanceof Boolean) {
            editor.putBoolean(key, (Boolean) raw);
            return true;
        }
        if (raw instanceof String) {
            editor.putString(key, (String) raw);
            return true;
        }
        if (raw instanceof Number) {
            Number numeric = (Number) raw;
            if (isLikelyFloatKey(key)) {
                editor.putFloat(key, numeric.floatValue());
                return true;
            }
            if (isLikelyIntKey(key)) {
                editor.putInt(key, numeric.intValue());
                return true;
            }
            if (isLikelyLongKey(key)) {
                editor.putLong(key, numeric.longValue());
                return true;
            }

            double asDouble = numeric.doubleValue();
            if (Math.floor(asDouble) != asDouble) {
                editor.putFloat(key, (float) asDouble);
                return true;
            }

            long asLong = numeric.longValue();
            if (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE) {
                editor.putInt(key, (int) asLong);
                return true;
            }
            editor.putLong(key, asLong);
            return true;
        }

        if (raw instanceof List || raw instanceof java.util.Set) {
            return decodeStringSetPreference(key, raw, editor);
        }

        return false;
    }

    private boolean decodeStringSetPreference(
            @NonNull String key,
            @Nullable Object raw,
            @NonNull SharedPreferences.Editor editor
    ) {
        List<?> values;
        if (raw instanceof List) {
            values = (List<?>) raw;
        } else if (raw instanceof java.util.Set) {
            values = new ArrayList<>((java.util.Set<?>) raw);
        } else {
            return false;
        }

        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (Object item : values) {
            if (item != null) {
                set.add(String.valueOf(item));
            }
        }
        editor.putStringSet(key, set);
        return true;
    }

    private boolean isLikelyFloatKey(@NonNull String key) {
        if (AudioEffectsService.KEY_BASS_DB.equals(key)
                || AudioEffectsService.KEY_BASS_FREQUENCY_HZ.equals(key)
                || key.startsWith(LEGACY_BAND_PREFIX)
                || key.startsWith(LEGACY_USER_PRESET_BAND_PREFIX)) {
            return true;
        }

        if (!key.startsWith(LEGACY_DEVICE_PROFILE_PREFIX)) {
            return false;
        }

        return key.endsWith("_" + AudioEffectsService.KEY_BASS_DB)
                || key.endsWith("_" + AudioEffectsService.KEY_BASS_FREQUENCY_HZ)
                || key.contains("_" + LEGACY_BAND_PREFIX);
    }

    private boolean isLikelyIntKey(@NonNull String key) {
        if (AudioEffectsService.KEY_SPATIAL_STRENGTH.equals(key)
                || AudioEffectsService.KEY_REVERB_LEVEL.equals(key)
                || AudioEffectsService.KEY_BASS_TYPE.equals(key)
                || KEY_DEFAULT_DURATION_MINUTES.equals(key)
                || KEY_NOTIFICATION_LEAD_MINUTES.equals(key)
                || KEY_DAILY_SUMMARY_INTERVAL_HOURS.equals(key)
                || LEGACY_SCHEDULE_AI_ACCEPT_COUNT.equals(key)
                || LEGACY_SCHEDULE_AI_DISMISS_COUNT.equals(key)
                || LEGACY_SCHEDULE_AI_OPEN_COUNT.equals(key)
                || LEGACY_APPS_AI_ACCEPT_COUNT.equals(key)
                || LEGACY_APPS_AI_DISMISS_COUNT.equals(key)) {
            return true;
        }

        return key.startsWith(LEGACY_DEVICE_PROFILE_PREFIX)
                && key.endsWith("_" + AudioEffectsService.KEY_BASS_TYPE);
    }

    private boolean isLikelyLongKey(@NonNull String key) {
        return key.startsWith(LEGACY_AI_EQ_NEXT_AT_PREFIX)
                || key.startsWith(LEGACY_AI_EQ_LAST_PROCESS_SESSION_PREFIX)
                || key.startsWith(LEGACY_AI_EQ_DISMISSED_SESSION_PREFIX)
                || LEGACY_SCHEDULE_AI_NEXT_AT.equals(key)
                || LEGACY_SCHEDULE_AI_LAST_PROCESS_SESSION.equals(key)
                || LEGACY_APPS_AI_NEXT_AT.equals(key)
                || LEGACY_APPS_AI_LAST_PROCESS_SESSION.equals(key);
    }

    @NonNull
    private DocumentReference userScope(@NonNull String uid) {
        return firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(APP_SCOPE_COLLECTION)
                .document("root");
    }

    @NonNull
    private DocumentReference eqDoc(@NonNull String uid) {
        return firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(APP_SCOPE_COLLECTION)
                .document(DOC_EQ);
    }

    @NonNull
    private DocumentReference settingsDoc(@NonNull String uid) {
        return firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(APP_SCOPE_COLLECTION)
                .document(DOC_SETTINGS);
    }

    @NonNull
    private DocumentReference agendaDoc(@NonNull String uid) {
        return firestore.collection(USERS_COLLECTION)
                .document(uid)
                .collection(APP_SCOPE_COLLECTION)
                .document(DOC_AGENDA);
    }

    private static final class PendingSync {
        private int remaining;
        private final SyncCallback callback;
        private boolean hasError;
        @Nullable
        private String firstError;

        PendingSync(int total, @NonNull SyncCallback callback) {
            this.remaining = total;
            this.callback = callback;
        }

        synchronized void finish(boolean success, @Nullable String message) {
            if (!success && !hasError) {
                hasError = true;
                firstError = TextUtils.isEmpty(message) ? "Error de sincronizacion" : message;
            }

            remaining--;
            if (remaining <= 0) {
                callback.onComplete(!hasError, firstError);
            }
        }
    }
}
