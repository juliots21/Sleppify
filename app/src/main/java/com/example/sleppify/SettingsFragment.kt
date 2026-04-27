package com.example.sleppify

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Optimized Kotlin version of SettingsFragment.
 * Manages app configuration, profile state, and storage analytics.
 * Powered by Coroutines for non-blocking storage operations.
 */
class SettingsFragment : Fragment() {

    companion object {
        private const val DELETE_CONFIRM_WORD = "eliminar"
    }

    private lateinit var cardProfile: MaterialCardView
    private lateinit var ivProfilePhoto: ShapeableImageView
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileBadge: TextView
    private lateinit var tvSummaryFrequencyValue: TextView
    private lateinit var swAiShiftPermissions: MaterialSwitch
    private lateinit var swAmoledMode: MaterialSwitch
    private lateinit var swDownloadOnMobileData: MaterialSwitch
    private lateinit var swTvMode: MaterialSwitch
    private lateinit var rowTvMode: View
    private lateinit var dividerTvMode: View
    private lateinit var rowSummaryFrequency: View
    private lateinit var rowDownloadQuality: View
    private lateinit var tvDownloadQualityValue: TextView
    private lateinit var tvOfflineCrossfadeThumbValue: TextView
    private lateinit var tvStorageOtherAppsValue: TextView
    private lateinit var tvStorageDownloadsValue: TextView
    private lateinit var tvStorageCacheValue: TextView
    private lateinit var tvStorageFreeValue: TextView
    private lateinit var sbOfflineCrossfade: SeekBar
    private lateinit var vStorageOtherApps: View
    private lateinit var vStorageDownloads: View
    private lateinit var vStorageCache: View
    private lateinit var vStorageFree: View
    private lateinit var btnDeleteSettingsCache: MaterialButton

    private val settingsPrefs: SharedPreferences by lazy { 
        requireContext().getSharedPreferences(CloudSyncManager.PREFS_SETTINGS, Context.MODE_PRIVATE) 
    }
    
    private val localPrefs: SharedPreferences by lazy {
        requireContext().getSharedPreferences("sleppify_local_config", Context.MODE_PRIVATE)
    }
    
    private val authManager: AuthManager by lazy { AuthManager.getInstance(requireContext()) }
    
    private var deleteAccountInFlight = false
    private var storageCleanupInFlight = false
    private var hasSettingsSnapshot = false
    private var hasProfileSnapshot = false

    // State Snapshots
    private var lastSmartSuggestionsEnabled = false
    private var lastAmoledModeEnabled = false
    private var lastSummaryFrequencyTimes = -1
    private var lastOfflineCrossfadeSeconds = -1
    private var lastAllowMobileDataDownloads = false
    private var lastDownloadQuality: String? = null
    private var lastProfileSignedIn = false
    private var lastProfileDisplayName: String? = null
    private var lastProfileEmail: String? = null
    private var lastProfilePhotoUrl: String? = null
    private var lastTvModeEnabled = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupInteractions()
        renderSettingsState()
        renderProfileState()
        refreshStorageBreakdownAsync()
    }

    private fun initViews(v: View) {
        cardProfile = v.findViewById(R.id.cardProfile)
        ivProfilePhoto = v.findViewById(R.id.ivProfilePhoto)
        tvProfileName = v.findViewById(R.id.tvProfileName)
        tvProfileBadge = v.findViewById(R.id.tvProfileBadge)
        tvSummaryFrequencyValue = v.findViewById(R.id.tvSummaryFrequencyValue)
        swAiShiftPermissions = v.findViewById(R.id.swAiShiftPermissions)
        swAmoledMode = v.findViewById(R.id.swAmoledMode)
        swDownloadOnMobileData = v.findViewById(R.id.swDownloadOnMobileData)
        rowSummaryFrequency = v.findViewById(R.id.rowSummaryFrequency)
        rowDownloadQuality = v.findViewById(R.id.rowDownloadQuality)
        tvDownloadQualityValue = v.findViewById(R.id.tvDownloadQualityValue)
        tvOfflineCrossfadeThumbValue = v.findViewById(R.id.tvOfflineCrossfadeThumbValue)
        sbOfflineCrossfade = v.findViewById(R.id.sbOfflineCrossfade)
        tvStorageOtherAppsValue = v.findViewById(R.id.tvStorageOtherAppsValue)
        tvStorageDownloadsValue = v.findViewById(R.id.tvStorageDownloadsValue)
        tvStorageCacheValue = v.findViewById(R.id.tvStorageCacheValue)
        tvStorageFreeValue = v.findViewById(R.id.tvStorageFreeValue)
        vStorageOtherApps = v.findViewById(R.id.vStorageOtherApps)
        vStorageDownloads = v.findViewById(R.id.vStorageDownloads)
        vStorageCache = v.findViewById(R.id.vStorageCache)
        vStorageFree = v.findViewById(R.id.vStorageFree)
        btnDeleteSettingsCache = v.findViewById(R.id.btnDeleteSettingsCache)
        swTvMode = v.findViewById(R.id.swTvMode)
        rowTvMode = v.findViewById(R.id.rowTvMode)
        dividerTvMode = v.findViewById(R.id.dividerTvMode)
    }

    private fun setupInteractions() {
        cardProfile.setOnClickListener {
            if (authManager.isSignedIn()) showAccountActionsDialog()
            else (activity as? MainActivity)?.requireAuth({ renderProfileState() }, { renderProfileState() })
        }
        
        rowSummaryFrequency.setOnClickListener { showSummaryFrequencyPicker() }
        rowDownloadQuality.setOnClickListener { showDownloadQualityPicker() }
        btnDeleteSettingsCache.setOnClickListener { showDeleteCacheConfirmation() }
        updateDeleteCacheButtonState()
    }

    override fun onResume() {
        super.onResume()
        renderSettingsState()
        renderProfileState()
        refreshStorageBreakdownAsync()
    }

    private fun showDeleteCacheConfirmation() {
        if (!isAdded || storageCleanupInFlight) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_storage_delete_cache_title)
            .setMessage(R.string.settings_storage_delete_cache_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_storage_delete_cache_confirm) { _, _ -> clearSettingsCacheAsync() }
            .show()
    }

    private fun clearSettingsCacheAsync() {
        if (!isAdded || storageCleanupInFlight) return
        storageCleanupInFlight = true
        updateDeleteCacheButtonState()

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val freed = clearAppCache(appContext)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                storageCleanupInFlight = false
                updateDeleteCacheButtonState()
                val msg = if (freed >= 0) R.string.settings_storage_delete_cache_done else R.string.settings_storage_delete_cache_error
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                refreshStorageBreakdownAsync()
            }
        }
    }

    private suspend fun clearAppCache(context: Context): Long {
        return try {
            var deleted = 0L
            collectAppCacheRoots(context).forEach { deleted += deleteRecursively(it) }
            try { 
                Glide.get(context).clearDiskCache()
                withContext(Dispatchers.Main) { Glide.get(context).clearMemory() }
            } catch (e: Exception) {}
            deleted.coerceAtLeast(0L)
        } catch (e: Exception) { -1L }
    }

    private fun refreshStorageBreakdownAsync() {
        if (!isAdded) return
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val breakdown = calculateStorageBreakdown(appContext)
            withContext(Dispatchers.Main) {
                if (isAdded) renderStorageBreakdown(breakdown)
            }
        }
    }

    private fun renderStorageBreakdown(b: StorageBreakdown?) {
        if (b == null || !isAdded) return
        
        tvStorageOtherAppsValue.text = getString(R.string.settings_storage_row_value_format, getString(R.string.settings_storage_other_apps), formatSize(b.otherAppsBytes))
        tvStorageDownloadsValue.text = getString(R.string.settings_storage_row_value_format, getString(R.string.settings_storage_downloads), formatSize(b.downloadsBytes))
        tvStorageCacheValue.text = getString(R.string.settings_storage_row_value_format, getString(R.string.settings_storage_cache), formatSize(b.cacheBytes))
        tvStorageFreeValue.text = getString(R.string.settings_storage_row_value_format, getString(R.string.settings_storage_free), formatSize(b.freeBytes))

        val total = b.otherAppsBytes + b.downloadsBytes + b.cacheBytes + b.freeBytes
        val safeTotal = total.coerceAtLeast(1L)
        
        applySegmentWeight(vStorageOtherApps, b.otherAppsBytes, safeTotal)
        applySegmentWeight(vStorageDownloads, b.downloadsBytes, safeTotal)
        applySegmentWeight(vStorageCache, b.cacheBytes, safeTotal)
        applySegmentWeight(vStorageFree, b.freeBytes, safeTotal)
    }

    private fun applySegmentWeight(v: View, bytes: Long, total: Long) {
        val params = v.layoutParams as? LinearLayout.LayoutParams ?: return
        if (bytes <= 0L) {
            params.weight = 0f
            v.visibility = View.GONE
        } else {
            params.weight = (bytes / total.toFloat()).coerceAtLeast(0.025f)
            v.visibility = View.VISIBLE
        }
        v.layoutParams = params
    }

    private fun calculateStorageBreakdown(context: Context): StorageBreakdown? {
        return try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = total - free

            val downloads = collectDownloadRoots(context).sumOf { calculateSize(it) }
            val cache = collectAppCacheRoots(context).sumOf { calculateSize(it) }
            val persistent = collectAppPersistentRoots(context).sumOf { calculateSize(it) }
            
            val appPersistentOnly = (persistent - downloads).coerceAtLeast(0L)
            val otherApps = (used - downloads - cache - appPersistentOnly).coerceAtLeast(0L)

            StorageBreakdown(total, free, downloads, cache, otherApps, appPersistentOnly)
        } catch (e: Exception) { null }
    }

    private fun collectDownloadRoots(context: Context) = mutableListOf<File>().apply {
        addDistinct(OfflineAudioStore.getOfflineAudioDir(context))
        context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { addDistinct(File(it, "offline_audio")) }
    }

    private fun collectAppCacheRoots(context: Context) = mutableListOf<File>().apply {
        addDistinct(context.cacheDir)
        addDistinct(context.codeCacheDir)
        context.getExternalCacheDirs()?.filterNotNull()?.forEach { addDistinct(it) }
    }

    private fun collectAppPersistentRoots(context: Context) = mutableListOf<File>().apply {
        addDistinct(context.filesDir)
        addDistinct(context.noBackupFilesDir)
        addDistinct(File(context.applicationInfo.dataDir, "databases"))
        addDistinct(File(context.applicationInfo.dataDir, "shared_prefs"))
        context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { addDistinct(it) }
        addDistinct(context.obbDir)
    }

    private fun MutableList<File>.addDistinct(f: File?) {
        val path = try { f?.canonicalPath } catch (e: IOException) { f?.absolutePath } ?: return
        if (none { try { it.canonicalPath == path } catch (e: IOException) { it.absolutePath == path } }) {
            add(f!!)
        }
    }

    private fun calculateSize(f: File?): Long {
        if (f == null || !f.exists()) return 0L
        if (f.isFile) return f.length().coerceAtLeast(0L)
        return f.listFiles()?.sumOf { calculateSize(it) } ?: 0L
    }

    private fun deleteRecursively(f: File?): Long {
        if (f == null || !f.exists()) return 0L
        if (f.isFile) {
            val s = f.length().coerceAtLeast(0L)
            return if (f.delete()) s else 0L
        }
        val deleted = f.listFiles()?.sumOf { deleteRecursively(it) } ?: 0L
        f.delete()
        return deleted
    }

    private fun formatSize(b: Long): String {
        val mb = b / (1024.0 * 1024.0)
        return if (mb < 1024.0) "%.0f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
    }

    private fun updateDeleteCacheButtonState() {
        btnDeleteSettingsCache.isEnabled = !storageCleanupInFlight
        btnDeleteSettingsCache.setText(if (storageCleanupInFlight) R.string.settings_storage_delete_cache_running else R.string.settings_storage_delete_cache)
    }

    private fun renderSettingsState() {
        val suggestions = settingsPrefs.getBoolean(CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED, settingsPrefs.getBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, true))
        val amoled = settingsPrefs.getBoolean(CloudSyncManager.KEY_AMOLED_MODE_ENABLED, false)
        val frequency = settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2)
        val crossfade = settingsPrefs.getInt(CloudSyncManager.KEY_OFFLINE_CROSSFADE_SECONDS, 0).coerceIn(0, 12)
        val mobileDownloads = settingsPrefs.getBoolean(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA, false)
        val tvMode = localPrefs.getBoolean("tv_mode_enabled", false)
        val quality = normalizeQuality(settingsPrefs.getString(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_QUALITY, CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM))

        if (hasSettingsSnapshot && lastSmartSuggestionsEnabled == suggestions && lastAmoledModeEnabled == amoled &&
            lastSummaryFrequencyTimes == frequency && lastOfflineCrossfadeSeconds == crossfade &&
            lastAllowMobileDataDownloads == mobileDownloads && lastDownloadQuality == quality &&
            lastTvModeEnabled == tvMode) return

        hasSettingsSnapshot = true
        lastSmartSuggestionsEnabled = suggestions; lastAmoledModeEnabled = amoled
        lastSummaryFrequencyTimes = frequency; lastOfflineCrossfadeSeconds = crossfade
        lastAllowMobileDataDownloads = mobileDownloads; lastDownloadQuality = quality
        lastTvModeEnabled = tvMode

        swAiShiftPermissions.apply {
            setOnCheckedChangeListener(null)
            isChecked = suggestions
            setOnCheckedChangeListener { _, c ->
                settingsPrefs.edit().putBoolean(CloudSyncManager.KEY_SMART_SUGGESTIONS_ENABLED, c).putBoolean(CloudSyncManager.KEY_AI_SHIFT_ENABLED, c).apply()
                (activity as? MainActivity)?.refreshSessionUi()
            }
        }

        swAmoledMode.apply {
            setOnCheckedChangeListener(null)
            isChecked = amoled
            setOnCheckedChangeListener { _, c ->
                settingsPrefs.edit().putBoolean(CloudSyncManager.KEY_AMOLED_MODE_ENABLED, c).apply()
                CloudSyncManager.getInstance(requireContext()).syncSettingsNowIfSignedIn()
                AppCompatDelegate.setDefaultNightMode(if (c) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        swDownloadOnMobileData.apply {
            setOnCheckedChangeListener(null)
            isChecked = mobileDownloads
            setOnCheckedChangeListener { _, c ->
                settingsPrefs.edit().putBoolean(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_ALLOW_MOBILE_DATA, c).apply()
                renderSettingsState()
            }
        }

        val isRunningOnTv = SystemType.isTv(requireContext())
        
        if (isRunningOnTv) {
            rowTvMode.visibility = View.VISIBLE
            dividerTvMode.visibility = View.VISIBLE
            swTvMode.apply {
                setOnCheckedChangeListener(null)
                isChecked = tvMode
                setOnCheckedChangeListener { _, c ->
                    localPrefs.edit().putBoolean("tv_mode_enabled", c).apply()
                    // No sync to cloud
                }
            }
        } else {
            rowTvMode.visibility = View.GONE
            dividerTvMode.visibility = View.GONE
        }

        tvSummaryFrequencyValue.text = if (frequency == 1) "1 vez" else "$frequency veces"
        tvDownloadQualityValue.text = labelForQuality(quality)

        sbOfflineCrossfade.apply {
            setOnSeekBarChangeListener(null)
            max = 12
            progress = crossfade
            updateIndicator(crossfade)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, p: Int, b: Boolean) = updateIndicator(p)
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {
                    settingsPrefs.edit().putInt(CloudSyncManager.KEY_OFFLINE_CROSSFADE_SECONDS, progress).apply()
                    renderSettingsState()
                }
            })
        }
    }

    private fun updateIndicator(s: Int) {
        tvOfflineCrossfadeThumbValue.text = s.toString()
        sbOfflineCrossfade.post {
            val pv = tvOfflineCrossfadeThumbValue.parent as? View ?: return@post
            val pw = pv.width.takeIf { it > 0 } ?: return@post
            val ratio = s / sbOfflineCrossfade.max.toFloat()
            val tw = sbOfflineCrossfade.width - sbOfflineCrossfade.paddingLeft - sbOfflineCrossfade.paddingRight
            val center = sbOfflineCrossfade.paddingLeft + (tw * ratio)
            val textW = tvOfflineCrossfadeThumbValue.width.takeIf { it > 0 } ?: run {
                tvOfflineCrossfadeThumbValue.measure(0, 0)
                tvOfflineCrossfadeThumbValue.getMeasuredWidth()
            }
            tvOfflineCrossfadeThumbValue.translationX = (center - textW / 2f).coerceIn(0f, (pw - textW).toFloat())
        }
    }

    private fun showDownloadQualityPicker() {
        val vals = arrayOf(CloudSyncManager.DOWNLOAD_QUALITY_LOW, CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM, CloudSyncManager.DOWNLOAD_QUALITY_HIGH, CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH)
        val labs = arrayOf("Baja", "Media", "Alta", "Muy alta")
        val cur = normalizeQuality(settingsPrefs.getString(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_QUALITY, CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM))
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_stream_download_quality_title)
            .setSingleChoiceItems(labs, vals.indexOf(cur)) { d, w ->
                settingsPrefs.edit().putString(CloudSyncManager.KEY_OFFLINE_DOWNLOAD_QUALITY, vals[w]).apply()
                renderSettingsState()
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSummaryFrequencyPicker() {
        val vals = intArrayOf(1, 2, 3, 4, 5)
        val labs = arrayOf("1 vez", "2 veces", "3 veces", "4 veces", "5 veces")
        val cur = settingsPrefs.getInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, 2)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Frecuencia resumen IA")
            .setSingleChoiceItems(labs, vals.indexOf(cur)) { d, w ->
                settingsPrefs.edit().putInt(CloudSyncManager.KEY_DAILY_SUMMARY_INTERVAL_HOURS, vals[w]).apply()
                renderSettingsState()
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun normalizeQuality(q: String?) = when (q) {
        CloudSyncManager.DOWNLOAD_QUALITY_LOW -> q
        CloudSyncManager.DOWNLOAD_QUALITY_HIGH -> q
        CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH -> q
        else -> CloudSyncManager.DOWNLOAD_QUALITY_MEDIUM
    }

    private fun labelForQuality(q: String) = when (q) {
        CloudSyncManager.DOWNLOAD_QUALITY_LOW -> "Baja"
        CloudSyncManager.DOWNLOAD_QUALITY_HIGH -> "Alta"
        CloudSyncManager.DOWNLOAD_QUALITY_VERY_HIGH -> "Muy alta"
        else -> "Media"
    }

    private fun renderProfileState() {
        val signedIn = authManager.isSignedIn()
        val name: String? = if (signedIn) authManager.getDisplayName() else null
        val email: String? = if (signedIn) authManager.getEmail() else null
        val photo: String? = if (signedIn) authManager.getPhotoUrl()?.toString() else null

        if (hasProfileSnapshot && lastProfileSignedIn == signedIn && lastProfileDisplayName == name && lastProfileEmail == email && lastProfilePhotoUrl == photo) return

        hasProfileSnapshot = true
        lastProfileSignedIn = signedIn; lastProfileDisplayName = name; lastProfileEmail = email; lastProfilePhotoUrl = photo

        if (!signedIn) {
            tvProfileName.text = "Sleppify"
            tvProfileBadge.text = "Inicia sesion para sincronizar"
            ivProfilePhoto.setImageDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.surface_high)))
            return
        }

        tvProfileName.text = name?.takeIf { it.isNotBlank() } ?: "Usuario"
        tvProfileBadge.text = email?.takeIf { it.isNotBlank() } ?: "Cuenta conectada · toca para cerrar sesion"
        
        if (!photo.isNullOrEmpty()) {
            Glide.with(this).load(photo).circleCrop()
                .placeholder(R.color.surface_high).error(R.color.surface_high).into(ivProfilePhoto)
        } else {
            ivProfilePhoto.setImageDrawable(ColorDrawable(ContextCompat.getColor(requireContext(), R.color.surface_high)))
        }
    }

    private fun showAccountActionsDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(authManager.getDisplayName())
            .setMessage(authManager.getEmail())
            .setNeutralButton("Eliminar cuenta", null)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesion") { _, _ -> performSignOut() }
            .show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            setTextColor(Color.parseColor("#FF6E6E"))
            setOnClickListener { dialog.dismiss(); showDeleteAccountDataConfirmation() }
        }
    }

    private fun performSignOut() {
        (activity as? MainActivity)?.pauseActiveMediaAndDownloadsForSessionChange()
        authManager.signOut(requireContext()) { _, _ ->
            CloudSyncManager.getInstance(requireContext()).onUserSignedOut()
            renderProfileState()
            (activity as? MainActivity)?.refreshSessionUi()
        }
    }

    private fun showDeleteAccountDataConfirmation() {
        if (!authManager.isSignedIn()) return
        val input = EditText(requireContext()).apply {
            hint = "Escribe \"eliminar\""
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(6), dp(24), 0)
            addView(input)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar cuenta y borrar todo")
            .setMessage("Esta accion eliminara de forma permanente tu cuenta y datos locales.\n\nPara confirmar, escribe \"eliminar\".")
            .setView(container)
            .setPositiveButton("Eliminar cuenta y todo", null)
            .setNegativeButton("Cancelar", null).show()

        val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply { isEnabled = false }
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val matches = s?.toString()?.trim()?.lowercase(Locale.ROOT) == DELETE_CONFIRM_WORD
                btn.isEnabled = matches
                input.error = if (!matches && !s.isNullOrEmpty()) "Debes escribir eliminar" else null
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        btn.setOnClickListener {
            if (input.text?.toString()?.trim()?.lowercase(Locale.ROOT) == DELETE_CONFIRM_WORD) {
                dialog.dismiss()
                performDeleteAccountAndData()
            }
        }
    }

    private fun performDeleteAccountAndData() {
        if (deleteAccountInFlight || !isAdded) return
        (activity as? MainActivity)?.pauseActiveMediaAndDownloadsForSessionChange()
        val uid = authManager.getCurrentUser()?.uid ?: return
        
        deleteAccountInFlight = true
        val appContext = requireContext().applicationContext
        val cloudSync = CloudSyncManager.getInstance(appContext)

        cloudSync.deleteUserDataFromCloud(uid) { ok: Boolean, _: String? ->
            if (ok && isAdded) {
                authManager.deleteCurrentUser(requireActivity()) { authOk: Boolean, _: String? ->
                    deleteAccountInFlight = false
                    if (authOk && isAdded) {
                        cloudSync.onUserSignedOut()
                        cloudSync.clearLocalUserDataCompletely()
                        hasProfileSnapshot = false; hasSettingsSnapshot = false
                        renderProfileState(); renderSettingsState()
                        (activity as? MainActivity)?.refreshSessionUi()
                    }
                }
            } else {
                deleteAccountInFlight = false
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private data class StorageBreakdown(val totalBytes: Long, val freeBytes: Long, val downloadsBytes: Long, val cacheBytes: Long, val otherAppsBytes: Long, val appPersistentBytes: Long)
}
