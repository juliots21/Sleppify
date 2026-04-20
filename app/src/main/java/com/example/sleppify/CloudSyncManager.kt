package com.example.sleppify

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.example.sleppify.FavoritesPlaylistStore.FavoriteTrack
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CloudSyncManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val eqPrefs: SharedPreferences = appContext.getSharedPreferences(AudioEffectsService.PREFS_NAME, Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences = appContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
    private val agendaPrefs: SharedPreferences = appContext.getSharedPreferences(PREFS_AGENDA, Context.MODE_PRIVATE)
    private val streamingCachePrefs: SharedPreferences = appContext.getSharedPreferences(PREFS_STREAMING_CACHE, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private var activeUserId: String? = null

    private var listenersRegistered = false
    private var suppressEqSync = false
    private var suppressSettingsSync = false
    private var suppressAgendaSync = false
    private var suppressStreamingSync = false
    private var initialHydrationInProgress = false

    private val syncStateLock = Any()
    private var activeNetworkSyncCount = 0
    private var syncStateListener: SyncStateListener? = null
    private val syncStuckWatchdogRunnable = Runnable { forceClearSyncStateIfStuck() }

    private var eqListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var settingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var agendaListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var streamingListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val eqSyncRunnable = Runnable { uploadEqPreferences() }
    private val settingsSyncRunnable = Runnable { uploadSettingsPreferences() }
    private val agendaSyncRunnable = Runnable { uploadAgendaJson() }
    private val streamingSyncRunnable = Runnable { uploadStreamingFavoritesPreferences() }
    private val eqUploadRetryRunnable = Runnable { uploadEqPreferences() }
    private val settingsUploadRetryRunnable = Runnable { uploadSettingsPreferences() }
    private val agendaUploadRetryRunnable = Runnable { uploadAgendaJson() }
    private val streamingUploadRetryRunnable = Runnable { uploadStreamingFavoritesPreferences() }

    private var eqUploadRetryAttempt = 0
    private var settingsUploadRetryAttempt = 0
    private var agendaUploadRetryAttempt = 0
    private var streamingUploadRetryAttempt = 0

    init {
        ensureEqDefaults()
        ensureSettingsDefaults()
        ensureAgendaDefaults()
        registerPreferenceListenersIfNeeded()
    }

    fun interface SyncCallback {
        fun onComplete(success: Boolean, message: String?)
    }

    fun interface SyncStateListener {
        fun onSyncStateChanged(syncing: Boolean)
    }

    interface CloudPlaylistsCallback {
        fun onResult(playlists: @JvmSuppressWildcards Map<String, List<FavoriteTrack>>)
    }

    companion object {
        private const val TAG = "CloudSyncManager"

        const val PREFS_SETTINGS = "sleppify_settings"
        const val PREFS_AGENDA = "sleppify_agenda"
        const val PREFS_STREAMING_CACHE = "streaming_cache"
        const val KEY_AI_SHIFT_ENABLED = "ai_shift_enabled"
        const val KEY_SMART_SUGGESTIONS_ENABLED = "smart_suggestions_enabled"
        const val KEY_DEFAULT_DURATION_MINUTES = "default_duration_minutes"
        const val KEY_NOTIFICATION_LEAD_MINUTES = "notification_lead_minutes"
        const val KEY_DAILY_SUMMARY_INTERVAL_HOURS = "daily_summary_interval_hours"
        const val KEY_APPS_SHOW_SYSTEM_INFO = "apps_show_system_info"
        const val KEY_APPS_WHITELIST_PACKAGES = "apps_whitelist_packages"
        const val KEY_PLAYER_VIDEO_MODE_ENABLED = "player_video_mode_enabled"
        const val KEY_PLAYER_SHUFFLE_ENABLED = "player_shuffle_enabled"
        const val KEY_PLAYER_REPEAT_MODE = "player_repeat_mode"
        const val KEY_AMOLED_MODE_ENABLED = "amoled_mode_enabled"
        const val KEY_OFFLINE_CROSSFADE_SECONDS = "offline_crossfade_seconds"
        const val KEY_OFFLINE_DOWNLOAD_QUALITY = "offline_download_quality"
        const val KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA = "offline_download_allow_mobile_data"
        const val DOWNLOAD_QUALITY_LOW = "low"
        const val DOWNLOAD_QUALITY_MEDIUM = "medium"
        const val DOWNLOAD_QUALITY_HIGH = "high"
        const val DOWNLOAD_QUALITY_VERY_HIGH = "very_high"
        const val KEY_AGENDA_JSON = "agenda_json"

        private const val USERS_COLLECTION = "users"
        private const val APP_SCOPE_COLLECTION = "sleppify"
        private const val DOC_EQ = "eq"
        private const val DOC_SETTINGS = "settings"
        private const val DOC_AGENDA = "agenda"
        private const val DOC_STREAMING = "streaming"
        private const val DOC_PLAYLISTS = "playlists"

        private const val FIELD_PREFS = "prefs"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_AGENDA_JSON = "agendaJson"
        private const val OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME = "offline_playlist_queue"
        private const val OFFLINE_DOWNLOAD_MANUAL_TRACK_QUEUE_UNIQUE_NAME = "offline_manual_track_queue"
        private const val LEGACY_SELECTED_PRESET = "selected_preset"
        private const val LEGACY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val LEGACY_DEVICE_PROFILE_PREFIX = "device_profile_"
        private const val LEGACY_BAND_PREFIX = "band_db_"
        private const val LEGACY_USER_PRESET_BAND_PREFIX = "user_preset_band_"
        private const val LEGACY_AI_EQ_NEXT_AT_PREFIX = "ai_eq_next_at_"
        private const val LEGACY_AI_EQ_LAST_PROCESS_SESSION_PREFIX = "ai_eq_last_process_session_"
        private const val LEGACY_AI_EQ_DISMISSED_SESSION_PREFIX = "ai_eq_dismissed_session_"
        private const val LEGACY_AI_EQ_PENDING_JSON_PREFIX = "ai_eq_pending_json_"
        private const val DEBUG_EQ_PREFIX = "debug_"
        private const val LEGACY_SCHEDULE_AI_NEXT_AT = "schedule_ai_next_at"
        private const val LEGACY_SCHEDULE_AI_LAST_PROCESS_SESSION = "schedule_ai_last_process_session"
        private const val LEGACY_APPS_AI_NEXT_AT = "apps_ai_next_at"
        private const val LEGACY_APPS_AI_LAST_PROCESS_SESSION = "apps_ai_last_process_session"
        private const val LEGACY_SCHEDULE_AI_ACCEPT_COUNT = "schedule_ai_accept_count"
        private const val LEGACY_SCHEDULE_AI_DISMISS_COUNT = "schedule_ai_dismiss_count"
        private const val LEGACY_SCHEDULE_AI_OPEN_COUNT = "schedule_ai_open_count"
        private const val LEGACY_APPS_AI_ACCEPT_COUNT = "apps_ai_accept_count"
        private const val LEGACY_APPS_AI_DISMISS_COUNT = "apps_ai_dismiss_count"
        private const val LEGACY_APPS_TURBO_MODE = "apps_stop_turbo_mode"

        private val FAVORITES_TRACKS_UPDATED_AT_KEY = "playlist_tracks_updated_at_${FavoritesPlaylistStore.PLAYLIST_ID}"
        private val FAVORITES_TRACKS_DATA_KEY = "playlist_tracks_data_${FavoritesPlaylistStore.PLAYLIST_ID}"
        private val FAVORITES_TRACKS_FULL_CACHE_KEY = "playlist_tracks_cache_full_${FavoritesPlaylistStore.PLAYLIST_ID}"
        private val FAVORITES_OFFLINE_COMPLETE_KEY = "playlist_offline_complete_${FavoritesPlaylistStore.PLAYLIST_ID}"

        private const val DEBOUNCE_MS = 650L
        private const val SYNC_STUCK_TIMEOUT_MS = 16000L
        private const val INITIAL_HYDRATION_RETRY_BASE_MS = 3000L
        private const val INITIAL_HYDRATION_RETRY_MAX_MS = 15000L
        private const val UPLOAD_RETRY_BASE_MS = 2500L
        private const val UPLOAD_RETRY_MAX_MS = 30000L
        private const val UPLOAD_RETRY_MAX_ATTEMPTS = 80

        @Volatile
        private var instance: CloudSyncManager? = null

        @JvmStatic
        fun getInstance(context: Context): CloudSyncManager {
            return instance ?: synchronized(this) {
                instance ?: CloudSyncManager(context).also { instance = it }
            }
        }
    }

    private fun ensureEqDefaults() {
        var editor: SharedPreferences.Editor? = null
        val getEditor = { editor ?: eqPrefs.edit().also { editor = it } }

        if (!eqPrefs.contains(AudioEffectsService.KEY_ENABLED)) getEditor().putBoolean(AudioEffectsService.KEY_ENABLED, false)
        if (!eqPrefs.contains(AudioEffectsService.KEY_SPATIAL_ENABLED)) getEditor().putBoolean(AudioEffectsService.KEY_SPATIAL_ENABLED, false)
        if (!eqPrefs.contains(AudioEffectsService.KEY_SPATIAL_STRENGTH)) getEditor().putInt(AudioEffectsService.KEY_SPATIAL_STRENGTH, AudioEffectsService.SPATIAL_STRENGTH_DEFAULT)
        if (!eqPrefs.contains(AudioEffectsService.KEY_REVERB_LEVEL)) getEditor().putInt(AudioEffectsService.KEY_REVERB_LEVEL, AudioEffectsService.REVERB_LEVEL_OFF)

        editor?.apply()
    }

    private fun ensureSettingsDefaults() {
        var editor: SharedPreferences.Editor? = null
        val getEditor = { editor ?: settingsPrefs.edit().also { editor = it } }

        if (!settingsPrefs.contains(KEY_AI_SHIFT_ENABLED)) getEditor().putBoolean(KEY_AI_SHIFT_ENABLED, true)
        if (!settingsPrefs.contains(KEY_SMART_SUGGESTIONS_ENABLED)) getEditor().putBoolean(KEY_SMART_SUGGESTIONS_ENABLED, true)
        if (!settingsPrefs.contains(KEY_DEFAULT_DURATION_MINUTES)) getEditor().putInt(KEY_DEFAULT_DURATION_MINUTES, 8 * 60 + 15)
        if (!settingsPrefs.contains(KEY_NOTIFICATION_LEAD_MINUTES)) getEditor().putInt(KEY_NOTIFICATION_LEAD_MINUTES, 1)
        if (!settingsPrefs.contains(KEY_DAILY_SUMMARY_INTERVAL_HOURS)) getEditor().putInt(KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2)
        if (!settingsPrefs.contains(KEY_APPS_SHOW_SYSTEM_INFO)) getEditor().putBoolean(KEY_APPS_SHOW_SYSTEM_INFO, true)
        if (!settingsPrefs.contains(KEY_APPS_WHITELIST_PACKAGES)) getEditor().putStringSet(KEY_APPS_WHITELIST_PACKAGES, hashSetOf())
        if (!settingsPrefs.contains(KEY_PLAYER_VIDEO_MODE_ENABLED)) getEditor().putBoolean(KEY_PLAYER_VIDEO_MODE_ENABLED, false)
        if (!settingsPrefs.contains(KEY_PLAYER_SHUFFLE_ENABLED)) getEditor().putBoolean(KEY_PLAYER_SHUFFLE_ENABLED, false)
        if (!settingsPrefs.contains(KEY_PLAYER_REPEAT_MODE)) getEditor().putInt(KEY_PLAYER_REPEAT_MODE, 1)
        if (!settingsPrefs.contains(KEY_AMOLED_MODE_ENABLED)) getEditor().putBoolean(KEY_AMOLED_MODE_ENABLED, false)
        if (!settingsPrefs.contains(KEY_OFFLINE_CROSSFADE_SECONDS)) getEditor().putInt(KEY_OFFLINE_CROSSFADE_SECONDS, 0)
        if (!settingsPrefs.contains(KEY_OFFLINE_DOWNLOAD_QUALITY)) getEditor().putString(KEY_OFFLINE_DOWNLOAD_QUALITY, DOWNLOAD_QUALITY_VERY_HIGH)
        if (!settingsPrefs.contains(KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA)) getEditor().putBoolean(KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA, false)

        editor?.apply()
        pruneDeprecatedSettingsKeys()
    }

    private fun ensureAgendaDefaults() {
        if (!agendaPrefs.contains(KEY_AGENDA_JSON)) {
            agendaPrefs.edit().putString(KEY_AGENDA_JSON, "{}").apply()
        }
    }

    private fun registerPreferenceListenersIfNeeded() {
        if (listenersRegistered) return

        eqListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (suppressEqSync || initialHydrationInProgress || activeUserId.isNullOrEmpty()) return@OnSharedPreferenceChangeListener
            handler.removeCallbacks(eqSyncRunnable)
            handler.postDelayed(eqSyncRunnable, DEBOUNCE_MS)
        }

        settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (suppressSettingsSync || initialHydrationInProgress || activeUserId.isNullOrEmpty()) return@OnSharedPreferenceChangeListener
            handler.removeCallbacks(settingsSyncRunnable)
            handler.postDelayed(settingsSyncRunnable, DEBOUNCE_MS)
        }

        agendaListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (suppressAgendaSync || initialHydrationInProgress || activeUserId.isNullOrEmpty()) return@OnSharedPreferenceChangeListener
            handler.removeCallbacks(agendaSyncRunnable)
            handler.postDelayed(agendaSyncRunnable, DEBOUNCE_MS)
        }

        streamingListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (suppressStreamingSync || initialHydrationInProgress || activeUserId.isNullOrEmpty()) return@OnSharedPreferenceChangeListener
            if (!isStreamingFavoritesKey(key)) return@OnSharedPreferenceChangeListener
            handler.removeCallbacks(streamingSyncRunnable)
            handler.postDelayed(streamingSyncRunnable, DEBOUNCE_MS)
        }

        eqPrefs.registerOnSharedPreferenceChangeListener(eqListener)
        settingsPrefs.registerOnSharedPreferenceChangeListener(settingsListener)
        agendaPrefs.registerOnSharedPreferenceChangeListener(agendaListener)
        streamingCachePrefs.registerOnSharedPreferenceChangeListener(streamingListener)
        listenersRegistered = true
    }

    fun isCloudEnabledForCurrentUser(): Boolean = !activeUserId.isNullOrEmpty()

    fun setSyncStateListener(listener: SyncStateListener?) {
        val syncing: Boolean
        synchronized(syncStateLock) {
            syncStateListener = listener
            syncing = activeNetworkSyncCount > 0
        }
        dispatchSyncState(syncing)
    }

    fun onUserSignedIn(userId: String, callback: SyncCallback) {
        activeUserId = userId
        handler.removeCallbacks(eqSyncRunnable)
        handler.removeCallbacks(settingsSyncRunnable)
        handler.removeCallbacks(agendaSyncRunnable)
        handler.removeCallbacks(streamingSyncRunnable)
        clearUploadRetryCallbacks()
        resetUploadRetryAttempts()
        initialHydrationInProgress = true
        ensureEqDefaults()
        ensureSettingsDefaults()
        ensureAgendaDefaults()

        startInitialHydrationAttempt(userId, 0, callback)
    }

    fun syncNow(callback: SyncCallback) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) {
            callback.onComplete(false, "No hay sesion activa para sincronizar.")
            return
        }
        syncNow(uid, callback)
    }

    fun syncNow(userId: String, callback: SyncCallback) {
        if (userId.isEmpty()) {
            callback.onComplete(false, "No se pudo resolver el usuario para sincronizar.")
            return
        }

        activeUserId = userId
        handler.removeCallbacks(eqSyncRunnable)
        handler.removeCallbacks(settingsSyncRunnable)
        handler.removeCallbacks(agendaSyncRunnable)
        handler.removeCallbacks(streamingSyncRunnable)
        clearUploadRetryCallbacks()
        resetUploadRetryAttempts()
        initialHydrationInProgress = true
        ensureEqDefaults()
        ensureSettingsDefaults()
        ensureAgendaDefaults()

        startInitialHydrationAttempt(userId, 0, callback)
    }

    fun syncSettingsNowIfSignedIn() {
        if (activeUserId.isNullOrEmpty() || initialHydrationInProgress) return
        handler.removeCallbacks(settingsSyncRunnable)
        uploadSettingsPreferences()
    }

    private fun startInitialHydrationAttempt(userId: String, attempt: Int, callback: SyncCallback) {
        if (activeUserId != userId) return

        val pending = PendingSync(5) { success, message ->
            if (activeUserId != userId) return@PendingSync

            if (!success && isRecoverableSyncMessage(message)) {
                val nextAttempt = attempt + 1
                val retryStep = nextAttempt.coerceIn(1, 6).toLong()
                val delayMs = minOf(INITIAL_HYDRATION_RETRY_MAX_MS, INITIAL_HYDRATION_RETRY_BASE_MS * retryStep)
                handler.postDelayed(
                    { startInitialHydrationAttempt(userId, nextAttempt, callback) },
                    delayMs
                )
                return@PendingSync
            }

            initialHydrationInProgress = false
            callback.onComplete(success, message)
        }
        syncEqFromCloudThenLocal(pending)
        syncSettingsFromCloudThenLocal(pending)
        syncAgendaFromCloudThenLocal(pending)
        syncStreamingFavoritesFromCloudThenLocal(pending)
        syncPlaylistsFromCloudThenLocal(pending)
    }

    fun onUserSignedOut() {
        activeUserId = null
        initialHydrationInProgress = false
        handler.removeCallbacks(eqSyncRunnable)
        handler.removeCallbacks(settingsSyncRunnable)
        handler.removeCallbacks(agendaSyncRunnable)
        handler.removeCallbacks(streamingSyncRunnable)
        clearUploadRetryCallbacks()
        resetUploadRetryAttempts()
        pauseUserScopedBackgroundWork()
        forceClearSyncState()
    }

    fun pauseUserScopedBackgroundWork() {
        try {
            val manager = WorkManager.getInstance(appContext)
            manager.cancelUniqueWork(OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME)
            manager.cancelAllWorkByTag(OFFLINE_DOWNLOAD_QUEUE_UNIQUE_NAME)
            manager.cancelUniqueWork(OFFLINE_DOWNLOAD_MANUAL_TRACK_QUEUE_UNIQUE_NAME)
            manager.cancelAllWorkByTag(OFFLINE_DOWNLOAD_MANUAL_TRACK_QUEUE_UNIQUE_NAME)
        } catch (ignored: Throwable) {
        }
    }

    fun deleteUserDataFromCloud(userId: String, callback: SyncCallback) {
        if (userId.isEmpty()) {
            callback.onComplete(false, "No se encontro usuario para eliminar datos.")
            return
        }

        beginNetworkSync()

        val userDoc = firestore.collection(USERS_COLLECTION).document(userId)
        userDoc.collection(APP_SCOPE_COLLECTION)
            .get(Source.SERVER)
            .addOnSuccessListener { scopeDocs -> deleteScopeDocumentsThenParent(userId, scopeDocs, callback) }
            .addOnFailureListener {
                val fallbackBatch = firestore.batch()
                fallbackBatch.delete(eqDoc(userId))
                fallbackBatch.delete(settingsDoc(userId))
                fallbackBatch.delete(agendaDoc(userId))
                fallbackBatch.delete(streamingDoc(userId))
                fallbackBatch.delete(userScope(userId))
                fallbackBatch.commit()
                    .addOnSuccessListener { deleteUserDocBestEffort(userId, callback) }
                    .addOnFailureListener { e ->
                        endNetworkSync()
                        callback.onComplete(false, toUserFacingSyncError(e))
                    }
            }
    }

    private fun deleteScopeDocumentsThenParent(userId: String, scopeDocs: QuerySnapshot, callback: SyncCallback) {
        val batch = firestore.batch()
        var deleteCount = 0

        for (doc in scopeDocs.documents) {
            batch.delete(doc.reference)
            deleteCount++
        }

        if (deleteCount == 0) {
            batch.delete(eqDoc(userId))
            batch.delete(settingsDoc(userId))
            batch.delete(agendaDoc(userId))
            batch.delete(streamingDoc(userId))
            batch.delete(userScope(userId))
        }

        batch.commit()
            .addOnSuccessListener { deleteUserDocBestEffort(userId, callback) }
            .addOnFailureListener { e ->
                endNetworkSync()
                callback.onComplete(false, toUserFacingSyncError(e))
            }
    }

    private fun deleteUserDocBestEffort(userId: String, callback: SyncCallback) {
        val userDoc = firestore.collection(USERS_COLLECTION).document(userId)
        userDoc.delete()
            .addOnSuccessListener {
                endNetworkSync()
                callback.onComplete(true, null)
            }
            .addOnFailureListener { parentDeleteError ->
                if (parentDeleteError is FirebaseFirestoreException &&
                    parentDeleteError.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) {
                    endNetworkSync()
                    callback.onComplete(true, null)
                    return@addOnFailureListener
                }

                endNetworkSync()
                callback.onComplete(false, toUserFacingSyncError(parentDeleteError))
            }
    }

    fun clearLocalUserData() {
        suppressEqSync = true
        suppressSettingsSync = true
        suppressAgendaSync = true
        suppressStreamingSync = true

        try {
            eqPrefs.edit().clear().apply()
            settingsPrefs.edit().clear().apply()
            agendaPrefs.edit().clear().apply()
            streamingCachePrefs.edit().clear().apply()
        } finally {
            suppressEqSync = false
            suppressSettingsSync = false
            suppressAgendaSync = false
            suppressStreamingSync = false
        }

        ensureEqDefaults()
        ensureSettingsDefaults()
        ensureAgendaDefaults()
    }

    fun clearLocalUserDataCompletely() {
        onUserSignedOut()

        try {
            WorkManager.getInstance(appContext).cancelAllWork()
        } catch (ignored: Throwable) {
        }

        try {
            NotificationManagerCompat.from(appContext).cancelAll()
        } catch (ignored: Throwable) {
        }

        clearAllSharedPreferencesFiles()
        clearAllDatabases()

        clearDirectoryContents(appContext.filesDir)
        clearDirectoryContents(appContext.cacheDir)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            clearDirectoryContents(appContext.noBackupFilesDir)
            clearDirectoryContents(appContext.codeCacheDir)
        }

        appContext.getExternalFilesDirs(null)?.forEach { dir ->
            clearDirectoryContents(dir)
        }

        appContext.externalCacheDirs?.forEach { dir ->
            clearDirectoryContents(dir)
        }
    }

    private fun clearAllSharedPreferencesFiles() {
        try {
            val sharedPrefsDir = File(appContext.applicationInfo.dataDir, "shared_prefs")
            val prefFiles = sharedPrefsDir.listFiles() ?: return

            for (prefFile in prefFiles) {
                if (prefFile == null) continue
                val fileName = prefFile.name
                if (fileName.isNullOrEmpty() || !fileName.endsWith(".xml")) continue

                val prefName = fileName.substring(0, fileName.length - 4)
                try {
                    appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .commit()
                } catch (ignored: Throwable) {
                }
                try {
                    prefFile.delete()
                } catch (ignored: Throwable) {
                }
            }
        } catch (ignored: Throwable) {
        }
    }

    private fun clearAllDatabases() {
        try {
            val databaseNames = appContext.databaseList() ?: return

            for (dbName in databaseNames) {
                if (dbName.isNullOrEmpty()) continue
                try {
                    appContext.deleteDatabase(dbName)
                } catch (ignored: Throwable) {
                }
            }
        } catch (ignored: Throwable) {
        }
    }

    private fun clearDirectoryContents(rootDir: File?) {
        if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory) return

        rootDir.listFiles()?.forEach { child ->
            deleteRecursively(child)
        }
    }

    private fun deleteRecursively(node: File?) {
        if (node == null || !node.exists()) return

        if (node.isDirectory) {
            node.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }

        try {
            node.delete()
        } catch (ignored: Throwable) {
        }
    }

    val localAgendaJson: String get() {
        val json = agendaPrefs.getString(KEY_AGENDA_JSON, "{}")
        return if (json.isNullOrEmpty()) "{}" else json
    }

    fun saveAgendaJsonLocally(agendaJson: String) {
        agendaPrefs.edit().putString(KEY_AGENDA_JSON, agendaJson).apply()
    }

    fun syncAgendaJson(agendaJson: String) {
        saveAgendaJsonLocally(agendaJson)
        uploadAgendaJson()
    }

    fun refreshAgendaFromCloud(callback: SyncCallback) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) {
            callback.onComplete(false, "No hay sesion activa para agenda en la nube.")
            return
        }

        beginNetworkSync()

        agendaDoc(uid).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    endNetworkSync()
                    callback.onComplete(true, null)
                    return@addOnSuccessListener
                }

                if (applyEncodedPrefsFromSnapshot(agendaPrefs, snapshot)) {
                    ensureAgendaDefaults()
                    endNetworkSync()
                    callback.onComplete(true, null)
                    return@addOnSuccessListener
                }

                val json = snapshot.getString(FIELD_AGENDA_JSON)
                if (!json.isNullOrEmpty()) {
                    agendaPrefs.edit().putString(KEY_AGENDA_JSON, json).apply()
                }
                ensureAgendaDefaults()
                endNetworkSync()
                callback.onComplete(true, null)
            }
            .addOnFailureListener { e ->
                endNetworkSync()
                callback.onComplete(false, e.message)
            }
    }

    private fun syncEqFromCloudThenLocal(pending: PendingSync) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) {
            pending.finish(true, null)
            return
        }

        beginNetworkSync()

        eqDoc(uid).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                handleEqSyncSnapshot(snapshot, true, null, pending)
            }
            .addOnFailureListener { serverError ->
                eqDoc(uid).get(Source.CACHE)
                    .addOnSuccessListener { snapshot ->
                        handleEqSyncSnapshot(snapshot, false, serverError, pending)
                    }
                    .addOnFailureListener {
                        endNetworkSync()
                        pending.finish(false, toUserFacingSyncError(serverError))
                    }
            }
    }

    private fun syncSettingsFromCloudThenLocal(pending: PendingSync) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) {
            pending.finish(true, null)
            return
        }

        beginNetworkSync()

        settingsDoc(uid).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                handleSettingsSyncSnapshot(snapshot, true, null, pending)
            }
            .addOnFailureListener { serverError ->
                settingsDoc(uid).get(Source.CACHE)
                    .addOnSuccessListener { snapshot ->
                        handleSettingsSyncSnapshot(snapshot, false, serverError, pending)
                    }
                    .addOnFailureListener {
                        endNetworkSync()
                        pending.finish(false, toUserFacingSyncError(serverError))
                    }
            }
    }

    private fun syncAgendaFromCloudThenLocal(pending: PendingSync) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) {
            pending.finish(true, null)
            return
        }

        beginNetworkSync()

        agendaDoc(uid).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                handleAgendaSyncSnapshot(snapshot, true, null, pending)
            }
            .addOnFailureListener { serverError ->
                agendaDoc(uid).get(Source.CACHE)
                    .addOnSuccessListener { snapshot ->
                        handleAgendaSyncSnapshot(snapshot, false, serverError, pending)
                    }
                    .addOnFailureListener {
                        endNetworkSync()
                        pending.finish(false, toUserFacingSyncError(serverError))
                    }
            }
    }

    private fun syncStreamingFavoritesFromCloudThenLocal(pending: PendingSync) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) {
            pending.finish(true, null)
            return
        }

        beginNetworkSync()

        streamingDoc(uid).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                handleStreamingSyncSnapshot(snapshot, true, null, pending)
            }
            .addOnFailureListener { serverError ->
                streamingDoc(uid).get(Source.CACHE)
                    .addOnSuccessListener { snapshot ->
                        handleStreamingSyncSnapshot(snapshot, false, serverError, pending)
                    }
                    .addOnFailureListener {
                        endNetworkSync()
                        pending.finish(false, toUserFacingSyncError(serverError))
                    }
            }
    }

    private fun syncPlaylistsFromCloudThenLocal(pending: PendingSync) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) {
            pending.finish(true, null)
            return
        }

        beginNetworkSync()

        playlistsDoc(uid).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                handlePlaylistsSyncSnapshot(snapshot, true, null, pending)
            }
            .addOnFailureListener { serverError ->
                playlistsDoc(uid).get(Source.CACHE)
                    .addOnSuccessListener { snapshot ->
                        handlePlaylistsSyncSnapshot(snapshot, false, serverError, pending)
                    }
                    .addOnFailureListener {
                        endNetworkSync()
                        pending.finish(false, toUserFacingSyncError(serverError))
                    }
            }
    }

    private fun handleEqSyncSnapshot(snapshot: DocumentSnapshot, fromServer: Boolean, serverError: Throwable?, pending: PendingSync) {
        if (!snapshot.exists()) {
            if (fromServer) {
                uploadEqPreferences()
                endNetworkSync()
                pending.finish(true, null)
            } else {
                endNetworkSync()
                pending.finish(false, toUserFacingSyncError(serverError))
            }
            return
        }

        if (applyEncodedPrefsFromSnapshot(eqPrefs, snapshot)) {
            ensureEqDefaults()
            if (fromServer && !activeUserId.isNullOrEmpty()) {
                uploadEqPreferences()
            }
            endNetworkSync()
            pending.finish(true, null)
            return
        }

        endNetworkSync()
        if (fromServer) {
            pending.finish(false, "No se pudo leer la configuracion EQ en la nube.")
        } else {
            pending.finish(false, toUserFacingSyncError(serverError))
        }
    }

    private fun handleSettingsSyncSnapshot(snapshot: DocumentSnapshot, fromServer: Boolean, serverError: Throwable?, pending: PendingSync) {
        if (!snapshot.exists()) {
            if (fromServer) {
                uploadSettingsPreferences()
                endNetworkSync()
                pending.finish(true, null)
            } else {
                endNetworkSync()
                pending.finish(false, toUserFacingSyncError(serverError))
            }
            return
        }

        if (applyEncodedPrefsFromSnapshot(settingsPrefs, snapshot)) {
            ensureSettingsDefaults()
            pruneDeprecatedSettingsKeys()
            endNetworkSync()
            if (fromServer && !activeUserId.isNullOrEmpty()) {
                uploadSettingsPreferences()
            }
            pending.finish(true, null)
            return
        }

        endNetworkSync()
        if (fromServer) {
            pending.finish(false, "No se pudo leer los ajustes en la nube.")
        } else {
            pending.finish(false, toUserFacingSyncError(serverError))
        }
    }

    private fun handleAgendaSyncSnapshot(snapshot: DocumentSnapshot, fromServer: Boolean, serverError: Throwable?, pending: PendingSync) {
        if (!snapshot.exists()) {
            if (fromServer) {
                uploadAgendaJson()
                endNetworkSync()
                pending.finish(true, null)
            } else {
                endNetworkSync()
                pending.finish(false, toUserFacingSyncError(serverError))
            }
            return
        }

        if (applyEncodedPrefsFromSnapshot(agendaPrefs, snapshot)) {
            ensureAgendaDefaults()
            endNetworkSync()
            pending.finish(true, null)
            return
        }

        val cloudJson = snapshot.getString(FIELD_AGENDA_JSON)
        if (!cloudJson.isNullOrEmpty()) {
            agendaPrefs.edit().putString(KEY_AGENDA_JSON, cloudJson).apply()
            ensureAgendaDefaults()
            endNetworkSync()
            pending.finish(true, null)
            return
        }

        if (fromServer) {
            uploadAgendaJson()
            endNetworkSync()
            pending.finish(true, null)
            return
        }

        endNetworkSync()
        pending.finish(false, toUserFacingSyncError(serverError))
    }

    private fun handleStreamingSyncSnapshot(snapshot: DocumentSnapshot, fromServer: Boolean, serverError: Throwable?, pending: PendingSync) {
        if (!snapshot.exists()) {
            if (fromServer) {
                uploadStreamingFavoritesPreferences()
                endNetworkSync()
                pending.finish(true, null)
            } else {
                endNetworkSync()
                pending.finish(false, toUserFacingSyncError(serverError))
            }
            return
        }

        if (applyStreamingFavoritesFromSnapshot(snapshot)) {
            endNetworkSync()
            pending.finish(true, null)
            return
        }

        endNetworkSync()
        if (fromServer) {
            pending.finish(false, "No se pudo leer favoritos de streaming en la nube.")
        } else {
            pending.finish(false, toUserFacingSyncError(serverError))
        }
    }

    private fun handlePlaylistsSyncSnapshot(snapshot: DocumentSnapshot, fromServer: Boolean, serverError: Throwable?, pending: PendingSync) {
        if (!snapshot.exists()) {
            if (fromServer) {
                uploadAllCustomPlaylists()
            }
            endNetworkSync()
            pending.finish(true, null)
            return
        }

        // Move heavy decoding and importing to background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val decoded = decodeCloudPlaylists(snapshot)
                if (decoded.isNotEmpty()) {
                    CustomPlaylistsStore.importCloudPlaylists(appContext, decoded)
                }
                
                withContext(Dispatchers.Main) {
                    endNetworkSync()
                    pending.finish(true, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Fallo procesando playlists desde la nube", e)
                    endNetworkSync()
                    pending.finish(false, "Fallo procesando playlists: " + e.message)
                }
            }
        }
    }

    private fun uploadAllCustomPlaylists() {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) return

        val names = CustomPlaylistsStore.getAllPlaylistNames(appContext)
        if (names.isEmpty()) return

        // We'll simulate a bulk sync. Since syncPlaylistToCloud reads and writes, 
        // doing it in a loop might have race conditions if not careful.
        // However, we are in hydration, usually no other mutations are happening.
        // Better: implement a batch upload.
        
        val payload = hashMapOf<String, Any>()
        val playlistsMap = hashMapOf<String, Any>()
        
        for (name in names) {
            val tracks = CustomPlaylistsStore.getTracksFromPlaylist(appContext, name)
            val tracksList = tracks.map { t ->
                hashMapOf<String, Any>(
                    "videoId" to t.videoId,
                    "title" to t.title,
                    "artist" to t.artist,
                    "duration" to t.duration,
                    "imageUrl" to t.imageUrl
                )
            }
            playlistsMap[encodePlaylistKey(name)] = hashMapOf(
                "name" to name,
                "tracks" to tracksList
            )
        }

        payload["playlists"] = playlistsMap
        payload[FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
        
        beginNetworkSync()
        playlistsDoc(uid).set(payload)
            .addOnSuccessListener { endNetworkSync() }
            .addOnFailureListener { endNetworkSync() }
    }

    private fun toUserFacingSyncError(throwable: Throwable?): String {
        if (throwable is FirebaseFirestoreException) {
            val code = throwable.code
            if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                return "Cloud Firestore no esta inicializado en Firebase. Crea la base de datos Firestore en modo nativo."
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Reglas de Firestore bloquean la sincronizacion. Verifica permisos para users/{uid}/sleppify."
            }
            if (code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
                return "Sesion Firebase invalida. Cierra sesion e inicia nuevamente."
            }
        }

        if (isRecoverableSyncThrowable(throwable)) {
            return "Sin conexion con la nube por ahora. Reintentando automaticamente."
        }
        if (throwable?.message.isNullOrEmpty()) {
            return "No se pudo sincronizar en la nube."
        }
        return throwable?.message ?: "No se pudo sincronizar en la nube."
    }

    private fun isRecoverableSyncMessage(message: String?): Boolean {
        if (message.isNullOrEmpty()) return false
        val normalized = message.lowercase()
        return "offline" in normalized ||
               "sin conexion" in normalized ||
               "network" in normalized ||
               "unavailable" in normalized ||
               "tempor" in normalized ||
               "timeout" in normalized ||
               "timed out" in normalized
    }

    private fun isRecoverableSyncThrowable(throwable: Throwable?): Boolean {
        if (throwable is FirebaseFirestoreException) {
            val code = throwable.code
            return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                   code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                   code == FirebaseFirestoreException.Code.ABORTED ||
                   code == FirebaseFirestoreException.Code.CANCELLED
        }

        if (throwable?.message.isNullOrEmpty()) return false
        return isRecoverableSyncMessage(throwable?.message)
    }

    private fun uploadAllBucketsNow() {
        if (activeUserId.isNullOrEmpty()) return

        handler.removeCallbacks(eqSyncRunnable)
        handler.removeCallbacks(settingsSyncRunnable)
        handler.removeCallbacks(agendaSyncRunnable)
        handler.removeCallbacks(streamingSyncRunnable)
        uploadEqPreferences()
        uploadSettingsPreferences()
        uploadAgendaJson()
        uploadStreamingFavoritesPreferences()
    }

    private fun uploadEqPreferences() {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) return

        val payload = hashMapOf<String, Any>(
            FIELD_PREFS to encodeEqPreferences(),
            FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        beginNetworkSync()
        eqDoc(uid).set(payload)
            .addOnSuccessListener {
                eqUploadRetryAttempt = 0
                handler.removeCallbacks(eqUploadRetryRunnable)
                endNetworkSync()
            }
            .addOnFailureListener { e ->
                endNetworkSync()
                Log.w(TAG, "Fallo subiendo preferencias de EQ", e)
                scheduleEqUploadRetryIfNeeded(e)
            }
    }

    private fun uploadSettingsPreferences() {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) return

        val payload = hashMapOf<String, Any>(
            FIELD_PREFS to encodeSettingsPreferences(),
            FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        beginNetworkSync()
        settingsDoc(uid).set(payload)
            .addOnSuccessListener {
                settingsUploadRetryAttempt = 0
                handler.removeCallbacks(settingsUploadRetryRunnable)
                endNetworkSync()
            }
            .addOnFailureListener { e ->
                endNetworkSync()
                Log.w(TAG, "Fallo subiendo preferencias de ajustes", e)
                scheduleSettingsUploadRetryIfNeeded(e)
            }
    }

    private fun uploadStreamingFavoritesPreferences() {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) return

        val payload = hashMapOf<String, Any>(
            FIELD_PREFS to encodeStreamingFavoritesPreferences(),
            FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        beginNetworkSync()
        streamingDoc(uid).set(payload)
            .addOnSuccessListener {
                streamingUploadRetryAttempt = 0
                handler.removeCallbacks(streamingUploadRetryRunnable)
                endNetworkSync()
            }
            .addOnFailureListener { e ->
                endNetworkSync()
                Log.w(TAG, "Fallo subiendo favoritos de streaming", e)
                scheduleStreamingUploadRetryIfNeeded(e)
            }
    }

    private fun uploadAgendaJson() {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) return

        val agendaJson = agendaPrefs.getString(KEY_AGENDA_JSON, "{}")
        val payload = hashMapOf<String, Any>(
            FIELD_PREFS to encodePreferences(agendaPrefs),
            FIELD_AGENDA_JSON to (if (agendaJson.isNullOrEmpty()) "{}" else agendaJson!!),
            FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        beginNetworkSync()
        agendaDoc(uid).set(payload)
            .addOnSuccessListener {
                agendaUploadRetryAttempt = 0
                handler.removeCallbacks(agendaUploadRetryRunnable)
                endNetworkSync()
            }
            .addOnFailureListener { e ->
                endNetworkSync()
                Log.w(TAG, "Fallo subiendo agenda", e)
                scheduleAgendaUploadRetryIfNeeded(e)
            }
    }

    private fun clearUploadRetryCallbacks() {
        handler.removeCallbacks(eqUploadRetryRunnable)
        handler.removeCallbacks(settingsUploadRetryRunnable)
        handler.removeCallbacks(agendaUploadRetryRunnable)
        handler.removeCallbacks(streamingUploadRetryRunnable)
    }

    private fun resetUploadRetryAttempts() {
        eqUploadRetryAttempt = 0
        settingsUploadRetryAttempt = 0
        agendaUploadRetryAttempt = 0
        streamingUploadRetryAttempt = 0
    }

    private fun scheduleEqUploadRetryIfNeeded(throwable: Throwable?) {
        if (!shouldRetryUpload(throwable) || activeUserId.isNullOrEmpty()) return
        eqUploadRetryAttempt = minOf(eqUploadRetryAttempt + 1, UPLOAD_RETRY_MAX_ATTEMPTS)
        val delayMs = computeUploadRetryDelayMs(eqUploadRetryAttempt)
        handler.removeCallbacks(eqUploadRetryRunnable)
        handler.postDelayed(eqUploadRetryRunnable, delayMs)
    }

    private fun scheduleSettingsUploadRetryIfNeeded(throwable: Throwable?) {
        if (!shouldRetryUpload(throwable) || activeUserId.isNullOrEmpty()) return
        settingsUploadRetryAttempt = minOf(settingsUploadRetryAttempt + 1, UPLOAD_RETRY_MAX_ATTEMPTS)
        val delayMs = computeUploadRetryDelayMs(settingsUploadRetryAttempt)
        handler.removeCallbacks(settingsUploadRetryRunnable)
        handler.postDelayed(settingsUploadRetryRunnable, delayMs)
    }

    private fun scheduleAgendaUploadRetryIfNeeded(throwable: Throwable?) {
        if (!shouldRetryUpload(throwable) || activeUserId.isNullOrEmpty()) return
        agendaUploadRetryAttempt = minOf(agendaUploadRetryAttempt + 1, UPLOAD_RETRY_MAX_ATTEMPTS)
        val delayMs = computeUploadRetryDelayMs(agendaUploadRetryAttempt)
        handler.removeCallbacks(agendaUploadRetryRunnable)
        handler.postDelayed(agendaUploadRetryRunnable, delayMs)
    }

    private fun scheduleStreamingUploadRetryIfNeeded(throwable: Throwable?) {
        if (!shouldRetryUpload(throwable) || activeUserId.isNullOrEmpty()) return
        streamingUploadRetryAttempt = minOf(streamingUploadRetryAttempt + 1, UPLOAD_RETRY_MAX_ATTEMPTS)
        val delayMs = computeUploadRetryDelayMs(streamingUploadRetryAttempt)
        handler.removeCallbacks(streamingUploadRetryRunnable)
        handler.postDelayed(streamingUploadRetryRunnable, delayMs)
    }

    private fun computeUploadRetryDelayMs(attempt: Int): Long {
        val boundedAttempt = maxOf(1L, attempt.toLong())
        return minOf(UPLOAD_RETRY_MAX_MS, UPLOAD_RETRY_BASE_MS * boundedAttempt)
    }

    private fun shouldRetryUpload(throwable: Throwable?): Boolean {
        if (throwable is FirebaseFirestoreException) {
            val code = throwable.code
            return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                   code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                   code == FirebaseFirestoreException.Code.ABORTED ||
                   code == FirebaseFirestoreException.Code.CANCELLED ||
                   code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED
        }

        if (throwable?.message.isNullOrEmpty()) return false
        val normalized = throwable?.message?.lowercase() ?: return false
        return "offline" in normalized ||
               "network" in normalized ||
               "timeout" in normalized ||
               "timed out" in normalized ||
               "unavailable" in normalized
    }

    private fun applyEncodedPrefsFromSnapshot(targetPrefs: SharedPreferences, snapshot: DocumentSnapshot): Boolean {
        val encodedPrefs = extractPrefsMap(snapshot)
        if (encodedPrefs.isNullOrEmpty()) return false

        val eqBucket = targetPrefs === eqPrefs
        val settingsBucket = targetPrefs === settingsPrefs
        val agendaBucket = targetPrefs === agendaPrefs

        if (eqBucket) suppressEqSync = true
        else if (settingsBucket) suppressSettingsSync = true
        else if (agendaBucket) suppressAgendaSync = true

        return try {
            val editor = targetPrefs.edit()
            editor.clear()
            val decodedCount = decodePreferences(encodedPrefs, editor)
            if (decodedCount <= 0) return false
            editor.apply()
            true
        } finally {
            if (eqBucket) suppressEqSync = false
            else if (settingsBucket) suppressSettingsSync = false
            else if (agendaBucket) suppressAgendaSync = false
        }
    }

    private fun applyStreamingFavoritesFromSnapshot(snapshot: DocumentSnapshot): Boolean {
        val encodedPrefs = extractPrefsMap(snapshot) ?: return false

        suppressStreamingSync = true
        return try {
            val editor = streamingCachePrefs.edit()
            editor.remove(FAVORITES_TRACKS_UPDATED_AT_KEY)
            editor.remove(FAVORITES_TRACKS_DATA_KEY)
            editor.remove(FAVORITES_TRACKS_FULL_CACHE_KEY)
            editor.remove(FAVORITES_OFFLINE_COMPLETE_KEY)

            for ((key, value) in encodedPrefs) {
                if (!isStreamingFavoritesKey(key)) continue
                decodePreferenceEntry(key, value, editor)
            }
            editor.apply()
            true
        } finally {
            suppressStreamingSync = false
        }
    }

    private fun extractPrefsMap(snapshot: DocumentSnapshot): Map<String, Any>? {
        val raw = snapshot.get(FIELD_PREFS)
        if (raw is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return raw as Map<String, Any>
        }

        val rootData = snapshot.data
        if (rootData.isNullOrEmpty()) return null

        val fallback = HashMap(rootData)
        fallback.remove(FIELD_PREFS)
        fallback.remove(FIELD_UPDATED_AT)
        fallback.remove(FIELD_AGENDA_JSON)
        return fallback.takeIf { it.isNotEmpty() }
    }

    private fun beginNetworkSync() {
        val shouldNotify: Boolean
        synchronized(syncStateLock) {
            activeNetworkSyncCount++
            shouldNotify = activeNetworkSyncCount == 1
        }
        handler.removeCallbacks(syncStuckWatchdogRunnable)
        handler.postDelayed(syncStuckWatchdogRunnable, SYNC_STUCK_TIMEOUT_MS)
        if (shouldNotify) {
            dispatchSyncState(true)
        }
    }

    private fun endNetworkSync() {
        val shouldNotify: Boolean
        synchronized(syncStateLock) {
            activeNetworkSyncCount = maxOf(0, activeNetworkSyncCount - 1)
            shouldNotify = activeNetworkSyncCount == 0
        }
        if (shouldNotify) {
            handler.removeCallbacks(syncStuckWatchdogRunnable)
        } else {
            handler.removeCallbacks(syncStuckWatchdogRunnable)
            handler.postDelayed(syncStuckWatchdogRunnable, SYNC_STUCK_TIMEOUT_MS)
        }
        if (shouldNotify) {
            dispatchSyncState(false)
        }
    }

    private fun forceClearSyncStateIfStuck() {
        val shouldNotify: Boolean
        synchronized(syncStateLock) {
            shouldNotify = activeNetworkSyncCount > 0
            activeNetworkSyncCount = 0
        }
        if (shouldNotify) {
            dispatchSyncState(false)
        }
    }

    private fun forceClearSyncState() {
        handler.removeCallbacks(syncStuckWatchdogRunnable)
        forceClearSyncStateIfStuck()
    }

    private fun dispatchSyncState(syncing: Boolean) {
        val listenerSnapshot: SyncStateListener?
        synchronized(syncStateLock) {
            listenerSnapshot = syncStateListener
        }
        listenerSnapshot?.let {
            handler.post { it.onSyncStateChanged(syncing) }
        }
    }

    private fun encodePreferences(prefs: SharedPreferences): Map<String, Any> {
        val all = prefs.all
        val encoded = hashMapOf<String, Any>()

        for ((key, value) in all) {
            if (value == null) continue

            val wrapper = hashMapOf<String, Any>()
            when (value) {
                is Boolean -> {
                    wrapper["type"] = "bool"
                    wrapper["value"] = value
                }
                is Int -> {
                    wrapper["type"] = "int"
                    wrapper["value"] = value.toLong()
                }
                is Long -> {
                    wrapper["type"] = "long"
                    wrapper["value"] = value
                }
                is Float -> {
                    wrapper["type"] = "float"
                    wrapper["value"] = value.toDouble()
                }
                is String -> {
                    wrapper["type"] = "string"
                    wrapper["value"] = value
                }
                is Set<*> -> {
                    wrapper["type"] = "string_set"
                    @Suppress("UNCHECKED_CAST")
                    wrapper["value"] = ArrayList(value as Set<String>)
                }
                else -> continue
            }
            encoded[key] = wrapper
        }
        return encoded
    }

    private fun encodeEqPreferences(): Map<String, Any> {
        return encodePreferences(eqPrefs).filterKeys { !isDeprecatedEqSyncKey(it) }
    }

    private fun encodeSettingsPreferences(): Map<String, Any> {
        return encodePreferences(settingsPrefs).filterKeys { !isDeprecatedSettingsKey(it) }
    }

    private fun encodeStreamingFavoritesPreferences(): Map<String, Any> {
        val all = streamingCachePrefs.all
        val encoded = hashMapOf<String, Any>()

        for ((key, value) in all) {
            if (!isStreamingFavoritesKey(key) || value == null) continue

            val wrapper = hashMapOf<String, Any>()
            when (value) {
                is Boolean -> {
                    wrapper["type"] = "bool"
                    wrapper["value"] = value
                }
                is Int -> {
                    wrapper["type"] = "int"
                    wrapper["value"] = value.toLong()
                }
                is Long -> {
                    wrapper["type"] = "long"
                    wrapper["value"] = value
                }
                is Float -> {
                    wrapper["type"] = "float"
                    wrapper["value"] = value.toDouble()
                }
                is String -> {
                    wrapper["type"] = "string"
                    wrapper["value"] = value
                }
                is Set<*> -> {
                    wrapper["type"] = "string_set"
                    @Suppress("UNCHECKED_CAST")
                    wrapper["value"] = ArrayList(value as Set<String>)
                }
                else -> continue
            }
            encoded[key] = wrapper
        }
        return encoded
    }

    private fun pruneDeprecatedSettingsKeys() {
        val all = settingsPrefs.all
        if (all.isEmpty()) return

        var editor: SharedPreferences.Editor? = null
        for (key in all.keys) {
            if (!isDeprecatedSettingsKey(key)) continue
            if (editor == null) editor = settingsPrefs.edit()
            editor.remove(key)
        }

        if (editor != null) {
            val previousSuppress = suppressSettingsSync
            suppressSettingsSync = true
            try {
                editor.apply()
            } finally {
                suppressSettingsSync = previousSuppress
            }
        }
    }

    private fun isDeprecatedSettingsKey(key: String?): Boolean {
        if (key.isNullOrEmpty()) return false
        if (LEGACY_APPS_TURBO_MODE == key) return true
        return key.startsWith("schedule_ai_") || key.startsWith("apps_ai_")
    }

    private fun isStreamingFavoritesKey(key: String?): Boolean {
        if (key.isNullOrEmpty()) return false
        return FAVORITES_TRACKS_UPDATED_AT_KEY == key ||
               FAVORITES_TRACKS_DATA_KEY == key ||
               FAVORITES_TRACKS_FULL_CACHE_KEY == key ||
               FAVORITES_OFFLINE_COMPLETE_KEY == key
    }

    private fun isDeprecatedEqSyncKey(key: String?): Boolean {
        if (key.isNullOrEmpty()) return false
        return key.startsWith(DEBUG_EQ_PREFIX) ||
               key.startsWith(LEGACY_AI_EQ_NEXT_AT_PREFIX) ||
               key.startsWith(LEGACY_AI_EQ_LAST_PROCESS_SESSION_PREFIX) ||
               key.startsWith(LEGACY_AI_EQ_DISMISSED_SESSION_PREFIX) ||
               key.startsWith(LEGACY_AI_EQ_PENDING_JSON_PREFIX)
    }

    private fun decodePreferences(encodedPrefs: Map<String, Any>, editor: SharedPreferences.Editor): Int {
        var decodedCount = 0
        for ((key, value) in encodedPrefs) {
            if (decodePreferenceEntry(key, value, editor)) {
                decodedCount++
            }
        }
        return decodedCount
    }

    private fun decodePreferenceEntry(key: String, value: Any?, editor: SharedPreferences.Editor): Boolean {
        if (value is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val wrapper = value as Map<String, Any>
            if (decodeWrappedPreference(key, wrapper, editor)) return true
        }
        return decodeLegacyPreference(key, value, editor)
    }

    private fun decodeWrappedPreference(key: String, wrapper: Map<String, Any>, editor: SharedPreferences.Editor): Boolean {
        val typeObj = wrapper["type"]
        val raw = wrapper["value"]
        if (typeObj == null) return decodeLegacyPreference(key, raw, editor)

        when (typeObj.toString()) {
            "bool" -> if (raw is Boolean) {
                editor.putBoolean(key, raw)
                return true
            }
            "int" -> if (raw is Number) {
                editor.putInt(key, raw.toInt())
                return true
            }
            "long" -> if (raw is Number) {
                editor.putLong(key, raw.toLong())
                return true
            }
            "float" -> if (raw is Number) {
                editor.putFloat(key, raw.toFloat())
                return true
            }
            "string" -> if (raw != null) {
                editor.putString(key, raw.toString())
                return true
            }
            "string_set" -> return decodeStringSetPreference(key, raw, editor)
        }
        return decodeLegacyPreference(key, raw, editor)
    }

    private fun decodeLegacyPreference(key: String, raw: Any?, editor: SharedPreferences.Editor): Boolean {
        if (raw == null) return false

        when (raw) {
            is Boolean -> {
                editor.putBoolean(key, raw)
                return true
            }
            is String -> {
                editor.putString(key, raw)
                return true
            }
            is Number -> {
                if (isLikelyFloatKey(key)) {
                    editor.putFloat(key, raw.toFloat())
                    return true
                }
                if (isLikelyIntKey(key)) {
                    editor.putInt(key, raw.toInt())
                    return true
                }
                if (isLikelyLongKey(key)) {
                    editor.putLong(key, raw.toLong())
                    return true
                }

                val asDouble = raw.toDouble()
                if (Math.floor(asDouble) != asDouble) {
                    editor.putFloat(key, asDouble.toFloat())
                    return true
                }

                val asLong = raw.toLong()
                if (asLong in Int.MIN_VALUE..Int.MAX_VALUE) {
                    editor.putInt(key, asLong.toInt())
                    return true
                }
                editor.putLong(key, asLong)
                return true
            }
            is List<*>, is Set<*> -> return decodeStringSetPreference(key, raw, editor)
        }
        return false
    }

    private fun decodeStringSetPreference(key: String, raw: Any?, editor: SharedPreferences.Editor): Boolean {
        val values: Iterable<*> = when (raw) {
            is List<*> -> raw
            is Set<*> -> raw
            else -> return false
        }

        val set = hashSetOf<String>()
        for (item in values) {
            if (item != null) set.add(item.toString())
        }
        editor.putStringSet(key, set)
        return true
    }

    private fun isLikelyFloatKey(key: String): Boolean {
        if (AudioEffectsService.KEY_BASS_DB == key ||
            AudioEffectsService.KEY_BASS_FREQUENCY_HZ == key ||
            key.startsWith(LEGACY_BAND_PREFIX) ||
            key.startsWith(LEGACY_USER_PRESET_BAND_PREFIX)) {
            return true
        }

        if (!key.startsWith(LEGACY_DEVICE_PROFILE_PREFIX)) return false

        return key.endsWith("_" + AudioEffectsService.KEY_BASS_DB) ||
               key.endsWith("_" + AudioEffectsService.KEY_BASS_FREQUENCY_HZ) ||
               key.contains("_" + LEGACY_BAND_PREFIX)
    }

    private fun isLikelyIntKey(key: String): Boolean {
        if (AudioEffectsService.KEY_SPATIAL_STRENGTH == key ||
            AudioEffectsService.KEY_REVERB_LEVEL == key ||
            AudioEffectsService.KEY_BASS_TYPE == key ||
            KEY_DEFAULT_DURATION_MINUTES == key ||
            KEY_NOTIFICATION_LEAD_MINUTES == key ||
            KEY_DAILY_SUMMARY_INTERVAL_HOURS == key ||
            KEY_OFFLINE_CROSSFADE_SECONDS == key ||
            LEGACY_SCHEDULE_AI_ACCEPT_COUNT == key ||
            LEGACY_SCHEDULE_AI_DISMISS_COUNT == key ||
            LEGACY_SCHEDULE_AI_OPEN_COUNT == key ||
            LEGACY_APPS_AI_ACCEPT_COUNT == key ||
            LEGACY_APPS_AI_DISMISS_COUNT == key) {
            return true
        }

        return key.startsWith(LEGACY_DEVICE_PROFILE_PREFIX) && key.endsWith("_" + AudioEffectsService.KEY_BASS_TYPE)
    }

    private fun isLikelyLongKey(key: String): Boolean {
        return key.startsWith(LEGACY_AI_EQ_NEXT_AT_PREFIX) ||
               key.startsWith(LEGACY_AI_EQ_LAST_PROCESS_SESSION_PREFIX) ||
               key.startsWith(LEGACY_AI_EQ_DISMISSED_SESSION_PREFIX) ||
               LEGACY_SCHEDULE_AI_NEXT_AT == key ||
               LEGACY_SCHEDULE_AI_LAST_PROCESS_SESSION == key ||
               LEGACY_APPS_AI_NEXT_AT == key ||
               LEGACY_APPS_AI_LAST_PROCESS_SESSION == key
    }

    private fun userScope(uid: String): DocumentReference =
        firestore.collection(USERS_COLLECTION).document(uid).collection(APP_SCOPE_COLLECTION).document("root")

    private fun eqDoc(uid: String): DocumentReference =
        firestore.collection(USERS_COLLECTION).document(uid).collection(APP_SCOPE_COLLECTION).document(DOC_EQ)

    private fun settingsDoc(uid: String): DocumentReference =
        firestore.collection(USERS_COLLECTION).document(uid).collection(APP_SCOPE_COLLECTION).document(DOC_SETTINGS)

    private fun agendaDoc(uid: String): DocumentReference =
        firestore.collection(USERS_COLLECTION).document(uid).collection(APP_SCOPE_COLLECTION).document(DOC_AGENDA)

    private fun streamingDoc(uid: String): DocumentReference =
        firestore.collection(USERS_COLLECTION).document(uid).collection(APP_SCOPE_COLLECTION).document(DOC_STREAMING)

    private fun playlistsDoc(uid: String): DocumentReference =
        firestore.collection(USERS_COLLECTION).document(uid).collection(APP_SCOPE_COLLECTION).document(DOC_PLAYLISTS)

    fun syncPlaylistToCloud(playlistName: String, tracks: List<FavoriteTrack>) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) return

        val tracksList = tracks.map { t ->
            hashMapOf<String, Any>(
                "videoId" to t.videoId,
                "title" to t.title,
                "artist" to t.artist,
                "duration" to t.duration,
                "imageUrl" to t.imageUrl
            )
        }

        val encodedPlaylistKey = encodePlaylistKey(playlistName)
        playlistsDoc(uid).get()
            .addOnSuccessListener { snapshot ->
                val payload = hashMapOf<String, Any>()
                val playlistsMap = hashMapOf<String, Any>()
                
                val existingPlaylists = snapshot.get("playlists")
                if (existingPlaylists is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    playlistsMap.putAll(existingPlaylists as Map<String, Any>)
                }

                playlistsMap[encodedPlaylistKey] = hashMapOf(
                    "name" to playlistName,
                    "tracks" to tracksList
                )

                payload["playlists"] = playlistsMap
                payload[FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
                playlistsDoc(uid).set(payload)
                    .addOnSuccessListener { Log.d(TAG, "Playlist $playlistName synced to cloud") }
                    .addOnFailureListener { e -> Log.e(TAG, "Error syncing playlist $playlistName", e) }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Error reading cloud playlists doc", e) }
    }

    fun deletePlaylistFromCloud(playlistName: String) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) return

        val encodedPlaylistKey = encodePlaylistKey(playlistName)
        playlistsDoc(uid).get()
            .addOnSuccessListener { snapshot ->
                val payload = hashMapOf<String, Any>()
                val playlistsMap = hashMapOf<String, Any>()
                
                val existingPlaylists = snapshot.get("playlists")
                if (existingPlaylists is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    playlistsMap.putAll(existingPlaylists as Map<String, Any>)
                }

                if (playlistsMap.containsKey(encodedPlaylistKey)) {
                    playlistsMap.remove(encodedPlaylistKey)
                    payload["playlists"] = playlistsMap
                    payload[FIELD_UPDATED_AT] = FieldValue.serverTimestamp()
                    playlistsDoc(uid).set(payload)
                        .addOnSuccessListener { Log.d(TAG, "Playlist \$playlistName deleted from cloud") }
                        .addOnFailureListener { e -> Log.e(TAG, "Error deleting playlist \$playlistName from cloud", e) }
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Error reading cloud playlists doc for deletion", e) }
    }

    fun fetchCloudPlaylists(callback: CloudPlaylistsCallback) {
        val uid = activeUserId
        if (uid.isNullOrEmpty()) {
            callback.onResult(emptyMap())
            return
        }

        playlistsDoc(uid).get(Source.SERVER)
            .addOnSuccessListener { snapshot -> callback.onResult(decodeCloudPlaylists(snapshot)) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching cloud playlists", e)
                callback.onResult(emptyMap())
            }
    }

    private fun decodeCloudPlaylists(snapshot: DocumentSnapshot): Map<String, List<FavoriteTrack>> {
        val result = hashMapOf<String, List<FavoriteTrack>>()
        val rawPlaylists = snapshot.get("playlists") as? Map<*, *> ?: return result

        for ((key, rawEntry) in rawPlaylists) {
            val playlistEntry = rawEntry as? Map<*, *> ?: continue

            var name = asStringOrEmpty(playlistEntry["name"])
            if (name.isEmpty() && key is String) name = decodePlaylistKey(key)
            if (name.isEmpty()) continue

            val tracks = mutableListOf<FavoriteTrack>()
            val rawTracks = playlistEntry["tracks"] as? List<*>
            if (rawTracks != null) {
                for (trackObj in rawTracks) {
                    val m = trackObj as? Map<*, *> ?: continue
                    tracks.add(
                        FavoriteTrack(
                            asStringOrEmpty(m["videoId"]),
                            asStringOrEmpty(m["title"]),
                            asStringOrEmpty(m["artist"]),
                            asStringOrEmpty(m["duration"]),
                            asStringOrEmpty(m["imageUrl"])
                        )
                    )
                }
            }
            result[name] = tracks
        }
        return result
    }

    private fun asStringOrEmpty(value: Any?): String = value?.toString() ?: ""

    private fun encodePlaylistKey(name: String): String {
        val raw = name.toByteArray(StandardCharsets.UTF_8)
        return Base64.encodeToString(raw, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decodePlaylistKey(key: String?): String {
        if (key.isNullOrEmpty()) return ""
        return try {
            val decoded = Base64.decode(key, Base64.NO_WRAP or Base64.URL_SAFE)
            String(decoded, StandardCharsets.UTF_8)
        } catch (ignored: IllegalArgumentException) {
            ""
        }
    }

    private class PendingSync(total: Int, private val callback: (Boolean, String?) -> Unit) {
        private var remaining = total
        private var hasError = false
        private var firstError: String? = null

        @Synchronized
        fun finish(success: Boolean, message: String?) {
            if (!success && !hasError) {
                hasError = true
                firstError = if (message.isNullOrEmpty()) "Error de sincronizacion" else message
            }

            remaining--
            if (remaining <= 0) {
                callback(!hasError, firstError)
            }
        }
    }
}
